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

class DetailGateTest {

    private val now = 10_000_000L
    private val sn = "261015-456000000060634"

    private fun parcel(
        code: String = "5-2-7712",
        tracking: String? = "465700000020382",
        stage: Stage = Stage.AT_STATION,
        picked: Boolean = false,
        retired: Boolean = false,
        lastSyncedAt: Long? = now - 60_000,
        orderSn: String = "261015-456000000060634",
    ) = Parcel(
        id = 1,
        code = code,
        lockerNo = null,
        carrier = "中通快递",
        pointKey = pointKey("菜鸟驿站·东门"),
        pointName = "菜鸟驿站·东门",
        pointKind = PointKind.STATION,
        owner = Owner.SELF,
        source = Source.ACCOUNT,
        receivedAt = 900,
        freeUntilAt = null,
        closesAtMinuteOfDay = null,
        picked = picked,
        pickedAt = null,
        raw = "",
        trackingNo = tracking,
        orderSn = orderSn,
        accountId = "pdd",
        stage = stage,
        codeKind = CodeKind.PICKUP,
        lastSyncedAt = lastSyncedAt,
        retired = retired,
    )

    private fun known(vararg rows: Parcel): Map<String, Parcel> =
        rows.associateBy { it.orderSn.orEmpty() }

    @Test
    fun `库里没有这一单 - 必须抓详情`() {
        assertTrue(detailNeeded(sn, Stage.IN_TRANSIT, emptyMap(), now))
    }

    @Test
    fun `已取的件 - 详情页没有任何用处`() {
        assertFalse(detailNeeded(sn, Stage.AT_STATION, known(parcel(picked = true)), now))
    }

    @Test
    fun `退休行重新出现 - 必须重抓才能复活`() {
        assertTrue(detailNeeded(sn, Stage.AT_STATION, known(parcel(retired = true)), now))
    }

    @Test
    fun `没有运单号 - 详情是唯一来源`() {
        assertTrue(detailNeeded(sn, Stage.IN_TRANSIT, known(parcel(tracking = null)), now))
    }

    @Test
    fun `到站却没码 - 照旧抓`() {
        assertTrue(
            detailNeeded(sn, Stage.AT_STATION, known(parcel(code = "")), now),
        )
    }

    @Test
    fun `在途没码是正常的 - 状态没变就跳过`() {
        assertFalse(
            detailNeeded(
                sn,
                Stage.IN_TRANSIT,
                known(parcel(code = "", stage = Stage.IN_TRANSIT)),
                now,
            ),
        )
    }

    @Test
    fun `列表说阶段变了 - 必须抓`() {
        assertTrue(
            detailNeeded(sn, Stage.AT_STATION, known(parcel(stage = Stage.IN_TRANSIT)), now),
        )
    }

    @Test
    fun `列表没有状态 - 阶段无从比较，只按时间自愈`() {
        assertFalse(detailNeeded(sn, null, known(parcel()), now))
        assertTrue(
            detailNeeded(
                sn,
                null,
                known(parcel(lastSyncedAt = now - DETAIL_REUSE_MS)),
                now,
            ),
        )
    }

    @Test
    fun `上次详情太旧 - 即使状态没变也重抓一次`() {
        assertTrue(
            detailNeeded(
                sn,
                Stage.AT_STATION,
                known(parcel(lastSyncedAt = now - DETAIL_REUSE_MS - 1)),
                now,
            ),
        )
    }

    @Test
    fun `没有上次同步时间 - 当作不知道，抓`() {
        assertTrue(detailNeeded(sn, Stage.AT_STATION, known(parcel(lastSyncedAt = null)), now))
    }

    @Test
    fun `列表文案到阶段 - 认不出就返回 null，不拿它下结论`() {
        assertEquals(Stage.AT_STATION, stageOfPrompt("待取件"))
        assertEquals(Stage.AT_STATION, stageOfPrompt("已到达驿站"))
        assertEquals(Stage.DELIVERING, stageOfPrompt("派送中"))
        assertEquals(Stage.IN_TRANSIT, stageOfPrompt("运输中"))
        assertEquals(Stage.IN_TRANSIT, stageOfPrompt("已发货"))
        assertNull(stageOfPrompt("待评价"))
        assertNull(stageOfPrompt(""))
    }
}
