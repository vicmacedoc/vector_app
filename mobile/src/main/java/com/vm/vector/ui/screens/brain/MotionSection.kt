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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.ui.viewmodel.BrainViewModel
import com.vm.vector.ui.viewmodel.MotionListSummary
import com.vm.vector.ui.viewmodel.MotionScoreSummary
import com.vm.vector.ui.viewmodel.SupermarketTileState
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun MotionSection(
    viewModel: BrainViewModel,
    onOpenSupermarket: () -> Unit,
    onOpenList: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val score by viewModel.motionScore.collectAsState()
    val lists by viewModel.motionLists.collectAsState()
    val supermarket by viewModel.supermarketTile.collectAsState()
    val toBuy by viewModel.supermarketToBuy.collectAsState()
    var pendingDelete by remember { mutableStateOf<MotionListSummary?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshSupermarketSummary() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "score") { MotionScoreCard(score) }
        item(key = "supermarket") {
            SupermarketTile(state = supermarket, toBuy = toBuy, onClick = onOpenSupermarket)
        }
        items(lists, key = { it.node.id }) { list ->
            MotionListCard(
                list = list,
                onClick = { onOpenList(list.node.id) },
                onLongClick = { pendingDelete = list },
            )
        }
        if (lists.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Tap + to create a to-do list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateGray,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }

    pendingDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = PureWhite,
            title = { Text("Delete \"${list.node.name}\"?", color = PureBlack) },
            text = {
                Text(
                    if (list.open == 0) {
                        "This list has no open items. Points already earned are kept."
                    } else {
                        "Its ${list.open} open item${if (list.open == 1) "" else "s"} will be deleted. " +
                            "Points already earned are kept."
                    },
                    color = PureBlack.copy(alpha = 0.75f),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMotionList(list.node.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = PureWhite),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = PureBlack) }
            },
        )
    }
}

@Composable
private fun MotionScoreCard(score: MotionScoreSummary) {
    val shape = RoundedCornerShape(14.dp)
    val progress = if (score.target > 0) (score.today.toFloat() / score.target).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(OffWhite)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                score.today.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = NavyDeep,
            )
            Text(
                " / ${score.target} pts today",
                style = MaterialTheme.typography.titleSmall,
                color = SlateGray,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                when (score.streak) {
                    0 -> "No streak"
                    1 -> "1-day streak"
                    else -> "${score.streak}-day streak"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (score.streak > 0) NavyDeep else SlateGray,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(PureWhite),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(3.dp))
                    .background(NavyDeep),
            )
        }
        if (score.lastSevenDays.isNotEmpty()) {
            WeekBars(days = score.lastSevenDays, target = score.target)
        }
    }
}

@Composable
private fun WeekBars(days: List<Pair<LocalDate, Int>>, target: Int) {
    val max = maxOf(target, days.maxOf { it.second }, 1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEachIndexed { index, (day, points) ->
            val isToday = index == days.lastIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val fraction = (points.toFloat() / max).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor(points, target)),
                    )
                }
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) NavyDeep else SlateGray,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun barColor(points: Int, target: Int): Color = when {
    points <= 0 -> IconGray.copy(alpha = 0.35f)
    points >= target -> NavyDeep
    else -> NavyDeep.copy(alpha = 0.4f)
}

@Composable
private fun SupermarketTile(state: SupermarketTileState?, toBuy: Int?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NavyDeep)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = PureWhite)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                "Supermarket",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PureWhite,
            )
            Text(
                listOfNotNull(toBuy?.let { "$it to buy" }, state?.let(::supermarketTripLabel))
                    .joinToString(" · ")
                    .ifBlank { "Shopping list" },
                style = MaterialTheme.typography.bodySmall,
                color = PureWhite.copy(alpha = 0.8f),
            )
        }
        if (state != null && state.cycle.open && state.worthNow > 0) {
            PointsChip(points = state.worthNow, inverted = true)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = PureWhite)
    }
}

private fun supermarketTripLabel(state: SupermarketTileState): String {
    val today = LocalDate.now()
    val due = state.cycle.dueDate
    val dayName = due.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
    if (!state.cycle.open) {
        val earnedToday = state.lastTripDate == today && state.lastTripPoints != null
        return if (earnedToday) "Trip logged today" else "Done this week · next $dayName"
    }
    val days = ChronoUnit.DAYS.between(today, due)
    return when {
        days > 1 -> "Trip due $dayName"
        days == 1L -> "Trip due tomorrow"
        days == 0L -> "Trip due today"
        days == -1L -> "1 day late"
        else -> "${-days} days late"
    }
}

/** "+15" pill; [inverted] for navy backgrounds. */
@Composable
fun PointsChip(points: Int, modifier: Modifier = Modifier, inverted: Boolean = false, prefix: String = "+") {
    Text(
        "$prefix$points",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (inverted) NavyDeep else PureWhite,
        modifier = modifier
            .padding(horizontal = 6.dp)
            .background(if (inverted) PureWhite else NavyDeep, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun MotionListCard(
    list: MotionListSummary,
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
                    Icons.Outlined.Checklist,
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
                    text = list.node.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PureBlack,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (list.open) {
                        0 -> "All clear"
                        1 -> "1 open item"
                        else -> "${list.open} open items"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateGray,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = NavyDeep)
        }
        if (list.overdue > 0 || list.today > 0 || list.week > 0) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (list.overdue > 0) DueCountPill("${list.overdue} overdue", DeleteRed)
                if (list.today > 0) DueCountPill("${list.today} today", NavyDeep)
                if (list.week > 0) DueCountPill("${list.week} this week", SlateGray)
            }
        }
    }
}

@Composable
private fun DueCountPill(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(OffWhite)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
