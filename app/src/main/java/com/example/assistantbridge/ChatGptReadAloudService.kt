package com.example.assistantbridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class ChatGptReadAloudService : AccessibilityService() {
    private var tts: TextToSpeech? = null
    private var lastSpoken = ""
    private var currentAnswer = ""
    private val handler = Handler(Looper.getMainLooper())
    private val readLatestRunnable = Runnable { readLatestAnswer() }

    private var lastForegroundPackage: String? = null
    private var returnPackage: String? = null
    private var phonebookFallbackScheduled = false
    private var callUiSeen = false
    private var callReturnScheduled = false
    private var hotspotTimeoutScheduled = false

    private val phonebookRetryRunnable = Runnable { attemptPhonebookNavigation() }
    private val hotspotRetryRunnable = Runnable { attemptHotspotToggle() }
    private val hotspotTimeoutRunnable = Runnable {
        val pending = automationPrefs.getString(BridgeActions.KEY_PENDING_HOTSPOT, null)
        if (pending != null) {
            automationPrefs.edit()
                .remove(BridgeActions.KEY_PENDING_HOTSPOT)
                .remove(BridgeActions.KEY_HOTSPOT_STAGE)
                .remove(BridgeActions.KEY_HOTSPOT_TOGGLE_SENT)
                .apply()
            hotspotTimeoutScheduled = false
            speakNotice("לא הצלחתי לזהות את מתג הנקודה החמה. אפשר להפעיל או לכבות אותו ידנית בהגדרות.")
            Log.w(TAG, "Hotspot switch was not found; leaving Settings open for manual control")
        }
    }
    private val callReturnRunnable = Runnable {
        val root = rootInActiveWindow
        val activePackage = root?.packageName?.toString()
        if (callUiSeen && activePackage == BLUETOOTH_PACKAGE && !containsCallUiMarker(root)) {
            Log.i(TAG, "Bluetooth call UI disappeared; returning to $returnPackage")
            callUiSeen = false
            restorePreviousApp()
        }
        callReturnScheduled = false
    }

    private val automationPrefs by lazy {
        getSharedPreferences(BridgeActions.AUTOMATION_PREFS, MODE_PRIVATE)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val languageResult = tts?.setLanguage(Locale("he", "IL"))
                Log.i(TAG, "TTS initialized; Hebrew language result=$languageResult")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS started: $utteranceId")
                    }
                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS finished: $utteranceId")
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS error: $utteranceId")
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.e(TAG, "TTS error $errorCode: $utteranceId")
                    }
                })
            } else {
                Log.e(TAG, "TTS initialization failed: $status")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        val eventType = event.eventType

        if (!isIgnoredForReturnPackage(packageName)) {
            lastForegroundPackage = packageName
        }

        if (packageName == CHATGPT_PACKAGE) {
            Log.d(TAG, "ChatGPT accessibility event type=$eventType")
            when (eventType) {
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    handler.removeCallbacks(readLatestRunnable)
                    handler.postDelayed(readLatestRunnable, QUIET_PERIOD_MS)
                }
            }
            return
        }

        if (packageName == BLUETOOTH_PACKAGE) {
            if (automationPrefs.getBoolean(BridgeActions.KEY_PENDING_PHONEBOOK, false)) {
                if (returnPackage == null) returnPackage = lastForegroundPackage
                handler.removeCallbacks(phonebookRetryRunnable)
                handler.postDelayed(phonebookRetryRunnable, PHONEBOOK_RETRY_MS)
            }
            inspectBluetoothCallScreen()
            return
        }

        if (packageName == SETTINGS_PACKAGE && automationPrefs.getString(BridgeActions.KEY_PENDING_HOTSPOT, null) != null) {
            if (returnPackage == null) returnPackage = lastForegroundPackage
            handler.removeCallbacks(hotspotRetryRunnable)
            handler.postDelayed(hotspotRetryRunnable, HOTSPOT_RETRY_MS)
            if (!hotspotTimeoutScheduled) {
                hotspotTimeoutScheduled = true
                handler.postDelayed(hotspotTimeoutRunnable, HOTSPOT_TIMEOUT_MS)
            }
            return
        }

        if (isCallUiPackage(packageName)) {
            val root = rootInActiveWindow
            if (containsCallUiMarker(root)) callUiSeen = true
            return
        }

        if (callUiSeen && packageName == returnPackage) {
            // The system already returned to the previous app; do not launch it again.
            callUiSeen = false
            returnPackage = null
        }
    }

    private fun attemptPhonebookNavigation() {
        if (!automationPrefs.getBoolean(BridgeActions.KEY_PENDING_PHONEBOOK, false)) {
            phonebookFallbackScheduled = false
            return
        }
        val root = rootInActiveWindow
        val button = findNode(root) {
            it.viewIdResourceName?.contains("ib_phone_book", ignoreCase = true) == true
        }
        if (button != null && clickNodeOrAncestor(button)) {
            automationPrefs.edit().putBoolean(BridgeActions.KEY_PENDING_PHONEBOOK, false).apply()
            phonebookFallbackScheduled = false
            Log.i(TAG, "Selected Bluetooth Phone Book using its accessibility view ID")
            return
        }

        if (!phonebookFallbackScheduled) {
            phonebookFallbackScheduled = true
            handler.postDelayed({
                if (!automationPrefs.getBoolean(BridgeActions.KEY_PENDING_PHONEBOOK, false)) {
                    phonebookFallbackScheduled = false
                    return@postDelayed
                }
                val metrics = resources.displayMetrics
                val x = metrics.widthPixels * (2.5f / 6f)
                val y = metrics.heightPixels - (42f * metrics.density)
                val path = Path().apply { moveTo(x, y) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 90))
                    .build()
                val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        automationPrefs.edit().putBoolean(BridgeActions.KEY_PENDING_PHONEBOOK, false).apply()
                        phonebookFallbackScheduled = false
                        Log.i(TAG, "Selected Bluetooth Phone Book using landscape fallback tap at $x,$y")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        phonebookFallbackScheduled = false
                        Log.w(TAG, "Phone Book fallback gesture was cancelled")
                    }
                }, null)
                if (!dispatched) {
                    phonebookFallbackScheduled = false
                    Log.w(TAG, "Could not dispatch Phone Book fallback gesture")
                }
            }, PHONEBOOK_FALLBACK_DELAY_MS)
        }
    }

    private fun inspectBluetoothCallScreen() {
        val root = rootInActiveWindow ?: return
        if (containsCallUiMarker(root)) {
            callUiSeen = true
            return
        }
        if (callUiSeen && !callReturnScheduled) {
            callReturnScheduled = true
            handler.removeCallbacks(callReturnRunnable)
            handler.postDelayed(callReturnRunnable, CALL_RETURN_DELAY_MS)
        }
    }

    private fun attemptHotspotToggle() {
        val requested = automationPrefs.getString(BridgeActions.KEY_PENDING_HOTSPOT, null) ?: return
        val root = rootInActiveWindow ?: return
        val shouldBeEnabled = requested == "on"

        // First look for the real switch. Never tap it twice while waiting for
        // Android to publish the changed checked state.
        val label = findNode(root) { isHotspotToggleLabel(it) }
        if (label != null) {
            val row = label.parent ?: label
            val switchNode = findNode(row) {
                it.isCheckable || it.className?.toString()?.contains("Switch", ignoreCase = true) == true
            } ?: if (label.isCheckable) label else null

            if (switchNode != null) {
                if (switchNode.isChecked == shouldBeEnabled) {
                    completeHotspotAutomation("Hotspot verified in requested state: $requested")
                    return
                }
                if (automationPrefs.getBoolean(BridgeActions.KEY_HOTSPOT_TOGGLE_SENT, false)) {
                    Log.d(TAG, "Waiting for hotspot switch state to change; will not tap a second time")
                    handler.removeCallbacks(hotspotRetryRunnable)
                    handler.postDelayed(hotspotRetryRunnable, HOTSPOT_VERIFY_DELAY_MS)
                    return
                }
                if (clickNodeOrAncestor(switchNode)) {
                    automationPrefs.edit().putBoolean(BridgeActions.KEY_HOTSPOT_TOGGLE_SENT, true).apply()
                    Log.i(TAG, "Tapped Wi-Fi hotspot switch once; waiting for state confirmation")
                    handler.removeCallbacks(hotspotRetryRunnable)
                    handler.postDelayed(hotspotRetryRunnable, HOTSPOT_VERIFY_DELAY_MS)
                }
                return
            }
        }

        // Some TS10 Android builds open the parent page first. Navigate by
        // accessible text instead of assuming a fixed screen coordinate.
        val stage = automationPrefs.getInt(BridgeActions.KEY_HOTSPOT_STAGE, 0)
        val nextEntry = if (stage == 0) {
            findNode(root) { isHotspotSettingsEntry(it) }
                ?: findNode(root) { isHotspotMenuEntry(it) }
        } else {
            findNode(root) { isHotspotMenuEntry(it) }
        }

        if (nextEntry != null && clickNodeOrAncestor(nextEntry)) {
            automationPrefs.edit()
                .putInt(BridgeActions.KEY_HOTSPOT_STAGE, if (stage == 0) 1 else 2)
                .apply()
            Log.i(TAG, "Opened hotspot settings navigation stage ${stage + 1}")
            handler.removeCallbacks(hotspotRetryRunnable)
            handler.postDelayed(hotspotRetryRunnable, HOTSPOT_VERIFY_DELAY_MS)
            return
        }
        Log.d(TAG, "Hotspot switch/menu not visible yet; waiting for next Settings accessibility event")
    }

    private fun completeHotspotAutomation(message: String) {
        Log.i(TAG, message)
        automationPrefs.edit()
            .remove(BridgeActions.KEY_PENDING_HOTSPOT)
            .remove(BridgeActions.KEY_HOTSPOT_STAGE)
            .remove(BridgeActions.KEY_HOTSPOT_TOGGLE_SENT)
            .apply()
        handler.removeCallbacks(hotspotTimeoutRunnable)
        handler.removeCallbacks(hotspotRetryRunnable)
        hotspotTimeoutScheduled = false
        restorePreviousApp()
    }

    private fun restorePreviousApp() {
        val target = returnPackage
        returnPackage = null
        if (target.isNullOrBlank() || target == packageName) return
        try {
            val launch = packageManager.getLaunchIntentForPackage(target)
            if (launch == null) {
                Log.w(TAG, "No launcher activity to restore for $target")
                return
            }
            launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(launch)
            Log.i(TAG, "Restored previous app: $target")
        } catch (e: Exception) {
            Log.e(TAG, "Could not restore previous app $target", e)
        }
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String =
        listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            .joinToString(" ").trim().lowercase(Locale.ROOT)

    private fun hasClickableAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(6) {
            if (current == null) return false
            if (current!!.isClickable) return true
            current = current!!.parent
        }
        return false
    }

    private fun isHotspotSettingsEntry(node: AccessibilityNodeInfo): Boolean {
        val value = nodeLabel(node)
        val matches = value.contains("hotspot & tethering") || value.contains("hotspot and tethering") ||
            value.contains("נקודה חמה ושיתוף אינטרנט") ||
            value.contains("שיתוף אינטרנט ונקודה חמה") ||
            value.contains("נקודה חמה וקישור")
        return matches && hasClickableAncestor(node)
    }

    private fun isHotspotMenuEntry(node: AccessibilityNodeInfo): Boolean {
        val value = nodeLabel(node)
        val matches = value == "hotspot" || value == "wi-fi hotspot" || value == "wifi hotspot" ||
            value == "נקודה חמה" || value == "נקודת גישה אישית" ||
            value == "נקודה חמה אלחוטית"
        return matches && hasClickableAncestor(node)
    }

    private fun isHotspotToggleLabel(node: AccessibilityNodeInfo): Boolean {
        val value = nodeLabel(node)
        if (value.isBlank() || isHotspotSettingsEntry(node)) return false
        return value.contains("wi-fi hotspot") || value.contains("wifi hotspot") ||
            value == "hotspot" || value.contains("mobile hotspot") ||
            value == "נקודה חמה" || value.contains("נקודת גישה אישית") ||
            value == "נקודה חמה אלחוטית"
    }

    private fun containsCallUiMarker(root: AccessibilityNodeInfo?): Boolean {
        val values = mutableListOf<String>()
        collectText(root, values)
        val text = values.joinToString(" ").lowercase(Locale.ROOT)
        return listOf(
            "end call", "hang up", "disconnect call", "call duration", "in call",
            "סיום שיחה", "ניתוק שיחה", "נתק שיחה", "שיחה פעילה"
        ).any { text.contains(it) }
    }

    private fun isCallUiPackage(value: String): Boolean {
        val lower = value.lowercase(Locale.ROOT)
        return lower.contains("incall") || lower == "com.android.phone" ||
            lower.contains("telecom")
    }

    private fun isIgnoredForReturnPackage(value: String): Boolean {
        val lower = value.lowercase(Locale.ROOT)
        return lower == packageName || lower == CHATGPT_PACKAGE ||
            lower == BLUETOOTH_PACKAGE || lower == SETTINGS_PACKAGE ||
            lower.contains("systemui") || lower.contains("launcher") ||
            isCallUiPackage(lower)
    }

    private fun findNode(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null || !node.isVisibleToUser) return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) {
            val found = findNode(node.getChild(index), predicate)
            if (found != null) return found
        }
        return null
    }

    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(6) {
            if (current == null) return false
            if (current!!.isClickable && current!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current!!.parent
        }
        return false
    }

    private fun speakNotice(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "assistant-bridge-notice")
    }

    private fun readLatestAnswer() {
        val root = rootInActiveWindow ?: run {
            Log.w(TAG, "No active accessibility window in ChatGPT")
            return
        }
        val visibleText = mutableListOf<String>()
        collectText(root, visibleText)
        val candidates = visibleText
            .map { it.replace("\\s+".toRegex(), " ").trim() }
            .filter { it.length >= MIN_ANSWER_LENGTH && !isUiLabel(it) }
            .distinct()
        // ChatGPT often exposes the complete assistant message on a parent node;
        // choose the longest visible text candidate rather than the last tiny child.
        val answer = candidates.maxByOrNull { it.length } ?: run {
            Log.d(TAG, "No answer text found; visible candidates=${visibleText.size}")
            return
        }
        Log.d(TAG, "Candidate answer length=${answer.length}; candidates=${candidates.size}")
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
            val result = tts?.speak(chunk, queueMode, null, "chatgpt-answer-$index")
            Log.d(TAG, "Queued TTS chunk $index; result=$result; chars=${chunk.length}")
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
        handler.removeCallbacks(phonebookRetryRunnable)
        handler.removeCallbacks(hotspotRetryRunnable)
        handler.removeCallbacks(hotspotTimeoutRunnable)
        handler.removeCallbacks(callReturnRunnable)
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AssistantBridgeTTS"
        private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
        private const val BLUETOOTH_PACKAGE = "com.tw.bt"
        private const val SETTINGS_PACKAGE = "com.android.settings"
        private const val QUIET_PERIOD_MS = 3000L
        private const val MIN_ANSWER_LENGTH = 25
        private const val MAX_TTS_CHARS = 3500
        private const val PHONEBOOK_RETRY_MS = 450L
        private const val PHONEBOOK_FALLBACK_DELAY_MS = 700L
        private const val HOTSPOT_RETRY_MS = 500L
        private const val HOTSPOT_VERIFY_DELAY_MS = 900L
        private const val HOTSPOT_TIMEOUT_MS = 9000L
        private const val CALL_RETURN_DELAY_MS = 1800L
    }
}
