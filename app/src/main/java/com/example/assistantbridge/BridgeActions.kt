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
    const val EXTRA_TEXT = "text"
    const val SHARED_TOKEN = "CHANGE_THIS_TOKEN"
    const val CHATGPT_PACKAGE = "com.openai.chatgpt"

    fun mediaKey(context: Context, keyCode: Int) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {
            Log.e("AssistantBridge", "Media key dispatch failed", e)
        }
    }

    fun adjustMusicVolume(context: Context, increase: Boolean) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (e: Exception) {
            Log.e("AssistantBridge", "Music volume adjustment failed", e)
        }
    }

    fun openPackage(context: Context, packageName: String) {
        try {
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launch == null) {
                Log.w("AssistantBridge", "App not found or has no launch activity: $packageName")
                return
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        } catch (e: Exception) {
            Log.e("AssistantBridge", "Could not open $packageName", e)
        }
    }

    fun openChatGPT(context: Context) = openPackage(context, CHATGPT_PACKAGE)

    fun sendTextToChatGPT(context: Context, text: String) {
        try {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage(CHATGPT_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (share.resolveActivity(context.packageManager) != null) {
                context.startActivity(share)
            } else {
                Log.w("AssistantBridge", "ChatGPT does not expose a compatible text-share activity; opening the app instead")
                openChatGPT(context)
            }
        } catch (e: Exception) {
            Log.e("AssistantBridge", "Could not send recognized text to ChatGPT", e)
            openChatGPT(context)
        }
    }

    fun startVoiceCommand(context: Context, mode: String) {
        val intent = Intent(context, VoiceCommandActivity::class.java)
            .putExtra(VoiceCommandActivity.EXTRA_MODE, mode)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun dial(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
