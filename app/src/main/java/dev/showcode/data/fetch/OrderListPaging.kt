package dev.showcode.data.fetch

import org.json.JSONObject

/**
 * 订单列表是不是「真的看全了」。
 *
 * 只信接口自己给的结束信号：hasNext=false、nextPage 为空、或 total 覆盖已读条数。
 * 拿条数去猜「应该就这些」会把没翻到的在途件误退休，而且它们下次仍然翻不到，永远回不来。
 * 认不出来就返回 false —— 交给退休逻辑的 seen 集合保持空集，沿用「空集不退休」的保护。
 */
internal fun orderListEnded(meta: JSONObject?, seen: Int): Boolean {
    if (meta == null) return false
    val keys = meta.keys()
    while (keys.hasNext()) {
        val raw = keys.next()
        val key = raw.lowercase()
        val value = meta.opt(raw) ?: continue
        if (key in END_KEYS) return flagEnded(value) ?: continue
        if (key in TOTAL_KEYS) {
            val total = numberOf(value) ?: continue
            if (total <= seen) return true
        }
    }
    return false
}

private val END_KEYS = setOf(
    "hasnext", "hasmore", "hasnextpage", "hasmorepage", "nextpage",
    "has_next", "has_more", "next_page",
)
private val TOTAL_KEYS = setOf("total", "totalcount", "totalnum", "total_count")

/** 结束标志的三种写法：布尔、0/1、字符串。读不懂就是「说不清」，不猜。 */
private fun flagEnded(value: Any?): Boolean? = when (value) {
    is Boolean -> !value
    is Number -> value.toInt() == 0
    is String -> when (value.trim().lowercase()) {
        "0", "false", "no" -> true
        "1", "true", "yes" -> false
        else -> null
    }
    else -> null
}

private fun numberOf(value: Any?): Int? = when (value) {
    is Number -> value.toInt()
    is String -> value.trim().toIntOrNull()
    else -> null
}
