package com.vm.vector.presentation.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

private const val SWIPE_BACK_THRESHOLD_DP = 56

/**
 * Rightward horizontal swipe anywhere on the screen calls [onSwipeBack] once per drag.
 * Used instead of SwipeToDismissBox, which crashes when nested with the workout screens.
 */
fun Modifier.swipeBackGesture(enabled: Boolean = true, onSwipeBack: () -> Unit): Modifier = composed {
    if (!enabled) return@composed this
    val latest by rememberUpdatedState(onSwipeBack)
    val thresholdPx = with(LocalDensity.current) { SWIPE_BACK_THRESHOLD_DP.dp.toPx() }
    pointerInput(thresholdPx) {
        var accumulated = 0f
        var fired = false
        detectHorizontalDragGestures(
            onDragStart = {
                accumulated = 0f
                fired = false
            },
            onHorizontalDrag = { change, amount ->
                if (fired) return@detectHorizontalDragGestures
                accumulated += amount
                if (accumulated >= thresholdPx) {
                    fired = true
                    change.consume()
                    latest()
                }
            },
        )
    }
}

private const val SWIPE_PAGE_THRESHOLD_DP = 48

/** Horizontal paging: one [onSwipeLeft] / [onSwipeRight] per drag once past the threshold. */
fun Modifier.horizontalPageGesture(onSwipeLeft: () -> Unit, onSwipeRight: () -> Unit): Modifier = composed {
    val latestLeft by rememberUpdatedState(onSwipeLeft)
    val latestRight by rememberUpdatedState(onSwipeRight)
    val thresholdPx = with(LocalDensity.current) { SWIPE_PAGE_THRESHOLD_DP.dp.toPx() }
    pointerInput(thresholdPx) {
        var accumulated = 0f
        var fired = false
        detectHorizontalDragGestures(
            onDragStart = {
                accumulated = 0f
                fired = false
            },
            onHorizontalDrag = { change, amount ->
                if (fired) return@detectHorizontalDragGestures
                accumulated += amount
                when {
                    accumulated >= thresholdPx -> {
                        fired = true
                        change.consume()
                        latestRight()
                    }
                    accumulated <= -thresholdPx -> {
                        fired = true
                        change.consume()
                        latestLeft()
                    }
                }
            },
        )
    }
}

/**
 * Leaving an exercise mid-way: Keep concluded work, Discard everything logged for it, or Stay.
 */
@Composable
fun ExitExerciseConfirm(
    title: String,
    detail: String,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    onStay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 28.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onKeep,
            shape = shape,
            colors = ButtonDefaults.buttonColors(backgroundColor = WorkoutGreen, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) { Text("Keep", color = Color.White) }
        Button(
            onClick = onDiscard,
            shape = shape,
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) { Text("Discard", color = Color.White) }
        Button(
            onClick = onStay,
            shape = shape,
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) { Text("Stay", color = Color.White) }
    }
}

internal val WorkoutGreen = Color(0xFF1B5E20)
internal val WorkoutRestBlue = Color(0xFF1565C0)
internal val NextUpBlue = Color(0xFF90CAF9)
internal val DoneGreen = Color(0xFF81C784)
