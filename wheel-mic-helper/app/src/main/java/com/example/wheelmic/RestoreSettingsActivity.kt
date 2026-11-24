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
    private lateinit var recoveryHelp: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.settings_title)

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
            text = getString(R.string.setup_heading)
            textSize = 22f
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.setup_description)
            textSize = 16f
            setPadding(0, padding / 2, 0, padding / 2)
        })

        status = TextView(this).apply { textSize = 15f }
        content.addView(status)

        restoreCheckBox = CheckBox(this).apply {
            text = getString(R.string.restore_after_restart)
            textSize = 16f
            setOnCheckedChangeListener { _, checked ->
                if (checked && !hasSecureSettingsPermission()) {
                    isChecked = false
                    Toast.makeText(
                        this@RestoreSettingsActivity,
                        getString(R.string.toast_adb_grant),
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

        recoveryHelp = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, padding / 2)
        }
        content.addView(recoveryHelp)

        content.addView(Button(this).apply {
            text = getString(R.string.open_accessibility_settings)
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
            getString(R.string.accessibility_enabled)
        } else {
            getString(R.string.accessibility_disabled)
        }

        recoveryHelp.text = if (canRestore) {
            getString(R.string.recovery_ready)
        } else {
            getString(R.string.secure_settings_grant)
        }

        val preferences = getSharedPreferences(RestorePrefs.PREFS_NAME, MODE_PRIVATE)
        restoreCheckBox.setOnCheckedChangeListener(null)
        restoreCheckBox.isChecked = preferences.getBoolean(RestorePrefs.KEY_RESTORE_AFTER_RESTART, false)
        restoreCheckBox.isEnabled = canRestore
        restoreCheckBox.setOnCheckedChangeListener { _, checked ->
            if (checked && !hasSecureSettingsPermission()) {
                restoreCheckBox.isChecked = false
                Toast.makeText(
                    this,
                    getString(R.string.toast_permission_required),
                    Toast.LENGTH_LONG
                ).show()
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