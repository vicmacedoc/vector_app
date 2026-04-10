package com.vm.vector.ui.analysis.dashboards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.vector.data.analysis.CountMaxStreak
import com.vm.vector.data.analysis.LineSeries
import com.vm.vector.ui.analysis.AnalysisTheme

@Composable
fun InputsViewDashboard(
    routineStats: CountMaxStreak?,
    routineLine: LineSeries?,
    nutritionStats: CountMaxStreak?,
    nutritionLine: LineSeries?,
    exerciseStats: CountMaxStreak?,
    exerciseLine: LineSeries?,
    diaryStats: CountMaxStreak?,
    diaryLine: LineSeries?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionTitle("Routine")
        CategoryCompletionBody(stats = routineStats, line = routineLine)
        Spacer(modifier = Modifier.height(28.dp))

        SectionTitle("Nutrition")
        CategoryCompletionBody(stats = nutritionStats, line = nutritionLine)
        Spacer(modifier = Modifier.height(28.dp))

        SectionTitle("Exercise")
        CategoryCompletionBody(stats = exerciseStats, line = exerciseLine)
        Spacer(modifier = Modifier.height(28.dp))

        SectionTitle("Diary")
        CategoryCompletionBody(stats = diaryStats, line = diaryLine)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = AnalysisTheme.TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(12.dp))
}
