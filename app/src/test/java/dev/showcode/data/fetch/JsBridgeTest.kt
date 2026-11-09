package dev.showcode.data.fetch

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `evaluateJavascript` 的回调值解码：字符串结果带一层 JSON 引号，漏解就是整条 WebView 抓取失效。 */
class JsBridgeTest {

    @Test
    fun `null 与字符串 null 都是没有值`() {
        assertNull(decodeJsString(null))
        assertNull(decodeJsString("null"))
    }

    @Test
    fun `字符串结果带一层 JSON 引号 - 解回原文`() {
        val buffer = """[{"u":"jsonp","res":"{\"orderId\":\"1\"}"}]"""
        assertEquals(buffer, decodeJsString(JSONObject.quote(buffer)))
        assertEquals("[]", decodeJsString(JSONObject.quote("[]")))
    }

    @Test
    fun `非字符串 JSON 原样返回`() {
        assertEquals("123", decodeJsString("123"))
    }
}
