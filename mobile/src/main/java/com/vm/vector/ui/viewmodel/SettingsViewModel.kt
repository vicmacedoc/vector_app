package com.vm.vector.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vm.vector.alarm.SleepAlarmScheduler
import com.vm.vector.data.DatabaseBackupManager
import com.vm.vector.data.DietRepository
import com.vm.vector.data.DriveConsentHandler
import com.vm.vector.data.DriveService
import com.vm.vector.data.HomeRepository
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.PresetCategory
import com.vm.vector.data.PresetStorage
import com.vm.vector.data.RoutineRepository
import com.vm.vector.data.ValidateConnectionResult
import com.vm.vector.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsUiState(
    val folderId: String = "",
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
    val workoutPresetName: String? = null,
    val routinePresetName: String? = null,
    val dietPresetName: String? = null,
    val workoutPresetJsonName: String? = null,
    val routinePresetJsonName: String? = null,
    val dietPresetJsonName: String? = null,
    val showPresetOverwriteModal: Boolean = false,
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
)

enum class SleepPickerKind { Bedtime, Wakeup }

class SettingsViewModel(
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
    private val dietRepository: DietRepository,
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val presetStorage: PresetStorage,
    private val backupManager: DatabaseBackupManager,
    private val homeRepository: HomeRepository,
    private val sleepAlarmScheduler: SleepAlarmScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadFolderId()
        loadPresetNames()
        loadSleepAndAlarms()
        loadWakeAlarmPrefs()
    }

    private fun loadSleepAndAlarms() {
        viewModelScope.launch {
            val entry = homeRepository.getTodayEntrySync()
            val start = entry?.sleepStart?.let { parseTimeToMinutes(it) } ?: 22 * 60
            val end = entry?.sleepEnd?.let { parseTimeToMinutes(it) } ?: 6 * 60
            _uiState.value = _uiState.value.copy(
                sleepBedtimeMinutes = start,
                sleepWakeupMinutes = end,
            )
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
            homeRepository.saveTodaySleepTarget(bedtimeStr, wakeupStr)
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
            presetStorage.getPresetDisplayNameFlow(PresetCategory.Routine),
            presetStorage.getPresetDisplayNameFlow(PresetCategory.Diet),
            preferenceManager.workoutPresetJsonName,
            preferenceManager.routinePresetJsonName,
            preferenceManager.dietPresetJsonName,
        ) { arr: Array<String?> ->
                Pair(
                    Triple(arr[0], arr[1], arr[2]),
                    Triple(arr[3], arr[4], arr[5])
                )
            }
            .onEach { (displayNames, jsonNames) ->
                _uiState.value = _uiState.value.copy(
                    workoutPresetName = displayNames.first,
                    routinePresetName = displayNames.second,
                    dietPresetName = displayNames.third,
                    workoutPresetJsonName = jsonNames.first,
                    routinePresetJsonName = jsonNames.second,
                    dietPresetJsonName = jsonNames.third,
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
                        snackbarMessage = "Connection OK. Check Logcat (VectorDriveDiscovery) for lists/presets IDs."
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
                is ValidateConnectionResult.ListsNotFound -> {
                    _uiState.value = _uiState.value.copy(
                        isValidating = false,
                        showSnackbar = true,
                        snackbarMessage = "Discovery failed: 'lists' folder not found under root."
                    )
                }
                is ValidateConnectionResult.PresetsNotFound -> {
                    _uiState.value = _uiState.value.copy(
                        isValidating = false,
                        showSnackbar = true,
                        snackbarMessage = "Discovery failed: 'presets' folder not found under root."
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

    fun backupToDrive() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBackingUp = true)
            val result = backupManager.backup()
            _uiState.value = _uiState.value.copy(isBackingUp = false)
            _uiState.value = _uiState.value.copy(
                showSnackbar = true,
                snackbarMessage = if (result.isSuccess) "Backup to Drive completed" else "Backup failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            )
        }
    }

    fun restoreFromDrive() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true)
            val result = backupManager.restore()
            _uiState.value = _uiState.value.copy(isRestoring = false)
            _uiState.value = _uiState.value.copy(
                showSnackbar = true,
                snackbarMessage = if (result.isSuccess) "Restored from Drive. You may need to restart the app." else "Restore failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
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
            val result = dietRepository.resetDatabase()
            _uiState.value = _uiState.value.copy(isResetting = false)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    showSnackbar = true,
                    snackbarMessage = "Database reset successfully. All daily data (diet, routine, exercise, diary) has been deleted."
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

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /**
     * Called after user picks a file. Validates JSON for the category; if valid, shows overwrite modal.
     */
    fun onPresetFilePicked(category: PresetCategory, content: String, displayName: String) {
        viewModelScope.launch {
            val result = when (category) {
                PresetCategory.Workout -> workoutRepository.getPresetSetsForDateFromContent(content, today())
                PresetCategory.Routine -> routineRepository.parsePresetContentAndGetEntriesForDate(content, today())
                PresetCategory.Diet -> dietRepository.parsePresetContentAndGetEntriesForDate(content, today())
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        showPresetOverwriteModal = true,
                        presetModalCategory = category,
                        presetModalDisplayName = displayName,
                        presetModalContent = content,
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
        )
    }

    /**
     * Overwrite today's data with the new preset and save preset as active.
     */
    fun applyPresetOverwriteToday() {
        applyPreset(overwriteToday = true)
    }

    /**
     * Keep today's data; save preset as active for future days only.
     */
    fun applyPresetNextDaysOnly() {
        applyPreset(overwriteToday = false)
    }

    private fun applyPreset(overwriteToday: Boolean) {
        val category = _uiState.value.presetModalCategory ?: return
        val content = _uiState.value.presetModalContent
        val displayName = _uiState.value.presetModalDisplayName
        if (content.isBlank() || displayName.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPresetApplying = true)
            val todayStr = today()
            try {
                when (category) {
                    PresetCategory.Workout -> {
                        if (overwriteToday) {
                            workoutRepository.getPresetSetsForDateFromContent(content, todayStr).getOrThrow().let { sets ->
                                workoutRepository.saveWorkoutForDate(todayStr, sets)
                            }
                            preferenceManager.setPresetOverwriteDate(PresetCategory.Workout, todayStr)
                        }
                        presetStorage.savePreset(PresetCategory.Workout, displayName, content)
                    }
                    PresetCategory.Routine -> {
                        if (overwriteToday) {
                            routineRepository.parsePresetContentAndGetEntriesForDate(content, todayStr).getOrThrow().let { entries ->
                                routineRepository.replaceEntriesForDate(todayStr, entries)
                            }
                            preferenceManager.setPresetOverwriteDate(PresetCategory.Routine, todayStr)
                        }
                        presetStorage.savePreset(PresetCategory.Routine, displayName, content)
                    }
                    PresetCategory.Diet -> {
                        if (overwriteToday) {
                            dietRepository.parsePresetContentAndGetEntriesForDate(content, todayStr).getOrThrow().let { entries ->
                                dietRepository.replaceEntriesForDate(todayStr, entries)
                            }
                            preferenceManager.setPresetOverwriteDate(PresetCategory.Diet, todayStr)
                        }
                        presetStorage.savePreset(PresetCategory.Diet, displayName, content)
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isPresetApplying = false,
                    showPresetOverwriteModal = false,
                    presetModalCategory = null,
                    presetModalDisplayName = "",
                    presetModalContent = "",
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
            _uiState.value = _uiState.value.copy(
                showSnackbar = true,
                snackbarMessage = "${category.name} preset removed.",
            )
        }
    }
}
