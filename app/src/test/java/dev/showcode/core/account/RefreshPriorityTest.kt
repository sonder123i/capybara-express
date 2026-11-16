package dev.showcode.core.account

import dev.showcode.core.CodeKind
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import dev.showcode.core.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPriorityTest {

    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun account(
        platform: Platform,
        state: AccountState = AccountState.ACTIVE,
        lastSyncAt: Long? = now - day,
    ) = BoundAccount(
        id = platform.id,
        platform = platform,
        label = platform.label,
        secret = "secret",
        state = state,
        createdAt = 0,
        lastSyncAt = lastSyncAt,
    )

    private fun parcel(
        id: Long,
        account: Platform,
        stage: Stage = Stage.AT_STATION,
        receivedAt: Long = now - day,
        picked: Boolean = false,
        retired: Boolean = false,
    ) = Parcel(
        id = id,
        code = "5-2-771$id",
        lockerNo = null,
        carrier = "中通快递",
        pointKey = "p$id",
        pointName = "驿站$id",
        pointKind = PointKind.STATION,
        owner = Owner.SELF,
        source = Source.ACCOUNT,
        receivedAt = receivedAt,
        freeUntilAt = null,
        closesAtMinuteOfDay = null,
        picked = picked,
        pickedAt = null,
        raw = "",
        trackingNo = "SF$id",
        orderSn = "SN$id",
        accountId = account.id,
        stage = stage,
        codeKind = CodeKind.PICKUP,
        retired = retired,
    )

    @Test
    fun `到站件多的平台排第一`() {
        val pdd = account(Platform.PDD)
        val jd = account(Platform.JD)
        val tb = account(Platform.TB)
        val parcels = listOf(
            parcel(1, Platform.PDD),
            parcel(2, Platform.PDD),
            parcel(3, Platform.PDD),
            parcel(4, Platform.JD),
            parcel(5, Platform.TB, stage = Stage.IN_TRANSIT),
        )
        val order = orderAccountsForRefresh(listOf(tb, jd, pdd), parcels, now)
        assertEquals(listOf(Platform.PDD, Platform.JD, Platform.TB), order.map { it.platform })
    }

    @Test
    fun `全部取完后由近期收件频率决定`() {
        val pdd = account(Platform.PDD, lastSyncAt = now - day)
        val jd = account(Platform.JD, lastSyncAt = now - day)
        val parcels = listOf(
            parcel(1, Platform.PDD, receivedAt = now - day, picked = true),
            parcel(2, Platform.PDD, receivedAt = now - 2 * day, picked = true),
            parcel(3, Platform.JD, receivedAt = now - 30 * day, picked = true),
        )
        val order = orderAccountsForRefresh(listOf(jd, pdd), parcels, now)
        assertEquals(Platform.PDD, order.first().platform)
    }

    @Test
    fun `失效账号排最后 - 哪怕待取件更多`() {
        val pdd = account(Platform.PDD, state = AccountState.EXPIRED)
        val jd = account(Platform.JD)
        val parcels = listOf(
            parcel(1, Platform.PDD),
            parcel(2, Platform.PDD),
            parcel(3, Platform.PDD),
            parcel(4, Platform.PDD),
            parcel(5, Platform.PDD),
        )
        val order = orderAccountsForRefresh(listOf(pdd, jd), parcels, now)
        assertEquals(Platform.JD, order.first().platform)
        assertEquals(Platform.PDD, order.last().platform)
    }

    @Test
    fun `冷启动没有历史 - 顺序确定且是全排列`() {
        val accounts = listOf(account(Platform.PDD), account(Platform.JD), account(Platform.TB))
        val first = orderAccountsForRefresh(accounts, emptyList(), now)
        val second = orderAccountsForRefresh(accounts, emptyList(), now)
        assertEquals(first, second)
        assertEquals(accounts.map { it.id }.toSet(), first.map { it.id }.toSet())
        assertEquals(accounts.size, first.size)
    }

    @Test
    fun `单账号原样返回`() {
        val only = account(Platform.PDD)
        val order = orderAccountsForRefresh(listOf(only), emptyList(), now)
        assertEquals(1, order.size)
        assertSame(only, order.single())
    }

    @Test
    fun `退休行不计入待取与在途`() {
        val pdd = account(Platform.PDD)
        val jd = account(Platform.JD)
        val parcels = listOf(
            parcel(1, Platform.PDD, retired = true),
            parcel(2, Platform.PDD, retired = true),
            parcel(3, Platform.PDD, retired = true),
            parcel(4, Platform.JD),
        )
        val order = orderAccountsForRefresh(listOf(pdd, jd), parcels, now)
        assertTrue(order.first().platform == Platform.JD)
    }

    @Test
    fun `短信件不归到任何平台`() {
        val pdd = account(Platform.PDD)
        val jd = account(Platform.JD)
        val sms = parcel(1, Platform.PDD).copy(source = Source.SMS, accountId = null)
        val order = orderAccountsForRefresh(listOf(pdd, jd), listOf(sms), now)
        assertEquals(Platform.JD, order.first().platform)
    }
}
