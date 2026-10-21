package com.vm.vector.ui.screens.calendar

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite

/** Swipe down from the top of a Calendar tab to reload it; the child must be vertically scrollable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun DiscardChangesDialog(
    categoryName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String = "Reloading $categoryName will throw away the changes you haven't saved for this day.",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discard unsaved changes?", color = PureBlack) },
        text = { Text(message, color = PureBlack) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Discard and reload", color = DeleteRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep editing", color = NavyDeep)
            }
        },
        containerColor = PureWhite,
    )
}
