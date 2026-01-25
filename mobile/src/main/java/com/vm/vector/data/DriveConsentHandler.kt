package com.vm.vector.data

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds pending NeedRemoteConsent (intent + retry). UI launches intent via ActivityResultLauncher;
 * on Result OK, invokes retry then clears.
 */
object DriveConsentHandler {

    private val _pendingIntent = MutableStateFlow<Intent?>(null)
    val pendingIntent: StateFlow<Intent?> = _pendingIntent.asStateFlow()

    @Volatile
    private var pendingRetry: (() -> Unit)? = null

    fun requestConsent(intent: Intent, retry: () -> Unit) {
        _pendingIntent.value = intent
        pendingRetry = retry
    }

    fun consumeIntent(): Intent? {
        val i = _pendingIntent.value
        _pendingIntent.value = null
        return i
    }

    fun getRetryAndClear(): (() -> Unit)? {
        val r = pendingRetry
        pendingRetry = null
        return r
    }

    fun clearPending() {
        _pendingIntent.value = null
        pendingRetry = null
    }
}
