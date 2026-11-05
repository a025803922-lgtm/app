package com.example.assistantbridge
import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
class MainActivity : Activity() {
    private lateinit var updateStatus: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(24,24,24,24) }
        layout.addView(TextView(this).apply { text = "Assistant Bridge ${BuildConfig.VERSION_NAME}\n\nTS10 voice and action bridge"; textSize = 22f; gravity = Gravity.CENTER; setPadding(12,24,12,24) })
        addTestButton(layout,"Music: play / pause") { BridgeActions.mediaKey(this,android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        addTestButton(layout,"Music: next track") { BridgeActions.mediaKey(this,android.view.KeyEvent.KEYCODE_MEDIA_NEXT) }
        addTestButton(layout,"Music: previous track") { BridgeActions.mediaKey(this,android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        addTestButton(layout,"Volume up") { BridgeActions.adjustMusicVolume(this,true) }
        addTestButton(layout,"Volume down") { BridgeActions.adjustMusicVolume(this,false) }
        addTestButton(layout,"Voice commands (Hebrew / English)") { BridgeActions.startVoiceCommand(this,VoiceCommandActivity.MODE_COMMAND) }
        addTestButton(layout,"Speak text to ChatGPT") { BridgeActions.startVoiceCommand(this,VoiceCommandActivity.MODE_CHATGPT) }
        addTestButton(layout,"Open ChatGPT") { BridgeActions.openChatGPT(this) }
        addTestButton(layout,"Open Waze") { BridgeActions.openPackage(this,"com.waze") }
        updateStatus = TextView(this).apply { text = "Updates are checked against GitHub Releases."; textSize = 14f; gravity = Gravity.CENTER; setPadding(8,16,8,8) }
        layout.addView(updateStatus)
        addTestButton(layout,"Check for updates") { UpdateManager.check(this) { updateStatus.text = it } }
        setContentView(layout)
    }
    private fun addTestButton(parent: LinearLayout,label: String,action: () -> Unit) {
        parent.addView(Button(this).apply { text = label; setOnClickListener { action(); if(label!="Check for updates") Toast.makeText(this@MainActivity,"Action requested",Toast.LENGTH_SHORT).show() } })
    }
}
