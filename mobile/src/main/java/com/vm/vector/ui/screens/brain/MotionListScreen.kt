@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.vm.vector.ui.screens.brain

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vm.core.models.MotionPriority
import com.vm.core.models.MotionScoring
import com.vm.core.models.MotionTask
import com.vm.core.models.MotionTaskStatus
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.core.ui.theme.VectorTheme
import com.vm.vector.data.MotionItemDraft
import com.vm.vector.ui.viewmodel.MotionListViewModel
import com.vm.vector.ui.viewmodel.MotionListViewModelFactory
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun MotionListScreen(
    nodeId: String,
    onBack: () -> Unit,
    viewModel: MotionListViewModel = viewModel(
        key = "motion_$nodeId",
        factory = MotionListViewModelFactory(LocalContext.current, nodeId),
    ),
) {
    val list by viewModel.list.collectAsState()
    val items by viewModel.items.collectAsState()
    val inbox by viewModel.inboxTodos.collectAsState()
    val message by viewModel.message.collectAsState()
    val aiBusy by viewModel.aiBusy.collectAsState()
    val aiDrafts by viewModel.aiDrafts.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddSheet by remember { mutableStateOf(false) }
    var showNewItem by remember { mutableStateOf(false) }
    var showMerge by remember { mutableStateOf(false) }
    var showAiText by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteList by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MotionTask?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val zone = ZoneId.systemDefault()
    val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val todayEnd = todayStart + TimeUnit.DAYS.toMillis(1)
    val weekEnd = todayStart + TimeUnit.DAYS.toMillis(7)
    val open = items.filter { it.status == MotionTaskStatus.OPEN }
        .sortedWith(compareBy<MotionTask> { it.dueAt ?: Long.MAX_VALUE }.thenByDescending { it.priority.ordinal })
    val done = items.filter { it.status == MotionTaskStatus.DONE }.sortedByDescending { it.completedAt }
    val overdue = open.filter { (it.dueAt ?: Long.MAX_VALUE) < todayStart }
    val today = open.filter { it.dueAt?.let { due -> due in todayStart until todayEnd } == true }
    val week = open.filter { it.dueAt?.let { due -> due in todayEnd until weekEnd } == true }
    val later = open.filter { it.dueAt?.let { due -> due >= weekEnd } == true }
    val undated = open.filter { it.dueAt == null }
    val earnedToday = done.sumOf { it.earnedPoints ?: 0 }

    VectorTheme {
        Scaffold(
            containerColor = PureWhite,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = NavyDeep,
                    contentColor = PureWhite,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add to list")
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                    text = list?.name.orEmpty(),
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
                            text = buildString {
                                append(
                                    when (open.size) {
                                        0 -> "All clear"
                                        1 -> "1 open item"
                                        else -> "${open.size} open items"
                                    }
                                )
                                if (earnedToday > 0) append(" · +$earnedToday pts today")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateGray,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        if (aiBusy) {
                            Row(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    color = NavyDeep,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    "Reading your text…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateGray,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
                if (items.isEmpty()) {
                    item(key = "empty") {
                        BrainEmptyState(
                            title = "Empty list",
                            message = "Tap + to add an item, merge to-dos from your Inbox, or let Gemini read a text.",
                        )
                    }
                }
                itemGroup("Overdue", overdue, viewModel) { editing = it }
                itemGroup("Today", today, viewModel) { editing = it }
                itemGroup("This week", week, viewModel) { editing = it }
                itemGroup("Later", later, viewModel) { editing = it }
                itemGroup("No deadline", undated, viewModel) { editing = it }
                itemGroup("Done today", done, viewModel) { editing = it }
                item(key = "delete_list") {
                    TextButton(
                        onClick = { showDeleteList = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = DeleteRed, modifier = Modifier.size(18.dp))
                        Text(
                            "Delete list",
                            color = DeleteRed,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }

        if (showAddSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = PureWhite,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Add to ${list?.name.orEmpty()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PureBlack,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CaptureOption(Icons.AutoMirrored.Outlined.PlaylistAdd, "New item", Modifier.weight(1f)) {
                            showAddSheet = false
                            showNewItem = true
                        }
                        CaptureOption(
                            Icons.Outlined.Inbox,
                            "Merge to-dos",
                            Modifier.weight(1f),
                            badge = inbox.size.takeIf { it > 0 }?.toString(),
                        ) {
                            showAddSheet = false
                            showMerge = true
                        }
                        CaptureOption(Icons.Outlined.AutoAwesome, "From text", Modifier.weight(1f)) {
                            showAddSheet = false
                            showAiText = true
                        }
                    }
                }
            }
        }

        if (showNewItem) {
            MotionItemDialog(
                initial = remember { MotionItemDraft() },
                heading = "New item",
                confirmLabel = "Add",
                onSave = { draft ->
                    viewModel.addItems(listOf(draft))
                    showNewItem = false
                },
                onDismiss = { showNewItem = false },
            )
        }

        editing?.let { task ->
            MotionItemDialog(
                initial = MotionItemDraft.from(task),
                heading = "Edit item",
                onSave = { draft ->
                    viewModel.updateItem(task, draft)
                    editing = null
                },
                onDelete = {
                    viewModel.deleteItem(task.id)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }

        if (showMerge) {
            if (inbox.isEmpty()) {
                AlertDialog(
                    onDismissRequest = { showMerge = false },
                    containerColor = PureWhite,
                    title = { Text("No captured to-dos", color = PureBlack) },
                    text = {
                        Text(
                            "To-dos from Quick capture wait in the Inbox until you merge them into a list.",
                            color = PureBlack.copy(alpha = 0.75f),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showMerge = false }) { Text("OK", color = NavyDeep) }
                    },
                )
            } else {
                MotionItemPickerDialog(
                    heading = "Merge captured to-dos",
                    message = "Tap an item to edit it before merging.",
                    drafts = remember(inbox) { inbox.map(MotionItemDraft::from) },
                    confirmLabel = { n -> if (n == 1) "Merge 1" else "Merge $n" },
                    onConfirm = { picked ->
                        viewModel.mergeTodos(picked)
                        showMerge = false
                    },
                    onDismiss = { showMerge = false },
                )
            }
        }

        if (showAiText) {
            AiTextDialog(
                onSubmit = { text ->
                    viewModel.parseWithAi(text)
                    showAiText = false
                },
                onDismiss = { showAiText = false },
            )
        }

        aiDrafts?.let { drafts ->
            MotionItemPickerDialog(
                heading = "Suggested items",
                message = "Generated by Gemini, verify. Tap an item to edit it.",
                drafts = drafts,
                confirmLabel = { n -> if (n == 1) "Add 1" else "Add $n" },
                onConfirm = { picked ->
                    viewModel.addItems(picked)
                    viewModel.clearAiDrafts()
                },
                onDismiss = viewModel::clearAiDrafts,
            )
        }

        if (showRename) {
            var name by remember { mutableStateOf(list?.name.orEmpty()) }
            AlertDialog(
                onDismissRequest = { showRename = false },
                containerColor = PureWhite,
                title = { Text("Rename list", color = PureBlack) },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = brainFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.rename(name)
                            showRename = false
                        },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showRename = false }) { Text("Cancel", color = PureBlack) }
                },
            )
        }

        if (showDeleteList) {
            AlertDialog(
                onDismissRequest = { showDeleteList = false },
                containerColor = PureWhite,
                title = { Text("Delete \"${list?.name.orEmpty()}\"?", color = PureBlack) },
                text = {
                    Text(
                        "Its items will be deleted. Points already earned are kept.",
                        color = PureBlack.copy(alpha = 0.75f),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteList = false
                            viewModel.deleteList(onBack)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = PureWhite),
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteList = false }) { Text("Cancel", color = PureBlack) }
                },
            )
        }
    }
}

