package com.vm.vector.ui.screens.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.core.models.ActivityLibraryItem
import com.vm.core.models.ExerciseLibraryItem
import com.vm.core.models.WorkoutDaySession
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.ActivityImportDraft
import com.vm.vector.data.CalendarAiMode
import com.vm.vector.data.ExerciseImportDefaults
import com.vm.vector.data.ExerciseImportDraft
import com.vm.vector.data.ExerciseSessionDraft
import com.vm.vector.data.MealImportDraft
import com.vm.vector.data.parseActivityClipboardText
import com.vm.vector.data.parseMealClipboardText
import com.vm.vector.data.toExerciseSessionDraft
import com.vm.vector.data.toImportDraft
import com.vm.vector.ui.screens.AddMealDialog
import com.vm.vector.ui.screens.CalendarCategory
import com.vm.vector.ui.screens.exercise.ExerciseImportWizardDialog
import com.vm.vector.ui.screens.exercise.FillExerciseFromAiDialog
import com.vm.vector.ui.screens.routine.ActivityEditDialog
import com.vm.vector.ui.screens.routine.ActivityImportWizardDialog
import com.vm.vector.ui.screens.routine.FillActivityFromAiDialog
import com.vm.vector.ui.screens.routine.ImportActivitiesFromGoogleCalendarGate
import com.vm.vector.ui.viewmodel.CalendarUiState
import com.vm.vector.ui.viewmodel.CalendarViewModel

private sealed interface AddFlowStep {
    data object Menu : AddFlowStep
    data class Ai(val mode: CalendarAiMode) : AddFlowStep
    data object Library : AddFlowStep
    data object GoogleCalendar : AddFlowStep
    data class ActivityForm(val prefill: ActivityImportDraft?) : AddFlowStep
    data class ActivityReview(val drafts: List<ActivityImportDraft>, val replacesDay: Boolean) : AddFlowStep
    data class MealForm(val prefill: MealImportDraft?) : AddFlowStep
    data class MealReview(
        val drafts: List<MealImportDraft>,
        val sourceLabel: String,
        val replacesDay: Boolean,
    ) : AddFlowStep
    data class ExerciseReview(
        val sessions: List<ExerciseSessionDraft>,
        val sourceLabel: String,
        val replacesDay: Boolean,
    ) : AddFlowStep
    data class Error(val message: String) : AddFlowStep
}

/**
 * Everything behind the Calendar edit FAB for one category: the menu sheet, then the clipboard /
 * AI / library / form / Google Calendar path, then the review step. Calls [onClose] when finished.
 * Single-item paths open the regular form (one exercise session for Exercise); multi-item and plan
 * paths open a review dialog, and plan paths replace the day.
 */
