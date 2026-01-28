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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitnessCenter
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vm.core.models.AuditStatus
import com.vm.core.models.DietEntry
import com.vm.core.models.RoutineEntry
import com.vm.core.models.RoutineStatus
import com.vm.core.models.RoutineType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.VectorTheme
import com.vm.vector.ui.viewmodel.CalendarViewModel
import com.vm.vector.ui.viewmodel.CalendarViewModelFactory
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
    viewModel: CalendarViewModel = viewModel(
        factory = CalendarViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

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
                var selectedCategory by remember { mutableStateOf(CalendarCategory.Diet) }
                var showAddMealDialog by remember { mutableStateOf(false) }
                var showAddRoutineDialog by remember { mutableStateOf(false) }

                CategorySwitch(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                )

                WeekScroller(
                    selectedDate = uiState.currentDate,
                    datesWithCheckedMeals = uiState.datesWithCheckedMeals,
                    isEditingEnabled = uiState.isEditingEnabled,
                    showDietActions = selectedCategory == CalendarCategory.Diet || selectedCategory == CalendarCategory.Routine,
                    onDateSelected = { date ->
                        viewModel.selectDate(date)
                    },
                    onAddMealClick = {
                        if (uiState.isEditingEnabled) {
                            when (selectedCategory) {
                                CalendarCategory.Diet -> showAddMealDialog = true
                                CalendarCategory.Routine -> showAddRoutineDialog = true
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
