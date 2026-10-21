package com.vm.vector.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vm.core.models.AudioBookmark
import com.vm.vector.VectorApplication
import com.vm.vector.data.BrainFileStore
import com.vm.vector.data.BrainRepository
import com.vm.vector.data.GeminiApiKeyStore
import com.vm.vector.data.GeminiBrainClient
import com.vm.vector.data.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AudioPlayerUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val fileName: String = "",
    val description: String = "",
    val transcript: String = "",
    val noteId: String? = null,
    val assetId: String = "",
    val isTranscribing: Boolean = false,
    val isSummarizing: Boolean = false,
    val notesStatus: String? = null,
    val transcriptStatus: String? = null,
    /** Bumped when Gemini replaces notes, so the screen can refresh an open editor. */
    val notesRevision: Int = 0,
    /** Bumped when Gemini replaces the transcript. */
    val transcriptRevision: Int = 0,
    val bookmarks: List<AudioBookmark> = emptyList(),
    val seekBackSec: Int = PreferenceManager.DEFAULT_SEEK_BACK_SEC,
    val seekForwardSec: Int = PreferenceManager.DEFAULT_SEEK_FORWARD_SEC,
)

class AudioPlayerViewModel(
    application: Application,
    private val repository: BrainRepository,
    private val preferences: PreferenceManager,
    private val assetId: String,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AudioPlayerUiState(assetId = assetId))
    val uiState: StateFlow<AudioPlayerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val asset = repository.getAsset(assetId)
            val note = repository.notesForAsset(assetId).firstOrNull()
            val title = note?.title?.takeIf { it.isNotBlank() }
                ?: asset?.fileName?.takeIf { it.isNotBlank() }
                ?: "Voice note"
            _uiState.update {
                it.copy(
                    isLoading = false,
                    title = title,
                    fileName = asset?.fileName.orEmpty(),
                    description = asset?.description.orEmpty(),
                    transcript = note?.body.orEmpty(),
                    noteId = note?.id,
                )
            }
        }
        viewModelScope.launch {
            repository.audioProgress(assetId).collect { progress ->
                _uiState.update { state ->
                    state.copy(bookmarks = progress?.bookmarks.orEmpty())
                }
            }
        }
        viewModelScope.launch {
            preferences.audioSeekBackSec.collect { seconds ->
                _uiState.update { it.copy(seekBackSec = seconds) }
            }
        }
        viewModelScope.launch {
            preferences.audioSeekForwardSec.collect { seconds ->
                _uiState.update { it.copy(seekForwardSec = seconds) }
            }
        }
    }

    fun commitTitle(title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank() || trimmed == _uiState.value.title) return
        _uiState.update { it.copy(title = trimmed) }
        viewModelScope.launch { repository.renameRecording(assetId, trimmed) }
    }

    fun commitDescription(description: String) {
        val trimmed = description.trim()
        if (trimmed == _uiState.value.description) return
        _uiState.update { it.copy(description = trimmed, notesStatus = null) }
        viewModelScope.launch { repository.updateRecordingDescription(assetId, trimmed) }
    }

    fun commitTranscript(body: String) {
        val noteId = _uiState.value.noteId ?: return
        if (body == _uiState.value.transcript) return
        _uiState.update { it.copy(transcript = body, transcriptStatus = null) }
        viewModelScope.launch { repository.updateNoteBody(noteId, body) }
    }

    fun transcribe() {
        val state = _uiState.value
        if (state.isTranscribing || state.isSummarizing) return
        val noteId = state.noteId
        if (noteId == null) {
            _uiState.update { it.copy(transcriptStatus = "This recording has no memory note") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isTranscribing = true, transcriptStatus = null) }
            val asset = repository.getAsset(assetId)
            if (asset == null) {
                _uiState.update { it.copy(isTranscribing = false, transcriptStatus = "Audio file missing") }
                return@launch
            }
            val client = GeminiBrainClient(getApplication())
            if (!client.isConfigured()) {
                _uiState.update {
                    it.copy(isTranscribing = false, transcriptStatus = GeminiApiKeyStore.MISSING_KEY_MESSAGE)
                }
                return@launch
            }
            val file = repository.fileStore().absoluteFile(asset.localRelativePath)
            client.transcribeAudio(file, asset.mimeType).fold(
                onSuccess = { result ->
                    val note = repository.getNote(noteId)
                    repository.applyTranscription(
                        noteId = noteId,
                        title = result.title.ifBlank { note?.title.orEmpty() },
                        transcript = result.transcript,
                    )
                    val updated = repository.getNote(noteId)
                    _uiState.update {
                        it.copy(
                            isTranscribing = false,
                            transcript = result.transcript,
                            title = updated?.title?.takeIf { title -> title.isNotBlank() } ?: it.title,
                            transcriptRevision = it.transcriptRevision + 1,
                            transcriptStatus = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isTranscribing = false,
                            transcriptStatus = error.message ?: "Transcription failed",
                        )
                    }
                },
            )
        }
    }

    fun summarize() {
        val state = _uiState.value
        if (state.isSummarizing || state.isTranscribing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSummarizing = true, notesStatus = null) }
            val client = GeminiBrainClient(getApplication())
            if (!client.isConfigured()) {
                _uiState.update {
                    it.copy(isSummarizing = false, notesStatus = GeminiApiKeyStore.MISSING_KEY_MESSAGE)
                }
                return@launch
            }
            val transcript = state.transcript.trim()
            val result = if (transcript.isNotBlank()) {
                client.summarizeText(transcript)
            } else {
                val asset = repository.getAsset(assetId)
                if (asset == null) {
                    _uiState.update { it.copy(isSummarizing = false, notesStatus = "Audio file missing") }
                    return@launch
                }
                val file = repository.fileStore().absoluteFile(asset.localRelativePath)
                client.summarizeAudio(file, asset.mimeType)
            }
            result.fold(
                onSuccess = { summary ->
                    repository.updateRecordingDescription(assetId, summary)
                    _uiState.update {
                        it.copy(
                            isSummarizing = false,
                            description = summary,
                            notesRevision = it.notesRevision + 1,
                            notesStatus = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isSummarizing = false, notesStatus = error.message ?: "Summary failed")
                    }
                },
            )
        }
    }

    fun setSeekBack(seconds: Int) {
        viewModelScope.launch { preferences.saveAudioSeekBackSec(seconds) }
    }

    fun setSeekForward(seconds: Int) {
        viewModelScope.launch { preferences.saveAudioSeekForwardSec(seconds) }
    }
}

class AudioPlayerViewModelFactory(
    private val context: android.content.Context,
    private val assetId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = context.applicationContext as VectorApplication
        val fileStore = BrainFileStore(app, app.database)
        val repo = BrainRepository(app.database, fileStore)
        val preferences = PreferenceManager(app)
        return AudioPlayerViewModel(app, repo, preferences, assetId) as T
    }
}