@Composable
fun CalendarAddFlowHost(
    category: CalendarCategory,
    viewModel: CalendarViewModel,
    uiState: CalendarUiState,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var step by remember(category) { mutableStateOf<AddFlowStep>(AddFlowStep.Menu) }
    var muscleVocabulary by remember { mutableStateOf(ExerciseImportDefaults.MUSCLES) }
    val date = uiState.currentDate

    LaunchedEffect(category) {
        if (category == CalendarCategory.Exercise) {
            muscleVocabulary = runCatching { viewModel.exerciseAiContext().muscleVocabulary }
                .getOrDefault(ExerciseImportDefaults.MUSCLES)
        }
    }

    fun fail(error: Throwable) {
        step = AddFlowStep.Error(error.message ?: "Couldn't read the clipboard")
    }

    fun handleClipboard(mode: CalendarAiMode) {
        val text = readClipboardText(context)
        when (category) {
            CalendarCategory.Routine -> parseActivityClipboardText(text, mode).map { items ->
                items.map { it.toImportDraft() }
            }.fold(
                onSuccess = { drafts ->
                    step = if (mode == CalendarAiMode.SINGLE) {
                        AddFlowStep.ActivityForm(drafts.first())
                    } else {
                        AddFlowStep.ActivityReview(drafts, replacesDay = false)
                    }
                },
                onFailure = ::fail,
            )
            CalendarCategory.Food -> parseMealClipboardText(text, mode).fold(
                onSuccess = { drafts ->
                    step = if (mode == CalendarAiMode.SINGLE) {
                        AddFlowStep.MealForm(drafts.first())
                    } else {
                        AddFlowStep.MealReview(drafts, "Clipboard", replacesDay = false)
                    }
                },
                onFailure = ::fail,
            )
            CalendarCategory.Exercise -> viewModel.parseClipboardWorkoutSessions(text, mode).fold(
                onSuccess = { sessions ->
                    step = AddFlowStep.ExerciseReview(
                        sessions.map { it.toExerciseSessionDraft() },
                        "Clipboard",
                        replacesDay = false,
                    )
                },
                onFailure = ::fail,
            )
            CalendarCategory.Self -> onClose()
        }
    }

    fun handleAction(action: CalendarAddAction) {
        when (action) {
            CalendarAddAction.SINGLE_CLIPBOARD -> handleClipboard(CalendarAiMode.SINGLE)
            CalendarAddAction.MULTI_CLIPBOARD -> handleClipboard(CalendarAiMode.MULTIPLE)
            CalendarAddAction.SINGLE_AI -> step = AddFlowStep.Ai(CalendarAiMode.SINGLE)
            CalendarAddAction.MULTI_AI -> step = AddFlowStep.Ai(CalendarAiMode.MULTIPLE)
            CalendarAddAction.PLAN_SCRATCH -> step = AddFlowStep.Ai(CalendarAiMode.PLAN_SCRATCH)
            CalendarAddAction.PLAN_PRESET -> step = AddFlowStep.Ai(CalendarAiMode.PLAN_PRESET)
            CalendarAddAction.SINGLE_LIBRARY -> step = AddFlowStep.Library
            CalendarAddAction.MULTI_GOOGLE_CALENDAR -> step = AddFlowStep.GoogleCalendar
            CalendarAddAction.SINGLE_NEW -> step = when (category) {
                CalendarCategory.Routine -> AddFlowStep.ActivityForm(null)
                CalendarCategory.Food -> AddFlowStep.MealForm(null)
                else -> AddFlowStep.ExerciseReview(
                    listOf(ExerciseSessionDraft(exercises = listOf(ExerciseImportDraft()))),
                    "New",
                    replacesDay = false,
                )
            }
        }
    }

    when (val current = step) {
        AddFlowStep.Menu -> CalendarAddMenuSheet(
            itemNoun = when (category) {
                CalendarCategory.Routine -> "activity"
                CalendarCategory.Food -> "meal"
                else -> "session"
            },
            itemNounPlural = when (category) {
                CalendarCategory.Routine -> "activities"
                CalendarCategory.Food -> "meals"
                else -> "sessions"
            },
            showGoogleCalendar = category == CalendarCategory.Routine,
            presetAvailable = when (category) {
                CalendarCategory.Routine -> !uiState.noPresetForRoutine
                CalendarCategory.Food -> !uiState.noPresetForFood
                else -> !uiState.noPresetForWorkout
            },
            onDismiss = onClose,
            onAction = ::handleAction,
        )

        is AddFlowStep.Ai -> when (category) {
            CalendarCategory.Routine -> FillActivityFromAiDialog(
                date = date,
                labelNames = uiState.activityLabels.map { it.name },
                mode = current.mode,
                onDismiss = onClose,
                onDraftsReady = { drafts ->
                    step = if (current.mode == CalendarAiMode.SINGLE) {
                        AddFlowStep.ActivityForm(drafts.first())
                    } else {
                        AddFlowStep.ActivityReview(drafts, replacesDay = current.mode.isPlan)
                    }
                },
                dayContextProvider = { viewModel.routineDayContext() },
            )
            CalendarCategory.Food -> FillMealFromAiDialog(
                mode = current.mode,
                date = date,
                onDismiss = onClose,
                onEstimates = { estimates ->
                    step = if (current.mode == CalendarAiMode.SINGLE) {
                        AddFlowStep.MealForm(estimates.first().toImportDraft(asPlanned = false))
                    } else {
                        AddFlowStep.MealReview(
                            estimates.map { it.toImportDraft(asPlanned = current.mode.isPlan) },
                            "AI",
                            replacesDay = current.mode.isPlan,
                        )
                    }
                },
                dayContextProvider = { viewModel.foodDayContext() },
            )
            else -> FillExerciseFromAiDialog(
                date = date,
                contextProvider = { viewModel.exerciseAiContext() },
                mode = current.mode,
                onDismiss = onClose,
                onDraftsReady = { drafts, aiContext ->
                    muscleVocabulary = aiContext.muscleVocabulary
                    step = AddFlowStep.ExerciseReview(drafts, "AI", replacesDay = current.mode.isPlan)
                },
                dayContextProvider = { viewModel.exerciseDayContext() },
            )
        }

        AddFlowStep.Library -> when (category) {
            CalendarCategory.Routine -> ActivityLibraryPickerDialog(
                items = uiState.activityLibrary,
                onDismiss = onClose,
                onPick = { item -> step = AddFlowStep.ActivityForm(item.toImportDraft()) },
                onDelete = viewModel::deleteActivityLibraryItem,
            )
            CalendarCategory.Food -> MealLibraryPickerDialog(
                meals = uiState.mealLibrary,
                onDismiss = onClose,
                onPick = { meal ->
                    step = AddFlowStep.MealForm(
                        MealImportDraft(
                            name = meal.name,
                            amount = meal.amount,
                            unit = meal.unit,
                            kcal = meal.kcal,
                            protein = meal.protein,
                            carbs = meal.carbs,
                            fats = meal.fats,
                        )
                    )
                },
                onDelete = viewModel::deleteMealLibraryItem,
            )
            else -> ExerciseLibraryPickerDialog(
                items = uiState.exerciseLibrary,
                decode = viewModel::decodeExerciseLibraryItem,
                onDismiss = onClose,
                onPick = { session ->
                    step = AddFlowStep.ExerciseReview(
                        listOf(session.toExerciseSessionDraft()),
                        "Library",
                        replacesDay = false,
                    )
                },
                onDelete = viewModel::deleteExerciseLibraryItem,
            )
        }

        AddFlowStep.GoogleCalendar -> ImportActivitiesFromGoogleCalendarGate(
            date = date,
            onDismiss = onClose,
            onDraftsReady = { drafts -> step = AddFlowStep.ActivityReview(drafts, replacesDay = false) },
        )

        is AddFlowStep.ActivityForm -> ActivityEditDialog(
            entry = null,
            labels = uiState.activityLabels,
            date = date,
            onDismiss = onClose,
            onSave = { entry ->
                viewModel.addActivity(entry)
                onClose()
            },
            onDelete = null,
            prefill = current.prefill,
        )

        is AddFlowStep.ActivityReview -> ActivityImportWizardDialog(
            initialDrafts = current.drafts,
            labels = uiState.activityLabels,
            date = date,
            onDismiss = onClose,
            onConfirmAll = { entries ->
                if (current.replacesDay) {
                    viewModel.replaceDayActivities(entries)
                } else {
                    viewModel.addActivities(entries)
                }
                onClose()
            },
            allowExclude = true,
            replacesDay = current.replacesDay,
        )

        is AddFlowStep.MealForm -> AddMealDialog(
            prefill = current.prefill,
            onDismiss = onClose,
            onConfirm = { name, time, amount, unit, kcal, protein, carbs, fats, asPlanned ->
                viewModel.addManualEntry(name, time, amount, unit, kcal, protein, carbs, fats, asPlanned)
                onClose()
            },
        )

        is AddFlowStep.MealReview -> MealImportReviewDialog(
            initialDrafts = current.drafts,
            date = date,
            sourceLabel = current.sourceLabel,
            replacesDay = current.replacesDay,
            onDismiss = onClose,
            onConfirm = { drafts ->
                if (current.replacesDay) viewModel.replaceDayFood(drafts) else viewModel.addFoodEntries(drafts)
                onClose()
            },
        )

        is AddFlowStep.ExerciseReview -> ExerciseImportWizardDialog(
            initialSessions = current.sessions,
            date = date,
            muscleVocabulary = muscleVocabulary,
            onDismiss = onClose,
            onConfirmAll = { sessions ->
                val result = if (current.replacesDay) {
                    viewModel.replaceDayWorkout(sessions)
                } else {
                    viewModel.addWorkoutSessions(sessions)
                }
                result.fold(onSuccess = { onClose() }, onFailure = ::fail)
            },
            sourceLabel = current.sourceLabel,
            replacesDay = current.replacesDay,
        )

        is AddFlowStep.Error -> AlertDialog(
            onDismissRequest = onClose,
            containerColor = PureWhite,
            title = { Text("Can't add that", color = PureBlack) },
            text = { Text(current.message, color = PureBlack) },
            confirmButton = {
                TextButton(onClick = { step = AddFlowStep.Menu }) { Text("Back to menu", color = NavyDeep) }
            },
            dismissButton = {
                TextButton(onClick = onClose) { Text("Close", color = PureBlack) }
            },
        )
    }
}

