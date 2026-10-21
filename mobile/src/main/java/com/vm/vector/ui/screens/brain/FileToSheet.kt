package com.vm.vector.ui.screens.brain

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vm.core.models.BrainNode
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.ui.viewmodel.BrainViewModel

@Composable
fun FileToSheet(
    visible: Boolean,
    viewModel: BrainViewModel,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val tiles by viewModel.memoryTiles.collectAsState()
    MemoryPickerDialog(
        title = "Move to memory",
        tiles = tiles.map { it.node },
        currentNodeId = null,
        onPick = viewModel::filePendingNote,
        onCreate = viewModel::filePendingNoteToNewTile,
        onDismiss = {
            viewModel.closeFileToSheet()
            onDismiss()
        },
    )
}

/** Pick a destination tile (memory by default), the Inbox (null), or create a new tile by name. */
@Composable
fun MemoryPickerDialog(
    title: String,
    tiles: List<BrainNode>,
    currentNodeId: String?,
    onPick: (nodeId: String?) -> Unit,
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit,
    noun: String = "memory",
    tileIcon: ImageVector = Icons.Outlined.Lightbulb,
) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val matches = tiles
        .filter { it.id != currentNodeId }
        .filter { trimmed.isBlank() || it.name.contains(trimmed, ignoreCase = true) }
        .sortedBy { it.name.lowercase() }
    val canCreate = trimmed.isNotBlank() && tiles.none { it.name.equals(trimmed, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text(title, color = PureBlack) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search or create a $noun", color = SlateGray) },
                    colors = brainFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .padding(top = 8.dp),
                ) {
                    if (canCreate) {
                        item(key = "create") {
                            PickerRow(Icons.Filled.Add, "Create \"$trimmed\"", highlight = true) {
                                onCreate(trimmed)
                            }
                        }
                    }
                    if (currentNodeId != null && trimmed.isBlank()) {
                        item(key = "inbox") {
                            PickerRow(Icons.Outlined.Inbox, "Back to Inbox") { onPick(null) }
                        }
                    }
                    items(matches, key = { it.id }) { node ->
                        PickerRow(tileIcon, node.name) { onPick(node.id) }
                    }
                    if (matches.isEmpty() && !canCreate) {
                        item(key = "empty") {
                            Text(
                                text = if (tiles.isEmpty()) "Type a name to create your first $noun" else "No matching $noun",
                                color = SlateGray,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    label: String,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            color = if (highlight) NavyDeep else PureBlack,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
