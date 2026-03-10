package com.vm.vector.presentation.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Modifier that triggers [onChange] on tap and repeats it faster while the button is held.
 * Initial delay (500ms) ensures a quick tap fires only once; repeat runs only after hold.
 */
internal fun Modifier.stepperWithHoldRepeat(onChange: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        coroutineScope {
            detectTapGestures(
                onPress = {
                    onChange()
                    val job = launch {
                        delay(500)
                        var repeatCount = 0
                        val maxRepeats = 200
                        while (isActive && repeatCount < maxRepeats) {
                            onChange()
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
    }
)

/**
 * A button-styled Box that responds to tap and hold-repeat; use instead of Button for +/- steppers
 * so that pointerInput receives the events (Wear Button's clickable would otherwise consume them).
 */
@Composable
internal fun StepperButton(
    onChange: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(ButtonDefaults.DefaultButtonSize)
            .clip(shape)
            .background(MaterialTheme.colors.surface)
            .stepperWithHoldRepeat(onChange),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
