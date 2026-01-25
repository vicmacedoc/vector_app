package com.vm.vector.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vm.vector.data.DriveService
import com.vm.vector.data.GoogleDriveAuthManager
import com.vm.vector.data.ListRepository
import com.vm.vector.data.PreferenceManager

class ListsViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {

    private val preferenceManager = PreferenceManager(context)
    private val authManager = GoogleDriveAuthManager(context)
    private val driveService = DriveService(context, authManager)
    private val repository = ListRepository(preferenceManager, driveService)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ListsViewModel::class.java)) {
            return ListsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
