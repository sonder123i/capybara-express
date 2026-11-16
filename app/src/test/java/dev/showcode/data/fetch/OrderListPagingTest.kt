package dev.showcode.data.fetch

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderListPagingTest {

    @Test
    fun `hasNext 明确为 false 才算结束`() {
        assertTrue(orderListEnded(JSONObject().put("hasNext", false), seen = 10))
        assertFalse(orderListEnded(JSONObject().put("hasNext", true), seen = 10))
    }

    @Test
    fun `has_more 的 0 与 1`() {
        assertTrue(orderListEnded(JSONObject().put("has_more", 0), seen = 10))
        assertFalse(orderListEnded(JSONObject().put("has_more", 1), seen = 10))
    }

    @Test
    fun `总数已经看全才结束`() {
        assertTrue(orderListEnded(JSONObject().put("total", 10), seen = 10))
        assertFalse(orderListEnded(JSONObject().put("total", 30), seen = 10))
    }

    @Test
    fun `没有结束信号或读不懂 - 不猜`() {
        assertFalse(orderListEnded(null, seen = 10))
        assertFalse(orderListEnded(JSONObject(), seen = 10))
        assertFalse(orderListEnded(JSONObject().put("hasNext", "maybe"), seen = 10))
    }
}
