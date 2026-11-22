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
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MicTapService : AccessibilityService() {
    companion object {
        const val ACTION_TAP_MIC = "com.example.wheelmic.ACTION_TAP_MIC"
        private const val MIC_LABEL = "assistant microphone"
        private const val TAG = "WheelMic"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tapRequested = false
    private var attempts = 0
    private var clickInProgress = false
    private fun log(message: String) = Log.i(TAG, message)

    private data class Candidate(val bounds: Rect)

    private val requestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_TAP_MIC) return
            log("Received mic tap request")
            tapRequested = true
            attempts = 0
            clickInProgress = false
            tryTapFloatingMic()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        log("Accessibility service connected")
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
            log("No microphone found after $attempts attempts")
            tapRequested = false
            return
        }
        attempts++
        val windows = windows
        if (attempts == 1) log("Attempting mic tap; accessible windows=${windows.size}")
        val candidates = windows.asSequence().flatMap { window ->
            val root = window.root
            if (attempts == 1) log("Window type=${window.type}, active=${window.isActive}, focused=${window.isFocused}, root=${root != null}")
            if (root == null) emptySequence() else findMicBounds(root).asSequence()
        }.toList()
        val candidate = candidates.firstOrNull()
        if (candidate == null) {
            if (attempts == 1) log("No accessible view labeled '$MIC_LABEL'; retrying")
            handler.postDelayed({ tryTapFloatingMic() }, 120L)
            return
        }

        log("Found mic at ${candidate.bounds}; dispatching tap")
        clickInProgress = true
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
        handler.postDelayed({
            val b = candidate.bounds
            val path = Path().apply { moveTo(b.exactCenterX(), b.exactCenterY()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    log("Mic tap gesture completed")
                    tapRequested = false
                    clickInProgress = false
                    tone.release()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    log("Mic tap gesture cancelled")
                    clickInProgress = false
                    tone.release()
                    handler.postDelayed({ tryTapFloatingMic() }, 150L)
                }
            }, handler)
            if (!dispatched) {
                log("Mic tap gesture rejected")
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
            if (description.contains("assistant") || description.contains("speak") || description.contains("microphone") || description == "mic") {
                log("View label='$description', visible=${node.isVisibleToUser}, enabled=${node.isEnabled}, bounds=$bounds, package=${node.packageName}")
            }
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
