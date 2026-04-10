package com.vm.vector.presentation.routine

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.vm.core.wear.RoutineCompletedPayload
import com.vm.core.wear.RoutineWearEntry
import com.vm.vector.data.WearMessageClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun vibrateMilestone(vibrator: Vibrator?) {
    if (vibrator == null || !vibrator.hasVibrator()) return
    vibrator.vibrate(
        VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE)
    )
}

private sealed class RoutineTimerPhase {
    data object Timer : RoutineTimerPhase()
    data object ConfirmFinish : RoutineTimerPhase()
    data object End : RoutineTimerPhase()
}

@Composable
fun RoutineTimerScreen(
    date: String,
    entry: RoutineWearEntry,
    messageClient: WearMessageClient,
    onRoutineSaved: () -> Unit,
    onExitDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vibrator = remember(context) {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    val baseSeconds = remember(entry.id, entry.currentValue, entry.unit) {
        unitToSeconds(entry.currentValue ?: 0.0, entry.unit).coerceAtLeast(0L)
    }
    val (minSeconds, maxSeconds) = remember(entry) { entrySecondsBounds(entry) }

    var accumulatedMs by remember(entry.id) { mutableLongStateOf(0L) }
    var runStartMs by remember(entry.id) { mutableLongStateOf(0L) }
    var isRunning by remember(entry.id) { mutableStateOf(false) }

    var displayTotalSeconds by remember(entry.id) { mutableLongStateOf(baseSeconds) }

    var hitPartial by remember(entry.id) { mutableStateOf(false) }
    var hitGoal by remember(entry.id) { mutableStateOf(false) }

    var phase by remember(entry.id) { mutableStateOf<RoutineTimerPhase>(RoutineTimerPhase.Timer) }
    var endInitialSeconds by remember(entry.id) { mutableLongStateOf(0L) }

    val isRunningRef = rememberUpdatedState(isRunning)
    val runStartRef = rememberUpdatedState(runStartMs)
    val accumulatedRef = rememberUpdatedState(accumulatedMs)
    val baseRef = rememberUpdatedState(baseSeconds)

    LaunchedEffect(entry.id) {
        var prevUnit = Double.NaN
        while (true) {
            val running = isRunningRef.value
            val startMs = runStartRef.value
            val accum = accumulatedRef.value
            val baseSec = baseRef.value
            val extraMs = if (running && startMs > 0L) {
                accum + (System.currentTimeMillis() - startMs)
            } else {
                accum
            }
            val total = baseSec + extraMs / 1000L
            displayTotalSeconds = total

            val curUnit = secondsToUnit(total, entry.unit)
            if (!prevUnit.isNaN()) {
                if (!hitPartial && prevUnit < entry.partialThreshold && curUnit >= entry.partialThreshold) {
                    hitPartial = true
                    vibrateMilestone(vibrator)
                }
                if (!hitGoal && prevUnit < entry.goalValue && curUnit >= entry.goalValue) {
                    hitGoal = true
                    vibrateMilestone(vibrator)
                }
            }
            prevUnit = curUnit

            delay(250)
        }
    }

    fun stopTimerLocal() {
        if (isRunning && runStartMs > 0L) {
            accumulatedMs += System.currentTimeMillis() - runStartMs
            runStartMs = 0L
        }
        isRunning = false
    }

    fun sendCompleted(totalSec: Long) {
        val clampedSec = totalSec.coerceIn(minSeconds, maxSeconds)
        val value = secondsToUnitForSliderPayload(clampedSec, entry.unit)
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                messageClient.sendRoutineCompleted(
                    RoutineCompletedPayload(date = date, entryId = entry.id, currentValue = value)
                )
            }
            if (ok) onRoutineSaved()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        when (phase) {
            is RoutineTimerPhase.End -> {
                RoutineEndScreen(
                    initialTotalSeconds = endInitialSeconds,
                    unit = entry.unit,
                    minSeconds = minSeconds,
                    maxSeconds = maxSeconds,
                    onSave = { totalSec -> sendCompleted(totalSec) },
                    onDiscard = onExitDiscard,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = formatHms(displayTotalSeconds),
                        style = MaterialTheme.typography.title1,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatRefLine("Partial", entry.partialThreshold, entry.unit),
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = formatRefLine("Goal", entry.goalValue, entry.unit),
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (isRunning) {
                                stopTimerLocal()
                            } else {
                                runStartMs = System.currentTimeMillis()
                                isRunning = true
                            }
                        },
                        modifier = Modifier.width(120.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.surface,
                            contentColor = MaterialTheme.colors.onSurface
                        )
                    ) {
                        Text(if (isRunning) "Stop" else "Start")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            stopTimerLocal()
                            phase = RoutineTimerPhase.ConfirmFinish
                        },
                        modifier = Modifier.width(120.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary,
                            contentColor = MaterialTheme.colors.onPrimary
                        )
                    ) {
                        Text("Finish")
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (phase is RoutineTimerPhase.ConfirmFinish) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.background)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Do you want to finish the activity?",
                            style = MaterialTheme.typography.body2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { phase = RoutineTimerPhase.Timer },
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
                                onClick = {
                                    endInitialSeconds = displayTotalSeconds.coerceIn(minSeconds, maxSeconds)
                                    phase = RoutineTimerPhase.End
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF1B5E20),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Yes",
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
