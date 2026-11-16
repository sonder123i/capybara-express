package dev.showcode.core

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * 同步分段耗时。只记阶段、计数与毫秒，不记单号、取件码、点位名或 Cookie。
 *
 * 一次同步一个 session；并发详情调用 mark 时用 seq 区分先后。
 * 日志走 [Slog.safe]，release 也保留，现场才能判断「慢在哪一段」。
 */
class SyncPerf private constructor(
    private val label: String,
    private val id: Long,
) {
    private val startedAt = SystemClock.uptimeMillis()
    private val seq = AtomicLong()

    fun mark(phase: String, account: String? = null, extra: String = "") {
        val elapsed = SystemClock.uptimeMillis() - startedAt
        val n = seq.incrementAndGet()
        val suffix = buildString {
            account?.takeIf { it.isNotBlank() }?.let { append(" account=").append(it) }
            extra.takeIf { it.isNotBlank() }?.let { append(' ').append(it) }
        }
        Slog.safe(TAG, "id=$id seq=$n label=$label elapsed=${elapsed}ms phase=$phase$suffix")
    }

    companion object {
        private const val TAG = "SyncPerf"
        private val nextId = AtomicLong()

        fun begin(label: String): SyncPerf = SyncPerf(label, nextId.incrementAndGet())
    }
}
