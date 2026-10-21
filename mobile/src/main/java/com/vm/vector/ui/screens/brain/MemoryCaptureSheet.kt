package com.vm.vector.ui.screens.brain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray

/** Quick-capture grid that files straight into one memory tile; Inbox replaces To-do. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryCaptureSheet(
    memoryName: String,
    inboxCount: Int,
    onText: () -> Unit,
    onVoice: () -> Unit,
    onLink: () -> Unit,
    onMedia: () -> Unit,
    onFile: () -> Unit,
    onInbox: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun pick(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                "Add to $memoryName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PureBlack,
            )
            Text(
                "Everything here goes straight into this memory.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptureOption(Icons.Outlined.Description, "Text", Modifier.weight(1f), onClick = pick(onText))
                CaptureOption(Icons.Outlined.Mic, "Voice", Modifier.weight(1f), onClick = pick(onVoice))
                CaptureOption(Icons.Outlined.Link, "Link", Modifier.weight(1f), onClick = pick(onLink))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptureOption(Icons.Outlined.PhotoLibrary, "Media", Modifier.weight(1f), onClick = pick(onMedia))
                CaptureOption(Icons.Outlined.AttachFile, "File", Modifier.weight(1f), onClick = pick(onFile))
                CaptureOption(
                    Icons.Outlined.Inbox,
                    "Inbox",
                    Modifier.weight(1f),
                    badge = inboxCount.takeIf { it > 0 }?.toString(),
                    onClick = pick(onInbox),
                )
            }
        }
    }
}
