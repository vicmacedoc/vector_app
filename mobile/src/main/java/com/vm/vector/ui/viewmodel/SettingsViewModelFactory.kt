package com.vm.vector.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vm.vector.alarm.SleepAlarmScheduler
import com.vm.vector.data.DatabaseBackupManager
import com.vm.vector.data.DietRepository
import com.vm.vector.data.DiaryRepository
import com.vm.vector.data.DriveService
import com.vm.vector.data.GoogleDriveAuthManager
import com.vm.vector.data.HomeRepository
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.PresetStorage
import com.vm.vector.data.RoutineRepository
import com.vm.vector.data.WorkoutRepository
import com.vm.vector.VectorApplication

class SettingsViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {

    private val database = (context.applicationContext as VectorApplication).database
    private val preferenceManager = PreferenceManager(context)
    private val authManager = GoogleDriveAuthManager(context)
    private val driveService = DriveService(context, authManager)
    private val presetStorage = PresetStorage(context, preferenceManager)
    private val dietRepository = DietRepository(database, preferenceManager, driveService, presetStorage)
    private val routineRepository = RoutineRepository(database, preferenceManager, driveService, presetStorage)
    private val workoutRepository = WorkoutRepository(context, database, preferenceManager, driveService, presetStorage)
    private val diaryRepository = DiaryRepository(context, database, preferenceManager, driveService)
    private val homeRepository = HomeRepository(
        database,
        dietRepository,
        routineRepository,
        workoutRepository,
        diaryRepository
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
                dietRepository,
                routineRepository,
                workoutRepository,
                presetStorage,
                backupManager,
                homeRepository,
                sleepAlarmScheduler,
                vectorApplication
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
