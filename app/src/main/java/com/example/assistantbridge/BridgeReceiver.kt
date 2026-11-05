package com.example.assistantbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BridgeActions.ACTION_COMMAND) return
        if (intent.getStringExtra(BridgeActions.EXTRA_TOKEN) != BridgeActions.SHARED_TOKEN) {
            Log.w("AssistantBridge", "Rejected command: invalid token")
            return
        }
        when (intent.getStringExtra(BridgeActions.EXTRA_COMMAND)) {
            "MUSIC_PLAY_PAUSE" -> BridgeActions.mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "MUSIC_NEXT" -> BridgeActions.mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            "MUSIC_PREVIOUS" -> BridgeActions.mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "OPEN_APP" -> BridgeActions.openPackage(
                context, intent.getStringExtra(BridgeActions.EXTRA_PACKAGE) ?: return
            )
            "DIAL" -> BridgeActions.dial(
                context, intent.getStringExtra(BridgeActions.EXTRA_NUMBER) ?: return
            )
            else -> Log.w("AssistantBridge", "Unknown command")
        }
    }
}
