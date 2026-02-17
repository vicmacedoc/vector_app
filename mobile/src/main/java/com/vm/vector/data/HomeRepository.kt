package com.vm.vector.data

import android.content.Context
import androidx.room.Room
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
    private val context: Context,
    private val dietRepository: DietRepository,
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val diaryRepository: DiaryRepository,
) {
    private val database: VectorDatabase = Room.databaseBuilder(
        context,
        VectorDatabase::class.java,
        "vector_database"
    )
        .fallbackToDestructiveMigration()
        .build()

    private val dao = database.dailyHomeEntryDao()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun getTodayEntry(): Flow<DailyHomeEntry?> = dao.getByDate(today())

    suspend fun getTodayEntrySync(): DailyHomeEntry? = dao.getByDateSync(today())

    suspend fun saveToday(
        weightKg: Double?,
        bodyFatPercent: Double?,
        sleepStart: String?,
        sleepEnd: String?,
        dailyPlanText: String?,
        dailyPlanCompletionPercent: Int
    ) {
        val entry = DailyHomeEntry(
            date = today(),
            weightKg = weightKg,
            bodyFatPercent = bodyFatPercent,
            sleepStart = sleepStart,
            sleepEnd = sleepEnd,
            dailyPlanText = dailyPlanText,
            dailyPlanCompletionPercent = dailyPlanCompletionPercent.coerceIn(0, 100)
        )
        dao.insert(entry)
    }

    /** Last 7 days (oldest first) for Routine completion % per day. */
    suspend fun getRoutineCompletionLast7Days(): List<DayCompletion> = last7Dates().map { date ->
        val entries = routineRepository.getEntriesByDateSync(date)
        val total = entries.size
        val completed = entries.count { e ->
            e.status == RoutineStatus.DONE || (e.type == RoutineType.NUMERICAL && e.isGoalMet)
        }
        val percent = if (total == 0) 0 else (100 * completed / total)
        DayCompletion(date = date, percent = percent, hasData = total > 0)
    }

    /** Last 7 days (oldest first) for Diet/Nutrition completion % per day. */
    suspend fun getDietCompletionLast7Days(): List<DayCompletion> = last7Dates().map { date ->
        val entries = dietRepository.getEntriesByDateSync(date)
        val total = entries.size
        val completed = entries.count { it.isChecked }
        val percent = if (total == 0) 0 else (100 * completed / total)
        DayCompletion(date = date, percent = percent, hasData = total > 0)
    }

    /** Last 7 days (oldest first) for Workout/Exercise completion % per day. */
    suspend fun getWorkoutCompletionLast7Days(): List<DayCompletion> = last7Dates().map { date ->
        val sets = workoutRepository.getSetsByDateSync(date)
        val total = sets.size
        val completed = sets.count { it.isCompleted }
        val percent = if (total == 0) 0 else (100 * completed / total)
        DayCompletion(date = date, percent = percent, hasData = total > 0)
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
