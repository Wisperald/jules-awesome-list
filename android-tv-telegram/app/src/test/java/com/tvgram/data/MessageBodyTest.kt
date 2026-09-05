package com.tvgram.data

import com.tvgram.data.messages.MessageBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chat-list preview is the one piece of message rendering that is pure logic,
 * so it is also the one worth pinning down with tests.
 */
class MessageBodyTest {

    @Test
    fun `text previews are the text itself`() {
        assertEquals("hello", MessageBody.Text("hello").preview)
    }

    @Test
    fun `captioned media shows the caption after the label`() {
        val photo = MessageBody.Photo(
            fileId = 1,
            thumbnailFileId = 2,
            caption = "at the lake",
            width = 100,
            height = 100,
        )
        assertEquals("🖼 Photo: at the lake", photo.preview)
    }

    @Test
    fun `uncaptioned media shows only the label`() {
        val video = MessageBody.Video(
            fileId = 1,
            thumbnailFileId = null,
            caption = "",
            durationSeconds = 30,
            fileName = "clip.mp4",
            mimeType = "video/mp4",
            sizeBytes = 1024,
        )
        assertEquals("🎬 Video", video.preview)
    }

    @Test
    fun `audio previews prefer performer and title together`() {
        val audio = MessageBody.Audio(
            fileId = 1,
            title = "Dancer",
            performer = "Bowie",
            durationSeconds = 200,
        )
        assertEquals("🎵 Bowie — Dancer", audio.preview)
    }

    @Test
    fun `a missed call reads differently from an answered one`() {
        val missed = MessageBody.Call(durationSeconds = 0, isVideo = false, isMissed = true)
        val answered = MessageBody.Call(durationSeconds = 90, isVideo = true, isMissed = false)
        assertEquals("Missed call", missed.preview)
        assertEquals("Video call", answered.preview)
    }

    @Test
    fun `unsupported content still produces something printable`() {
        assertTrue(MessageBody.Unsupported("Poll").preview.isNotBlank())
    }
}
