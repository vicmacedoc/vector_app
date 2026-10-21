package com.vm.vector.ui.screens.routine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.core.models.ActivityCompletion
import com.vm.core.models.ActivityEntry
import com.vm.core.models.ActivityLabel
import com.vm.core.models.ActivityType
import com.vm.core.models.cappedDoneMinutesTowardPlan
import com.vm.core.models.effectiveEndTime
import com.vm.core.models.effectiveStartTime
import com.vm.core.models.plannedDurationMinutes
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import java.util.Locale

private const val MINUTES_PER_DAY = 24 * 60

data class DonutLegendRow(
    val name: String,
    val color: Color,
    val doneHours: Double,
    val plannedHours: Double,
)

data class SleepClockArc(
    val startMinute: Int,
    val endMinute: Int,
)

@Composable
fun BehaviorDonutSection(
    activities: List<ActivityEntry>,
    labels: List<ActivityLabel>,
    plannedSleepArcs: List<SleepClockArc>,
    doneSleepArcs: List<SleepClockArc>,
    modifier: Modifier = Modifier,
) {
    var showLegend by remember { mutableStateOf(false) }
    val labelById = remember(labels) { labels.associateBy { it.id } }
    val legend = remember(activities, labels, plannedSleepArcs, doneSleepArcs) {
        buildLegend(
            activities = activities,
            labelById = labelById,
            plannedSleepArcs = plannedSleepArcs,
            doneSleepArcs = doneSleepArcs,
        )
    }
    val plannedSegments = remember(activities, labels, plannedSleepArcs) {
        buildSegments(
            activities = activities.filter { it.type == ActivityType.PLANNED },
            labelById = labelById,
            sleepArcs = plannedSleepArcs,
            includeSleep = true,
            useEffectiveTimes = false,
        )
    }
    val doneSegments = remember(activities, labels, doneSleepArcs) {
        buildSegments(
            activities = activities.filter { it.completion == ActivityCompletion.DONE },
            labelById = labelById,
            sleepArcs = doneSleepArcs,
            includeSleep = true,
            useEffectiveTimes = true,
        )
    }
    val plannedDoneStats = remember(activities) { computePlannedDoneStats(activities) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "24h Activities",
            color = PureBlack,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 8.dp),
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .clickable { showLegend = true },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(220.dp)) {
                val strokeOuter = size.minDimension * 0.12f
                val strokeInner = size.minDimension * 0.12f
                val gap = size.minDimension * 0.04f
                val outerRadius = (size.minDimension - strokeOuter) / 2f
                val innerRadius = outerRadius - strokeOuter / 2f - gap - strokeInner / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                fun drawRing(segments: List<DonutSegment>, radius: Float, stroke: Float) {
                    segments.forEach { seg ->
                        val startAngle = minutesToAngle(seg.startMinute)
                        val sweep = (seg.endMinute - seg.startMinute) / MINUTES_PER_DAY.toFloat() * 360f
                        if (sweep <= 0f) return@forEach
                        drawArc(
                            color = seg.color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2f, radius * 2f),
                            style = Stroke(width = stroke, cap = StrokeCap.Butt),
                        )
                    }
                }

                drawRing(doneSegments, outerRadius, strokeOuter)
                drawRing(plannedSegments, innerRadius, strokeInner)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = fmtHoursLabel(plannedDoneStats.doneHours),
                    color = PureBlack,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "${plannedDoneStats.percentOfPlanned}%",
                    color = PureBlack.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            text = "Tap for legend · Inner: Planned · Outer: Done",
            color = PureBlack.copy(alpha = 0.55f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    if (showLegend) {
        AlertDialog(
            onDismissRequest = { showLegend = false },
            containerColor = PureWhite,
            title = { Text("Behavior legend", color = PureBlack) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    legend.forEach { row ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(row.color, CircleShape)
                                    .border(1.dp, PureBlack.copy(alpha = 0.2f), CircleShape),
                            )
                            Text(
                                text = "${row.name}  ${fmtHours(row.doneHours)}/${fmtHours(row.plannedHours)} h",
                                color = PureBlack,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLegend = false }) {
                    Text("Close", color = PureBlack)
                }
            },
        )
    }
}

