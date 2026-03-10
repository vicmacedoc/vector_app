package com.vm.vector.presentation.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

private val DarkGreen = Color(0xFF1B5E20)

@Composable
fun EnduranceEndScreen(
    initialDurationValue: Int,
    durationUnit: String,
    onSave: (durationValueInUnit: Int) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var durationValue by remember(initialDurationValue, durationUnit) {
        mutableStateOf(initialDurationValue.coerceAtLeast(0))
    }
    var showConfirmDiscard by remember { mutableStateOf(false) }

    if (showConfirmDiscard) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Do you wish to discard the workout?",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showConfirmDiscard = false },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.Red,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "X",
                        style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                Button(
                    onClick = onDiscard,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = DarkGreen,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Discard",
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }
            }
        }
        return@EnduranceEndScreen
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Total Duration ($durationUnit)",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepperButton(
                    onChange = { durationValue = (durationValue - 1).coerceAtLeast(0) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) { Text("-", style = MaterialTheme.typography.title2) }
                Text(
                    text = "$durationValue",
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                StepperButton(
                    onChange = { durationValue = durationValue + 1 },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) { Text("+", style = MaterialTheme.typography.title2) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showConfirmDiscard = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.Red,
                        contentColor = Color.White
                    )
                ) {
                    Text("Discard", color = Color.White)
                }
                Button(
                    onClick = { onSave(durationValue) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = DarkGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text("Save", color = Color.White)
                }
            }
        }
    }
}
