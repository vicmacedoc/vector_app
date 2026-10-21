@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.vm.vector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import android.content.ClipData
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
import androidx.compose.ui.unit.sp
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
import com.vm.core.models.FoodEntry
import com.vm.core.models.SelfCollection
import com.vm.core.models.SelfCollectionImage
import com.vm.core.models.SelfScale
import com.vm.core.models.WorkoutSet
import com.vm.core.models.WorkoutSetType
import com.vm.core.models.durationToMinutes
import com.vm.core.models.isDropRow
import com.vm.core.models.isInterval
import com.vm.vector.data.analysis.ExerciseDayMath
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.DropSetOrange
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.MoodColor1
import com.vm.core.ui.theme.MoodPalette
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.moodColorForLevel
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.core.ui.theme.VectorTheme
import com.vm.vector.data.analysis.AnalysisStats
import com.vm.vector.ui.screens.routine.ActivitiesSection
import com.vm.vector.ui.screens.routine.BehaviorDonutSection
import com.vm.vector.ui.screens.routine.SleepSection
import com.vm.vector.ui.screens.routine.sleepArcsForCalendarDay
import com.vm.vector.VectorApplication
import com.vm.vector.audio.DailyPlanAudioHelper
import com.vm.vector.data.SelfPhotoSummary
import com.vm.vector.data.toImportDraft
import com.vm.vector.ui.screens.exercise.ExerciseDaySummarySection
import com.vm.vector.data.CalendarAiMode
import com.vm.vector.data.MealImportDraft
import com.vm.vector.data.parseMealClipboardText
import com.vm.vector.ui.screens.calendar.CalendarAddFlowHost
import com.vm.vector.ui.screens.calendar.CalendarPullToRefresh
import com.vm.vector.ui.screens.calendar.DiscardChangesDialog
import com.vm.vector.ui.screens.calendar.FillMealFromAiDialog
import com.vm.vector.ui.screens.calendar.readClipboardText
import androidx.compose.material3.FloatingActionButton
import com.vm.vector.ui.util.isoCalendarDateToUtcPickerMillis
import com.vm.vector.ui.components.DialogMultilineFieldDefaults
import com.vm.vector.ui.components.KeyboardAwareDialogColumn
import com.vm.vector.ui.components.WorkoutNumberStepper
import com.vm.vector.ui.components.WorkoutNumberStepperDouble
import com.vm.vector.ui.util.utcPickerMillisToIsoCalendarDate
import com.vm.vector.ui.viewmodel.CalendarUiState
import com.vm.vector.ui.viewmodel.CalendarViewModel
import com.vm.vector.ui.viewmodel.CalendarViewModelFactory
import com.vm.vector.util.applyExifOrientationToJpegBytes
import androidx.compose.material.icons.filled.AutoAwesome
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

enum class CalendarCategory(val displayName: String) {
    Routine("Routine"),
    Food("Food"),
    Exercise("Exercise"),
    Self("Self"),
}

private val calendarClipboardJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

@Serializable
private data class FoodMealClipboardJson(
    val name: String = "",
    val time: String? = null,
    val amount: Double? = null,
    val plannedTime: String? = null,
    val plannedAmount: Double? = null,
    val unit: String = "g",
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
)

private fun copyPlainTextToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun foodEntryToClipboardJson(entry: FoodEntry): String =
    calendarClipboardJson.encodeToString(
        FoodMealClipboardJson(
            name = entry.name,
            time = entry.effectiveTime(),
            amount = entry.effectiveAmount(),
            plannedTime = entry.plannedTime,
            plannedAmount = entry.plannedAmount,
            unit = entry.unit,
            kcal = entry.kcal,
            protein = entry.protein,
            carbs = entry.carbs,
            fats = entry.fats,
        )
    )

private sealed interface CalendarTileActionSheet {
    data class Food(val entry: FoodEntry) : CalendarTileActionSheet
    data class Workout(val sessionSets: List<WorkoutSet>, val displayTitle: String) : CalendarTileActionSheet
}

