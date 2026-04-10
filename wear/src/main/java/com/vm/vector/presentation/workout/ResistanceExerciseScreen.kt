package com.vm.vector.presentation.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.vm.core.models.WorkoutSet

private val DarkGreen = Color(0xFF1B5E20)

@Composable
fun ResistanceExerciseScreen(
    exerciseName: String,
    set: WorkoutSet,
    setNumber: Int,
    totalSets: Int,
    restCountdownSec: Int?,
    /** After last set: show "Next" without countdown; rest is recorded as planned when user taps. */
    pendingNextWithoutCountdown: Boolean = false,
    onUpdateSet: (WorkoutSet) -> Unit,
    onStartSet: () -> Unit,
    onStopSet: () -> Unit,
    onSkipRest: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showLoad = set.targetLoad != null
    val showReps = set.targetReps != null
    val showRir = set.targetRir != null

    var load by remember(set.id) { mutableStateOf((set.actualLoad ?: set.targetLoad ?: 0.0).toInt()) }
    var reps by remember(set.id) { mutableStateOf(set.actualReps ?: set.targetReps ?: 0) }
    var rir by remember(set.id) { mutableStateOf(set.actualRir ?: set.targetRir ?: 0) }
    var rpe by remember(set.id) { mutableStateOf(set.actualRpe?.toInt()?.coerceIn(0, 10) ?: 5) }
    var isRunning by remember { mutableStateOf(false) }

    val stepperShape = RoundedCornerShape(8.dp)
    val inRest = restCountdownSec != null || pendingNextWithoutCountdown

    // Bottom region background: Green = Start, Red = Stop, Blue = Next
    val bottomBackgroundColor = when {
        inRest -> Color.Blue
        !isRunning -> Color(0xFF1B5E20) // Green (same as DarkGreen)
        else -> Color.Red
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Set $setNumber/$totalSets",
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 4
                )
                val exerciseDesc = set.exerciseDescription.takeIf { it.isNotBlank() }
                val timingStr = set.timing?.takeIf { it.isNotBlank() }
                if (exerciseDesc != null) {
                    Text(
                        text = exerciseDesc,
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        textAlign = TextAlign.Center,
                        maxLines = 3
                    )
                }
                if (timingStr != null) {
                    Text(
                        text = "Timing: $timingStr",
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
                if (showLoad) {
                    Text("Load (${set.unitLoad})", style = MaterialTheme.typography.body2, modifier = Modifier.padding(top = 14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StepperButton(onChange = { load = (load - 1).coerceIn(0, 999) }, shape = stepperShape) {
                            Text("-", style = MaterialTheme.typography.title2)
                        }
                        Text(
                            text = load.toString(),
                            style = MaterialTheme.typography.title3,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        StepperButton(onChange = { load = (load + 1).coerceIn(0, 999) }, shape = stepperShape) {
                            Text("+", style = MaterialTheme.typography.title2)
                        }
                    }
                }
                if (showReps) {
                    Text("Reps", style = MaterialTheme.typography.body2, modifier = Modifier.padding(top = 14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StepperButton(onChange = { reps = (reps - 1).coerceIn(0, 999) }, shape = stepperShape) {
                            Text("-", style = MaterialTheme.typography.title2)
                        }
                        Text(
                            text = reps.toString(),
                            style = MaterialTheme.typography.title3,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        StepperButton(onChange = { reps = (reps + 1).coerceIn(0, 999) }, shape = stepperShape) {
                            Text("+", style = MaterialTheme.typography.title2)
                        }
                    }
                }
                if (showRir) {
                    Text("RIR", style = MaterialTheme.typography.body2, modifier = Modifier.padding(top = 14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StepperButton(onChange = { rir = (rir - 1).coerceIn(0, 10) }, shape = stepperShape) {
                            Text("-", style = MaterialTheme.typography.title2)
                        }
                        Text(
                            text = rir.toString(),
                            style = MaterialTheme.typography.title3,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        StepperButton(onChange = { rir = (rir + 1).coerceIn(0, 10) }, shape = stepperShape) {
                            Text("+", style = MaterialTheme.typography.title2)
                        }
                    }
                }
                Text("RPE", style = MaterialTheme.typography.body2, modifier = Modifier.padding(top = 14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepperButton(onChange = { rpe = (rpe - 1).coerceIn(0, 10) }, shape = stepperShape) {
                        Text("-", style = MaterialTheme.typography.title2)
                    }
                    Text(
                        text = rpe.toString(),
                        style = MaterialTheme.typography.title3,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    StepperButton(onChange = { rpe = (rpe + 1).coerceIn(0, 10) }, shape = stepperShape) {
                        Text("+", style = MaterialTheme.typography.title2)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bottomBackgroundColor)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    inRest -> Text(
                        text = if (pendingNextWithoutCountdown) "Next" else "Next (${restCountdownSec}s)",
                        style = MaterialTheme.typography.body2,
                        color = Color.White,
                        modifier = Modifier.clickable(onClick = onSkipRest)
                    )
                    !isRunning -> Text(
                        text = "Start Set",
                        style = MaterialTheme.typography.body2,
                        color = Color.White,
                        modifier = Modifier.clickable(onClick = {
                            isRunning = true
                            onStartSet()
                        })
                    )
                    else -> Text(
                        text = "Stop Set",
                        style = MaterialTheme.typography.body2,
                        color = Color.White,
                        modifier = Modifier.clickable(onClick = {
                            isRunning = false
                            val updated = set.copy(
                                actualLoad = if (showLoad) load.toDouble() else set.actualLoad,
                                actualReps = if (showReps) reps else set.actualReps,
                                actualRir = if (showRir) rir else set.actualRir,
                                actualRpe = rpe.toDouble()
                            )
                            onUpdateSet(updated)
                            onStopSet()
                        })
                    )
                }
            }
        }
    }
}
