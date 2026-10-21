package com.vm.vector.presentation.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.wear.FoodDayForWearPayload
import com.vm.core.wear.WearFoodItem
import com.vm.core.wear.WearFoodState

@Composable
fun NutritionScreen(
    payload: FoodDayForWearPayload?,
    isLoading: Boolean,
    error: String?,
    onCycleStatus: (WearFoodItem) -> Unit,
    supermarketSubtitle: String,
    onOpenSupermarket: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
    val items = payload?.items.orEmpty()
    val eaten = items.filter { it.state == WearFoodState.EATEN }
    val plannedKcal = items.sumOf { it.kcal }
    val eatenKcal = eaten.sumOf { it.kcal }
    val progress = if (plannedKcal > 0) (eatenKcal / plannedKcal).toFloat().coerceIn(0f, 1f) else 0f
    val eatenP = eaten.sumOf { it.protein }
    val eatenC = eaten.sumOf { it.carbs }
    val eatenF = eaten.sumOf { it.fats }
    val byTime = items.groupBy { it.plannedTime?.takeIf { t -> t.isNotBlank() } ?: "—" }
        .toSortedMap(compareBy { it })

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
                    text = "Nutrition",
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }
            item {
                Chip(
                    onClick = onOpenSupermarket,
                    label = { Text("Supermarket", maxLines = 1) },
                    secondaryLabel = {
                        Text(supermarketSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = MaterialTheme.colors.surface,
                        contentColor = MaterialTheme.colors.onSurface,
                        secondaryContentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            when {
                isLoading -> item {
                    Text("Loading…", style = MaterialTheme.typography.body2)
                }
                error != null -> item {
                    Text(error, style = MaterialTheme.typography.body2, color = DeleteRed)
                }
                payload != null && !payload.hasPreset && items.isEmpty() -> item {
                    Text("No food plan", style = MaterialTheme.typography.body2)
                }
                else -> {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier.size(56.dp),
                                    indicatorColor = ElectricBlue,
                                    trackColor = MaterialTheme.colors.surface
                                )
                                Text(
                                    text = "${eatenKcal.toInt()}",
                                    style = MaterialTheme.typography.caption1
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${eaten.size}/${items.size} · ${plannedKcal.toInt()} kcal",
                                style = MaterialTheme.typography.caption2
                            )
                            Text(
                                text = "P ${eatenP.toInt()} · C ${eatenC.toInt()} · F ${eatenF.toInt()}",
                                style = MaterialTheme.typography.caption2,
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                    for ((time, slotItems) in byTime) {
                        item {
                            ListHeader {
                                Text(text = time, style = MaterialTheme.typography.caption1)
                            }
                        }
                        items(slotItems, key = { it.id }) { entry ->
                            FoodRow(entry = entry, onClick = { onCycleStatus(entry) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodRow(
    entry: WearFoodItem,
    onClick: () -> Unit
) {
    val (color, decoration) = when (entry.state) {
        WearFoodState.EATEN -> ElectricBlue to TextDecoration.None
        WearFoodState.SKIPPED -> DeleteRed to TextDecoration.LineThrough
        WearFoodState.NONE -> MaterialTheme.colors.onBackground to TextDecoration.None
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.body2,
                color = color,
                textDecoration = decoration,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val amount = entry.amount
            if (amount != null && amount > 0) {
                Text(
                    text = "${amount.toInt()} ${entry.unit}".trim(),
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = when (entry.state) {
                WearFoodState.EATEN -> "✓"
                WearFoodState.SKIPPED -> "✕"
                WearFoodState.NONE -> "·"
            },
            style = MaterialTheme.typography.body1,
            color = color
        )
    }
}
