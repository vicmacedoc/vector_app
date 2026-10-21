package com.vm.vector.ui.screens.brain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vm.core.models.BrainNode
import com.vm.core.models.LearningTileStatus
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.GeminiLearningClient

/** Linked tile chip: status dot + name; tap opens the tile, × removes the link. */
@Composable
fun LearnLinkChip(
    name: String,
    status: LearningTileStatus,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .background(PureWhite)
            .border(1.dp, NavyDeep.copy(alpha = 0.2f), shape)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(learnStatusColor(status), CircleShape),
        )
        Text(
            name,
            color = NavyDeep,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 6.dp)
                .weight(1f, fill = false),
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = "Remove link to $name",
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

@Composable
private fun PickBox(on: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (on) NavyDeep else PureWhite)
            .border(1.5.dp, NavyDeep, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = PureWhite, modifier = Modifier.size(16.dp))
    }
}

/** Search Learn tiles, pick several, or create a new Backlog tile from the query. */
@Composable
fun LearnLinkPickerDialog(
    heading: String,
    candidates: List<BrainNode>,
    statusOf: (String) -> LearningTileStatus,
    onAdd: (Set<String>) -> Unit,
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val trimmed = query.trim()
    val matches = candidates
        .filter { trimmed.isBlank() || it.name.contains(trimmed, ignoreCase = true) }
        .sortedBy { it.name.lowercase() }
    val canCreate = trimmed.isNotBlank() && candidates.none { it.name.equals(trimmed, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text(heading, color = PureBlack) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search or name a new tile", color = PureBlack.copy(alpha = 0.45f)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = NavyDeep) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = brainFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 340.dp)
                        .padding(top = 8.dp),
                ) {
                    if (canCreate) {
                        item(key = "create") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onCreate(trimmed)
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(22.dp))
                                Text(
                                    "Create \"$trimmed\" in Backlog",
                                    color = NavyDeep,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                    items(matches, key = { it.id }) { node ->
                        val on = node.id in picked
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { picked = if (on) picked - node.id else picked + node.id }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PickBox(on)
                            Text(
                                node.name,
                                color = PureBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            )
                            LearnStatusPill(status = statusOf(node.id), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (matches.isEmpty() && !canCreate) {
                        item(key = "empty") {
                            Text(
                                if (candidates.isEmpty()) "No other Learn tiles yet. Type a name to create one." else "No tiles match.",
                                color = SlateGray,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
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
            ) { Text(if (picked.isEmpty()) "Link" else "Link ${picked.size}") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )
}

/** Checklist of Gemini suggestions; everything starts checked. */
@Composable
fun LinkSuggestionsDialog(
    suggestions: GeminiLearningClient.LinkSuggestions,
    nodesById: Map<String, BrainNode>,
    onAccept: (List<GeminiLearningClient.SuggestedLink>, List<GeminiLearningClient.MissingPrerequisite>) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickedLinks by remember { mutableStateOf(suggestions.links.map { it.tileId }.toSet()) }
    var pickedMissing by remember { mutableStateOf(suggestions.missing.map { it.name }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Suggested links", color = PureBlack) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                if (suggestions.links.isNotEmpty()) {
                    item(key = "links_title") { SuggestionHeader("Existing tiles") }
                    items(suggestions.links, key = { "link_${it.tileId}" }) { link ->
                        val on = link.tileId in pickedLinks
                        val name = nodesById[link.tileId]?.name ?: return@items
                        SuggestionRow(
                            on = on,
                            title = if (link.direction == GeminiLearningClient.DEPENDS_ON) {
                                "Depends on $name"
                            } else {
                                "Used in $name"
                            },
                            reason = link.reason,
                            onToggle = { pickedLinks = if (on) pickedLinks - link.tileId else pickedLinks + link.tileId },
                        )
                    }
                }
                if (suggestions.missing.isNotEmpty()) {
                    item(key = "missing_title") { SuggestionHeader("New prerequisites (added to Backlog)") }
                    items(suggestions.missing, key = { "missing_${it.name}" }) { missing ->
                        val on = missing.name in pickedMissing
                        SuggestionRow(
                            on = on,
                            title = missing.name,
                            reason = missing.reason,
                            onToggle = { pickedMissing = if (on) pickedMissing - missing.name else pickedMissing + missing.name },
                        )
                    }
                }
            }
        },
        confirmButton = {
            val count = pickedLinks.size + pickedMissing.size
            Button(
                onClick = {
                    onAccept(
                        suggestions.links.filter { it.tileId in pickedLinks },
                        suggestions.missing.filter { it.name in pickedMissing },
                    )
                },
                enabled = count > 0,
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) { Text(if (count == 0) "Add" else "Add $count") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )
}

@Composable
private fun SuggestionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = SlateGray,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SuggestionRow(on: Boolean, title: String, reason: String, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.padding(top = 2.dp)) { PickBox(on) }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(title, color = PureBlack, fontWeight = FontWeight.Medium)
            if (reason.isNotBlank()) {
                Text(reason, style = MaterialTheme.typography.bodySmall, color = SlateGray)
            }
        }
    }
}

/** Export scope (this tile, or with its prerequisites) and destination (save or share). */
@Composable
fun LearnExportDialog(
    prerequisiteCount: Int,
    onSave: (includePrerequisites: Boolean) -> Unit,
    onShare: (includePrerequisites: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var include by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Export as .txt", color = PureBlack) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Joins the tile's notes, links and file names into one text file.",
                    color = PureBlack.copy(alpha = 0.75f),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ExportOption(selected = !include, label = "This tile only", onClick = { include = false })
                ExportOption(
                    selected = include,
                    label = "Include prerequisites ($prerequisiteCount)",
                    enabled = prerequisiteCount > 0,
                    onClick = { include = true },
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onShare(include)
                        onDismiss()
                    },
                ) { Text("Share", color = NavyDeep) }
                Button(
                    onClick = {
                        onSave(include)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                ) { Text("Save") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )
}

@Composable
private fun ExportOption(selected: Boolean, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = NavyDeep),
        )
        Text(label, color = if (enabled) PureBlack else SlateGray)
    }
}
