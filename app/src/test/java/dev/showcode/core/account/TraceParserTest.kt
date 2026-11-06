package dev.showcode.core.account

import dev.showcode.core.CodeKind
import dev.showcode.core.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 真实轨迹的形态：状态在头、取件点在「已暂存至/已投递…在」后面、码/后五位各自出现。 */
class TraceParserTest {

    private fun parse(text: String, tracking: String = "465700000020382") = TraceParser.parse(
        platform = Platform.PDD,
        accountId = "pdd",
        orderSn = "261015-456000000060634",
        trackingNo = tracking,
        text = text,
        at = 1_793_000_000_000,
    )

    @Test
    fun `到站件 - 取件码与取件点都认得出`() {
        val item = parse(
            "取件码 5-2-7712 复制 菜鸟驿站·东门 您有 1 个快递待取 " +
                "待取件2026-10-17 09:46:39 【代收点】您的快件已暂存至 菜鸟驿站·东门（服务热线 95269901），请及时领取",
        )
        assertNotNull(item)
        assertEquals(Stage.AT_STATION, item!!.stage)
        assertEquals("5-2-7712", item.code)
        assertEquals(CodeKind.PICKUP, item.codeKind)
        assertEquals("菜鸟驿站·东门", item.pointName)
        assertFalse(item.explicitPickup)
    }

    @Test
    fun `后五位 - 正文里的数字优先于运单号末五位`() {
        val item = parse("请出示单号后五位 76416 到 菜鸟驿站·东门 领取", tracking = "123456789012345")
        assertNotNull(item)
        assertEquals("76416", item!!.code)
        assertEquals(CodeKind.SUFFIX, item.codeKind)
        assertEquals(Stage.AT_STATION, item.stage)
    }

    @Test
    fun `后五位 - 正文没写数字就用运单号 takeLast5`() {
        val item = parse("请凭取件码单号后五位在 丰巢快递柜 领取", tracking = "465700000020382")
        assertNotNull(item)
        assertEquals("20382", item!!.code)
        assertEquals(CodeKind.SUFFIX, item.codeKind)
        assertTrue(item.pointName!!.contains("丰巢"))
    }

    @Test
    fun `已签收 - 本人取件才算明确已取`() {
        val item = parse("已取件 本人取件 中通快递 : 465700000020382 菜鸟驿站·东门")
        assertNotNull(item)
        assertEquals(Stage.DONE, item!!.stage)
        assertTrue(item.explicitPickup)
    }

    @Test
    fun `已签收 - 签收人门口不算明确已取`() {
        val item = parse("已签收2026-10-20 签收人：门口 中通快递 : 465700000020382 菜鸟驿站·东门")
        assertNotNull(item)
        assertEquals(Stage.DONE, item!!.stage)
        assertFalse(item.explicitPickup)
    }

    @Test
    fun `运输中 - 没有点没有码`() {
        val item = parse("运输中 已发货 中通快递 : 465700000020382 目的地：示例省示例市")
        assertNotNull(item)
        assertEquals(Stage.IN_TRANSIT, item!!.stage)
        assertNull(item.pointName)
        assertEquals(CodeKind.NONE, item.codeKind)
    }

    @Test
    fun `没有运单号就不产出账号件`() {
        assertNull(parse("待取件 菜鸟驿站·东门", tracking = ""))
    }

    @Test
    fun `长地址 - 取「已暂存至」后面的点名而不是整段地址`() {
        val item = parse(
            "取件码 38-1-17031 已签收2026-10-20 【代收点】您的快件已投递，" +
                "收件人凭取件码在【示例大学示例校区驿站（服务热线95269901）】领取",
        )
        assertNotNull(item)
        assertEquals("示例大学示例校区驿站", item!!.pointName)
    }

    @Test
    fun `重复的驿站后缀 - 折叠成一个，保留分店名的菜鸟驿站`() {
        val item = parse("待取件 已暂存至 示例大学示例校区菜鸟驿站驿站（服务热线）")
        assertNotNull(item)
        assertEquals("示例大学示例校区菜鸟驿站", item!!.pointName)
    }

