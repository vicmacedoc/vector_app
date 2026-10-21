package com.vm.vector.presentation.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.vm.core.models.WorkoutSet
import com.vm.core.ui.theme.DropSetOrange
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/** Where a step sits relative to the active one (the step the action button drives). */
enum class StepStatus(val label: String) {
    Done("Done"),
    JustDone("Just done"),
    Current("Now"),
    Running("In progress"),
    Next("Up next"),
    Upcoming("Upcoming"),
}

/** One swipeable page of a resistance block: a main set, its drops, and its status. */
data class StepPage(
    val header: String,
    val exerciseName: String,
    val main: WorkoutSet,
    val drops: List<WorkoutSet>,
    /** Superset round; dots of the same round sit closer together on the indicator. */
    val round: Int,
    val status: StepStatus,
)

private const val ROUND_GAP_UNITS = 1.8f
private const val MAX_DOT_SPACING_DEG = 6f
private const val MAX_ARC_SPAN_DEG = 70f

/**
 * The steps of a resistance block (main set + drops per page), swiped left/right. Values write
 * straight to the rows, so any step stays editable at any time. The action button always drives
 * the active step, whichever page is being viewed.
 */
@Composable
fun ResistanceExerciseScreen(
    pages: List<StepPage>,
    viewIndex: Int,
    isRunning: Boolean,
    restCountdownSec: Int?,
    /** Concluded with no countdown (last step or zero rest): show a plain "Next". */
    awaitingNext: Boolean,
    nextUpLabel: String?,
    onUpdateSet: (WorkoutSet) -> Unit,
    onStartSet: () -> Unit,
    onStopSet: () -> Unit,
    onNext: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inRest = restCountdownSec != null || awaitingNext

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .horizontalPageGesture(onSwipeLeft = onSwipeLeft, onSwipeRight = onSwipeRight)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = viewIndex,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { width -> direction * width / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { width -> -direction * width / 3 } + fadeOut())
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "resistanceStep",
            ) { index ->
                val page = pages.getOrNull(index) ?: return@AnimatedContent
                StepPageContent(
                    page = page,
                    nextUpLabel = nextUpLabel.takeIf { inRest && page.status == StepStatus.JustDone },
                    onUpdateSet = onUpdateSet,
                )
            }

            val actionLabel = when {
                inRest -> if (awaitingNext) "Next" else "Next (${restCountdownSec}s)"
                !isRunning -> "Start Set"
                else -> "Stop Set"
            }
            val actionColor = when {
                inRest -> Color.Blue
                !isRunning -> WorkoutGreen
                else -> Color.Red
            }
            Button(
                onClick = {
                    when {
                        inRest -> onNext()
                        !isRunning -> onStartSet()
                        else -> onStopSet()
                    }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = actionColor, contentColor = Color.White),
                shape = RectangleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Text(text = actionLabel, style = MaterialTheme.typography.body2, color = Color.White)
            }
        }
        StepArcIndicator(pages = pages, viewIndex = viewIndex, modifier = Modifier.matchParentSize())
    }
}

