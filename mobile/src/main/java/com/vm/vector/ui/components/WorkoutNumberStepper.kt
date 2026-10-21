package com.vm.vector.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureBlack
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun WorkoutNumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    enabled: Boolean,
    label: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var draftText by remember { mutableStateOf("") }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = textStyle,
            color = PureBlack.copy(alpha = 0.8f),
            modifier = Modifier.widthIn(min = 120.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val stepBy: (Boolean, Int) -> Unit = { increase, repeats ->
                val multiplier = holdMultiplier(repeats, 1.0, (max - min).toDouble())
                val next = if (multiplier == 1) {
                    value + if (increase) 1 else -1
                } else {
                    snapStep(value.toDouble(), multiplier.toDouble(), increase).roundToInt()
                }
                onValueChange(next.coerceIn(min, max))
            }
            HoldRepeatArrowButton(
                increase = false,
                enabled = enabled && value > min,
                onStep = { repeats -> stepBy(false, repeats) },
            )
            Text(
                text = value.toString(),
                style = textStyle,
                color = PureBlack,
                modifier = Modifier
                    .widthIn(min = 32.dp)
                    .clickable(
                        enabled = enabled,
                        onClick = {
                            draftText = value.toString()
                            showEditDialog = true
                        }
                    ),
                textAlign = TextAlign.Center
            )
            HoldRepeatArrowButton(
                increase = true,
                enabled = enabled && value < max,
                onStep = { repeats -> stepBy(true, repeats) },
            )
        }
    }
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(label, color = PureBlack) },
            text = {
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { t -> draftText = t.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            draftText.toIntOrNull()?.let { onValueChange(it.coerceIn(min, max)) }
                            showEditDialog = false
                        }
                    ),
                    label = { Text("Value ($min–$max)") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        draftText.toIntOrNull()?.let { onValueChange(it.coerceIn(min, max)) }
                        showEditDialog = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun WorkoutNumberStepperDouble(
    value: Double,
    onValueChange: (Double) -> Unit,
    min: Double,
    max: Double,
    step: Double = 0.1,
    enabled: Boolean,
    label: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    val rounded = (value * 10).toInt() / 10.0
    var showEditDialog by remember { mutableStateOf(false) }
    var draftText by remember { mutableStateOf("") }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = textStyle,
            color = PureBlack.copy(alpha = 0.8f),
            modifier = Modifier.widthIn(min = 120.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val stepBy: (Boolean, Int) -> Unit = { increase, repeats ->
                val multiplier = holdMultiplier(repeats, step, max - min)
                val next = if (multiplier == 1) {
                    value + if (increase) step else -step
                } else {
                    snapStep(value, step * multiplier, increase)
                }
                onValueChange(kotlin.math.round(next.coerceIn(min, max) * 10) / 10)
            }
            HoldRepeatArrowButton(
                increase = false,
                enabled = enabled && value > min,
                onStep = { repeats -> stepBy(false, repeats) },
            )
            Text(
                text = "%.1f".format(rounded),
                style = textStyle,
                color = PureBlack,
                modifier = Modifier
                    .widthIn(min = 32.dp)
                    .clickable(
                        enabled = enabled,
                        onClick = {
                            draftText = "%.1f".format(rounded).trimEnd('0').trimEnd('.').ifEmpty { "0" }
                            showEditDialog = true
                        }
                    ),
                textAlign = TextAlign.Center
            )
            HoldRepeatArrowButton(
                increase = true,
                enabled = enabled && value < max,
                onStep = { repeats -> stepBy(true, repeats) },
            )
        }
    }
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(label, color = PureBlack) },
            text = {
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { t ->
                        val out = StringBuilder()
                        var dotSeen = false
                        for (c in t) {
                            when {
                                c.isDigit() -> out.append(c)
                                c == '.' && !dotSeen -> {
                                    dotSeen = true
                                    out.append(c)
                                }
                            }
                        }
                        draftText = out.toString().take(16)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            draftText.toDoubleOrNull()?.let { onValueChange(it.coerceIn(min, max)) }
                            showEditDialog = false
                        }
                    ),
                    label = { Text("Value") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        draftText.toDoubleOrNull()?.let { onValueChange(it.coerceIn(min, max)) }
                        showEditDialog = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private const val HOLD_INITIAL_DELAY_MS = 400L
private const val HOLD_START_INTERVAL_MS = 180L
private const val HOLD_MIN_INTERVAL_MS = 50L

/**
 * Step multiplier for the [repeats]-th auto-repeat while holding: ×1, then ×5, then ×10.
 * Bigger steps are skipped when they exceed 1/20 of [range], so small scales (RIR, RPE) stay fine-grained.
 */
private fun holdMultiplier(repeats: Int, step: Double, range: Double): Int {
    val target = when {
        repeats >= 25 -> 10
        repeats >= 10 -> 5
        else -> 1
    }
    return listOf(10, 5, 1).first { it <= target && (it == 1 || step * it <= range / 20.0) }
}

/** Next multiple of [step] above or below [value], so accelerated steps land on round numbers. */
private fun snapStep(value: Double, step: Double, increase: Boolean): Double {
    val epsilon = step * 1e-6
    return if (increase) {
        floor((value + epsilon) / step) * step + step
    } else {
        ceil((value - epsilon) / step) * step - step
    }
}

/**
 * Arrow that steps once on tap and auto-repeats while held, getting faster down to
 * [HOLD_MIN_INTERVAL_MS]. [onStep] receives the repeat index (0 for a tap or the first repeat).
 */
@Composable
private fun HoldRepeatArrowButton(
    increase: Boolean,
    enabled: Boolean,
    onStep: (repeats: Int) -> Unit,
) {
    val currentOnStep by rememberUpdatedState(onStep)
    val interactionSource = remember { MutableInteractionSource() }
    val description = if (increase) "Increase" else "Decrease"
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .indication(interactionSource, LocalIndication.current)
            .semantics {
                role = Role.Button
                contentDescription = description
                if (enabled) {
                    onClick { currentOnStep(0); true }
                } else {
                    disabled()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val press = PressInteraction.Press(down.position)
                        interactionSource.tryEmit(press)
                        var held = false
                        val repeater = launch {
                            delay(HOLD_INITIAL_DELAY_MS)
                            held = true
                            var repeats = 0
                            var interval = HOLD_START_INTERVAL_MS
                            while (true) {
                                currentOnStep(repeats++)
                                delay(interval)
                                interval = (interval * 0.85).toLong().coerceAtLeast(HOLD_MIN_INTERVAL_MS)
                            }
                        }
                        var released = false
                        try {
                            val up = waitForUpOrCancellation()
                            repeater.cancel()
                            if (up != null && !held) currentOnStep(0)
                            released = up != null
                        } finally {
                            repeater.cancel()
                            interactionSource.tryEmit(
                                if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press)
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (increase) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = if (enabled) NavyDeep else PureBlack.copy(alpha = 0.38f),
        )
    }
}
