package com.github.gbandszxc.tvmediaplayer.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlin.math.min

class SmbDataSource(
    private val configProvider: () -> SmbConfig
) : BaseDataSource(false) {

    companion object {
        // 每次从 SMB 预读的块大小：1MB
        private const val PREFETCH_CHUNK_SIZE = 1 * 1024 * 1024
        // 队列最多存 4 块（4MB），防止内存无限增长，也提供背压
        private const val MAX_QUEUED_CHUNKS = 4
        // 标记流结束的哨兵（空数组）
        private val EOF_SENTINEL = ByteArray(0)
    }

    private var dataSpec: DataSpec? = null
    private var randomAccessFile: SmbRandomAccessFile? = null
    private var bytesRemaining: Long = 0
    private var opened = false

    // --- 预读状态 ---
    private val chunkQueue = LinkedBlockingQueue<ByteArray>(MAX_QUEUED_CHUNKS)
    @Volatile private var prefetchCancelled = false
    /**
     * 预读世代标记：close() 与 startPrefetch() 递增。
     * close() 的 join(500) 超时后旧线程可能仍存活；DataSource 实例被 ExoPlayer
     * 复用时，若没有世代隔离，旧线程会把上一首的 EOF/数据块投进新歌的队列，
     * 造成新曲目被提前截断。旧线程发现世代变化后立即停止投递。
     */
    @Volatile private var prefetchGeneration = 0
    private var prefetchThread: Thread? = null

    // ExoPlayer 当前正在消费的块及其读取偏移
    private var pendingChunk: ByteArray? = null
    private var pendingOffset = 0

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        if (!uri.scheme.equals("smb", ignoreCase = true)) {
            throw PlaybackException(
                "Unsupported URI scheme: ${uri.scheme}",
                null,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED
            )
        }
        transferInitializing(dataSpec)
        this.dataSpec = dataSpec

        try {
            val context = SmbContextFactory.build(configProvider())
            val file = SmbFile(uri.toString(), context)
            val raf = SmbRandomAccessFile(file, "r")
            randomAccessFile = raf

            if (dataSpec.position > 0) {
                raf.seek(dataSpec.position)
            }

            val fileLength = raf.length()
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                fileLength - dataSpec.position
            }

            if (bytesRemaining < 0) {
                throw IOException("Invalid read range: position=${dataSpec.position}, length=$fileLength")
            }

            startPrefetch(raf, bytesRemaining)

            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: Exception) {
            throw SmbDataSourceOpenFailurePolicy.wrapOpenFailure(uri.toString(), e)
        }
    }

    /**
     * 启动后台预读线程。
     * 每次从 RAF 读取 [PREFETCH_CHUNK_SIZE]（1MB），放入 [chunkQueue]。
     * 队列满时自动阻塞（背压），不会无限消耗内存。
     */
    private fun startPrefetch(raf: SmbRandomAccessFile, totalBytes: Long) {
        chunkQueue.clear()
        pendingChunk = null
        pendingOffset = 0
        prefetchCancelled = false
        val generation = ++prefetchGeneration

        prefetchThread = Thread({
            var remaining = totalBytes
            val buf = ByteArray(PREFETCH_CHUNK_SIZE)
            try {
                while (!prefetchCancelled && prefetchGeneration == generation && remaining > 0) {
                    val toRead = min(PREFETCH_CHUNK_SIZE.toLong(), remaining).toInt()
                    var filled = 0
                    // 循环确保读满 toRead 字节（RAF 可能一次返回不足）
                    while (filled < toRead && !prefetchCancelled) {
                        val n = raf.read(buf, filled, toRead - filled)
                        if (n == -1) break
                        filled += n
                    }
                    if (filled == 0) break
                    val chunk = buf.copyOf(filled)
                    // 队列满时阻塞（背压），每 100ms 检查一次取消/世代标志
                    while (!prefetchCancelled && prefetchGeneration == generation) {
                        if (chunkQueue.offer(chunk, 100, TimeUnit.MILLISECONDS)) break
                    }
                    remaining -= filled
                }
            } catch (_: InterruptedException) {
                // 正常取消
            } catch (_: Exception) {
                // SMB 读取异常：放入 EOF 让消费侧感知
            }
            // 投递 EOF 哨兵，通知消费侧流已结束（仅当当前仍是本世代）
            if (!prefetchCancelled && prefetchGeneration == generation) {
                runCatching { chunkQueue.offer(EOF_SENTINEL, 5, TimeUnit.SECONDS) }
            }
        }, "smb-prefetch").apply {
            isDaemon = true
            start()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        // 当前块已耗尽，从队列取下一块（此处可能短暂阻塞等待预读）
        if (pendingChunk == null || pendingOffset >= pendingChunk!!.size) {
            val next = try {
                chunkQueue.poll(10, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                return C.RESULT_END_OF_INPUT
            } ?: return C.RESULT_END_OF_INPUT  // 超时：视为流结束

            if (next === EOF_SENTINEL || next.isEmpty()) return C.RESULT_END_OF_INPUT
            pendingChunk = next
            pendingOffset = 0
        }

        val chunk = pendingChunk!!
        val available = chunk.size - pendingOffset
        val toCopy = min(min(available.toLong(), bytesRemaining), length.toLong()).toInt()
        System.arraycopy(chunk, pendingOffset, buffer, offset, toCopy)
        pendingOffset += toCopy
        bytesRemaining -= toCopy
        bytesTransferred(toCopy)
        return toCopy
    }

    override fun getUri() = dataSpec?.uri

    override fun close() {
        // 先取消并等预读线程退出，再关 RAF，避免 RAF 关闭时线程还在读；
        // join 超时残留的旧线程由世代标记兜底，不会再投递数据
        prefetchCancelled = true
        prefetchGeneration++
        prefetchThread?.interrupt()
        prefetchThread?.join(500)
        prefetchThread = null
        chunkQueue.clear()
        pendingChunk = null
        pendingOffset = 0

        try {
            randomAccessFile?.close()
        } finally {
            randomAccessFile = null
            dataSpec = null
            bytesRemaining = 0
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(
        private val configProvider: () -> SmbConfig
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(configProvider)
    }
}
