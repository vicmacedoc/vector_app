package com.vm.vector.data.sync

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.vm.core.models.VectorDatabase
import com.vm.vector.data.DriveFileMeta
import com.vm.vector.data.DriveResult
import com.vm.vector.data.DriveService
import com.vm.vector.data.GoogleDriveAuthManager
import com.vm.vector.data.PreferenceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth

private const val TAG = "ShardSync"

data class ShardSyncReport(
    val ok: Boolean,
    val pushed: Int = 0,
    val pulled: Int = 0,
    /** Changes from the other device folded in silently. */
    val merged: Int = 0,
    /** Shards waiting for a merge decision. */
    val conflicts: List<String> = emptyList(),
    val message: String? = null,
    val consentIntent: Intent? = null,
)

data class ShardMergeView(
    val shard: String,
    val analysis: MergeAnalysis,
    val remoteDevice: SyncDevice?,
    val remoteWrittenAt: Long,
)

/**
 * Keeps the daily shards (Data/sync/config.json, Data/sync/days/YYYY-MM.json) in step with Drive.
 * Protocol: docs/daily-sync-spec.md; mirrors desktop shardEngine.ts.
 */
class ShardSync(
    context: Context,
    database: VectorDatabase,
) {
    private val appContext = context.applicationContext
    private val preferences = PreferenceManager(appContext)
    private val drive = DriveService(appContext, GoogleDriveAuthManager(appContext))
    private val files = ShardSyncFiles(appContext)
    private val store = DailySnapshotStore(appContext, database)

    private data class Folders(val syncId: String, val daysId: String)

    private class Stop(val report: ShardSyncReport) : Exception()

    /** [fullScan] compares every shard with its base even when nothing looks changed. */
    suspend fun sync(fullScan: Boolean = false): ShardSyncReport = LOCK.withLock {
        guard { syncLocked(fullScan) }
    }

    suspend fun loadPending(shard: String): ShardMergeView? = LOCK.withLock {
        val pending = files.readPending(shard) ?: return@withLock null
        ShardMergeView(shard, analysis(shard, pending.remote), pending.remote.writtenBy, pending.remote.writtenAt)
    }

    suspend fun resolvePending(
        shard: String,
        choice: MergeChoice,
        resolutions: Map<String, Resolution> = emptyMap(),
    ): ShardSyncReport = LOCK.withLock {
        guard { resolveLocked(shard, choice, resolutions) }
    }

    private suspend fun guard(block: suspend () -> ShardSyncReport): ShardSyncReport =
        try {
            block()
        } catch (stop: Stop) {
            stop.report
        } catch (e: Exception) {
            Log.w(TAG, "Daily sync failed", e)
            ShardSyncReport(ok = false, message = e.message ?: "Calendar sync failed")
        }

    private suspend fun syncLocked(fullScan: Boolean): ShardSyncReport {
        val folders = folders() ?: return ShardSyncReport(ok = false, message = "Choose a Drive folder in Settings")
        val index = remoteIndex(folders)
        val dirty = files.dirty()
        val recent = recentShards()
        val shards = (store.localShards() + index.keys + dirty.keys + files.pendingShards()).toSortedSet()
        var report = ShardSyncReport(ok = true)
        for (shard in shards) {
            val base = files.readBase(shard)
            val meta = index[shard]
            val remoteMoved = if (meta != null) meta.version != base?.driveVersion else base?.driveVersion != null
            val quiet = shard !in dirty && shard !in recent && base != null && !remoteMoved && files.readPending(shard) == null
            if (!fullScan && quiet) continue
            report = syncShard(shard, folders, meta, report)
        }
        files.refreshPendingFlag()
        return report
    }

    private suspend fun syncShard(
        shard: String,
        folders: Folders,
        firstMeta: DriveFileMeta?,
        report: ShardSyncReport,
    ): ShardSyncReport {
        val parentId = parentOf(shard, folders)
        var meta = firstMeta
        repeat(MAX_ATTEMPTS) {
            val pending = files.readPending(shard)
            if (pending != null) {
                refreshPending(shard, pending, meta)
                return report.copy(conflicts = report.conflicts + shard)
            }
            val dirtyAt = files.dirty()[shard]
            val base = files.readBase(shard)
            val localSet = store.export(shard)
            val remote = meta?.let { m -> if (m.version == base?.driveVersion) base.file else download(m) }

            val localChanged = if (base != null) BrainMerge.canonical(localSet) != BrainMerge.canonical(base.file.recordSet())
            else !BrainMerge.isEmpty(localSet)
            val remoteChanged = remote != null && (base == null || remote.rev != base.file.rev)

            fun clean() { if (dirtyAt != null) files.clearDirty(shard, dirtyAt) }

            if (remote == null || meta == null) {
                if (localChanged) {
                    if (!push(shard, parentId, localSet, (base?.file?.rev ?: 0L) + 1, expectedVersion = null)) {
                        meta = findMeta(parentId, shard); return@repeat
                    }
                    clean()
                    return report.copy(pushed = report.pushed + 1)
                }
                clean()
                return report
            }
            val m = meta!!
            when {
                !localChanged && !remoteChanged -> {
                    clean()
                    if (base != null && base.driveVersion != m.version) files.writeBase(shard, base.copy(driveVersion = m.version))
                    return report
                }
                localChanged && !remoteChanged -> {
                    if (!push(shard, parentId, localSet, remote.rev + 1, m.version)) {
                        meta = findMeta(parentId, shard); return@repeat
                    }
                    clean()
                    return report.copy(pushed = report.pushed + 1)
                }
                !localChanged -> {
                    if (!replace(shard, localSet, remote.recordSet())) return@repeat
                    files.writeBase(shard, ShardBase(remote, m.version))
                    return report.copy(pulled = report.pulled + 1)
                }
                BrainMerge.canonical(localSet) == BrainMerge.canonical(remote.recordSet()) -> {
                    files.writeBase(shard, ShardBase(remote, m.version))
                    clean()
                    return report
                }
            }
            val analysis = BrainMerge.analyze(
                base?.file?.recordSet().orEmpty(),
                localSet,
                remote.recordSet(),
                DailyShards.POLICY,
                SideStamps(local = dirtyAt ?: System.currentTimeMillis(), remote = remote.writtenAt),
            )
            if (analysis.conflicts.isNotEmpty()) {
                files.writePending(shard, ShardPending(remote, m.version, System.currentTimeMillis()))
                return report.copy(conflicts = report.conflicts + shard)
            }
            val merged = BrainMerge.resolve(analysis, emptyMap())
            if (!replace(shard, localSet, merged)) return@repeat
            if (!push(shard, parentId, store.export(shard), remote.rev + 1, m.version)) {
                meta = findMeta(parentId, shard); return@repeat
            }
            clean()
            return report.copy(merged = report.merged + analysis.remoteChanges, pushed = report.pushed + 1)
        }
        throw IllegalStateException("Drive kept changing during sync; will retry")
    }

    private suspend fun refreshPending(shard: String, pending: ShardPending, meta: DriveFileMeta?) {
        if (meta != null && meta.version != pending.remoteVersion) {
            files.writePending(shard, pending.copy(remote = download(meta), remoteVersion = meta.version))
        }
    }

    private suspend fun analysis(shard: String, remote: ShardFile): MergeAnalysis =
        BrainMerge.analyze(
            files.readBase(shard)?.file?.recordSet().orEmpty(),
            store.export(shard),
            remote.recordSet(),
            DailyShards.POLICY,
            SideStamps(local = files.dirty()[shard] ?: 0L, remote = remote.writtenAt),
        )

    private suspend fun resolveLocked(
        shard: String,
        choice: MergeChoice,
        resolutions: Map<String, Resolution>,
    ): ShardSyncReport {
        val pending = files.readPending(shard) ?: return ShardSyncReport(ok = true)
        val folders = folders() ?: return ShardSyncReport(ok = false, message = "Choose a Drive folder in Settings")
        val parentId = parentOf(shard, folders)
        val meta = findMeta(parentId, shard)
        if (meta != null && meta.version != pending.remoteVersion) {
            refreshPending(shard, pending, meta)
            return ShardSyncReport(ok = true, conflicts = listOf(shard))
        }
        val local = store.export(shard)
        val remoteSet = pending.remote.recordSet()
        val merged = when (choice) {
            MergeChoice.KEEP_LOCAL -> BrainMerge.whole(local, remoteSet, DailyShards.POLICY)
            MergeChoice.KEEP_REMOTE -> BrainMerge.whole(remoteSet, remoteSet, DailyShards.POLICY)
            MergeChoice.SMART -> BrainMerge.resolve(analysis(shard, pending.remote), resolutions)
        }
        val dirtyAt = files.dirty()[shard]
        store.import(shard, merged)
        write(shard, parentId, store.export(shard), pending.remote.rev + 1)
        files.clearPending(shard)
        if (dirtyAt != null) files.clearDirty(shard, dirtyAt)
        return ShardSyncReport(ok = true, pushed = 1)
    }

    /** Imports [next] unless the shard changed since [expected] was exported; false means try again. */
    private suspend fun replace(shard: String, expected: RecordSet, next: RecordSet): Boolean {
        if (BrainMerge.canonical(store.export(shard)) != BrainMerge.canonical(expected)) return false
        store.import(shard, next)
        return true
    }

    /** False when Drive moved since it was read. */
    private suspend fun push(shard: String, parentId: String, records: RecordSet, rev: Long, expectedVersion: Long?): Boolean {
        if (findMeta(parentId, shard)?.version != expectedVersion) return false
        write(shard, parentId, records, rev)
        return true
    }

    private suspend fun write(shard: String, parentId: String, records: RecordSet, rev: Long) {
        val file = ShardFile(
            formatVersion = SHARD_FORMAT_VERSION,
            shard = shard,
            rev = rev,
            writtenBy = device(),
            writtenAt = System.currentTimeMillis(),
            records = records.filterValues { it.isNotEmpty() },
        )
        val written = unwrap(drive.writeTextFile(parentId, DailyShards.fileName(shard), ShardFile.encode(file)))
        files.writeBase(shard, ShardBase(file, written.version))
    }

    private suspend fun download(meta: DriveFileMeta): ShardFile {
        val text = unwrap(drive.getTextContent(meta.id))
        val file = runCatching { ShardFile.decode(text) }.getOrElse { throw IllegalStateException("A Drive calendar shard is unreadable") }
        if (file.formatVersion > SHARD_FORMAT_VERSION) throw IllegalStateException("Drive calendar data needs a newer Vector")
        return file
    }

    private suspend fun findMeta(parentId: String, shard: String): DriveFileMeta? =
        unwrap(drive.findFileMeta(parentId, DailyShards.fileName(shard)))

    private suspend fun folders(): Folders? {
        val root = preferences.googleDriveFolderId.first()
        if (root.isNullOrBlank()) return null
        val syncId = unwrap(drive.discoverOrCreateFolder(root, SYNC_FOLDER))
        val daysId = unwrap(drive.discoverOrCreateFolder(syncId, DAYS_FOLDER))
        return Folders(syncId, daysId)
    }

    private suspend fun remoteIndex(folders: Folders): Map<String, DriveFileMeta> {
        val out = mutableMapOf<String, DriveFileMeta>()
        unwrap(drive.listChildrenMeta(folders.syncId)).forEach { f ->
            DailyShards.shardForFileName(f.name, inDays = false)?.let { out[it] = f }
        }
        unwrap(drive.listChildrenMeta(folders.daysId)).forEach { f ->
            DailyShards.shardForFileName(f.name, inDays = true)?.let { out[it] = f }
        }
        return out
    }

    private fun parentOf(shard: String, folders: Folders) =
        if (shard == DailyShards.CONFIG) folders.syncId else folders.daysId

    /** Edits here are the likeliest to have been made without an explicit dirty mark. */
    private fun recentShards(): Set<String> {
        val now = YearMonth.now()
        return setOf(DailyShards.CONFIG) + (-1L..1L).map { DailyShards.monthShard("${now.plusMonths(it)}-01") }
    }

    private suspend fun device() = SyncDevice(
        deviceId = preferences.installId(),
        name = Build.MODEL ?: "Android",
        platform = "android",
    )

    private fun <T> unwrap(result: DriveResult<T>): T = when (result) {
        is DriveResult.Success -> result.data
        is DriveResult.NeedsRemoteConsent -> throw Stop(ShardSyncReport(ok = false, message = "Drive needs permission", consentIntent = result.intent))
        is DriveResult.Unauthorized -> throw Stop(ShardSyncReport(ok = false, message = "Sign in to Google Drive in Settings"))
        is DriveResult.OtherError -> throw Stop(ShardSyncReport(ok = false, message = result.message))
        else -> throw Stop(ShardSyncReport(ok = false, message = "Drive unavailable"))
    }

    companion object {
        const val SYNC_FOLDER = "sync"
        const val DAYS_FOLDER = "days"
        private const val MAX_ATTEMPTS = 3
        private val LOCK = Mutex()
    }
}
