package dev.showcode.data

import android.app.Activity
import dev.showcode.core.Slog
import dev.showcode.core.account.AccountState
import dev.showcode.core.account.BoundAccount
import dev.showcode.core.account.FetchOutcome
import dev.showcode.core.account.FailureKind
import dev.showcode.core.account.Platform
import dev.showcode.core.account.SYNC_OK_PREFIX
import dev.showcode.core.account.SyncProgress
import dev.showcode.core.account.planAccountSync
import dev.showcode.data.fetch.JdFetcher
import dev.showcode.data.fetch.PddFetcher
import dev.showcode.data.fetch.TbFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 账号同步的唯一编排点：解密 → 抓取 → 合并 → 写库 → 记状态。
 *
 * 它刻意不判断「这件该不该显示」——那在 core 的 planAccountSync 与 groupByPoint 里。
 * 它也不做重试/轮询：一次用户动作 = 一次同步；失败就把话说清楚，不在后台偷偷重来。
 */
object AccountSyncEngine {

    private const val TAG = "AccountSyncEngine"

    /**
     * 编排本身放在 IO：解密 Keystore、读写账号表、合并写库都是阻塞调用，
     * 不该占主线程。抓取器自己会切回 Main（WebView 只能主线程），
     * 取消语义不变——[withContext] 可取消，用户离开登录页时整条链一起停。
     */
    suspend fun sync(
        activity: Activity?,
        repository: ParcelRepository,
        account: BoundAccount,
        /** true = 忽略「状态没变就跳过详情」，把这一次当全量重抓。 */
        force: Boolean = false,
        /** 同步走到哪一步；调用方负责切回主线程再改 UI 状态。 */
        onProgress: suspend (SyncProgress) -> Unit = {},
    ): AccountSyncResult? = withContext(Dispatchers.IO) {
        syncOnIo(activity, repository, account, force, onProgress)
    }

    private suspend fun syncOnIo(
        activity: Activity?,
        repository: ParcelRepository,
        account: BoundAccount,
        force: Boolean,
        onProgress: suspend (SyncProgress) -> Unit,
    ): AccountSyncResult? {
        if (!account.enabled) return null
        if (activity == null) {
            repository.accounts.mark(account, AccountState.EXPIRED, "需要在 App 前台才能同步")
            return null
        }
        val cookie = repository.accounts.cookieOf(account)
        if (cookie == null) {
            repository.accounts.mark(account, AccountState.BROKEN, "凭据解不开，需要重新登录")
            return null
        }
        /* 详情门控的数据面：这个账号库里已有的件，按订单号索引。
           读一次给抓取器，状态没变的单就不再进详情页（见 core/account/DetailGate.kt）。
           强制重抓时传空表：门控认为「什么都没有」，于是每一单都会重新读。 */
        val known = if (force) {
            emptyMap()
        } else {
            repository.parcelsForAccountSync()
                .filter { it.accountId == account.id && !it.orderSn.isNullOrBlank() }
                .associateBy { it.orderSn.orEmpty() }
        }
        /* 列表阶段先报出去：用户至少知道这 20 秒不是在等一个死界面。 */
        onProgress(SyncProgress.Orders)
        val outcome = when (account.platform) {
            Platform.PDD -> PddFetcher.fetch(activity, account.id, cookie, known, onProgress)
            Platform.TB -> TbFetcher.fetch(account.id, cookie, known, onProgress)
            Platform.JD -> JdFetcher.fetch(activity, account.id, cookie, known, onProgress)
        }
        val at = System.currentTimeMillis()
        when (val decision = decideSync(outcome)) {
            is SyncDecision.Failed -> {
                /* 失败落到账号状态上，并且**不**推进 last_sync_at（mark 不传 at 就是保留旧值）：
                   首页的「很久没同步」判据只认真成功过的章。
                   瞬时失败不改账号既有的状态（原来是 EXPIRED 就还是 EXPIRED，不能因为一次断网
                   把「需要重新登录」擦成 ACTIVE）；只记一次退避时间，别每次回前台都重试。 */
                val state = if (decision.retryable) account.state else decision.state
                repository.accounts.mark(account, state, decision.message)
                if (decision.retryable) {
                    repository.prefs.lastTransientFailureAt = at
                } else {
                    repository.prefs.lastTransientFailureAt = 0L
                }
                /* release 只留错误码与平台标识；原因文案属于业务信息，只在 debug 打。 */
                Slog.failure(TAG, if (decision.retryable) "TRANSIENT" else "AUTH", account.platform.id)
                Slog.i(TAG) { "failure reason=${decision.message}" }
                return null
            }
            is SyncDecision.Succeeded -> {
                // 必须看见退休行：平台重新给出这件时要能把它从 retirement 里捞回来。
                val actions = planAccountSync(decision.parcels, repository.parcelsForAccountSync())
                Slog.safe(TAG, "actions=${actions.size}")
                actions.forEach { action ->
                    val item = action.item
                    val verdict = when (action) {
                        is dev.showcode.core.account.AccountSyncAction.Insert -> "INSERT"
                        is dev.showcode.core.account.AccountSyncAction.Update -> "UPDATE"
                        is dev.showcode.core.account.AccountSyncAction.Skip -> "SKIP(${action.reason})"
                    }
                    Slog.i(TAG) {
                        "$verdict sn=${Slog.mask(item.orderSn)} tracking=${Slog.mask(item.trackingNo)} " +
                            "stage=${item.stage} codeKind=${item.codeKind} " +
                            "point=${item.pointName?.length ?: 0}字"
                    }
                }
                /* 退休与合并放在 repository 的同一个事务里：
                   历史卡片 + 上次进来、这次两处接口都不再返回的残留在途行都在这里处理。
                   空 seenOrderSns 说明这次抓取不完整，宁可留着也不误删（由 repository 判断）。 */
                val result = repository.applyAccountSync(
                    actions = actions,
                    at = at,
                    accountId = account.id,
                    historyOrderSns = decision.historyOrderSns,
                    seenOrderSns = decision.seenOrderSns,
                )
                repository.prefs.lastTransientFailureAt = 0L
                /* 单件详情失败的数量属于本次抓取，不进库；带上它，状态行才不会把
                   「3 件没抓到」说成「没有新件」。 */
                val withDetails = result.copy(detailsFailed = decision.detailsFailed)
                repository.accounts.mark(account, AccountState.ACTIVE, syncSuccessMessage(withDetails), at)
                return withDetails
            }
        }
    }
}

