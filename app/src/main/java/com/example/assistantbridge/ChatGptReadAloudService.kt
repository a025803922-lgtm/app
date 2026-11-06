package com.example.assistantbridge

import android.accessibilityservice.AccessibilityService
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import java.util.Locale

class ChatGptReadAloudService : AccessibilityService() {
    private var tts: TextToSpeech? = null
    private var lastSpoken = ""
    private val handler = Handler(Looper.getMainLooper())
    private val readLatestRunnable = Runnable { readLatestAnswer() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("he", "IL"))
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != CHATGPT_PACKAGE) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handler.removeCallbacks(readLatestRunnable)
                handler.postDelayed(readLatestRunnable, QUIET_PERIOD_MS)
            }
        }
    }

    private fun readLatestAnswer() {
        val root = rootInActiveWindow ?: return
        val visibleText = mutableListOf<String>()
        collectText(root, visibleText)
        val candidates = visibleText
            .map { it.replace("\\s+".toRegex(), " ").trim() }
            .filter { it.length >= MIN_ANSWER_LENGTH && !isUiLabel(it) }
            .distinct()
        val answer = candidates.lastOrNull() ?: return
        if (answer == lastSpoken) return
        lastSpoken = answer
        val locale = if (answer.any { it in '\u0590'..'\u05ff' }) Locale("he", "IL") else Locale.US
        val languageStatus = tts?.setLanguage(locale)
        if (languageStatus != null && languageStatus < TextToSpeech.LANG_AVAILABLE) {
            tts?.setLanguage(Locale.getDefault())
        }
        tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "chatgpt-answer")
    }

    private fun collectText(node: AccessibilityNodeInfo?, output: MutableList<String>) {
        if (node == null || !node.isVisibleToUser) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        for (index in 0 until node.childCount) {
            collectText(node.getChild(index), output)
        }
    }

    private fun isUiLabel(value: String): Boolean {
        val text = value.lowercase(Locale.ROOT)
        val labels = listOf(
            "chatgpt", "message chatgpt", "ask anything", "stop generating",
            "regenerate", "copy", "good response", "bad response", "new chat",
            "search the web", "read aloud", "edit message", "share", "thinking"
        )
        return labels.any { text == it || text.startsWith("$it ") }
    }

    override fun onInterrupt() {
        tts?.stop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(readLatestRunnable)
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
        private const val QUIET_PERIOD_MS = 2500L
        private const val MIN_ANSWER_LENGTH = 25
    }
}