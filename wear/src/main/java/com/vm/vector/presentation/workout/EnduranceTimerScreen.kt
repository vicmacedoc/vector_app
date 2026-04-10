package com.vm.vector.presentation.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

private val DarkGreen = Color(0xFF1B5E20)

@Composable
fun EnduranceTimerScreen(
    onKeep: (elapsedSeconds: Long) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var startTimeMs by remember { mutableLongStateOf(0L) }
    var totalPausedMs by remember { mutableLongStateOf(0L) }
    var pauseStartMs by remember { mutableLongStateOf(0L) }
    var showFinishModal by remember { mutableStateOf(false) }

    val elapsedMs = remember(isPlaying, startTimeMs, totalPausedMs, pauseStartMs) {
        if (startTimeMs == 0L) 0L
        else if (isPlaying) System.currentTimeMillis() - startTimeMs - totalPausedMs
        else pauseStartMs - startTimeMs - totalPausedMs
    }
    var displaySeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(isPlaying, startTimeMs) {
        while (true) {
            displaySeconds = if (startTimeMs == 0L) 0L
            else if (isPlaying) (System.currentTimeMillis() - startTimeMs - totalPausedMs) / 1000
            else (pauseStartMs - startTimeMs - totalPausedMs) / 1000
            delay(1000)
        }
    }

    if (showFinishModal) {
        EnduranceFinishModal(
            onKeep = {
                showFinishModal = false
                onKeep(displaySeconds)
            },
            onDiscard = {
                showFinishModal = false
                onDiscard()
            },
            onDismiss = { showFinishModal = false }
        )
        return@EnduranceTimerScreen
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = formatElapsed(displaySeconds),
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (!isPlaying && startTimeMs == 0L) {
                            startTimeMs = System.currentTimeMillis()
                            isPlaying = true
                        } else if (isPlaying) {
                            pauseStartMs = System.currentTimeMillis()
                            isPlaying = false
                        } else {
                            totalPausedMs += (System.currentTimeMillis() - pauseStartMs)
                            isPlaying = true
                        }
                    },
                    modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(24.dp),
                        tint = Color.Black
                    )
                }
                Button(
                    onClick = { showFinishModal = true },
                    modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
                ) {
                    Text(
                        text = "X",
                        style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun EnduranceFinishModal(
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    if (confirmDiscard) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Do you wish to discard the workout",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDiscard,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.Red,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "X",
                        style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                Button(
                    onClick = { confirmDiscard = false },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = DarkGreen,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "No",
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }
            }
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Do you wish to save this workout?",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { confirmDiscard = true },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.Red,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "X",
                    style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
            Button(
                onClick = onKeep,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = DarkGreen,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Save",
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            }
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
