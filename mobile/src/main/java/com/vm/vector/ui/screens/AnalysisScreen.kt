@file:OptIn(ExperimentalMaterial3Api::class)

package com.vm.vector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vm.core.ui.theme.PureWhite
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vm.vector.data.AnalysisTimeWindow
import com.vm.vector.ui.analysis.AnalysisDashboard
import com.vm.vector.ui.analysis.AnalysisTheme
import com.vm.vector.ui.analysis.analysisFilterChipColors
import com.vm.vector.ui.analysis.analysisOutlinedTextFieldColors
import com.vm.vector.ui.analysis.dashboards.DailyInfoDashboard
import com.vm.vector.ui.analysis.dashboards.DailyMoodDashboard
import com.vm.vector.ui.analysis.dashboards.DietStatusDashboard
import com.vm.vector.ui.analysis.dashboards.HabitsTrackDashboard
import com.vm.vector.ui.analysis.dashboards.InputsViewDashboard
import com.vm.vector.ui.analysis.dashboards.TrainingVolumeDashboard
import com.vm.vector.ui.viewmodel.AnalysisViewModel
import com.vm.vector.ui.viewmodel.AnalysisViewModelFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun AnalysisScreen(
    onBack: (() -> Unit)? = null,
    viewModel: AnalysisViewModel = viewModel(
        factory = AnalysisViewModelFactory(LocalContext.current.applicationContext)
    ),
) {
    val ui by viewModel.uiState.collectAsState()
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var pendingStart: LocalDate? by remember { mutableStateOf(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AnalysisTheme.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AnalysisTheme.TextPrimary
                        )
                    }
                }
                DashboardDropdown(
                    selected = ui.dashboard,
                    onSelect = viewModel::setDashboard,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeChip("7 Days", ui.timeWindow == AnalysisTimeWindow.DAYS_7) {
                    viewModel.setTimeWindow(AnalysisTimeWindow.DAYS_7)
                }
                TimeChip("30 Days", ui.timeWindow == AnalysisTimeWindow.DAYS_30) {
                    viewModel.setTimeWindow(AnalysisTimeWindow.DAYS_30)
                }
                TimeChip("90 Days", ui.timeWindow == AnalysisTimeWindow.DAYS_90) {
                    viewModel.setTimeWindow(AnalysisTimeWindow.DAYS_90)
                }
                TimeChip("All Time", ui.timeWindow == AnalysisTimeWindow.ALL) {
                    viewModel.setTimeWindow(AnalysisTimeWindow.ALL)
                }
                TimeChip("Custom", ui.timeWindow == AnalysisTimeWindow.CUSTOM) {
                    showStartPicker = true
                }
            }

            Text(
                text = "${ui.startDate} → ${ui.endDate}",
                color = AnalysisTheme.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (ui.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AnalysisTheme.NavyAccent
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        when (ui.dashboard) {
                            AnalysisDashboard.InputsView ->
                                InputsViewDashboard(
                                    routineStats = ui.routineCategoryStats,
                                    routineLine = ui.routineCategoryLine,
                                    nutritionStats = ui.nutritionCategoryStats,
                                    nutritionLine = ui.nutritionCategoryLine,
                                    exerciseStats = ui.exerciseCategoryStats,
                                    exerciseLine = ui.exerciseCategoryLine,
                                    diaryStats = ui.diaryCategoryStats,
                                    diaryLine = ui.diaryCategoryLine
                                )
                            AnalysisDashboard.DailyInfo ->
                                DailyInfoDashboard(
                                    dailyPlanStats = ui.dailyPlanStats,
                                    dailyPlanLine = ui.dailyPlanLine,
                                    dailyInfo = ui.dailyInfo
                                )
                            AnalysisDashboard.DietStatus ->
                                DietStatusDashboard(
                                    planComplianceStats = ui.dietPlanComplianceStats,
                                    planComplianceLine = ui.dietPlanComplianceLine,
                                    macros = ui.dietMacros
                                )
                            AnalysisDashboard.HabitsTrack ->
                                HabitsTrackDashboard(
                                    habitKeys = ui.habitKeys,
                                    selectedIndex = ui.selectedHabitIndex,
                                    onSelectIndex = viewModel::setSelectedHabitIndex,
                                    breakdown = ui.habitBreakdown
                                )
                            AnalysisDashboard.TrainingVolume ->
                                TrainingVolumeDashboard(
                                    muscles = ui.muscles,
                                    selectedIndex = ui.selectedMuscleIndex,
                                    onSelectIndex = viewModel::setSelectedMuscleIndex,
                                    triple = ui.volumeStat,
                                    weeks = ui.volumeWeeks
                                )
                            AnalysisDashboard.DailyMood ->
                                DailyMoodDashboard(rows = ui.moodRows)
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        pendingStart = Instant.ofEpochMilli(ms)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        showStartPicker = false
                        showEndPicker = true
                    } ?: run { showStartPicker = false }
                }) { Text("Next", color = AnalysisTheme.NavyAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) {
                    Text("Cancel", color = AnalysisTheme.TextSecondary)
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val end = Instant.ofEpochMilli(ms)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        val start = pendingStart ?: end
                        viewModel.setCustomRange(
                            if (start.isAfter(end)) end else start,
                            if (start.isAfter(end)) start else end
                        )
                    }
                    showEndPicker = false
                    pendingStart = null
                }) { Text("OK", color = AnalysisTheme.NavyAccent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEndPicker = false
                    pendingStart = null
                }) { Text("Cancel", color = AnalysisTheme.TextSecondary) }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun TimeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        colors = analysisFilterChipColors()
    )
}

@Composable
private fun DashboardDropdown(
    selected: AnalysisDashboard,
    onSelect: (AnalysisDashboard) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.title,
            onValueChange = {},
            readOnly = true,
            label = { Text("Dashboard", color = AnalysisTheme.TextSecondary) },
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
            AnalysisDashboard.entries.forEach { d ->
                DropdownMenuItem(
                    text = { Text(d.title, color = AnalysisTheme.TextPrimary) },
                    onClick = {
                        onSelect(d)
                        expanded = false
                    }
                )
            }
        }
    }
}
