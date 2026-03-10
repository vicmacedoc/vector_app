package com.vm.vector.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vm.vector.data.DayCompletion
import com.vm.vector.data.HomeRepository
import com.vm.vector.data.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Day offset for Home tabs: -1 = Yesterday, 0 = Today, 1 = Tomorrow */
const val DAY_OFFSET_YESTERDAY = -1
const val DAY_OFFSET_TODAY = 0
const val DAY_OFFSET_TOMORROW = 1

data class HomeUiState(
    val weightKg: String = "",
    val bodyFatPercent: String = "",
    val sleepStartMinutes: Int = 22 * 60,
    val sleepEndMinutes: Int = 6 * 60,
    val actualSleepStartMinutes: Int = 22 * 60,
    val actualSleepEndMinutes: Int = 6 * 60,
    val dailyPlanText: String = "",
    val dailyPlanAudioPath: String? = null,
    val dailyPlanCompletionPercent: Int = 0,
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val routineLast7: List<DayCompletion> = emptyList(),
    val dietLast7: List<DayCompletion> = emptyList(),
    val workoutLast7: List<DayCompletion> = emptyList(),
    val diaryLast7: List<DayCompletion> = emptyList(),
    val selectedDayOffset: Int = DAY_OFFSET_TODAY,
    val yesterdayDateDisplay: String = "",
    val todayDateDisplay: String = "",
    val tomorrowDateDisplay: String = "",
)

