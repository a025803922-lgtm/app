package com.example.wheelmic

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class RestoreSettingsActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var restoreCheckBox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Wheel Mic Helper"

        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val scroll = ScrollView(this).apply {
            addView(content)
        }
        setContentView(scroll)

        content.addView(TextView(this).apply {
            text = "Accessibility setup"
            textSize = 22f
        })
        content.addView(TextView(this).apply {
            text = "Enable Wheel Mic Helper in Android Accessibility settings so the steering-wheel button can tap Offline Assistant’s microphone."
            textSize = 16f
            setPadding(0, padding / 2, 0, padding / 2)
        })

        status = TextView(this).apply { textSize = 15f }
        content.addView(status)

        restoreCheckBox = CheckBox(this).apply {
            text = "Restore accessibility after restart"
            textSize = 16f
            setOnCheckedChangeListener { _, checked ->
                if (checked && !hasSecureSettingsPermission()) {
                    isChecked = false
                    Toast.makeText(
                        this@RestoreSettingsActivity,
                        "Grant WRITE_SECURE_SETTINGS once with ADB, then enable this option.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    getSharedPreferences(RestorePrefs.PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(RestorePrefs.KEY_RESTORE_AFTER_RESTART, checked)
                        .apply()
                }
            }
        }
        content.addView(restoreCheckBox)

        content.addView(TextView(this).apply {
            text = if (hasSecureSettingsPermission()) {
                "Restart recovery is ready. The helper only restores its own accessibility service and keeps other enabled services unchanged."
            } else {
                "One-time ADB grant required:\nadb shell pm grant com.example.wheelmic android.permission.WRITE_SECURE_SETTINGS"
            }
            textSize = 14f
            setPadding(0, 0, 0, padding / 2)
        })

        content.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        val enabled = isMicAccessibilityEnabled()
        val canRestore = hasSecureSettingsPermission()
        status.text = if (enabled) {
            "Wheel Mic Helper is enabled."
        } else {
            "Wheel Mic Helper is currently disabled. Turn it on in Accessibility settings."
        }

        val preferences = getSharedPreferences(RestorePrefs.PREFS_NAME, MODE_PRIVATE)
        restoreCheckBox.setOnCheckedChangeListener(null)
        restoreCheckBox.isChecked = preferences.getBoolean(RestorePrefs.KEY_RESTORE_AFTER_RESTART, false)
        restoreCheckBox.isEnabled = canRestore
        restoreCheckBox.setOnCheckedChangeListener { _, checked ->
            if (checked && !hasSecureSettingsPermission()) {
                restoreCheckBox.isChecked = false
                Toast.makeText(this, "WRITE_SECURE_SETTINGS permission is required.", Toast.LENGTH_LONG).show()
            } else {
                preferences.edit().putBoolean(RestorePrefs.KEY_RESTORE_AFTER_RESTART, checked).apply()
            }
        }
    }

    private fun hasSecureSettingsPermission(): Boolean =
        checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun isMicAccessibilityEnabled(): Boolean {
        val service = ComponentName(this, MicTapService::class.java)
        val list = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return list.split(':').any { ComponentName.unflattenFromString(it.trim()) == service }
    }
}
