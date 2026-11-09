package dev.showcode.core.account

import dev.showcode.core.Stage

/**
 * 订单列表里的状态文案 → 阶段。
 *
 * 这张表是**订单列表文案的唯一入口**：淘宝、拼多多、京东的列表状态、
 * 兜底条目都必须走这里。以前每个 fetcher 各写一份词表，结果「已揽件」这种
 * 真实存在的写法被漏掉，件被静默判成未知。
 *
 * 详情页轨迹仍由 [StageMachine] 判定（那里是整段轨迹，语序与上下文不同）；
 * 两张表若新增同义写法，要一起补，避免再次漂移。
 *
 * 顺序有语义：
 * 1. 先看「还在点上」（待取件 / 已到驿站 / 取件码…）——这些是最强的待取信号；
 * 2. 再看终态（已签收 / 交易成功…）；
 * 3. 再看派送中；
 * 4. 最后看运输中（含已揽件 / 揽收 / 待收货）。
 *
 * 认不出来返回 null，调用方必须把它当「不知道」而不是「已结束」。
 */
object OrderStage {

    /** 还在驿站/柜里等取。 */
    private val HOLDING = Regex(
        /* `待取(?!消)`：待取件/待取货/待取都要，但「待取消」不是取件信号。 */
        "待取(?!消)|待自提|待领取|" +
            /* 到站文案常带回声（「已到达菜鸟驿站」「已到【示例驿站】」），
               允许中间夹 8 个字，否则会被泛化的「已到达」判成在途。 */
            "已到[^，。；]{0,8}(驿站|快递柜|代收点|自提点|服务点)|" +
            "到达[^，。；]{0,8}(驿站|快递柜|代收点|自提点|服务点)|" +
            "已放入|已放到|已暂存|已入柜|取件码|取货码|提货码|自提码",
    )

    /** 终态：已经拿走或交易结束。 */
    private val DONE = Regex(
        "已签收|签收成功|已代收|已投递|交易成功|订单完成|已完成|待评价|已收货|" +
            /* 「待确认收货 / 等待确认收货」还活着，不能因为含「确认收货」就归档。 */
            "(?<!待)确认收货|" +
            "已取件|已取走|本人取件|本人签收|完成取件|取件完成|取件成功",
    )

    private val DELIVERING = Regex("派送中|派件中|正在派送|待派送")

    /**
     * 运输中。`揽件`/`揽收` 必须单独列——淘宝真实文案写的是「已揽件」，
     * 而「已揽收」匹配不到「已揽件」，这正是之前漏单的根因之一。
     */
    private val IN_TRANSIT = Regex(
        "待确认收货|等待确认收货|待收货|运输中|已发货|已发出|已发往|已揽收|已揽件|揽件|揽收|已装车|" +
            "转运中|中转|清关|异地|已到达",
    )

    fun stageOf(prompt: String): Stage? {
        val text = prompt.trim()
        if (text.isEmpty()) return null
        return when {
            HOLDING.containsMatchIn(text) -> Stage.AT_STATION
            DONE.containsMatchIn(text) -> Stage.DONE
            DELIVERING.containsMatchIn(text) -> Stage.DELIVERING
            IN_TRANSIT.containsMatchIn(text) -> Stage.IN_TRANSIT
            else -> null
        }
    }

    /** 这条状态还值得跟进吗？DONE 与认不出的都不算。 */
    fun isActive(prompt: String): Boolean {
        val stage = stageOf(prompt) ?: return false
        return stage != Stage.DONE
    }
}
