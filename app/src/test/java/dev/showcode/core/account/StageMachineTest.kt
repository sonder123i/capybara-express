package dev.showcode.core.account

import dev.showcode.core.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 三家共用状态机：词表、终态不可逆、最新轨迹覆盖旧轨迹。 */
class StageMachineTest {

    @Test
    fun `派送中到已签收 - 最新轨迹覆盖旧轨迹`() {
        val stage = StageMachine.stageFromTraces(
            listOf(
                TraceEntry("快件已到达示例转运中心，运输中", at = 1_000_000),
                TraceEntry("快件在派送中，请保持电话畅通", at = 2_000_000),
                TraceEntry("已签收，签收人：本人", at = 3_000_000),
            ),
        )
        assertEquals(Stage.DONE, stage)
    }

    @Test
    fun `旧轨迹不覆盖新轨迹 - 更新的派送中压过更旧的已签收`() {
        val stage = StageMachine.stageFromTraces(
            listOf(
                TraceEntry("已签收", at = 1_000_000),
                TraceEntry("派送中", at = 2_000_000),
            ),
        )
        assertEquals(Stage.DELIVERING, stage)
    }

    @Test
    fun `没有时间戳 - 不依赖数组顺序，终态赢`() {
        val oldestFirst = listOf(TraceEntry("运输中"), TraceEntry("派送中"), TraceEntry("已签收"))
        val newestFirst = oldestFirst.reversed()
        assertEquals(Stage.DONE, StageMachine.stageFromTraces(oldestFirst))
        assertEquals(Stage.DONE, StageMachine.stageFromTraces(newestFirst))
    }

    @Test
    fun `祈使句只是请你去取 - 不能当成已取`() {
        assertEquals(Stage.AT_STATION, StageMachine.stageOfTrace("请凭取件码取件 5-1-2277"))
        assertEquals(Stage.AT_STATION, StageMachine.stageOfTrace("驿站取件请出示取件码"))
        assertFalse(StageMachine.isExplicitPickup("凭取件码取件"))
        assertFalse(StageMachine.isExplicitPickup("驿站取件"))
        /* 取出后的事后提示仍带「取件码」，不能把它当成完成态。 */
        assertFalse(StageMachine.isExplicitPickup("如遇取件码问题或找不到包裹请联系快递员"))
    }

    @Test
    fun `完成态仍然归档 - 不让误判修复漏掉真已取`() {
        listOf("本人取件", "已取件", "取件完成", "取件成功", "已完成取件").forEach {
            assertEquals(it, Stage.DONE, StageMachine.stageOfTrace(it))
            assertTrue(it, StageMachine.isExplicitPickup(it))
        }
    }

    @Test
    fun `没有时间戳 - 新到站与旧已签收同时存在时留在待取`() {
        val newestFirst = listOf(
            TraceEntry("包裹已放入菜鸟驿站，请凭取件码取件"),
            TraceEntry("已签收"),
        )
        // 两种数组顺序都不能把待取件归档：顺序不可靠时宁可不误判。
        assertEquals(Stage.AT_STATION, StageMachine.stageFromTraces(newestFirst))
        assertEquals(Stage.AT_STATION, StageMachine.stageFromTraces(newestFirst.reversed()))
    }

    @Test
    fun `驿站代收未取 - 仍是待取而不是归档`() {
        assertEquals(
            Stage.AT_STATION,
            StageMachine.stageOfTrace("已签收 包裹已放入菜鸟驿站 取件码 5-2-7712"),
        )
        assertEquals(
            Stage.AT_STATION,
            StageMachine.stageOfTrace("已签收2026-11-05 11:37 [菜鸟驿站](示例大学示例校区菜鸟驿站驿站) 取件出示单号后五位 54819"),
        )
    }

    @Test
    fun `代收点代签 - 已签收凭取件码仍是待取而不是归档`() {
        /* 极兔真机原文：代收点入库后系统写「已签收（凭取件码）」，
           件其实还躺在驿站等取；括号里的「凭取件码」不是祈使句，也不是本人取件。 */
        val text = "包裹已签收！（凭取件码）如有问题请电联：代收点 13800000000/" +
            "示例员-示例学院 13800000000，投诉电话：021-00000000"
        assertEquals(Stage.AT_STATION, StageMachine.stageOfTrace(text))
        assertFalse(StageMachine.isExplicitPickup(text))
        assertTrue(StageMachine.isAgentSigned(text))
    }

