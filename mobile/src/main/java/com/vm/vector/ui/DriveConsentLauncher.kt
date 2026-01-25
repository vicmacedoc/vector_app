package com.vm.vector.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.vm.vector.data.DriveConsentHandler
import kotlinx.coroutines.flow.collect

/**
 * Handles [UserRecoverableAuthIOException] from Drive API: when [DriveConsentHandler] has a
 * pending intent, launches it via [ActivityResultContracts.StartActivityForResult] to trigger
 * the system consent dialog. On RESULT_OK, invokes the stored retry; otherwise clears pending.
 */
@Composable
fun DriveConsentLauncher() {
    val context = LocalContext.current
    val activity = context as? Activity
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            DriveConsentHandler.getRetryAndClear()?.invoke()
        } else {
            DriveConsentHandler.clearPending()
        }
    }
    LaunchedEffect(Unit) {
        DriveConsentHandler.pendingIntent.collect { intent ->
            if (intent != null && activity != null) {
                DriveConsentHandler.consumeIntent()
                launcher.launch(intent)
            }
        }
    }
}
