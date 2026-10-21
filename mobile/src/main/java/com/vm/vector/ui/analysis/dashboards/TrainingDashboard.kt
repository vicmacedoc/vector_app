@file:OptIn(ExperimentalMaterial3Api::class)

package com.vm.vector.ui.analysis.dashboards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.core.ui.theme.PureWhite
import com.vm.vector.data.analysis.TrainingData
import com.vm.vector.ui.analysis.AnalysisEmptyState
import com.vm.vector.ui.analysis.AnalysisHeroStat
import com.vm.vector.ui.analysis.AnalysisSection
import com.vm.vector.ui.analysis.AnalysisStatRow
import com.vm.vector.ui.analysis.AnalysisTheme
import com.vm.vector.ui.analysis.analysisOutlinedTextFieldColors
import com.vm.vector.ui.analysis.formatPercentOrDash
import com.vm.vector.ui.analysis.formatStat
import com.vm.vector.ui.analysis.formatStatOrDash
import com.vm.vector.ui.analysis.charts.AnalysisLineChart
import com.vm.vector.ui.analysis.charts.AnalysisRankBars
import com.vm.vector.ui.analysis.charts.LineLayer
import com.vm.vector.ui.analysis.charts.RankBarItem

@Composable
fun TrainingDashboard(
    data: TrainingData,
    onSelectMuscle: (String) -> Unit,
    onSelectExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (data.setsLogged == 0) {
        AnalysisEmptyState("No sets logged in this range.", modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        if (data.muscleRanking.isNotEmpty()) {
            AnalysisSection(
                title = "Muscle volume",
                trailing = "${data.sessionDays} session days",
                subtitle = "Set-equivalents over the range. Tap a muscle to chart it."
            ) {
                AnalysisRankBars(
                    items = data.muscleRanking.map {
                        RankBarItem(it.name, it.setEquivalents, formatStat(it.setEquivalents))
                    },
                    selected = data.selectedMuscle,
                    onSelect = onSelectMuscle
                )
                if (data.selectedMuscle != null && data.weeks.isNotEmpty()) {
                    AnalysisLineChart(
                        dates = data.weeks,
                        layers = listOf(
                            LineLayer(
                                data.selectedMuscle,
                                data.muscleWeekly,
                                AnalysisTheme.NavyAccent,
                                filled = true
                            )
                        ),
                        yLabel = "sets / week",
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    AnalysisStatRow(
                        stats = listOf(
                            "Peak week" to (data.muscleStat?.let { formatStat(it.high) } ?: "—"),
                            "Average" to (data.muscleStat?.let { formatStat(it.avg) } ?: "—"),
                            "Lowest" to (data.muscleStat?.let { formatStat(it.low) } ?: "—"),
                        ),
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }
        }

        if (data.weeks.isNotEmpty() && data.totalTonnage > 0.0) {
            AnalysisSection(
                title = "Tonnage",
                trailing = "${formatStat(data.totalTonnage)} kg total",
                subtitle = "Load times reps for every completed set, by week."
            ) {
                AnalysisLineChart(
                    dates = data.weeks,
                    layers = listOf(
                        LineLayer("Tonnage", data.tonnageWeekly, AnalysisTheme.AccentLine, filled = true)
                    ),
                    yLabel = "kg / week"
                )
            }
        }

        if (data.exercises.isNotEmpty()) {
            AnalysisSection(
                title = "Strength progression",
                subtitle = "Best estimated one-rep max per session (Epley)."
            ) {
                ExercisePicker(
                    exercises = data.exercises,
                    selected = data.selectedExercise,
                    onSelect = onSelectExercise
                )
                if (data.e1rmDates.size >= 2) {
                    AnalysisHeroStat(
                        value = formatStatOrDash(data.bestE1rm),
                        unit = "kg",
                        caption = "best estimated 1RM",
                        delta = data.e1rmDelta,
                        deltaUnit = "kg",
                        higherIsBetter = true,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    AnalysisLineChart(
                        dates = data.e1rmDates,
                        layers = listOf(
                            LineLayer("e1RM", data.e1rm, AnalysisTheme.AccentLine, filled = true)
                        ),
                        yLabel = "kg",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else {
                    Text(
                        text = "Log load and reps on at least two sessions to chart progression.",
                        color = AnalysisTheme.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }

        AnalysisSection(
            title = "Execution",
            subtitle = "Intensity and how closely sets matched their targets."
        ) {
            AnalysisStatRow(
                stats = listOf(
                    "Sets done" to "${data.setsCompleted}/${data.setsLogged}",
                    "Completion" to formatPercentOrDash(data.completionPercent),
                    "Avg RPE" to formatStatOrDash(data.avgRpe),
                )
            )
            AnalysisStatRow(
                stats = listOf(
                    "Avg RIR" to formatStatOrDash(data.avgRir),
                    "Load vs target" to (
                        data.avgLoadDelta?.let { signedKg(it) } ?: "—"
                        ),
                    "Session days" to data.sessionDays.toString(),
                ),
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

@Composable
private fun ExercisePicker(
    exercises: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected ?: "Select exercise",
            onValueChange = {},
            readOnly = true,
            label = { Text("Exercise", color = AnalysisTheme.TextSecondary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = analysisOutlinedTextFieldColors()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(PureWhite)
        ) {
            exercises.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name, color = AnalysisTheme.TextPrimary) },
                    onClick = {
                        onSelect(name)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun signedKg(value: Double): String {
    val sign = if (value >= 0) "+" else "−"
    return "$sign${formatStat(kotlin.math.abs(value))}kg"
}
