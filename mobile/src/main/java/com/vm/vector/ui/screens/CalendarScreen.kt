package com.vm.vector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import com.vm.core.models.AuditStatus
import com.vm.core.models.DiaryCollection
import com.vm.core.models.DiaryCollectionImage
import com.vm.core.models.DietEntry
import com.vm.core.models.RoutineEntry
import com.vm.core.models.RoutineStatus
import com.vm.core.models.RoutineType
import com.vm.core.models.WorkoutSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.moodColorForLevel
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.core.ui.theme.VectorTheme
import com.vm.vector.VectorApplication
import com.vm.vector.audio.DailyPlanAudioHelper
import com.vm.vector.data.DiaryPhotoSummary
import com.vm.vector.ui.util.isoCalendarDateToUtcPickerMillis
import com.vm.vector.ui.util.utcPickerMillisToIsoCalendarDate
import com.vm.vector.ui.viewmodel.CalendarUiState
import com.vm.vector.ui.viewmodel.CalendarViewModel
import com.vm.vector.ui.viewmodel.CalendarViewModelFactory
import com.vm.vector.util.applyExifOrientationToJpegBytes
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

enum class CalendarCategory(val displayName: String) {
    Routine("Routine"),
    Diet("Diet"),
    Workout("Workout"),
    Diary("Diary"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    initialDate: String? = null,
    initialCategory: String? = null,
    onNavigateToSettings: () -> Unit = {},
    viewModel: CalendarViewModel = viewModel(
        factory = CalendarViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val initialCat = when (initialCategory) {
        "Routine" -> CalendarCategory.Routine
        "Workout" -> CalendarCategory.Workout
        "Diet" -> CalendarCategory.Diet
        "Diary" -> CalendarCategory.Diary
        else -> CalendarCategory.Routine
    }
    var selectedCategory by rememberSaveable(initialCategory) { mutableStateOf(initialCat) }

    LaunchedEffect(initialDate) {
        if (!initialDate.isNullOrBlank()) {
            viewModel.selectDate(initialDate)
        }
    }

    // Set status bar appearance
    SideEffect {
        val window = (context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
        }
    }

    VectorTheme {
        Scaffold(
            containerColor = PureWhite,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            val layoutDirection = LocalLayoutDirection.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PureWhite)
                    .padding(
                        start = paddingValues.calculateLeftPadding(layoutDirection),
                        end = paddingValues.calculateRightPadding(layoutDirection),
                        bottom = paddingValues.calculateBottomPadding()
                    )
            ) {
                var showAddMealDialog by remember { mutableStateOf(false) }
                var showAddRoutineDialog by remember { mutableStateOf(false) }
                var showAddWorkoutDialog by remember { mutableStateOf(false) }
                var showActivateEditingAlert by remember { mutableStateOf(false) }
                var showRefreshFromDriveModal by remember { mutableStateOf(false) }
                var showRefreshDiaryModal by remember { mutableStateOf(false) }

                CategorySwitch(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                )

                WeekScroller(
                    selectedDate = uiState.currentDate,
                    datesWithCheckedMeals = uiState.datesWithCheckedMeals,
                    isEditingEnabled = uiState.isEditingEnabled,
                    showRefreshButton = selectedCategory == CalendarCategory.Diet ||
                        selectedCategory == CalendarCategory.Routine ||
                        selectedCategory == CalendarCategory.Workout ||
                        selectedCategory == CalendarCategory.Diary,
                    showAddButton = selectedCategory == CalendarCategory.Diet ||
                        selectedCategory == CalendarCategory.Routine ||
                        selectedCategory == CalendarCategory.Workout,
                    onDateSelected = { date ->
                        viewModel.selectDate(date)
                    },
                    onRefreshFromDriveClick = {
                        if (selectedCategory == CalendarCategory.Diary) {
                            showRefreshDiaryModal = true
                        } else {
                            showRefreshFromDriveModal = true
                        }
                    },
                    onAddMealClick = {
                        if (uiState.isEditingEnabled) {
                            when (selectedCategory) {
                                CalendarCategory.Diet -> showAddMealDialog = true
                                CalendarCategory.Routine -> showAddRoutineDialog = true
                                CalendarCategory.Workout -> showAddWorkoutDialog = true
                                else -> {}
                            }
                        } else {
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val today = dateFormat.format(Date())
                            if (uiState.currentDate < today) {
                                showActivateEditingAlert = true
                            }
                        }
                    },
                    onEditClick = {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val today = dateFormat.format(Date())
                        val isPastDay = uiState.currentDate < today
                        if (isPastDay) {
                            viewModel.toggleEditingEnabled()
                        }
                    },
                    isPastDay = {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val today = dateFormat.format(Date())
                        uiState.currentDate < today
                    },
                    isFutureDay = {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val today = dateFormat.format(Date())
                        uiState.currentDate > today
                    }
                )

                if (showAddMealDialog) {
                    AddMealDialog(
                        onDismiss = { showAddMealDialog = false },
                        onConfirm = { name, time, amount, unit, kcal, protein, carbs, fats ->
                            viewModel.addManualEntry(name, time, amount, unit, kcal, protein, carbs, fats)
                            showAddMealDialog = false
                        }
                    )
                }

                if (showAddRoutineDialog) {
                    AddRoutineDialog(
                        onDismiss = { showAddRoutineDialog = false },
                        onConfirm = { title, category, type, goalValue, partialThreshold, minValue, maxValue, unit, directionBetter ->
                            viewModel.addManualRoutineEntry(
                                title = title,
                                category = category,
                                type = type,
                                goalValue = goalValue,
                                partialThreshold = partialThreshold,
                                minValue = minValue,
                                maxValue = maxValue,
                                unit = unit,
                                directionBetter = directionBetter
                            )
                            showAddRoutineDialog = false
                        }
                    )
                }

                if (showAddWorkoutDialog) {
                    AddWorkoutFromClipboardDialog(
                        viewModel = viewModel,
                        onDismiss = { showAddWorkoutDialog = false },
                        onAdded = { showAddWorkoutDialog = false }
                    )
                }

                if (showActivateEditingAlert) {
                    AlertDialog(
                        onDismissRequest = { showActivateEditingAlert = false },
                        title = { Text("Editing locked", color = PureBlack) },
                        text = { Text("Tap the edit icon to enable editing for this date.", color = PureBlack) },
                        confirmButton = {
                            TextButton(onClick = { showActivateEditingAlert = false }) {
                                Text("OK", color = NavyDeep)
                            }
                        },
                        containerColor = PureWhite,
                    )
                }

                if (showRefreshFromDriveModal) {
                    AlertDialog(
                        onDismissRequest = { showRefreshFromDriveModal = false },
                        title = { Text("Reload preset", color = PureBlack) },
                        text = { Text("Reload the active preset for this day and replace current content?", color = PureBlack) },
                        confirmButton = {
                            TextButton(onClick = {
                                when (selectedCategory) {
                                    CalendarCategory.Diet -> viewModel.refreshFromDriveForDiet()
                                    CalendarCategory.Routine -> viewModel.refreshFromDriveForRoutine()
                                    CalendarCategory.Workout -> viewModel.refreshFromDriveForWorkout()
                                    else -> {}
                                }
                                showRefreshFromDriveModal = false
                            }) {
                                Text("Yes", color = NavyDeep)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRefreshFromDriveModal = false }) {
                                Text("No", color = NavyDeep)
                            }
                        },
                        containerColor = PureWhite
                    )
                }

                if (showRefreshDiaryModal) {
                    AlertDialog(
                        onDismissRequest = { showRefreshDiaryModal = false },
                        title = { Text("Refresh diary photos", color = PureBlack) },
                        text = {
                            Text(
                                "Fetch images from your Google Drive collections folder and update the list on this device?",
                                color = PureBlack
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.refreshDiaryFromDrive()
                                showRefreshDiaryModal = false
                            }) {
                                Text("Refresh", color = NavyDeep)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRefreshDiaryModal = false }) {
                                Text("Cancel", color = NavyDeep)
                            }
                        },
                        containerColor = PureWhite
                    )
                }

