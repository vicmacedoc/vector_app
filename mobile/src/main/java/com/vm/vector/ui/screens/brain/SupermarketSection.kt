@file:OptIn(ExperimentalMaterial3Api::class)

package com.vm.vector.ui.screens.brain

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vm.core.models.VectorItem
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.ui.screens.calendar.CalendarPullToRefresh
import com.vm.vector.ui.viewmodel.SupermarketFilter
import com.vm.vector.ui.viewmodel.SupermarketViewModel
import kotlinx.coroutines.launch

private val TileOuterPadding = 16.dp
private val DragHandleWidth = 48.dp
private val TrashZoneHeight = 80.dp

@Composable
fun SupermarketScreen(
    onBack: () -> Unit,
    viewModel: SupermarketViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.vm.vector.ui.viewmodel.SupermarketViewModelFactory(LocalContext.current),
    ),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    Column(modifier = Modifier.fillMaxSize().background(PureWhite)) {
        SupermarketSection(viewModel = viewModel, onBack = onBack)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupermarketSection(
    viewModel: SupermarketViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val counts = viewModel.filterCounts()
    val sourceItems = viewModel.filteredItems()
    var showAddItem by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<VectorItem?>(null) }
    var pendingDelete by remember { mutableStateOf<VectorItem?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edgeScrollZone = with(density) { 56.dp.toPx() }
    val handleZone = with(density) { (TileOuterPadding + DragHandleWidth).toPx() }
    val trashZone = with(density) { TrashZoneHeight.toPx() }
    val currentSource by rememberUpdatedState(sourceItems)

    // Non-null only while a drag is in progress; holds the live, locally reordered list.
    var dragItems by remember { mutableStateOf<List<VectorItem>?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragInitialOffset by remember { mutableIntStateOf(0) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }

    // The trash zone overlays the bottom of the list viewport.
    val overTrash = draggingId != null &&
        pointerY > listState.layoutInfo.viewportSize.height - trashZone
    val rendered = dragItems ?: sourceItems

    fun draggedOffset(): Float {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingId } ?: return 0f
        return dragInitialOffset + dragDelta - info.offset
    }

    fun resetDrag() {
        draggingId = null
        dragItems = null
        dragDelta = 0f
        pointerY = 0f
    }

    LaunchedEffect(overTrash) {
        if (overTrash) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearSnackbar()
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.onConsentGranted()
    }

    val requestRefresh: () -> Unit = { viewModel.refresh() }

    LaunchedEffect(uiState.pendingConsentIntent) {
        val intent = uiState.pendingConsentIntent ?: return@LaunchedEffect
        viewModel.clearPendingConsent()
        consentLauncher.launch(intent)
    }

    Column(modifier = modifier.fillMaxSize().background(PureWhite)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NavyDeep)
                }
            }
            Text(
                text = "Supermarket",
                style = MaterialTheme.typography.titleLarge,
                color = PureBlack,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.toggleSlidersLocked() }) {
                Icon(
                    imageVector = if (uiState.slidersLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (uiState.slidersLocked) "Unlock sliders" else "Lock sliders",
                    tint = NavyDeep,
                )
            }
            val canSave = uiState.hasUnsavedChanges && !uiState.isSaving
            IconButton(
                onClick = { viewModel.save() },
                enabled = canSave,
                modifier = Modifier
                    .size(48.dp)
                    .then(if (canSave) Modifier.background(NavyDeep, CircleShape) else Modifier),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = NavyDeep,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = if (uiState.hasUnsavedChanges) "Save" else "No changes to save",
                        tint = if (canSave) PureWhite else PureBlack.copy(alpha = 0.3f),
                    )
                }
            }
        }

        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ElectricBlue)
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SupermarketFilter.entries.forEach { filter ->
                val label = when (filter) {
                    SupermarketFilter.ALL -> "All"
                    SupermarketFilter.TO_BUY -> "To buy"
                    SupermarketFilter.STOCKED -> "Stocked"
                }
                FilterChip(
                    selected = uiState.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text("$label (${counts[filter] ?: 0})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NavyDeep,
                        selectedLabelColor = PureWhite,
                        labelColor = PureBlack,
                        containerColor = PureWhite,
                    ),
                )
            }
        }

        CalendarPullToRefresh(
            isRefreshing = uiState.isRefreshing,
            onRefresh = requestRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                    offset.y.toInt() in it.offset..(it.offset + it.size)
                                } ?: return@detectDragGesturesAfterLongPress
                                val item = currentSource.firstOrNull { it.id == hit.key }
                                    ?: return@detectDragGesturesAfterLongPress
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Only the drag handle on the right edge starts a reorder; elsewhere edits.
                                if (offset.x < size.width - handleZone) {
                                    editingItem = item
                                    return@detectDragGesturesAfterLongPress
                                }
                                dragItems = currentSource
                                draggingId = item.id
                                dragInitialOffset = hit.offset
                                dragDelta = 0f
                                pointerY = offset.y
                            },
                            onDrag = { change, amount ->
                                val id = draggingId ?: return@detectDragGesturesAfterLongPress
                                change.consume()
                                dragDelta += amount.y
                                pointerY += amount.y

                                val layout = listState.layoutInfo
                                val info = layout.visibleItemsInfo.firstOrNull { it.key == id }
                                val items = dragItems
                                if (info != null && items != null) {
                                    val middle = (dragInitialOffset + dragDelta + info.size / 2f).toInt()
                                    val target = layout.visibleItemsInfo.firstOrNull {
                                        it.key != id && middle in it.offset..(it.offset + it.size)
                                    }
                                    val from = items.indexOfFirst { it.id == id }
                                    val to = target?.let { t -> items.indexOfFirst { it.id == t.key } } ?: -1
                                    if (from >= 0 && to >= 0) {
                                        val first = listState.firstVisibleItemIndex
                                        if (from == first || to == first) {
                                            val firstOffset = listState.firstVisibleItemScrollOffset
                                            scope.launch { listState.scrollToItem(first, firstOffset) }
                                        }
                                        dragItems = items.toMutableList().apply { add(to, removeAt(from)) }
                                    }
                                }

                                val scrollBottom = layout.viewportSize.height - trashZone
                                val scroll = when {
                                    pointerY < edgeScrollZone -> -(edgeScrollZone - pointerY) / 3f
                                    pointerY in (scrollBottom - edgeScrollZone)..scrollBottom ->
                                        (pointerY - (scrollBottom - edgeScrollZone)) / 3f
                                    else -> 0f
                                }
                                if (scroll != 0f) scope.launch { listState.scrollBy(scroll) }
                            },
                            onDragEnd = {
                                val id = draggingId
                                val items = dragItems
                                val droppedOnTrash =
                                    pointerY > listState.layoutInfo.viewportSize.height - trashZone
                                if (id != null && items != null) {
                                    if (droppedOnTrash) {
                                        pendingDelete = items.firstOrNull { it.id == id }
                                    } else if (items.map { it.id } != currentSource.map { it.id }) {
                                        viewModel.reorderItems(items.map { it.id })
                                    }
                                }
                                resetDrag()
                            },
                            onDragCancel = { resetDrag() },
                        )
                    },
            ) {
                items(rendered, key = { it.id }) { item ->
                    val isDragging = item.id == draggingId
                    val itemModifier = if (isDragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                translationY = draggedOffset()
                                alpha = if (overTrash) 0.55f else 1f
                            }
                    } else {
                        Modifier.animateItem()
                    }
                    SupermarketItemCard(
                        item = item,
                        isDragging = isDragging,
                        sliderEnabled = !uiState.slidersLocked,
                        onRemainingChange = { viewModel.updateRemaining(item.id, it) },
                        modifier = itemModifier,
                    )
                }
                item(key = "bottom-spacer") { Spacer(Modifier.height(TrashZoneHeight + 8.dp)) }
            }

            if (rendered.isEmpty() && !uiState.isLoading) {
                Text(
                    text = when (uiState.filter) {
                        SupermarketFilter.ALL -> "No items yet — tap + to add one"
                        SupermarketFilter.TO_BUY -> "Nothing to buy"
                        SupermarketFilter.STOCKED -> "Nothing stocked"
                    },
                    color = SlateGray,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }

            AddItemFab(visible = draggingId == null, onClick = { showAddItem = true })
            TrashDropZone(visible = draggingId != null, active = overTrash)
        }
    }

    if (showAddItem) {
        ItemEditorDialog(
            initial = null,
            onDismiss = { showAddItem = false },
            onSave = { title, aisle, total, left ->
                viewModel.addItem(title, aisle, total, left)
                showAddItem = false
            },
        )
    }

    editingItem?.let { item ->
        ItemEditorDialog(
            initial = item,
            onDismiss = { editingItem = null },
            onSave = { title, aisle, total, left ->
                viewModel.editItem(item.id, title, aisle, total, left)
                editingItem = null
            },
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = PureWhite,
            title = { Text("Delete item?", color = PureBlack) },
            text = { Text("\"${item.title}\" will be removed from the list.", color = SlateGray) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(item.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = PureWhite),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = NavyDeep) }
            },
        )
    }

    uiState.tripPromptPoints?.let { points ->
        AlertDialog(
            onDismissRequest = { viewModel.confirmTrip(wentShopping = false) },
            containerColor = PureWhite,
            title = { Text("Did you go to the supermarket?", color = PureBlack) },
            text = {
                Text(
                    "Stock went up and nothing is at zero. Log this as this week's trip for +$points pts?",
                    color = SlateGray,
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmTrip(wentShopping = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                ) { Text("Yes, +$points pts") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmTrip(wentShopping = false) }) {
                    Text("Just save", color = NavyDeep)
                }
            },
        )
    }
}

