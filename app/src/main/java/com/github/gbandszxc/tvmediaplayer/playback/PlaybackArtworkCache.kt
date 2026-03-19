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

    // key哈希 → 内容 MD5，多线程安全
    private val keyToMd5 = ConcurrentHashMap<String, String>()

    @Volatile
    private var indexLoaded = false

    // ---- 公开接口 ----

    /** 查内存缓存 */
    fun get(key: String): Bitmap? = memory.get(key)

    /** 仅写内存缓存 */
    fun put(key: String, bitmap: Bitmap) {
        memory.put(key, bitmap)
    }

    /**
     * 异步将 bitmap 以 JPEG 写入磁盘。
     * 相同内容只写一份文件，通过索引共享（省空间、省 IO）。
     */
    fun saveAsync(context: Context, key: String, bitmap: Bitmap) {
        ioScope.launch {
            runCatching {
                val bytes = ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.toByteArray()
                }
                val md5 = md5Hex(bytes)
                val content = contentFile(context, md5)
                if (!content.exists()) {
                    content.writeBytes(bytes)
                }
                keyToMd5[keyHash(key)] = md5
                saveIndex(context)
            }
        }
    }

    /**
     * 从磁盘读取缓存（同步，调用方负责在 IO 线程调用）。
     * 命中时不会自动放入内存，调用方应自行调用 put()。
     */
    fun loadFromDisk(context: Context, key: String): Bitmap? = runCatching {
        ensureIndexLoaded(context)
        val md5 = keyToMd5[keyHash(key)] ?: return null
        val file = contentFile(context, md5)
        if (!file.exists()) return null
        BitmapFactory.decodeFile(file.absolutePath)
    }.getOrNull()

    /** 清空内存和磁盘缓存 */
    fun clearDisk(context: Context) {
        memory.evictAll()
        keyToMd5.clear()
        indexLoaded = false
        artworkCacheDir(context).deleteRecursively()
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
}
