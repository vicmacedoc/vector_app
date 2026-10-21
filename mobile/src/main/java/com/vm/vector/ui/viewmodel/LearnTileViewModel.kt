package com.vm.vector.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vm.core.models.BrainNode
import com.vm.core.models.BrainSection
import com.vm.core.models.BrainTag
import com.vm.core.models.LearningTile
import com.vm.core.models.LearningTileStatus
import com.vm.core.models.MemoryNote
import com.vm.vector.VectorApplication
import com.vm.vector.data.BrainFileStore
import com.vm.vector.data.BrainRepository
import com.vm.vector.data.BrainSyncWorker
import com.vm.vector.data.GeminiLearningClient
import com.vm.vector.data.LearningContextBuilder
import com.vm.vector.data.LearningExporter
import com.vm.vector.data.LearningSettings
import com.vm.vector.data.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LinkedTile(val node: BrainNode, val status: LearningTileStatus)

data class TileLinks(
    /** "Depends on": tiles this one needs first. */
    val prerequisites: List<LinkedTile> = emptyList(),
    /** "Used in": tiles that depend on this one. */
    val dependents: List<LinkedTile> = emptyList(),
)

class LearnTileViewModel(
    application: Application,
    private val repository: BrainRepository,
    private val preferences: PreferenceManager,
    private val gemini: GeminiLearningClient,
    private val contextBuilder: LearningContextBuilder,
    private val exporter: LearningExporter,
    private val nodeId: String,
) : AndroidViewModel(application) {

    val node: StateFlow<BrainNode?> = repository.memoryTile(nodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tile: StateFlow<LearningTile?> = repository.learnTile(nodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sources: StateFlow<List<MemoryNote>> = repository.notesInNode(nodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tagLibrary: StateFlow<List<BrainTag>> = repository.tagLibrary(BrainSection.MODEL)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val learnNodes: StateFlow<List<BrainNode>> = repository.learnNodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val statuses: StateFlow<Map<String, LearningTileStatus>> = repository.learnTiles()
        .map { tiles -> tiles.associate { it.id to it.status } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val inboxNotes: StateFlow<List<MemoryNote>> = repository.inboxNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<LearningSettings> = preferences.learningSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, LearningSettings())

    val links: StateFlow<TileLinks> = combine(
        repository.learnNodes(),
        repository.learnTiles(),
        repository.learningLinks(),
    ) { nodes, tiles, links ->
        val nodesById = nodes.associateBy { it.id }
        val statusById = tiles.associate { it.id to it.status }
        fun linked(id: String) = nodesById[id]?.let { LinkedTile(it, statusById[id] ?: LearningTileStatus.BACKLOG) }
        TileLinks(
            prerequisites = links.filter { it.fromNodeId == nodeId }.mapNotNull { linked(it.toNodeId) }
                .sortedBy { it.node.name.lowercase() },
            dependents = links.filter { it.toNodeId == nodeId }.mapNotNull { linked(it.fromNodeId) }
                .sortedBy { it.node.name.lowercase() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TileLinks())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Label of the running Gemini or export job, null when idle. */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _linkSuggestions = MutableStateFlow<GeminiLearningClient.LinkSuggestions?>(null)
    val linkSuggestions: StateFlow<GeminiLearningClient.LinkSuggestions?> = _linkSuggestions.asStateFlow()

    private var pendingExport: LearningExporter.Export? = null

    fun clearMessage() {
        _message.value = null
    }

    // ---- Tile ----
    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renameTile(nodeId, name.trim()) }
    }

    fun addTag(name: String) {
        viewModelScope.launch {
            val libraryName = repository.ensureTag(name, BrainSection.MODEL) ?: return@launch
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

    fun deleteTile(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteLearnTile(nodeId)
            BrainSyncWorker.enqueue(getApplication())
            onDeleted()
        }
    }

    // ---- Status ----
    fun setStatus(status: LearningTileStatus) {
        viewModelScope.launch {
            repository.setLearnStatus(nodeId, status, settings.value)
            if (status == LearningTileStatus.LEARNED) {
                _message.value = settings.value.intervalDays(0)?.let { "Learned · first review in $it days" } ?: "Learned"
            }
        }
    }

    fun completeReview(solid: Boolean, score: Float? = null) {
        viewModelScope.launch {
            val step = (repository.getLearnTile(nodeId)?.reviewStep ?: 0) + 1
            repository.completeReview(nodeId, solid, score, settings.value)
            _message.value = when {
                !solid -> "Back to In Progress · the review schedule restarts"
                else -> settings.value.intervalDays(step)?.let { "Next review in $it days" } ?: "Mastered · no more reviews"
            }
        }
    }

    // ---- Sources ----
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
            val created = repository.createLearnTile(name.trim())
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

    // ---- Links ----
    /** [asPrerequisite]: this tile depends on the picked ones; otherwise the picked ones depend on this tile. */
    fun addLinks(otherIds: Collection<String>, asPrerequisite: Boolean) {
        if (otherIds.isEmpty()) return
        viewModelScope.launch {
            val refused = otherIds.count { !link(it, asPrerequisite) }
            if (refused > 0) {
                _message.value = if (refused == 1) {
                    "1 link skipped: it would make a loop"
                } else {
                    "$refused links skipped: they would make a loop"
                }
            }
        }
    }

    fun createLinkedTile(name: String, asPrerequisite: Boolean) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val created = repository.createLearnTile(name.trim())
            link(created.id, asPrerequisite)
            _message.value = "Created ${created.name} in Backlog"
        }
    }

    fun removeLink(otherId: String, asPrerequisite: Boolean) {
        viewModelScope.launch {
            if (asPrerequisite) repository.removeLearningLink(nodeId, otherId)
            else repository.removeLearningLink(otherId, nodeId)
        }
    }

    private suspend fun link(otherId: String, asPrerequisite: Boolean): Boolean =
        if (asPrerequisite) repository.addLearningLink(nodeId, otherId)
        else repository.addLearningLink(otherId, nodeId)

    // ---- AI ----
    fun suggestLinks() {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = "Finding links…"
            val current = links.value
            val existing = (current.prerequisites + current.dependents).map { it.node.id }.toSet()
            val result = runCatching {
                val learning = contextBuilder.build(nodeId)
                gemini.suggestLinks(learning, contextBuilder.candidateList(nodeId), existing).getOrThrow()
            }
            _busy.value = null
            result
                .onSuccess { suggestions ->
                    if (suggestions.links.isEmpty() && suggestions.missing.isEmpty()) {
                        _message.value = "No new links suggested"
                    } else {
                        _linkSuggestions.value = suggestions
                    }
                }
                .onFailure { _message.value = it.message ?: "Could not suggest links" }
        }
    }

    fun dismissSuggestions() {
        _linkSuggestions.value = null
    }

    fun acceptSuggestions(
        links: List<GeminiLearningClient.SuggestedLink>,
        missing: List<GeminiLearningClient.MissingPrerequisite>,
    ) {
        _linkSuggestions.value = null
        if (links.isEmpty() && missing.isEmpty()) return
        viewModelScope.launch {
            var added = 0
            for (suggestion in links) {
                if (link(suggestion.tileId, suggestion.direction == GeminiLearningClient.DEPENDS_ON)) added++
            }
            for (prerequisite in missing) {
                val created = repository.createLearnTile(prerequisite.name)
                if (repository.addLearningLink(nodeId, created.id)) added++
            }
            _message.value = buildString {
                append("Added $added link${if (added == 1) "" else "s"}")
                if (missing.isNotEmpty()) {
                    append(" · ${missing.size} new tile${if (missing.size == 1) "" else "s"} in Backlog")
                }
            }
        }
    }

    fun draftNotes(onCreated: (noteId: String) -> Unit) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = "Drafting notes…"
            val result = runCatching {
                val learning = contextBuilder.build(nodeId, includePdfs = true)
                gemini.draftNotes(learning).getOrThrow()
            }
            _busy.value = null
            result
                .onSuccess { draft ->
                    val note = repository.createTextNote(
                        title = "AI draft: ${draft.title}",
                        body = draft.markdown,
                        nodeId = nodeId,
                        description = "Generated by Gemini, verify",
                    )
                    onCreated(note.id)
                }
                .onFailure { _message.value = it.message ?: "Could not draft notes" }
        }
    }

    // ---- Export ----
    /** Builds the .txt and hands its file name to [onReady] (for the document picker). */
    fun prepareExport(includePrerequisites: Boolean, onReady: (fileName: String) -> Unit) {
        viewModelScope.launch {
            runCatching { exporter.build(nodeId, includePrerequisites) }
                .onSuccess {
                    pendingExport = it
                    onReady(it.fileName)
                }
                .onFailure { _message.value = it.message ?: "Export failed" }
        }
    }

    fun saveExport(uri: Uri?) {
        val export = pendingExport ?: return
        if (uri == null) return
        viewModelScope.launch {
            exporter.save(uri, export)
                .onSuccess { _message.value = "Saved ${export.fileName}" }
                .onFailure { _message.value = it.message ?: "Could not save the file" }
        }
    }

    fun shareExport(includePrerequisites: Boolean, onIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val export = exporter.build(nodeId, includePrerequisites)
                exporter.shareIntent(export).getOrThrow()
            }
                .onSuccess(onIntent)
                .onFailure { _message.value = it.message ?: "Could not share the file" }
        }
    }

    fun repository(): BrainRepository = repository
}

class LearnTileViewModelFactory(
    private val context: android.content.Context,
    private val nodeId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = context.applicationContext as VectorApplication
        val fileStore = BrainFileStore(app, app.database)
        val repo = BrainRepository(app.database, fileStore)
        val contextBuilder = LearningContextBuilder(repo)
        return LearnTileViewModel(
            application = app,
            repository = repo,
            preferences = PreferenceManager(app),
            gemini = GeminiLearningClient(app),
            contextBuilder = contextBuilder,
            exporter = LearningExporter(app, contextBuilder),
            nodeId = nodeId,
        ) as T
    }
}
