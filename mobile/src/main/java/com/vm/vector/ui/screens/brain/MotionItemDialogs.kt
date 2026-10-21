@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.vm.vector.ui.screens.brain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vm.core.models.MotionPriority
import com.vm.core.models.MotionRecurrence
import com.vm.core.models.MotionScoring
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.GeminiMotionClient
import com.vm.vector.data.MotionItemDraft
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DefaultDueTime: LocalTime = LocalTime.of(GeminiMotionClient.DEFAULT_DUE_HOUR, 0)

private fun dueDateTime(dueAt: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(dueAt), ZoneId.systemDefault())

private fun toMillis(date: LocalDate, time: LocalTime): Long =
    date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** "Today", "Tomorrow", "Mon, Oct 12" (+ " · 09:30" unless it's the default end-of-day time). */
fun motionDeadlineLabel(dueAt: Long?): String {
    if (dueAt == null) return "No deadline"
    val due = dueDateTime(dueAt)
    val today = LocalDate.now()
    val day = when (due.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> due.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
    }
    val time = due.toLocalTime()
    return if (time == DefaultDueTime) day else "$day · ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

fun formatMotionAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(Locale.US, value)

@Composable
fun MotionPrioritySelector(
    priority: MotionPriority,
    onChange: (MotionPriority) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MotionPriority.entries.forEach { option ->
            val selected = option == priority
            val color = motionPriorityColor(option)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) color else PureWhite)
                    .border(1.dp, color.copy(alpha = if (selected) 1f else 0.5f), RoundedCornerShape(8.dp))
                    .clickable { onChange(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    motionPriorityLabel(option),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) PureWhite else PureBlack,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Deadline chip with Today / Tomorrow shortcuts, a date picker and an optional time. */
@Composable
fun MotionDeadlineField(
    dueAt: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    showTime: Boolean = true,
) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val currentTime = dueAt?.let { dueDateTime(it).toLocalTime() } ?: DefaultDueTime

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SmallChip(
            text = motionDeadlineLabel(dueAt),
            icon = Icons.Outlined.Event,
            selected = dueAt != null,
            onClick = { pickDate = true },
        )
        if (dueAt == null) {
            SmallChip("Today", onClick = { onChange(toMillis(today, DefaultDueTime)) })
            SmallChip("Tomorrow", onClick = { onChange(toMillis(today.plusDays(1), DefaultDueTime)) })
        } else {
            if (showTime) {
                SmallChip(
                    text = if (currentTime == DefaultDueTime) "Add time" else currentTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    icon = Icons.Outlined.Schedule,
                    onClick = { pickTime = true },
                )
            }
            SmallChip("Clear", icon = Icons.Default.Close, onClick = { onChange(null) })
        }
    }

    if (pickDate) {
        val initialUtc = (dueAt?.let { dueDateTime(it).toLocalDate() } ?: today)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(toMillis(date, currentTime))
                    }
                    pickDate = false
                }) { Text("OK", color = NavyDeep) }
            },
            dismissButton = {
                TextButton(onClick = { pickDate = false }) { Text("Cancel", color = PureBlack) }
            },
        ) {
            DatePicker(
                state = state,
                colors = DatePickerDefaults.colors(
                    containerColor = PureWhite,
                    titleContentColor = PureBlack,
                    headlineContentColor = PureBlack,
                    weekdayContentColor = PureBlack,
                    subheadContentColor = PureBlack,
                    navigationContentColor = PureBlack,
                    selectedDayContainerColor = NavyDeep,
                    selectedDayContentColor = PureWhite,
                    todayDateBorderColor = NavyDeep,
                    todayContentColor = NavyDeep,
                    dayContentColor = PureBlack,
                ),
            )
        }
    }

    if (pickTime && dueAt != null) {
        val state = rememberTimePickerState(
            initialHour = currentTime.hour,
            initialMinute = currentTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = PureWhite,
            confirmButton = {
                TextButton(onClick = {
                    onChange(toMillis(dueDateTime(dueAt).toLocalDate(), LocalTime.of(state.hour, state.minute)))
                    pickTime = false
                }) { Text("OK", color = NavyDeep) }
            },
            dismissButton = {
                TextButton(onClick = { pickTime = false }) { Text("Cancel", color = SlateGray) }
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
        )
    }
}

@Composable
private fun SmallChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) NavyDeep else OffWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) PureWhite else NavyDeep,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 2.dp),
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) PureWhite else NavyDeep,
        )
    }
}

