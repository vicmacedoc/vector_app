package com.vm.vector.presentation.workout

import androidx.compose.foundation.background
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

@Composable
fun ResistanceWorkoutScreen(
    exerciseName: String,
    setCount: Int,
    exerciseIndex: Int,
    totalItems: Int,
    isEndWorkoutItem: Boolean,
    onStartExercise: () -> Unit,
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
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = exerciseName,
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 2
                    )
                    Text(
                        text = "$setCount set${if (setCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                    Button(
                        onClick = onStartExercise,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = DarkGreen,
                            contentColor = Color.White
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                    ) {
                        Text("Start Exercise", color = Color.White)
                    }
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .padding(start = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                val arcRadiusPx = with(density) { maxHeight.toPx() } / 2f
                val startAngleRad = PI / 2
                val endAngleRad = -PI / 2
                repeat(totalItems) { i ->
                    val angleRad = if (totalItems > 1) {
                        startAngleRad - (startAngleRad - endAngleRad) * i / (totalItems - 1)
                    } else {
                        0.0
                    }
                    val offsetXPx = arcRadiusPx * (cos(angleRad) - 1)
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
