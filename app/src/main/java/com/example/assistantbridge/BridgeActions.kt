package com.example.assistantbridge

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

    fun openBluetoothPhone(context: Context) = openPackage(context, BLUETOOTH_PHONE_PACKAGE)

    fun findContacts(context: Context, query: String): List<ContactMatch> {
        if (query.isBlank()) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query))
        val matches = mutableListOf<ContactMatch>()
        val queries = linkedSetOf(query, query.removePrefix("ל").takeIf { query.startsWith("ל") && query.length > 1 }.orEmpty()).filter { it.isNotBlank() }
        try {
            for (candidate in queries) {
            val candidateUri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(candidate))
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
        val bluetoothIntent = Intent(Intent.ACTION_DIAL, uri)
            .setPackage(BLUETOOTH_PHONE_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val defaultIntent = Intent(Intent.ACTION_DIAL, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (bluetoothIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(bluetoothIntent)
            } else {
                context.startActivity(defaultIntent)
            }
        } catch (e: Exception) {
            Log.e("AssistantBridge", "Could not open dialer", e)
            openBluetoothPhone(context)
        }
    }
}