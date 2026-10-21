@file:OptIn(ExperimentalFoundationApi::class)

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vm.core.models.LearningTileStatus
import com.vm.core.models.MemoryNoteKind
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.LearningSettings
import com.vm.vector.ui.viewmodel.BrainViewModel
import com.vm.vector.ui.viewmodel.LearnTileSummary

@Composable
fun ModelSection(
    viewModel: BrainViewModel,
    onOpenTile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val tiles by viewModel.learnTiles.collectAsState()
    val settings by viewModel.learningSettings.collectAsState()
    var selectedName by rememberSaveable { mutableStateOf(LearnGroup.IN_PROGRESS.name) }
    val selected = LearnGroup.valueOf(selectedName)
    /** Comma-separated names of collapsed sections. */
    var collapsedNames by rememberSaveable { mutableStateOf("") }
    val collapsed = collapsedNames.split(',').filter { it.isNotEmpty() }.toSet()
    var pendingDelete by remember { mutableStateOf<LearnTileSummary?>(null) }

    val query = uiState.searchQuery.trim()
    val tagFilter = uiState.tagFilter
    val matching = tiles
        .filter { query.isBlank() || it.node.name.contains(query, ignoreCase = true) }
        .filter { tile ->
            tagFilter.all { wanted -> tile.node.tags.any { it.equals(wanted, ignoreCase = true) } }
        }
    val byStatus = matching.groupBy { it.tile.status }
    val sections = selected.sections
        .map { status -> status to sortedForSection(status, byStatus[status].orEmpty()) }
        .filter { (_, list) -> list.isNotEmpty() }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LearnGroup.entries.forEach { group ->
                LearnGroupChip(
                    label = group.label,
                    count = group.sections.sumOf { byStatus[it].orEmpty().size },
                    selected = group == selected,
                    onClick = { selectedName = group.name },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (filtering && tiles.isNotEmpty()) {
            Text(
                text = "${matching.size} of ${tiles.size} tiles",
                style = MaterialTheme.typography.labelMedium,
                color = SlateGray,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        when {
            tiles.isEmpty() -> BrainEmptyState(
                title = "Nothing to learn yet",
                message = "Tap + to create a learn tile, then add notes, links, audio and files to it.",
            )
            sections.isEmpty() -> BrainEmptyState(
                title = if (filtering) "Nothing matches" else "Nothing in ${selected.label}",
                message = if (filtering) "Try another name or clear the tag filter." else emptyGroupMessage(selected),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                sections.forEach { (status, list) ->
                    val isCollapsed = status.name in collapsed
                    item(key = "section_${status.name}") {
                        LearnSectionHeader(
                            status = status,
                            count = list.size,
                            collapsed = isCollapsed,
                            onToggle = {
                                collapsedNames = (if (isCollapsed) collapsed - status.name else collapsed + status.name)
                                    .joinToString(",")
                            },
                        )
                    }
                    if (!isCollapsed) {
                        items(list, key = { it.node.id }) { tile ->
                            LearnTileCard(
                                tile = tile,
                                settings = settings,
                                highlightedTags = tagFilter,
                                onClick = { onOpenTile(tile.node.id) },
                                onSetStatus = { viewModel.setLearnStatus(tile.node.id, it) },
                                onDelete = { pendingDelete = tile },
                                modifier = if (status == LearningTileStatus.ON_HOLD) Modifier.alpha(0.6f) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { tile ->
        DeleteMemoryDialog(
            name = tile.node.name,
            sourceCount = tile.total,
            noun = "learn tile",
            onConfirm = {
                viewModel.deleteLearnTile(tile.node.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

private fun sortedForSection(status: LearningTileStatus, tiles: List<LearnTileSummary>): List<LearnTileSummary> =
    when (status) {
        LearningTileStatus.BACKLOG, LearningTileStatus.PRIORITY -> tiles.sortedByDescending { it.node.createdAt }
        LearningTileStatus.IN_PROGRESS, LearningTileStatus.ON_HOLD -> tiles.sortedByDescending { it.tile.lastActivityAt }
        LearningTileStatus.TO_REVIEW -> tiles.sortedBy { it.tile.nextReviewAt ?: Long.MAX_VALUE }
        LearningTileStatus.LEARNED -> tiles.sortedWith(
            compareBy<LearnTileSummary> { it.tile.nextReviewAt == null }
                .thenBy { it.tile.nextReviewAt ?: Long.MAX_VALUE }
                .thenByDescending { it.tile.learnedAt ?: 0L },
        )
    }

private fun emptyGroupMessage(group: LearnGroup): String = when (group) {
    LearnGroup.BACKLOG -> "New tiles start here until you add something to them. Set a tile to Priority to list it first."
    LearnGroup.IN_PROGRESS -> "Tiles move here as soon as you add a note, link or file."
    LearnGroup.LEARNED -> "Set a tile to Learned from its status menu to start the review schedule."
}

@Composable
private fun LearnGroupChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) NavyDeep else PureWhite)
            .border(1.dp, NavyDeep.copy(alpha = if (selected) 1f else 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) PureWhite else NavyDeep,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = count.toString(),
            color = if (selected) PureWhite.copy(alpha = 0.8f) else SlateGray,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Tappable section divider: status dot, name, count and a chevron. */
@Composable
private fun LearnSectionHeader(
    status: LearningTileStatus,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(learnStatusColor(status), CircleShape),
        )
        Text(
            text = "${learnSectionLabel(status)} ($count)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = SlateGray,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = IconGray.copy(alpha = 0.35f))
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = if (collapsed) "Expand" else "Collapse",
            tint = SlateGray,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp)
                .rotate(if (collapsed) -90f else 0f),
        )
    }
}

@Composable
private fun LearnTileCard(
    tile: LearnTileSummary,
    settings: LearningSettings,
    highlightedTags: Set<String>,
    onClick: () -> Unit,
    onSetStatus: (LearningTileStatus) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val status = tile.tile.status
    Box(modifier = modifier) {
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
                        menuOpen = true
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
                        ModelNetworkIcon,
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
                        text = buildList {
                            add(
                                when (tile.total) {
                                    0 -> "No sources yet"
                                    1 -> "1 source"
                                    else -> "${tile.total} sources"
                                },
                            )
                            if (tile.prerequisites > 0) add("Depends on ${tile.prerequisites}")
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LearnStatusPill(status = status, modifier = Modifier.padding(start = 8.dp))
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
            val hint = learnCardHint(tile.tile, settings)
            val showPending = tile.pendingPrerequisites > 0 && status != LearningTileStatus.LEARNED
            if (hint != null || showPending) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hint != null) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelMedium,
                            color = learnStatusColor(status),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (showPending) {
                        Text(
                            text = "${if (hint != null) " · " else ""}${tile.pendingPrerequisites} prerequisite" +
                                "${if (tile.pendingPrerequisites == 1) "" else "s"} not learned",
                            style = MaterialTheme.typography.labelMedium,
                            color = SlateGray,
                        )
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
        LearnStatusMenu(
            expanded = menuOpen,
            current = status,
            onSelect = {
                menuOpen = false
                onSetStatus(it)
            },
            onDelete = {
                menuOpen = false
                onDelete()
            },
            onDismiss = { menuOpen = false },
        )
    }
}

/** Manual status change menu; [onDelete] adds a Delete item at the bottom. */
@Composable
fun LearnStatusMenu(
    expanded: Boolean,
    current: LearningTileStatus,
    onSelect: (LearningTileStatus) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
    ) {
        LearnMenuStatuses.forEach { status ->
            val isCurrent = status == current
            DropdownMenuItem(
                text = {
                    Text(
                        text = learnStatusLabel(status),
                        color = PureBlack,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(learnStatusColor(status), CircleShape),
                    )
                },
                enabled = !isCurrent,
                onClick = { onSelect(status) },
            )
        }
        if (onDelete != null) {
            HorizontalDivider(color = IconGray.copy(alpha = 0.35f))
            DropdownMenuItem(
                text = { Text("Delete", color = DeleteRed) },
                onClick = onDelete,
            )
        }
    }
}
