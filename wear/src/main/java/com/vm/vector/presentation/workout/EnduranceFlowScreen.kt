package com.vm.vector.presentation.workout

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.font.FontWeight
import com.vm.core.models.WorkoutSet
import kotlinx.coroutines.delay

private val DarkGreen = Color(0xFF1B5E20)

@Composable
fun EnduranceFlowScreen(
    session: SessionItem,
    date: String,
    onSave: (List<WorkoutSet>) -> Unit,
    onBack: () -> Unit,
    onWorkoutStarted: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val exercises = remember(session) { session.toEnduranceExercises() }
    val mutableSets = remember { mutableStateListOf<WorkoutSet>().apply { addAll(session.sets) } }

    var phase by remember { mutableStateOf("start") }
    var workoutStartMs by remember { mutableStateOf(0L) }
    var exerciseIndex by remember { mutableStateOf(0) }
    var formExerciseIndex by remember { mutableStateOf(0) }
    var showEndConfirmModal by remember { mutableStateOf(false) }

    val exerciseElapsedSeconds = remember(exercises.size) {
        mutableStateListOf<Long>().apply { repeat(exercises.size) { add(0L) } }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var startTimeMs by remember { mutableLongStateOf(0L) }
    var totalPausedMs by remember { mutableLongStateOf(0L) }
    var pauseStartMs by remember { mutableLongStateOf(0L) }
    var runningSeconds by remember { mutableStateOf(0L) }

    fun currentRunningSeconds(): Long {
        if (startTimeMs == 0L) return 0L
        return if (isPlaying) (System.currentTimeMillis() - startTimeMs - totalPausedMs) / 1000
        else (pauseStartMs - startTimeMs - totalPausedMs) / 1000
    }

    LaunchedEffect(isPlaying, startTimeMs, totalPausedMs, pauseStartMs) {
        while (true) {
            runningSeconds = currentRunningSeconds()
            delay(1000)
        }
    }

    val displaySeconds = if (exerciseIndex < exercises.size) {
        (exerciseElapsedSeconds.getOrNull(exerciseIndex) ?: 0L) + runningSeconds
    } else 0L

    fun persistCurrentRunningAndResetTimer() {
        if (exerciseIndex < exerciseElapsedSeconds.size && (startTimeMs != 0L || isPlaying)) {
            val current = exerciseElapsedSeconds[exerciseIndex] + currentRunningSeconds()
            exerciseElapsedSeconds[exerciseIndex] = current
        }
        startTimeMs = 0L
        totalPausedMs = 0L
        pauseStartMs = 0L
        isPlaying = false
        runningSeconds = 0L
    }

    fun updateSetsForExercise(exerciseId: String, newSets: List<WorkoutSet>) {
        val toKeep = mutableSets.filter { it.exerciseId != exerciseId }.toMutableList()
        mutableSets.clear()
        mutableSets.addAll(toKeep)
        mutableSets.addAll(newSets)
        mutableSets.sortWith(compareBy<WorkoutSet> { it.exerciseId }.thenBy { it.setNumber })
    }

    val totalCarouselItems = exercises.size + 1

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
        "form" -> {
            val ex = exercises.getOrNull(formExerciseIndex) ?: run {
                phase = "carousel"
                return@EnduranceFlowScreen
            }
            val currentSets = mutableSets.filter { it.exerciseId == ex.exerciseId }.sortedBy { it.setNumber }
            val exerciseSession = SessionItem(title = ex.exerciseName, sets = currentSets.ifEmpty { ex.sets })
            val currentElapsed = exerciseElapsedSeconds.getOrNull(formExerciseIndex) ?: 0L
            EnduranceExerciseScreen(
                session = exerciseSession,
                date = date,
                initialTotalSeconds = currentElapsed,
                onSave = { sets ->
                    updateSetsForExercise(ex.exerciseId, sets)
                    val savedSeconds = sets.firstOrNull()?.actualDuration?.toLong() ?: 0L
                    if (formExerciseIndex < exerciseElapsedSeconds.size) {
                        exerciseElapsedSeconds[formExerciseIndex] = savedSeconds
                    }
                    phase = "carousel"
                    exerciseIndex = (formExerciseIndex + 1).coerceAtMost(totalCarouselItems - 1)
                },
                onCancel = { phase = "carousel" },
                modifier = modifier
            )
            return@EnduranceFlowScreen
        }
        "total_time" -> {
            val totalSecondsFromExercises = mutableSets.sumOf { (it.actualDuration ?: 0).toLong() }
            val durationUnit = (mutableSets.firstOrNull()?.unitDuration ?: "s").lowercase().let { u ->
                if (u == "min" || u == "minutes" || u == "minute") "min" else "s"
            }
            val initialDurationValue = when (durationUnit) {
                "min" -> (totalSecondsFromExercises / 60).toInt().coerceAtLeast(0)
                else -> totalSecondsFromExercises.toInt().coerceAtLeast(0)
            }
            EnduranceEndScreen(
                initialDurationValue = initialDurationValue,
                durationUnit = durationUnit,
                onSave = { valueInUnit ->
                    val durationSec = when (durationUnit) {
                        "min" -> valueInUnit * 60
                        else -> valueInUnit
                    }
                    val withDuration = mutableSets.map { s ->
                        if (s.actualDuration != null) s else s.copy(actualDuration = durationSec)
                    }
                    onSave(withDuration)
                },
                onDiscard = onBack,
                modifier = modifier
            )
            return@EnduranceFlowScreen
        }
        "carousel" -> { }
    }

    if (phase == "carousel") {
        if (showEndConfirmModal) {
            EnduranceEndConfirmModal(
                onConfirm = { showEndConfirmModal = false; phase = "total_time" },
                onDismiss = { showEndConfirmModal = false }
            )
            return@EnduranceFlowScreen
        }

        val isEndWorkoutItem = exerciseIndex >= exercises.size
        val currentEx = exercises.getOrNull(exerciseIndex)
        EnduranceWorkoutScreen(
            exerciseName = if (currentEx != null) currentEx.exerciseName else "",
            displaySeconds = displaySeconds,
            isPlaying = isPlaying,
            isEndWorkoutItem = isEndWorkoutItem,
            exerciseIndex = exerciseIndex,
            totalItems = totalCarouselItems,
            onPlayPause = {
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
            onSave = {
                persistCurrentRunningAndResetTimer()
                formExerciseIndex = exerciseIndex
                phase = "form"
            },
            onEndWorkout = { showEndConfirmModal = true },
            onPrev = {
                persistCurrentRunningAndResetTimer()
                exerciseIndex = (exerciseIndex - 1).coerceIn(0, totalCarouselItems - 1)
            },
            onNext = {
                persistCurrentRunningAndResetTimer()
                exerciseIndex = (exerciseIndex + 1).coerceIn(0, totalCarouselItems - 1)
            },
            modifier = modifier
        )
    }
}

@Composable
private fun EnduranceEndConfirmModal(
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
            "Do you want to finish the workout?",
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
