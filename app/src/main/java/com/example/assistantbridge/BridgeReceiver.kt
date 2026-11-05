package com.example.assistantbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent

class BridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BridgeActions.ACTION_COMMAND) return
        if (intent.getStringExtra(BridgeActions.EXTRA_TOKEN) != BridgeActions.SHARED_TOKEN) {
            Log.w("AssistantBridge", "Rejected command: invalid token")
            return
        }
        when (BridgeCommand.parse(intent.getStringExtra(BridgeActions.EXTRA_COMMAND))) {
            BridgeCommand.MUSIC_PLAY_PAUSE -> BridgeActions.mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            BridgeCommand.MUSIC_NEXT -> BridgeActions.mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            BridgeCommand.MUSIC_PREVIOUS -> BridgeActions.mediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            BridgeCommand.VOLUME_UP -> BridgeActions.adjustMusicVolume(context, true)
            BridgeCommand.VOLUME_DOWN -> BridgeActions.adjustMusicVolume(context, false)
            BridgeCommand.OPEN_APP -> BridgeActions.openPackage(
                context, intent.getStringExtra(BridgeActions.EXTRA_PACKAGE) ?: return
            )
            BridgeCommand.OPEN_CHATGPT -> BridgeActions.openChatGPT(context)
            BridgeCommand.VOICE_COMMAND -> BridgeActions.startVoiceCommand(
                context, VoiceCommandActivity.MODE_COMMAND
            )
            BridgeCommand.VOICE_TO_CHATGPT -> BridgeActions.startVoiceCommand(
                context, VoiceCommandActivity.MODE_CHATGPT
            )
            BridgeCommand.DIAL -> BridgeActions.dial(
                context, intent.getStringExtra(BridgeActions.EXTRA_NUMBER) ?: return
            )
            null -> Log.w("AssistantBridge", "Unknown command")
        }
    }
}
