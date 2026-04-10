package com.vm.vector.ui.analysis.dashboards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.core.models.RoutineStatus
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.PriorityLow
import com.vm.core.ui.theme.PriorityMid
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.analysis.HabitStatusBreakdown
import com.vm.vector.data.analysis.RoutineHabitKey
import com.vm.vector.ui.analysis.AnalysisLineChart
import com.vm.vector.ui.analysis.AnalysisTripleStatCard
import com.vm.vector.ui.analysis.AnalysisTheme
import com.vm.vector.ui.analysis.analysisOutlinedTextFieldColors
import kotlin.math.roundToInt

private fun routineStatusDisplayName(status: RoutineStatus): String = when (status) {
    RoutineStatus.NONE -> "None"
    RoutineStatus.NOT_DONE -> "Not done"
    RoutineStatus.PARTIAL -> "Partial"
    RoutineStatus.DONE -> "Done"
    RoutineStatus.EXCEEDED -> "Exceeded"
    RoutineStatus.NA -> "N/A"
}

private fun routineStatusAccent(status: RoutineStatus): Color = when (status) {
    RoutineStatus.NONE -> IconGray
    RoutineStatus.NOT_DONE -> DeleteRed
    RoutineStatus.PARTIAL -> PriorityMid
    RoutineStatus.DONE -> PriorityLow
    RoutineStatus.EXCEEDED -> ElectricBlue
    RoutineStatus.NA -> SlateGray
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsTrackDashboard(
    habitKeys: List<RoutineHabitKey>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    breakdown: HabitStatusBreakdown?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (habitKeys.isEmpty()) {
            Text("No routine data in this range.", color = AnalysisTheme.TextSecondary)
            return
        }
        var expanded by remember { mutableStateOf(false) }
        val idx = selectedIndex.coerceIn(0, habitKeys.lastIndex)
        val key = habitKeys[idx]
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = key.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Habit", color = AnalysisTheme.TextSecondary) },
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
                habitKeys.forEachIndexed { i, k ->
                    DropdownMenuItem(
                        text = { Text(k.label, color = AnalysisTheme.TextPrimary) },
                        onClick = {
                            onSelectIndex(i)
                            expanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        breakdown?.let { b ->
            val total = b.counts.values.sum()
            Text(
                text = "Status (days with this habit)",
                color = AnalysisTheme.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (total <= 0) {
                Text("No status counts in range.", color = AnalysisTheme.TextSecondary, fontSize = 14.sp)
            } else {
                val tiles = RoutineStatus.entries.mapNotNull { st ->
                    val c = b.counts[st] ?: 0
                    if (c <= 0) null else HabitStatusTileData(st, c, (c * 100.0 / total))
                }
                tiles.chunked(2).forEach { rowTiles ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTiles.forEach { data ->
                            HabitStatusTile(
                                data = data,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowTiles.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            b.numericalLine?.let { line ->
                Spacer(modifier = Modifier.height(16.dp))
                b.tripleStat?.let { AnalysisTripleStatCard(stat = it) }
                Spacer(modifier = Modifier.height(8.dp))
                AnalysisLineChart(series = line)
            }
        }
    }
}

private data class HabitStatusTileData(
    val status: RoutineStatus,
    val count: Int,
    val percent: Double,
)

@Composable
private fun HabitStatusTile(
    data: HabitStatusTileData,
    modifier: Modifier = Modifier,
) {
    val accent = routineStatusAccent(data.status)
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .border(1.5.dp, accent.copy(alpha = 0.45f), shape)
            .background(accent.copy(alpha = 0.12f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = routineStatusDisplayName(data.status),
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${data.count} day${if (data.count == 1) "" else "s"}",
            color = AnalysisTheme.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${data.percent.roundToInt()}% of days",
            color = AnalysisTheme.TextSecondary,
            fontSize = 12.sp
        )
    }
}
