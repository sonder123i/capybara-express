package dev.showcode.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 详情页时间线解析：只保留带时间戳的物流轨迹，订单头 / 商品卡 / 推荐位 / 保障文案一律不进。
 * 语料取自 TraceParserTest 里的真机原案（整页 innerText 形态）。
 */
class TraceTimelineParseTest {

    private val realPage =
        "取件码 2-3-06120\n\n复制\n示例大学多多驿站示例大学示例体育场旁多多驿站\n支持退换货\n" +
            "申通快递\n:\n777400000060701\n复制\n收货地址: 示例省示例市示例区示例技术学院示例校区菜鸟驿站\n" +
            "待取件2026-11-06 10:32:14\n\n您的包裹已放入示例大学多多驿站，请您尽快前往领取您的包裹，" +
            "如有疑问请联系：13800000000\n" +
            "派件中2026-11-06 10:04:32\n\n【示例市】快递员(示例师菜鸟驿站/13800000000)正在为您派送，" +
            "请致电（0791-82303998）或专属渠道95543"

    @Test
    fun `只抽带时间的轨迹_订单头与商品介绍被丢掉`() {
        val rows = parseTraceTimeline(realPage)
        assertEquals(2, rows.size)

        assertEquals("待取件", rows[0].stage)
        assertEquals("11-06 10:32", rows[0].time)
        assertTrue(rows[0].detail.contains("您的包裹已放入示例大学多多驿站"))

        assertEquals("派件中", rows[1].stage)
        assertEquals("11-06 10:04", rows[1].time)
        assertTrue(rows[1].detail.contains("正在为您派送"))

        val joined = rows.joinToString("\n") { it.stage + it.detail }
        assertFalse(joined.contains("收货地址"))
        assertFalse(joined.contains("支持退换货"))
        assertFalse(joined.contains("复制"))
        assertFalse(joined.contains("取件码 2-3-06120"))
    }

    @Test
    fun `尾部商品区在展开处截止`() {
        val raw =
            "韵达快递\n:\n465851071777761\n复制\n订单编号: 261109-541841190480634\n" +
                "收货地址: 某省某市某驿站\n" +
                "已发货2026-11-10 09:14:13\n商家已发货，正在通知韵达快递取件\n" +
                "展开\nOXO高领毛衣男女秋冬慵懒风针织衫\n全店回购5945件\n正品险\n假一赔十\n36.96"
        val rows = parseTraceTimeline(raw)
        assertEquals(1, rows.size)
        assertEquals("已发货", rows[0].stage)
        assertEquals("商家已发货，正在通知韵达快递取件", rows[0].detail)
        assertFalse(rows[0].detail.contains("OXO"))
        assertFalse(rows[0].detail.contains("全店回购"))
    }

    @Test
    fun `既没时间也没阶段词就返回空表_交给调用方回退原文`() {
        assertEquals(emptyList<TraceRow>(), parseTraceTimeline("这是一段没有轨迹的包裹说明文字"))
        assertEquals(emptyList<TraceRow>(), parseTraceTimeline(""))
    }

    @Test
    fun `乱序输入也按时间从新到旧`() {
        val raw =
            "派件中2026-11-06 10:04:32\n正在派送\n" +
                "待取件2026-11-06 10:32:14\n已放入驿站，取件码 5-1-2277"
        val rows = parseTraceTimeline(raw)
        assertEquals(2, rows.size)
        assertEquals("待取件", rows[0].stage) // 10:32 比 10:04 新，排前面
        assertEquals("派件中", rows[1].stage)
    }

    @Test
    fun `阶段词和时间戳分行也能识别`() {
        val raw =
            "运输中\n2026-11-10 09:14:13\n包裹已发出，正在通知承运商\n" +
                "派送中\n2026-11-10 10:20:00\n正在派送"
        val rows = parseTraceTimeline(raw)
        assertEquals(listOf("派送中", "运输中"), rows.map { it.stage })
        assertEquals("11-10 10:20", rows[0].time)
        assertTrue(rows[1].detail.contains("包裹已发出"))
        assertFalse(rows[1].detail.contains("派送中"))
    }

    @Test
    fun `淘宝无时间戳_按阶段词切_且不切断明细里的动宾短语`() {
        val raw =
            "派送中 【南昌市】快件已到达 南昌前湖，如遇问题请致电【客服热线：0791-82303998】 " +
                "运输中 【南昌市】快件已发往 南昌前湖 已揽件 【上海市】快件已由 松江南部 揽收 " +
                "已发货 包裹正在等待揽收"
        val rows = parseTraceTimeline(raw)
        assertEquals(listOf("派送中", "运输中", "已揽件", "已发货"), rows.map { it.stage })
        // 「快件已到达」「快件已发往」是明细里的动宾短语，不能被当成新条目。
        assertTrue(rows[0].detail.contains("快件已到达 南昌前湖"))
        assertTrue(rows[1].detail.contains("快件已发往 南昌前湖"))
        assertEquals("", rows[0].time)
    }
}