private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)

@Composable
private fun SupermarketItemCard(
    item: VectorItem,
    isDragging: Boolean,
    sliderEnabled: Boolean,
    onRemainingChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = (item.quantity?.takeIf { it > 0.0 } ?: 1.0).toFloat()
    val current = (item.remaining ?: 0.0).toFloat().coerceIn(0f, total)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TileOuterPadding, vertical = 6.dp)
            .shadow(if (isDragging) 8.dp else 0.dp, shape)
            .border(
                width = if (isDragging) 2.dp else 1.dp,
                color = if (isDragging) NavyDeep else IconGray.copy(alpha = 0.35f),
                shape = shape,
            )
            .background(PureWhite, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    color = PureBlack,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val aisle = item.category
                if (!aisle.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = aisle,
                        color = SlateGray,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        modifier = Modifier
                            .background(OffWhite, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            VectorSlider(
                value = current,
                onValueChange = { onRemainingChange(it.toDouble()) },
                valueRange = 0f..total,
                enabled = sliderEnabled,
                label = "Left: ${formatAmount(current.toDouble())} / ${formatAmount(total.toDouble())}",
            )
        }
        Box(
            modifier = Modifier.width(DragHandleWidth),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = "Hold to reorder",
                tint = if (isDragging) NavyDeep else IconGray,
            )
        }
    }
}

