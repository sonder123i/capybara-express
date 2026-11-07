package dev.showcode.core.account

import dev.showcode.core.MINUTE_MS

/**
 * 首页「同步轨道」的状态。
 *
 * 一次刷新其实是四条互不相干的通道：本地短信收件箱 + 三家平台账号。它们各自会失败、
 * 各自会过期，而顶栏只有一行字。把四条通道摊开，失败才会被看见——
 * 这比一个全局的「更新于 3 分钟前」诚实：后者在三条通道都挂掉时依然显得很新。
 *
 * 这一层只做判断，不画东西；UI 在 ui/home/SyncRail.kt。
 */

/** 轨道上的四条通道，顺序固定：先本地，再三家平台。 */
enum class ChannelKind { SMS, JD, TB, PDD }

/** 一条通道此刻的样子。颜色之外，每个状态都有文字兜底（色弱也读得懂）。 */
enum class ChannelState {
    /** 最近一次成功，且在新鲜窗口内。 */
    FRESH,

    /** 最近一次成功，但已经过了新鲜窗口。 */
    STALE,

    /** 这一轮正在读。 */
    RUNNING,

    /** 最近一次尝试失败；登录失效也算。 */
    FAILED,

    /** 配好了，但从没成功过。 */
    NEVER,

    /** 没绑定、已停用、或短信导入没开。 */
    OFF,
}

data class SyncChannel(
    val kind: ChannelKind,
    /** 「短信」「京东」这类短名。平台用 [Platform.label]，不另写一套。 */
    val label: String,
    val state: ChannelState,
    /** 最近一次**成功**的时间；失败不推进它。 */
    val lastAt: Long?,
    /** 展开后那一行说明：上次结果、失败原因、或正在读第几件。 */
    val detail: String,
    /** 平台账号 id；短信为空。单通道重试要用它。 */
    val accountId: String? = null,
    /** 点这一行能不能直接去修（重试 / 重新登录 / 去绑定）。 */
    val action: ChannelAction = ChannelAction.NONE,
    /** 登录已失效或凭据解不开：重试没有意义，必须重新登录。 */
    val needsLogin: Boolean = false,
    /**
     * 正在跑的那一轮走到哪：0..1。null = 不知道（读订单阶段还没有分母）。
     *
     * 只有 RUNNING 才有值。它给轨道上那一行画一条 2dp 的细进度——
     * 「在动」必须能被看见，否则用户只会觉得「卡住了」。
     */
    val fraction: Float? = null,
)

enum class ChannelAction { NONE, RETRY, BIND, RELOGIN }

data class SyncRailState(
    val channels: List<SyncChannel>,
    /** 最近成功过的通道数。 */
    val answered: Int,
    /** 参与计数的通道数（短信开了 + 绑定且启用的账号）。 */
    val total: Int,
    /** 四条通道里最新的那次成功。用来回答「现在看到的是什么时候的数据」。 */
    val newestAt: Long?,
    /** 最近一次失败的通道名；空 = 没有失败。 */
    val failedLabels: List<String>,
    /** 登录失效/凭据解不开的通道名。这些重试没有意义，必须重新登录。 */
    val needsLoginLabels: List<String> = emptyList(),
) {
    val running: Boolean get() = channels.any { it.state == ChannelState.RUNNING }
}

/** 短信通道的输入。账号那条直接从 [BoundAccount] 读，不需要额外结构。 */
data class SmsChannelInput(
    val enabled: Boolean,
    val lastAt: Long?,
    val failed: Boolean,
    val running: Boolean,
    /** 最近一次回填新增了几条；用来把「刚回填 12 条」说清楚。 */
    val lastAdded: Int = 0,
)

/**
 * 成功结果的固定前缀。失败与成功在同一列 `last_sync_result` 里，没有单独的布尔位；
 * 约定「成功一定以这两个字开头」，比加一列迁移便宜，且这一条在 [syncSuccessMessage] 里只有一个出口。
 */
const val SYNC_OK_PREFIX = "同步成功"

fun isSuccessResult(result: String?): Boolean = result?.startsWith(SYNC_OK_PREFIX) == true

/** 默认新鲜窗口：和自动同步的节流一致（15 分钟）。 */
const val SYNC_FRESH_MS = 15 * MINUTE_MS

/**
 * 拼出四条通道。缺的账号补成 OFF，而不是从列表里消失——
 * 「这条通道根本不存在」和「它在但没连上」是两件事，用户要能分开看。
 */
