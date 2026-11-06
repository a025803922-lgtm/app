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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class VoiceCommandActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var languagePicker: Spinner
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var mode: String = MODE_COMMAND
    private var pendingContactTarget: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_COMMAND
        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val selected = if (languagePicker.selectedItemPosition == 2) Locale.US else Locale("he", "IL")
                val languageResult = tts?.setLanguage(selected)
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
            text = if (mode == MODE_CHATGPT) "Speak a question. ChatGPT will open with the recognized text; you may need to tap Send."
                else "Choose a language, then speak a command. You can also say: call followed by a contact name."
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(12, 24, 12, 24)
        }
        layout.addView(status)
        languagePicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@VoiceCommandActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Automatic (system)", "עברית", "English"))
            setSelection(getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_LANGUAGE, 1))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_LANGUAGE, position).apply()
                    recognizer?.destroy()
                    recognizer = null
                    tts?.setLanguage(if (position == 2) Locale.US else Locale("he", "IL"))
                }
            }
        }
        layout.addView(languagePicker)
        layout.addView(Button(this).apply {
            text = "Start listening"
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
                status.text = "Opening phone for ${match.name}: ${match.number}"
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
                val language = languagePicker.selectedItemPosition
                recognizer = when (language) {
                    1 -> {
                        val service = ComponentName(VOICE_PACKAGE, VOICE_SERVICE)
                        try {
                            SpeechRecognizer.createSpeechRecognizer(this, service)
                        } catch (_: Exception) {
                            SpeechRecognizer.createSpeechRecognizer(this)
                        }
                    }
                    else -> SpeechRecognizer.createSpeechRecognizer(this)
                }.apply { setRecognitionListener(listener) }
            }
            val language = languagePicker.selectedItemPosition
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                when (language) {
                    1 -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
                    2 -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    else -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                }
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
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
            status.text = "Recognition error ($error). Check the selected voice service and microphone permission, then try again."
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
                BridgeActions.sendTextToChatGPT(this@VoiceCommandActivity, recognized)
                status.append("\nChatGPT opened with the recognized text. Tap Send if it is not sent automatically.")
                speak("פתחתי את צ'אט ג'י פי טי עם השאלה. אם צריך, לחץ על שליחה")
            } else {
                val callTarget = alternatives.asSequence().mapNotNull { VoiceCommandParser.parseCallTarget(it) }.firstOrNull()
                if (callTarget != null) {
                    handleContactCall(callTarget)
                    return
                }
                val command = alternatives.asSequence().mapNotNull { VoiceCommandParser.parse(it) }.firstOrNull()
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
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
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
        private const val VOICE_PACKAGE = "com.hebvoice.stt"
        private const val VOICE_SERVICE = "com.hebvoice.stt.HebrewRecognitionService"
        private const val PREFS = "assistant_bridge"
        private const val KEY_LANGUAGE = "voice_language"
    }
}