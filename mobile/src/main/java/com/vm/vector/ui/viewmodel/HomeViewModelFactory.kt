package com.vm.vector.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vm.vector.VectorApplication
import com.vm.vector.data.ActivityRepository
import com.vm.vector.data.BrainFileStore
import com.vm.vector.data.BrainRepository
import com.vm.vector.data.CalendarDraftRepository
import com.vm.vector.data.SleepRepository
import com.vm.vector.data.DriveService
import com.vm.vector.data.FoodRepository
import com.vm.vector.data.GoogleDriveAuthManager
import com.vm.vector.data.HabitRepository
import com.vm.vector.data.HomeRepository
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.PresetStorage
import com.vm.vector.data.SelfRepository
import com.vm.vector.data.WorkoutRepository

class HomeViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {

    private val database = (context.applicationContext as VectorApplication).database
    private val preferenceManager = PreferenceManager(context)
    private val authManager = GoogleDriveAuthManager(context)
    private val driveService = DriveService(context, authManager)
    private val presetStorage = PresetStorage(context, preferenceManager)
    private val foodRepository = FoodRepository(database, preferenceManager, presetStorage)
    private val habitRepository = HabitRepository(database)
    private val activityRepository = ActivityRepository(database, presetStorage)
    private val workoutRepository = WorkoutRepository(context, database, preferenceManager, presetStorage)
    private val selfRepository = SelfRepository(context, database, preferenceManager, driveService)
    private val homeRepository = HomeRepository(
        database,
        foodRepository,
        activityRepository,
        workoutRepository,
        selfRepository,
        SleepRepository(database),
        CalendarDraftRepository(database),
    )
    private val brainRepository = BrainRepository(database, BrainFileStore(context.applicationContext, database))

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(
                context.applicationContext as Application,
                homeRepository,
                selfRepository,
                habitRepository,
                brainRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
