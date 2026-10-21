package com.vm.vector.data.sync

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.vm.core.models.VectorDatabase
import com.vm.vector.data.DriveConfig
import com.vm.vector.data.DriveFileMeta
import com.vm.vector.data.DriveResult
import com.vm.vector.data.DriveService
import com.vm.vector.data.GoogleDriveAuthManager
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.SupermarketRepository
import com.vm.vector.data.backup.BackupNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "BrainStateSync"

sealed class BrainSyncOutcome {
    object NotConfigured : BrainSyncOutcome()
    object UpToDate : BrainSyncOutcome()
    data class Pushed(val rev: Long) : BrainSyncOutcome()
    data class Pulled(val rev: Long) : BrainSyncOutcome()
    /** Both devices changed Brain; a [PendingMerge] is stored and the user has been notified. */
    data class NeedsDecision(val conflicts: Int) : BrainSyncOutcome()
    data class Failed(val message: String, val consentIntent: Intent? = null) : BrainSyncOutcome()
}

enum class MergeChoice { KEEP_LOCAL, KEEP_REMOTE, SMART }

/** Pending merge as shown in the merge sheet. */
data class PendingMergeView(
    val analysis: MergeAnalysis,
    val remoteDevice: SyncDevice?,
    val remoteWrittenAt: Long,
)

/**
 * Keeps Brain in step with Data/brain/_sync/state.json. Protocol and merge rules: docs/brain-sync-spec.md.
 */