    @Test
    fun `代收点代签 - 只给签收词或只给取件码都不算`() {
        listOf(
            "已签收",
            "包裹已放入多多驿站，请凭取件码取件",
            "请凭取件码取件 5-1-2277",
            "确认收货 取件码 3-1-1111",
            "您的快件已取出签收，签收代收点：示例大学示例驿站",
        ).forEach {
            assertFalse(it, StageMachine.isAgentSigned(it))
        }
    }

    @Test
    fun `真机原文 - 极兔代收点签收凭取件码留在待取`() {
        val item = TraceParser.parseTraces(
            platform = Platform.TB,
            accountId = "tb",
            orderSn = "000000-0000000000000000",
            trackingNo = "JT0000000000000",
            traces = listOf(
                TraceEntry(
                    "包裹已签收！（凭取件码）如有问题请电联：代收点 13800000000/" +
                        "示例员-示例学院 13800000000，投诉电话：021-00000000",
                    at = 3_000_000,
                ),
                TraceEntry(
                    "快件已由兔兔快递员示例员-示例学院 13800000000送达代收点暂存,取件地址:" +
                        "示例大学示例校区旁多多驿站,请及时取件。代理点电话:13800000000," +
                        "投诉电话:021-00000000",
                    at = 2_000_000,
                ),
            ),
            at = 4_000_000,
            status = "待收货",
            rawText = "取件码 4-6-05004\n示例大学示例校区旁多多驿站",
        )
        assertNotNull(item)
        assertEquals(Stage.AT_STATION, item!!.stage)
        assertFalse(item.explicitPickup)
        assertEquals("4-6-05004", item.code)
        assertEquals("示例大学示例校区旁多多驿站", item.pointName)
    }

    @Test
    fun `本人签收或已取件 - 归档`() {
        assertEquals(Stage.DONE, StageMachine.stageOfTrace("本人签收，包裹已交付"))
        assertEquals(Stage.DONE, StageMachine.stageOfTrace("已取件 本人取件 中通快递 465700000020382"))
        assertEquals(Stage.DONE, StageMachine.stageOfTrace("您的订单已签收，感谢使用京东"))
    }

    @Test
    fun `派送中 - 有取件信息进待取，没有就留在在途`() {
        assertEquals(
            Stage.AT_STATION,
            StageMachine.stageOfTrace("派送中，已到达菜鸟驿站·东门，取件码 5-2-7712"),
        )
        assertEquals(Stage.DELIVERING, StageMachine.stageOfTrace("物流派件中，请保持电话畅通"))
    }

    @Test
    fun `在途词表 - 揽收运输中转清关异地`() {
        listOf("已揽收", "运输中", "已发出", "中转", "清关", "异地").forEach {
            assertEquals(it, Stage.IN_TRANSIT, StageMachine.stageOfTrace(it))
        }
    }

    @Test
    fun `三家真实文案 - 同一份词表同一个结论（运输中）`() {
        val samples = listOf(
            "【拼多多】快递单号 465700000020382 运输中 已到达杭州中转中心",
            "淘宝：快件已揽收，运输中，预计明天送达",
            "京东物流：您的订单已发货，运输中",
        )
        samples.forEach {
            assertEquals(it, Stage.IN_TRANSIT, StageMachine.stageOfTrace(it))
        }
    }

    @Test
    fun `订单列表的待收货不会盖住轨迹 - 已签收会`() {
        val traces = listOf(
            TraceEntry("派送中", at = 1_000_000),
            TraceEntry("待收货", at = 2_000_000),
        )
        assertEquals(Stage.DELIVERING, StageMachine.stageFromTraces(traces))
        assertEquals(
            Stage.DONE,
            StageMachine.stageFromTraces(traces + TraceEntry("已签收", at = 3_000_000)),
        )
    }