private fun LazyListScope.itemGroup(
    title: String,
    tasks: List<MotionTask>,
    viewModel: MotionListViewModel,
    onEdit: (MotionTask) -> Unit,
) {
    if (tasks.isEmpty()) return
    item(key = "group_$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (title == "Overdue") DeleteRed else PureBlack,
            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        )
    }
    items(tasks, key = { it.id }) { task ->
        MotionItemRow(task = task, viewModel = viewModel, onEdit = { onEdit(task) })
    }
}

@Composable
private fun MotionItemRow(
    task: MotionTask,
    viewModel: MotionListViewModel,
    onEdit: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val isDone = task.status == MotionTaskStatus.DONE
    val target = task.target
    var showMenu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val worth = if (isDone) task.earnedPoints ?: 0 else MotionScoring.pointsAt(task, System.currentTimeMillis())
    val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val isOverdue = !isDone && (task.dueAt ?: Long.MAX_VALUE) < todayStart

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, IconGray.copy(alpha = 0.35f), shape)
                .background(PureWhite)
                .combinedClickable(
                    onClick = onEdit,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    },
                )
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isDone,
                    onCheckedChange = { viewModel.toggle(task) },
                    colors = motionCheckboxColors(),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isDone) SlateGray else PureBlack,
                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (task.notes.isNotBlank()) {
                        Text(
                            task.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (task.priority != MotionPriority.NONE) {
                            Text(
                                motionPriorityLabel(task.priority),
                                color = PureWhite,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .background(motionPriorityColor(task.priority), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        if (task.dueAt != null) {
                            Text(
                                motionDeadlineLabel(task.dueAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isOverdue) DeleteRed else SlateGray,
                            )
                        }
                        if (target != null) {
                            Text(
                                "${formatMotionAmount(task.progress)} / ${formatMotionAmount(target)}" +
                                    task.unit?.let { " $it" }.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NavyDeep,
                            )
                        }
                    }
                }
                PointsChip(points = worth, prefix = if (isDone) "+" else "")
            }
            if (target != null && !isDone) {
                ProgressSlider(task = task, target = target, onCommit = { viewModel.setProgress(task, it) })
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (!isDone) {
                DropdownMenuItem(
                    text = { Text("Snooze 1 hour") },
                    onClick = {
                        viewModel.snooze(task.id, snoozeOneHour())
                        showMenu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Snooze to tonight") },
                    onClick = {
                        viewModel.snooze(task.id, snoozeTonight())
                        showMenu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Snooze to tomorrow") },
                    onClick = {
                        viewModel.snooze(task.id, snoozeTomorrowMorning())
                        showMenu = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", color = DeleteRed) },
                onClick = {
                    viewModel.deleteItem(task.id)
                    showMenu = false
                },
            )
        }
    }
}

/** Progress toward the numeric target; whole-number targets snap to whole steps. */
@Composable
private fun ProgressSlider(task: MotionTask, target: Double, onCommit: (Double) -> Unit) {
    val whole = target % 1.0 == 0.0
    var value by remember(task.id, task.progress) {
        mutableFloatStateOf(task.progress.coerceIn(0.0, target).toFloat())
    }
    VectorSlider(
        value = value,
        onValueChange = { raw -> value = if (whole) raw.roundToInt().toFloat() else raw },
        onValueChangeFinished = {
            val committed = if (whole) value.roundToInt().toDouble() else (value * 10).roundToInt() / 10.0
            onCommit(committed)
        },
        valueRange = 0f..target.toFloat(),
        modifier = Modifier.padding(start = 12.dp, top = 2.dp),
    )
}

@Composable
private fun AiTextDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Items from text", color = PureBlack) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste notes, a message or a plan. Gemini suggests items with deadlines, priorities and " +
                        "targets; you pick which ones to add.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateGray,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Buy a gift for Ana by Friday, read 40 pages, renew passport (urgent)") },
                    minLines = 5,
                    colors = brainFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(text) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) { Text("Suggest items") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )
}
