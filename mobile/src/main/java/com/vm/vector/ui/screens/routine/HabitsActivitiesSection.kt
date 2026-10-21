package com.vm.vector.ui.screens.routine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vm.core.models.ActivityCompletion
import com.vm.core.models.ActivityEntry
import com.vm.core.models.ActivityLabel
import com.vm.core.models.ActivityType
import com.vm.core.models.HabitDefinition
import com.vm.core.models.HabitLog
import com.vm.core.models.HabitStatus
import com.vm.core.models.effectiveEndTime
import com.vm.core.models.effectiveStartTime
import com.vm.core.models.isAdjusted
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.ActivityImportDefaults
import com.vm.vector.data.ActivityImportDraft
import com.vm.vector.data.HabitScoreSummary
import com.vm.vector.data.resolveImportLabelId
import com.vm.vector.ui.components.DialogMultilineFieldDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import java.util.Locale
import java.util.UUID

fun habitStatusColor(status: HabitStatus): Color = when (status) {
    HabitStatus.DONE -> Color(0xFF4CAF50)
    HabitStatus.EXCEEDED -> ElectricBlue
    HabitStatus.PARTIAL -> Color(0xFFFF9800)
    HabitStatus.NOT_DONE -> Color(0xFFD32F2F)
    HabitStatus.NA -> Color(0xFF9E9E9E)
    HabitStatus.NONE -> PureWhite
}

fun activityCompletionColor(completion: ActivityCompletion): Color = when (completion) {
    ActivityCompletion.DONE -> Color(0xFF4CAF50)
    ActivityCompletion.NOT_DONE -> Color(0xFFD32F2F)
    ActivityCompletion.CANCELLED -> Color(0xFF9E9E9E)
    ActivityCompletion.NA -> Color(0xFF78909C)
    ActivityCompletion.NONE -> OffWhite
}

