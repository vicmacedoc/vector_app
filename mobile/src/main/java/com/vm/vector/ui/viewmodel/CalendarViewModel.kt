package com.vm.vector.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vm.core.models.ActivityCompletion
import com.vm.core.models.ActivityEntry
import com.vm.core.models.ActivityLabel
import com.vm.core.models.ActivityType
import com.vm.core.models.AuditStatus
import com.vm.core.models.FoodEntry
import com.vm.core.models.HabitDefinition
import com.vm.core.models.HabitLog
import com.vm.core.models.HabitStatus
import com.vm.core.models.SelfCollection
import com.vm.core.models.SelfCollectionImage
import com.vm.core.models.SelfEntry
import com.vm.core.models.SelfScale
import com.vm.core.models.WorkoutDaySession
import com.vm.core.models.WorkoutSet
import com.vm.vector.data.LibraryRepository
import com.vm.core.models.ExerciseLibraryItem
import com.vm.core.models.MealLibraryItem
import com.vm.core.models.ActivityLibraryItem
import com.vm.core.models.effectiveEndTime
import com.vm.core.models.effectiveStartTime
import java.util.UUID
import com.vm.core.models.SleepEntry
import com.vm.core.models.CalendarDraft
import com.vm.vector.data.ActivityRepository
import com.vm.vector.data.CalendarAiMode
import com.vm.vector.data.CalendarDayContext
import com.vm.vector.data.CalendarDraftRepository
import com.vm.vector.data.MealImportDraft
import com.vm.vector.data.toFoodEntry
import com.vm.vector.data.ExerciseDraftPayload
import com.vm.vector.data.FoodDraftPayload
import com.vm.vector.data.RoutineDraftPayload
import com.vm.vector.data.SelfDraftPayload
import com.vm.vector.data.SleepEdits
import com.vm.vector.data.isSameAs
import com.vm.vector.data.DatabaseBackupScheduler
import com.vm.vector.data.DailySync
import com.vm.vector.data.ExerciseAiContext
import com.vm.vector.data.FoodRepository
import com.vm.vector.data.GeminiSleepClient
import com.vm.vector.data.HabitRepository
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.SelfRepository
import com.vm.vector.data.SleepRepository
import com.vm.vector.data.SleepStage
import com.vm.vector.data.WorkoutRepository
import com.vm.vector.data.mergeWearOverlay
import com.vm.vector.VectorApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Everything typed or tapped in the Self tab for one day, except the audio note.
 * Text fields stay non-null for the Compose fields and collapse to null on save.
 */
data class SelfInputs(
    /** Level 1–5 per [SelfScale]; absent means unrated. */
    val scales: Map<SelfScale, Int> = emptyMap(),
    val intention: String = "",
    val highlight: String = "",
    val lowlight: String = "",
    val gratitude: String = "",
    val journalText: String = "",
) {
    fun level(scale: SelfScale): Int? = scales[scale]
}

/** What a category's in-memory state is compared against: saved data, or the preset when unsaved. */
private data class Baseline<T>(val date: String, val payload: T)

data class CalendarUiState(
    val entries: List<FoodEntry> = emptyList(),
    val workoutSets: List<WorkoutSet> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isWorkoutSaving: Boolean = false,
    val showSnackbar: Boolean = false,
    val snackbarMessage: String = "",
    val currentDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val datesWithCheckedMeals: Set<String> = emptySet(),
    val isEditingEnabled: Boolean = true,
    // Habits
    val habitDefinitions: List<HabitDefinition> = emptyList(),
    val selectedHabitIds: List<String> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
    // Activities
    val activityEntries: List<ActivityEntry> = emptyList(),
    val activityLabels: List<ActivityLabel> = emptyList(),
    /**
     * Night that ended this morning (row D−1) and the night starting tonight (row D).
     */
    val previousNight: SleepEntry? = null,
    val tonight: SleepEntry? = null,
    val defaultBedMinutes: Int = 22 * 60,
    val defaultWakeMinutes: Int = 6 * 60,
    // Self
    val selfInputs: SelfInputs = SelfInputs(),
    /** Dates with any self signal, for the week dots while the Self tab is open. */
    val datesWithSelfEntries: Set<String> = emptySet(),
    val selfAudioPath: String? = null,
    val selfAudioDriveFileId: String? = null,
    /** All albums from Drive (subfolders of `collections/`), each with its images. */
    val selfCollectionsWithImages: List<Pair<SelfCollection, List<SelfCollectionImage>>> = emptyList(),
    val isSelfSaving: Boolean = false,
    val isSelfRefreshing: Boolean = false,
    val isCreatingCollection: Boolean = false,
    val isRoutineSaving: Boolean = false,
    val noPresetForFood: Boolean = false,
    val noPresetForWorkout: Boolean = false,
    val noPresetForRoutine: Boolean = false,
    /** [CalendarDraft] category names whose current day has unsaved changes. */
    val dirtyCategories: Set<String> = emptySet(),
    val exerciseLibrary: List<com.vm.core.models.ExerciseLibraryItem> = emptyList(),
    val mealLibrary: List<com.vm.core.models.MealLibraryItem> = emptyList(),
    val activityLibrary: List<ActivityLibraryItem> = emptyList(),
)

data class LibraryLists(
    val exercises: List<ExerciseLibraryItem> = emptyList(),
    val meals: List<MealLibraryItem> = emptyList(),
    val activities: List<ActivityLibraryItem> = emptyList(),
)

