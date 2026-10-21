package com.vm.vector.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vm.vector.VectorApplication
import com.vm.vector.data.AnalysisRepository
import com.vm.vector.data.AnalysisTimeWindow
import com.vm.vector.data.analysis.BodyData
import com.vm.vector.data.analysis.FoodData
import com.vm.vector.data.analysis.OverviewData
import com.vm.vector.data.analysis.RoutineData
import com.vm.vector.data.analysis.SelfData
import com.vm.vector.data.analysis.SleepData
import com.vm.vector.data.analysis.TrainingData
import com.vm.vector.ui.analysis.AnalysisDashboard
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AnalysisUiState(
    val isLoading: Boolean = true,
    val startDate: String = "",
    val endDate: String = "",
    val timeWindow: AnalysisTimeWindow = AnalysisTimeWindow.DAYS_30,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val dashboard: AnalysisDashboard = AnalysisDashboard.Overview,
    val overview: OverviewData? = null,
    val routine: RoutineData? = null,
    val food: FoodData? = null,
    val training: TrainingData? = null,
    val body: BodyData? = null,
    val sleep: SleepData? = null,
    val self: SelfData? = null,
)

/**
 * Loads one dashboard at a time. Results are cached per (dashboard, range) so switching
 * tabs back and forth is free, while changing the range clears everything.
 */
class AnalysisViewModel(
    application: Application,
    private val repository: AnalysisRepository,
) : AndroidViewModel(application) {

    private val _ui = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _ui.asStateFlow()

    private var loadJob: Job? = null
    private var preferredMuscle: String? = null
    private var preferredExercise: String? = null

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

    fun setDashboard(dashboard: AnalysisDashboard) {
        if (_ui.value.dashboard == dashboard) return
        _ui.update { it.copy(dashboard = dashboard) }
        loadSelected()
    }

    fun selectMuscle(muscle: String) {
        preferredMuscle = muscle
        loadTraining()
    }

    fun selectExercise(exercise: String) {
        preferredExercise = exercise
        loadTraining()
    }

    /** Resolves the range from scratch and drops every cached dashboard. */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            repository.invalidateCaches()
            val s = _ui.value
            val (start, end) = repository.resolveDateRange(s.timeWindow, s.customStart, s.customEnd)
            _ui.update {
                it.copy(
                    startDate = start,
                    endDate = end,
                    overview = null,
                    routine = null,
                    food = null,
                    training = null,
                    body = null,
                    sleep = null,
                    self = null,
                )
            }
            load(_ui.value.dashboard, start, end)
        }
    }

    private fun loadSelected() {
        val s = _ui.value
        if (s.startDate.isEmpty()) return
        if (isCached(s.dashboard)) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            load(s.dashboard, s.startDate, s.endDate)
        }
    }

    private fun loadTraining() {
        val s = _ui.value
        if (s.startDate.isEmpty()) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            load(AnalysisDashboard.Training, s.startDate, s.endDate)
        }
    }

    private fun isCached(dashboard: AnalysisDashboard): Boolean {
        val s = _ui.value
        return when (dashboard) {
            AnalysisDashboard.Overview -> s.overview != null
            AnalysisDashboard.Routine -> s.routine != null
            AnalysisDashboard.Food -> s.food != null
            AnalysisDashboard.Training -> s.training != null
            AnalysisDashboard.Body -> s.body != null
            AnalysisDashboard.Sleep -> s.sleep != null
            AnalysisDashboard.Self -> s.self != null
        }
    }

    private suspend fun load(dashboard: AnalysisDashboard, start: String, end: String) {
        when (dashboard) {
            AnalysisDashboard.Overview -> {
                val data = repository.loadOverview(start, end)
                _ui.update { it.copy(overview = data) }
            }
            AnalysisDashboard.Routine -> {
                val data = repository.loadRoutine(start, end)
                _ui.update { it.copy(routine = data) }
            }
            AnalysisDashboard.Food -> {
                val data = repository.loadFood(start, end)
                _ui.update { it.copy(food = data) }
            }
            AnalysisDashboard.Training -> {
                val data = repository.loadTraining(start, end, preferredMuscle, preferredExercise)
                preferredMuscle = data.selectedMuscle
                preferredExercise = data.selectedExercise
                _ui.update { it.copy(training = data) }
            }
            AnalysisDashboard.Body -> {
                val data = repository.loadBody(start, end)
                _ui.update { it.copy(body = data) }
            }
            AnalysisDashboard.Sleep -> {
                val data = repository.loadSleep(start, end)
                _ui.update { it.copy(sleep = data) }
            }
            AnalysisDashboard.Self -> {
                val data = repository.loadSelf(start, end)
                _ui.update { it.copy(self = data) }
            }
        }
        _ui.update { it.copy(isLoading = false) }
    }
}
