@file:OptIn(ExperimentalFoundationApi::class)

package com.vm.vector.ui.screens.brain

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.CheckBox
import com.vm.core.models.MemoryNote
import com.vm.core.models.MemoryNoteKind
import com.vm.core.models.MotionPriority
import com.vm.core.models.MotionTask
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.ui.util.OpenWithLauncher
import com.vm.vector.ui.viewmodel.BrainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun InboxSection(
    viewModel: BrainViewModel,
    onOpenPlayer: (assetId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val notes by viewModel.inboxNotes.collectAsState()
    val todos by viewModel.inboxTodos.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<MemoryNote?>(null) }
    var pendingDeleteTodo by remember { mutableStateOf<MotionTask?>(null) }

    val query = uiState.searchQuery.trim()
    val shown = notes.filter {
        query.isBlank() || it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
    }
    val shownTodos = todos.filter {
        query.isBlank() || it.title.contains(query, ignoreCase = true) || it.notes.contains(query, ignoreCase = true)
    }
    val selectedNote = uiState.selectedNoteId?.let { id -> notes.find { it.id == id } }

    Column(modifier = modifier.fillMaxSize()) {
        when {
            notes.isEmpty() && todos.isEmpty() -> BrainEmptyState(
                title = "Inbox zero",
                message = "Quick captures, voice notes and shares land here until you file them.",
            )
            shown.isEmpty() && shownTodos.isEmpty() -> BrainEmptyState(
                title = "Nothing matches",
                message = "No inbox item has that title.",
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (shownTodos.isNotEmpty()) {
                    item(key = "todos_header") {
                        Column {
                            Text(
                                "To-dos (${shownTodos.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PureBlack,
                            )
                            Text(
                                "Open a list in Motion and tap + → Merge to-dos to file them.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateGray,
                            )
                        }
                    }
                    items(shownTodos, key = { it.id }) { todo ->
                        InboxTodoCard(todo = todo, onLongClick = { pendingDeleteTodo = todo })
                    }
                    if (shown.isNotEmpty()) {
                        item(key = "notes_header") {
                            Text(
                                "Notes (${shown.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PureBlack,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                items(shown, key = { it.id }) { note ->
                    val assetId = note.assetId
                    InboxCard(
                        note = note,
                        onClick = {
                            when {
                                note.kind == MemoryNoteKind.AUDIO && assetId != null -> onOpenPlayer(assetId)
                                note.kind == MemoryNoteKind.TEXT || note.kind == MemoryNoteKind.LINK ->
                                    viewModel.openNoteDetail(note.id)
                                assetId != null -> scope.launch {
                                    val repo = viewModel.repository()
                                    val asset = repo.getAsset(assetId) ?: return@launch
                                    OpenWithLauncher.openLocalFile(
                                        context,
                                        repo.fileStore().absoluteFile(asset.localRelativePath),
                                        asset.mimeType,
                                    )
                                }
                                else -> viewModel.openNoteDetail(note.id)
                            }
                        },
                        onLongClick = { pendingDelete = note },
                        onMove = { viewModel.openFileToSheet(note.id) },
                    )
                }
            }
        }
    }

    if (selectedNote != null) {
        NoteDetailDialog(
            note = selectedNote,
            onDismiss = viewModel::closeNoteDetail,
            onMove = {
                viewModel.openFileToSheet(selectedNote.id)
                viewModel.closeNoteDetail()
            },
        )
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = PureWhite,
            title = { Text("Delete \"${note.title.ifBlank { memoryKindLabel(note.kind) }}\"?", color = PureBlack) },
            text = { Text("Its file is removed from this phone and from Drive.", color = PureBlack.copy(alpha = 0.75f)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNote(note.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = PureWhite),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = PureBlack) }
            },
        )
    }

    pendingDeleteTodo?.let { todo ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTodo = null },
            containerColor = PureWhite,
            title = { Text("Delete \"${todo.title}\"?", color = PureBlack) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTask(todo.id)
                        pendingDeleteTodo = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = PureWhite),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTodo = null }) { Text("Cancel", color = PureBlack) }
            },
        )
    }
}

@Composable
private fun InboxTodoCard(todo: MotionTask, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, IconGray.copy(alpha = 0.35f), shape)
            .background(PureWhite)
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(OffWhite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.CheckBox, contentDescription = "To-do", tint = NavyDeep, modifier = Modifier.size(22.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = PureBlack,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    motionPriorityLabel(todo.priority).takeIf { todo.priority != MotionPriority.NONE },
                    todo.dueAt?.let(::motionDeadlineLabel),
                    brainRelativeTime(todo.createdAt),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PointsChip(points = todo.points, prefix = "")
    }
}

@Composable
private fun InboxCard(
    note: MemoryNote,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMove: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, IconGray.copy(alpha = 0.35f), shape)
            .background(PureWhite)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(OffWhite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                memoryKindIcon(note.kind),
                contentDescription = memoryKindLabel(note.kind),
                tint = NavyDeep,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = note.title.ifBlank { memoryKindLabel(note.kind) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = PureBlack,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val preview = note.url ?: note.body.takeIf { note.kind == MemoryNoteKind.TEXT }
            Text(
                text = listOfNotNull(
                    "${memoryKindLabel(note.kind)} · ${brainRelativeTime(note.createdAt)}",
                    preview?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMove) {
            Icon(
                Icons.AutoMirrored.Filled.DriveFileMove,
                contentDescription = "Move to a memory",
                tint = NavyDeep,
            )
        }
    }
}

@Composable
private fun NoteDetailDialog(
    note: MemoryNote,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(note.title.ifBlank { memoryKindLabel(note.kind) }, color = PureBlack) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = memoryKindLabel(note.kind),
                    style = MaterialTheme.typography.labelMedium,
                    color = PureBlack.copy(alpha = 0.6f),
                )
                if (note.body.isNotBlank()) {
                    Text(
                        text = note.body,
                        color = PureBlack,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                note.url?.let { url ->
                    Text(url, color = NavyDeep, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onMove) { Text("Move to…", color = NavyDeep) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = PureBlack) }
        },
        containerColor = PureWhite,
    )
}

fun brainRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(now - timestamp)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}d ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestamp))
    }
}
