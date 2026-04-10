package com.vm.vector.presentation.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A button-styled Box that responds to tap and hold-repeat; use instead of Button for +/- steppers
 * so that pointerInput receives the events (Wear Button's clickable would otherwise consume them).
 *
 * [onChange] is read via [rememberUpdatedState] so [pointerInput(Unit)] does not keep a stale closure
 * when local state (load, reps, etc.) changes across recompositions.
 */
@Composable
internal fun StepperButton(
    onChange: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    buttonSize: Dp = ButtonDefaults.DefaultButtonSize,
    content: @Composable () -> Unit
) {
    val latestOnChange by rememberUpdatedState(onChange)
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(shape)
            .background(MaterialTheme.colors.surface)
            .pointerInput(Unit) {
                coroutineScope {
                    detectTapGestures(
                        onPress = {
                            latestOnChange()
                            val job = launch {
                                delay(500)
                                var repeatCount = 0
                                val maxRepeats = 200
                                while (isActive && repeatCount < maxRepeats) {
                                    latestOnChange()
                                    delay(100)
                                    repeatCount++
                                }
                            }
                            try {
                                awaitRelease()
                            } finally {
                                job.cancel()
                            }
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
