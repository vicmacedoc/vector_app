package com.vm.vector.presentation.routine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.MoodColor5
import com.vm.core.ui.theme.PriorityHigh
import com.vm.core.ui.theme.PureWhite
import com.vm.core.wear.ActivitiesForWearPayload
import com.vm.core.wear.WearActivityCompletion
import com.vm.core.wear.WearRoutineActivity
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

private const val MINUTES_PER_DAY = 24 * 60
private val SleepGray = Color(0xFF78909C)

@Composable
fun RoutineDayScreen(
    payload: ActivitiesForWearPayload?,
    isLoading: Boolean,
    error: String?,
    onCycleCompletion: (WearRoutineActivity) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
    val all = payload?.activities.orEmpty()
    val tappable = all.filter { !it.isSleep }
    var nowMinute by remember { mutableIntStateOf(currentMinuteOfDay()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMinute = currentMinuteOfDay()
        }
    }
    val current = tappable.firstOrNull { it.containsMinute(nowMinute) }
    val doneCount = tappable.count { it.completion == WearActivityCompletion.DONE }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(horizontal = 8.dp)
        ) {
            item {
                Text(
                    text = "Routine",
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
            }
            when {
                isLoading && payload == null -> item {
                    Text("Loading…", style = MaterialTheme.typography.body2)
                }
                error != null && payload == null -> item {
                    Text(error, style = MaterialTheme.typography.body2, color = DeleteRed)
                }
                else -> {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            DayRing(
                                activities = all,
                                nowMinute = nowMinute,
                                modifier = Modifier.size(124.dp)
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(76.dp)
                            ) {
                                Text(
                                    text = "$doneCount/${tappable.size}",
                                    style = MaterialTheme.typography.title3,
                                    color = MaterialTheme.colors.onBackground
                                )
                                Text(
                                    text = current?.title ?: "Free",
                                    style = MaterialTheme.typography.caption2,
                                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    if (tappable.isEmpty()) {
                        item {
                            Text(
                                text = "No activities today",
                                style = MaterialTheme.typography.body2,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    items(tappable, key = { it.id }) { activity ->
                        ActivityRow(
                            activity = activity,
                            isCurrent = activity.id == current?.id,
                            onClick = { onCycleCompletion(activity) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayRing(
    activities: List<WearRoutineActivity>,
    nowMinute: Int,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colors.surface
    Canvas(modifier = modifier) {
        val strokePx = 10.dp.toPx()
        val inset = strokePx / 2 + 2.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx)
        )
        for (activity in activities) {
            val start = parseHhMm(activity.start) ?: continue
            val end = parseHhMm(activity.end) ?: continue
            val duration = if (end >= start) end - start else MINUTES_PER_DAY - start + end
            if (duration <= 0) continue
            drawArc(
                color = arcColor(activity),
                startAngle = minuteToAngle(start),
                sweepAngle = duration * 360f / MINUTES_PER_DAY,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Butt)
            )
        }
        val radius = arcSize.width / 2
        val center = Offset(size.width / 2, size.height / 2)
        for (hour in listOf(0, 6, 12, 18)) {
            val angle = Math.toRadians(minuteToAngle(hour * 60).toDouble())
            val outer = radius + strokePx / 2
            val inner = radius - strokePx / 2
            drawLine(
                color = PureWhite.copy(alpha = 0.35f),
                start = Offset(center.x + inner * cos(angle).toFloat(), center.y + inner * sin(angle).toFloat()),
                end = Offset(center.x + outer * cos(angle).toFloat(), center.y + outer * sin(angle).toFloat()),
                strokeWidth = 1.dp.toPx()
            )
        }
        val nowAngle = Math.toRadians(minuteToAngle(nowMinute).toDouble())
        drawCircle(
            color = PureWhite,
            radius = strokePx * 0.45f,
            center = Offset(
                center.x + radius * cos(nowAngle).toFloat(),
                center.y + radius * sin(nowAngle).toFloat()
            )
        )
    }
}

@Composable
private fun ActivityRow(
    activity: WearRoutineActivity,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val statusColor = completionColor(activity.completion)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isCurrent) MaterialTheme.colors.surface else Color.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(activity.colorArgb))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.title,
                style = MaterialTheme.typography.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (activity.completion == WearActivityCompletion.NONE) {
                    MaterialTheme.colors.onBackground
                } else {
                    statusColor
                },
                textDecoration = if (
                    activity.completion == WearActivityCompletion.NOT_DONE ||
                    activity.completion == WearActivityCompletion.CANCELLED
                ) TextDecoration.LineThrough else null
            )
            Text(
                text = "${activity.start}–${activity.end}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = when (activity.completion) {
                WearActivityCompletion.DONE -> "✓"
                WearActivityCompletion.NOT_DONE -> "✕"
                WearActivityCompletion.CANCELLED -> "–"
                WearActivityCompletion.NA -> "n/a"
                WearActivityCompletion.NONE -> "·"
            },
            style = MaterialTheme.typography.body1,
            color = if (activity.completion == WearActivityCompletion.NONE) {
                MaterialTheme.colors.onBackground
            } else {
                statusColor
            }
        )
    }
}

/** Watch cycle: blank → done → not done → blank (matches Food / Nutrition tap-cycle). */
fun WearActivityCompletion.nextOnWatch(): WearActivityCompletion = when (this) {
    WearActivityCompletion.NONE -> WearActivityCompletion.DONE
    WearActivityCompletion.DONE -> WearActivityCompletion.NOT_DONE
    else -> WearActivityCompletion.NONE
}

private fun completionColor(completion: WearActivityCompletion): Color = when (completion) {
    WearActivityCompletion.DONE -> MoodColor5
    WearActivityCompletion.NOT_DONE -> PriorityHigh
    WearActivityCompletion.CANCELLED -> IconGray
    WearActivityCompletion.NA -> SleepGray
    WearActivityCompletion.NONE -> PureWhite
}

private fun arcColor(activity: WearRoutineActivity): Color {
    val base = Color(activity.colorArgb)
    return when {
        activity.isSleep -> base.copy(alpha = 0.6f)
        activity.completion == WearActivityCompletion.DONE -> base
        activity.completion == WearActivityCompletion.NONE -> base.copy(alpha = 0.55f)
        else -> base.copy(alpha = 0.25f)
    }
}

private fun WearRoutineActivity.containsMinute(minute: Int): Boolean {
    val s = parseHhMm(start) ?: return false
    val e = parseHhMm(end) ?: return false
    return if (e >= s) minute in s until e else minute >= s || minute < e
}

/** 00:00 at the top, clockwise. */
private fun minuteToAngle(minute: Int): Float = minute * 360f / MINUTES_PER_DAY - 90f

private fun currentMinuteOfDay(): Int {
    val cal = Calendar.getInstance()
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

private fun parseHhMm(time: String?): Int? {
    if (time.isNullOrBlank()) return null
    val parts = time.trim().split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h.coerceIn(0, 23) * 60 + m.coerceIn(0, 59)
}