@Composable
private fun StepPageContent(
    page: StepPage,
    nextUpLabel: String?,
    onUpdateSet: (WorkoutSet) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(top = 28.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = page.header,
            style = MaterialTheme.typography.title3,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        StatusTag(status = page.status)
        Text(
            text = page.exerciseName,
            style = MaterialTheme.typography.body2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            textAlign = TextAlign.Center,
        )
        page.main.timing?.takeIf { it.isNotBlank() }?.let { timing ->
            Text(
                text = "Timing: $timing",
                style = MaterialTheme.typography.caption1,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        if (nextUpLabel != null) {
            Text(
                text = nextUpLabel,
                style = MaterialTheme.typography.caption1,
                color = NextUpBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = TextAlign.Center,
            )
        }

        SetSteppers(set = page.main, showRpe = true, onUpdateSet = onUpdateSet)

        page.drops.forEach { drop ->
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DropSetOrange.copy(alpha = 0.6f))
            )
            Text(
                text = "Drop ${drop.dropNumber}",
                style = MaterialTheme.typography.caption1,
                color = DropSetOrange,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
            SetSteppers(set = drop, showRpe = false, onUpdateSet = onUpdateSet)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun StatusTag(status: StepStatus) {
    val color = statusColor(status)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 1.dp, bottom = 1.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Text(
            text = status.label.uppercase(Locale.US),
            style = MaterialTheme.typography.caption2.copy(letterSpacing = 0.8.sp),
            color = color,
        )
    }
}

@Composable
private fun statusColor(status: StepStatus, upcomingAlpha: Float = 0.55f): Color = when (status) {
    StepStatus.Done, StepStatus.JustDone -> DoneGreen
    StepStatus.Current, StepStatus.Running -> MaterialTheme.colors.onBackground
    StepStatus.Next -> NextUpBlue
    StepStatus.Upcoming -> MaterialTheme.colors.onBackground.copy(alpha = upcomingAlpha)
}

/**
 * Dots along the top edge, one per step, colored by status; the viewed step is larger and ringed.
 * Superset rounds are spaced apart so A1/A2 pairs read as groups.
 */
@Composable
private fun StepArcIndicator(pages: List<StepPage>, viewIndex: Int, modifier: Modifier = Modifier) {
    if (pages.size < 2) return
    val colors = pages.map { statusColor(it.status, upcomingAlpha = 0.3f) }
    val ringColor = MaterialTheme.colors.onBackground
    Canvas(modifier = modifier) {
        val offsets = FloatArray(pages.size)
        for (i in 1 until pages.size) {
            val gap = if (pages[i].round != pages[i - 1].round) ROUND_GAP_UNITS else 1f
            offsets[i] = offsets[i - 1] + gap
        }
        val span = offsets.last()
        val spacingDeg = minOf(MAX_DOT_SPACING_DEG, MAX_ARC_SPAN_DEG / span)
        val radius = minOf(size.width, size.height) / 2f - 9.dp.toPx()
        val dotRadius = 3.dp.toPx()
        val viewedRadius = 4.dp.toPx()
        pages.indices.forEach { i ->
            val angleRad = Math.toRadians((-90.0 + (offsets[i] - span / 2f) * spacingDeg))
            val position = Offset(
                x = center.x + radius * cos(angleRad).toFloat(),
                y = center.y + radius * sin(angleRad).toFloat(),
            )
            if (i == viewIndex) {
                drawCircle(color = colors[i], radius = viewedRadius, center = position)
                drawCircle(
                    color = ringColor,
                    radius = viewedRadius + 1.5.dp.toPx(),
                    center = position,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            } else {
                drawCircle(color = colors[i], radius = dotRadius, center = position)
            }
        }
    }
}

/** Displayed RPE when nothing is logged yet: planned RPE, else derived from planned RIR. */
fun defaultRpe(set: WorkoutSet): Double =
    set.actualRpe ?: set.targetRpe ?: set.targetRir?.let { (10 - it).coerceIn(0, 10).toDouble() } ?: 8.0

@Composable
private fun SetSteppers(
    set: WorkoutSet,
    showRpe: Boolean,
    onUpdateSet: (WorkoutSet) -> Unit,
) {
    if (set.targetLoad != null || set.actualLoad != null) {
        val load = set.actualLoad ?: set.targetLoad ?: 0.0
        val step = loadStep(set.unitLoad)
        StepperRow(
            label = "Load (${set.unitLoad})",
            value = formatLoadForWear(load),
            onMinus = { onUpdateSet(set.copy(actualLoad = (load - step).coerceIn(0.0, 999.0))) },
            onPlus = { onUpdateSet(set.copy(actualLoad = (load + step).coerceIn(0.0, 999.0))) },
        )
    }
    if (set.targetReps != null || set.actualReps != null) {
        val reps = set.actualReps ?: set.targetReps ?: 0
        StepperRow(
            label = "Reps",
            value = reps.toString(),
            onMinus = { onUpdateSet(set.copy(actualReps = (reps - 1).coerceIn(0, 999))) },
            onPlus = { onUpdateSet(set.copy(actualReps = (reps + 1).coerceIn(0, 999))) },
        )
    }
    if (set.targetRir != null || set.actualRir != null) {
        val rir = set.actualRir ?: set.targetRir ?: 0
        StepperRow(
            label = "RIR",
            value = rir.toString(),
            onMinus = { onUpdateSet(set.copy(actualRir = (rir - 1).coerceIn(0, 10))) },
            onPlus = { onUpdateSet(set.copy(actualRir = (rir + 1).coerceIn(0, 10))) },
        )
    }
    if (showRpe) {
        val rpe = defaultRpe(set)
        StepperRow(
            label = "RPE",
            value = formatHalfStep(rpe),
            onMinus = { onUpdateSet(set.copy(actualRpe = (rpe - 0.5).coerceIn(0.0, 10.0))) },
            onPlus = { onUpdateSet(set.copy(actualRpe = (rpe + 0.5).coerceIn(0.0, 10.0))) },
        )
    }
}

@Composable
internal fun StepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.body2,
        color = MaterialTheme.colors.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(onChange = onMinus, shape = shape) {
            Text("-", style = MaterialTheme.typography.title2)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.title3,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        StepperButton(onChange = onPlus, shape = shape) {
            Text("+", style = MaterialTheme.typography.title2)
        }
    }
}

private fun loadStep(unitLoad: String): Double =
    when (unitLoad.lowercase()) {
        "kg" -> 0.5
        else -> 1.0
    }

internal fun formatLoadForWear(v: Double): String {
    val rounded = round(v * 10.0) / 10.0
    return if (abs(rounded - rounded.toInt()) < 1e-6) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}

internal fun formatHalfStep(v: Double): String =
    if (abs(v - v.toInt()) < 1e-6) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
