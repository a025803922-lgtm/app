package com.example.assistantbridge

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class VoiceCommandActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var languagePicker: Spinner
    private var recognizer: SpeechRecognizer? = null
    private var mode: String = MODE_COMMAND

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_COMMAND
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }
        status = TextView(this).apply {
            text = if (mode == MODE_CHATGPT) "Speak in Hebrew or English. Review the text in ChatGPT before sending."
                else "Choose a language, then speak a command."
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(12, 24, 12, 24)
        }
        layout.addView(status)
        languagePicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@VoiceCommandActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Automatic (system)", "עברית", "English"))
            setSelection(getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_LANGUAGE, 0))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_LANGUAGE, position).apply()
                    recognizer?.destroy()
                    recognizer = null
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
                        if (SpeechRecognizer.isRecognitionAvailable(this)) SpeechRecognizer.createSpeechRecognizer(this, service)
                        else SpeechRecognizer.createSpeechRecognizer(this)
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
                    else -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
                }
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            recognizer?.startListening(request)
        } catch (e: Exception) {
            status.text = "Could not start voice recognition: ${e.message}"
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
        }
        override fun onResults(results: Bundle?) {
            val alternatives = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val recognized = alternatives.firstOrNull().orEmpty()
            if (recognized.isBlank()) {
                status.text = "I didn't catch that. Please try again."
                return
            }
            status.text = "Heard: $recognized"
            if (mode == MODE_CHATGPT) {
                BridgeActions.sendTextToChatGPT(this@VoiceCommandActivity, recognized)
            } else {
                val command = alternatives.asSequence().mapNotNull { VoiceCommandParser.parse(it) }.firstOrNull()
                if (command == null) {
                    status.append("\nCommand not recognized. Try saying it again more clearly.")
                } else {
                    VoiceCommandParser.execute(this@VoiceCommandActivity, command)
                    status.append("\nCommand executed: $command")
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
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "voice_mode"
        const val MODE_COMMAND = "command"
        const val MODE_CHATGPT = "chatgpt"
        private const val REQUEST_AUDIO = 701
        private const val VOICE_PACKAGE = "com.hebvoice.stt"
        private const val VOICE_SERVICE = "com.hebvoice.stt.HebrewRecognitionService"
        private const val PREFS = "assistant_bridge"
        private const val KEY_LANGUAGE = "voice_language"
    }
}
