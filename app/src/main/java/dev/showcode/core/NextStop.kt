package dev.showcode.core

/**
 * 收工卡上的「下一站」。
 *
 * 只回答一个问题：这个点清空之后，下一件最该去哪儿。
 *
 * 排序**不另立一套**——直接用首页的 [pointOrder]（紧急度优先、件数次之）。
 * 两处排序一旦分叉，用户会看到首页说先去 A、收工卡说先去 B，而这个 App 的全部价值
 * 就是「不用自己判断先去哪」。
 *
 * [deadlineAt] 取该点最急那件的截止时刻；没有依据时为 null。
 * 和首页一样，不编造时效——宁可只说「2 件」，也不写一个猜出来的关门时间。
 */
data class NextStop(
    val key: String,
    val name: String,
    val count: Int,
    val deadlineAt: Long?,
)

/**
 * 当前点之后最该去的另一个点。没有别的点时返回 null（收工卡会改说「今天没有别的要拿了」）。
 *
 * [currentKey] 是 canonical key（`PickupPointGroup.key`），不是短信里的原始写法。
 * 排除当前点只是防御：调用时当前点通常已经从 groups 里消失了（它的件全被标成已取），
 * 但深链或竞态下它可能还在，而「下一站是原地」是一句谎话。
 */
fun nextStopAfter(
    currentKey: String,
    groups: List<PickupPointGroup>,
    now: Long,
): NextStop? = groups
    .asSequence()
    .filter { it.key != currentKey && it.items.isNotEmpty() }
    .minWithOrNull(pointOrder())
    ?.let { group ->
        NextStop(
            key = group.key,
            name = group.name,
            count = group.items.size,
            // groupByPoint 已按 parcelOrder 排过，第一件就是最急的那件。
            deadlineAt = deadlineOf(group.items.first(), now),
        )
    }