class BrainStateSync(
    context: Context,
    database: VectorDatabase,
) {
    private val appContext = context.applicationContext
    private val preferences = PreferenceManager(appContext)
    private val drive = DriveService(appContext, GoogleDriveAuthManager(appContext))
    private val files = BrainSyncFiles(appContext)
    private val store = BrainSnapshotStore(appContext, database)
    private val supermarket = SupermarketRepository.create(appContext)

    private data class Location(val syncFolderId: String)

    private sealed class Located {
        data class Ok(val location: Location) : Located()
        data class Stop(val outcome: BrainSyncOutcome) : Located()
    }

    suspend fun sync(): BrainSyncOutcome = LOCK.withLock {
        runCatching { syncLocked() }.getOrElse {
            Log.w(TAG, "Brain sync failed", it)
            BrainSyncOutcome.Failed(it.message ?: "Brain sync failed")
        }
    }

    /** Analysis of the stored pending merge against this device's current Brain; null when nothing is pending. */
    suspend fun loadPending(): PendingMergeView? = LOCK.withLock {
        val pending = files.readPending() ?: return@withLock null
        val analysis = BrainMerge.analyze(baseRecords(), store.export().toRecords(), pending.remote.toRecords())
        PendingMergeView(analysis, pending.remote.writtenBy, pending.remote.writtenAt)
    }

    suspend fun resolvePending(
        choice: MergeChoice,
        resolutions: Map<String, Resolution> = emptyMap(),
    ): BrainSyncOutcome = LOCK.withLock {
        runCatching { resolveLocked(choice, resolutions) }.getOrElse {
            Log.w(TAG, "Brain merge failed", it)
            BrainSyncOutcome.Failed(it.message ?: "Merge failed")
        }
    }

    private suspend fun syncLocked(): BrainSyncOutcome {
        repeat(MAX_ATTEMPTS) {
            val location = when (val l = locate()) {
                is Located.Ok -> l.location
                is Located.Stop -> return l.outcome
            }
            val remoteMeta = when (val r = drive.findFileMeta(location.syncFolderId, DriveConfig.BRAIN_STATE_FILENAME)) {
                is DriveResult.Success -> r.data
                else -> return failure(r)
            }

            files.readPending()?.let { pending -> return refreshPending(pending, remoteMeta) }

            val base = files.readBase()
            if (base == null && !supermarket.hasLocalFile()) supermarket.importLegacyFromDrive()
            val local = store.export()
            val remote = remoteMeta?.let { meta ->
                when (val r = download(meta)) {
                    is Downloaded.Ok -> r.state
                    is Downloaded.Fail -> return r.outcome
                }
            }

            val localRecords = local.toRecords()
            val localChanged = if (base == null) hasContent(localRecords) else
                BrainMerge.canonical(localRecords) != BrainMerge.canonical(base.toRecords())
            val remoteChanged = remote != null && (base == null || remote.rev != base.rev)

            if (remote == null || remoteMeta == null) {
                push(location, local, rev = (base?.rev ?: 0L) + 1, expectedVersion = null)?.let { return it }
                return@repeat
            }

            when {
                !localChanged && !remoteChanged -> {
                    preferences.setLastBrainSyncAt(System.currentTimeMillis())
                    return BrainSyncOutcome.UpToDate
                }
                localChanged && !remoteChanged -> {
                    val pushed = push(location, local, rev = remote.rev + 1, expectedVersion = remoteMeta.version)
                    if (pushed != null) return pushed
                }
                !localChanged -> return pull(remote)
                BrainMerge.canonical(localRecords) == BrainMerge.canonical(remote.toRecords()) -> {
                    files.writeBase(local.copy(rev = remote.rev, writtenBy = remote.writtenBy, writtenAt = remote.writtenAt))
                    preferences.setLastBrainSyncAt(System.currentTimeMillis())
                    return BrainSyncOutcome.UpToDate
                }
                else -> {
                    files.writePending(PendingMerge(remote, remoteMeta.version, System.currentTimeMillis()))
                    BackupNotifier.notifyBrainMerge(appContext)
                    val conflicts = BrainMerge.analyze(baseRecords(), localRecords, remote.toRecords()).conflicts.size
                    return BrainSyncOutcome.NeedsDecision(conflicts)
                }
            }
        }
        return BrainSyncOutcome.Failed("Drive kept changing during sync; will retry")
    }

    private suspend fun refreshPending(pending: PendingMerge, remoteMeta: DriveFileMeta?): BrainSyncOutcome {
        if (remoteMeta != null && remoteMeta.version != pending.remoteVersion) {
            when (val r = download(remoteMeta)) {
                is Downloaded.Ok -> files.writePending(pending.copy(remote = r.state, remoteVersion = remoteMeta.version))
                is Downloaded.Fail -> return r.outcome
            }
        }
        val current = files.readPending() ?: pending
        val conflicts = BrainMerge.analyze(baseRecords(), store.export().toRecords(), current.remote.toRecords()).conflicts.size
        return BrainSyncOutcome.NeedsDecision(conflicts)
    }

    private suspend fun resolveLocked(choice: MergeChoice, resolutions: Map<String, Resolution>): BrainSyncOutcome {
        val pending = files.readPending() ?: return BrainSyncOutcome.UpToDate
        val location = when (val l = locate()) {
            is Located.Ok -> l.location
            is Located.Stop -> return l.outcome
        }
        val remoteMeta = when (val r = drive.findFileMeta(location.syncFolderId, DriveConfig.BRAIN_STATE_FILENAME)) {
            is DriveResult.Success -> r.data
            else -> return failure(r)
        }
        if (remoteMeta != null && remoteMeta.version != pending.remoteVersion) {
            // The other device pushed again; the user must look at the new state.
            return refreshPending(pending, remoteMeta)
        }

        val local = store.export()
        val remoteRecords = pending.remote.toRecords()
        val merged = when (choice) {
            MergeChoice.KEEP_LOCAL -> BrainMerge.whole(local.toRecords(), remoteRecords)
            MergeChoice.KEEP_REMOTE -> BrainMerge.whole(remoteRecords, remoteRecords)
            MergeChoice.SMART -> BrainMerge.resolve(
                BrainMerge.analyze(baseRecords(), local.toRecords(), remoteRecords),
                resolutions,
            )
        }
        store.import(BrainState.fromRecords(merged, local))
        val after = store.export()
        val rev = pending.remote.rev + 1
        val written = writeRemote(location, after, rev) ?: return BrainSyncOutcome.Failed("Could not upload merged Brain")
        files.writeBase(written)
        files.clearPending()
        BackupNotifier.cancelBrainMerge(appContext)
        preferences.setLastBrainSyncAt(System.currentTimeMillis())
        supermarket.exportToDrive()
        return BrainSyncOutcome.Pushed(rev)
    }

    /** Null means Drive moved since it was read; the caller starts over. */
    private suspend fun push(
        location: Location,
        local: BrainState,
        rev: Long,
        expectedVersion: Long?,
    ): BrainSyncOutcome? {
        val now = (drive.findFileMeta(location.syncFolderId, DriveConfig.BRAIN_STATE_FILENAME) as? DriveResult.Success)?.data
        if (now?.version != expectedVersion) return null
        val written = writeRemote(location, local, rev) ?: return BrainSyncOutcome.Failed("Could not upload Brain state")
        files.writeBase(written)
        preferences.setLastBrainSyncAt(System.currentTimeMillis())
        supermarket.exportToDrive()
        return BrainSyncOutcome.Pushed(rev)
    }

    private suspend fun pull(remote: BrainState): BrainSyncOutcome {
        store.import(remote)
        files.writeBase(store.export().copy(rev = remote.rev, writtenBy = remote.writtenBy, writtenAt = remote.writtenAt))
        preferences.setLastBrainSyncAt(System.currentTimeMillis())
        return BrainSyncOutcome.Pulled(remote.rev)
    }

    private suspend fun writeRemote(location: Location, state: BrainState, rev: Long): BrainState? {
        val stamped = state.copy(
            formatVersion = BRAIN_STATE_FORMAT_VERSION,
            rev = rev,
            writtenBy = device(),
            writtenAt = System.currentTimeMillis(),
        )
        val result = drive.writeTextFile(
            location.syncFolderId,
            DriveConfig.BRAIN_STATE_FILENAME,
            BrainStateCodec.encode(stamped),
        )
        return if (result is DriveResult.Success) stamped else null
    }

    private sealed class Downloaded {
        data class Ok(val state: BrainState) : Downloaded()
        data class Fail(val outcome: BrainSyncOutcome) : Downloaded()
    }

    private suspend fun download(meta: DriveFileMeta): Downloaded {
        val text = when (val r = drive.getTextContent(meta.id)) {
            is DriveResult.Success -> r.data
            else -> return Downloaded.Fail(failure(r))
        }
        val state = runCatching { BrainStateCodec.decode(text) }.getOrElse {
            return Downloaded.Fail(BrainSyncOutcome.Failed("Drive Brain state is unreadable"))
        }
        if (state.formatVersion > BRAIN_STATE_FORMAT_VERSION) {
            return Downloaded.Fail(BrainSyncOutcome.Failed("Drive Brain state needs a newer Vector version"))
        }
        return Downloaded.Ok(BrainStateCodec.normalize(state))
    }

    private suspend fun locate(): Located {
        val root = preferences.googleDriveFolderId.first()
        if (root.isNullOrBlank()) return Located.Stop(BrainSyncOutcome.NotConfigured)
        val brain = when (val r = drive.discoverOrCreateBrainFolder(root)) {
            is DriveResult.Success -> r.data
            else -> return Located.Stop(failure(r))
        }
        val sync = when (val r = drive.discoverOrCreateFolder(brain, DriveConfig.BRAIN_SYNC_FOLDER)) {
            is DriveResult.Success -> r.data
            else -> return Located.Stop(failure(r))
        }
        return Located.Ok(Location(sync))
    }

    private fun baseRecords(): RecordSet = files.readBase()?.toRecords().orEmpty()

    private fun hasContent(set: RecordSet): Boolean =
        set.any { (collection, records) -> collection != BrainMerge.META && records.isNotEmpty() }

    private suspend fun device() = SyncDevice(
        deviceId = preferences.installId(),
        name = Build.MODEL ?: "Android",
        platform = "android",
    )

    private fun failure(result: DriveResult<*>): BrainSyncOutcome = when (result) {
        is DriveResult.NeedsRemoteConsent -> BrainSyncOutcome.Failed("Drive needs permission", result.intent)
        is DriveResult.Unauthorized -> BrainSyncOutcome.Failed("Sign in to Google Drive in Settings")
        is DriveResult.OtherError -> BrainSyncOutcome.Failed(result.message)
        else -> BrainSyncOutcome.Failed("Drive unavailable")
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private val LOCK = Mutex()
    }
}
