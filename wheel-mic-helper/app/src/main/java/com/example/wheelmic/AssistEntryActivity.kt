package com.example.wheelmic

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class AssistEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isMicAccessibilityEnabled()) {
            Toast.makeText(
                this,
                getString(R.string.enable_accessibility_first),
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            finish()
            return
        }

        // Do not launch the assistant. Its screen is already open.
        // Ask the enabled accessibility service to tap its visible microphone once.
        sendBroadcast(Intent(MicTapService.ACTION_TAP_MIC).setPackage(packageName))
        finish()
    }

    private fun isMicAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val component = ComponentName(this, MicTapService::class.java)
        val acceptedNames = setOf(component.flattenToString(), component.flattenToShortString())
        return enabledServices.split(':').any { enabled ->
            acceptedNames.any { accepted ->
                enabled.trim().equals(accepted, ignoreCase = true)
            }
        }
    }
}