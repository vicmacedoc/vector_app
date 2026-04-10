@file:OptIn(ExperimentalMaterial3Api::class)
package com.vm.vector.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.PriorityLow
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.DayCompletion
import com.vm.vector.ui.util.isoCalendarDateToUtcPickerMillis
import com.vm.vector.ui.util.utcPickerMillisToIsoCalendarDate
import com.vm.vector.ui.viewmodel.HomeViewModel
import com.vm.vector.ui.viewmodel.HomeViewModelFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToCalendar: (date: String, category: String) -> Unit,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Refresh Daily Inputs whenever this screen is shown (e.g. when navigating back from Calendar)
    // so the last-7 completion circles stay in sync. Swipe-down also triggers refresh via PullToRefreshBox.
    LaunchedEffect(Unit) {
        viewModel.refreshLast7()
    }

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
        val isRefreshing by viewModel.isRefreshingLast7.collectAsState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = paddingValues.calculateLeftPadding(layoutDirection),
                    end = paddingValues.calculateRightPadding(layoutDirection),
                    bottom = paddingValues.calculateBottomPadding()
                )
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshLast7() },
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp)
                ) {
                HomeSectionTitle("Daily Inputs")
                DailyInputsBlock(
                    routineLast7 = uiState.routineLast7,
                    dietLast7 = uiState.dietLast7,
                    workoutLast7 = uiState.workoutLast7,
                    diaryLast7 = uiState.diaryLast7,
                    onNavigateToCalendar = onNavigateToCalendar
                )
                Spacer(modifier = Modifier.height(12.dp))
                HomeSelectedDateRow(
                    selectedDateIso = uiState.selectedDate,
                    isPastSelectedDate = uiState.isPastSelectedDate,
                    isFutureSelectedDate = uiState.isFutureSelectedDate,
                    pastDayEditingEnabled = uiState.pastDayEditingEnabled,
                    onDatePicked = viewModel::setSelectedDate,
                    onTogglePastEditing = viewModel::togglePastDayEditing
                )
                Spacer(modifier = Modifier.height(12.dp))
                HomeSectionTitle("Sleep")
                SleepWidget(
                    enabled = uiState.homeFieldsEditable,
                    actualSleepStartMinutes = uiState.actualSleepStartMinutes,
                    actualSleepEndMinutes = uiState.actualSleepEndMinutes,
                    onActualSleepChange = { start, end ->
                        viewModel.setActualSleepStart(start)
                        viewModel.setActualSleepEnd(end)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                HomeSectionTitle("Body")
                BodyWidget(
                    enabled = uiState.homeFieldsEditable,
                    weightKg = uiState.weightKg,
                    bodyFatPercent = uiState.bodyFatPercent,
                    onWeightChange = viewModel::setWeight,
                    onBodyFatChange = viewModel::setBodyFat
                )
                Spacer(modifier = Modifier.height(12.dp))
                HomeSectionTitle("Daily Plan")
                DailyPlanCompletionWidget(
                    enabled = uiState.homeFieldsEditable,
                    completionPercent = uiState.dailyPlanCompletionPercent,
                    onCompletionChange = viewModel::setDailyPlanCompletionPercent
                )
                }
            }

            // SAVE (outside scroll)
            Button(
                onClick = { viewModel.save() },
                enabled = uiState.homeFieldsEditable && !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 0.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSelectedDateRow(
    selectedDateIso: String,
    isPastSelectedDate: Boolean,
    isFutureSelectedDate: Boolean,
    pastDayEditingEnabled: Boolean,
    onDatePicked: (String) -> Unit,
    onTogglePastEditing: () -> Unit
) {
    val displayPattern = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US) }
    val displayText = remember(selectedDateIso) {
        try {
            LocalDate.parse(selectedDateIso).format(displayPattern)
        } catch (_: Exception) {
            selectedDateIso
        }
    }
    val initialPickerMillis = remember(selectedDateIso) {
        try {
            isoCalendarDateToUtcPickerMillis(selectedDateIso)
        } catch (_: Exception) {
            isoCalendarDateToUtcPickerMillis(LocalDate.now().toString())
        }
    }
    var showDatePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Pick date",
                    tint = NavyDeep,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = displayText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NavyDeep,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { showDatePicker = true }
            )
            when {
                isPastSelectedDate -> {
                    IconButton(
                        onClick = onTogglePastEditing,
                        modifier = Modifier
                            .size(48.dp)
                            .then(
                                if (pastDayEditingEnabled) Modifier.background(NavyDeep, CircleShape)
                                else Modifier
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = if (pastDayEditingEnabled) "Lock editing" else "Enable editing",
                            tint = if (pastDayEditingEnabled) PureWhite else PureBlack.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                isFutureSelectedDate -> {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Future day — not editable",
                        tint = PureBlack.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(48.dp)
                            .padding(12.dp)
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialPickerMillis
        )
        val datePickerColors = DatePickerDefaults.colors(
            containerColor = PureWhite,
            titleContentColor = PureBlack,
            headlineContentColor = PureBlack,
            weekdayContentColor = PureBlack,
            subheadContentColor = PureBlack,
            navigationContentColor = PureBlack,
            selectedDayContainerColor = NavyDeep,
            selectedDayContentColor = PureWhite,
            todayDateBorderColor = NavyDeep,
            todayContentColor = NavyDeep,
            dayContentColor = PureBlack,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis ?: initialPickerMillis
                        onDatePicked(utcPickerMillisToIsoCalendarDate(millis))
                        showDatePicker = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = NavyDeep)
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = PureBlack.copy(alpha = 0.87f)
                    )
                ) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = datePickerColors
            )
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
private fun DailyInputsBlock(
    routineLast7: List<DayCompletion>,
    dietLast7: List<DayCompletion>,
    workoutLast7: List<DayCompletion>,
    diaryLast7: List<DayCompletion>,
    onNavigateToCalendar: (date: String, category: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OffWhite)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WeekCompletionRow(
            categoryName = "Routine",
            days = routineLast7,
            onClickDay = { onNavigateToCalendar(it, "Routine") }
        )
        WeekCompletionRow(
            categoryName = "Nutrition",
            days = dietLast7,
            onClickDay = { onNavigateToCalendar(it, "Diet") }
        )
        WeekCompletionRow(
            categoryName = "Exercise",
            days = workoutLast7,
            onClickDay = { onNavigateToCalendar(it, "Workout") }
        )
        WeekCompletionRow(
            categoryName = "Diary",
            days = diaryLast7,
            onClickDay = { onNavigateToCalendar(it, "Diary") }
        )
    }
}

