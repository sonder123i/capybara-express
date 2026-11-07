package dev.showcode.core.account

import dev.showcode.core.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRailTest {

    private val now = 10_000 * MINUTE_MS

    private fun account(
        platform: Platform,
        enabled: Boolean = true,
        state: AccountState = AccountState.ACTIVE,
        lastSyncAt: Long? = now - MINUTE_MS,
        result: String? = "同步成功 · 没有新件",
    ) = BoundAccount(
        id = platform.id,
        platform = platform,
        label = platform.label,
        secret = "cipher",
        enabled = enabled,
        state = state,
        createdAt = 0,
        lastSyncAt = lastSyncAt,
        lastSyncResult = result,
    )

    private fun sms(
        enabled: Boolean = true,
        lastAt: Long? = now - MINUTE_MS,
        failed: Boolean = false,
        running: Boolean = false,
        added: Int = 0,
    ) = SmsChannelInput(enabled, lastAt, failed, running, added)

    private fun rail(
        accounts: List<BoundAccount> = emptyList(),
        sms: SmsChannelInput = this.sms(),
        syncingAccountId: String? = null,
        progress: SyncProgress? = null,
    ) = syncRailState(accounts, sms, syncingAccountId, progress, now)

    @Test
    fun `四条通道齐全时按固定顺序 - 短信在前`() {
        val state = rail(
            accounts = listOf(
                account(Platform.JD),
                account(Platform.TB),
                account(Platform.PDD),
            ),
        )
        assertEquals(
            listOf(ChannelKind.SMS, ChannelKind.JD, ChannelKind.TB, ChannelKind.PDD),
            state.channels.map { it.kind },
        )
    }

    @Test
    fun `未开启与未绑定的沉底 - 绑定中的通道排前面`() {
        /* 截图里的样子：短信没开、京东没绑，真正在动的淘宝/拼多多却在最下面。
           排序后它们先出场，空环留在底下。 */
        val state = rail(
            accounts = listOf(account(Platform.TB), account(Platform.PDD)),
            sms = sms(enabled = false),
        )
        assertEquals(
            listOf(ChannelKind.TB, ChannelKind.PDD, ChannelKind.SMS, ChannelKind.JD),
            state.channels.map { it.kind },
        )
        val pdd = state.channels.first { it.kind == ChannelKind.PDD }
        assertEquals(ChannelAction.NONE, pdd.action)
        val jd = state.channels.first { it.kind == ChannelKind.JD }
        assertEquals(ChannelAction.BIND, jd.action)
        assertEquals("未绑定", jd.detail)
        // 绑定的两家参与计数；短信没开、京东没绑不算。
        assertEquals(2, state.total)
        assertEquals(2, state.answered)
    }

    @Test
    fun `刚同步过的账号 - 新鲜，并给出去绑定以外的重试入口`() {
        val state = rail(accounts = listOf(account(Platform.JD)))
        val jd = state.channels.first { it.kind == ChannelKind.JD }
        assertEquals(ChannelState.FRESH, jd.state)
        assertEquals(ChannelAction.NONE, jd.action)
        assertEquals(now - MINUTE_MS, jd.lastAt)
        assertEquals(ChannelAction.BIND, state.channels.first { it.kind == ChannelKind.PDD }.action)
    }

    @Test
    fun `正在读详情 - 文案带第几件，重复点击的紧迫感才有着落`() {
        val state = rail(
            accounts = listOf(account(Platform.PDD)),
            syncingAccountId = Platform.PDD.id,
            progress = SyncProgress(SyncPhase.DETAILS, done = 2, total = 5),
        )
        val pdd = state.channels.first { it.kind == ChannelKind.PDD }
        assertEquals(ChannelState.RUNNING, pdd.state)
        assertEquals("读取详情 2/5", pdd.detail)
        assertTrue(state.running)
    }

    @Test
    fun `读订单阶段 - 还没有件数`() {
        val state = rail(
            accounts = listOf(account(Platform.TB)),
            syncingAccountId = Platform.TB.id,
            progress = SyncProgress.Orders,
        )
        assertEquals("正在读取订单…", state.channels.first { it.kind == ChannelKind.TB }.detail)
    }

    @Test
    fun `上次结果不是成功 - 算失败，并且能被单独重试`() {
        val state = rail(
            accounts = listOf(account(Platform.JD, result = "这次没同步上，稍后再试")),
        )
        val jd = state.channels.first { it.kind == ChannelKind.JD }
        assertEquals(ChannelState.FAILED, jd.state)
        assertEquals(ChannelAction.RETRY, jd.action)
        assertEquals("这次没同步上，稍后再试", jd.detail)
        assertEquals(listOf("京东"), state.failedLabels)
        assertTrue(state.needsLoginLabels.isEmpty())
    }

    @Test
    fun `登录失效 - 动作是重新登录，不是再重试一次`() {
        val state = rail(
            accounts = listOf(
                account(Platform.TB, state = AccountState.EXPIRED, result = "登录已失效，需要重新登录"),
            ),
        )
        val tb = state.channels.first { it.kind == ChannelKind.TB }
        assertEquals(ChannelState.FAILED, tb.state)
        assertEquals("登录已失效，需要重新登录", tb.detail)
        assertEquals(ChannelAction.RELOGIN, tb.action)
        assertTrue(tb.needsLogin)
        assertEquals(listOf("淘宝"), state.needsLoginLabels)
    }

    @Test
    fun `凭据解不开 - 也要重新登录，而不是反复重试`() {
        val state = rail(
            accounts = listOf(account(Platform.PDD, state = AccountState.BROKEN, result = null)),
        )
        val pdd = state.channels.first { it.kind == ChannelKind.PDD }
        assertEquals(ChannelAction.RELOGIN, pdd.action)
        assertEquals("凭据解不开，需要重新登录", pdd.detail)
        assertEquals(listOf("拼多多"), state.needsLoginLabels)
    }

    @Test
    fun `停用的账号 - 不算进通道总数，也不显示成失败`() {
        val state = rail(accounts = listOf(account(Platform.PDD, enabled = false)))
        val pdd = state.channels.first { it.kind == ChannelKind.PDD }
        assertEquals(ChannelState.OFF, pdd.state)
        assertEquals("已停用", pdd.detail)
        assertEquals(ChannelAction.NONE, pdd.action)
        assertEquals(1, state.total) // 只剩短信
    }

    @Test
    fun `超过新鲜窗口 - 变陈旧，但仍算答过`() {
        val state = rail(
            accounts = listOf(account(Platform.JD, lastSyncAt = now - 60 * MINUTE_MS)),
        )
        val jd = state.channels.first { it.kind == ChannelKind.JD }
        assertEquals(ChannelState.STALE, jd.state)
        assertEquals(2, state.answered) // 短信也是新鲜的
    }

    @Test
    fun `从没成功过 - 是 NEVER，不是失败`() {
        val state = rail(
            accounts = listOf(account(Platform.TB, lastSyncAt = null, result = null)),
            sms = sms(enabled = false),
        )
        val tb = state.channels.first { it.kind == ChannelKind.TB }
        assertEquals(ChannelState.NEVER, tb.state)
        assertEquals("还没成功同步过", tb.detail)
        assertEquals(0, state.answered)
        assertTrue(state.failedLabels.isEmpty())
    }

    @Test
    fun `短信未开启 - 中性状态，不报失败`() {
        val state = rail(sms = sms(enabled = false))
        val smsChannel = state.channels.first { it.kind == ChannelKind.SMS }
        assertEquals(ChannelState.OFF, smsChannel.state)
        assertEquals("未开启（可选）", smsChannel.detail)
        assertEquals(0, state.total)
        assertTrue(state.failedLabels.isEmpty())
    }

    @Test
    fun `短信失败 - 轨道上说得出来`() {
        val state = rail(sms = sms(failed = true))
        val smsChannel = state.channels.first { it.kind == ChannelKind.SMS }
        assertEquals(ChannelState.FAILED, smsChannel.state)
        assertEquals(listOf("短信"), state.failedLabels)
    }

    @Test
    fun `回填了几条 - 短信那格直接说数量`() {
        val state = rail(sms = sms(added = 3))
        assertEquals("回填 3 条", state.channels.first { it.kind == ChannelKind.SMS }.detail)
    }

    @Test
    fun `最新时间取四条通道里最大的那个`() {
        val state = rail(
            accounts = listOf(
                account(Platform.JD, lastSyncAt = now - 10 * MINUTE_MS),
                account(Platform.TB, lastSyncAt = now - 2 * MINUTE_MS),
            ),
            sms = sms(lastAt = now - 30 * MINUTE_MS),
        )
        assertEquals(now - 2 * MINUTE_MS, state.newestAt)
    }

    @Test
    fun `全都没有通道 - 总数为零，没有最新时间`() {
        val state = rail(sms = sms(enabled = false))
        assertEquals(0, state.total)
        assertEquals(0, state.answered)
        assertNull(state.newestAt)
        assertTrue(state.channels.all { it.state == ChannelState.OFF })
    }

    @Test
    fun `成功结果在明细里不带成功前缀 - 实心点已经说明过了`() {
        val changed = rail(
            accounts = listOf(account(Platform.TB, result = "同步成功 · 新增 2 · 更新 1")),
        )
        assertEquals(
            "新增 2 · 更新 1",
            changed.channels.first { it.kind == ChannelKind.TB }.detail,
        )
        val quiet = rail(
            accounts = listOf(account(Platform.JD, result = "同步成功 · 没有新件")),
        )
        assertEquals(
            "没有新件",
            quiet.channels.first { it.kind == ChannelKind.JD }.detail,
        )
    }

    @Test
    fun `只有成功结果才算成功 - 失败那句不能以成功开头`() {
        assertTrue(isSuccessResult("同步成功 · 没有新件"))
        assertTrue(isSuccessResult("同步成功 · 新增 1 · 更新 0"))
        assertFalse(isSuccessResult("这次没同步上，稍后再试"))
        assertFalse(isSuccessResult("登录已失效，需要重新登录"))
        assertFalse(isSuccessResult(null))
    }
}
