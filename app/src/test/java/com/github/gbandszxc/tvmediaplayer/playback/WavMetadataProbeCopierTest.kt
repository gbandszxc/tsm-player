package com.github.gbandszxc.tvmediaplayer.playback

import org.jaudiotagger.audio.AudioFileIO
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Base64
import java.util.Collections
import java.util.zip.CRC32

class WavMetadataProbeCopierTest {

    private val png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    )

    @Test
    fun `strict artwork probe never copies an invalid full audio payload`() {
        val input = ByteArray(1024 * 1024) { 1 }
        val output = ByteArrayOutputStream()

        assertTrue(
            SmbAudioMetadataProbe.copyFastMetadataProbe(
                ByteArrayInputStream(input),
                output,
                suffix = "mp3",
                strictPartial = true,
            )
        )
        assertEquals(10, output.size())
    }

    @Test
    fun `normalizes wave parser suffix while leaving other suffixes unchanged`() {
        mapOf(
            "wave" to "wav",
            "wav" to "wav",
            "flac" to "flac",
        ).forEach { (sourceSuffix, expectedParserSuffix) ->
            assertEquals(
                sourceSuffix,
                expectedParserSuffix,
                SmbAudioMetadataProbe.metadataParserSuffix(sourceSuffix),
            )
        }
    }

    @Test
    fun `SMB fast metadata probe routes wav and wave through WAV copier`() {
        val source = wav(id3BeforeData = false)
        listOf("wav", "wave").forEach { suffix ->
            val expected = ByteArrayOutputStream()
            assertTrue(WavMetadataProbeCopier.copy(ByteArrayInputStream(source), expected))
            val actual = ByteArrayOutputStream()

            assertTrue(
                "$suffix must use the fast WAV copier",
                SmbAudioMetadataProbe.copyFastMetadataProbe(
                    ByteArrayInputStream(source),
                    actual,
                    suffix,
                ),
            )
            assertArrayEquals("$suffix dispatch output", expected.toByteArray(), actual.toByteArray())
            assertTrue("$suffix probe must be bounded", actual.size() < source.size / 2)

            val parserSuffix = SmbAudioMetadataProbe.metadataParserSuffix(suffix)
            val temp = File.createTempFile("wav-routing-probe", ".$parserSuffix")
            try {
                temp.writeBytes(actual.toByteArray())
                assertArrayEquals(
                    "$suffix dispatch must retain APIC",
                    png,
                    AudioFileIO.read(temp).tag.firstArtwork.binaryData,
                )
            } finally {
                temp.delete()
            }
        }
    }

    @Test
    fun `retains artwork when ID3 precedes data`() {
        assertValidProbe(wav(id3BeforeData = true))
    }

    @Test
    fun `retains artwork when ID3 follows data`() {
        assertValidProbe(wav(id3BeforeData = false))
    }

    @Test
    fun `handles odd sized unknown chunk padding before ID3`() {
        assertValidProbe(wav(id3BeforeData = true, includeOddJunk = true))
    }

    @Test
    fun `invalid RIFF signature is copied unchanged`() {
        val source = wav(id3BeforeData = true).also { it[0] = 'X'.code.toByte() }
        val output = ByteArrayOutputStream()

        assertFalse(WavMetadataProbeCopier.copy(ByteArrayInputStream(source), output))
        assertArrayEquals(source, output.toByteArray())
    }

    @Test
    fun `RIFF size smaller than WAVE identifier is copied unchanged`() {
        val source = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            writeLe32(3)
            write("WAVEtrailing bytes".toByteArray(Charsets.US_ASCII))
        }.toByteArray()
        val output = ByteArrayOutputStream()

        assertFalse(WavMetadataProbeCopier.copy(ByteArrayInputStream(source), output))
        assertArrayEquals(source, output.toByteArray())
    }

    @Test
    fun `artwork fixture is a structurally valid PNG`() {
        assertArrayEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
            png.copyOfRange(0, 8)
        )
        var offset = 8
        val chunkTypes = mutableListOf<String>()
        while (offset < png.size) {
            val length = readBe32(png, offset)
            val typeOffset = offset + 4
            val dataEnd = typeOffset + 4 + length
            val expectedCrc = readBe32(png, dataEnd).toLong() and 0xffffffffL
            val crc = CRC32().apply { update(png, typeOffset, 4 + length) }
            assertTrue("PNG chunk CRC must match", crc.value == expectedCrc)
            chunkTypes += String(png, typeOffset, 4, Charsets.US_ASCII)
            offset = dataEnd + 4
        }
        assertTrue("PNG chunks must consume the fixture", offset == png.size)
        assertTrue(chunkTypes.first() == "IHDR")
        assertTrue("IDAT" in chunkTypes)
        assertTrue(chunkTypes.last() == "IEND")
    }

    @Test
    fun `bulk read zero progress falls back to single byte read`() {
        val source = wav(id3BeforeData = true)
        val input = object : InputStream() {
            private val delegate = ByteArrayInputStream(source)
            private var singleByteReadSinceZero = true

            override fun read(): Int {
                singleByteReadSinceZero = true
                return delegate.read()
            }

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                check(singleByteReadSinceZero) { "bulk read retried without making progress" }
                singleByteReadSinceZero = false
                return 0
            }

            override fun skip(byteCount: Long): Long = delegate.skip(byteCount)
        }

        assertTrue(WavMetadataProbeCopier.copy(input, ByteArrayOutputStream()))
    }

    @Test
    fun `repeated retained chunks stay within cumulative output budget`() {
        val payload = ByteArray(4 * 1024 * 1024)
        val chunkCount = 10
        val chunkHeader = ByteArrayOutputStream().apply {
            write("LIST".toByteArray(Charsets.US_ASCII))
            writeLe32(payload.size)
        }.toByteArray()
        val riffBodySize = 4 + chunkCount * (chunkHeader.size + payload.size)
        val streams = mutableListOf<InputStream>()
        streams += ByteArrayInputStream(ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            writeLe32(riffBodySize)
            write("WAVE".toByteArray(Charsets.US_ASCII))
        }.toByteArray())
        repeat(chunkCount) {
            streams += ByteArrayInputStream(chunkHeader)
            streams += ByteArrayInputStream(payload)
        }
        val input = SequenceInputStream(Collections.enumeration(streams))
        val output = ByteArrayOutputStream()

        assertTrue(WavMetadataProbeCopier.copy(input, output))
        assertTrue(
            "output must honor cumulative retained budget",
            output.size() <= WavMetadataProbeCopier.MAX_RETAINED_BYTES
        )
        assertTrue("not every recognized payload may be retained", output.size() < riffBodySize + 8)
    }

    @Test
    fun `large retained storage uses bounded segments`() {
        val storage = SegmentedOutputStream()
        val block = ByteArray(SegmentedOutputStream.MAX_SEGMENT_BYTES)
        repeat((32 * 1024 * 1024) / block.size) { storage.write(block) }

        assertTrue(storage.maxAllocatedSegmentSize <= SegmentedOutputStream.MAX_SEGMENT_BYTES)
        assertTrue(storage.size() == 32 * 1024 * 1024)
    }

    @Test
    fun `segmented storage backpatches length across segment boundary`() {
        val storage = SegmentedOutputStream()
        storage.write(ByteArray(SegmentedOutputStream.MAX_SEGMENT_BYTES - 2))
        val offset = storage.size()
        storage.write(ByteArray(4))

        storage.patchUInt32Le(offset, 0x78563412)

        assertTrue(storage.byteAt(offset) == 0x12)
        assertTrue(storage.byteAt(offset + 1) == 0x34)
        assertTrue(storage.byteAt(offset + 2) == 0x56)
        assertTrue(storage.byteAt(offset + 3) == 0x78)
    }

    @Test
    fun `zero progress skip falls back to single byte reads`() {
        val source = wav(id3BeforeData = true)
        var singleByteReads = 0
        val input = object : InputStream() {
            private val delegate = ByteArrayInputStream(source)
            override fun read(): Int = delegate.read().also { if (it >= 0) singleByteReads++ }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                delegate.read(bytes, offset, length)
            override fun skip(byteCount: Long): Long = 0
        }

        assertTrue(WavMetadataProbeCopier.copy(input, ByteArrayOutputStream()))
        assertTrue("skip fallback must consume bytes", singleByteReads > 0)
    }

    @Test
    fun `truncated valid RIFF returns true with self consistent output size`() {
        val full = wav(id3BeforeData = true)
        val truncated = full.copyOf(full.size - 100)
        val output = ByteArrayOutputStream()

        assertTrue(WavMetadataProbeCopier.copy(ByteArrayInputStream(truncated), output))
        val probe = output.toByteArray()
        assertTrue(readLe32(probe, 4) == probe.size - 8)
    }

    private fun assertValidProbe(source: ByteArray) {
        val output = ByteArrayOutputStream()
        assertTrue(WavMetadataProbeCopier.copy(ByteArrayInputStream(source), output))
        val probe = output.toByteArray()
        assertTrue("probe must be less than half the source", probe.size < source.size / 2)

        val temp = File.createTempFile("wav-metadata-probe", ".wav")
        try {
            temp.writeBytes(probe)
            assertArrayEquals(png, AudioFileIO.read(temp).tag.firstArtwork.binaryData)
        } finally {
            temp.delete()
        }
    }

    private fun wav(id3BeforeData: Boolean, includeOddJunk: Boolean = false): ByteArray {
        val fmt = ByteArrayOutputStream().apply {
            writeLe16(1)
            writeLe16(2)
            writeLe32(44_100)
            writeLe32(176_400)
            writeLe16(4)
            writeLe16(16)
        }.toByteArray()
        val chunks = mutableListOf<Pair<String, ByteArray>>()
        chunks += "fmt " to fmt
        if (includeOddJunk) chunks += "JUNK" to byteArrayOf(1, 2, 3)
        val id3 = id3Tag()
        val data = ByteArray(256 * 1024) { (it and 0xff).toByte() }
        if (id3BeforeData) {
            chunks += "id3 " to id3
            chunks += "data" to data
        } else {
            chunks += "data" to data
            chunks += "id3 " to id3
        }
        val body = ByteArrayOutputStream()
        chunks.forEach { (id, bytes) ->
            body.write(id.toByteArray(Charsets.US_ASCII))
            body.writeLe32(bytes.size)
            body.write(bytes)
            if (bytes.size and 1 != 0) body.write(0)
        }
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            writeLe32(body.size() + 4)
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write(body.toByteArray())
        }.toByteArray()
    }

    private fun id3Tag(): ByteArray {
        val apicPayload = ByteArrayOutputStream().apply {
            write(0)
            write("image/png".toByteArray(Charsets.ISO_8859_1))
            write(0)
            write(3)
            write(0)
            write(png)
        }.toByteArray()
        val frame = ByteArrayOutputStream().apply {
            write("APIC".toByteArray(Charsets.US_ASCII))
            writeBe32(apicPayload.size)
            write(byteArrayOf(0, 0))
            write(apicPayload)
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("ID3".toByteArray(Charsets.US_ASCII))
            write(byteArrayOf(3, 0, 0))
            writeSynchsafe(frame.size)
            write(frame)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLe16(value: Int) {
        write(value and 0xff)
        write(value ushr 8 and 0xff)
    }

    private fun ByteArrayOutputStream.writeLe32(value: Int) {
        repeat(4) { write(value ushr (it * 8) and 0xff) }
    }

    private fun ByteArrayOutputStream.writeBe32(value: Int) {
        repeat(4) { write(value ushr ((3 - it) * 8) and 0xff) }
    }

    private fun ByteArrayOutputStream.writeSynchsafe(value: Int) {
        repeat(4) { write(value ushr ((3 - it) * 7) and 0x7f) }
    }

    private fun readBe32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun readLe32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
