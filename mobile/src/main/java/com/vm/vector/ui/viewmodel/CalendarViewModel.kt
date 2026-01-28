package com.vm.vector.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vm.core.models.AuditStatus
import com.vm.core.models.DietEntry
import com.vm.core.models.RoutineEntry
import com.vm.core.models.RoutineStatus
import com.vm.core.models.RoutineType
import java.util.UUID
import com.vm.vector.data.DietRepository
import com.vm.vector.data.RoutineRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

data class CalendarUiState(
    val entries: List<DietEntry> = emptyList(),
    val routineEntries: List<RoutineEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val showSnackbar: Boolean = false,
    val snackbarMessage: String = "",
    val currentDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val datesWithCheckedMeals: Set<String> = emptySet(),
    val isEditingEnabled: Boolean = true,
)

class CalendarViewModel(
    private val dietRepository: DietRepository,
    private val routineRepository: RoutineRepository,
) : ViewModel() {

    private val _currentDate = MutableStateFlow(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    private val _isLoading = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    private val _showSnackbar = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow("")
    private val _entries = MutableStateFlow<List<DietEntry>>(emptyList())
    private val _routineEntries = MutableStateFlow<List<RoutineEntry>>(emptyList())
    private val _datesWithCheckedMeals = MutableStateFlow<Set<String>>(emptySet())
    private val _isEditingEnabled = MutableStateFlow(true)
    private val _editingEntry = MutableStateFlow<DietEntry?>(null)
    private var collectionJob: Job? = null
    private var routineCollectionJob: Job? = null
    
    val uiState: StateFlow<CalendarUiState> = combine(
        _currentDate,
        _entries,
        _routineEntries,
        _isLoading,
        _isSaving,
        _showSnackbar,
        _snackbarMessage,
        _datesWithCheckedMeals,
        _isEditingEnabled
    ) { values: Array<Any> ->
        val date = values[0] as String
        @Suppress("UNCHECKED_CAST")
        val entriesList = values[1] as List<DietEntry>
        @Suppress("UNCHECKED_CAST")
        val routineEntriesList = values[2] as List<RoutineEntry>
        val loading = values[3] as Boolean
        val saving = values[4] as Boolean
        val snackbar = values[5] as Boolean
        val message = values[6] as String
        @Suppress("UNCHECKED_CAST")
        val checkedDates = values[7] as Set<String>
        val editingEnabled = values[8] as Boolean
        CalendarUiState(
            entries = entriesList,
            routineEntries = routineEntriesList,
            isLoading = loading,
            isSaving = saving,
            showSnackbar = snackbar,
            snackbarMessage = message,
            currentDate = date,
            datesWithCheckedMeals = checkedDates,
            isEditingEnabled = editingEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState()
    )

    init {
        loadEntriesForDate(_currentDate.value)
        loadRoutineEntriesForDate(_currentDate.value)
        loadDatesWithCheckedMeals()
        updateEditingState()
    }
    
    fun refresh() {
        loadEntriesForDate(_currentDate.value)
        loadRoutineEntriesForDate(_currentDate.value)
        loadDatesWithCheckedMeals()
    }
    
    private fun updateEditingState() {
        viewModelScope.launch {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = dateFormat.format(Date())
            val currentDateStr = _currentDate.value
            _isEditingEnabled.value = currentDateStr <= today
        }
    }
    
    private fun loadDatesWithCheckedMeals() {
        viewModelScope.launch {
            try {
                val dates = dietRepository.getDatesWithCheckedMeals()
                _datesWithCheckedMeals.value = dates.toSet()
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    fun selectDate(date: String) {
        loadEntriesForDate(date)
        loadRoutineEntriesForDate(date)
        updateEditingState()
    }

    private fun loadEntriesForDate(date: String) {
        collectionJob?.cancel()
        _currentDate.value = date
        _isLoading.value = true
        collectionJob = viewModelScope.launch {
            try {
                // First, get existing entries from Room
                val existingEntries = dietRepository.getEntriesByDateSync(date)
                
                // If entries exist in Room, show them immediately
                if (existingEntries.isNotEmpty()) {
                    _entries.value = existingEntries
                    _isLoading.value = false
                }
                
                // If no entries exist, load from preset (Drive fallback)
                if (existingEntries.isEmpty()) {
                    val presetResult = dietRepository.loadPresetEntriesForDate(date)
                    when {
                        presetResult.isSuccess -> {
                            val presetEntries = presetResult.getOrNull() ?: emptyList()
                            // Insert preset entries into Room
                            if (presetEntries.isNotEmpty()) {
                                dietRepository.insertEntries(presetEntries)
                            } else {
                                // Empty preset for this day - show info message
                                showError("No diet plan found for this day. Please check presets/diet_weekly.json in Drive.")
                            }
                        }
                        else -> {
                            // Drive fetch failed - show specific error message
                            val error = presetResult.exceptionOrNull()
                            val errorMsg = error?.message ?: "Unknown error"
                            when {
                                errorMsg.contains("diet_weekly.json not found", ignoreCase = true) || 
                                errorMsg.contains("ListsFolderNotFound", ignoreCase = true) -> {
                                    showError("Diet plan not found. Please ensure presets/diet_weekly.json exists in your Drive folder.")
                                }
                                errorMsg.contains("403") || errorMsg.contains("Forbidden") -> {
                                    showError("Drive access denied. Please grant DRIVE scope permission in Settings.")
                                }
                                errorMsg.contains("NeedsRemoteConsent") || errorMsg.contains("consent") -> {
                                    showError("Drive consent required. Please authorize access in Settings.")
                                }
                                errorMsg.contains("Drive folder ID not configured") -> {
                                    showError("Drive not configured. Please set your Drive folder ID in Settings.")
                                }
                                else -> {
                                    showError("Failed to load diet plan: $errorMsg")
                                }
                            }
                        }
                    }
                }
                
                // Collect entries (will include newly inserted presets or existing entries)
                // Use first() to get initial value, then collect updates
                val initialEntries = dietRepository.getEntriesByDate(date).first()
                _entries.value = initialEntries
                _isLoading.value = false
                
                // Continue collecting updates
                dietRepository.getEntriesByDate(date).cancellable().collect { entries ->
                    _entries.value = entries
                    loadDatesWithCheckedMeals() // Refresh checked dates when entries change
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancellation is expected when switching dates - don't show error
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                showError("Failed to load entries: ${e.message}")
            }
        }
    }

    private fun loadRoutineEntriesForDate(date: String) {
        routineCollectionJob?.cancel()
        routineCollectionJob = viewModelScope.launch {
            try {
                // First, get existing entries from Room
                val existingEntries = routineRepository.getEntriesByDateSync(date)
                
                // If entries exist in Room, show them immediately
                if (existingEntries.isNotEmpty()) {
                    _routineEntries.value = existingEntries
                }
                
                // If no entries exist, load from preset (Drive fallback)
                if (existingEntries.isEmpty()) {
                    val presetResult = routineRepository.loadPresetEntriesForDate(date)
                    when {
                        presetResult.isSuccess -> {
                            val presetEntries = presetResult.getOrNull() ?: emptyList()
                            // Insert preset entries into Room
                            if (presetEntries.isNotEmpty()) {
                                routineRepository.insertEntries(presetEntries)
                            } else {
                                // Empty preset for this day - show info message
                                showError("No routine plan found for this day. Please check presets/routine_weekly.json in Drive.")
                            }
                        }
                        else -> {
                            // Drive fetch failed - show specific error message
                            val error = presetResult.exceptionOrNull()
                            val errorMsg = error?.message ?: "Unknown error"
                            when {
                                errorMsg.contains("routine_weekly.json not found", ignoreCase = true) || 
                                errorMsg.contains("ListsFolderNotFound", ignoreCase = true) -> {
                                    showError("Routine plan not found. Please ensure presets/routine_weekly.json exists in your Drive folder.")
                                }
                                errorMsg.contains("403") || errorMsg.contains("Forbidden") -> {
                                    showError("Drive access denied. Please grant DRIVE scope permission in Settings.")
                                }
                                errorMsg.contains("NeedsRemoteConsent") || errorMsg.contains("consent") -> {
                                    showError("Drive consent required. Please authorize access in Settings.")
                                }
                                errorMsg.contains("Drive folder ID not configured") -> {
                                    showError("Drive not configured. Please set your Drive folder ID in Settings.")
                                }
                                else -> {
                                    showError("Failed to load routine plan: $errorMsg")
                                }
                            }
                        }
                    }
                }
                
                // Collect entries (will include newly inserted presets or existing entries)
                // Use first() to get initial value, then collect updates
                val initialEntries = routineRepository.getEntriesByDate(date).first()
                _routineEntries.value = initialEntries
                
                // Continue collecting updates
                routineRepository.getEntriesByDate(date).cancellable().collect { entries ->
                    _routineEntries.value = entries
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancellation is expected when switching dates - don't show error
            } catch (e: Exception) {
                showError("Failed to load routine entries: ${e.message}")
            }
        }
    }

    fun updateEntryMultiplier(entryId: String, multiplier: Double) {
        if (!_isEditingEnabled.value) return
        val entry = _entries.value.find { it.id == entryId } ?: return
        val newStatus = when {
            multiplier != 1.0 -> AuditStatus.ADJUSTED
            else -> AuditStatus.PLANNED
        }
        val updated = entry.copy(
            quantityMultiplier = multiplier.coerceIn(0.0, 3.0),
            status = newStatus
        )
        viewModelScope.launch {
            dietRepository.updateEntry(updated)
        }
    }

    fun toggleEntryChecked(entryId: String) {
        if (!_isEditingEnabled.value) return
        val entry = _entries.value.find { it.id == entryId } ?: return
        val updated = entry.copy(isChecked = !entry.isChecked)
        viewModelScope.launch {
            dietRepository.updateEntry(updated)
            loadDatesWithCheckedMeals() // Refresh checked dates
        }
    }

    fun updateRoutineValue(entryId: String, newValue: Double?) {
        if (!_isEditingEnabled.value) return
        viewModelScope.launch {
            routineRepository.updateEntryValue(entryId, _currentDate.value, newValue)
        }
    }

    fun updateRoutineStatus(entryId: String, newStatus: RoutineStatus) {
        if (!_isEditingEnabled.value) return
        viewModelScope.launch {
            routineRepository.updateEntryStatus(entryId, _currentDate.value, newStatus)
        }
    }
    
    fun toggleNumericalStatus(entryId: String) {
        if (!_isEditingEnabled.value) return
        viewModelScope.launch {
            routineRepository.toggleNumericalStatus(entryId, _currentDate.value)
        }
    }
    
    fun addManualRoutineEntry(
        title: String,
        category: String,
        type: RoutineType,
        goalValue: Double? = null,
        partialThreshold: Double? = null,
        minValue: Double? = null,
        maxValue: Double? = null,
        unit: String? = null,
        directionBetter: Int = 1
    ) {
        if (!_isEditingEnabled.value) return
        viewModelScope.launch {
            val newEntry = RoutineEntry(
                id = UUID.randomUUID().toString(),
                date = _currentDate.value,
                category = category,
                title = title,
                type = type,
                unit = unit,
                goalValue = goalValue,
                partialThreshold = partialThreshold,
                minValue = minValue,
                maxValue = maxValue,
                currentValue = null,
                directionBetter = directionBetter,
                status = RoutineStatus.NONE,
                isGoalMet = false,
                timestamp = System.currentTimeMillis()
            )
            routineRepository.insertEntry(newEntry)
        }
    }
    
    fun toggleEditingEnabled() {
        _isEditingEnabled.value = !_isEditingEnabled.value
    }

    fun addManualEntry(
        name: String,
        plannedTime: String,
        plannedAmount: Double,
        unit: String,
        kcal: Double,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fats: Double = 0.0
    ) {
        if (!_isEditingEnabled.value) return
        viewModelScope.launch {
            val newEntry = DietEntry(
                id = UUID.randomUUID().toString(),
                date = _currentDate.value,
                plannedTime = plannedTime,
                name = name,
                kcal = kcal,
                protein = protein,
                carbs = carbs,
                fats = fats,
                plannedAmount = plannedAmount,
                quantityMultiplier = 1.0,
                unit = unit,
                isChecked = false,
                status = AuditStatus.UNPLANNED,
                timestamp = System.currentTimeMillis()
            )
            dietRepository.insertEntry(newEntry)
        }
    }
    
    fun updateManualEntry(
        entryId: String,
        name: String,
        plannedTime: String,
        plannedAmount: Double,
        unit: String,
        kcal: Double,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fats: Double = 0.0
    ) {
        if (!_isEditingEnabled.value) return
        viewModelScope.launch {
            val entry = _entries.value.find { it.id == entryId } ?: return@launch
            val updated = entry.copy(
                name = name,
                plannedTime = plannedTime,
                plannedAmount = plannedAmount,
                unit = unit,
                kcal = kcal,
                protein = protein,
                carbs = carbs,
                fats = fats
            )
            dietRepository.updateEntry(updated)
        }
    }
    
    fun deleteEntry(entryId: String) {
        if (!_isEditingEnabled.value) return
        viewModelScope.launch {
            val entry = _entries.value.find { it.id == entryId } ?: return@launch
            dietRepository.deleteEntry(entry)
        }
    }
    
    fun editUnplannedEntry(entry: DietEntry) {
        _editingEntry.value = entry
    }
    
    val editingEntry: StateFlow<DietEntry?> = _editingEntry.asStateFlow()
    
    fun getEditingEntry(): DietEntry? = _editingEntry.value
    
    fun clearEditingEntry() {
        _editingEntry.value = null
    }

    fun saveDay() {
        viewModelScope.launch {
            _isSaving.value = true
            val dietResult = dietRepository.saveDay(_currentDate.value)
            val routineResult = routineRepository.saveDay(_currentDate.value)
            _isSaving.value = false
            when {
                dietResult.isSuccess && routineResult.isSuccess -> {
                    showSuccess("Day saved successfully")
                }
                dietResult.isFailure -> {
                    showError("Failed to save diet: ${dietResult.exceptionOrNull()?.message ?: "Unknown error"}")
                }
                routineResult.isFailure -> {
                    showError("Failed to save routine: ${routineResult.exceptionOrNull()?.message ?: "Unknown error"}")
                }
            }
        }
    }

    fun dismissSnackbar() {
        _showSnackbar.value = false
    }

    private fun showError(message: String) {
        _showSnackbar.value = true
        _snackbarMessage.value = message
    }

    private fun showSuccess(message: String) {
        _showSnackbar.value = true
        _snackbarMessage.value = message
    }
}
