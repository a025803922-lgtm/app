package com.example.gchatcompanion

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPreviewTest {
    @Test
    fun titleUsesGoogleChatFallbackWhenMissingOrBlank() {
        assertEquals("Google Chat", NotificationPreview.title(null))
        assertEquals("Google Chat", NotificationPreview.title("   "))
        assertEquals("Alex", NotificationPreview.title("Alex"))
    }

    @Test
    fun bodyPrefersExpandedTextThenOrdinaryTextThenSummary() {
        assertEquals("expanded", NotificationPreview.body("expanded", "short", "summary"))
        assertEquals("short", NotificationPreview.body(null, "short", "summary"))
        assertEquals("summary", NotificationPreview.body(null, " ", "summary"))
        assertEquals("New message", NotificationPreview.body(null, null, null))
    }
}
