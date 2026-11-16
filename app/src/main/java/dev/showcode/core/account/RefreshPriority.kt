package dev.showcode.core.account

import dev.showcode.core.Parcel
import dev.showcode.core.Source
import dev.showcode.core.Stage
import kotlin.math.pow

/**
 * 刷新顺序的智能排序。
 *
 * 目标只有一个：让「最可能有新取件码」的那家先跑。它只决定**调度顺序**，
 * 不参与任何数据判定——输出必须是输入的一个排列；排错了最坏是多等几秒，
 * 不会少同步一家、也不会改变任何一件的结果。
 *
 * 信号全部来自库里已有的包裹与账号状态，不新增表、不发额外请求：
 * - 当前待取件：这家现在就有几件等你拿；
 * - 在途/派送：即将到站，码大概率在路上；
 * - 近期收件频率：近 60 天按 14 天半衰期加权，「经常有快递」的那家自然靠前；
 * - 上次成功同步越久，越可能有新变化；
 * - 会话失效/密文损坏的账号排最后，不让一个要重新登录的账号挡住有码的那家。
 */

internal const val AT_STATION_WEIGHT = 10.0
internal const val IN_TRANSIT_WEIGHT = 4.0
internal const val MOMENTUM_WEIGHT = 2.0
internal const val STALENESS_WEIGHT = 1.0
internal const val MOMENTUM_WINDOW_MS = 60L * 24 * 60 * 60 * 1000
internal const val MOMENTUM_HALF_LIFE_MS = 14L * 24 * 60 * 60 * 1000
internal const val STALENESS_CAP_MS = 7L * 24 * 60 * 60 * 1000

data class RefreshScore(
    val account: BoundAccount,
    val score: Double,
    val atStation: Int,
    val inTransit: Int,
    val momentum: Double,
    /** 距上次同步的毫秒；从未同步按封顶值算。 */
    val lastSyncAgeMs: Long,
    val broken: Boolean,
)

fun orderAccountsForRefresh(
    accounts: List<BoundAccount>,
    parcels: List<Parcel>,
    now: Long,
): List<BoundAccount> = rankAccountsForRefresh(accounts, parcels, now).map { it.account }

fun rankAccountsForRefresh(
    accounts: List<BoundAccount>,
    parcels: List<Parcel>,
    now: Long,
): List<RefreshScore> {
    val byAccount = parcels
        .asSequence()
        .filter { it.source == Source.ACCOUNT && !it.accountId.isNullOrBlank() }
        .groupBy { it.accountId.orEmpty() }

    return accounts
        .map { account ->
            val rows = byAccount[account.id].orEmpty()
            val live = rows.filter { !it.picked && !it.retired }
            val atStation = live.count { it.stage == Stage.AT_STATION }
            val inTransit = live.count {
                it.stage == Stage.IN_TRANSIT || it.stage == Stage.DELIVERING
            }
            val momentum = rows
                .asSequence()
                .filter { !it.retired }
                .map { ageMs(it.receivedAt, now) }
                .filter { it in 0L..MOMENTUM_WINDOW_MS }
                .sumOf { age ->
                    0.5.pow(age.toDouble() / MOMENTUM_HALF_LIFE_MS)
                }
            val lastSyncAgeMs = (now - (account.lastSyncAt ?: 0L))
                .coerceIn(0L, STALENESS_CAP_MS)
            val broken = account.state != AccountState.ACTIVE
            val score = AT_STATION_WEIGHT * atStation +
                IN_TRANSIT_WEIGHT * inTransit +
                MOMENTUM_WEIGHT * momentum +
                STALENESS_WEIGHT * (lastSyncAgeMs.toDouble() / STALENESS_CAP_MS)
            RefreshScore(account, score, atStation, inTransit, momentum, lastSyncAgeMs, broken)
        }
        .sortedWith(
            compareBy<RefreshScore> { it.broken }
                .thenByDescending { it.score }
                .thenByDescending { it.atStation }
                .thenByDescending { it.momentum }
                .thenByDescending { it.lastSyncAgeMs }
                .thenBy { it.account.id },
        )
}

private fun ageMs(at: Long, now: Long): Long = (now - at).coerceAtLeast(0L)
