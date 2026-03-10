package com.vm.vector.presentation.workout

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
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.vm.core.models.WorkoutSet

private val DarkGreen = Color(0xFF1B5E20)

@Composable
fun EnduranceExerciseScreen(
    session: SessionItem,
    date: String,
    initialTotalSeconds: Long,
    onSave: (List<WorkoutSet>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sets = session.sets
    val durationUnit = (sets.firstOrNull()?.unitDuration ?: "s").lowercase().let { u ->
        if (u == "min" || u == "minutes" || u == "minute") "min" else "s"
    }
    val showRpe = true
    val showDistance = sets.any { it.targetDistance != null }
    val distanceUnit = sets.firstOrNull()?.unitDistance ?: "km"

    var durationValue by remember(session, initialTotalSeconds, durationUnit) {
        val fromTimerSec = initialTotalSeconds.coerceIn(0L, 99999L)
        val first = sets.firstOrNull()
        val fromSetsInDisplayUnit = when (durationUnit) {
            "min" -> first?.actualDuration?.div(60) ?: first?.targetDuration ?: 0
            else -> first?.actualDuration ?: first?.targetDuration ?: 0
        }
        val fromTimerInDisplayUnit = when (durationUnit) {
            "min" -> (fromTimerSec / 60).toInt().coerceIn(0, 99999)
            else -> fromTimerSec.toInt().coerceIn(0, 99999)
        }
        val initial = if (fromTimerInDisplayUnit > 0) fromTimerInDisplayUnit else fromSetsInDisplayUnit.coerceIn(0, 99999)
        mutableStateOf(initial)
    }
    var rpe by remember(session) {
        mutableStateOf(
            sets.firstOrNull()?.actualRpe?.toInt()?.coerceIn(0, 10) ?: 5
        )
    }
    var distance by remember(session) {
        mutableStateOf(sets.firstOrNull()?.targetDistance ?: sets.firstOrNull()?.actualDistance ?: 0.0)
    }

    val stepperShape = RoundedCornerShape(8.dp)
    val firstSet = sets.firstOrNull()
    val exerciseName = firstSet?.exerciseName?.takeIf { it.isNotBlank() }
    val exerciseDesc = firstSet?.exerciseDescription?.takeIf { it.isNotBlank() }
    val timingStr = firstSet?.timing?.takeIf { it.isNotBlank() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        if (exerciseName != null) {
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.title3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
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
        Spacer(modifier = Modifier.height(12.dp))
        Text("Duration ($durationUnit)", style = MaterialTheme.typography.body2)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepperButton(onChange = { durationValue = (durationValue - 1).coerceIn(0, 99999) }, shape = stepperShape) {
                Text("-", style = MaterialTheme.typography.title2)
            }
            Text(
                text = durationValue.toString(),
                style = MaterialTheme.typography.title3,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            StepperButton(onChange = { durationValue = (durationValue + 1).coerceIn(0, 99999) }, shape = stepperShape) {
                Text("+", style = MaterialTheme.typography.title2)
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
            Text(text = rpe.toString(), style = MaterialTheme.typography.title3, modifier = Modifier.padding(horizontal = 8.dp))
            StepperButton(onChange = { rpe = (rpe + 1).coerceIn(0, 10) }, shape = stepperShape) {
                Text("+", style = MaterialTheme.typography.title2)
            }
        }
        if (showDistance) {
            Text("Distance ($distanceUnit)", style = MaterialTheme.typography.body2, modifier = Modifier.padding(top = 14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepperButton(onChange = { distance = (distance - 0.1).coerceIn(0.0, 999.99) }, shape = stepperShape) {
                    Text("-", style = MaterialTheme.typography.title2)
                }
                Text(text = "%.1f".format(distance), style = MaterialTheme.typography.title3, modifier = Modifier.padding(horizontal = 8.dp))
                StepperButton(onChange = { distance = (distance + 0.1).coerceIn(0.0, 999.99) }, shape = stepperShape) {
                    Text("+", style = MaterialTheme.typography.title2)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.Red,
                    contentColor = Color.White
                )
            ) {
                Text("Cancel", color = Color.White)
            }
            Button(
                onClick = {
                    val durationSec = when (durationUnit) {
                        "min" -> durationValue * 60
                        else -> durationValue
                    }
                    val updated = sets.map { set ->
                        set.copy(
                            actualDuration = durationSec,
                            actualRpe = rpe.toDouble(),
                            actualDistance = if (showDistance) distance else set.actualDistance
                        )
                    }
                    onSave(updated)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = DarkGreen,
                    contentColor = Color.White
                )
            ) {
                Text("Save", color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(35.dp))
    }
}
