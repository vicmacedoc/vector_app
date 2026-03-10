package com.vm.vector.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vm.core.models.VectorItem
import com.vm.core.models.VectorList
import com.vm.vector.data.DriveConsentHandler
import com.vm.vector.data.DriveFileInfo
import com.vm.vector.data.DriveResult
import com.vm.vector.data.ListRepository
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.SaveFileResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

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
    val showAddEntryModal: Boolean = false,
    val showDeleteEntryConfirm: Boolean = false,
    val deleteTargetItemId: String? = null,
    val pendingSaveConsentIntent: Intent? = null,
)

class ListsViewModel(
    private val repository: ListRepository,
    private val preferenceManager: PreferenceManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListsUiState())
    val uiState: StateFlow<ListsUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    /** Loads file list from local cache and restores last opened list if still present. Call when signed in on first load. */
    fun loadFromLocal() {
        viewModelScope.launch {
            val files = repository.listLocalJsonFiles()
            val lastId = preferenceManager.lastOpenedListId.first()
            _uiState.value = _uiState.value.copy(
                files = files,
                selectedFileId = null,
                workingList = null,
            )
            if (files.isNotEmpty() && lastId != null && files.any { it.id == lastId }) {
                val list = repository.getLocalFileContent(lastId)
                if (list != null) {
                    _uiState.value = _uiState.value.copy(
                        selectedFileId = lastId,
                        workingList = list,
                    )
                }
            }
        }
    }

    /** Fetches from Drive, replaces local cache with Drive contents. Only runs when user taps Refresh. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                configError = false,
                listsFolderMissing = false,
            )
            when (val r = repository.refreshFromDrive()) {
                is DriveResult.Success -> {
                    val files = r.data
                    val lastId = preferenceManager.lastOpenedListId.first()
                    val keepSelection = lastId != null && files.any { it.id == lastId }
                    val selectedId = if (keepSelection) lastId else null
                    val workingList = if (keepSelection && selectedId != null) repository.getLocalFileContent(selectedId) else null
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        files = files,
                        selectedFileId = selectedId,
                        workingList = workingList,
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
            _uiState.value = _uiState.value.copy(selectedFileId = fileId)
            val list = repository.getLocalFileContent(fileId)
            if (list != null) {
                _uiState.value = _uiState.value.copy(workingList = list)
                preferenceManager.saveLastOpenedListId(fileId)
            } else {
                showError("List not found locally.")
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

    fun openAddEntryModal() {
        _uiState.value = _uiState.value.copy(showAddEntryModal = true)
    }

    fun closeAddEntryModal() {
        _uiState.value = _uiState.value.copy(showAddEntryModal = false)
    }

    fun addEntry(title: String, quantity: Double?, remaining: Double?, unit: String?, priority: String?) {
        val list = _uiState.value.workingList ?: return
        val validPriority = setOf("High", "Mid", "Low", "Hold", "None")
        val priorityValue = if (priority != null && priority in validPriority) priority else null
        val newItem = VectorItem(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            isChecked = false,
            quantity = quantity,
            remaining = remaining ?: quantity,
            unit = unit?.takeIf { it.isNotBlank() },
            priority = priorityValue,
        )
        val updated = list.copy(items = list.items + newItem)
        _uiState.value = _uiState.value.copy(workingList = updated, showAddEntryModal = false)
    }

    fun requestDeleteEntry(itemId: String) {
        _uiState.value = _uiState.value.copy(showDeleteEntryConfirm = true, deleteTargetItemId = itemId)
    }

    fun cancelDeleteEntry() {
        _uiState.value = _uiState.value.copy(showDeleteEntryConfirm = false, deleteTargetItemId = null)
    }

    fun confirmDeleteEntry() {
        val itemId = _uiState.value.deleteTargetItemId ?: return
        val list = _uiState.value.workingList ?: return
        val updated = list.copy(items = list.items.filter { it.id != itemId })
        _uiState.value = _uiState.value.copy(
            workingList = updated,
            showDeleteEntryConfirm = false,
            deleteTargetItemId = null,
        )
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
                    _uiState.value = _uiState.value.copy(
                        showAddModal = false,
                        files = _uiState.value.files + r.data,
                        selectedFileId = r.data.id,
                        workingList = repository.getLocalFileContent(r.data.id),
                    )
                    preferenceManager.saveLastOpenedListId(r.data.id)
                    showSuccess("File created.")
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
                    val wasSelected = _uiState.value.selectedFileId == fileId
                    _uiState.value = _uiState.value.copy(
                        showDeleteConfirm = false,
                        deleteTargetFileId = null,
                        files = _uiState.value.files.filter { it.id != fileId },
                        selectedFileId = if (wasSelected) null else _uiState.value.selectedFileId,
                        workingList = if (wasSelected) null else _uiState.value.workingList,
                    )
                    if (wasSelected) preferenceManager.saveLastOpenedListId(null)
                    showSuccess("File deleted.")
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
        if (_uiState.value.isSaving) return
        val fileId = _uiState.value.selectedFileId ?: return
        val list = _uiState.value.workingList ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            when (val r = repository.saveFileContent(fileId, list)) {
                is SaveFileResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    showSuccess("Changes saved.")
                }
                is SaveFileResult.SavedLocallyOnly -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    showSuccess("Saved locally. Sync when online.")
                }
                is SaveFileResult.NeedsRemoteConsent -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        pendingSaveConsentIntent = r.intent,
                    )
                    showError("Permission required — open consent, then Save again.")
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
