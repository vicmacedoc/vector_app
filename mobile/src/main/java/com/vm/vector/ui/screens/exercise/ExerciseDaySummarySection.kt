package com.vm.vector.ui.screens.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vm.core.models.WorkoutSet
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.MoodColor5
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.analysis.ExerciseBucket
import com.vm.vector.data.analysis.ExerciseDayMath
import com.vm.vector.data.analysis.ExerciseDaySummary
import com.vm.vector.data.analysis.ExerciseGroup
import com.vm.vector.data.analysis.ExerciseProgress
import com.vm.vector.data.analysis.MuscleVolumeProgress
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

fun exerciseBucketIcon(bucket: ExerciseBucket): ImageVector = when (bucket) {
    ExerciseBucket.RESISTANCE -> Icons.Filled.FitnessCenter
    ExerciseBucket.RUN -> Icons.Filled.DirectionsRun
    ExerciseBucket.BIKE -> Icons.Filled.DirectionsBike
    ExerciseBucket.SWIM -> Icons.Filled.Pool
    ExerciseBucket.ENDURANCE -> Icons.Filled.Timer
    ExerciseBucket.OTHER -> Icons.Filled.Sports
}

fun exerciseGroupIcon(group: ExerciseGroup): ImageVector = when (group) {
    ExerciseGroup.RESISTANCE -> Icons.Filled.FitnessCenter
    ExerciseGroup.ENDURANCE -> Icons.Filled.Timer
    ExerciseGroup.OTHER -> Icons.Filled.Sports
}

/**
 * Day header for the Exercise calendar: total minutes and fixed Resistance / Endurance / Other
 * markers (done / planned). Tapping a marker opens [ExerciseGroupInfoDialog] for that category.
 * Recomputed from [sets] on every change.
 */
@Composable
fun ExerciseDaySummarySection(sets: List<WorkoutSet>, modifier: Modifier = Modifier) {
    val summary = remember(sets) { ExerciseDayMath.summarize(sets) }
    var selectedGroup by remember { mutableStateOf<ExerciseGroup?>(null) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SummaryMeter(
            label = "Exercise minutes",
            value = "${summary.minutes.done.roundToInt()} / ${summary.minutes.planned.roundToInt()} min",
            caption = minutesLeftLabel(summary.minutes),
            progress = summary.minutes,
            valueStyle = MaterialTheme.typography.headlineSmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            summary.byGroup.forEach { entry ->
                SummaryMeter(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedGroup = entry.group }
                        .padding(4.dp),
                    label = entry.group.label,
                    icon = exerciseGroupIcon(entry.group),
                    value = "${entry.minutes.done.roundToInt()} / ${entry.minutes.planned.roundToInt()}",
                    caption = "min",
                    progress = entry.minutes,
                    valueStyle = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
    selectedGroup?.let { group ->
        ExerciseGroupInfoDialog(
            group = group,
            summary = summary,
            onDismiss = { selectedGroup = null },
        )
    }
}

/**
 * More information for one category: Resistance shows training volume per muscle, Endurance the
 * minutes per sport, Other the minutes per activity. Empty categories say nothing was set.
 */
@Composable
private fun ExerciseGroupInfoDialog(
    group: ExerciseGroup,
    summary: ExerciseDaySummary,
    onDismiss: () -> Unit,
) {
    val setsFormat = remember { DecimalFormat("#.#") }
    val groupMinutes = summary.byGroup.first { it.group == group }.minutes
    val hasActivities = summary.activities.any { it.bucket.group == group }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
        ) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "More information",
                        style = MaterialTheme.typography.titleLarge,
                        color = PureBlack,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = PureBlack)
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DetailRow(
                        icon = exerciseGroupIcon(group),
                        title = group.label,
                        value = minutesValue(groupMinutes),
                        progress = groupMinutes,
                        emphasized = true,
                    )
                    when {
                        !hasActivities -> InfoText("No ${group.label.lowercase(Locale.US)} activity set for this day.")
                        group == ExerciseGroup.RESISTANCE -> if (summary.muscles.isEmpty()) {
                            InfoText("No muscle targets set for today's resistance exercises.")
                        } else {
                            TrainingVolumeHeader()
                            MuscleVolumeGrid(summary.muscles, setsFormat)
                        }
                        group == ExerciseGroup.ENDURANCE -> summary.byBucket
                            .filter { it.bucket.group == ExerciseGroup.ENDURANCE }
                            .forEach { sport ->
                                DetailRow(
                                    icon = exerciseBucketIcon(sport.bucket),
                                    title = sport.bucket.label,
                                    value = minutesValue(sport.minutes),
                                    progress = sport.minutes,
                                )
                            }
                        else -> summary.activities
                            .filter { it.bucket.group == ExerciseGroup.OTHER }
                            .groupBy { it.name }
                            .forEach { (name, rows) ->
                                val minutes = rows.fold(ExerciseProgress.ZERO) { acc, row -> acc + row.minutes }
                                DetailRow(
                                    icon = exerciseBucketIcon(ExerciseBucket.OTHER),
                                    title = name,
                                    value = minutesValue(minutes),
                                    progress = minutes,
                                )
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = SlateGray)
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    title: String,
    value: String,
    progress: ExerciseProgress,
    emphasized: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NavyDeep,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(if (emphasized) 18.dp else 16.dp),
            )
            Text(
                text = title,
                style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                color = PureBlack,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = value, style = MaterialTheme.typography.labelMedium, color = SlateGray, maxLines = 1)
        }
        SummaryProgressBar(progress)
    }
}


