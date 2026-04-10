package com.vm.vector.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vm.core.models.RoutineEntry
import com.vm.vector.VectorApplication
import com.vm.vector.data.AnalysisRepository
import com.vm.vector.data.AnalysisTimeWindow
import com.vm.vector.data.analysis.CountMaxStreak
import com.vm.vector.data.analysis.HabitStatusBreakdown
import com.vm.vector.data.analysis.LineSeries
import com.vm.vector.data.analysis.RoutineHabitKey
import com.vm.vector.data.analysis.TripleStat
import com.vm.vector.data.analysis.WeekVolumePoint
import com.vm.vector.ui.analysis.AnalysisDashboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AnalysisUiState(
    val isLoading: Boolean = false,
    val startDate: String = "",
    val endDate: String = "",
    val timeWindow: AnalysisTimeWindow = AnalysisTimeWindow.DAYS_7,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val dashboard: AnalysisDashboard = AnalysisDashboard.InputsView,
    val dailyPlanStats: CountMaxStreak? = null,
    val dailyPlanLine: LineSeries? = null,
    val routineCategoryStats: CountMaxStreak? = null,
    val routineCategoryLine: LineSeries? = null,
    val nutritionCategoryStats: CountMaxStreak? = null,
    val nutritionCategoryLine: LineSeries? = null,
    val exerciseCategoryStats: CountMaxStreak? = null,
    val exerciseCategoryLine: LineSeries? = null,
    val diaryCategoryStats: CountMaxStreak? = null,
    val diaryCategoryLine: LineSeries? = null,
    val dietPlanComplianceStats: CountMaxStreak? = null,
    val dietPlanComplianceLine: LineSeries? = null,
    val dietMacros: Map<String, Pair<TripleStat?, LineSeries>> = emptyMap(),
    val dailyInfo: Map<String, Pair<TripleStat?, LineSeries>> = emptyMap(),
    val routineEntries: List<RoutineEntry> = emptyList(),
    val habitKeys: List<RoutineHabitKey> = emptyList(),
    val selectedHabitIndex: Int = 0,
    val habitBreakdown: HabitStatusBreakdown? = null,
    val muscles: List<String> = emptyList(),
    val selectedMuscleIndex: Int = 0,
    val volumeStat: TripleStat? = null,
    val volumeWeeks: List<WeekVolumePoint> = emptyList(),
    val moodRows: List<AnalysisRepository.MoodGridRow> = emptyList(),
)

class AnalysisViewModel(
    application: Application,
    private val repository: AnalysisRepository,
) : AndroidViewModel(application) {

    private val _ui = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _ui.asStateFlow()

    init {
        refresh()
        (application as? VectorApplication)?.databaseResetEvents
            ?.onEach { refresh() }
            ?.launchIn(viewModelScope)
    }

    fun setTimeWindow(window: AnalysisTimeWindow) {
        _ui.update { it.copy(timeWindow = window) }
        refresh()
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        _ui.update {
            it.copy(
                timeWindow = AnalysisTimeWindow.CUSTOM,
                customStart = start,
                customEnd = end
            )
        }
        refresh()
    }

    fun setDashboard(d: AnalysisDashboard) {
        _ui.update { it.copy(dashboard = d) }
        recomputeHabitIfNeeded()
        recomputeVolumeIfNeeded()
    }

    fun setSelectedHabitIndex(index: Int) {
        _ui.update { it.copy(selectedHabitIndex = index.coerceAtLeast(0)) }
        recomputeHabitIfNeeded()
    }

    fun setSelectedMuscleIndex(index: Int) {
        _ui.update { it.copy(selectedMuscleIndex = index.coerceAtLeast(0)) }
        recomputeVolumeIfNeeded()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            val s = _ui.value
            val (start, end) = repository.resolveDateRange(
                s.timeWindow,
                s.customStart,
                s.customEnd
            )
            val daily = repository.loadDailyPlanSeries(start, end)
            val routineCat = repository.loadRoutineCategorySeries(start, end)
            val dietCat = repository.loadDietCategorySeries(start, end)
            val woCat = repository.loadWorkoutCategorySeries(start, end)
            val diaryCat = repository.loadDiaryCategorySeries(start, end)
            val dietPlanCompliance = repository.loadDietPlanComplianceSeries(start, end)
            val macros = repository.loadDietMacros(start, end)
            val info = repository.loadDailyInfo(start, end)
            val habits = repository.listRoutineHabits(start, end)
            val routineEntries = repository.loadRoutineEntriesForHabits(start, end)
            val muscles = repository.listMuscles(start, end)
            val mood = repository.loadMoodGrid(start, end)
            val habitIdx = _ui.value.selectedHabitIndex.coerceIn(0, (habits.size - 1).coerceAtLeast(0))
            val muscleIdx = _ui.value.selectedMuscleIndex.coerceIn(0, (muscles.size - 1).coerceAtLeast(0))
            val habitKey = habits.getOrNull(habitIdx)
            val habitBreakdown = habitKey?.let { repository.habitBreakdown(start, end, it, routineEntries) }
            val muscle = muscles.getOrNull(muscleIdx)
            val (volStat, volWeeks) = if (muscle != null) {
                repository.loadVolumeSeriesForMuscle(start, end, muscle)
            } else {
                null to emptyList()
            }
            _ui.update {
                it.copy(
                    isLoading = false,
                    startDate = start,
                    endDate = end,
                    dailyPlanStats = daily.first,
                    dailyPlanLine = daily.second,
                    routineCategoryStats = routineCat.first,
                    routineCategoryLine = routineCat.second,
                    nutritionCategoryStats = dietCat.first,
                    nutritionCategoryLine = dietCat.second,
                    exerciseCategoryStats = woCat.first,
                    exerciseCategoryLine = woCat.second,
                    diaryCategoryStats = diaryCat.first,
                    diaryCategoryLine = diaryCat.second,
                    dietPlanComplianceStats = dietPlanCompliance.first,
                    dietPlanComplianceLine = dietPlanCompliance.second,
                    dietMacros = macros,
                    dailyInfo = info,
                    habitKeys = habits,
                    selectedHabitIndex = habitIdx,
                    selectedMuscleIndex = muscleIdx,
                    routineEntries = routineEntries,
                    habitBreakdown = habitBreakdown,
                    muscles = muscles,
                    volumeStat = volStat,
                    volumeWeeks = volWeeks,
                    moodRows = mood,
                )
            }
        }
    }

    private fun recomputeHabitIfNeeded() {
        if (_ui.value.dashboard != AnalysisDashboard.HabitsTrack) return
        val s = _ui.value
        val key = s.habitKeys.getOrNull(s.selectedHabitIndex) ?: return
        val breakdown = repository.habitBreakdown(s.startDate, s.endDate, key, s.routineEntries)
        _ui.update { it.copy(habitBreakdown = breakdown) }
    }

    private fun recomputeVolumeIfNeeded() {
        if (_ui.value.dashboard != AnalysisDashboard.TrainingVolume) return
        val s = _ui.value
        val muscle = s.muscles.getOrNull(s.selectedMuscleIndex) ?: run {
            _ui.update { it.copy(volumeStat = null, volumeWeeks = emptyList()) }
            return
        }
        viewModelScope.launch {
            val (stat, weeks) = repository.loadVolumeSeriesForMuscle(s.startDate, s.endDate, muscle)
            _ui.update {
                it.copy(
                    volumeStat = stat,
                    volumeWeeks = weeks
                )
            }
        }
    }
}
