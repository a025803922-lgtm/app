package com.example.assistantbridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import android.view.KeyEvent

data class ContactMatch(val name: String, val number: String)

object BridgeActions {
    const val ACTION_COMMAND = "com.example.assistantbridge.COMMAND"
    const val EXTRA_TOKEN = "token"
    const val EXTRA_COMMAND = "command"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_NUMBER = "number"
    const val EXTRA_TEXT = "text"
    const val SHARED_TOKEN = "CHANGE_THIS_TOKEN"
    const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    const val BLUETOOTH_PHONE_PACKAGE = "com.tw.bt"
    const val BLUETOOTH_PHONE_ACTIVITY = "com.tw.bt.ATBluetoothActivity"

    fun mediaKey(context: Context, keyCode: Int) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {
            Log.e("AssistantBridge", "Media key dispatch failed", e)
        }
    }

    fun adjustMusicVolume(context: Context, increase: Boolean) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (e: Exception) {
            Log.e("AssistantBridge", "Music volume adjustment failed", e)
        }
    }

    fun openPackage(context: Context, packageName: String) {
        try {
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launch == null) {
                Log.w("AssistantBridge", "App not found or has no launch activity: $packageName")
                return
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        } catch (e: Exception) {
            Log.e("AssistantBridge", "Could not open $packageName", e)
        }
    }

    fun openChatGPT(context: Context) = openPackage(context, CHATGPT_PACKAGE)

    /** Opens an installed app by its visible launcher name. */
    fun openAppByName(context: Context, requestedName: String): String {
        val query = requestedName.trim().lowercase(java.util.Locale.ROOT)
        if (query.isBlank()) return "Please say the app name."
        return try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = context.packageManager.queryIntentActivities(launcherIntent, 0)
                .mapNotNull { info ->
                    val label = info.loadLabel(context.packageManager)?.toString()?.trim().orEmpty()
                    val packageName = info.activityInfo?.packageName.orEmpty()
                    if (label.isBlank() || packageName == context.packageName) null
                    else Triple(label, packageName, label.lowercase(java.util.Locale.ROOT))
                }
                .distinctBy { it.second }
            val exact = apps.filter { it.third == query }
            val matches = if (exact.isNotEmpty()) exact else apps.filter {
                it.third.startsWith(query) || it.third.contains(query)
            }
            when (matches.size) {
                0 -> "I couldn't find an installed app named $requestedName."
                1 -> {
                    val (label, packageName) = matches.first()
                    val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                        ?: return "The app $label does not have a launcher activity."
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    "Opened $label."
                }
                else -> "More than one app matches $requestedName: " +
                    matches.take(4).joinToString(", ") { it.first } +
                    ". Please say the full app name."
            }
        } catch (e: Exception) {
            Log.e("AssistantBridge", "Could not open app by name: $requestedName", e)
            "I couldn't open $requestedName."
        }
    }

    fun openBluetoothPhone(context: Context) = openPackage(context, BLUETOOTH_PHONE_PACKAGE)

    fun findContacts(context: Context, query: String): List<ContactMatch> {
        if (query.isBlank()) return emptyList()
        val matches = mutableListOf<ContactMatch>()
        val queries = linkedSetOf(query, query.removePrefix("ל").takeIf { query.startsWith("ל") && query.length > 1 }.orEmpty())
            .filter { it.isNotBlank() }
        try {
            for (candidate in queries) {
                val candidateUri = Uri.withAppendedPath(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                    Uri.encode(candidate)
                )
                context.contentResolver.query(
                    candidateUri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
                )?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0 && numberIndex >= 0) {
                            val name = cursor.getString(nameIndex).orEmpty()
                            val number = cursor.getString(numberIndex).orEmpty()
                            if (number.isNotBlank() && matches.none { it.number == number }) {
                                matches.add(ContactMatch(name, number))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AssistantBridge", "Contact lookup failed", e)
        }
        return matches
    }

    fun sendTextToChatGPT(context: Context, text: String) {
        try {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage(CHATGPT_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (share.resolveActivity(context.packageManager) != null) {
                context.startActivity(share)
            } else {
                Log.w("AssistantBridge", "ChatGPT does not expose a compatible text-share activity; opening the app instead")
                openChatGPT(context)
            }
        } catch (e: Exception) {
            Log.e("AssistantBridge", "Could not send recognized text to ChatGPT", e)
            openChatGPT(context)
        }
    }

    fun startVoiceCommand(context: Context, mode: String) {
        val intent = Intent(context, VoiceCommandActivity::class.java)
            .putExtra(VoiceCommandActivity.EXTRA_MODE, mode)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun dial(context: Context, number: String) {
        val uri = Uri.fromParts("tel", number, null)
        val bluetoothIntent = Intent(Intent.ACTION_DIAL, uri).apply {
            component = ComponentName(BLUETOOTH_PHONE_PACKAGE, BLUETOOTH_PHONE_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(bluetoothIntent)
        } catch (e: Exception) {
            Log.e("AssistantBridge", "Could not open TS10 Bluetooth dialer; refusing to open Android dialer instead", e)
            openBluetoothPhone(context)
        }
    }
}
