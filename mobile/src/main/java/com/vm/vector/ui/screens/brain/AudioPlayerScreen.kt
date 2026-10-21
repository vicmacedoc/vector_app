@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.vm.vector.ui.screens.brain

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.vm.core.models.AudioBookmark
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.core.ui.theme.VectorTheme
import com.vm.vector.audio.AudioPlayback
import com.vm.vector.audio.BrainPlayerController
import com.vm.vector.data.PreferenceManager
import com.vm.vector.ui.viewmodel.AudioPlayerViewModel
import com.vm.vector.ui.viewmodel.AudioPlayerViewModelFactory
import kotlin.math.abs

private enum class SeekTarget { Back, Forward }

@Composable
fun AudioPlayerScreen(
    assetId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val viewModel: AudioPlayerViewModel = viewModel(
        factory = AudioPlayerViewModelFactory(context, assetId),
    )
    val uiState by viewModel.uiState.collectAsState()
    val playerState by BrainPlayerController.state.collectAsState()

    LaunchedEffect(assetId) {
        BrainPlayerController.open(context, assetId)
    }

    var titleText by remember(assetId) { mutableStateOf("") }
    var titleSynced by remember(assetId) { mutableStateOf("") }
    var descriptionText by remember(assetId) { mutableStateOf("") }
    var transcriptDraft by remember(assetId) { mutableStateOf("") }
    var editingNotes by remember(assetId) { mutableStateOf(false) }
    var editingTranscript by remember(assetId) { mutableStateOf(false) }
    var fieldsReady by remember(assetId) { mutableStateOf(false) }
    var exportError by remember(assetId) { mutableStateOf<String?>(null) }
    var pendingExport by remember(assetId) { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = pendingExport
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("Could not create the file")
        }.onFailure { error ->
            exportError = error.message ?: "Could not export the transcript"
        }
    }
    LaunchedEffect(uiState.isLoading, uiState.title, uiState.fileName) {
        if (uiState.isLoading) return@LaunchedEffect
        val name = stripAudioExtension(uiState.title.ifBlank { uiState.fileName })
        if (!fieldsReady) {
            titleText = name
            titleSynced = name
            descriptionText = uiState.description
            transcriptDraft = uiState.transcript
            fieldsReady = true
            if (name.isNotBlank()) BrainPlayerController.setDisplayTitle(name)
        } else if (titleText == titleSynced && name != titleText) {
            titleText = name
            titleSynced = name
            if (name.isNotBlank()) BrainPlayerController.setDisplayTitle(name)
        }
    }
    LaunchedEffect(uiState.notesRevision) {
        if (uiState.notesRevision == 0 || !fieldsReady) return@LaunchedEffect
        descriptionText = uiState.description
        editingNotes = false
    }
    LaunchedEffect(uiState.transcriptRevision) {
        if (uiState.transcriptRevision == 0 || !fieldsReady) return@LaunchedEffect
        transcriptDraft = uiState.transcript
        editingTranscript = false
    }
    val latestTitle = rememberUpdatedState(titleText)
    val latestDescription = rememberUpdatedState(descriptionText)
    val latestTranscript = rememberUpdatedState(transcriptDraft)
    val latestReady = rememberUpdatedState(fieldsReady)
    DisposableEffect(assetId) {
        onDispose {
            BrainPlayerController.saveCheckpoint(context)
            if (latestReady.value) {
                viewModel.commitTitle(latestTitle.value)
                viewModel.commitDescription(latestDescription.value)
                viewModel.commitTranscript(latestTranscript.value)
            }
        }
    }

    val durationMs = playerState.durationMs
    var scrubPosition by remember(assetId) { mutableFloatStateOf(0f) }
    var isScrubbing by remember(assetId) { mutableStateOf(false) }
    var pendingSeekMs by remember(assetId) { mutableLongStateOf(-1L) }
    LaunchedEffect(playerState.positionMs, pendingSeekMs) {
        if (pendingSeekMs >= 0L && abs(playerState.positionMs - pendingSeekMs) < 800L) {
            pendingSeekMs = -1L
        }
    }
    val shownPosition = when {
        isScrubbing && durationMs > 0L -> (scrubPosition * durationMs).toLong()
        pendingSeekMs >= 0L -> pendingSeekMs
        else -> playerState.positionMs
    }
    val sliderValue = when {
        durationMs <= 0L -> 0f
        isScrubbing -> scrubPosition
        else -> (shownPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }
    val remainingMs = (durationMs - shownPosition).coerceAtLeast(0L)

    var seekTarget by remember { mutableStateOf<SeekTarget?>(null) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showSleepPicker by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val sleepDeadline = playerState.sleepDeadlineEpochMs
    LaunchedEffect(sleepDeadline) {
        if (sleepDeadline == null) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkLabel by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = PureBlack,
        unfocusedTextColor = PureBlack,
        focusedBorderColor = NavyDeep,
        unfocusedBorderColor = PureBlack.copy(alpha = 0.25f),
        focusedLabelColor = NavyDeep,
        unfocusedLabelColor = SlateGray,
        cursorColor = NavyDeep,
    )

    VectorTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PureWhite)
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NavyDeep,
                    )
                }
                UnderlinedEditable(
                    value = titleText,
                    onValueChange = { value ->
                        titleText = value
                        if (value.isNotBlank()) BrainPlayerController.setDisplayTitle(value)
                    },
                    onCommit = { viewModel.commitTitle(titleText) },
                    placeholder = "Untitled",
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = PureBlack),
                    singleLine = true,
                    enabled = fieldsReady,
                    modifier = Modifier.weight(1f),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Bookmarks",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureBlack,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { showBookmarkDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add bookmark", tint = NavyDeep)
                        }
                    }
                }
                if (uiState.bookmarks.isEmpty()) {
                    item {
                        Text(
                            text = "No bookmarks yet",
                            color = SlateGray,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(uiState.bookmarks, key = { it.id }) { bookmark ->
                    BookmarkRow(
                        bookmark = bookmark,
                        onSeek = { BrainPlayerController.seekTo(context, bookmark.positionMs) },
                        onDelete = { BrainPlayerController.deleteBookmark(context, bookmark.id) },
                    )
                }
                item {
                    NotesSession(
                        text = descriptionText,
                        editing = editingNotes,
                        busy = uiState.isSummarizing,
                        status = uiState.notesStatus,
                        enabled = fieldsReady,
                        onEditToggle = {
                            if (editingNotes) {
                                viewModel.commitDescription(descriptionText)
                                editingNotes = false
                            } else {
                                editingNotes = true
                            }
                        },
                        onTextChange = { descriptionText = it },
                        onSummarize = {
                            if (editingTranscript) viewModel.commitTranscript(transcriptDraft)
                            viewModel.summarize()
                        },
                    )
                }
                item {
                    TranscriptSession(
                        text = transcriptDraft,
                        editing = editingTranscript,
                        busy = uiState.isTranscribing,
                        status = exportError ?: uiState.transcriptStatus,
                        enabled = fieldsReady,
                        onEditToggle = {
                            if (editingTranscript) {
                                viewModel.commitTranscript(transcriptDraft)
                                editingTranscript = false
                            } else {
                                editingTranscript = true
                            }
                        },
                        onTextChange = { transcriptDraft = it },
                        onTranscribe = { viewModel.transcribe() },
                        onExport = {
                            val text = transcriptDraft.trim()
                            if (text.isBlank()) return@TranscriptSession
                            viewModel.commitTranscript(text)
                            exportError = null
                            pendingExport = text
                            val base = stripAudioExtension(titleText).ifBlank { "transcript" }
                                .replace(Regex("""[\\/:*?"<>|]"""), " ")
                                .replace(Regex("\\s+"), " ")
                                .trim()
                                .take(80)
                                .ifBlank { "transcript" }
                            exportLauncher.launch("$base.txt")
                        },
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureBlack)
                    .padding(top = 8.dp, bottom = 12.dp),
            ) {
                PlaybackBar(
                    value = sliderValue,
                    enabled = durationMs > 0L,
                    onValueChange = { value ->
                        if (durationMs <= 0L) return@PlaybackBar
                        isScrubbing = true
                        scrubPosition = value
                    },
                    onValueChangeFinished = {
                        if (durationMs <= 0L) {
                            isScrubbing = false
                            return@PlaybackBar
                        }
                        val target = (scrubPosition * durationMs).toLong()
                        pendingSeekMs = target
                        isScrubbing = false
                        BrainPlayerController.seekTo(context, target)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = AudioPlayback.formatTime(shownPosition),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = PureWhite,
                    )
                    Text(
                        text = "−${AudioPlayback.formatTime(remainingMs)} left",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = PureWhite.copy(alpha = 0.6f),
                    )
                    Text(
                        text = AudioPlayback.formatTime(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = PureWhite,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkipButton(
                        label = AudioPlayback.speedLabel(playerState.speed),
                        onClick = { showSpeedPicker = true },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSpeedPicker = true
                        },
                    )
                    SkipButton(
                        label = "−${uiState.seekBackSec}s",
                        onClick = { BrainPlayerController.seekBy(context, -uiState.seekBackSec * 1000L) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            seekTarget = SeekTarget.Back
                        },
                    )
                    IconButton(
                        onClick = { BrainPlayerController.togglePlayPause(context) },
                        modifier = Modifier
                            .size(64.dp)
                            .background(PureWhite, CircleShape),
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            tint = PureBlack,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    SkipButton(
                        label = "+${uiState.seekForwardSec}s",
                        onClick = { BrainPlayerController.seekBy(context, uiState.seekForwardSec * 1000L) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            seekTarget = SeekTarget.Forward
                        },
                    )
                    SleepTimerButton(
                        deadlineEpochMs = sleepDeadline,
                        untilEnd = playerState.sleepUntilEnd,
                        nowMs = nowMs,
                        onClick = { showSleepPicker = true },
                    )
                }
            }
        }
    }

    val picking = seekTarget
    if (picking != null) {
        val current = if (picking == SeekTarget.Back) uiState.seekBackSec else uiState.seekForwardSec
        AlertDialog(
            onDismissRequest = { seekTarget = null },
            title = {
                Text(
                    if (picking == SeekTarget.Back) "Skip backward" else "Skip forward",
                    color = PureBlack,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceManager.AUDIO_SEEK_CHOICES.sorted().chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { seconds ->
                                val selectedChoice = seconds == current
                                Button(
                                    onClick = {
                                        if (picking == SeekTarget.Back) viewModel.setSeekBack(seconds)
                                        else viewModel.setSeekForward(seconds)
                                        seekTarget = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedChoice) NavyDeep else OffWhite,
                                        contentColor = if (selectedChoice) PureWhite else NavyDeep,
                                    ),
                                ) {
                                    Text("${seconds}s")
                                }
                            }
                            repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { seekTarget = null }) {
                    Text("Close", color = PureBlack)
                }
            },
            containerColor = PureWhite,
        )
    }

    if (showSpeedPicker) {
        val selected = AudioPlayback.nearestSpeed(playerState.speed)
        ChoiceDialog(
            title = "Playback speed",
            onDismiss = { showSpeedPicker = false },
        ) {
            AudioPlayback.SPEEDS.chunked(3).forEach { row ->
                ChoiceRow(row = row, spacerCount = 3 - row.size) { speed ->
                    val on = abs(speed - selected) < 0.01f
                    ChoiceButton(
                        label = AudioPlayback.speedLabel(speed),
                        selected = on,
                        onClick = {
                            BrainPlayerController.setSpeed(context, speed)
                            showSpeedPicker = false
                        },
                    )
                }
            }
        }
    }

    if (showSleepPicker) {
        val activeMinutes = playerState.sleepMinutes
        ChoiceDialog(
            title = "Sleep timer",
            onDismiss = { showSleepPicker = false },
        ) {
            val minuteChoices = listOf(5, 10, 15, 30, 45, 60)
            minuteChoices.chunked(3).forEach { row ->
                ChoiceRow(row = row, spacerCount = 3 - row.size) { minutes ->
                    ChoiceButton(
                        label = "${minutes}m",
                        selected = activeMinutes == minutes && !playerState.sleepUntilEnd,
                        onClick = {
                            BrainPlayerController.setSleepMinutes(minutes)
                            showSleepPicker = false
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChoiceButton(
                    label = "End",
                    selected = playerState.sleepUntilEnd,
                    onClick = {
                        BrainPlayerController.setSleepUntilEnd()
                        showSleepPicker = false
                    },
                    modifier = Modifier.weight(1f),
                )
                ChoiceButton(
                    label = "Off",
                    selected = sleepDeadline == null && !playerState.sleepUntilEnd,
                    onClick = {
                        BrainPlayerController.clearSleepTimer()
                        showSleepPicker = false
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

    if (showBookmarkDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            title = { Text("Bookmark", color = PureBlack) },
            text = {
                OutlinedTextField(
                    value = bookmarkLabel,
                    onValueChange = { bookmarkLabel = it },
                    label = { Text("Label") },
                    singleLine = true,
                    colors = fieldColors,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        BrainPlayerController.addBookmark(context, bookmarkLabel.trim())
                        bookmarkLabel = ""
                        showBookmarkDialog = false
                    },
                ) {
                    Text("Save", color = NavyDeep)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarkDialog = false }) {
                    Text("Cancel", color = PureBlack)
                }
            },
            containerColor = PureWhite,
        )
    }
}

@Composable
private fun PlaybackBar(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = value.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    fun fractionAt(x: Float) = (x / width).coerceIn(0f, 1f)
                    onValueChange(fractionAt(down.position.x))
                    drag(down.id) { change ->
                        onValueChange(fractionAt(change.position.x))
                        change.consume()
                    }
                    onValueChangeFinished()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PureWhite.copy(alpha = 0.25f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (enabled) PureWhite else PureWhite.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun NotesSession(
    text: String,
    editing: Boolean,
    busy: Boolean,
    status: String?,
    enabled: Boolean,
    onEditToggle: () -> Unit,
    onTextChange: (String) -> Unit,
    onSummarize: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        SessionHeader(
            title = "Notes",
            editing = editing,
            enabled = enabled,
            onEditToggle = onEditToggle,
            trailing = {
                AiButton(
                    busy = busy,
                    enabled = enabled,
                    contentDescription = "Summarize with Gemini",
                    onClick = onSummarize,
                )
            },
        )
        SessionText(
            text = text,
            editing = editing,
            placeholder = "Notes",
            onTextChange = onTextChange,
        )
        if (!status.isNullOrBlank()) {
            Text(
                text = status,
                color = PureBlack.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun TranscriptSession(
    text: String,
    editing: Boolean,
    busy: Boolean,
    status: String?,
    enabled: Boolean,
    onEditToggle: () -> Unit,
    onTextChange: (String) -> Unit,
    onTranscribe: () -> Unit,
    onExport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        SessionHeader(
            title = "Transcript",
            editing = editing,
            enabled = enabled,
            onEditToggle = onEditToggle,
            trailing = {
                if (text.isNotBlank()) {
                    IconButton(onClick = onExport, enabled = enabled && !busy) {
                        Icon(
                            Icons.Outlined.FileDownload,
                            contentDescription = "Export as text",
                            tint = NavyDeep,
                        )
                    }
                }
                AiButton(
                    busy = busy,
                    enabled = enabled,
                    contentDescription = "Transcribe with Gemini",
                    onClick = onTranscribe,
                )
            },
        )
        SessionText(
            text = text,
            editing = editing,
            placeholder = if (busy) "Transcribing…" else "No transcript yet",
            onTextChange = onTextChange,
        )
        if (!status.isNullOrBlank()) {
            Text(
                text = status,
                color = PureBlack.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SessionHeader(
    title: String,
    editing: Boolean,
    enabled: Boolean,
    onEditToggle: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = PureBlack,
            modifier = Modifier.weight(1f),
        )
        trailing()
        IconButton(onClick = onEditToggle, enabled = enabled) {
            Icon(
                imageVector = if (editing) Icons.Outlined.Check else Icons.Outlined.Edit,
                contentDescription = if (editing) "Done editing" else "Edit",
                tint = NavyDeep,
            )
        }
    }
}

@Composable
private fun SessionText(
    text: String,
    editing: Boolean,
    placeholder: String,
    onTextChange: (String) -> Unit,
) {
    if (editing) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp),
            placeholder = { Text(placeholder, color = SlateGray) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = PureBlack),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = PureBlack,
                unfocusedTextColor = PureBlack,
                focusedBorderColor = NavyDeep,
                unfocusedBorderColor = PureBlack.copy(alpha = 0.25f),
                cursorColor = NavyDeep,
                focusedContainerColor = PureWhite,
                unfocusedContainerColor = PureWhite,
            ),
        )
    } else {
        Text(
            text = text.ifBlank { placeholder },
            color = if (text.isBlank()) SlateGray else PureBlack,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun AiButton(
    busy: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    if (busy) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = NavyDeep,
                strokeWidth = 2.dp,
            )
        }
    } else {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = contentDescription,
                tint = if (enabled) NavyDeep else IconGray,
            )
        }
    }
}

@Composable
private fun UnderlinedEditable(
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    singleLine: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (editing) {
        var gainedFocus by remember { mutableStateOf(false) }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(NavyDeep),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onCommit()
                    keyboard?.hide()
                    editing = false
                },
            ),
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged { focus ->
                    if (focus.isFocused) gainedFocus = true
                    else if (gainedFocus) {
                        onCommit()
                        editing = false
                    }
                }
                .drawBehind {
                    val y = size.height - 1.dp.toPx()
                    drawLine(NavyDeep, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                .padding(bottom = 4.dp),
        )
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    } else {
        Text(
            text = value.ifBlank { placeholder },
            style = textStyle,
            color = if (value.isBlank()) SlateGray else textStyle.color,
            textDecoration = TextDecoration.Underline,
            maxLines = if (singleLine) 1 else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.clickable(enabled = enabled) { editing = true },
        )
    }
}

@Composable
private fun SleepTimerButton(
    deadlineEpochMs: Long?,
    untilEnd: Boolean,
    nowMs: Long,
    onClick: () -> Unit,
) {
    val label = when {
        deadlineEpochMs != null -> AudioPlayback.formatTime((deadlineEpochMs - nowMs).coerceAtLeast(0L))
        untilEnd -> "End"
        else -> null
    }
    if (label == null) {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = "Sleep timer",
                tint = PureWhite,
            )
        }
    } else {
        Text(
            text = label,
            color = PureWhite,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = PureBlack) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = { content() }) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = PureBlack) }
        },
        containerColor = PureWhite,
    )
}

