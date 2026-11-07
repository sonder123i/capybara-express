package dev.showcode.data

import dev.showcode.core.account.AccountState
import dev.showcode.core.account.FailureKind
import dev.showcode.core.account.FetchOutcome
import dev.showcode.core.account.SYNC_OK_PREFIX
import dev.showcode.core.account.allDetailsFailed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 抓取结局 → 账号状态。四条红线：
 * 1. 只有登录失效的证据才置 EXPIRED；
 * 2. 瞬时失败不改账号状态，可重试；
 * 3. 失败不推进 last_sync_at；
 * 4. 成功但零新增仍是成功态，文案要和失败分开，且单件失败要显式计数。
 */
class AccountSyncTest {

    @Test
    fun authFailureExpiresTheAccount() {
        val decision = decideSync(
            FetchOutcome.Failure("订单页返回 HTTP 403", FailureKind.AUTH),
        )
        assertTrue(decision is SyncDecision.Failed)
        val failed = decision as SyncDecision.Failed
        assertEquals(AccountState.EXPIRED, failed.state)
        assertFalse(failed.retryable)
        assertTrue(failed.message.contains("重新登录"))
    }

    @Test
    fun transientFailureKeepsAccountAndSaysRetryLater() {
        val failed = decideSync(
            FetchOutcome.Failure("订单页加载超时", FailureKind.TRANSIENT),
        ) as SyncDecision.Failed
        assertTrue(failed.retryable)
        assertEquals(AccountState.ACTIVE, failed.state)
        assertTrue(failed.message.contains("稍后再试"))
        assertFalse(failed.message.contains("重新登录"))
    }

    @Test
    fun failureDoesNotAdvanceLastSyncAt() {
        val failed = decideSync(FetchOutcome.Failure("window.name 读不到")) as SyncDecision.Failed
        assertFalse(failed.advanceLastSyncAt)
        // 成功分支（即使零新增）才允许盖章。
        val success = decideSync(FetchOutcome.Success(emptyList())) as SyncDecision.Succeeded
        assertTrue(success.advanceLastSyncAt)
    }

    @Test
    fun successButZeroNewIsStillSuccess() {
        val result = AccountSyncResult(
            added = 0, updated = 0,
            skippedHistory = 0, skippedNoTracking = 0, skippedNoPoint = 0, skippedNoCode = 0,
            at = 0L,
        )
        assertEquals("同步成功 · 没有新件", syncSuccessMessage(result))
        assertTrue(decideSync(FetchOutcome.Success(emptyList())) is SyncDecision.Succeeded)
    }

    @Test
    fun successWithNewParcelsReportsCounts() {
        val result = AccountSyncResult(
            added = 2, updated = 1,
            skippedHistory = 0, skippedNoTracking = 0, skippedNoPoint = 0, skippedNoCode = 0,
            at = 0L,
        )
        assertEquals("同步成功 · 新增 2 · 更新 1", syncSuccessMessage(result))
    }

    /**
     * `last_sync_result` 没有单独的布尔位；同步轨道靠这个前缀判断上次是成功还是失败。
     * 这条一破，「成功但新增 0」的账号就会在首页被标成琥珀色失败。
     */
    @Test
    fun everySuccessMessageCarriesThePrefixContract() {
        val messages = listOf(
            AccountSyncResult(0, 0, 0, 0, 0, 0, 0L),
            AccountSyncResult(2, 1, 1, 0, 0, 0, 0L),
            AccountSyncResult(0, 0, 0, 0, 0, 0, 0L, detailsFailed = 2),
        )
        messages.forEach { assertTrue(syncSuccessMessage(it).startsWith(SYNC_OK_PREFIX)) }
    }

    @Test
    fun skippedReasonsAreVisibleInSummary() {
        val result = AccountSyncResult(
            added = 2, updated = 1,
            skippedHistory = 2, skippedNoTracking = 1, skippedNoPoint = 0, skippedNoCode = 0,
            at = 0L,
        )
        assertEquals("同步成功 · 新增 2 · 更新 1 · 跳过 3（已签收 2 / 无运单号 1）", syncSuccessMessage(result))
    }

    @Test
    fun detailFailuresAreVisibleInsteadOfSayingNoNewParcels() {
        val result = AccountSyncResult(
            added = 0, updated = 0,
            skippedHistory = 0, skippedNoTracking = 0, skippedNoPoint = 0, skippedNoCode = 0,
            at = 0L,
            detailsFailed = 3,
        )
        /* 不能吞成「没有新件」：3 件详情没抓到必须说出来。 */
        assertEquals("同步成功 · 新增 0 · 更新 0 · 3 件详情没抓到", syncSuccessMessage(result))
        assertFalse(syncSuccessMessage(result).contains("没有新件"))
    }

    @Test
    fun allDetailsFailedIsABatchFailureNotAnEmptySuccess() {
        assertTrue(allDetailsFailed(attempted = 3, failed = 3))
        assertFalse(allDetailsFailed(attempted = 3, failed = 2))
        assertFalse(allDetailsFailed(attempted = 0, failed = 0))
    }

    /**
     * 只有一单时，它没解析出来不足以证明整条链路坏了（虚拟商品、兑换码、还没揽收
     * 都没有物流）。这一单要落到 `Success(detailsFailed = 1)`，账号不能被标成失败。
     */
    @Test
    fun oneUnparseableDetailIsNotABatchFailure() {
        assertFalse(allDetailsFailed(attempted = 1, failed = 1))
        assertTrue(allDetailsFailed(attempted = 2, failed = 2))
    }

    @Test
    fun successCarriesDetailFailureCount() {
        val decision = decideSync(
            FetchOutcome.Success(emptyList(), detailsFailed = 2),
        ) as SyncDecision.Succeeded
        assertEquals(2, decision.detailsFailed)
    }
}