    @Test
    fun `淘宝 multiStage 结构化解析 - 两件中通的已签收不会被旧派送中压住`() {
        val item = TraceParser.parseTraces(
            platform = Platform.TB,
            accountId = "tb",
            orderSn = "000000-442000000020634",
            trackingNo = "79040000076061",
            traces = listOf(
                TraceEntry("快件在派送中", at = 1_000_000),
                TraceEntry("已签收", at = 2_000_000),
                TraceEntry("待收货", at = 3_000_000),
            ),
            at = 3_000_000,
        )
        assertNotNull(item)
        assertEquals(Stage.DONE, item!!.stage)
        assertFalse(item.explicitPickup)
        assertNull(item.pointName)
    }

    @Test
    fun `真机原文 - 淘宝两件中通的完成取件必须归档`() {
        /* 2026-11-05 从用户设备 tb 账号取到的原文（节选，顺序与库里一致：最新在上）。 */
        val raw = "您已在示例大学示例校区菜鸟驿站驿站完成取件，感谢使用菜鸟驿站，期待再次为您服务。 " +
            "10-30 19:38 您的快件已由示例大学示例校区菜鸟驿站驿站菜鸟驿站代收。" +
            "请凭取货码及时取件（查询方式：短信/物流详情页） 10-30 17:11 " +
            "示例市示例区 快递员【示例员：13800000000】正在为您派件 " +
            "10-30 13:10 【示例市】 快件已到达 示例市示例区 物流派件中"
        val item = TraceParser.parse(
            platform = Platform.TB,
            accountId = "tb",
            orderSn = "5127000000000058749",
            trackingNo = "79040000038478",
            text = raw,
            at = 0,
        )
        assertNotNull(item)
        assertEquals(Stage.DONE, item!!.stage)
        assertTrue(item.explicitPickup)
    }

    @Test
    fun `真机原文 - 淘宝兔喜取出必须归档`() {
        /* 2026-11-06 19:37 从用户设备 tb 账号取到的原文（节选，最新在上）。
           最新一条是「已从【兔喜生活…】取出」，后面还跟「如遇取件码问题…」；
           旧规则先命中「取件码」判成到站，这件就一直挂在待取。 */
        val item = TraceParser.parseTraces(
            platform = Platform.TB,
            accountId = "tb",
            orderSn = "5127000000000013123",
            trackingNo = "79040000020486",
            traces = listOf(
                TraceEntry("包裹已从代收点取出 11-06 15:35", at = 1_793_950_500_000),
                TraceEntry(
                    "您的快件已从【兔喜生活-示例市示例区示例大学示例校区店】取出，如遇问题请联系" +
                        "代收点电话【13800000000】，无需找商家/平台。如遇取件码问题或找不到包裹" +
                        "等问题请联系快递员【示例员：13800000000】",
                    at = 1_793_950_500_000,
                ),
                TraceEntry(
                    "快件已送达【兔喜生活-示例市示例区示例大学示例校区店，电话：13800000000】，" +
                        "取件地址：示例大学示例校区代收点(示例校区)，请及时取件。" +
                        "如遇取件码问题或找不到包裹等问题请联系快递员【示例员：13800000000】",
                    at = 1_793_926_980_000,
                ),
            ),
            at = 1_793_965_000_000,
            status = "物流派件中",
        )
        assertNotNull(item)
        assertEquals(Stage.DONE, item!!.stage)
        assertTrue(item.explicitPickup)
    }

    @Test
    fun `订单汇总物流派件中 - 不能盖住轨迹里的完成取件`() {
        val item = TraceParser.parseTraces(
            platform = Platform.TB,
            accountId = "tb",
            orderSn = "5127000000000058749",
            trackingNo = "79040000038478",
            traces = listOf(
                TraceEntry("您的快件已由示例大学示例校区菜鸟驿站代收，请凭取货码及时取件", at = 1_000_000),
                TraceEntry("您已在示例大学示例校区菜鸟驿站完成取件", at = 2_000_000),
            ),
            at = 3_000_000,
            status = "物流派件中",
        )
        assertNotNull(item)
        assertEquals(Stage.DONE, item!!.stage)
        assertTrue(item.explicitPickup)
    }

