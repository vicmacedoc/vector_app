package com.vm.vector.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vm.vector.VectorApplication
import com.vm.vector.alarm.SleepAlarmScheduler
import com.vm.vector.data.ActivityRepository
import com.vm.vector.data.CalendarDraftRepository
import com.vm.vector.data.DatabaseBackupManager
import com.vm.vector.data.DriveService
import com.vm.vector.data.FoodRepository
import com.vm.vector.data.GeminiApiKeyStore
import com.vm.vector.data.GoogleDriveAuthManager
import com.vm.vector.data.HabitRepository
import com.vm.vector.data.HomeRepository
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.PresetStorage
import com.vm.vector.data.SelfRepository
import com.vm.vector.data.SleepRepository
import com.vm.vector.data.WorkoutRepository

class SettingsViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {

    private val database = (context.applicationContext as VectorApplication).database
    private val preferenceManager = PreferenceManager(context)
    private val geminiApiKeyStore = GeminiApiKeyStore(context)
    private val authManager = GoogleDriveAuthManager(context)
    private val driveService = DriveService(context, authManager)
    private val presetStorage = PresetStorage(context, preferenceManager)
    private val foodRepository = FoodRepository(database, preferenceManager, presetStorage)
    private val habitRepository = HabitRepository(database)
    private val activityRepository = ActivityRepository(database, presetStorage)
    private val workoutRepository = WorkoutRepository(context, database, preferenceManager, presetStorage)
    private val selfRepository = SelfRepository(context, database, preferenceManager, driveService)
    private val sleepRepository = SleepRepository(database)
    private val homeRepository = HomeRepository(
        database,
        foodRepository,
        activityRepository,
        workoutRepository,
        selfRepository,
        sleepRepository,
        CalendarDraftRepository(database),
    )
    private val backupManager = DatabaseBackupManager(context, database, driveService, preferenceManager)
    private val sleepAlarmScheduler = SleepAlarmScheduler(context)
    private val vectorApplication = context.applicationContext as VectorApplication

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(
                preferenceManager,
                driveService,
                foodRepository,
                workoutRepository,
                presetStorage,
                backupManager,
                homeRepository,
                sleepRepository,
                sleepAlarmScheduler,
                vectorApplication,
                geminiApiKeyStore,
                activityRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