    @Test
    fun `括号里点名带重复后缀 - 清洗成用户看到的驿站名`() {
        // 用户真机上的原文：品牌在方括号里，括号里是完整的「分店 + 菜鸟驿站驿站」。
        val item = parse(
            "待取件 已签收2026-11-05 11:37 [菜鸟驿站](示例大学示例校区菜鸟驿站驿站) 取件出示单号后五位 54819",
        )
        assertNotNull(item)
        assertEquals("示例大学示例校区菜鸟驿站", item!!.pointName)
        assertEquals("54819", item.code)
        assertEquals(CodeKind.SUFFIX, item.codeKind)
    }

    @Test
    fun `派送中但已给出取件码 - 进待取而不是留在在途`() {
        val item = parse("取件码 5-2-7712 您已在示例大学示例校区驿站 物流派件中")
        assertNotNull(item)
        assertEquals(Stage.AT_STATION, item!!.stage)
        assertEquals("示例大学示例校区驿站", item.pointName)
    }

    @Test
    fun `派送中且没有取件信息 - 留在在途区，文案是派送中`() {
        val item = parse("物流派件中 请保持电话畅通 中通快递 : 465700000020382")
        assertNotNull(item)
        assertEquals(Stage.DELIVERING, item!!.stage)
        assertNull(item.pointName)
    }

    @Test
    fun `结构化轨迹 - 旧的派送中不能压住新的本人签收`() {
        val item = TraceParser.parseTraces(
            platform = Platform.TB,
            accountId = "tb",
            orderSn = "000000-442000000020634",
            trackingNo = "79040000076061",
            traces = listOf(
                TraceEntry("快件在派送中，请保持电话畅通", at = 1_000_000),
                TraceEntry("本人签收，包裹已交付", at = 2_000_000),
            ),
            at = 3_000_000,
        )
        assertNotNull(item)
        assertEquals(Stage.DONE, item!!.stage)
        assertTrue(item.explicitPickup)
    }

    @Test
    fun `点名清洗 - 去掉您已在前面，但不碰品牌分店名`() {
        assertEquals("示例大学示例校区驿站", TraceParser.cleanPointName("您已在示例大学示例校区驿站"))
        assertEquals("菜鸟驿站·东门", TraceParser.cleanPointName("菜鸟驿站·东门"))
        assertEquals("示例大学示例校区菜鸟驿站", TraceParser.cleanPointName("示例大学示例校区菜鸟驿站驿站"))
    }

    @Test
    fun `格口号不把取件码当成格口`() {
        val item = parse("取件码 38-1-17031 格口 A12 丰巢快递柜 待取件")
        assertNotNull(item)
        assertEquals("38-1-17031", item!!.code)
        assertEquals("格口 A12", item.lockerNo)
    }

    @Test
    fun `在途件没有运单号 - 有商品标题仍入列`() {
        // 淘宝真机原文：列表只有「物流运输中」，轨迹尚未到揽收节点。
        val item = TraceParser.parseTraces(
            platform = Platform.TB,
            accountId = "tb",
            orderSn = "5127000000000013123",
            trackingNo = "",
            traces = listOf(TraceEntry("【惠州市】快件已发往 上饶转运中心", at = 1_793_000_000_000)),
            at = 1_793_000_000_000,
            goods = GoodsInfo(title = "示例进口薄荷糖清凉润喉"),
            status = "已发货 物流运输中 预计明天送达",
        )
        assertNotNull(item)
        assertEquals(Stage.IN_TRANSIT, item!!.stage)
        assertEquals("", item.trackingNo)
        assertEquals("示例进口薄荷糖清凉润喉", item.goodsTitle)
        assertEquals("预计明天送达", item.raw.substringAfterLast(" ").trim())
    }

