package dev.showcode.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoodsInfoTest {

    @Test
    fun `纯数字价格补人民币符号`() {
        assertEquals("¥9.90", goodsPriceText("9.90"))
        assertEquals("¥9", goodsPriceText("9"))
    }

    @Test
    fun `已经带货币符号的价格不重复加`() {
        assertEquals("¥12.5", goodsPriceText("￥12.5"))
        assertEquals("¥ 12.50", goodsPriceText("¥ 12.50"))
    }

    @Test
    fun `空白价格不显示`() {
        assertNull(goodsPriceText(null))
        assertNull(goodsPriceText("  "))
    }

    @Test
    fun `拼多多整数价格按分换算`() {
        assertEquals("¥28.4", pddPriceText("2840"))
        assertEquals("¥9", pddPriceText("900"))
        assertEquals("¥28.40", pddPriceText("28.40"))
    }

    @Test
    fun `十位时间戳按秒、十三位按毫秒`() {
        assertEquals(1_763_000_000_000L, orderTimeMs(1_763_000_000L))
        assertEquals(1_763_000_000_123L, orderTimeMs(1_763_000_000_123L))
        assertNull(orderTimeMs(0))
    }

    @Test
    fun `日期文本可以解析，解析不了就放弃`() {
        val millis = orderTimeMs("2025-11-04 09:30:00")
        assertTrue(millis != null && millis > 1_700_000_000_000L)
        assertNull(orderTimeMs("昨天下午"))
    }

    @Test
    fun `协议相对图片地址补 https`() {
        assertEquals(
            "https://img.alicdn.com/a.jpg",
            normalizeImageUrl("//img.alicdn.com/a.jpg"),
        )
        assertEquals(
            "https://img.alicdn.com/a.jpg",
            normalizeImageUrl("http://img.alicdn.com/a.jpg"),
        )
        assertNull(normalizeImageUrl("data:image/png;base64,x"))
    }

    @Test
    fun `拼多多图床的 avif 降级成 jpeg - 低版本系统也能显示缩略图`() {
        assertEquals(
            "https://img-1.pddpic.com/a.jpeg?imageMogr2/format/jpeg/quality/90",
            normalizeImageUrl("https://img-1.pddpic.com/a.jpeg?imageMogr2/format/avif/quality/90"),
        )
    }
}