/** Points field: blank follows the priority's base points. */
@Composable
fun MotionPointsField(
    priority: MotionPriority,
    points: Int?,
    onChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val base = MotionScoring.basePoints(priority)
    OutlinedTextField(
        value = points?.toString().orEmpty(),
        onValueChange = { raw -> onChange(raw.filter(Char::isDigit).take(4).toIntOrNull()) },
        label = { Text("Points") },
        placeholder = { Text(base.toString()) },
        supportingText = { Text("Default for ${motionPriorityLabel(priority)}: $base") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = brainFieldColors(),
        modifier = modifier,
    )
}

@Composable
fun MotionItemDialog(
    initial: MotionItemDraft,
    heading: String,
    onSave: (MotionItemDraft) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    confirmLabel: String = "Save",
) {
    var draft by remember(initial.key) { mutableStateOf(initial) }
    var hasTarget by remember(initial.key) { mutableStateOf(initial.target != null) }
    var targetText by remember(initial.key) { mutableStateOf(initial.target?.let(::formatMotionAmount).orEmpty()) }
    val targetValue = targetText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
    val valid = draft.title.isNotBlank() && (!hasTarget || targetValue != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text(heading, color = PureBlack) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = brainFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { draft = draft.copy(notes = it) },
                    label = { Text("Description (optional)") },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = brainFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionLabel("Deadline")
                MotionDeadlineField(
                    dueAt = draft.dueAt,
                    onChange = { due ->
                        draft = draft.copy(
                            dueAt = due,
                            recurrence = if (due == null) MotionRecurrence.NONE else draft.recurrence,
                        )
                    },
                )
                if (draft.dueAt != null) {
                    SectionLabel("Repeat")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MotionRecurrence.entries.forEach { option ->
                            SmallChip(
                                text = option.name.lowercase().replaceFirstChar { it.uppercase() }
                                    .let { if (option == MotionRecurrence.NONE) "Never" else it },
                                selected = draft.recurrence == option,
                                onClick = { draft = draft.copy(recurrence = option) },
                            )
                        }
                    }
                }
                SectionLabel("Priority")
                MotionPrioritySelector(
                    priority = draft.priority,
                    onChange = { draft = draft.copy(priority = it) },
                )
                MotionPointsField(
                    priority = draft.priority,
                    points = draft.points,
                    onChange = { draft = draft.copy(points = it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Numeric target", style = MaterialTheme.typography.bodyLarge, color = PureBlack)
                        Text(
                            "Done once progress reaches the minimum",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateGray,
                        )
                    }
                    Switch(
                        checked = hasTarget,
                        onCheckedChange = { hasTarget = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureWhite,
                            checkedTrackColor = NavyDeep,
                            uncheckedThumbColor = IconGray,
                            uncheckedTrackColor = OffWhite,
                        ),
                    )
                }
                if (hasTarget) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = targetText,
                            onValueChange = { raw -> targetText = raw.filter { it.isDigit() || it == '.' || it == ',' }.take(7) },
                            label = { Text("Min target") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = brainFieldColors(),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = draft.unit.orEmpty(),
                            onValueChange = { draft = draft.copy(unit = it.take(16)) },
                            label = { Text("Unit") },
                            singleLine = true,
                            colors = brainFieldColors(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete item", color = DeleteRed) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        draft.copy(
                            title = draft.title.trim(),
                            target = if (hasTarget) targetValue else null,
                            unit = if (hasTarget) draft.unit?.trim()?.takeIf { it.isNotEmpty() } else null,
                        )
                    )
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = SlateGray)
}

/**
 * Checkbox list of drafts (all checked by default). Tapping a row opens [MotionItemDialog] so it can
 * be edited before confirming; [onConfirm] receives the checked drafts with their edits.
 */
@Composable
fun MotionItemPickerDialog(
    heading: String,
    message: String?,
    drafts: List<MotionItemDraft>,
    confirmLabel: (Int) -> String,
    onConfirm: (List<MotionItemDraft>) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember(drafts) { mutableStateOf(drafts) }
    var checked by remember(drafts) { mutableStateOf(drafts.map { it.key }.toSet()) }
    var editing by remember { mutableStateOf<MotionItemDraft?>(null) }
    val allChecked = checked.size == current.size && current.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text(heading, color = PureBlack) },
        text = {
            Column {
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateGray,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { checked = if (allChecked) emptySet() else current.map { it.key }.toSet() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = allChecked,
                        onCheckedChange = { checked = if (it) current.map { d -> d.key }.toSet() else emptySet() },
                        colors = motionCheckboxColors(),
                    )
                    Text("All (${current.size})", style = MaterialTheme.typography.labelLarge, color = PureBlack)
                }
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(current, key = { it.key }) { draft ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { editing = draft }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = draft.key in checked,
                                onCheckedChange = { on -> checked = if (on) checked + draft.key else checked - draft.key },
                                colors = motionCheckboxColors(),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    draft.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = PureBlack,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    draftSummary(draft),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            PointsChip(points = draft.effectivePoints, prefix = "")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(current.filter { it.key in checked }) },
                enabled = checked.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) { Text(confirmLabel(checked.size)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )

    editing?.let { draft ->
        MotionItemDialog(
            initial = draft,
            heading = "Edit item",
            onSave = { edited ->
                current = current.map { if (it.key == edited.key) edited else it }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private fun draftSummary(draft: MotionItemDraft): String = listOfNotNull(
    motionPriorityLabel(draft.priority).takeIf { draft.priority != MotionPriority.NONE },
    draft.dueAt?.let(::motionDeadlineLabel),
    draft.target?.let { "Target ${formatMotionAmount(it)}${draft.unit?.let { u -> " $u" }.orEmpty()}" },
).joinToString(" · ").ifBlank { "No deadline" }

@Composable
fun motionCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = NavyDeep,
    uncheckedColor = NavyDeep.copy(alpha = 0.6f),
    checkmarkColor = PureWhite,
)
