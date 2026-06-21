package com.github.gbandszxc.tvmediaplayer.playback

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

internal object WavMetadataProbeCopier {

    const val DATA_PROBE_BYTES = 64 * 1024
    const val MAX_METADATA_CHUNK_BYTES = 32 * 1024 * 1024
    const val MAX_RETAINED_BYTES = 36 * 1024 * 1024

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
        val retained = RetainedOutputStream()
        val chunkHeader = ByteArray(CHUNK_HEADER_BYTES)

        while (remaining >= CHUNK_HEADER_BYTES.toLong()) {
            val chunkHeaderBytes = readFully(input, chunkHeader, 0, chunkHeader.size)
            if (chunkHeaderBytes < chunkHeader.size) break
            remaining -= CHUNK_HEADER_BYTES.toLong()

            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val declaredSize = readUInt32Le(chunkHeader, 4)
            val availableSize = minOf(declaredSize, remaining)
            val consumed = when {
                chunkId == "data" && canRetain(retained, minOf(availableSize, DATA_PROBE_BYTES.toLong())) ->
                    retainDataChunk(input, retained, availableSize)
                isChunkRetained(chunkId) &&
                    declaredSize <= MAX_METADATA_CHUNK_BYTES &&
                    canRetain(retained, availableSize) ->
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
        output: RetainedOutputStream,
        availableSize: Long
    ): Long {
        val retainedSize = minOf(availableSize, DATA_PROBE_BYTES.toLong())
        val sizeOffset = output.writeChunkHeader("data")
        val copied = copyExactly(input, output, retainedSize)
        output.finishChunk(sizeOffset, copied)
        if (copied < retainedSize) return copied
        return copied + skipFully(input, availableSize - copied)
    }

    private fun retainMetadataChunk(
        input: InputStream,
        output: RetainedOutputStream,
        chunkId: String,
        availableSize: Long
    ): Long {
        val sizeOffset = output.writeChunkHeader(chunkId)
        val copied = copyExactly(input, output, availableSize)
        output.finishChunk(sizeOffset, copied)
        return copied
    }

    private fun canRetain(output: RetainedOutputStream, payloadSize: Long): Boolean {
        val paddedSize = payloadSize + (payloadSize and 1L)
        return RIFF_HEADER_BYTES + output.size().toLong() + CHUNK_HEADER_BYTES + paddedSize <=
            MAX_RETAINED_BYTES
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = input.read(buffer, offset + total, length - total)
            if (read < 0) break
            if (read == 0) {
                val value = input.read()
                if (value < 0) break
                buffer[offset + total] = value.toByte()
                total++
            } else {
                total += read
            }
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

    private fun copyExactly(input: InputStream, output: OutputStream, byteCount: Long): Long {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var copied = 0L
        while (copied < byteCount) {
            val requested = minOf(buffer.size.toLong(), byteCount - copied).toInt()
            val read = readFully(input, buffer, 0, requested)
            if (read == 0) break
            output.write(buffer, 0, read)
            copied += read
            if (read < requested) break
        }
        return copied
    }

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

    private class RetainedOutputStream : ByteArrayOutputStream() {
        fun writeChunkHeader(chunkId: String): Int {
            write(chunkId.toByteArray(Charsets.US_ASCII))
            val sizeOffset = count
            repeat(4) { write(0) }
            return sizeOffset
        }

        fun finishChunk(sizeOffset: Int, payloadSize: Long) {
            repeat(4) { buf[sizeOffset + it] = (payloadSize ushr (it * 8) and 0xff).toByte() }
            if (payloadSize and 1L != 0L) write(0)
        }
    }

    private const val RIFF_HEADER_BYTES = 12
    private const val CHUNK_HEADER_BYTES = 8
    private const val WAVE_ID_BYTES = 4L
    private const val COPY_BUFFER_BYTES = 8 * 1024
}
