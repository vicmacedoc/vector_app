package com.vm.vector.ui.screens.calendar

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.vm.core.models.MealLibraryItem
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.CalendarAiMode
import com.vm.vector.data.CalendarDayContext
import com.vm.vector.data.GeminiNutritionClient
import com.vm.vector.data.MealImportDraft
import com.vm.vector.data.normalizeMealTimeOrBlank
import com.vm.vector.data.validationError
import com.vm.vector.ui.components.DialogMultilineFieldDefaults
import com.vm.vector.ui.components.DiscardImportReviewDialog
import com.vm.vector.ui.components.ImportReviewFooter
import com.vm.vector.ui.components.KeyboardAwareDialogColumn
import com.vm.vector.ui.screens.MealPlanTypeSwitch
import com.vm.vector.util.applyExifOrientationToJpegBytes
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun phoneClockHhMm(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

@Composable
private fun foodFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NavyDeep,
    unfocusedBorderColor = NavyDeep,
    focusedTextColor = PureBlack,
    unfocusedTextColor = PureBlack,
    focusedContainerColor = PureWhite,
    unfocusedContainerColor = PureWhite,
)

/**
 * Text and/or photo → Gemini nutrition estimate. [CalendarAiMode.SINGLE] returns one row; the other
 * modes return one row per food / ingredient. Plan modes ground the prompt in [dayContextProvider].
 */
@Composable
fun FillMealFromAiDialog(
    mode: CalendarAiMode,
    date: String,
    onDismiss: () -> Unit,
    onEstimates: (List<GeminiNutritionClient.MealEstimate>) -> Unit,
    dayContextProvider: suspend () -> CalendarDayContext = { CalendarDayContext() },
    /** Editing an existing meal: its time is kept, so the copy says so. */
    keepsExistingTime: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(context) { GeminiNutritionClient(context.applicationContext) }

    var description by remember { mutableStateOf("") }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var imagePreview by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val inputOptional = mode == CalendarAiMode.PLAN_PRESET

    fun setImageFromBytes(bytes: ByteArray) {
        imageBytes = bytes
        imagePreview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (!success || uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (raw == null) {
                    errorMessage = "Failed to read captured photo"
                    return@launch
                }
                setImageFromBytes(applyExifOrientationToJpegBytes(raw) ?: raw)
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load photo"
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCaptureUri?.let { takePictureLauncher.launch(it) }
        } else {
            pendingCaptureUri = null
            errorMessage = "Camera permission denied"
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes == null) {
                    errorMessage = "Failed to read selected image"
                    return@launch
                }
                setImageFromBytes(applyExifOrientationToJpegBytes(bytes) ?: bytes)
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load image"
            }
        }
    }

    fun startCameraCapture() {
        val file = File(context.cacheDir, "meal_ai_${System.currentTimeMillis()}.jpg")
        file.createNewFile()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCaptureUri = uri
        when {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> takePictureLauncher.launch(uri)
            else -> cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    fun sendToAi() {
        if (isLoading) return
        if (description.isBlank() && imageBytes == null && !inputOptional) {
            errorMessage = "Add a description and/or a photo"
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            val dayContext = runCatching { dayContextProvider() }.getOrDefault(CalendarDayContext())
            val result = client.estimateMeals(
                mode = mode,
                description = description.takeIf { it.isNotBlank() },
                imageBytes = imageBytes,
                date = date,
                dayContext = dayContext,
            )
            isLoading = false
            result.onSuccess { estimates ->
                if (estimates.isEmpty()) {
                    errorMessage = "AI returned no food rows"
                } else {
                    onEstimates(estimates)
                }
            }.onFailure { e ->
                errorMessage = e.message ?: "AI estimate failed"
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        modifier = Modifier.imePadding(),
        title = { Text(calendarAiDialogTitle(mode), color = PureBlack) },
        text = {
            KeyboardAwareDialogColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = when (mode) {
                        CalendarAiMode.SINGLE -> if (keepsExistingTime) {
                            "Describe and/or photograph the meal. Existing time is kept; other fields are filled for review."
                        } else {
                            "Describe and/or photograph one meal. Review the fields before adding."
                        }
                        CalendarAiMode.MULTIPLE ->
                            "Describe and/or photograph what you ate. Each food becomes its own row for review."
                        CalendarAiMode.PLAN_SCRATCH ->
                            "Describe the day's eating plan (a photo of a written plan works too). " +
                                "The result replaces every food row on $date."
                        CalendarAiMode.PLAN_PRESET ->
                            "Starts from this weekday's preset and aims for its macro targets. Describe what to " +
                                "change, or leave it blank to keep the preset. The result replaces every food row on $date."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateGray,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { startCameraCapture() },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyDeep),
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Camera")
                    }
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyDeep),
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload")
                    }
                }

                imagePreview?.let { bmp ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(OffWhite),
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Selected meal photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        IconButton(
                            onClick = {
                                imageBytes = null
                                imagePreview = null
                            },
                            modifier = Modifier.align(Alignment.TopEnd),
                            enabled = !isLoading,
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = PureWhite)
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = {
                        Text(
                            if (inputOptional) "Changes (optional)" else "Description (optional with photo)",
                            color = PureBlack,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = DialogMultilineFieldDefaults.MediumMin,
                            max = DialogMultilineFieldDefaults.MediumMax,
                        ),
                    minLines = 3,
                    enabled = !isLoading,
                    colors = foodFieldColors(),
                )

                errorMessage?.let { msg ->
                    Text(msg, color = DeleteRed, style = MaterialTheme.typography.bodySmall)
                }

                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = NavyDeep,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Estimating nutrition…", color = SlateGray)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { sendToAi() },
                enabled = !isLoading && (inputOptional || description.isNotBlank() || imageBytes != null),
            ) {
                Text("Send", color = NavyDeep)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel", color = PureBlack)
            }
        },
        containerColor = PureWhite,
    )
}

