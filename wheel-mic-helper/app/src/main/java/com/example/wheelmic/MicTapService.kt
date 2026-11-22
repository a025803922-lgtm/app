package com.example.wheelmic

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class MicTapService : AccessibilityService() {
    companion object {
        const val ACTION_TAP_MIC = "com.example.wheelmic.ACTION_TAP_MIC"
        private const val MIC_LABEL = "assistant microphone"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tapRequested = false
    private var attempts = 0
    private var clickInProgress = false

    private data class Candidate(val bounds: Rect)

    private val requestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_TAP_MIC) return
            tapRequested = true
            attempts = 0
            clickInProgress = false
            tryTapFloatingMic()
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
        if (tapRequested) tryTapFloatingMic()
    }

    private fun tryTapFloatingMic() {
        if (!tapRequested || clickInProgress) return
        if (attempts >= 20) {
            tapRequested = false
            return
        }
        attempts++

        // Search every accessible window type because head-unit Android builds
        // may expose application overlays as SYSTEM, APPLICATION, or accessibility
        // windows. The exact assistant label keeps PiP and unrelated controls out.
        val candidate = windows.asSequence()
            .flatMap { window ->
                val root = window.root
                if (root == null) emptySequence() else findMicBounds(root).asSequence()
            }
            .firstOrNull()
        if (candidate == null) {
            handler.postDelayed({ tryTapFloatingMic() }, 120L)
            return
        }

        clickInProgress = true
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
        handler.postDelayed({
            // The overlay handles a physical touch sequence in OnTouchListener;
            // dispatch a short tap at the center of the labelled mic bounds.
            val b = candidate.bounds
            val path = Path().apply { moveTo(b.exactCenterX(), b.exactCenterY()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    tapRequested = false
                    clickInProgress = false
                    tone.release()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    clickInProgress = false
                    tone.release()
                    handler.postDelayed({ tryTapFloatingMic() }, 150L)
                }
            }, handler)
            if (!dispatched) {
                clickInProgress = false
                tone.release()
                handler.postDelayed({ tryTapFloatingMic() }, 150L)
            }
        }, 100L)
    }

    private fun findMicBounds(root: AccessibilityNodeInfo): List<Candidate> {
        val found = mutableListOf<Candidate>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val description = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (description == MIC_LABEL && node.isVisibleToUser && node.isEnabled &&
                !bounds.isEmpty && bounds.width() <= 300 && bounds.height() <= 300
            ) {
                found += Candidate(bounds)
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(stack::add)
        }
        return found
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
