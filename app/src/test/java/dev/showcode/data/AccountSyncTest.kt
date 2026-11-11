package dev.showcode.data

import dev.showcode.core.account.AccountState
import dev.showcode.core.account.FailureKind
import dev.showcode.core.account.FetchOutcome
import dev.showcode.core.account.SYNC_OK_PREFIX
import dev.showcode.core.account.allDetailsFailed
import dev.showcode.core.account.detailBatchFailed
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

    @Test
    fun virtualGoodsRetirementIsNotReportedAsSkippedHistory() {
        val decision = decideSync(
            FetchOutcome.Success(emptyList(), retireOrderSns = listOf("000000000000000003")),
        ) as SyncDecision.Succeeded
        assertEquals(listOf("000000000000000003"), decision.retireOrderSns)
        /* 卡券不是「已签收的历史单」：退休它不该在状态行冒充跳过。 */
        assertEquals(
            "同步成功 · 没有新件",
            syncSuccessMessage(AccountSyncResult(0, 0, 0, 0, 0, 0, 0L)),
        )
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
    fun fallbackParcelsAreVisibleInSummary() {
        val result = AccountSyncResult(
            added = 3, updated = 0,
            skippedHistory = 0, skippedNoTracking = 0, skippedNoPoint = 0, skippedNoCode = 0,
            at = 0L,
            fallbackCount = 2,
        )
        assertEquals("同步成功 · 新增 3 · 更新 0 · 2 件轨迹待补", syncSuccessMessage(result))
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

    /**
     * PDD 的第二条红线：候选全是「状态没变、库里已有」时 pending=0，
     * 这一轮一条详情都没抓，不能因为 candidates 非空就报「0 件详情都没抓到」。
     */
    @Test
    fun noAttemptedDetailIsNotABatchFailure() {
        assertFalse(detailBatchFailed(attempted = 0, parcels = 0, returns = 0))
        /* 单件也确实尝试过、且一件都没拼出来：算批次失败，不能退回「成功·没新件」。 */
        assertTrue(detailBatchFailed(attempted = 1, parcels = 0, returns = 0))
        assertTrue(detailBatchFailed(attempted = 3, parcels = 0, returns = 0))
        assertFalse(detailBatchFailed(attempted = 3, parcels = 1, returns = 0))
        assertFalse(detailBatchFailed(attempted = 3, parcels = 0, returns = 1))
    }

    @Test
    fun successCarriesDetailFailureCount() {
        val decision = decideSync(
            FetchOutcome.Success(emptyList(), detailsFailed = 2),
        ) as SyncDecision.Succeeded
        assertEquals(2, decision.detailsFailed)
    }

    /* 「没同步上」四个字看不出卡在哪；归类后的短标签要上屏，且绝不把原始 reason
       （可能夹 URL / 单号）漏进账号卡。 */
    @Test
    fun transientFailureSurfacesSafeCategory() {
        val timeout = decideSync(
            FetchOutcome.Failure("订单页加载超时", FailureKind.TRANSIENT),
        ) as SyncDecision.Failed
        assertTrue(timeout.message.contains("页面加载超时"))
        assertTrue(timeout.message.contains("稍后再试"))

        val leaky = decideSync(
            FetchOutcome.Failure("抓取中断：http://x/order?sn=123456789012", FailureKind.TRANSIENT),
        ) as SyncDecision.Failed
        assertFalse(leaky.message.contains("http"))
        assertFalse(leaky.message.contains("123456789012"))
        assertTrue(leaky.message.contains("稍后再试"))
    }

    /**
     * 京东/淘宝写「N 件都没有可用信息」，不含「详情」二字；旧归类只认「详情」，
     * 这条主失败路径会退回通用措辞，本次「看清卡在哪」对它完全无效。
     */
    @Test
    fun transientFailureClassifiesNoUsableInfoWording() {
        listOf(
            "8 件都没有可用信息，稍后再试",
            "3 件详情都没抓到，稍后再试",
        ).forEach { reason ->
            val decision = decideSync(
                FetchOutcome.Failure(reason, FailureKind.TRANSIENT),
            ) as SyncDecision.Failed
            assertTrue(reason, decision.message.contains("详情页没抓到"))
        }
        /* 未识别的原因仍退回通用措辞，不能把原始串带出去。 */
        val unknown = decideSync(
            FetchOutcome.Failure("某个没见过的异常", FailureKind.TRANSIENT),
        ) as SyncDecision.Failed
        assertEquals("这次没同步上，稍后再试", unknown.message)
    }
}