    @Test
    fun `在途件没有运单号 - 没有商品标题仍然丢弃`() {
        val item = TraceParser.parseTraces(
            platform = Platform.TB,
            accountId = "tb",
            orderSn = "5127000000000013123",
            trackingNo = "",
            traces = listOf(TraceEntry("快件已发往 上饶转运中心")),
            at = 1_793_000_000_000,
        )
        assertNull(item)
    }

    @Test
    fun `待取件没有运单号也没有码 - 有商品标题也不放宽门槛`() {
        val item = TraceParser.parseTraces(
            platform = Platform.TB,
            accountId = "tb",
            orderSn = "5127000000000013123",
            trackingNo = "",
            traces = listOf(TraceEntry("已暂存至 菜鸟驿站·东门 待取件")),
            at = 1_793_000_000_000,
            goods = GoodsInfo(title = "示例进口薄荷糖清凉润喉"),
            status = "待取件",
        )
        assertNull(item)
    }

    @Test
    fun `待取件有取件码 - 没有运单号也能入列`() {
        val item = TraceParser.parseTraces(
            platform = Platform.TB,
            accountId = "tb",
            orderSn = "5127000000000013123",
            trackingNo = "",
            traces = listOf(TraceEntry("取件码 5-2-7712 已暂存至 菜鸟驿站·东门 待取件")),
            at = 1_793_000_000_000,
            goods = GoodsInfo(title = "示例进口薄荷糖清凉润喉"),
            status = "待取件",
        )
        assertNotNull(item)
        assertEquals(Stage.AT_STATION, item!!.stage)
        assertEquals("5-2-7712", item.code)
        assertEquals(CodeKind.PICKUP, item.codeKind)
    }

    /**
     * 用户真机原案：同一件里「多多驿站」是取件点，而派件中那条括号装的是派件网点 + 电话。
     * 括号优先若不过滤，卡名会变成「示例师菜鸟驿站/13800000000」。
     */
    @Test
    fun `括号里是快递员网点 - 不得覆盖原文里的真实取件点`() {
        val item = parse(
            "取件码 2-3-06120\n\n复制\n示例大学多多驿站示例大学示例体育场旁多多驿站\n支持退换货\n" +
                "申通快递\n:\n777400000060701\n复制\n收货地址: 示例省示例市示例区示例技术学院示例校区菜鸟驿站\n" +
                "待取件2026-11-06 10:32:14\n\n您的包裹已放入示例大学多多驿站，请您尽快前往" +
                "示例大学示例体育场旁多多驿站领取您的包裹，如有疑问请联系：13800000000，" +
                "如有取件码问题或找不到包裹等问题，请联系：快递员【13800000000】，" +
                "投诉电话【079100000000】，营业时间【08:30-20:00】，可关注“申通快递”官方微信公众号获取实时物流信息\n" +
                "派件中2026-11-06 10:04:32\n\n【示例市】示例省示例市新建区新建公司的 快递员" +
                "(示例师菜鸟驿站/13800000000)正在为您派送，【物流问题无需找商家或平台，请致电（079100000000）或专属渠道95543尽快解决】",
            tracking = "777400000060701",
        )
        assertNotNull(item)
        assertEquals("2-3-06120", item!!.code)
        assertEquals("示例大学多多驿站", item.pointName)
        assertFalse(item.pointName!!.contains("示例师"))
        assertFalse(item.pointName.contains("/"))
    }

    @Test
    fun `具体门店优先于通用描述`() {
        val item = parse("取件码 2-3-06120 待取件 已到 驿站 示例大学多多驿站 领取")
        assertNotNull(item)
        assertEquals("示例大学多多驿站", item!!.pointName)
    }

    @Test
    fun `括号里只有通用品牌名 - 具体门店仍然胜出`() {
        val item = parse(
            "待取件 您的快件已暂存至（菜鸟驿站），随后已到 示例大学示例校区驿站 领取 取件码 3-1-5678",
        )
        assertNotNull(item)
        assertEquals("示例大学示例校区驿站", item!!.pointName)
        assertEquals("菜鸟驿站", TraceParser.cleanPointName("菜鸟驿站"))
        assertTrue(TraceParser.isGenericPoint("菜鸟驿站"))
        assertTrue(TraceParser.isGenericPoint("多多驿站"))
        assertFalse(TraceParser.isGenericPoint("示例大学多多驿站"))
    }