class CalendarViewModel(
    private val application: Application,
    private val foodRepository: FoodRepository,
    private val habitRepository: HabitRepository,
    private val activityRepository: ActivityRepository,
    private val workoutRepository: WorkoutRepository,
    private val selfRepository: SelfRepository,
    private val sleepRepository: SleepRepository,
    private val preferenceManager: PreferenceManager,
    private val libraryRepository: LibraryRepository,
    private val draftRepository: CalendarDraftRepository,
) : ViewModel() {

    private val _currentDate = MutableStateFlow(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    private val _isLoading = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    private val _showSnackbar = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow("")
    private val _entries = MutableStateFlow<List<FoodEntry>>(emptyList())
    private val _workoutSets = MutableStateFlow<List<WorkoutSet>>(emptyList())
    private val _datesWithCheckedMeals = MutableStateFlow<Set<String>>(emptySet())
    private val _isEditingEnabled = MutableStateFlow(true)
    private val _isWorkoutSaving = MutableStateFlow(false)
    private val _editingEntry = MutableStateFlow<FoodEntry?>(null)
    private val _habitDefinitions = MutableStateFlow<List<HabitDefinition>>(emptyList())
    private val _selectedHabitIds = MutableStateFlow<List<String>>(emptyList())
    private val _habitLogs = MutableStateFlow<List<HabitLog>>(emptyList())
    private val _activityEntries = MutableStateFlow<List<ActivityEntry>>(emptyList())
    private val _activityLabels = MutableStateFlow<List<ActivityLabel>>(emptyList())
    private val _previousNight = MutableStateFlow<SleepEntry?>(null)
    private val _tonight = MutableStateFlow<SleepEntry?>(null)
    private val _defaultBedMinutes = MutableStateFlow(22 * 60)
    private val _defaultWakeMinutes = MutableStateFlow(6 * 60)
    private val _selfInputs = MutableStateFlow(SelfInputs())
    private val _datesWithSelfEntries = MutableStateFlow<Set<String>>(emptySet())
    private val _selfAudioPath = MutableStateFlow<String?>(null)
    private val _selfAudioDriveFileId = MutableStateFlow<String?>(null)
    private val _selfCollectionsWithImages =
        MutableStateFlow<List<Pair<SelfCollection, List<SelfCollectionImage>>>>(emptyList())
    private val _isSelfSaving = MutableStateFlow(false)
    private val _isSelfRefreshing = MutableStateFlow(false)
    private val _isCreatingCollection = MutableStateFlow(false)
    private val _isRoutineSaving = MutableStateFlow(false)
    private val _noPresetForFood = MutableStateFlow(false)
    private val _noPresetForWorkout = MutableStateFlow(false)
    private val _noPresetForRoutine = MutableStateFlow(false)
    private val _libraryLists = MutableStateFlow(LibraryLists())
    private var collectionJob: Job? = null
    private var habitsActivitiesJob: Job? = null
    private var workoutCollectionJob: Job? = null
    private var selfJob: Job? = null

    // Dirty tracking: a null baseline means the category is loading and must not sync drafts.
    private val _routineBaseline = MutableStateFlow<Baseline<RoutineDraftPayload>?>(null)
    private val _foodBaseline = MutableStateFlow<Baseline<FoodDraftPayload>?>(null)
    private val _exerciseBaseline = MutableStateFlow<Baseline<ExerciseDraftPayload>?>(null)
    private val _selfBaseline = MutableStateFlow<Baseline<SelfDraftPayload>?>(null)
    private val _dirtyCategories = MutableStateFlow<Set<String>>(emptySet())

    /** Survives [onCleared] so a debounced draft write still lands after leaving the Calendar. */
    private val draftScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val draftJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val pendingDraftWrites = java.util.concurrent.ConcurrentHashMap<String, suspend () -> Unit>()

    val uiState: StateFlow<CalendarUiState> = combine(
        _currentDate,
        _entries,
        _workoutSets,
        _isLoading,
        _isSaving,
        _isWorkoutSaving,
        _showSnackbar,
        _snackbarMessage,
        _datesWithCheckedMeals,
        _isEditingEnabled,
        _habitDefinitions,
        _selectedHabitIds,
        _habitLogs,
        _activityEntries,
        _activityLabels,
        _previousNight,
        _tonight,
        _defaultBedMinutes,
        _defaultWakeMinutes,
        _selfInputs,
        _datesWithSelfEntries,
        _selfAudioPath,
        _selfAudioDriveFileId,
        _selfCollectionsWithImages,
        _isSelfSaving,
        _isSelfRefreshing,
        _isCreatingCollection,
        _isRoutineSaving,
        _noPresetForFood,
        _noPresetForWorkout,
        _libraryLists,
        _noPresetForRoutine,
        _dirtyCategories,
    ) { values: Array<Any?> ->
        val date = values[0] as String
        @Suppress("UNCHECKED_CAST")
        val entriesList = (values[1] as? List<FoodEntry>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val workoutSetsList = (values[2] as? List<WorkoutSet>) ?: emptyList()
        val loading = values[3] as? Boolean ?: false
        val saving = values[4] as? Boolean ?: false
        val workoutSaving = values[5] as? Boolean ?: false
        val snackbar = values[6] as? Boolean ?: false
        val message = values[7] as? String ?: ""
        @Suppress("UNCHECKED_CAST")
        val checkedDates = (values[8] as? Set<String>) ?: emptySet()
        val editingEnabled = values[9] as? Boolean ?: true
        @Suppress("UNCHECKED_CAST")
        val habitDefinitions = (values[10] as? List<HabitDefinition>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val selectedHabitIds = (values[11] as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val habitLogs = (values[12] as? List<HabitLog>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val activityEntries = (values[13] as? List<ActivityEntry>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val activityLabels = (values[14] as? List<ActivityLabel>) ?: emptyList()
        val previousNight = values[15] as? SleepEntry?
        val tonight = values[16] as? SleepEntry?
        val defaultBedMinutes = values[17] as? Int ?: (22 * 60)
        val defaultWakeMinutes = values[18] as? Int ?: (6 * 60)
        val selfInputs = values[19] as? SelfInputs ?: SelfInputs()
        @Suppress("UNCHECKED_CAST")
        val datesWithSelfEntries = (values[20] as? Set<String>) ?: emptySet()
        val selfAudioPath = values[21] as? String?
        val selfAudioDriveFileId = values[22] as? String?
        @Suppress("UNCHECKED_CAST")
        val selfCollectionsWithImages =
            (values[23] as? List<Pair<SelfCollection, List<SelfCollectionImage>>>) ?: emptyList()
        val isSelfSaving = values[24] as? Boolean ?: false
        val isSelfRefreshing = values[25] as? Boolean ?: false
        val isCreatingCollection = values[26] as? Boolean ?: false
        val isRoutineSaving = values[27] as? Boolean ?: false
        val noPresetForFood = values[28] as? Boolean ?: false
        val noPresetForWorkout = values[29] as? Boolean ?: false
        val libraryLists = values[30] as? LibraryLists ?: LibraryLists()
        val noPresetForRoutine = values[31] as? Boolean ?: false
        @Suppress("UNCHECKED_CAST")
        val dirtyCategories = (values[32] as? Set<String>) ?: emptySet()
        CalendarUiState(
            noPresetForRoutine = noPresetForRoutine,
            dirtyCategories = dirtyCategories,
            entries = entriesList,
            workoutSets = workoutSetsList,
            isLoading = loading,
            isSaving = saving,
            isWorkoutSaving = workoutSaving,
            showSnackbar = snackbar,
            snackbarMessage = message,
            currentDate = date,
            datesWithCheckedMeals = checkedDates,
            isEditingEnabled = editingEnabled,
            habitDefinitions = habitDefinitions,
            selectedHabitIds = selectedHabitIds,
            habitLogs = habitLogs,
            activityEntries = activityEntries,
            activityLabels = activityLabels,
            previousNight = previousNight,
            tonight = tonight,
            defaultBedMinutes = defaultBedMinutes,
            defaultWakeMinutes = defaultWakeMinutes,
            selfInputs = selfInputs,
            datesWithSelfEntries = datesWithSelfEntries,
            selfAudioPath = selfAudioPath,
            selfAudioDriveFileId = selfAudioDriveFileId,
            selfCollectionsWithImages = selfCollectionsWithImages,
            isSelfSaving = isSelfSaving,
            isSelfRefreshing = isSelfRefreshing,
            isCreatingCollection = isCreatingCollection,
            isRoutineSaving = isRoutineSaving,
            noPresetForFood = noPresetForFood,
            noPresetForWorkout = noPresetForWorkout,
            exerciseLibrary = libraryLists.exercises,
            mealLibrary = libraryLists.meals,
            activityLibrary = libraryLists.activities,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState()
    )

    init {
        viewModelScope.launch {
            combine(
                libraryRepository.observeExercises(),
                libraryRepository.observeMeals(),
                libraryRepository.observeActivities(),
            ) { exercises, meals, activities ->
                LibraryLists(exercises, meals, activities)
            }.collect { _libraryLists.value = it }
        }
        viewModelScope.launch { runCatching { libraryRepository.migrateLegacyRoutineBundles() } }
        startDraftSync()
        loadEntriesForDate(_currentDate.value)
        loadHabitsAndActivitiesForDate(_currentDate.value)
        loadWorkoutForDate(_currentDate.value)
        loadDatesWithCheckedMeals()
        loadSelfForDate(_currentDate.value)
        updateEditingState()
        viewModelScope.launch {
            DailySync.imported.collect {
                if (_dirtyCategories.value.isNotEmpty()) return@collect
                val date = _currentDate.value
                loadEntriesForDate(date)
                loadHabitsAndActivitiesForDate(date)
                loadWorkoutForDate(date)
                loadSelfForDate(date)
                loadDatesWithCheckedMeals()
            }
        }
        (application as? VectorApplication)?.let { app ->
            viewModelScope.launch {
                app.wearDataReceived.collect { (date, wearSets) ->
                    if (date == _currentDate.value) {
                        val byId = _workoutSets.value.associateBy { it.id }.toMutableMap()
                        wearSets.forEach { wear ->
                            val base = byId[wear.id] ?: wear
                            byId[wear.id] = base.mergeWearOverlay(wear)
                        }
                        _workoutSets.value = byId.values.toList()
                        // Do not clear pending wear here: persist runs async; clearing before Room is
                        // updated makes the next loadWorkoutForDate miss the overlay and drop actuals.
                        syncWorkoutSetsToWearCache()
                    }
                }
            }
            viewModelScope.launch {
                app.wearWorkoutPersistedForDate.collect { date ->
                    if (date != _currentDate.value) return@collect
                    if (CalendarDraft.CATEGORY_EXERCISE in _dirtyCategories.value) {
                        // Keep the user's unsaved edits (already overlaid with the watch data) and
                        // only move the baseline to what the watch just saved.
                        workoutRepository.loadWorkoutForDateDisplay(date).onSuccess { saved ->
                            if (date == _currentDate.value) {
                                _exerciseBaseline.value = Baseline(date, ExerciseDraftPayload(saved))
                            }
                        }
                    } else {
                        loadWorkoutForDate(date)
                    }
                }
            }
            viewModelScope.launch {
                app.wearFoodUpdated.collect { (date, entry) ->
                    if (date == _currentDate.value) {
                        val patch: (List<FoodEntry>) -> List<FoodEntry> = { list ->
                            list.map {
                                if (it.id == entry.id) {
                                    it.copy(isChecked = entry.isChecked, notEaten = entry.notEaten)
                                } else {
                                    it
                                }
                            }
                        }
                        // The watch persists this itself, so the baseline moves with it.
                        _foodBaseline.value?.takeIf { it.date == date }?.let { base ->
                            _foodBaseline.value = base.copy(payload = FoodDraftPayload(patch(base.payload.entries)))
                        }
                        _entries.value = patch(_entries.value)
                    }
                }
            }
            viewModelScope.launch {
                app.wearActivityUpdated.collect { update ->
                    if (update.date == _currentDate.value) {
                        val patch: (List<ActivityEntry>) -> List<ActivityEntry> = { list ->
                            list.map {
                                if (it.id == update.activityId) it.copy(completion = update.completion) else it
                            }
                        }
                        _routineBaseline.value?.takeIf { it.date == update.date }?.let { base ->
                            _routineBaseline.value = base.copy(
                                payload = base.payload.copy(activities = patch(base.payload.activities))
                            )
                        }
                        _activityEntries.value = patch(_activityEntries.value)
                    }
                }
            }
            viewModelScope.launch {
                app.databaseResetEvents.collect {
                    val d = _currentDate.value
                    loadEntriesForDate(d)
                    loadHabitsAndActivitiesForDate(d)
                    loadWorkoutForDate(d)
                    loadDatesWithCheckedMeals()
                    loadSelfForDate(d)
                }
            }
            viewModelScope.launch {
                app.calendarDayCleared.collect { (category, date) ->
                    val current = _currentDate.value
                    when {
                        date == current -> discardAndReload(category)
                        // Clearing Routine on D also drops sleep row D−1, shown as "tonight" on D−1.
                        category == CalendarDraft.CATEGORY_ROUTINE && previousCalendarDate(date) == current ->
                            loadHabitsAndActivitiesForDate(current)
                    }
                }
            }
        }
    }

    // --- Unsaved changes (drafts) ---

    /**
     * Mirrors each category's in-memory state into `calendar_drafts`: dirty flags update at once,
     * the DB write is debounced. A null baseline (category loading) pauses syncing.
     */
    private fun startDraftSync() {
        viewModelScope.launch {
            combine(_activityEntries, _previousNight, _tonight, _routineBaseline) { activities, prev, tonight, base ->
                base?.let { it to RoutineDraftPayload(activities, prev, tonight) }
            }.collect { pair ->
                val (base, current) = pair ?: return@collect
                syncDraft(CalendarDraft.CATEGORY_ROUTINE, base.date, !current.isSameAs(base.payload)) { dirty ->
                    if (dirty) draftRepository.saveRoutine(base.date, current)
                    else draftRepository.delete(CalendarDraft.CATEGORY_ROUTINE, base.date)
                }
            }
        }
        viewModelScope.launch {
            combine(_entries, _foodBaseline) { entries, base -> base?.let { it to FoodDraftPayload(entries) } }
                .collect { pair ->
                    val (base, current) = pair ?: return@collect
                    syncDraft(CalendarDraft.CATEGORY_FOOD, base.date, !current.isSameAs(base.payload)) { dirty ->
                        if (dirty) draftRepository.saveFood(base.date, current)
                        else draftRepository.delete(CalendarDraft.CATEGORY_FOOD, base.date)
                    }
                }
        }
        viewModelScope.launch {
            combine(_workoutSets, _exerciseBaseline) { sets, base -> base?.let { it to ExerciseDraftPayload(sets) } }
                .collect { pair ->
                    val (base, current) = pair ?: return@collect
                    syncDraft(CalendarDraft.CATEGORY_EXERCISE, base.date, !current.isSameAs(base.payload)) { dirty ->
                        if (dirty) draftRepository.saveExercise(base.date, current)
                        else draftRepository.delete(CalendarDraft.CATEGORY_EXERCISE, base.date)
                    }
                }
        }
        viewModelScope.launch {
            combine(_selfInputs, _selfAudioPath, _selfAudioDriveFileId, _selfBaseline) { inputs, path, driveId, base ->
                base?.let { it to inputs.toDraftPayload(path, driveId) }
            }.collect { pair ->
                val (base, current) = pair ?: return@collect
                syncDraft(CalendarDraft.CATEGORY_SELF, base.date, current != base.payload) { dirty ->
                    if (dirty) draftRepository.saveSelf(base.date, current)
                    else draftRepository.delete(CalendarDraft.CATEGORY_SELF, base.date)
                }
            }
        }
    }

    private fun syncDraft(category: String, date: String, dirty: Boolean, write: suspend (Boolean) -> Unit) {
        if (date == _currentDate.value) {
            _dirtyCategories.value = if (dirty) {
                _dirtyCategories.value + category
            } else {
                _dirtyCategories.value - category
            }
        }
        pendingDraftWrites[category] = { write(dirty) }
        draftJobs[category]?.cancel()
        draftJobs[category] = draftScope.launch {
            delay(DRAFT_WRITE_DEBOUNCE_MS)
            pendingDraftWrites.remove(category)?.let { pending ->
                withContext(NonCancellable) { pending() }
            }
        }
    }

    /** Runs any debounced draft writes now, so a reload reads the latest draft. */
    private suspend fun flushDraftWrites() {
        draftJobs.values.forEach { it.cancel() }
        draftJobs.clear()
        val writes = pendingDraftWrites.keys.toList().mapNotNull { pendingDraftWrites.remove(it) }
        withContext(Dispatchers.IO + NonCancellable) { writes.forEach { it() } }
    }

    /** Drops a category's pending write and its stored draft (after Save or discard). */
    private suspend fun clearDraft(category: String, date: String) {
        draftJobs.remove(category)?.cancel()
        pendingDraftWrites.remove(category)
        withContext(Dispatchers.IO) { draftRepository.delete(category, date) }
        if (date == _currentDate.value) _dirtyCategories.value = _dirtyCategories.value - category
    }

    override fun onCleared() {
        draftScope.launch { flushDraftWrites() }
        super.onCleared()
    }

    /**
     * Pull-to-refresh: throws away unsaved changes for [category] and reloads saved data (or the
     * preset). Self also re-syncs album photos from Drive.
     */
    fun discardAndReload(category: String) {
        viewModelScope.launch {
            val date = _currentDate.value
            clearDraft(category, date)
            when (category) {
                CalendarDraft.CATEGORY_ROUTINE -> loadHabitsAndActivitiesForDate(date)
                CalendarDraft.CATEGORY_FOOD -> {
                    loadEntriesForDate(date)
                    loadDatesWithCheckedMeals()
                }
                CalendarDraft.CATEGORY_EXERCISE -> loadWorkoutForDate(date)
                CalendarDraft.CATEGORY_SELF -> {
                    loadSelfForDate(date)
                    refreshSelfFromDrive()
                }
            }
        }
    }

    private fun syncWorkoutSetsToWearCache() {
        (application as? VectorApplication)?.setWorkoutSetsForWearRequest(_currentDate.value, _workoutSets.value)
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
                val dates = foodRepository.getDatesWithCheckedMeals()
                _datesWithCheckedMeals.value = dates.toSet()
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    fun selectDate(date: String) {
        if (date == _currentDate.value) return
        _routineBaseline.value = null
        _foodBaseline.value = null
        _exerciseBaseline.value = null
        _selfBaseline.value = null
        _dirtyCategories.value = emptySet()
        _currentDate.value = date
        loadEntriesForDate(date)
        loadHabitsAndActivitiesForDate(date)
        loadWorkoutForDate(date)
        loadSelfForDate(date)
        updateEditingState()
    }

    private fun SelfInputs.toDraftPayload(audioPath: String?, audioDriveFileId: String?) = SelfDraftPayload(
        scales = scales.mapKeys { it.key.name },
        intention = intention,
        highlight = highlight,
        lowlight = lowlight,
        gratitude = gratitude,
        journalText = journalText,
        audioPath = audioPath,
        audioDriveFileId = audioDriveFileId,
    )

    private fun SelfDraftPayload.toInputs() = SelfInputs(
        scales = scales.mapNotNull { (name, level) ->
            SelfScale.entries.find { it.name == name }?.let { it to level }
        }.toMap(),
        intention = intention,
        highlight = highlight,
        lowlight = lowlight,
        gratitude = gratitude,
        journalText = journalText,
    )

    private fun loadSelfForDate(date: String) {
        _selfBaseline.value = null
        selfJob?.cancel()
        selfJob = viewModelScope.launch {
            try {
                flushDraftWrites()
                val entry = selfRepository.getSelfEntryByDateSync(date)
                val saved = SelfInputs(
                    scales = entry?.let { savedEntry ->
                        SelfScale.entries.mapNotNull { scale ->
                            scale.valueIn(savedEntry)?.let { scale to it }
                        }.toMap()
                    }.orEmpty(),
                    intention = entry?.intention.orEmpty(),
                    highlight = entry?.highlight.orEmpty(),
                    lowlight = entry?.lowlight.orEmpty(),
                    gratitude = entry?.gratitude.orEmpty(),
                    journalText = entry?.journalText.orEmpty(),
                ).toDraftPayload(audioPath = null, audioDriveFileId = entry?.journalAudioDriveFileId)
                val current = draftRepository.loadSelf(date) ?: saved
                _selfInputs.value = current.toInputs()
                _selfAudioPath.value = current.audioPath
                _selfAudioDriveFileId.value = current.audioDriveFileId
                _selfBaseline.value = Baseline(date, saved)
                _selfCollectionsWithImages.value = selfRepository.getAllCollectionsWithImagesSync()
                _datesWithSelfEntries.value = selfRepository.getDatesWithSelfContent().toSet()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _selfCollectionsWithImages.value = emptyList()
            }
        }
    }

    private fun loadHabitsAndActivitiesForDate(date: String) {
        _routineBaseline.value = null
        habitsActivitiesJob?.cancel()
        habitsActivitiesJob = viewModelScope.launch {
            try {
                flushDraftWrites()
                _habitDefinitions.value = habitRepository.getAllDefinitions()
                val selected = habitRepository.getSelectedHabitIdsForDate(date)
                _selectedHabitIds.value = selected
                val logs = habitRepository.getLogsForDate(date)
                val byHabitId = logs.associateBy { it.habitId }
                _habitLogs.value = selected.mapNotNull { byHabitId[it] }

                activityRepository.ensureSleepLabel()
                val savedActivities = activityRepository.getEntriesForDate(date)
                val baselineActivities = if (savedActivities.isNotEmpty()) {
                    _noPresetForRoutine.value = activityRepository.getActiveRoutinePresetContent() == null
                    savedActivities
                } else {
                    activityRepository.loadPresetActivitiesForDate(date).fold(
                        onSuccess = {
                            _noPresetForRoutine.value = false
                            it
                        },
                        onFailure = { e ->
                            _noPresetForRoutine.value = true
                            if (e !is ActivityRepository.NoRoutinePresetException) {
                                showError("Failed to load routine preset: ${e.message}")
                            }
                            emptyList()
                        },
                    )
                }
                // Loading the preset may have created labels.
                _activityLabels.value = activityRepository.getAllLabels()

                val prefBed = preferenceManager.sleepTargetBedtimeMinutes.first() ?: 22 * 60
                val prefWake = preferenceManager.sleepTargetWakeupMinutes.first() ?: 6 * 60
                _defaultBedMinutes.value = prefBed
                _defaultWakeMinutes.value = prefWake
                val previousDate = previousCalendarDate(date)
                val baseline = RoutineDraftPayload(
                    activities = baselineActivities,
                    previousNight = previousDate?.let { d -> sleepRepository.getNight(d) ?: SleepEntry(date = d) },
                    tonight = sleepRepository.getNight(date) ?: SleepEntry(date = date),
                )
                val current = draftRepository.loadRoutine(date) ?: baseline
                _activityEntries.value = current.activities.sortedForDisplay()
                _previousNight.value = current.previousNight
                    ?: previousDate?.let { SleepEntry(date = it) }
                _tonight.value = current.tonight ?: SleepEntry(date = date)
                _routineBaseline.value = Baseline(date, baseline)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // expected when switching dates
            } catch (e: Exception) {
                showError("Failed to load habits/activities: ${e.message}")
            }
        }
    }

    private fun List<ActivityEntry>.sortedForDisplay(): List<ActivityEntry> = sortedWith(
        compareBy(
            { it.effectiveStartTime() },
            { it.effectiveEndTime() },
            { it.title },
        )
    )

    /** Sleep edits stay in memory with the Routine draft and are saved by [saveRoutineDay]. */
    private fun editNight(nightDate: String, transform: (SleepEntry) -> SleepEntry) {
        if (!_isEditingEnabled.value) return
        when (nightDate) {
            _tonight.value?.date -> _tonight.value = transform(_tonight.value ?: SleepEntry(date = nightDate))
            _previousNight.value?.date ->
                _previousNight.value = transform(_previousNight.value ?: SleepEntry(date = nightDate))
        }
    }

    fun setPlannedBedtime(nightDate: String, minutes: Int) =
        editNight(nightDate) { SleepEdits.plannedBedtime(it, minutes) }

    fun setPlannedWakeUp(nightDate: String, minutes: Int) =
        editNight(nightDate) { SleepEdits.plannedWakeUp(it, minutes) }

    fun setActualBedtime(nightDate: String, minutes: Int) =
        editNight(nightDate) { SleepEdits.actualBedtime(it, minutes) }

    fun setActualWakeUp(nightDate: String, minutes: Int) =
        editNight(nightDate) { SleepEdits.actualWakeUp(it, minutes) }

    fun setFellAsleep(nightDate: String, minutes: Int?) =
        editNight(nightDate) { SleepEdits.fellAsleep(it, minutes) }

    fun setLatency(nightDate: String, minutes: Int?) =
        editNight(nightDate) { SleepEdits.latency(it, minutes) }

    fun setStageMinutes(nightDate: String, stage: SleepStage, minutes: Int?) =
        editNight(nightDate) { SleepEdits.stageMinutes(it, stage, minutes) }

    fun setStagePercent(nightDate: String, stage: SleepStage, percent: Int?) =
        editNight(nightDate) { SleepEdits.stagePercent(it, stage, percent) }

    fun applySleepAiEstimate(nightDate: String, estimate: GeminiSleepClient.SleepEstimate) =
        editNight(nightDate) { SleepEdits.applyAiEstimate(it, estimate) }

    private fun previousCalendarDate(date: String): String? = try {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply {
            time = fmt.parse(date) ?: return null
            add(Calendar.DAY_OF_MONTH, -1)
        }
        fmt.format(cal.time)
    } catch (_: Exception) {
        null
    }

    fun updateHabitStatus(habitId: String, status: HabitStatus) {
        if (!_isEditingEnabled.value) return
        val date = _currentDate.value
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
        if (!_isEditingEnabled.value) return
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            showError("Habit name cannot be empty")
            return
        }
        if (_habitDefinitions.value.any { it.name.equals(trimmed, ignoreCase = true) }) {
            showError("A habit with this name already exists")
            return
        }
        val def = HabitDefinition(
            id = "habit_${UUID.randomUUID()}",
            name = trimmed,
        )
        _habitDefinitions.value = _habitDefinitions.value + def
        onCreated(def)
    }

    fun deleteHabit(id: String) {
        if (!_isEditingEnabled.value) return
        _habitDefinitions.value = _habitDefinitions.value.filter { it.id != id }
        _selectedHabitIds.value = _selectedHabitIds.value.filter { it != id }
        _habitLogs.value = _habitLogs.value.filter { it.habitId != id }
    }

    fun setSelectedHabits(ids: List<String>) {
        if (!_isEditingEnabled.value) return
        val date = _currentDate.value
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

    fun upsertActivity(entry: ActivityEntry) {
        if (!_isEditingEnabled.value) return
        val list = _activityEntries.value
        val updated = if (list.any { it.id == entry.id }) {
            list.map { if (it.id == entry.id) entry else it }
        } else {
            list + entry
        }
        _activityEntries.value = updated.sortedWith(
            compareBy(
                { it.effectiveStartTime() },
                { it.effectiveEndTime() },
                { it.title },
            )
        )
    }

    fun deleteActivity(id: String) {
        if (!_isEditingEnabled.value) return
        _activityEntries.value = _activityEntries.value.filter { it.id != id }
    }

    fun updateActivityCompletion(id: String, completion: ActivityCompletion) {
        if (!_isEditingEnabled.value) return
        _activityEntries.value = _activityEntries.value.map {
            if (it.id == id) {
                it.copy(completion = completion, timestamp = System.currentTimeMillis())
            } else {
                it
            }
        }
    }

    fun toggleActivityType(id: String) {
        if (!_isEditingEnabled.value) return
        _activityEntries.value = _activityEntries.value.map { entry ->
            if (entry.id != id) entry
            else entry.copy(
                type = if (entry.type == ActivityType.PLANNED) {
                    ActivityType.NEW
                } else {
                    ActivityType.PLANNED
                },
                timestamp = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Persists Routine activities and the two sleep nights shown for the current date.
     * Habits are managed on the Home screen.
     */
    fun saveRoutineDay() {
        if (!_isEditingEnabled.value) return
        viewModelScope.launch {
            _isRoutineSaving.value = true
            try {
                val date = _currentDate.value
                val baseline = _routineBaseline.value?.takeIf { it.date == date }?.payload
                activityRepository.persistDaySnapshot(
                    date = date,
                    entries = _activityEntries.value,
                )
                listOfNotNull(
                    _previousNight.value to baseline?.previousNight,
                    _tonight.value to baseline?.tonight,
                ).forEach { (current, saved) ->
                    if (current != null && current.copy(timestamp = 0L) != saved?.copy(timestamp = 0L)) {
                        sleepRepository.saveNight(current)
                    }
                }
                clearDraft(CalendarDraft.CATEGORY_ROUTINE, date)
                loadHabitsAndActivitiesForDate(date)
                showSuccess("Routine saved")
                DailySync.markDay(application, date)
                _previousNight.value?.let { DailySync.markDay(application, it.date) }
                DatabaseBackupScheduler.requestDebounced(application)
            } catch (e: Exception) {
                showError("Failed to save routine: ${e.message}")
            } finally {
                _isRoutineSaving.value = false
            }
        }
    }


    fun refreshSelfFromDrive() {
        viewModelScope.launch {
            _isSelfRefreshing.value = true
            try {
                val result = selfRepository.syncDiaryPhotosFromDrive()
                result.fold(
                    onSuccess = { r ->
                        loadSelfForDate(_currentDate.value)
                        val changed = r.importedCount > 0 || r.removedImagesCount > 0 || r.removedCollectionsCount > 0
                        val msg = if (!changed) {
                            "Self photos up to date"
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
                        showError(e.message ?: "Failed to refresh self photos")
                    }
                )
            } finally {
                _isSelfRefreshing.value = false
            }
        }
    }

    /** Tapping the selected level again clears it, so a mis-tap is not stuck for the day. */
    fun setSelfScale(scale: SelfScale, level: Int) {
        _selfInputs.value = _selfInputs.value.let { inputs ->
            val scales = inputs.scales.toMutableMap()
            if (scales[scale] == level) scales.remove(scale) else scales[scale] = level.coerceIn(1, 5)
            inputs.copy(scales = scales)
        }
    }

    fun setSelfIntention(text: String) {
        _selfInputs.value = _selfInputs.value.copy(intention = text)
    }

    fun setSelfHighlight(text: String) {
        _selfInputs.value = _selfInputs.value.copy(highlight = text)
    }

    fun setSelfLowlight(text: String) {
        _selfInputs.value = _selfInputs.value.copy(lowlight = text)
    }

    fun setSelfGratitude(text: String) {
        _selfInputs.value = _selfInputs.value.copy(gratitude = text)
    }

    fun setSelfJournalText(text: String) {
        _selfInputs.value = _selfInputs.value.copy(journalText = text)
    }

    fun setSelfAudioPath(path: String?) {
        _selfAudioPath.value = path
    }

    fun setSelfAudioDriveFileId(id: String?) {
        _selfAudioDriveFileId.value = id
    }

    suspend fun getSelfAudioBytes(): ByteArray? {
        val id = _selfAudioDriveFileId.value ?: return null
        return selfRepository.getDiaryAudioBytes(id)
    }

    fun saveSelf() {
        viewModelScope.launch {
            _isSelfSaving.value = true
            try {
                var journalAudioDriveFileId: String? = _selfAudioDriveFileId.value
                if (_selfAudioPath.value != null) {
                    val uploadResult = selfRepository.uploadDiaryAudioFromPath(
                        _currentDate.value,
                        _selfAudioPath.value!!
                    )
                    journalAudioDriveFileId = uploadResult.getOrNull()
                    if (journalAudioDriveFileId != null) {
                        _selfAudioDriveFileId.value = journalAudioDriveFileId
                        _selfAudioPath.value = null
                    } else {
                        _isSelfSaving.value = false
                        showError("Failed to upload self audio: ${uploadResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                }
                val inputs = _selfInputs.value
                val base = SelfEntry(
                    date = _currentDate.value,
                    intention = inputs.intention.trim().ifBlank { null },
                    highlight = inputs.highlight.trim().ifBlank { null },
                    lowlight = inputs.lowlight.trim().ifBlank { null },
                    gratitude = inputs.gratitude.trim().ifBlank { null },
                    journalText = inputs.journalText.ifBlank { null },
                    journalAudioDriveFileId = journalAudioDriveFileId
                )
                val entry = SelfScale.entries.fold(base) { acc, scale ->
                    scale.applyTo(acc, inputs.level(scale))
                }
                selfRepository.saveSelfEntry(entry)
                val date = _currentDate.value
                clearDraft(CalendarDraft.CATEGORY_SELF, date)
                _selfBaseline.value = Baseline(
                    date,
                    inputs.toDraftPayload(audioPath = null, audioDriveFileId = journalAudioDriveFileId),
                )
                _datesWithSelfEntries.value = selfRepository.getDatesWithSelfContent().toSet()
                _isSelfSaving.value = false
                showSuccess("Self saved")
                DailySync.markDay(application, date)
            } catch (e: Exception) {
                _isSelfSaving.value = false
                showError("Failed to save self: ${e.message}")
            }
        }
    }

    suspend fun createCollection(name: String): Result<SelfCollection> {
        if (_isCreatingCollection.value) return Result.failure(Exception("Creation already in progress"))
        _isCreatingCollection.value = true
        return try {
            val result = selfRepository.createCollection(_currentDate.value, name)
            if (result.isSuccess) {
                loadSelfForDate(_currentDate.value)
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
    ): Result<SelfCollectionImage> {
        val result = selfRepository.addImageToCollection(collectionId, mimeType, bytes, takenAtMillis)
        if (result.isSuccess) {
            loadSelfForDate(_currentDate.value)
            showSuccess("Image uploaded")
        } else {
            showError(result.exceptionOrNull()?.message ?: "Failed to upload image")
        }
        return result
    }

    fun deleteSelfImage(image: SelfCollectionImage) {
        viewModelScope.launch {
            selfRepository.deleteImage(image)
            loadSelfForDate(_currentDate.value)
            showSuccess("Image removed")
        }
    }

    fun deleteSelfCollection(collection: SelfCollection) {
        viewModelScope.launch {
            val result = selfRepository.deleteCollection(collection)
            if (result.isSuccess) {
                loadSelfForDate(_currentDate.value)
                showSuccess("Album removed from app (files stay on Drive until you delete them there)")
            } else {
                showError("Failed to delete: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    suspend fun getImageBytes(driveFileId: String): ByteArray? = selfRepository.getImageBytes(driveFileId)

    private fun loadEntriesForDate(date: String) {
        _foodBaseline.value = null
        collectionJob?.cancel()
        _isLoading.value = true
        collectionJob = viewModelScope.launch {
            try {
                flushDraftWrites()
                // Baseline = saved rows, else the preset (in memory until Save); a draft overrides both.
                val baselineEntries = loadFoodBaselineEntries(date)
                val current = draftRepository.loadFood(date)?.entries ?: baselineEntries
                _entries.value = current
                _foodBaseline.value = Baseline(date, FoodDraftPayload(baselineEntries))
                _isLoading.value = false
                loadDatesWithCheckedMeals()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                showError("Failed to load entries: ${e.message}")
            }
        }
    }

    private suspend fun loadFoodBaselineEntries(date: String): List<FoodEntry> {
        val existingEntries = foodRepository.getEntriesByDateSync(date)
        if (existingEntries.isNotEmpty()) {
            _noPresetForFood.value = false
            return existingEntries
        }
        val presetResult = foodRepository.loadPresetEntriesForDate(date)
        presetResult.onSuccess { presetEntries ->
            _noPresetForFood.value = false
            if (presetEntries.isEmpty()) showError("No food plan found for this day in the preset.")
            return presetEntries
        }
        val errorMsg = presetResult.exceptionOrNull()?.message ?: "Unknown error"
        _noPresetForFood.value = errorMsg.contains("No food preset active", ignoreCase = true)
        when {
            _noPresetForFood.value -> Unit
            errorMsg.contains("403") || errorMsg.contains("Forbidden") ->
                showError("Drive access denied. Please grant DRIVE scope permission in Settings.")
            errorMsg.contains("NeedsRemoteConsent") || errorMsg.contains("consent") ->
                showError("Drive consent required. Please authorize access in Settings.")
            errorMsg.contains("Drive folder ID not configured") ->
                showError("Drive not configured. Please set your Drive folder ID in Settings.")
            else -> showError("Failed to load food plan: $errorMsg")
        }
        return emptyList()
    }

    private fun loadWorkoutForDate(date: String) {
        _exerciseBaseline.value = null
        workoutCollectionJob?.cancel()
        workoutCollectionJob = viewModelScope.launch {
            try {
                flushDraftWrites()
                // Baseline = saved sets, else the preset blueprint (in memory until Save).
                val result = workoutRepository.loadWorkoutForDateDisplay(date)
                val baselineSets = result.getOrElse { e ->
                    val msg = e.message ?: "Failed to load workout plan"
                    if (msg.contains("No workout preset active", ignoreCase = true)) {
                        _noPresetForWorkout.value = true
                    } else {
                        _noPresetForWorkout.value = false
                        showError(msg)
                    }
                    emptyList()
                }
                if (result.isSuccess) {
                    _noPresetForWorkout.value = workoutRepository.presetContentOrNull() == null
                }
                var sets = draftRepository.loadExercise(date)?.sets ?: baselineSets
                val pending = (application as? VectorApplication)?.getAndClearPendingWearData(date)
                if (!pending.isNullOrEmpty()) {
                    val byId = sets.associateBy { it.id }.toMutableMap()
                    pending.forEach { wear ->
                        val base = byId[wear.id] ?: wear
                        byId[wear.id] = base.mergeWearOverlay(wear)
                    }
                    sets = byId.values.toList()
                }
                _workoutSets.value = sets
                _exerciseBaseline.value = Baseline(date, ExerciseDraftPayload(baselineSets))
                syncWorkoutSetsToWearCache()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // expected when switching dates
            } catch (e: Exception) {
                showError("Failed to load workout: ${e.message}")
            }
        }
    }

    /** Update a single row (main set, drop or interval) in memory; persisted when user taps Save Workout. */
    fun updateWorkoutSetInMemory(setId: String, transform: (WorkoutSet) -> WorkoutSet) {
        _workoutSets.value = _workoutSets.value.map { entry ->
            if (entry.id != setId) entry else transform(entry)
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
            val date = _currentDate.value
            val sets = _workoutSets.value
            val result = workoutRepository.saveWorkoutForDate(date, sets)
            _isWorkoutSaving.value = false
            if (result.isSuccess) {
                clearDraft(CalendarDraft.CATEGORY_EXERCISE, date)
                _exerciseBaseline.value = Baseline(date, ExerciseDraftPayload(sets))
                showSuccess("Exercise saved")
                DailySync.markDay(application, date)
            } else {
                showError("Failed to save workout: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
            }
        }
    }

    /** Validate pasted workout JSON format (for dialog). */
    fun validateWorkoutJson(jsonString: String): Result<Unit> =
        workoutRepository.parseAndConvertToWorkoutSets(
            _currentDate.value,
            jsonString,
            _workoutSets.value
        ).map { }

    /** Add workout sets from pasted JSON (clipboard). Appends to current day; persist with Save Workout. */
    fun addWorkoutFromClipboard(jsonString: String): Result<Unit> {
        val result = workoutRepository.parseAndConvertToWorkoutSets(
            _currentDate.value,
            jsonString,
            _workoutSets.value
        )
        return result.fold(
            onSuccess = { newSets ->
                _workoutSets.value = _workoutSets.value + newSets
                syncWorkoutSetsToWearCache()
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
    }

    fun parseClipboardWorkoutSessions(jsonString: String, mode: CalendarAiMode) =
        workoutRepository.parseClipboardToSessions(jsonString, mode)

    fun decodeExerciseLibraryItem(item: ExerciseLibraryItem) =
        libraryRepository.decodeExerciseSession(item)

    fun deleteExerciseLibraryItem(id: String) {
        viewModelScope.launch { libraryRepository.deleteExercise(id) }
    }

    fun deleteMealLibraryItem(id: String) {
        viewModelScope.launch { libraryRepository.deleteMeal(id) }
    }

    fun deleteActivityLibraryItem(id: String) {
        viewModelScope.launch { libraryRepository.deleteActivity(id) }
    }

    /** Muscle tags, load unit and recent prescriptions used to ground AI exercise generation. */
    suspend fun exerciseAiContext(): ExerciseAiContext =
        workoutRepository.buildExerciseAiContext(_currentDate.value).copy(
            existingSessionTitles = _workoutSets.value
                .map { it.sessionTitle }
                .filter { it.isNotBlank() }
                .distinct()
        )

    /** Appends reviewed sessions to the current day; persist with Save. */
    fun addWorkoutSessions(sessions: List<WorkoutDaySession>): Result<Unit> {
        if (!_isEditingEnabled.value) return Result.failure(Exception("Editing is locked for this date"))
        val result = workoutRepository.convertSessionsToWorkoutSets(
            _currentDate.value,
            sessions,
            _workoutSets.value
        )
        return result.fold(
            onSuccess = { newSets ->
                _workoutSets.value = _workoutSets.value + newSets
                syncWorkoutSetsToWearCache()
                viewModelScope.launch { libraryRepository.upsertExerciseSessions(sessions) }
                val exerciseCount = sessions.sumOf { it.exercises.size }
                showSuccess("Added $exerciseCount exercises. Tap Save to keep it.")
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
    }

    /** Replaces every session on the current day (plan modes); persist with Save. */
    fun replaceDayWorkout(sessions: List<WorkoutDaySession>): Result<Unit> {
        if (!_isEditingEnabled.value) return Result.failure(Exception("Editing is locked for this date"))
        return workoutRepository.convertPlannedDayToWorkoutSets(_currentDate.value, sessions).map { sets ->
            _workoutSets.value = sets
            syncWorkoutSetsToWearCache()
            viewModelScope.launch { libraryRepository.upsertExerciseSessions(sessions) }
            showSuccess("Day replaced with ${sessions.size} session(s). Tap Save to keep it.")
        }
    }

    /** Adds one confirmed new activity (form) and saves it to the Routine library. */
    fun addActivity(entry: ActivityEntry) {
        if (!_isEditingEnabled.value) return
        upsertActivity(entry)
        rememberActivities(listOf(entry))
    }

    fun addActivities(entries: List<ActivityEntry>) {
        if (!_isEditingEnabled.value || entries.isEmpty()) return
        val incomingIds = entries.map { it.id }.toSet()
        _activityEntries.value = (_activityEntries.value.filter { it.id !in incomingIds } + entries).sortedForDisplay()
        rememberActivities(entries)
        showSuccess("Added ${entries.size} activities. Tap Save to keep it.")
    }

    /** Replaces every activity on the current day (plan modes); persist with Save. */
    fun replaceDayActivities(entries: List<ActivityEntry>) {
        if (!_isEditingEnabled.value) return
        _activityEntries.value = entries.sortedForDisplay()
        rememberActivities(entries)
        showSuccess("Day replaced with ${entries.size} activities. Tap Save to keep it.")
    }

    private fun rememberActivities(entries: List<ActivityEntry>) {
        val labelNames = _activityLabels.value.associate { it.id to it.name }
        viewModelScope.launch {
            entries.forEach { entry ->
                libraryRepository.upsertActivity(
                    title = entry.title,
                    startTime = entry.effectiveStartTime(),
                    endTime = entry.effectiveEndTime(),
                    labelName = labelNames[entry.labelId].orEmpty(),
                    type = entry.type,
                    description = entry.description,
                )
            }
        }
    }

    fun addFoodEntries(drafts: List<MealImportDraft>) {
        if (!_isEditingEnabled.value || drafts.isEmpty()) return
        _entries.value = _entries.value + drafts.map { it.toFoodEntry(_currentDate.value) }
        rememberMeals(drafts)
        showSuccess("Added ${drafts.size} food rows. Tap Save to keep it.")
    }

    /** Replaces every food row on the current day (plan modes); persist with Save. */
    fun replaceDayFood(drafts: List<MealImportDraft>) {
        if (!_isEditingEnabled.value) return
        _entries.value = drafts.map { it.toFoodEntry(_currentDate.value) }
        rememberMeals(drafts)
        showSuccess("Day replaced with ${drafts.size} food rows. Tap Save to keep it.")
    }

    private fun rememberMeals(drafts: List<MealImportDraft>) {
        viewModelScope.launch {
            drafts.forEach { d ->
                libraryRepository.upsertMeal(d.name, d.kcal, d.protein, d.carbs, d.fats, d.amount, d.unit)
            }
        }
    }

    /** Current activities plus the weekday's preset, for Routine AI prompts. */
    suspend fun routineDayContext(): CalendarDayContext {
        val labelNames = _activityLabels.value.associate { it.id to it.name }
        val existing = _activityEntries.value.joinToString("\n") { entry ->
            "- ${entry.effectiveStartTime()}–${entry.effectiveEndTime()} ${entry.title}" +
                " [${labelNames[entry.labelId].orEmpty()}] ${entry.type}"
        }
        val preset = activityRepository.presetPlanForDate(_currentDate.value)
            ?.let(activityRepository::encodePlanActivitiesJson)
        return CalendarDayContext(existingItems = existing, presetJson = preset)
    }

    /** Current food rows plus the weekday's preset and its macro totals, for Food AI prompts. */
    suspend fun foodDayContext(): CalendarDayContext {
        val existing = _entries.value.sortedBy { it.effectiveTime() }.joinToString("\n") { entry ->
            "- ${entry.effectiveTime()} ${entry.name} ${formatAmount(entry.effectiveAmount())} ${entry.unit}" +
                " (${entry.kcal.toInt()} kcal)"
        }
        val presetEntries = foodRepository.presetPlanEntriesForDate(_currentDate.value)
        val targets = presetEntries?.takeIf { it.isNotEmpty() }?.let { items ->
            "${items.sumOf { it.kcal }.toInt()} kcal, protein ${items.sumOf { it.protein }.toInt()} g, " +
                "carbs ${items.sumOf { it.carbs }.toInt()} g, fats ${items.sumOf { it.fats }.toInt()} g"
        }
        return CalendarDayContext(
            existingItems = existing,
            presetJson = presetEntries?.let(foodRepository::encodePlanEntriesJson),
            targets = targets,
        )
    }

    /** The weekday's preset sessions, for Exercise AI prompts (existing titles live in [exerciseAiContext]). */
    suspend fun exerciseDayContext(): CalendarDayContext = CalendarDayContext(
        presetJson = workoutRepository.presetSessionsForDate(_currentDate.value)
            ?.let(workoutRepository::encodeSessionsJson),
    )

    private fun formatAmount(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    /** JSON for one workout session tile, for clipboard → Add exercise from JSON. */
    fun encodeWorkoutSessionForClipboard(sessionSets: List<WorkoutSet>): String =
        workoutRepository.encodeSessionForClipboard(sessionSets)

    fun notifyClipboardCopied() {
        showSuccess("Copied to clipboard")
    }

    fun notifyTileRemovedFromDay() {
        showSuccess("Removed from this day. Tap Save to keep it.")
    }

    /** Drops all sets for one session in memory; persists when the user saves the Workout tab. */
    fun removeWorkoutSession(setsToRemove: List<WorkoutSet>) {
        if (!_isEditingEnabled.value) return
        if (setsToRemove.isEmpty()) return
        val removeIds = setsToRemove.map { it.id }.toSet()
        _workoutSets.value = _workoutSets.value.filter { it.id !in removeIds }
        syncWorkoutSetsToWearCache()
    }

    fun updateEntryMultiplier(entryId: String, multiplier: Double) {
        if (!_isEditingEnabled.value) return
        val entry = _entries.value.find { it.id == entryId } ?: return
        val coerced = multiplier.coerceIn(0.0, 3.0)
        val updated = entry.copy(
            quantityMultiplier = coerced,
            status = entry.resolveAuditStatus(multiplier = coerced)
        )
        _entries.value = _entries.value.map { if (it.id == entryId) updated else it }
    }

    /**
     * Updates the meal's effective time-of-day (calendar grouping). Preset [FoodEntry.plannedTime]
     * is left unchanged; status becomes ADJUSTED when effective time diverges from planned.
     */
    fun updateEntryPlannedTime(entryId: String, newPlannedTime: String) {
        if (!_isEditingEnabled.value) return
        val entry = _entries.value.find { it.id == entryId } ?: return
        val normalized = normalizeFoodPlannedTime(newPlannedTime)
        if (normalized == entry.effectiveTime()) return
        val updated = entry.copy(
            time = normalized,
            status = entry.copy(time = normalized).resolveAuditStatus()
        )
        _entries.value = _entries.value.map { if (it.id == entryId) updated else it }
    }

    private fun normalizeFoodPlannedTime(raw: String): String {
        val parts = raw.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 12
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return String.format(Locale.US, "%02d:%02d", h, m)
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

    fun toggleEditingEnabled() {
        _isEditingEnabled.value = !_isEditingEnabled.value
    }

    fun addManualEntry(
        name: String,
        time: String,
        amount: Double,
        unit: String,
        kcal: Double,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fats: Double = 0.0,
        asPlanned: Boolean = false,
    ) {
        if (!_isEditingEnabled.value) return
        val normalizedTime = normalizeFoodPlannedTime(time)
        val newEntry = FoodEntry(
            id = UUID.randomUUID().toString(),
            date = _currentDate.value,
            plannedTime = if (asPlanned) normalizedTime else null,
            name = name,
            kcal = kcal,
            protein = protein,
            carbs = carbs,
            fats = fats,
            plannedAmount = if (asPlanned) amount else null,
            time = normalizedTime,
            amount = amount,
            quantityMultiplier = 1.0,
            unit = unit,
            isChecked = false,
            notEaten = false,
            status = if (asPlanned) AuditStatus.PLANNED else AuditStatus.UNPLANNED,
            timestamp = System.currentTimeMillis()
        )
        _entries.value = _entries.value + newEntry
        viewModelScope.launch {
            libraryRepository.upsertMeal(name, kcal, protein, carbs, fats, amount, unit)
        }
    }

    fun updateManualEntry(
        entryId: String,
        name: String,
        time: String,
        amount: Double,
        unit: String,
        kcal: Double,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fats: Double = 0.0,
        asPlanned: Boolean = false,
    ) {
        if (!_isEditingEnabled.value) return
        val entry = _entries.value.find { it.id == entryId } ?: return
        val normalizedTime = normalizeFoodPlannedTime(time)
        val updatedBase = entry.copy(
            name = name,
            time = normalizedTime,
            amount = amount,
            unit = unit,
            kcal = kcal,
            protein = protein,
            carbs = carbs,
            fats = fats,
            plannedTime = if (asPlanned) (entry.plannedTime ?: normalizedTime) else null,
            plannedAmount = if (asPlanned) (entry.plannedAmount ?: amount) else null,
        )
        val updated = updatedBase.copy(status = updatedBase.resolveAuditStatus())
        _entries.value = _entries.value.map { if (it.id == entryId) updated else it }
    }

    /** Promotes a New (unplanned) meal onto the day's plan using its current time and amount. */
    fun markEntryAsPlanned(entryId: String) {
        if (!_isEditingEnabled.value) return
        val entry = _entries.value.find { it.id == entryId } ?: return
        if (entry.hasPresetBaseline()) return
        val withBaseline = entry.copy(
            plannedTime = entry.effectiveTime(),
            plannedAmount = entry.effectiveAmount(),
        )
        val updated = withBaseline.copy(status = withBaseline.resolveAuditStatus())
        _entries.value = _entries.value.map { if (it.id == entryId) updated else it }
    }

    fun deleteEntry(entryId: String) {
        if (!_isEditingEnabled.value) return
        _entries.value = _entries.value.filter { it.id != entryId }
    }

    fun editUnplannedEntry(entry: FoodEntry) {
        _editingEntry.value = entry
    }

    val editingEntry: StateFlow<FoodEntry?> = _editingEntry.asStateFlow()

    fun getEditingEntry(): FoodEntry? = _editingEntry.value

    fun clearEditingEntry() {
        _editingEntry.value = null
    }

    /**
     * Saves only the Food/Nutrition data for the current date.
     * Called when the user taps Save on the Food tab.
     */
    fun saveFoodDay() {
        viewModelScope.launch {
            _isSaving.value = true
            val date = _currentDate.value
            foodRepository.replaceEntriesForDate(date, _entries.value)
            val result = foodRepository.saveDay(date)
            if (result.isSuccess) {
                clearDraft(CalendarDraft.CATEGORY_FOOD, date)
                val saved = foodRepository.getEntriesByDateSync(date)
                _foodBaseline.value = null
                _entries.value = saved
                _foodBaseline.value = Baseline(date, FoodDraftPayload(saved))
            }
            _isSaving.value = false
            loadDatesWithCheckedMeals()
            if (result.isSuccess) {
                showSuccess("Food saved")
                DailySync.markDay(application, date)
                DatabaseBackupScheduler.requestDebounced(application)
            } else {
                showError("Failed to save food: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
            }
        }
    }

    fun dismissSnackbar() {
        _showSnackbar.value = false
    }

    fun notifySelfError(message: String) {
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

    private companion object {
        const val DRAFT_WRITE_DEBOUNCE_MS = 300L
    }
}
