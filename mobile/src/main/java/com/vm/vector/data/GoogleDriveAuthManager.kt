package com.vm.vector.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.drive.DriveScopes

/**
 * Handles Google Sign-In for Drive + Calendar (readonly) access.
 */
class GoogleDriveAuthManager(private val context: Context) {

    val calendarScope: Scope = Scope(CalendarScopes.CALENDAR_READONLY)

    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(
            Scope(DriveScopes.DRIVE),
            calendarScope,
        )
        .build()

    private val googleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    fun hasCalendarPermission(account: GoogleSignInAccount?): Boolean {
        if (account == null) return false
        return GoogleSignIn.hasPermissions(account, calendarScope)
    }

    /**
     * Handles the sign-in result from Activity result intent.
     * @param data Result intent from Activity; null if cancelled.
     * @param onResult Callback with the signed-in account, or null if failed/cancelled.
     */
    fun handleSignInResult(data: Intent?, onResult: (GoogleSignInAccount?) -> Unit) {
        if (data == null) {
            onResult(null)
            return
        }
        GoogleSignIn.getSignedInAccountFromIntent(data)
            .addOnSuccessListener { onResult(it) }
            .addOnFailureListener { onResult(null) }
    }

    fun signOut() = googleSignInClient.signOut()
}
