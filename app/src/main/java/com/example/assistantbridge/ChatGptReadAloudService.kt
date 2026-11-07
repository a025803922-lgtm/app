package com.example.assistantbridge

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class ChatGptReadAloudService : AccessibilityService() {
    private var tts: TextToSpeech? = null
    private var lastSpoken = ""
    private var currentAnswer = ""
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
        // ChatGPT often exposes the complete assistant message on a parent node;
        // choose the longest visible text candidate rather than the last tiny child.
        val answer = candidates.maxByOrNull { it.length } ?: return
        if (answer == lastSpoken || answer == currentAnswer) return

        val speechText = if (lastSpoken.isNotBlank() && answer.startsWith(lastSpoken)) {
            answer.removePrefix(lastSpoken).trim()
        } else {
            answer
        }
        currentAnswer = answer
        if (speechText.isBlank()) return
        lastSpoken = answer

        val locale = if (speechText.any { it in '\u0590'..'\u05ff' }) Locale("he", "IL") else Locale.US
        val languageStatus = tts?.setLanguage(locale)
        if (languageStatus != null && languageStatus < TextToSpeech.LANG_AVAILABLE) {
            tts?.setLanguage(Locale.getDefault())
        }
        speakInChunks(speechText)
    }

    private fun speakInChunks(text: String) {
        val chunks = splitForTts(text)
        chunks.forEachIndexed { index, chunk ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(chunk, queueMode, null, "chatgpt-answer-$index")
        }
    }

    private fun splitForTts(text: String): List<String> {
        if (text.length <= MAX_TTS_CHARS) return listOf(text)
        val chunks = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.isNotEmpty()) {
            if (remaining.length <= MAX_TTS_CHARS) {
                chunks.add(remaining)
                break
            }
            var cut = remaining.lastIndexOf(' ', MAX_TTS_CHARS)
            if (cut < MAX_TTS_CHARS / 2) cut = MAX_TTS_CHARS
            chunks.add(remaining.substring(0, cut).trim())
            remaining = remaining.substring(cut).trim()
        }
        return chunks.filter { it.isNotBlank() }
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
        private const val QUIET_PERIOD_MS = 3000L
        private const val MIN_ANSWER_LENGTH = 25
        private const val MAX_TTS_CHARS = 3500
    }
}
