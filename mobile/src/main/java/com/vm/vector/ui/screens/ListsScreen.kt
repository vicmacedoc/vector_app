package com.vm.vector.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vm.core.models.VectorItem
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PriorityHigh
import com.vm.core.ui.theme.PriorityHold
import com.vm.core.ui.theme.PriorityLow
import com.vm.core.ui.theme.PriorityMid
import com.vm.core.ui.theme.PriorityNone
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.VectorTheme
import com.vm.vector.data.GoogleDriveAuthManager
import com.vm.vector.data.DriveFileInfo
import com.vm.vector.ui.viewmodel.ListsViewModel
import com.vm.vector.ui.viewmodel.ListsViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    viewModel: ListsViewModel = viewModel(
        factory = ListsViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val authManager = remember { GoogleDriveAuthManager(context) }
    val isSignedIn = authManager.getLastSignedInAccount() != null

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        authManager.handleSignInResult(result.data) { account ->
            if (account != null) viewModel.refresh()
        }
    }

    val saveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.saveChanges()
    }

    LaunchedEffect(uiState.pendingSaveConsentIntent) {
        val intent = uiState.pendingSaveConsentIntent ?: return@LaunchedEffect
        if (context !is Activity) return@LaunchedEffect
        viewModel.clearPendingSaveConsent()
        saveConsentLauncher.launch(intent)
    }

    LaunchedEffect(uiState.showSnackbar) {
        if (uiState.showSnackbar) {
            snackbarHostState.showSnackbar(uiState.snackbarMessage)
            viewModel.dismissSnackbar()
        }
    }

    LaunchedEffect(Unit) {
        if (isSignedIn && uiState.files.isEmpty()) {
            viewModel.refresh()
        }
    }

    VectorTheme {
        Scaffold(
            containerColor = PureWhite,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            if (!isSignedIn) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureWhite)
                        .padding(paddingValues)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "Sign in to load lists from Google Drive",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = PureBlack,
                        )
                        Button(
                            onClick = {
                                val activity = context as? Activity
                                if (activity != null) {
                                    signInLauncher.launch(authManager.getSignInIntent())
                                }
                            },
                        ) {
                            Text("Sign in with Google")
                        }
                    }
                }
            } else {
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
                    if (uiState.isLoading && !uiState.isSaving) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = ElectricBlue,
                        )
                    }
                    when {
                        uiState.configError -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(PureWhite)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Configuration Error",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        uiState.listsFolderMissing -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(PureWhite)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Discovery failed: 'lists' folder not found under root.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = PureBlack,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        else -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                PullToRefreshBox(
                                    isRefreshing = uiState.isLoading && !uiState.isSaving,
                                    onRefresh = { viewModel.refresh() },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        ListsDropdownRow(
                                            files = uiState.files,
                                            selectedFileId = uiState.selectedFileId,
                                            onFileSelected = viewModel::selectFile,
                                            onAddClick = viewModel::openAddModal,
                                            onTrashClick = { viewModel.requestDeleteFile(uiState.selectedFileId!!) },
                                            canTrash = uiState.selectedFileId != null,
                                        )
                                        uiState.workingList?.let { list ->
                                            Text(
                                                text = list.name,
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = PureBlack,
                                            )
                                            val sorted = list.items
                                                .sortedBy { it.isChecked }
                                            sorted.forEach { item ->
                                                ListItemRow(
                                                    item = item,
                                                    onCheckChange = { viewModel.toggleItemCheck(item.id) },
                                                    onRemainingChange = { viewModel.updateItemRemaining(item.id, it) },
                                                    onPriorityChange = { viewModel.updateItemPriority(item.id, it) },
                                                )
                                            }
                                        }
                                    }
                                }
                                if (uiState.selectedFileId != null && uiState.workingList != null) {
                                    Button(
                                        onClick = { viewModel.saveChanges() },
                                        enabled = !uiState.isSaving,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                                    ) {
                                        if (uiState.isSaving) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = PureWhite,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            if (uiState.isSaving) "Saving…" else "Save Changes",
                                            color = PureWhite,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.showAddModal) {
            AddFileModal(
                onDismiss = viewModel::closeAddModal,
                onSubmit = viewModel::submitAdd,
            )
        }

        if (uiState.showDeleteConfirm && uiState.deleteTargetFileId != null) {
            AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = { Text("Delete file?", color = PureBlack) },
                text = { Text("This file will be removed from Drive.", color = PureBlack) },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmDelete) {
                        Text("Delete", color = DeleteRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelDelete) {
                        Text("Cancel", color = PureBlack)
                    }
                },
                containerColor = PureWhite,
            )
        }
    }
}

