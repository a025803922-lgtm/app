package com.example.gchatcompanion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

class ChatNotificationListener : NotificationListenerService() {
    companion object {
        const val CHAT_PACKAGE = "com.google.android.apps.dynamite"
        private const val CHANNEL_ID = "google_chat_mirror"
        private const val PREFS = "chat_companion"
        private const val HISTORY = "history"

        @Volatile var activeService: ChatNotificationListener? = null
            private set

        fun hasNotificationAccess(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            return enabled.contains(context.packageName)
        }

        fun history(context: Context): JSONArray {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(HISTORY, "[]") ?: "[]"
            return try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        }

        fun openConversation(key: String): Boolean {
            val service = activeService ?: return false
            val sbn = service.activeNotifications?.firstOrNull { it.key == key } ?: return false
            return try {
                sbn.notification.contentIntent?.send()
                true
            } catch (_: Exception) { false }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeService = this
        createChannel()
    }

    override fun onListenerDisconnected() {
        activeService = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != CHAT_PACKAGE) return
        val notification = sbn.notification
        val extras = notification.extras ?: Bundle()
        val title = NotificationPreview.title(extras.getCharSequence(Notification.EXTRA_TITLE))
        val body = NotificationPreview.body(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
        )

        saveMessage(sbn.key, title, body, sbn.postTime, hasReplyAction(notification))
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("mirror", true)) {
            postMirror(sbn, title, body)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == CHAT_PACKAGE) {
            getSystemService(NotificationManager::class.java)
                .cancel("gchat_mirror", sbn.key.hashCode())
        }
    }

    private fun hasReplyAction(notification: Notification): Boolean =
        notification.actions?.any { it.remoteInputs?.isNotEmpty() == true } == true

    private fun saveMessage(key: String, title: String, body: String, time: Long, canReply: Boolean) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val old = try { JSONArray(prefs.getString(HISTORY, "[]") ?: "[]") } catch (_: Exception) {
            JSONArray()
        }
        val next = JSONArray()
        next.put(JSONObject().apply {
            put("key", key); put("title", title); put("body", body)
            put("time", time); put("canReply", canReply)
        })
        for (i in 0 until minOf(old.length(), 99)) {
            val item = old.optJSONObject(i) ?: continue
            if (item.optString("key") != key || item.optLong("time") != time) next.put(item)
        }
        prefs.edit().putString(HISTORY, next.toString()).apply()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Google Chat messages", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun postMirror(sbn: StatusBarNotification, title: String, body: String) {
        val original = sbn.notification
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setWhen(sbn.postTime)
            .setShowWhen(true)
            .setAutoCancel(true)
        original.contentIntent?.let { builder.setContentIntent(it) }

        val reply = original.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
        if (reply != null) {
            val actionBuilder = Notification.Action.Builder(reply.icon, reply.title ?: "Reply", reply.actionIntent)
            reply.remoteInputs?.forEach { actionBuilder.addRemoteInput(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                actionBuilder.setAllowGeneratedReplies(reply.allowGeneratedReplies)
            }
            builder.addAction(actionBuilder.build())
        }
        getSystemService(NotificationManager::class.java)
            .notify("gchat_mirror", sbn.key.hashCode(), builder.build())
    }
}
