package com.vm.vector

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vm.core.models.VectorDatabase
import com.vm.core.models.WorkoutSet
import com.vm.vector.data.DatabaseBackupManager
import com.vm.vector.data.DriveService
import com.vm.vector.data.GoogleDriveAuthManager
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.PresetStorage
import com.vm.vector.data.WearMessageReceiver
import com.vm.vector.data.RoutineRepository
import com.vm.vector.data.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE diet_logs ADD COLUMN notEaten INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_routine_logs_date ON routine_logs (`date`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_diet_logs_date ON diet_logs (`date`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_workout_sets_date ON workout_sets (`date`)"
        )
    }
}

private val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN targetMuscles TEXT NOT NULL DEFAULT ''")
    }
}

class VectorApplication : Application() {
    val database: VectorDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            VectorDatabase::class.java,
            "vector_database"
        )
            .addMigrations(MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
            .fallbackToDestructiveMigration()
            .build()
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Pending workout sets received from Wear; merged when user loads that date in Calendar. */
    private val pendingWearData = ConcurrentHashMap<String, MutableList<WorkoutSet>>()

    /**
     * In-memory workout sets by date, kept in sync by CalendarViewModel.
     * Used when the Wear app requests workouts so it gets what the user sees on mobile (saved or not).
     */
    private val workoutSetsForWearRequest = ConcurrentHashMap<String, List<WorkoutSet>>()

    fun setWorkoutSetsForWearRequest(date: String, sets: List<WorkoutSet>) {
        workoutSetsForWearRequest[date] = sets
    }

    /**
     * Returns workout sets for the given date if the Calendar has loaded/displayed that date, else null.
     */
    fun getWorkoutSetsForWearRequest(date: String): List<WorkoutSet>? = workoutSetsForWearRequest[date]

    fun addPendingWearData(date: String, sets: List<WorkoutSet>) {
        if (sets.isEmpty()) return
        pendingWearData.getOrPut(date) { mutableListOf() }.let { list ->
            val byId = list.associateBy { it.id }.toMutableMap()
            sets.forEach { byId[it.id] = it }
            list.clear()
            list.addAll(byId.values)
        }
        applicationScope.launch {
            _wearDataReceived.emit(date to sets)
        }
    }

    /** Persists merged watch workout to Room (same merge as Calendar) so it survives without tapping Save Changes. */
    fun persistWearWorkoutCompletion(date: String, sets: List<WorkoutSet>) {
        if (sets.isEmpty()) return
        applicationScope.launch(Dispatchers.IO) {
            workoutRepository.mergeWearCompletionAndSave(date, sets)
        }
    }

    fun getAndClearPendingWearData(date: String): List<WorkoutSet> {
        return pendingWearData.remove(date) ?: emptyList()
    }

    /** Call after merging wear data into the current view so it is not applied again when switching dates. */
    fun clearPendingWearData(date: String) {
        pendingWearData.remove(date)
    }

    /** Pending routine numerical updates from Wear; merged when Calendar loads that date (same pattern as workouts). */
    private val pendingRoutineWearData = ConcurrentHashMap<String, MutableMap<String, Double>>()

    fun addPendingRoutineWearData(date: String, entryId: String, currentValue: Double) {
        pendingRoutineWearData.getOrPut(date) { mutableMapOf() }[entryId] = currentValue
        applicationScope.launch {
            _wearRoutineReceived.emit(Triple(date, entryId, currentValue))
        }
    }

    fun getAndClearPendingRoutineWearData(date: String): Map<String, Double> {
        return pendingRoutineWearData.remove(date) ?: emptyMap()
    }

    /** After merging a single entry from the live [wearRoutineReceived] stream. */
    fun removePendingRoutineWearEntry(date: String, entryId: String) {
        pendingRoutineWearData[date]?.remove(entryId)
        if (pendingRoutineWearData[date]?.isEmpty() != false) {
            pendingRoutineWearData.remove(date)
        }
    }

    fun clearPendingRoutineWearData(date: String) {
        pendingRoutineWearData.remove(date)
    }

    private val _wearRoutineReceived =
        MutableSharedFlow<Triple<String, String, Double>>(replay = 1, extraBufferCapacity = 32)
    val wearRoutineReceived: Flow<Triple<String, String, Double>> = _wearRoutineReceived

    private val _wearRoutineReceivedMessage = MutableStateFlow<String?>(null)
    val wearRoutineReceivedMessage: StateFlow<String?> = _wearRoutineReceivedMessage.asStateFlow()

    fun setWearRoutineReceivedMessage(message: String) {
        _wearRoutineReceivedMessage.value = message
    }

    fun clearWearRoutineReceivedMessage() {
        _wearRoutineReceivedMessage.value = null
    }

    private val _wearDataReceived = MutableSharedFlow<Pair<String, List<WorkoutSet>>>(replay = 1, extraBufferCapacity = 32)
    val wearDataReceived: kotlinx.coroutines.flow.Flow<Pair<String, List<WorkoutSet>>> = _wearDataReceived

    private val _wearWorkoutReceivedMessage = MutableStateFlow<String?>(null)
    val wearWorkoutReceivedMessage: StateFlow<String?> = _wearWorkoutReceivedMessage.asStateFlow()

    fun setWearWorkoutReceivedMessage(message: String) {
        _wearWorkoutReceivedMessage.value = message
    }

    fun clearWearWorkoutReceivedMessage() {
        _wearWorkoutReceivedMessage.value = null
    }

    /** Emitted after [DietRepository.resetDatabase] succeeds so UI can reload from Room. */
    private val _databaseResetEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val databaseResetEvents: SharedFlow<Unit> = _databaseResetEvents.asSharedFlow()

    fun notifyDatabaseReset() {
        _databaseResetEvents.tryEmit(Unit)
    }

    private val backupManager: DatabaseBackupManager by lazy {
        val prefManager = PreferenceManager(this)
        val authManager = GoogleDriveAuthManager(this)
        val driveService = DriveService(this, authManager)
        DatabaseBackupManager(this, database, driveService, prefManager)
    }

    val workoutRepository: WorkoutRepository by lazy {
        val prefManager = PreferenceManager(this)
        val authManager = GoogleDriveAuthManager(this)
        val driveService = DriveService(this, authManager)
        val presetStorage = PresetStorage(this, prefManager)
        WorkoutRepository(this, database, prefManager, driveService, presetStorage)
    }

    val routineRepository: RoutineRepository by lazy {
        val prefManager = PreferenceManager(this)
        val authManager = GoogleDriveAuthManager(this)
        val driveService = DriveService(this, authManager)
        val presetStorage = PresetStorage(this, prefManager)
        RoutineRepository(database, prefManager, driveService, presetStorage)
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                applicationScope.launch(Dispatchers.IO) {
                    backupManager.backup()
                }
            }
        })
        WearMessageReceiver.register(this)
    }
}
