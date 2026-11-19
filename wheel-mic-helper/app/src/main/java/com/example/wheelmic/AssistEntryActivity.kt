package com.example.wheelmic

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class AssistEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        if (!enabled.contains("com.example.wheelmic/.MicTapService")) {
            Toast.makeText(this, "Enable Wheel Mic Helper in Accessibility settings, then press the steering-wheel button again.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            finish()
            return
        }
        val launch = packageManager.getLaunchIntentForPackage("com.offline.assistant")
        if (launch == null) Toast.makeText(this, "OfflineAssistant is not installed.", Toast.LENGTH_LONG).show()
        else startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
