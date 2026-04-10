package com.vm.vector.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vm.vector.VectorApplication
import com.vm.vector.data.DayCompletion
import com.vm.vector.data.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class HomeUiState(
    val selectedDate: String = "",
    val isPastSelectedDate: Boolean = false,
    val isFutureSelectedDate: Boolean = false,
    val homeFieldsEditable: Boolean = true,
    val pastDayEditingEnabled: Boolean = false,
    val weightKg: String = "",
    val bodyFatPercent: String = "",
    val sleepStartMinutes: Int = 22 * 60,
    val sleepEndMinutes: Int = 6 * 60,
    val actualSleepStartMinutes: Int = 22 * 60,
    val actualSleepEndMinutes: Int = 6 * 60,
    val dailyPlanCompletionPercent: Int = 0,
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val routineLast7: List<DayCompletion> = emptyList(),
    val dietLast7: List<DayCompletion> = emptyList(),
    val workoutLast7: List<DayCompletion> = emptyList(),
    val diaryLast7: List<DayCompletion> = emptyList(),
)

class HomeViewModel(
    application: Application,
    private val homeRepository: HomeRepository,
) : AndroidViewModel(application) {

    private val _weightKg = MutableStateFlow("")
    private val _bodyFatPercent = MutableStateFlow("")
    private val _sleepStartMinutes = MutableStateFlow(22 * 60)
    private val _sleepEndMinutes = MutableStateFlow(6 * 60)
    private val _actualSleepStartMinutes = MutableStateFlow(22 * 60)
    private val _actualSleepEndMinutes = MutableStateFlow(6 * 60)
    private val _dailyPlanCompletionPercent = MutableStateFlow(0)
    private val _selectedDate = MutableStateFlow(homeRepository.dateForOffset(0))
    private val _pastDayEditingEnabled = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    private val _saveMessage = MutableStateFlow<String?>(null)
    private val _routineLast7 = MutableStateFlow<List<DayCompletion>>(emptyList())
    private val _dietLast7 = MutableStateFlow<List<DayCompletion>>(emptyList())
    private val _workoutLast7 = MutableStateFlow<List<DayCompletion>>(emptyList())
    private val _diaryLast7 = MutableStateFlow<List<DayCompletion>>(emptyList())

    val uiState: StateFlow<HomeUiState> = combine(
        _weightKg,
        _bodyFatPercent,
        _sleepStartMinutes,
        _sleepEndMinutes,
        _actualSleepStartMinutes,
        _actualSleepEndMinutes,
        _dailyPlanCompletionPercent,
        _selectedDate,
        _pastDayEditingEnabled,
        _isSaving,
        _saveMessage,
        _routineLast7,
        _dietLast7,
        _workoutLast7,
        _diaryLast7
    ) { values ->
        val selected = values[7] as String
        val pastUnlock = values[8] as Boolean
        val today = homeRepository.dateForOffset(0)
        val cmp = selected.compareTo(today)
        val editable = when {
            cmp > 0 -> false
            cmp == 0 -> true
            else -> pastUnlock
        }
        HomeUiState(
            selectedDate = selected,
            isPastSelectedDate = cmp < 0,
            isFutureSelectedDate = cmp > 0,
            homeFieldsEditable = editable,
            pastDayEditingEnabled = pastUnlock,
            weightKg = values[0] as String,
            bodyFatPercent = values[1] as String,
            sleepStartMinutes = values[2] as Int,
            sleepEndMinutes = values[3] as Int,
            actualSleepStartMinutes = values[4] as Int,
            actualSleepEndMinutes = values[5] as Int,
            dailyPlanCompletionPercent = values[6] as Int,
            isSaving = values[9] as Boolean,
            saveMessage = values[10] as String?,
            routineLast7 = values[11] as List<DayCompletion>,
            dietLast7 = values[12] as List<DayCompletion>,
            workoutLast7 = values[13] as List<DayCompletion>,
            diaryLast7 = values[14] as List<DayCompletion>
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        viewModelScope.launch {
            loadEntryForSelectedDate()
            loadLast7Completions()
        }
        (getApplication() as? VectorApplication)?.let { app ->
            app.databaseResetEvents.onEach {
                viewModelScope.launch {
                    loadEntryForSelectedDate()
                    loadLast7Completions()
                }
            }.launchIn(viewModelScope)
        }
    }

    fun setSelectedDate(isoDate: String) {
        viewModelScope.launch {
            _selectedDate.value = isoDate
            _pastDayEditingEnabled.value = false
            loadEntryForSelectedDate()
        }
    }

    fun togglePastDayEditing() {
        val sel = _selectedDate.value
        val today = homeRepository.dateForOffset(0)
        if (sel.compareTo(today) < 0) {
            _pastDayEditingEnabled.value = !_pastDayEditingEnabled.value
        }
    }

    private suspend fun loadEntryForSelectedDate() {
        val date = _selectedDate.value
        val entry = homeRepository.getEntryForDate(date)
        entry?.let {
            _weightKg.value = it.weightKg?.toString()?.trim() ?: ""
            _bodyFatPercent.value = it.bodyFatPercent?.toString()?.trim() ?: ""
            val start = parseTimeToMinutes(it.sleepStart) ?: 22 * 60
            val end = parseTimeToMinutes(it.sleepEnd) ?: 6 * 60
            val actualStart = parseTimeToMinutes(it.actualSleepStart) ?: start
            val actualEnd = parseTimeToMinutes(it.actualSleepEnd) ?: end
            _sleepStartMinutes.value = start
            _sleepEndMinutes.value = end
            _actualSleepStartMinutes.value = actualStart
            _actualSleepEndMinutes.value = actualEnd
            _dailyPlanCompletionPercent.value = it.dailyPlanCompletionPercent.coerceIn(0, 100)
        }
        if (entry == null) {
            _weightKg.value = ""
            _bodyFatPercent.value = ""
            _sleepStartMinutes.value = 22 * 60
            _sleepEndMinutes.value = 6 * 60
            _actualSleepStartMinutes.value = 22 * 60
            _actualSleepEndMinutes.value = 6 * 60
            _dailyPlanCompletionPercent.value = 0
        }
    }

    fun setWeight(value: String) { _weightKg.value = value }
    fun setBodyFat(value: String) { _bodyFatPercent.value = value }
    fun setSleepStart(minutes: Int) {
        _sleepStartMinutes.value = minutes
        _actualSleepStartMinutes.value = minutes
    }
    fun setSleepEnd(minutes: Int) {
        _sleepEndMinutes.value = minutes
        _actualSleepEndMinutes.value = minutes
    }
    fun setActualSleepStart(minutes: Int) { _actualSleepStartMinutes.value = minutes }
    fun setActualSleepEnd(minutes: Int) { _actualSleepEndMinutes.value = minutes }
    fun setDailyPlanCompletionPercent(value: Int) { _dailyPlanCompletionPercent.value = value.coerceIn(0, 100) }

    fun save() {
        viewModelScope.launch {
            val today = homeRepository.dateForOffset(0)
            val sel = _selectedDate.value
            val cmp = sel.compareTo(today)
            val editable = when {
                cmp > 0 -> false
                cmp == 0 -> true
                else -> _pastDayEditingEnabled.value
            }
            if (!editable) return@launch
            _isSaving.value = true
            _saveMessage.value = null
            try {
                val weight = _weightKg.value.toDoubleOrNull()
                val bodyFat = _bodyFatPercent.value.toDoubleOrNull()
                val startStr = minutesToTime(_sleepStartMinutes.value)
                val endStr = minutesToTime(_sleepEndMinutes.value)
                val actualStartStr = minutesToTime(_actualSleepStartMinutes.value)
                val actualEndStr = minutesToTime(_actualSleepEndMinutes.value)
                val date = _selectedDate.value
                val existing = homeRepository.getEntryForDate(date)
                homeRepository.saveForDate(
                    date = date,
                    weightKg = weight,
                    bodyFatPercent = bodyFat,
                    sleepStart = startStr,
                    sleepEnd = endStr,
                    actualSleepStart = actualStartStr,
                    actualSleepEnd = actualEndStr,
                    dailyPlanText = existing?.dailyPlanText,
                    dailyPlanAudioPath = existing?.dailyPlanAudioPath,
                    dailyPlanCompletionPercent = _dailyPlanCompletionPercent.value
                )
                _saveMessage.value = "Saved"
                loadLast7Completions()
            } catch (e: Exception) {
                _saveMessage.value = "Save failed"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearSaveMessage() { _saveMessage.value = null }

    private val _isRefreshingLast7 = MutableStateFlow(false)
    val isRefreshingLast7: StateFlow<Boolean> = _isRefreshingLast7.asStateFlow()

    fun refreshLast7() {
        viewModelScope.launch {
            _isRefreshingLast7.value = true
            try {
                loadEntryForSelectedDate()
                loadLast7Completions()
            } finally {
                _isRefreshingLast7.value = false
            }
        }
    }

    private suspend fun loadLast7Completions() {
        _routineLast7.value = homeRepository.getRoutineCompletionLast7Days()
        _dietLast7.value = homeRepository.getDietCompletionLast7Days()
        _workoutLast7.value = homeRepository.getWorkoutCompletionLast7Days()
        _diaryLast7.value = homeRepository.getDiaryCompletionLast7Days()
    }

    private fun parseTimeToMinutes(time: String?): Int? {
        if (time.isNullOrBlank()) return null
        val parts = time.split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: 0
        return (h * 60 + m) % (24 * 60)
    }

    private fun minutesToTime(minutes: Int): String {
        val m = (minutes % (24 * 60) + 24 * 60) % (24 * 60)
        val h = m / 60
        val min = m % 60
        return "%02d:%02d".format(h, min)
    }
}
