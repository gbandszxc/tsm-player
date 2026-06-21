package com.github.gbandszxc.tvmediaplayer.playback

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

internal object WavMetadataProbeCopier {

    const val DATA_PROBE_BYTES = 64 * 1024
    const val MAX_METADATA_CHUNK_BYTES = 32 * 1024 * 1024

    fun copy(input: InputStream, output: OutputStream): Boolean {
        val header = ByteArray(RIFF_HEADER_BYTES)
        val headerBytes = readFully(input, header, 0, header.size)
        if (headerBytes < header.size ||
            !header.matchesAscii(0, "RIFF") ||
            !header.matchesAscii(8, "WAVE") ||
            readUInt32Le(header, 4) < WAVE_ID_BYTES
        ) {
            output.write(header, 0, headerBytes)
            input.copyTo(output)
            return false
        }

        var remaining = readUInt32Le(header, 4) - WAVE_ID_BYTES
        val retained = ByteArrayOutputStream()
        val chunkHeader = ByteArray(CHUNK_HEADER_BYTES)

        while (remaining >= CHUNK_HEADER_BYTES.toLong()) {
            val chunkHeaderBytes = readFully(input, chunkHeader, 0, chunkHeader.size)
            if (chunkHeaderBytes < chunkHeader.size) break
            remaining -= CHUNK_HEADER_BYTES.toLong()

            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val declaredSize = readUInt32Le(chunkHeader, 4)
            val availableSize = minOf(declaredSize, remaining)
            val consumed = when {
                chunkId == "data" -> retainDataChunk(input, retained, availableSize)
                isChunkRetained(chunkId) && declaredSize <= MAX_METADATA_CHUNK_BYTES ->
                    retainMetadataChunk(input, retained, chunkId, availableSize)
                else -> skipFully(input, availableSize)
            }
            remaining -= consumed
            if (consumed < availableSize) break

            if (declaredSize > availableSize) break
            if (declaredSize and 1L != 0L && remaining > 0) {
                if (skipFully(input, 1) != 1L) break
                remaining--
            }
        }

        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeUInt32Le(output, WAVE_ID_BYTES + retained.size().toLong())
        output.write("WAVE".toByteArray(Charsets.US_ASCII))
        retained.writeTo(output)
        return true
    }

    private fun retainDataChunk(
        input: InputStream,
        output: OutputStream,
        availableSize: Long
    ): Long {
        val data = ByteArray(minOf(availableSize, DATA_PROBE_BYTES.toLong()).toInt())
        val copied = readFully(input, data, 0, data.size)
        writeChunk(output, "data", data, copied)
        if (copied < data.size) return copied.toLong()
        return copied + skipFully(input, availableSize - copied)
    }

    private fun retainMetadataChunk(
        input: InputStream,
        output: OutputStream,
        chunkId: String,
        availableSize: Long
    ): Long {
        val data = ByteArray(availableSize.toInt())
        val copied = copyExactly(input, data, availableSize)
        writeChunk(output, chunkId, data, copied.toInt())
        return copied
    }

    private fun writeChunk(output: OutputStream, chunkId: String, data: ByteArray, size: Int) {
        output.write(chunkId.toByteArray(Charsets.US_ASCII))
        writeUInt32Le(output, size.toLong())
        output.write(data, 0, size)
        if (size and 1 != 0) output.write(0)
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = input.read(buffer, offset + total, length - total)
            if (read < 0) break
            if (read == 0) continue
            total += read
        }
        return total
    }

    private fun readUInt32Le(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)

    private fun writeUInt32Le(output: OutputStream, value: Long) {
        repeat(4) { output.write((value ushr (it * 8) and 0xff).toInt()) }
    }

    private fun copyExactly(input: InputStream, output: ByteArray, byteCount: Long): Long =
        readFully(input, output, 0, byteCount.toInt()).toLong()

    private fun skipFully(input: InputStream, byteCount: Long): Long {
        var skipped = 0L
        while (skipped < byteCount) {
            val count = input.skip(byteCount - skipped)
            if (count > 0) {
                skipped += count
            } else if (input.read() >= 0) {
                skipped++
            } else {
                break
            }
        }
        return skipped
    }

    private fun isChunkRetained(chunkId: String): Boolean =
        chunkId == "fmt " || chunkId == "LIST" || chunkId == "id3 " || chunkId == "ID3 "

    private fun ByteArray.matchesAscii(offset: Int, value: String): Boolean =
        value.indices.all { this[offset + it] == value[it].code.toByte() }

    private const val RIFF_HEADER_BYTES = 12
    private const val CHUNK_HEADER_BYTES = 8
    private const val WAVE_ID_BYTES = 4L
}
