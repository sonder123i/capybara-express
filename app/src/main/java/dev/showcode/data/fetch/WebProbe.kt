package dev.showcode.data.fetch

import android.os.SystemClock
import android.webkit.WebView
import kotlinx.coroutines.delay

/**
 * 「页面能读了没有」的探测。
 *
 * 抓取器以前在每次导航后固定睡 2.2~2.5 秒：快机上白等，慢机上还不够。
 * 这里改成轮询页面自身的状态，读到就立刻继续；上限仍是原来那个时长，
 * 所以最坏情况不会比固定睡眠更慢，只是典型路径快了一大截。
 */
internal object WebProbe {

    /** 正文非空、文档已 complete，且页面里的图（如果有）至少加载出一张。 */
    private const val READY_JS = """
        (function(){
          if (document.readyState !== 'complete') return '0';
          var b = document.body;
          if (!b || !b.innerText || b.innerText.trim().length === 0) return '0';
          var imgs = document.images || [];
          if (imgs.length === 0) return '1';
          for (var i = 0; i < imgs.length; i++) {
            if (imgs[i].complete && imgs[i].naturalWidth > 0) return '1';
          }
          return '0';
        })()
    """

    /**
     * 等到正文可读，再给 [settleMs] 让懒加载的图落位。
     *
     * @param maxMs 上限，默认和原来固定睡眠的 2200ms 对齐。
     * @return true = 在超时前读到了内容。
     */
    suspend fun awaitBody(
        evaluate: suspend (String) -> String?,
        maxMs: Long = 2_200,
        settleMs: Long = 220,
    ): Boolean {
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < maxMs) {
            if (evaluate(READY_JS) == "1") {
                delay(settleMs)
                return true
            }
            delay(120)
        }
        return false
    }
}