    @Test
    fun `通用描述不会顶替具体点名`() {
        val item = parse("取件码 3-1-5678 待取件 已暂存至 菜鸟驿站 示例大学多多驿站 领取")
        assertNotNull(item)
        assertEquals("示例大学多多驿站", item!!.pointName)
        assertNotEquals("菜鸟驿站", item.pointName)
        assertTrue(TraceParser.isGenericPoint("包裹已从代收点"))
        assertFalse(TraceParser.isGenericPoint("示例大学示例校区代收点"))
    }

    @Test
    fun `不同平台的同一种写法 - 点名一致`() {
        val text = "取件码 2-3-06120 待取件 快递员(示例师菜鸟驿站/13800000000)派送中，" +
            "您的包裹已放入示例大学多多驿站，请您尽快前往示例大学示例体育场旁多多驿站领取"
        val names = listOf(Platform.PDD, Platform.TB, Platform.JD).map { platform ->
            val item = TraceParser.parse(
                platform = platform,
                accountId = platform.name.lowercase(),
                orderSn = "000000-585500000080634",
                trackingNo = "777400000060701",
                text = text,
                at = 1_793_000_000_000,
            )
            item!!.pointName
        }
        assertEquals(listOf("示例大学多多驿站", "示例大学多多驿站", "示例大学多多驿站"), names)
    }

    /** 圆通真机原文：括号里就是取件点，点名的分店名不能被后面那句更长的描述顶掉。 */
    @Test
    fun `圆通括号点名 - 不被后面更长的分店描述顶替`() {
        val item = parse(
            "取件码 6-5-15031 待取件 您的快件已到达[代收点](示例大学多多驿站)" +
                "示例大学示例体育场旁多多驿站，请及时取件",
            tracking = "YT0000000010257",
        )
        assertNotNull(item)
        assertEquals("示例大学多多驿站", item!!.pointName)
    }

    /* ---------- 中文方括号门店（兔喜生活真机原文） ---------- */

    @Test
    fun `已从中文方括号取出 - 归档且点名为该门店`() {
        val item = TraceParser.parseTraces(
            platform = Platform.TB,
            accountId = "tb",
            orderSn = "5127000000000013123",
            trackingNo = "79040000020486",
            traces = listOf(
                TraceEntry("包裹已从代收点取出 11-06 15:35", at = 1_793_950_500_000),
                TraceEntry(
                    "您的快件已从【兔喜生活-示例市示例区示例大学示例校区店】取出，如遇问题请联系" +
                        "代收点电话【13800000000】",
                    at = 1_793_950_500_000,
                ),
            ),
            at = 1_793_965_000_000,
        )
        assertNotNull(item)
        assertEquals(Stage.DONE, item!!.stage)
        assertTrue(item.explicitPickup)
        assertEquals("兔喜生活-示例市示例区示例大学示例校区店", item.pointName)
    }

    @Test
    fun `已送达中文方括号 - 只取门店名，电话和方括号不入点名`() {
        val item = parse(
            "取件码 5-2-7712 待取件 已送达【兔喜生活-示例市示例区示例大学示例校区店，" +
                "电话：13800000000】 请及时取件",
        )
        assertNotNull(item)
        assertEquals(Stage.AT_STATION, item!!.stage)
        assertEquals("兔喜生活-示例市示例区示例大学示例校区店", item.pointName)
        assertFalse(item.pointName!!.contains("电话"))
        assertFalse(item.pointName.contains("【"))
    }

    @Test
    fun `句首到达动词 - 不进点名`() {
        assertEquals("代收点", TraceParser.cleanPointName("包裹已从代收点"))
        assertEquals("示例大学多多驿站", TraceParser.cleanPointName("您的包裹已从示例大学多多驿站"))
        assertEquals("示例大学多多驿站", TraceParser.cleanPointName("您的包裹已放入示例大学多多驿站"))
        assertEquals("示例大学多多驿站", TraceParser.cleanPointName("快件已送达示例大学多多驿站"))
    }