                if (selectedCategory == CalendarCategory.Diet) {
                    val groupedEntries = uiState.entries.groupBy { it.plannedTime }
                        .toSortedMap()

                    Box(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                        ) {
                            if (uiState.isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(16.dp),
                                        color = NavyDeep
                                    )
                                }
                            } else {
                                if (groupedEntries.isEmpty() && uiState.noPresetForDiet) {
                                    NoPresetPlaceholder(
                                        categoryName = "Diet",
                                        onLoadPreset = onNavigateToSettings
                                    )
                                } else if (groupedEntries.isEmpty()) {
                                    Text(
                                        text = "No meals for this day.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PureBlack.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(vertical = 24.dp)
                                    )
                                } else {
                                    val dietExpandedByTime = remember(uiState.currentDate) { mutableStateMapOf<String, Boolean>() }
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        groupedEntries.forEach { (time, entries) ->
                                            val isExpanded = dietExpandedByTime.getOrElse(time) { false }
                                            CollapsibleDietTimeSection(
                                                time = time,
                                                entries = entries,
                                                isExpanded = isExpanded,
                                                onToggleExpanded = {
                                                    dietExpandedByTime[time] = !isExpanded
                                                },
                                                isEditingEnabled = uiState.isEditingEnabled,
                                                viewModel = viewModel
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedCategory == CalendarCategory.Routine) {
                    val groupedEntries = uiState.routineEntries.groupBy { it.category }
                        .toSortedMap()

                    Box(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                        ) {
                            if (uiState.isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(16.dp),
                                        color = NavyDeep
                                    )
                                }
                            } else {
                                if (groupedEntries.isEmpty() && uiState.noPresetForRoutine) {
                                    NoPresetPlaceholder(
                                        categoryName = "Routine",
                                        onLoadPreset = onNavigateToSettings
                                    )
                                } else if (groupedEntries.isEmpty()) {
                                    Text(
                                        text = "No routine for this day.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PureBlack.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(vertical = 24.dp)
                                    )
                                } else {
                                    groupedEntries.forEach { (category, entries) ->
                                        CategoryHeader(category = category)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        entries.forEach { entry ->
                                            RoutineEntryCard(
                                                entry = entry,
                                                isEditingEnabled = uiState.isEditingEnabled,
                                                onValueChange = { newValue ->
                                                    viewModel.updateRoutineValue(entry.id, newValue)
                                                },
                                                onStatusChange = { newStatus ->
                                                    viewModel.updateRoutineStatus(entry.id, newStatus)
                                                },
                                                onToggleNA = {
                                                    viewModel.toggleNumericalStatus(entry.id)
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedCategory == CalendarCategory.Workout) {
                    var exerciseModalExerciseId by remember { mutableStateOf<String?>(null) }
                    val modalSets = remember(exerciseModalExerciseId, uiState.workoutSets) {
                        exerciseModalExerciseId?.let { id ->
                            uiState.workoutSets.filter { it.exerciseId == id }
                        }.orEmpty()
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        WorkoutContent(
                            sets = uiState.workoutSets,
                            noPreset = uiState.noPresetForWorkout,
                            onLoadPreset = onNavigateToSettings,
                            isEditingEnabled = uiState.isEditingEnabled,
                            onUpdateActuals = viewModel::updateWorkoutSetInMemory,
                            onUpdateExerciseStatus = viewModel::updateWorkoutExerciseStatus,
                            onExerciseClick = { exerciseModalExerciseId = it.first().exerciseId }
                        )
                    }
                    if (modalSets.isNotEmpty()) {
                        WorkoutExerciseDetailModal(
                            setsInExercise = modalSets,
                            onDismiss = { exerciseModalExerciseId = null },
                            isEditingEnabled = uiState.isEditingEnabled,
                            onUpdateActuals = viewModel::updateWorkoutSetInMemory
                        )
                    }
                } else if (selectedCategory == CalendarCategory.Diary) {
                    var diaryUnlocked by remember { mutableStateOf(false) }
                    LaunchedEffect(selectedCategory) {
                        if (selectedCategory != CalendarCategory.Diary) diaryUnlocked = false
                    }
                    val keyguardManager = remember(context) {
                        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    }
                    val credentialIntent = remember(keyguardManager) {
                        keyguardManager?.createConfirmDeviceCredentialIntent("Unlock Diary", "Use your device PIN, pattern or password to view your diary.")
                    }
                    val credentialLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == Activity.RESULT_OK) diaryUnlocked = true
                    }
                    if (!diaryUnlocked && credentialIntent != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = NavyDeep
                                )
                                Text(
                                    "Diary is protected",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NavyDeep
                                )
                                Button(
                                    onClick = { credentialLauncher.launch(credentialIntent) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite)
                                ) {
                                    Text("Unlock with device PIN")
                                }
                            }
                        }
                    } else if (diaryUnlocked || credentialIntent == null) {
                        if (credentialIntent == null) {
                            diaryUnlocked = true
                        }
                        DiaryContent(
                            modifier = Modifier.weight(1f),
                            viewModel = viewModel,
                            uiState = uiState,
                            isEditingEnabled = uiState.isEditingEnabled,
                            isDiaryRefreshing = uiState.isDiaryRefreshing,
                        )
                    }
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryPlaceholder(category = selectedCategory)
                    }
                }

                val editingEntry by viewModel.editingEntry.collectAsState()
                if (editingEntry != null) {
                    AddMealDialog(
                        entry = editingEntry,
                        onDismiss = { viewModel.clearEditingEntry() },
                        onConfirm = { name, time, amount, unit, kcal, protein, carbs, fats ->
                            viewModel.updateManualEntry(editingEntry!!.id, name, time, amount, unit, kcal, protein, carbs, fats)
                            viewModel.clearEditingEntry()
                        },
                        onDelete = {
                            viewModel.deleteEntry(editingEntry!!.id)
                            viewModel.clearEditingEntry()
                        }
                    )
                }

                if (selectedCategory == CalendarCategory.Routine) {
                    Button(
                        onClick = { viewModel.saveRoutineDay() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyDeep,
                            contentColor = PureWhite
                        ),
                        enabled = uiState.isEditingEnabled
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Save Changes")
                    }
                } else if (selectedCategory == CalendarCategory.Diet) {
                    Button(
                        onClick = { viewModel.saveDietDay() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyDeep,
                            contentColor = PureWhite
                        ),
                        enabled = uiState.isEditingEnabled
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Save Changes")
                    }
                } else if (selectedCategory == CalendarCategory.Workout) {
                    Button(
                        onClick = { viewModel.saveWorkout() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyDeep,
                            contentColor = PureWhite
                        ),
                        enabled = uiState.isEditingEnabled
                    ) {
                        if (uiState.isWorkoutSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Save Changes")
                    }
                } else if (selectedCategory == CalendarCategory.Diary) {
                    Button(
                        onClick = { viewModel.saveDiary() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyDeep,
                            contentColor = PureWhite
                        ),
                        enabled = uiState.isEditingEnabled
                    ) {
                        if (uiState.isDiarySaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Save Changes")
                    }
                }
            }
        }
        
        // Show snackbar messages
        if (uiState.showSnackbar) {
            LaunchedEffect(uiState.snackbarMessage) {
                snackbarHostState.showSnackbar(uiState.snackbarMessage)
                viewModel.dismissSnackbar()
            }
        }

        // Show alert when workout or routine data is received from watch
        val app = context.applicationContext as? VectorApplication
        if (app != null) {
            val wearWorkoutMessage by app.wearWorkoutReceivedMessage.collectAsState(initial = null)
            LaunchedEffect(wearWorkoutMessage) {
                wearWorkoutMessage?.let { msg ->
                    snackbarHostState.showSnackbar(msg)
                    app.clearWearWorkoutReceivedMessage()
                }
            }
            val wearRoutineMessage by app.wearRoutineReceivedMessage.collectAsState(initial = null)
            LaunchedEffect(wearRoutineMessage) {
                wearRoutineMessage?.let { msg ->
                    snackbarHostState.showSnackbar(msg)
                    app.clearWearRoutineReceivedMessage()
                }
            }
        }
    }
}

@Composable
private fun CategorySwitch(
    selectedCategory: CalendarCategory,
    onCategorySelected: (CalendarCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CalendarCategory.entries.forEach { category ->
            val selected = selectedCategory == category
            val icon = when (category) {
                CalendarCategory.Routine -> Icons.Default.Schedule
                CalendarCategory.Diet -> Icons.Default.Restaurant
                CalendarCategory.Workout -> Icons.Default.FitnessCenter
                CalendarCategory.Diary -> Icons.Default.EditNote
            }
            Surface(
                onClick = { onCategorySelected(category) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = if (selected) NavyDeep else PureWhite,
                border = if (selected) null else BorderStroke(1.dp, NavyDeep),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = category.displayName,
                        tint = if (selected) PureWhite else NavyDeep,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPlaceholder(category: CalendarCategory) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.headlineMedium,
            color = PureBlack,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "(In progress)",
            style = MaterialTheme.typography.bodyMedium,
            color = PureBlack.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun NoPresetPlaceholder(
    categoryName: String,
    onLoadPreset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No preset is active for $categoryName.",
            style = MaterialTheme.typography.bodyMedium,
            color = PureBlack.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onLoadPreset,
            colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
        ) {
            Text("Load preset")
        }
    }
}

@Composable
private fun DiarySectionTitle(title: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = NavyDeep
        )
        HorizontalDivider(color = NavyDeep, thickness = 2.dp)
    }
}

// Scale 1–5: 1 = saddest (orange), 5 = happiest (green). Red and orange emoji faces swapped from original order.
private val MOOD_EMOJIS = listOf("\uD83D\uDE1E", "\uD83D\uDE14", "\uD83D\uDE41", "\uD83D\uDE0A", "\uD83D\uDE00") // level 1 = orange face, 2 = red face, then neutral → happy
@Composable
private fun DiaryContent(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel,
    uiState: CalendarUiState,
    isEditingEnabled: Boolean,
    isDiaryRefreshing: Boolean,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        DiarySectionTitle("MOOD")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            (1..5).forEach { level ->
                val selected = uiState.diaryMood == level
                val color = moodColorForLevel(level)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (selected) color else color.copy(alpha = 0.3f))
                        .then(if (selected) Modifier.border(2.dp, NavyDeep, CircleShape) else Modifier)
                        .clickable(enabled = isEditingEnabled) { viewModel.setDiaryMood(level) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = MOOD_EMOJIS[level - 1], style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        DiarySectionTitle("JOURNAL")
        JournalAudioRow(
            viewModel = viewModel,
            currentDate = uiState.currentDate,
            diaryAudioPath = uiState.diaryAudioPath,
            diaryAudioDriveFileId = uiState.diaryAudioDriveFileId,
            isEditingEnabled = isEditingEnabled
        )
        OutlinedTextField(
            value = uiState.diaryJournalText,
            onValueChange = viewModel::setDiaryJournalText,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            placeholder = { Text("How was your day?", color = SlateGray) },
            maxLines = 6
        )
        Spacer(modifier = Modifier.height(16.dp))

        DiarySectionTitle("COLLECTIONS")
        DiaryCollectionsSection(
            viewModel = viewModel,
            collectionsWithImages = uiState.diaryCollectionsWithImages,
            isDiaryRefreshing = isDiaryRefreshing,
            isCreatingCollection = uiState.isCreatingCollection,
            isEditingEnabled = isEditingEnabled
        )
    }
}

@Composable
private fun JournalAudioRow(
    viewModel: CalendarViewModel,
    currentDate: String,
    diaryAudioPath: String?,
    diaryAudioDriveFileId: String?,
    isEditingEnabled: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioHelper = remember { DailyPlanAudioHelper(context) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    val hasLocalAudio = !diaryAudioPath.isNullOrBlank() && audioHelper.fileExists(diaryAudioPath)
    val hasDriveAudio = !diaryAudioDriveFileId.isNullOrBlank()
    val hasAudio = hasLocalAudio || hasDriveAudio

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val path = audioHelper.startRecordingToPath(
                DailyPlanAudioHelper.relativePathForDiaryDate(currentDate)
            ) { /* showError could be passed if needed */ }
            if (path != null) {
                isRecording = true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioHelper.stopRecording()
            audioHelper.stopPlayback()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isRecording) {
            IconButton(
                onClick = {
                    audioHelper.stopRecording()
                    val path = DailyPlanAudioHelper.relativePathForDiaryDate(currentDate)
                    viewModel.setDiaryAudioPath(path)
                    isRecording = false
                }
            ) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop recording", tint = NavyDeep)
            }
            Text("Recording...", color = NavyDeep, style = MaterialTheme.typography.bodyMedium)
        } else {
            if (isEditingEnabled) {
                IconButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            val path = audioHelper.startRecordingToPath(
                                DailyPlanAudioHelper.relativePathForDiaryDate(currentDate)
                            ) { }
                            if (path != null) isRecording = true
                        } else {
                            recordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Record", tint = NavyDeep)
                }
            }
            if (hasAudio) {
                if (isPlaying) {
                    IconButton(onClick = {
                        audioHelper.pausePlayback()
                        isPlaying = false
                        isPaused = true
                    }) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = NavyDeep)
                    }
                } else {
                    IconButton(onClick = {
                        if (isPaused) {
                            audioHelper.resumePlayback()
                            isPlaying = true
                            isPaused = false
                        } else {
                            val pathToPlay = when {
                                hasLocalAudio -> diaryAudioPath
                                else -> null
                            }
                            if (pathToPlay != null) {
                                audioHelper.startPlayback(
                                    pathToPlay,
                                    onCompletion = { isPlaying = false; isPaused = false },
                                    onError = { }
                                )
                                isPlaying = true
                            } else if (diaryAudioDriveFileId != null) {
                                scope.launch {
                                    val bytes = viewModel.getDiaryAudioBytes()
                                    if (bytes != null) {
                                        val relPath = audioHelper.writeDiaryPlaybackBytes(bytes)
                                        if (relPath != null) {
                                            audioHelper.startPlayback(
                                                relPath,
                                                onCompletion = { isPlaying = false; isPaused = false },
                                                onError = { }
                                            )
                                            isPlaying = true
                                        }
                                    }
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = NavyDeep)
                    }
                }
                if (isEditingEnabled) {
                    IconButton(onClick = {
                        audioHelper.stopPlayback()
                        if (hasLocalAudio && diaryAudioPath != null) {
                            audioHelper.deleteFile(diaryAudioPath)
                        }
                        viewModel.setDiaryAudioPath(null)
                        viewModel.setDiaryAudioDriveFileId(null)
                        isPlaying = false
                        isPaused = false
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove recording", tint = NavyDeep)
                    }
                }
                if (hasAudio && !isRecording) {
                    Text("Recording saved", color = SlateGray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

private data class PendingPhoto(val collectionId: String, val uri: Uri)

private fun formatDiaryPhotoRange(summary: DiaryPhotoSummary): String {
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return if (summary.rangeStartMillis == summary.rangeEndMillis) {
        fmt.format(Date(summary.rangeStartMillis))
    } else {
        "${fmt.format(Date(summary.rangeStartMillis))} – ${fmt.format(Date(summary.rangeEndMillis))}"
    }
}

private fun diaryAlbumSummary(images: List<DiaryCollectionImage>): DiaryPhotoSummary? {
    if (images.isEmpty()) return null
    val times = images.map { it.takenAtMillis }
    val latest = images.sortedByDescending { it.takenAtMillis }.take(4).map { it.driveFileId }
    return DiaryPhotoSummary(
        totalCount = images.size,
        rangeStartMillis = times.minOrNull()!!,
        rangeEndMillis = times.maxOrNull()!!,
        latestPreviewDriveFileIds = latest
    )
}

@Composable
private fun DiaryCollectionsSection(
    viewModel: CalendarViewModel,
    collectionsWithImages: List<Pair<DiaryCollection, List<DiaryCollectionImage>>>,
    isDiaryRefreshing: Boolean,
    isCreatingCollection: Boolean,
    isEditingEnabled: Boolean
) {
    val context = LocalContext.current
    var showNewCollectionDialog by remember { mutableStateOf(false) }
    var showTimelapseConfig by remember { mutableStateOf<List<DiaryCollectionImage>?>(null) }
    var showImageViewer by remember { mutableStateOf<DiaryCollectionImage?>(null) }
    var showImageGallery by remember { mutableStateOf<List<DiaryCollectionImage>?>(null) }
    var showDeleteCollectionConfirm by remember { mutableStateOf<DiaryCollection?>(null) }
    var showDeleteImageConfirm by remember { mutableStateOf<DiaryCollectionImage?>(null) }
    var pendingPhoto by remember { mutableStateOf<PendingPhoto?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        pendingPhoto?.let { (collectionId, uri) ->
            if (success) {
                coroutineScope.launch {
                    try {
                        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (rawBytes == null) {
                            viewModel.notifyDiaryError("Failed to read captured photo.")
                            return@launch
                        }
                        val bytes = applyExifOrientationToJpegBytes(rawBytes) ?: rawBytes
                        viewModel.addImageToCollection(
                            collectionId = collectionId,
                            mimeType = "image/jpeg",
                            bytes = bytes,
                            takenAtMillis = System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        viewModel.notifyDiaryError("Failed to upload photo: ${e.message ?: "Unknown error"}")
                    } finally {
                        pendingPhoto = null
                    }
                }
            } else {
                pendingPhoto = null
            }
        } ?: run { pendingPhoto = null }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingPhoto?.let { takePictureLauncher.launch(it.uri) }
        else pendingPhoto = null
    }

    fun startCameraForCollection(collectionId: String) {
        val file = File(context.cacheDir, "diary_${System.currentTimeMillis()}.jpg")
        file.createNewFile()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = PendingPhoto(collectionId, uri)
        when {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
                takePictureLauncher.launch(uri)
            else -> permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Albums (Google Drive)",
                    style = MaterialTheme.typography.titleSmall,
                    color = NavyDeep
                )
                Text(
                    text = "Each folder under your Drive “collections” folder is an album. Refresh to sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateGray
                )
            }
            if (isDiaryRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = NavyDeep,
                    strokeWidth = 2.dp
                )
            }
        }

        Button(
            onClick = { showNewCollectionDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("New album")
        }

        if (collectionsWithImages.isEmpty()) {
            Text(
                text = "No albums yet. Create one or pull from Drive with Refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = PureBlack.copy(alpha = 0.6f)
            )
        }

        collectionsWithImages.forEach { (collection, images) ->
            DiaryAlbumCard(
                collection = collection,
                images = images,
                viewModel = viewModel,
                isEditingEnabled = isEditingEnabled,
                onAddPhoto = { startCameraForCollection(collection.id) },
                onTimelapse = {
                    if (images.isNotEmpty()) showTimelapseConfig = images.sortedBy { it.takenAtMillis }
                },
                onRemoveAlbum = { showDeleteCollectionConfirm = collection },
                onImageClick = { showImageViewer = it },
                onViewAll = { showImageGallery = images.sortedBy { it.takenAtMillis } }
            )
        }
    }

    if (showNewCollectionDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!isCreatingCollection) showNewCollectionDialog = false },
            title = { Text("New album") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Album name (Drive folder title)") },
                    singleLine = true,
                    enabled = !isCreatingCollection
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isCreatingCollection) return@Button
                        coroutineScope.launch {
                            viewModel.createCollection(name.trim()).fold(
                                onSuccess = { showNewCollectionDialog = false; name = "" },
                                onFailure = { }
                            )
                        }
                    },
                    enabled = !isCreatingCollection,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite)
                ) { Text(if (isCreatingCollection) "Creating…" else "Create") }
            },
            dismissButton = {
                TextButton(onClick = { if (!isCreatingCollection) showNewCollectionDialog = false }) { Text("Cancel") }
            }
        )
    }

