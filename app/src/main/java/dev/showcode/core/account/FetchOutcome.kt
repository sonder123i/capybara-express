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
     */
    data class Success(
        val parcels: List<FetchedParcel>,
        val historyOrderSns: List<String> = emptyList(),
        val seenOrderSns: Set<String> = emptySet(),
        val detailsFailed: Int = 0,
    ) : FetchOutcome

    data class Failure(val reason: String, val kind: FailureKind = FailureKind.TRANSIENT) : FetchOutcome
}

/** 一件详情都没解析出来时整批算失败：交出一个「0 新增」的成功等于骗人。 */
internal fun allDetailsFailed(attempted: Int, failed: Int): Boolean =
    attempted > 0 && failed >= attempted