private data class MealRowForm(
    val name: String,
    val time: String,
    val amount: String,
    val unit: String,
    val kcal: String,
    val protein: String,
    val carbs: String,
    val fats: String,
    val asPlanned: Boolean,
    val included: Boolean = true,
    val expanded: Boolean = false,
)

private fun Double.asField(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()

private fun MealImportDraft.toRowForm(fallbackTime: String) = MealRowForm(
    name = name,
    time = normalizeMealTimeOrBlank(time).ifBlank { fallbackTime },
    amount = amount.takeIf { it > 0 }?.asField().orEmpty(),
    unit = unit.ifBlank { "g" },
    kcal = kcal.asField(),
    protein = protein.asField(),
    carbs = carbs.asField(),
    fats = fats.asField(),
    asPlanned = asPlanned,
)

private fun MealRowForm.toDraftOrError(): Pair<MealImportDraft?, String?> {
    val amountValue = amount.trim().toDoubleOrNull() ?: return null to "Amount must be a number"
    val kcalValue = kcal.trim().toDoubleOrNull() ?: return null to "Calories must be a number"
    val p = protein.trim().ifBlank { "0" }.toDoubleOrNull() ?: return null to "Protein must be a number"
    val c = carbs.trim().ifBlank { "0" }.toDoubleOrNull() ?: return null to "Carbs must be a number"
    val f = fats.trim().ifBlank { "0" }.toDoubleOrNull() ?: return null to "Fats must be a number"
    val draft = MealImportDraft(
        name = name.trim(),
        time = normalizeMealTimeOrBlank(time),
        amount = amountValue,
        unit = unit.trim().ifBlank { "g" },
        kcal = kcalValue,
        protein = p,
        carbs = c,
        fats = f,
        asPlanned = asPlanned,
    )
    return draft.validationError()?.let { null to it } ?: (draft to null)
}

/**
 * Editable list of food rows from the clipboard or AI. Rows can be excluded, edited and switched
 * between New and Planned. With [replacesDay] the confirmed rows replace the whole Food day.
 */
@Composable
fun MealImportReviewDialog(
    initialDrafts: List<MealImportDraft>,
    date: String,
    sourceLabel: String,
    replacesDay: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<MealImportDraft>) -> Unit,
) {
    val fallbackTime = remember { phoneClockHhMm() }
    var rows by remember {
        mutableStateOf(
            initialDrafts.map { it.toRowForm(fallbackTime) }.let { list ->
                if (list.size == 1) list.map { it.copy(expanded = true) } else list
            }
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val includedCount = rows.count { it.included }
    val allPlanned = rows.filter { it.included }.let { inc -> inc.isNotEmpty() && inc.all { it.asPlanned } }

    fun update(index: Int, transform: (MealRowForm) -> MealRowForm) {
        rows = rows.toMutableList().also { it[index] = transform(it[index]) }
        errorMessage = null
    }

    fun confirm() {
        val drafts = mutableListOf<MealImportDraft>()
        rows.forEachIndexed { index, row ->
            if (!row.included) return@forEachIndexed
            val (draft, error) = row.toDraftOrError()
            if (error != null) {
                update(index) { it.copy(expanded = true) }
                errorMessage = "Row ${index + 1}${row.name.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}: $error"
                return
            }
            drafts += draft!!
        }
        if (drafts.isEmpty()) {
            errorMessage = "Keep at least one row, or cancel"
            return
        }
        onConfirm(drafts)
    }

    Dialog(onDismissRequest = { showDiscardConfirm = true }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .imePadding()
                .heightIn(max = 640.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Review food", style = MaterialTheme.typography.titleMedium, color = PureBlack)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$sourceLabel · ${rows.size} rows · $date",
                    color = SlateGray,
                    fontSize = 13.sp,
                )
                if (replacesDay) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ReplacesDayBanner("Confirming replaces every food row on this day, preset items included.")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Type for all included rows", color = PureBlack, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                MealPlanTypeSwitch(
                    asPlanned = allPlanned,
                    onAsPlannedChange = { planned ->
                        rows = rows.map { if (it.included) it.copy(asPlanned = planned) else it }
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rows.forEachIndexed { index, row ->
                        MealReviewRow(
                            row = row,
                            onChange = { changed -> update(index) { changed } },
                        )
                    }
                }
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = DeleteRed, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                ImportReviewFooter(
                    primaryLabel = if (replacesDay) "Replace day ($includedCount)" else "Add ($includedCount)",
                    onPrimary = { confirm() },
                    onCancel = { showDiscardConfirm = true },
                    primaryEnabled = includedCount > 0,
                )
            }
        }
    }

    if (showDiscardConfirm) {
        DiscardImportReviewDialog(
            sourceLabel = sourceLabel,
            onKeepReviewing = { showDiscardConfirm = false },
            onDiscard = {
                showDiscardConfirm = false
                onDismiss()
            },
        )
    }
}