fun activityCompletionContentColor(completion: ActivityCompletion): Color = when (completion) {
    ActivityCompletion.NONE -> PureBlack
    else -> PureWhite
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HabitsTrackerSection(
    definitions: List<HabitDefinition>,
    selectedIds: List<String>,
    logs: List<HabitLog>,
    isEditingEnabled: Boolean,
    onStatusChange: (habitId: String, status: HabitStatus) -> Unit,
    onCreateHabit: (name: String, onCreated: (HabitDefinition) -> Unit) -> Unit,
    onDeleteHabit: (String) -> Unit,
    onSetSelected: (List<String>) -> Unit,
    scoreSummaries: Map<String, HabitScoreSummary> = emptyMap(),
    modifier: Modifier = Modifier,
    /**
     * Optional external header (e.g. Home section title row). Receives expand/edit actions
     * so they can sit on the same line as the section title.
     * When null, the default in-card title row is used.
     */
    header: (@Composable (
        expanded: Boolean,
        onToggleExpand: () -> Unit,
        onEditClick: (() -> Unit)?,
    ) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    val defById = remember(definitions) { definitions.associateBy { it.id } }
    val logByHabit = remember(logs) { logs.associateBy { it.habitId } }
    val orderedSelected = selectedIds.mapNotNull { defById[it] }
    val onEditClick: (() -> Unit)? = if (isEditingEnabled) {{ showEdit = true }} else null

    if (header != null) {
        header(expanded, { expanded = !expanded }, onEditClick)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = OffWhite,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (header == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Habits Tracker",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureBlack,
                        modifier = Modifier.weight(1f),
                    )
                    if (onEditClick != null) {
                        IconButton(onClick = onEditClick) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit habits", tint = NavyDeep)
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = NavyDeep,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = if (header != null) {
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                } else {
                    Modifier
                },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (orderedSelected.isEmpty()) {
                    Text("No habits selected", color = SlateGray, fontSize = 13.sp)
                } else {
                    orderedSelected.forEach { def ->
                        val status = logByHabit[def.id]?.status ?: HabitStatus.NONE
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(habitStatusColor(status), RoundedCornerShape(4.dp))
                                .border(1.dp, NavyDeep.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                if (orderedSelected.isEmpty()) {
                    Text("Add habits via the edit button.", color = SlateGray, fontSize = 13.sp)
                } else {
                    orderedSelected.forEach { def ->
                        val status = logByHabit[def.id]?.status ?: HabitStatus.NONE
                        HabitCard(
                            name = def.name,
                            status = status,
                            enabled = isEditingEnabled,
                            onStatusChange = { onStatusChange(def.id, it) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showEdit) {
        HabitsEditDialog(
            definitions = definitions,
            selectedIds = selectedIds,
            scoreSummaries = scoreSummaries,
            onDismiss = { showEdit = false },
            onCreate = onCreateHabit,
            onDelete = onDeleteHabit,
            onApply = {
                onSetSelected(it)
                showEdit = false
            },
        )
    }
}

@Composable
private fun HabitCard(
    name: String,
    status: HabitStatus,
    enabled: Boolean,
    onStatusChange: (HabitStatus) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = PureWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, OffWhite),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, color = PureBlack, modifier = Modifier.weight(1f))
            HabitStatusDropdown(status = status, enabled = enabled, onStatusChange = onStatusChange)
        }
    }
}

@Composable
fun HabitStatusDropdown(
    status: HabitStatus,
    enabled: Boolean,
    onStatusChange: (HabitStatus) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { if (enabled) expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = habitStatusColor(status),
            border = androidx.compose.foundation.BorderStroke(1.dp, PureBlack.copy(alpha = 0.25f)),
        ) {
            Text(
                text = status.name.replace('_', ' '),
                color = if (status == HabitStatus.NONE) PureBlack else PureWhite,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = PureWhite,
        ) {
            HabitStatus.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.name.replace('_', ' '), color = PureBlack) },
                    onClick = {
                        onStatusChange(s)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun HabitsEditDialog(
    definitions: List<HabitDefinition>,
    selectedIds: List<String>,
    scoreSummaries: Map<String, HabitScoreSummary>,
    onDismiss: () -> Unit,
    onCreate: (name: String, onCreated: (HabitDefinition) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onApply: (List<String>) -> Unit,
) {
    var selected by remember(selectedIds) { mutableStateOf(selectedIds.toSet()) }
    var newName by remember { mutableStateOf("") }
    var pendingDeselect by remember { mutableStateOf<HabitDefinition?>(null) }
    var pendingDelete by remember { mutableStateOf<HabitDefinition?>(null) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = PureBlack,
        unfocusedTextColor = PureBlack,
        cursorColor = NavyDeep,
        focusedBorderColor = NavyDeep,
        unfocusedBorderColor = PureBlack.copy(alpha = 0.5f),
        focusedPlaceholderColor = SlateGray,
        unfocusedPlaceholderColor = SlateGray,
        focusedContainerColor = PureWhite,
        unfocusedContainerColor = PureWhite,
    )
    val query = newName.trim()
    val filtered = remember(definitions, query) {
        if (query.isBlank()) definitions
        else definitions.filter { it.name.contains(query, ignoreCase = true) }
    }
    val selectedDefs = definitions.filter { it.id in selected }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 560.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Edit Habits", style = MaterialTheme.typography.titleMedium, color = PureBlack)
                Spacer(modifier = Modifier.height(12.dp))
                if (selectedDefs.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        selectedDefs.forEach { def ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = NavyDeep,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                                ) {
                                    Text(def.name, color = PureWhite, fontSize = 13.sp)
                                    IconButton(
                                        onClick = { pendingDeselect = def },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove from today",
                                            tint = PureWhite,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("New habit", color = SlateGray) },
                        singleLine = true,
                        colors = fieldColors,
                    )
                    IconButton(
                        onClick = {
                            val trimmed = newName.trim()
                            if (trimmed.isNotBlank()) {
                                onCreate(trimmed) { created -> selected = selected + created.id }
                                newName = ""
                            }
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = NavyDeep)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (filtered.isEmpty()) {
                        Text(
                            if (definitions.isEmpty()) {
                                "No habits yet. Type a name below and tap +."
                            } else {
                                "No habits match."
                            },
                            color = SlateGray,
                            fontSize = 13.sp,
                        )
                    } else {
                        filtered.forEach { def ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (def.id in selected) NavyDeep.copy(alpha = 0.08f) else OffWhite,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (def.id in selected) {
                                            selected - def.id
                                        } else {
                                            selected + def.id
                                        }
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(def.name, color = PureBlack, fontWeight = FontWeight.Medium)
                                        Text(
                                            scoreSummaries[def.id]?.display() ?: "[0 / 0]",
                                            color = SlateGray,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    IconButton(onClick = { pendingDelete = def }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete habit",
                                            tint = DeleteRed,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
                    Button(
                        onClick = { onApply(selected.toList()) },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                    ) { Text("Done") }
                }
            }
        }
    }

    pendingDeselect?.let { def ->
        AlertDialog(
            onDismissRequest = { pendingDeselect = null },
            containerColor = PureWhite,
            title = { Text("Remove from today?", color = PureBlack) },
            text = {
                Text("Stop tracking \"${def.name}\" on this day? The habit stays in your catalog.", color = PureBlack)
            },
            confirmButton = {
                TextButton(onClick = {
                    selected = selected - def.id
                    pendingDeselect = null
                }) { Text("Remove", color = NavyDeep) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeselect = null }) { Text("Cancel", color = PureBlack) }
            },
        )
    }

    pendingDelete?.let { def ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = PureWhite,
            title = { Text("Delete habit?", color = PureBlack) },
            text = {
                Text("Remove \"${def.name}\" from the catalog permanently?", color = PureBlack)
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(def.id)
                    selected = selected - def.id
                    pendingDelete = null
                }) { Text("Delete", color = Color(0xFFD32F2F)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = PureBlack) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ActivitiesSection(
    activities: List<ActivityEntry>,
    labels: List<ActivityLabel>,
    isEditingEnabled: Boolean,
    onUpsert: (ActivityEntry) -> Unit,
    onDelete: (String) -> Unit,
    onCompletion: (String, ActivityCompletion) -> Unit,
    onToggleType: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<ActivityEntry?>(null) }
    var adjusting by remember { mutableStateOf<ActivityEntry?>(null) }
    val labelById = remember(labels) { labels.associateBy { it.id } }

    Column(modifier = modifier.fillMaxWidth()) {
        if (activities.isEmpty()) {
            Text("No activities for this day.", color = SlateGray, fontSize = 13.sp)
        } else {
            activities.forEach { entry ->
                ActivityCard(
                    entry = entry,
                    label = labelById[entry.labelId],
                    isEditingEnabled = isEditingEnabled,
                    onCompletion = { onCompletion(entry.id, it) },
                    onToggleType = { onToggleType(entry.id) },
                    onEdit = { editing = entry },
                    onAdjust = { adjusting = entry },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    adjusting?.let { entry ->
        ActivityAdjustDialog(
            entry = entry,
            onDismiss = { adjusting = null },
            onSave = {
                onUpsert(it)
                adjusting = null
            },
        )
    }

    editing?.let { entry ->
        ActivityEditDialog(
            entry = entry,
            labels = labels,
            date = entry.date,
            onDismiss = { editing = null },
            onSave = {
                onUpsert(it)
                editing = null
            },
            onDelete = {
                onDelete(entry.id)
                editing = null
            },
        )
    }
}

@Composable
private fun ActivityCard(
    entry: ActivityEntry,
    label: ActivityLabel?,
    isEditingEnabled: Boolean,
    onCompletion: (ActivityCompletion) -> Unit,
    onToggleType: () -> Unit,
    onEdit: () -> Unit,
    onAdjust: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val originalRange = "${entry.startTime} – ${entry.endTime}"
    val effectiveRange = "${entry.effectiveStartTime()} – ${entry.effectiveEndTime()}"
    val adjustButton: @Composable () -> Unit = {
        if (isEditingEnabled) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onAdjust, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = "Adjust time",
                    tint = NavyDeep,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = PureWhite,
        border = BorderStroke(1.dp, OffWhite),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title.ifBlank { "(Untitled)" },
                        color = PureBlack,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (entry.isAdjusted()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = originalRange,
                                color = SlateGray,
                                fontSize = 13.sp,
                                textDecoration = TextDecoration.LineThrough,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = effectiveRange,
                                color = PureBlack,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            adjustButton()
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = originalRange,
                                color = PureBlack,
                                fontSize = 13.sp,
                            )
                            adjustButton()
                        }
                    }
                }
                if (isEditingEnabled) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = NavyDeep,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = { if (isEditingEnabled) onToggleType() },
                    shape = RoundedCornerShape(12.dp),
                    color = if (entry.type == ActivityType.PLANNED) NavyDeep else OffWhite,
                    border = BorderStroke(1.dp, PureBlack.copy(alpha = 0.2f)),
                ) {
                    Text(
                        text = if (entry.type == ActivityType.PLANNED) "Planned" else "New",
                        color = if (entry.type == ActivityType.PLANNED) PureWhite else PureBlack,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(label?.colorArgb ?: 0xFF90A4AE.toInt()),
                ) {
                    Text(
                        text = label?.name ?: "?",
                        color = PureWhite,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                ActivityCompletionDropdown(
                    completion = entry.completion,
                    enabled = isEditingEnabled,
                    onChange = onCompletion,
                )
            }
            if (expanded && entry.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(entry.description, color = PureBlack.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ActivityCompletionDropdown(
    completion: ActivityCompletion,
    enabled: Boolean,
    onChange: (ActivityCompletion) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { if (enabled) expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = activityCompletionColor(completion),
            border = if (completion == ActivityCompletion.NONE) {
                BorderStroke(1.dp, PureBlack.copy(alpha = 0.25f))
            } else {
                null
            },
        ) {
            Text(
                text = completion.name.replace('_', ' '),
                fontSize = 11.sp,
                color = activityCompletionContentColor(completion),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = PureWhite,
        ) {
            ActivityCompletion.entries.forEach { c ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(activityCompletionColor(c), CircleShape)
                                    .border(1.dp, PureBlack.copy(alpha = 0.2f), CircleShape),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(c.name.replace('_', ' '), color = PureBlack)
                        }
                    },
                    onClick = {
                        onChange(c)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActivityEditDialog(
    entry: ActivityEntry?,
    labels: List<ActivityLabel>,
    date: String,
    onDismiss: () -> Unit,
    onSave: (ActivityEntry) -> Unit,
    onDelete: (() -> Unit)?,
    /** New activities only: values from the clipboard, AI or the library. */
    prefill: ActivityImportDraft? = null,
) {
    val seed = prefill?.takeIf { entry == null }
    var startTime by remember {
        mutableStateOf(entry?.startTime ?: seed?.startTime ?: ActivityImportDefaults.START_TIME)
    }
    var endTime by remember {
        mutableStateOf(entry?.endTime ?: seed?.endTime ?: ActivityImportDefaults.END_TIME)
    }
    var showTimePicker by remember { mutableStateOf<ActivityTimePickerKind?>(null) }
    var title by remember { mutableStateOf(entry?.title ?: seed?.title.orEmpty()) }
    var labelId by remember {
        mutableStateOf(
            entry?.labelId
                ?: seed?.let { resolveImportLabelId(it, labels, "") }?.ifBlank { null }
                ?: labels.firstOrNull()?.id.orEmpty()
        )
    }
    var completion by remember { mutableStateOf(entry?.completion ?: ActivityCompletion.NONE) }
    var type by remember { mutableStateOf(entry?.type ?: seed?.type ?: ActivityType.NEW) }
    var description by remember { mutableStateOf(entry?.description ?: seed?.description.orEmpty()) }
    val unknownLabel = seed?.labelName?.takeIf { name -> labels.none { it.name.equals(name, ignoreCase = true) } }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = PureBlack,
        unfocusedTextColor = PureBlack,
        cursorColor = NavyDeep,
        focusedBorderColor = NavyDeep,
        unfocusedBorderColor = PureBlack.copy(alpha = 0.5f),
        focusedLabelColor = PureBlack,
        unfocusedLabelColor = SlateGray,
        focusedPlaceholderColor = SlateGray,
        unfocusedPlaceholderColor = SlateGray,
        focusedContainerColor = PureWhite,
        unfocusedContainerColor = PureWhite,
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .imePadding()
                .heightIn(max = 560.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    if (entry == null) "New Activity" else "Edit Activity",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureBlack,
                )
                Spacer(modifier = Modifier.height(8.dp))
                unknownLabel?.let { name ->
                    Text(
                        "Unknown label \"$name\". Pick one below or create it in Settings → Preset Settings.",
                        color = Color(0xFFD32F2F),
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title", color = PureBlack) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                Spacer(modifier = Modifier.height(8.dp))
                ActivityTimeRangeInputs(
                    startLabel = "Start",
                    endLabel = "End",
                    startTime = startTime,
                    endTime = endTime,
                    onPick = { showTimePicker = it },
                )
                if (entry != null && entry.isAdjusted()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Adjusted: ${entry.effectiveStartTime()} – ${entry.effectiveEndTime()}",
                        color = SlateGray,
                        fontSize = 12.sp,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Label", color = PureBlack, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    labels.forEach { label ->
                        val selected = label.id == labelId
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) Color(label.colorArgb) else OffWhite,
                            border = BorderStroke(1.dp, PureBlack.copy(alpha = 0.25f)),
                            modifier = Modifier.clickable { labelId = label.id },
                        ) {
                            Text(
                                text = label.name,
                                color = if (selected) PureWhite else PureBlack,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Type", color = PureBlack, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                ActivityTypeSwitch(
                    type = type,
                    onTypeChange = { type = it },
                )
                if (entry != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Status", color = PureBlack, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    ActivityCompletionDropdown(completion, true) { completion = it }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", color = PureBlack) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = DialogMultilineFieldDefaults.MediumMin,
                            max = DialogMultilineFieldDefaults.MediumMax,
                        ),
                    colors = fieldColors,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text("Remove", color = Color(0xFFD32F2F))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
                        Button(
                            onClick = {
                                val trimmedTitle = title.trim()
                                if (trimmedTitle.isBlank()) return@Button
                                val resolvedLabel = labelId.ifBlank {
                                    labels.firstOrNull()?.id.orEmpty()
                                }
                                if (resolvedLabel.isBlank()) return@Button
                                val newStart = startTime.trim()
                                val newEnd = endTime.trim()
                                val saved = if (entry == null) {
                                    ActivityEntry(
                                        id = "act_${UUID.randomUUID()}",
                                        date = date,
                                        title = trimmedTitle,
                                        startTime = newStart,
                                        endTime = newEnd,
                                        adjustedStartTime = null,
                                        adjustedEndTime = null,
                                        labelId = resolvedLabel,
                                        completion = ActivityCompletion.NONE,
                                        type = type,
                                        description = description.trim(),
                                    )
                                } else {
                                    val adjustmentMatchesNewTimes =
                                        entry.effectiveStartTime() == newStart &&
                                            entry.effectiveEndTime() == newEnd
                                    val keepAdjustment = entry.isAdjusted() && !adjustmentMatchesNewTimes
                                    ActivityEntry(
                                        id = entry.id,
                                        date = entry.date,
                                        title = trimmedTitle,
                                        startTime = newStart,
                                        endTime = newEnd,
                                        adjustedStartTime = if (keepAdjustment) entry.effectiveStartTime() else null,
                                        adjustedEndTime = if (keepAdjustment) entry.effectiveEndTime() else null,
                                        labelId = resolvedLabel,
                                        completion = completion,
                                        type = type,
                                        description = description.trim(),
                                        timestamp = System.currentTimeMillis(),
                                    )
                                }
                                onSave(saved)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NavyDeep,
                                contentColor = PureWhite,
                            ),
                        ) { Text(if (entry == null) "Add" else "Save") }
                    }
                }
            }
        }
    }

    showTimePicker?.let { kind ->
        val initial = when (kind) {
            ActivityTimePickerKind.Start -> parseActivityHhMmToMinutes(startTime) ?: (9 * 60)
            ActivityTimePickerKind.End -> parseActivityHhMmToMinutes(endTime) ?: (10 * 60)
        }
        ActivityTimePickerDialog(
            initialMinutes = initial,
            onDismiss = { showTimePicker = null },
            onConfirm = { minutes ->
                val formatted = minutesToActivityHhMm(minutes)
                when (kind) {
                    ActivityTimePickerKind.Start -> startTime = formatted
                    ActivityTimePickerKind.End -> endTime = formatted
                }
                showTimePicker = null
            },
        )
    }
}

@Composable
private fun ActivityTimeRangeInputs(
    startLabel: String,
    endLabel: String,
    startTime: String,
    endTime: String,
    onPick: (ActivityTimePickerKind) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(startLabel, color = SlateGray, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            ActivityTimeInputChip(timeText = startTime, onClick = { onPick(ActivityTimePickerKind.Start) })
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(endLabel, color = SlateGray, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            ActivityTimeInputChip(timeText = endTime, onClick = { onPick(ActivityTimePickerKind.End) })
        }
    }
}

/**
 * Records when an activity actually happened. The planned times stay untouched; a new adjustment
 * replaces the previous one, and matching the planned times clears it.
 */
@Composable
private fun ActivityAdjustDialog(
    entry: ActivityEntry,
    onDismiss: () -> Unit,
    onSave: (ActivityEntry) -> Unit,
) {
    var startTime by remember { mutableStateOf(entry.effectiveStartTime()) }
    var endTime by remember { mutableStateOf(entry.effectiveEndTime()) }
    var showTimePicker by remember { mutableStateOf<ActivityTimePickerKind?>(null) }

    fun save(start: String?, end: String?) {
        val clear = start == null || end == null || (start == entry.startTime && end == entry.endTime)
        onSave(
            entry.copy(
                adjustedStartTime = if (clear) null else start,
                adjustedEndTime = if (clear) null else end,
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Adjust Time", color = PureBlack) },
        text = {
            Column {
                Text(
                    text = entry.title.ifBlank { "(Untitled)" },
                    color = PureBlack,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Planned: ${entry.startTime} – ${entry.endTime}",
                    color = SlateGray,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                ActivityTimeRangeInputs(
                    startLabel = "Actual start",
                    endLabel = "Actual end",
                    startTime = startTime,
                    endTime = endTime,
                    onPick = { showTimePicker = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { save(startTime.trim(), endTime.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (entry.isAdjusted()) {
                    TextButton(onClick = { save(null, null) }) { Text("Reset", color = DeleteRed) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
            }
        },
    )

    showTimePicker?.let { kind ->
        val initial = when (kind) {
            ActivityTimePickerKind.Start -> parseActivityHhMmToMinutes(startTime) ?: (9 * 60)
            ActivityTimePickerKind.End -> parseActivityHhMmToMinutes(endTime) ?: (10 * 60)
        }
        ActivityTimePickerDialog(
            initialMinutes = initial,
            onDismiss = { showTimePicker = null },
            onConfirm = { minutes ->
                val formatted = minutesToActivityHhMm(minutes)
                when (kind) {
                    ActivityTimePickerKind.Start -> startTime = formatted
                    ActivityTimePickerKind.End -> endTime = formatted
                }
                showTimePicker = null
            },
        )
    }
}

internal enum class ActivityTimePickerKind { Start, End }

@Composable
internal fun ActivityTypeSwitch(
    type: ActivityType,
    onTypeChange: (ActivityType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PureBlack.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        listOf(ActivityType.NEW, ActivityType.PLANNED).forEach { option ->
            val selected = type == option
            Surface(
                onClick = { onTypeChange(option) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) NavyDeep else PureWhite,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = when (option) {
                        ActivityType.NEW -> "New"
                        ActivityType.PLANNED -> "Planned"
                    },
                    color = if (selected) PureWhite else PureBlack,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** Distinct saturated tones for activity labels (hue-spread for easy visual separation). */
private val LabelColorPalette = listOf(
    // Reds / pinks
    0xFFC62828.toInt(),
    0xFFE91E63.toInt(),
    0xFFAD1457.toInt(),
    // Oranges / ambers
    0xFFEF6C00.toInt(),
    0xFFFF8F00.toInt(),
    0xFFF9A825.toInt(),
    // Yellows / limes
    0xFFFBC02D.toInt(),
    0xFFAFB42B.toInt(),
    0xFF7CB342.toInt(),
    // Greens
    0xFF2E7D32.toInt(),
    0xFF00897B.toInt(),
    0xFF00695C.toInt(),
    // Cyans / blues
    0xFF0097A7.toInt(),
    0xFF0288D1.toInt(),
    0xFF1565C0.toInt(),
    0xFF283593.toInt(),
    // Purples / violets
    0xFF5E35B1.toInt(),
    0xFF8E24AA.toInt(),
    0xFF6A1B9A.toInt(),
    // Neutrals / earth
    0xFF5D4037.toInt(),
    0xFF795548.toInt(),
    0xFF546E7A.toInt(),
    0xFF455A64.toInt(),
    0xFF37474F.toInt(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateActivityLabelDialog(
    name: String,
    colorArgb: Int,
    onNameChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = PureBlack,
        unfocusedTextColor = PureBlack,
        cursorColor = NavyDeep,
        focusedBorderColor = NavyDeep,
        unfocusedBorderColor = PureBlack.copy(alpha = 0.5f),
        focusedPlaceholderColor = SlateGray,
        unfocusedPlaceholderColor = SlateGray,
        focusedContainerColor = PureWhite,
        unfocusedContainerColor = PureWhite,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("New Label", style = MaterialTheme.typography.titleMedium, color = PureBlack)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Color", color = SlateGray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LabelColorPalette.forEach { swatch ->
                        val selected = swatch == colorArgb
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(swatch), CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) PureBlack else PureBlack.copy(alpha = 0.2f),
                                    shape = CircleShape,
                                )
                                .clickable { onColorChange(swatch) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Label name", color = SlateGray) },
                    singleLine = true,
                    colors = fieldColors,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
                    Button(
                        onClick = onSave,
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyDeep,
                            contentColor = PureWhite,
                            disabledContainerColor = OffWhite,
                            disabledContentColor = SlateGray,
                        ),
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
internal fun ActivityTimeInputChip(
    timeText: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, PureBlack.copy(alpha = 0.5f)),
        color = PureWhite,
    ) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.bodyLarge,
            color = PureBlack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityTimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit,
) {
    val totalMinutes = 24 * 60
    val normalized = (initialMinutes % totalMinutes + totalMinutes) % totalMinutes
    val state = rememberTimePickerState(
        initialHour = normalized / 60,
        initialMinute = normalized % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(state.hour * 60 + state.minute)
            }) {
                Text("OK", color = NavyDeep)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SlateGray)
            }
        },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = PureWhite,
                    clockDialSelectedContentColor = NavyDeep,
                    clockDialUnselectedContentColor = NavyDeep,
                    containerColor = PureWhite,
                    selectorColor = NavyDeep,
                    timeSelectorSelectedContainerColor = Color(0xFFE0E0E0),
                    timeSelectorUnselectedContainerColor = PureWhite,
                    timeSelectorSelectedContentColor = NavyDeep,
                    timeSelectorUnselectedContentColor = NavyDeep,
                ),
            )
        },
        containerColor = PureWhite,
    )
}

internal fun parseActivityHhMmToMinutes(time: String?): Int? {
    if (time.isNullOrBlank()) return null
    val parts = time.trim().split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h.coerceIn(0, 23) * 60 + m.coerceIn(0, 59)
}

internal fun minutesToActivityHhMm(minutes: Int): String {
    val m = (minutes % (24 * 60) + 24 * 60) % (24 * 60)
    return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
}
