package com.vm.vector.data

import com.vm.core.models.DailyHomeEntry
import com.vm.core.models.RoutineStatus
import com.vm.core.models.RoutineType
import com.vm.core.models.VectorDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

data class DayCompletion(
    val date: String,
    val percent: Int,
    val hasData: Boolean
)

class HomeRepository(
    private val database: VectorDatabase,
    private val dietRepository: DietRepository,
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val diaryRepository: DiaryRepository,
    private val preferenceManager: PreferenceManager,
) {
    private val dao = database.dailyHomeEntryDao()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun getTodayEntry(): Flow<DailyHomeEntry?> = dao.getByDate(today())

    suspend fun getTodayEntrySync(): DailyHomeEntry? = dao.getByDateSync(today())

    /** Returns the date string (yyyy-MM-dd) for today + the given day offset (-1 = yesterday, 0 = today, 1 = tomorrow). */
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
        sleepStart: String?,
        sleepEnd: String?,
        actualSleepStart: String?,
        actualSleepEnd: String?,
        dailyPlanText: String?,
        dailyPlanAudioPath: String?,
        dailyPlanCompletionPercent: Int
    ) {
        val entry = DailyHomeEntry(
            date = date,
            weightKg = weightKg,
            bodyFatPercent = bodyFatPercent,
            sleepStart = sleepStart,
            sleepEnd = sleepEnd,
            actualSleepStart = actualSleepStart,
            actualSleepEnd = actualSleepEnd,
            dailyPlanText = dailyPlanText,
            dailyPlanAudioPath = dailyPlanAudioPath,
            dailyPlanCompletionPercent = dailyPlanCompletionPercent.coerceIn(0, 100)
        )
        dao.insert(entry)
    }

    suspend fun saveToday(
        weightKg: Double?,
        bodyFatPercent: Double?,
        sleepStart: String?,
        sleepEnd: String?,
        actualSleepStart: String?,
        actualSleepEnd: String?,
        dailyPlanText: String?,
        dailyPlanAudioPath: String?,
        dailyPlanCompletionPercent: Int
    ) {
        val entry = DailyHomeEntry(
            date = today(),
            weightKg = weightKg,
            bodyFatPercent = bodyFatPercent,
            sleepStart = sleepStart,
            sleepEnd = sleepEnd,
            actualSleepStart = actualSleepStart,
            actualSleepEnd = actualSleepEnd,
            dailyPlanText = dailyPlanText,
            dailyPlanAudioPath = dailyPlanAudioPath,
            dailyPlanCompletionPercent = dailyPlanCompletionPercent.coerceIn(0, 100)
        )
        dao.insert(entry)
    }

    suspend fun getTodayDailyPlanAudioPath(): String? {
        return dao.getByDateSync(today())?.dailyPlanAudioPath
    }

    /** Updates only sleep target (sleepStart=bedtime, sleepEnd=wake-up) for today, keeping other fields. */
    suspend fun saveTodaySleepTarget(sleepStart: String?, sleepEnd: String?) {
        val existing = dao.getByDateSync(today())
        val entry = DailyHomeEntry(
            date = today(),
            weightKg = existing?.weightKg,
            bodyFatPercent = existing?.bodyFatPercent,
            sleepStart = sleepStart,
            sleepEnd = sleepEnd,
            actualSleepStart = existing?.actualSleepStart,
            actualSleepEnd = existing?.actualSleepEnd,
            dailyPlanText = existing?.dailyPlanText,
            dailyPlanAudioPath = existing?.dailyPlanAudioPath,
            dailyPlanCompletionPercent = existing?.dailyPlanCompletionPercent ?: 0,
            timestamp = System.currentTimeMillis()
        )
        dao.insert(entry)
    }

    /** Last 7 days (oldest first) for Routine completion % per day. */
    suspend fun getRoutineCompletionLast7Days(): List<DayCompletion> {
        return last7Dates().map { date ->
            val entries = routineRepository.getEntriesByDateSync(date)
            val total = entries.size
            val completed = entries.count { e ->
                e.status == RoutineStatus.DONE || (e.type == RoutineType.NUMERICAL && e.isGoalMet)
            }
            val percent = if (total == 0) 0 else (100 * completed / total)
            DayCompletion(date = date, percent = percent, hasData = total > 0)
        }
    }

    /** Last 7 days (oldest first) for Diet/Nutrition completion % per day. */
    suspend fun getDietCompletionLast7Days(): List<DayCompletion> {
        return last7Dates().map { date ->
            val entries = dietRepository.getEntriesByDateSync(date)
            val total = entries.size
            val completed = entries.count { it.isChecked }
            val percent = if (total == 0) 0 else (100 * completed / total)
            DayCompletion(date = date, percent = percent, hasData = total > 0)
        }
    }

    /** Last 7 days (oldest first) for Workout/Exercise completion % per day. */
    suspend fun getWorkoutCompletionLast7Days(): List<DayCompletion> {
        return last7Dates().map { date ->
            val sets = workoutRepository.getSetsByDateSync(date)
            val total = sets.size
            val completed = sets.count { it.isCompleted }
            val percent = if (total == 0) 0 else (100 * completed / total)
            DayCompletion(date = date, percent = percent, hasData = total > 0)
        }
    }

    /** Last 7 days (oldest first) for Diary: hasData if day has mood, journal text, or any collection; percent 100 when hasData. */
    suspend fun getDiaryCompletionLast7Days(): List<DayCompletion> = last7Dates().map { date ->
        val entry = diaryRepository.getDiaryEntryByDateSync(date)
        val collections = diaryRepository.getCollectionsByDateSync(date)
        val hasContent = entry != null && (entry.mood != null || !entry.journalText.isNullOrBlank()) || collections.isNotEmpty()
        DayCompletion(date = date, percent = if (hasContent) 100 else 0, hasData = hasContent)
    }

    private fun today(): String = dateFormat.format(Date())

    /** Returns last 7 days including today: [today-6, ..., today]. */
    private fun last7Dates(): List<String> {
        val cal = Calendar.getInstance(Locale.US)
        return (6 downTo 0).map { offset ->
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -offset)
            dateFormat.format(cal.time)
        }
    }
}
