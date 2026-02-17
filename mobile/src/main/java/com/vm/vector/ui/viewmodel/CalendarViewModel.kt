package com.vm.vector.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vm.core.models.AuditStatus
import com.vm.core.models.DietEntry
import com.vm.core.models.DiaryCollection
import com.vm.core.models.DiaryCollectionImage
import com.vm.core.models.RoutineEntry
import com.vm.core.models.RoutineStatus
import com.vm.core.models.RoutineType
import com.vm.core.models.WorkoutSet
import java.util.UUID
import com.vm.vector.data.DietRepository
import com.vm.vector.data.DiaryRepository
import com.vm.vector.data.RoutineRepository
import com.vm.vector.data.WorkoutRepository
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
    val workoutSets: List<WorkoutSet> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isWorkoutSaving: Boolean = false,
    val showSnackbar: Boolean = false,
    val snackbarMessage: String = "",
    val currentDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val datesWithCheckedMeals: Set<String> = emptySet(),
    val isEditingEnabled: Boolean = true,
    // Diary
    val diaryMood: Int? = null,
    val diaryJournalText: String = "",
    val diaryCollectionsWithImages: List<Pair<DiaryCollection, List<DiaryCollectionImage>>> = emptyList(),
    val isDiarySaving: Boolean = false,
)

