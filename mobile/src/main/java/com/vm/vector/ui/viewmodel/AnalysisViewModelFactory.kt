package com.vm.vector.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vm.vector.VectorApplication
import com.vm.vector.data.AnalysisRepository
import com.vm.vector.data.DriveService
import com.vm.vector.data.GoogleDriveAuthManager
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.PresetStorage
import com.vm.vector.data.WorkoutRepository

class AnalysisViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {

    private val database = (context.applicationContext as VectorApplication).database
    private val preferenceManager = PreferenceManager(context)
    private val authManager = GoogleDriveAuthManager(context)
    private val driveService = DriveService(context, authManager)
    private val presetStorage = PresetStorage(context, preferenceManager)
    private val workoutRepository = WorkoutRepository(
        context.applicationContext,
        database,
        preferenceManager,
        driveService,
        presetStorage,
    )
    private val repository = AnalysisRepository(database, workoutRepository)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalysisViewModel::class.java)) {
            return AnalysisViewModel(
                context.applicationContext as Application,
                repository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