/**
 * 三种抓取结局到账号状态的唯一映射。抽成纯函数，单测才能在没有 WebView/DB 的情况下
 * 断言「失败绝不置 ACTIVE」。
 */
sealed interface SyncDecision {
    /** 失败：置非 ACTIVE，且**不**推进 last_sync_at（[advanceLastSyncAt] 恒为 false）。 */
    data class Failed(
        val state: AccountState,
        val message: String,
        /** true = 断网/超时/结构异常，账号保持原状态，稍后重试即可。 */
        val retryable: Boolean = false,
        val advanceLastSyncAt: Boolean = false,
    ) : SyncDecision

    /** 成功（含「成功但零新增」）：可以推进 last_sync_at。 */
    data class Succeeded(
        val parcels: List<dev.showcode.core.account.FetchedParcel>,
        val historyOrderSns: List<String> = emptyList(),
        /** 本次实际见到的订单号；空集时不做残留轨迹退休（抓取可能不完整）。 */
        val seenOrderSns: Set<String> = emptySet(),
        /** 单件详情没抓到的数量；0 = 全部解析成功。 */
        val detailsFailed: Int = 0,
        val advanceLastSyncAt: Boolean = true,
    ) : SyncDecision
}

/**
 * 抓取结局 → 账号状态。
 *
 * 只有证据指向登录失效（[FailureKind.AUTH]：跳到登录/通行证页、401/403、会话失效响应）
 * 才置 `EXPIRED`。断网、超时、5xx、页面结构异常都归可重试：账号保持原状态，
 * 文案是「这次没同步上，稍后再试」，而不是催人重新登录。
 */
internal fun decideSync(outcome: FetchOutcome): SyncDecision = when (outcome) {
    is FetchOutcome.Failure -> if (outcome.kind == FailureKind.AUTH) {
        SyncDecision.Failed(
            state = AccountState.EXPIRED,
            message = "登录已失效，需要重新登录",
        )
    } else {
        SyncDecision.Failed(
            state = AccountState.ACTIVE,
            message = "这次没同步上，稍后再试",
            retryable = true,
        )
    }
    is FetchOutcome.Success -> SyncDecision.Succeeded(
        parcels = outcome.parcels,
        historyOrderSns = outcome.historyOrderSns,
        seenOrderSns = outcome.seenOrderSns,
        detailsFailed = outcome.detailsFailed,
    )
}

/**
 * 成功但零新增也必须说成「成功·没有新件」，不能和失败共用一句话。
 * 跳过要带原因：不然平台说有 3 件、首页只多 1 张卡时，用户无从判断是哪一步漏了。
 *
 * ⚠️ 成功的结果**一定**以 [SYNC_OK_PREFIX] 开头：`last_sync_result` 这一列没有单独的
 * 布尔位，同步轨道就是靠这个前缀区分「上次是成功」还是「上次是失败」。改文案时别丢它。
 */
internal fun syncSuccessMessage(result: AccountSyncResult): String {
    val skipped = result.skippedHistory +
        result.skippedNoTracking + result.skippedNoPoint + result.skippedNoCode
    if (result.added == 0 && result.updated == 0 && skipped == 0 && result.detailsFailed == 0) {
        return "$SYNC_OK_PREFIX · 没有新件"
    }
    return buildString {
        append(SYNC_OK_PREFIX)
        append(" · 新增 ${result.added} · 更新 ${result.updated}")
        if (skipped > 0) {
            append(" · 跳过 $skipped（")
            val reasons = buildList {
                val history = result.skippedHistory
                if (history > 0) add("已签收 $history")
                if (result.skippedNoTracking > 0) add("无运单号 ${result.skippedNoTracking}")
                if (result.skippedNoPoint > 0) add("无取件点 ${result.skippedNoPoint}")
                if (result.skippedNoCode > 0) add("无取件码 ${result.skippedNoCode}")
            }
            append(reasons.joinToString(" / "))
            append("）")
        }
        /* 中性陈述，不是告警：已解析到的件照常入库，只是这几条没读到。 */
        if (result.detailsFailed > 0) {
            append(" · ${result.detailsFailed} 件详情没抓到")
        }
    }
}
