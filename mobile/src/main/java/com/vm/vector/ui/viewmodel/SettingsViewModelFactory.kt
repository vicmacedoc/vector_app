package com.vm.vector.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vm.vector.data.DriveService
import com.vm.vector.data.GoogleDriveAuthManager
import com.vm.vector.data.PreferenceManager

class SettingsViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {

    private val preferenceManager = PreferenceManager(context)
    private val authManager = GoogleDriveAuthManager(context)
    private val driveService = DriveService(context, authManager)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(preferenceManager, driveService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
