package com.github.gbandszxc.tvmediaplayer.playback

import android.net.Uri
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.CompletableDeferred
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

data class SmbAudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val lyrics: String?,
    val artworkData: ByteArray?,
)

object SmbAudioMetadataProbe {
    private const val MP4_TAG_PROBE_BYTES = 8L * 1024 * 1024
    private const val OGG_TAG_PROBE_BYTES = 2L * 1024 * 1024
    private const val FLAC_MAX_METADATA_BYTES = 32L * 1024 * 1024
    private const val FLAC_POST_METADATA_AUDIO_BYTES = 16L * 1024

    private val memoryCache = ConcurrentHashMap<String, SmbAudioMetadata>()
    private val missingKeys = ConcurrentHashMap.newKeySet<String>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<SmbAudioMetadata?>>()
    private val missingArtworkKeys = ConcurrentHashMap.newKeySet<String>()
    private val artworkInFlight = ConcurrentHashMap<String, CompletableDeferred<ByteArray?>>()

    suspend fun probe(config: SmbConfig, mediaUri: String): SmbAudioMetadata? {
        val isSmb = mediaUri.startsWith("smb://", ignoreCase = true)
        val isLocal = Uri.parse(mediaUri).scheme.equals("file", ignoreCase = true)
        if (!isSmb && !isLocal) return null
        val key = if (isSmb) buildKey(config, mediaUri) else mediaUri.trim()
        memoryCache[key]?.let { return it }
        if (key in missingKeys) return null

        val waiter = CompletableDeferred<SmbAudioMetadata?>()
        val existing = inFlight.putIfAbsent(key, waiter)
        if (existing != null) return existing.await()

        return try {
            val loaded = if (isSmb) load(config, mediaUri) else loadLocal(mediaUri)
            if (loaded != null) memoryCache[key] = loaded else missingKeys += key
            waiter.complete(loaded)
            loaded
        } catch (t: Throwable) {
            waiter.completeExceptionally(t)
            throw t
        } finally {
            inFlight.remove(key, waiter)
        }
    }

    /** 供宫格批量加载使用：只读取格式标签区域，绝不回退为整首 SMB 音频复制。 */
    suspend fun probeArtwork(config: SmbConfig, mediaUri: String): ByteArray? {
        val isSmb = mediaUri.startsWith("smb://", ignoreCase = true)
        val isLocal = Uri.parse(mediaUri).scheme.equals("file", ignoreCase = true)
        if (!isSmb && !isLocal) return null
        val key = (if (isSmb) buildKey(config, mediaUri) else mediaUri.trim()) + "|artwork"
        if (key in missingArtworkKeys) return null

        val waiter = CompletableDeferred<ByteArray?>()
        val existing = artworkInFlight.putIfAbsent(key, waiter)
        if (existing != null) return existing.await()

        return try {
            val artwork = if (isSmb) {
                load(config, mediaUri, allowFullFallback = false)?.artworkData
            } else {
                loadLocal(mediaUri)?.artworkData
            }
            if (artwork == null) missingArtworkKeys += key
            waiter.complete(artwork)
            artwork
        } catch (t: Throwable) {
            waiter.completeExceptionally(t)
            throw t
        } finally {
            artworkInFlight.remove(key, waiter)
        }
    }

    fun clearMemory() {
        memoryCache.clear()
        missingKeys.clear()
        missingArtworkKeys.clear()
    }

    private fun buildKey(config: SmbConfig, mediaUri: String): String {
        return listOf(
            mediaUri.trim(),
            config.host.trim(),
            config.share.trim(),
            config.username.trim(),
            config.guest.toString(),
            config.smb1Enabled.toString(),
        ).joinToString("|")
    }

    private fun load(
        config: SmbConfig,
        mediaUri: String,
        allowFullFallback: Boolean = true,
    ): SmbAudioMetadata? {
        val smbFile = SmbFile(mediaUri, SmbContextFactory.build(config))
        val ext = mediaUri.substringAfterLast('.', "").lowercase()
        val suffix = if (ext.isBlank() || ext.length > 8) "tmp" else ext
        val temp = File.createTempFile("probe-", ".${metadataParserSuffix(suffix)}")
        return try {
            val fastUsed = copySmbForMetadataProbe(
                smbFile,
                temp,
                suffix,
                fastPath = true,
                strictPartial = !allowFullFallback,
            )
            var metadata = extractMetadata(temp)
            if (metadata == null && fastUsed && allowFullFallback) {
                copySmbForMetadataProbe(smbFile, temp, suffix, fastPath = false)
                metadata = extractMetadata(temp)
            }
            metadata
        } finally {
            temp.delete()
        }
    }

    private fun loadLocal(mediaUri: String): SmbAudioMetadata? {
        val file = Uri.parse(mediaUri).path?.let(::File) ?: return null
        return file.takeIf(File::isFile)?.let(::extractMetadata)
    }

    internal fun metadataParserSuffix(sourceSuffix: String): String {
        return if (sourceSuffix == "wave") "wav" else sourceSuffix
    }

