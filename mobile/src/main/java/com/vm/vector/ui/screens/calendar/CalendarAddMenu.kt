package com.vm.vector.ui.screens.calendar

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.core.ui.theme.DropSetOrange
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.CalendarAiMode

enum class CalendarAddAction {
    SINGLE_CLIPBOARD,
    SINGLE_AI,
    SINGLE_LIBRARY,
    SINGLE_NEW,
    MULTI_CLIPBOARD,
    MULTI_AI,
    MULTI_GOOGLE_CALENDAR,
    PLAN_SCRATCH,
    PLAN_PRESET,
}

private enum class AddMenuPage { MAIN, SINGLE, MULTIPLE, PLAN }

/**
 * Entry point of the Calendar edit FAB. The first page picks the mode (add one, add several, or
 * plan the whole day); each mode opens its own page of sources with a back arrow.
 * Plan actions replace every item on the day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarAddMenuSheet(
    itemNoun: String,
    itemNounPlural: String,
    showGoogleCalendar: Boolean,
    presetAvailable: Boolean,
    onDismiss: () -> Unit,
    onAction: (CalendarAddAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var page by rememberSaveable { mutableStateOf(AddMenuPage.MAIN) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PureWhite,
    ) {
        BackHandler(enabled = page != AddMenuPage.MAIN) { page = AddMenuPage.MAIN }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != AddMenuPage.MAIN
                val enter = slideInHorizontally { width -> if (forward) width else -width } + fadeIn()
                val exit = slideOutHorizontally { width -> if (forward) -width else width } + fadeOut()
                (enter togetherWith exit).using(SizeTransform(clip = false))
            },
            label = "calendarAddMenuPage",
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                when (current) {
                    AddMenuPage.MAIN -> {
                        Text(
                            "Edit day",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PureBlack,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        MenuRow(
                            Icons.Default.Add,
                            "Add one $itemNoun",
                            "Clipboard, AI, library or a blank form",
                            showChevron = true,
                        ) { page = AddMenuPage.SINGLE }
                        MenuRow(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            "Add multiple $itemNounPlural",
                            if (showGoogleCalendar) "Clipboard, AI or Google Calendar" else "Clipboard or AI",
                            showChevron = true,
                        ) { page = AddMenuPage.MULTIPLE }
                        MenuRow(
                            Icons.Default.EditCalendar,
                            "Plan the day",
                            "Replace the whole day with an AI plan",
                            showChevron = true,
                        ) { page = AddMenuPage.PLAN }
                    }
                    AddMenuPage.SINGLE -> {
                        MenuPageHeader("Add one $itemNoun") { page = AddMenuPage.MAIN }
                        MenuRow(Icons.Default.ContentPaste, "Clipboard", "Paste exactly one JSON object") {
                            onAction(CalendarAddAction.SINGLE_CLIPBOARD)
                        }
                        MenuRow(Icons.Default.AutoAwesome, "AI", "Describe one $itemNoun") {
                            onAction(CalendarAddAction.SINGLE_AI)
                        }
                        MenuRow(Icons.Default.CollectionsBookmark, "Library", "Search saved $itemNounPlural") {
                            onAction(CalendarAddAction.SINGLE_LIBRARY)
                        }
                        MenuRow(Icons.Default.Add, "New", "Fill the form by hand") {
                            onAction(CalendarAddAction.SINGLE_NEW)
                        }
                    }
                    AddMenuPage.MULTIPLE -> {
                        MenuPageHeader("Add multiple $itemNounPlural") { page = AddMenuPage.MAIN }
                        MenuRow(Icons.Default.ContentPaste, "Clipboard", "Paste a JSON array (or wrapper object)") {
                            onAction(CalendarAddAction.MULTI_CLIPBOARD)
                        }
                        MenuRow(Icons.Default.AutoAwesome, "AI", "Describe several $itemNounPlural") {
                            onAction(CalendarAddAction.MULTI_AI)
                        }
                        if (showGoogleCalendar) {
                            MenuRow(Icons.Default.Event, "Google Calendar", "Import this day's events") {
                                onAction(CalendarAddAction.MULTI_GOOGLE_CALENDAR)
                            }
                        }
                    }
                    AddMenuPage.PLAN -> {
                        MenuPageHeader("Plan the day") { page = AddMenuPage.MAIN }
                        Text(
                            "Replaces every $itemNoun on this day, preset items included.",
                            color = SlateGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        MenuRow(Icons.Default.EditCalendar, "From scratch", "AI builds the day from your description") {
                            onAction(CalendarAddAction.PLAN_SCRATCH)
                        }
                        MenuRow(
                            icon = Icons.Default.EventRepeat,
                            title = "From preset",
                            subtitle = if (presetAvailable) {
                                "AI adapts this weekday's preset"
                            } else {
                                "No active preset for this weekday. Load one in Settings."
                            },
                            enabled = presetAvailable,
                        ) {
                            onAction(CalendarAddAction.PLAN_PRESET)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuPageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NavyDeep)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = PureBlack,
            modifier = Modifier.offset(x = (-8).dp),
        )
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    showChevron: Boolean = false,
    onClick: () -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = NavyDeep.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = PureWhite,
                modifier = Modifier.padding(8.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = PureBlack.copy(alpha = contentAlpha), fontSize = 15.sp)
            Text(subtitle, color = SlateGray.copy(alpha = contentAlpha), fontSize = 12.sp)
        }
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SlateGray,
            )
        }
    }
}

/** Warning strip for review dialogs whose confirm replaces the whole day. */
@Composable
fun ReplacesDayBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DropSetOrange.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = DropSetOrange,
            modifier = Modifier.size(18.dp),
        )
        Text(text, color = PureBlack, fontSize = 12.sp)
    }
}

fun calendarAiDialogTitle(mode: CalendarAiMode): String = when (mode) {
    CalendarAiMode.SINGLE -> "Add one with AI"
    CalendarAiMode.MULTIPLE -> "Add multiple with AI"
    CalendarAiMode.PLAN_SCRATCH -> "Plan day from scratch"
    CalendarAiMode.PLAN_PRESET -> "Plan day from preset"
}

fun readClipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
}

@Composable
internal fun LibraryEmptyText(text: String) {
    Text(
        text,
        color = SlateGray,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(OffWhite, RoundedCornerShape(8.dp))
            .padding(12.dp),
    )
}
