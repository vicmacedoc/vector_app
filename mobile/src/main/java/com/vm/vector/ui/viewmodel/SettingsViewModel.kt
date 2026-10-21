package com.vm.vector.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vm.vector.alarm.SleepAlarmScheduler
import com.vm.vector.data.ActivityRepository
import com.vm.vector.data.BrainSyncWorker
import com.vm.vector.data.DailySync
import com.vm.vector.data.DatabaseBackupManager
import com.vm.vector.data.DriveConsentHandler
import com.vm.vector.data.DriveService
import com.vm.vector.data.FoodRepository
import com.vm.vector.data.GeminiApiKeySource
import com.vm.vector.data.GeminiApiKeyStore
import com.vm.core.models.ActivityLabel
import com.vm.core.models.SleepMath
import com.vm.vector.data.HomeRepository
import com.vm.vector.data.LearningSettings
import com.vm.vector.data.MotionSettings
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.PresetCategory
import com.vm.vector.data.PresetStorage
import com.vm.vector.data.SleepRepository
import com.vm.vector.data.ValidateConnectionResult
import com.vm.vector.data.WorkoutRepository
import com.vm.vector.VectorApplication
import com.vm.vector.AppRestarter
import com.vm.vector.data.backup.BackupOutcome
import com.vm.vector.data.backup.BackupSnapshotInfo
import com.vm.vector.data.sync.BrainStateSync
import com.vm.vector.data.sync.BrainSyncOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsUiState(
    val folderId: String = "",
    val geminiApiKey: String = "",
    val geminiKeyStatus: String = "Not configured",
    val geminiKeySource: GeminiApiKeySource = GeminiApiKeySource.None,
    val isLoading: Boolean = false,
    val isValidating: Boolean = false,
    val permissionRequired: Boolean = false,
    val showSnackbar: Boolean = false,
    val snackbarMessage: String = "",
    val showPasswordDialog: Boolean = false,
    val showResetConfirmation: Boolean = false,
    val isResetting: Boolean = false,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val isSyncingBrain: Boolean = false,
    val showRestorePicker: Boolean = false,
    val isLoadingSnapshots: Boolean = false,
    val backupSnapshots: List<BackupSnapshotInfo> = emptyList(),
    val workoutPresetName: String? = null,
    val foodPresetName: String? = null,
    val workoutPresetJsonName: String? = null,
    val foodPresetJsonName: String? = null,
    val routinePresetName: String? = null,
    val routinePresetJsonName: String? = null,
    val showPresetOverwriteModal: Boolean = false,
    /** Routine preset labels that will be created when the preset is applied. */
    val presetModalNewLabels: List<String> = emptyList(),
    val presetModalCategory: PresetCategory? = null,
    val presetModalDisplayName: String = "",
    val presetModalContent: String = "",
    val isPresetApplying: Boolean = false,
    val sleepBedtimeMinutes: Int = 22 * 60,
    val sleepWakeupMinutes: Int = 6 * 60,
    val wakeAlarmBaseMinutes: Int? = null,
    val wakeAlarmOffsets: List<Int> = emptyList(),
    val filloutReminderMinutes: Int? = null,
    val showSleepTimePicker: SleepPickerKind? = null,
    val showFilloutTimePicker: Boolean = false,
    val activityLabels: List<ActivityLabel> = emptyList(),
)

enum class SleepPickerKind { Bedtime, Wakeup }

