package com.vm.vector.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vm.core.models.HabitDefinition
import com.vm.core.models.HabitLog
import com.vm.core.models.HabitStatus
import com.vm.core.models.MotionTask
import com.vm.vector.data.BrainRepository
import com.vm.vector.VectorApplication
import com.vm.vector.data.DailySync
import com.vm.vector.data.DayCompletion
import com.vm.vector.data.HabitRepository
import com.vm.vector.data.HomeRepository
import com.vm.vector.data.SelfRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class HomeUiState(
    val selectedDate: String = "",
    val homeFieldsEditable: Boolean = true,
    val weightKg: String = "",
    val bodyFatPercent: String = "",
    val habitDefinitions: List<HabitDefinition> = emptyList(),
    val selectedHabitIds: List<String> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
    val habitScoreSummaries: Map<String, com.vm.vector.data.HabitScoreSummary> = emptyMap(),
    val isSaving: Boolean = false,
    /** True when body inputs or the day's habits differ from what is saved. */
    val hasUnsavedChanges: Boolean = false,
    val saveMessage: String? = null,
    val routineLast7: List<DayCompletion> = emptyList(),
    val foodLast7: List<DayCompletion> = emptyList(),
    val workoutLast7: List<DayCompletion> = emptyList(),
    val selfLast7: List<DayCompletion> = emptyList(),
    val brainInboxCount: Int = 0,
    val brainOverdue: Int = 0,
    val brainDueToday: Int = 0,
    val brainActiveTiles: Int = 0,
    val brainUpNext: List<MotionTask> = emptyList(),
    val wearUnseenEvents: List<com.vm.vector.data.WearActivityEvent> = emptyList(),
)

/** The saved state of Home's editable fields, normalized so cosmetic differences don't count. */
private data class HomeSnapshot(
    val weightKg: Double?,
    val bodyFatPercent: Double?,
    val selectedHabitIds: List<String>,
    val habitStatuses: Map<String, HabitStatus>,
) {
    fun withoutHabit(habitId: String) = copy(
        selectedHabitIds = selectedHabitIds - habitId,
        habitStatuses = habitStatuses - habitId,
    )

    companion object {
        fun of(
            weightKg: String,
            bodyFatPercent: String,
            selectedHabitIds: List<String>,
            habitLogs: List<HabitLog>,
        ): HomeSnapshot {
            val byHabit = habitLogs.associateBy { it.habitId }
            return HomeSnapshot(
                weightKg = weightKg.trim().toDoubleOrNull(),
                bodyFatPercent = bodyFatPercent.trim().toDoubleOrNull(),
                selectedHabitIds = selectedHabitIds,
                habitStatuses = selectedHabitIds.associateWith { byHabit[it]?.status ?: HabitStatus.NONE },
            )
        }
    }
}

