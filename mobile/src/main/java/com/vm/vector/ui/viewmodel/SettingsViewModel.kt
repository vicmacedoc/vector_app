package com.vm.vector.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vm.vector.data.DriveConsentHandler
import com.vm.vector.data.DriveService
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.ValidateConnectionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class SettingsUiState(
    val folderId: String = "",
    val isLoading: Boolean = false,
    val isValidating: Boolean = false,
    val permissionRequired: Boolean = false,
    val showSnackbar: Boolean = false,
    val snackbarMessage: String = ""
)

class SettingsViewModel(
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadFolderId()
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
}
