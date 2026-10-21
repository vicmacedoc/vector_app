package com.vm.vector.presentation.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.vm.core.audio.M4aRepair
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.PureWhite
import com.vm.vector.data.WearAudioSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val MAX_RECORD_MS = 5 * 60 * 1000L

@Composable
fun VoiceCaptureScreen(
    audioSender: WearAudioSender,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var statusText by remember { mutableStateOf("Tap to record") }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) statusText = "Mic permission needed"
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.apply {
                    stop()
                    release()
                }
            } catch (_: Exception) { }
            recorder = null
        }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (isRecording) {
            elapsedMs = System.currentTimeMillis() - start
            if (elapsedMs >= MAX_RECORD_MS) {
                stopAndSend(
                    recorder = recorder,
                    outputFile = outputFile,
                    audioSender = audioSender,
                    scope = scope,
                    setRecording = { isRecording = it },
                    setRecorder = { recorder = it },
                    setOutput = { outputFile = it },
                    setStatus = { statusText = it },
                    onDone = onDone
                )
                break
            }
            delay(200)
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isRecording) formatElapsed(elapsedMs) else "Voice",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .scale(if (isRecording) scale else 1f)
                    .then(
                        if (isRecording) {
                            Modifier.border(3.dp, DeleteRed, CircleShape)
                        } else {
                            Modifier
                        }
                    )
            ) {
                Button(
                    onClick = {
                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@Button
                        }
                        if (isRecording) {
                            stopAndSend(
                                recorder = recorder,
                                outputFile = outputFile,
                                audioSender = audioSender,
                                scope = scope,
                                setRecording = { isRecording = it },
                                setRecorder = { recorder = it },
                                setOutput = { outputFile = it },
                                setStatus = { statusText = it },
                                onDone = onDone
                            )
                        } else {
                            val file = File(
                                audioSender.outboxDir(),
                                "watch_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.m4a"
                            )
                            val mr = createRecorder(context, file)
                            try {
                                mr.prepare()
                                mr.start()
                                recorder = mr
                                outputFile = file
                                isRecording = true
                                elapsedMs = 0L
                                statusText = "Recording…"
                            } catch (_: Exception) {
                                try { mr.release() } catch (_: Exception) { }
                                file.delete()
                                statusText = "Record failed"
                            }
                        }
                    },
                    modifier = Modifier.size(72.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isRecording) DeleteRed else ElectricBlue,
                        contentColor = PureWhite
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isRecording) "Stop" else "Record",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.8f)
            )
        }
    }
}

private fun createRecorder(context: Context, file: File): MediaRecorder {
    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }
    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    recorder.setAudioEncodingBitRate(128_000)
    recorder.setAudioSamplingRate(44_100)
    recorder.setOutputFile(file.absolutePath)
    return recorder
}

private fun stopAndSend(
    recorder: MediaRecorder?,
    outputFile: File?,
    audioSender: WearAudioSender,
    scope: CoroutineScope,
    setRecording: (Boolean) -> Unit,
    setRecorder: (MediaRecorder?) -> Unit,
    setOutput: (File?) -> Unit,
    setStatus: (String) -> Unit,
    onDone: () -> Unit
) {
    setRecording(false)
    try {
        recorder?.apply {
            stop()
            release()
        }
    } catch (_: Exception) { }
    setRecorder(null)
    val file = outputFile
    setOutput(null)
    if (file == null || !file.exists()) {
        setStatus("Nothing recorded")
        return
    }
    setStatus("Sending…")
    scope.launch {
        val ok = withContext(Dispatchers.IO) {
            M4aRepair.repairIfNeeded(file)
            audioSender.sendFile(file)
        }
        setStatus(if (ok) "Sent to phone Inbox" else "Queued")
        delay(1200)
        onDone()
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
