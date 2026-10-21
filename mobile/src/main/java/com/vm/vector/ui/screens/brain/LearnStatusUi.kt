package com.vm.vector.ui.screens.brain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vm.core.models.LearningTile
import com.vm.core.models.LearningTileStatus
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PriorityHigh
import com.vm.core.ui.theme.PriorityLow
import com.vm.core.ui.theme.PriorityMid
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.LearningSettings
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/** One chip on the Learn list, split into two collapsible sections ([first] is listed first). */
enum class LearnGroup(
    val label: String,
    val first: LearningTileStatus,
    val second: LearningTileStatus,
) {
    BACKLOG("Backlog", LearningTileStatus.PRIORITY, LearningTileStatus.BACKLOG),
    IN_PROGRESS("In Progress", LearningTileStatus.IN_PROGRESS, LearningTileStatus.ON_HOLD),
    LEARNED("Learned", LearningTileStatus.TO_REVIEW, LearningTileStatus.LEARNED);

    val sections: List<LearningTileStatus> get() = listOf(first, second)
}

/** Order of the manual status menu. */
val LearnMenuStatuses = listOf(
    LearningTileStatus.PRIORITY,
    LearningTileStatus.BACKLOG,
    LearningTileStatus.IN_PROGRESS,
    LearningTileStatus.ON_HOLD,
    LearningTileStatus.TO_REVIEW,
    LearningTileStatus.LEARNED,
)

fun learnStatusLabel(status: LearningTileStatus): String = when (status) {
    LearningTileStatus.BACKLOG -> "Backlog"
    LearningTileStatus.PRIORITY -> "Priority"
    LearningTileStatus.IN_PROGRESS -> "In Progress"
    LearningTileStatus.ON_HOLD -> "On Hold"
    LearningTileStatus.TO_REVIEW -> "Review"
    LearningTileStatus.LEARNED -> "Learned"
}

/** Section title inside a chip, e.g. "General" for plain Backlog and "Done" for Learned. */
fun learnSectionLabel(status: LearningTileStatus): String = when (status) {
    LearningTileStatus.BACKLOG -> "General"
    LearningTileStatus.PRIORITY -> "Priority"
    LearningTileStatus.IN_PROGRESS -> "Active"
    LearningTileStatus.ON_HOLD -> "On Hold"
    LearningTileStatus.TO_REVIEW -> "Review"
    LearningTileStatus.LEARNED -> "Done"
}

fun learnStatusColor(status: LearningTileStatus): Color = when (status) {
    LearningTileStatus.BACKLOG -> SlateGray
    LearningTileStatus.PRIORITY -> PriorityHigh
    LearningTileStatus.IN_PROGRESS -> ElectricBlue
    LearningTileStatus.ON_HOLD -> PriorityMid
    LearningTileStatus.TO_REVIEW -> NavyDeep
    LearningTileStatus.LEARNED -> PriorityLow
}

fun LearningTile.isMastered(settings: LearningSettings): Boolean =
    status == LearningTileStatus.LEARNED && nextReviewAt == null && reviewStep >= settings.reviewIntervalsDays.size

private fun daysBetween(from: Long, to: Long): Int =
    TimeUnit.MILLISECONDS.toDays((to - from).coerceAtLeast(0)).toInt()

private fun daysUntil(target: Long, now: Long): Int =
    ceil((target - now).coerceAtLeast(0) / TimeUnit.DAYS.toMillis(1).toDouble()).toInt()

/** Short card line, e.g. "Review due", "Next review in 12d (2/5)", "No activity 18d". */
fun learnCardHint(tile: LearningTile, settings: LearningSettings, now: Long = System.currentTimeMillis()): String? =
    when (tile.status) {
        LearningTileStatus.BACKLOG, LearningTileStatus.PRIORITY -> null
        LearningTileStatus.IN_PROGRESS -> "Active ${brainRelativeTime(tile.lastActivityAt, now)}"
        LearningTileStatus.ON_HOLD -> "No activity ${daysBetween(tile.lastActivityAt, now)}d"
        LearningTileStatus.TO_REVIEW -> "Review due"
        LearningTileStatus.LEARNED -> when {
            tile.isMastered(settings) -> "Mastered"
            tile.nextReviewAt != null ->
                "Next review in ${daysUntil(tile.nextReviewAt!!, now)}d " +
                    "(${(tile.reviewStep + 1).coerceAtMost(settings.reviewIntervalsDays.size)}/${settings.reviewIntervalsDays.size})"
            else -> null
        }
    }

/** Longer line under the tile screen title. */
fun learnStatusLine(tile: LearningTile, settings: LearningSettings, now: Long = System.currentTimeMillis()): String {
    val total = settings.reviewIntervalsDays.size
    return when (tile.status) {
        LearningTileStatus.BACKLOG -> "Backlog · moves to In Progress when you add something"
        LearningTileStatus.PRIORITY -> "Priority backlog · moves to In Progress when you add something"
        LearningTileStatus.IN_PROGRESS -> "In Progress · last activity ${brainRelativeTime(tile.lastActivityAt, now)}"
        LearningTileStatus.ON_HOLD -> "On Hold · no activity for ${daysBetween(tile.lastActivityAt, now)} days"
        LearningTileStatus.TO_REVIEW -> "Review due · review ${(tile.reviewStep + 1).coerceAtMost(total)} of $total"
        LearningTileStatus.LEARNED -> when {
            tile.isMastered(settings) -> "Learned · mastered, no more reviews"
            tile.nextReviewAt != null -> {
                val days = daysUntil(tile.nextReviewAt!!, now)
                "Learned · review in $days day${if (days == 1) "" else "s"} " +
                    "(step ${(tile.reviewStep + 1).coerceAtMost(total)} of $total)"
            }
            else -> "Learned"
        }
    }
}

/** Dot + label pill; [large] is used in the tile screen header. */
@Composable
fun LearnStatusPill(
    status: LearningTileStatus,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val color = learnStatusColor(status)
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .padding(
                horizontal = if (large) 12.dp else 8.dp,
                vertical = if (large) 6.dp else 3.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (large) 10.dp else 8.dp)
                .background(color, CircleShape)
                .border(1.dp, PureWhite, CircleShape),
        )
        Text(
            text = learnStatusLabel(status),
            color = color,
            style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
