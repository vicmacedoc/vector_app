package com.vm.vector.ui.analysis.dashboards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vm.core.ui.theme.PureWhite
import androidx.compose.ui.unit.dp
import com.vm.vector.data.analysis.LineSeries
import com.vm.vector.data.analysis.TripleStat
import com.vm.vector.data.analysis.WeekVolumePoint
import com.vm.vector.ui.analysis.AnalysisLineChart
import com.vm.vector.ui.analysis.AnalysisTripleStatCard
import com.vm.vector.ui.analysis.AnalysisTheme
import com.vm.vector.ui.analysis.analysisOutlinedTextFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingVolumeDashboard(
    muscles: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    triple: TripleStat?,
    weeks: List<WeekVolumePoint>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Weekly fractional sets (selected muscle)",
            color = AnalysisTheme.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (muscles.isEmpty()) {
            Text(
                "No resistance `target` muscles found for this range. Load a workout preset in Settings and log completed sets on days in the range.",
                color = AnalysisTheme.TextSecondary
            )
            return
        }
        var expanded by remember { mutableStateOf(false) }
        val idx = selectedIndex.coerceIn(0, muscles.lastIndex)
        val m = muscles[idx]
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = m,
                onValueChange = {},
                readOnly = true,
                label = { Text("Muscle", color = AnalysisTheme.TextSecondary) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = analysisOutlinedTextFieldColors()
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(PureWhite)
            ) {
                muscles.forEachIndexed { i, name ->
                    DropdownMenuItem(
                        text = { Text(name, color = AnalysisTheme.TextPrimary) },
                        onClick = {
                            onSelectIndex(i)
                            expanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        AnalysisTripleStatCard(stat = triple)
        Spacer(modifier = Modifier.height(8.dp))
        if (weeks.isNotEmpty()) {
            val series = LineSeries(
                dates = weeks.map { it.weekMondayIso },
                yValues = weeks.map { it.volumeLoad },
                yLabel = "Set-equivalents / week"
            )
            AnalysisLineChart(series = series)
        }
    }
}
