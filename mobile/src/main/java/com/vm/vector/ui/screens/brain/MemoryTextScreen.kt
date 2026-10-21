package com.vm.vector.ui.screens.brain

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.core.ui.theme.VectorTheme
import com.vm.vector.ui.viewmodel.MemoryTextViewModel
import com.vm.vector.ui.viewmodel.MemoryTextViewModelFactory

@Composable
fun MemoryTextScreen(
    noteId: String,
    onBack: () -> Unit,
    viewModel: MemoryTextViewModel = viewModel(
        key = "memory_text_$noteId",
        factory = MemoryTextViewModelFactory(LocalContext.current, noteId),
    ),
) {
    val note by viewModel.note.collectAsState()
    val body by viewModel.body.collectAsState()
    var showDetails by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(body == null) {
        if (body?.isEmpty() == true) focusRequester.requestFocus()
    }

    VectorTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PureWhite)
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = note != null) { showDetails = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = note?.title.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PureBlack,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Edit title and description",
                        tint = SlateGray,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(18.dp),
                    )
                }
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PureBlack)
                }
            }
            note?.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateGray,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = IconGray.copy(alpha = 0.25f),
            )

            val loaded = body
            if (loaded != null) {
                var field by remember { mutableStateOf(TextFieldValue(loaded)) }
                var focused by remember { mutableStateOf(false) }
                val revealLines = if (focused) {
                    markdownLinesAround(field.text, field.selection.start, field.selection.end)
                } else {
                    null
                }
                val markdown = remember(revealLines) { MarkdownVisualTransformation(revealLines) }
                BasicTextField(
                    value = field,
                    onValueChange = { value ->
                        if (value.text != field.text) viewModel.onBodyChange(value.text)
                        field = value
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = PureBlack),
                    cursorBrush = SolidColor(NavyDeep),
                    visualTransformation = markdown,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { inner ->
                        Box {
                            if (field.text.isEmpty()) {
                                Text(
                                    "Start writing… Markdown works: # Heading, **bold**, *italic*, - list, - [ ] task, `code`",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = IconGray,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }
    }

    if (showDetails) {
        note?.let { current ->
            TextDetailsDialog(
                heading = "Edit details",
                initialTitle = current.title,
                initialDescription = current.description,
                confirm = "Save",
                onSave = viewModel::updateDetails,
                onDismiss = { showDetails = false },
            )
        }
    }
}

/** Title (required) and optional description for a memory text source. */
@Composable
fun TextDetailsDialog(
    heading: String,
    initialTitle: String,
    initialDescription: String,
    confirm: String,
    onSave: (title: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text(heading, color = PureBlack) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = brainFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = brainFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(title, description)
                    onDismiss()
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PureBlack) }
        },
    )
}
