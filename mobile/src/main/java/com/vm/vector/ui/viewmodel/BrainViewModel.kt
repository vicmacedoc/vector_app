package com.vm.vector.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vm.core.models.BrainNode
import com.vm.core.models.BrainSection
import com.vm.core.models.LearningTile
import com.vm.core.models.LearningTileStatus
import com.vm.core.models.MemoryNoteKind
import com.vm.core.models.MotionScoring
import com.vm.core.models.SupermarketCycle
import com.vm.core.models.SupermarketMath
import com.vm.vector.VectorApplication
import com.vm.vector.data.BrainFileStore
import com.vm.vector.data.BrainRepository
import com.vm.vector.data.BrainSearchResult
import com.vm.vector.data.BrainSyncWorker
import com.vm.vector.data.GeminiApiKeyStore
import com.vm.vector.data.GeminiBrainClient
import com.vm.vector.data.LearningSettings
import com.vm.vector.data.MotionItemDraft
import com.vm.vector.data.MotionSettings
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.SupermarketRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import com.vm.vector.motion.MotionReminderScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Top-level tabs of the Brain screen. Inbox holds unfiled captures for every section. */
enum class BrainTab(val label: String) {
    MEMORY("Memory"),
    MODEL("Model"),
    MOTION("Motion"),
    INBOX("Inbox");

    /** Memory, Model and Inbox filter their own list with the search bar instead of running a global search. */
    val filtersLocally: Boolean get() = this == MEMORY || this == MODEL || this == INBOX

    /** Tabs with a tag filter button next to the search bar. */
    val hasTagFilter: Boolean get() = this == MEMORY || this == MODEL
}

data class MemoryTileSummary(
    val node: BrainNode,
    val counts: Map<MemoryNoteKind, Int>,
) {
    val total: Int get() = counts.values.sum()
}

data class LearnTileSummary(
    val node: BrainNode,
    val tile: LearningTile,
    val counts: Map<MemoryNoteKind, Int>,
    val prerequisites: Int,
    /** Prerequisites that are not Learned yet. */
    val pendingPrerequisites: Int,
) {
    val total: Int get() = counts.values.sum()
}

data class MotionListSummary(
    val node: BrainNode,
    val open: Int,
    val overdue: Int,
    val today: Int,
    val week: Int,
)

data class MotionScoreSummary(
    val today: Int = 0,
    val target: Int = MotionScoring.DEFAULT_DAILY_TARGET,
    val streak: Int = 0,
    /** Oldest first, ending today. */
    val lastSevenDays: List<Pair<LocalDate, Int>> = emptyList(),
)

data class SupermarketTileState(
    val cycle: SupermarketCycle,
    /** Points a trip logged today would earn (0 once this week's trip is logged). */
    val worthNow: Int,
    val lastTripPoints: Int?,
    val lastTripDate: LocalDate?,
)

data class BrainUiState(
    val tab: BrainTab = BrainTab.MEMORY,
    val searchQuery: String = "",
    val searchResults: BrainSearchResult? = null,
    val tagFilters: Map<BrainTab, Set<String>> = emptyMap(),
    val showCaptureSheet: Boolean = false,
    val showFileToSheet: Boolean = false,
    val pendingCaptureNoteId: String? = null,
    val selectedNoteId: String? = null,
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val snackbarMessage: String? = null,
    val inboxCount: Int = 0,
    val overdueCount: Int = 0,
    val dueTodayCount: Int = 0,
    val dueReviewCount: Int = 0,
) {
    val tagFilter: Set<String> get() = tagFilters[tab].orEmpty()
}

