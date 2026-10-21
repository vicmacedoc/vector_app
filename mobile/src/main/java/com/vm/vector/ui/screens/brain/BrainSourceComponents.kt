@file:OptIn(ExperimentalFoundationApi::class)

package com.vm.vector.ui.screens.brain

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vm.core.models.MemoryNote
import com.vm.core.models.MemoryNoteKind
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.BrainRepository
import com.vm.vector.ui.util.OpenWithLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Tile-page tag: white pill with a navy hairline, blue `#` and a quiet remove ×. */
@Composable
fun TileTagPill(text: String, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .background(PureWhite)
            .border(1.dp, NavyDeep.copy(alpha = 0.2f), shape)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "#",
            color = ElectricBlue,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text,
            color = NavyDeep,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(start = 3.dp),
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = "Remove $text",
            tint = SlateGray,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                .padding(3.dp),
        )
    }
}

/** Off-white pill with a `+` and a label; [label] defaults to "Tag". */
@Composable
fun AddPill(onClick: () -> Unit, label: String = "Tag") {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .background(OffWhite)
            .border(1.dp, NavyDeep.copy(alpha = 0.08f), shape)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(16.dp))
        Text(
            label,
            color = NavyDeep,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
fun SourceCard(
    note: MemoryNote,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, IconGray.copy(alpha = 0.35f), shape)
            .background(PureWhite)
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = ElectricBlue,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(14.dp),
                    )
                }
                Text(
                    text = note.title.ifBlank { memoryKindLabel(note.kind) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = PureBlack,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val preview = note.description.takeIf { it.isNotBlank() }
                ?: note.url
                ?: note.body.takeIf { note.kind == MemoryNoteKind.TEXT }
                    ?.lineSequence()?.firstOrNull { it.isNotBlank() }
            Text(
                text = preview ?: "${memoryKindLabel(note.kind)} · ${brainRelativeTime(note.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = SlateGray)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = PureWhite,
            ) {
                DropdownMenuItem(
                    text = { Text("Move to…", color = PureBlack) },
                    onClick = {
                        menuOpen = false
                        onMove()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = DeleteRed) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
fun DeleteSourceDialog(note: MemoryNote, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Delete \"${note.title.ifBlank { memoryKindLabel(note.kind) }}\"?", color = PureBlack) },
        text = {
            Text(
                if (note.assetId != null) "Its file is removed from this phone and from Drive." else "This can't be undone.",
                color = PureBlack.copy(alpha = 0.75f),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = PureWhite),
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )
}

@Composable
fun SingleFieldDialog(
    title: String,
    label: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text(title, color = PureBlack) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = brainFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(value)
                    onDismiss()
                },
                enabled = value.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )
}

@Composable
fun LinkDialog(
    onAdd: (url: String, title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Add link", color = PureBlack) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = brainFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (optional)") },
                    singleLine = true,
                    colors = brainFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(url, title)
                    onDismiss()
                },
                enabled = url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )
}

@Composable
fun InboxPickerDialog(
    notes: List<MemoryNote>,
    onAdd: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Add from Inbox", color = PureBlack) },
        text = {
            if (notes.isEmpty()) {
                Text("Your Inbox is empty.", color = SlateGray)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(notes, key = { it.id }) { note ->
                        val on = note.id in picked
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { picked = if (on) picked - note.id else picked + note.id }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (on) NavyDeep else PureWhite)
                                    .border(1.5.dp, NavyDeep, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = PureWhite, modifier = Modifier.size(16.dp))
                                }
                            }
                            Icon(
                                memoryKindIcon(note.kind),
                                contentDescription = null,
                                tint = NavyDeep,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .size(20.dp),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp),
                            ) {
                                Text(
                                    note.title.ifBlank { memoryKindLabel(note.kind) },
                                    color = PureBlack,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    brainRelativeTime(note.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateGray,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(picked)
                    onDismiss()
                },
                enabled = picked.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (picked.isEmpty()) "Add" else "Add ${picked.size}", modifier = Modifier.padding(start = 4.dp))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )
}

fun openMemorySource(
    note: MemoryNote,
    context: Context,
    scope: CoroutineScope,
    repository: BrainRepository,
    onOpenPlayer: (String) -> Unit,
    onOpenText: (String) -> Unit,
) {
    val assetId = note.assetId
    when {
        note.kind == MemoryNoteKind.AUDIO && assetId != null -> onOpenPlayer(assetId)
        note.kind == MemoryNoteKind.TEXT -> onOpenText(note.id)
        note.url != null -> OpenWithLauncher.openUrl(context, note.url!!)
        assetId != null -> scope.launch {
            val asset = repository.getAsset(assetId) ?: return@launch
            val file = repository.fileStore().absoluteFile(asset.localRelativePath)
            OpenWithLauncher.openLocalFile(context, file, asset.mimeType)
        }
    }
}