private data class DonutSegment(
    val startMinute: Int,
    val endMinute: Int,
    val color: Color,
    val labelKey: String,
    /** Lower values draw first; latest activity timestamps draw on top. */
    val drawOrder: Long,
)

private data class PlannedDoneStats(
    val doneHours: Double,
    val plannedHours: Double,
    val percentOfPlanned: Int,
)

/**
 * Planned completion center metric:
 * - Denominator: sum of original planned durations (PLANNED activities).
 * - Numerator: for PLANNED+DONE, min(effective duration, planned duration).
 */
private fun computePlannedDoneStats(activities: List<ActivityEntry>): PlannedDoneStats {
    val planned = activities.filter { it.type == ActivityType.PLANNED }
    val plannedMinutes = planned.sumOf { it.plannedDurationMinutes() ?: 0 }
    val doneMinutes = planned
        .filter { it.completion == ActivityCompletion.DONE }
        .sumOf { it.cappedDoneMinutesTowardPlan() }
    val plannedHours = hoursFromMinutes(plannedMinutes)
    val doneHours = hoursFromMinutes(doneMinutes)
    val percent = if (plannedMinutes <= 0) {
        0
    } else {
        ((doneMinutes.toDouble() / plannedMinutes.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }
    return PlannedDoneStats(doneHours = doneHours, plannedHours = plannedHours, percentOfPlanned = percent)
}

private fun minutesToAngle(minute: Int): Float {
    // Midnight at top (−90°), clockwise
    return -90f + (minute.coerceIn(0, MINUTES_PER_DAY) / MINUTES_PER_DAY.toFloat()) * 360f
}

private fun parseHhMmToMinutes(time: String?): Int? {
    if (time.isNullOrBlank()) return null
    val parts = time.trim().split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return (h.coerceIn(0, 23) * 60 + m.coerceIn(0, 59)).coerceIn(0, MINUTES_PER_DAY - 1)
}

private fun splitRange(start: Int, end: Int): List<Pair<Int, Int>> {
    return if (end > start) {
        listOf(start to end)
    } else if (end < start) {
        // Overnight: start → midnight, midnight → end
        listOf(start to MINUTES_PER_DAY, 0 to end)
    } else {
        emptyList()
    }
}

private fun buildSegments(
    activities: List<ActivityEntry>,
    labelById: Map<String, ActivityLabel>,
    sleepArcs: List<SleepClockArc>,
    includeSleep: Boolean,
    useEffectiveTimes: Boolean,
): List<DonutSegment> {
    val segments = mutableListOf<DonutSegment>()
    if (includeSleep) {
        val sleepColor = Color(ActivityLabel.SLEEP_COLOR_ARGB)
        sleepArcs.forEach { arc ->
            splitRange(arc.startMinute, arc.endMinute).forEach { (s, e) ->
                segments += DonutSegment(
                    startMinute = s,
                    endMinute = e,
                    color = sleepColor,
                    labelKey = ActivityLabel.SLEEP_LABEL_NAME,
                    drawOrder = Long.MIN_VALUE,
                )
            }
        }
    }
    activities.forEach { a ->
        val startHhMm = if (useEffectiveTimes) a.effectiveStartTime() else a.startTime
        val endHhMm = if (useEffectiveTimes) a.effectiveEndTime() else a.endTime
        val start = parseHhMmToMinutes(startHhMm) ?: return@forEach
        val end = parseHhMmToMinutes(endHhMm) ?: return@forEach
        val label = labelById[a.labelId]
        val color = Color(label?.colorArgb ?: 0xFF90A4AE.toInt())
        val key = label?.name ?: "Unknown"
        splitRange(start, end).forEach { (s, e) ->
            segments += DonutSegment(
                startMinute = s,
                endMinute = e,
                color = color,
                labelKey = key,
                drawOrder = a.timestamp,
            )
        }
    }
    return segments.sortedWith(compareBy({ it.drawOrder }, { it.startMinute }))
}

private fun rangeMinutes(start: Int, end: Int): Int {
    return if (end >= start) end - start else (MINUTES_PER_DAY - start) + end
}

private fun hoursFromMinutes(minutes: Int): Double =
    (minutes / 60.0).let { kotlin.math.round(it * 10.0) / 10.0 }

private fun buildLegend(
    activities: List<ActivityEntry>,
    labelById: Map<String, ActivityLabel>,
    plannedSleepArcs: List<SleepClockArc>,
    doneSleepArcs: List<SleepClockArc>,
): List<DonutLegendRow> {
    val plannedMins = mutableMapOf<String, Int>()
    val doneMins = mutableMapOf<String, Int>()
    val colors = mutableMapOf<String, Color>()

    fun add(map: MutableMap<String, Int>, key: String, start: String, end: String) {
        val s = parseHhMmToMinutes(start) ?: return
        val e = parseHhMmToMinutes(end) ?: return
        map[key] = (map[key] ?: 0) + rangeMinutes(s, e)
    }

    activities.forEach { a ->
        val label = labelById[a.labelId] ?: return@forEach
        colors[label.name] = Color(label.colorArgb)
        if (a.type == ActivityType.PLANNED) {
            add(plannedMins, label.name, a.startTime, a.endTime)
        }
        if (a.completion == ActivityCompletion.DONE) {
            if (a.type == ActivityType.PLANNED) {
                doneMins[label.name] =
                    (doneMins[label.name] ?: 0) + a.cappedDoneMinutesTowardPlan()
            } else {
                add(doneMins, label.name, a.effectiveStartTime(), a.effectiveEndTime())
            }
        }
    }

    if (plannedSleepArcs.isNotEmpty()) {
        colors[ActivityLabel.SLEEP_LABEL_NAME] = Color(ActivityLabel.SLEEP_COLOR_ARGB)
        plannedSleepArcs.forEach { arc ->
            plannedMins[ActivityLabel.SLEEP_LABEL_NAME] =
                (plannedMins[ActivityLabel.SLEEP_LABEL_NAME] ?: 0) + rangeMinutes(arc.startMinute, arc.endMinute)
        }
    }

    if (doneSleepArcs.isNotEmpty()) {
        colors[ActivityLabel.SLEEP_LABEL_NAME] = Color(ActivityLabel.SLEEP_COLOR_ARGB)
        doneSleepArcs.forEach { arc ->
            doneMins[ActivityLabel.SLEEP_LABEL_NAME] =
                (doneMins[ActivityLabel.SLEEP_LABEL_NAME] ?: 0) + rangeMinutes(arc.startMinute, arc.endMinute)
        }
    }

    val plannedCovered = plannedMins.values.sum().coerceAtMost(MINUTES_PER_DAY)
    val doneCovered = doneMins.values.sum().coerceAtMost(MINUTES_PER_DAY)
    val notInformedPlanned = (MINUTES_PER_DAY - plannedCovered).coerceAtLeast(0)
    val notInformedDone = (MINUTES_PER_DAY - doneCovered).coerceAtLeast(0)

    val keys = (plannedMins.keys + doneMins.keys).toSortedSet()
    val rows = keys.map { name ->
        DonutLegendRow(
            name = name,
            color = colors[name] ?: Color.Gray,
            doneHours = hoursFromMinutes(doneMins[name] ?: 0),
            plannedHours = hoursFromMinutes(plannedMins[name] ?: 0),
        )
    }.toMutableList()
    rows += DonutLegendRow(
        name = "Not Informed",
        color = Color.Transparent,
        doneHours = hoursFromMinutes(notInformedDone),
        plannedHours = hoursFromMinutes(notInformedPlanned),
    )
    return rows
}

private fun fmtHours(h: Double): String =
    String.format(Locale.US, "%.1f", h)

private fun fmtHoursLabel(h: Double): String =
    if (h == h.toLong().toDouble()) {
        "${h.toLong()}h"
    } else {
        String.format(Locale.US, "%.1fh", h)
    }
