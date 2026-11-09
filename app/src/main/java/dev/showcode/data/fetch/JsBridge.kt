package dev.showcode.data.fetch

import org.json.JSONTokener

/**
 * `WebView.evaluateJavascript` 的回调值是 **JSON 编码后**的文本：
 * 页面的字符串结果会带引号与转义（`JSON.stringify([])` 回来是 `"[]"`，多一层引号）。
 *
 * 三处 WebView 抓取都用这一个函数，别再各写一份——淘宝第二源漏了这步解码，
 * 于是 `JSONArray(raw)` 永远抛异常，整条链路静默失效。
 */
internal fun decodeJsString(value: String?): String? {
    if (value == null || value == "null") return null
    return runCatching {
        val parsed = JSONTokener(value).nextValue()
        if (parsed is String) parsed else value
    }.getOrDefault(value)
}
