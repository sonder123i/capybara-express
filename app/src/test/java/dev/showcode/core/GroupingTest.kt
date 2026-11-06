package dev.showcode.core

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupingTest {

    /** 固定为今天 15:00，这样「21:00 关门」是安全、「16:00 关门」是临期。 */
    private val now = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 15)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun parcel(
        id: Long,
        code: String,
        pointKey: String,
        pointName: String,
        kind: PointKind = PointKind.STATION,
        closesAtMinuteOfDay: Int? = 21 * 60,
        freeUntilAt: Long? = null,
        receivedAt: Long = now,
        picked: Boolean = false,
        codeKind: CodeKind = CodeKind.PICKUP,
        trackingNo: String? = null,
        stage: Stage = Stage.AT_STATION,
    ) = Parcel(
        id = id, code = code, lockerNo = null, carrier = "中通快递",
        pointKey = pointKey, pointName = pointName, pointKind = kind,
        owner = Owner.SELF, source = Source.SMS, receivedAt = receivedAt,
        freeUntilAt = freeUntilAt, closesAtMinuteOfDay = closesAtMinuteOfDay,
        picked = picked, pickedAt = null, raw = "",
        trackingNo = trackingNo, codeKind = codeKind, stage = stage,
    )

    private fun atRiskLocker(id: Long) = parcel(
        id = id, code = "8-3-1024", pointKey = "fc3", pointName = "丰巢柜·3号楼",
        kind = PointKind.LOCKER, closesAtMinuteOfDay = null, freeUntilAt = now - HOUR_MS,
    )

    private fun safeStation(id: Long, receivedAt: Long = now, picked: Boolean = false) = parcel(
        id = id, code = "5-2-339$id", pointKey = "cndm", pointName = "菜鸟驿站·东门",
        receivedAt = receivedAt, picked = picked,
    )

    private fun dueSoonStation(id: Long, receivedAt: Long = now) = parcel(
        id = id, code = "5-1-227$id", pointKey = "cndm", pointName = "菜鸟驿站·东门",
        closesAtMinuteOfDay = 16 * 60, receivedAt = receivedAt,
    )

    /**
     * Story 2 AC3 的核心回归：紧急度优先，件数只是次级排序。
     * 数据集刻意做成「1 件过期风险」对「3 件安全」——按件数排会得到相反序列，
     * 所以这个断言能抓住「排序实现反了」这类 bug（mockup 的 Check finding M-r2-1）。
     */
    @Test
    fun `取件点按紧急度排序 - 1件过期风险排在3件安全前面`() {
        val groups = groupByPoint(
            listOf(safeStation(1), safeStation(2), safeStation(3), atRiskLocker(4)),
            now,
        )
        assertEquals(listOf("fc3", "cndm"), groups.map { it.key })
        assertEquals(Urgency.AT_RISK, groups[0].worst)
        assertEquals(1, groups[0].items.size)
        assertEquals(3, groups[1].items.size)
    }

    @Test
    fun `同档紧急度时件数多的点排前面`() {
        val few = parcel(id = 1, code = "A1", pointKey = "a", pointName = "A点")
        val many1 = parcel(id = 2, code = "B1", pointKey = "b", pointName = "B点")
        val many2 = parcel(id = 3, code = "B2", pointKey = "b", pointName = "B点")
        val groups = groupByPoint(listOf(few, many1, many2), now)
        assertEquals(listOf("b", "a"), groups.map { it.key })
    }

    @Test
    fun `卡片内也按紧急度排序 - 不是最新到达的排最前`() {
        val groups = groupByPoint(
            listOf(
                safeStation(1, receivedAt = now - 3 * HOUR_MS),
                dueSoonStation(2, receivedAt = now),          // 最新到达，但最急
                safeStation(3, receivedAt = now - 5 * HOUR_MS),
            ),
            now,
        )
        assertEquals(listOf(2L, 3L, 1L), groups.single().items.map { it.id })
        assertEquals(Urgency.DUE_SOON, groups.single().worst)
    }

    @Test
    fun `同一档里放得最久的排前面`() {
        val groups = groupByPoint(
            listOf(
                safeStation(1, receivedAt = now - 1 * HOUR_MS),
                safeStation(2, receivedAt = now - 9 * HOUR_MS),
            ),
            now,
        )
        assertEquals(listOf(2L, 1L), groups.single().items.map { it.id })
    }

    @Test
    fun `已取的件不出现在任何分组里 - 取完的点整张卡片消失`() {
        val groups = groupByPoint(
            listOf(safeStation(1), safeStation(2, picked = true)),
            now,
        )
        assertEquals(1, groups.single().items.size)

        val allPicked = groupByPoint(listOf(safeStation(1, picked = true)), now)
        assertTrue(allPicked.isEmpty())
    }

    @Test
    fun `同点两件取走一件 - 卡片件数立刻从 2 变 1`() {
        val waiting = groupByPoint(listOf(safeStation(1), safeStation(2)), now)
        assertEquals(2, waiting.single().items.size)

        val afterOne = groupByPoint(listOf(safeStation(1), safeStation(2, picked = true)), now)
        assertEquals(1, afterOne.single().items.size)
    }

    @Test
    fun `卡片数只等于有在站件的点数`() {
        val groups = groupByPoint(
            listOf(safeStation(1), safeStation(2, picked = true), atRiskLocker(3)),
            now,
        )
        assertEquals(2, groups.size)
        assertEquals(setOf("cndm", "fc3"), groups.map { it.key }.toSet())
    }

    @Test
    fun `一个点全部取完 - 它不再贡献任何卡片`() {
        val groups = groupByPoint(
            listOf(
                safeStation(1, picked = true),
                safeStation(2, picked = true),
                atRiskLocker(3),
            ),
            now,
        )
        assertEquals(listOf("fc3"), groups.map { it.key })
    }

    @Test
    fun `已取的无码件也不进首页 - 不会因为有运单号又冒出来`() {
        val groups = groupByPoint(
            listOf(
                parcel(
                    id = 3, code = "", pointKey = "cndm", pointName = "菜鸟驿站·东门",
                    codeKind = CodeKind.NONE, trackingNo = "79040000038478",
                    picked = true, receivedAt = now,
                ),
            ),
            now,
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `无码但有运单号 - 照样进首页分组`() {
        val groups = groupByPoint(
            listOf(
                parcel(
                    id = 1, code = "", pointKey = "cndm", pointName = "菜鸟驿站·东门",
                    codeKind = CodeKind.NONE, trackingNo = "79040000038478",
                ),
            ),
            now,
        )
        assertEquals(1, groups.single().items.size)
        assertEquals("79040000038478", groups.single().items.single().trackingNo)
    }

    @Test
    fun `既无码也无运单号 - 不进首页`() {
        val groups = groupByPoint(
            listOf(
                parcel(
                    id = 2, code = "", pointKey = "cndm", pointName = "菜鸟驿站·东门",
                    codeKind = CodeKind.NONE, trackingNo = null,
                ),
            ),
            now,
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `在途件不进待取分组 - 即使残留点名也不占待取计数`() {
        val groups = groupByPoint(
            listOf(
                parcel(id = 1, code = "A1", pointKey = "cndm", pointName = "菜鸟驿站·东门"),
                parcel(
                    id = 2, code = "", pointKey = "cndm", pointName = "菜鸟驿站·东门",
                    codeKind = CodeKind.NONE, trackingNo = "777400000060701",
                    stage = Stage.IN_TRANSIT,
                ),
            ),
            now,
        )
        assertEquals(1, groups.single().items.size)
        assertEquals(1L, groups.single().items.single().id)
    }

    @Test
    fun `空点名的到站件也不进待取分组 - 它只出现在在途待补区`() {
        val groups = groupByPoint(
            listOf(
                parcel(
                    id = 5, code = "", pointKey = "", pointName = "",
                    codeKind = CodeKind.NONE, trackingNo = "YT0724000054819",
                ),
            ),
            now,
        )
        assertTrue(groups.isEmpty())
    }

    // ── 保守归一化（frame 锁定：不自动合并任何名称不同的取件点） ──

    @Test
    fun `同一个名字的全角半角写法归一为一个点`() {
        assertEquals(
            pointKey("菜鸟驿站（阳光花园小区东门店）"),
            pointKey("菜鸟驿站(阳光花园小区东门店)"),
        )
    }

    @Test
    fun `大小写、空格、分隔符差异归一为一个点`() {
        assertEquals(pointKey("京东自提点 B-4"), pointKey("京东自提点b4"))
        assertEquals(pointKey("丰巢柜·3号楼"), pointKey("丰巢柜-3-号楼"))
    }

    @Test
    fun `名称不同的点绝不自动合并 - 宁可多一张卡片`() {
        // 这是已接受的代价：现场模式下「码不见了」是灾难性失败，「多了一张卡片」只是难看。
        assertNotEquals(
            pointKey("菜鸟驿站(东门店)"),
            pointKey("菜鸟驿站(阳光花园小区东门店)"),
        )
        assertNotEquals(pointKey("XX便利店"), pointKey("菜鸟驿站(XX小区店)"))
    }

    @Test
    fun `归一化后仍然保留可显示的原名`() {
        val groups = groupByPoint(
            listOf(parcel(id = 1, code = "A1", pointKey = pointKey("菜鸟驿站(东门店)"), pointName = "菜鸟驿站(东门店)")),
            now,
        )
        assertEquals("菜鸟驿站(东门店)", groups.single().name)
    }
}
