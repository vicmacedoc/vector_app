package com.vm.vector.ui.viewmodel

import android.app.Application
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
import com.vm.vector.data.DatabaseBackupManager
import com.vm.vector.data.DietRepository
import com.vm.vector.data.DiaryRepository
import com.vm.vector.data.RoutineRepository
import com.vm.vector.data.WorkoutRepository
import com.vm.vector.VectorApplication
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
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
    val diaryAudioPath: String? = null,           // local path for current recording
    val diaryAudioDriveFileId: String? = null,   // Drive file ID for saved audio
    /** All albums from Drive (subfolders of `collections/`), each with its images. */
    val diaryCollectionsWithImages: List<Pair<DiaryCollection, List<DiaryCollectionImage>>> = emptyList(),
    val isDiarySaving: Boolean = false,
    val isDiaryRefreshing: Boolean = false,
    val isCreatingCollection: Boolean = false,
    val noPresetForDiet: Boolean = false,
    val noPresetForRoutine: Boolean = false,
    val noPresetForWorkout: Boolean = false,
)

class CalendarViewModel(
    private val application: Application,
    private val dietRepository: DietRepository,
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val diaryRepository: DiaryRepository,
    private val backupManager: DatabaseBackupManager,
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
    private val _diaryAudioPath = MutableStateFlow<String?>(null)
    private val _diaryAudioDriveFileId = MutableStateFlow<String?>(null)
    private val _diaryCollectionsWithImages = MutableStateFlow<List<Pair<DiaryCollection, List<DiaryCollectionImage>>>>(emptyList())
    private val _isDiarySaving = MutableStateFlow(false)
    private val _isDiaryRefreshing = MutableStateFlow(false)
    private val _isCreatingCollection = MutableStateFlow(false)
    private val _noPresetForDiet = MutableStateFlow(false)
    private val _noPresetForRoutine = MutableStateFlow(false)
    private val _noPresetForWorkout = MutableStateFlow(false)
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
        _diaryAudioPath,
        _diaryAudioDriveFileId,
        _diaryCollectionsWithImages,
        _isDiarySaving,
        _isDiaryRefreshing,
        _isCreatingCollection,
        _noPresetForDiet,
        _noPresetForRoutine,
        _noPresetForWorkout
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
        val diaryAudioPath = values[13] as? String?
        val diaryAudioDriveFileId = values[14] as? String?
        @Suppress("UNCHECKED_CAST")
        val diaryCollectionsWithImages = (values[15] as? List<Pair<DiaryCollection, List<DiaryCollectionImage>>>) ?: emptyList()
        val isDiarySaving = values[16] as? Boolean ?: false
        val isDiaryRefreshing = values[17] as? Boolean ?: false
        val isCreatingCollection = values[18] as? Boolean ?: false
        val noPresetForDiet = values[19] as? Boolean ?: false
        val noPresetForRoutine = values[20] as? Boolean ?: false
        val noPresetForWorkout = values[21] as? Boolean ?: false
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
            diaryAudioPath = diaryAudioPath,
            diaryAudioDriveFileId = diaryAudioDriveFileId,
            diaryCollectionsWithImages = diaryCollectionsWithImages,
            isDiarySaving = isDiarySaving,
            isDiaryRefreshing = isDiaryRefreshing,
            isCreatingCollection = isCreatingCollection,
            noPresetForDiet = noPresetForDiet,
            noPresetForRoutine = noPresetForRoutine,
            noPresetForWorkout = noPresetForWorkout
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
        (application as? VectorApplication)?.let { app ->
            viewModelScope.launch {
                app.wearDataReceived.collect { (date, wearSets) ->
                    if (date == _currentDate.value) {
                        val byId = _workoutSets.value.associateBy { it.id }.toMutableMap()
                        wearSets.forEach { wearSet ->
                            val withStatus = if (wearSet.hasAnyActual()) wearSet.copy(workoutStatus = "Done") else wearSet
                            byId[withStatus.id] = withStatus
                        }
                        _workoutSets.value = byId.values.toList()
                        app.clearPendingWearData(date)
                        syncWorkoutSetsToWearCache()
                    }
                }
            }
            viewModelScope.launch {
                app.wearRoutineReceived.collect { (date, entryId, value) ->
                    if (date == _currentDate.value) {
                        _routineEntries.value = _routineEntries.value.map { e ->
                            if (e.id == entryId) {
                                routineRepository.computeEntryWithNewValue(e, value, fromWear = true)
                            } else e
                        }
                        app.removePendingRoutineWearEntry(date, entryId)
                    }
                }
            }
            viewModelScope.launch {
                app.databaseResetEvents.collect {
                    val d = _currentDate.value
                    loadEntriesForDate(d)
                    loadRoutineEntriesForDate(d)
                    loadWorkoutForDate(d)
                    loadDatesWithCheckedMeals()
                    loadDiaryForDate(d)
                }
            }
        }
    }

    private fun mergeRoutineEntriesWithPendingWear(
        date: String,
        entries: List<RoutineEntry>
    ): List<RoutineEntry> {
        val app = application as? VectorApplication ?: return entries
        val pending = app.getAndClearPendingRoutineWearData(date)
        if (pending.isEmpty()) return entries
        return entries.map { e ->
            pending[e.id]?.let { routineRepository.computeEntryWithNewValue(e, it, fromWear = true) } ?: e
        }
    }

    private fun syncWorkoutSetsToWearCache() {
        (application as? VectorApplication)?.setWorkoutSetsForWearRequest(_currentDate.value, _workoutSets.value)
    }

    fun refresh() {
        loadEntriesForDate(_currentDate.value)
        loadRoutineEntriesForDate(_currentDate.value)
        loadWorkoutForDate(_currentDate.value)
        loadDatesWithCheckedMeals()
    }

    /**
     * Fetch newest preset from Drive and replace current in-memory content for this category.
     * Does not persist; user taps Save Changes to write to DB.
     */
    fun refreshFromDriveForDiet() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = dietRepository.loadPresetEntriesForDate(_currentDate.value)
                if (result.isSuccess) {
                    _entries.value = result.getOrNull() ?: emptyList()
                } else {
                    showError(result.exceptionOrNull()?.message ?: "Failed to fetch diet preset")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshFromDriveForRoutine() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = routineRepository.loadPresetEntriesForDate(_currentDate.value)
                if (result.isSuccess) {
                    _routineEntries.value = result.getOrNull() ?: emptyList()
                } else {
                    showError(result.exceptionOrNull()?.message ?: "Failed to fetch routine preset")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshFromDriveForWorkout() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = workoutRepository.loadPresetOnlyForDate(_currentDate.value)
                if (result.isSuccess) {
                    _workoutSets.value = result.getOrNull() ?: emptyList()
                    syncWorkoutSetsToWearCache()
                } else {
                    showError(result.exceptionOrNull()?.message ?: "Failed to fetch workout preset")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateEditingState() {
        viewModelScope.launch {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = dateFormat.format(Date())
            val currentDateStr = _currentDate.value
            // Past dates and future dates: editing locked by default. Only today is editable by default.
            _isEditingEnabled.value = (currentDateStr == today)
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
                _diaryAudioPath.value = null
                _diaryAudioDriveFileId.value = entry?.journalAudioDriveFileId
                _diaryCollectionsWithImages.value = diaryRepository.getAllCollectionsWithImagesSync()
            } catch (e: Exception) {
                _diaryCollectionsWithImages.value = emptyList()
            }
        }
    }

    fun refreshDiaryFromDrive() {
        viewModelScope.launch {
            _isDiaryRefreshing.value = true
            try {
                val result = diaryRepository.syncDiaryPhotosFromDrive()
                result.fold(
                    onSuccess = { r ->
                        loadDiaryForDate(_currentDate.value)
                        val changed = r.importedCount > 0 || r.removedImagesCount > 0 || r.removedCollectionsCount > 0
                        val msg = if (!changed) {
                            "Diary photos up to date"
                        } else {
                            buildList {
                                if (r.importedCount > 0) add("${r.importedCount} added")
                                if (r.removedImagesCount > 0) add("${r.removedImagesCount} removed")
                                if (r.removedCollectionsCount > 0) add("${r.removedCollectionsCount} album(s) removed")
                            }.joinToString(", ")
                        }
                        showSuccess(msg)
                    },
                    onFailure = { e ->
                        showError(e.message ?: "Failed to refresh diary photos")
                    }
                )
            } finally {
                _isDiaryRefreshing.value = false
            }
        }
    }

    fun setDiaryMood(mood: Int?) {
        _diaryMood.value = mood
    }

    fun setDiaryJournalText(text: String) {
        _diaryJournalText.value = text
    }

    fun setDiaryAudioPath(path: String?) {
        _diaryAudioPath.value = path
    }

    fun setDiaryAudioDriveFileId(id: String?) {
        _diaryAudioDriveFileId.value = id
    }

    suspend fun getDiaryAudioBytes(): ByteArray? {
        val id = _diaryAudioDriveFileId.value ?: return null
        return diaryRepository.getDiaryAudioBytes(id)
    }

    fun saveDiary() {
        viewModelScope.launch {
            _isDiarySaving.value = true
            try {
                var journalAudioDriveFileId: String? = _diaryAudioDriveFileId.value
                if (_diaryAudioPath.value != null) {
                    val uploadResult = diaryRepository.uploadDiaryAudioFromPath(_currentDate.value, _diaryAudioPath.value!!)
                    journalAudioDriveFileId = uploadResult.getOrNull()
                    if (journalAudioDriveFileId != null) {
                        _diaryAudioDriveFileId.value = journalAudioDriveFileId
                        _diaryAudioPath.value = null
                    } else {
                        _isDiarySaving.value = false
                        showError("Failed to upload diary audio: ${uploadResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                }
                val entry = com.vm.core.models.DiaryEntry(
                    date = _currentDate.value,
                    mood = _diaryMood.value?.coerceIn(1, 5),
                    journalText = _diaryJournalText.value.ifBlank { null },
                    journalAudioDriveFileId = journalAudioDriveFileId
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
        if (_isCreatingCollection.value) return Result.failure(Exception("Creation already in progress"))
        _isCreatingCollection.value = true
        return try {
            val result = diaryRepository.createCollection(_currentDate.value, name)
            if (result.isSuccess) {
                loadDiaryForDate(_currentDate.value)
                showSuccess("Collection created")
            } else {
                showError(result.exceptionOrNull()?.message ?: "Failed to create collection")
            }
            result
        } finally {
            _isCreatingCollection.value = false
        }
    }

    suspend fun addImageToCollection(
        collectionId: String,
        mimeType: String,
        bytes: ByteArray,
        takenAtMillis: Long = System.currentTimeMillis()
    ): Result<DiaryCollectionImage> {
        val result = diaryRepository.addImageToCollection(collectionId, mimeType, bytes, takenAtMillis)
        if (result.isSuccess) {
            loadDiaryForDate(_currentDate.value)
            showSuccess("Image uploaded")
        } else {
            showError(result.exceptionOrNull()?.message ?: "Failed to upload image")
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
                showSuccess("Album removed from app (files stay on Drive until you delete them there)")
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
                // Load only from DB (saved state). Preset loaded into memory only; persist when user taps Save.
                val existingEntries = dietRepository.getEntriesByDateSync(date)
                if (existingEntries.isNotEmpty()) {
                    _noPresetForDiet.value = false
                    _entries.value = existingEntries
                    _isLoading.value = false
                    loadDatesWithCheckedMeals()
                    return@launch
                }
                val presetResult = dietRepository.loadPresetEntriesForDate(date)
                when {
                    presetResult.isSuccess -> {
                        _noPresetForDiet.value = false
                        val presetEntries = presetResult.getOrNull() ?: emptyList()
                        if (presetEntries.isNotEmpty()) {
                            _entries.value = presetEntries
                        } else {
                            _entries.value = emptyList()
                            showError("No diet plan found for this day in the preset.")
                        }
                    }
                    else -> {
                        val error = presetResult.exceptionOrNull()
                        val errorMsg = error?.message ?: "Unknown error"
                        when {
                            errorMsg.contains("No diet preset active", ignoreCase = true) -> {
                                _noPresetForDiet.value = true
                                _entries.value = emptyList()
                            }
                            errorMsg.contains("403") || errorMsg.contains("Forbidden") -> {
                                _noPresetForDiet.value = false
                                showError("Drive access denied. Please grant DRIVE scope permission in Settings.")
                                _entries.value = emptyList()
                            }
                            errorMsg.contains("NeedsRemoteConsent") || errorMsg.contains("consent") -> {
                                _noPresetForDiet.value = false
                                showError("Drive consent required. Please authorize access in Settings.")
                                _entries.value = emptyList()
                            }
                            errorMsg.contains("Drive folder ID not configured") -> {
                                _noPresetForDiet.value = false
                                showError("Drive not configured. Please set your Drive folder ID in Settings.")
                                _entries.value = emptyList()
                            }
                            else -> {
                                _noPresetForDiet.value = false
                                showError("Failed to load diet plan: $errorMsg")
                                _entries.value = emptyList()
                            }
                        }
                    }
                }
                _isLoading.value = false
            } catch (e: kotlinx.coroutines.CancellationException) {
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
                // Load only from DB (saved state). Preset loaded into memory only; persist when user taps Save.
                val existingEntries = routineRepository.getEntriesByDateSync(date)
                if (existingEntries.isNotEmpty()) {
                    _noPresetForRoutine.value = false
                    _routineEntries.value = mergeRoutineEntriesWithPendingWear(date, existingEntries)
                    return@launch
                }
                val presetResult = routineRepository.loadPresetEntriesForDate(date)
                when {
                    presetResult.isSuccess -> {
                        _noPresetForRoutine.value = false
                        val presetEntries = presetResult.getOrNull() ?: emptyList()
                        if (presetEntries.isNotEmpty()) {
                            _routineEntries.value = mergeRoutineEntriesWithPendingWear(date, presetEntries)
                        } else {
                            _routineEntries.value = emptyList()
                            showError("No routine plan found for this day in the preset.")
                        }
                    }
                    else -> {
                        val error = presetResult.exceptionOrNull()
                        val errorMsg = error?.message ?: "Unknown error"
                        when {
                            errorMsg.contains("No routine preset active", ignoreCase = true) -> {
                                _noPresetForRoutine.value = true
                                _routineEntries.value = emptyList()
                            }
                            errorMsg.contains("403") || errorMsg.contains("Forbidden") -> {
                                _noPresetForRoutine.value = false
                                showError("Drive access denied. Please grant DRIVE scope permission in Settings.")
                                _routineEntries.value = emptyList()
                            }
                            errorMsg.contains("NeedsRemoteConsent") || errorMsg.contains("consent") -> {
                                _noPresetForRoutine.value = false
                                showError("Drive consent required. Please authorize access in Settings.")
                                _routineEntries.value = emptyList()
                            }
                            errorMsg.contains("Drive folder ID not configured") -> {
                                _noPresetForRoutine.value = false
                                showError("Drive not configured. Please set your Drive folder ID in Settings.")
                                _routineEntries.value = emptyList()
                            }
                            else -> {
                                _noPresetForRoutine.value = false
                                showError("Failed to load routine plan: $errorMsg")
                                _routineEntries.value = emptyList()
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // expected when switching dates
            } catch (e: Exception) {
                showError("Failed to load routine entries: ${e.message}")
            }
        }
    }

    private fun loadWorkoutForDate(date: String) {
        workoutCollectionJob?.cancel()
        workoutCollectionJob = viewModelScope.launch {
            try {
                // Load for display only: saved sets from DB, or blueprint in memory (persist on Save Workout).
                val result = workoutRepository.loadWorkoutForDateDisplay(date)
                if (result.isSuccess) {
                    _noPresetForWorkout.value = false
                    var sets = result.getOrNull() ?: emptyList()
                    val pending = (application as? com.vm.vector.VectorApplication)?.getAndClearPendingWearData(date)
                    if (!pending.isNullOrEmpty()) {
                        val byId = sets.associateBy { it.id }.toMutableMap()
                        pending.forEach { wearSet ->
                            val withStatus = if (wearSet.hasAnyActual()) wearSet.copy(workoutStatus = "Done") else wearSet
                            byId[withStatus.id] = withStatus
                        }
                        sets = byId.values.toList()
                    }
                    _workoutSets.value = sets
                    syncWorkoutSetsToWearCache()
                } else {
                    val msg = result.exceptionOrNull()?.message ?: "Failed to load workout plan"
                    if (msg.contains("No workout preset active", ignoreCase = true)) {
                        _noPresetForWorkout.value = true
                    } else {
                        _noPresetForWorkout.value = false
                        showError(msg)
                    }
                    _workoutSets.value = emptyList()
                    syncWorkoutSetsToWearCache()
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
        syncWorkoutSetsToWearCache()
    }

    /** Update workout status (Done/Partial/Exceed) for all sets in an exercise. */
    fun updateWorkoutExerciseStatus(exerciseId: String, status: String?) {
        _workoutSets.value = _workoutSets.value.map { entry ->
            if (entry.exerciseId != exerciseId) entry
            else entry.copy(workoutStatus = status)
        }
        syncWorkoutSetsToWearCache()
    }

    fun saveWorkout() {
        viewModelScope.launch {
            _isWorkoutSaving.value = true
            val result = workoutRepository.saveWorkoutForDate(_currentDate.value, _workoutSets.value)
            _isWorkoutSaving.value = false
            if (result.isSuccess) {
                showSuccess("Exercise saved")
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
                syncWorkoutSetsToWearCache()
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
        _entries.value = _entries.value.map { if (it.id == entryId) updated else it }
    }

    /** Cycles eaten state: blank → done (blue) → not eaten (red) → blank. */
    fun cycleEntryEatenState(entryId: String) {
        if (!_isEditingEnabled.value) return
        val entry = _entries.value.find { it.id == entryId } ?: return
        val updated = when {
            entry.notEaten -> entry.copy(notEaten = false)
            !entry.isChecked -> entry.copy(isChecked = true)
            else -> entry.copy(isChecked = false, notEaten = true)
        }
        _entries.value = _entries.value.map { if (it.id == entryId) updated else it }
    }

    fun updateRoutineValue(entryId: String, newValue: Double?) {
        if (!_isEditingEnabled.value) return
        val entry = _routineEntries.value.find { it.id == entryId } ?: return
        val updated = routineRepository.computeEntryWithNewValue(entry, newValue)
        _routineEntries.value = _routineEntries.value.map { if (it.id == entryId) updated else it }
    }

    fun updateRoutineStatus(entryId: String, newStatus: RoutineStatus) {
        if (!_isEditingEnabled.value) return
        val entry = _routineEntries.value.find { it.id == entryId } ?: return
        val isGoalMet = newStatus == RoutineStatus.DONE || newStatus == RoutineStatus.EXCEEDED
        val updated = entry.copy(status = newStatus, isGoalMet = isGoalMet)
        _routineEntries.value = _routineEntries.value.map { if (it.id == entryId) updated else it }
    }

    fun toggleNumericalStatus(entryId: String) {
        if (!_isEditingEnabled.value) return
        val entry = _routineEntries.value.find { it.id == entryId } ?: return
        val updated = routineRepository.computeToggleNumericalStatus(entry)
        _routineEntries.value = _routineEntries.value.map { if (it.id == entryId) updated else it }
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
        _routineEntries.value = _routineEntries.value + newEntry
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
            notEaten = false,
            status = AuditStatus.UNPLANNED,
            timestamp = System.currentTimeMillis()
        )
        _entries.value = _entries.value + newEntry
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
        val entry = _entries.value.find { it.id == entryId } ?: return
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
        _entries.value = _entries.value.map { if (it.id == entryId) updated else it }
    }
    
    fun deleteEntry(entryId: String) {
        if (!_isEditingEnabled.value) return
        _entries.value = _entries.value.filter { it.id != entryId }
    }
    
    fun editUnplannedEntry(entry: DietEntry) {
        _editingEntry.value = entry
    }
    
    val editingEntry: StateFlow<DietEntry?> = _editingEntry.asStateFlow()
    
    fun getEditingEntry(): DietEntry? = _editingEntry.value
    
    fun clearEditingEntry() {
        _editingEntry.value = null
    }

    /**
     * Saves only the Routine data for the current date.
     * Called when the user taps Save Changes on the Routine tab. We do not persist Diet here
     * because _entries may be stale (e.g. from another date or not yet loaded), which would
     * overwrite Diet in the DB and break Daily Inputs for Nutrition.
     */
    fun saveRoutineDay() {
        viewModelScope.launch {
            _isSaving.value = true
            val date = _currentDate.value
            val result = runCatching {
                routineRepository.replaceEntriesForDate(date, _routineEntries.value)
            }
            _isSaving.value = false
            loadDatesWithCheckedMeals()
            if (result.isSuccess) {
                showSuccess("Routine saved")
                viewModelScope.launch {
                    backupManager.backup().onFailure { e ->
                        showError("Routine saved; backup to Drive failed: ${e.message ?: "Unknown error"}")
                    }
                }
            } else {
                showError("Failed to save routine: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
            }
        }
    }

    /**
     * Saves only the Diet/Nutrition data for the current date.
     * Called when the user taps Save Changes on the Diet tab. We do not persist Routine here
     * because _routineEntries may be stale, which would overwrite Routine in the DB and break
     * Daily Inputs for Routine.
     */
    fun saveDietDay() {
        viewModelScope.launch {
            _isSaving.value = true
            val date = _currentDate.value
            dietRepository.replaceEntriesForDate(date, _entries.value)
            val result = dietRepository.saveDay(date)
            _isSaving.value = false
            loadDatesWithCheckedMeals()
            if (result.isSuccess) {
                showSuccess("Nutrition saved")
                viewModelScope.launch {
                    backupManager.backup().onFailure { e ->
                        showError("Nutrition saved; backup to Drive failed: ${e.message ?: "Unknown error"}")
                    }
                }
            } else {
                showError("Failed to save diet: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
            }
        }
    }


    fun dismissSnackbar() {
        _showSnackbar.value = false
    }

    fun notifyDiaryError(message: String) {
        showError(message)
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

private fun WorkoutSet.hasAnyActual(): Boolean =
    actualLoad != null || actualReps != null || actualRpe != null || actualRir != null ||
        actualDuration != null || actualDistance != null || actualRest != null
