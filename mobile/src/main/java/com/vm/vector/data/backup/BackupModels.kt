package com.vm.vector.data.backup

import kotlinx.serialization.Serializable

/** Written next to every database backup on Drive. */
@Serializable
data class BackupManifest(
    val installId: String,
    val deviceName: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val sha256: String,
    val sizeBytes: Long,
    val rowCounts: Map<String, Long> = emptyMap(),
) {
    val totalRows: Long get() = rowCounts.values.sum()
}

/** Why an automatic backup was held back instead of overwriting Drive's latest copy. */
@Serializable
enum class BackupConflictReason {
    /** Another install wrote the latest backup after this install's last successful backup. */
    OTHER_INSTALL_NEWER,
    /** This device's database is much smaller than the latest backup (e.g. empty after a reinstall). */
    LOCAL_MUCH_SMALLER,
}

@Serializable
data class BackupConflict(
    val reason: BackupConflictReason,
    val remote: BackupManifest?,
    val remoteSizeBytes: Long?,
    val local: BackupManifest,
    val detectedAt: Long,
)

/** A restorable backup listed in Settings. [driveFileId] points at the database file (gzip for dated ones). */
data class BackupSnapshotInfo(
    val driveFileId: String,
    val name: String,
    val createdAt: Long,
    val sizeBytes: Long?,
    val gzipped: Boolean,
    val isLatest: Boolean,
)

sealed class BackupOutcome {
    object Success : BackupOutcome()
    data class Conflict(val conflict: BackupConflict) : BackupOutcome()
    data class Failure(val message: String) : BackupOutcome()
}
