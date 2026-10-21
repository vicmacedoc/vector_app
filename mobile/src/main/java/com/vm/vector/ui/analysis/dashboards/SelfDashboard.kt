package com.vm.vector.ui.analysis.dashboards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.core.ui.theme.moodColorForLevel
import com.vm.vector.data.analysis.SelfData
import com.vm.vector.data.analysis.SelfScaleSeries
import com.vm.vector.ui.analysis.AnalysisDeltaPill
import com.vm.vector.ui.analysis.AnalysisEmptyState
import com.vm.vector.ui.analysis.AnalysisHeroStat
import com.vm.vector.ui.analysis.AnalysisLegendRow
import com.vm.vector.ui.analysis.AnalysisSection
import com.vm.vector.ui.analysis.AnalysisStatRow
import com.vm.vector.ui.analysis.AnalysisTheme
import com.vm.vector.ui.analysis.formatPercentOrDash
import com.vm.vector.ui.analysis.formatStat
import com.vm.vector.ui.analysis.formatStatOrDash
import com.vm.vector.ui.analysis.charts.AnalysisHeatmap
import com.vm.vector.ui.analysis.charts.AnalysisLineChart
import com.vm.vector.ui.analysis.charts.AnalysisRankBars
import com.vm.vector.ui.analysis.charts.HeatmapRow
import com.vm.vector.ui.analysis.charts.LineLayer
import com.vm.vector.ui.analysis.charts.RankBarItem
import com.vm.vector.ui.analysis.charts.shortDate
import java.util.Locale
import kotlin.math.abs

/** Every scale fits, so the ranking never hides one. */
private const val SelfScaleRankRows = 12

