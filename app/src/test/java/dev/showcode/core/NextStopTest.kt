package dev.showcode.core

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextStopTest {

    /** 固定为今天 15:00：21:00 关门是安全，16:00 关门是临期。 */
    private val now = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 15)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun parcel(
        id: Long,
        pointKey: String,
        pointName: String,
        kind: PointKind = PointKind.STATION,
        closesAtMinuteOfDay: Int? = 21 * 60,
        freeUntilAt: Long? = null,
    ) = Parcel(
        id = id, code = "5-2-339$id", lockerNo = null, carrier = "中通快递",
        pointKey = pointKey, pointName = pointName, pointKind = kind,
        owner = Owner.SELF, source = Source.SMS, receivedAt = now,
        freeUntilAt = freeUntilAt, closesAtMinuteOfDay = closesAtMinuteOfDay,
        picked = false, pickedAt = null, raw = "",
    )

    @Test
    fun `下一站是最急的另一个点 - 不是件数最多的那个`() {
        val groups = groupByPoint(
            listOf(
                // 安全但 3 件
                parcel(1, "safe", "菜鸟驿站·东门"),
                parcel(2, "safe", "菜鸟驿站·东门"),
                parcel(3, "safe", "菜鸟驿站·东门"),
                // 临期但 1 件
                parcel(
                    4, "due", "丰巢柜·3号楼",
                    kind = PointKind.LOCKER, closesAtMinuteOfDay = null,
                    freeUntilAt = now + 2 * HOUR_MS,
                ),
            ),
            now,
        )
        val next = nextStopAfter("here", groups, now)
        assertEquals("due", next?.key)
        assertEquals("丰巢柜·3号楼", next?.name)
        assertEquals(1, next?.count)
    }

    @Test
    fun `当前点不进下一站`() {
        val groups = groupByPoint(
            listOf(parcel(1, "a", "A点"), parcel(2, "b", "B点")),
            now,
        )
        assertEquals("b", nextStopAfter("a", groups, now)?.key)
    }

    @Test
    fun `空组不进下一站`() {
        val empty = PickupPointGroup(
            key = "empty", name = "空点", kind = PointKind.STATION,
            closesAtMinuteOfDay = null, items = emptyList(), worst = Urgency.SAFE,
        )
        val groups = listOf(empty) + groupByPoint(listOf(parcel(1, "b", "B点")), now)
        assertEquals("b", nextStopAfter("a", groups, now)?.key)
    }

    @Test
    fun `没有别的点时返回 null`() {
        val groups = groupByPoint(listOf(parcel(1, "a", "A点")), now)
        assertNull(nextStopAfter("a", groups, now))
    }

    @Test
    fun `件数是该点的待取件数`() {
        val groups = groupByPoint(
            listOf(parcel(1, "b", "B点"), parcel(2, "b", "B点"), parcel(3, "a", "A点")),
            now,
        )
        assertEquals(2, nextStopAfter("a", groups, now)?.count)
    }

    @Test
    fun `截止时刻取该点最急的那件 - 不是最晚到的那件`() {
        val soon = now + 3 * HOUR_MS
        val groups = groupByPoint(
            listOf(
                parcel(
                    1, "b", "B点", kind = PointKind.LOCKER,
                    closesAtMinuteOfDay = null, freeUntilAt = soon,
                ),
                parcel(
                    2, "b", "B点", kind = PointKind.LOCKER,
                    closesAtMinuteOfDay = null, freeUntilAt = now + 10 * HOUR_MS,
                ),
                parcel(3, "a", "A点"),
            ),
            now,
        )
        assertEquals(soon, nextStopAfter("a", groups, now)?.deadlineAt)
    }

    @Test
    fun `没有截止依据时不编造时刻`() {
        val groups = groupByPoint(
            listOf(parcel(1, "b", "B点", closesAtMinuteOfDay = null)),
            now,
        )
        assertNull(nextStopAfter("a", groups, now)?.deadlineAt)
    }
}
