package com.vm.vector.data

import kotlinx.coroutines.flow.first

/**
 * Fetches [DriveConfig.ROUTINE_WEEKLY_FILENAME] from the discovered 'presets' folder.
 * Root ID in DataStore points to the 'Data' folder.
 */
class RoutineRepository(
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
) {

    suspend fun getRoutineWeekly(): DriveResult<String> {
        val rootFolderId = preferenceManager.googleDriveFolderId.first()
        if (rootFolderId.isNullOrBlank()) return DriveResult.ConfigurationError
        return driveService.getRoutineWeekly(rootFolderId)
    }
}
