package dev.showcode.core.account

/**
 * 一轮平台同步已经走到哪一步。
 *
 * 抓取本身是串行的、有明确台阶的：先定位单，再逐单读详情。把它报出来，
 * 用户才知道那 20 秒不是「卡住了」。只报阶段与计数，不报平台内部细节。
 */
enum class SyncPhase {
    /** 读订单列表：还在确认「有哪些单」。 */
    ORDERS,

    /** 逐单读详情：`done`/`total` 是已经读过的单数。 */
    DETAILS,
}

data class SyncProgress(
    val phase: SyncPhase,
    val done: Int = 0,
    val total: Int = 0,
) {
    companion object {
        val Orders = SyncProgress(SyncPhase.ORDERS)
    }
}
