package com.example.assistantbridge

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.Normalizer
import java.util.Locale

class VoiceCommandActivity : Activity() {
    private lateinit var status: TextView
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var mode: String = MODE_COMMAND
    private var pendingContactTarget: String? = null
    private var pendingCallNumber: String? = null

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
                "Speak your question in normal Hebrew. The Hebrew Voice recognition service will send Hebrew text directly to ChatGPT."
            } else {
                "Say a command in Hebrew using English letters, for example: nagen muzika or titkasher la-bayit."
            }
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(12, 24, 12, 24)
        }
        layout.addView(status)
        layout.addView(Button(this).apply {
            text = if (mode == MODE_CHATGPT) "Start listening (Hebrew)" else "Start listening (voice commands)"
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

    private fun normalizeContactName(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^\\p{L}\\p{N}]+".toRegex(), " ")
            .trim()
            .replace("\\s+".toRegex(), " ")

    private fun handleContactCall(target: String) {
        pendingContactTarget = target
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Permission is needed to search your synced contacts."
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS)
            return
        }
        searchContactsAndConfirm(target)
    }

    private fun searchContactsAndConfirm(target: String) {
        val matches = mutableListOf<Pair<String, String>>()
        val query = normalizeContactName(target)
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)?.trim().orEmpty()
                    val number = cursor.getString(numberIndex)?.trim().orEmpty()
                    if (name.isNotBlank() && number.isNotBlank() &&
                        normalizeContactName(name).contains(query)) {
                        val item = name to number
                        if (item !in matches) matches.add(item)
                    }
                }
            }
        } catch (e: SecurityException) {
            status.text = "Contacts permission was not granted."
            speak("אין הרשאה לקריאת אנשי הקשר")
            return
        } catch (e: Exception) {
            status.text = "Could not read contacts: ${e.message}"
            return
        }

        when (matches.size) {
            0 -> {
                status.text = "No contact matching $target was found in synced Android Contacts."
                speak("לא מצאתי איש קשר בשם $target")
            }
            1 -> showCallConfirmation(matches[0].first, matches[0].second)
            else -> {
                val labels = matches.map { "${it.first} — ${it.second}" }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Choose a contact")
                    .setItems(labels) { _, which ->
                        val selected = matches[which]
                        showCallConfirmation(selected.first, selected.second)
                    }
                    .setNegativeButton("Cancel") { _, _ -> status.text = "Call cancelled." }
                    .show()
            }
        }
    }

    private fun showCallConfirmation(name: String, number: String) {
        status.text = "Waiting for confirmation to call $name."
        speak("האם להתקשר אל $name")
        AlertDialog.Builder(this)
            .setTitle("Call $name?")
            .setMessage("$name\n$number")
            .setPositiveButton("Yes") { _, _ -> placeConfirmedCall(number) }
            .setNegativeButton("No") { _, _ ->
                status.text = "Call cancelled."
                speak("השיחה בוטלה")
            }
            .setOnCancelListener {
                status.text = "Call cancelled."
            }
            .show()
    }

    private fun placeConfirmedCall(number: String) {
        pendingCallNumber = number
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL)
            return
        }
        startConfirmedCall(number)
    }

    private fun startConfirmedCall(number: String) {
        try {
            // This is the actual contact-call path (ACTION_CALL), so persist the
            // previous foreground app here, immediately before handing off to Android.
            BridgeActions.rememberReturnApp(this)
            val callIntent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
            startActivity(callIntent)
            status.text = "Calling $number."
        } catch (e: Exception) {
            status.text = "Could not start the call: ${e.message}"
            speak("לא הצלחתי להתחיל את השיחה")
        } finally {
            pendingCallNumber = null
        }
    }

    private fun startRecognition() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        try {
            if (recognizer == null) {
                recognizer = if (mode == MODE_CHATGPT) {
                    try {
                        SpeechRecognizer.createSpeechRecognizer(
                            this,
                            ComponentName(VOICE_PACKAGE, VOICE_SERVICE)
                        )
                    } catch (_: Exception) {
                        SpeechRecognizer.createSpeechRecognizer(this)
                    }
                } else {
                    SpeechRecognizer.createSpeechRecognizer(this)
                }.apply { setRecognitionListener(listener) }
            }
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                val language = if (mode == MODE_CHATGPT) "he-IL" else "en-US"
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            status.text = if (mode == MODE_CHATGPT) "Listening in Hebrew…" else "Listening for voice commands…"
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
                BridgeActions.sendTextToChatGPT(this@VoiceCommandActivity, recognized)
                status.append("\nHebrew text sent to ChatGPT: $recognized")
                status.append("\nChatGPT opened with the recognized text. Tap Send if it is not sent automatically.")
                speak("פתחתי את צ'אט ג'י פי טי עם השאלה")
            } else {
                val callTarget = alternatives.asSequence()
                    .mapNotNull { VoiceCommandParser.parseCallTarget(it) }
                    .firstOrNull()
                if (callTarget != null) {
                    handleContactCall(callTarget)
                    return
                }
                val appTarget = alternatives.asSequence()
                    .mapNotNull { VoiceCommandParser.parseOpenAppTarget(it) }
                    .firstOrNull()
                if (appTarget != null) {
                    val result = BridgeActions.openAppByName(this@VoiceCommandActivity, appTarget)
                    status.append("\n$result")
                    speak(if (result.startsWith("Opened")) "פותח את $appTarget" else "לא מצאתי את האפליקציה")
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
                        VoiceAction.OPEN_BLUETOOTH_PHONE -> "פותח את ספר הטלפונים"
                        VoiceAction.HOTSPOT_ON -> "מפעיל נקודה חמה"
                        VoiceAction.HOTSPOT_OFF -> "מכבה נקודה חמה"
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
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            REQUEST_AUDIO -> if (granted) startRecognition()
                else Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_LONG).show()
            REQUEST_CONTACTS -> {
                val target = pendingContactTarget
                if (granted && target != null) searchContactsAndConfirm(target)
                else {
                    status.text = "Contacts permission is required to search names."
                    speak("נדרשת הרשאה לאנשי קשר")
                }
            }
            REQUEST_CALL -> {
                val number = pendingCallNumber
                if (granted && number != null) startConfirmedCall(number)
                else {
                    pendingCallNumber = null
                    status.text = "Call permission was denied. No call was placed."
                    speak("לא ניתנה הרשאה להתקשר. לא בוצעה שיחה")
                }
            }
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
        private const val REQUEST_CALL = 703
        private const val VOICE_PACKAGE = "com.hebvoice.stt"
        private const val VOICE_SERVICE = "com.hebvoice.stt.HebrewRecognitionService"
    }
}
