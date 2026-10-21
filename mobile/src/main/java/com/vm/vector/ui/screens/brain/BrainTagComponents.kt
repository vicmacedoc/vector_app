@file:OptIn(ExperimentalLayoutApi::class)

package com.vm.vector.ui.screens.brain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vm.core.models.BrainTag
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray

@Composable
fun brainFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PureBlack,
    unfocusedTextColor = PureBlack,
    focusedContainerColor = PureWhite,
    unfocusedContainerColor = PureWhite,
    focusedBorderColor = NavyDeep,
    unfocusedBorderColor = NavyDeep.copy(alpha = 0.35f),
    focusedLabelColor = NavyDeep,
    unfocusedLabelColor = SlateGray,
    cursorColor = NavyDeep,
)

/** Tag pill. Filled navy when [selected]; shows an × when [onRemove] is set. */
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) NavyDeep else PureWhite)
            .border(1.dp, NavyDeep.copy(alpha = if (selected) 1f else 0.35f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                start = if (compact) 8.dp else 10.dp,
                end = if (onRemove != null) 4.dp else if (compact) 8.dp else 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (selected) PureWhite else NavyDeep,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(vertical = if (compact) 3.dp else 6.dp),
        )
        if (onRemove != null) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove $text",
                tint = if (selected) PureWhite else NavyDeep,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
            )
        }
    }
}

/** Search the tag library, add a tag, create a new one, or delete one from the library. */
@Composable
fun TagPickerDialog(
    selected: List<String>,
    library: List<BrainTag>,
    onPick: (String) -> Unit,
    onDeleteFromLibrary: (BrainTag) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val chosen = remember(selected) { selected.map { it.lowercase() }.toSet() }
    val matches = library
        .filter { it.name.lowercase() !in chosen }
        .filter { trimmed.isBlank() || it.name.contains(trimmed, ignoreCase = true) }
    val canCreate = trimmed.isNotBlank() &&
        library.none { it.name.equals(trimmed, ignoreCase = true) } &&
        trimmed.lowercase() !in chosen

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Add tag", color = PureBlack) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search or create", color = SlateGray) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val exact = library.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
                            when {
                                exact != null -> onPick(exact.name)
                                canCreate -> onPick(trimmed)
                                else -> return@KeyboardActions
                            }
                            query = ""
                        },
                    ),
                    colors = brainFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .padding(top = 8.dp),
                ) {
                    if (canCreate) {
                        item(key = "create") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onPick(trimmed)
                                        query = ""
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = NavyDeep,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Create \"$trimmed\"",
                                    color = NavyDeep,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                    items(matches, key = { it.id }) { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPick(tag.name) }
                                .padding(start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = tag.name,
                                color = PureBlack,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onDeleteFromLibrary(tag) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete ${tag.name} from library",
                                    tint = SlateGray,
                                )
                            }
                        }
                    }
                    if (matches.isEmpty() && !canCreate) {
                        item(key = "empty") {
                            Text(
                                text = if (library.isEmpty()) "Library is empty" else "No matching tags",
                                color = SlateGray,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = NavyDeep) }
        },
    )
}

/** Multi-select over the tags in use; a memory must carry every selected tag to show. */
@Composable
fun TagFilterDialog(
    available: List<String>,
    selected: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Filter by tags", color = PureBlack) },
        text = {
            if (available.isEmpty()) {
                Text("No memory has tags yet.", color = SlateGray)
            } else {
                FlowRow(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    available.forEach { tag ->
                        val on = tag in draft
                        TagChip(
                            text = tag,
                            selected = on,
                            onClick = { draft = if (on) draft - tag else draft + tag },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(draft)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onApply(emptySet())
                    onDismiss()
                },
                enabled = selected.isNotEmpty() || draft.isNotEmpty(),
            ) { Text("Clear", color = NavyDeep) }
        },
    )
}

/** Empty-state block used by Brain lists. */
@Composable
fun BrainEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(OffWhite)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = NavyDeep)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = SlateGray,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
