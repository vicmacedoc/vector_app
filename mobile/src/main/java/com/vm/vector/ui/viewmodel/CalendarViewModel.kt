package com.vm.vector.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vm.core.models.AuditStatus
import com.vm.core.models.DietEntry
import com.vm.vector.data.DietRepository
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
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val showSnackbar: Boolean = false,
    val snackbarMessage: String = "",
    val currentDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val datesWithCheckedMeals: Set<String> = emptySet(),
    val isEditingEnabled: Boolean = true,
)

class CalendarViewModel(
    private val repository: DietRepository,
) : ViewModel() {

    private val _currentDate = MutableStateFlow(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    private val _isLoading = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    private val _showSnackbar = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow("")
    private val _entries = MutableStateFlow<List<DietEntry>>(emptyList())
    private val _datesWithCheckedMeals = MutableStateFlow<Set<String>>(emptySet())
    private val _isEditingEnabled = MutableStateFlow(true)
    private val _editingEntry = MutableStateFlow<DietEntry?>(null)
    private var collectionJob: Job? = null
    
    val uiState: StateFlow<CalendarUiState> = combine(
        _currentDate,
        _entries,
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
        val loading = values[2] as Boolean
        val saving = values[3] as Boolean
        val snackbar = values[4] as Boolean
        val message = values[5] as String
        @Suppress("UNCHECKED_CAST")
        val checkedDates = values[6] as Set<String>
        val editingEnabled = values[7] as Boolean
        CalendarUiState(
            entries = entriesList,
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
        loadDatesWithCheckedMeals()
        updateEditingState()
    }
    
    fun refresh() {
        loadEntriesForDate(_currentDate.value)
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
                val dates = repository.getDatesWithCheckedMeals()
                _datesWithCheckedMeals.value = dates.toSet()
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    fun selectDate(date: String) {
        loadEntriesForDate(date)
        updateEditingState()
    }

    private fun loadEntriesForDate(date: String) {
        collectionJob?.cancel()
        _currentDate.value = date
        _isLoading.value = true
        collectionJob = viewModelScope.launch {
            try {
                // First, get existing entries from Room
                val existingEntries = repository.getEntriesByDateSync(date)
                
                // If entries exist in Room, show them immediately
                if (existingEntries.isNotEmpty()) {
                    _entries.value = existingEntries
                    _isLoading.value = false
                }
                
                // If no entries exist, load from preset (Drive fallback)
                if (existingEntries.isEmpty()) {
                    val presetResult = repository.loadPresetEntriesForDate(date)
                    when {
                        presetResult.isSuccess -> {
                            val presetEntries = presetResult.getOrNull() ?: emptyList()
                            // Insert preset entries into Room
                            if (presetEntries.isNotEmpty()) {
                                repository.insertEntries(presetEntries)
                            } else {
                                // Empty preset for this day - show info message
                                _isLoading.value = false
                                showError("No diet plan found for this day. Please check presets/diet_weekly.json in Drive.")
                            }
                        }
                        else -> {
                            // Drive fetch failed - show specific error message
                            _isLoading.value = false
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
                val initialEntries = repository.getEntriesByDate(date).first()
                _entries.value = initialEntries
                _isLoading.value = false
                
                // Continue collecting updates
                repository.getEntriesByDate(date).cancellable().collect { entries ->
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
            repository.updateEntry(updated)
        }
    }

    fun toggleEntryChecked(entryId: String) {
        if (!_isEditingEnabled.value) return
        val entry = _entries.value.find { it.id == entryId } ?: return
        val updated = entry.copy(isChecked = !entry.isChecked)
        viewModelScope.launch {
            repository.updateEntry(updated)
            loadDatesWithCheckedMeals() // Refresh checked dates
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
            repository.insertEntry(newEntry)
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
            repository.updateEntry(updated)
        }
    }
    
    fun deleteEntry(entryId: String) {
        if (!_isEditingEnabled.value) return
        viewModelScope.launch {
            val entry = _entries.value.find { it.id == entryId } ?: return@launch
            repository.deleteEntry(entry)
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
            val result = repository.saveDay(_currentDate.value)
            _isSaving.value = false
            if (result.isSuccess) {
                showSuccess("Day saved successfully")
            } else {
                showError("Failed to save: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
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
