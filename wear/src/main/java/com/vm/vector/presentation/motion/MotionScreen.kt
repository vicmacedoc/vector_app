package com.vm.vector.presentation.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.PriorityHigh
import com.vm.core.ui.theme.PriorityHold
import com.vm.core.ui.theme.PriorityLow
import com.vm.core.ui.theme.PriorityMid
import com.vm.core.wear.WearMotionBucket
import com.vm.core.wear.WearMotionTask

@Composable
fun MotionScreen(
    tasks: List<WearMotionTask>,
    completingIds: Set<String>,
    isLoading: Boolean,
    error: String?,
    onComplete: (WearMotionTask) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
    val grouped = WearMotionBucket.entries.map { bucket ->
        bucket to tasks.filter { it.bucket == bucket }
    }.filter { it.second.isNotEmpty() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(horizontal = 8.dp)
        ) {
            item {
                Text(
                    text = "Motion",
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }
            when {
                isLoading -> item {
                    Text("Loading…", style = MaterialTheme.typography.body2)
                }
                error != null -> item {
                    Text(error, style = MaterialTheme.typography.body2, color = DeleteRed)
                }
                tasks.isEmpty() -> item {
                    Text("No open tasks", style = MaterialTheme.typography.body2)
                }
                else -> {
                    for ((bucket, bucketTasks) in grouped) {
                        item {
                            ListHeader {
                                Text(
                                    text = bucketLabel(bucket),
                                    style = MaterialTheme.typography.caption1
                                )
                            }
                        }
                        items(bucketTasks, key = { it.id }) { task ->
                            val completing = task.id in completingIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        enabled = !completing
                                    ) { onComplete(task) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(priorityColor(task.priority))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.body2,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textDecoration = if (completing) TextDecoration.LineThrough else null,
                                    color = if (completing) {
                                        MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colors.onBackground
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun bucketLabel(bucket: WearMotionBucket): String = when (bucket) {
    WearMotionBucket.OVERDUE -> "Overdue"
    WearMotionBucket.TODAY -> "Today"
    WearMotionBucket.UPCOMING -> "Upcoming"
    WearMotionBucket.SOMEDAY -> "Someday"
}

private fun priorityColor(priority: String) = when (priority.uppercase()) {
    "HIGH", "P1" -> PriorityHigh
    "MEDIUM", "P2" -> PriorityMid
    "LOW", "P3" -> PriorityLow
    "NONE", "P4" -> PriorityHold
    else -> ElectricBlue
}