@Composable
private fun BoxScope.AddItemFab(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = NavyDeep,
            contentColor = PureWhite,
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add item")
        }
    }
}

@Composable
private fun BoxScope.TrashDropZone(visible: Boolean, active: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        TrashDropZoneContent(active)
    }
}

@Composable
private fun TrashDropZoneContent(active: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TrashZoneHeight)
            .background(PureWhite)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(if (active) DeleteRed else PureWhite, shape)
                .border(2.dp, DeleteRed, shape),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = if (active) PureWhite else DeleteRed,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (active) "Release to delete" else "Drag here to delete",
                color = if (active) PureWhite else DeleteRed,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun parseAmount(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** Add (initial == null) or edit an item. Aisle and "left" are optional; total defaults to 1. */
@Composable
private fun ItemEditorDialog(
    initial: VectorItem?,
    onDismiss: () -> Unit,
    onSave: (title: String, aisle: String?, total: Double?, left: Double?) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var aisle by remember { mutableStateOf(initial?.category.orEmpty()) }
    var total by remember { mutableStateOf(initial?.quantity?.let(::formatAmount) ?: "1") }
    var left by remember { mutableStateOf(initial?.remaining?.let(::formatAmount).orEmpty()) }

    val totalValue = parseAmount(total)
    val leftValue = parseAmount(left)
    val totalError = total.isNotBlank() && (totalValue == null || totalValue <= 0.0)
    val leftError = left.isNotBlank() &&
        (leftValue == null || leftValue < 0.0 || leftValue > (totalValue ?: 1.0))
    val canSave = title.isNotBlank() && !totalError && !leftError

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = PureBlack,
        unfocusedTextColor = PureBlack,
        focusedContainerColor = PureWhite,
        unfocusedContainerColor = PureWhite,
        focusedBorderColor = NavyDeep,
        unfocusedBorderColor = NavyDeep.copy(alpha = 0.4f),
        focusedLabelColor = NavyDeep,
        cursorColor = NavyDeep,
    )
    val decimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text(if (initial == null) "Add item" else "Edit item", color = PureBlack) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = aisle,
                    onValueChange = { aisle = it },
                    label = { Text("Aisle (optional)") },
                    singleLine = true,
                    colors = fieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = total,
                        onValueChange = { total = it },
                        label = { Text("Total") },
                        singleLine = true,
                        isError = totalError,
                        keyboardOptions = decimalKeyboard,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = left,
                        onValueChange = { left = it },
                        label = { Text("Left") },
                        placeholder = { Text("= total") },
                        singleLine = true,
                        isError = leftError,
                        keyboardOptions = decimalKeyboard,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (totalError || leftError) {
                    Text(
                        text = if (totalError) "Total must be a number above 0" else "Left must be between 0 and total",
                        color = DeleteRed,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, aisle.ifBlank { null }, totalValue, leftValue) },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = NavyDeep) } },
    )
}
