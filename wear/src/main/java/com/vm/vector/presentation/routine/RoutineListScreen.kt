package com.vm.vector.presentation.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.vm.core.wear.RoutineWearEntry

@Composable
fun RoutineListScreen(
    date: String,
    entries: List<RoutineWearEntry>,
    isLoading: Boolean,
    error: String?,
    onEntryClick: (RoutineWearEntry) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(density) { 60.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { dragAccum = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dragAccum += dragAmount
                        if (dragAccum >= thresholdPx) {
                            onRefresh()
                            dragAccum = 0f
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Routine",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = date,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading…",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onBackground
                            )
                        }
                    }
                    error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.body2,
                                    color = MaterialTheme.colors.onSurface
                                )
                                Chip(
                                    onClick = onRefresh,
                                    label = { Text("Refresh") },
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                    entries.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No time goals today",
                                    style = MaterialTheme.typography.body2,
                                    color = MaterialTheme.colors.onBackground
                                )
                                Chip(
                                    onClick = onRefresh,
                                    label = { Text("Refresh") },
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 12.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            entries.forEach { entry ->
                                RoutineEntryChip(
                                    title = entry.title,
                                    onClick = { onEntryClick(entry) }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RoutineEntryChip(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chipColors = ChipDefaults.chipColors(
        backgroundColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface
    )
    Chip(
        onClick = onClick,
        label = { Text(text = title, maxLines = 2) },
        colors = chipColors,
        modifier = modifier.fillMaxWidth()
    )
}