@Composable
private fun TileActionsDialog(
    target: CalendarTileActionSheet,
    isEditingEnabled: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
) {
    val (title, subtitle) = when (target) {
        is CalendarTileActionSheet.Food -> "Meal" to target.entry.name
        is CalendarTileActionSheet.Workout -> "Workout session" to target.displayTitle
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = PureWhite,
            tonalElevation = 2.dp
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = PureBlack
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = PureBlack
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PureBlack
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Removing marks this day as having unsaved changes. Tap the Save icon to keep it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PureBlack.copy(alpha = 0.65f)
                )
                if (!isEditingEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Unlock editing (lock icon) to remove this tile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DeleteRed
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    OutlinedButton(
                        onClick = {
                            onCopy()
                            onDismiss()
                        }
                    ) {
                        Text("Copy", color = NavyDeep)
                    }
                    Button(
                        onClick = {
                            onRemove()
                            onDismiss()
                        },
                        enabled = isEditingEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeleteRed,
                            contentColor = PureWhite,
                            disabledContainerColor = PureBlack.copy(alpha = 0.12f),
                            disabledContentColor = PureBlack.copy(alpha = 0.38f),
                        )
                    ) {
                        Text("Remove")
                    }
                }
            }
        }
    }
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
        "Exercise", "Workout" -> CalendarCategory.Exercise
        "Food", "Diet" -> CalendarCategory.Food
        "Self", "Diary" -> CalendarCategory.Self
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

    var showAddFlow by remember { mutableStateOf(false) }
    val addFlowAvailable = uiState.isEditingEnabled && selectedCategory != CalendarCategory.Self
    LaunchedEffect(addFlowAvailable) {
        if (!addFlowAvailable) showAddFlow = false
    }

    VectorTheme {
        Scaffold(
            containerColor = PureWhite,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            floatingActionButton = {
                if (addFlowAvailable) {
                    FloatingActionButton(
                        onClick = { showAddFlow = true },
                        containerColor = NavyDeep,
                        contentColor = PureWhite,
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Add or plan ${selectedCategory.displayName}")
                    }
                }
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
                var pendingDiscardCategory by remember { mutableStateOf<CalendarCategory?>(null) }
                var tileActionSheet by remember { mutableStateOf<CalendarTileActionSheet?>(null) }

                LaunchedEffect(uiState.currentDate, selectedCategory) {
                    tileActionSheet = null
                }

                CategorySwitch(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                )

                WeekScroller(
                    selectedDate = uiState.currentDate,
                    markedDates = if (selectedCategory == CalendarCategory.Self) {
                        uiState.datesWithSelfEntries
                    } else {
                        uiState.datesWithCheckedMeals
                    },
                    isEditingEnabled = uiState.isEditingEnabled,
                    hasUnsavedChanges = selectedCategory.displayName in uiState.dirtyCategories,
                    isSaving = when (selectedCategory) {
                        CalendarCategory.Routine -> uiState.isRoutineSaving
                        CalendarCategory.Food -> uiState.isSaving
                        CalendarCategory.Exercise -> uiState.isWorkoutSaving
                        CalendarCategory.Self -> uiState.isSelfSaving
                    },
                    onDateSelected = { date ->
                        viewModel.selectDate(date)
                    },
                    onToggleLock = {
                        viewModel.toggleEditingEnabled()
                    },
                    onSave = {
                        when (selectedCategory) {
                            CalendarCategory.Routine -> viewModel.saveRoutineDay()
                            CalendarCategory.Food -> viewModel.saveFoodDay()
                            CalendarCategory.Exercise -> viewModel.saveWorkout()
                            CalendarCategory.Self -> viewModel.saveSelf()
                        }
                    },
                )

                val requestRefresh: (CalendarCategory) -> Unit = { category ->
                    if (category.displayName in uiState.dirtyCategories) {
                        pendingDiscardCategory = category
                    } else {
                        viewModel.discardAndReload(category.displayName)
                    }
                }
                pendingDiscardCategory?.let { category ->
                    DiscardChangesDialog(
                        categoryName = category.displayName,
                        onConfirm = {
                            viewModel.discardAndReload(category.displayName)
                            pendingDiscardCategory = null
                        },
                        onDismiss = { pendingDiscardCategory = null },
                    )
                }

                if (showAddFlow) {
                    CalendarAddFlowHost(
                        category = selectedCategory,
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { showAddFlow = false },
                    )
                }

                tileActionSheet?.let { sheet ->
                    TileActionsDialog(
                        target = sheet,
                        isEditingEnabled = uiState.isEditingEnabled,
                        onDismiss = { tileActionSheet = null },
                        onCopy = {
                            when (sheet) {
                                is CalendarTileActionSheet.Food ->
                                    copyPlainTextToClipboard(context, "Meal", foodEntryToClipboardJson(sheet.entry))
                                is CalendarTileActionSheet.Workout ->
                                    copyPlainTextToClipboard(
                                        context,
                                        "Workout session",
                                        viewModel.encodeWorkoutSessionForClipboard(sheet.sessionSets)
                                    )
                            }
                            viewModel.notifyClipboardCopied()
                        },
                        onRemove = {
                            when (sheet) {
                                is CalendarTileActionSheet.Food -> viewModel.deleteEntry(sheet.entry.id)
                                is CalendarTileActionSheet.Workout -> viewModel.removeWorkoutSession(sheet.sessionSets)
                            }
                            viewModel.notifyTileRemovedFromDay()
                        },
                    )
                }

                if (selectedCategory == CalendarCategory.Food) {
                    var foodTimeEditTarget by remember { mutableStateOf<FoodEntry?>(null) }
                    val groupedEntries = uiState.entries.groupBy { it.effectiveTime() }
                        .toSortedMap()

                    CalendarPullToRefresh(
                        isRefreshing = uiState.isLoading,
                        onRefresh = { requestRefresh(CalendarCategory.Food) },
                        modifier = Modifier.weight(1f),
                    ) {
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
                                if (groupedEntries.isEmpty() && uiState.noPresetForFood) {
                                    NoPresetPlaceholder(
                                        categoryName = "Food",
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
                                        FoodDayMacrosSummary(entries = uiState.entries)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        groupedEntries.forEach { (time, entries) ->
                                            val isExpanded = dietExpandedByTime.getOrElse(time) { false }
                                            CollapsibleFoodTimeSection(
                                                time = time,
                                                entries = entries,
                                                isExpanded = isExpanded,
                                                onToggleExpanded = {
                                                    dietExpandedByTime[time] = !isExpanded
                                                },
                                                isEditingEnabled = uiState.isEditingEnabled,
                                                viewModel = viewModel,
                                                onEditEntryTime = { foodTimeEditTarget = it },
                                                onOpenFoodTileActions = { entry ->
                                                    tileActionSheet = CalendarTileActionSheet.Food(entry)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(88.dp))
                        }
                        foodTimeEditTarget?.let { target ->
                            FoodPlannedTimePickerDialog(
                                initialMinutes = foodPlannedTimeToMinutes(target.effectiveTime()),
                                onDismiss = { foodTimeEditTarget = null },
                                onConfirm = { minutes ->
                                    viewModel.updateEntryPlannedTime(
                                        target.id,
                                        dietMinutesToPlannedTime(minutes)
                                    )
                                    foodTimeEditTarget = null
                                }
                            )
                        }
                    }
                } else if (selectedCategory == CalendarCategory.Routine) {
                    CalendarPullToRefresh(
                        isRefreshing = false,
                        onRefresh = { requestRefresh(CalendarCategory.Routine) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                        ) {
                            val plannedArcs = sleepArcsForCalendarDay(
                                previousNight = uiState.previousNight,
                                tonight = uiState.tonight,
                                defaultBedMinutes = uiState.defaultBedMinutes,
                                defaultWakeMinutes = uiState.defaultWakeMinutes,
                                useActual = false,
                            )
                            val doneArcs = sleepArcsForCalendarDay(
                                previousNight = uiState.previousNight,
                                tonight = uiState.tonight,
                                defaultBedMinutes = uiState.defaultBedMinutes,
                                defaultWakeMinutes = uiState.defaultWakeMinutes,
                                useActual = true,
                            )
                            SleepSection(
                                enabled = uiState.isEditingEnabled,
                                previousNight = uiState.previousNight,
                                tonight = uiState.tonight,
                                defaultBedMinutes = uiState.defaultBedMinutes,
                                defaultWakeMinutes = uiState.defaultWakeMinutes,
                                onSetPlannedBedtime = viewModel::setPlannedBedtime,
                                onSetPlannedWakeUp = viewModel::setPlannedWakeUp,
                                onSetActualBedtime = viewModel::setActualBedtime,
                                onSetActualWakeUp = viewModel::setActualWakeUp,
                                onSetFellAsleep = viewModel::setFellAsleep,
                                onSetLatency = viewModel::setLatency,
                                onSetStageMinutes = viewModel::setStageMinutes,
                                onSetStagePercent = viewModel::setStagePercent,
                                onApplyAi = viewModel::applySleepAiEstimate,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            BehaviorDonutSection(
                                activities = uiState.activityEntries,
                                labels = uiState.activityLabels,
                                plannedSleepArcs = plannedArcs,
                                doneSleepArcs = doneArcs,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            ActivitiesSection(
                                activities = uiState.activityEntries,
                                labels = uiState.activityLabels,
                                isEditingEnabled = uiState.isEditingEnabled,
                                onUpsert = viewModel::upsertActivity,
                                onDelete = viewModel::deleteActivity,
                                onCompletion = viewModel::updateActivityCompletion,
                                onToggleType = viewModel::toggleActivityType,
                            )
                            Spacer(modifier = Modifier.height(88.dp))
                        }
                    }
                } else if (selectedCategory == CalendarCategory.Exercise) {
                    var exerciseModalExerciseId by remember { mutableStateOf<String?>(null) }
                    val modalSets = remember(exerciseModalExerciseId, uiState.workoutSets) {
                        exerciseModalExerciseId?.let { id ->
                            uiState.workoutSets.filter { it.exerciseId == id }
                        }.orEmpty()
                    }
                    CalendarPullToRefresh(
                        isRefreshing = false,
                        onRefresh = { requestRefresh(CalendarCategory.Exercise) },
                        modifier = Modifier.weight(1f),
                    ) {
                        WorkoutContent(
                            sets = uiState.workoutSets,
                            noPreset = uiState.noPresetForWorkout,
                            onLoadPreset = onNavigateToSettings,
                            isEditingEnabled = uiState.isEditingEnabled,
                            onUpdateExerciseStatus = viewModel::updateWorkoutExerciseStatus,
                            onExerciseClick = { exerciseModalExerciseId = it.first().exerciseId },
                            onLongPressWorkoutSessionActions = { sessionSets, displayTitle ->
                                tileActionSheet = CalendarTileActionSheet.Workout(sessionSets, displayTitle)
                            },
                        )
                    }
                    if (modalSets.isNotEmpty()) {
                        WorkoutExerciseDetailModal(
                            setsInExercise = modalSets,
                            onDismiss = { exerciseModalExerciseId = null },
                            isEditingEnabled = uiState.isEditingEnabled,
                            onUpdateSet = viewModel::updateWorkoutSetInMemory
                        )
                    }
                } else if (selectedCategory == CalendarCategory.Self) {
                    var selfUnlocked by remember { mutableStateOf(false) }
                    LaunchedEffect(selectedCategory) {
                        if (selectedCategory != CalendarCategory.Self) selfUnlocked = false
                    }
                    val keyguardManager = remember(context) {
                        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    }
                    val credentialIntent = remember(keyguardManager) {
                        keyguardManager?.createConfirmDeviceCredentialIntent(
                            "Unlock Self",
                            "Use your device PIN, pattern or password to view your journal."
                        )
                    }
                    val credentialLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == Activity.RESULT_OK) selfUnlocked = true
                    }
                    if (!selfUnlocked && credentialIntent != null) {
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
                                    "Self is protected",
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
                    } else if (selfUnlocked || credentialIntent == null) {
                        if (credentialIntent == null) {
                            selfUnlocked = true
                        }
                        CalendarPullToRefresh(
                            isRefreshing = uiState.isSelfRefreshing,
                            onRefresh = { requestRefresh(CalendarCategory.Self) },
                            modifier = Modifier.weight(1f),
                        ) {
                            SelfContent(
                                modifier = Modifier.fillMaxSize(),
                                viewModel = viewModel,
                                uiState = uiState,
                                isEditingEnabled = uiState.isEditingEnabled,
                                isSelfRefreshing = uiState.isSelfRefreshing,
                            )
                        }
                    }
                }

                val editingEntry by viewModel.editingEntry.collectAsState()
                if (editingEntry != null) {
                    AddMealDialog(
                        entry = editingEntry,
                        onDismiss = { viewModel.clearEditingEntry() },
                        onConfirm = { name, time, amount, unit, kcal, protein, carbs, fats, asPlanned ->
                            viewModel.updateManualEntry(
                                editingEntry!!.id, name, time, amount, unit, kcal, protein, carbs, fats, asPlanned,
                            )
                            viewModel.clearEditingEntry()
                        },
                        onDelete = {
                            viewModel.deleteEntry(editingEntry!!.id)
                            viewModel.clearEditingEntry()
                        }
                    )
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
                    when (category) {
                        CalendarCategory.Routine -> Text(text = "⏰", fontSize = 22.sp)
                        CalendarCategory.Food -> Text(text = "🍽️", fontSize = 22.sp)
                        CalendarCategory.Exercise -> Text(text = "🏋️", fontSize = 22.sp)
                        CalendarCategory.Self -> Text(text = "🧘", fontSize = 22.sp)
                    }
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
private fun SelfSectionTitle(title: String) {
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
private fun SelfContent(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel,
    uiState: CalendarUiState,
    isEditingEnabled: Boolean,
    isSelfRefreshing: Boolean,
) {
    val scrollState = rememberScrollState()
    val inputs = uiState.selfInputs

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        SelfSectionTitle("HOW I FELT")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            (1..5).forEach { level ->
                val selected = inputs.level(SelfScale.MOOD) == level
                val color = moodColorForLevel(level)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (selected) color else color.copy(alpha = 0.3f))
                        .then(if (selected) Modifier.border(2.dp, NavyDeep, CircleShape) else Modifier)
                        .clickable(enabled = isEditingEnabled) {
                            viewModel.setSelfScale(SelfScale.MOOD, level)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = MOOD_EMOJIS[level - 1], style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = SelfScale.MOOD.lowLabel,
                style = MaterialTheme.typography.labelSmall,
                color = SlateGray
            )
            Text(
                text = SelfScale.MOOD.highLabel,
                style = MaterialTheme.typography.labelSmall,
                color = SlateGray
            )
        }
        SelfScale.secondary.forEach { scale ->
            Spacer(modifier = Modifier.height(12.dp))
            SelfLevelScale(
                scale = scale,
                value = inputs.level(scale),
                isEditingEnabled = isEditingEnabled,
                onSelect = { level -> viewModel.setSelfScale(scale, level) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        SelfSectionTitle("INTENTION")
        OutlinedTextField(
            value = inputs.intention,
            onValueChange = viewModel::setSelfIntention,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("What mattered most today?", color = SlateGray) },
            enabled = isEditingEnabled,
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        SelfSectionTitle("JOURNAL")
        JournalAudioRow(
            viewModel = viewModel,
            currentDate = uiState.currentDate,
            selfAudioPath = uiState.selfAudioPath,
            selfAudioDriveFileId = uiState.selfAudioDriveFileId,
            isEditingEnabled = isEditingEnabled
        )
        SelfPromptField(
            value = inputs.highlight,
            onValueChange = viewModel::setSelfHighlight,
            placeholder = "What fuelled me",
            isEditingEnabled = isEditingEnabled
        )
        SelfPromptField(
            value = inputs.lowlight,
            onValueChange = viewModel::setSelfLowlight,
            placeholder = "What drained me",
            isEditingEnabled = isEditingEnabled
        )
        SelfPromptField(
            value = inputs.gratitude,
            onValueChange = viewModel::setSelfGratitude,
            placeholder = "Grateful for",
            isEditingEnabled = isEditingEnabled
        )
        OutlinedTextField(
            value = inputs.journalText,
            onValueChange = viewModel::setSelfJournalText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .heightIn(
                    min = DialogMultilineFieldDefaults.LargeMin,
                    max = DialogMultilineFieldDefaults.LargeMax,
                ),
            placeholder = { Text("How was your day?", color = SlateGray) },
            enabled = isEditingEnabled,
            maxLines = 6
        )
        Spacer(modifier = Modifier.height(16.dp))

        SelfSectionTitle("COLLECTIONS")
        SelfCollectionsSection(
            viewModel = viewModel,
            collectionsWithImages = uiState.selfCollectionsWithImages,
            isSelfRefreshing = isSelfRefreshing,
            isCreatingCollection = uiState.isCreatingCollection,
            isEditingEnabled = isEditingEnabled
        )
    }
}

/**
 * 1–5 tap scale with the poles named, so the number keeps the same meaning across days.
 * Circles carry the mood ramp, which lines up because every scale runs worst to best.
 */
@Composable
private fun SelfLevelScale(
    scale: SelfScale,
    value: Int?,
    isEditingEnabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = scale.label,
            style = MaterialTheme.typography.bodyMedium,
            color = PureBlack
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            (1..5).forEach { level ->
                val selected = value == level
                val color = moodColorForLevel(level)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (selected) color else color.copy(alpha = 0.3f))
                        .then(
                            if (selected) Modifier.border(2.dp, NavyDeep, CircleShape) else Modifier
                        )
                        .clickable(enabled = isEditingEnabled) { onSelect(level) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = level.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureBlack
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = scale.lowLabel,
                style = MaterialTheme.typography.labelSmall,
                color = SlateGray
            )
            Text(
                text = scale.highLabel,
                style = MaterialTheme.typography.labelSmall,
                color = SlateGray
            )
        }
    }
}

@Composable
private fun SelfPromptField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isEditingEnabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        placeholder = { Text(placeholder, color = SlateGray) },
        enabled = isEditingEnabled,
        maxLines = 2
    )
}


@Composable
private fun JournalAudioRow(
    viewModel: CalendarViewModel,
    currentDate: String,
    selfAudioPath: String?,
    selfAudioDriveFileId: String?,
    isEditingEnabled: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioHelper = remember { DailyPlanAudioHelper(context) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    val hasLocalAudio = !selfAudioPath.isNullOrBlank() && audioHelper.fileExists(selfAudioPath)
    val hasDriveAudio = !selfAudioDriveFileId.isNullOrBlank()
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
                    viewModel.setSelfAudioPath(path)
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
                                hasLocalAudio -> selfAudioPath
                                else -> null
                            }
                            if (pathToPlay != null) {
                                audioHelper.startPlayback(
                                    pathToPlay,
                                    onCompletion = { isPlaying = false; isPaused = false },
                                    onError = { }
                                )
                                isPlaying = true
                            } else if (selfAudioDriveFileId != null) {
                                scope.launch {
                                    val bytes = viewModel.getSelfAudioBytes()
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
                        if (hasLocalAudio && selfAudioPath != null) {
                            audioHelper.deleteFile(selfAudioPath)
                        }
                        viewModel.setSelfAudioPath(null)
                        viewModel.setSelfAudioDriveFileId(null)
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

private fun formatDiaryPhotoRange(summary: SelfPhotoSummary): String {
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return if (summary.rangeStartMillis == summary.rangeEndMillis) {
        fmt.format(Date(summary.rangeStartMillis))
    } else {
        "${fmt.format(Date(summary.rangeStartMillis))} – ${fmt.format(Date(summary.rangeEndMillis))}"
    }
}

private fun diaryAlbumSummary(images: List<SelfCollectionImage>): SelfPhotoSummary? {
    if (images.isEmpty()) return null
    val times = images.map { it.takenAtMillis }
    val latest = images.sortedByDescending { it.takenAtMillis }.take(4).map { it.driveFileId }
    return SelfPhotoSummary(
        totalCount = images.size,
        rangeStartMillis = times.minOrNull()!!,
        rangeEndMillis = times.maxOrNull()!!,
        latestPreviewDriveFileIds = latest
    )
}

@Composable
private fun SelfCollectionsSection(
    viewModel: CalendarViewModel,
    collectionsWithImages: List<Pair<SelfCollection, List<SelfCollectionImage>>>,
    isSelfRefreshing: Boolean,
    isCreatingCollection: Boolean,
    isEditingEnabled: Boolean
) {
    val context = LocalContext.current
    var showNewCollectionDialog by remember { mutableStateOf(false) }
    var showTimelapseConfig by remember { mutableStateOf<List<SelfCollectionImage>?>(null) }
    var showImageViewer by remember { mutableStateOf<SelfCollectionImage?>(null) }
    var showImageGallery by remember { mutableStateOf<List<SelfCollectionImage>?>(null) }
    var showDeleteCollectionConfirm by remember { mutableStateOf<SelfCollection?>(null) }
    var showDeleteImageConfirm by remember { mutableStateOf<SelfCollectionImage?>(null) }
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
                            viewModel.notifySelfError("Failed to read captured photo.")
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
                        viewModel.notifySelfError("Failed to upload photo: ${e.message ?: "Unknown error"}")
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
            if (isSelfRefreshing) {
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
            SelfAlbumCard(
                collection = collection,
                images = images,
                viewModel = viewModel,
                isEditingEnabled = isEditingEnabled,
                onAddPhoto = { startCameraForCollection(collection.id) },
                onTimelapse = {
                    if (images.isNotEmpty()) showTimelapseConfig = images.sortedBy { it.takenAtMillis }
                },
                onRemoveAlbum = {
                    if (!collection.isSystem && !SelfCollection.isBodyScanAngleName(collection.name)) {
                        showDeleteCollectionConfirm = collection
                    }
                },
                canRemoveAlbum = !collection.isSystem && !SelfCollection.isBodyScanAngleName(collection.name),
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
                    viewModel.deleteSelfCollection(col)
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
                    viewModel.deleteSelfImage(img)
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
        SelfTimelapseConfigDialog(
            images = imgs,
            viewModel = viewModel,
            onDismiss = { showTimelapseConfig = null }
        )
    }
}

@Composable
private fun SelfAlbumCard(
    collection: SelfCollection,
    images: List<SelfCollectionImage>,
    viewModel: CalendarViewModel,
    isEditingEnabled: Boolean,
    onAddPhoto: () -> Unit,
    onTimelapse: () -> Unit,
    onRemoveAlbum: () -> Unit,
    canRemoveAlbum: Boolean = true,
    onImageClick: (SelfCollectionImage) -> Unit,
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
                if (isEditingEnabled && canRemoveAlbum) {
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
    image: SelfCollectionImage,
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
    image: SelfCollectionImage,
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
    images: List<SelfCollectionImage>,
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
private fun SelfTimelapseConfigDialog(
    images: List<SelfCollectionImage>,
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
        SelfTimelapsePlayer(
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
private fun SelfTimelapsePlayer(
    images: List<SelfCollectionImage>,
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
    onUpdateExerciseStatus: (exerciseId: String, status: String?) -> Unit,
    onExerciseClick: (List<WorkoutSet>) -> Unit,
    onLongPressWorkoutSessionActions: (sessionSets: List<WorkoutSet>, displayTitle: String) -> Unit,
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
        ExerciseDaySummarySection(sets = sets)
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
                onExerciseClick = onExerciseClick,
                onLongPressSessionActions = {
                    onLongPressWorkoutSessionActions(sessionSets, sessionTitle)
                },
            )
        }
        Spacer(modifier = Modifier.height(72.dp))
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
    onExerciseClick: (List<WorkoutSet>) -> Unit,
    onLongPressSessionActions: () -> Unit,
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongPressSessionActions() },
            ),
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
                    supersetRuns(exerciseBlocks).forEach { run ->
                        val letter = run.first().firstOrNull()?.groupingId
                        if (letter != null && run.size > 1) {
                            SupersetDivider(label = "${if (run.size > 2) "Tri-set" else "Superset"} $letter")
                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .padding(vertical = 4.dp)
                                        .background(NavyDeep.copy(alpha = 0.5f))
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    run.forEachIndexed { index, block ->
                                        SimpleExerciseCard(
                                            setsInExercise = block,
                                            badge = "$letter${index + 1}",
                                            isEditingEnabled = isEditingEnabled,
                                            onStatusChange = { onStatusChange(block.first().exerciseId, it) },
                                            onClick = { onExerciseClick(block) }
                                        )
                                    }
                                }
                            }
                        } else {
                            run.forEach { block ->
                                SimpleExerciseCard(
                                    setsInExercise = block,
                                    badge = null,
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
    }
}

/** Splits exercise blocks into runs of consecutive exercises that share a superset group. */
private fun supersetRuns(blocks: List<List<WorkoutSet>>): List<List<List<WorkoutSet>>> {
    val runs = mutableListOf<MutableList<List<WorkoutSet>>>()
    blocks.forEach { block ->
        val group = block.firstOrNull()?.groupingId
        val last = runs.lastOrNull()
        val lastGroup = last?.firstOrNull()?.firstOrNull()?.groupingId
        if (group != null && last != null && group == lastGroup) last.add(block) else runs.add(mutableListOf(block))
    }
    return runs
}

@Composable
private fun SimpleExerciseCard(
    setsInExercise: List<WorkoutSet>,
    badge: String?,
    isEditingEnabled: Boolean,
    onStatusChange: (String?) -> Unit,
    onClick: () -> Unit
) {
    val exerciseName = setsInExercise.firstOrNull()?.exerciseName?.ifBlank { setsInExercise.first().exerciseId } ?: ""
    val subtitle = remember(setsInExercise) { exerciseCardSubtitle(setsInExercise) }
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
            if (badge != null) {
                ExerciseBadge(text = badge, emphasized = true)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                    color = NavyDeep
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateGray
                    )
                }
            }
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
    onUpdateSet: WorkoutRowUpdate,
) {
    val monospaceLabel = MaterialTheme.typography.labelMedium.copy(
        fontFamily = FontFamily.Monospace
    )
    val sorted = setsInExercise.sortedWith(compareBy<WorkoutSet> { it.setNumber }.thenBy { it.dropNumber })
    val mains = sorted.filter { !it.isDropRow }
    val first = sorted.firstOrNull()
    val exerciseName = first?.exerciseName ?: ""
    val exerciseDescription = first?.exerciseDescription?.takeIf { it.isNotBlank() } ?: ""
    val timing = first?.timing?.takeIf { it.isNotBlank() }
    val exerciseType = first?.exerciseType?.ifBlank { "RESISTANCE" } ?: "RESISTANCE"
    val badges = exerciseBadges(sorted)
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
                if (badges.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        badges.forEach { ExerciseBadge(it) }
                    }
                }
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
                    mains.forEach { main ->
                        WorkoutSetRow(
                            setEntry = main,
                            drops = sorted.filter { it.isDropRow && it.setNumber == main.setNumber },
                            rowLabel = workoutRowLabel(main, exerciseType),
                            exerciseDescription = exerciseDescription,
                            isSingleRow = mains.size == 1,
                            isEditingEnabled = isEditingEnabled,
                            monospaceLabel = monospaceLabel,
                            onUpdateSet = onUpdateSet
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

private typealias WorkoutRowUpdate = (setId: String, transform: (WorkoutSet) -> WorkoutSet) -> Unit

private fun workoutRowLabel(row: WorkoutSet, exerciseType: String): String? = when {
    row.isInterval -> "Interval ${row.setNumber}"
    exerciseType.equals("RESISTANCE", ignoreCase = true) ->
        if (row.setType == WorkoutSetType.DROP) "Set ${row.setNumber} · Drop set" else "Set ${row.setNumber}"
    else -> null
}

private fun formatWorkoutNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else DecimalFormat("#.#").format(value)

private fun strokeLabel(stroke: String): String =
    stroke.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }

/** Superset, drop, stroke and planned-minutes chips for an exercise. */
private fun exerciseBadges(rows: List<WorkoutSet>): List<String> {
    val head = rows.firstOrNull() ?: return emptyList()
    val planned = ExerciseDayMath.plannedMinutes(rows)
    return buildList {
        head.groupingId?.let { add("Superset $it") }
        if (rows.any { it.isDropRow }) add("Drop set")
        head.stroke?.let { add(strokeLabel(it)) }
        if (planned > 0.0) add("${formatWorkoutNumber(planned)} min")
    }
}

/** One-line plan summary under the exercise name, e.g. "3 sets · drop" or "8 × 100 yd · Z2". */
private fun exerciseCardSubtitle(rows: List<WorkoutSet>): String {
    val mains = rows.filter { !it.isDropRow }
    val head = mains.firstOrNull() ?: return ""
    val planned = ExerciseDayMath.plannedMinutes(rows)
    return buildList {
        when {
            head.isInterval -> {
                val work = head.targetDistance?.let { "${formatWorkoutNumber(it)} ${head.unitDistance}" }
                    ?: head.targetDuration?.let { "$it ${head.unitDuration}" }
                add(if (work != null) "${mains.size} × $work" else "${mains.size} intervals")
                mains.mapNotNull { it.targetZone }.distinct().singleOrNull()?.let { add("Z$it") }
            }
            head.exerciseType.equals("RESISTANCE", ignoreCase = true) -> {
                add("${mains.size} set${if (mains.size != 1) "s" else ""}")
                if (rows.any { it.isDropRow }) add("drop")
            }
        }
        head.stroke?.let { add(strokeLabel(it)) }
        if (planned > 0.0) add("${formatWorkoutNumber(planned)} min")
    }.joinToString(" · ")
}

@Composable
private fun ExerciseBadge(text: String, emphasized: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (emphasized) NavyDeep else NavyDeep.copy(alpha = 0.08f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (emphasized) PureWhite else NavyDeep,
            maxLines = 1
        )
    }
}

@Composable
private fun SupersetDivider(label: String) {
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
            text = label.uppercase(Locale.US),
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
private fun WorkoutSetRow(
    setEntry: WorkoutSet,
    drops: List<WorkoutSet>,
    rowLabel: String?,
    exerciseDescription: String,
    isSingleRow: Boolean,
    isEditingEnabled: Boolean,
    monospaceLabel: androidx.compose.ui.text.TextStyle,
    onUpdateSet: WorkoutRowUpdate
) {
    val isResistance = setEntry.exerciseType.equals("RESISTANCE", ignoreCase = true)
    // OTHER exercises carry their work time only as the exercise total.
    val totalMinutesTarget = if (isSingleRow && !isResistance && !setEntry.isInterval && setEntry.targetDuration == null) {
        setEntry.totalDuration?.let { durationToMinutes(it, setEntry.unitTotalDuration) }
    } else {
        null
    }

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
            if (rowLabel != null) {
                Text(
                    text = rowLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = NavyDeep,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // OTHER: exercise name is the drill title; avoid repeating exerciseDescription as a note.
            val isOther = setEntry.exerciseType.equals("OTHER", ignoreCase = true)
            val showDescriptionNote = setEntry.description.isNotBlank() &&
                !isOther &&
                setEntry.description != exerciseDescription &&
                !(setEntry.setNumber == 1 && !setEntry.isInterval && exerciseDescription.isNotBlank())
            if (showDescriptionNote) {
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

            WorkoutRowTargetLine(setEntry, totalMinutesTarget, monospaceLabel)
            WorkoutRowActuals(setEntry, totalMinutesTarget, isEditingEnabled, monospaceLabel, onUpdateSet)

            drops.forEach { drop ->
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    color = DropSetOrange.copy(alpha = 0.4f)
                )
                Text(
                    text = "Drop ${drop.dropNumber}",
                    style = MaterialTheme.typography.titleSmall,
                    color = DropSetOrange,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                WorkoutRowTargetLine(drop, null, monospaceLabel)
                WorkoutRowActuals(drop, null, isEditingEnabled, monospaceLabel, onUpdateSet)
            }
        }
    }
}

@Composable
private fun WorkoutRowTargetLine(
    row: WorkoutSet,
    totalMinutesTarget: Double?,
    monospaceLabel: androidx.compose.ui.text.TextStyle,
) {
    val targetParts = buildList {
        row.targetLoad?.let { add("${formatWorkoutNumber(it)} ${row.unitLoad}") }
        row.targetReps?.let { add("$it r") }
        row.targetVelocity?.let { add("$it ${row.unitVelocity}") }
        row.targetDuration?.let { add("$it ${row.unitDuration}") }
        totalMinutesTarget?.let { add("${formatWorkoutNumber(it)} min") }
        row.targetDistance?.let { add("${formatWorkoutNumber(it)} ${row.unitDistance}") }
        row.targetZone?.let { add("Z$it") }
        row.targetRpe?.let { add("RPE ${formatWorkoutNumber(it)}") }
        row.targetCadence?.let { add("$it ${row.unitCadence}") }
        row.targetRir?.let { add("RIR $it") }
        row.targetRest?.takeIf { it > 0 }?.let { add("Rest $it ${row.restUnit}") }
    }
    if (targetParts.isEmpty()) return
    Text(
        text = "Target",
        style = MaterialTheme.typography.labelSmall,
        color = PureBlack.copy(alpha = 0.6f),
        modifier = Modifier.padding(bottom = 2.dp)
    )
    Text(
        text = targetParts.joinToString(" · "),
        style = monospaceLabel,
        color = PureBlack.copy(alpha = 0.85f),
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun WorkoutRowActuals(
    row: WorkoutSet,
    totalMinutesTarget: Double?,
    isEditingEnabled: Boolean,
    monospaceLabel: androidx.compose.ui.text.TextStyle,
    onUpdateSet: WorkoutRowUpdate,
) {
    val durationUnitIsMin = totalMinutesTarget != null ||
        row.unitDuration.lowercase().let { it == "min" || it == "minutes" || it == "minute" }
    val targetDurationInUnit = row.targetDuration ?: totalMinutesTarget?.roundToInt()
    val currentLoad = (row.actualLoad ?: row.targetLoad ?: 0.0).coerceIn(0.0, 999.9)
    val currentReps = (row.actualReps ?: row.targetReps ?: 0).coerceIn(0, 999)
    val currentVelocity = (row.actualVelocity ?: row.targetVelocity ?: 0.0).toInt().coerceIn(0, 99)
    val currentRpe = (row.actualRpe ?: row.targetRpe ?: 0.0).coerceIn(0.0, 10.0)
    val currentRir = (row.actualRir ?: row.targetRir ?: 0).coerceIn(0, 10)
    val currentDuration = when {
        durationUnitIsMin -> (row.actualDuration?.div(60) ?: targetDurationInUnit ?: 0).coerceIn(0, 9999)
        else -> (row.actualDuration ?: targetDurationInUnit ?: 0).coerceIn(0, 99999)
    }
    val currentDistance = (row.actualDistance ?: row.targetDistance ?: 0.0).coerceIn(0.0, 9999.9)
    val currentCadence = (row.actualCadence ?: row.targetCadence ?: 0).coerceIn(0, 250)
    val currentRest = (row.actualRest ?: row.targetRest ?: 0).coerceIn(0, 9999)

    val showLoad = row.targetLoad != null || row.actualLoad != null
    val showReps = row.targetReps != null || row.actualReps != null
    val showDuration = targetDurationInUnit != null || row.actualDuration != null
    val showDistance = row.targetDistance != null || row.actualDistance != null
    val showRpe = !row.isDropRow && (row.actualRpe != null || row.targetRpe != null || row.targetRir != null ||
        showDuration || showDistance)
    val showRir = row.targetRir != null || row.actualRir != null
    val showVelocity = row.targetVelocity != null || row.actualVelocity != null
    val showCadence = row.targetCadence != null || row.actualCadence != null
    val showRest = (row.targetRest ?: 0) > 0 || row.actualRest != null
    val hasAnyActual = showLoad || showReps || showRpe || showRir || showVelocity || showDuration ||
        showDistance || showCadence || showRest
    if (!hasAnyActual) return

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
                onValueChange = { v -> onUpdateSet(row.id) { it.copy(actualLoad = v) } },
                min = 0.0,
                max = 999.9,
                step = 0.1,
                enabled = isEditingEnabled,
                label = "Load (${row.unitLoad})",
                textStyle = monospaceLabel
            )
        }
        if (showReps) {
            WorkoutNumberStepper(
                value = currentReps,
                onValueChange = { v -> onUpdateSet(row.id) { it.copy(actualReps = v) } },
                min = 0,
                max = 999,
                enabled = isEditingEnabled,
                label = "Reps",
                textStyle = monospaceLabel
            )
        }
        if (showDuration) {
            WorkoutNumberStepper(
                value = currentDuration,
                onValueChange = { valueInDisplayUnit ->
                    val seconds = if (durationUnitIsMin) valueInDisplayUnit * 60 else valueInDisplayUnit
                    onUpdateSet(row.id) { it.copy(actualDuration = seconds) }
                },
                min = 0,
                max = if (durationUnitIsMin) 9999 else 99999,
                enabled = isEditingEnabled,
                label = "Duration (${if (durationUnitIsMin) "min" else row.unitDuration})",
                textStyle = monospaceLabel
            )
        }
        if (showDistance) {
            WorkoutNumberStepperDouble(
                value = currentDistance,
                onValueChange = { v -> onUpdateSet(row.id) { it.copy(actualDistance = v) } },
                min = 0.0,
                max = 9999.9,
                step = if (row.unitDistance.lowercase() in listOf("m", "yd")) 25.0 else 0.1,
                enabled = isEditingEnabled,
                label = "Distance (${row.unitDistance})",
                textStyle = monospaceLabel
            )
        }
        if (showCadence) {
            WorkoutNumberStepper(
                value = currentCadence,
                onValueChange = { v -> onUpdateSet(row.id) { it.copy(actualCadence = v) } },
                min = 0,
                max = 250,
                enabled = isEditingEnabled,
                label = "Cadence (${row.unitCadence})",
                textStyle = monospaceLabel
            )
        }
        if (showRpe) {
            WorkoutNumberStepperDouble(
                value = currentRpe,
                onValueChange = { v -> onUpdateSet(row.id) { it.copy(actualRpe = (v * 2).roundToInt() / 2.0) } },
                min = 0.0,
                max = 10.0,
                step = 0.5,
                enabled = isEditingEnabled,
                label = "RPE (0–10)",
                textStyle = monospaceLabel
            )
        }
        if (showRir) {
            WorkoutNumberStepper(
                value = currentRir,
                onValueChange = { v -> onUpdateSet(row.id) { it.copy(actualRir = v) } },
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
                onValueChange = { v -> onUpdateSet(row.id) { it.copy(actualVelocity = v.toDouble()) } },
                min = 0,
                max = 99,
                enabled = isEditingEnabled,
                label = "Velocity (${row.unitVelocity})",
                textStyle = monospaceLabel
            )
        }
        if (showRest) {
            WorkoutNumberStepper(
                value = currentRest,
                onValueChange = { v -> onUpdateSet(row.id) { it.copy(actualRest = v) } },
                min = 0,
                max = 9999,
                enabled = isEditingEnabled,
                label = "Rest (${row.restUnit})",
                textStyle = monospaceLabel
            )
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

private fun foodPlannedTimeToMinutes(plannedTime: String): Int {
    val parts = plannedTime.trim().split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 12
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return ((h * 60 + m) % (24 * 60) + 24 * 60) % (24 * 60)
}

private fun dietMinutesToPlannedTime(minutes: Int): String {
    val m = (minutes % (24 * 60) + 24 * 60) % (24 * 60)
    return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodPlannedTimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit,
) {
    val totalMinutes = 24 * 60
    val normalized = (initialMinutes % totalMinutes + totalMinutes) % totalMinutes
    val state = rememberTimePickerState(
        initialHour = normalized / 60,
        initialMinute = normalized % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meal time", color = PureBlack) },
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
                    timeSelectorUnselectedContentColor = NavyDeep,
                ),
            )
        },
        containerColor = PureWhite,
    )
}

private enum class FoodMacroOverflow { Protein, Penalty }

private fun foodMacroRatio(got: Double, planned: Double): Float =
    when {
        planned > 0.0 -> (got / planned).toFloat()
        got > 0.0 -> 1.01f
        else -> 0f
    }

private fun foodMacroBarColor(ratio: Float, overflow: FoodMacroOverflow): Color {
    if (ratio > 1f) {
        return when (overflow) {
            FoodMacroOverflow.Protein -> ElectricBlue
            FoodMacroOverflow.Penalty -> MoodColor1
        }
    }
    val t = ratio.coerceIn(0f, 1f)
    val lastIndex = MoodPalette.lastIndex
    if (lastIndex <= 0) return MoodColor1
    val scaled = t * lastIndex
    val i = scaled.toInt().coerceIn(0, lastIndex - 1)
    val frac = scaled - i
    return lerp(MoodPalette[i], MoodPalette[i + 1], frac)
}

private fun leftoverLabel(leftover: Double, unit: String? = null, format: (Double) -> String): String {
    val magnitude = abs(leftover)
    val amount = format(magnitude)
    val amountWithUnit = if (unit.isNullOrBlank()) amount else "$amount $unit"
    return if (leftover >= 0.0) "$amountWithUnit left" else "$amountWithUnit over"
}

@Composable
private fun FoodDayMacrosSummary(entries: List<FoodEntry>) {
    val totals = remember(entries) { AnalysisStats.foodDayMacroTotals(entries) }
    val gramFormat = remember { DecimalFormat("#.##") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FoodMacroMeter(
            label = "Calories",
            gotPlannedLabel = "${totals.gotKcal.roundToInt()} / ${totals.plannedKcal.roundToInt()} kcal",
            leftoverText = leftoverLabel(totals.plannedKcal - totals.gotKcal, "kcal") {
                it.roundToInt().toString()
            },
            ratio = foodMacroRatio(totals.gotKcal, totals.plannedKcal),
            overflow = FoodMacroOverflow.Penalty,
            gotStyle = MaterialTheme.typography.headlineSmall,
            centered = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FoodMacroMeter(
                modifier = Modifier.weight(1f),
                label = "Protein",
                gotPlannedLabel = "${gramFormat.format(totals.gotProtein)} / ${gramFormat.format(totals.plannedProtein)}g",
                leftoverText = leftoverLabel(totals.plannedProtein - totals.gotProtein) {
                    gramFormat.format(it)
                },
                ratio = foodMacroRatio(totals.gotProtein, totals.plannedProtein),
                overflow = FoodMacroOverflow.Protein,
                gotStyle = MaterialTheme.typography.bodyLarge,
                centered = true,
            )
            FoodMacroMeter(
                modifier = Modifier.weight(1f),
                label = "Carbs",
                gotPlannedLabel = "${gramFormat.format(totals.gotCarbs)} / ${gramFormat.format(totals.plannedCarbs)}g",
                leftoverText = leftoverLabel(totals.plannedCarbs - totals.gotCarbs) {
                    gramFormat.format(it)
                },
                ratio = foodMacroRatio(totals.gotCarbs, totals.plannedCarbs),
                overflow = FoodMacroOverflow.Penalty,
                gotStyle = MaterialTheme.typography.bodyLarge,
                centered = true,
            )
            FoodMacroMeter(
                modifier = Modifier.weight(1f),
                label = "Fats",
                gotPlannedLabel = "${gramFormat.format(totals.gotFats)} / ${gramFormat.format(totals.plannedFats)}g",
                leftoverText = leftoverLabel(totals.plannedFats - totals.gotFats) {
                    gramFormat.format(it)
                },
                ratio = foodMacroRatio(totals.gotFats, totals.plannedFats),
                overflow = FoodMacroOverflow.Penalty,
                gotStyle = MaterialTheme.typography.bodyLarge,
                centered = true,
            )
        }
    }
}

@Composable
private fun FoodMacroMeter(
    label: String,
    gotPlannedLabel: String,
    leftoverText: String,
    ratio: Float,
    overflow: FoodMacroOverflow,
    gotStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    centered: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = SlateGray,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        Text(
            text = gotPlannedLabel,
            style = gotStyle,
            color = PureBlack,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 1,
        )
        Text(
            text = leftoverText,
            style = MaterialTheme.typography.labelSmall,
            color = SlateGray,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 1,
        )
        FoodMacroProgressBar(
            ratio = ratio,
            color = foodMacroBarColor(ratio, overflow),
        )
    }
}

@Composable
private fun FoodMacroProgressBar(ratio: Float, color: Color) {
    val fill = ratio.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(OffWhite),
    ) {
        if (fill > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .fillMaxHeight()
                    .background(color),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MealPlanTypeSwitch(
    asPlanned: Boolean,
    onAsPlannedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PureBlack.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(3.dp),
    ) {
        listOf(false to "New", true to "Planned").forEach { (planned, label) ->
            val selected = asPlanned == planned
            Surface(
                onClick = { onAsPlannedChange(planned) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) NavyDeep else PureWhite,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = label,
                    color = if (selected) PureWhite else PureBlack,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CollapsibleFoodTimeSection(
    time: String,
    entries: List<FoodEntry>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    isEditingEnabled: Boolean,
    viewModel: CalendarViewModel,
    onEditEntryTime: (FoodEntry) -> Unit,
    onOpenFoodTileActions: (FoodEntry) -> Unit,
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
                FoodEntryCard(
                    entry = entry,
                    isEditingEnabled = isEditingEnabled,
                    onMultiplierChange = { multiplier ->
                        viewModel.updateEntryMultiplier(entry.id, multiplier)
                    },
                    onEatenStateCycle = { viewModel.cycleEntryEatenState(entry.id) },
                    onEditTimeClick = { onEditEntryTime(entry) },
                    onLongPressForTileActions = { onOpenFoodTileActions(entry) },
                    onEditClick = if (entry.status == AuditStatus.UNPLANNED) {
                        { viewModel.editUnplannedEntry(entry) }
                    } else null,
                    onDeleteClick = if (entry.status == AuditStatus.UNPLANNED) {
                        { viewModel.deleteEntry(entry.id) }
                    } else null,
                    onMarkAsPlanned = if (entry.status == AuditStatus.UNPLANNED) {
                        { viewModel.markEntryAsPlanned(entry.id) }
                    } else null,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private val DoneGreen = androidx.compose.ui.graphics.Color(0xFFE8F5E9)
private val NotEatenRedBg = androidx.compose.ui.graphics.Color(0xFFFFEBEE)

@Composable
private fun FoodEntryCard(
    entry: FoodEntry,
    isEditingEnabled: Boolean,
    onMultiplierChange: (Double) -> Unit,
    onEatenStateCycle: () -> Unit,
    onEditTimeClick: () -> Unit,
    onLongPressForTileActions: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onMarkAsPlanned: (() -> Unit)? = null,
) {
    val targetAmount = entry.effectiveAmount() * entry.quantityMultiplier
    val formatter = DecimalFormat("#.##")
    val isNotEaten = entry.notEaten
    val isDone = entry.isChecked && !entry.notEaten
    val tileBackground = when {
        isNotEaten -> NotEatenRedBg
        isDone -> DoneGreen
        else -> PureWhite
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isEditingEnabled) onEditTimeClick() },
                onLongClick = { onLongPressForTileActions() },
            ),
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
                    StatusBadge(
                        status = entry.status,
                        onClick = if (isEditingEnabled) onMarkAsPlanned else null,
                    )
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
@OptIn(ExperimentalMaterial3Api::class)
private fun StatusBadge(
    status: AuditStatus,
    onClick: (() -> Unit)? = null,
) {
    val (text, color) = when (status) {
        AuditStatus.PLANNED -> "PLANNED" to NavyDeep
        AuditStatus.ADJUSTED -> "ADJUSTED" to ElectricBlue
        AuditStatus.UNPLANNED -> "NEW" to DeleteRed
    }
    val badgeText: @Composable () -> Unit = {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(4.dp),
            color = color.copy(alpha = 0.2f),
            modifier = Modifier.padding(start = 8.dp)
        ) {
            badgeText()
        }
    } else {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = color.copy(alpha = 0.2f),
            modifier = Modifier.padding(start = 8.dp)
        ) {
            badgeText()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekScroller(
    selectedDate: String,
    /** Days the current category already has data for; drawn as a dot under the date. */
    markedDates: Set<String>,
    isEditingEnabled: Boolean,
    /** True when the current tab has unsaved changes for this day. */
    hasUnsavedChanges: Boolean,
    isSaving: Boolean,
    onDateSelected: (String) -> Unit,
    onToggleLock: () -> Unit,
    onSave: () -> Unit,
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
                val isMarked = markedDates.contains(dateStr)
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
                        // Indicator dot for days that already have data (centered below number)
                        if (isMarked && !isSelected) {
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
            
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier
                    .size(48.dp)
                    .then(
                        if (isEditingEnabled) Modifier.background(NavyDeep, CircleShape)
                        else Modifier
                    )
            ) {
                Icon(
                    imageVector = if (isEditingEnabled) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = if (isEditingEnabled) "Lock editing" else "Unlock editing",
                    tint = if (isEditingEnabled) PureWhite else PureBlack.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
            val canSave = hasUnsavedChanges && isEditingEnabled && !isSaving
            IconButton(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier
                    .size(48.dp)
                    .then(if (canSave) Modifier.background(NavyDeep, CircleShape) else Modifier)
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
                        contentDescription = if (hasUnsavedChanges) "Save changes" else "No changes to save",
                        tint = if (canSave) PureWhite else PureBlack.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                }
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
internal fun AddMealDialog(
    entry: FoodEntry? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double, String, Double, Double, Double, Double, Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
    /** New meals only: values from the clipboard, AI or the library. */
    prefill: MealImportDraft? = null,
) {
    val nowTime = remember {
        SimpleDateFormat("HH:mm", Locale.US).format(Date())
    }
    val seed = prefill?.takeIf { entry == null }
    fun Double.asField(): String = if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()
    var name by remember { mutableStateOf(entry?.name ?: seed?.name.orEmpty()) }
    var time by remember {
        mutableStateOf(entry?.effectiveTime() ?: seed?.time?.ifBlank { null } ?: nowTime)
    }
    var amount by remember {
        mutableStateOf(
            (entry?.effectiveAmount() ?: seed?.amount)?.takeIf { it > 0 }?.asField() ?: ""
        )
    }
    var unit by remember { mutableStateOf(entry?.unit ?: seed?.unit?.ifBlank { null } ?: "g") }
    var kcal by remember { mutableStateOf((entry?.kcal ?: seed?.kcal)?.asField() ?: "") }
    var protein by remember { mutableStateOf((entry?.protein ?: seed?.protein)?.asField() ?: "0") }
    var carbs by remember { mutableStateOf((entry?.carbs ?: seed?.carbs)?.asField() ?: "0") }
    var fats by remember { mutableStateOf((entry?.fats ?: seed?.fats)?.asField() ?: "0") }
    var asPlanned by remember { mutableStateOf(entry?.hasPresetBaseline() ?: seed?.asPlanned ?: false) }
    var jsonErrorMessage by remember { mutableStateOf<String?>(null) }
    var showAiDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = NavyDeep,
        unfocusedBorderColor = NavyDeep,
        focusedTextColor = PureBlack,
        unfocusedTextColor = PureBlack,
    )

    fun applyDraft(draft: MealImportDraft, keepTime: Boolean) {
        name = draft.name
        if (!keepTime && draft.time.isNotBlank()) time = draft.time
        amount = draft.amount.asField()
        unit = draft.unit.ifBlank { "g" }
        kcal = draft.kcal.asField()
        protein = draft.protein.asField()
        carbs = draft.carbs.asField()
        fats = draft.fats.asField()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        title = {
            Text(
                text = if (entry == null) "Add Meal" else "Edit Meal",
                color = PureBlack
            )
        },
        text = {
            KeyboardAwareDialogColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (entry != null) {
                    Button(
                        onClick = {
                            parseMealClipboardText(readClipboardText(context), CalendarAiMode.SINGLE).fold(
                                onSuccess = { drafts -> applyDraft(drafts.first(), keepTime = false) },
                                onFailure = { jsonErrorMessage = it.message ?: "Couldn't read the clipboard" },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyDeep.copy(alpha = 0.1f),
                            contentColor = NavyDeep
                        )
                    ) {
                        Text("Fill from Clipboard", color = NavyDeep)
                    }
                    Button(
                        onClick = { showAiDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue.copy(alpha = 0.15f),
                            contentColor = NavyDeep
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = NavyDeep
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fill from AI", color = NavyDeep)
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Meal Name", color = PureBlack) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors,
                )

                MealPlanTypeSwitch(
                    asPlanned = asPlanned,
                    onAsPlannedChange = { asPlanned = it },
                )

                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Time (HH:mm)", color = PureBlack) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(nowTime, color = PureBlack.copy(alpha = 0.6f)) },
                    colors = fieldColors,
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
                        colors = fieldColors,
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit", color = PureBlack) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("g", color = PureBlack.copy(alpha = 0.6f)) },
                        colors = fieldColors,
                    )
                }

                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it },
                    label = { Text("Calories (kcal)", color = PureBlack) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors,
                )

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
                        colors = fieldColors,
                    )
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text("Carbs (g)", color = PureBlack) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = fieldColors,
                    )
                    OutlinedTextField(
                        value = fats,
                        onValueChange = { fats = it },
                        label = { Text("Fats (g)", color = PureBlack) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = fieldColors,
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
                        onConfirm(name, time, amountValue, unit, kcalValue, proteinValue, carbsValue, fatsValue, asPlanned)
                    }
                },
                enabled = name.isNotBlank() && time.isNotBlank() &&
                    (amount.toDoubleOrNull() ?: 0.0) > 0 &&
                    (kcal.toDoubleOrNull() ?: -1.0) >= 0 &&
                    protein.toDoubleOrNull() != null && carbs.toDoubleOrNull() != null && fats.toDoubleOrNull() != null
            ) {
                Text(if (entry == null) "Add" else "Save", color = NavyDeep)
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

    jsonErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { jsonErrorMessage = null },
            title = { Text("Clipboard error", color = PureBlack) },
            text = { Text(message, color = PureBlack) },
            confirmButton = {
                TextButton(onClick = { jsonErrorMessage = null }) {
                    Text("OK", color = NavyDeep)
                }
            },
            containerColor = PureWhite
        )
    }

    if (showAiDialog) {
        FillMealFromAiDialog(
            mode = CalendarAiMode.SINGLE,
            date = entry?.date.orEmpty(),
            keepsExistingTime = true,
            onDismiss = { showAiDialog = false },
            onEstimates = { estimates ->
                applyDraft(estimates.first().toImportDraft(asPlanned), keepTime = true)
                showAiDialog = false
            },
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