fun syncRailState(
    accounts: List<BoundAccount>,
    sms: SmsChannelInput,
    syncingAccountId: String?,
    progress: SyncProgress?,
    now: Long,
    freshMs: Long = SYNC_FRESH_MS,
): SyncRailState {
    val channels = buildList {
        add(smsChannel(sms, now, freshMs))
        listOf(Platform.JD, Platform.TB, Platform.PDD).forEach { platform ->
            val account = accounts.firstOrNull { it.platform == platform && it.state != AccountState.BROKEN }
                ?: accounts.firstOrNull { it.platform == platform }
            add(accountChannel(platform, account, syncingAccountId, progress, now, freshMs))
        }
    }.sortedWith(
        /* 在用/绑定中的通道排前面，未开启与未绑定的沉到后面：
           空的圆环和「未绑定」撑在顶上，会让整条轨道看起来头轻脚重，
           而真正在发生事情的几家反而要往下找。同一组内按固定顺序，避免每次同步都换位。 */
        compareBy<SyncChannel> { it.state == ChannelState.OFF }
            .thenBy { it.kind.ordinal },
    )
    val counted = channels.filter { it.state != ChannelState.OFF }
    return SyncRailState(
        channels = channels,
        answered = channels.count { it.state == ChannelState.FRESH || it.state == ChannelState.STALE },
        total = counted.size,
        /* 关掉的通道不参与「最新」：一个已经停用的来源不该给整屏数据打上新鲜的时间。 */
        newestAt = channels.filter { it.state != ChannelState.OFF }
            .mapNotNull { it.lastAt }
            .maxOrNull(),
        failedLabels = channels.filter { it.state == ChannelState.FAILED }.map { it.label },
        needsLoginLabels = channels.filter { it.needsLogin }.map { it.label },
    )
}

private fun smsChannel(sms: SmsChannelInput, now: Long, freshMs: Long): SyncChannel {
    val state = when {
        sms.running -> ChannelState.RUNNING
        !sms.enabled -> ChannelState.OFF
        sms.failed -> ChannelState.FAILED
        sms.lastAt == null -> ChannelState.NEVER
        now - sms.lastAt <= freshMs -> ChannelState.FRESH
        else -> ChannelState.STALE
    }
    val detail = when (state) {
        ChannelState.RUNNING -> "正在读取收件箱…"
        ChannelState.OFF -> "未开启（可选）"
        ChannelState.FAILED -> "上次读收件箱失败"
        ChannelState.NEVER -> "还没读过收件箱"
        else -> if (sms.lastAdded > 0) "回填 ${sms.lastAdded} 条" else "收件箱没有新短信"
    }
    return SyncChannel(
        kind = ChannelKind.SMS,
        label = "短信",
        state = state,
        lastAt = sms.lastAt,
        detail = detail,
        action = ChannelAction.NONE,
    )
}

private fun accountChannel(
    platform: Platform,
    account: BoundAccount?,
    syncingAccountId: String?,
    progress: SyncProgress?,
    now: Long,
    freshMs: Long,
): SyncChannel {
    if (account == null) {
        return SyncChannel(
            kind = platform.channelKind(),
            label = platform.label,
            state = ChannelState.OFF,
            lastAt = null,
            detail = "未绑定",
            action = ChannelAction.BIND,
        )
    }
    val lastFailed = account.lastSyncResult != null && !isSuccessResult(account.lastSyncResult)
    /* 登录失效/凭据解不开：这不是「再试一次」能好的失败，动作必须是重新登录。 */
    val needsLogin = account.state == AccountState.BROKEN || account.state == AccountState.EXPIRED
    val state = when {
        account.id == syncingAccountId -> ChannelState.RUNNING
        !account.enabled -> ChannelState.OFF
        needsLogin -> ChannelState.FAILED
        lastFailed -> ChannelState.FAILED
        account.lastSyncAt == null -> ChannelState.NEVER
        now - account.lastSyncAt <= freshMs -> ChannelState.FRESH
        else -> ChannelState.STALE
    }
    val fraction = if (state == ChannelState.RUNNING) progressFraction(progress) else null
    val detail = when {
        state == ChannelState.RUNNING -> progressText(progress)
        state == ChannelState.OFF -> "已停用"
        state == ChannelState.FAILED -> account.lastSyncResult ?: when (account.state) {
            AccountState.BROKEN -> "凭据解不开，需要重新登录"
            else -> "登录已失效，需要重新登录"
        }
        state == ChannelState.NEVER -> "还没成功同步过"
        /* 点本身已经说明「成功」，这行只留结果：省下的字够把「跳过 3（已签收）」说全。 */
        else -> account.lastSyncResult?.removePrefix("$SYNC_OK_PREFIX · ") ?: "同步成功"
    }
    return SyncChannel(
        kind = platform.channelKind(),
        label = platform.label,
        state = state,
        lastAt = account.lastSyncAt,
        detail = detail,
        accountId = account.id,
        action = when {
            state != ChannelState.FAILED -> ChannelAction.NONE
            needsLogin -> ChannelAction.RELOGIN
            else -> ChannelAction.RETRY
        },
        needsLogin = needsLogin,
        fraction = fraction,
    )
}

/**
 * 进度比例。读订单阶段没有分母，返回 null（画成不确定态）；
 * 详情阶段按 done/total，从 0 起——不把「读订单」那一截算进去，否则一进详情就跳到 50%。
 */
private fun progressFraction(progress: SyncProgress?): Float? = when {
    progress == null -> null
    progress.phase == SyncPhase.ORDERS -> null
    progress.total <= 0 -> null
    else -> (progress.done.toFloat() / progress.total).coerceIn(0f, 1f)
}

private fun progressText(progress: SyncProgress?): String = when {
    progress == null -> "正在同步…"
    progress.phase == SyncPhase.ORDERS -> "正在读取订单…"
    progress.total > 0 -> "读取详情 ${progress.done}/${progress.total}"
    else -> "正在读取详情…"
}

private fun Platform.channelKind(): ChannelKind = when (this) {
    Platform.JD -> ChannelKind.JD
    Platform.TB -> ChannelKind.TB
    Platform.PDD -> ChannelKind.PDD
}
