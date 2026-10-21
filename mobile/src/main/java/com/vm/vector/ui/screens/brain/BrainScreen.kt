@file:OptIn(ExperimentalMaterial3Api::class)

package com.vm.vector.ui.screens.brain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.VectorTheme
import com.vm.vector.audio.BrainAudioRecorder
import com.vm.vector.ui.components.rememberVoiceCaptureStarter
import com.vm.vector.ui.viewmodel.BrainTab
import com.vm.vector.ui.viewmodel.BrainViewModel
import com.vm.vector.ui.viewmodel.BrainViewModelFactory
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun BrainScreen(
    initialTab: BrainTab? = null,
    onOpenSupermarket: () -> Unit,
    onOpenTile: (String) -> Unit,
    onOpenMemory: (String) -> Unit,
    onOpenPlayer: (String) -> Unit = {},
    onOpenMotionList: (String) -> Unit = {},
    viewModel: BrainViewModel = viewModel(factory = BrainViewModelFactory(LocalContext.current)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val memoryTiles by viewModel.memoryTiles.collectAsState()
    val learnTiles by viewModel.learnTiles.collectAsState()
    val tagLibrary by viewModel.tagLibrary.collectAsState()
    val learnTagLibrary by viewModel.learnTagLibrary.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val recorder = remember { BrainAudioRecorder(context) }
    val startVoiceCapture = rememberVoiceCaptureStarter(
        onMicDenied = { viewModel.showSnackbar("Microphone permission is needed for voice capture.") },
    )
    var initialTabApplied by rememberSaveable { mutableStateOf(false) }
    var showNewMemory by remember { mutableStateOf(false) }
    var showNewLearnTile by remember { mutableStateOf(false) }
    var showNewMotionList by remember { mutableStateOf(false) }
    var showTagFilter by remember { mutableStateOf(false) }

    LaunchedEffect(initialTab) {
        if (!initialTabApplied) {
            initialTab?.let(viewModel::selectTab)
            initialTabApplied = true
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    val tab = uiState.tab
    val badges = buildMap {
        if (uiState.inboxCount > 0) {
            val inboxSelected = tab == BrainTab.INBOX
            put(
                BrainTab.INBOX,
                BrainTabBadge(
                    text = uiState.inboxCount.toString(),
                    color = if (inboxSelected) PureWhite else NavyDeep,
                    textColor = if (inboxSelected) NavyDeep else PureWhite,
                ),
            )
        }
        if (uiState.dueReviewCount > 0) {
            val modelSelected = tab == BrainTab.MODEL
            put(
                BrainTab.MODEL,
                BrainTabBadge(
                    text = uiState.dueReviewCount.toString(),
                    color = if (modelSelected) PureWhite else NavyDeep,
                    textColor = if (modelSelected) NavyDeep else PureWhite,
                ),
            )
        }
        when {
            uiState.overdueCount > 0 -> put(BrainTab.MOTION, BrainTabBadge(uiState.overdueCount.toString(), DeleteRed))
            uiState.dueTodayCount > 0 -> put(BrainTab.MOTION, BrainTabBadge(uiState.dueTodayCount.toString()))
        }
    }

    VectorTheme {
        Scaffold(
            containerColor = PureWhite,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {},
                    containerColor = if (uiState.isRecording) DeleteRed else NavyDeep,
                    contentColor = PureWhite,
                    modifier = Modifier.pointerInput(viewModel, recorder, tab) {
                        coroutineScope {
                            awaitEachGesture {
                                // Initial pass: the FAB's own clickable consumes the up event in the Main pass.
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    waitForUpOrCancellation(PointerEventPass.Initial) != null
                                }
                                if (released == true) {
                                    when (tab) {
                                        BrainTab.MEMORY -> showNewMemory = true
                                        BrainTab.MODEL -> showNewLearnTile = true
                                        BrainTab.MOTION -> showNewMotionList = true
                                        else -> viewModel.openCaptureSheet()
                                    }
                                } else if (released == null) {
                                    viewModel.setRecording(true)
                                    recorder.startInInbox { viewModel.showSnackbar(it) }
                                    waitForUpOrCancellation(PointerEventPass.Initial)
                                    val path = recorder.stop()
                                    viewModel.setRecording(false)
                                    if (path != null) viewModel.saveRecordingToInbox(path)
                                }
                            }
                        }
                    },
                ) {
                    when (tab) {
                        BrainTab.MEMORY -> Icon(Icons.Default.Add, contentDescription = "New memory")
                        BrainTab.MODEL -> Icon(Icons.Default.Add, contentDescription = "New learn tile")
                        BrainTab.MOTION -> Icon(Icons.Default.Add, contentDescription = "New list")
                        else -> Icon(EditSquareIcon, contentDescription = "Quick capture")
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                BrainTabBar(
                    selected = tab,
                    onSelect = viewModel::selectTab,
                    badges = badges,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = when (tab) {
                                    BrainTab.MEMORY -> "Search memories"
                                    BrainTab.MODEL -> "Search learning"
                                    BrainTab.INBOX -> "Search Inbox"
                                    else -> "Search Brain"
                                },
                                color = PureBlack.copy(alpha = 0.45f),
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NavyDeep) },
                        trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = PureBlack)
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = brainFieldColors(),
                    )
                    if (tab.hasTagFilter) {
                        TagFilterButton(
                            activeCount = uiState.tagFilter.size,
                            onClick = { showTagFilter = true },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                val results = uiState.searchResults
                when {
                    results != null && !tab.filtersLocally ->
                        SearchResultsPanel(results = results, modifier = Modifier.weight(1f))
                    tab == BrainTab.MEMORY -> MemorySection(
                        viewModel = viewModel,
                        onOpenMemory = onOpenMemory,
                        modifier = Modifier.weight(1f),
                    )
                    tab == BrainTab.MODEL -> ModelSection(
                        viewModel = viewModel,
                        onOpenTile = onOpenTile,
                        modifier = Modifier.weight(1f),
                    )
                    tab == BrainTab.MOTION -> MotionSection(
                        viewModel = viewModel,
                        onOpenSupermarket = onOpenSupermarket,
                        onOpenList = onOpenMotionList,
                        modifier = Modifier.weight(1f),
                    )
                    else -> InboxSection(
                        viewModel = viewModel,
                        onOpenPlayer = onOpenPlayer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        QuickCaptureSheet(
            visible = uiState.showCaptureSheet,
            viewModel = viewModel,
            onVoiceStart = {
                viewModel.closeCaptureSheet()
                startVoiceCapture()
            },
            onDismiss = {},
        )

        FileToSheet(
            visible = uiState.showFileToSheet,
            viewModel = viewModel,
            onDismiss = {},
        )

        if (showNewMemory) {
            NewMemoryDialog(
                library = tagLibrary,
                onCreate = viewModel::createMemoryTile,
                onDismiss = { showNewMemory = false },
            )
        }

        if (showNewLearnTile) {
            NewMemoryDialog(
                library = learnTagLibrary,
                onCreate = viewModel::createLearnTile,
                onDismiss = { showNewLearnTile = false },
                heading = "New learn tile",
            )
        }

        if (showNewMotionList) {
            NewMemoryDialog(
                library = emptyList(),
                onCreate = { name, _ -> viewModel.createMotionList(name) },
                onDismiss = { showNewMotionList = false },
                heading = "New list",
            )
        }

        if (showTagFilter) {
            val inUse = if (tab == BrainTab.MODEL) {
                learnTiles.flatMap { it.node.tags }
            } else {
                memoryTiles.flatMap { it.node.tags }
            }
            val available = (inUse + uiState.tagFilter)
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
            TagFilterDialog(
                available = available,
                selected = uiState.tagFilter,
                onApply = viewModel::setTagFilter,
                onDismiss = { showTagFilter = false },
            )
        }
    }
}

@Composable
private fun TagFilterButton(
    activeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = activeCount > 0
    val shape = RoundedCornerShape(12.dp)
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
                .background(if (active) NavyDeep else PureWhite)
                .border(1.dp, NavyDeep.copy(alpha = if (active) 1f else 0.35f), shape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = "Filter by tags",
                tint = if (active) PureWhite else NavyDeep,
            )
        }
        if (active) {
            Text(
                text = activeCount.toString(),
                color = PureWhite,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .background(ElectricBlue, CircleShape)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}
