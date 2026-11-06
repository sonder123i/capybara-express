package dev.showcode.core.account

import dev.showcode.core.CodeKind
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import dev.showcode.core.Stage
import dev.showcode.core.pointKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountMergeTest {

    private fun item(
        code: String = "5-2-7712",
        kind: CodeKind = CodeKind.PICKUP,
        point: String? = "菜鸟驿站·东门",
        tracking: String = "465700000020382",
        stage: Stage = Stage.AT_STATION,
        explicit: Boolean = false,
        goodsTitle: String? = null,
        orderSn: String = "261015-456000000060634",
    ) = FetchedParcel(
        platform = Platform.PDD,
        accountId = "pdd",
        trackingNo = tracking,
        orderSn = orderSn,
        carrier = "中通快递",
        stage = stage,
        pointName = point,
        lockerNo = null,
        code = code,
        codeKind = kind,
        explicitPickup = explicit,
        raw = "轨迹",
        at = 1_000,
        goodsTitle = goodsTitle,
    )

    private fun parcel(
        id: Long = 1,
        code: String = "5-2-7712",
        pointName: String = "菜鸟驿站·东门",
        picked: Boolean = false,
        tracking: String? = null,
        stage: Stage = Stage.AT_STATION,
        source: Source = Source.SMS,
        orderSn: String? = null,
        accountId: String? = null,
        retired: Boolean = false,
    ) = Parcel(
        id = id,
        code = code,
        lockerNo = null,
        carrier = "中通快递",
        pointKey = pointKey(pointName),
        pointName = pointName,
        pointKind = PointKind.STATION,
        owner = Owner.SELF,
        source = source,
        receivedAt = 900,
        freeUntilAt = null,
        closesAtMinuteOfDay = null,
        picked = picked,
        pickedAt = null,
        raw = "",
        trackingNo = tracking,
        stage = stage,
        orderSn = orderSn,
        accountId = accountId,
        retired = retired,
    )

    @Test
    fun `新件 - 有码有点才插入`() {
        val action = planAccountSync(listOf(item()), emptyList()).single()
        assertTrue(action is AccountSyncAction.Insert)
        assertEquals(pointKey("菜鸟驿站·东门"), (action as AccountSyncAction.Insert).pointKey)
        assertEquals(PointKind.STATION, action.pointKind)
    }

    @Test
    fun `无运单号无码 - 用订单号先入列，不伪造首页卡片`() {
        val noPoint = planAccountSync(listOf(item(point = null, tracking = "")), emptyList()).single()
        assertTrue(noPoint is AccountSyncAction.Insert)
        val insert = noPoint as AccountSyncAction.Insert
        assertEquals("", insert.pointKey)
        assertEquals("", insert.item.pointName)
    }

    @Test
    fun `在途件先无运单号入列 - 后续补运单号只更新同一行`() {
        val first = item(
            code = "", kind = CodeKind.NONE, point = null, tracking = "",
            stage = Stage.IN_TRANSIT, goodsTitle = "示例进口薄荷糖清凉润喉",
        )
        val insert = planAccountSync(listOf(first), emptyList()).single()
        assertTrue(insert is AccountSyncAction.Insert)

        val row = parcel(
            id = 88, code = "", pointName = "", tracking = null,
            stage = Stage.IN_TRANSIT, source = Source.ACCOUNT,
            orderSn = first.orderSn, accountId = "pdd",
        )
        val update = planAccountSync(
            listOf(first.copy(trackingNo = "79040000020486")),
            listOf(row),
        ).single()
        assertTrue(update is AccountSyncAction.Update)
        assertEquals(88L, (update as AccountSyncAction.Update).id)
        assertEquals("79040000020486", update.item.trackingNo)
    }

    @Test
    fun `连订单号都没有 - 才允许丢弃`() {
        val noIdentity = planAccountSync(
            listOf(item(point = null, tracking = "", code = "", kind = CodeKind.NONE, orderSn = "")),
            emptyList(),
        ).single()
        assertEquals(SkipReason.NO_CODE, (noIdentity as AccountSyncAction.Skip).reason)
    }

    @Test
    fun `无码但有运单号 - 照样入首页，空点名归到驿站未定位`() {
        val action = planAccountSync(
            listOf(item(code = "", kind = CodeKind.NONE, point = null)),
            emptyList(),
        ).single()
        assertTrue(action is AccountSyncAction.Insert)
        val insert = action as AccountSyncAction.Insert
        assertEquals(UNLOCATED_POINT_NAME, insert.item.pointName)
        assertEquals(pointKey(UNLOCATED_POINT_NAME), insert.pointKey)
        assertEquals("465700000020382", insert.item.trackingNo)
    }

    @Test
    fun `无码退出但运单号相同 - 仍按运单号更新，不插第二张卡`() {
        val old = parcel(id = 30, code = "", tracking = "465700000020382")
        val action = planAccountSync(
            listOf(item(code = "", kind = CodeKind.NONE)),
            listOf(old),
        ).single()
        assertTrue(action is AccountSyncAction.Update)
        assertEquals(30L, (action as AccountSyncAction.Update).id)
        assertEquals("", (action as AccountSyncAction.Update).code)
        assertEquals(CodeKind.NONE, (action as AccountSyncAction.Update).codeKind)
    }

    @Test
    fun `无码件的不明确签收 - 不误标已取，但终态必须离开在途区`() {
        val old = parcel(id = 31, code = "", tracking = "465700000020382", stage = Stage.DELIVERING)
        val action = planAccountSync(
            listOf(item(code = "", kind = CodeKind.NONE, stage = Stage.DONE, explicit = false)),
            listOf(old),
        ).single() as AccountSyncAction.Update
        assertFalse(action.markPicked)
        assertEquals(Stage.DONE, action.stage)
    }

    @Test
    fun `退休行不是已取 - 平台再给待取时回到待取`() {
        val retired = parcel(
            id = 80,
            code = "20382",
            picked = false,
            tracking = "465700000020382",
            stage = Stage.DONE,
            source = Source.ACCOUNT,
            orderSn = "261015-456000000060634",
            accountId = "pdd",
            retired = true,
        )
        val action = planAccountSync(listOf(item(stage = Stage.AT_STATION)), listOf(retired))
        val update = action.single() as AccountSyncAction.Update
        assertEquals(80L, update.id)
        assertEquals(Stage.AT_STATION, update.item.stage)
        assertFalse(update.item.explicitPickup)
    }

    @Test
    fun `同后五位不同运单号 - 两件都保留，各自成行`() {
        val a = item(code = "76416", kind = CodeKind.SUFFIX, tracking = "465700000076416")
        val b = item(code = "76416", kind = CodeKind.SUFFIX, tracking = "790400000087641")
        val actions = planAccountSync(listOf(a, b), emptyList())
        assertEquals(2, actions.size)
        assertTrue(actions.all { it is AccountSyncAction.Insert })
        assertEquals(
            listOf("465700000076416", "790400000087641"),
            actions.map { it.item.trackingNo },
        )
    }

    @Test
    fun `同后五位不同运单号 - 库里已有其中一件时，另一件仍插新行`() {
        val old = parcel(id = 60, code = "76416", tracking = "465700000076416", source = Source.ACCOUNT)
        val a = item(code = "76416", kind = CodeKind.SUFFIX, tracking = "465700000076416")
        val b = item(code = "76416", kind = CodeKind.SUFFIX, tracking = "790400000087641")
        val actions = planAccountSync(listOf(a, b), listOf(old))
        assertTrue(actions[0] is AccountSyncAction.Update)
        assertEquals(60L, (actions[0] as AccountSyncAction.Update).id)
        assertTrue(actions[1] is AccountSyncAction.Insert)
    }

    @Test
    fun `同一运单号重复同步 - 只更新原行，不重复插入`() {
        val old = parcel(id = 61, code = "76416", tracking = "465700000076416", source = Source.ACCOUNT)
        val repeated = item(code = "76416", kind = CodeKind.SUFFIX, tracking = "465700000076416")
        val actions = planAccountSync(listOf(repeated, repeated.copy()), listOf(old))
        assertEquals(2, actions.size)
        actions.forEach {
            assertTrue(it is AccountSyncAction.Update)
            assertEquals(61L, (it as AccountSyncAction.Update).id)
        }
    }

    @Test
    fun `同码同点 - 更新短信行而不是插第二张卡`() {
        val old = parcel(id = 7, tracking = null)
        val action = planAccountSync(listOf(item()), listOf(old)).single()
        assertTrue(action is AccountSyncAction.Update)
        assertEquals(7L, (action as AccountSyncAction.Update).id)
        assertEquals("5-2-7712", action.code)
    }

    @Test
    fun `后五位码同码同点 - 不参与身份判定，短信行不被占用`() {
        /* 老行是短信件（没有运单号），后五位码与它相同、点位也相同。
           但后五位只用于展示，不能证明是同一件：必须插新行，不能把短信卡改写成账号件。 */
        val old = parcel(id = 9, code = "54819", pointName = "示例大学示例校区驿站", tracking = null)
        val action = planAccountSync(
            listOf(
                item(
                    code = "54819", kind = CodeKind.SUFFIX,
                    point = "示例大学示例校区驿站", tracking = "4657000000054819",
                ),
            ),
            listOf(old),
        ).single()
        assertTrue(action is AccountSyncAction.Insert)
        assertEquals("4657000000054819", action.item.trackingNo)
    }

    @Test
    fun `运单号相同 - 优先按运单号匹配`() {
        val old = parcel(id = 8, code = "old", tracking = "465700000020382")
        val action = planAccountSync(
            listOf(item(code = "new", tracking = "465700000020382")),
            listOf(old),
        ).single()
        assertTrue(action is AccountSyncAction.Update)
        assertEquals("old", (action as AccountSyncAction.Update).code)
    }

    @Test
    fun `同运单号合并 - 只更新原行，商品字段跟着带过去`() {
        val old = parcel(id = 50, tracking = "465700000020382", source = Source.ACCOUNT)
        val action = planAccountSync(
            listOf(item(goodsTitle = "示例竹浆手帕纸")),
            listOf(old),
        ).single() as AccountSyncAction.Update
        assertEquals(50L, action.id)
        assertEquals("示例竹浆手帕纸", action.item.goodsTitle)
    }

    @Test
    fun `已取行一个字段都不动`() {
        val old = parcel(id = 9, picked = true)
        val action = planAccountSync(listOf(item()), listOf(old)).single()
        assertEquals(SkipReason.ALREADY_PICKED, (action as AccountSyncAction.Skip).reason)
    }

    @Test
    fun `已取的账号件再同步仍 Skip - 不会复活`() {
        val old = parcel(id = 40, code = "", tracking = "79040000038478", picked = true)
        val action = planAccountSync(
            listOf(item(code = "", kind = CodeKind.NONE, tracking = "79040000038478")),
            listOf(old),
        ).single()
        assertEquals(SkipReason.ALREADY_PICKED, (action as AccountSyncAction.Skip).reason)
    }

    @Test
    fun `明确本人取件才自动标已取`() {
        val old = parcel(id = 10)
        val action = planAccountSync(
            listOf(item(stage = Stage.DONE, explicit = true)),
            listOf(old),
        ).single() as AccountSyncAction.Update
        assertTrue(action.markPicked)
    }

    @Test
    fun `签收人门口不标已取，但终态必须离开在途区`() {
        val old = parcel(id = 11, stage = Stage.DELIVERING, tracking = "465700000020382", source = Source.ACCOUNT)
        val action = planAccountSync(
            listOf(item(stage = Stage.DONE, explicit = false)),
            listOf(old),
        ).single() as AccountSyncAction.Update
        assertFalse(action.markPicked)
        assertEquals(Stage.DONE, action.stage)
    }

    @Test
    fun `旧派送中 - 新轨迹本人签收必须归档`() {
        val old = parcel(
            id = 70,
            code = "",
            tracking = "79040000038478",
            stage = Stage.DELIVERING,
            source = Source.ACCOUNT,
            accountId = "tb",
            orderSn = "000000-442000000020634",
        )
        val action = planAccountSync(
            listOf(
                item(
                    code = "", kind = CodeKind.NONE, point = null, tracking = "79040000038478",
                    stage = Stage.DONE, explicit = true,
                ),
            ),
            listOf(old),
        ).single() as AccountSyncAction.Update
        assertTrue(action.markPicked)
        assertEquals(Stage.DONE, action.stage)
    }

    @Test
    fun `旧派送中 - 新轨迹是驿站代收仍进待取`() {
        val old = parcel(
            id = 71,
            code = "",
            tracking = "79040000076061",
            stage = Stage.DELIVERING,
            source = Source.ACCOUNT,
            accountId = "tb",
            orderSn = "000000-585500000080634",
        )
        val action = planAccountSync(
            listOf(
                item(
                    code = "54819", kind = CodeKind.SUFFIX, point = "示例大学示例校区驿站",
                    tracking = "79040000076061", stage = Stage.AT_STATION, explicit = false,
                ),
            ),
            listOf(old),
        ).single() as AccountSyncAction.Update
        assertEquals(Stage.AT_STATION, action.stage)
        assertFalse(action.markPicked)
    }

    @Test
    fun `状态只升不降 - 到站后又被运输中覆盖是不允许的`() {
        val old = parcel(id = 12, stage = Stage.AT_STATION)
        val action = planAccountSync(
            listOf(item(stage = Stage.DELIVERING)),
            listOf(old),
        ).single() as AccountSyncAction.Update
        assertEquals(Stage.AT_STATION, action.stage)
    }

    @Test
    fun `账号行点名被清洗后 - pointKey 跟着换，不裂成两张卡`() {
        val old = parcel(
            id = 21,
            pointName = "示例大学示例校区菜鸟驿站驿站",
            source = Source.ACCOUNT,
            tracking = "465700000076416",
        )
        val action = planAccountSync(
            listOf(item(point = "示例大学示例校区驿站", tracking = "465700000076416", code = "76416")),
            listOf(old),
        ).single() as AccountSyncAction.Update
        assertEquals(pointKey("示例大学示例校区驿站"), action.pointKey)
        assertEquals("示例大学示例校区驿站", action.pointName)
    }

    @Test
    fun `再同步时点名解析为空 - 保留库内点名，不清空不换key`() {
        val old = parcel(
            id = 31,
            pointName = "示例大学示例校区菜鸟驿站",
            source = Source.ACCOUNT,
            tracking = "465700000020383",
        )
        val action = planAccountSync(
            listOf(
                item(
                    point = null,
                    tracking = "465700000020383",
                    code = "",
                    kind = CodeKind.NONE,
                ),
            ),
            listOf(old),
        ).single() as AccountSyncAction.Update
        assertEquals("示例大学示例校区菜鸟驿站", action.pointName)
        assertEquals(pointKey("示例大学示例校区菜鸟驿站"), action.pointKey)
    }

    @Test
    fun `不明确已签收但有码有点 - 插入为到站件而不是消失`() {
        val action = planAccountSync(
            listOf(item(stage = Stage.DONE, explicit = false)),
            emptyList(),
        ).single()
        assertTrue(action is AccountSyncAction.Insert)
    }

    @Test
    fun `没有匹配的运输中件 - 进在途区且不占待取`() {
        val action = planAccountSync(
            listOf(item(stage = Stage.IN_TRANSIT, point = null, code = "", kind = CodeKind.NONE)),
            emptyList(),
        ).single()
        assertTrue(action is AccountSyncAction.Insert)
        val insert = action as AccountSyncAction.Insert
        assertEquals(Stage.IN_TRANSIT, insert.item.stage)
        assertEquals("", insert.pointKey)
        assertEquals("", insert.item.pointName)
    }

    @Test
    fun `历史单口径 - 只有待收货与在途状态才是候选`() {
        assertTrue(isActiveOrderPrompt("待收货"))
        assertTrue(isActiveOrderPrompt("运输中"))
        assertTrue(isActiveOrderPrompt("待取件"))
        assertFalse(isActiveOrderPrompt("待评价"))
        assertFalse(isActiveOrderPrompt("交易成功"))

        assertTrue(isFinishedOrderPrompt("已签收"))
        assertTrue(isFinishedOrderPrompt("待评价"))
        assertTrue(isFinishedOrderPrompt("交易成功"))
        assertFalse(isFinishedOrderPrompt("待取件"))

        assertTrue(isTerminalTrackStatus("OUT_CABINET"))
        assertTrue(isTerminalTrackStatus("SIGNED"))
        assertFalse(isTerminalTrackStatus("IN_CABINET"))
        assertFalse(isTerminalTrackStatus("DEPARTURE"))
    }

    @Test
    fun `订单状态交叉校验 - 取消关闭退款售后一律不许进在途`() {
        assertTrue(conflictsWithOrderStatus("000000-651500000000634", mapOf("000000-651500000000634" to "已取消")))
        assertTrue(conflictsWithOrderStatus("x", mapOf("x" to "交易关闭")))
        assertTrue(conflictsWithOrderStatus("x", mapOf("x" to "退款成功")))
        assertTrue(conflictsWithOrderStatus("x", mapOf("x" to "退款中")))
        assertTrue(conflictsWithOrderStatus("x", mapOf("x" to "售后完成")))
        assertTrue(conflictsWithOrderStatus("x", mapOf("x" to "已签收")))
        assertFalse(conflictsWithOrderStatus("x", mapOf("x" to "待收货")))
        assertFalse(conflictsWithOrderStatus("x", mapOf("x" to "运输中")))
    }

    @Test
    fun `待收货接口里的单没有订单状态记录 - 不被否决`() {
        // A/B 只在 express/track/status 里出现，订单列表没有它们；这是合法来源。
        assertFalse(conflictsWithOrderStatus("000000-442000000020634", emptyMap()))
    }

    @Test
    fun `退款处理中不伪装成运输 - 兜底阶段也不认它`() {
        assertNull(
            fallbackFetchedParcel(
                platform = Platform.PDD,
                accountId = "pdd",
                orderSn = "261030-047900000000634",
                prompt = "退款中",
                goods = null,
                at = 1_000,
            ),
        )
    }

    @Test
    fun `残留轨迹 - 上次进来的在途件这次两处都不见才退休`() {
        val stale = parcel(
            stage = Stage.IN_TRANSIT,
            source = Source.ACCOUNT,
            orderSn = "000000-651500000000634",
            accountId = "pdd",
        )
        assertTrue(
            "这次只见到 A/B，公牛不在任何接口里，应退休",
            isStaleTransitRow(stale, "pdd", setOf("000000-442000000020634", "000000-585500000080634")),
        )
        assertFalse("本次仍在待收货列表里，不退休", isStaleTransitRow(stale, "pdd", setOf("000000-651500000000634")))
        assertFalse(
            "到站件哪怕这次没出现在列表，也可能是真没取的件，不能退休",
            isStaleTransitRow(stale.copy(stage = Stage.AT_STATION), "pdd", emptySet()),
        )
        assertFalse("用户手动行不动", isStaleTransitRow(stale.copy(source = Source.MANUAL), "pdd", emptySet()))
        assertFalse("已取行不动", isStaleTransitRow(stale.copy(picked = true), "pdd", emptySet()))
        assertFalse("别的账号的行不动", isStaleTransitRow(stale, "tb", emptySet()))
    }

    @Test
    fun `详情抓取失败 - 用列表阶段先入列，不丢件`() {
        val a = fallbackFetchedParcel(
            platform = Platform.PDD,
            accountId = "pdd",
            orderSn = "000000-442000000020634",
            prompt = "待取件",
            goods = null,
            at = 1_000,
        )!!
        assertEquals(Stage.AT_STATION, a.stage)
        assertEquals("000000-442000000020634", a.orderSn)
        assertEquals("", a.trackingNo)

        val b = fallbackFetchedParcel(
            platform = Platform.PDD,
            accountId = "pdd",
            orderSn = "000000-585500000080634",
            prompt = "运输中",
            goods = GoodsInfo(title = "和泓食品", image = null, price = null, shop = "和泓食品", orderedAt = null),
            at = 1_000,
        )!!
        assertEquals(Stage.IN_TRANSIT, b.stage)
        assertEquals("和泓食品", b.goodsTitle)

        // 兜底件仍然带着订单号身份，planAccountSync 不许把它当成「什么都认不出」丢掉。
        val actions = planAccountSync(listOf(a), emptyList())
        assertEquals(1, actions.size)
        assertTrue(actions.single() is AccountSyncAction.Insert)
    }
}