    private fun extractMetadata(temp: File): SmbAudioMetadata? {
        val tag = runCatching { AudioFileIO.read(temp).tag }.getOrNull() ?: return null
        val title = tag.getFirst(FieldKey.TITLE).trim().ifBlank { null }
        val artist = tag.getFirst(FieldKey.ARTIST).trim().ifBlank { null }
        val album = tag.getFirst(FieldKey.ALBUM).trim().ifBlank { null }
        val lyrics = tag.getFirst(FieldKey.LYRICS).trim().ifBlank { null }
        val artwork = runCatching { tag.firstArtwork?.binaryData }.getOrNull()
        if (title == null && artist == null && album == null && lyrics == null && artwork == null) {
            return null
        }
        return SmbAudioMetadata(
            title = title,
            artist = artist,
            album = album,
            lyrics = lyrics,
            artworkData = artwork,
        )
    }

    private fun copySmbForMetadataProbe(
        smbFile: SmbFile,
        temp: File,
        suffix: String,
        fastPath: Boolean,
        strictPartial: Boolean = false,
    ): Boolean {
        SmbFileInputStream(smbFile).use { input ->
            FileOutputStream(temp, false).use { output ->
                if (!fastPath) {
                    input.copyTo(output)
                    return false
                }
                return copyFastMetadataProbe(input, output, suffix, strictPartial)
            }
        }
    }

    internal fun copyFastMetadataProbe(
        input: InputStream,
        output: OutputStream,
        suffix: String,
        strictPartial: Boolean = false,
    ): Boolean {
        return when (suffix) {
            "mp3" -> {
                copyId3TagRegion(input, output, strictPartial)
                true
            }

            "flac" -> copyFlacMetadataRegion(input, output, strictPartial)

            "wav", "wave" -> WavMetadataProbeCopier.copy(input, output, strictPartial)

            "m4a", "mp4", "m4b", "aac", "alac" -> {
                copyLimited(input, output, MP4_TAG_PROBE_BYTES)
                true
            }

            "ogg", "opus" -> {
                copyLimited(input, output, OGG_TAG_PROBE_BYTES)
                true
            }

            else -> {
                if (strictPartial) true else {
                    input.copyTo(output)
                    false
                }
            }
        }
    }

    private fun copyFlacMetadataRegion(
        input: InputStream,
        output: OutputStream,
        strictPartial: Boolean,
    ): Boolean {
        val signature = ByteArray(4)
        val signatureRead = input.readFully(signature)
        output.write(signature, 0, signatureRead)
        if (signatureRead < 4) {
            if (!strictPartial) input.copyTo(output)
            return strictPartial
        }
        if (
            signature[0] != 'f'.code.toByte() ||
            signature[1] != 'L'.code.toByte() ||
            signature[2] != 'a'.code.toByte() ||
            signature[3] != 'C'.code.toByte()
        ) {
            if (!strictPartial) input.copyTo(output)
            return strictPartial
        }

        var copiedMetadataBytes = 0L
        var isLastBlock = false
        val blockHeader = ByteArray(4)
        while (!isLastBlock) {
            val headerRead = input.readFully(blockHeader)
            if (headerRead < 4) return true
            output.write(blockHeader)

            isLastBlock = (blockHeader[0].toInt() and 0x80) != 0
            val blockLength =
                ((blockHeader[1].toInt() and 0xFF) shl 16) or
                    ((blockHeader[2].toInt() and 0xFF) shl 8) or
                    (blockHeader[3].toInt() and 0xFF)

            if (blockLength > 0) {
                copyExactly(input, output, blockLength.toLong())
                copiedMetadataBytes += blockLength.toLong()
                if (copiedMetadataBytes >= FLAC_MAX_METADATA_BYTES) break
            }
        }

        copyLimited(input, output, FLAC_POST_METADATA_AUDIO_BYTES)
        return true
    }

    private fun copyExactly(input: InputStream, output: OutputStream, bytes: Long) {
        var remaining = bytes
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (n <= 0) break
            output.write(buffer, 0, n)
            remaining -= n
        }
    }

    private fun copyLimited(input: InputStream, output: OutputStream, bytes: Long) {
        copyExactly(input, output, bytes)
    }

    private fun InputStream.readFully(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = read(buffer, total, buffer.size - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    private fun copyId3TagRegion(input: InputStream, output: OutputStream, strictPartial: Boolean) {
        val header = ByteArray(10)
        var totalRead = 0
        while (totalRead < 10) {
            val n = input.read(header, totalRead, 10 - totalRead)
            if (n < 0) break
            totalRead += n
        }
        output.write(header, 0, totalRead)
        if (totalRead < 10) {
            if (!strictPartial) input.copyTo(output)
            return
        }
        if (header[0] != 0x49.toByte() || header[1] != 0x44.toByte() || header[2] != 0x33.toByte()) {
            if (!strictPartial) input.copyTo(output)
            return
        }
        val tagContentSize =
            ((header[6].toInt() and 0x7F) shl 21) or
                ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or
                (header[9].toInt() and 0x7F)
        var remaining = tagContentSize.toLong()
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n < 0) break
            output.write(buf, 0, n)
            remaining -= n
        }
        val audioBuf = ByteArray(65536)
        val audioRead = input.read(audioBuf)
        if (audioRead > 0) output.write(audioBuf, 0, audioRead)
    }
}