class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val preferenceManager: PreferenceManager,
) : ViewModel() {

    private val _weightKg = MutableStateFlow("")
    private val _bodyFatPercent = MutableStateFlow("")
    private val _sleepStartMinutes = MutableStateFlow(22 * 60)
    private val _sleepEndMinutes = MutableStateFlow(6 * 60)
    private val _actualSleepStartMinutes = MutableStateFlow(22 * 60)
    private val _actualSleepEndMinutes = MutableStateFlow(6 * 60)
    private val _dailyPlanText = MutableStateFlow("")
    private val _dailyPlanAudioPath = MutableStateFlow<String?>(null)
    private val _dailyPlanCompletionPercent = MutableStateFlow(0)
    private val _isSaving = MutableStateFlow(false)
    private val _saveMessage = MutableStateFlow<String?>(null)
    private val _routineLast7 = MutableStateFlow<List<DayCompletion>>(emptyList())
    private val _dietLast7 = MutableStateFlow<List<DayCompletion>>(emptyList())
    private val _workoutLast7 = MutableStateFlow<List<DayCompletion>>(emptyList())
    private val _diaryLast7 = MutableStateFlow<List<DayCompletion>>(emptyList())
    private val _selectedDayOffset = MutableStateFlow(DAY_OFFSET_TODAY)
    private val _yesterdayDateDisplay = MutableStateFlow("")
    private val _todayDateDisplay = MutableStateFlow("")
    private val _tomorrowDateDisplay = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        _weightKg,
        _bodyFatPercent,
        _sleepStartMinutes,
        _sleepEndMinutes,
        _actualSleepStartMinutes,
        _actualSleepEndMinutes,
        _dailyPlanText,
        _dailyPlanAudioPath,
        _dailyPlanCompletionPercent,
        _isSaving,
        _saveMessage,
        _routineLast7,
        _dietLast7,
        _workoutLast7,
        _diaryLast7,
        _selectedDayOffset,
        _yesterdayDateDisplay,
        _todayDateDisplay,
        _tomorrowDateDisplay
    ) { values ->
        HomeUiState(
            weightKg = values[0] as String,
            bodyFatPercent = values[1] as String,
            sleepStartMinutes = values[2] as Int,
            sleepEndMinutes = values[3] as Int,
            actualSleepStartMinutes = values[4] as Int,
            actualSleepEndMinutes = values[5] as Int,
            dailyPlanText = values[6] as String,
            dailyPlanAudioPath = values[7] as String?,
            dailyPlanCompletionPercent = values[8] as Int,
            isSaving = values[9] as Boolean,
            saveMessage = values[10] as String?,
            routineLast7 = values[11] as List<DayCompletion>,
            dietLast7 = values[12] as List<DayCompletion>,
            workoutLast7 = values[13] as List<DayCompletion>,
            diaryLast7 = values[14] as List<DayCompletion>,
            selectedDayOffset = values[15] as Int,
            yesterdayDateDisplay = values[16] as String,
            todayDateDisplay = values[17] as String,
            tomorrowDateDisplay = values[18] as String
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        _yesterdayDateDisplay.value = formatDateDisplay(homeRepository.dateForOffset(DAY_OFFSET_YESTERDAY))
        _todayDateDisplay.value = formatDateDisplay(homeRepository.dateForOffset(DAY_OFFSET_TODAY))
        _tomorrowDateDisplay.value = formatDateDisplay(homeRepository.dateForOffset(DAY_OFFSET_TOMORROW))
        viewModelScope.launch { loadEntryForOffset(DAY_OFFSET_TODAY) }
        viewModelScope.launch { loadLast7Completions() }
    }

    /** Switch the active day tab and load that day's entry into the form. */
    fun setSelectedDayOffset(offset: Int) {
        if (_selectedDayOffset.value == offset) return
        _selectedDayOffset.value = offset
        viewModelScope.launch { loadEntryForOffset(offset) }
    }

    private suspend fun loadEntryForOffset(offset: Int) {
        val date = homeRepository.dateForOffset(offset)
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
            _dailyPlanAudioPath.value = it.dailyPlanAudioPath
        }
        if (entry == null) {
            _weightKg.value = ""
            _bodyFatPercent.value = ""
            _sleepStartMinutes.value = 22 * 60
            _sleepEndMinutes.value = 6 * 60
            _actualSleepStartMinutes.value = 22 * 60
            _actualSleepEndMinutes.value = 6 * 60
            _dailyPlanCompletionPercent.value = 0
            _dailyPlanAudioPath.value = null
        }
        _dailyPlanText.value = if (offset == DAY_OFFSET_TODAY) {
            preferenceManager.dailyPlanDraft.first() ?: entry?.dailyPlanText ?: ""
        } else {
            entry?.dailyPlanText ?: ""
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
    fun setDailyPlanText(value: String) {
        _dailyPlanText.value = value
        if (_selectedDayOffset.value == DAY_OFFSET_TODAY) {
            viewModelScope.launch { preferenceManager.saveDailyPlanDraft(value) }
        }
    }
    fun setDailyPlanAudioPath(path: String?) { _dailyPlanAudioPath.value = path }
    fun setDailyPlanCompletionPercent(value: Int) { _dailyPlanCompletionPercent.value = value.coerceIn(0, 100) }

    fun save() {
        viewModelScope.launch {
            _isSaving.value = true
            _saveMessage.value = null
            try {
                val weight = _weightKg.value.toDoubleOrNull()
                val bodyFat = _bodyFatPercent.value.toDoubleOrNull()
                val startStr = minutesToTime(_sleepStartMinutes.value)
                val endStr = minutesToTime(_sleepEndMinutes.value)
                val actualStartStr = minutesToTime(_actualSleepStartMinutes.value)
                val actualEndStr = minutesToTime(_actualSleepEndMinutes.value)
                val date = homeRepository.dateForOffset(_selectedDayOffset.value)
                homeRepository.saveForDate(
                    date = date,
                    weightKg = weight,
                    bodyFatPercent = bodyFat,
                    sleepStart = startStr,
                    sleepEnd = endStr,
                    actualSleepStart = actualStartStr,
                    actualSleepEnd = actualEndStr,
                    dailyPlanText = _dailyPlanText.value.ifBlank { null },
                    dailyPlanAudioPath = _dailyPlanAudioPath.value,
                    dailyPlanCompletionPercent = _dailyPlanCompletionPercent.value
                )
                // Sleep target and alarms are set in Settings
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
                loadDailyInputsFromDb()
                loadLast7Completions()
            } finally {
                _isRefreshingLast7.value = false
            }
        }
    }

    /** Load Sleep, Body and Daily Plan for the currently selected day (e.g. on pull-to-refresh). */
    private suspend fun loadDailyInputsFromDb() {
        loadEntryForOffset(_selectedDayOffset.value)
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

    private fun formatDateDisplay(dateStr: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
            if (parsed != null) SimpleDateFormat("EEE, MMM d", Locale.US).format(parsed) else dateStr
        } catch (_: Exception) {
            dateStr
        }
    }
}