@Composable
fun SelfDashboard(data: SelfData, modifier: Modifier = Modifier) {
    if (data.loggedDays == 0) {
        AnalysisEmptyState("No self entries in this range.", modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        AnalysisSection(
            title = "Affect",
            subtitle = "Mood, energy and stress on the same 1–5 scale."
        ) {
            AnalysisHeroStat(
                value = formatStatOrDash(data.moodAverage),
                unit = "/ 5 mood",
                caption = "${data.loggedDays} of ${data.dates.size} days logged",
                delta = data.moodDelta
            )
            AnalysisLineChart(
                dates = data.dates,
                layers = listOfNotNull(
                    LineLayer("Mood", data.mood, AnalysisTheme.AccentLine, filled = true),
                    LineLayer("Energy", data.energy, AnalysisTheme.NavyAccent),
                    LineLayer("Stress", data.stress, AnalysisTheme.Negative),
                    LineLayer(
                        "Trend",
                        data.moodTrend,
                        AnalysisTheme.TextSecondary,
                        dashed = true,
                        strokeWidth = 2f
                    ),
                ),
                yLabel = "level",
                modifier = Modifier.padding(top = 14.dp)
            )
            AnalysisLegendRow(
                entries = listOf(
                    "Mood" to AnalysisTheme.AccentLine,
                    "Energy" to AnalysisTheme.NavyAccent,
                    "Stress" to AnalysisTheme.Negative,
                    "Trend" to AnalysisTheme.TextSecondary,
                ),
                modifier = Modifier.padding(top = 10.dp)
            )
            AnalysisStatRow(
                stats = listOf(
                    "Mood" to formatStatOrDash(data.moodAverage),
                    "Energy" to formatStatOrDash(data.energyAverage),
                    "Stress" to formatStatOrDash(data.stressAverage),
                ),
                modifier = Modifier.padding(top = 14.dp)
            )
        }

        if (data.moodRows.isNotEmpty()) {
            AnalysisSection(
                title = "Mood calendar",
                trailing = data.moodAverage?.let { "${formatStat(it)} / 5" },
                subtitle = "One row per week, Monday through Sunday."
            ) {
                AnalysisHeatmap(
                    rows = data.moodRows.map { row ->
                        HeatmapRow(
                            label = shortDate(row.mondayIso),
                            cells = row.cells.map { level ->
                                if (level == null) AnalysisTheme.Empty else moodColorForLevel(level)
                            }
                        )
                    },
                    labelWidth = 52.dp,
                    cellHeight = 22.dp,
                    startCaption = "Mon",
                    endCaption = "Sun"
                )
                AnalysisLegendRow(
                    entries = (1..5).map { "$it" to moodColorForLevel(it) } +
                        listOf("None" to AnalysisTheme.Empty),
                    modifier = Modifier.padding(top = 12.dp)
                )
                if (data.moodDistribution.size == 5) {
                    AnalysisStatRow(
                        stats = data.moodDistribution.mapIndexed { i, count ->
                            "Mood ${i + 1}" to count.toString()
                        },
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }
        }

        val ratedScales = data.scales.filter { it.daysRated > 0 }
        if (ratedScales.isNotEmpty()) {
            AnalysisSection(
                title = "Scales",
                subtitle = "One row per scale, one cell per day. Every scale runs worst to best."
            ) {
                AnalysisHeatmap(
                    rows = ratedScales.map { scale ->
                        HeatmapRow(
                            label = scale.label,
                            cells = scale.values.map { value ->
                                if (value == null) {
                                    AnalysisTheme.Empty
                                } else {
                                    moodColorForLevel(value.toInt())
                                }
                            },
                            trailing = formatStatOrDash(scale.average)
                        )
                    },
                    labelWidth = 76.dp,
                    cellHeight = 18.dp
                )
                AnalysisLegendRow(
                    entries = (1..5).map { "$it" to moodColorForLevel(it) } +
                        listOf("None" to AnalysisTheme.Empty),
                    modifier = Modifier.padding(top = 12.dp)
                )
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ratedScales
                        .sortedByDescending { it.average ?: 0.0 }
                        .forEach { SelfScaleRow(it) }
                }
                Text(
                    text = "Average level and how it moved between the first and second half of the range.",
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        if (data.moodCorrelations.isNotEmpty()) {
            AnalysisSection(
                title = "Tracks with mood",
                subtitle = "Correlation with mood across the days both were rated."
            ) {
                AnalysisRankBars(
                    items = data.moodCorrelations.map { correlation ->
                        RankBarItem(
                            label = correlation.label,
                            value = abs(correlation.r),
                            valueLabel = signedTwoDecimals(correlation.r)
                        )
                    },
                    maxRows = SelfScaleRankRows
                )
                Text(
                    text = "Bar length is the strength; a negative value means the two move apart. " +
                        "Correlation is not causation, and short ranges are noisy.",
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        val hasCrossDomain = data.moodOnGoodSleep != null || data.moodOnPoorSleep != null ||
            data.moodOnTrainingDays != null || data.moodOnHighHabitDays != null
        if (hasCrossDomain) {
            AnalysisSection(
                title = "What moves mood",
                subtitle = "Average mood split by sleep, training and habit adherence."
            ) {
                AnalysisStatRow(
                    stats = listOf(
                        "Slept ${formatStat(data.goodSleepThresholdHours)}h+" to
                            formatStatOrDash(data.moodOnGoodSleep),
                        "Short sleep" to formatStatOrDash(data.moodOnPoorSleep),
                        "Gap" to gap(data.moodOnGoodSleep, data.moodOnPoorSleep),
                    )
                )
                AnalysisStatRow(
                    stats = listOf(
                        "Trained" to formatStatOrDash(data.moodOnTrainingDays),
                        "Rest day" to formatStatOrDash(data.moodOnRestDays),
                        "Day after" to formatStatOrDash(data.moodDayAfterTraining),
                    ),
                    modifier = Modifier.padding(top = 14.dp)
                )
                AnalysisStatRow(
                    stats = listOf(
                        "Habits ${formatStat(data.habitThresholdPercent)}%+" to
                            formatStatOrDash(data.moodOnHighHabitDays),
                        "Habits below" to formatStatOrDash(data.moodOnLowHabitDays),
                        "Gap" to gap(data.moodOnHighHabitDays, data.moodOnLowHabitDays),
                    ),
                    modifier = Modifier.padding(top = 14.dp)
                )
                if (data.sleepRatingOnGoodSleep != null || data.sleepRatingOnPoorSleep != null) {
                    AnalysisStatRow(
                        stats = listOf(
                            "Rated sleep, long" to formatStatOrDash(data.sleepRatingOnGoodSleep),
                            "Rated sleep, short" to formatStatOrDash(data.sleepRatingOnPoorSleep),
                            "Gap" to gap(data.sleepRatingOnGoodSleep, data.sleepRatingOnPoorSleep),
                        ),
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Text(
                        text = "How restorative the night felt against how long it actually was.",
                        color = AnalysisTheme.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        if (data.intentionDays > 0) {
            AnalysisSection(
                title = "Intention",
                trailing = "${data.intentionDays} days",
                subtitle = "Mood on the days you named an intention against the days you did not."
            ) {
                AnalysisStatRow(
                    stats = listOf(
                        "With intention" to formatStatOrDash(data.moodWithIntention),
                        "Without" to formatStatOrDash(data.moodWithoutIntention),
                        "Gap" to gap(data.moodWithIntention, data.moodWithoutIntention),
                    )
                )
            }
        }

        AnalysisSection(
            title = "Reflection",
            subtitle = "Writing only after good days hides the days worth reading back."
        ) {
            AnalysisStatRow(
                stats = listOf(
                    "Journal days" to data.journalDays.toString(),
                    "Avg words" to formatStatOrDash(data.avgWordsPerJournalDay),
                    "Photos" to data.photoCount.toString(),
                )
            )
            AnalysisStatRow(
                stats = listOf(
                    "Wrote on low days" to formatPercentOrDash(data.journalRateOnLowMoodDays),
                    "Wrote on good days" to formatPercentOrDash(data.journalRateOnHighMoodDays),
                    "Days in range" to data.dates.size.toString(),
                ),
                modifier = Modifier.padding(top = 14.dp)
            )
            AnalysisStatRow(
                stats = listOf(
                    "Fuelled" to data.highlightDays.toString(),
                    "Drained" to data.lowlightDays.toString(),
                    "Grateful" to data.gratitudeDays.toString(),
                ),
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                text = "Days each journal prompt was answered.",
                color = AnalysisTheme.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** One scale line: poles, average level and which way it moved across the range. */
@Composable
private fun SelfScaleRow(scale: SelfScaleSeries) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scale.label,
                color = AnalysisTheme.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = "${scale.lowLabel} → ${scale.highLabel}",
                color = AnalysisTheme.TextSecondary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
        Text(
            text = formatStatOrDash(scale.average),
            color = AnalysisTheme.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Text(
            text = "${scale.daysRated}d",
            color = AnalysisTheme.TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
        if (scale.delta != null) {
            AnalysisDeltaPill(
                delta = scale.delta,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

private fun gap(a: Double?, b: Double?): String {
    if (a == null || b == null) return "—"
    val delta = a - b
    val sign = if (delta >= 0) "+" else "−"
    return "$sign${formatStat(abs(delta))}"
}

private fun signedTwoDecimals(value: Double): String {
    val sign = if (value >= 0) "+" else "−"
    return sign + String.format(Locale.US, "%.2f", abs(value))
}
