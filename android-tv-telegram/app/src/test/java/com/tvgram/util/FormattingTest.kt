package com.tvgram.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun `durations below an hour omit the hour part`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:07", formatDuration(7))
        assertEquals("1:05", formatDuration(65))
        assertEquals("59:59", formatDuration(3599))
    }

    @Test
    fun `durations of an hour or more include it`() {
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("2:03:04", formatDuration(2 * 3600 + 3 * 60 + 4))
    }

    @Test
    fun `file sizes step through the usual units`() {
        assertEquals("", formatFileSize(0))
        assertEquals("512 B", formatFileSize(512))
        assertEquals("1.0 KB", formatFileSize(1024))
        assertEquals("1.5 MB", formatFileSize((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `unread badges are abbreviated instead of growing`() {
        assertEquals("", formatBadge(0))
        assertEquals("7", formatBadge(7))
        assertEquals("999", formatBadge(999))
        assertEquals("1K", formatBadge(1000))
        assertEquals("12K", formatBadge(12_345))
    }

    @Test
    fun `initials cope with empty, single and multi word titles`() {
        assertEquals("?", initialsOf("   "))
        assertEquals("T", initialsOf("telegram"))
        assertEquals("AB", initialsOf("alice bob carol"))
        assertEquals("АБ", initialsOf("Алиса Борисова"))
    }
}