@Composable
private fun MealReviewRow(
    row: MealRowForm,
    onChange: (MealRowForm) -> Unit,
) {
    val fieldColors = foodFieldColors()
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (row.included) OffWhite else OffWhite.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = row.included,
                    onCheckedChange = { onChange(row.copy(included = it)) },
                    colors = CheckboxDefaults.colors(checkedColor = NavyDeep, checkmarkColor = PureWhite),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onChange(row.copy(expanded = !row.expanded)) },
                ) {
                    Text(
                        row.name.ifBlank { "Unnamed" },
                        color = if (row.included) PureBlack else SlateGray,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${row.time} · ${row.amount} ${row.unit} · ${row.kcal} kcal · " +
                            if (row.asPlanned) "Planned" else "New",
                        color = SlateGray,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = { onChange(row.copy(expanded = !row.expanded)) }) {
                    Icon(
                        if (row.expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (row.expanded) "Collapse" else "Edit",
                        tint = NavyDeep,
                    )
                }
            }
            if (row.expanded && row.included) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = row.name,
                        onValueChange = { onChange(row.copy(name = it)) },
                        label = { Text("Name", color = PureBlack) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                    )
                    MealPlanTypeSwitch(
                        asPlanned = row.asPlanned,
                        onAsPlannedChange = { onChange(row.copy(asPlanned = it)) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = row.time,
                            onValueChange = { onChange(row.copy(time = it)) },
                            label = { Text("Time", color = PureBlack) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors,
                        )
                        OutlinedTextField(
                            value = row.amount,
                            onValueChange = { onChange(row.copy(amount = it)) },
                            label = { Text("Amount", color = PureBlack) },
                            singleLine = true,
                            keyboardOptions = numberKeyboard,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors,
                        )
                        OutlinedTextField(
                            value = row.unit,
                            onValueChange = { onChange(row.copy(unit = it)) },
                            label = { Text("Unit", color = PureBlack) },
                            singleLine = true,
                            modifier = Modifier.weight(0.8f),
                            colors = fieldColors,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = row.kcal,
                            onValueChange = { onChange(row.copy(kcal = it)) },
                            label = { Text("kcal", color = PureBlack) },
                            singleLine = true,
                            keyboardOptions = numberKeyboard,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors,
                        )
                        OutlinedTextField(
                            value = row.protein,
                            onValueChange = { onChange(row.copy(protein = it)) },
                            label = { Text("P", color = PureBlack) },
                            singleLine = true,
                            keyboardOptions = numberKeyboard,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors,
                        )
                        OutlinedTextField(
                            value = row.carbs,
                            onValueChange = { onChange(row.copy(carbs = it)) },
                            label = { Text("C", color = PureBlack) },
                            singleLine = true,
                            keyboardOptions = numberKeyboard,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors,
                        )
                        OutlinedTextField(
                            value = row.fats,
                            onValueChange = { onChange(row.copy(fats = it)) },
                            label = { Text("F", color = PureBlack) },
                            singleLine = true,
                            keyboardOptions = numberKeyboard,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors,
                        )
                    }
                }
            }
        }
    }
}

