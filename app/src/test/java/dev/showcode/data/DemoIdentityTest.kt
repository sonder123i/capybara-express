package dev.showcode.data

import dev.showcode.core.IdentityCode
import dev.showcode.core.IdentityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 演示身份码的三个写操作只改演示列表。
 * 这里另放一条「真实码」做哨兵：演示分支连它的引用都拿不到，更不可能写库。
 */
class DemoIdentityTest {

    private val real = listOf(
        IdentityCode(id = 1, label = "真实门禁码", value = "1234 5678", createdAt = 100L),
    )

    @Test
    fun seedUsesNegativeIdsSoNoRealRowCollides() {
        val demo = DemoIdentity.seed(now = 1_000L)
        assertTrue(demo.isNotEmpty())
        assertTrue(demo.all { it.id < 0 })
    }

    @Test
    fun addTogglesAndDeleteOnlyTouchDemoList() {
        val demo = DemoIdentity.seed(now = 1_000L).toMutableList()
        val beforeReal = real.map { it.copy() }

        assertTrue(
            DemoIdentity.add(
                codes = demo,
                label = "公司门禁",
                value = "9001 2233",
                kind = IdentityKind.CODE,
                dynamic = false,
                note = null,
                appPackage = null,
                now = 1_000L,
            ),
        )
        assertEquals(3, demo.size)

        val target = demo.first { it.label == "小区门禁码" }
        DemoIdentity.setDynamic(demo, target.id, true)
        assertTrue(demo.first { it.id == target.id }.dynamic)

        DemoIdentity.delete(demo, target.id)
        assertEquals(2, demo.size)
        assertTrue(demo.none { it.id == target.id })

        assertEquals(beforeReal, real)
    }

    @Test
    fun addWithoutDynamicChoiceIsRejected() {
        val demo = DemoIdentity.seed(now = 1_000L).toMutableList()
        assertTrue(
            !DemoIdentity.add(
                codes = demo,
                label = "临时码",
                value = "7777",
                kind = IdentityKind.CODE,
                dynamic = null,
                note = null,
                appPackage = null,
                now = 1_000L,
            ),
        )
        assertEquals(2, demo.size)
    }
}
