package dev.showcode.data

import dev.showcode.core.CodeKind
import dev.showcode.core.groupByPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 宣传片录制数据集的回归：顺序、件数、码形态、图片 key 都必须固定。
 * 这些断言一旦红了，说明演示里会出现「这次和上次不一样」的画面。
 */
class DemoDataTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `同一锚点生成两次完全一致`() {
        val first = DemoData.parcels(now)
        val second = DemoData.parcels(now)
        assertEquals(first, second)
        assertEquals(6, first.size)
        assertTrue(first.none { it.picked })
    }

    @Test
    fun `首页分组与排序固定 丰巢2 菜鸟3 京东1`() {
        val groups = groupByPoint(DemoData.parcels(now), now)
        assertEquals(listOf(2, 3, 1), groups.map { it.items.size })
        assertEquals(
            listOf("丰巢快递柜(3号楼下)", "菜鸟驿站(阳光花园小区东门店)", "京东自提点(好邻居便利店)"),
            groups.map { it.name },
        )
        // 丰巢第一件必须是超时件，菜鸟第一件必须是 PICKUP，京东第一件必须是 NONE。
        assertEquals("8-3-1024", groups[0].items.first().code)
        assertEquals(CodeKind.PICKUP, groups[1].items.first().codeKind)
        assertEquals(CodeKind.NONE, groups[2].items.first().codeKind)
    }

    @Test
    fun `三件码形态齐全且后五位带完整运单号`() {
        val parcels = DemoData.parcels(now)
        assertTrue(parcels.any { it.codeKind == CodeKind.PICKUP })
        assertTrue(parcels.any { it.codeKind == CodeKind.SUFFIX })
        assertTrue(parcels.any { it.codeKind == CodeKind.NONE })
        parcels.filter { it.codeKind == CodeKind.SUFFIX }.forEach {
            assertTrue(it.code.isNotBlank())
            assertTrue(!it.trackingNo.isNullOrBlank())
        }
        parcels.filter { it.codeKind == CodeKind.NONE }.forEach {
            assertTrue(it.code.isBlank())
            assertTrue(!it.trackingNo.isNullOrBlank())
        }
    }

    @Test
    fun `每一件都有本地商品图和商品信息`() {
        DemoData.parcels(now).forEach { parcel ->
            assertTrue(parcel.goodsImage.orEmpty().startsWith("demo:"))
            assertTrue(!parcel.goodsTitle.isNullOrBlank())
            assertTrue(!parcel.goodsPrice.isNullOrBlank())
            assertTrue(!parcel.shopName.isNullOrBlank())
            assertTrue(!parcel.accountId.isNullOrBlank())
        }
    }

    @Test
    fun `演示账号固定两条且都是已绑定`() {
        val accounts = DemoData.accounts(now)
        assertEquals(2, accounts.size)
        assertTrue(accounts.all { it.enabled && it.secret.isBlank() })
    }
}