class BrainViewModel(
    application: Application,
    private val repository: BrainRepository,
    private val preferences: PreferenceManager,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BrainUiState())
    val uiState: StateFlow<BrainUiState> = _uiState.asStateFlow()

    val memoryTiles: StateFlow<List<MemoryTileSummary>> = combine(
        repository.memoryTiles(),
        repository.memoryKindCounts(),
    ) { nodes, counts ->
        val byNode = counts.groupBy { it.nodeId }
        nodes.map { node ->
            MemoryTileSummary(
                node = node,
                counts = byNode[node.id].orEmpty().associate { it.kind to it.count },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tagLibrary = repository.tagLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val learnTagLibrary = repository.tagLibrary(BrainSection.MODEL)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val inboxNotes = repository.inboxNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val learnTiles: StateFlow<List<LearnTileSummary>> = combine(
        repository.learnNodes(),
        repository.learnTiles(),
        repository.memoryKindCounts(),
        repository.learningLinks(),
    ) { nodes, tiles, counts, links ->
        val tileById = tiles.associateBy { it.id }
        val countsByNode = counts.groupBy { it.nodeId }
        val prerequisitesByNode = links.groupBy({ it.fromNodeId }, { it.toNodeId })
        nodes.map { node ->
            val prerequisites = prerequisitesByNode[node.id].orEmpty()
            LearnTileSummary(
                node = node,
                tile = tileById[node.id] ?: LearningTile(id = node.id, createdAt = node.createdAt),
                counts = countsByNode[node.id].orEmpty().associate { it.kind to it.count },
                prerequisites = prerequisites.size,
                pendingPrerequisites = prerequisites.count { tileById[it]?.status != LearningTileStatus.LEARNED },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val learningSettings: StateFlow<LearningSettings> = preferences.learningSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, LearningSettings())
    val openTasks = repository.openTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val inboxTodos = repository.inboxTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val motionSettings: StateFlow<MotionSettings> = preferences.motionSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, MotionSettings())

    val motionLists: StateFlow<List<MotionListSummary>> = combine(
        repository.motionLists(),
        repository.openTasks(),
    ) { nodes, tasks ->
        val (todayStart, todayEnd) = dayBounds()
        val weekEnd = todayStart + TimeUnit.DAYS.toMillis(7)
        val byNode = tasks.groupBy { it.nodeId }
        nodes.sortedBy { it.createdAt }.map { node ->
            val items = byNode[node.id].orEmpty()
            MotionListSummary(
                node = node,
                open = items.size,
                overdue = items.count { (it.dueAt ?: Long.MAX_VALUE) < todayStart },
                today = items.count { it.dueAt?.let { due -> due in todayStart until todayEnd } == true },
                week = items.count { it.dueAt?.let { due -> due in todayStart until weekEnd } == true },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val motionScore: StateFlow<MotionScoreSummary> = combine(
        repository.dailyPoints(LocalDate.now().minusDays(SCORE_HISTORY_DAYS)),
        motionSettings,
    ) { days, settings ->
        val today = LocalDate.now()
        val byDate = days.mapNotNull { day ->
            runCatching { LocalDate.parse(day.dateKey) }.getOrNull()?.let { it to day.points }
        }.toMap()
        MotionScoreSummary(
            today = byDate[today] ?: 0,
            target = settings.dailyTarget,
            streak = MotionScoring.streak(byDate, today, settings.dailyTarget),
            lastSevenDays = (6 downTo 0).map { back ->
                val day = today.minusDays(back.toLong())
                day to (byDate[day] ?: 0)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MotionScoreSummary())

    val supermarketTile: StateFlow<SupermarketTileState?> = combine(
        repository.lastSupermarketTrip(),
        motionSettings,
    ) { last, settings ->
        val today = LocalDate.now()
        val lastDue = last?.sourceId?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val cycle = MotionScoring.supermarketCycle(lastDue, today, settings.supermarketDay)
        SupermarketTileState(
            cycle = cycle,
            worthNow = if (cycle.open) MotionScoring.supermarketPoints(cycle.dueDate, today) else 0,
            lastTripPoints = last?.points,
            lastTripDate = last?.dateKey?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _supermarketToBuy = MutableStateFlow<Int?>(null)
    /** Items at or below half stock in the local Supermarket file; null until loaded or when missing. */
    val supermarketToBuy: StateFlow<Int?> = _supermarketToBuy.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                combine(repository.inboxCount(), repository.inboxTodoCount()) { notes, todos -> notes + todos },
                repository.countOverdue(),
                repository.countDueToday(),
                repository.countDueReviews()
            ) { inbox, overdue, today, reviews ->
                intArrayOf(inbox, overdue, today, reviews)
            }.collect { counts ->
                _uiState.value = _uiState.value.copy(
                    inboxCount = counts[0],
                    overdueCount = counts[1],
                    dueTodayCount = counts[2],
                    dueReviewCount = counts[3]
                )
            }
        }
        viewModelScope.launch { repository.refreshLearningStatuses(preferences.learningSettings.first()) }
    }

    private fun dayBounds(): Pair<Long, Long> {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return start to start + TimeUnit.DAYS.toMillis(1)
    }

    fun selectTab(tab: BrainTab) {
        if (tab == _uiState.value.tab) return
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(tab = tab, searchQuery = "", searchResults = null)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank() || _uiState.value.tab.filtersLocally) {
            _uiState.value = _uiState.value.copy(searchResults = null)
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            val results = repository.searchAll(query)
            _uiState.value = _uiState.value.copy(searchResults = results)
        }
    }

    /** Sets the tag filter of the current tab. */
    fun setTagFilter(tags: Set<String>) {
        val state = _uiState.value
        _uiState.value = state.copy(tagFilters = state.tagFilters + (state.tab to tags))
    }

    fun openCaptureSheet() {
        _uiState.value = _uiState.value.copy(showCaptureSheet = true)
    }

    fun closeCaptureSheet() {
        _uiState.value = _uiState.value.copy(showCaptureSheet = false)
    }

    fun openFileToSheet(noteId: String) {
        _uiState.value = _uiState.value.copy(
            showFileToSheet = true,
            pendingCaptureNoteId = noteId,
            showCaptureSheet = false
        )
    }

    fun closeFileToSheet() {
        _uiState.value = _uiState.value.copy(showFileToSheet = false, pendingCaptureNoteId = null)
    }

    fun filePendingNote(nodeId: String?) {
        val noteId = _uiState.value.pendingCaptureNoteId ?: return
        viewModelScope.launch {
            repository.moveNote(noteId, nodeId)
            closeFileToSheet()
            showSnackbar(if (nodeId == null) "Left in Inbox" else "Moved to memory")
        }
    }

    /** Creates a memory tile and files the pending inbox note into it. */
    fun filePendingNoteToNewTile(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val tile = repository.createMemoryTile(name)
            filePendingNote(tile.id)
        }
    }

    fun createMemoryTile(name: String, tags: List<String>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createMemoryTile(name, tags)
            showSnackbar("Memory created")
        }
    }

    fun deleteMemoryTile(id: String) {
        viewModelScope.launch {
            repository.deleteMemoryTile(id)
            BrainSyncWorker.enqueue(getApplication())
            showSnackbar("Memory deleted")
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
            BrainSyncWorker.enqueue(getApplication())
        }
    }

    fun createTextCapture(title: String, body: String) {
        viewModelScope.launch {
            repository.createTextNote(title, body, nodeId = null)
            showSnackbar("Saved to Inbox")
        }
    }

    fun createLinkCapture(url: String) {
        viewModelScope.launch {
            repository.createLinkNote(url, url, nodeId = null)
            showSnackbar("Saved to Inbox")
        }
    }

    fun importToInbox(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val (imported, anyVideo) = repository.importUrisAsNotes(uris, nodeId = null)
            if (imported > 0) BrainSyncWorker.enqueue(getApplication(), wifiOnly = anyVideo)
            showSnackbar(
                when {
                    imported == 0 -> "Import failed"
                    imported == 1 -> "Saved to Inbox"
                    else -> "Saved $imported items to Inbox"
                }
            )
        }
    }

    fun saveRecordingToInbox(relativePath: String) {
        viewModelScope.launch {
            runCatching { repository.createAudioNoteFromRecording(relativePath) }
                .onSuccess {
                    BrainSyncWorker.enqueue(getApplication())
                    showSnackbar("Voice note saved to Inbox")
                }
                .onFailure { showSnackbar(it.message ?: "Recording failed") }
        }
    }

    /** Quick-captured to-dos wait in the Inbox until a list merges them. */
    fun createTodoCapture(drafts: List<MotionItemDraft>) {
        val valid = drafts.filter { it.title.isNotBlank() }
        if (valid.isEmpty()) return
        viewModelScope.launch {
            valid.forEach { draft ->
                val task = repository.createTask(draft, nodeId = null)
                MotionReminderScheduler.schedule(getApplication(), task)
            }
            closeCaptureSheet()
            showSnackbar(if (valid.size == 1) "1 to-do in Inbox" else "${valid.size} to-dos in Inbox")
        }
    }

    fun createMotionList(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createMotionList(name)
            showSnackbar("List created")
        }
    }

    fun deleteMotionList(id: String) {
        viewModelScope.launch {
            repository.deleteMotionList(id)
            showSnackbar("List deleted")
        }
    }

    fun refreshSupermarketSummary() {
        viewModelScope.launch {
            val list = SupermarketRepository.create(getApplication()).loadLocal()
            _supermarketToBuy.value = list?.items?.count { SupermarketMath.needsBuy(it) }
        }
    }

    fun createLearnTile(name: String, tags: List<String>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createLearnTile(name.trim(), tags)
            showSnackbar("Tile created")
        }
    }

    fun setLearnStatus(tileId: String, status: LearningTileStatus) {
        viewModelScope.launch { repository.setLearnStatus(tileId, status, learningSettings.value) }
    }

    fun deleteLearnTile(tileId: String) {
        viewModelScope.launch {
            repository.deleteLearnTile(tileId)
            BrainSyncWorker.enqueue(getApplication())
            showSnackbar("Tile deleted")
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            MotionReminderScheduler.cancel(getApplication(), taskId)
            repository.deleteTask(taskId)
        }
    }

    fun repository(): BrainRepository = repository

    fun showSnackbar(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun setRecording(recording: Boolean) {
        _uiState.value = _uiState.value.copy(isRecording = recording)
    }

    fun openNoteDetail(noteId: String) {
        _uiState.value = _uiState.value.copy(selectedNoteId = noteId)
    }

    fun closeNoteDetail() {
        _uiState.value = _uiState.value.copy(selectedNoteId = null)
    }

    /** Optional Gemini transcription — never auto-runs after recording. */
    fun transcribeSelectedNote() {
        val noteId = _uiState.value.selectedNoteId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTranscribing = true)
            val note = repository.getNote(noteId)
            if (note == null || note.kind != MemoryNoteKind.AUDIO || note.assetId == null) {
                _uiState.value = _uiState.value.copy(isTranscribing = false)
                showSnackbar("Only voice notes can be transcribed")
                return@launch
            }
            val asset = repository.getAsset(note.assetId!!)
            if (asset == null) {
                _uiState.value = _uiState.value.copy(isTranscribing = false)
                showSnackbar("Audio file missing")
                return@launch
            }
            val file = repository.fileStore().absoluteFile(asset.localRelativePath)
            val client = GeminiBrainClient(getApplication())
            if (!client.isConfigured()) {
                _uiState.value = _uiState.value.copy(isTranscribing = false)
                showSnackbar(GeminiApiKeyStore.MISSING_KEY_MESSAGE)
                return@launch
            }
            client.transcribeAudio(file, asset.mimeType).fold(
                onSuccess = { result ->
                    repository.applyTranscription(
                        noteId = noteId,
                        title = result.title.ifBlank { note.title },
                        transcript = result.transcript,
                    )
                    _uiState.value = _uiState.value.copy(isTranscribing = false)
                    showSnackbar("Transcript saved")
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isTranscribing = false)
                    showSnackbar(e.message ?: "Transcription failed")
                },
            )
        }
    }
}

private const val SCORE_HISTORY_DAYS = 60L

class BrainViewModelFactory(
    private val context: android.content.Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = context.applicationContext as VectorApplication
        val fileStore = BrainFileStore(app, app.database)
        val repo = BrainRepository(app.database, fileStore)
        return BrainViewModel(app, repo, PreferenceManager(app)) as T
    }
}