@Composable
private fun BodyWidget(
    enabled: Boolean,
    weightKg: String,
    bodyFatPercent: String,
    onWeightChange: (String) -> Unit,
    onBodyFatChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OffWhite)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    enabled = enabled,
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
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text("%", color = SlateGray) }
            )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepWidget(
    enabled: Boolean,
    actualSleepStartMinutes: Int,
    actualSleepEndMinutes: Int,
    onActualSleepChange: (startMinutes: Int, endMinutes: Int) -> Unit
) {
    var showPicker by remember { mutableStateOf<SleepPickerKind?>(null) }
    val pickerMinutes = when (showPicker) {
        SleepPickerKind.ActualStart -> actualSleepStartMinutes
        SleepPickerKind.ActualEnd -> actualSleepEndMinutes
        null -> 0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OffWhite)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Bedtime", color = SlateGray, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                TimeInputChip(
                    enabled = enabled,
                    timeText = minutesToTimeString(actualSleepStartMinutes),
                    onClick = { showPicker = SleepPickerKind.ActualStart }
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Wake-up", color = SlateGray, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                TimeInputChip(
                    enabled = enabled,
                    timeText = minutesToTimeString(actualSleepEndMinutes),
                    onClick = { showPicker = SleepPickerKind.ActualEnd }
                )
            }
        }
    }

    showPicker?.let { kind ->
        TimePickerDialog(
            initialMinutes = pickerMinutes,
            onDismiss = { showPicker = null },
            onConfirm = { minutes ->
                when (kind) {
                    SleepPickerKind.ActualStart -> onActualSleepChange(minutes, actualSleepEndMinutes)
                    SleepPickerKind.ActualEnd -> onActualSleepChange(actualSleepStartMinutes, minutes)
                }
                showPicker = null
            }
        )
    }
}

private enum class SleepPickerKind { ActualStart, ActualEnd }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit
) {
    val totalMinutes = 24 * 60
    val normalized = (initialMinutes % totalMinutes + totalMinutes) % totalMinutes
    val initialHour = normalized / 60
    val initialMinute = normalized % 60
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val minutes = state.hour * 60 + state.minute
                onConfirm(minutes)
            }) {
                Text("OK", color = NavyDeep)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SlateGray)
            }
        },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = PureWhite,
                    clockDialSelectedContentColor = NavyDeep,
                    clockDialUnselectedContentColor = NavyDeep,
                    containerColor = PureWhite,
                    selectorColor = NavyDeep,
                    timeSelectorSelectedContainerColor = Color(0xFFE0E0E0),
                    timeSelectorUnselectedContainerColor = PureWhite,
                    timeSelectorSelectedContentColor = NavyDeep,
                    timeSelectorUnselectedContentColor = NavyDeep
                )
            )
        },
        containerColor = PureWhite
    )
}

private fun minutesToTimeString(minutes: Int): String {
    val m = (minutes % (24 * 60) + 24 * 60) % (24 * 60)
    val h = m / 60
    val min = m % 60
    return "%02d:%02d".format(h, min)
}

@Composable
private fun TimeInputChip(
    timeText: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyPlanCompletionWidget(
    enabled: Boolean,
    completionPercent: Int,
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
                enabled = enabled,
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
    categoryName: String,
    days: List<DayCompletion>,
    onClickDay: (date: String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$categoryName:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = NavyDeep
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            days.forEach { day ->
                DayCircle(
                    day = day,
                    onClick = { onClickDay(day.date) }
                )
            }
        }
    }
}

@Composable
private fun DayCircle(
    day: DayCompletion,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (day.hasData) PriorityLow else SlateGray.copy(alpha = 0.25f)
            )
            .border(
                width = 1.dp,
                color = NavyDeep.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}
