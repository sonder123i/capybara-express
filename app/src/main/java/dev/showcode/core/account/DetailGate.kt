package dev.showcode.core.account

import dev.showcode.core.Parcel
import dev.showcode.core.Stage

/**
 * 一次刷新里，哪些单还值得再加载一遍详情页。
 *
 * 详情页是这条链路上最贵的一步：一单 = 一次整页导航 + 一次「查看物流」点击 +
 * 一次轨迹页导航。而列表接口（拼多多的 `express/track/status`、京东/淘宝的订单状态）
 * 已经把「这件有没有变化」说出来了。状态没变、运单号与取件码都在的那一行，
 * 再点进详情得到的还是同一段文字。
 *
 * 所以判据是**保守地跳过**：只有库里有这一单、运单号不缺、该有的码也有了、
 * 列表状态与库里阶段一致、而且上次详情还不算久，才跳过。任何一条不满足就照旧抓——
 * 宁可多花 7 秒，也不能让「已到站」停在「运输中」。
 */

/** 详情页多久之后即使列表状态没变也重抓一次。这是自愈窗口，不是缓存有效期。 */
const val DETAIL_REUSE_MS = 6L * 60 * 60 * 1000

/**
 * 订单列表里的一行状态文案 → 阶段。认不出来返回 null，调用方据此退回「照旧抓」。
 *
 * 这里只看列表级文案，粒度和 [fallbackFetchedParcel] 一致：列表说不清到达与否时，
 * 不拿它去和库里的阶段做等值判断。
 */
fun stageOfPrompt(prompt: String): Stage? = OrderStage.stageOf(prompt)?.takeUnless { it == Stage.DONE }

/**
 * 这次还要不要为 [orderSn] 抓详情。
 *
 * @param listStage 列表接口给出的阶段；null = 这一行没有可用状态（例如它只出现在订单列表里），
 *   此时只能靠「上次详情多久以前」来判断。
 * @param known 库里这个账号的件，键是订单号（见 AccountSyncEngine 的组法）。
 */
fun detailNeeded(
    orderSn: String,
    listStage: Stage?,
    known: Map<String, Parcel>,
    now: Long,
    maxAgeMs: Long = DETAIL_REUSE_MS,
): Boolean {
    val row = known[orderSn] ?: return true
    /* 已取：账号同步本来就会 Skip(ALREADY_PICKED)，详情页一个字都用不上。 */
    if (row.picked) return false
    /* 退休行重新出现在活跃列表里：它要复活，必须拿新数据，不能沿用旧行。 */
    if (row.retired) return true
    /* 没有运单号就还没有身份键；详情是唯一能补上它的地方。 */
    if (row.trackingNo.isNullOrBlank()) return true
    /* 到站了却没有码：详情页仍可能是唯一来源（码、格口号、点位都在那一段文字里）。 */
    if (row.code.isBlank() && row.stage == Stage.AT_STATION) return true
    /* 列表说阶段变了：哪怕只是文案变了，也值得进去确认。 */
    if (listStage != null && listStage != row.stage) return true
    val at = row.lastSyncedAt ?: return true
    return now - at >= maxAgeMs
}
