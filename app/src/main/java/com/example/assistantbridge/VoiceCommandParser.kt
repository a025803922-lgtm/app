package com.example.assistantbridge

import android.content.Context
import java.text.Normalizer
import java.util.Locale

enum class VoiceAction { VOLUME_UP, VOLUME_DOWN, MUSIC_PLAY_PAUSE, MUSIC_NEXT, MUSIC_PREVIOUS, OPEN_CHATGPT }

object VoiceCommandParser {
    private fun normalize(raw: String): String =
        Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^\\p{L}\\p{N}]+".toRegex(), " ")
            .trim()

    fun parse(raw: String): VoiceAction? {
        val text = normalize(raw)
        val phrases = listOf(
            VoiceAction.VOLUME_UP to listOf("הגבר קול", "תגביר קול", "ווליום למעלה", "קול למעלה", "תגביר את הווליום", "volume up", "increase volume", "turn it up", "louder"),
            VoiceAction.VOLUME_DOWN to listOf("הנמך קול", "הנמיך קול", "ווליום למטה", "קול למטה", "תנמיך את הווליום", "volume down", "decrease volume", "turn it down", "quieter"),
            VoiceAction.MUSIC_NEXT to listOf("השיר הבא", "שיר הבא", "הבא שיר", "תעביר שיר", "next track", "next song", "skip track"),
            VoiceAction.MUSIC_PREVIOUS to listOf("השיר הקודם", "שיר קודם", "הקודם", "previous track", "previous song", "go back a song"),
            VoiceAction.MUSIC_PLAY_PAUSE to listOf("נגן מוזיקה", "הפעל מוזיקה", "עצור מוזיקה", "השהה מוזיקה", "נגן", "השהה", "play pause", "play music", "pause music", "resume music", "stop music"),
            VoiceAction.OPEN_CHATGPT to listOf("פתח צאט גי פי טי", "פתח צאט גיפיטי", "פתח chatgpt", "open chatgpt", "open chat gpt")
        )
        return phrases.firstOrNull { (_, alternatives) -> alternatives.any { text.contains(normalize(it)) } }?.first
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
