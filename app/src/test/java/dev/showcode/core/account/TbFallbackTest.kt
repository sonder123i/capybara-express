package dev.showcode.core.account

import dev.showcode.core.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 列表状态兜底：详情失败时，订单列表文案必须能独立撑起一条可见条目。
 * 淘宝真实的「已揽件」曾经不在任何词表里，这条测试防它再退化。
 */
class TbFallbackTest {

    private fun fallback(prompt: String) = fallbackFetchedParcel(
        platform = Platform.TB,
        accountId = "tb",
        orderSn = "000000000000000001",
        prompt = prompt,
        goods = GoodsInfo(title = "明日方舟 山山兔毛绒玩偶", image = null, price = null, shop = null, orderedAt = null),
        at = 1_000,
    )

    @Test
    fun `已揽件兜底进在途`() {
        val item = fallback("已揽件")!!
        assertEquals(Stage.IN_TRANSIT, item.stage)
        assertEquals("000000000000000001", item.orderSn)
    }

    @Test
    fun `运输中与待收货兜底进在途`() {
        assertEquals(Stage.IN_TRANSIT, fallback("运输中")!!.stage)
        assertEquals(Stage.IN_TRANSIT, fallback("待收货")!!.stage)
    }

    @Test
    fun `待取件兜底进待取`() {
        assertEquals(Stage.AT_STATION, fallback("待取件")!!.stage)
    }

    @Test
    fun `已签收不出兜底 - 终态只用于退休`() {
        assertNull(fallback("已签收"))
        assertNull(fallback("交易成功"))
    }
}