    showDeleteCollectionConfirm?.let { col ->
        AlertDialog(
            onDismissRequest = { showDeleteCollectionConfirm = null },
            title = { Text("Remove album?") },
            text = {
                Text(
                    "Remove \"${col.name}\" from this device? Photo files stay on Google Drive until you delete them in Drive."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDiaryCollection(col)
                    showDeleteCollectionConfirm = null
                }) { Text("Remove", color = DeleteRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCollectionConfirm = null }) { Text("Cancel") }
            }
        )
    }

    showImageViewer?.let { image ->
        DiaryImageViewerDialog(
            image = image,
            viewModel = viewModel,
            onDismiss = { showImageViewer = null },
            onDelete = {
                showDeleteImageConfirm = image
                showImageViewer = null
            }
        )
    }

    showDeleteImageConfirm?.let { img ->
        AlertDialog(
            onDismissRequest = { showDeleteImageConfirm = null },
            title = { Text("Remove image?") },
            text = { Text("This will delete the image from the app and from Drive.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDiaryImage(img)
                    showDeleteImageConfirm = null
                }) { Text("Delete", color = DeleteRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteImageConfirm = null }) { Text("Cancel") }
            }
        )
    }

    showImageGallery?.let { imgs ->
        DiaryImageGalleryDialog(
            images = imgs,
            viewModel = viewModel,
            onDismiss = { showImageGallery = null }
        )
    }

    showTimelapseConfig?.let { imgs ->
        DiaryTimelapseConfigDialog(
            images = imgs,
            viewModel = viewModel,
            onDismiss = { showTimelapseConfig = null }
        )
    }
}

@Composable
private fun DiaryAlbumCard(
    collection: DiaryCollection,
    images: List<DiaryCollectionImage>,
    viewModel: CalendarViewModel,
    isEditingEnabled: Boolean,
    onAddPhoto: () -> Unit,
    onTimelapse: () -> Unit,
    onRemoveAlbum: () -> Unit,
    onImageClick: (DiaryCollectionImage) -> Unit,
    onViewAll: () -> Unit,
) {
    val summary = diaryAlbumSummary(images)
    val previewImages = remember(images) {
        images.sortedByDescending { it.takenAtMillis }.take(4)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = NavyDeep
                    )
                    if (summary != null) {
                        Text(
                            text = "${summary.totalCount} photos · ${formatDiaryPhotoRange(summary)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateGray
                        )
                    } else {
                        Text(
                            text = "No photos",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateGray
                        )
                    }
                }
                if (isEditingEnabled) {
                    IconButton(onClick = onRemoveAlbum) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove album",
                            tint = DeleteRed
                        )
                    }
                }
            }
            if (previewImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    previewImages.forEach { img ->
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageClick(img) }
                        ) {
                            DiaryDriveThumb(
                                driveFileId = img.driveFileId,
                                viewModel = viewModel,
                                size = 56.dp
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onAddPhoto,
                    enabled = isEditingEnabled
                ) { Text("Add photo") }
                TextButton(
                    onClick = onTimelapse,
                    enabled = images.isNotEmpty()
                ) { Text("Timelapse") }
                if (images.isNotEmpty()) {
                    TextButton(onClick = onViewAll) { Text("View all") }
                }
            }
        }
    }
}