    @Test
    fun `订单汇总已签收 - 不能单独覆盖更新的到站轨迹`() {
        val item = TraceParser.parseTraces(
            platform = Platform.PDD,
            accountId = "pdd",
            orderSn = "000000-442000000020634",
            trackingNo = "465700000020382",
            traces = listOf(TraceEntry("包裹已放入多多驿站，请凭取件码取件", at = 1_000_000)),
            at = 2_000_000,
            status = "已签收",
        )
        assertNotNull(item)
        assertEquals(Stage.AT_STATION, item!!.stage)
        assertFalse(item.explicitPickup)
        assertEquals("多多驿站", item.pointName)
    }

    @Test
    fun `订单汇总本人取件 - 明确拿走仍然归档`() {
        val item = TraceParser.parseTraces(
            platform = Platform.PDD,
            accountId = "pdd",
            orderSn = "000000-442000000020634",
            trackingNo = "465700000020382",
            traces = listOf(TraceEntry("包裹已放入多多驿站，请凭取件码取件", at = 1_000_000)),
            at = 2_000_000,
            status = "本人取件",
        )
        assertNotNull(item)
        assertEquals(Stage.DONE, item!!.stage)
        assertTrue(item.explicitPickup)
    }

    @Test
    fun `订单汇总交易成功 - 轨迹缺失时也能归档`() {
        val item = TraceParser.parseTraces(
            platform = Platform.TB,
            accountId = "tb",
            orderSn = "5127000000000012740",
            trackingNo = "79030000003029",
            traces = listOf(TraceEntry("快件已发往示例转运中心", at = 1_000_000)),
            at = 2_000_000,
            status = "交易成功",
        )
        assertNotNull(item)
        assertEquals(Stage.DONE, item!!.stage)
    }

    @Test
    fun `轨迹时间 - 可读的带时刻日期才参与排序`() {
        assertNotNull(StageMachine.traceTimeMs("2026-11-05 11:37:20"))
        assertNotNull(StageMachine.traceTimeMs("2026-11-05T11:37:20"))
        assertNotNull(StageMachine.traceTimeMs("11-05 11:37"))
        assertNull(StageMachine.traceTimeMs("2026-11-05"))
        assertNull(StageMachine.traceTimeMs("11:37"))
        assertNull(StageMachine.traceTimeMs(""))
    }

    @Test
    fun `一条状态都没有 - 不猜`() {
        assertEquals(Stage.UNKNOWN, StageMachine.stageFromTraces(emptyList()))
        assertEquals(
            Stage.UNKNOWN,
            StageMachine.stageFromTraces(listOf(TraceEntry("您的快件正在路上"))),
        )
    }

    @Test
    fun `已取出签收 已取出 已领取 - 都是完成态`() {
        listOf(
            "您的快件已取出签收，签收代收点：示例大学示例驿站",
            "包裹已取出",
            "您的快件已领取，收件人在示例大学示例驿站",
        ).forEach { text ->
            assertEquals(text, Stage.DONE, StageMachine.stageOfTrace(text))
            assertTrue(text, StageMachine.isExplicitPickup(text))
        }
    }

    @Test
    fun `领取前的已 是硬条件 - 祈使句与待领取都不算完成`() {
        listOf(
            "请凭取件码领取",
            "请到店领取",
            "待领取",
            "您的快件已投递，收件人凭取件码领取",
            "已签收，请及时领取",
        ).forEach { text ->
            assertFalse(text, StageMachine.isExplicitPickup(text))
            assertFalse(text, StageMachine.stageOfTrace(text) == Stage.DONE)
        }
    }

    @Test
    fun `最新轨迹认不出 - 回退次新并说明用了第几条`() {
        val traces = listOf(
            TraceEntry("您的快件正在路上", at = 3_000_000),
            TraceEntry("您的快件已取出签收", at = 2_000_000),
            TraceEntry("快件已到达转运中心", at = 1_000_000),
        )
        val pick = StageMachine.pickStage(traces)
        assertEquals(Stage.DONE, pick.stage)
        assertEquals(2, pick.index)
        assertTrue(pick.reason, "回退到第 2 条" in pick.reason)
    }
}
