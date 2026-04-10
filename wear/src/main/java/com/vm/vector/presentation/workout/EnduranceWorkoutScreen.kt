package com.vm.vector.presentation.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

private val DarkGreen = Color(0xFF1B5E20)

private const val SWIPE_THRESHOLD_DP = 40f
private const val ARC_RADIUS_DP = 36f

@Composable
fun EnduranceWorkoutScreen(
    exerciseName: String,
    displaySeconds: Long,
    isPlaying: Boolean,
    isEndWorkoutItem: Boolean,
    exerciseIndex: Int,
    totalItems: Int,
    onPlayPause: () -> Unit,
    onSave: () -> Unit,
    onEndWorkout: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }

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
                            onPrev()
                            dragAccum = 0f
                        } else if (dragAccum <= -thresholdPx) {
                            onNext()
                            dragAccum = 0f
                        }
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isEndWorkoutItem) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = onEndWorkout,
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(horizontal = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color.Red,
                                contentColor = Color.White
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                        ) {
                            Text("End Workout", color = Color.White)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(25.dp))
                    Text(
                        text = exerciseName,
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        maxLines = 2
                    )
                    Text(
                        text = formatElapsed(displaySeconds),
                        style = MaterialTheme.typography.title2,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPlayPause,
                            modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(24.dp),
                                tint = Color.Black
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Set Info",
                        style = MaterialTheme.typography.body2.copy(textDecoration = TextDecoration.Underline),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                            .clickable(onClick = onSave)
                    )
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .padding(start = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                val arcRadiusPx = with(density) {
                    val radiusDp = ARC_RADIUS_DP.dp.toPx()
                    val halfHeightPx = maxHeight.toPx() / 2f
                    minOf(radiusDp, halfHeightPx)
                }
                val startAngleRad = PI / 2
                val endAngleRad = -PI / 2
                repeat(totalItems) { i ->
                    val angleRad = if (totalItems > 1) {
                        startAngleRad - (startAngleRad - endAngleRad) * i / (totalItems - 1)
                    } else {
                        0.0
                    }
                    val horizontalScale = 0.35f
                    val offsetXPx = -arcRadiusPx * horizontalScale * (1 - cos(angleRad))
                    val offsetYPx = -arcRadiusPx * sin(angleRad)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset { IntOffset(offsetXPx.roundToInt(), offsetYPx.roundToInt()) }
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == exerciseIndex) MaterialTheme.colors.primary
                                else MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
