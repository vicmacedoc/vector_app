package com.vm.vector.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.PureWhite

@Composable
fun MainMenuScreen(
    dateLabel: String,
    motionSubtitle: String,
    routineSubtitle: String,
    nutritionSubtitle: String,
    workoutSubtitle: String,
    showMemory: Boolean,
    onMemory: () -> Unit,
    onMotion: () -> Unit,
    onRoutine: () -> Unit,
    onNutrition: () -> Unit,
    onWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
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
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Vector",
                        style = MaterialTheme.typography.title2,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (showMemory) {
                item {
                    Button(
                        onClick = onMemory,
                        modifier = Modifier.size(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = ElectricBlue,
                            contentColor = PureWhite
                        ),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Memory voice note",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                item {
                    Text(
                        text = "Memory",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onBackground
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }
            item {
                MenuChip(
                    label = "Motion",
                    subtitle = motionSubtitle,
                    icon = { Icon(Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = onMotion
                )
            }
            item {
                MenuChip(
                    label = "Routine",
                    subtitle = routineSubtitle,
                    icon = { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = onRoutine
                )
            }
            item {
                MenuChip(
                    label = "Nutrition",
                    subtitle = nutritionSubtitle,
                    icon = { Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = onNutrition
                )
            }
            item {
                MenuChip(
                    label = "Workouts",
                    subtitle = workoutSubtitle,
                    icon = { Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = onWorkout
                )
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MenuChip(
    label: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        icon = { Box(contentAlignment = Alignment.Center) { icon() } },
        colors = ChipDefaults.chipColors(
            backgroundColor = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.onSurface,
            secondaryContentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