class SettingsViewModel(
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
    private val foodRepository: FoodRepository,
    private val workoutRepository: WorkoutRepository,
    private val presetStorage: PresetStorage,
    private val backupManager: DatabaseBackupManager,
    private val homeRepository: HomeRepository,
    private val sleepRepository: SleepRepository,
    private val sleepAlarmScheduler: SleepAlarmScheduler,
    private val vectorApplication: VectorApplication,
    private val geminiApiKeyStore: GeminiApiKeyStore,
    private val activityRepository: ActivityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val learningSettings: StateFlow<LearningSettings> = preferenceManager.learningSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearningSettings())

    fun saveLearningSettings(settings: LearningSettings) {
        viewModelScope.launch {
            preferenceManager.saveLearningSettings(settings)
            DailySync.markConfig(vectorApplication)
            _uiState.value = _uiState.value.copy(showSnackbar = true, snackbarMessage = "Learning settings saved")
        }
    }

    val motionSettings: StateFlow<MotionSettings> = preferenceManager.motionSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MotionSettings())

    fun saveMotionSettings(settings: MotionSettings) {
        viewModelScope.launch {
            preferenceManager.saveMotionSettings(settings)
            BrainSyncWorker.requestDebounced(vectorApplication)
            _uiState.value = _uiState.value.copy(showSnackbar = true, snackbarMessage = "Motion settings saved")
        }
    }

    init {
        loadFolderId()
        loadGeminiApiKey()
        loadPresetNames()
        loadSleepAndAlarms()
        loadWakeAlarmPrefs()
        loadActivityLabels()
    }

    private fun loadSleepAndAlarms() {
        viewModelScope.launch {
            val bedtimePref = preferenceManager.sleepTargetBedtimeMinutes.first()
            val wakeupPref = preferenceManager.sleepTargetWakeupMinutes.first()
            val night = sleepRepository.getNight(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
            val start = bedtimePref ?: SleepMath.parseHhMm(night?.plannedBedtime) ?: 22 * 60
            val end = wakeupPref ?: SleepMath.parseHhMm(night?.plannedWakeUp) ?: 6 * 60
            _uiState.value = _uiState.value.copy(
                sleepBedtimeMinutes = start,
                sleepWakeupMinutes = end,
            )
            if (bedtimePref == null && wakeupPref == null && night != null &&
                (night.plannedBedtime != null || night.plannedWakeUp != null)
            ) {
                preferenceManager.saveSleepTargetMinutes(
                    SleepMath.parseHhMm(night.plannedBedtime) ?: 22 * 60,
                    SleepMath.parseHhMm(night.plannedWakeUp) ?: 6 * 60,
                )
            }
        }
    }

    private fun loadWakeAlarmPrefs() {
        combine(
            preferenceManager.wakeAlarmBaseMinutes,
            preferenceManager.wakeAlarmOffsets,
            preferenceManager.filloutReminderMinutes,
        ) { base, offsets, fillout ->
            Triple(base, offsets ?: emptyList(), fillout)
        }.onEach { (base, offsets, fillout) ->
            _uiState.value = _uiState.value.copy(
                wakeAlarmBaseMinutes = base,
                wakeAlarmOffsets = offsets,
                filloutReminderMinutes = fillout,
            )
        }.catch { e -> e.printStackTrace() }
            .launchIn(viewModelScope)
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
        return "%02d:%02d".format(m / 60, m % 60)
    }

    fun setSleepBedtime(minutes: Int) {
        _uiState.value = _uiState.value.copy(sleepBedtimeMinutes = minutes)
    }

    fun setSleepWakeup(minutes: Int) {
        _uiState.value = _uiState.value.copy(sleepWakeupMinutes = minutes)
    }

    fun showSleepTimePicker(kind: SleepPickerKind?) {
        _uiState.value = _uiState.value.copy(showSleepTimePicker = kind)
    }

    fun setPreferences() {
        viewModelScope.launch {
            val bedtime = _uiState.value.sleepBedtimeMinutes
            val wakeup = _uiState.value.sleepWakeupMinutes
            val bedtimeStr = minutesToTime(bedtime)
            val wakeupStr = minutesToTime(wakeup)
            sleepRepository.saveTodaySleepTarget(bedtimeStr, wakeupStr)
            preferenceManager.saveSleepTargetMinutes(bedtime, wakeup)
            DailySync.markConfig(vectorApplication)
            preferenceManager.saveWakeAlarmBaseMinutes(wakeup)
            preferenceManager.saveWakeAlarmOffsets(listOf(0, 5, 10, 15))
            sleepAlarmScheduler.cancelWakeAlarms()
            val ringtoneUri = sleepAlarmScheduler.pickRandomAlarmRingtone()
            sleepAlarmScheduler.scheduleWakeAlarmsRecurrent(wakeup, ringtoneUri)
            _uiState.value = _uiState.value.copy(
                wakeAlarmBaseMinutes = wakeup,
                wakeAlarmOffsets = listOf(0, 5, 10, 15),
                showSnackbar = true,
                snackbarMessage = "Sleep target and alarms set.",
            )
        }
    }

    fun removeWakeAlarm(offsetMinutes: Int) {
        viewModelScope.launch {
            val requestCode = when (offsetMinutes) {
                0 -> SleepAlarmScheduler.REQUEST_MAIN
                5 -> SleepAlarmScheduler.REQUEST_5MIN
                10 -> SleepAlarmScheduler.REQUEST_10MIN
                15 -> SleepAlarmScheduler.REQUEST_15MIN
                else -> return@launch
            }
            sleepAlarmScheduler.cancelWakeAlarm(requestCode)
            val current = _uiState.value.wakeAlarmOffsets.filter { it != offsetMinutes }
            preferenceManager.saveWakeAlarmOffsets(current)
            if (current.isEmpty()) {
                preferenceManager.saveWakeAlarmBaseMinutes(null)
                _uiState.value = _uiState.value.copy(wakeAlarmBaseMinutes = null)
            }
            _uiState.value = _uiState.value.copy(
                wakeAlarmOffsets = current,
                showSnackbar = true,
                snackbarMessage = "Alarm removed.",
            )
        }
    }

    fun showFilloutTimePicker(show: Boolean) {
        _uiState.value = _uiState.value.copy(showFilloutTimePicker = show)
    }

    fun setFilloutReminder(minutes: Int) {
        viewModelScope.launch {
            preferenceManager.saveFilloutReminderMinutes(minutes)
            DailySync.markConfig(vectorApplication)
            sleepAlarmScheduler.scheduleFilloutReminder(minutes)
            _uiState.value = _uiState.value.copy(
                filloutReminderMinutes = minutes,
                showFilloutTimePicker = false,
                showSnackbar = true,
                snackbarMessage = "Fill-out reminder set.",
            )
        }
    }

    fun removeFilloutReminder() {
        viewModelScope.launch {
            preferenceManager.saveFilloutReminderMinutes(null)
            DailySync.markConfig(vectorApplication)
            sleepAlarmScheduler.cancelFilloutReminder()
            _uiState.value = _uiState.value.copy(
                filloutReminderMinutes = null,
                showSnackbar = true,
                snackbarMessage = "Fill-out reminder removed.",
            )
        }
    }

    private fun loadPresetNames() {
        combine(
            presetStorage.getPresetDisplayNameFlow(PresetCategory.Workout),
            presetStorage.getPresetDisplayNameFlow(PresetCategory.Food),
            preferenceManager.workoutPresetJsonName,
            preferenceManager.foodPresetJsonName,
            presetStorage.getPresetDisplayNameFlow(PresetCategory.Routine),
            preferenceManager.routinePresetJsonName,
        ) { values -> values }
            .onEach { arr ->
                _uiState.value = _uiState.value.copy(
                    workoutPresetName = arr[0],
                    foodPresetName = arr[1],
                    workoutPresetJsonName = arr[2],
                    foodPresetJsonName = arr[3],
                    routinePresetName = arr[4],
                    routinePresetJsonName = arr[5],
                )
            }
            .catch { e -> e.printStackTrace() }
            .launchIn(viewModelScope)
    }

    private fun loadFolderId() {
        preferenceManager.googleDriveFolderId
            .onEach { folderId ->
                _uiState.value = _uiState.value.copy(
                    folderId = folderId ?: ""
                )
            }
            .catch { e ->
                e.printStackTrace()
            }
            .launchIn(viewModelScope)
    }

    private fun loadGeminiApiKey() {
        val resolution = geminiApiKeyStore.resolve()
        _uiState.value = _uiState.value.copy(
            geminiApiKey = geminiApiKeyStore.getCustomKey(),
            geminiKeyStatus = geminiApiKeyStore.statusLabel(),
            geminiKeySource = resolution.source,
        )
    }

    fun updateGeminiApiKey(key: String) {
        _uiState.value = _uiState.value.copy(geminiApiKey = key)
    }

    fun saveGeminiApiKey() {
        val key = _uiState.value.geminiApiKey.trim()
        if (key.isBlank()) {
            _uiState.value = _uiState.value.copy(
                showSnackbar = true,
                snackbarMessage = "Enter a Gemini API key, or use Clear",
            )
            return
        }
        geminiApiKeyStore.setCustomKey(key)
        val resolution = geminiApiKeyStore.resolve()
        _uiState.value = _uiState.value.copy(
            geminiApiKey = geminiApiKeyStore.getCustomKey(),
            geminiKeyStatus = geminiApiKeyStore.statusLabel(),
            geminiKeySource = resolution.source,
            showSnackbar = true,
            snackbarMessage = "Gemini API key saved",
        )
    }

    fun clearGeminiApiKey() {
        geminiApiKeyStore.clearCustomKey()
        val resolution = geminiApiKeyStore.resolve()
        _uiState.value = _uiState.value.copy(
            geminiApiKey = "",
            geminiKeyStatus = geminiApiKeyStore.statusLabel(),
            geminiKeySource = resolution.source,
            showSnackbar = true,
            snackbarMessage = "Custom Gemini API key cleared",
        )
    }

    fun updateFolderId(folderId: String) {
        _uiState.value = _uiState.value.copy(folderId = folderId)
    }

    fun saveFolderId() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isValidating = false)
            try {
                preferenceManager.saveGoogleDriveFolderId(_uiState.value.folderId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showSnackbar = true,
                    snackbarMessage = "Folder ID saved successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showSnackbar = true,
                    snackbarMessage = "Error saving Folder ID: ${e.message}"
                )
            }
        }
    }

    /**
     * Validates the Root ID is accessible, runs discovery for 'lists' and 'presets',
     * logs IDs to Logcat. Returns clear error messages to the UI if a folder is not found.
     */
    fun validateConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = false, isValidating = true)
            val rootId = preferenceManager.googleDriveFolderId.first()
            if (rootId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isValidating = false,
                    showSnackbar = true,
                    snackbarMessage = "Set and save Root Folder ID (Data folder) first"
                )
                return@launch
            }
            when (val r = driveService.validateConnection(rootId)) {
                is ValidateConnectionResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isValidating = false,
                        permissionRequired = false,
                        showSnackbar = true,
                        snackbarMessage = "Connection OK. brain, database and backups folders are ready."
                    )
                }
                is ValidateConnectionResult.NeedsRemoteConsent -> {
                    DriveConsentHandler.requestConsent(r.intent) { validateConnection() }
                    _uiState.value = _uiState.value.copy(
                        isValidating = false,
                        permissionRequired = true,
                    )
                }
                is ValidateConnectionResult.Unauthorized -> {
                    _uiState.value = _uiState.value.copy(
                        isValidating = false,
                        showSnackbar = true,
                        snackbarMessage = "Sign in required"
                    )
                }
                is ValidateConnectionResult.RootInaccessible -> {
                    _uiState.value = _uiState.value.copy(
                        isValidating = false,
                        showSnackbar = true,
                        snackbarMessage = "Root ID not accessible. Check the Data folder ID."
                    )
                }
                is ValidateConnectionResult.OtherError -> {
                    _uiState.value = _uiState.value.copy(
                        isValidating = false,
                        showSnackbar = true,
                        snackbarMessage = r.message
                    )
                }
            }
        }
    }

    fun dismissSnackbar() {
        _uiState.value = _uiState.value.copy(showSnackbar = false)
    }

    val lastBrainSyncAt: StateFlow<Long> = preferenceManager.lastBrainSyncAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun syncBrainNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncingBrain = true)
            val outcome = BrainStateSync(vectorApplication, vectorApplication.database).sync()
            val message = when (outcome) {
                BrainSyncOutcome.UpToDate -> "Brain is up to date"
                is BrainSyncOutcome.Pushed -> "Brain synced to Drive"
                is BrainSyncOutcome.Pulled -> "Brain updated from your other devices"
                is BrainSyncOutcome.NeedsDecision -> "Brain changed on two devices — choose how to merge"
                BrainSyncOutcome.NotConfigured -> "Set a Drive folder first"
                is BrainSyncOutcome.Failed -> "Brain sync failed: ${outcome.message}"
            }
            (outcome as? BrainSyncOutcome.Failed)?.consentIntent?.let { DriveConsentHandler.requestConsent(it, ::syncBrainNow) }
            _uiState.value = _uiState.value.copy(isSyncingBrain = false, showSnackbar = true, snackbarMessage = message)
        }
    }

    fun backupToDrive() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBackingUp = true)
            val message = when (val outcome = backupManager.backupLatest()) {
                is BackupOutcome.Success -> "Backup to Drive completed"
                is BackupOutcome.Conflict -> "Backup paused: Drive has a backup this would overwrite. Choose what to keep."
                is BackupOutcome.Failure -> "Backup failed: ${outcome.message}"
            }
            _uiState.value = _uiState.value.copy(isBackingUp = false, showSnackbar = true, snackbarMessage = message)
        }
    }

    /** Loads the latest + dated Drive backups and opens the restore picker. */
    fun openRestorePicker() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSnapshots = true, showRestorePicker = true, backupSnapshots = emptyList())
            backupManager.listSnapshots()
                .onSuccess { _uiState.value = _uiState.value.copy(isLoadingSnapshots = false, backupSnapshots = it) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoadingSnapshots = false,
                        showRestorePicker = false,
                        showSnackbar = true,
                        snackbarMessage = "Could not list backups: ${it.message}",
                    )
                }
        }
    }

    fun dismissRestorePicker() {
        _uiState.value = _uiState.value.copy(showRestorePicker = false)
    }

    /** Restores [snapshot] and relaunches the app so Room reopens the swapped database file. */
    fun restoreFromDrive(snapshot: BackupSnapshotInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true, showRestorePicker = false)
            val result = backupManager.restore(snapshot)
            if (result.isSuccess) {
                AppRestarter.restart(vectorApplication)
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                isRestoring = false,
                showSnackbar = true,
                snackbarMessage = "Restore failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            )
        }
    }

    companion object {
        private const val RESET_PASSWORD = "brutus"
    }

    fun showPasswordDialog() {
        _uiState.value = _uiState.value.copy(showPasswordDialog = true)
    }

    fun dismissPasswordDialog() {
        _uiState.value = _uiState.value.copy(showPasswordDialog = false)
    }

    fun verifyPasswordAndShowConfirmation(password: String) {
        if (password == RESET_PASSWORD) {
            _uiState.value = _uiState.value.copy(
                showPasswordDialog = false,
                showResetConfirmation = true
            )
        } else {
            _uiState.value = _uiState.value.copy(
                showPasswordDialog = false,
                showSnackbar = true,
                snackbarMessage = "Incorrect password"
            )
        }
    }

    fun dismissResetConfirmation() {
        _uiState.value = _uiState.value.copy(showResetConfirmation = false)
    }

    fun resetDatabase() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResetting = true, showResetConfirmation = false)
            val result = foodRepository.resetDatabase()
            _uiState.value = _uiState.value.copy(isResetting = false)
            if (result.isSuccess) {
                vectorApplication.notifyDatabaseReset()
                _uiState.value = _uiState.value.copy(
                    showSnackbar = true,
                    snackbarMessage = "Database reset successfully. Food, habits, activities, workout, and self mood/journal have been cleared. Photo albums (Drive) are unchanged."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    showSnackbar = true,
                    snackbarMessage = "Failed to reset database: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                )
            }
        }
    }

    // --- Presets ---

    private fun loadActivityLabels() {
        viewModelScope.launch {
            val labels = activityRepository.getAllLabels()
            _uiState.value = _uiState.value.copy(activityLabels = labels)
        }
    }

    fun createActivityLabel(name: String, colorArgb: Int, onCreated: () -> Unit = {}) {
        viewModelScope.launch {
            activityRepository.createLabel(name, colorArgb)
                .onSuccess {
                    loadActivityLabels()
                    onCreated()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        showSnackbar = true,
                        snackbarMessage = e.message ?: "Failed to create label",
                    )
                }
        }
    }

    fun deleteActivityLabel(id: String) {
        viewModelScope.launch {
            activityRepository.deleteLabel(id)
                .onSuccess { loadActivityLabels() }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        showSnackbar = true,
                        snackbarMessage = e.message ?: "Failed to delete label",
                    )
                }
        }
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /**
     * Called after user picks a file. Validates JSON for the category; if valid, shows overwrite modal.
     */
    fun onPresetFilePicked(category: PresetCategory, content: String, displayName: String) {
        viewModelScope.launch {
            val result: Result<List<Any>> = when (category) {
                PresetCategory.Workout -> workoutRepository.getPresetSetsForDateFromContent(content, today())
                PresetCategory.Food -> foodRepository.parsePresetContentAndGetEntriesForDate(content, today())
                PresetCategory.Routine -> activityRepository.parseRoutinePresetForDate(content, today())
            }
            val newLabels = if (category == PresetCategory.Routine && result.isSuccess) {
                runCatching { activityRepository.unknownPresetLabels(content) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        showPresetOverwriteModal = true,
                        presetModalCategory = category,
                        presetModalDisplayName = displayName,
                        presetModalContent = content,
                        presetModalNewLabels = newLabels,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        showSnackbar = true,
                        snackbarMessage = "Invalid preset JSON for ${category.name}: ${e.message}"
                    )
                }
            )
        }
    }

    fun dismissPresetModal() {
        _uiState.value = _uiState.value.copy(
            showPresetOverwriteModal = false,
            presetModalCategory = null,
            presetModalDisplayName = "",
            presetModalContent = "",
            presetModalNewLabels = emptyList(),
        )
    }

    /** Saves the preset file for future days only; does not change today's category data. */
    fun applyPresetFromModal() {
        val category = _uiState.value.presetModalCategory ?: return
        val content = _uiState.value.presetModalContent
        val displayName = _uiState.value.presetModalDisplayName
        if (content.isBlank() || displayName.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPresetApplying = true)
            try {
                presetStorage.savePreset(category, displayName, content)
                when (category) {
                    PresetCategory.Workout -> workoutRepository.ingestSessionsFromWeeklyPlan(content)
                    PresetCategory.Food -> foodRepository.ingestMealsFromWeeklyPlan(content)
                    PresetCategory.Routine -> {
                        activityRepository.ensurePresetLabels(content)
                        activityRepository.ingestPresetActivities(content)
                        loadActivityLabels()
                    }
                }
                DailySync.markConfig(vectorApplication)
                _uiState.value = _uiState.value.copy(
                    isPresetApplying = false,
                    showPresetOverwriteModal = false,
                    presetModalCategory = null,
                    presetModalDisplayName = "",
                    presetModalContent = "",
                    presetModalNewLabels = emptyList(),
                    showSnackbar = true,
                    snackbarMessage = "Preset \"$displayName\" is now active for ${category.name}.",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPresetApplying = false,
                    showSnackbar = true,
                    snackbarMessage = "Failed to apply preset: ${e.message}",
                )
            }
        }
    }

    fun removePreset(category: PresetCategory) {
        viewModelScope.launch {
            presetStorage.clearPreset(category)
            DailySync.markConfig(vectorApplication)
            _uiState.value = _uiState.value.copy(
                showSnackbar = true,
                snackbarMessage = "${category.name} preset removed.",
            )
        }
    }
}
