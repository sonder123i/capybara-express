package dev.showcode.ui.parcels

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 商品缩略图的轻量加载器。
 *
 * 只为详情页一张 60dp 的小图，不值得引入 Coil/Glide；这里做两件事就够：
 * 内存 LRU 缓存，以及「下载 → 按目标尺寸降采样解码」。失败一律返回 null，
 * 由调用方画一个中性占位，不抛异常、不重试、不留半张图。
 */
object ThumbnailLoader {

    /** 目标边长：60dp 在 3x 屏上约 180px，多留一点给缩放。 */
    private const val TARGET_PX = 200

    private val cache = object : LruCache<String, Bitmap>(maxCacheKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        val bitmap = runCatching { download(url) }.getOrNull()
        bitmap?.let { cache.put(url, it) }
        bitmap
    }

    private fun download(url: String): Bitmap? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12)")
        }
        try {
            if (conn.responseCode !in 200..299) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= TARGET_PX &&
                bounds.outHeight / (sample * 2) >= TARGET_PX
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } finally {
            conn.disconnect()
        }
    }

    /** 取可用堆的 1/8 做缓存，和 Android 官方 Bitmap 示例同一条口径。 */
    private fun maxCacheKb(): Int {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (maxKb / 8).coerceAtLeast(1024)
    }
}