@Composable
private fun <T> ChoiceRow(
    row: List<T>,
    spacerCount: Int,
    item: @Composable (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        row.forEach { value ->
            BoxWeight { item(value) }
        }
        repeat(spacerCount) { Spacer(modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun RowScope.BoxWeight(content: @Composable () -> Unit) {
    Column(modifier = Modifier.weight(1f)) { content() }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) NavyDeep else OffWhite,
            contentColor = if (selected) PureWhite else NavyDeep,
        ),
    ) {
        Text(label, maxLines = 1)
    }
}

@Composable
private fun SkipButton(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Text(
        text = label,
        color = PureWhite,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

@Composable
private fun BookmarkRow(
    bookmark: AudioBookmark,
    onSeek: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = onSeek)
                .padding(vertical = 6.dp),
        ) {
            val time = AudioPlayback.formatTime(bookmark.positionMs)
            val title = bookmark.label.ifBlank { time }
            Text(
                text = title,
                color = PureBlack,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (title != time) {
                Text(
                    text = time,
                    color = NavyDeep,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Remove bookmark", tint = SlateGray)
        }
    }
}

private val AUDIO_EXTENSIONS = listOf(".m4a", ".mp3", ".mp4", ".aac", ".wav", ".ogg", ".m4b")

private fun stripAudioExtension(name: String): String {
    val trimmed = name.trim()
    val lower = trimmed.lowercase()
    val ext = AUDIO_EXTENSIONS.firstOrNull { lower.endsWith(it) } ?: return trimmed
    return trimmed.dropLast(ext.length).ifBlank { trimmed }
}
