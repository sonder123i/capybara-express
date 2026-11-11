package dev.showcode.core.account

/**
 * 失败的两类。判据只看证据形状，不看平台名：
 *
 * - [AUTH]：页面/接口把我们带到登录页或通行证（URL 有 login/passport），
 *   或明确回了 401/403、会话失效标记。只有这一类才置 `EXPIRED`。
 * - [TRANSIENT]：断网、超时、5xx、页面结构看不懂、脚本没响应。
 *   账号保持原状态，过一会儿再试就行。
 */
enum class FailureKind { AUTH, TRANSIENT }

/**
 * 一次账号抓取的结局，必须显式区分：
 *
 * - [Success] 且 `parcels` 非空：拿到了新件；
 * - [Success] 且 `parcels` 为空：链路确实通了，但这次没有件——这是成功；
 * - [Failure]：链路没通。绝不能折成「成功 + 0 新增」，否则用户会把登录过期
 *   读成「今天没快递」。
 *
 * 失败原因要能直接面向用户，所以是短句，不是异常类名。
 */
sealed interface FetchOutcome {
    /**
     * @param historyOrderSns 本次明确看到「已完成/已签收/已出柜/已取消/退款」的订单号。
     * 抓取侧不再入库它们，但库里可能还留着旧版本插入的同号卡片，合并时要一并退休。
     * @param seenOrderSns 本次在「待收货」和订单列表里实际见到的全部订单号。
     * 库里之前入库、这次彻底不见的在途行属于残留轨迹，要退休；空集表示抓取不完整，不做退休。
     * @param detailsFailed 单件详情没抓到的数量。它不影响整批已拿到的件，但必须说出来，
     * 不能把「N 件没抓到」吞成「没有新件」。
     * @param retireOrderSns 确认「不是包裹」的订单号（卡券/虚拟商品）。库里同号的旧卡要退休，
     * 但这不属于「跳过」，不进同步文案的跳过统计。
     */
    data class Success(
        val parcels: List<FetchedParcel>,
        val historyOrderSns: List<String> = emptyList(),
        val seenOrderSns: Set<String> = emptySet(),
        val detailsFailed: Int = 0,
        /** 用列表状态兜底、轨迹还没拿到的件数。必须说出来，不能冒充完整轨迹。 */
        val fallbackCount: Int = 0,
        val retireOrderSns: List<String> = emptyList(),
    ) : FetchOutcome

    data class Failure(val reason: String, val kind: FailureKind = FailureKind.TRANSIENT) : FetchOutcome
}

/**
 * 这一批详情是不是「全军覆没」。
 *
 * 判据要求**至少两单**：只有一单时，它没解析出来更可能是这一单本身没有物流
 * （虚拟商品、兑换码、还没揽收），不足以证明整条链路坏了。那种情况交
 * `Success(detailsFailed = 1)`，文案会说「1 件详情没抓到」——比把整个账号标成
 * 「没同步上」诚实，也不会让首页的通道点无故变琥珀。
 */
internal fun allDetailsFailed(attempted: Int, failed: Int): Boolean =
    attempted >= 2 && failed >= attempted

/**
 * 详情批次是否真的失败：必须**尝试过**至少一单，且没有任何包裹结果、也没有识别出退回单。
 *
 * attempted=0 时必须返回 false：候选可能全是「状态没变、库里已有」的单，
 * 这一轮一条详情都没抓。拿 candidates 当分母会误报「0 件详情都没抓到」，
 * 把一次正常的「没有新件」变成同步失败。
 */
internal fun detailBatchFailed(attempted: Int, parcels: Int, returns: Int): Boolean =
    attempted > 0 && parcels == 0 && returns == 0
