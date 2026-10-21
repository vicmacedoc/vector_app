package com.vm.vector.presentation.workout

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableIntStateOf
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

private fun vibrateRestAlmostDone(vibrator: Vibrator?) {
    if (vibrator == null || !vibrator.hasVibrator()) return
    vibrator.vibrate(
        VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
    )
}

/**
 * start → carousel of blocks (single exercises or supersets) → steps (main set + drops) with rest
 * → back to the carousel → end. Swiping back from a step asks to keep or discard the block's data.
 */
@Composable
fun ResistanceFlowScreen(
    session: SessionItem,
    date: String,
    onSave: (List<WorkoutSet>) -> Unit,
    onBack: () -> Unit,
    onWorkoutStarted: (() -> Unit)? = null
) {
    val blocks = remember(session) { session.toResistanceBlocks() }
    val exerciseCount = remember(blocks) { blocks.sumOf { it.exercises.size } }
    val originals = remember(session) { session.sets.associateBy { it.id } }
    val mutableSets = remember { mutableStateListOf<WorkoutSet>().apply { addAll(session.sets) } }

    var phase by remember { mutableStateOf("start") }
    var showEndConfirmModal by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var blockIndex by remember { mutableIntStateOf(0) }
    var stepIndex by remember { mutableIntStateOf(0) }
    /** Step shown on screen; swiping moves it while [stepIndex] stays the one being worked. */
    var viewIndex by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var blockStartMs by remember { mutableStateOf<Long?>(null) }
    var restCountdownSec by remember { mutableStateOf<Int?>(null) }
    var restStartMs by remember { mutableStateOf<Long?>(null) }
    var restDeadlineMs by remember { mutableStateOf<Long?>(null) }
    var awaitingNext by remember { mutableStateOf(false) }

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

    fun clearRest() {
        restStartMs = null
        restDeadlineMs = null
        restCountdownSec = null
        awaitingNext = false
    }

    fun applyStatuses(block: ResistanceBlock) {
        block.exercises.forEach { exercise ->
            val rows = exercise.rowIds.mapNotNull { findById(it) }
            val status = exerciseStatus(rows)
            rows.forEach { updateSet(it.copy(workoutStatus = status)) }
        }
    }

    /** Adds the time spent in the block to each member, split by planned minutes. */
    fun recordBlockTime(block: ResistanceBlock) {
        val start = blockStartMs ?: return
        blockStartMs = null
        val elapsedSec = ((System.currentTimeMillis() - start) / 1000L).toInt().coerceAtLeast(0)
        splitElapsed(elapsedSec, block.exercises).forEach { (exerciseId, seconds) ->
            val exercise = block.exercises.first { it.exerciseId == exerciseId }
            exercise.rowIds.mapNotNull { findById(it) }.forEach { row ->
                updateSet(row.copy(actualTotalDuration = (row.actualTotalDuration ?: 0) + seconds))
            }
        }
    }

    fun isStepDone(step: ResistanceStep): Boolean = findById(step.main.id)?.isCompleted == true

    fun startBlock(index: Int) {
        val block = blocks.getOrNull(index) ?: return
        blockIndex = index
        stepIndex = block.steps.indexOfFirst { !isStepDone(it) }.takeIf { it >= 0 } ?: 0
        viewIndex = stepIndex
        isRunning = false
        clearRest()
        blockStartMs = System.currentTimeMillis()
        phase = "set"
    }

    fun completeStep(step: ResistanceStep) {
        step.rowIds.mapNotNull { findById(it) }.forEach { row ->
            updateSet(
                row.copy(
                    actualLoad = row.actualLoad ?: row.targetLoad,
                    actualReps = row.actualReps ?: row.targetReps,
                    actualRir = row.actualRir ?: row.targetRir,
                    actualRpe = if (row.id == step.main.id) defaultRpe(row) else row.actualRpe,
                    isCompleted = true,
                )
            )
        }
    }

    fun finishBlock(block: ResistanceBlock) {
        recordBlockTime(block)
        applyStatuses(block)
        isRunning = false
        clearRest()
        blockIndex++
        stepIndex = 0
        viewIndex = 0
        phase = if (blockIndex >= blocks.size) "end" else "carousel"
    }

    /** Keep: concluded steps stay, unfinished steps go back to their planned values. */
    fun exitKeeping(block: ResistanceBlock) {
        block.steps.filterNot { isStepDone(it) }.flatMap { it.rowIds }.forEach { id ->
            val original = originals[id] ?: return@forEach
            val current = findById(id) ?: return@forEach
            // Exercise time is shared by all of its rows, so it survives the revert.
            updateSet(original.copy(actualTotalDuration = current.actualTotalDuration))
        }
        recordBlockTime(block)
        applyStatuses(block)
        isRunning = false
        clearRest()
        showExitConfirm = false
        phase = "carousel"
    }

    fun exitDiscarding(block: ResistanceBlock) {
        block.rowIds.forEach { id -> originals[id]?.let { updateSet(it) } }
        blockStartMs = null
        isRunning = false
        clearRest()
        showExitConfirm = false
        phase = "carousel"
    }

    fun requestExit() {
        val block = blocks.getOrNull(blockIndex) ?: return
        if (block.steps.none { isStepDone(it) }) {
            exitDiscarding(block)
        } else {
            showExitConfirm = true
        }
    }

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
            workoutDescription = "$exerciseCount exercise(s)",
            sessionDescription = session.description,
            onStart = {
                // Defer leave so ScalingLazyColumn can dispose cleanly under SwipeToDismissBox.
                scope.launch {
                    delay(32)
                    phase = "carousel"
                    blockIndex = 0
                    onWorkoutStarted?.invoke()
                }
            },
            onBack = onBack
        )
        "carousel" -> {
            if (showEndConfirmModal) {
                ResistanceEndConfirmModal(
                    onConfirm = {
                        showEndConfirmModal = false
                        phase = "end"
                    },
                    onDismiss = { showEndConfirmModal = false }
                )
            } else {
                val totalCarouselItems = (blocks.size + 1).coerceAtLeast(1)
                val block = blocks.getOrNull(blockIndex)
                val doneSteps = block?.steps?.count { isStepDone(it) } ?: 0
                ResistanceWorkoutScreen(
                    exerciseName = block?.title.orEmpty(),
                    subtitle = block?.let { blockSubtitle(it, doneSteps) }.orEmpty(),
                    exerciseDescription = block?.takeIf { it.isSuperset }?.let { b ->
                        b.exercises.mapIndexed { i, ex -> "${b.groupingId}${i + 1} ${ex.exerciseName}" }
                            .joinToString("\n")
                    },
                    exerciseIndex = blockIndex.coerceIn(0, totalCarouselItems - 1),
                    totalItems = totalCarouselItems,
                    isEndWorkoutItem = block == null,
                    startLabel = when {
                        block == null -> "Start Exercise"
                        doneSteps >= block.steps.size -> "Redo"
                        doneSteps > 0 -> "Resume"
                        block.isSuperset -> "Start Superset"
                        else -> "Start Exercise"
                    },
                    onStartExercise = { startBlock(blockIndex) },
                    onEndWorkout = { showEndConfirmModal = true },
                    onPrev = { blockIndex = (blockIndex - 1).coerceIn(0, totalCarouselItems - 1) },
                    onNext = { blockIndex = (blockIndex + 1).coerceIn(0, totalCarouselItems - 1) }
                )
            }
        }
        "set" -> {
            val block = blocks.getOrNull(blockIndex)
            val step = block?.steps?.getOrNull(stepIndex)
            if (block == null || step == null) {
                LaunchedEffect(blockIndex, stepIndex) { phase = "carousel" }
            } else if (showExitConfirm) {
                BackHandler { showExitConfirm = false }
                val done = block.steps.count { isStepDone(it) }
                ExitExerciseConfirm(
                    title = "Leave ${block.title}?",
                    detail = "$done of ${block.steps.size} sets concluded. Keep saves them; " +
                        "Discard clears this exercise.",
                    onKeep = { exitKeeping(block) },
                    onDiscard = { exitDiscarding(block) },
                    onStay = { showExitConfirm = false },
                )
            } else {
                BackHandler { requestExit() }
                val main = findById(step.main.id) ?: step.main
                val isLastStep = stepIndex == block.steps.lastIndex
                val inRest = restCountdownSec != null || awaitingNext
                val pages = block.steps.mapIndexed { i, s ->
                    StepPage(
                        header = stepHeader(block, s),
                        exerciseName = s.exercise.exerciseName,
                        main = findById(s.main.id) ?: s.main,
                        drops = s.drops.map { findById(it.id) ?: it },
                        round = s.round,
                        status = when {
                            i == stepIndex && inRest -> StepStatus.JustDone
                            i == stepIndex && isRunning -> StepStatus.Running
                            i == stepIndex -> StepStatus.Current
                            i == stepIndex + 1 -> StepStatus.Next
                            isStepDone(s) -> StepStatus.Done
                            else -> StepStatus.Upcoming
                        },
                    )
                }
                ResistanceExerciseScreen(
                    pages = pages,
                    viewIndex = viewIndex.coerceIn(0, pages.lastIndex),
                    isRunning = isRunning,
                    restCountdownSec = restCountdownSec,
                    awaitingNext = awaitingNext,
                    nextUpLabel = nextUpLabel(blocks, blockIndex, stepIndex, ::findById),
                    onUpdateSet = { updateSet(it) },
                    onStartSet = {
                        viewIndex = stepIndex
                        isRunning = true
                    },
                    onStopSet = {
                        viewIndex = stepIndex
                        isRunning = false
                        completeStep(step)
                        applyStatuses(block)
                        val restSec = restSeconds(main)
                        if (isLastStep || restSec <= 0) {
                            awaitingNext = true
                        } else {
                            val start = System.currentTimeMillis()
                            restStartMs = start
                            restDeadlineMs = start + restSec * 1000L
                            restCountdownSec = restSec
                        }
                    },
                    onNext = {
                        val current = findById(step.main.id)
                        if (current != null) {
                            val restSec = if (awaitingNext) {
                                if (isLastStep) restSeconds(current) else 0
                            } else {
                                val startMs = restStartMs ?: System.currentTimeMillis()
                                ((System.currentTimeMillis() - startMs) / 1000L).toInt()
                            }
                            updateSet(current.copy(actualRest = restInUnit(restSec, current.restUnit)))
                        }
                        if (isLastStep) {
                            finishBlock(block)
                        } else {
                            clearRest()
                            stepIndex++
                            viewIndex = stepIndex
                        }
                    },
                    onSwipeLeft = {
                        if (viewIndex < block.steps.lastIndex) viewIndex++
                    },
                    onSwipeRight = {
                        if (viewIndex > 0) viewIndex-- else requestExit()
                    },
                )
            }
        }
        "end" -> {
            ResistanceEndScreen(
                initialTotalMinutes = mutableSets
                    .groupBy { it.exerciseId }
                    .values
                    .sumOf { rows -> rows.firstOrNull()?.actualTotalDuration ?: 0 } / 60,
                onSave = { _ ->
                    // Per-set duration is not tracked for resistance; exercise time lives in actualTotalDuration.
                    onSave(mutableSets.map { it.copy(actualDuration = null) })
                },
                onCancel = onBack
            )
        }
    }
}

