package com.vm.vector.ui.bodyscan

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureWhite
import com.vm.vector.util.applyExifOrientationToJpegBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "BodyScanCapture"

private val driveAngles = listOf("front", "left", "back", "right")
private val angleLabels = listOf("Front", "Left", "Back", "Right")

@Composable
fun BodyScanCaptureOverlay(
    waitSeconds: IntArray,
    onDismiss: () -> Unit,
    onError: (String) -> Unit,
    uploadJpeg: suspend (driveFolderName: String, bytes: ByteArray) -> Result<Unit>,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor: Executor = remember { ContextCompat.getMainExecutor(context) }

    var statusLine by remember { mutableStateOf("Starting camera…") }
    var secondsLeftInCountdown by remember { mutableIntStateOf(-1) }

    val beeper = remember { ScanBeeper() }

    DisposableEffect(Unit) {
        onDispose { beeper.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            var previewView by remember { mutableStateOf<PreviewView?>(null) }

            AndroidView(
                factory = { ctx -> PreviewView(ctx).also { previewView = it } },
                modifier = Modifier.fillMaxSize()
            )

            LaunchedEffect(previewView) {
                val pv = previewView ?: return@LaunchedEffect
                var provider: ProcessCameraProvider? = null
                try {
                    provider = withContext(Dispatchers.IO) {
                        ProcessCameraProvider.getInstance(context).get()
                    }
                    val cameraProvider = provider!!
                    val preview = Preview.Builder().build()
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    withContext(Dispatchers.Main) {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                        preview.setSurfaceProvider(pv.surfaceProvider)
                        pv.display?.rotation?.let { imageCapture.targetRotation = it }
                    }

                    awaitPreviewStreaming(pv)

                    if (waitSeconds.size != 4) {
                        onError("Expected 4 timers")
                        onDismiss()
                        return@LaunchedEffect
                    }

                    val captured = ArrayList<Pair<String, ByteArray>>(4)
                    val scanDir = File(context.filesDir, "body_scan").apply { mkdirs() }

                    for (i in 0 until 4) {
                        val angleKey = driveAngles[i]
                        val label = angleLabels[i]
                        val wait = waitSeconds[i].coerceIn(0, 600)
                        statusLine = "$label — get ready"
                        secondsLeftInCountdown = wait
                        if (wait > 0) {
                            repeat(wait) { tick ->
                                ensureActive()
                                secondsLeftInCountdown = wait - tick
                                withContext(Dispatchers.Default) { beeper.tick() }
                                delay(1000L)
                            }
                        }
                        secondsLeftInCountdown = -1
                        if (!isActive) return@LaunchedEffect
                        statusLine = "Capturing $label…"
                        withContext(Dispatchers.Default) { beeper.doubleBeep() }
                        pv.display?.rotation?.let { imageCapture.targetRotation = it }
                        val rawBytes = captureJpegToBytes(scanDir, angleKey, imageCapture, mainExecutor)
                        if (rawBytes.isEmpty()) {
                            onError("Capture produced an empty $label photo")
                            onDismiss()
                            return@LaunchedEffect
                        }
                        val jpegBytes = withContext(Dispatchers.Default) {
                            applyExifOrientationToJpegBytes(rawBytes) ?: rawBytes
                        }
                        File(scanDir, "${angleKey}_${System.currentTimeMillis()}.jpg").writeBytes(jpegBytes)
                        captured.add(angleKey to jpegBytes)
                    }

                    withContext(Dispatchers.Default) { beeper.finalBeep() }
                    statusLine = "Saving photos…"
                    for ((folder, bytes) in captured) {
                        withContext(NonCancellable) {
                            uploadJpeg(folder, bytes)
                        }.fold(
                            onSuccess = { },
                            onFailure = { e ->
                                onError(e.message ?: "Upload failed")
                                onDismiss()
                                return@LaunchedEffect
                            }
                        )
                    }
                    onDismiss()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Body scan session failed", e)
                    onError(e.message ?: "Camera error")
                    onDismiss()
                } finally {
                    try {
                        provider?.unbindAll()
                    } catch (_: Exception) {
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite
                )
                if (secondsLeftInCountdown >= 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${secondsLeftInCountdown}s",
                        style = MaterialTheme.typography.headlineSmall,
                        color = PureWhite
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureWhite,
                        contentColor = NavyDeep
                    )
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

private suspend fun awaitPreviewStreaming(previewView: PreviewView) {
    if (previewView.previewStreamState.value == PreviewView.StreamState.STREAMING) return
    withTimeoutOrNull(8_000L) {
        suspendCancellableCoroutine { cont ->
            val live = previewView.previewStreamState
            val observer = object : Observer<PreviewView.StreamState> {
                override fun onChanged(value: PreviewView.StreamState) {
                    if (value == PreviewView.StreamState.STREAMING && cont.isActive) {
                        live.removeObserver(this)
                        cont.resume(Unit)
                    }
                }
            }
            live.observeForever(observer)
            if (live.value == PreviewView.StreamState.STREAMING && cont.isActive) {
                live.removeObserver(observer)
                cont.resume(Unit)
            }
            cont.invokeOnCancellation { live.removeObserver(observer) }
        }
    }
}

private suspend fun captureJpegToBytes(
    scanDir: File,
    angleKey: String,
    imageCapture: ImageCapture,
    executor: Executor,
): ByteArray = suspendCancellableCoroutine { cont ->
    val file = File(scanDir, "capture_${angleKey}_${System.currentTimeMillis()}.jpg")
    cont.invokeOnCancellation {
        try {
            file.delete()
        } catch (_: Exception) {
        }
    }
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        options,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val bytes = if (file.exists()) file.readBytes() else ByteArray(0)
                    if (cont.isActive) cont.resume(bytes)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                if (cont.isActive) cont.resumeWithException(exception)
            }
        }
    )
}
