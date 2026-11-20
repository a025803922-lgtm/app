package com.example.wheelmic

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MicTapService : AccessibilityService() {
    companion object {
        const val ACTION_TAP_MIC = "com.example.wheelmic.ACTION_TAP_MIC"
        private const val ASSISTANT_PACKAGE = "com.offline.assistant"
        private const val MIC_VIEW_ID = "com.offline.assistant:id/chatMic"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tapRequested = false
    private var attempts = 0
    private var clickInProgress = false

    private val requestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_TAP_MIC) return
            tapRequested = true
            attempts = 0
            clickInProgress = false
            tryTapMic()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter(ACTION_TAP_MIC)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(requestReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(requestReceiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!tapRequested || event?.packageName?.toString() != ASSISTANT_PACKAGE) return
        tryTapMic()
    }

    private fun tryTapMic() {
        if (!tapRequested || clickInProgress) return
        if (attempts >= 8) {
            tapRequested = false
            return
        }
        attempts++
        val root = rootInActiveWindow ?: return
        val nodes = root.findAccessibilityNodeInfosByViewId(MIC_VIEW_ID)
        val mic = nodes.firstOrNull { it.isVisibleToUser && it.isEnabled }
        if (mic == null) return

        clickInProgress = true
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 65)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        handler.postDelayed({
            val freshNodes = rootInActiveWindow
                ?.findAccessibilityNodeInfosByViewId(MIC_VIEW_ID)
                .orEmpty()
            val freshMic = freshNodes.firstOrNull { it.isVisibleToUser && it.isEnabled }
            val clicked = freshMic?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            tone.release()
            tapRequested = false
            clickInProgress = false
        }, 120L)
    }

    override fun onInterrupt() {
        tapRequested = false
        clickInProgress = false
    }

    override fun onDestroy() {
        try { unregisterReceiver(requestReceiver) } catch (_: IllegalArgumentException) {}
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
