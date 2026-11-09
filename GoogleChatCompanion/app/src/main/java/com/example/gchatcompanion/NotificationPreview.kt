package com.example.gchatcompanion

internal object NotificationPreview {
    fun title(value: CharSequence?): String =
        value?.toString()?.takeIf { it.isNotBlank() } ?: "Google Chat"

    fun body(bigText: CharSequence?, text: CharSequence?, summary: CharSequence?): String =
        listOf(bigText, text, summary)
            .firstNotNullOfOrNull { it?.toString()?.takeIf(String::isNotBlank) }
            ?: "New message"
}
