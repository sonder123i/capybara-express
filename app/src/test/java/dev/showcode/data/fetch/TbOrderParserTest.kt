package dev.showcode.data.fetch

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 订单响应的两种 schema；mtop 主源和 WebView 第二源共用同一解析。 */
class TbOrderParserTest {

    @Test
    fun `mainOrders schema parses order and goods`() {
        val inner = JSONObject()
            .put("mainOrders", JSONArray().put(
                JSONObject()
                    .put("id", "000000000000000001")
                    .put("statusInfo", JSONObject().put("text", "运输中"))
                    .put("subOrders", JSONArray().put(
                        JSONObject().put("itemInfo", JSONObject().put("title", "明日方舟 山山兔")),
                    )),
            ))
        val body = JSONObject().put("data", JSONObject().put("result", inner.toString())).toString()
        val page = TbFetcher.parseOrderResponse(body)
        assertEquals(1, page.orders.size)
        assertEquals("000000000000000001", page.orders[0].id)
        assertEquals("运输中", page.orders[0].status)
        assertEquals("明日方舟 山山兔", page.orders[0].goods?.title)
    }

    @Test
    fun `Main_ key schema parses legacy field layout`() {
        val inner = JSONObject().put(
            "Main_1",
            JSONObject().put(
                "fields",
                JSONObject().put("orderId", "261109-13507758").put("orderStatus", "已揽件"),
            ),
        )
        val body = JSONObject().put("data", JSONObject().put("result", inner.toString())).toString()
        val page = TbFetcher.parseOrderResponse(body)
        assertEquals(1, page.orders.size)
        assertEquals("261109-13507758", page.orders[0].id)
        assertEquals("已揽件", page.orders[0].status)
    }

    @Test
    fun `empty mainOrders is a valid empty page`() {
        val inner = JSONObject().put("mainOrders", JSONArray())
        val body = JSONObject().put("data", JSONObject().put("result", inner.toString())).toString()
        val page = TbFetcher.parseOrderResponse(body)
        assertTrue(page.orders.isEmpty())
    }
}
