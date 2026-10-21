@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package com.vm.vector.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.ui.graphics.vector.ImageVector
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.IconGray
import com.vm.vector.ui.screens.brain.EditSquareIcon
import com.vm.vector.ui.screens.brain.brainTabIcon
import com.vm.vector.ui.viewmodel.BrainTab
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Watch
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.vm.vector.R
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.core.content.ContextCompat
import com.vm.core.models.MotionTask
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.PriorityLow
import com.vm.core.ui.theme.PriorityMid
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.DayCompletion
import com.vm.vector.data.WearActivityEvent
import com.vm.vector.data.WearActivityEventType
import com.vm.vector.ui.bodyscan.BodyScanCaptureOverlay
import com.vm.vector.ui.components.WorkoutNumberStepper
import com.vm.vector.ui.components.rememberVoiceCaptureStarter
import com.vm.vector.ui.screens.routine.HabitsTrackerSection
import com.vm.vector.ui.util.isoCalendarDateToUtcPickerMillis
import com.vm.vector.ui.util.utcPickerMillisToIsoCalendarDate
import com.vm.vector.ui.screens.brain.QuickCaptureSheet
import com.vm.vector.ui.screens.brain.motionDueLabel
import com.vm.vector.ui.viewmodel.BrainViewModel
import com.vm.vector.ui.viewmodel.BrainViewModelFactory
import com.vm.vector.ui.viewmodel.HomeViewModel
import com.vm.vector.ui.viewmodel.HomeViewModelFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigateToCalendar: (date: String, category: String) -> Unit,
    onNavigateToBrain: (BrainTab) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(LocalContext.current.applicationContext)
    ),
    brainViewModel: BrainViewModel = viewModel(
        factory = BrainViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val brainUiState by brainViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showBodyScanTimesDialog by remember { mutableStateOf(false) }
    var bodyScanActiveTimers by remember { mutableStateOf<IntArray?>(null) }
    val startVoiceCapture = rememberVoiceCaptureStarter(
        onMicDenied = {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone permission is needed for voice capture.")
            }
        },
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showBodyScanTimesDialog = true
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Camera permission is needed for full body scan.")
            }
        }
    }

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

    LaunchedEffect(brainUiState.snackbarMessage) {
        brainUiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            brainViewModel.clearSnackbar()
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
            HomeSelectedDateRow(
                selectedDateIso = uiState.selectedDate,
                canSave = uiState.homeFieldsEditable && uiState.hasUnsavedChanges,
                isSaving = uiState.isSaving,
                onDatePicked = viewModel::setSelectedDate,
                onSave = viewModel::save,
                onLogoClick = onNavigateToSettings,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp),
            )
            HorizontalDivider(color = NavyDeep.copy(alpha = 0.15f))
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
                if (uiState.wearUnseenEvents.isNotEmpty()) {
                    FromYourWatchCard(
                        events = uiState.wearUnseenEvents,
                        onDismiss = viewModel::dismissWearEvents,
                        onAudioClick = { event ->
                            viewModel.markWearEventSeen(event.id)
                            onNavigateToBrain(BrainTab.INBOX)
                        },
                        onWorkoutClick = { event ->
                            viewModel.markWearEventSeen(event.id)
                            val date = event.date ?: uiState.selectedDate
                            onNavigateToCalendar(date, "Exercise")
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                HomeSectionTitle("Brain")
                BrainHomeRow(
                    inboxCount = uiState.brainInboxCount,
                    activeTiles = uiState.brainActiveTiles,
                    overdue = uiState.brainOverdue,
                    dueToday = uiState.brainDueToday,
                    upNext = uiState.brainUpNext,
                    onNavigateToBrain = onNavigateToBrain,
                    onQuickCapture = brainViewModel::openCaptureSheet,
                    onCompleteTask = viewModel::completeBrainTask,
                )
                Spacer(modifier = Modifier.height(12.dp))
                HomeSectionTitle("Daily Inputs")
                DailyInputsBlock(
                    selectedDate = uiState.selectedDate,
                    routineLast7 = uiState.routineLast7,
                    foodLast7 = uiState.foodLast7,
                    workoutLast7 = uiState.workoutLast7,
                    selfLast7 = uiState.selfLast7,
                    onNavigateToCalendar = onNavigateToCalendar,
                    onClearDay = viewModel::clearDay,
                )
                Spacer(modifier = Modifier.height(12.dp))
                HabitsTrackerSection(
                    definitions = uiState.habitDefinitions,
                    selectedIds = uiState.selectedHabitIds,
                    logs = uiState.habitLogs,
                    isEditingEnabled = uiState.homeFieldsEditable,
                    onStatusChange = viewModel::updateHabitStatus,
                    onCreateHabit = viewModel::createHabit,
                    onDeleteHabit = viewModel::deleteHabit,
                    onSetSelected = viewModel::setSelectedHabits,
                    scoreSummaries = uiState.habitScoreSummaries,
                    header = { expanded, onToggleExpand, onEditClick ->
                        HomeSectionTitle(
                            title = "Habits Tracker",
                            trailing = {
                                if (onEditClick != null) {
                                    IconButton(onClick = onEditClick) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit habits",
                                            tint = NavyDeep,
                                        )
                                    }
                                }
                                IconButton(onClick = onToggleExpand) {
                                    Icon(
                                        imageVector = if (expanded) {
                                            Icons.Default.KeyboardArrowUp
                                        } else {
                                            Icons.Default.KeyboardArrowDown
                                        },
                                        contentDescription = if (expanded) "Collapse" else "Expand",
                                        tint = NavyDeep,
                                    )
                                }
                            },
                        )
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                HomeSectionTitle(
                    title = "Body",
                    trailing = {
                        IconButton(
                            onClick = {
                                when (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)) {
                                    PackageManager.PERMISSION_GRANTED -> showBodyScanTimesDialog = true
                                    else -> cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                }
                            },
                            enabled = uiState.homeFieldsEditable,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_body_scan),
                                contentDescription = "Scan full body",
                                tint = if (uiState.homeFieldsEditable) NavyDeep else IconGray,
                            )
                        }
                    },
                )
                BodyWidget(
                    enabled = uiState.homeFieldsEditable,
                    weightKg = uiState.weightKg,
                    bodyFatPercent = uiState.bodyFatPercent,
                    onWeightChange = viewModel::setWeight,
                    onBodyFatChange = viewModel::setBodyFat,
                )
                Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (showBodyScanTimesDialog) {
                val monospaceLabel = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)
                var wFront by remember { mutableIntStateOf(5) }
                var wLeft by remember { mutableIntStateOf(3) }
                var wBack by remember { mutableIntStateOf(3) }
                var wRight by remember { mutableIntStateOf(3) }
                AlertDialog(
                    onDismissRequest = { showBodyScanTimesDialog = false },
                    containerColor = PureWhite,
                    title = { Text("Full body scan timers", color = PureBlack) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Photos go to Drive folders front, left, back, and right under collections.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateGray
                            )
                            WorkoutNumberStepper(
                                value = wFront,
                                onValueChange = { wFront = it },
                                min = 0,
                                max = 600,
                                enabled = true,
                                label = "Seconds before front",
                                textStyle = monospaceLabel
                            )
                            WorkoutNumberStepper(
                                value = wLeft,
                                onValueChange = { wLeft = it },
                                min = 0,
                                max = 600,
                                enabled = true,
                                label = "Seconds before left",
                                textStyle = monospaceLabel
                            )
                            WorkoutNumberStepper(
                                value = wBack,
                                onValueChange = { wBack = it },
                                min = 0,
                                max = 600,
                                enabled = true,
                                label = "Seconds before back",
                                textStyle = monospaceLabel
                            )
                            WorkoutNumberStepper(
                                value = wRight,
                                onValueChange = { wRight = it },
                                min = 0,
                                max = 600,
                                enabled = true,
                                label = "Seconds before right",
                                textStyle = monospaceLabel
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                bodyScanActiveTimers = intArrayOf(wFront, wLeft, wBack, wRight)
                                showBodyScanTimesDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NavyDeep,
                                contentColor = PureWhite
                            )
                        ) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBodyScanTimesDialog = false }) { Text("Cancel", color = NavyDeep) }
                    }
                )
            }

            QuickCaptureSheet(
                visible = brainUiState.showCaptureSheet,
                viewModel = brainViewModel,
                onVoiceStart = {
                    brainViewModel.closeCaptureSheet()
                    startVoiceCapture()
                },
                onDismiss = {},
            )

            bodyScanActiveTimers?.let { timers ->
                BodyScanCaptureOverlay(
                    waitSeconds = timers,
                    onDismiss = { bodyScanActiveTimers = null },
                    onError = { msg ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    uploadJpeg = { folder, bytes -> viewModel.uploadBodyScanPhoto(folder, bytes) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSelectedDateRow(
    selectedDateIso: String,
    canSave: Boolean,
    isSaving: Boolean,
    onDatePicked: (String) -> Unit,
    onSave: () -> Unit,
    onLogoClick: () -> Unit,
    modifier: Modifier = Modifier,
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

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onLogoClick),
        ) {
            Image(
                painter = painterResource(R.drawable.dark_logo),
                contentDescription = "Vector settings",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.6f),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(8.dp))
                .clickable { showDatePicker = true }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = "Pick date",
                tint = NavyDeep,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = displayText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NavyDeep,
            )
        }
        IconButton(
            onClick = onSave,
            enabled = canSave && !isSaving,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = NavyDeep,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = if (canSave) "Save changes" else "No changes to save",
                    tint = if (canSave) NavyDeep else IconGray,
                )
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
private fun BrainHomeRow(
    inboxCount: Int,
    activeTiles: Int,
    overdue: Int,
    dueToday: Int,
    upNext: List<MotionTask>,
    onNavigateToBrain: (BrainTab) -> Unit,
    onQuickCapture: () -> Unit,
    onCompleteTask: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OffWhite)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrainIconTile(
                icon = brainTabIcon(BrainTab.MEMORY),
                contentDescription = "Memory",
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToBrain(BrainTab.MEMORY) },
            )
            BrainIconTile(
                icon = brainTabIcon(BrainTab.MODEL),
                contentDescription = "Model",
                badge = activeTiles.takeIf { it > 0 }?.toString(),
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToBrain(BrainTab.MODEL) },
            )
            BrainIconTile(
                icon = brainTabIcon(BrainTab.MOTION),
                contentDescription = "Motion",
                badge = when {
                    overdue > 0 -> overdue.toString()
                    dueToday > 0 -> dueToday.toString()
                    else -> null
                },
                badgeColor = if (overdue > 0) DeleteRed else ElectricBlue,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToBrain(BrainTab.MOTION) },
            )
            BrainIconTile(
                icon = EditSquareIcon,
                contentDescription = "Quick Capture",
                badge = inboxCount.takeIf { it > 0 }?.toString(),
                badgeColor = PureWhite,
                badgeTextColor = NavyDeep,
                highlighted = true,
                modifier = Modifier.weight(1f),
                onClick = onQuickCapture,
                onLongClick = { onNavigateToBrain(BrainTab.INBOX) },
            )
        }
        if (upNext.isNotEmpty()) {
            Text(
                "Up next",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = NavyDeep,
            )
            upNext.forEach { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onNavigateToBrain(BrainTab.MOTION) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(task.title, color = PureBlack, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            motionDueLabel(task.dueAt),
                            color = SlateGray,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { onCompleteTask(task.id) }) {
                        Text("Done", color = NavyDeep)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrainIconTile(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    badgeColor: Color = ElectricBlue,
    badgeTextColor: Color = PureWhite,
    highlighted: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) NavyDeep else PureWhite)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (highlighted) PureWhite else NavyDeep,
            modifier = Modifier.align(Alignment.Center).size(28.dp),
        )
        if (badge != null) {
            Text(
                text = badge,
                color = badgeTextColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(badgeColor, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun FromYourWatchCard(
    events: List<WearActivityEvent>,
    onDismiss: () -> Unit,
    onAudioClick: (WearActivityEvent) -> Unit,
    onWorkoutClick: (WearActivityEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NavyDeep.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .background(OffWhite, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Watch,
                    contentDescription = null,
                    tint = NavyDeep,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "From your watch",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PureBlack
                )
            }
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("Dismiss", color = SlateGray, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        events.take(5).forEach { event ->
            val icon = when (event.type) {
                WearActivityEventType.AUDIO -> Icons.Filled.Mic
                WearActivityEventType.WORKOUT -> Icons.Filled.FitnessCenter
            }
            val subtitle = when (event.type) {
                WearActivityEventType.AUDIO -> "Voice note · ${relativeTime(event.timestamp)}"
                WearActivityEventType.WORKOUT -> "Workout saved · ${relativeTime(event.timestamp)}"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        when (event.type) {
                            WearActivityEventType.AUDIO -> onAudioClick(event)
                            WearActivityEventType.WORKOUT -> onWorkoutClick(event)
                        }
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureBlack,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateGray
                    )
                }
            }
        }
    }
}

private fun relativeTime(timestamp: Long): String {
    val delta = System.currentTimeMillis() - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}

@Composable
private fun HomeSectionTitle(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NavyDeep,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        HorizontalDivider(color = NavyDeep, thickness = 2.dp)
    }
}

@Composable
private fun DailyInputsBlock(
    selectedDate: String,
    routineLast7: List<DayCompletion>,
    foodLast7: List<DayCompletion>,
    workoutLast7: List<DayCompletion>,
    selfLast7: List<DayCompletion>,
    onNavigateToCalendar: (date: String, category: String) -> Unit,
    onClearDay: (category: String, date: String) -> Unit,
) {
    var pendingClear by remember { mutableStateOf<Pair<String, DayCompletion>?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OffWhite)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            "Routine" to routineLast7,
            "Food" to foodLast7,
            "Exercise" to workoutLast7,
            "Self" to selfLast7,
        ).forEach { (category, days) ->
            WeekCompletionRow(
                categoryName = category,
                days = days,
                selectedDate = selectedDate,
                onClickDay = { onNavigateToCalendar(it, category) },
                onLongPressDay = { day -> pendingClear = category to day },
            )
        }
    }
    pendingClear?.let { (category, day) ->
        val hasAnything = day.hasData || day.hasDraft
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text("Reset $category for ${day.date}?", color = PureBlack) },
            text = {
                Text(
                    if (hasAnything) {
                        buildString {
                            append("This deletes the saved $category data")
                            if (day.hasDraft) append(" and unsaved changes")
                            append(" for this day. The Calendar will show the preset again.")
                            if (category == "Routine") append(" Last night's sleep log is deleted too.")
                        }
                    } else {
                        "Nothing is saved for this day yet. It already shows the preset."
                    },
                    color = PureBlack,
                )
            },
            confirmButton = {
                if (hasAnything) {
                    TextButton(onClick = {
                        onClearDay(category, day.date)
                        pendingClear = null
                    }) {
                        Text("Delete", color = DeleteRed)
                    }
                } else {
                    TextButton(onClick = { pendingClear = null }) {
                        Text("OK", color = NavyDeep)
                    }
                }
            },
            dismissButton = if (hasAnything) {
                {
                    TextButton(onClick = { pendingClear = null }) {
                        Text("Cancel", color = NavyDeep)
                    }
                }
            } else null,
            containerColor = PureWhite,
        )
    }
}

@Composable
private fun BodyWidget(
    enabled: Boolean,
    weightKg: String,
    bodyFatPercent: String,
    onWeightChange: (String) -> Unit,
    onBodyFatChange: (String) -> Unit,
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

@Composable
private fun WeekCompletionRow(
    categoryName: String,
    days: List<DayCompletion>,
    selectedDate: String,
    onClickDay: (date: String) -> Unit,
    onLongPressDay: (DayCompletion) -> Unit,
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
                DaySquare(
                    day = day,
                    isSelected = day.date == selectedDate,
                    onClick = { onClickDay(day.date) },
                    onLongClick = { onLongPressDay(day) },
                )
            }
        }
    }
}

/** Orange: unsaved Calendar changes. Green: saved data. Gray: preset only. */
@Composable
private fun DaySquare(
    day: DayCompletion,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    day.hasDraft -> PriorityMid
                    day.hasData -> PriorityLow
                    else -> SlateGray.copy(alpha = 0.25f)
                }
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) NavyDeep else NavyDeep.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    )
}
