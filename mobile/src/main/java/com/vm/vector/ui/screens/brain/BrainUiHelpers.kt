@file:OptIn(ExperimentalMaterial3Api::class)

package com.vm.vector.ui.screens.brain

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vm.core.models.MemoryNoteKind
import com.vm.core.models.MotionPriority
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PriorityHigh
import com.vm.core.ui.theme.PriorityHold
import com.vm.core.ui.theme.PriorityLow
import com.vm.core.ui.theme.PriorityMid
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.ui.viewmodel.BrainTab

@Composable
fun VectorSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    label: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val sliderColors = SliderDefaults.colors(
        thumbColor = NavyDeep,
        activeTrackColor = NavyDeep,
        inactiveTrackColor = OffWhite,
        disabledThumbColor = IconGray,
        disabledActiveTrackColor = IconGray,
        disabledInactiveTrackColor = OffWhite,
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) NavyDeep else SlateGray,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = sliderColors,
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    enabled = enabled,
                    colors = sliderColors,
                    modifier = Modifier.height(4.dp),
                )
            },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(if (enabled) NavyDeep else IconGray, CircleShape)
                        .border(2.dp, if (enabled) PureBlack else SlateGray, CircleShape),
                )
            },
        )
    }
}

fun motionDueLabel(dueAt: Long?, now: Long = System.currentTimeMillis()): String {
    if (dueAt == null) return "No date"
    val cal = java.util.Calendar.getInstance()
    val todayStart = cal.apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val todayEnd = todayStart + java.util.concurrent.TimeUnit.DAYS.toMillis(1)
    return when {
        dueAt < todayStart -> "Overdue"
        dueAt in todayStart until todayEnd -> "Today"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(java.util.Date(dueAt))
    }
}

fun motionPriorityColor(priority: MotionPriority): Color = when (priority) {
    MotionPriority.HIGH -> PriorityHigh
    MotionPriority.MEDIUM -> PriorityMid
    MotionPriority.LOW -> PriorityLow
    MotionPriority.NONE -> PriorityHold
}

fun motionPriorityLabel(priority: MotionPriority): String = when (priority) {
    MotionPriority.HIGH -> "High"
    MotionPriority.MEDIUM -> "Medium"
    MotionPriority.LOW -> "Low"
    MotionPriority.NONE -> "None"
}

fun brainTabIcon(tab: BrainTab): ImageVector = when (tab) {
    BrainTab.MEMORY -> Icons.Outlined.Lightbulb
    BrainTab.MODEL -> ModelNetworkIcon
    BrainTab.MOTION -> Icons.Outlined.Bolt
    BrainTab.INBOX -> Icons.Outlined.Inbox
}

fun memoryKindIcon(kind: MemoryNoteKind): ImageVector = when (kind) {
    MemoryNoteKind.TEXT -> Icons.Outlined.Description
    MemoryNoteKind.AUDIO -> Icons.Outlined.Mic
    MemoryNoteKind.IMAGE -> Icons.Outlined.Image
    MemoryNoteKind.LINK -> Icons.Outlined.Link
    MemoryNoteKind.PDF -> Icons.Outlined.PictureAsPdf
    MemoryNoteKind.VIDEO -> Icons.Outlined.Movie
    MemoryNoteKind.FILE -> Icons.Outlined.AttachFile
}

fun memoryKindLabel(kind: MemoryNoteKind): String = when (kind) {
    MemoryNoteKind.TEXT -> "Text"
    MemoryNoteKind.AUDIO -> "Audio"
    MemoryNoteKind.IMAGE -> "Image"
    MemoryNoteKind.LINK -> "Link"
    MemoryNoteKind.PDF -> "PDF"
    MemoryNoteKind.VIDEO -> "Video"
    MemoryNoteKind.FILE -> "File"
}

/** Rounded square with a pen, used for the Home quick-capture button. */
val EditSquareIcon: ImageVector by lazy {
    val ink = SolidColor(Color.Black)
    ImageVector.Builder(
        name = "EditSquare",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .path(
            stroke = ink,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 4f)
            horizontalLineTo(7f)
            curveTo(5.343f, 4f, 4f, 5.343f, 4f, 7f)
            verticalLineTo(17f)
            curveTo(4f, 18.657f, 5.343f, 20f, 7f, 20f)
            horizontalLineTo(17f)
            curveTo(18.657f, 20f, 20f, 18.657f, 20f, 17f)
            verticalLineTo(12f)
        }
        .path(stroke = ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(10.5f, 13.5f)
            lineTo(20f, 4f)
        }
        .path(fill = ink) {
            moveTo(8.5f, 15.5f)
            lineTo(9.65f, 12.65f)
            lineTo(11.35f, 14.35f)
            close()
        }
        .build()
}

val ModelNetworkIcon: ImageVector by lazy {
    val ink = SolidColor(Color.Black)
    val topLeft = 7f to 3.6f
    val topRight = 17f to 3.6f
    val left = 2.4f to 12f
    val right = 21.6f to 12f
    val bottomLeft = 7f to 20.4f
    val bottomRight = 17f to 20.4f
    val hubTop = 14.8f to 9f
    val hubBottom = 10.4f to 14.6f
    val nodes = listOf(topLeft, topRight, left, right, bottomLeft, bottomRight, hubTop, hubBottom)
    val edges = listOf(
        topLeft to topRight, topRight to right, right to bottomRight,
        bottomRight to bottomLeft, bottomLeft to left, left to topLeft,
        topLeft to hubTop, topRight to hubTop, hubTop to right, hubTop to hubBottom,
        topLeft to hubBottom, left to hubBottom, hubBottom to right,
        hubBottom to bottomLeft, hubBottom to bottomRight,
    )
    val nodeRadius = 2f
    ImageVector.Builder(
        name = "ModelNetwork",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .path(stroke = ink, strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round) {
            edges.forEach { (from, to) ->
                moveTo(from.first, from.second)
                lineTo(to.first, to.second)
            }
        }
        .path(fill = ink) {
            nodes.forEach { (cx, cy) ->
                moveTo(cx - nodeRadius, cy)
                arcToRelative(nodeRadius, nodeRadius, 0f, true, true, nodeRadius * 2, 0f)
                arcToRelative(nodeRadius, nodeRadius, 0f, true, true, -nodeRadius * 2, 0f)
                close()
            }
        }
        .build()
}

data class BrainTabBadge(
    val text: String,
    val color: Color = ElectricBlue,
    val textColor: Color = PureWhite,
)

/** Icon-only tabs; the selected one widens to show its name. */
@Composable
fun BrainTabBar(
    selected: BrainTab,
    onSelect: (BrainTab) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<BrainTab, BrainTabBadge> = emptyMap(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrainTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val weight by animateFloatAsState(if (isSelected) 2.4f else 1f, label = "brainTabWeight")
            val container by animateColorAsState(if (isSelected) NavyDeep else OffWhite, label = "brainTabBg")
            val content by animateColorAsState(if (isSelected) PureWhite else NavyDeep, label = "brainTabFg")
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(container)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) }),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = brainTabIcon(tab),
                        contentDescription = if (isSelected) null else tab.label,
                        tint = content,
                        modifier = Modifier.size(24.dp),
                    )
                    AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = content,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                badges[tab]?.let { badge ->
                    Text(
                        text = badge.text,
                        color = badge.textColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(badge.color, CircleShape)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}
