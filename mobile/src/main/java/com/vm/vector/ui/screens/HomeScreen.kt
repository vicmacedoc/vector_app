package com.vm.vector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.DayCompletion
import com.vm.vector.ui.viewmodel.HomeViewModel
import com.vm.vector.ui.viewmodel.HomeViewModelFactory

@Composable
fun HomeScreen(
    onNavigateToCalendar: (date: String, category: String) -> Unit,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveMessage) {
        uiState.saveMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSaveMessage()
        }
    }

    Scaffold(
        containerColor = PureWhite,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = paddingValues.calculateLeftPadding(layoutDirection),
                    end = paddingValues.calculateRightPadding(layoutDirection),
                    bottom = paddingValues.calculateBottomPadding()
                )
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp)
            ) {
                // ROUTINE
                HomeSectionTitle("ROUTINE")
                WeekCompletionRow(
                    days = uiState.routineLast7,
                    onClickDay = { date -> onNavigateToCalendar(date, "Routine") }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // NUTRITION
                HomeSectionTitle("NUTRITION")
                WeekCompletionRow(
                    days = uiState.dietLast7,
                    onClickDay = { date -> onNavigateToCalendar(date, "Diet") }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // EXERCISE
                HomeSectionTitle("EXERCISE")
                WeekCompletionRow(
                    days = uiState.workoutLast7,
                    onClickDay = { date -> onNavigateToCalendar(date, "Workout") }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // DIARY
                HomeSectionTitle("DIARY")
                WeekCompletionRow(
                    days = uiState.diaryLast7,
                    onClickDay = { date -> onNavigateToCalendar(date, "Diary") }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // SLEEP
                HomeSectionTitle("SLEEP")
                SleepWidget(
                    sleepStartMinutes = uiState.sleepStartMinutes,
                    sleepEndMinutes = uiState.sleepEndMinutes,
                    onSleepRangeChange = { start, end ->
                        viewModel.setSleepStart(start)
                        viewModel.setSleepEnd(end)
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // BODY
                HomeSectionTitle("BODY")
                BodyWidget(
                    weightKg = uiState.weightKg,
                    bodyFatPercent = uiState.bodyFatPercent,
                    onWeightChange = viewModel::setWeight,
                    onBodyFatChange = viewModel::setBodyFat
                )
                Spacer(modifier = Modifier.height(16.dp))

                // DAILY PLAN
                HomeSectionTitle("DAILY PLAN")
                DailyPlanWidget(
                    text = uiState.dailyPlanText,
                    completionPercent = uiState.dailyPlanCompletionPercent,
                    onTextChange = viewModel::setDailyPlanText,
                    onCompletionChange = viewModel::setDailyPlanCompletionPercent
                )
            }

            // SAVE (outside scroll)
            Button(
                onClick = { viewModel.save() },
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NavyDeep,
                    contentColor = PureWhite
                )
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PureWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Changes")
                }
            }
        }
    }
}

@Composable
private fun HomeSectionTitle(title: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = NavyDeep
        )
        HorizontalDivider(color = NavyDeep, thickness = 2.dp)
    }
}

@Composable
private fun BodyWidget(
    weightKg: String,
    bodyFatPercent: String,
    onWeightChange: (String) -> Unit,
    onBodyFatChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OffWhite)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Weight:", color = SlateGray, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = weightKg,
                onValueChange = onWeightChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text("Kg", color = SlateGray) }
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Body Fat:", color = SlateGray, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = bodyFatPercent,
                onValueChange = onBodyFatChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text("%", color = SlateGray) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepWidget(
    sleepStartMinutes: Int,
    sleepEndMinutes: Int,
    onSleepRangeChange: (startMinutes: Int, endMinutes: Int) -> Unit
) {
    val totalMinutes = 24 * 60

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OffWhite)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SleepTimeSlider(
            label = "Start",
            minutes = sleepStartMinutes,
            onMinutesChange = { onSleepRangeChange(it, sleepEndMinutes) },
            totalMinutes = totalMinutes,
            invertedColors = false
        )
        SleepTimeSlider(
            label = "End",
            minutes = sleepEndMinutes,
            onMinutesChange = { onSleepRangeChange(sleepStartMinutes, it) },
            totalMinutes = totalMinutes,
            invertedColors = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimeSlider(
    label: String,
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    totalMinutes: Int,
    invertedColors: Boolean = false
) {
    val valueF = ((minutes % totalMinutes) + totalMinutes) % totalMinutes / totalMinutes.toFloat()
    val activeColor = if (invertedColors) OffWhite else NavyDeep
    val inactiveColor = if (invertedColors) NavyDeep else OffWhite

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = SlateGray
            )
            Text(
                minutesToTimeString(minutes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = NavyDeep
            )
        }
        Slider(
            value = valueF,
            onValueChange = { onMinutesChange((it * totalMinutes).toInt()) },
            valueRange = 0f..1f,
            steps = (totalMinutes / 5) - 1,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = SliderDefaults.colors(
                thumbColor = NavyDeep,
                activeTrackColor = activeColor,
                inactiveTrackColor = inactiveColor
            ),
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    enabled = true,
                    colors = SliderDefaults.colors(
                        activeTrackColor = activeColor,
                        inactiveTrackColor = inactiveColor
                    )
                )
            },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(NavyDeep, CircleShape)
                        .border(2.dp, PureBlack, CircleShape)
                )
            }
        )
    }
}

private fun minutesToTimeString(minutes: Int): String {
    val m = (minutes % (24 * 60) + 24 * 60) % (24 * 60)
    val h = m / 60
    val min = m % 60
    return "%02d:%02d".format(h, min)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyPlanWidget(
    text: String,
    completionPercent: Int,
    onTextChange: (String) -> Unit,
    onCompletionChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OffWhite)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            placeholder = { Text("Today's plan...", color = SlateGray) },
            maxLines = 6
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Complete:", color = SlateGray, style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = completionPercent.toFloat(),
                onValueChange = { onCompletionChange(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = NavyDeep,
                    activeTrackColor = NavyDeep,
                    inactiveTrackColor = OffWhite
                ),
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        colors = SliderDefaults.colors(
                            activeTrackColor = NavyDeep,
                            inactiveTrackColor = OffWhite
                        ),
                        modifier = Modifier.height(4.dp)
                    )
                },
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(NavyDeep, CircleShape)
                            .border(2.dp, PureBlack, CircleShape)
                    )
                }
            )
            Text(
                "${completionPercent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = NavyDeep
            )
        }
    }
}

@Composable
private fun WeekCompletionRow(
    days: List<DayCompletion>,
    onClickDay: (date: String) -> Unit
) {
    val totalPercent = if (days.isEmpty()) 0 else days.map { it.percent }.average().toInt()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OffWhite)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Complete: ${totalPercent}%",
            style = MaterialTheme.typography.bodyMedium,
            color = SlateGray,
            modifier = Modifier.padding(end = 12.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            days.forEach { day ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    DayCircle(
                        day = day,
                        onClick = { onClickDay(day.date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCircle(
    day: DayCompletion,
    onClick: () -> Unit
) {
    val filled = day.hasData && day.percent > 0
    val fullyFilled = day.percent >= 100
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                when {
                    fullyFilled -> ElectricBlue
                    filled -> ElectricBlue.copy(alpha = 0.5f)
                    else -> SlateGray.copy(alpha = 0.2f)
                }
            )
            .border(
                width = 1.dp,
                color = NavyDeep.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (day.percent in 1..99) {
            Text(
                "${day.percent}%",
                style = MaterialTheme.typography.labelSmall,
                color = NavyDeep
            )
        }
    }
}
