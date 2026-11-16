package dev.showcode.core.account

import dev.showcode.core.CodeKind
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import dev.showcode.core.Stage
import dev.showcode.core.pointKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提前落库与整批应用共用同一个纯函数。这里钉住两件事：
 * 同一件再规划一次必须是 Update（不是再插一张），以及已取的行不会再被写。
 */
class EarlyWritePlanTest {

    private val item = FetchedParcel(
        platform = Platform.PDD,
        accountId = "pdd",
        trackingNo = "465700000020382",
        orderSn = "261015-456000000060634",
        carrier = "中通快递",
        stage = Stage.AT_STATION,
        pointName = "菜鸟驿站·东门",
        lockerNo = null,
        code = "5-2-7712",
        codeKind = CodeKind.PICKUP,
        explicitPickup = false,
        raw = "轨迹",
        at = 1_000,
    )

    private fun row(picked: Boolean = false) = Parcel(
        id = 7,
        code = "5-2-7712",
        lockerNo = null,
        carrier = "中通快递",
        pointKey = pointKey("菜鸟驿站·东门"),
        pointName = "菜鸟驿站·东门",
        pointKind = PointKind.STATION,
        owner = Owner.SELF,
        source = Source.ACCOUNT,
        receivedAt = 1_000,
        freeUntilAt = null,
        closesAtMinuteOfDay = null,
        picked = picked,
        pickedAt = null,
        raw = "轨迹",
        trackingNo = "465700000020382",
        orderSn = "261015-456000000060634",
        accountId = "pdd",
        stage = Stage.AT_STATION,
        codeKind = CodeKind.PICKUP,
        lastSyncedAt = 2_000,
    )

    @Test
    fun `同一件再规划一次是更新而不是再插一张`() {
        val action = planAccountSync(listOf(item), listOf(row())).single()
        assertTrue(action is AccountSyncAction.Update)
        assertEquals(7L, (action as AccountSyncAction.Update).id)
    }

    @Test
    fun `用户已经点过已取 - 不再写`() {
        val action = planAccountSync(listOf(item), listOf(row(picked = true))).single()
        assertEquals(
            SkipReason.ALREADY_PICKED,
            (action as AccountSyncAction.Skip).reason,
        )
    }

    @Test
    fun `库里没有这件 - 第一次是插入`() {
        val action = planAccountSync(listOf(item), emptyList()).single()
        assertTrue(action is AccountSyncAction.Insert)
    }
}
