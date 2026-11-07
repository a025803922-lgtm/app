package com.example.assistantbridge

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class VoiceCommandActivity : Activity() {
    private lateinit var status: TextView
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var mode: String = MODE_COMMAND
    private var pendingContactTarget: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_COMMAND
        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val languageResult = tts?.setLanguage(Locale("he", "IL"))
                if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
            }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }
        status = TextView(this).apply {
            text = if (mode == MODE_CHATGPT) {
                "Speak your Hebrew question. The app will convert phonetic Hebrew to Hebrew text before opening ChatGPT."
            } else {
                "Say a command in Hebrew using English letters, for example: nagen muzika or titkasher la-bayit."
            }
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(12, 24, 12, 24)
        }
        layout.addView(status)
        layout.addView(Button(this).apply {
            text = "Start listening (English phonetic input)"
            setOnClickListener { startRecognition() }
        })
        layout.addView(Button(this).apply {
            text = "Open ChatGPT"
            setOnClickListener { BridgeActions.openChatGPT(this@VoiceCommandActivity) }
        })
        setContentView(layout)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        }
    }

    private fun speak(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "assistant-bridge")
    }

    private fun handleContactCall(target: String) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingContactTarget = target
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS)
            status.text = "Allow contacts access to find $target."
            return
        }
        val matches = BridgeActions.findContacts(this, target)
        when (matches.size) {
            0 -> {
                status.text = "No phone number found for: $target"
                speak("לא מצאתי מספר עבור $target")
            }
            1 -> {
                val match = matches.first()
                status.text = "Opening Bluetooth phone for ${match.name}: ${match.number}"
                speak("מחייג אל ${match.name}")
                BridgeActions.dial(this, match.number)
            }
            else -> {
                status.text = "Choose the contact to call."
                val labels = matches.map { "${it.name} — ${it.number}" }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Which contact?")
                    .setItems(labels) { _, which ->
                        val match = matches[which]
                        speak("מחייג אל ${match.name}")
                        BridgeActions.dial(this, match.number)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun startRecognition() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(listener)
                }
            }
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            status.text = "Listening in phonetic-English mode…"
            recognizer?.startListening(request)
        } catch (e: Exception) {
            status.text = "Could not start voice recognition: ${e.message}"
            speak("לא ניתן להתחיל זיהוי קולי")
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { status.text = "Listening…" }
        override fun onBeginningOfSpeech() { status.text = "Hearing you…" }
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() { status.text = "Processing…" }
        override fun onError(error: Int) {
            status.text = "Recognition error ($error). Check microphone permission and speech service, then try again."
            speak("לא שמעתי. נסה שוב")
        }
        override fun onResults(results: Bundle?) {
            val alternatives = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val recognized = alternatives.firstOrNull().orEmpty()
            if (recognized.isBlank()) {
                status.text = "I didn't catch that. Please try again."
                speak("לא קלטתי. נסה שוב")
                return
            }
            status.text = "Heard: $recognized"
            if (mode == MODE_CHATGPT) {
                val hebrewText = HebrewTransliterator.toHebrew(recognized)
                BridgeActions.sendTextToChatGPT(this@VoiceCommandActivity, hebrewText)
                status.append("\nChatGPT text: $hebrewText")
                status.append("\nChatGPT opened with the converted text. Tap Send if it is not sent automatically.")
                speak("פתחתי את צ'אט ג'י פי טי עם השאלה")
            } else {
                val callTarget = alternatives.asSequence()
                    .mapNotNull { VoiceCommandParser.parseCallTarget(it) }
                    .firstOrNull()
                if (callTarget != null) {
                    handleContactCall(callTarget)
                    return
                }
                val command = alternatives.asSequence()
                    .mapNotNull { VoiceCommandParser.parse(it) }
                    .firstOrNull()
                if (command == null) {
                    status.append("\nCommand not recognized. Try a shorter phrase.")
                    speak("הפקודה לא זוהתה")
                } else {
                    VoiceCommandParser.execute(this@VoiceCommandActivity, command)
                    status.append("\nCommand executed: $command")
                    speak(when (command) {
                        VoiceAction.VOLUME_UP -> "מגביר קול"
                        VoiceAction.VOLUME_DOWN -> "מנמיך קול"
                        VoiceAction.MUSIC_NEXT -> "השיר הבא"
                        VoiceAction.MUSIC_PREVIOUS -> "השיר הקודם"
                        VoiceAction.MUSIC_PLAY_PAUSE -> "מוזיקה"
                        VoiceAction.OPEN_CHATGPT -> "פותח צ'אט ג'י פי טי"
                        VoiceAction.OPEN_BLUETOOTH_PHONE -> "פותח טלפון בלוטות"
                    })
                }
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val alternatives = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val partial = alternatives.firstOrNull()
            if (!partial.isNullOrBlank()) status.text = "Hearing: $partial"
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startRecognition()
        else if (requestCode == REQUEST_AUDIO) Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_LONG).show()
        if (requestCode == REQUEST_CONTACTS) {
            val target = pendingContactTarget
            pendingContactTarget = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && target != null) handleContactCall(target)
            else speak("לא ניתנה הרשאה לאנשי קשר")
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "voice_mode"
        const val MODE_COMMAND = "command"
        const val MODE_CHATGPT = "chatgpt"
        private const val REQUEST_AUDIO = 701
        private const val REQUEST_CONTACTS = 702
    }
}
