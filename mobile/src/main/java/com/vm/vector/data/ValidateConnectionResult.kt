package com.vm.vector.data

import android.content.Intent

/** Result of "Validate Connection". Logs lists/presets IDs to Logcat. */
sealed class ValidateConnectionResult {
    data class Success(val listsId: String, val presetsId: String) : ValidateConnectionResult()
    object Unauthorized : ValidateConnectionResult()
    /** Root ID not accessible (missing or 404). */
    object RootInaccessible : ValidateConnectionResult()
    /** 'lists' folder not found under root. */
    object ListsNotFound : ValidateConnectionResult()
    /** 'presets' folder not found under root. */
    object PresetsNotFound : ValidateConnectionResult()
    /** NeedRemoteConsent: launch [intent] via ActivityResultLauncher, then retry. */
    data class NeedsRemoteConsent(val intent: Intent) : ValidateConnectionResult()
    data class OtherError(val message: String) : ValidateConnectionResult()
}