private val PRIORITY_VALUES = listOf("High", "Mid", "Low", "Hold", "None")

private fun priorityColor(p: String) = when (p) {
    "High" -> PriorityHigh
    "Mid" -> PriorityMid
    "Low" -> PriorityLow
    "Hold" -> PriorityHold
    else -> PriorityNone
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListItemRow(
    item: VectorItem,
    onCheckChange: () -> Unit,
    onRemainingChange: (Double) -> Unit,
    onPriorityChange: (String) -> Unit,
) {
    val qty = item.quantity
    val hasQty = qty != null && item.unit != null
    val qtyPart = if (hasQty) ": ${item.quantity} ${item.unit}" else ""
    val descText = item.title + qtyPart
    val alpha = if (item.isChecked) 0.5f else 1f
    val priority = item.priority
    val showPriority = priority != null
    val priorityDisplay = if (showPriority && priority != null) {
        if (priority in PRIORITY_VALUES) priority else "None"
    } else "None"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = { onCheckChange() },
                colors = CheckboxDefaults.colors(checkedColor = NavyDeep, uncheckedColor = PureBlack),
            )
            Text(
                text = descText,
                style = MaterialTheme.typography.bodyLarge,
                color = PureBlack.copy(alpha = alpha),
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            if (showPriority) {
                PriorityChip(
                    priority = priorityDisplay,
                    onPriorityChange = onPriorityChange,
                    alpha = alpha,
                )
            }
        }
        val remaining = item.remaining
        if (remaining != null && qty != null && qty > 0) {
            val maxVal = qty.toFloat()
            val currentVal = remaining.toFloat().coerceIn(0f, maxVal)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Left: %.1f".format(currentVal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NavyDeep,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Slider(
                    value = currentVal,
                    onValueChange = { onRemainingChange(it.toDouble()) },
                    valueRange = 0f..maxVal,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = NavyDeep,
                        activeTrackColor = NavyDeep,
                        inactiveTrackColor = OffWhite,
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
        }
    }
}

@Composable
private fun PriorityChip(
    priority: String,
    onPriorityChange: (String) -> Unit,
    alpha: Float,
) {
    var expanded by remember { mutableStateOf(false) }
    val color = priorityColor(priority)

    Box {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = PureWhite),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Text(
                text = priority,
                style = MaterialTheme.typography.labelMedium,
                color = PureWhite.copy(alpha = alpha),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = PureWhite,
        ) {
            PRIORITY_VALUES.forEach { p ->
                DropdownMenuItem(
                    text = { Text(text = p, color = PureBlack) },
                    onClick = {
                        onPriorityChange(p)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AddFileModal(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var fileName by remember { mutableStateOf("") }
    var jsonContent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add file", color = PureBlack) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File name", color = PureBlack) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = jsonContent,
                    onValueChange = { jsonContent = it },
                    label = { Text("Paste JSON content", color = PureBlack) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (fileName.isNotBlank() && jsonContent.isNotBlank()) {
                        onSubmit(fileName.trim(), jsonContent.trim())
                    }
                },
            ) {
                Text("Upload", color = NavyDeep)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PureBlack)
            }
        },
        containerColor = PureWhite,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListsDropdownRow(
    files: List<DriveFileInfo>,
    selectedFileId: String?,
    onFileSelected: (String) -> Unit,
    onAddClick: () -> Unit,
    onTrashClick: () -> Unit,
    canTrash: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = files.find { it.id == selectedFileId }?.name ?: "Select a list"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = selectedName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NavyDeep,
                    unfocusedBorderColor = NavyDeep,
                    focusedTextColor = PureBlack,
                    unfocusedTextColor = PureBlack,
                ),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = PureWhite,
            ) {
                files.forEach { file ->
                    DropdownMenuItem(
                        text = { Text(text = file.name, color = PureBlack) },
                        onClick = {
                            onFileSelected(file.id)
                            expanded = false
                        },
                    )
                }
            }
        }
        IconButton(
            onClick = onAddClick,
            modifier = Modifier
                .background(PureWhite)
                .border(1.5.dp, NavyDeep, RoundedCornerShape(8.dp))
                .size(48.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add file", tint = NavyDeep)
        }
        IconButton(
            onClick = onTrashClick,
            enabled = canTrash,
            modifier = Modifier
                .background(PureWhite)
                .border(1.5.dp, NavyDeep, RoundedCornerShape(8.dp))
                .size(48.dp),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete file",
                tint = IconGray,
            )
        }
    }
}
