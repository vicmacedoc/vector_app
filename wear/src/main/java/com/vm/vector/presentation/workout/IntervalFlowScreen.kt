package com.vm.vector.presentation.workout

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.vm.core.models.WorkoutSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val KEEP_PROMPT_MIN_SECONDS = 5

private fun vibrate(vibrator: Vibrator?) {
    if (vibrator == null || !vibrator.hasVibrator()) return
    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
}

/**
 * ENDURANCE and OTHER sessions: start → carousel of exercises → work (countdown for timed
 * intervals, stopwatch for distance) → rest → ... → effort (RPE) log → carousel → total time.
 */
@Composable
fun IntervalFlowScreen(
    session: SessionItem,
    onSave: (List<WorkoutSet>) -> Unit,
    onBack: () -> Unit,
    onWorkoutStarted: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val exercises = remember(session) { session.toIntervalExercises() }
    val originals = remember(session) { session.sets.associateBy { it.id } }
    val mutableSets = remember { mutableStateListOf<WorkoutSet>().apply { addAll(session.sets) } }

    var phase by remember { mutableStateOf("start") } // start | carousel | work | rest | rpe | end
    var exerciseIndex by remember { mutableIntStateOf(0) }
    var stepIndex by remember { mutableIntStateOf(0) }
    var showEndConfirmModal by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var exerciseStartMs by remember { mutableStateOf<Long?>(null) }

    // Elapsed time = accumulated + (now - runStart) while running; countdowns derive from it.
    var accumulatedMs by remember { mutableLongStateOf(0L) }
    var runStartMs by remember { mutableStateOf<Long?>(null) }
    var plannedSec by remember { mutableStateOf<Int?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var rpe by remember { mutableDoubleStateOf(5.0) }

    val context = LocalContext.current
    val vibrator = remember(context) {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    val scope = rememberCoroutineScope()

    fun findById(id: String): WorkoutSet? = mutableSets.find { it.id == id }

    fun updateSet(updated: WorkoutSet) {
        val i = mutableSets.indexOfFirst { it.id == updated.id }
        if (i >= 0) mutableSets[i] = updated
    }

    fun elapsedSec(): Int {
        val running = runStartMs?.let { System.currentTimeMillis() - it } ?: 0L
        return ((accumulatedMs + running) / 1000L).toInt()
    }

    fun startTimer(planned: Int?) {
        accumulatedMs = 0L
        plannedSec = planned
        runStartMs = System.currentTimeMillis()
        nowMs = runStartMs ?: 0L
    }

    fun stopTimer() {
        runStartMs = null
        accumulatedMs = 0L
        plannedSec = null
    }

    fun togglePause() {
        val start = runStartMs
        if (start != null) {
            accumulatedMs += System.currentTimeMillis() - start
            runStartMs = null
        } else {
            runStartMs = System.currentTimeMillis()
        }
    }

    fun isStepDone(step: IntervalStep): Boolean = findById(step.row.id)?.isCompleted == true

    fun currentExercise(): IntervalExercise? = exercises.getOrNull(exerciseIndex)

    fun recordExerciseTime(exercise: IntervalExercise) {
        val start = exerciseStartMs ?: return
        exerciseStartMs = null
        val seconds = ((System.currentTimeMillis() - start) / 1000L).toInt().coerceAtLeast(0)
        exercise.rowIds.mapNotNull { findById(it) }.forEach { row ->
            updateSet(row.copy(actualTotalDuration = (row.actualTotalDuration ?: 0) + seconds))
        }
    }

    fun applyStatus(exercise: IntervalExercise) {
        val rows = exercise.rowIds.mapNotNull { findById(it) }
        val status = exerciseStatus(rows)
        rows.forEach { updateSet(it.copy(workoutStatus = status)) }
    }

    fun recordWork(step: IntervalStep, seconds: Int) {
        val row = findById(step.row.id) ?: return
        updateSet(
            row.copy(
                actualDuration = seconds.coerceAtLeast(0),
                actualDistance = row.actualDistance ?: row.targetDistance,
                isCompleted = true,
            )
        )
    }

    fun beginWork() {
        val step = currentExercise()?.steps?.getOrNull(stepIndex) ?: return
        phase = "work"
        startTimer(step.workSeconds)
    }

    fun afterRest() {
        val exercise = currentExercise() ?: return
        if (stepIndex < exercise.steps.lastIndex) {
            stepIndex++
            beginWork()
        } else {
            stopTimer()
            val planned = exercise.steps.mapNotNull { it.row.targetRpe }
            rpe = if (planned.isEmpty()) 5.0 else (planned.average() * 2).toInt() / 2.0
            phase = "rpe"
        }
    }

    fun concludeWork() {
        val step = currentExercise()?.steps?.getOrNull(stepIndex) ?: return
        recordWork(step, elapsedSec())
        if (step.restSeconds > 0) {
            phase = "rest"
            startTimer(step.restSeconds)
        } else {
            afterRest()
        }
    }

    fun concludeRest() {
        val step = currentExercise()?.steps?.getOrNull(stepIndex) ?: return
        findById(step.row.id)?.let { updateSet(it.copy(actualRest = restInUnit(elapsedSec(), it.restUnit))) }
        afterRest()
    }

    fun startExercise(index: Int) {
        val exercise = exercises.getOrNull(index) ?: return
        exerciseIndex = index
        stepIndex = exercise.steps.indexOfFirst { !isStepDone(it) }.takeIf { it >= 0 } ?: 0
        exerciseStartMs = System.currentTimeMillis()
        beginWork()
    }

    fun finishExercise(loggedRpe: Double?) {
        val exercise = currentExercise() ?: return
        if (loggedRpe != null) {
            exercise.rowIds.mapNotNull { findById(it) }
                .filter { it.isCompleted }
                .forEach { updateSet(it.copy(actualRpe = loggedRpe)) }
        }
        recordExerciseTime(exercise)
        applyStatus(exercise)
        exerciseIndex++
        stepIndex = 0
        phase = if (exerciseIndex >= exercises.size) "end" else "carousel"
    }

    /** Keep: concluded intervals stay (plus the running one if it got going), the rest revert. */
    fun exitKeeping() {
        val exercise = currentExercise() ?: return
        val step = exercise.steps.getOrNull(stepIndex)
        if (phase == "work" && step != null && elapsedSec() >= KEEP_PROMPT_MIN_SECONDS) {
            recordWork(step, elapsedSec())
        }
        exercise.steps.filterNot { isStepDone(it) }.forEach { pending ->
            val original = originals[pending.row.id] ?: return@forEach
            val current = findById(pending.row.id) ?: return@forEach
            // Exercise time is shared by all of its rows, so it survives the revert.
            updateSet(original.copy(actualTotalDuration = current.actualTotalDuration))
        }
        recordExerciseTime(exercise)
        applyStatus(exercise)
        stopTimer()
        showExitConfirm = false
        phase = "carousel"
    }

    fun exitDiscarding() {
        val exercise = currentExercise() ?: return
        exercise.rowIds.forEach { id -> originals[id]?.let { updateSet(it) } }
        exerciseStartMs = null
        stopTimer()
        showExitConfirm = false
        phase = "carousel"
    }

    fun requestExit() {
        val exercise = currentExercise() ?: return
        val anyDone = exercise.steps.any { isStepDone(it) }
        val runningWork = phase == "work" && elapsedSec() >= KEEP_PROMPT_MIN_SECONDS
        if (anyDone || runningWork) showExitConfirm = true else exitDiscarding()
    }

    /** Applies an edited session total: scales measured times, or spreads it by plan if none were measured. */
    fun rowsWithTotal(totalMinutes: Int): List<WorkoutSet> {
        val measured = exercises.associate { ex ->
            ex.exerciseId to (ex.rowIds.firstNotNullOfOrNull { findById(it)?.actualTotalDuration } ?: 0)
        }
        val measuredTotal = measured.values.sum()
        if (measuredTotal / 60 == totalMinutes) return mutableSets.toList()
        val targetSec = totalMinutes * 60
        val shares: Map<String, Double> = if (measuredTotal > 0) {
            measured.mapValues { (_, sec) -> sec.toDouble() / measuredTotal }
        } else {
            val worked = exercises.filter { ex -> ex.rowIds.any { findById(it)?.isCompleted == true } }
                .ifEmpty { exercises }
            val planned = worked.associate { ex -> ex.exerciseId to (ex.steps.first().row.totalDuration ?: 0.0) }
            val plannedTotal = planned.values.sum()
            planned.mapValues { (_, minutes) -> if (plannedTotal > 0) minutes / plannedTotal else 1.0 / planned.size }
        }
        return mutableSets.map { row ->
            val share = shares[row.exerciseId] ?: return@map row
            row.copy(actualTotalDuration = (targetSec * share).toInt())
        }
    }

    LaunchedEffect(phase, runStartMs) {
        if (phase != "work" && phase != "rest") return@LaunchedEffect
        if (runStartMs == null) return@LaunchedEffect
        var warned = false
        while (true) {
            nowMs = System.currentTimeMillis()
            val planned = plannedSec
            if (planned != null) {
                val remaining = planned - elapsedSec()
                if (!warned && planned > 10 && remaining in 1..10) {
                    vibrate(vibrator)
                    warned = true
                }
                if (remaining <= 0) {
                    vibrate(vibrator)
                    if (phase == "work") concludeWork() else concludeRest()
                    break
                }
            }
            delay(250L)
        }
    }

    when (phase) {
        "start" -> ResistanceStartScreen(
            workoutTitle = session.title,
            workoutDescription = "${exercises.size} exercise(s)",
            sessionDescription = session.description,
            onStart = {
                scope.launch {
                    delay(32)
                    exerciseIndex = 0
                    phase = "carousel"
                    onWorkoutStarted?.invoke()
                }
            },
            onBack = onBack,
            modifier = modifier
        )
        "carousel" -> {
            if (showEndConfirmModal) {
                IntervalEndConfirmModal(
                    onConfirm = {
                        showEndConfirmModal = false
                        phase = "end"
                    },
                    onDismiss = { showEndConfirmModal = false }
                )
            } else {
                val totalItems = (exercises.size + 1).coerceAtLeast(1)
                val exercise = exercises.getOrNull(exerciseIndex)
                val done = exercise?.steps?.count { isStepDone(it) } ?: 0
                val progress = exercise?.takeIf { done > 0 }?.let {
                    if (it.steps.size == 1) "Logged" else "$done/${it.steps.size} done"
                }
                ResistanceWorkoutScreen(
                    exerciseName = exercise?.exerciseName.orEmpty(),
                    subtitle = listOfNotNull(exercise?.summary()?.ifBlank { null }, progress).joinToString("\n"),
                    exerciseDescription = exercise?.let { ex ->
                        listOfNotNull(ex.stroke?.let(::strokeName), ex.description.ifBlank { null })
                            .joinToString(" · ")
                            .ifBlank { null }
                    },
                    exerciseIndex = exerciseIndex.coerceIn(0, totalItems - 1),
                    totalItems = totalItems,
                    isEndWorkoutItem = exercise == null,
                    startLabel = when {
                        exercise == null -> "Start"
                        done >= exercise.steps.size -> "Redo"
                        done > 0 -> "Resume"
                        else -> "Start"
                    },
                    onStartExercise = { startExercise(exerciseIndex) },
                    onEndWorkout = { showEndConfirmModal = true },
                    onPrev = { exerciseIndex = (exerciseIndex - 1).coerceIn(0, totalItems - 1) },
                    onNext = { exerciseIndex = (exerciseIndex + 1).coerceIn(0, totalItems - 1) },
                    modifier = modifier
                )
            }
        }
        "work", "rest" -> {
            val exercise = currentExercise()
            val step = exercise?.steps?.getOrNull(stepIndex)
            if (exercise == null || step == null) {
                LaunchedEffect(Unit) { phase = "carousel" }
            } else if (showExitConfirm) {
                BackHandler { showExitConfirm = false }
                val done = exercise.steps.count { isStepDone(it) }
                ExitExerciseConfirm(
                    title = "Leave ${exercise.exerciseName}?",
                    detail = if (exercise.steps.size == 1) {
                        "Keep saves the time so far; Discard clears it."
                    } else {
                        "$done of ${exercise.steps.size} intervals done. Keep saves them; Discard clears this exercise."
                    },
                    onKeep = ::exitKeeping,
                    onDiscard = ::exitDiscarding,
                    onStay = { showExitConfirm = false },
                )
            } else {
                BackHandler { requestExit() }
                val elapsed = ((accumulatedMs + (runStartMs?.let { nowMs - it } ?: 0L).coerceAtLeast(0L)) / 1000L).toInt()
                val isRest = phase == "rest"
                val planned = plannedSec
                val position = if (exercise.steps.size > 1) {
                    "Interval ${step.number}/${exercise.steps.size}"
                } else {
                    "Work"
                }
                val next = exercise.steps.getOrNull(stepIndex + 1)
                IntervalTimerScreen(
                    phaseLabel = if (isRest) "Rest" else position,
                    title = exercise.exerciseName,
                    targetLabel = if (isRest) null else step.targetLabel().ifBlank { null },
                    timeText = formatCountdown(if (planned != null) planned - elapsed else elapsed),
                    timeCaption = when {
                        planned != null -> "of ${formatCountdown(planned)}"
                        step.distanceLabel() != null -> "elapsed · Done at ${step.distanceLabel()}"
                        else -> "elapsed"
                    },
                    previewText = if (isRest) {
                        next?.let { "Next: Interval ${it.number} · ${it.targetLabel()}" } ?: "Next: log effort"
                    } else {
                        null
                    },
                    isRest = isRest,
                    isPlaying = runStartMs != null,
                    primaryLabel = if (isRest) "Skip" else "Done",
                    onPlayPause = if (isRest) null else ::togglePause,
                    onPrimary = { if (isRest) concludeRest() else concludeWork() },
                    modifier = modifier.swipeBackGesture(onSwipeBack = ::requestExit),
                )
            }
        }
        "rpe" -> {
            val exercise = currentExercise()
            if (exercise == null) {
                LaunchedEffect(Unit) { phase = "carousel" }
            } else {
                BackHandler { finishExercise(null) }
                IntervalRpeScreen(
                    exerciseName = exercise.exerciseName,
                    rpe = rpe,
                    onRpeChange = { rpe = it },
                    onSave = { finishExercise(rpe) },
                    onSkip = { finishExercise(null) },
                    modifier = modifier,
                )
            }
        }
        "end" -> {
            val measuredSec = exercises.sumOf { ex ->
                ex.rowIds.firstNotNullOfOrNull { findById(it)?.actualTotalDuration } ?: 0
            }
            EnduranceEndScreen(
                initialDurationValue = measuredSec / 60,
                durationUnit = "min",
                onSave = { minutes -> onSave(rowsWithTotal(minutes)) },
                onDiscard = onBack,
                modifier = modifier
            )
        }
    }
}

private fun strokeName(stroke: String): String = when (stroke.uppercase()) {
    "IM" -> "IM"
    else -> stroke.lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
private fun IntervalTimerScreen(
    phaseLabel: String,
    title: String,
    targetLabel: String?,
    timeText: String,
    timeCaption: String,
    previewText: String?,
    isRest: Boolean,
    isPlaying: Boolean,
    primaryLabel: String,
    onPlayPause: (() -> Unit)?,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (isRest) WorkoutRestBlue else WorkoutGreen
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(horizontal = 12.dp)
            .padding(top = 28.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.caption2,
                color = if (isRest) NextUpBlue else MaterialTheme.colors.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Text(
                text = title,
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
            (targetLabel ?: previewText)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.caption1,
                    color = if (previewText != null) NextUpBlue else MaterialTheme.colors.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.title1.copy(fontWeight = FontWeight.Bold),
                color = if (isRest) NextUpBlue else MaterialTheme.colors.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = timeCaption,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onPlayPause != null) {
                Button(
                    onClick = onPlayPause,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isPlaying) Color.Red else WorkoutGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Button(
                onClick = onPrimary,
                colors = ButtonDefaults.buttonColors(backgroundColor = accent, contentColor = Color.White),
                modifier = Modifier
                    .height(44.dp)
                    .fillMaxWidth(0.55f)
            ) {
                Text(
                    text = primaryLabel,
                    style = MaterialTheme.typography.button.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun IntervalRpeScreen(
    exerciseName: String,
    rpe: Double,
    onRpeChange: (Double) -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(top = 28.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "How hard?",
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = exerciseName,
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        StepperRow(
            label = "RPE",
            value = formatHalfStep(rpe),
            onMinus = { onRpeChange((rpe - 0.5).coerceIn(0.0, 10.0)) },
            onPlus = { onRpeChange((rpe + 0.5).coerceIn(0.0, 10.0)) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSkip,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray, contentColor = Color.White)
            ) { Text("Skip", color = Color.White) }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(backgroundColor = WorkoutGreen, contentColor = Color.White)
            ) { Text("Save", color = Color.White) }
        }
    }
}

@Composable
private fun IntervalEndConfirmModal(
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
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red, contentColor = Color.White)
            ) {
                Text(
                    text = "X",
                    style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(backgroundColor = WorkoutGreen, contentColor = Color.White)
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