@Composable
private fun pickerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NavyDeep,
    unfocusedBorderColor = NavyDeep.copy(alpha = 0.6f),
    focusedTextColor = PureBlack,
    unfocusedTextColor = PureBlack,
)

/** Saved single activities (weekly preset items and confirmed new entries) with search. */
@Composable
private fun ActivityLibraryPickerDialog(
    items: List<ActivityLibraryItem>,
    onDismiss: () -> Unit,
    onPick: (ActivityLibraryItem) -> Unit,
    onDelete: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ActivityLibraryItem?>(null) }
    val filtered = remember(items, query) {
        val needle = query.trim()
        if (needle.isBlank()) items
        else items.filter {
            it.title.contains(needle, ignoreCase = true) || it.labelName.contains(needle, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        containerColor = PureWhite,
        title = { Text("Activity library", color = PureBlack) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search title or label", color = SlateGray) },
                    singleLine = true,
                    colors = pickerFieldColors(),
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when {
                        items.isEmpty() -> LibraryEmptyText(
                            "No saved activities yet. Weekly preset items and activities you add are saved here."
                        )
                        filtered.isEmpty() -> LibraryEmptyText("No activities match \"$query\".")
                        else -> filtered.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = OffWhite,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onPick(item) }
                                            .padding(vertical = 10.dp),
                                    ) {
                                        Text(item.title, color = PureBlack)
                                        Text(
                                            "${item.startTime}–${item.endTime}" +
                                                item.labelName.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                            color = SlateGray,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    IconButton(onClick = { pendingDelete = item }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DeleteRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = PureWhite,
            title = { Text("Remove from library?", color = PureBlack) },
            text = { Text("Delete \"${item.title}\" from saved activities?", color = PureBlack) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(item.id)
                    pendingDelete = null
                }) { Text("Delete", color = DeleteRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = PureBlack) }
            },
        )
    }
}


/** Saved exercise sessions with search; picking one opens the review wizard for that session. */
@Composable
private fun ExerciseLibraryPickerDialog(
    items: List<ExerciseLibraryItem>,
    decode: (ExerciseLibraryItem) -> WorkoutDaySession?,
    onDismiss: () -> Unit,
    onPick: (WorkoutDaySession) -> Unit,
    onDelete: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ExerciseLibraryItem?>(null) }
    val filtered = remember(items, query) {
        val needle = query.trim()
        if (needle.isBlank()) items
        else items.filter { it.name.contains(needle, ignoreCase = true) || it.type.contains(needle, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        containerColor = PureWhite,
        title = { Text("Exercise library", color = PureBlack) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search sessions", color = SlateGray) },
                    singleLine = true,
                    colors = pickerFieldColors(),
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when {
                        items.isEmpty() -> LibraryEmptyText("No saved sessions yet. Sessions you add are saved here.")
                        filtered.isEmpty() -> LibraryEmptyText("No sessions match \"$query\".")
                        else -> filtered.forEach { item ->
                            val session = remember(item.id, item.payloadJson) { decode(item) }
                            val expanded = expandedId == item.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = OffWhite,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(start = 10.dp, bottom = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${item.type}: ${item.name}",
                                            color = if (session != null) PureBlack else SlateGray,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(enabled = session != null) { session?.let(onPick) }
                                                .padding(vertical = 10.dp),
                                        )
                                        IconButton(onClick = { expandedId = if (expanded) null else item.id }) {
                                            Icon(
                                                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (expanded) "Collapse" else "Expand",
                                                tint = NavyDeep,
                                            )
                                        }
                                        IconButton(onClick = { pendingDelete = item }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DeleteRed)
                                        }
                                    }
                                    if (expanded) {
                                        val desc = item.description.ifBlank { session?.description.orEmpty() }
                                        if (desc.isNotBlank()) Text(desc, color = SlateGray, fontSize = 12.sp)
                                        Text(
                                            session?.exercises?.joinToString(" · ") { it.name }
                                                ?.ifBlank { null }
                                                ?: "${session?.exercises?.size ?: 0} exercises",
                                            color = SlateGray,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = PureWhite,
            title = { Text("Remove from library?", color = PureBlack) },
            text = { Text("Delete \"${item.type}: ${item.name}\" from the library?", color = PureBlack) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(item.id)
                    pendingDelete = null
                }) { Text("Delete", color = DeleteRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = PureBlack) }
            },
        )
    }
}
