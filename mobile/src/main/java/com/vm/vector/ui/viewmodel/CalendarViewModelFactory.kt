package com.vm.vector.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vm.vector.data.DietRepository
import com.vm.vector.data.DriveService
import com.vm.vector.data.GoogleDriveAuthManager
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.RoutineRepository

class CalendarViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {

    private val preferenceManager = PreferenceManager(context)
    private val authManager = GoogleDriveAuthManager(context)
    private val driveService = DriveService(context, authManager)
    private val dietRepository = DietRepository(context, preferenceManager, driveService)
    private val routineRepository = RoutineRepository(context, preferenceManager, driveService)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            return CalendarViewModel(dietRepository, routineRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
