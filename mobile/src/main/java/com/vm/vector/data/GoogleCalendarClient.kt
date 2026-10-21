package com.vm.vector.data

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections

/**
 * Reads Google Calendar events for a single local calendar day and maps them to
 * [ActivityImportDraft]s for the Routine import wizard.
 */
class GoogleCalendarClient(
    private val context: Context,
    private val authManager: GoogleDriveAuthManager,
) {
    sealed class FetchResult {
        data class Success(val drafts: List<ActivityImportDraft>) : FetchResult()
        data object Unauthorized : FetchResult()
        data object NeedsCalendarPermission : FetchResult()
        data class NeedsUserRecovery(val intent: android.content.Intent) : FetchResult()
        data class Error(val message: String) : FetchResult()
    }

    suspend fun fetchActivitiesForDate(dateYyyyMmDd: String): FetchResult = withContext(Dispatchers.IO) {
        val account = authManager.getLastSignedInAccount()
            ?: return@withContext FetchResult.Unauthorized

        if (!authManager.hasCalendarPermission(account)) {
            return@withContext FetchResult.NeedsCalendarPermission
        }

        try {
            val calendar = buildCalendar(account)
            val zone = ZoneId.systemDefault()
            val day = LocalDate.parse(dateYyyyMmDd, DAY_FMT)
            val timeMin = com.google.api.client.util.DateTime(
                day.atStartOfDay(zone).toInstant().toEpochMilli()
            )
            val timeMax = com.google.api.client.util.DateTime(
                day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            )

            val events = calendar.events()
                .list("primary")
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute()
                .items
                .orEmpty()

            val drafts = events.mapNotNull { event ->
                event.toDraft(day, zone)
            }
            FetchResult.Success(drafts)
        } catch (e: Exception) {
            val recoverable = e as? UserRecoverableAuthIOException
                ?: e.cause as? UserRecoverableAuthIOException
            if (recoverable != null) {
                return@withContext FetchResult.NeedsUserRecovery(recoverable.intent)
            }
            Log.e(TAG, "fetchActivitiesForDate failed for $dateYyyyMmDd", e)
            FetchResult.Error(friendlyCalendarErrorMessage(e))
        }
    }

    private fun friendlyCalendarErrorMessage(e: Exception): String {
        val gje = e as? GoogleJsonResponseException ?: e.cause as? GoogleJsonResponseException
        val apiReason = gje?.details?.errors?.firstOrNull()?.reason
        when (apiReason) {
            "SERVICE_DISABLED", "accessNotConfigured" -> {
                return "Google Calendar API is not enabled for this app’s Google Cloud project. " +
                    "In Google Cloud Console → APIs & Services → Library, enable “Google Calendar API” " +
                    "(same OAuth project as Drive), wait a minute, then tap Retry."
            }
        }
        if (gje?.statusCode == 403) {
            return "Calendar access was denied (403). Enable Google Calendar API in Google Cloud Console " +
                "for your OAuth project, then retry."
        }
        val raw = e.message.orEmpty()
        if (raw.contains("SERVICE_DISABLED") || raw.contains("calendar-json.googleapis.com")) {
            return "Google Calendar API is not enabled. Enable “Google Calendar API” in Google Cloud Console " +
                "for your OAuth project, then retry."
        }
        return raw.ifBlank { "Failed to load Google Calendar" }
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: "Failed to load Google Calendar"
    }

    private fun buildCalendar(account: GoogleSignInAccount): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(CalendarScopes.CALENDAR_READONLY),
        ).apply { selectedAccountName = account.email }
        return Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName(APP_NAME)
            .build()
    }

    private fun Event.toDraft(day: LocalDate, zone: ZoneId): ActivityImportDraft? {
        if (status.equals("cancelled", ignoreCase = true)) return null
        val title = summary.asOptionalImportValue() ?: "(No title)"
        val description = this.description.asOptionalImportValue().orEmpty()
        val start = start ?: return null
        val end = end ?: start

        val (startHhMm, endHhMm) = resolveLocalTimes(start, end, day, zone)
            ?: return null

        return ActivityImportDraft(
            title = title,
            startTime = startHhMm,
            endTime = endHhMm,
            labelName = null, // Calendar has no Vector labels — wizard keeps default
            type = ActivityImportDefaults.TYPE,
            description = description,
            source = ActivityImportSource.GOOGLE_CALENDAR,
            externalId = id,
        )
    }

    /**
     * Maps event start/end onto HH:mm for [day] in [zone].
     * All-day events default to 09:00–10:00 so the wizard can adjust.
     * Multi-day timed events are clipped to the portion on [day].
     */
    private fun resolveLocalTimes(
        start: EventDateTime,
        end: EventDateTime,
        day: LocalDate,
        zone: ZoneId,
    ): Pair<String, String>? {
        // All-day: Google end.date is exclusive (date-only RFC3339, e.g. 2026-07-26)
        if (start.date != null && start.dateTime == null) {
            val rangeStart = parseDateOnly(start.date) ?: return null
            val rangeEndExclusive = parseDateOnly(end.date) ?: rangeStart.plusDays(1)
            if (day.isBefore(rangeStart) || !day.isBefore(rangeEndExclusive)) return null
            return ActivityImportDefaults.START_TIME to ActivityImportDefaults.END_TIME
        }

        val startInstant = start.dateTime?.value?.let { Instant.ofEpochMilli(it) } ?: return null
        val endInstant = end.dateTime?.value?.let { Instant.ofEpochMilli(it) } ?: startInstant
        val startZoned = startInstant.atZone(zone)
        val endZoned = endInstant.atZone(zone)
        val dayStart = day.atStartOfDay(zone)
        val dayEnd = day.plusDays(1).atStartOfDay(zone)

        if (endZoned <= dayStart || startZoned >= dayEnd) return null

        val clippedStart = maxOf(startZoned, dayStart).toLocalTime()
        val clippedEndExclusive = minOf(endZoned, dayEnd)
        val clippedEnd = if (clippedEndExclusive == dayEnd) {
            LocalTime.of(23, 59)
        } else {
            clippedEndExclusive.toLocalTime()
        }

        return formatHhMm(clippedStart) to formatHhMm(clippedEnd)
    }

    private fun parseDateOnly(dateTime: com.google.api.client.util.DateTime?): LocalDate? {
        if (dateTime == null) return null
        val rfc = dateTime.toStringRfc3339()
        return runCatching { LocalDate.parse(rfc.take(10), DAY_FMT) }.getOrNull()
    }

    private fun formatHhMm(time: LocalTime): String =
        String.format(java.util.Locale.US, "%02d:%02d", time.hour, time.minute)

    companion object {
        private const val TAG = "GoogleCalendar"
        private const val APP_NAME = "Vector"
        private val DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
