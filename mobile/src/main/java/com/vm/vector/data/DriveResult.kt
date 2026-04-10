package com.vm.vector.data

import android.content.Intent
import kotlinx.serialization.Serializable

/**
 * Result of a Drive API operation. Used for UI states (Configuration Error, Create lists folder).
 */
sealed class DriveResult<out T> {
    data class Success<T>(val data: T) : DriveResult<T>()
    object Unauthorized : DriveResult<Nothing>()
    /** Invalid or missing Root ID (e.g. not set in DataStore, or 404 from Drive). */
    object ConfigurationError : DriveResult<Nothing>()
    /** Root valid but `lists/` subfolder does not exist. */
    object ListsFolderNotFound : DriveResult<Nothing>()
    /** NeedRemoteConsent: launch [intent] via ActivityResultLauncher, then retry. */
    data class NeedsRemoteConsent(val intent: Intent) : DriveResult<Nothing>()
    data class OtherError(val message: String) : DriveResult<Nothing>()
}

@Serializable
data class DriveFileInfo(val id: String, val name: String)

/** File row from Drive list (images / folders) with metadata for diary sync. */
data class DriveListedFile(
    val id: String,
    val name: String,
    val mimeType: String?,
    val modifiedTimeRfc3339: String?,
)