class CalendarViewModel(
    private val dietRepository: DietRepository,
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val diaryRepository: DiaryRepository,
) : ViewModel() {

    private val _currentDate = MutableStateFlow(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    private val _isLoading = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    private val _showSnackbar = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow("")
    private val _entries = MutableStateFlow<List<DietEntry>>(emptyList())
    private val _routineEntries = MutableStateFlow<List<RoutineEntry>>(emptyList())
    private val _workoutSets = MutableStateFlow<List<WorkoutSet>>(emptyList())
    private val _datesWithCheckedMeals = MutableStateFlow<Set<String>>(emptySet())
    private val _isEditingEnabled = MutableStateFlow(true)
    private val _isWorkoutSaving = MutableStateFlow(false)
    private val _editingEntry = MutableStateFlow<DietEntry?>(null)
    private val _diaryMood = MutableStateFlow<Int?>(null)
    private val _diaryJournalText = MutableStateFlow("")
    private val _diaryCollectionsWithImages = MutableStateFlow<List<Pair<DiaryCollection, List<DiaryCollectionImage>>>>(emptyList())
    private val _isDiarySaving = MutableStateFlow(false)
    private var collectionJob: Job? = null
    private var routineCollectionJob: Job? = null
    private var workoutCollectionJob: Job? = null

    val uiState: StateFlow<CalendarUiState> = combine(
        _currentDate,
        _entries,
        _routineEntries,
        _workoutSets,
        _isLoading,
        _isSaving,
        _isWorkoutSaving,
        _showSnackbar,
        _snackbarMessage,
        _datesWithCheckedMeals,
        _isEditingEnabled,
        _diaryMood,
        _diaryJournalText,
        _diaryCollectionsWithImages,
        _isDiarySaving
    ) { values: Array<Any?> ->
        val date = values[0] as String
        @Suppress("UNCHECKED_CAST")
        val entriesList = (values[1] as? List<DietEntry>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val routineEntriesList = (values[2] as? List<RoutineEntry>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val workoutSetsList = (values[3] as? List<WorkoutSet>) ?: emptyList()
        val loading = values[4] as? Boolean ?: false
        val saving = values[5] as? Boolean ?: false
        val workoutSaving = values[6] as? Boolean ?: false
        val snackbar = values[7] as? Boolean ?: false
        val message = values[8] as? String ?: ""
        @Suppress("UNCHECKED_CAST")
        val checkedDates = (values[9] as? Set<String>) ?: emptySet()
        val editingEnabled = values[10] as? Boolean ?: true
        val diaryMood = values[11] as? Int?
        val diaryJournalText = values[12] as? String ?: ""
        @Suppress("UNCHECKED_CAST")
        val diaryCollectionsWithImages = (values[13] as? List<Pair<DiaryCollection, List<DiaryCollectionImage>>>) ?: emptyList()
        val isDiarySaving = values[14] as? Boolean ?: false
        CalendarUiState(
            entries = entriesList,
            routineEntries = routineEntriesList,
            workoutSets = workoutSetsList,
            isLoading = loading,
            isSaving = saving,
            isWorkoutSaving = workoutSaving,
            showSnackbar = snackbar,
            snackbarMessage = message,
            currentDate = date,
            datesWithCheckedMeals = checkedDates,
            isEditingEnabled = editingEnabled,
            diaryMood = diaryMood,
            diaryJournalText = diaryJournalText,
            diaryCollectionsWithImages = diaryCollectionsWithImages,
            isDiarySaving = isDiarySaving
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState()
    )

    init {
        loadEntriesForDate(_currentDate.value)
        loadRoutineEntriesForDate(_currentDate.value)
        loadWorkoutForDate(_currentDate.value)
        loadDatesWithCheckedMeals()
        loadDiaryForDate(_currentDate.value)
        updateEditingState()
    }

    fun refresh() {
        loadEntriesForDate(_currentDate.value)
        loadRoutineEntriesForDate(_currentDate.value)
        loadWorkoutForDate(_currentDate.value)
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
        loadWorkoutForDate(date)
        loadDiaryForDate(date)
        updateEditingState()
    }

    private fun loadDiaryForDate(date: String) {
        viewModelScope.launch {
            try {
                val entry = diaryRepository.getDiaryEntryByDateSync(date)
                _diaryMood.value = entry?.mood
                _diaryJournalText.value = entry?.journalText.orEmpty()
                _diaryCollectionsWithImages.value = diaryRepository.getCollectionsWithImagesSync(date)
            } catch (e: Exception) {
                _diaryCollectionsWithImages.value = emptyList()
            }
        }
    }

    fun setDiaryMood(mood: Int?) {
        _diaryMood.value = mood
    }

    fun setDiaryJournalText(text: String) {
        _diaryJournalText.value = text
    }

    fun saveDiary() {
        viewModelScope.launch {
            _isDiarySaving.value = true
            try {
                val entry = com.vm.core.models.DiaryEntry(
                    date = _currentDate.value,
                    mood = _diaryMood.value?.coerceIn(1, 5),
                    journalText = _diaryJournalText.value.ifBlank { null }
                )
                diaryRepository.saveDiaryEntry(entry)
                _isDiarySaving.value = false
                showSuccess("Diary saved")
            } catch (e: Exception) {
                _isDiarySaving.value = false
                showError("Failed to save diary: ${e.message}")
            }
        }
    }

    suspend fun createCollection(name: String): Result<DiaryCollection> {
        val result = diaryRepository.createCollection(_currentDate.value, name)
        if (result.isSuccess) {
            loadDiaryForDate(_currentDate.value)
        }
        return result
    }

    suspend fun addImageToCollection(
        collectionId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        takenAtMillis: Long = System.currentTimeMillis()
    ): Result<DiaryCollectionImage> {
        val result = diaryRepository.addImageToCollection(collectionId, fileName, mimeType, bytes, takenAtMillis)
        if (result.isSuccess) {
            loadDiaryForDate(_currentDate.value)
        }
        return result
    }

    fun deleteDiaryImage(image: DiaryCollectionImage) {
        viewModelScope.launch {
            diaryRepository.deleteImage(image)
            loadDiaryForDate(_currentDate.value)
            showSuccess("Image removed")
        }
    }

    fun deleteDiaryCollection(collection: DiaryCollection) {
        viewModelScope.launch {
            val result = diaryRepository.deleteCollection(collection)
            if (result.isSuccess) {
                loadDiaryForDate(_currentDate.value)
                showSuccess("Collection deleted")
            } else {
                showError("Failed to delete: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    suspend fun getImageBytes(driveFileId: String): ByteArray? = diaryRepository.getImageBytes(driveFileId)

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

    private fun loadWorkoutForDate(date: String) {
        workoutCollectionJob?.cancel()
        workoutCollectionJob = viewModelScope.launch {
            try {
                val result = workoutRepository.loadBlueprintForDate(date)
                if (result.isFailure) {
                    val msg = result.exceptionOrNull()?.message ?: "Failed to load workout plan"
                    if (!msg.contains("workout_weekly.json", ignoreCase = true)) {
                        showError(msg)
                    }
                }
                workoutRepository.getSetsByDate(date).cancellable().collect { sets ->
                    _workoutSets.value = sets
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // expected when switching dates
            } catch (e: Exception) {
                showError("Failed to load workout: ${e.message}")
            }
        }
    }

    /** Update a single set's actuals in memory; persisted when user taps Save Workout. */
    fun updateWorkoutSetInMemory(
        setId: String,
        actualLoad: Double? = null,
        actualReps: Int? = null,
        actualVelocity: Double? = null,
        actualRpe: Double? = null,
        actualRir: Int? = null,
        actualDuration: Int? = null,
        actualDistance: Double? = null,
        actualRest: Int? = null
    ) {
        _workoutSets.value = _workoutSets.value.map { entry ->
            if (entry.id != setId) entry
            else entry.copy(
                actualLoad = actualLoad ?: entry.actualLoad,
                actualReps = actualReps ?: entry.actualReps,
                actualVelocity = actualVelocity ?: entry.actualVelocity,
                actualRpe = actualRpe ?: entry.actualRpe,
                actualRir = actualRir ?: entry.actualRir,
                actualDuration = actualDuration ?: entry.actualDuration,
                actualDistance = actualDistance ?: entry.actualDistance,
                actualRest = actualRest ?: entry.actualRest
            )
        }
    }

    /** Update workout status (Done/Partial/Exceed) for all sets in an exercise. */
    fun updateWorkoutExerciseStatus(exerciseId: String, status: String?) {
        _workoutSets.value = _workoutSets.value.map { entry ->
            if (entry.exerciseId != exerciseId) entry
            else entry.copy(workoutStatus = status)
        }
    }

    fun saveWorkout() {
        viewModelScope.launch {
            _isWorkoutSaving.value = true
            val result = workoutRepository.saveWorkoutForDate(_currentDate.value, _workoutSets.value)
            _isWorkoutSaving.value = false
            if (result.isSuccess) {
                showSuccess("Workout saved")
            } else {
                showError("Failed to save workout: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
            }
        }
    }

    /** Validate pasted workout JSON format (for dialog). */
    fun validateWorkoutJson(jsonString: String): Result<Unit> =
        workoutRepository.parseAndConvertToWorkoutSets(_currentDate.value, jsonString).map { }

    /** Add workout sets from pasted JSON (clipboard). Appends to current day; persist with Save Workout. */
    fun addWorkoutFromClipboard(jsonString: String): Result<Unit> {
        val result = workoutRepository.parseAndConvertToWorkoutSets(_currentDate.value, jsonString)
        return result.fold(
            onSuccess = { newSets ->
                _workoutSets.value = _workoutSets.value + newSets
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
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
