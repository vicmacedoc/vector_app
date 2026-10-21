package com.vm.vector.presentation.supermarket

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
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
import com.vm.core.ui.theme.PriorityMid
import com.vm.core.wear.SupermarketForWearPayload
import com.vm.core.wear.WearSupermarketItem

@Composable
fun SupermarketScreen(
    payload: SupermarketForWearPayload?,
    isLoading: Boolean,
    error: String?,
    onToggle: (WearSupermarketItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
    val items = payload?.items.orEmpty()
    val inCart = items.count { it.isChecked }
    val aisles = items.groupBy { it.aisle }.toList()

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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Supermarket",
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center
                    )
                    if (items.isNotEmpty()) {
                        Text(
                            text = "$inCart/${items.size} in cart",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            when {
                isLoading && payload == null -> item {
                    Text("Loading…", style = MaterialTheme.typography.body2)
                }
                error != null && payload == null -> item {
                    Text(error, style = MaterialTheme.typography.body2, color = DeleteRed)
                }
                payload != null && !payload.available -> item {
                    Text(
                        text = "No list cached on phone",
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                items.isEmpty() -> item {
                    Text(
                        text = "Nothing to buy",
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    for ((aisle, aisleItems) in aisles) {
                        item {
                            ListHeader {
                                Text(text = aisle, style = MaterialTheme.typography.caption1)
                            }
                        }
                        items(aisleItems, key = { "${it.index}_${it.title}" }) { item ->
                            SupermarketRow(item = item, onClick = { onToggle(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupermarketRow(
    item: WearSupermarketItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when (item.urgency) {
                        "URGENT" -> PriorityHigh
                        "LOW" -> PriorityMid
                        else -> ElectricBlue
                    }
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                color = if (item.isChecked) {
                    MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colors.onBackground
                }
            )
            quantityLabel(item)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (item.isChecked) "✓" else "·",
            style = MaterialTheme.typography.body1,
            color = if (item.isChecked) ElectricBlue else MaterialTheme.colors.onBackground
        )
    }
}

private fun quantityLabel(item: WearSupermarketItem): String? {
    val qty = item.quantity ?: return null
    val unit = item.unit.orEmpty()
    val remaining = item.remaining
    return if (remaining != null) {
        "${formatNumber(remaining)}/${formatNumber(qty)} $unit".trim()
    } else {
        "${formatNumber(qty)} $unit".trim()
    }
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
