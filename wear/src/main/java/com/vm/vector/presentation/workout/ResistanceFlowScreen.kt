package com.vm.vector.presentation.workout

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.vm.core.models.WorkoutSet
import kotlinx.coroutines.delay

private val DarkGreen = Color(0xFF1B5E20)

private fun vibrateRestAlmostDone(vibrator: Vibrator?) {
    if (vibrator == null || !vibrator.hasVibrator()) return
    vibrator.vibrate(
        VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
    )
}

@Composable
fun ResistanceFlowScreen(
    session: SessionItem,
    date: String,
    onSave: (List<WorkoutSet>) -> Unit,
    onBack: () -> Unit,
    onWorkoutStarted: (() -> Unit)? = null
) {
    val exercises = remember(session) { session.toResistanceExercises() }
    val mutableSets = remember { mutableStateListOf<WorkoutSet>().apply { addAll(session.sets) } }

    var phase by remember { mutableStateOf("start") }
    var workoutStartMs by remember { mutableStateOf(0L) }
    var workoutEndMs by remember { mutableStateOf(0L) }
    var showEndConfirmModal by remember { mutableStateOf(false) }
    var exerciseIndex by remember { mutableStateOf(0) }
    var setIndex by remember { mutableStateOf(0) }
    var restCountdownSec by remember { mutableStateOf<Int?>(null) }
    var restStartMs by remember { mutableStateOf<Long?>(null) }
    var restDeadlineMs by remember { mutableStateOf<Long?>(null) }
    /** Last set of an exercise: skip rest countdown; Next records planned rest and advances. */
    var lastSetAwaitingNext by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val vibrator = remember(context) {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun updateSet(updated: WorkoutSet) {
        val i = mutableSets.indexOfFirst { it.id == updated.id }
        if (i >= 0) mutableSets[i] = updated
    }

    fun findById(id: String): WorkoutSet? = mutableSets.find { it.id == id }

    // Wall-clock countdown so pauses in delay() (e.g. process throttling) don't skew the rest timer.
    LaunchedEffect(restDeadlineMs) {
        val deadline = restDeadlineMs ?: return@LaunchedEffect
        val startMs = restStartMs ?: return@LaunchedEffect
        val totalRestSec = ((deadline - startMs) / 1000L).toInt().coerceAtLeast(0)
        val warnNearEnd = totalRestSec > 10
        var warned10Sec = false
        var prevRemaining = Int.MAX_VALUE
        while (true) {
            val now = System.currentTimeMillis()
            val remaining = ((deadline - now) / 1000L).toInt().coerceAtLeast(0)
            restCountdownSec = remaining
            // ~10s left: transition handles first tick skipping exactly "10" due to ms alignment
            if (warnNearEnd && !warned10Sec && prevRemaining > 10 && remaining <= 10 && remaining > 0) {
                vibrateRestAlmostDone(vibrator)
                warned10Sec = true
            }
            if (remaining <= 0) break
            prevRemaining = remaining
            delay(minOf(1000L, (deadline - now).coerceAtLeast(1L)))
        }
    }

    when (phase) {
        "start" -> ResistanceStartScreen(
            workoutTitle = session.title,
            workoutDescription = "${exercises.size} exercise(s)",
            sessionDescription = session.description,
            onStart = {
                workoutStartMs = System.currentTimeMillis()
                phase = "carousel"
                exerciseIndex = 0
                onWorkoutStarted?.invoke()
            },
            onBack = onBack
        )
        "carousel" -> {
            if (showEndConfirmModal) {
                ResistanceEndConfirmModal(
                    onConfirm = {
                        showEndConfirmModal = false
                        workoutEndMs = System.currentTimeMillis()
                        phase = "end"
                    },
                    onDismiss = { showEndConfirmModal = false }
                )
                return@ResistanceFlowScreen
            }
            val totalCarouselItems = exercises.size + 1
            val isEndWorkoutItem = exerciseIndex >= exercises.size
            val ex = exercises.getOrNull(exerciseIndex)
            if (!isEndWorkoutItem && ex == null) {
                phase = "end"
                return@ResistanceFlowScreen
            }
            ResistanceWorkoutScreen(
                exerciseName = ex?.exerciseName ?: "",
                setCount = ex?.sets?.size ?: 0,
                exerciseIndex = exerciseIndex,
                totalItems = totalCarouselItems,
                isEndWorkoutItem = isEndWorkoutItem,
                onStartExercise = {
                    if (exerciseIndex < exercises.size) {
                        setIndex = 0
                        phase = "set"
                    }
                },
                onEndWorkout = { showEndConfirmModal = true },
                onPrev = { exerciseIndex = (exerciseIndex - 1).coerceIn(0, totalCarouselItems - 1) },
                onNext = { exerciseIndex = (exerciseIndex + 1).coerceIn(0, totalCarouselItems - 1) }
            )
        }
        "set" -> {
            val ex = exercises.getOrNull(exerciseIndex) ?: run {
                phase = "carousel"
                return@ResistanceFlowScreen
            }
            val set = ex.sets.getOrNull(setIndex) ?: run {
                phase = "carousel"
                return@ResistanceFlowScreen
            }
            val currentSet = findById(set.id) ?: set
            val isLastSet = setIndex == ex.sets.size - 1

            ResistanceExerciseScreen(
                exerciseName = ex.exerciseName,
                set = currentSet,
                setNumber = setIndex + 1,
                totalSets = ex.sets.size,
                restCountdownSec = restCountdownSec,
                pendingNextWithoutCountdown = lastSetAwaitingNext,
                onUpdateSet = { updateSet(it) },
                onStartSet = { },
                onStopSet = {
                    if (isLastSet) {
                        lastSetAwaitingNext = true
                        restStartMs = null
                        restDeadlineMs = null
                        restCountdownSec = null
                    } else {
                        lastSetAwaitingNext = false
                        val start = System.currentTimeMillis()
                        restStartMs = start
                        val restSec = when (currentSet.restUnit) {
                            "s" -> currentSet.targetRest ?: 60
                            "min" -> (currentSet.targetRest ?: 1) * 60
                            else -> currentSet.targetRest ?: 60
                        }
                        restDeadlineMs = start + restSec * 1000L
                        restCountdownSec = restSec
                    }
                },
                onSkipRest = label@{
                    if (lastSetAwaitingNext) {
                        val plannedSec = when (currentSet.restUnit.lowercase()) {
                            "min", "minutes", "minute" -> (currentSet.targetRest ?: 1) * 60
                            else -> currentSet.targetRest ?: 60
                        }
                        val restUnit = currentSet.restUnit.lowercase()
                        val actualRestInUnit = when {
                            restUnit == "min" || restUnit == "minutes" || restUnit == "minute" -> (plannedSec / 60).toInt()
                            else -> plannedSec.toInt()
                        }
                        val current = findById(set.id) ?: return@label
                        updateSet(current.copy(actualRest = actualRestInUnit))
                        lastSetAwaitingNext = false
                        restStartMs = null
                        restDeadlineMs = null
                        restCountdownSec = null
                        if (setIndex + 1 < ex.sets.size) {
                            setIndex++
                        } else {
                            exerciseIndex++
                            setIndex = 0
                            phase = if (exerciseIndex >= exercises.size) "end" else "carousel"
                        }
                        return@label
                    }
                    if (restCountdownSec == null) return@label
                    val endMs = System.currentTimeMillis()
                    val startMs = restStartMs ?: endMs
                    val restSec = (endMs - startMs) / 1000
                    val restUnit = currentSet.restUnit.lowercase()
                    val actualRestInUnit = when {
                        restUnit == "min" || restUnit == "minutes" || restUnit == "minute" -> (restSec / 60).toInt()
                        else -> restSec.toInt()
                    }
                    val current = findById(set.id) ?: return@label
                    updateSet(current.copy(actualRest = actualRestInUnit))
                    restStartMs = null
                    restDeadlineMs = null
                    restCountdownSec = null
                    if (setIndex + 1 < ex.sets.size) {
                        setIndex++
                    } else {
                        exerciseIndex++
                        setIndex = 0
                        phase = if (exerciseIndex >= exercises.size) "end" else "carousel"
                    }
                },
                onNext = { }
            )
        }
        "end" -> {
            val totalMinutes = if (workoutStartMs > 0 && workoutEndMs >= workoutStartMs) {
                ((workoutEndMs - workoutStartMs) / 60_000).toInt().coerceAtLeast(0)
            } else if (workoutStartMs > 0) {
                ((System.currentTimeMillis() - workoutStartMs) / 60_000).toInt().coerceAtLeast(0)
            } else 0
            ResistanceEndScreen(
                initialTotalMinutes = totalMinutes,
                onSave = { _ ->
                    // Don't include duration in resistance set data; pass sets with actualRest, actualRpe, etc.
                    val setsWithoutDuration = mutableSets.map { it.copy(actualDuration = null) }
                    onSave(setsWithoutDuration)
                },
                onCancel = onBack
            )
        }
    }
}

@Composable
private fun ResistanceEndConfirmModal(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Do you want to finish the workout?",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDismiss,
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
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = DarkGreen,
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