class HomeViewModel(
    application: Application,
    private val homeRepository: HomeRepository,
    private val selfRepository: SelfRepository,
    private val habitRepository: HabitRepository,
    private val brainRepository: BrainRepository,
) : AndroidViewModel(application) {

    private val _weightKg = MutableStateFlow("")
    private val _bodyFatPercent = MutableStateFlow("")
    private val _habitDefinitions = MutableStateFlow<List<HabitDefinition>>(emptyList())
    private val _selectedHabitIds = MutableStateFlow<List<String>>(emptyList())
    private val _habitLogs = MutableStateFlow<List<HabitLog>>(emptyList())
    private val _habitScoreSummaries =
        MutableStateFlow<Map<String, com.vm.vector.data.HabitScoreSummary>>(emptyMap())
    private val _selectedDate = MutableStateFlow(homeRepository.dateForOffset(0))
    private val _isSaving = MutableStateFlow(false)
    private val _saveMessage = MutableStateFlow<String?>(null)
    private val _routineLast7 = MutableStateFlow<List<DayCompletion>>(emptyList())
    private val _foodLast7 = MutableStateFlow<List<DayCompletion>>(emptyList())
    private val _workoutLast7 = MutableStateFlow<List<DayCompletion>>(emptyList())
    private val _selfLast7 = MutableStateFlow<List<DayCompletion>>(emptyList())
    private val _brainInboxCount = MutableStateFlow(0)
    private val _brainOverdue = MutableStateFlow(0)
    private val _brainDueToday = MutableStateFlow(0)
    private val _brainActiveTiles = MutableStateFlow(0)
    private val _brainUpNext = MutableStateFlow<List<MotionTask>>(emptyList())
    private val _wearUnseenEvents =
        MutableStateFlow<List<com.vm.vector.data.WearActivityEvent>>(emptyList())
    /** Null while the selected date is loading. */
    private val _savedSnapshot = MutableStateFlow<HomeSnapshot?>(null)

    private val hasUnsavedChanges = combine(
        _weightKg,
        _bodyFatPercent,
        _selectedHabitIds,
        _habitLogs,
        _savedSnapshot,
    ) { weight, bodyFat, selectedIds, logs, saved ->
        saved != null && HomeSnapshot.of(weight, bodyFat, selectedIds, logs) != saved
    }

    val uiState: StateFlow<HomeUiState> = combine(
        _weightKg,
        _bodyFatPercent,
        _selectedDate,
        _isSaving,
        _saveMessage,
        _habitDefinitions,
        _selectedHabitIds,
        _habitLogs,
        _habitScoreSummaries,
        _routineLast7,
        _foodLast7,
        _workoutLast7,
        _selfLast7,
        _brainInboxCount,
        _brainOverdue,
        _brainDueToday,
        _brainActiveTiles,
        _brainUpNext,
        _wearUnseenEvents,
        hasUnsavedChanges,
    ) { values: Array<Any?> ->
        val selected = values[2] as String
        val isFuture = selected > homeRepository.dateForOffset(0)
        @Suppress("UNCHECKED_CAST")
        HomeUiState(
            selectedDate = selected,
            homeFieldsEditable = !isFuture,
            weightKg = values[0] as String,
            bodyFatPercent = values[1] as String,
            isSaving = values[3] as Boolean,
            saveMessage = values[4] as String?,
            habitDefinitions = (values[5] as? List<HabitDefinition>) ?: emptyList(),
            selectedHabitIds = (values[6] as? List<String>) ?: emptyList(),
            habitLogs = (values[7] as? List<HabitLog>) ?: emptyList(),
            habitScoreSummaries = (values[8] as? Map<String, com.vm.vector.data.HabitScoreSummary>)
                ?: emptyMap(),
            routineLast7 = (values[9] as? List<DayCompletion>) ?: emptyList(),
            foodLast7 = (values[10] as? List<DayCompletion>) ?: emptyList(),
            workoutLast7 = (values[11] as? List<DayCompletion>) ?: emptyList(),
            selfLast7 = (values[12] as? List<DayCompletion>) ?: emptyList(),
            brainInboxCount = values[13] as Int,
            brainOverdue = values[14] as Int,
            brainDueToday = values[15] as Int,
            brainActiveTiles = values[16] as Int,
            brainUpNext = (values[17] as? List<MotionTask>) ?: emptyList(),
            wearUnseenEvents = (values[18] as? List<com.vm.vector.data.WearActivityEvent>)
                ?: emptyList(),
            hasUnsavedChanges = values[19] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        viewModelScope.launch {
            loadEntryForSelectedDate()
            loadLast7Completions()
            loadBrainSummary()
        }
        habitRepository.observeScoreSummaries()
            .onEach { _habitScoreSummaries.value = it }
            .launchIn(viewModelScope)
        homeRepository.observeDraftChanges()
            .map { keys -> keys.toSet() }
            .distinctUntilChanged()
            .drop(1)
            .onEach { loadLast7Completions() }
            .launchIn(viewModelScope)
        (getApplication() as? VectorApplication)?.let { app ->
            app.databaseResetEvents.onEach {
                viewModelScope.launch {
                    loadEntryForSelectedDate()
                    loadLast7Completions()
                    loadBrainSummary()
                }
            }.launchIn(viewModelScope)
            DailySync.imported.onEach {
                if (_savedSnapshot.value != null && currentSnapshot() != _savedSnapshot.value) return@onEach
                loadEntryForSelectedDate()
                loadLast7Completions()
            }.launchIn(viewModelScope)
            app.wearActivityStore.events
                .onEach { events -> _wearUnseenEvents.value = events.filter { !it.seen } }
                .launchIn(viewModelScope)
        }
    }

    fun setSelectedDate(isoDate: String) {
        viewModelScope.launch {
            _selectedDate.value = isoDate
            loadEntryForSelectedDate()
            loadLast7Completions()
        }
    }

    private suspend fun loadEntryForSelectedDate() {
        _savedSnapshot.value = null
        val date = _selectedDate.value
        val entry = homeRepository.getEntryForDate(date)
        entry?.let {
            _weightKg.value = it.weightKg?.toString()?.trim() ?: ""
            _bodyFatPercent.value = it.bodyFatPercent?.toString()?.trim() ?: ""
        }
        if (entry == null) {
            _weightKg.value = ""
            _bodyFatPercent.value = ""
        }
        loadHabitsForDate(date)
        _savedSnapshot.value = currentSnapshot()
    }

    private fun currentSnapshot() = HomeSnapshot.of(
        weightKg = _weightKg.value,
        bodyFatPercent = _bodyFatPercent.value,
        selectedHabitIds = _selectedHabitIds.value,
        habitLogs = _habitLogs.value,
    )

    private suspend fun loadHabitsForDate(date: String) {
        _habitDefinitions.value = habitRepository.getAllDefinitions()
        val selected = habitRepository.getSelectedHabitIdsForDate(date)
        _selectedHabitIds.value = selected
        val logs = habitRepository.getLogsForDate(date)
        val byHabitId = logs.associateBy { it.habitId }
        _habitLogs.value = selected.mapNotNull { byHabitId[it] }
    }

    fun setWeight(value: String) { _weightKg.value = value }
    fun setBodyFat(value: String) { _bodyFatPercent.value = value }

    fun updateHabitStatus(habitId: String, status: HabitStatus) {
        if (!_homeEditable()) return
        val date = _selectedDate.value
        val existing = _habitLogs.value.find { it.habitId == habitId }
        _habitLogs.value = if (existing != null) {
            _habitLogs.value.map {
                if (it.habitId == habitId) {
                    it.copy(status = status, timestamp = System.currentTimeMillis())
                } else {
                    it
                }
            }
        } else {
            _habitLogs.value + HabitLog(
                id = "hlog_${date}_$habitId",
                date = date,
                habitId = habitId,
                status = status,
            )
        }
    }

    fun createHabit(name: String, onCreated: (HabitDefinition) -> Unit = {}) {
        if (!_homeEditable()) return
        viewModelScope.launch {
            habitRepository.createHabit(name).fold(
                onSuccess = { def ->
                    DailySync.markConfig(getApplication())
                    _habitDefinitions.value = _habitDefinitions.value + def
                    onCreated(def)
                },
                onFailure = { _saveMessage.value = it.message },
            )
        }
    }

    fun deleteHabit(id: String) {
        if (!_homeEditable()) return
        viewModelScope.launch {
            habitRepository.deleteHabit(id)
            DailySync.markConfig(getApplication())
            _savedSnapshot.value = _savedSnapshot.value?.withoutHabit(id)
            _habitDefinitions.value = _habitDefinitions.value.filter { it.id != id }
            _selectedHabitIds.value = _selectedHabitIds.value.filter { it != id }
            _habitLogs.value = _habitLogs.value.filter { it.habitId != id }
        }
    }

    fun setSelectedHabits(ids: List<String>) {
        if (!_homeEditable()) return
        val date = _selectedDate.value
        val known = _habitDefinitions.value.map { it.id }.toSet()
        val cleaned = ids.filter { it in known }.distinct()
        _selectedHabitIds.value = cleaned
        val byId = _habitLogs.value.associateBy { it.habitId }
        _habitLogs.value = cleaned.map { habitId ->
            byId[habitId] ?: HabitLog(
                id = "hlog_${date}_$habitId",
                date = date,
                habitId = habitId,
                status = HabitStatus.NONE,
            )
        }
    }

    private fun _homeEditable(): Boolean =
        _selectedDate.value <= homeRepository.dateForOffset(0)

    fun save() {
        viewModelScope.launch {
            if (!_homeEditable()) return@launch
            _isSaving.value = true
            _saveMessage.value = null
            try {
                val weight = _weightKg.value.toDoubleOrNull()
                val bodyFat = _bodyFatPercent.value.toDoubleOrNull()
                val date = _selectedDate.value
                val existing = homeRepository.getEntryForDate(date)
                homeRepository.saveForDate(
                    date = date,
                    weightKg = weight,
                    bodyFatPercent = bodyFat,
                    dailyPlanText = existing?.dailyPlanText,
                    dailyPlanAudioPath = existing?.dailyPlanAudioPath,
                )
                habitRepository.persistDaySnapshot(
                    date = date,
                    definitions = _habitDefinitions.value,
                    selectedIds = _selectedHabitIds.value,
                    logs = _habitLogs.value,
                )
                loadHabitsForDate(date)
                _savedSnapshot.value = currentSnapshot()
                _saveMessage.value = "Saved"
                DailySync.markDay(getApplication(), date)
                loadLast7Completions()
            } catch (e: Exception) {
                _saveMessage.value = "Save failed"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearSaveMessage() { _saveMessage.value = null }

    suspend fun uploadBodyScanPhoto(driveFolderName: String, jpegBytes: ByteArray): Result<Unit> =
        selfRepository.uploadBodyScanPhotoToFixedCollectionFolder(
            driveFolderName,
            "image/jpeg",
            jpegBytes
        )

    private val _isRefreshingLast7 = MutableStateFlow(false)
    val isRefreshingLast7: StateFlow<Boolean> = _isRefreshingLast7.asStateFlow()

    fun refreshLast7() {
        viewModelScope.launch {
            _isRefreshingLast7.value = true
            try {
                loadEntryForSelectedDate()
                loadLast7Completions()
                loadBrainSummary()
            } finally {
                _isRefreshingLast7.value = false
            }
        }
    }

    /** Deletes saved data and drafts for [category] on [date]; the Calendar falls back to the preset. */
    fun clearDay(category: String, date: String) {
        viewModelScope.launch {
            try {
                homeRepository.clearDay(category, date)
                DailySync.markDay(getApplication(), date)
                (getApplication() as? VectorApplication)?.notifyCalendarDayCleared(category, date)
                _saveMessage.value = "$category cleared for $date"
            } catch (e: Exception) {
                _saveMessage.value = "Couldn't clear $category: ${e.message}"
            }
            loadLast7Completions()
        }
    }

    fun completeBrainTask(taskId: String) {
        viewModelScope.launch {
            brainRepository.completeTask(taskId)
            loadBrainSummary()
        }
    }

    fun dismissWearEvents() {
        (getApplication() as? VectorApplication)?.wearActivityStore?.markAllSeen()
    }

    fun markWearEventSeen(id: String) {
        (getApplication() as? VectorApplication)?.wearActivityStore?.markSeen(id)
    }

    private suspend fun loadBrainSummary() {
        _brainInboxCount.value = brainRepository.inboxCount().first()
        _brainOverdue.value = brainRepository.countOverdue().first()
        _brainDueToday.value = brainRepository.countDueToday().first()
        _brainActiveTiles.value = brainRepository.countActiveTiles().first()
        _brainUpNext.value = brainRepository.upcomingOpen(3)
    }

    private suspend fun loadLast7Completions() {
        val endDate = _selectedDate.value
        _routineLast7.value = homeRepository.getRoutineCompletionLast7Days(endDate)
        _foodLast7.value = homeRepository.getFoodCompletionLast7Days(endDate)
        _workoutLast7.value = homeRepository.getWorkoutCompletionLast7Days(endDate)
        _selfLast7.value = homeRepository.getSelfCompletionLast7Days(endDate)
    }
}
