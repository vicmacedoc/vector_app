package com.vm.vector.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vm.core.models.BrainNode
import com.vm.core.models.BrainTag
import com.vm.core.models.MemoryNote
import com.vm.vector.VectorApplication
import com.vm.vector.data.BrainFileStore
import com.vm.vector.data.BrainRepository
import com.vm.vector.data.BrainSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemoryTileViewModel(
    application: Application,
    private val repository: BrainRepository,
    private val nodeId: String,
) : AndroidViewModel(application) {

    val tile: StateFlow<BrainNode?> = repository.memoryTile(nodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sources: StateFlow<List<MemoryNote>> = repository.notesInNode(nodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tagLibrary: StateFlow<List<BrainTag>> = repository.tagLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val otherTiles: StateFlow<List<BrainNode>> = repository.memoryTiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val inboxNotes: StateFlow<List<MemoryNote>> = repository.inboxNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renameTile(nodeId, name.trim()) }
    }

    fun addTag(name: String) {
        viewModelScope.launch {
            val libraryName = repository.ensureTag(name) ?: return@launch
            val current = repository.getNode(nodeId)?.tags.orEmpty()
            if (current.none { it.equals(libraryName, ignoreCase = true) }) {
                repository.setTileTags(nodeId, current + libraryName)
            }
        }
    }

    fun removeTag(name: String) {
        viewModelScope.launch {
            val current = repository.getNode(nodeId)?.tags.orEmpty()
            repository.setTileTags(nodeId, current.filterNot { it.equals(name, ignoreCase = true) })
        }
    }

    fun deleteLibraryTag(tag: BrainTag) {
        viewModelScope.launch { repository.deleteTag(tag.id) }
    }

    fun createText(title: String, description: String, onCreated: (noteId: String) -> Unit) {
        viewModelScope.launch {
            val note = repository.createTextNote(
                title = title.trim().ifBlank { "Note" },
                body = "",
                nodeId = nodeId,
                description = description.trim(),
            )
            onCreated(note.id)
        }
    }

    fun addLink(url: String, title: String) {
        val clean = url.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { repository.createLinkNote(title.trim(), clean, nodeId) }
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val (imported, anyVideo) = repository.importUrisAsNotes(uris, nodeId)
            if (imported > 0) BrainSyncWorker.enqueue(getApplication(), wifiOnly = anyVideo)
            _message.value = when {
                imported == uris.size -> null
                imported == 0 -> "Import failed"
                else -> "Imported $imported of ${uris.size} files"
            }
        }
    }

    fun pullFromInbox(noteIds: Collection<String>) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch {
            noteIds.forEach { repository.moveNote(it, nodeId) }
            _message.value = if (noteIds.size == 1) "Moved 1 item from Inbox" else "Moved ${noteIds.size} items from Inbox"
        }
    }

    fun moveSource(noteId: String, target: String?) {
        viewModelScope.launch {
            repository.moveNote(noteId, target)
            _message.value = if (target == null) "Moved to Inbox" else "Moved"
        }
    }

    fun moveSourceToNewTile(noteId: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val created = repository.createMemoryTile(name.trim())
            repository.moveNote(noteId, created.id)
            _message.value = "Moved to ${created.name}"
        }
    }

    fun deleteSource(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
            BrainSyncWorker.enqueue(getApplication())
        }
    }

    fun deleteTile(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteMemoryTile(nodeId)
            BrainSyncWorker.enqueue(getApplication())
            onDeleted()
        }
    }

    fun repository(): BrainRepository = repository
}

class MemoryTileViewModelFactory(
    private val context: android.content.Context,
    private val nodeId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = context.applicationContext as VectorApplication
        val fileStore = BrainFileStore(app, app.database)
        val repo = BrainRepository(app.database, fileStore)
        return MemoryTileViewModel(app, repo, nodeId) as T
    }
}
