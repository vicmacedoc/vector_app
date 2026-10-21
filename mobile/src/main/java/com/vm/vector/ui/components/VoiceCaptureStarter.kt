package com.vm.vector.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.vm.vector.audio.VoiceCaptureController

/**
 * Returns a launcher that starts a Brain voice quick capture, asking for microphone
 * (and, on Android 13+, notification) permission first when needed. The recording
 * files into the memory tile [nodeId], or the Inbox when null.
 */
@Composable
fun rememberVoiceCaptureStarter(onMicDenied: () -> Unit, nodeId: String? = null): () -> Unit {
    val context = LocalContext.current
    val currentOnMicDenied by rememberUpdatedState(onMicDenied)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO]
            ?: context.isGranted(Manifest.permission.RECORD_AUDIO)
        if (micGranted) VoiceCaptureController.start(context, nodeId) else currentOnMicDenied()
    }
    return remember(context, launcher, nodeId) {
        {
            val missing = buildList {
                if (!context.isGranted(Manifest.permission.RECORD_AUDIO)) {
                    add(Manifest.permission.RECORD_AUDIO)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !context.isGranted(Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (missing.isEmpty()) {
                VoiceCaptureController.start(context, nodeId)
            } else {
                launcher.launch(missing.toTypedArray())
            }
        }
    }
}

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
