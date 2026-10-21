package com.vm.vector.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray

/**
 * Shown when an import review (AI, clipboard, Google Calendar…) is about to close without confirming,
 * so a stray tap outside the dialog doesn't silently drop the generated items.
 */
@Composable
fun DiscardImportReviewDialog(
    sourceLabel: String,
    onKeepReviewing: () -> Unit,
    onDiscard: () -> Unit,
) {
    val isAi = sourceLabel.equals("AI", ignoreCase = true)
    AlertDialog(
        onDismissRequest = onKeepReviewing,
        containerColor = PureWhite,
        title = {
            Text(if (isAi) "Discard AI output?" else "Discard import?", color = PureBlack)
        },
        text = {
            Text(
                if (isAi) {
                    "The AI output and any edits you made in this review will be lost."
                } else {
                    "The imported $sourceLabel items and any edits you made in this review will be lost."
                },
                color = PureBlack,
            )
        },
        confirmButton = {
            Button(
                onClick = onKeepReviewing,
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) { Text("Keep reviewing") }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text("Discard", color = DeleteRed) }
        },
    )
}

/**
 * Review dialog footer. Cancel and [extraActions] get their own row so the primary label
 * (e.g. "Replace day (12)") always fits on one line in a phone-width dialog.
 */
@Composable
fun ImportReviewFooter(
    primaryLabel: String,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    onBack: (() -> Unit)? = null,
    backEnabled: Boolean = true,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel", color = PureBlack) }
            Row(verticalAlignment = Alignment.CenterVertically, content = extraActions)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack, enabled = backEnabled) {
                    Text("Back", color = if (backEnabled) PureBlack else SlateGray, maxLines = 1)
                }
            }
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NavyDeep,
                    contentColor = PureWhite,
                    disabledContainerColor = OffWhite,
                    disabledContentColor = SlateGray,
                ),
            ) {
                Text(primaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
