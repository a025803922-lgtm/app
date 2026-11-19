package com.example.wheelmic

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MicTapService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingTap = false
    private var clickScheduled = false
    private var attempts = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != "com.offline.assistant") return
        if (!pendingTap) {
            pendingTap = true
            clickScheduled = false
            attempts = 0
        }
        tapMic(rootInActiveWindow)
    }

    private fun tapMic(root: AccessibilityNodeInfo?): Boolean {
        if (root == null || !pendingTap || clickScheduled) return false
        if (attempts++ > 30) {
            pendingTap = false
            return false
        }
        val nodes = root.findAccessibilityNodeInfosByViewId("com.offline.assistant:id/chatMic")
        for (node in nodes) {
            if (!node.isVisibleToUser || !node.isEnabled) continue
            clickScheduled = true
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
            handler.postDelayed({
                val freshRoot = rootInActiveWindow
                val freshNodes = freshRoot?.findAccessibilityNodeInfosByViewId("com.offline.assistant:id/chatMic").orEmpty()
                val mic = freshNodes.firstOrNull { it.isVisibleToUser && it.isEnabled }
                val clicked = mic?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                tone.release()
                if (clicked) {
                    pendingTap = false
                    clickScheduled = false
                } else {
                    clickScheduled = false
                }
            }, 180L)
            return true
        }
        for (i in 0 until root.childCount) {
            if (tapMic(root.getChild(i))) return true
        }
        return false
    }

    override fun onInterrupt() {}
}
