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
                "Enable Wheel Mic Helper in Accessibility settings once, then press the steering-wheel button again.",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            finish()
            return
        }

        val launch = packageManager.getLaunchIntentForPackage("com.offline.assistant")
        if (launch == null) {
            Toast.makeText(this, "OfflineAssistant is not installed.", Toast.LENGTH_LONG).show()
        } else {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(launch)
        }
        finish()
    }

    private fun isMicAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        val component = ComponentName(this, MicTapService::class.java)
        val acceptedNames = setOf(
            component.flattenToString(),
            component.flattenToShortString()
        )

        return enabledServices.split(':').any { it in acceptedNames }
    }
}
