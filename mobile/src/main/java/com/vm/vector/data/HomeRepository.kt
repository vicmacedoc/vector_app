package com.vm.vector.data

import com.vm.core.models.CalendarDraft
import com.vm.core.models.CalendarDraftKey
import com.vm.core.models.DailyHomeEntry
import com.vm.core.models.SleepEntry
import com.vm.core.models.VectorDatabase
import com.vm.vector.data.analysis.AnalysisStats
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

data class DayCompletion(
    val date: String,
    val percent: Int,
    val hasData: Boolean,
    /** Unsaved Calendar edits exist for this category/day. */
    val hasDraft: Boolean = false,
)

class HomeRepository(
    private val database: VectorDatabase,
    private val foodRepository: FoodRepository,
    private val activityRepository: ActivityRepository,
    private val workoutRepository: WorkoutRepository,
    private val selfRepository: SelfRepository,
    private val sleepRepository: SleepRepository,
    private val draftRepository: CalendarDraftRepository,
) {
    private val dao = database.dailyHomeEntryDao()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun getTodayEntry(): Flow<DailyHomeEntry?> = dao.getByDate(today())

    suspend fun getTodayEntrySync(): DailyHomeEntry? = dao.getByDateSync(today())

    /** Returns the date string (yyyy-MM-dd) for today plus [dayOffset] calendar days. */
    fun dateForOffset(dayOffset: Int): String {
        val cal = Calendar.getInstance(Locale.US)
        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, dayOffset)
        return dateFormat.format(cal.time)
    }

    suspend fun getEntryForDate(date: String): DailyHomeEntry? = dao.getByDateSync(date)

    suspend fun saveForDate(
        date: String,
        weightKg: Double?,
        bodyFatPercent: Double?,
        dailyPlanText: String?,
        dailyPlanAudioPath: String?,
    ) {
        val entry = DailyHomeEntry(
            date = date,
            weightKg = weightKg,
            bodyFatPercent = bodyFatPercent,
            dailyPlanText = dailyPlanText,
            dailyPlanAudioPath = dailyPlanAudioPath,
        )
        dao.insert(entry)
    }

    suspend fun saveToday(
        weightKg: Double?,
        bodyFatPercent: Double?,
        dailyPlanText: String?,
        dailyPlanAudioPath: String?,
    ) {
        val entry = DailyHomeEntry(
            date = today(),
            weightKg = weightKg,
            bodyFatPercent = bodyFatPercent,
            dailyPlanText = dailyPlanText,
            dailyPlanAudioPath = dailyPlanAudioPath,
        )
        dao.insert(entry)
    }

    suspend fun getTodayDailyPlanAudioPath(): String? {
        return dao.getByDateSync(today())?.dailyPlanAudioPath
    }

    /**
     * Last 7 days (oldest first) for Routine (Activities + the sleep shown on that day).
     * hasData if day D has saved activities, or last night's row (D−1) has actual times or stages.
     */
    suspend fun getRoutineCompletionLast7Days(endDate: String = today()): List<DayCompletion> {
        val drafts = draftDatesFor(CalendarDraft.CATEGORY_ROUTINE, endDate)
        return last7Dates(endDate).map { date ->
            val hasActivity = activityRepository.getEntriesForDate(date).isNotEmpty()
            val hasData = hasActivity || sleepRepository.getNight(previousDate(date)).hasLoggedSleep()
            DayCompletion(
                date = date,
                percent = if (hasData) 100 else 0,
                hasData = hasData,
                hasDraft = date in drafts,
            )
        }
    }

    /** Last 7 days (oldest first) for Food completion % per day. */
    suspend fun getFoodCompletionLast7Days(endDate: String = today()): List<DayCompletion> {
        val drafts = draftDatesFor(CalendarDraft.CATEGORY_FOOD, endDate)
        return last7Dates(endDate).map { date ->
            val entries = foodRepository.getEntriesByDateSync(date)
            val total = entries.size
            val completed = entries.count { it.isChecked }
            val percent = if (total == 0) 0 else (100 * completed / total)
            DayCompletion(date = date, percent = percent, hasData = total > 0, hasDraft = date in drafts)
        }
    }

    /** Last 7 days (oldest first) for Workout/Exercise completion % per day. */
    suspend fun getWorkoutCompletionLast7Days(endDate: String = today()): List<DayCompletion> {
        val drafts = draftDatesFor(CalendarDraft.CATEGORY_EXERCISE, endDate)
        return last7Dates(endDate).map { date ->
            val sets = workoutRepository.getSetsByDateSync(date)
            val total = sets.size
            val completed = sets.count { AnalysisStats.workoutSetCountsAsCompleted(it) }
            val percent = if (total == 0) 0 else (100 * completed / total)
            DayCompletion(date = date, percent = percent, hasData = total > 0, hasDraft = date in drafts)
        }
    }

    /**
     * Last 7 days (oldest first) for Self: hasData when a saved [com.vm.core.models.SelfEntry]
     * carries any signal, from a single mood tap to a full journal.
     */
    suspend fun getSelfCompletionLast7Days(endDate: String = today()): List<DayCompletion> {
        val drafts = draftDatesFor(CalendarDraft.CATEGORY_SELF, endDate)
        return last7Dates(endDate).map { date ->
            val hasContent = selfRepository.getSelfEntryByDateSync(date)?.hasAnyContent() == true
            DayCompletion(
                date = date,
                percent = if (hasContent) 100 else 0,
                hasData = hasContent,
                hasDraft = date in drafts,
            )
        }
    }

    /** Emits whenever any Calendar draft is written or cleared. */
    fun observeDraftChanges(): Flow<List<CalendarDraftKey>> =
        draftRepository.observeKeysBetween("0000-00-00", "9999-99-99")

    /**
     * Deletes saved data and any draft for [category] (a [CalendarDraft] category) on [date],
     * so the Calendar falls back to the preset. Routine also drops last night's sleep row (D−1).
     */
    suspend fun clearDay(category: String, date: String) {
        when (category) {
            CalendarDraft.CATEGORY_ROUTINE -> {
                activityRepository.deleteEntriesForDate(date)
                sleepRepository.deleteNight(previousDate(date))
            }
            CalendarDraft.CATEGORY_FOOD -> foodRepository.replaceEntriesForDate(date, emptyList())
            CalendarDraft.CATEGORY_EXERCISE -> workoutRepository.deleteWorkoutForDate(date)
            CalendarDraft.CATEGORY_SELF -> selfRepository.deleteSelfEntry(date)
        }
        draftRepository.delete(category, date)
    }

    private suspend fun draftDatesFor(category: String, endDate: String): Set<String> {
        val dates = last7Dates(endDate)
        return draftRepository.getKeysBetween(dates.first(), dates.last())
            .filter { it.category == category }
            .mapTo(mutableSetOf()) { it.date }
    }

    private fun SleepEntry?.hasLoggedSleep(): Boolean = this != null && (
        actualBedtime != null || actualWakeUp != null || fellAsleepAt != null ||
            deepMinutes != null || lightMinutes != null || remMinutes != null || awakeMinutes != null
        )

    private fun previousDate(date: String): String {
        val parsed = runCatching { dateFormat.parse(date) }.getOrNull() ?: return date
        val cal = Calendar.getInstance(Locale.US)
        cal.time = parsed
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return dateFormat.format(cal.time)
    }

    private fun today(): String = dateFormat.format(Date())

    /** Returns the 7 days ending at [endDate] (yyyy-MM-dd): [end-6, ..., end]. */
    private fun last7Dates(endDate: String): List<String> {
        val end = try {
            dateFormat.parse(endDate) ?: Date()
        } catch (_: Exception) {
            Date()
        }
        val cal = Calendar.getInstance(Locale.US)
        return (6 downTo 0).map { offset ->
            cal.time = end
            cal.add(Calendar.DAY_OF_YEAR, -offset)
            dateFormat.format(cal.time)
        }
    }
}