private fun blockSubtitle(block: ResistanceBlock, doneSteps: Int): String {
    val shape = if (block.isSuperset) {
        "${block.exercises.size} exercises · ${block.totalRounds} rounds"
    } else {
        val exercise = block.exercises.first()
        val sets = exercise.mains.size
        val dropNote = when {
            exercise.drops.isEmpty() -> null
            exercise.drops.keys == setOf(exercise.mains.last().setNumber) -> "drop on last"
            else -> "${exercise.drops.size} drop sets"
        }
        listOfNotNull("$sets set${if (sets != 1) "s" else ""}", dropNote).joinToString(" · ")
    }
    val progress = if (doneSteps > 0) "$doneSteps/${block.steps.size} logged" else null
    return listOfNotNull(shape, progress).joinToString("\n")
}

private fun stepHeader(block: ResistanceBlock, step: ResistanceStep): String {
    val badge = block.badge(step)
    return if (badge != null) {
        "$badge · Round ${step.round}/${block.totalRounds}"
    } else {
        "Set ${step.round}/${step.exercise.mains.size}"
    }
}

private fun nextUpLabel(
    blocks: List<ResistanceBlock>,
    blockIndex: Int,
    stepIndex: Int,
    findById: (String) -> WorkoutSet?,
): String {
    val block = blocks[blockIndex]
    val next = block.steps.getOrNull(stepIndex + 1)
    if (next == null) {
        val nextBlock = blocks.getOrNull(blockIndex + 1) ?: return "Next: finish workout"
        return "Next: ${nextBlock.title}"
    }
    val row = findById(next.main.id) ?: next.main
    val target = listOfNotNull(
        (row.actualLoad ?: row.targetLoad)?.let { formatLoadForWear(it) },
        (row.actualReps ?: row.targetReps)?.toString(),
    ).joinToString(" × ")
    val who = block.badge(next)?.let { "$it ${next.exercise.exerciseName}" } ?: "Set ${next.round}"
    return listOf("Next: $who", target).filter { it.isNotBlank() }.joinToString(" · ")
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
                    backgroundColor = WorkoutGreen,
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
