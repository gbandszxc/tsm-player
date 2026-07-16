package com.github.gbandszxc.tvmediaplayer.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 封面缓存（内存 + 磁盘）。
 *
 * 磁盘布局：
 *   cacheDir/artwork/
 *     index.json          — key哈希 → 内容 MD5 的映射
 *     content/<md5>.jpg   — 按内容寻址的实际图片文件
 *
 * 相同内容的封面（如同一专辑所有曲目）只在磁盘存一份，通过索引共享。
 */
object PlaybackArtworkCache {

    private val memory = object : LruCache<String, Bitmap>(24) {}
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val diskLock = Any()

    // key哈希 → 内容 MD5，多线程安全
    private val keyToMd5 = ConcurrentHashMap<String, String>()

    @Volatile
    private var indexLoaded = false

    // ---- 公开接口 ----

    /** 查内存缓存 */
    fun get(key: String): Bitmap? = memory.get(key)

    /** 仅写内存缓存 */
    fun put(key: String, bitmap: Bitmap) {
        memory.put(key, fitToCache(bitmap))
    }

    /** 从封面原始字节采样解码，避免高分辨率图片触发 Canvas 超大 Bitmap 崩溃。 */
    fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_BITMAP_EDGE)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let(::fitToCache)
    }

    fun decodeSampled(stream: InputStream): Bitmap? = decodeSampled(stream.readBytes())

    fun decodeSampled(file: File): Bitmap? = decodeSampledFile(file)

    /**
     * 异步将 bitmap 以 JPEG 写入磁盘。
     * 相同内容只写一份文件，通过索引共享（省空间、省 IO）。
     */
    fun saveAsync(context: Context, key: String, bitmap: Bitmap) {
        saveAsync(context, listOf(key), bitmap)
    }

    /** 同一封面对应多首歌时只压缩和写入一次，再为所有歌曲建立索引。 */
    fun saveAsync(context: Context, keys: Collection<String>, bitmap: Bitmap) {
        if (keys.isEmpty()) return
        ioScope.launch {
            runCatching {
                val cachedBitmap = fitToCache(bitmap)
                val bytes = ByteArrayOutputStream().use { out ->
                    cachedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.toByteArray()
                }
                val md5 = md5Hex(bytes)
                synchronized(diskLock) {
                    ensureIndexLoaded(context)
                    val content = contentFile(context, md5)
                    if (!content.exists()) content.writeBytes(bytes)
                    keys.forEach { key -> keyToMd5[keyHash(key)] = md5 }
                    saveIndex(context)
                }
            }
        }
    }

    /**
     * 从磁盘读取缓存（同步，调用方负责在 IO 线程调用）。
     * 命中时不会自动放入内存，调用方应自行调用 put()。
     */
    fun loadFromDisk(context: Context, key: String): Bitmap? = synchronized(diskLock) {
        runCatching {
            ensureIndexLoaded(context)
            val md5 = keyToMd5[keyHash(key)] ?: return@runCatching null
            val file = contentFile(context, md5)
            if (!file.exists()) return@runCatching null
            decodeSampledFile(file)
        }.getOrNull()
    }

    /** 清空内存和磁盘缓存 */
    fun clearDisk(context: Context) {
        memory.evictAll()
        synchronized(diskLock) {
            keyToMd5.clear()
            indexLoaded = false
            artworkCacheDir(context).deleteRecursively()
        }
    }

    /** 返回磁盘缓存占用字节数 */
    fun diskCacheSize(context: Context): Long =
        artworkCacheDir(context).walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    // ---- 内部工具 ----

    private fun artworkCacheDir(context: Context): File =
        File(context.cacheDir, "artwork").also { it.mkdirs() }

    private fun contentDir(context: Context): File =
        File(artworkCacheDir(context), "content").also { it.mkdirs() }

    private fun contentFile(context: Context, md5: String): File =
        File(contentDir(context), "$md5.jpg")

    private fun indexFile(context: Context): File =
        File(artworkCacheDir(context), "index.json")

    /** 将任意字符串 key 映射为稳定的短标识符，用作索引 key */
    private fun keyHash(key: String): String {
        val hash = key.hashCode()
        return if (hash >= 0) "p$hash" else "n${-hash}"
    }

    private fun md5Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun decodeSampledFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_BITMAP_EDGE)
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)?.let(::fitToCache)
    }

    private fun fitToCache(bitmap: Bitmap): Bitmap {
        val largestEdge = maxOf(bitmap.width, bitmap.height)
        if (largestEdge <= MAX_BITMAP_EDGE) return bitmap
        val scale = MAX_BITMAP_EDGE.toFloat() / largestEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    internal fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        var sampleSize = 1
        while (maxOf(width, height) / (sampleSize * 2) >= maxEdge) sampleSize *= 2
        return sampleSize
    }

    /** 懒加载索引（首次访问磁盘时读入内存） */
    private fun ensureIndexLoaded(context: Context) {
        if (indexLoaded) return
        runCatching {
            val file = indexFile(context)
            if (file.exists()) {
                val json = JSONObject(file.readText())
                json.keys().forEach { k -> keyToMd5[k] = json.getString(k) }
            }
        }
        indexLoaded = true
    }

    private fun saveIndex(context: Context) {
        runCatching {
            val json = JSONObject()
            keyToMd5.forEach { (k, v) -> json.put(k, v) }
            indexFile(context).writeText(json.toString())
        }
    }

    private const val MAX_BITMAP_EDGE = 1024
}
