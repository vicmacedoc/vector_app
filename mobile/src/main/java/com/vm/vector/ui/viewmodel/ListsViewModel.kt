package com.vm.vector.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vm.core.models.VectorList
import com.vm.vector.data.DriveConsentHandler
import com.vm.vector.data.DriveFileInfo
import com.vm.vector.data.DriveResult
import com.vm.vector.data.ListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode

data class ListsUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val files: List<DriveFileInfo> = emptyList(),
    val selectedFileId: String? = null,
    val workingList: VectorList? = null,
    val showSnackbar: Boolean = false,
    val snackbarMessage: String = "",
    val configError: Boolean = false,
    val listsFolderMissing: Boolean = false,
    val showAddModal: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val deleteTargetFileId: String? = null,
    val pendingSaveConsentIntent: Intent? = null,
)

class ListsViewModel(
    private val repository: ListRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListsUiState())
    val uiState: StateFlow<ListsUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                configError = false,
                listsFolderMissing = false,
            )
            when (val r = repository.listJsonFiles()) {
                is DriveResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        files = r.data,
                        selectedFileId = null,
                        workingList = null,
                    )
                }
                is DriveResult.Unauthorized -> showError("Sign in required")
                is DriveResult.ConfigurationError -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, configError = true)
                    showError("Root ID not accessible. Check Settings.")
                }
                is DriveResult.ListsFolderNotFound -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, listsFolderMissing = true)
                    showError("Discovery failed: 'lists' folder not found under root.")
                }
                is DriveResult.NeedsRemoteConsent -> {
                    DriveConsentHandler.requestConsent(r.intent) { refresh() }
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    showError("Permission required — opening consent…")
                }
                is DriveResult.OtherError -> showError(r.message)
            }
        }
    }

    fun selectFile(fileId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, selectedFileId = fileId)
            when (val r = repository.getFileContent(fileId)) {
                is DriveResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        workingList = r.data,
                    )
                }
                is DriveResult.Unauthorized -> showError("Sign in required")
                is DriveResult.ConfigurationError -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, configError = true)
                    showError("Root ID not accessible.")
                }
                is DriveResult.ListsFolderNotFound -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, listsFolderMissing = true)
                    showError("'lists' folder not found.")
                }
                is DriveResult.NeedsRemoteConsent -> {
                    DriveConsentHandler.requestConsent(r.intent) { selectFile(fileId) }
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    showError("Permission required — opening consent…")
                }
                is DriveResult.OtherError -> showError(r.message)
            }
        }
    }

    fun toggleItemCheck(itemId: String) {
        val list = _uiState.value.workingList ?: return
        val updated = list.copy(
            items = list.items.map { if (it.id == itemId) it.copy(isChecked = !it.isChecked) else it }
        )
        _uiState.value = _uiState.value.copy(workingList = updated)
    }

    fun updateItemRemaining(itemId: String, value: Double) {
        val list = _uiState.value.workingList ?: return
        val roundedValue = BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble()
        val updated = list.copy(
            items = list.items.map { if (it.id == itemId) it.copy(remaining = roundedValue) else it }
        )
        _uiState.value = _uiState.value.copy(workingList = updated)
    }

    fun updateItemPriority(itemId: String, priority: String) {
        val list = _uiState.value.workingList ?: return
        val valid = setOf("High", "Mid", "Low", "Hold", "None")
        val value = if (priority in valid) priority else "None"
        val updated = list.copy(
            items = list.items.map { if (it.id == itemId) it.copy(priority = value) else it }
        )
        _uiState.value = _uiState.value.copy(workingList = updated)
    }

    fun openAddModal() {
        _uiState.value = _uiState.value.copy(showAddModal = true)
    }

    fun closeAddModal() {
        _uiState.value = _uiState.value.copy(showAddModal = false)
    }

    fun submitAdd(fileName: String, jsonContent: String) {
        viewModelScope.launch {
            try {
                json.decodeFromString<VectorList>(jsonContent)
            } catch (e: Exception) {
                showError("Invalid JSON: ${e.message}")
                return@launch
            }
            when (val r = repository.createFile(fileName, jsonContent)) {
                is DriveResult.Success -> {
                    _uiState.value = _uiState.value.copy(showAddModal = false)
                    showSuccess("File created.")
                    refresh()
                    selectFile(r.data.id)
                }
                is DriveResult.Unauthorized -> showError("Sign in required")
                is DriveResult.ConfigurationError -> {
                    _uiState.value = _uiState.value.copy(configError = true)
                    showError("Root ID not accessible.")
                }
                is DriveResult.ListsFolderNotFound -> showError("'lists' folder not found.")
                is DriveResult.NeedsRemoteConsent -> {
                    DriveConsentHandler.requestConsent(r.intent) { submitAdd(fileName, jsonContent) }
                    showError("Permission required — opening consent…")
                }
                is DriveResult.OtherError -> showError(r.message)
            }
        }
    }

    fun requestDeleteFile(fileId: String) {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = true, deleteTargetFileId = fileId)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = false, deleteTargetFileId = null)
    }

    fun confirmDelete() {
        val fileId = _uiState.value.deleteTargetFileId ?: return
        viewModelScope.launch {
            when (val r = repository.deleteFile(fileId)) {
                is DriveResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        showDeleteConfirm = false,
                        deleteTargetFileId = null,
                    )
                    showSuccess("File deleted.")
                    refresh()
                }
                is DriveResult.Unauthorized -> showError("Sign in required")
                is DriveResult.ConfigurationError -> showError("Root ID not accessible.")
                is DriveResult.ListsFolderNotFound -> showError("'lists' folder not found.")
                is DriveResult.NeedsRemoteConsent -> {
                    DriveConsentHandler.requestConsent(r.intent) { confirmDelete() }
                    showError("Permission required — opening consent…")
                }
                is DriveResult.OtherError -> showError(r.message)
            }
        }
    }

    fun saveChanges() {
        val fileId = _uiState.value.selectedFileId ?: return
        val list = _uiState.value.workingList ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            when (val r = repository.saveFileContent(fileId, list)) {
                is DriveResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    showSuccess("Changes saved.")
                }
                is DriveResult.Unauthorized -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    showError("Sign in required")
                }
                is DriveResult.ConfigurationError -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, configError = true)
                    showError("Root ID not accessible.")
                }
                is DriveResult.ListsFolderNotFound -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    showError("'lists' folder not found.")
                }
                is DriveResult.NeedsRemoteConsent -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        pendingSaveConsentIntent = r.intent,
                    )
                    showError("Permission required — open consent, then Save again.")
                }
                is DriveResult.OtherError -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    showError(r.message)
                }
            }
        }
    }

    fun dismissSnackbar() {
        _uiState.value = _uiState.value.copy(showSnackbar = false)
    }

    fun clearPendingSaveConsent() {
        _uiState.value = _uiState.value.copy(pendingSaveConsentIntent = null)
    }

    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            showSnackbar = true,
            snackbarMessage = message,
        )
    }

    private fun showSuccess(message: String) {
        _uiState.value = _uiState.value.copy(
            showSnackbar = true,
            snackbarMessage = message,
        )
    }
}
