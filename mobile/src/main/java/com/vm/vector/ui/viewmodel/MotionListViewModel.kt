package com.vm.vector.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vm.core.models.BrainNode
import com.vm.core.models.MotionTask
import com.vm.core.models.MotionTaskStatus
import com.vm.vector.VectorApplication
import com.vm.vector.data.BrainFileStore
import com.vm.vector.data.BrainRepository
import com.vm.vector.data.GeminiApiKeyStore
import com.vm.vector.data.GeminiMotionClient
import com.vm.vector.data.MotionItemDraft
import com.vm.vector.data.withDraft
import com.vm.vector.motion.MotionReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MotionListViewModel(
    application: Application,
    private val repository: BrainRepository,
    private val nodeId: String,
) : AndroidViewModel(application) {

    val list: StateFlow<BrainNode?> = repository.memoryTile(nodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items: StateFlow<List<MotionTask>> = repository.listItems(nodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val inboxTodos: StateFlow<List<MotionTask>> = repository.inboxTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()

    /** Gemini suggestions waiting for the user to pick which ones to add. */
    private val _aiDrafts = MutableStateFlow<List<MotionItemDraft>?>(null)
    val aiDrafts: StateFlow<List<MotionItemDraft>?> = _aiDrafts.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renameTile(nodeId, name.trim()) }
    }

    fun addItems(drafts: List<MotionItemDraft>) {
        val valid = drafts.filter { it.title.isNotBlank() }
        if (valid.isEmpty()) return
        viewModelScope.launch {
            valid.forEach { draft ->
                val task = repository.createTask(draft, nodeId)
                MotionReminderScheduler.schedule(getApplication(), task)
            }
            if (valid.size > 1) _message.value = "Added ${valid.size} items"
        }
    }

    fun updateItem(task: MotionTask, draft: MotionItemDraft) {
        viewModelScope.launch {
            repository.saveTask(task.withDraft(draft))
            repository.getTask(task.id)?.let { MotionReminderScheduler.schedule(getApplication(), it) }
        }
    }

    fun deleteItem(taskId: String) {
        viewModelScope.launch {
            MotionReminderScheduler.cancel(getApplication(), taskId)
            repository.deleteTask(taskId)
        }
    }

    fun toggle(task: MotionTask) {
        viewModelScope.launch {
            if (task.status == MotionTaskStatus.DONE) {
                repository.uncheckTask(task.id)
                repository.getTask(task.id)?.let { MotionReminderScheduler.schedule(getApplication(), it) }
            } else {
                MotionReminderScheduler.cancel(getApplication(), task.id)
                val points = repository.checkTask(task.id)
                if (points != null) _message.value = "+$points pts"
            }
        }
    }

    fun setProgress(task: MotionTask, progress: Double) {
        viewModelScope.launch {
            val wasOpen = task.status == MotionTaskStatus.OPEN
            repository.setProgress(task.id, progress)
            val updated = repository.getTask(task.id) ?: return@launch
            if (wasOpen && updated.status == MotionTaskStatus.DONE) {
                MotionReminderScheduler.cancel(getApplication(), task.id)
                updated.earnedPoints?.let { _message.value = "+$it pts" }
            }
        }
    }

    fun snooze(taskId: String, until: Long) {
        viewModelScope.launch {
            repository.snoozeTask(taskId, until)
            repository.getTask(taskId)?.let { MotionReminderScheduler.schedule(getApplication(), it) }
        }
    }

    /** Files the picked Inbox to-dos (keyed by task id) into this list, keeping edits made in the picker. */
    fun mergeTodos(drafts: List<MotionItemDraft>) {
        if (drafts.isEmpty()) return
        viewModelScope.launch {
            drafts.forEach { draft ->
                val task = repository.getTask(draft.key) ?: return@forEach
                if (MotionItemDraft.from(task) != draft) repository.saveTask(task.withDraft(draft))
            }
            repository.mergeTodos(drafts.map { it.key }, nodeId)
            _message.value = if (drafts.size == 1) "Merged 1 to-do" else "Merged ${drafts.size} to-dos"
        }
    }

    fun parseWithAi(text: String) {
        if (text.isBlank()) return
        val client = GeminiMotionClient(getApplication())
        if (!client.isConfigured()) {
            _message.value = GeminiApiKeyStore.MISSING_KEY_MESSAGE
            return
        }
        viewModelScope.launch {
            _aiBusy.value = true
            client.parseTodos(text, list.value?.name.orEmpty()).fold(
                onSuccess = { drafts ->
                    if (drafts.isEmpty()) _message.value = "No to-dos found in that text"
                    else _aiDrafts.value = drafts
                },
                onFailure = { _message.value = it.message ?: "Gemini request failed" },
            )
            _aiBusy.value = false
        }
    }

    fun clearAiDrafts() {
        _aiDrafts.value = null
    }

    fun deleteList(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteMotionList(nodeId)
            onDeleted()
        }
    }
}

class MotionListViewModelFactory(
    private val context: android.content.Context,
    private val nodeId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = context.applicationContext as VectorApplication
        val repo = BrainRepository(app.database, BrainFileStore(app, app.database))
        return MotionListViewModel(app, repo, nodeId) as T
    }
}
