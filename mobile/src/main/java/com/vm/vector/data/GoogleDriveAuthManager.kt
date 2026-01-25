package com.vm.vector.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

/**
 * Handles Google Sign-In for Drive access. Broad Drive scope enabled for discovery.
 */
class GoogleDriveAuthManager(private val context: Context) {

    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(
            Scope(DriveScopes.DRIVE),
        )
        .build()

    private val googleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

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
