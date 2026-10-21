@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.vm.vector.ui.screens.brain

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vm.core.models.MemoryNote
import com.vm.core.models.MemoryNoteKind
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.core.ui.theme.VectorTheme
import com.vm.vector.ui.components.rememberVoiceCaptureStarter
import com.vm.vector.ui.viewmodel.MemoryTileViewModel
import com.vm.vector.ui.viewmodel.MemoryTileViewModelFactory
import kotlinx.coroutines.launch

@Composable
fun MemoryTileScreen(
    nodeId: String,
    onBack: () -> Unit,
    onOpenPlayer: (assetId: String) -> Unit,
    onOpenText: (noteId: String) -> Unit,
    viewModel: MemoryTileViewModel = viewModel(
        key = "memory_$nodeId",
        factory = MemoryTileViewModelFactory(LocalContext.current, nodeId),
    ),
) {
    val tile by viewModel.tile.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val library by viewModel.tagLibrary.collectAsState()
    val allTiles by viewModel.otherTiles.collectAsState()
    val inbox by viewModel.inboxNotes.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showRename by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showDeleteTile by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showInboxPicker by remember { mutableStateOf(false) }
    var showCaptureSheet by remember { mutableStateOf(false) }
    var showNewText by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<MemoryNote?>(null) }
    var deleting by remember { mutableStateOf<MemoryNote?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importFiles(uris) }
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> viewModel.importFiles(uris) }
    val startVoiceCapture = rememberVoiceCaptureStarter(
        onMicDenied = { scope.launch { snackbarHostState.showSnackbar("Microphone permission is needed for voice capture.") } },
        nodeId = nodeId,
    )

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val counts = sources.groupingBy { it.kind }.eachCount()
    val sorted = sources.sortedByDescending { it.createdAt }

    VectorTheme {
        Scaffold(
            containerColor = PureWhite,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCaptureSheet = true },
                    containerColor = NavyDeep,
                    contentColor = PureWhite,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add to memory")
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "header") {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showRename = true },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = tile?.name.orEmpty(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PureBlack,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "Rename",
                                    tint = SlateGray,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(18.dp),
                                )
                            }
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PureBlack)
                            }
                        }
                        Text(
                            text = when (sources.size) {
                                0 -> "No sources yet"
                                1 -> "1 source"
                                else -> "${sources.size} sources"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateGray,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        FlowRow(
                            modifier = Modifier.padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            tile?.tags.orEmpty().forEach { tag ->
                                TileTagPill(text = tag, onRemove = { viewModel.removeTag(tag) })
                            }
                            AddPill(onClick = { showTagPicker = true })
                        }
                        if (counts.isNotEmpty()) {
                            Row(
                                modifier = Modifier.padding(top = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                MemoryNoteKind.entries.forEach { kind ->
                                    val count = counts[kind] ?: return@forEach
                                    KindCountPill(kind, count)
                                }
                            }
                        }
                    }
                }
                item(key = "sources_title") {
                    Text(
                        "Sources",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PureBlack,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (sorted.isEmpty()) {
                    item(key = "empty") {
                        BrainEmptyState(
                            title = "Empty memory",
                            message = "Tap + to write a note, record, add a link or media, or pull something from your Inbox.",
                        )
                    }
                }
                items(sorted, key = { it.id }) { note ->
                    SourceCard(
                        note = note,
                        onOpen = {
                            openMemorySource(note, context, scope, viewModel.repository(), onOpenPlayer, onOpenText)
                        },
                        onMove = { moving = note },
                        onDelete = { deleting = note },
                    )
                }
                item(key = "delete_memory") {
                    TextButton(
                        onClick = { showDeleteTile = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = DeleteRed,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "Delete memory",
                            color = DeleteRed,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }

    if (showCaptureSheet) {
        MemoryCaptureSheet(
            memoryName = tile?.name.orEmpty(),
            inboxCount = inbox.size,
            onText = { showNewText = true },
            onVoice = startVoiceCapture,
            onLink = { showLinkDialog = true },
            onMedia = {
                mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onFile = { filePicker.launch(arrayOf("*/*")) },
            onInbox = { showInboxPicker = true },
            onDismiss = { showCaptureSheet = false },
        )
    }

    if (showRename) {
        SingleFieldDialog(
            title = "Rename memory",
            label = "Title",
            initial = tile?.name.orEmpty(),
            confirm = "Save",
            onConfirm = viewModel::rename,
            onDismiss = { showRename = false },
        )
    }

    if (showTagPicker) {
        TagPickerDialog(
            selected = tile?.tags.orEmpty(),
            library = library,
            onPick = viewModel::addTag,
            onDeleteFromLibrary = viewModel::deleteLibraryTag,
            onDismiss = { showTagPicker = false },
        )
    }

    if (showDeleteTile) {
        DeleteMemoryDialog(
            name = tile?.name.orEmpty(),
            sourceCount = sources.size,
            onConfirm = {
                showDeleteTile = false
                viewModel.deleteTile(onDeleted = onBack)
            },
            onDismiss = { showDeleteTile = false },
        )
    }

    if (showLinkDialog) {
        LinkDialog(
            onAdd = viewModel::addLink,
            onDismiss = { showLinkDialog = false },
        )
    }

    if (showNewText) {
        TextDetailsDialog(
            heading = "New text",
            initialTitle = "",
            initialDescription = "",
            confirm = "Create",
            onSave = { title, description -> viewModel.createText(title, description, onCreated = onOpenText) },
            onDismiss = { showNewText = false },
        )
    }

    if (showInboxPicker) {
        InboxPickerDialog(
            notes = inbox,
            onAdd = viewModel::pullFromInbox,
            onDismiss = { showInboxPicker = false },
        )
    }

    moving?.let { note ->
        MemoryPickerDialog(
            title = "Move to",
            tiles = allTiles,
            currentNodeId = nodeId,
            onPick = { target ->
                viewModel.moveSource(note.id, target)
                moving = null
            },
            onCreate = { name ->
                viewModel.moveSourceToNewTile(note.id, name)
                moving = null
            },
            onDismiss = { moving = null },
        )
    }

    deleting?.let { note ->
        DeleteSourceDialog(
            note = note,
            onConfirm = {
                viewModel.deleteSource(note.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}
