package com.example.assistantbridge

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }
        layout.addView(TextView(this).apply {
            text = "Assistant Bridge 0.1\n\nLocal action test"
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(12, 24, 12, 24)
        })
        addTestButton(layout, "Music: play / pause") {
            BridgeActions.mediaKey(this, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        addTestButton(layout, "Music: next track") {
            BridgeActions.mediaKey(this, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
        }
        addTestButton(layout, "Music: previous track") {
            BridgeActions.mediaKey(this, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        addTestButton(layout, "Open Waze") { BridgeActions.openPackage(this, "com.waze") }
        addTestButton(layout, "Open ChatGPT") { BridgeActions.openPackage(this, "com.openai.chatgpt") }
        setContentView(layout)
    }

    private fun addTestButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            setOnClickListener {
                action()
                Toast.makeText(this@MainActivity, "Action requested", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