    @Test
    fun `中文方括号里是联系方式 - 不当取件点`() {
        val item = parse(
            "取件码 5-2-7712 待取件 已送达【示例员：13800000000】 请联系快递员",
        )
        assertNotNull(item)
        assertNull(item!!.pointName)
    }

    /** 旧数据红线：已认出具体门店时，后面的中文方括号门店不得再改写它。 */
    @Test
    fun `具体门店已认出来 - 中文方括号不得覆盖`() {
        val item = parse(
            "取件码 3-1-5678 待取件 示例大学示例校区驿站 已送达" +
                "【菜鸟-示例大学示例校区菜鸟驿站驿站，电话：13800000000】",
        )
        assertNotNull(item)
        assertEquals("示例大学示例校区驿站", item!!.pointName)
    }

    /* ---------- 整页按最新轨迹判定（顶部提示不得参与） ---------- */

    @Test
    fun `顶部待取件提示不参与 - 最新轨迹已取出签收`() {
        val item = parse(
            "取件码 5-2-7712 待取件 单号后五位 54819 " +
                "菜鸟驿站示例大学示例校区菜鸟驿站驿站 " +
                "已签收2026-10-20 10:19:12 您的快件已取出签收，" +
                "签收代收点：菜鸟驿站示例大学示例校区菜鸟驿站驿站 " +
                "待取件2026-10-16 17:12:48 快件已送达" +
                "【菜鸟驿站示例大学示例校区菜鸟驿站驿站】，请及时取件",
        )
        assertNotNull(item)
        assertEquals(Stage.DONE, item!!.stage)
        assertTrue(item.explicitPickup)
    }

    @Test
    fun `最新轨迹还是待取件 - 旧的已取出不得把它归档`() {
        val item = parse(
            "取件码 5-2-7712 待取件 单号后五位 54819 " +
                "已签收2026-10-20 10:19:12 您的快件已取出签收，" +
                "签收代收点：菜鸟驿站示例大学示例校区菜鸟驿站驿站 " +
                "待取件2026-10-21 09:00:00 快件已送达" +
                "【菜鸟驿站示例大学示例校区菜鸟驿站驿站】，请凭取件码领取",
        )
        assertNotNull(item)
        assertEquals(Stage.AT_STATION, item!!.stage)
        assertFalse(item.explicitPickup || item.stage == Stage.DONE)
    }

    @Test
    fun `最新轨迹已领取 - 归档而顶部取件码不拦`() {
        val item = parse(
            "取件码 5-2-7712 待取件 " +
                "已签收2026-10-15 18:20:05 您的快件已领取，" +
                "收件人在【示例大学示例驿站】 " +
                "待取件2026-10-15 11:41:21 请及时取件",
        )
        assertNotNull(item)
        assertEquals(Stage.DONE, item!!.stage)
        assertTrue(item.explicitPickup)
    }

    @Test
    fun `最新轨迹认不出 - 回退到次新完成态`() {
        val item = parse(
            "取件码 5-2-7712 待取件 " +
                "快件动态2026-10-21 10:00:00 您的快件正在路上 " +
                "已签收2026-10-20 10:00:00 您的快件已取出签收，" +
                "签收代收点：示例大学示例驿站 " +
                "待取件2026-10-19 10:00:00 请凭取件码领取",
        )
        assertNotNull(item)
        assertEquals(Stage.DONE, item!!.stage)
    }

    /* ---------- 点位名拼接 ---------- */

    @Test
    fun `页头拼了两处点名 - 只取第一处完整点名`() {
        assertEquals(
            "示例大学多多驿站",
            TraceParser.cleanPointName("示例大学多多驿站示例大学示例校区旁多多驿站"),
        )
        assertEquals(
            "示例大学示例校区菜鸟驿站",
            TraceParser.cleanPointName("示例大学示例校区菜鸟驿站驿站"),
        )
    }
}
