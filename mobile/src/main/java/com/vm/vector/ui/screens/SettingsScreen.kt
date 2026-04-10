package com.vm.vector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import com.vm.vector.data.PresetCategory
import com.vm.vector.ui.viewmodel.SleepPickerKind
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.core.ui.theme.VectorTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.HorizontalDivider
import com.vm.vector.ui.viewmodel.SettingsViewModel
import com.vm.vector.ui.viewmodel.SettingsViewModelFactory

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingPresetCategory by remember { mutableStateOf<PresetCategory?>(null) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val category = pendingPresetCategory
        pendingPresetCategory = null
        if (uri != null && category != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""
                val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else "preset.json"
                    } else "preset.json"
                } ?: "preset.json"
                viewModel.onPresetFilePicked(category, content, displayName)
            } catch (e: Exception) {
                viewModel.onPresetFilePicked(category, "", "preset.json") // will show validation error
            }
        }
    }

    fun launchPresetPicker(category: PresetCategory) {
        pendingPresetCategory = category
        openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
    }

    LaunchedEffect(uiState.showSnackbar) {
        if (uiState.showSnackbar) {
            snackbarHostState.showSnackbar(uiState.snackbarMessage)
            viewModel.dismissSnackbar()
        }
    }

    VectorTheme {
        Scaffold(
            containerColor = PureWhite,
            snackbarHost = { SnackbarHost(snackbarHostState) }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ——— Sleep Settings ———
                    SettingsSectionTitle("Sleep Settings", isFirstSection = true)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OffWhite),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Wake-up", style = MaterialTheme.typography.bodyMedium, color = SlateGray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TimeChip(
                                        minutes = uiState.sleepWakeupMinutes,
                                        onClick = { viewModel.showSleepTimePicker(SleepPickerKind.Wakeup) }
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Bedtime", style = MaterialTheme.typography.bodyMedium, color = SlateGray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TimeChip(
                                        minutes = uiState.sleepBedtimeMinutes,
                                        onClick = { viewModel.showSleepTimePicker(SleepPickerKind.Bedtime) }
                                    )
                                }
                            }
                            Button(
                                onClick = { viewModel.setPreferences() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                            ) {
                                Text("Set Preferences", color = PureWhite)
                            }
                            val base = uiState.wakeAlarmBaseMinutes
                            val offsets = uiState.wakeAlarmOffsets
                            val hasAlarms = base != null && offsets.isNotEmpty()
                            if (hasAlarms) {
                                var alarmsExpanded by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { alarmsExpanded = !alarmsExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "Alarms",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = PureBlack,
                                    )
                                    Icon(
                                        imageVector = if (alarmsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (alarmsExpanded) "Collapse" else "Expand",
                                        tint = SlateGray,
                                    )
                                }
                                if (alarmsExpanded) {
                                    offsets.sorted().forEach { offset ->
                                        val totalMin = (base + offset) % (24 * 60)
                                        val label = if (offset == 0) "Wake-up" else "+${offset} min"
                                        AlarmReminderTile(
                                            label = label,
                                            timeText = "%02d:%02d".format(totalMin / 60, totalMin % 60),
                                            onRemove = { viewModel.removeWakeAlarm(offset) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ——— Notification Settings ———
                    SettingsSectionTitle("Notification Settings")
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OffWhite),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { viewModel.showFilloutTimePicker(true) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                            ) {
                                Text("Fill-out Reminder", color = PureWhite)
                            }
                            uiState.filloutReminderMinutes?.let { minutes ->
                                val totalMin = (minutes % (24 * 60) + 24 * 60) % (24 * 60)
                                AlarmReminderTile(
                                    label = "Fill-out reminder",
                                    timeText = "%02d:%02d".format(totalMin / 60, totalMin % 60),
                                    onRemove = { viewModel.removeFilloutReminder() }
                                )
                            }
                        }
                    }

                    // ——— Preset Settings ———
                    SettingsSectionTitle("Preset Settings")
                    PresetTile(
                        label = "Workout",
                        activeFileName = uiState.workoutPresetName,
                        presetJsonName = uiState.workoutPresetJsonName,
                        onBrowse = { launchPresetPicker(PresetCategory.Workout) },
                        onRemove = { viewModel.removePreset(PresetCategory.Workout) },
                    )
                    PresetTile(
                        label = "Routine",
                        activeFileName = uiState.routinePresetName,
                        presetJsonName = uiState.routinePresetJsonName,
                        onBrowse = { launchPresetPicker(PresetCategory.Routine) },
                        onRemove = { viewModel.removePreset(PresetCategory.Routine) },
                    )
                    PresetTile(
                        label = "Diet",
                        activeFileName = uiState.dietPresetName,
                        presetJsonName = uiState.dietPresetJsonName,
                        onBrowse = { launchPresetPicker(PresetCategory.Diet) },
                        onRemove = { viewModel.removePreset(PresetCategory.Diet) },
                    )

                    // ——— Storage Settings ———
                    SettingsSectionTitle("Storage Settings")
                    if (uiState.permissionRequired) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = OffWhite),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "Permission Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PureBlack,
                                )
                                Text(
                                    text = "Drive access needs your consent. The permission screen should open — grant access, then connection will be validated again.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PureBlack,
                                )
                            }
                        }
                    }
                    if (uiState.isLoading || uiState.isValidating) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = ElectricBlue,
                        )
                    }
                    OutlinedTextField(
                        value = uiState.folderId,
                        onValueChange = viewModel::updateFolderId,
                        label = { Text("Data folder ID (Root)", color = PureBlack) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading && !uiState.isValidating,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PureBlack,
                            unfocusedTextColor = PureBlack,
                            focusedLabelColor = PureBlack,
                            unfocusedLabelColor = PureBlack,
                            focusedBorderColor = NavyDeep,
                            unfocusedBorderColor = PureBlack,
                        ),
                    )

                    Button(
                        onClick = viewModel::saveFolderId,
                        enabled = !uiState.isValidating && !uiState.isResetting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (uiState.isLoading) "Saving…" else "Set Drive Folder", color = PureWhite)
                    }

                    Button(
                        onClick = viewModel::validateConnection,
                        enabled = !uiState.isLoading && !uiState.isResetting && !uiState.isBackingUp && !uiState.isRestoring,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                    ) {
                        if (uiState.isValidating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (uiState.isValidating) "Validating…" else "Validate Connection", color = PureWhite)
                    }

                    Button(
                        onClick = viewModel::backupToDrive,
                        enabled = !uiState.isLoading && !uiState.isValidating && !uiState.isResetting && !uiState.isRestoring,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                    ) {
                        if (uiState.isBackingUp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (uiState.isBackingUp) "Backing up…" else "Backup to Drive", color = PureWhite)
                    }

                    Button(
                        onClick = viewModel::restoreFromDrive,
                        enabled = !uiState.isLoading && !uiState.isValidating && !uiState.isResetting && !uiState.isBackingUp,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                    ) {
                        if (uiState.isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (uiState.isRestoring) "Restoring…" else "Restore from Drive", color = PureWhite)
                    }

                    Button(
                        onClick = viewModel::showPasswordDialog,
                        enabled = !uiState.isLoading && !uiState.isValidating && !uiState.isResetting && !uiState.isBackingUp && !uiState.isRestoring,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeleteRed,
                            contentColor = PureWhite
                        ),
                    ) {
                        if (uiState.isResetting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Reset Database", color = PureWhite)
                    }
            }
        }

        // Password dialog
        if (uiState.showPasswordDialog) {
            var password by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = viewModel::dismissPasswordDialog,
                title = {
                    Text("Enter Password", color = PureBlack)
                },
                text = {
                    Column {
                        Text(
                            "Please enter the password to reset the database.",
                            color = PureBlack,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password", color = PureBlack) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PureBlack,
                                unfocusedTextColor = PureBlack,
                                focusedLabelColor = PureBlack,
                                unfocusedLabelColor = PureBlack,
                                focusedBorderColor = DeleteRed,
                                unfocusedBorderColor = PureBlack,
                            ),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.verifyPasswordAndShowConfirmation(password)
                            password = ""
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = DeleteRed
                        ),
                        enabled = password.isNotBlank()
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.dismissPasswordDialog()
                            password = ""
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = PureBlack
                        )
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = PureWhite
            )
        }

        // Reset confirmation dialog
        if (uiState.showResetConfirmation) {
            AlertDialog(
                onDismissRequest = viewModel::dismissResetConfirmation,
                title = {
                    Text("Reset Database", color = PureBlack)
                },
                text = {
                    Text(
                        "This will delete all daily data from the database (diet, routine, exercise, diary text/audio, and home entries). " +
                        "Diary photo albums and their Drive links are kept so images in Google Drive stay connected. " +
                        "Sleep targets, notifications, and active presets on this device are not removed. " +
                        "Presets and Lists in Drive will not be affected.\n\n" +
                        "This action cannot be undone.",
                        color = PureBlack
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::resetDatabase,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = DeleteRed
                        )
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = viewModel::dismissResetConfirmation,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = PureBlack
                        )
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = PureWhite
            )
        }

        // Preset overwrite modal
        if (uiState.showPresetOverwriteModal && uiState.presetModalCategory != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissPresetModal,
                title = { Text("Apply preset \"${uiState.presetModalDisplayName}\"", color = PureBlack) },
                text = {
                    Text(
                        "Save this file as the active ${uiState.presetModalCategory!!.name} preset for future days. " +
                            "Today's ${uiState.presetModalCategory!!.name} data on the calendar will not be changed.",
                        color = PureBlack
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.applyPresetFromModal() },
                        enabled = !uiState.isPresetApplying,
                        colors = ButtonDefaults.textButtonColors(contentColor = NavyDeep)
                    ) {
                        if (uiState.isPresetApplying) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = NavyDeep, strokeWidth = 2.dp)
                        } else {
                            Text("Save preset")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = viewModel::dismissPresetModal,
                        enabled = !uiState.isPresetApplying,
                        colors = ButtonDefaults.textButtonColors(contentColor = PureBlack)
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = PureWhite
            )
        }

        uiState.showSleepTimePicker?.let { kind ->
            SettingsTimePickerDialog(
                initialMinutes = when (kind) {
                    SleepPickerKind.Bedtime -> uiState.sleepBedtimeMinutes
                    SleepPickerKind.Wakeup -> uiState.sleepWakeupMinutes
                },
                onDismiss = { viewModel.showSleepTimePicker(null) },
                onConfirm = { minutes ->
                    when (kind) {
                        SleepPickerKind.Bedtime -> viewModel.setSleepBedtime(minutes)
                        SleepPickerKind.Wakeup -> viewModel.setSleepWakeup(minutes)
                    }
                    viewModel.showSleepTimePicker(null)
                }
            )
        }

        if (uiState.showFilloutTimePicker) {
            SettingsTimePickerDialog(
                initialMinutes = uiState.filloutReminderMinutes ?: 20 * 60,
                onDismiss = { viewModel.showFilloutTimePicker(false) },
                onConfirm = { minutes -> viewModel.setFilloutReminder(minutes) }
            )
        }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String, isFirstSection: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            top = if (isFirstSection) 0.dp else 16.dp,
            bottom = 8.dp
        )
    ) {
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
private fun TimeChip(minutes: Int, onClick: () -> Unit) {
    val totalMin = (minutes % (24 * 60) + 24 * 60) % (24 * 60)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = PureWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, PureBlack.copy(alpha = 0.3f)),
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("%02d:%02d".format(totalMin / 60, totalMin % 60), style = MaterialTheme.typography.bodyLarge, color = PureBlack)
        }
    }
}

