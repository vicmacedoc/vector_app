package com.vm.vector.data

import android.content.Intent

/** Result of "Validate Connection": root reachable and the brain/database/backups folders exist. */
sealed class ValidateConnectionResult {
    data class Success(val brainFolderId: String, val backupsFolderId: String) : ValidateConnectionResult()
    object Unauthorized : ValidateConnectionResult()
    /** Root ID not accessible (missing or 404). */
    object RootInaccessible : ValidateConnectionResult()
    /** NeedRemoteConsent: launch [intent] via ActivityResultLauncher, then retry. */
    data class NeedsRemoteConsent(val intent: Intent) : ValidateConnectionResult()
    data class OtherError(val message: String) : ValidateConnectionResult()
}
