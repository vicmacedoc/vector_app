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
import com.vm.vector.data.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE diet_logs ADD COLUMN notEaten INTEGER NOT NULL DEFAULT 0")
    }
}

class VectorApplication : Application() {
    val database: VectorDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            VectorDatabase::class.java,
            "vector_database"
        )
            .addMigrations(MIGRATION_15_16)
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

    fun getAndClearPendingWearData(date: String): List<WorkoutSet> {
        return pendingWearData.remove(date) ?: emptyList()
    }

    /** Call after merging wear data into the current view so it is not applied again when switching dates. */
    fun clearPendingWearData(date: String) {
        pendingWearData.remove(date)
    }

    private val _wearDataReceived = MutableSharedFlow<Pair<String, List<WorkoutSet>>>(replay = 0, extraBufferCapacity = 1)
    val wearDataReceived: kotlinx.coroutines.flow.Flow<Pair<String, List<WorkoutSet>>> = _wearDataReceived

    private val _wearWorkoutReceivedMessage = MutableStateFlow<String?>(null)
    val wearWorkoutReceivedMessage: StateFlow<String?> = _wearWorkoutReceivedMessage.asStateFlow()

    fun setWearWorkoutReceivedMessage(message: String) {
        _wearWorkoutReceivedMessage.value = message
    }

    fun clearWearWorkoutReceivedMessage() {
        _wearWorkoutReceivedMessage.value = null
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
