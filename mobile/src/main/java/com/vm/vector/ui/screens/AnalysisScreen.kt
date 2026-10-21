@file:OptIn(ExperimentalMaterial3Api::class)

package com.vm.vector.ui.screens

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vm.vector.data.AnalysisTimeWindow
import com.vm.vector.ui.analysis.AnalysisDashboard
import com.vm.vector.ui.analysis.AnalysisTheme
import com.vm.vector.ui.analysis.analysisFilterChipColors
import com.vm.vector.ui.analysis.dashboards.BodyDashboard
import com.vm.vector.ui.analysis.dashboards.FoodDashboard
import com.vm.vector.ui.analysis.dashboards.OverviewDashboard
import com.vm.vector.ui.analysis.dashboards.RoutineDashboard
import com.vm.vector.ui.analysis.dashboards.SelfDashboard
import com.vm.vector.ui.analysis.dashboards.SleepDashboard
import com.vm.vector.ui.analysis.dashboards.TrainingDashboard
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

    Surface(modifier = Modifier.fillMaxSize(), color = AnalysisTheme.Background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
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
                Text(
                    text = "ANALYSIS",
                    color = AnalysisTheme.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = if (onBack != null) 0.dp else 12.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalysisDashboard.pickerEntries.forEach { dashboard ->
                    AnalysisChip(
                        label = dashboard.title,
                        selected = ui.dashboard == dashboard,
                        onClick = { viewModel.setDashboard(dashboard) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalysisChip("7D", ui.timeWindow == AnalysisTimeWindow.DAYS_7) {
                    viewModel.setTimeWindow(AnalysisTimeWindow.DAYS_7)
                }
                AnalysisChip("30D", ui.timeWindow == AnalysisTimeWindow.DAYS_30) {
                    viewModel.setTimeWindow(AnalysisTimeWindow.DAYS_30)
                }
                AnalysisChip("90D", ui.timeWindow == AnalysisTimeWindow.DAYS_90) {
                    viewModel.setTimeWindow(AnalysisTimeWindow.DAYS_90)
                }
                AnalysisChip("All", ui.timeWindow == AnalysisTimeWindow.ALL) {
                    viewModel.setTimeWindow(AnalysisTimeWindow.ALL)
                }
                AnalysisChip("Custom", ui.timeWindow == AnalysisTimeWindow.CUSTOM) {
                    showStartPicker = true
                }
            }

            Text(
                text = "${ui.startDate}  →  ${ui.endDate}",
                color = AnalysisTheme.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                            .padding(horizontal = 12.dp)
                    ) {
                        when (ui.dashboard) {
                            AnalysisDashboard.Overview ->
                                ui.overview?.let { OverviewDashboard(it) }
                            AnalysisDashboard.Routine ->
                                ui.routine?.let { RoutineDashboard(it) }
                            AnalysisDashboard.Food ->
                                ui.food?.let { FoodDashboard(it) }
                            AnalysisDashboard.Training ->
                                ui.training?.let {
                                    TrainingDashboard(
                                        data = it,
                                        onSelectMuscle = viewModel::selectMuscle,
                                        onSelectExercise = viewModel::selectExercise
                                    )
                                }
                            AnalysisDashboard.Body ->
                                ui.body?.let { BodyDashboard(it) }
                            AnalysisDashboard.Sleep ->
                                ui.sleep?.let { SleepDashboard(it) }
                            AnalysisDashboard.Self ->
                                ui.self?.let { SelfDashboard(it) }
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
private fun AnalysisChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        colors = analysisFilterChipColors()
    )
}
