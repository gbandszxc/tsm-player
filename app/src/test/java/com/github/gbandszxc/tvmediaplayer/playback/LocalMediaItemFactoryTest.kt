package com.github.gbandszxc.tvmediaplayer.playback

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalMediaItemFactoryTest {
    @Test
    fun `creates file media item with filename title`() {
        val file = File("C:/Music/Album/01 - Intro.flac")
        val item = LocalMediaItemFactory().create(
            listOf(
                SmbEntry(
                    name = file.name,
                    fullPath = "Music/Album/${file.name}",
                    isDirectory = false,
                    streamUri = file.toURI().toString(),
                )
            )
        ).single()

        assertEquals(file.toURI().toString(), item.localConfiguration?.uri.toString())
        assertEquals("01 - Intro", item.mediaMetadata.title.toString())
    }
}
