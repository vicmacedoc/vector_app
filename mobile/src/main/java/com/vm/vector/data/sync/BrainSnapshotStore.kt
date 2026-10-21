package com.vm.vector.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.vm.core.models.BrainAsset
import com.vm.core.models.BrainAssetSyncState
import com.vm.core.models.VectorDatabase
import com.vm.vector.data.BrainFileStore
import com.vm.vector.data.BrainRepository
import com.vm.vector.data.MotionSettings
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.SupermarketRepository
import com.vm.vector.motion.MotionReminderScheduler
import kotlinx.coroutines.flow.first
import java.io.File

/** Converts the whole Brain (Room + Supermarket file + Motion settings) to and from a [BrainState]. */
class BrainSnapshotStore(
    context: Context,
    private val database: VectorDatabase,
) {
    private val appContext = context.applicationContext
    private val preferences = PreferenceManager(appContext)
    private val fileStore = BrainFileStore(appContext, database)
    private val supermarket = SupermarketRepository.create(appContext)

    suspend fun export(): BrainState {
        val assets = database.brainAssetDao().getAllSync()
            .filter { it.syncState != BrainAssetSyncState.DELETE_PENDING }
            .map { it.toSync() }
        val motion = preferences.motionSettings.first()
        return BrainStateCodec.normalize(
            BrainState(
                settings = SyncSettings(motion.dailyTarget, motion.supermarketDay.name),
                nodes = database.brainNodeDao().getAllSync().sortedBy { it.id },
                notes = database.memoryNoteDao().getAllSync().sortedBy { it.id },
                assets = assets.sortedBy { it.id },
                learningTiles = database.learningTileDao().getAllSync().sortedBy { it.id },
                learningLinks = database.learningLinkDao().getAllSync().sortedBy { "${it.fromNodeId}|${it.toNodeId}" },
                tags = database.brainTagDao().getAllSync().sortedBy { it.id },
                tasks = database.motionTaskDao().getAllSync().sortedBy { it.id },
                scoreEvents = database.motionScoreEventDao().getAllSync().sortedBy { it.id },
                supermarket = supermarket.syncSnapshot(),
            )
        )
    }

    suspend fun exportJson(): String = BrainStateCodec.encode(export())

    /**
     * Makes this device match [target]: one Room transaction for the diff against the current export, then the
     * Supermarket file, Motion settings and side effects (search index, reminders, learning statuses, files).
     */
    suspend fun import(target: BrainState) {
        val current = export()
        val removedAssets = mutableListOf<BrainAsset>()
        val changedTaskIds = mutableSetOf<String>()
        val removedTaskIds = mutableSetOf<String>()

        BrainImportGuard.run {
            database.withTransaction {
                val nodeDao = database.brainNodeDao()
                diff(current.nodes, target.nodes, { it.id }) { upserts, removed ->
                    removed.forEach { nodeDao.deleteById(it.id) }
                    if (upserts.isNotEmpty()) nodeDao.insertAll(upserts)
                }
                val noteDao = database.memoryNoteDao()
                diff(current.notes, target.notes, { it.id }) { upserts, removed ->
                    removed.forEach { noteDao.deleteById(it.id) }
                    upserts.forEach { noteDao.insert(it) }
                }
                val tileDao = database.learningTileDao()
                diff(current.learningTiles, target.learningTiles, { it.id }) { upserts, removed ->
                    removed.forEach { tileDao.deleteById(it.id) }
                    upserts.forEach { tileDao.insert(it) }
                }
                val linkDao = database.learningLinkDao()
                diff(current.learningLinks, target.learningLinks, { "${it.fromNodeId}|${it.toNodeId}" }) { upserts, removed ->
                    removed.forEach { linkDao.delete(it.fromNodeId, it.toNodeId) }
                    upserts.forEach {
                        linkDao.delete(it.fromNodeId, it.toNodeId)
                        linkDao.insert(it)
                    }
                }
                val tagDao = database.brainTagDao()
                diff(current.tags, target.tags, { it.id }) { upserts, removed ->
                    removed.forEach { tagDao.deleteById(it.id) }
                    upserts.forEach { tagDao.insert(it) }
                }
                val taskDao = database.motionTaskDao()
                diff(current.tasks, target.tasks, { it.id }) { upserts, removed ->
                    removed.forEach { taskDao.deleteById(it.id) }
                    upserts.forEach { taskDao.insert(it) }
                    changedTaskIds += upserts.map { it.id }
                    removedTaskIds += removed.map { it.id }
                }
                val eventDao = database.motionScoreEventDao()
                diff(current.scoreEvents, target.scoreEvents, { it.id }) { upserts, removed ->
                    removed.forEach { eventDao.deleteById(it.id) }
                    upserts.forEach { eventDao.insert(it) }
                }
                val assetDao = database.brainAssetDao()
                diff(current.assets, target.assets, { it.id }) { upserts, removed ->
                    for (gone in removed) {
                        assetDao.getByIdSync(gone.id)?.let { removedAssets += it }
                        assetDao.deleteById(gone.id)
                    }
                    for (asset in upserts) {
                        val existing = assetDao.getByIdSync(asset.id)
                        assetDao.insert(asset.toLocal(existing))
                    }
                }
            }
        }

        if (!BrainMerge.sameJson(
                BrainStateCodec.json.encodeToJsonElement(SyncSupermarket.serializer(), current.supermarket),
                BrainStateCodec.json.encodeToJsonElement(SyncSupermarket.serializer(), target.supermarket),
            )
        ) {
            supermarket.applySynced(target.supermarket)
        }
        if (current.settings != target.settings) {
            preferences.saveMotionSettings(
                MotionSettings.sanitized(target.settings.motionDailyTarget, target.settings.supermarketDay)
            )
        }

        removedAssets.forEach { fileStore.deleteLocal(it.localRelativePath) }
        removedTaskIds.forEach { MotionReminderScheduler.cancel(appContext, it) }
        val taskDao = database.motionTaskDao()
        changedTaskIds.forEach { id -> taskDao.getByIdSync(id)?.let { MotionReminderScheduler.schedule(appContext, it) } }

        val repository = BrainRepository(database, fileStore)
        runCatching { repository.rebuildFtsIndex() }
        runCatching { repository.refreshLearningStatuses(preferences.learningSettings.first()) }
    }

    private suspend fun <T> diff(
        current: List<T>,
        target: List<T>,
        key: (T) -> String,
        apply: suspend (upserts: List<T>, removed: List<T>) -> Unit,
    ) {
        val before = current.associateBy(key)
        val after = target.associateBy(key)
        val upserts = target.filter { before[key(it)] != it }
        val removed = current.filter { key(it) !in after }
        if (upserts.isNotEmpty() || removed.isNotEmpty()) apply(upserts, removed)
    }

    private fun BrainAsset.toSync(): SyncAsset = SyncAsset(
        id = id,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        driveFileId = driveFileId,
        fileName = fileName,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
        path = localRelativePath.removePrefix("${BrainFileStore.ROOT}/"),
    )

    /** Keeps where this device stores the file; new assets get a path mirroring Drive. */
    private fun SyncAsset.toLocal(existing: BrainAsset?): BrainAsset {
        val localPath = existing?.localRelativePath ?: localPathFor(this)
        val state = when {
            existing != null && existing.driveFileId == driveFileId -> existing.syncState
            driveFileId != null -> BrainAssetSyncState.UPLOADED
            else -> BrainAssetSyncState.FAILED
        }
        return BrainAsset(
            id = id,
            localRelativePath = localPath,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            driveFileId = driveFileId,
            syncState = state,
            fileName = fileName,
            description = description,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun localPathFor(asset: SyncAsset): String {
        val segments = asset.path.split('/')
            .map { it.replace(Regex("[\\\\:*?\"<>|]"), "_").trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        val candidate = if (segments.isEmpty()) {
            "${BrainFileStore.ROOT}/Synced/${asset.id}_${asset.fileName.ifBlank { "file" }}"
        } else {
            "${BrainFileStore.ROOT}/${segments.joinToString("/")}"
        }
        return if (File(appContext.filesDir, candidate).exists()) {
            "${BrainFileStore.ROOT}/Synced/${asset.id}/${candidate.substringAfterLast('/')}"
        } else {
            candidate
        }
    }
}

/**
 * Room reports table changes after the transaction commits, so writes made by sync stay "quiet" for a short
 * window afterwards; the change observer skips scheduling another sync for them.
 */
object BrainImportGuard {
    private const val QUIET_MS = 3_000L

    @Volatile
    private var running = false

    @Volatile
    private var quietUntil = 0L

    val isQuiet: Boolean get() = running || System.currentTimeMillis() < quietUntil

    suspend fun <T> run(block: suspend () -> T): T {
        running = true
        try {
            return block()
        } finally {
            quietUntil = System.currentTimeMillis() + QUIET_MS
            running = false
        }
    }
}
