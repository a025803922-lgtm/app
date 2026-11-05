package com.example.assistantbridge

import android.content.Context

enum class VoiceAction {
    VOLUME_UP,
    VOLUME_DOWN,
    MUSIC_PLAY_PAUSE,
    MUSIC_NEXT,
    MUSIC_PREVIOUS,
    OPEN_CHATGPT
}

object VoiceCommandParser {
    fun parse(raw: String): VoiceAction? {
        val text = raw.lowercase().trim()
        return when {
            listOf("הגבר קול", "תגביר קול", "ווליום למעלה", "קול למעלה", "volume up", "increase volume", "louder")
                .any { text.contains(it) } -> VoiceAction.VOLUME_UP
            listOf("הנמך קול", "הנמיך קול", "ווליום למטה", "קול למטה", "volume down", "decrease volume", "quieter")
                .any { text.contains(it) } -> VoiceAction.VOLUME_DOWN
            listOf("השיר הבא", "שיר הבא", "הבא שיר", "next track", "next song")
                .any { text.contains(it) } -> VoiceAction.MUSIC_NEXT
            listOf("השיר הקודם", "שיר קודם", "הקודם", "previous track", "previous song")
                .any { text.contains(it) } -> VoiceAction.MUSIC_PREVIOUS
            listOf("נגן מוזיקה", "הפעל מוזיקה", "עצור מוזיקה", "השהה מוזיקה", "נגן", "השהה", "play pause", "play music", "pause music")
                .any { text.contains(it) } -> VoiceAction.MUSIC_PLAY_PAUSE
            listOf("פתח צ'אט ג'י פי טי", "פתח צ'אט גיפיטי", "פתח chatgpt", "open chatgpt")
                .any { text.contains(it) } -> VoiceAction.OPEN_CHATGPT
            else -> null
        }
    }

    fun execute(context: Context, action: VoiceAction) {
        when (action) {
            VoiceAction.VOLUME_UP -> BridgeActions.adjustMusicVolume(context, true)
            VoiceAction.VOLUME_DOWN -> BridgeActions.adjustMusicVolume(context, false)
            VoiceAction.MUSIC_PLAY_PAUSE -> BridgeActions.mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            VoiceAction.MUSIC_NEXT -> BridgeActions.mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            VoiceAction.MUSIC_PREVIOUS -> BridgeActions.mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            VoiceAction.OPEN_CHATGPT -> BridgeActions.openChatGPT(context)
        }
    }
}
