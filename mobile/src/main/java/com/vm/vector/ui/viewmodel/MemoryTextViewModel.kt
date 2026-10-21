package com.vm.vector.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vm.core.models.MemoryNote
import com.vm.vector.VectorApplication
import com.vm.vector.data.BrainFileStore
import com.vm.vector.data.BrainRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Full-screen markdown editor for a TEXT [MemoryNote]; the body autosaves while typing. */
class MemoryTextViewModel(
    application: Application,
    private val repository: BrainRepository,
    private val noteId: String,
) : AndroidViewModel(application) {

    val note: StateFlow<MemoryNote?> = repository.note(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _body = MutableStateFlow<String?>(null)
    /** Null until the stored body has loaded. */
    val body: StateFlow<String?> = _body.asStateFlow()

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            _body.value = repository.getNote(noteId)?.body.orEmpty()
        }
    }

    fun onBodyChange(text: String) {
        _body.value = text
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            repository.updateNoteBody(noteId, text)
        }
    }

    fun updateDetails(title: String, description: String) {
        viewModelScope.launch {
            val current = repository.getNote(noteId) ?: return@launch
            repository.saveNote(
                current.copy(
                    title = title.trim().ifBlank { current.title },
                    description = description.trim(),
                ),
            )
        }
    }

    override fun onCleared() {
        val pending = _body.value
        if (saveJob?.isActive == true && pending != null) {
            saveJob?.cancel()
            CoroutineScope(Dispatchers.IO).launch { repository.updateNoteBody(noteId, pending) }
        }
        super.onCleared()
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 500L
    }
}

class MemoryTextViewModelFactory(
    private val context: android.content.Context,
    private val noteId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = context.applicationContext as VectorApplication
        val fileStore = BrainFileStore(app, app.database)
        val repo = BrainRepository(app.database, fileStore)
        return MemoryTextViewModel(app, repo, noteId) as T
    }
}
