@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.vm.vector.ui.screens.brain

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.vm.core.models.BrainTag
import com.vm.core.models.MemoryNoteKind
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.ui.viewmodel.BrainViewModel
import com.vm.vector.ui.viewmodel.MemoryTileSummary

@Composable
fun MemorySection(
    viewModel: BrainViewModel,
    onOpenMemory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val tiles by viewModel.memoryTiles.collectAsState()
    var pendingDelete by remember { mutableStateOf<MemoryTileSummary?>(null) }

    val query = uiState.searchQuery.trim()
    val tagFilter = uiState.tagFilter
    val shown = tiles
        .filter { query.isBlank() || it.node.name.contains(query, ignoreCase = true) }
        .filter { tile ->
            tagFilter.all { wanted -> tile.node.tags.any { it.equals(wanted, ignoreCase = true) } }
        }
        .sortedByDescending { it.node.createdAt }
    val filtering = query.isNotBlank() || tagFilter.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        if (tagFilter.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(tagFilter.sorted(), key = { it }) { tag ->
                    TagChip(
                        text = tag,
                        compact = true,
                        onRemove = { viewModel.setTagFilter(tagFilter - tag) },
                    )
                }
            }
        }
        if (filtering && tiles.isNotEmpty()) {
            Text(
                text = "${shown.size} of ${tiles.size} memories",
                style = MaterialTheme.typography.labelMedium,
                color = SlateGray,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        when {
            tiles.isEmpty() -> BrainEmptyState(
                title = "No memories yet",
                message = "Tap + to create a memory, then add notes, links, audio and files to it.",
            )
            shown.isEmpty() -> BrainEmptyState(
                title = "Nothing matches",
                message = "Try another name or clear the tag filter.",
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.node.id }) { tile ->
                    MemoryTileCard(
                        tile = tile,
                        highlightedTags = tagFilter,
                        onClick = { onOpenMemory(tile.node.id) },
                        onLongClick = { pendingDelete = tile },
                    )
                }
            }
        }
    }

    pendingDelete?.let { tile ->
        DeleteMemoryDialog(
            name = tile.node.name,
            sourceCount = tile.total,
            onConfirm = {
                viewModel.deleteMemoryTile(tile.node.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun MemoryTileCard(
    tile: MemoryTileSummary,
    highlightedTags: Set<String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    Column(
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
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OffWhite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Lightbulb,
                    contentDescription = null,
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
                    text = tile.node.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PureBlack,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (tile.total) {
                        0 -> "No sources yet"
                        1 -> "1 source"
                        else -> "${tile.total} sources"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateGray,
                )
            }
        }
        if (tile.counts.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MemoryNoteKind.entries.forEach { kind ->
                    val count = tile.counts[kind] ?: return@forEach
                    KindCountPill(kind, count)
                }
            }
        }
        if (tile.node.tags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(tile.node.tags) { tag ->
                    TagChip(
                        text = tag,
                        compact = true,
                        selected = highlightedTags.any { it.equals(tag, ignoreCase = true) },
                    )
                }
            }
        }
    }
}

@Composable
fun KindCountPill(kind: MemoryNoteKind, count: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(OffWhite)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            memoryKindIcon(kind),
            contentDescription = memoryKindLabel(kind),
            tint = NavyDeep,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = NavyDeep,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
fun DeleteMemoryDialog(
    name: String,
    sourceCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    noun: String = "memory",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Delete \"$name\"?", color = PureBlack) },
        text = {
            Text(
                text = if (sourceCount == 0) {
                    "This $noun has no sources."
                } else {
                    "Its $sourceCount source${if (sourceCount == 1) "" else "s"} and their files will be " +
                        "deleted from this phone and from Drive."
                },
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
fun NewMemoryDialog(
    library: List<BrainTag>,
    onCreate: (name: String, tags: List<String>) -> Unit,
    onDismiss: () -> Unit,
    heading: String = "New memory",
    initialName: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
    var tags by remember { mutableStateOf(listOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text(heading, color = PureBlack) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = brainFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (library.isNotEmpty()) {
                    Text(
                        "Tags",
                        style = MaterialTheme.typography.labelLarge,
                        color = SlateGray,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                    FlowRow(
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        library.forEach { tag ->
                            val on = tag.name in tags
                            TagChip(
                                text = tag.name,
                                compact = true,
                                selected = on,
                                onClick = { tags = if (on) tags - tag.name else tags + tag.name },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(name.trim(), tags)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )
}
