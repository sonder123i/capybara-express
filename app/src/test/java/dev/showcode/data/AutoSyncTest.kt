package dev.showcode.data

import dev.showcode.core.account.AccountState
import dev.showcode.core.account.BoundAccount
import dev.showcode.core.account.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 前台静默同步的判据。四条红线：
 * 1. 15 分钟节流只认「成功过的章」；
 * 2. 本会话只跑一次；
 * 3. 演示 / 无账号 / 已有同步 / 无网络都不跑；
 * 4. BROKEN（和用户停用的）账号不从自动流程里绕过去。
 */
class AutoSyncTest {

    private fun account(
        id: String = "pdd",
        state: AccountState = AccountState.ACTIVE,
        enabled: Boolean = true,
        lastSyncAt: Long? = null,
    ) = BoundAccount(
        id = id,
        platform = Platform.of(id) ?: Platform.PDD,
        label = id,
        secret = "sealed",
        enabled = enabled,
        state = state,
        createdAt = 0L,
        lastSyncAt = lastSyncAt,
    )

    private fun decide(
        demo: Boolean = false,
        accounts: List<BoundAccount> = listOf(account()),
        syncing: Boolean = false,
        online: Boolean = true,
        lastSuccessAt: Long? = null,
        alreadyRanThisSession: Boolean = false,
        lastTransientFailureAt: Long? = null,
        now: Long = 100L * 60 * 1000,
    ) = decideAutoSync(
        demo = demo,
        accounts = accounts,
        syncing = syncing,
        online = online,
        lastSuccessAt = lastSuccessAt,
        now = now,
        alreadyRanThisSession = alreadyRanThisSession,
        lastTransientFailureAt = lastTransientFailureAt,
    )

    @Test
    fun `距上次成功不足 15 分钟 - 跳过`() {
        val skip = decide(lastSuccessAt = 100L * 60 * 1000 - 5L * 60 * 1000) as AutoSyncDecision.Skip
        assertTrue(skip.reason.contains("15 分钟"))
    }

    @Test
    fun `距上次成功超过 15 分钟 - 跑`() {
        val run = decide(lastSuccessAt = 100L * 60 * 1000 - 20L * 60 * 1000)
        assertTrue(run is AutoSyncDecision.Run)
    }

    @Test
    fun `本会话已经跑过 - 连续切前后台不再跑`() {
        val skip = decide(alreadyRanThisSession = true) as AutoSyncDecision.Skip
        assertTrue(skip.reason.contains("本会话"))
    }

    @Test
    fun `演示模式 - 跳过`() {
        assertTrue(decide(demo = true) is AutoSyncDecision.Skip)
    }

    @Test
    fun `没有已绑定账号 - 跳过`() {
        assertTrue(decide(accounts = emptyList()) is AutoSyncDecision.Skip)
    }

    @Test
    fun `已有同步在跑 - 跳过`() {
        assertTrue(decide(syncing = true) is AutoSyncDecision.Skip)
    }

    @Test
    fun `设备无网络 - 跳过并给中性提示`() {
        val skip = decide(online = false) as AutoSyncDecision.Skip
        assertTrue(skip.offline)
    }

    @Test
    fun `BROKEN 与停用账号不参与自动同步`() {
        val skip = decide(
            accounts = listOf(
                account(id = "pdd", state = AccountState.BROKEN),
                account(id = "jd", enabled = false),
            ),
        ) as AutoSyncDecision.Skip
        assertTrue(skip.reason.contains("不可用"))
    }

    @Test
    fun `一个 BROKEN 一个正常 - 只跑正常的那个`() {
        val run = decide(
            accounts = listOf(
                account(id = "pdd", state = AccountState.BROKEN),
                account(id = "tb", state = AccountState.EXPIRED),
            ),
        ) as AutoSyncDecision.Run
        assertEquals(listOf("tb"), run.accounts.map { it.id })
    }

    @Test
    fun `上次瞬时失败不足 30 分钟 - 不重撞`() {
        val skip = decide(
            lastTransientFailureAt = 100L * 60 * 1000 - 10L * 60 * 1000,
        ) as AutoSyncDecision.Skip
        assertTrue(skip.reason.contains("临时失败"))
    }

    @Test
    fun `上次瞬时失败超过 30 分钟 - 自动再试`() {
        val run = decide(lastTransientFailureAt = 100L * 60 * 1000 - 40L * 60 * 1000)
        assertTrue(run is AutoSyncDecision.Run)
    }

    @Test
    fun `成功但零新增 - 文案是刚刚同步没有新件`() {
        assertEquals(
            "刚刚同步 · 没有新件",
            autoSyncMessage(AccountSyncResult(
                added = 0, updated = 0,
                skippedHistory = 0, skippedNoTracking = 0, skippedNoPoint = 0, skippedNoCode = 0,
                at = 0L,
            )),
        )
    }

    @Test
    fun `成功有新件 - 文案带新增更新数`() {
        assertEquals(
            "刚刚同步 · 新增 2 · 更新 1",
            autoSyncMessage(AccountSyncResult(
                added = 2, updated = 1,
                skippedHistory = 0, skippedNoTracking = 0, skippedNoPoint = 0, skippedNoCode = 0,
                at = 0L,
            )),
        )
    }
}
