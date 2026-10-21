@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.vm.vector.ui.screens.brain

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.vm.core.models.LearningTileStatus
import com.vm.core.models.MemoryNote
import com.vm.core.models.MemoryNoteKind
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.core.ui.theme.VectorTheme
import com.vm.vector.ui.components.rememberVoiceCaptureStarter
import com.vm.vector.ui.viewmodel.LearnTileViewModel
import com.vm.vector.ui.viewmodel.LearnTileViewModelFactory
import com.vm.vector.ui.viewmodel.LinkedTile
import kotlinx.coroutines.launch

@Composable
fun LearningTileScreen(
    tileId: String,
    onBack: () -> Unit,
    onOpenText: (noteId: String) -> Unit,
    onOpenPlayer: (assetId: String) -> Unit,
    onOpenTile: (nodeId: String) -> Unit,
    onOpenQuiz: (nodeId: String) -> Unit,
    viewModel: LearnTileViewModel = viewModel(
        key = "learn_$tileId",
        factory = LearnTileViewModelFactory(LocalContext.current, tileId),
    ),
) {
    val node by viewModel.node.collectAsState()
    val tile by viewModel.tile.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val library by viewModel.tagLibrary.collectAsState()
    val learnNodes by viewModel.learnNodes.collectAsState()
    val inbox by viewModel.inboxNotes.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val links by viewModel.links.collectAsState()
    val statuses by viewModel.statuses.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val suggestions by viewModel.linkSuggestions.collectAsState()
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
    var showExport by remember { mutableStateOf(false) }
    var statusMenuOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    /** Non-null while the link picker is open: true adds prerequisites, false adds dependents. */
    var linkPickerForPrerequisites by remember { mutableStateOf<Boolean?>(null) }
    var moving by remember { mutableStateOf<MemoryNote?>(null) }
    var deleting by remember { mutableStateOf<MemoryNote?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importFiles(uris) }
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> viewModel.importFiles(uris) }
    val exportSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> viewModel.saveExport(uri) }
    val startVoiceCapture = rememberVoiceCaptureStarter(
        onMicDenied = { scope.launch { snackbarHostState.showSnackbar("Microphone permission is needed for voice capture.") } },
        nodeId = tileId,
    )

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val status = tile?.status ?: LearningTileStatus.BACKLOG
    val counts = sources.groupingBy { it.kind }.eachCount()
    val sorted = sources.sortedWith(compareByDescending<MemoryNote> { it.pinned }.thenByDescending { it.createdAt })
    val pendingPrerequisites = links.prerequisites.count { it.status != LearningTileStatus.LEARNED }

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
                    Icon(Icons.Filled.Add, contentDescription = "Add to tile")
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
                                    text = node?.name.orEmpty(),
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
                            Box {
                                IconButton(onClick = { overflowOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = PureBlack)
                                }
                                TileOverflowMenu(
                                    expanded = overflowOpen,
                                    onDismiss = { overflowOpen = false },
                                    onQuiz = { onOpenQuiz(tileId) },
                                    onSuggestLinks = viewModel::suggestLinks,
                                    onDraftNotes = { viewModel.draftNotes(onCreated = onOpenText) },
                                    onExport = { showExport = true },
                                    onDelete = { showDeleteTile = true },
                                )
                            }
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PureBlack)
                            }
                        }

                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable { statusMenuOpen = true },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LearnStatusPill(status = status, large = true)
                                    Icon(
                                        Icons.Filled.ArrowDropDown,
                                        contentDescription = "Change status",
                                        tint = SlateGray,
                                    )
                                }
                                LearnStatusMenu(
                                    expanded = statusMenuOpen,
                                    current = status,
                                    onSelect = {
                                        statusMenuOpen = false
                                        viewModel.setStatus(it)
                                    },
                                    onDismiss = { statusMenuOpen = false },
                                )
                            }
                        }
                        tile?.let {
                            Text(
                                text = learnStatusLine(it, settings),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateGray,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        tile?.lastReviewScore?.let { score ->
                            Text(
                                text = "Last quiz: ${(score * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateGray,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }

                        if (status == LearningTileStatus.TO_REVIEW) {
                            ReviewActions(
                                onStartReview = { onOpenQuiz(tileId) },
                                onSolid = { viewModel.completeReview(solid = true) },
                                onNeedsMore = { viewModel.completeReview(solid = false) },
                                modifier = Modifier.padding(top = 14.dp),
                            )
                        }

                        busy?.let { label ->
                            Row(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    color = NavyDeep,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SlateGray,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }

                        FlowRow(
                            modifier = Modifier.padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            node?.tags.orEmpty().forEach { tag ->
                                TileTagPill(text = tag, onRemove = { viewModel.removeTag(tag) })
                            }
                            AddPill(onClick = { showTagPicker = true })
                        }
                    }
                }

                item(key = "links") {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        LinkGroup(
                            title = "Depends on",
                            empty = "Nothing yet. Link the tiles you need to know first.",
                            linked = links.prerequisites,
                            onOpen = onOpenTile,
                            onRemove = { viewModel.removeLink(it, asPrerequisite = true) },
                            onAdd = { linkPickerForPrerequisites = true },
                        )
                        if (pendingPrerequisites > 0 && status != LearningTileStatus.LEARNED) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = learnStatusColor(LearningTileStatus.ON_HOLD),
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (pendingPrerequisites == 1) {
                                        "1 prerequisite isn't learned yet"
                                    } else {
                                        "$pendingPrerequisites prerequisites aren't learned yet"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateGray,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                        LinkGroup(
                            title = "Used in",
                            empty = "No tile depends on this one yet.",
                            linked = links.dependents,
                            onOpen = onOpenTile,
                            onRemove = { viewModel.removeLink(it, asPrerequisite = false) },
                            onAdd = { linkPickerForPrerequisites = false },
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }

                item(key = "sources_title") {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "Sources" + if (sources.isNotEmpty()) " · ${sources.size}" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PureBlack,
                        )
                        if (counts.isNotEmpty()) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
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
                if (sorted.isEmpty()) {
                    item(key = "empty") {
                        BrainEmptyState(
                            title = "Nothing here yet",
                            message = "Tap + to write a note, record, add a link or media, or pull something from your Inbox. " +
                                "Adding something moves the tile to In Progress.",
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
            }
        }
    }

    if (showCaptureSheet) {
        MemoryCaptureSheet(
            memoryName = node?.name.orEmpty(),
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
            title = "Rename tile",
            label = "Title",
            initial = node?.name.orEmpty(),
            confirm = "Save",
            onConfirm = viewModel::rename,
            onDismiss = { showRename = false },
        )
    }

    if (showTagPicker) {
        TagPickerDialog(
            selected = node?.tags.orEmpty(),
            library = library,
            onPick = viewModel::addTag,
            onDeleteFromLibrary = viewModel::deleteLibraryTag,
            onDismiss = { showTagPicker = false },
        )
    }

    if (showDeleteTile) {
        DeleteMemoryDialog(
            name = node?.name.orEmpty(),
            sourceCount = sources.size,
            noun = "learn tile",
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

    if (showExport) {
        LearnExportDialog(
            prerequisiteCount = links.prerequisites.size,
            onSave = { include -> viewModel.prepareExport(include) { fileName -> exportSaver.launch(fileName) } },
            onShare = { include -> viewModel.shareExport(include) { intent -> context.startActivity(intent) } },
            onDismiss = { showExport = false },
        )
    }

    linkPickerForPrerequisites?.let { asPrerequisite ->
        val linkedIds = (links.prerequisites + links.dependents).map { it.node.id }.toSet()
        LearnLinkPickerDialog(
            heading = if (asPrerequisite) "Depends on…" else "Used in…",
            candidates = learnNodes.filter { it.id != tileId && it.id !in linkedIds },
            statusOf = { id -> statuses[id] ?: LearningTileStatus.BACKLOG },
            onAdd = { viewModel.addLinks(it, asPrerequisite) },
            onCreate = { viewModel.createLinkedTile(it, asPrerequisite) },
            onDismiss = { linkPickerForPrerequisites = null },
        )
    }

    suggestions?.let { current ->
        LinkSuggestionsDialog(
            suggestions = current,
            nodesById = learnNodes.associateBy { it.id },
            onAccept = viewModel::acceptSuggestions,
            onDismiss = viewModel::dismissSuggestions,
        )
    }

    moving?.let { note ->
        MemoryPickerDialog(
            title = "Move to",
            tiles = learnNodes,
            currentNodeId = tileId,
            onPick = { target ->
                viewModel.moveSource(note.id, target)
                moving = null
            },
            onCreate = { name ->
                viewModel.moveSourceToNewTile(note.id, name)
                moving = null
            },
            onDismiss = { moving = null },
            noun = "Learn tile",
            tileIcon = ModelNetworkIcon,
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

/** Shown only while a review is due. */
@Composable
private fun ReviewActions(
    onStartReview: () -> Unit,
    onSolid: () -> Unit,
    onNeedsMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onStartReview,
            colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start review")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onNeedsMore, modifier = Modifier.weight(1f)) {
                Text("Needs more", color = NavyDeep)
            }
            OutlinedButton(onClick = onSolid, modifier = Modifier.weight(1f)) {
                Text("Still solid", color = NavyDeep)
            }
        }
    }
}

@Composable
private fun LinkGroup(
    title: String,
    empty: String,
    linked: List<LinkedTile>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title + if (linked.isNotEmpty()) " · ${linked.size}" else "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = PureBlack,
        )
        if (linked.isEmpty()) {
            Text(
                empty,
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            linked.forEach { item ->
                LearnLinkChip(
                    name = item.node.name,
                    status = item.status,
                    onClick = { onOpen(item.node.id) },
                    onRemove = { onRemove(item.node.id) },
                )
            }
            AddPill(onClick = onAdd, label = "Link")
        }
    }
}

@Composable
private fun TileOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onQuiz: () -> Unit,
    onSuggestLinks: () -> Unit,
    onDraftNotes: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = PureWhite) {
        listOf(
            "Quiz me" to onQuiz,
            "Suggest links (AI)" to onSuggestLinks,
            "Draft notes (AI)" to onDraftNotes,
            "Export as .txt" to onExport,
        ).forEach { (label, action) ->
            DropdownMenuItem(
                text = { Text(label, color = PureBlack) },
                onClick = {
                    onDismiss()
                    action()
                },
            )
        }
        HorizontalDivider(color = IconGray.copy(alpha = 0.35f))
        DropdownMenuItem(
            text = { Text("Delete tile", color = DeleteRed) },
            onClick = {
                onDismiss()
                onDelete()
            },
        )
    }
}
