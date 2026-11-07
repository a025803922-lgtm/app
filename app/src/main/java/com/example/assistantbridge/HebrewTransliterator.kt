package com.example.assistantbridge

import java.util.Locale

/**
 * Offline Hebrew transliteration helper for speech recognizers that return Hebrew
 * spoken phonetically in Latin letters. Known words are mapped exactly; unknown
 * words use a conservative phonetic fallback when the sentence looks Hebrew.
 */
object HebrewTransliterator {
    private val words = mapOf(
        "ani" to "אני", "anochi" to "אנוכי", "ata" to "אתה", "at" to "את",
        "atem" to "אתם", "hem" to "הם", "hen" to "הן", "ze" to "זה", "zeh" to "זה",
        "zot" to "זאת", "ele" to "אלה", "ma" to "מה", "lama" to "למה",
        "eifo" to "איפה", "eich" to "איך", "eikh" to "איך", "mi" to "מי",
        "matai" to "מתי", "kama" to "כמה", "rotze" to "רוצה", "rotza" to "רוצה",
        "tzarich" to "צריך", "tsarich" to "צריך", "tzricha" to "צריכה",
        "yesh" to "יש", "ein" to "אין", "im" to "אם", "ve" to "ו", "ha" to "ה",
        "et" to "את", "shel" to "של", "li" to "לי", "lanu" to "לנו",
        "le" to "ל", "la" to "ל", "el" to "אל", "al" to "על", "be" to "ב",
        "ba" to "ב", "ki" to "כי", "aval" to "אבל", "gam" to "גם",
        "zeh" to "זה", "tov" to "טוב", "tova" to "טובה", "toda" to "תודה",
        "bevakasha" to "בבקשה", "slיחה" to "סליחה", "slicha" to "סליחה",
        "shalom" to "שלום", "boker" to "בוקר", "layla" to "לילה",
        "erev" to "ערב", "hayom" to "היום", "machar" to "מחר",
        "achshav" to "עכשיו", "yesterday" to "אתמול", "me" to "מ",
        "bayit" to "בית", "bait" to "בית", "bayet" to "בית", "habayit" to "הבית",
        "bayta" to "הביתה", "baitah" to "הביתה", "home" to "בית",
        "titkasher" to "תתקשר", "tiskasher" to "תתקשר", "tikasher" to "תתקשר",
        "hitkasher" to "התקשר", "nagen" to "נגן", "naghen" to "נגן",
        "muzika" to "מוזיקה", "music" to "מוזיקה", "atsor" to "עצור",
        "atzor" to "עצור", "haashe" to "השהה", "tamshech" to "תמשיך",
        "hagdil" to "הגדל", "tagbir" to "תגביר", "hanemich" to "הנמיך",
        "hanmech" to "הנמך", "tamnich" to "תנמיך", "volyum" to "ווליום",
        "shir" to "שיר", "hashir" to "השיר", "haba" to "הבא", "maavar" to "מעבר",
        "maavir" to "מעביר", "kodem" to "קודם", "hakodem" to "הקודם",
        "ptach" to "פתח", "patach" to "פתח", "chatgpt" to "ChatGPT",
        "new" to "ניו", "york" to "יורק", "israel" to "ישראל"
    )

    fun toHebrew(input: String): String {
        if (input.any { it in '\u0590'..'\u05ff' }) return input
        val tokens = Regex("[A-Za-z]+(?:'[A-Za-z]+)?|[^A-Za-z]+").findAll(input).map { it.value }.toList()
        val latinWords = tokens.filter { it.any(Char::isLetter) }
        if (latinWords.isEmpty()) return input
        val normalized = latinWords.map { it.lowercase(Locale.ROOT) }
        val looksHebrew = normalized.any { it in words } ||
            normalized.any { it in setOf("et", "ha", "shel", "ani", "ata", "la", "le", "li", "ze", "ma", "eifo") }
        if (!looksHebrew) return input

        return tokens.joinToString("") { token ->
            if (token.none(Char::isLetter)) token
            else {
                val lower = token.lowercase(Locale.ROOT)
                words[lower] ?: transliterateWord(lower)
            }
        }
    }

    private fun transliterateWord(word: String): String {
        val replacements = listOf(
            "sh" to "ש", "ch" to "ח", "kh" to "ח", "tz" to "צ", "ts" to "צ",
            "ph" to "פ", "th" to "ת", "zh" to "ז"
        )
        var remaining = word
        val result = StringBuilder()
        while (remaining.isNotEmpty()) {
            val pair = replacements.firstOrNull { remaining.startsWith(it.first) }
            if (pair != null) {
                result.append(pair.second)
                remaining = remaining.drop(pair.first.length)
                continue
            }
            val c = remaining.first()
            result.append(
                when (c) {
                    'b' -> "ב"; 'v', 'w' -> "ו"; 'g' -> "ג"; 'd' -> "ד"
                    'h' -> "ה"; 'z' -> "ז"; 'j' -> "ג׳"; 'k', 'q' -> "ק"
                    'l' -> "ל"; 'm' -> "מ"; 'n' -> "נ"; 's' -> "ס"
                    'p', 'f' -> "פ"; 'r' -> "ר"; 't' -> "ת"; 'y' -> "י"
                    'a' -> "א"; 'e' -> "א"; 'i' -> "י"; 'o', 'u' -> "ו"
                    'c' -> "ק"; else -> c.toString()
                }
            )
            remaining = remaining.drop(1)
        }
        return result.toString()
    }
}