/** Search saved meals; picking one opens the meal form prefilled. */
@Composable
fun MealLibraryPickerDialog(
    meals: List<MealLibraryItem>,
    onDismiss: () -> Unit,
    onPick: (MealLibraryItem) -> Unit,
    onDelete: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<MealLibraryItem?>(null) }
    val filtered = remember(meals, query) {
        if (query.isBlank()) meals else meals.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        containerColor = PureWhite,
        title = { Text("Meal library", color = PureBlack) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search meals", color = SlateGray) },
                    singleLine = true,
                    colors = foodFieldColors(),
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when {
                        meals.isEmpty() -> LibraryEmptyText("No saved meals yet. Meals you add are saved here.")
                        filtered.isEmpty() -> LibraryEmptyText("No meals match \"$query\".")
                        else -> filtered.forEach { meal ->
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
                                            .clickable { onPick(meal) }
                                            .padding(vertical = 10.dp),
                                    ) {
                                        Text(meal.name, color = PureBlack)
                                        Text(
                                            "${meal.amount.asField()} ${meal.unit} · ${meal.kcal.toInt()} kcal · " +
                                                "P ${meal.protein.asField()} · C ${meal.carbs.asField()} · F ${meal.fats.asField()}",
                                            color = SlateGray,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    IconButton(onClick = { pendingDelete = meal }) {
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

    pendingDelete?.let { meal ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = PureWhite,
            title = { Text("Remove from library?", color = PureBlack) },
            text = { Text("Delete \"${meal.name}\" from saved meals?", color = PureBlack) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(meal.id)
                    pendingDelete = null
                }) { Text("Delete", color = DeleteRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = PureBlack) }
            },
        )
    }
}
