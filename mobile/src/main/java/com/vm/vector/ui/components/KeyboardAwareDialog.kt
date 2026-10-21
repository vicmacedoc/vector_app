package com.vm.vector.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Scrollable dialog body for AlertDialog / Dialog content with multiline fields.
 * Pair with [Modifier.imePadding] on the AlertDialog or Dialog Surface so the
 * whole chrome lifts above the keyboard.
 */
@Composable
fun KeyboardAwareDialogColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** Height caps so multiline dialog fields scroll internally instead of growing under the IME. */
object DialogMultilineFieldDefaults {
    val LargeMin: Dp = 120.dp
    val LargeMax: Dp = 220.dp
    val MediumMin: Dp = 80.dp
    val MediumMax: Dp = 160.dp
}
