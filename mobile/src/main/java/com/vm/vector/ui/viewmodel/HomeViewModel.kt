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

data class HomeUiState(
    val weightKg: String = "",
    val bodyFatPercent: String = "",
    val sleepStartMinutes: Int = 22 * 60,  // 22:00 default
    val sleepEndMinutes: Int = 6 * 60,     // 06:00 default
    val dailyPlanText: String = "",
    val dailyPlanCompletionPercent: Int = 0,
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val routineLast7: List<DayCompletion> = emptyList(),
    val dietLast7: List<DayCompletion> = emptyList(),
    val workoutLast7: List<DayCompletion> = emptyList(),
    val diaryLast7: List<DayCompletion> = emptyList(),
)

class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _weightKg = MutableStateFlow("")
    private val _bodyFatPercent = MutableStateFlow("")
    private val _sleepStartMinutes = MutableStateFlow(22 * 60)
    private val _sleepEndMinutes = MutableStateFlow(6 * 60)
    private val _dailyPlanText = MutableStateFlow("")
    private val _dailyPlanCompletionPercent = MutableStateFlow(0)
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
        _dailyPlanText,
        _dailyPlanCompletionPercent,
        _isSaving,
        _saveMessage,
        _routineLast7,
        _dietLast7,
        _workoutLast7,
        _diaryLast7
    ) { values ->
        HomeUiState(
            weightKg = values[0] as String,
            bodyFatPercent = values[1] as String,
            sleepStartMinutes = values[2] as Int,
            sleepEndMinutes = values[3] as Int,
            dailyPlanText = values[4] as String,
            dailyPlanCompletionPercent = values[5] as Int,
            isSaving = values[6] as Boolean,
            saveMessage = values[7] as String?,
            routineLast7 = values[8] as List<DayCompletion>,
            dietLast7 = values[9] as List<DayCompletion>,
            workoutLast7 = values[10] as List<DayCompletion>,
            diaryLast7 = values[11] as List<DayCompletion>
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        viewModelScope.launch {
            val draft = preferenceManager.dailyPlanDraft.first()
            homeRepository.getTodayEntry()
                .catch { }
                .collect { entry ->
                    entry?.let {
                        _weightKg.value = it.weightKg?.toString()?.trim() ?: ""
                        _bodyFatPercent.value = it.bodyFatPercent?.toString()?.trim() ?: ""
                        _sleepStartMinutes.value = parseTimeToMinutes(it.sleepStart) ?: 22 * 60
                        _sleepEndMinutes.value = parseTimeToMinutes(it.sleepEnd) ?: 6 * 60
                        _dailyPlanCompletionPercent.value = it.dailyPlanCompletionPercent.coerceIn(0, 100)
                    }
                    _dailyPlanText.value = draft ?: entry?.dailyPlanText ?: ""
                }
        }
        viewModelScope.launch { loadLast7Completions() }
    }

    fun setWeight(value: String) { _weightKg.value = value }
    fun setBodyFat(value: String) { _bodyFatPercent.value = value }
    fun setSleepStart(minutes: Int) { _sleepStartMinutes.value = minutes }
    fun setSleepEnd(minutes: Int) { _sleepEndMinutes.value = minutes }
    fun setDailyPlanText(value: String) {
        _dailyPlanText.value = value
        viewModelScope.launch { preferenceManager.saveDailyPlanDraft(value) }
    }
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
                homeRepository.saveToday(
                    weightKg = weight,
                    bodyFatPercent = bodyFat,
                    sleepStart = startStr,
                    sleepEnd = endStr,
                    dailyPlanText = _dailyPlanText.value.ifBlank { null },
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

    fun refreshLast7() {
        viewModelScope.launch { loadLast7Completions() }
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
