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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.core.ui.theme.VectorTheme
import com.vm.vector.ui.viewmodel.CalendarUiState
import com.vm.vector.ui.viewmodel.CalendarViewModel
import com.vm.vector.ui.viewmodel.CalendarViewModelFactory
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
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCat) }

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

                CategorySwitch(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                )

                WeekScroller(
                    selectedDate = uiState.currentDate,
                    datesWithCheckedMeals = uiState.datesWithCheckedMeals,
                    isEditingEnabled = uiState.isEditingEnabled,
                    showDietActions = selectedCategory == CalendarCategory.Diet || selectedCategory == CalendarCategory.Routine || selectedCategory == CalendarCategory.Workout,
                    onDateSelected = { date ->
                        viewModel.selectDate(date)
                    },
                    onAddMealClick = {
                        if (uiState.isEditingEnabled) {
                            when (selectedCategory) {
                                CalendarCategory.Diet -> showAddMealDialog = true
                                CalendarCategory.Routine -> showAddRoutineDialog = true
                                CalendarCategory.Workout -> showAddWorkoutDialog = true
                                else -> {}
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

                if (selectedCategory == CalendarCategory.Diet) {
                    val groupedEntries = uiState.entries.groupBy { it.plannedTime }
                        .toSortedMap()

                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading && !uiState.isSaving,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.weight(1f)
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
                                groupedEntries.forEach { (time, entries) ->
                                    TimeHeader(time = time)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    entries.forEach { entry ->
                                        DietEntryCard(
                                            entry = entry,
                                            isEditingEnabled = uiState.isEditingEnabled,
                                            onMultiplierChange = { multiplier ->
                                                viewModel.updateEntryMultiplier(entry.id, multiplier)
                                            },
                                            onCheckedChange = {
                                                viewModel.toggleEntryChecked(entry.id)
                                            },
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
                    }
                } else if (selectedCategory == CalendarCategory.Routine) {
                    val groupedEntries = uiState.routineEntries.groupBy { it.category }
                        .toSortedMap()

                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading && !uiState.isSaving,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.weight(1f)
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
                } else if (selectedCategory == CalendarCategory.Workout) {
                    var exerciseModalExerciseId by remember { mutableStateOf<String?>(null) }
                    val modalSets = remember(exerciseModalExerciseId, uiState.workoutSets) {
                        exerciseModalExerciseId?.let { id ->
                            uiState.workoutSets.filter { it.exerciseId == id }
                        }.orEmpty()
                    }
                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading && !uiState.isWorkoutSaving,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.weight(1f)
                    ) {
                        WorkoutContent(
                            sets = uiState.workoutSets,
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
                            onSave = {
                                viewModel.saveWorkout()
                                exerciseModalExerciseId = null
                            },
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
                            isEditingEnabled = uiState.isEditingEnabled
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
                        onClick = { viewModel.saveDay() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
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
                        onClick = { viewModel.saveDay() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
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
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyDeep,
                            contentColor = PureWhite
                        ),
                        enabled = uiState.isEditingEnabled && uiState.workoutSets.isNotEmpty()
                    ) {
                        if (uiState.isWorkoutSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Save Workout")
                    }
                } else if (selectedCategory == CalendarCategory.Diary) {
                    Button(
                        onClick = { viewModel.saveDiary() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyDeep,
                            contentColor = PureWhite
                        ),
                        enabled = !uiState.isDiarySaving
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
private val MOOD_COLORS = listOf(
    androidx.compose.ui.graphics.Color(0xFFFF9800), // 1 = orange
    androidx.compose.ui.graphics.Color(0xFFE53935), // 2 = red
    androidx.compose.ui.graphics.Color(0xFFFFEB3B), // 3 = yellow
    androidx.compose.ui.graphics.Color(0xFF8BC34A), // 4 = light green
    androidx.compose.ui.graphics.Color(0xFF4CAF50)   // 5 = green
)

@Composable
private fun DiaryContent(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel,
    uiState: CalendarUiState,
    isEditingEnabled: Boolean
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
                val color = MOOD_COLORS[level - 1]
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
            isEditingEnabled = isEditingEnabled
        )
    }
}

private data class PendingPhoto(val collection: DiaryCollection, val uri: Uri)

@Composable
private fun DiaryCollectionsSection(
    viewModel: CalendarViewModel,
    collectionsWithImages: List<Pair<DiaryCollection, List<DiaryCollectionImage>>>,
    isEditingEnabled: Boolean
) {
    val context = LocalContext.current
    var showNewCollectionDialog by remember { mutableStateOf(false) }
    var showTimelapseConfig by remember { mutableStateOf<Pair<DiaryCollection, List<DiaryCollectionImage>>?>(null) }
    var showImageViewer by remember { mutableStateOf<DiaryCollectionImage?>(null) }
    var showImageGallery by remember { mutableStateOf<Pair<DiaryCollection, List<DiaryCollectionImage>>?>(null) }
    var showDeleteCollectionConfirm by remember { mutableStateOf<DiaryCollection?>(null) }
    var showDeleteImageConfirm by remember { mutableStateOf<DiaryCollectionImage?>(null) }
    var pendingPhoto by remember { mutableStateOf<PendingPhoto?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        pendingPhoto?.let { (collection, uri) ->
            if (success) {
                coroutineScope.launch {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = stream.readBytes()
                            viewModel.addImageToCollection(
                                collectionId = collection.id,
                                fileName = "image_${System.currentTimeMillis()}.jpg",
                                mimeType = "image/jpeg",
                                bytes = bytes,
                                takenAtMillis = System.currentTimeMillis()
                            )
                        }
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

    fun onAddPhotoClick(collection: DiaryCollection) {
        val file = File(context.cacheDir, "diary_${System.currentTimeMillis()}.jpg")
        file.createNewFile()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = PendingPhoto(collection, uri)
        when {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
                takePictureLauncher.launch(uri)
            else -> permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    Button(
        onClick = { showNewCollectionDialog = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite)
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("New collection")
    }

    collectionsWithImages.forEach { (collection, images) ->
        Spacer(modifier = Modifier.height(12.dp))
        DiaryCollectionRow(
            collection = collection,
            images = images,
            isEditingEnabled = isEditingEnabled,
            viewModel = viewModel,
            context = context,
            onAddImageClick = { onAddPhotoClick(collection) },
            onImageClick = { showImageViewer = it },
            onViewAllClick = { showImageGallery = Pair(collection, images) },
            onTimelapseClick = { showTimelapseConfig = Pair(collection, images) },
            onDeleteCollectionClick = { showDeleteCollectionConfirm = collection }
        )
    }

    if (showNewCollectionDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewCollectionDialog = false },
            title = { Text("New collection") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Collection name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.createCollection(name.trim()).fold(
                                onSuccess = { showNewCollectionDialog = false; name = "" },
                                onFailure = { }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite)
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewCollectionDialog = false }) { Text("Cancel") }
            }
        )
    }

    showDeleteCollectionConfirm?.let { col ->
        AlertDialog(
            onDismissRequest = { showDeleteCollectionConfirm = null },
            title = { Text("Delete collection?") },
            text = { Text("This will remove \"${col.name}\" and all its images from the app and from Drive.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDiaryCollection(col)
                    showDeleteCollectionConfirm = null
                }) { Text("Delete", color = DeleteRed) }
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

    showImageGallery?.let { (col, imgs) ->
        DiaryImageGalleryDialog(
            collection = col,
            images = imgs,
            viewModel = viewModel,
            onDismiss = { showImageGallery = null }
        )
    }

    showTimelapseConfig?.let { (col, imgs) ->
        DiaryTimelapseConfigDialog(
            collection = col,
            images = imgs,
            viewModel = viewModel,
            onDismiss = { showTimelapseConfig = null }
        )
    }
}

@Composable
private fun DiaryCollectionRow(
    collection: DiaryCollection,
    images: List<DiaryCollectionImage>,
    isEditingEnabled: Boolean,
    viewModel: CalendarViewModel,
    context: Context,
    onAddImageClick: () -> Unit,
    onImageClick: (DiaryCollectionImage) -> Unit,
    onViewAllClick: () -> Unit,
    onTimelapseClick: () -> Unit,
    onDeleteCollectionClick: () -> Unit
) {
    var imageBytesCache by remember(collection.id, images.map { it.id }) { mutableStateOf<Map<String, ByteArray>>(emptyMap()) }
    LaunchedEffect(collection.id, images) {
        val newMap = mutableMapOf<String, ByteArray>()
        images.forEach { img ->
            viewModel.getImageBytes(img.driveFileId)?.let { bytes -> newMap[img.id] = bytes }
        }
        imageBytesCache = newMap
    }
    val thumbSize = 72.dp
    val maxVisibleThumbs = 4
    val hasOverflow = images.size > maxVisibleThumbs
    val visibleImages = if (hasOverflow) images.take(maxVisibleThumbs - 1) else images

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OffWhite)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = collection.name,
                style = MaterialTheme.typography.titleSmall,
                color = NavyDeep
            )
            if (isEditingEnabled) {
                IconButton(onClick = onDeleteCollectionClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete collection", tint = DeleteRed, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditingEnabled) {
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NavyDeep.copy(alpha = 0.2f))
                        .border(1.dp, NavyDeep, RoundedCornerShape(8.dp))
                        .clickable { onAddImageClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add photo", tint = NavyDeep, modifier = Modifier.size(32.dp))
                }
            }
            visibleImages.forEach { img ->
                val bytes = imageBytesCache[img.id]
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onImageClick(img) }
                ) {
                    if (bytes != null) {
                        val bitmap = remember(bytes) {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(PureBlack.copy(alpha = 0.1f)))
                    }
                }
            }
            if (hasOverflow) {
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NavyDeep.copy(alpha = 0.3f))
                        .clickable { onViewAllClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("${images.size}", color = PureWhite, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (images.isNotEmpty()) {
            Button(
                onClick = onTimelapseClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep.copy(alpha = 0.8f), contentColor = PureWhite)
            ) {
                Text("View Timelapse")
            }
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
    collection: DiaryCollection,
    images: List<DiaryCollectionImage>,
    viewModel: CalendarViewModel,
    onDismiss: () -> Unit
) {
    var index by remember { mutableStateOf(0) }
    val img = images.getOrNull(index)
    var bitmap by remember(img) { mutableStateOf<Bitmap?>(null) }
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
    collection: DiaryCollection,
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
    val totalMs = durationSeconds * 1000L
    val perImageMs = if (images.isEmpty()) 1000L else totalMs / images.size
    var currentIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var bitmap by remember(images.getOrNull(currentIndex)?.id) { mutableStateOf<Bitmap?>(null) }
    val currentImage = images.getOrNull(currentIndex)
    LaunchedEffect(currentImage?.driveFileId) {
        if (currentImage != null) {
            val bytes = viewModel.getImageBytes(currentImage.driveFileId)
            bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
    }
    LaunchedEffect(isPlaying, currentIndex, images.size) {
        if (!isPlaying || images.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(perImageMs)
        if (currentIndex < images.size - 1) {
            currentIndex++
            progress = currentIndex.toFloat() / (images.size - 1).coerceAtLeast(1)
        } else {
            currentIndex = 0
            progress = 0f
        }
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
                        currentIndex = (it * (images.size - 1).coerceAtLeast(1)).toInt().coerceIn(0, images.size - 1)
                    },
                    valueRange = 0f..1f
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
    isEditingEnabled: Boolean,
    onUpdateActuals: (setId: String, actualLoad: Double?, actualReps: Int?, actualVelocity: Double?, actualRpe: Double?, actualRir: Int?, actualDuration: Int?, actualDistance: Double?, actualRest: Int?) -> Unit,
    onUpdateExerciseStatus: (exerciseId: String, status: String?) -> Unit,
    onExerciseClick: (List<WorkoutSet>) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        if (sets.isEmpty()) {
            Text(
                text = "No workout for this day. Select another date or add a plan at presets/workout_weekly.json.",
                style = MaterialTheme.typography.bodyMedium,
                color = PureBlack.copy(alpha = 0.7f),
                modifier = Modifier.padding(vertical = 24.dp)
            )
            return@Column
        }
        val byType = sets.groupBy { it.exerciseType.ifBlank { "RESISTANCE" } }.toList()
            .sortedBy { (type, _) -> type }
        byType.forEach { (exerciseType, typeSets) ->
            TypeHeader(title = exerciseType)
            val orderedByExercise = typeSets.groupBy { it.exerciseId }
                .toList()
                .map { (_, block) -> block to typeSets.indexOf(block.first()) }
                .sortedBy { it.second }
                .map { it.first }
            orderedByExercise.forEach { block ->
                SimpleExerciseCard(
                    setsInExercise = block,
                    isEditingEnabled = isEditingEnabled,
                    onStatusChange = { onUpdateExerciseStatus(block.first().exerciseId, it) },
                    onClick = { onExerciseClick(block) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
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
    onSave: () -> Unit,
    isEditingEnabled: Boolean,
    onUpdateActuals: (setId: String, actualLoad: Double?, actualReps: Int?, actualVelocity: Double?, actualRpe: Double?, actualRir: Int?, actualDuration: Int?, actualDistance: Double?, actualRest: Int?) -> Unit
) {
    val monospaceLabel = MaterialTheme.typography.labelMedium.copy(
        fontFamily = FontFamily.Monospace
    )
    val exerciseName = setsInExercise.firstOrNull()?.exerciseName ?: ""
    val exerciseType = setsInExercise.firstOrNull()?.exerciseType?.ifBlank { "RESISTANCE" } ?: "RESISTANCE"
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
                    modifier = Modifier.padding(bottom = 16.dp)
                )
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyDeep,
                            contentColor = PureWhite
                        )
                    ) {
                        Text("Save Changes")
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
            setsInExercise.sortedBy { it.setNumber }.forEach { setEntry ->
                WorkoutSetRow(
                    setEntry = setEntry,
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
                modifier = Modifier.widthIn(min = 32.dp),
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
}

@Composable
private fun WorkoutSetRow(
    setEntry: WorkoutSet,
    showSetLabel: Boolean,
    isEditingEnabled: Boolean,
    monospaceLabel: androidx.compose.ui.text.TextStyle,
    onUpdateActuals: (setId: String, actualLoad: Double?, actualReps: Int?, actualVelocity: Double?, actualRpe: Double?, actualRir: Int?, actualDuration: Int?, actualDistance: Double?, actualRest: Int?) -> Unit
) {
    val currentLoad = (setEntry.actualLoad ?: setEntry.targetLoad ?: 0.0).toInt().coerceIn(0, 999)
    val currentReps = (setEntry.actualReps ?: setEntry.targetReps ?: 0).coerceIn(0, 999)
    val currentVelocity = (setEntry.actualVelocity ?: setEntry.targetVelocity ?: 0.0).toInt().coerceIn(0, 99)
    val currentRpe = (setEntry.actualRpe ?: 0.0).toInt().coerceIn(0, 10)
    val currentRir = (setEntry.actualRir ?: setEntry.targetRir ?: 0).coerceIn(0, 10)
    val currentDuration = (setEntry.actualDuration ?: setEntry.targetDuration ?: 0).coerceIn(0, 9999)
    val currentDistanceInt = (setEntry.actualDistance ?: setEntry.targetDistance ?: 0.0).toInt().coerceIn(0, 999)
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

            if (setEntry.description.isNotBlank()) {
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
                        WorkoutNumberStepper(
                            value = currentLoad,
                            onValueChange = { onUpdateActuals(setEntry.id, it.toDouble(), null, null, null, null, null, null, null) },
                            min = 0,
                            max = 999,
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
                            onValueChange = { onUpdateActuals(setEntry.id, null, null, null, null, null, it, null, null) },
                            min = 0,
                            max = 9999,
                            enabled = isEditingEnabled,
                            label = "Duration (${setEntry.unitDuration})",
                            textStyle = monospaceLabel
                        )
                    }
                    if (showDistance) {
                        WorkoutNumberStepper(
                            value = currentDistanceInt,
                            onValueChange = { onUpdateActuals(setEntry.id, null, null, null, null, null, null, it.toDouble(), null) },
                            min = 0,
                            max = 999,
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
                    Text("Paste from clipboard")
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
private fun TimeHeader(time: String) {
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
            text = time,
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
private fun DietEntryCard(
    entry: DietEntry,
    isEditingEnabled: Boolean,
    onMultiplierChange: (Double) -> Unit,
    onCheckedChange: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
) {
    val targetAmount = entry.plannedAmount * entry.quantityMultiplier
    val formatter = DecimalFormat("#.##")
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = PureWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row: Name, Checkbox, Status, Edit/Delete buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = entry.isChecked,
                        onCheckedChange = { onCheckedChange() },
                        enabled = isEditingEnabled,
                        colors = CheckboxDefaults.colors(
                            checkedColor = NavyDeep
                        )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (entry.isChecked) PureBlack.copy(alpha = 0.6f) else PureBlack,
                            textDecoration = if (entry.isChecked) TextDecoration.LineThrough else null
                        )
                        // Macro row (P/C/F) - engineering style
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
                    }
                }
                // Edit/Delete buttons for unplanned meals
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
                // Status badge
                StatusBadge(status = entry.status)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Target Amount (monospace)
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
            
            // Multiplier Slider (0.0 to 3.0)
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
                    steps = 29, // 0.1 increments
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
            
            // Kcal info
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
    showDietActions: Boolean,
    onDateSelected: (String) -> Unit,
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
    
    val selectedDateState = remember { mutableStateOf(selectedDateObj) }
    
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
                onClick = {
                    selectedDateState.value = selectedDateObj
                    showDatePicker = true
                },
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
            
            if (showDietActions && isEditingEnabled) {
                IconButton(
                    onClick = onAddMealClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add meal",
                        tint = NavyDeep,
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (isPastDay()) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = if (isEditingEnabled) "Freeze editing" else "Enable editing",
                            tint = if (isEditingEnabled) NavyDeep else PureBlack.copy(alpha = 0.6f),
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
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateState.value.time
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
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selected = Date(millis)
                            onDateSelected(dateFormatter.format(selected))
                            showDatePicker = false
                        }
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
    val formatter = DecimalFormat("#.##")
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
                    
                    // Slider
                    @OptIn(ExperimentalMaterial3Api::class)
                    Slider(
                        value = sliderValue,
                        onValueChange = { 
                            // When slider moves, set currentValue (triggers automatic status)
                            onValueChange(it.toDouble())
                        },
                        enabled = isEditingEnabled && entry.status != RoutineStatus.NA,
                        valueRange = (entry.minValue ?: 0.0).toFloat()..(entry.maxValue ?: 1.0).toFloat(),
                        steps = if (entry.maxValue != null && entry.minValue != null) {
                            ((entry.maxValue!! - entry.minValue!!) * 10).toInt().coerceAtMost(100)
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
