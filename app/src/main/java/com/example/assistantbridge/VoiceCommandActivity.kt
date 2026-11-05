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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class VoiceCommandActivity : Activity() {
    private lateinit var status: TextView
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
            text = if (mode == MODE_CHATGPT) {
                "Speak in Hebrew. Your recognized text will be passed to ChatGPT for you to review and send."
            } else {
                "Speak a command in Hebrew."
            }
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(12, 24, 12, 24)
        }
        layout.addView(status)
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
                val service = ComponentName(VOICE_PACKAGE, VOICE_SERVICE)
                recognizer = SpeechRecognizer.createSpeechRecognizer(this, service).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            status.text = "Listening…"
                        }
                        override fun onBeginningOfSpeech() { status.text = "Hearing you…" }
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() { status.text = "Processing…" }
                        override fun onError(error: Int) {
                            status.text = "Voice recognition error: $error. Check that Hebrew Voice is installed and microphone permission is allowed."
                        }
                        override fun onResults(results: Bundle?) {
                            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull().orEmpty()
                            if (text.isBlank()) {
                                status.text = "I didn't catch that. Please try again."
                                return
                            }
                            status.text = "Heard: $text"
                            if (mode == MODE_CHATGPT) {
                                BridgeActions.sendTextToChatGPT(this@VoiceCommandActivity, text)
                            } else {
                                val command = VoiceCommandParser.parse(text)
                                if (command == null) {
                                    status.append("\\nCommand not recognized.")
                                } else {
                                    VoiceCommandParser.execute(this@VoiceCommandActivity, command)
                                    status.append("\\nCommand executed: $command")
                                }
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })
                }
            }
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            recognizer?.startListening(request)
        } catch (e: Exception) {
            status.text = "Could not start Hebrew Voice recognition: ${e.message}"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startRecognition()
        } else if (requestCode == REQUEST_AUDIO) {
            Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_LONG).show()
        }
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
    }
}
