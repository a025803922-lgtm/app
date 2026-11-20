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
import kotlin.math.max
import kotlin.math.min

class MicTapService : AccessibilityService() {
    companion object {
        const val ACTION_TAP_MIC = "com.example.wheelmic.ACTION_TAP_MIC"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tapRequested = false
    private var attempts = 0
    private var clickInProgress = false

    private data class Candidate(
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
        val area: Int
    )

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
        if (attempts >= 12) {
            tapRequested = false
            return
        }
        attempts++

        // A movable floating control is normally exposed in a system window,
        // separate from the Assistant app's own content window.
        val candidate = windows
            .asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_SYSTEM }
            .flatMap { window ->
                val root = window.root
                if (root == null) emptySequence() else findSmallUnlabelledClickTargets(root).asSequence()
            }
            .minByOrNull { it.area }
            ?: return

        clickInProgress = true
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
        handler.postDelayed({
            val clicked = candidate.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (!clicked) {
                val b = candidate.bounds
                val path = Path().apply { moveTo(b.exactCenterX(), b.exactCenterY()) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
                    .build()
                dispatchGesture(gesture, null, null)
            }
            tone.release()
            tapRequested = false
            clickInProgress = false
        }, 100L)
    }

    private fun findSmallUnlabelledClickTargets(root: AccessibilityNodeInfo): List<Candidate> {
        val found = mutableListOf<Candidate>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val width = bounds.width()
            val height = bounds.height()
            val ratio = if (height == 0) 0f else width.toFloat() / height.toFloat()
            val sizeLooksLikeFloatingIcon =
                width in 28..240 && height in 28..240 && ratio in 0.65f..1.55f
            val hasNoLabel = node.text.isNullOrBlank() && node.contentDescription.isNullOrBlank()
            if (node.isVisibleToUser && node.isEnabled && node.isClickable &&
                sizeLooksLikeFloatingIcon && hasNoLabel
            ) {
                found += Candidate(node, bounds, max(1, width * height))
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::add)
            }
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