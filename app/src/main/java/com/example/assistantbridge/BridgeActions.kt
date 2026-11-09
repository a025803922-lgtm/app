package com.example.assistantbridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent

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
    const val AUTOMATION_PREFS = "assistant_bridge_automation"
    const val KEY_PENDING_PHONEBOOK = "pending_phonebook"
    const val KEY_PHONE_TARGET = "phone_target"
    const val KEY_PENDING_HOTSPOT = "pending_hotspot"
    const val KEY_HOTSPOT_STAGE = "hotspot_stage"
    const val KEY_HOTSPOT_TOGGLE_SENT = "hotspot_toggle_sent"
    const val KEY_LAST_FOREGROUND_PACKAGE = "last_foreground_package"
    const val KEY_CALL_RETURN_PACKAGE = "call_return_package"

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

    fun openBluetoothPhone(context: Context) = openBluetoothPhonebook(context)

    /**
     * Opens the TS10 Bluetooth app and asks its Accessibility companion to select
     * the Phone Book tab. The user still chooses the contact and confirms the call.
     */
    fun openBluetoothPhonebook(context: Context, contactTarget: String? = null) {
        context.getSharedPreferences(AUTOMATION_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PENDING_PHONEBOOK, true)
            .putString(KEY_PHONE_TARGET, contactTarget.orEmpty())
            .apply()
        openPackage(context, BLUETOOTH_PHONE_PACKAGE)
    }

    /**
     * Opens Android's built-in Hotspot & tethering screen. The enabled Accessibility
     * service locates the Wi-Fi hotspot switch and changes it only after checking its
     * current state, then returns to the app that was previously in the foreground.
     */
    fun setHotspot(context: Context, enabled: Boolean) {
        context.getSharedPreferences(AUTOMATION_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PENDING_HOTSPOT, if (enabled) "on" else "off")
            .putInt(KEY_HOTSPOT_STAGE, 0)
            .putBoolean(KEY_HOTSPOT_TOGGLE_SENT, false)
            .apply()
        val tetherSettings = Intent("android.settings.TETHER_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(tetherSettings)
        } catch (e: Exception) {
            Log.w("AssistantBridge", "Tether settings unavailable; opening main Settings", e)
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (fallback: Exception) {
                Log.e("AssistantBridge", "Could not open hotspot settings", fallback)
                context.getSharedPreferences(AUTOMATION_PREFS, Context.MODE_PRIVATE).edit()
                    .remove(KEY_PENDING_HOTSPOT)
                    .apply()
            }
        }
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

    /** Save the app that was active before the dialer/call UI takes over. */
    fun rememberReturnApp(context: Context) {
        val prefs = context.getSharedPreferences(AUTOMATION_PREFS, Context.MODE_PRIVATE)
        val previousApp = prefs.getString(KEY_LAST_FOREGROUND_PACKAGE, null)
        if (!previousApp.isNullOrBlank() && previousApp != context.packageName) {
            prefs.edit().putString(KEY_CALL_RETURN_PACKAGE, previousApp).apply()
            Log.i("AssistantBridge", "Saved call return destination: $previousApp")
        }
    }

    fun dial(context: Context, number: String) {
        // Save the current app before opening the Bluetooth dialer.
        rememberReturnApp(context)
        val uri = Uri.fromParts("tel", number, null)
        // ACTION_CALL starts the call immediately on this TS10; use ACTION_DIAL so the user must press Call.
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