@Composable
private fun TrainingVolumeHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.FitnessCenter,
            contentDescription = null,
            tint = NavyDeep,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Training volume (sets)",
            style = MaterialTheme.typography.labelMedium,
            color = SlateGray,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun MuscleVolumeGrid(muscles: List<MuscleVolumeProgress>, setsFormat: DecimalFormat) {
    muscles.chunked(2).forEach { pair ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            pair.forEach { muscle ->
                MuscleMeter(
                    modifier = Modifier.weight(1f),
                    name = muscleLabel(muscle.muscle),
                    value = "${setsFormat.format(muscle.sets.done)} / ${setsFormat.format(muscle.sets.planned)}",
                    progress = muscle.sets,
                )
            }
            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun minutesValue(progress: ExerciseProgress): String =
    "${progress.done.roundToInt()} / ${progress.planned.roundToInt()} min"

@Composable
private fun SummaryMeter(
    label: String,
    value: String,
    caption: String,
    progress: ExerciseProgress,
    valueStyle: TextStyle,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NavyDeep,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(16.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = SlateGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(text = value, style = valueStyle, color = PureBlack, textAlign = TextAlign.Center, maxLines = 1)
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = SlateGray,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        SummaryProgressBar(progress)
    }
}

@Composable
private fun MuscleMeter(
    name: String,
    value: String,
    progress: ExerciseProgress,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = PureBlack,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = value, style = MaterialTheme.typography.labelMedium, color = SlateGray, maxLines = 1)
        }
        SummaryProgressBar(progress)
    }
}

@Composable
private fun SummaryProgressBar(progress: ExerciseProgress) {
    val ratio = progressRatio(progress)
    val fill = ratio.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(OffWhite),
    ) {
        if (fill > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .fillMaxHeight()
                    .background(progressColor(ratio)),
            )
        }
    }
}

private fun progressRatio(progress: ExerciseProgress): Float = when {
    progress.planned > 0.0 -> (progress.done / progress.planned).toFloat()
    progress.done > 0.0 -> 1.01f
    else -> 0f
}

private fun progressColor(ratio: Float): Color = when {
    ratio > 1.001f -> ElectricBlue
    ratio >= 0.999f -> MoodColor5
    else -> NavyDeep
}

private fun minutesLeftLabel(minutes: ExerciseProgress): String {
    val left = (minutes.planned - minutes.done).roundToInt()
    return when {
        minutes.planned <= 0.0 && minutes.done <= 0.0 -> "Nothing planned"
        left > 0 -> "$left min left"
        left < 0 -> "${abs(left)} min over"
        else -> "All done"
    }
}

private fun muscleLabel(raw: String): String =
    raw.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
