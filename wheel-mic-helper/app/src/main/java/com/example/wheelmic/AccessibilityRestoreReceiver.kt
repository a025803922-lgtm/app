package com.example.wheelmic

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

internal object RestorePrefs {
    const val PREFS_NAME = "accessibility_restore"
    const val KEY_RESTORE_AFTER_RESTART = "restore_after_restart"
}

class AccessibilityRestoreReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "WheelMicRestore"
        private val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "com.fyt.boot.ACCON",
            "com.ts.intent.action.ACC_ON",
            "android.intent.action.ACC_ON",
            "com.microntek.bootcheck",
            "com.glsx.boot.ACCON",
            "com.android.action.ACC_ON",
            "autochips.intent.action.QB_POWERON"
        )
        // The TS10 may apply its accessibility reset after the ACC-on event.
        // Recheck for up to eight seconds so a late firmware reset is recovered too.
        private val retryDelaysMs = longArrayOf(0L, 1000L, 2000L, 3000L, 2000L)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in supportedActions) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val handler = Handler(Looper.getMainLooper())
        var attemptNumber = 0

        fun runNextAttempt() {
            val delay = retryDelaysMs[attemptNumber]
            handler.postDelayed({
                try {
                    if (!restoreOnce(appContext, action, attemptNumber + 1) ||
                        attemptNumber == retryDelaysMs.lastIndex
                    ) {
                        pendingResult.finish()
                    } else {
                        attemptNumber++
                        runNextAttempt()
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Recovery attempt failed after $action", error)
                    if (attemptNumber == retryDelaysMs.lastIndex) {
                        pendingResult.finish()
                    } else {
                        attemptNumber++
                        runNextAttempt()
                    }
                }
            }, delay)
        }

        runNextAttempt()
    }

    private fun restoreOnce(context: Context, action: String, attempt: Int): Boolean {
        val preferences = context.getSharedPreferences(RestorePrefs.PREFS_NAME, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(RestorePrefs.KEY_RESTORE_AFTER_RESTART, false)) {
            Log.i(TAG, "Restore skipped: user has not enabled keep-accessibility")
            return false
        }
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Restore skipped: WRITE_SECURE_SETTINGS has not been granted")
            return false
        }

        val resolver = context.contentResolver
        val service = ComponentName(context, MicTapService::class.java)
        val entries = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty().split(':').map(String::trim).filter(String::isNotEmpty)

        val alreadyEnabled = entries.any { entry ->
            ComponentName.unflattenFromString(entry) == service
        }
        if (!alreadyEnabled) {
            val updated = (entries + service.flattenToString()).joinToString(":")
            if (!Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    updated
                )
            ) {
                Log.w(TAG, "Could not restore Wheel Mic Helper after $action (check $attempt)")
                return true
            }
            Log.i(TAG, "Restored Wheel Mic Helper after $action (check $attempt)")
        } else {
            Log.i(TAG, "Wheel Mic Helper is enabled after $action (check $attempt)")
        }

        if (Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i(TAG, "Re-enabled Android Accessibility after $action (check $attempt)")
        }
        return true
    }
}