@Composable
private fun DiaryDriveThumb(
    driveFileId: String,
    viewModel: CalendarViewModel,
    size: Dp,
) {
    var bitmap by remember(driveFileId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(driveFileId) {
        val bytes = viewModel.getImageBytes(driveFileId)
        bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(PureBlack.copy(alpha = 0.08f))
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun DiaryImageThumb(
    image: DiaryCollectionImage,
    viewModel: CalendarViewModel,
    onClick: () -> Unit,
) {
    var bitmap by remember(image.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(image.driveFileId) {
        val bytes = viewModel.getImageBytes(image.driveFileId)
        bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(PureBlack.copy(alpha = 0.08f))
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun DiaryImageViewerDialog(
    image: DiaryCollectionImage,
    viewModel: CalendarViewModel,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(image.driveFileId) {
        val bytes = viewModel.getImageBytes(image.driveFileId)
        bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = dateFormat.format(Date(image.takenAtMillis)),
                    style = MaterialTheme.typography.labelMedium,
                    color = PureBlack
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    bitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDelete) { Text("Remove", color = DeleteRed) }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun DiaryImageGalleryDialog(
    images: List<DiaryCollectionImage>,
    viewModel: CalendarViewModel,
    onDismiss: () -> Unit
) {
    var index by remember { mutableStateOf(0) }
    val img = images.getOrNull(index)
    var bitmap by remember(img?.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(img?.driveFileId) {
        if (img != null) {
            val bytes = viewModel.getImageBytes(img.driveFileId)
            bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
    }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (img != null) {
                    Text(
                        text = dateFormat.format(Date(img.takenAtMillis)),
                        style = MaterialTheme.typography.labelMedium,
                        color = PureBlack
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    bitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { if (index > 0) index-- },
                        enabled = index > 0
                    ) { Text("Previous") }
                    Text("${index + 1} / ${images.size}")
                    Button(
                        onClick = { if (index < images.size - 1) index++ },
                        enabled = index < images.size - 1
                    ) { Text("Next") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@Composable
private fun DiaryTimelapseConfigDialog(
    images: List<DiaryCollectionImage>,
    viewModel: CalendarViewModel,
    onDismiss: () -> Unit
) {
    var durationSeconds by remember { mutableStateOf(10) }
    var showDay by remember { mutableStateOf(true) }
    var showMonth by remember { mutableStateOf(true) }
    var showYear by remember { mutableStateOf(true) }
    var showHour by remember { mutableStateOf(false) }
    var showMinute by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    if (started) {
        DiaryTimelapsePlayer(
            images = images,
            durationSeconds = durationSeconds,
            showDay = showDay,
            showMonth = showMonth,
            showYear = showYear,
            showHour = showHour,
            showMinute = showMinute,
            viewModel = viewModel,
            onDismiss = {
                started = false
                onDismiss()
            }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timelapse settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Duration (seconds)")
                Slider(
                    value = durationSeconds.toFloat(),
                    onValueChange = { durationSeconds = it.toInt().coerceIn(1, 120) },
                    valueRange = 1f..120f,
                    steps = 118
                )
                Text("$durationSeconds s")
                Text("Label on each image:")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showDay, onCheckedChange = { showDay = it })
                    Text("Day")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showMonth, onCheckedChange = { showMonth = it })
                    Text("Month")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showYear, onCheckedChange = { showYear = it })
                    Text("Year")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showHour, onCheckedChange = { showHour = it })
                    Text("Hour")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showMinute, onCheckedChange = { showMinute = it })
                    Text("Minute")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { started = true }) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DiaryTimelapsePlayer(
    images: List<DiaryCollectionImage>,
    durationSeconds: Int,
    showDay: Boolean,
    showMonth: Boolean,
    showYear: Boolean,
    showHour: Boolean,
    showMinute: Boolean,
    viewModel: CalendarViewModel,
    onDismiss: () -> Unit
) {
    val sortedImages = remember(images) { images.sortedBy { it.takenAtMillis } }
    var currentIndex by remember(sortedImages) { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    LaunchedEffect(sortedImages) {
        currentIndex = 0
        progress = 0f
        isPlaying = false
    }
    val totalMs = durationSeconds * 1000L
    val perImageMs = if (sortedImages.isEmpty()) 1000L else (totalMs / sortedImages.size).coerceAtLeast(1L)
    val currentImage = sortedImages.getOrNull(currentIndex)
    var bitmap by remember(currentImage?.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(currentImage?.driveFileId) {
        if (currentImage != null) {
            val bytes = viewModel.getImageBytes(currentImage.driveFileId)
            bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } else {
            bitmap = null
        }
    }
    LaunchedEffect(isPlaying, currentIndex, sortedImages.size) {
        if (!isPlaying || sortedImages.isEmpty()) return@LaunchedEffect
        if (sortedImages.size <= 1) return@LaunchedEffect
        kotlinx.coroutines.delay(perImageMs)
        currentIndex = (currentIndex + 1) % sortedImages.size
        progress = currentIndex.toFloat() / (sortedImages.size - 1).coerceAtLeast(1)
    }
    val dateFormat = remember {
        val parts = mutableListOf<String>()
        if (showYear) parts.add("yyyy")
        if (showMonth) parts.add("MMM")
        if (showDay) parts.add("d")
        if (showHour || showMinute) parts.add("HH:mm")
        SimpleDateFormat(parts.joinToString(" "), Locale.getDefault())
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (currentImage != null) {
                    Text(
                        text = dateFormat.format(Date(currentImage.takenAtMillis)),
                        style = MaterialTheme.typography.titleSmall,
                        color = NavyDeep
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    bitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Slider(
                    value = progress,
                    onValueChange = {
                        progress = it
                        if (sortedImages.size > 1) {
                            currentIndex =
                                (it * (sortedImages.size - 1)).toInt().coerceIn(0, sortedImages.size - 1)
                        }
                    },
                    valueRange = 0f..1f,
                    enabled = sortedImages.size > 1
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { isPlaying = !isPlaying }) {
                        Text(if (isPlaying) "Pause" else "Play")
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

private val WORKOUT_STATUS_OPTIONS = listOf("None", "Not Done", "Partial", "Done", "Exceeded", "N/A")
private fun getWorkoutStatusColor(status: String?): androidx.compose.ui.graphics.Color = when (status) {
    "Done" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    "Partial" -> androidx.compose.ui.graphics.Color(0xFFFF9800)
    "Not Done" -> androidx.compose.ui.graphics.Color(0xFFD32F2F)
    "Exceeded" -> ElectricBlue
    "N/A" -> androidx.compose.ui.graphics.Color(0xFF757575)
    else -> PureBlack.copy(alpha = 0.5f) // None or null
}

@Composable
private fun WorkoutContent(
    sets: List<WorkoutSet>,
    noPreset: Boolean,
    onLoadPreset: () -> Unit,
    isEditingEnabled: Boolean,
    onUpdateActuals: (setId: String, actualLoad: Double?, actualReps: Int?, actualVelocity: Double?, actualRpe: Double?, actualRir: Int?, actualDuration: Int?, actualDistance: Double?, actualRest: Int?) -> Unit,
    onUpdateExerciseStatus: (exerciseId: String, status: String?) -> Unit,
    onExerciseClick: (List<WorkoutSet>) -> Unit
) {
    val scrollState = rememberScrollState()
    var expandedSessionKeys by remember { mutableStateOf(setOf<String>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        if (sets.isEmpty()) {
            if (noPreset) {
                NoPresetPlaceholder(
                    categoryName = "Workout",
                    onLoadPreset = onLoadPreset
                )
            } else {
                Text(
                    text = "No workout for this day. Select another date or load a preset in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureBlack.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            return@Column
        }
        val sessions = sets.groupBy { it.sessionTitle.ifBlank { "Workout" } }.toList()
            .map { (sessionTitle, sessionSets) ->
                val orderedByExercise = sessionSets.groupBy { it.exerciseId }
                    .toList()
                    .map { (_, block) -> block to sessionSets.indexOf(block.first()) }
                    .sortedBy { it.second }
                    .map { it.first }
                Triple(sessionTitle, sessionSets, orderedByExercise)
            }
            .sortedBy { (_, sessionSets, _) -> sessionSets.minOfOrNull { it.exerciseId } ?: "" }
        sessions.forEach { (sessionTitle, sessionSets, orderedByExercise) ->
            val sessionKey = sessionTitle
            val isExpanded = sessionKey in expandedSessionKeys
            val sessionDescription = sessionSets.firstOrNull()?.sessionDescription?.orEmpty() ?: ""
            WorkoutSessionTile(
                sessionTitle = sessionTitle,
                sessionDescription = sessionDescription,
                isExpanded = isExpanded,
                onClick = { expandedSessionKeys = if (isExpanded) expandedSessionKeys - sessionKey else expandedSessionKeys + sessionKey },
                exerciseBlocks = orderedByExercise,
                isEditingEnabled = isEditingEnabled,
                onStatusChange = onUpdateExerciseStatus,
                onExerciseClick = onExerciseClick
            )
        }
    }
}

private val WorkoutTileOrange = androidx.compose.ui.graphics.Color(0xFFFF9800)
private val WorkoutTileGreen = androidx.compose.ui.graphics.Color(0xFF4CAF50)
private val WorkoutTileBlue = androidx.compose.ui.graphics.Color(0xFF2196F3)
private val WorkoutTileRed = androidx.compose.ui.graphics.Color(0xFFD32F2F)

@Composable
private fun WorkoutSessionTile(
    sessionTitle: String,
    sessionDescription: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    exerciseBlocks: List<List<WorkoutSet>>,
    isEditingEnabled: Boolean,
    onStatusChange: (exerciseId: String, status: String?) -> Unit,
    onExerciseClick: (List<WorkoutSet>) -> Unit
) {
    val statuses = exerciseBlocks.map { block -> block.firstOrNull()?.workoutStatus }
    val relevant = statuses.filter { it != "N/A" }
    val tileColor = when {
        relevant.isEmpty() -> PureWhite
        relevant.all { it in listOf("None", "Not Done") } -> WorkoutTileRed
        relevant.all { it == "Exceeded" } -> WorkoutTileBlue.copy(alpha = 0.2f)
        relevant.all { it == "Done" } -> WorkoutTileGreen.copy(alpha = 0.2f)
        relevant.any { it == "Done" } && relevant.any { it == "Exceeded" } && relevant.all { it in listOf("Done", "Exceeded") } -> WorkoutTileGreen.copy(alpha = 0.2f)
        relevant.any { it == "Partial" } -> WorkoutTileOrange.copy(alpha = 0.2f)
        relevant.toSet().size > 1 -> WorkoutTileOrange.copy(alpha = 0.2f)
        else -> PureWhite
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = tileColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, NavyDeep.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = sessionTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = NavyDeep,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = NavyDeep
                )
            }
            if (isExpanded && exerciseBlocks.isNotEmpty()) {
                if (sessionDescription.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = sessionDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureBlack.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    exerciseBlocks.forEach { block ->
                        SimpleExerciseCard(
                            setsInExercise = block,
                            isEditingEnabled = isEditingEnabled,
                            onStatusChange = { onStatusChange(block.first().exerciseId, it) },
                            onClick = { onExerciseClick(block) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(NavyDeep)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = NavyDeep,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(NavyDeep)
        )
    }
}

@Composable
private fun SimpleExerciseCard(
    setsInExercise: List<WorkoutSet>,
    isEditingEnabled: Boolean,
    onStatusChange: (String?) -> Unit,
    onClick: () -> Unit
) {
    val exerciseName = setsInExercise.firstOrNull()?.exerciseName?.ifBlank { setsInExercise.first().exerciseId } ?: ""
    val currentStatus = setsInExercise.firstOrNull()?.workoutStatus
    var expanded by remember { mutableStateOf(false) }
    val statusColor = getWorkoutStatusColor(currentStatus)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, NavyDeep.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.titleSmall,
                color = NavyDeep,
                modifier = Modifier.weight(1f)
            )
            Box {
                Button(
                    onClick = { if (isEditingEnabled) expanded = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = statusColor.copy(alpha = 0.2f),
                        contentColor = statusColor,
                        disabledContainerColor = PureBlack.copy(alpha = 0.12f),
                        disabledContentColor = PureBlack.copy(alpha = 0.38f)
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    enabled = isEditingEnabled,
                ) {
                    Text(
                        text = currentStatus ?: "None",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = PureWhite,
                ) {
                    WORKOUT_STATUS_OPTIONS.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status, color = PureBlack) },
                            onClick = {
                                onStatusChange(if (status == "None") null else status)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutExerciseDetailModal(
    setsInExercise: List<WorkoutSet>,
    onDismiss: () -> Unit,
    isEditingEnabled: Boolean,
    onUpdateActuals: (setId: String, actualLoad: Double?, actualReps: Int?, actualVelocity: Double?, actualRpe: Double?, actualRir: Int?, actualDuration: Int?, actualDistance: Double?, actualRest: Int?) -> Unit
) {
    val monospaceLabel = MaterialTheme.typography.labelMedium.copy(
        fontFamily = FontFamily.Monospace
    )
    val first = setsInExercise.firstOrNull()
    val exerciseName = first?.exerciseName ?: ""
    val exerciseDescription = first?.exerciseDescription?.takeIf { it.isNotBlank() } ?: ""
    val timing = first?.timing?.takeIf { it.isNotBlank() }
    val exerciseType = first?.exerciseType?.ifBlank { "RESISTANCE" } ?: "RESISTANCE"
    val showSetLabel = exerciseType == "RESISTANCE"
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = PureWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 0.dp)
            ) {
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.titleLarge,
                    color = PureBlack,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (exerciseDescription.isNotBlank() || timing != null) {
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        if (exerciseDescription.isNotBlank()) {
                            Text(
                                text = exerciseDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureBlack.copy(alpha = 0.85f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        if (timing != null) {
                            Text(
                                text = "Timing: $timing",
                                style = MaterialTheme.typography.labelMedium,
                                color = PureBlack.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    setsInExercise.sortedBy { it.setNumber }.forEach { setEntry ->
                        WorkoutSetRow(
                            setEntry = setEntry,
                            exerciseDescription = exerciseDescription,
                            showSetLabel = showSetLabel,
                            isEditingEnabled = isEditingEnabled,
                            monospaceLabel = monospaceLabel,
                            onUpdateActuals = onUpdateActuals
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = NavyDeep)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(NavyDeep)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = PureBlack,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(NavyDeep)
        )
    }
}

@Composable
private fun SupersetDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(NavyDeep.copy(alpha = 0.5f))
        )
        Text(
            text = "SUPERSET",
            style = MaterialTheme.typography.labelSmall,
            color = NavyDeep,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(NavyDeep.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun ExerciseBlock(
    setsInExercise: List<WorkoutSet>,
    isEditingEnabled: Boolean,
    monospaceLabel: androidx.compose.ui.text.TextStyle,
    onUpdateActuals: (setId: String, actualLoad: Double?, actualReps: Int?, actualVelocity: Double?, actualRpe: Double?, actualRir: Int?, actualDuration: Int?, actualDistance: Double?, actualRest: Int?) -> Unit
) {
    val exerciseName = setsInExercise.firstOrNull()?.exerciseName?.ifBlank { setsInExercise.first().exerciseId } ?: ""
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, NavyDeep.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.titleSmall,
                color = NavyDeep,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val exerciseDesc = setsInExercise.firstOrNull()?.exerciseDescription?.takeIf { it.isNotBlank() } ?: ""
            setsInExercise.sortedBy { it.setNumber }.forEach { setEntry ->
                WorkoutSetRow(
                    setEntry = setEntry,
                    exerciseDescription = exerciseDesc,
                    showSetLabel = setsInExercise.firstOrNull()?.exerciseType == "RESISTANCE",
                    isEditingEnabled = isEditingEnabled,
                    monospaceLabel = monospaceLabel,
                    onUpdateActuals = onUpdateActuals
                )
            }
        }
    }
}

@Composable
private fun WorkoutNumberStepper(
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
            IconButton(
                onClick = { onValueChange((value - 1).coerceIn(min, max)) },
                enabled = enabled && value > min,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Decrease",
                    tint = if (enabled && value > min) NavyDeep else PureBlack.copy(alpha = 0.38f)
                )
            }
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
            IconButton(
                onClick = { onValueChange((value + 1).coerceIn(min, max)) },
                enabled = enabled && value < max,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Increase",
                    tint = if (enabled && value < max) NavyDeep else PureBlack.copy(alpha = 0.38f)
                )
            }
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
private fun WorkoutNumberStepperDouble(
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
            IconButton(
                onClick = { onValueChange(kotlin.math.round((value - step).coerceIn(min, max) * 10) / 10) },
                enabled = enabled && value > min,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Decrease",
                    tint = if (enabled && value > min) NavyDeep else PureBlack.copy(alpha = 0.38f)
                )
            }
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
            IconButton(
                onClick = { onValueChange(kotlin.math.round((value + step).coerceIn(min, max) * 10) / 10) },
                enabled = enabled && value < max,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Increase",
                    tint = if (enabled && value < max) NavyDeep else PureBlack.copy(alpha = 0.38f)
                )
            }
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

@Composable
private fun WorkoutSetRow(
    setEntry: WorkoutSet,
    exerciseDescription: String,
    showSetLabel: Boolean,
    isEditingEnabled: Boolean,
    monospaceLabel: androidx.compose.ui.text.TextStyle,
    onUpdateActuals: (setId: String, actualLoad: Double?, actualReps: Int?, actualVelocity: Double?, actualRpe: Double?, actualRir: Int?, actualDuration: Int?, actualDistance: Double?, actualRest: Int?) -> Unit
) {
    val currentLoad = (setEntry.actualLoad ?: setEntry.targetLoad ?: 0.0).coerceIn(0.0, 999.9)
    val currentReps = (setEntry.actualReps ?: setEntry.targetReps ?: 0).coerceIn(0, 999)
    val currentVelocity = (setEntry.actualVelocity ?: setEntry.targetVelocity ?: 0.0).toInt().coerceIn(0, 99)
    val currentRpe = (setEntry.actualRpe ?: 0.0).toInt().coerceIn(0, 10)
    val currentRir = (setEntry.actualRir ?: setEntry.targetRir ?: 0).coerceIn(0, 10)
    val durationUnitIsMin = (setEntry.unitDuration.lowercase().let { it == "min" || it == "minutes" || it == "minute" })
    val currentDuration = when {
        durationUnitIsMin -> (setEntry.actualDuration?.div(60) ?: setEntry.targetDuration ?: 0).coerceIn(0, 9999)
        else -> (setEntry.actualDuration ?: setEntry.targetDuration ?: 0).coerceIn(0, 9999)
    }
    val currentDistance = (setEntry.actualDistance ?: setEntry.targetDistance ?: 0.0).coerceIn(0.0, 999.9)
    val currentRest = (setEntry.actualRest ?: setEntry.targetRest ?: 0).coerceIn(0, 9999)

    val showLoad = setEntry.targetLoad != null || setEntry.actualLoad != null
    val showReps = setEntry.targetReps != null || setEntry.actualReps != null
    val showRpe = setEntry.actualRpe != null || setEntry.targetRir != null || setEntry.targetDuration != null || setEntry.targetDistance != null
    val showRir = setEntry.targetRir != null || setEntry.actualRir != null
    val showVelocity = setEntry.targetVelocity != null || setEntry.actualVelocity != null
    val showDuration = setEntry.targetDuration != null || setEntry.actualDuration != null
    val showDistance = setEntry.targetDistance != null || setEntry.actualDistance != null
    val showRest = setEntry.targetRest != null || setEntry.actualRest != null
    val hasAnyActual = showLoad || showReps || showRpe || showRir || showVelocity || showDuration || showDistance || showRest

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = OffWhite.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, NavyDeep.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (showSetLabel) {
                Text(
                    text = "Set ${setEntry.setNumber}",
                    style = MaterialTheme.typography.titleSmall,
                    color = NavyDeep,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            if (setEntry.description.isNotBlank() && !(setEntry.setNumber == 1 && exerciseDescription.isNotBlank())) {
                Text(
                    text = setEntry.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureBlack,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            if (setEntry.cadence != null) {
                Text(
                    text = "Cadence: ${setEntry.cadence}",
                    style = monospaceLabel,
                    color = PureBlack.copy(alpha = 0.75f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (setEntry.targetLoad != null || setEntry.targetReps != null || setEntry.targetVelocity != null ||
                setEntry.targetDuration != null || setEntry.targetDistance != null || setEntry.targetRir != null || setEntry.targetRest != null) {
                Text(
                    text = "Target",
                    style = MaterialTheme.typography.labelSmall,
                    color = PureBlack.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                val targetParts = buildList {
                    setEntry.targetLoad?.let { add("${it} ${setEntry.unitLoad}") }
                    setEntry.targetReps?.let { add("$it r") }
                    setEntry.targetVelocity?.let { add("${it} ${setEntry.unitVelocity}") }
                    setEntry.targetDuration?.let { add("$it ${setEntry.unitDuration}") }
                    setEntry.targetDistance?.let { add("$it ${setEntry.unitDistance}") }
                    setEntry.targetRir?.let { add("RIR $it") }
                    setEntry.targetRest?.let { add("Rest $it ${setEntry.restUnit}") }
                }
                Text(
                    text = if (targetParts.isEmpty()) "—" else targetParts.joinToString(" · "),
                    style = monospaceLabel,
                    color = PureBlack.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (hasAnyActual) {
                Text(
                    text = "Actual",
                    style = MaterialTheme.typography.labelSmall,
                    color = PureBlack.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showLoad) {
                        WorkoutNumberStepperDouble(
                            value = currentLoad,
                            onValueChange = { onUpdateActuals(setEntry.id, it, null, null, null, null, null, null, null) },
                            min = 0.0,
                            max = 999.9,
                            step = 0.1,
                            enabled = isEditingEnabled,
                            label = "Load (${setEntry.unitLoad})",
                            textStyle = monospaceLabel
                        )
                    }
                    if (showReps) {
                        WorkoutNumberStepper(
                            value = currentReps,
                            onValueChange = { onUpdateActuals(setEntry.id, null, it, null, null, null, null, null, null) },
                            min = 0,
                            max = 999,
                            enabled = isEditingEnabled,
                            label = "Reps",
                            textStyle = monospaceLabel
                        )
                    }
                    if (showRpe) {
                        WorkoutNumberStepper(
                            value = currentRpe,
                            onValueChange = { onUpdateActuals(setEntry.id, null, null, null, it.toDouble(), null, null, null, null) },
                            min = 0,
                            max = 10,
                            enabled = isEditingEnabled,
                            label = "RPE (0–10)",
                            textStyle = monospaceLabel
                        )
                    }
                    if (showRir) {
                        WorkoutNumberStepper(
                            value = currentRir,
                            onValueChange = { onUpdateActuals(setEntry.id, null, null, null, null, it, null, null, null) },
                            min = 0,
                            max = 10,
                            enabled = isEditingEnabled,
                            label = "RIR (0–10)",
                            textStyle = monospaceLabel
                        )
                    }
                    if (showVelocity) {
                        WorkoutNumberStepper(
                            value = currentVelocity,
                            onValueChange = { onUpdateActuals(setEntry.id, null, null, it.toDouble(), null, null, null, null, null) },
                            min = 0,
                            max = 99,
                            enabled = isEditingEnabled,
                            label = "Velocity (${setEntry.unitVelocity})",
                            textStyle = monospaceLabel
                        )
                    }
                    if (showDuration) {
                        WorkoutNumberStepper(
                            value = currentDuration,
                            onValueChange = { valueInDisplayUnit ->
                                val actualDurationSec = if (durationUnitIsMin) valueInDisplayUnit * 60 else valueInDisplayUnit
                                onUpdateActuals(setEntry.id, null, null, null, null, null, actualDurationSec, null, null)
                            },
                            min = 0,
                            max = if (durationUnitIsMin) 9999 else 99999,
                            enabled = isEditingEnabled,
                            label = "Duration (${setEntry.unitDuration})",
                            textStyle = monospaceLabel
                        )
                    }
                    if (showDistance) {
                        WorkoutNumberStepperDouble(
                            value = currentDistance,
                            onValueChange = { onUpdateActuals(setEntry.id, null, null, null, null, null, null, it, null) },
                            min = 0.0,
                            max = 999.9,
                            step = 0.1,
                            enabled = isEditingEnabled,
                            label = "Distance (${setEntry.unitDistance})",
                            textStyle = monospaceLabel
                        )
                    }
                    if (showRest) {
                        WorkoutNumberStepper(
                            value = currentRest,
                            onValueChange = { onUpdateActuals(setEntry.id, null, null, null, null, null, null, null, it) },
                            min = 0,
                            max = 9999,
                            enabled = isEditingEnabled,
                            label = "Rest (${setEntry.restUnit})",
                            textStyle = monospaceLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpinBoxInt(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int = 0,
    max: Int = 999,
    enabled: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = { onValueChange((value - 1).coerceIn(min, max)) },
            enabled = enabled && value > min,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Decrease",
                tint = if (enabled && value > min) NavyDeep else PureBlack.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = value.toString(),
            style = textStyle,
            color = PureBlack,
            modifier = Modifier
                .widthIn(min = 28.dp)
                .padding(horizontal = 4.dp),
            textAlign = TextAlign.Center
        )
        IconButton(
            onClick = { onValueChange((value + 1).coerceIn(min, max)) },
            enabled = enabled && value < max,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Increase",
                tint = if (enabled && value < max) NavyDeep else PureBlack.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SpinBoxDouble(
    value: Double,
    onValueChange: (Double) -> Unit,
    min: Double = 0.0,
    max: Double = 999.0,
    step: Double = 0.5,
    enabled: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    val stepped = (value / step).toInt().toDouble() * step
    val displayValue = stepped.coerceIn(min, max)
    Row(
        modifier = modifier.padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = { onValueChange((displayValue - step).coerceIn(min, max)) },
            enabled = enabled && displayValue > min,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Decrease",
                tint = if (enabled && displayValue > min) NavyDeep else PureBlack.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = if (displayValue == displayValue.toLong().toDouble()) "${displayValue.toLong()}" else "%.1f".format(displayValue),
            style = textStyle,
            color = PureBlack,
            modifier = Modifier
                .widthIn(min = 32.dp)
                .padding(horizontal = 4.dp),
            textAlign = TextAlign.Center
        )
        IconButton(
            onClick = { onValueChange((displayValue + step).coerceIn(min, max)) },
            enabled = enabled && displayValue < max,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Increase",
                tint = if (enabled && displayValue < max) NavyDeep else PureBlack.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private const val WHEEL_ITEM_WIDTH_DP = 56

@Composable
private fun HorizontalWheelPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    enabled: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (value - range.first).coerceIn(0, (range.last - range.first).coerceAtLeast(0))
    )
    val listStateRef = remember { listState }
    val rangeList = remember(range) { (range.first..range.last).toList() }
    val horizontalPadding = 72.dp

    Box(modifier = modifier.height(48.dp)) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            userScrollEnabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = rangeList,
                key = { it }
            ) { item ->
                val isSelected = item == value
                Text(
                    text = "$item",
                    style = textStyle,
                    color = when {
                        !enabled -> PureBlack.copy(alpha = 0.38f)
                        isSelected -> NavyDeep
                        else -> PureBlack.copy(alpha = 0.5f + 0.2f * (1 - kotlin.math.abs(item - value) / 10f).coerceIn(0f, 1f))
                    },
                    modifier = Modifier
                        .width(WHEEL_ITEM_WIDTH_DP.dp)
                        .wrapContentWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
        LaunchedEffect(value) {
            val index = (value - range.first).coerceIn(0, (range.last - range.first).coerceAtLeast(0))
            listStateRef.animateScrollToItem(index)
        }
        LaunchedEffect(listStateRef, rangeList) {
            snapshotFlow {
                val idx = listStateRef.firstVisibleItemIndex
                rangeList.getOrNull(idx) ?: value
            }.collect { newVal ->
                if (newVal != value) onValueChange(newVal)
            }
        }
    }
}

@Composable
private fun AddWorkoutFromClipboardDialog(
    viewModel: CalendarViewModel,
    onDismiss: () -> Unit,
    onAdded: () -> Unit
) {
    var pastedText by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun validate() {
        if (pastedText.isBlank()) {
            validationMessage = "Paste JSON first"
            return
        }
        validationMessage = viewModel.validateWorkoutJson(pastedText).fold(
            onSuccess = { "Valid format — ready to add" },
            onFailure = { it.message ?: "Invalid JSON" }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add exercise from JSON", color = PureBlack)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        pastedText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        validationMessage = null
                        if (pastedText.isNotBlank()) validate()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NavyDeep.copy(alpha = 0.15f),
                        contentColor = NavyDeep
                    )
                ) {
                    Text("Fill from Clipboard")
                }
                OutlinedTextField(
                    value = pastedText,
                    onValueChange = { pastedText = it; validationMessage = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    placeholder = { Text("Paste JSON: exercise with \"name\", \"sets\", or session with \"title\", \"exercises\"", color = PureBlack.copy(alpha = 0.5f)) },
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NavyDeep,
                        unfocusedBorderColor = NavyDeep.copy(alpha = 0.6f),
                        focusedTextColor = PureBlack,
                        unfocusedTextColor = PureBlack
                    )
                )
                if (validationMessage != null) {
                    Text(
                        text = validationMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (validationMessage!!.startsWith("Valid")) NavyDeep else DeleteRed
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pastedText.isBlank()) return@TextButton
                    viewModel.addWorkoutFromClipboard(pastedText).onSuccess { onAdded() }.onFailure {
                        validationMessage = it.message ?: "Failed to add"
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = NavyDeep)
            ) {
                Text("Confirm add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PureBlack)
            }
        },
        containerColor = PureWhite
    )
}

@Composable
private fun TimeHeader(
    time: String,
    isExpanded: Boolean = true,
    onToggle: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(
                if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(NavyDeep)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.headlineMedium,
                color = PureBlack
            )
            if (onToggle != null) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = PureBlack,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(NavyDeep)
        )
    }
}

@Composable
private fun CollapsibleDietTimeSection(
    time: String,
    entries: List<DietEntry>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    isEditingEnabled: Boolean,
    viewModel: CalendarViewModel
) {
    TimeHeader(time = time, isExpanded = isExpanded, onToggle = onToggleExpanded)
    Spacer(modifier = Modifier.height(8.dp))
    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column {
            entries.forEach { entry ->
                DietEntryCard(
                    entry = entry,
                    isEditingEnabled = isEditingEnabled,
                    onMultiplierChange = { multiplier ->
                        viewModel.updateEntryMultiplier(entry.id, multiplier)
                    },
                    onEatenStateCycle = { viewModel.cycleEntryEatenState(entry.id) },
                    onEditClick = if (entry.status == AuditStatus.UNPLANNED) {
                        { viewModel.editUnplannedEntry(entry) }
                    } else null,
                    onDeleteClick = if (entry.status == AuditStatus.UNPLANNED) {
                        { viewModel.deleteEntry(entry.id) }
                    } else null
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private val DoneGreen = androidx.compose.ui.graphics.Color(0xFFE8F5E9)
private val NotEatenRedBg = androidx.compose.ui.graphics.Color(0xFFFFEBEE)

@Composable
private fun DietEntryCard(
    entry: DietEntry,
    isEditingEnabled: Boolean,
    onMultiplierChange: (Double) -> Unit,
    onEatenStateCycle: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
) {
    val targetAmount = entry.plannedAmount * entry.quantityMultiplier
    val formatter = DecimalFormat("#.##")
    val isNotEaten = entry.notEaten
    val isDone = entry.isChecked && !entry.notEaten
    val tileBackground = when {
        isNotEaten -> NotEatenRedBg
        isDone -> DoneGreen
        else -> PureWhite
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = tileBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Three-state check: blank → blue filled (done) → red filled (not eaten) → blank
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(
                                width = 2.dp,
                                color = when {
                                    isNotEaten -> DeleteRed
                                    isDone -> NavyDeep
                                    else -> PureBlack.copy(alpha = 0.5f)
                                },
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable(enabled = isEditingEnabled) { onEatenStateCycle() }
                    ) {
                        if (isDone || isNotEaten) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(2.dp)
                                    .background(
                                        color = if (isNotEaten) DeleteRed else NavyDeep,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isDone) PureBlack.copy(alpha = 0.6f) else PureBlack,
                            textDecoration = if (isDone) TextDecoration.LineThrough else null
                        )
                        if (!isNotEaten) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "P: ${formatter.format(entry.protein * entry.quantityMultiplier)}g",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = PureBlack.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "C: ${formatter.format(entry.carbs * entry.quantityMultiplier)}g",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = PureBlack.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "F: ${formatter.format(entry.fats * entry.quantityMultiplier)}g",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = PureBlack.copy(alpha = 0.7f)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "NOT EATEN",
                                style = MaterialTheme.typography.labelMedium,
                                color = DeleteRed
                            )
                        }
                    }
                }
                if (!isNotEaten) {
                    if (onEditClick != null && isEditingEnabled) {
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit meal",
                                tint = NavyDeep,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    StatusBadge(status = entry.status)
                }
            }

            if (!isNotEaten) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Target Amount:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureBlack
                    )
                    Text(
                        text = "${formatter.format(targetAmount)} ${entry.unit}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = PureBlack
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Multiplier:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PureBlack
                        )
                        Text(
                            text = formatter.format(entry.quantityMultiplier),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = PureBlack
                        )
                    }
                    @OptIn(ExperimentalMaterial3Api::class)
                    Slider(
                        value = entry.quantityMultiplier.toFloat(),
                        onValueChange = { onMultiplierChange(it.toDouble()) },
                        enabled = isEditingEnabled,
                        valueRange = 0f..3f,
                        steps = 29,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = NavyDeep,
                            activeTrackColor = NavyDeep,
                            inactiveTrackColor = OffWhite,
                            disabledThumbColor = PureBlack.copy(alpha = 0.38f),
                            disabledActiveTrackColor = PureBlack.copy(alpha = 0.38f),
                            disabledInactiveTrackColor = PureBlack.copy(alpha = 0.12f),
                        ),
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = NavyDeep,
                                    inactiveTrackColor = OffWhite,
                                ),
                                modifier = Modifier.height(4.dp),
                            )
                        },
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(NavyDeep, CircleShape)
                                    .border(2.dp, PureBlack, CircleShape)
                            )
                        },
                    )
                }
                Text(
                    text = "${formatter.format(entry.kcal * entry.quantityMultiplier)} kcal",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = PureBlack.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: AuditStatus) {
    val (text, color) = when (status) {
        AuditStatus.PLANNED -> "PLANNED" to NavyDeep
        AuditStatus.ADJUSTED -> "ADJUSTED" to ElectricBlue
        AuditStatus.UNPLANNED -> "UNPLANNED" to DeleteRed
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f),
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekScroller(
    selectedDate: String,
    datesWithCheckedMeals: Set<String>,
    isEditingEnabled: Boolean,
    showRefreshButton: Boolean,
    showAddButton: Boolean,
    onDateSelected: (String) -> Unit,
    onRefreshFromDriveClick: () -> Unit,
    onAddMealClick: () -> Unit,
    onEditClick: () -> Unit,
    isPastDay: () -> Boolean,
    isFutureDay: () -> Boolean
) {
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dayFormatter = SimpleDateFormat("EEE", Locale.US) // Day initials (Mon, Tue, etc.)
    val dateOnlyFormatter = SimpleDateFormat("dd", Locale.US) // Date number
    val displayFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    
    var showDatePicker by remember { mutableStateOf(false) }
    
    val selectedDateObj = try {
        dateFormatter.parse(selectedDate) ?: Date()
    } catch (e: Exception) {
        Date()
    }

    // Generate 7 days: 3 days before, selected day, 3 days after
    val weekDays = remember(selectedDate) {
        val calendar = Calendar.getInstance().apply { time = selectedDateObj }
        calendar.add(Calendar.DAY_OF_MONTH, -3)
        (0..6).map {
            val date = calendar.time
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            date
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // Week scroller at the top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            weekDays.forEach { date ->
                val dateStr = dateFormatter.format(date)
                val isSelected = dateStr == selectedDate
                val hasCheckedMeals = datesWithCheckedMeals.contains(dateStr)
                val dayInitial = dayFormatter.format(date).take(1).uppercase()
                val dateNumber = dateOnlyFormatter.format(date)
                
                Column(
                    modifier = Modifier
                        .width(56.dp)
                        .clickable { onDateSelected(dateStr) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Day initial
                    Text(
                        text = dayInitial,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) NavyDeep else PureBlack.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    // Date number with circle background if selected, and indicator if has checked meals
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) NavyDeep else PureWhite
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dateNumber,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) PureWhite else PureBlack,
                                textAlign = TextAlign.Center
                            )
                        }
                        // Indicator dot for days with checked meals (centered below number)
                        if (hasCheckedMeals && !isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(NavyDeep)
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Date display, calendar button, add meal button, and edit/lock button row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Calendar icon button for date picker
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
            
            // Current date display
            Text(
                text = displayFormatter.format(selectedDateObj),
                style = MaterialTheme.typography.titleLarge,
                color = PureBlack,
                modifier = Modifier.weight(1f)
            )
            
            if (showRefreshButton) {
                IconButton(
                    onClick = onRefreshFromDriveClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh from Drive",
                        tint = NavyDeep,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (showAddButton) {
                IconButton(
                    onClick = onAddMealClick,
                    modifier = Modifier
                        .size(48.dp)
                        .then(if (isEditingEnabled) Modifier else Modifier.alpha(0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add meal",
                        tint = NavyDeep,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (showRefreshButton || showAddButton) {
                if (isPastDay()) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .size(48.dp)
                            .then(
                                if (isEditingEnabled) Modifier.background(NavyDeep, CircleShape)
                                else Modifier
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = if (isEditingEnabled) "Freeze editing" else "Enable editing",
                            tint = if (isEditingEnabled) PureWhite else PureBlack.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            // Lock icon for future days
            if (isFutureDay()) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked - future day",
                    tint = PureBlack.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(24.dp)
                        .padding(12.dp)
                )
            }
        }
    }
    
    // Date picker dialog
    if (showDatePicker) {
        val initialPickerMillis = remember(selectedDate) {
            try {
                isoCalendarDateToUtcPickerMillis(selectedDate)
            } catch (_: Exception) {
                selectedDateObj.time
            }
        }
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
                        onDateSelected(utcPickerMillisToIsoCalendarDate(millis))
                        showDatePicker = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = NavyDeep
                    )
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
private fun AddMealDialog(
    entry: DietEntry? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double, String, Double, Double, Double, Double) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(entry?.name ?: "") }
    var time by remember { mutableStateOf(entry?.plannedTime ?: "12:00") }
    var amount by remember { mutableStateOf(entry?.plannedAmount?.toString() ?: "") }
    var unit by remember { mutableStateOf(entry?.unit ?: "g") }
    var kcal by remember { mutableStateOf(entry?.kcal?.toString() ?: "") }
    var protein by remember { mutableStateOf(entry?.protein?.toString() ?: "0") }
    var carbs by remember { mutableStateOf(entry?.carbs?.toString() ?: "0") }
    var fats by remember { mutableStateOf(entry?.fats?.toString() ?: "0") }
    var showJsonErrorDialog by remember { mutableStateOf(false) }
    var jsonErrorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Simple data class for parsing JSON (only fields we need)
    @kotlinx.serialization.Serializable
    data class MealJsonInput(
        val name: String = "",
        val plannedTime: String = "",
        val plannedAmount: Double = 0.0,
        val unit: String = "g",
        val kcal: Double = 0.0,
        val protein: Double = 0.0,
        val carbs: Double = 0.0,
        val fats: Double = 0.0
    )
    
    fun parseJsonFromClipboard() {
        try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipboardText = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (clipboardText.isBlank()) {
                jsonErrorMessage = "Clipboard is empty"
                showJsonErrorDialog = true
                return
            }
            
            // Parse JSON using a simple data class (ignores date, timestamp, id, isChecked, quantityMultiplier, status)
            val parsed = json.decodeFromString<MealJsonInput>(clipboardText.trim())
            
            // Validate required fields
            if (parsed.name.isBlank()) {
                jsonErrorMessage = "JSON missing required field: name"
                showJsonErrorDialog = true
                return
            }
            if (parsed.plannedTime.isBlank()) {
                jsonErrorMessage = "JSON missing required field: plannedTime"
                showJsonErrorDialog = true
                return
            }
            if (parsed.plannedAmount <= 0) {
                jsonErrorMessage = "JSON invalid: plannedAmount must be greater than 0"
                showJsonErrorDialog = true
                return
            }
            
            // Populate fields
            name = parsed.name
            time = parsed.plannedTime
            amount = parsed.plannedAmount.toString()
            unit = parsed.unit.ifBlank { "g" }
            kcal = parsed.kcal.toString()
            protein = parsed.protein.toString()
            carbs = parsed.carbs.toString()
            fats = parsed.fats.toString()
            showJsonErrorDialog = false
        } catch (e: kotlinx.serialization.SerializationException) {
            jsonErrorMessage = "Invalid JSON format: ${e.message ?: "Unable to parse JSON"}"
            showJsonErrorDialog = true
        } catch (e: Exception) {
            jsonErrorMessage = "Error parsing JSON: ${e.message ?: "Unknown error"}"
            showJsonErrorDialog = true
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (entry == null) "Add Meal" else "Edit Meal",
                color = PureBlack
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // JSON from clipboard option
                Button(
                    onClick = { parseJsonFromClipboard() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NavyDeep.copy(alpha = 0.1f),
                        contentColor = NavyDeep
                    )
                ) {
                    Text("Fill from Clipboard", color = NavyDeep)
                }
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Meal Name", color = PureBlack) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NavyDeep,
                        unfocusedBorderColor = NavyDeep,
                        focusedTextColor = PureBlack,
                        unfocusedTextColor = PureBlack,
                    )
                )
                
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Time (HH:mm)", color = PureBlack) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("12:00", color = PureBlack.copy(alpha = 0.6f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NavyDeep,
                        unfocusedBorderColor = NavyDeep,
                        focusedTextColor = PureBlack,
                        unfocusedTextColor = PureBlack,
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount", color = PureBlack) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyDeep,
                            unfocusedBorderColor = NavyDeep,
                            focusedTextColor = PureBlack,
                            unfocusedTextColor = PureBlack,
                        )
                    )
                    
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit", color = PureBlack) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("g", color = PureBlack.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyDeep,
                            unfocusedBorderColor = NavyDeep,
                            focusedTextColor = PureBlack,
                            unfocusedTextColor = PureBlack,
                        )
                    )
                }
                
                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it },
                    label = { Text("Calories (kcal)", color = PureBlack) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NavyDeep,
                        unfocusedBorderColor = NavyDeep,
                        focusedTextColor = PureBlack,
                        unfocusedTextColor = PureBlack,
                    )
                )
                
                // Macro fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text("Protein (g)", color = PureBlack) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyDeep,
                            unfocusedBorderColor = NavyDeep,
                            focusedTextColor = PureBlack,
                            unfocusedTextColor = PureBlack,
                        )
                    )
                    
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text("Carbs (g)", color = PureBlack) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyDeep,
                            unfocusedBorderColor = NavyDeep,
                            focusedTextColor = PureBlack,
                            unfocusedTextColor = PureBlack,
                        )
                    )
                    
                    OutlinedTextField(
                        value = fats,
                        onValueChange = { fats = it },
                        label = { Text("Fats (g)", color = PureBlack) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyDeep,
                            unfocusedBorderColor = NavyDeep,
                            focusedTextColor = PureBlack,
                            unfocusedTextColor = PureBlack,
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountValue = amount.toDoubleOrNull() ?: 0.0
                    val kcalValue = kcal.toDoubleOrNull() ?: 0.0
                    val proteinValue = protein.toDoubleOrNull() ?: 0.0
                    val carbsValue = carbs.toDoubleOrNull() ?: 0.0
                    val fatsValue = fats.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && time.isNotBlank() && amountValue > 0 && kcalValue >= 0) {
                        onConfirm(name, time, amountValue, unit, kcalValue, proteinValue, carbsValue, fatsValue)
                    }
                },
                enabled = name.isNotBlank() && time.isNotBlank() && 
                         amount.toDoubleOrNull() != null && amount.toDoubleOrNull()!! > 0 &&
                         kcal.toDoubleOrNull() != null && kcal.toDoubleOrNull()!! >= 0 &&
                         protein.toDoubleOrNull() != null && carbs.toDoubleOrNull() != null && fats.toDoubleOrNull() != null
            ) {
                Text("Save", color = NavyDeep)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = DeleteRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = DeleteRed)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = PureBlack)
                }
            }
        },
        containerColor = PureWhite
    )
    
    // Error dialog for JSON parsing
    if (showJsonErrorDialog) {
        AlertDialog(
            onDismissRequest = { showJsonErrorDialog = false },
            title = {
                Text("JSON Parse Error", color = PureBlack)
            },
            text = {
                Text(jsonErrorMessage, color = PureBlack)
            },
            confirmButton = {
                TextButton(onClick = { showJsonErrorDialog = false }) {
                    Text("OK", color = NavyDeep)
                }
            },
            containerColor = PureWhite
        )
    }
}

@Composable
private fun CategoryHeader(category: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(NavyDeep)
        )
        Text(
            text = category,
            style = MaterialTheme.typography.headlineMedium,
            color = PureBlack,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(NavyDeep)
        )
    }
}

@Composable
private fun getRoutineStatusColor(status: RoutineStatus): androidx.compose.ui.graphics.Color {
    return when (status) {
        RoutineStatus.DONE -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
        RoutineStatus.PARTIAL -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
        RoutineStatus.NOT_DONE -> androidx.compose.ui.graphics.Color(0xFFD32F2F) // Red
        RoutineStatus.NA -> androidx.compose.ui.graphics.Color(0xFF757575) // Gray
        RoutineStatus.EXCEEDED -> ElectricBlue // Blue
        RoutineStatus.NONE -> androidx.compose.ui.graphics.Color(0xFF757575) // Gray (Unreported)
    }
}

@Composable
private fun RoutineEntryCard(
    entry: RoutineEntry,
    isEditingEnabled: Boolean,
    onValueChange: (Double?) -> Unit,
    onStatusChange: (RoutineStatus) -> Unit,
    onToggleNA: () -> Unit,
) {
    val formatter = DecimalFormat("#.#")
    // Use NONE color if currentValue is null (unreported)
    val effectiveStatus = if (entry.type == RoutineType.NUMERICAL && entry.currentValue == null) {
        RoutineStatus.NONE
    } else {
        entry.status
    }
    val statusColor = getRoutineStatusColor(effectiveStatus)
    
    // For numerical: show slider at minValue if currentValue is null, but keep background Gray
    val sliderValue = if (entry.type == RoutineType.NUMERICAL) {
        (entry.currentValue ?: entry.minValue ?: 0.0).toFloat()
    } else {
        0f
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Title row with clickable status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PureBlack,
                    modifier = Modifier.weight(1f)
                )
                // Clickable status badge
                when (entry.type) {
                    RoutineType.CATEGORICAL -> {
                        StatusChipDropdown(
                            status = entry.status,
                            onStatusChange = onStatusChange,
                            isEditingEnabled = isEditingEnabled
                        )
                    }
                    RoutineType.NUMERICAL -> {
                        StatusChipClickable(
                            status = entry.status,
                            onClick = onToggleNA,
                            isEditingEnabled = isEditingEnabled
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            when (entry.type) {
                RoutineType.NUMERICAL -> {
                    // Goal and Current in horizontal row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Goal
                        Column {
                            Text(
                                text = "Goal",
                                style = MaterialTheme.typography.labelSmall,
                                color = PureBlack.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${formatter.format(entry.goalValue ?: 0.0)} ${entry.unit ?: ""}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = PureBlack
                            )
                        }
                        // Current
                        Column {
                            Text(
                                text = "Current",
                                style = MaterialTheme.typography.labelSmall,
                                color = PureBlack.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${formatter.format(entry.currentValue ?: entry.minValue ?: 0.0)} ${entry.unit ?: ""}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = PureBlack
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Slider (one decimal place: step 0.1, display and value rounded to 1 decimal)
                    @OptIn(ExperimentalMaterial3Api::class)
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            val rounded = (it * 10).toInt() / 10.0
                            onValueChange(rounded)
                        },
                        enabled = isEditingEnabled && entry.status != RoutineStatus.NA,
                        valueRange = (entry.minValue ?: 0.0).toFloat()..(entry.maxValue ?: 1.0).toFloat(),
                        steps = if (entry.maxValue != null && entry.minValue != null) {
                            (((entry.maxValue!! - entry.minValue!!) * 10).toInt() - 1).coerceAtLeast(0).coerceAtMost(999)
                        } else 0,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = NavyDeep,
                            activeTrackColor = NavyDeep,
                            inactiveTrackColor = OffWhite,
                            disabledThumbColor = PureBlack.copy(alpha = 0.38f),
                            disabledActiveTrackColor = PureBlack.copy(alpha = 0.38f),
                            disabledInactiveTrackColor = PureBlack.copy(alpha = 0.12f),
                        ),
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = NavyDeep,
                                    inactiveTrackColor = OffWhite,
                                ),
                                modifier = Modifier.height(4.dp),
                            )
                        },
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(NavyDeep, CircleShape)
                                    .border(2.dp, PureBlack, CircleShape)
                            )
                        },
                    )
                }
                RoutineType.CATEGORICAL -> {
                    // No additional UI needed - status is handled by clickable badge
                }
            }
        }
    }
}

@Composable
private fun StatusChipDropdown(
    status: RoutineStatus,
    onStatusChange: (RoutineStatus) -> Unit,
    isEditingEnabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = getRoutineStatusColor(status)
    
    Box {
        Button(
            onClick = { if (isEditingEnabled) expanded = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = statusColor.copy(alpha = 0.2f),
                contentColor = statusColor,
                disabledContainerColor = PureBlack.copy(alpha = 0.12f),
                disabledContentColor = PureBlack.copy(alpha = 0.38f)
            ),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            enabled = isEditingEnabled,
        ) {
            Text(
                text = status.name.replace("_", " "),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = PureWhite,
        ) {
            RoutineStatus.entries.forEach { s ->
                DropdownMenuItem(
                    text = { 
                        Text(
                            text = s.name.replace("_", " "),
                            color = PureBlack
                        ) 
                    },
                    onClick = {
                        onStatusChange(s)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusChipClickable(
    status: RoutineStatus,
    onClick: () -> Unit,
    isEditingEnabled: Boolean,
) {
    val statusColor = getRoutineStatusColor(status)
    
    Button(
        onClick = { if (isEditingEnabled) onClick() },
        colors = ButtonDefaults.buttonColors(
            containerColor = statusColor.copy(alpha = 0.2f),
            contentColor = statusColor,
            disabledContainerColor = PureBlack.copy(alpha = 0.12f),
            disabledContentColor = PureBlack.copy(alpha = 0.38f)
        ),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        enabled = isEditingEnabled,
    ) {
        Text(
            text = status.name.replace("_", " "),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun AddRoutineDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, RoutineType, Double?, Double?, Double?, Double?, String?, Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(RoutineType.NUMERICAL) }
    var goalValue by remember { mutableStateOf("") }
    var partialThreshold by remember { mutableStateOf("") }
    var minValue by remember { mutableStateOf("") }
    var maxValue by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var directionBetter by remember { mutableStateOf(1) } // 1 = Up, -1 = Down
    var showJsonErrorDialog by remember { mutableStateOf(false) }
    var jsonErrorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Simple data class for parsing JSON
    @kotlinx.serialization.Serializable
    data class RoutineJsonInput(
        val title: String = "",
        val category: String = "",
        val type: String = "NUMERICAL",
        val goalValue: Double? = null,
        val partialThreshold: Double? = null,
        val minValue: Double? = null,
        val maxValue: Double? = null,
        val unit: String? = null,
        val directionBetter: Int = 1
    )
    
    fun parseJsonFromClipboard() {
        try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipboardText = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (clipboardText.isBlank()) {
                jsonErrorMessage = "Clipboard is empty"
                showJsonErrorDialog = true
                return
            }
            
            // Parse JSON
            val parsed = json.decodeFromString<RoutineJsonInput>(clipboardText.trim())
            
            // Validate required fields
            if (parsed.title.isBlank()) {
                jsonErrorMessage = "JSON missing required field: title"
                showJsonErrorDialog = true
                return
            }
            if (parsed.category.isBlank()) {
                jsonErrorMessage = "JSON missing required field: category"
                showJsonErrorDialog = true
                return
            }
            
            // Populate fields
            title = parsed.title
            category = parsed.category
            selectedType = try {
                RoutineType.valueOf(parsed.type.uppercase())
            } catch (e: Exception) {
                RoutineType.NUMERICAL
            }
            goalValue = parsed.goalValue?.toString() ?: ""
            partialThreshold = parsed.partialThreshold?.toString() ?: ""
            minValue = parsed.minValue?.toString() ?: ""
            maxValue = parsed.maxValue?.toString() ?: ""
            unit = parsed.unit ?: ""
            directionBetter = parsed.directionBetter
            showJsonErrorDialog = false
        } catch (e: kotlinx.serialization.SerializationException) {
            jsonErrorMessage = "Invalid JSON format: ${e.message ?: "Unable to parse JSON"}"
            showJsonErrorDialog = true
        } catch (e: Exception) {
            jsonErrorMessage = "Error parsing JSON: ${e.message ?: "Unknown error"}"
            showJsonErrorDialog = true
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Activity",
                color = PureBlack
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // JSON from clipboard option
                Button(
                    onClick = { parseJsonFromClipboard() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NavyDeep.copy(alpha = 0.1f),
                        contentColor = NavyDeep
                    )
                ) {
                    Text("Fill from Clipboard", color = NavyDeep)
                }
                
                // Activity Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Activity Title", color = PureBlack) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NavyDeep,
                        unfocusedBorderColor = NavyDeep,
                        focusedTextColor = PureBlack,
                        unfocusedTextColor = PureBlack,
                    )
                )
                
                // Category
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category", color = PureBlack) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NavyDeep,
                        unfocusedBorderColor = NavyDeep,
                        focusedTextColor = PureBlack,
                        unfocusedTextColor = PureBlack,
                    )
                )
                
                // Type selection (Numerical vs Categorical)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { selectedType = RoutineType.NUMERICAL },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedType == RoutineType.NUMERICAL) NavyDeep else PureWhite,
                            contentColor = if (selectedType == RoutineType.NUMERICAL) PureWhite else NavyDeep
                        ),
                        border = if (selectedType == RoutineType.NUMERICAL) null else BorderStroke(1.dp, NavyDeep)
                    ) {
                        Text("Numerical")
                    }
                    Button(
                        onClick = { selectedType = RoutineType.CATEGORICAL },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedType == RoutineType.CATEGORICAL) NavyDeep else PureWhite,
                            contentColor = if (selectedType == RoutineType.CATEGORICAL) PureWhite else NavyDeep
                        ),
                        border = if (selectedType == RoutineType.CATEGORICAL) null else BorderStroke(1.dp, NavyDeep)
                    ) {
                        Text("Categorical")
                    }
                }
                
                // Numerical fields (only show if Numerical is selected)
                if (selectedType == RoutineType.NUMERICAL) {
                    OutlinedTextField(
                        value = goalValue,
                        onValueChange = { goalValue = it },
                        label = { Text("Goal Value", color = PureBlack) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyDeep,
                            unfocusedBorderColor = NavyDeep,
                            focusedTextColor = PureBlack,
                            unfocusedTextColor = PureBlack,
                        )
                    )
                    
                    OutlinedTextField(
                        value = partialThreshold,
                        onValueChange = { partialThreshold = it },
                        label = { Text("Partial Threshold", color = PureBlack) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyDeep,
                            unfocusedBorderColor = NavyDeep,
                            focusedTextColor = PureBlack,
                            unfocusedTextColor = PureBlack,
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = minValue,
                            onValueChange = { minValue = it },
                            label = { Text("Min Value", color = PureBlack) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NavyDeep,
                                unfocusedBorderColor = NavyDeep,
                                focusedTextColor = PureBlack,
                                unfocusedTextColor = PureBlack,
                            )
                        )
                        
                        OutlinedTextField(
                            value = maxValue,
                            onValueChange = { maxValue = it },
                            label = { Text("Max Value", color = PureBlack) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NavyDeep,
                                unfocusedBorderColor = NavyDeep,
                                focusedTextColor = PureBlack,
                                unfocusedTextColor = PureBlack,
                            )
                        )
                    }
                    
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit", color = PureBlack) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("h, cups, steps, etc.", color = PureBlack.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyDeep,
                            unfocusedBorderColor = NavyDeep,
                            focusedTextColor = PureBlack,
                            unfocusedTextColor = PureBlack,
                        )
                    )
                    
                    // Direction switch (Up = 1, Down = -1)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Direction:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PureBlack,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Button(
                            onClick = { directionBetter = 1 },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (directionBetter == 1) NavyDeep else PureWhite,
                                contentColor = if (directionBetter == 1) PureWhite else NavyDeep
                            ),
                            border = if (directionBetter == 1) null else BorderStroke(1.dp, NavyDeep)
                        ) {
                            Text("Up")
                        }
                        Button(
                            onClick = { directionBetter = -1 },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (directionBetter == -1) NavyDeep else PureWhite,
                                contentColor = if (directionBetter == -1) PureWhite else NavyDeep
                            ),
                            border = if (directionBetter == -1) null else BorderStroke(1.dp, NavyDeep)
                        ) {
                            Text("Down")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank() && category.isNotBlank()) {
                        val goal = goalValue.toDoubleOrNull()
                        val partial = partialThreshold.toDoubleOrNull()
                        val min = minValue.toDoubleOrNull()
                        val max = maxValue.toDoubleOrNull()
                        val unitValue = unit.ifBlank { null }
                        
                        // Validate numerical fields if Numerical type
                        if (selectedType == RoutineType.NUMERICAL) {
                            if (goal == null || min == null || max == null) {
                                return@TextButton
                            }
                        }
                        
                        onConfirm(
                            title,
                            category,
                            selectedType,
                            goal,
                            partial,
                            min,
                            max,
                            unitValue,
                            directionBetter
                        )
                    }
                },
                enabled = title.isNotBlank() && category.isNotBlank() &&
                         (selectedType == RoutineType.CATEGORICAL || 
                          (goalValue.toDoubleOrNull() != null && 
                           minValue.toDoubleOrNull() != null && 
                           maxValue.toDoubleOrNull() != null))
            ) {
                Text("Save", color = NavyDeep)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PureBlack)
            }
        },
        containerColor = PureWhite
    )
    
    // Error dialog for JSON parsing
    if (showJsonErrorDialog) {
        AlertDialog(
            onDismissRequest = { showJsonErrorDialog = false },
            title = {
                Text("JSON Parse Error", color = PureBlack)
            },
            text = {
                Text(jsonErrorMessage, color = PureBlack)
            },
            confirmButton = {
                TextButton(onClick = { showJsonErrorDialog = false }) {
                    Text("OK", color = NavyDeep)
                }
            },
            containerColor = PureWhite
        )
    }
}
