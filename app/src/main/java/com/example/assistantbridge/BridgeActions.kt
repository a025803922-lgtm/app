package com.example.assistantbridge

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import android.view.KeyEvent

object BridgeActions {
    const val ACTION_COMMAND = "com.example.assistantbridge.COMMAND"
    const val EXTRA_TOKEN = "token"
    const val EXTRA_COMMAND = "command"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_NUMBER = "number"
    const val SHARED_TOKEN = "CHANGE_THIS_TOKEN"

    fun mediaKey(context: Context, keyCode: Int) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {
            Log.e("AssistantBridge", "Media key dispatch failed", e)
        }
    }

    fun openPackage(context: Context, packageName: String) {
        try {
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launch == null) {
                Log.w("AssistantBridge", "App not found: $packageName")
                return
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        } catch (e: Exception) {
            Log.e("AssistantBridge", "Could not open $packageName", e)
        }
    }

    fun dial(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
