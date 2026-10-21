package com.vm.vector.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import com.vm.core.models.VectorDatabase
import com.vm.vector.data.backup.BackupConflict
import com.vm.vector.data.backup.BackupConflictReason
import com.vm.vector.data.backup.BackupManifest
import com.vm.vector.data.backup.BackupNotifier
import com.vm.vector.data.backup.BackupOutcome
import com.vm.vector.data.backup.BackupRetention
import com.vm.vector.data.backup.BackupSnapshotInfo
import com.vm.vector.data.sync.BrainSnapshotStore
import com.vm.vector.data.sync.BrainSyncFiles
import com.vm.vector.data.sync.ShardSyncFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val TAG = "DatabaseBackup"

/**
 * Backs up the Room database to Google Drive and restores it.
 *
 * - "Latest" copy: `Data/database/vector_database.db` + manifest, written by the debounced job and the
 *   Settings button. Guarded so an empty or foreign database never silently replaces a good backup.
 * - Dated history: `Data/backups/db|brain|settings/`, written by the daily job and pruned with [BackupRetention].
 */
class DatabaseBackupManager(
    context: Context,
    private val database: VectorDatabase,
    private val driveService: DriveService,
    private val preferenceManager: PreferenceManager,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val workDir: File get() = File(appContext.cacheDir, "backup").also { it.mkdirs() }

    private class Snapshot(val file: File, val manifest: BackupManifest)

    /** Writes Drive's latest copy. [force] skips the overwrite guard (used after the user picked "Overwrite"). */
    suspend fun backupLatest(force: Boolean = false): BackupOutcome = lock.withLock {
        withSnapshot { root, snapshot -> writeLatest(root, snapshot, force) }
    }

    /** Daily job: dated snapshot + Brain state + settings, retention pruning, then the guarded latest copy. */
    suspend fun backupDaily(): BackupOutcome = lock.withLock {
        withSnapshot { root, snapshot ->
            writeDated(root, snapshot)?.let { return@withSnapshot BackupOutcome.Failure(it) }
            writeLatest(root, snapshot, force = false)
        }
    }

    /** Settings "Keep both": archives Drive's current latest copy under backups/db, then overwrites it. */
    suspend fun keepBothThenOverwrite(): BackupOutcome = lock.withLock {
        withSnapshot { root, snapshot ->
            val dbFolder = folder(root, DriveConfig.DATABASE_SUBFOLDER_NAME) ?: return@withSnapshot fail("Drive database folder unavailable")
            val archiveFolder = path(root, "${DriveConfig.BACKUPS_SUBFOLDER_NAME}/${DriveConfig.BACKUPS_DB_FOLDER}")
                ?: return@withSnapshot fail("Drive backups folder unavailable")
            val remote = (driveService.findFileMeta(dbFolder, DriveConfig.DATABASE_FILENAME) as? DriveResult.Success)?.data
            if (remote != null) {
                val stamp = BackupRetention.stamp(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(remote.modifiedTimeMillis), ZoneId.systemDefault()))
                val archive = File(workDir, "archive.db")
                if (driveService.downloadToFile(remote.id, archive) !is DriveResult.Success) {
                    return@withSnapshot fail("Could not download the existing backup")
                }
                val gz = gzip(archive, File(workDir, "archive.db.gz"))
                val result = driveService.uploadOrReplaceFile(archiveFolder, "vector_db_${stamp}_kept.db.gz", GZIP_MIME, gz)
                archive.delete()
                gz.delete()
                if (result !is DriveResult.Success) return@withSnapshot fail("Could not archive the existing backup")
            }
            writeLatest(root, snapshot, force = true)
        }
    }

    suspend fun listSnapshots(): Result<List<BackupSnapshotInfo>> = withContext(Dispatchers.IO) {
        val root = rootId() ?: return@withContext Result.failure(Exception("Drive folder ID not configured"))
        val out = mutableListOf<BackupSnapshotInfo>()
        folder(root, DriveConfig.DATABASE_SUBFOLDER_NAME)?.let { dbFolder ->
            (driveService.findFileMeta(dbFolder, DriveConfig.DATABASE_FILENAME) as? DriveResult.Success)?.data?.let {
                out += BackupSnapshotInfo(it.id, "Latest", it.modifiedTimeMillis, it.sizeBytes, gzipped = false, isLatest = true)
            }
        }
        val datedFolder = path(root, "${DriveConfig.BACKUPS_SUBFOLDER_NAME}/${DriveConfig.BACKUPS_DB_FOLDER}")
        if (datedFolder != null) {
            val children = (driveService.listChildrenWithMetadata(datedFolder) as? DriveResult.Success)?.data.orEmpty()
            children.filter { it.name.endsWith(".db.gz") }.forEach { f ->
                val time = BackupRetention.parseStamp(f.name) ?: return@forEach
                out += BackupSnapshotInfo(
                    driveFileId = f.id,
                    name = f.name.removeSuffix(".db.gz"),
                    createdAt = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    sizeBytes = f.sizeBytes,
                    gzipped = true,
                    isLatest = false,
                )
            }
        }
        Result.success(out.sortedWith(compareByDescending<BackupSnapshotInfo> { it.isLatest }.thenByDescending { it.createdAt }))
    }

    /**
     * Downloads [snapshot] (or the latest copy when null), checks it, and swaps it in for the local database.
     * Room is closed afterwards: the caller must restart the process (see [com.vm.vector.AppRestarter]).
     */
    suspend fun restore(snapshot: BackupSnapshotInfo? = null): Result<Unit> = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val target = snapshot ?: listSnapshots().getOrThrow().firstOrNull { it.isLatest }
                    ?: error("No database backup found in Drive")
                val download = File(workDir, "restore.download").apply { delete() }
                when (driveService.downloadToFile(target.driveFileId, download)) {
                    is DriveResult.Success -> Unit
                    is DriveResult.NeedsRemoteConsent -> error("Needs remote consent. Please authorize Drive in Settings.")
                    else -> error("Download failed")
                }
                val candidate = if (target.gzipped) gunzip(download, File(workDir, "restore.db")) else download
                val currentVersion = database.openHelper.readableDatabase.version
                val version = validate(candidate)
                check(version in 1..currentVersion) {
                    "Backup schema v$version is newer than this app (v$currentVersion). Update the app first."
                }
                database.close()
                val dbFile = appContext.getDatabasePath(DB_NAME)
                candidate.copyTo(dbFile, overwrite = true)
                listOf("-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
                download.delete()
                candidate.delete()
                preferenceManager.setLastDbBackupAt(maxOf(target.createdAt, System.currentTimeMillis()))
                preferenceManager.setBackupConflict(null)
                BrainSyncFiles(appContext).clearBase()
                ShardSyncFiles(appContext).clearAll()
            }
        }
    }

    private suspend fun withSnapshot(block: suspend (root: String, snapshot: Snapshot) -> BackupOutcome): BackupOutcome =
        withContext(Dispatchers.IO) {
            val root = rootId() ?: return@withContext fail("Drive folder ID not configured")
            val snapshot = try {
                createSnapshot()
            } catch (e: Exception) {
                Log.e(TAG, "Snapshot failed", e)
                return@withContext fail("Could not snapshot the database: ${e.message}")
            }
            try {
                block(root, snapshot)
            } catch (e: Exception) {
                Log.e(TAG, "Backup failed", e)
                fail(e.message ?: "Backup failed")
            } finally {
                snapshot.file.delete()
            }
        }

    private suspend fun writeLatest(root: String, snapshot: Snapshot, force: Boolean): BackupOutcome {
        val dbFolder = folder(root, DriveConfig.DATABASE_SUBFOLDER_NAME) ?: return fail("Drive database folder unavailable")
        if (!force) {
            detectConflict(dbFolder, snapshot.manifest)?.let { conflict ->
                preferenceManager.setBackupConflict(json.encodeToString(conflict))
                BackupNotifier.notifyConflict(appContext)
                return BackupOutcome.Conflict(conflict)
            }
        }
        when (val r = driveService.uploadOrReplaceFile(dbFolder, DriveConfig.DATABASE_FILENAME, SQLITE_MIME, snapshot.file)) {
            is DriveResult.Success -> Unit
            else -> return fail(describe(r))
        }
        val manifestResult = driveService.writeTextFile(
            dbFolder, DriveConfig.DATABASE_MANIFEST_FILENAME, json.encodeToString(snapshot.manifest),
        )
        if (manifestResult !is DriveResult.Success) return fail(describe(manifestResult))
        preferenceManager.setLastDbBackupAt(snapshot.manifest.createdAt)
        preferenceManager.setBackupConflict(null)
        BackupNotifier.cancelConflict(appContext)
        return BackupOutcome.Success
    }

    /** Uploads the dated database/brain/settings copies and prunes. Returns an error message or null. */
    private suspend fun writeDated(root: String, snapshot: Snapshot): String? {
        val backups = "${DriveConfig.BACKUPS_SUBFOLDER_NAME}/"
        val dbDir = path(root, backups + DriveConfig.BACKUPS_DB_FOLDER) ?: return "Drive backups folder unavailable"
        val now = LocalDateTime.now()
        val base = "vector_db_${BackupRetention.stamp(now)}_v${snapshot.manifest.schemaVersion}"
        val gz = gzip(snapshot.file, File(workDir, "$base.db.gz"))
        try {
            val upload = driveService.uploadOrReplaceFile(dbDir, gz.name, GZIP_MIME, gz)
            if (upload !is DriveResult.Success) return describe(upload)
        } finally {
            gz.delete()
        }
        driveService.writeTextFile(dbDir, "$base.manifest.json", json.encodeToString(snapshot.manifest))

        val today = LocalDate.now().toString()
        path(root, backups + DriveConfig.BACKUPS_BRAIN_FOLDER)?.let { brainDir ->
            runCatching { BrainSnapshotStore(appContext, database).exportJson() }
                .onSuccess { driveService.writeTextFile(brainDir, "state_$today.json", it) }
                .onFailure { Log.w(TAG, "Brain state export failed", it) }
            prune(brainDir)
        }
        path(root, backups + DriveConfig.BACKUPS_SETTINGS_FOLDER)?.let { settingsDir ->
            driveService.writeTextFile(settingsDir, "settings_$today.json", json.encodeToString(preferenceManager.exportSettings()))
            prune(settingsDir)
        }
        prune(dbDir)
        return null
    }

    private suspend fun detectConflict(dbFolder: String, local: BackupManifest): BackupConflict? {
        val remoteDb = (driveService.findFileMeta(dbFolder, DriveConfig.DATABASE_FILENAME) as? DriveResult.Success)?.data
            ?: return null
        val manifestMeta = (driveService.findFileMeta(dbFolder, DriveConfig.DATABASE_MANIFEST_FILENAME) as? DriveResult.Success)?.data
        val remote = manifestMeta?.let { meta ->
            (driveService.getTextContent(meta.id) as? DriveResult.Success)?.data
                ?.let { runCatching { json.decodeFromString<BackupManifest>(it) }.getOrNull() }
        }
        val lastBackupAt = preferenceManager.lastDbBackupAt()
        val reason = when {
            remote != null && remote.installId != local.installId && remote.createdAt > lastBackupAt ->
                BackupConflictReason.OTHER_INSTALL_NEWER
            remote != null && isMuchSmaller(local.totalRows, remote.totalRows) ->
                BackupConflictReason.LOCAL_MUCH_SMALLER
            remote == null && lastBackupAt == 0L && isMuchSmallerBytes(local.sizeBytes, remoteDb.sizeBytes ?: 0L) ->
                BackupConflictReason.LOCAL_MUCH_SMALLER
            else -> return null
        }
        return BackupConflict(reason, remote, remoteDb.sizeBytes, local, System.currentTimeMillis())
    }

    private suspend fun prune(folderId: String) {
        val files = (driveService.listChildrenWithMetadata(folderId) as? DriveResult.Success)?.data ?: return
        val stamped = files.filterNot { it.name.contains("_kept") }
            .mapNotNull { f -> BackupRetention.parseStamp(f.name)?.let { f to it } }
        val keepTimes = BackupRetention.keep(stamped.map { it.second }.distinct(), timeOf = { it })
        stamped.filter { it.second !in keepTimes }.forEach { (file, _) -> driveService.deleteFile(file.id) }
    }

    private suspend fun createSnapshot(): Snapshot {
        val out = File(workDir, "snapshot.db")
        listOf("", "-wal", "-shm", "-journal").forEach { File(out.path + it).delete() }
        val db = database.openHelper.writableDatabase
        try {
            db.execSQL("VACUUM INTO '${out.absolutePath.replace("'", "''")}'")
        } catch (e: Exception) {
            Log.w(TAG, "VACUUM INTO failed, falling back to a checkpointed file copy", e)
            runCatching { db.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() } }
            appContext.getDatabasePath(DB_NAME).copyTo(out, overwrite = true)
        }
        val (version, counts) = inspect(out)
        val manifest = BackupManifest(
            installId = preferenceManager.installId(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            schemaVersion = version,
            createdAt = System.currentTimeMillis(),
            sha256 = sha256(out),
            sizeBytes = out.length(),
            rowCounts = counts,
        )
        return Snapshot(out, manifest)
    }

    /** Opens a standalone copy and returns (user_version, row count per table). */
    private fun inspect(file: File): Pair<Int, Map<String, Long>> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val tables = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' " +
                    "AND name NOT LIKE 'android_%' AND name NOT LIKE 'room_%' AND name NOT LIKE '%fts%'",
                null,
            ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            val counts = tables.associateWith { table ->
                db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
            }
            db.version to counts.toSortedMap()
        }.also { listOf("-wal", "-shm", "-journal").forEach { File(file.path + it).delete() } }

    /** Returns the schema version after `PRAGMA integrity_check` passes. */
    private fun validate(file: File): Int =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val result = db.rawQuery("PRAGMA integrity_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            check(result == "ok") { "Backup failed integrity check: ${result ?: "unreadable"}" }
            db.version
        }.also { listOf("-wal", "-shm", "-journal").forEach { File(file.path + it).delete() } }

    private suspend fun rootId(): String? = preferenceManager.googleDriveFolderId.first()?.takeIf { it.isNotBlank() }

    private suspend fun folder(parent: String, name: String): String? =
        (driveService.discoverOrCreateFolder(parent, name) as? DriveResult.Success)?.data

    private suspend fun path(root: String, relative: String): String? =
        (driveService.discoverOrCreatePath(root, relative) as? DriveResult.Success)?.data

    private fun fail(message: String) = BackupOutcome.Failure(message)

    private fun describe(result: DriveResult<*>): String = when (result) {
        is DriveResult.NeedsRemoteConsent -> "Needs remote consent. Please authorize Drive in Settings."
        is DriveResult.Unauthorized -> "Drive access unauthorized"
        is DriveResult.ConfigurationError, is DriveResult.ListsFolderNotFound -> "Drive folder not found or invalid"
        is DriveResult.OtherError -> result.message
        is DriveResult.Success -> "OK"
    }

    companion object {
        const val DB_NAME = "vector_database"
        private const val SQLITE_MIME = "application/x-sqlite3"
        private const val GZIP_MIME = "application/gzip"

        /** Shared by every instance so the debounced, daily and manual backups never overlap. */
        private val lock = Mutex()

        internal fun isMuchSmaller(localRows: Long, remoteRows: Long): Boolean =
            remoteRows >= 50 && localRows * 2 < remoteRows

        internal fun isMuchSmallerBytes(localBytes: Long, remoteBytes: Long): Boolean =
            remoteBytes - localBytes > 256 * 1024 && localBytes * 2 < remoteBytes

        private fun gzip(source: File, dest: File): File {
            GZIPOutputStream(dest.outputStream().buffered()).use { out -> source.inputStream().use { it.copyTo(out) } }
            return dest
        }

        private fun gunzip(source: File, dest: File): File {
            GZIPInputStream(source.inputStream().buffered()).use { input -> dest.outputStream().use { input.copyTo(it) } }
            return dest
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
