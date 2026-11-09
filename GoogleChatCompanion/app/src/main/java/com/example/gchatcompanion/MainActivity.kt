package com.example.gchatcompanion

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var feed: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPostNotificationPermissionIfNeeded()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 16)
        }
        root.addView(TextView(this).apply {
            text = "Chat Companion"; textSize = 26f; setPadding(0, 0, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "Private Google Chat notifications"; textSize = 15f
            setPadding(0, 0, 0, 16)
        })

        status = TextView(this).apply { textSize = 14f; setPadding(0, 0, 0, 12) }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Enable notification access"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        })

        root.addView(Switch(this).apply {
            text = "Show companion notifications"
            isChecked = getSharedPreferences("chat_companion", MODE_PRIVATE).getBoolean("mirror", true)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences("chat_companion", MODE_PRIVATE).edit()
                    .putBoolean("mirror", checked).apply()
                Toast.makeText(this@MainActivity,
                    if (checked) "Notification mirroring enabled" else "Notification mirroring disabled",
                    Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(Button(this).apply {
            text = "Open Google Chat"
            setOnClickListener { startActivity(Intent(this@MainActivity, WebChatActivity::class.java)) }
        })

        root.addView(Button(this).apply {
            text = "Refresh conversations"; setOnClickListener { renderHistory() }
        })
        root.addView(TextView(this).apply {
            text = "Recent notification previews"; textSize = 20f; setPadding(0, 20, 0, 8)
        })

        val scroll = ScrollView(this)
        feed = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(feed)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(TextView(this).apply {
            text = "Messages are shown in Google Chat. This list contains notification previews only."
            textSize = 12f; setPadding(0, 12, 0, 0)
        })

        setContentView(root)
        updateAccessStatus()
        renderHistory()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateAccessStatus()
        if (::feed.isInitialized) renderHistory()
    }

    private fun requestPostNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
    }

    private fun updateAccessStatus() {
        status.text = if (ChatNotificationListener.hasNotificationAccess(this)) {
            "Notification access: enabled"
        } else {
            "Notification access: not enabled. Tap above and allow Chat Companion."
        }
    }

    private fun renderHistory() {
        feed.removeAllViews()
        val history = ChatNotificationListener.history(this)
        if (history.length() == 0) {
            feed.addView(TextView(this).apply {
                text = "No Google Chat notifications captured yet. Enable notification access, then receive a private message."
                textSize = 15f; setPadding(0, 16, 0, 16)
            })
            return
        }

        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            val key = item.optString("key")
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
                setBackgroundColor(0xFFF1F3F4.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 12 }
            }
            card.addView(TextView(this).apply {
                text = item.optString("title", "Google Chat"); textSize = 17f
            })
            card.addView(TextView(this).apply {
                text = item.optString("body", ""); textSize = 15f; setPadding(0, 6, 0, 6)
            })
            val reply = if (item.optBoolean("canReply")) " • Reply action detected" else ""
            card.addView(TextView(this).apply {
                text = android.text.format.DateFormat.format(
                    "yyyy-MM-dd HH:mm", item.optLong("time")
                ).toString() + reply
                textSize = 12f
            })
            card.setOnClickListener {
                if (!ChatNotificationListener.openConversation(key)) {
                    Toast.makeText(this@MainActivity,
                        "Opening Google Chat. Some older notifications may no longer be active.",
                        Toast.LENGTH_SHORT).show()
                    packageManager.getLaunchIntentForPackage(ChatNotificationListener.CHAT_PACKAGE)
                        ?.let { startActivity(it) }
                }
            }
            feed.addView(card)
        }
    }
}
