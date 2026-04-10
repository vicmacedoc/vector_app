package com.vm.vector.ui.analysis.dashboards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.core.ui.theme.MoodColorNa
import com.vm.core.ui.theme.moodColorForLevel
import com.vm.vector.data.AnalysisRepository
import com.vm.vector.ui.analysis.AnalysisTheme

private val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun DailyMoodDashboard(
    rows: List<AnalysisRepository.MoodGridRow>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Mood level (1–5) by calendar day",
            color = AnalysisTheme.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Spacer(modifier = Modifier.width(72.dp))
            dayLetters.forEach { letter ->
                Text(
                    text = letter,
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        rows.forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = row.mondayIso,
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.width(72.dp)
                )
                row.cells.forEach { mood ->
                    val color = when (mood) {
                        null -> MoodColorNa
                        in 1..5 -> moodColorForLevel(mood)
                        else -> MoodColorNa
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(color)
                            .border(1.dp, AnalysisTheme.Grid)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