@Composable
private fun AlarmReminderTile(label: String, timeText: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PureWhite).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = PureBlack)
            Text(timeText, style = MaterialTheme.typography.bodyMedium, color = SlateGray)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = DeleteRed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTimePickerDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (minutes: Int) -> Unit) {
    val totalMinutes = 24 * 60
    val normalized = (initialMinutes % totalMinutes + totalMinutes) % totalMinutes
    val state = rememberTimePickerState(initialHour = normalized / 60, initialMinute = normalized % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK", color = NavyDeep) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SlateGray) } },
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

@Composable
private fun PresetTile(
    label: String,
    activeFileName: String?,
    presetJsonName: String? = null,
    onBrowse: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OffWhite),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = PureBlack,
            )
            Text(
                text = activeFileName ?: "No preset",
                style = MaterialTheme.typography.bodyMedium,
                color = if (activeFileName != null) PureBlack else SlateGray,
                modifier = Modifier.fillMaxWidth(),
            )
            if (presetJsonName != null) {
                Text(
                    text = presetJsonName,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateGray,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onBrowse,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                ) {
                    Text("Browse", color = PureWhite)
                }
                if (activeFileName != null) {
                    Button(
                        onClick = onRemove,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = PureWhite),
                    ) {
                        Text("Remove", color = PureWhite)
                    }
                }
            }
        }
    }
}
