package com.vm.vector.ui.screens.brain

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.MotionItemDraft
import com.vm.vector.ui.viewmodel.BrainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCaptureSheet(
    visible: Boolean,
    viewModel: BrainViewModel,
    onVoiceStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var step by remember { mutableStateOf<CaptureStep?>(null) }
    var field1 by remember { mutableStateOf("") }
    var field2 by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun resetAndDismiss() {
        step = null
        field1 = ""
        field2 = ""
        viewModel.closeCaptureSheet()
        onDismiss()
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        viewModel.importToInbox(uris)
        resetAndDismiss()
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.importToInbox(uris)
        resetAndDismiss()
    }

    if (step == CaptureStep.TODO) {
        TodoCaptureDialog(
            onSave = { drafts ->
                viewModel.createTodoCapture(drafts)
                resetAndDismiss()
            },
            onBack = { step = null },
        )
    } else if (step != null) {
        AlertDialog(
            onDismissRequest = { step = null },
            containerColor = PureWhite,
            title = {
                Text(
                    when (step) {
                        CaptureStep.TEXT -> "Text note"
                        CaptureStep.LINK -> "Link"
                        else -> ""
                    },
                    color = PureBlack,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (step == CaptureStep.TEXT) {
                        OutlinedTextField(
                            value = field1,
                            onValueChange = { field1 = it },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = brainFieldColors(),
                        )
                        OutlinedTextField(
                            value = field2,
                            onValueChange = { field2 = it },
                            label = { Text("Body") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            colors = brainFieldColors(),
                        )
                    } else {
                        OutlinedTextField(
                            value = field1,
                            onValueChange = { field1 = it },
                            label = { Text("URL") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = brainFieldColors(),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (step) {
                            CaptureStep.TEXT -> viewModel.createTextCapture(field1, field2)
                            CaptureStep.LINK -> viewModel.createLinkCapture(field1)
                            else -> {}
                        }
                        resetAndDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { step = null }) { Text("Back", color = PureBlack) }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = { resetAndDismiss() },
        sheetState = sheetState,
        containerColor = PureWhite,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Quick capture",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PureBlack,
            )
            Text(
                "Everything lands in the Inbox until you file it.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptureOption(Icons.Outlined.Description, "Text", Modifier.weight(1f)) { step = CaptureStep.TEXT }
                CaptureOption(Icons.Outlined.Mic, "Voice", Modifier.weight(1f)) {
                    viewModel.closeCaptureSheet()
                    onVoiceStart()
                }
                CaptureOption(Icons.Outlined.Link, "Link", Modifier.weight(1f)) { step = CaptureStep.LINK }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptureOption(Icons.Outlined.PhotoLibrary, "Media", Modifier.weight(1f)) {
                    mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }
                CaptureOption(Icons.Outlined.AttachFile, "File", Modifier.weight(1f)) {
                    filePicker.launch(arrayOf("*/*"))
                }
                CaptureOption(Icons.Outlined.CheckBox, "To-do", Modifier.weight(1f)) { step = CaptureStep.TODO }
            }
        }
    }
}

private enum class CaptureStep { TEXT, LINK, TODO }

/** Several to-dos at once; they wait in the Inbox until a list merges them. */
@Composable
private fun TodoCaptureDialog(
    onSave: (List<MotionItemDraft>) -> Unit,
    onBack: () -> Unit,
) {
    var drafts by remember { mutableStateOf(listOf(MotionItemDraft())) }
    val valid = drafts.filter { it.title.isNotBlank() }

    fun update(key: String, transform: (MotionItemDraft) -> MotionItemDraft) {
        drafts = drafts.map { if (it.key == key) transform(it) else it }
    }

    AlertDialog(
        onDismissRequest = onBack,
        containerColor = PureWhite,
        title = { Text("To-dos", color = PureBlack) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "They wait in the Inbox until you merge them into a list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateGray,
                )
                drafts.forEachIndexed { index, draft ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(OffWhite)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = draft.title,
                                onValueChange = { value -> update(draft.key) { it.copy(title = value) } },
                                label = { Text("Name") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                colors = brainFieldColors(),
                                modifier = Modifier.weight(1f),
                            )
                            if (drafts.size > 1) {
                                IconButton(onClick = { drafts = drafts.filterNot { it.key == draft.key } }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove item ${index + 1}", tint = SlateGray)
                                }
                            }
                        }
                        MotionDeadlineField(
                            dueAt = draft.dueAt,
                            onChange = { due -> update(draft.key) { it.copy(dueAt = due) } },
                            showTime = false,
                        )
                        MotionPrioritySelector(
                            priority = draft.priority,
                            onChange = { priority -> update(draft.key) { it.copy(priority = priority) } },
                        )
                        MotionPointsField(
                            priority = draft.priority,
                            points = draft.points,
                            onChange = { points -> update(draft.key) { it.copy(points = points) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                TextButton(onClick = { drafts = drafts + MotionItemDraft() }) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(18.dp))
                    Text("Add another", color = NavyDeep, modifier = Modifier.padding(start = 4.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(valid) },
                enabled = valid.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) { Text(if (valid.size <= 1) "Save to Inbox" else "Save ${valid.size} to Inbox") }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text("Back", color = PureBlack) }
        },
    )
}

/** Navy capture tile; [badge] shows as a white circle with navy text. */
@Composable
fun CaptureOption(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NavyDeep)
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = PureWhite, modifier = Modifier.size(24.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = PureWhite,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (badge != null) {
            Text(
                text = badge,
                color = NavyDeep,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(PureWhite, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}
