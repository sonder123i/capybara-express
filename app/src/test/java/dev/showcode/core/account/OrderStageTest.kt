package dev.showcode.core.account

import dev.showcode.core.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderStageTest {

    @Test
    fun `终态状态全部映射到 DONE`() {
        listOf(
            "已签收", "签收成功", "已代收", "已投递", "交易成功",
            "订单完成", "已完成", "待评价", "已收货", "确认收货",
            "已取件", "已取走", "本人取件", "完成取件",
            /* 京东列表真实文案。 */
            "完成", "处理成功", "充值成功", "交易完成",
        ).forEach { assertEquals(it, Stage.DONE, OrderStage.stageOf(it)) }
    }

    @Test
    fun `未完成与待完成不算结束`() {
        listOf("未完成", "待完成", "尚未完成").forEach {
            assertFalse(it, OrderStage.stageOf(it) == Stage.DONE)
        }
    }

    /**
     * 「动作+完成」是在途节点播报，不是整单结束。DONE 里的裸「完成」+ 它先于
     * IN_TRANSIT 命中，曾把「揽收完成」这类在途件误归档，退休逻辑会真的删件。
     * 现在要么落到 IN_TRANSIT，要么认不出返回 null，都不能是 DONE。
     */
    @Test
    fun `过程性完成文案不能被判成终态`() {
        listOf(
            "揽收完成", "出库完成", "支付完成", "已揽收完成", "下单完成",
            "打包完成", "发货完成", "入库完成", "处理完成", "分拣完成", "清关完成",
        ).forEach {
            assertFalse("$it 不该是 DONE", OrderStage.stageOf(it) == Stage.DONE)
        }
        /* 明确带终态词的仍按 DONE 走，不被排除层误伤。 */
        assertEquals(Stage.DONE, OrderStage.stageOf("订单已完成"))
        assertEquals(Stage.DONE, OrderStage.stageOf("交易完成"))
        /* 「揽收完成」含「揽收」，应落到在途而不是认不出。 */
        assertEquals(Stage.IN_TRANSIT, OrderStage.stageOf("揽收完成"))
    }

    @Test
    fun `待取状态全部映射到 AT_STATION`() {
        listOf(
            "待取件", "待取货", "待自提", "待领取", "已到驿站", "已到达驿站", "已到快递柜",
            "已放入", "已暂存", "已入柜", "取件码已送达",
            "已到达菜鸟驿站", "已到【示例驿站】", "快件已放到示例代收点",
        ).forEach { assertEquals(it, Stage.AT_STATION, OrderStage.stageOf(it)) }
    }

    @Test
    fun `派送状态全部映射到 DELIVERING`() {
        listOf("派送中", "派件中", "正在派送", "待派送")
            .forEach { assertEquals(it, Stage.DELIVERING, OrderStage.stageOf(it)) }
    }

    @Test
    fun `运输状态全部映射到 IN_TRANSIT - 含已揽件与揽收`() {
        listOf(
            "待收货", "运输中", "已发货", "已发出", "已发往",
            "已揽收", "已揽件", "揽件", "揽收", "已装车",
            "转运中", "中转", "清关", "异地", "已到达",
            "待确认收货", "等待确认收货",
        ).forEach { assertEquals(it, Stage.IN_TRANSIT, OrderStage.stageOf(it)) }
    }

    @Test
    fun `认不出的状态返回 null - 不冒充结束`() {
        listOf("待付款", "未支付", "已取消", "退款中", "交易关闭", "", "未知状态", "待取消")
            .forEach { assertNull(it, OrderStage.stageOf(it)) }
    }

    @Test
    fun `isActive 只排除终态与未知`() {
        assertTrue(OrderStage.isActive("待取件"))
        assertTrue(OrderStage.isActive("待取货"))
        assertTrue(OrderStage.isActive("运输中"))
        assertTrue(OrderStage.isActive("已揽件"))
        assertTrue(OrderStage.isActive("待确认收货"))
        assertFalse(OrderStage.isActive("已签收"))
        assertFalse(OrderStage.isActive("确认收货"))
        assertFalse(OrderStage.isActive("待付款"))
        assertFalse(OrderStage.isActive(""))
    }
}
