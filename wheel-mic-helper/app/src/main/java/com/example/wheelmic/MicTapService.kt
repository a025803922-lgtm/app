package com.example.wheelmic

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.File

class MicTapService : AccessibilityService() {
    companion object {
        const val ACTION_TAP_MIC = "com.example.wheelmic.ACTION_TAP_MIC"
        private const val MIC_LABEL = "assistant microphone"
        private const val ASSISTANT_PACKAGE = "com.offline.assistant"
        private const val TAG = "WheelMic"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tapRequested = false
    private var attempts = 0
    private var clickInProgress = false
    private fun log(message: String) = Log.i(TAG, message)

    private data class Candidate(val bounds: Rect, val windowType: Int)

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
        log("Accessibility service connected; gesture capability active")
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
            log("Assistant mic not found after $attempts attempts")
            tapRequested = false
            return
        }
        attempts++
        val systemWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_SYSTEM }
        if (attempts == 1) log("Searching ${systemWindows.size} system overlay windows")
        val candidates = systemWindows.asSequence().flatMap { window ->
            val root = window.root
            if (root == null) emptySequence()
            else findMicBounds(root).asSequence().map { Candidate(it, window.type) }
        }.toList()
        val candidate = candidates.minByOrNull { it.bounds.width().toLong() * it.bounds.height() }
        if (candidate == null) {
            if (attempts == 1) log("No assistant-owned mic control in system overlay; retrying")
            handler.postDelayed({ tryTapFloatingMic() }, 120L)
            return
        }

        log("Targeting assistant overlay mic at ${candidate.bounds}, windowType=${candidate.windowType}")
        clickInProgress = true
        handler.postDelayed({
            val b = candidate.bounds
            val path = Path().apply { moveTo(b.exactCenterX(), b.exactCenterY()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    log("Assistant mic tap completed; playing Waze-style listening cue")
                    tapRequested = false
                    clickInProgress = false
                    playListeningBeep()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    log("Assistant mic tap was cancelled")
                    clickInProgress = false
                    handler.postDelayed({ tryTapFloatingMic() }, 150L)
                }
            }, handler)
            log("dispatchGesture returned $dispatched")
            if (!dispatched) {
                clickInProgress = false
                handler.postDelayed({ tryTapFloatingMic() }, 150L)
            }
        }, 100L)
    }

    private fun playListeningBeep() {
        var player: MediaPlayer? = null
        var toneFile: File? = null
        try {
            val encoded = assets.open("google_open.wav.b64").bufferedReader().use { it.readText() }
            toneFile = File(cacheDir, "voice_search_open.wav").apply {
                writeBytes(Base64.decode(encoded, Base64.DEFAULT))
            }
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(toneFile.absolutePath)
                setOnCompletionListener {
                    it.release()
                    toneFile.delete()
                }
                setOnErrorListener { mediaPlayer, _, _ ->
                    mediaPlayer.release()
                    toneFile.delete()
                    true
                }
                prepare()
                start()
            }
        } catch (error: Exception) {
            log("Waze cue playback failed; using short system beep: ${error.message}")
            try { player?.release() } catch (_: IllegalStateException) {}
            toneFile?.delete()
            val fallback = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
            fallback.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            handler.postDelayed({ fallback.release() }, 250L)
        }
    }

    private fun findMicBounds(root: AccessibilityNodeInfo): List<Rect> {
        val found = mutableListOf<Rect>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val description = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (description == MIC_LABEL && node.packageName?.toString() == ASSISTANT_PACKAGE &&
                node.isVisibleToUser && node.isEnabled && !bounds.isEmpty &&
                bounds.width() <= 100 && bounds.height() <= 100
            ) {
                log("Eligible mic node at $bounds in $ASSISTANT_PACKAGE")
                found += bounds
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
