package dev.showcode.core.account

import dev.showcode.core.Stage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 一条物流轨迹。`at` 是平台给出的时间；拿不到就留 null。
 *
 * 平台数组的先后顺序并不可信（淘宝的 `multiStage` 就出现过把「派送中」排在「已签收」之前），
 * 所以状态判定只看这份结构，不再依赖字符串拼接后的位置。
 */
data class TraceEntry(val text: String, val at: Long? = null)

/**
 * PDD / 淘宝 / 京东共用的状态机。词表只有这一份，任何平台都不许另写一套。
 *
 * 分层与不可逆规则：
 * - 本人取件 / 本人签收 / 已取件 / 已取走 → [Stage.DONE]（可直接归档）；
 * - 驿站/快递柜代收、暂存、入柜、给了取件码或后五位 → [Stage.AT_STATION]（仍要用户去取）；
 * - 已签收 / 签收成功 / 已投递 / 交易成功 → [Stage.DONE]（没有取件证据就是终态）；
 * - 派送中：已给出取件点或取件码 → [Stage.AT_STATION]，否则 [Stage.DELIVERING]；
 * - 已揽收 / 运输中 / 中转 / 清关 / 异地 → [Stage.IN_TRANSIT]。
 *
 * 「代收」与「取件」的先后靠轨迹本身区分：一条轨迹里同时出现「已签收」和「已放入驿站 /
 * 取件码」时，按「驿站代收未取」处理，留在待取；只有明确本人取件才归档。
 * 「包裹已签收（凭取件码）」这类代收点代签同理，见 [isAgentSigned]。
 */
object StageMachine {

    /**
     * 明确拿走了件的词。注意不能用光秃秃的「已取」——那会命中「已取消」。
     * 「完成取件 / 取件完成」是淘宝轨迹里的真实写法（真机原文：您已在…完成取件）。
     * 「已从【兔喜生活…】取出」是同一条链路在兔喜代收点的另一种写法
     * （真机原文：包裹已从代收点取出 / 您的快件已从【…】取出）——
     * 前后文里还会跟着「如遇取件码问题请联系…」，那是事后提示，不是祈使句，
     * 所以这里只认「已从…取出」这个动作本身，不吃「取件码」那一段。
     *
     * 「已取出 / 已取出签收 / 已领取」是三种真实完成态写法。
     * 「领取」只在紧接「已」的字面量里算完成：`请凭取件码领取`、`请到店领取`
     * 都是祈使句，仍归 [HOLDING]，绝不能因为出现「领取」就归档。
     */
    private val PICKED = Regex(
        "已取件|已取走|本人取件|本人签收|完成取件|取件完成|取件成功|已完成取件|" +
            "已取出签收|已取出|已领取|已从[^，。；\\n]{0,40}取出",
    )

    /**
     * 驿站/柜代收、暂存、入柜、给出取件码：这些都说明件还在点上。
     *
     * 「凭取件码取件」「驿站取件」是祈使句（原文是「请凭取件码去取件」），不是完成态。
     * 它们必须归到这里；前一版把它们放进 [PICKED]，于是待取件在同步时被直接归档。
     */
    private val HOLDING = Regex(
        "待取件|待自提|待领取|已到驿站|已到快递柜|已放入驿站|已放入快递柜|已暂存|已入柜|" +
            "存放于|请及时领取|单号后五位|取件码|取货码|提货码|自提码|驿站码|" +
            "凭取件码取件|驿站取件",
    )

    /** 真正把件拿走的完成态。祈使句不算，供 TraceParser 与无时间戳规则共用。 */
    fun isExplicitPickup(text: String): Boolean = text.isNotBlank() && PICKED.containsMatchIn(text.take(1200))

    /** 终态/归档。 */
    private val TERMINAL = Regex(
        "已签收|签收成功|已代收|已投递|驿站签收|快递柜签收|交易成功|订单完成|已完成|" +
            "待评价|已收货|确认收货",
    )

    /**
     * 代收点写进轨迹的签收词。刻意不收「确认收货 / 待评价」——那是订单侧按钮文案，
     * 详情页 innerText 里经常混着它们，不能据此说「代收点已签收」。
     */
    private val AGENT_SIGNED = Regex("已签收|签收成功|已代签收")

    /**
     * 这段原文是不是「代收点代签、件还在驿站」。
     *
     * 极兔真机原文：`包裹已签收！（凭取件码）如有问题请电联：代收点 138…/示例员-示例学院 …`。
     * 这类「已签收」是代收点入库后的系统签收（存放超过 5 天也会自动出这条），
     * 件通常仍躺在驿站、凭码可取；所以阶段仍是 [Stage.AT_STATION]，不能归档。
     *
     * 详情页用它把「平台显示已签收」和「仍需去取」同时讲清楚，
     * 免得用户把 App 的「待取件」当成误判。
     */
    fun isAgentSigned(text: String): Boolean {
        val head = text.take(1200)
        if (head.isBlank() || PICKED.containsMatchIn(head)) return false
        return AGENT_SIGNED.containsMatchIn(head) && HOLDING.containsMatchIn(head)
    }

    /**
     * 最新一条是代签文案，但它和上一次「待取件」隔了很久：按取件后的签收算。
     *
     * 代收点入库时代签和「待取件」通常同一时刻前后；用户真的取走后，平台会在
     * 原来那条待取件之后很久再补一条「已签收（凭取件码）」。这是目前能从文本里
     * 区分两者的唯一可靠信号。
     */
    private fun isLatePickupAfterStation(latest: TraceEntry, older: List<TraceEntry>): Boolean {
        val latestAt = latest.at ?: return false
        if (!isAgentSigned(latest.text)) return false
        val previous = older.firstOrNull { entry ->
            entry.at != null && !isAgentSigned(entry.text) && (
                stageOfTrace(entry.text) == Stage.AT_STATION ||
                    entry.text.contains("待取件") ||
                    entry.text.contains("代收点") ||
                    entry.text.contains("暂存")
                )
        } ?: return false
        /* 只认「刚从驿站取走」这条新签收：几天/几十天前就存在的旧签收
           多半是平台自动代签，没取件证据，仍应留在待取。 */
        val recent = latestAt >= System.currentTimeMillis() - RECENT_SIGN_WINDOW_MS
        return recent && latestAt - previous.at!! >= AGENT_SIGNED_PICKUP_GAP_MS
    }

    private val DELIVERING = Regex("派送中|派件中|正在派送")

    private val IN_TRANSIT = Regex(
        "已揽收|已揽件|揽件|揽收|运输中|已发出|已发往|已发货|已装车|转运中|中转|清关|异地|已到达",
    )

    /** 取件点/取件码的迹象：用于「派送中」是否已经落到点上。 */
    private val PICKUP_INFO = Regex(
        "驿站|代收点|快递柜|自提柜|服务点|取件码|取货码|提货码|自提码|驿站码|单号后五位",
    )

    /** 代签文案之后隔这么久才出现的「已签收」，按用户取件处理。 */
    private const val AGENT_SIGNED_PICKUP_GAP_MS = 12L * 60 * 60 * 1000

    /** 只有最近出现的新签收才可能是用户刚取走；旧签收不做这个推断。 */
    private const val RECENT_SIGN_WINDOW_MS = 14L * 24 * 60 * 60 * 1000

    /** 一次阶段决策的来龙去脉：用了第几条（1 = 最新）、时间、为什么。 */
    internal data class StagePick(
        val stage: Stage,
        val index: Int? = null,
        val at: Long? = null,
        val reason: String,
    )

    /**
     * 从一组轨迹给出阶段，并说明「用了第几条、为什么」。
     *
     * - 全部都有时间：最新一条算数；最新一条认不出状态时再往前退。
     * - 有任何一条没有时间：不按「最大推进程度」选，直接取最靠后的一档会让**旧的**通用终态
     *   永久压住后来的到站轨迹。改用下面的顺序规则：
     *   1. 任意一条是明确取件 → DONE（不可逆，先定调）；
     *   2. 同时存在到站与「非明确取件的终态」→ AT_STATION。顺序不可靠时宁可留在待取，
     *      也不把一个可能还没取的件归档；
     *   3. 其余情况看整个可识别序列的单调方向：递增说明数组是旧→新，取最后一条；
     *      递减说明是新→旧，取第一条；判断不了就按平台默认「最新在前」取第一条。
     */
    internal fun pickStage(traces: List<TraceEntry>): StagePick {
        val usable = traces.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return StagePick(Stage.UNKNOWN, reason = "没有可用轨迹")

        val timed = usable.filter { it.at != null && it.at!! > 0 }
        if (timed.isNotEmpty() && timed.size == usable.size) {
            val sorted = timed.sortedByDescending { it.at!! }
            sorted.forEachIndexed { i, entry ->
                val rawStage = stageOfTrace(entry.text)
                /* 代收点先写「已签收（凭取件码）」，用户实际取走时平台又补一条同样的
                   「已签收」。两条文字一样，只能看时间：最新的签收离上一次「待取件」
                   已经隔了半天以上，说明这是取件后的签收，不是入库代签。 */
                val stage = if (i == 0 && rawStage == Stage.AT_STATION &&
                    isLatePickupAfterStation(entry, sorted.drop(1))
                ) {
                    Stage.DONE
                } else {
                    rawStage
                }
                if (stage != Stage.UNKNOWN) {
                    val reason = if (i == 0) {
                        "最新第1条（时间 ${entry.at}）"
                    } else {
                        "前 $i 条认不出，回退到第 ${i + 1} 条（时间 ${entry.at}）"
                    }
                    return StagePick(stage, i + 1, entry.at, reason)
                }
            }
            return StagePick(
                Stage.UNKNOWN,
                null,
                sorted.firstOrNull()?.at,
                "最新 ${sorted.size} 条都认不出",
            )
        }

        if (usable.any { isExplicitPickup(it.text) }) {
            return StagePick(Stage.DONE, reason = "无时间戳：存在明确取件")
        }

        val recognized = usable.map { stageOfTrace(it.text) }.filter { it != Stage.UNKNOWN }
        if (recognized.isEmpty()) return StagePick(Stage.UNKNOWN, reason = "无时间戳：一条都认不出")

        if (recognized.contains(Stage.AT_STATION) && recognized.contains(Stage.DONE)) {
            return StagePick(Stage.AT_STATION, reason = "无时间戳：到站与终态并存，留在待取")
        }

        val increasing = recognized.zipWithNext().all { (a, b) ->
            progression(a) <= progression(b)
        }
        val decreasing = recognized.zipWithNext().all { (a, b) ->
            progression(a) >= progression(b)
        }
        return when {
            increasing && !decreasing ->
                StagePick(recognized.last(), reason = "无时间戳：数组旧→新，取最后一条")
            decreasing && !increasing ->
                StagePick(recognized.first(), reason = "无时间戳：数组新→旧，取第一条")
            else ->
                StagePick(recognized.first(), reason = "无时间戳：顺序判不了，按默认最新在前取第一条")
        }
    }

    fun stageFromTraces(traces: List<TraceEntry>): Stage = pickStage(traces).stage

    /**
     * 只用来判断数组朝向，不参与最终选值。
     * 这正是与旧实现的分界：旧实现用这张表取最大值，于是「已签收」永远赢。
     */
    private fun progression(stage: Stage): Int = when (stage) {
        Stage.IN_TRANSIT -> 1
        Stage.DELIVERING -> 2
        Stage.AT_STATION -> 3
        Stage.DONE -> 4
        else -> 0
    }

    /** 单条轨迹的状态。优先看「有没有拿走」，再看「是不是还在点上」。 */
    fun stageOfTrace(text: String): Stage {
        if (text.isBlank()) return Stage.UNKNOWN
        val head = text.take(1200)
        return when {
            PICKED.containsMatchIn(head) -> Stage.DONE
            HOLDING.containsMatchIn(head) -> Stage.AT_STATION
            TERMINAL.containsMatchIn(head) -> Stage.DONE
            DELIVERING.containsMatchIn(head) ->
                if (PICKUP_INFO.containsMatchIn(head)) Stage.AT_STATION else Stage.DELIVERING
            IN_TRANSIT.containsMatchIn(head) -> Stage.IN_TRANSIT
            else -> Stage.UNKNOWN
        }
    }

    /**
     * 轨迹时间。只认带时刻的写法；只有日期时返回 null——
     * 同一天的多条轨迹全落在 0 点会制造假的「更新」，不如交给 [stageFromTraces] 的单调规则。
     */
    fun traceTimeMs(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        text.toLongOrNull()?.let {
            return when {
                it <= 0L -> null
                it < 100_000_000_000L -> it * 1000L
                else -> it
            }
        }
        val full = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})[ T]?(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
            .find(text)
        if (full != null) {
            val (y, m, d, h, min, s) = full.destructured
            return runCatching { localMs(y.toInt(), m.toInt(), d.toInt(), h.toInt(), min.toInt(), s.toIntOrNull() ?: 0) }
                .getOrNull()
        }
        val monthDay = Regex("(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
            .find(text)
        if (monthDay != null) {
            val (m, d, h, min, s) = monthDay.destructured
            return runCatching {
                localMs(LocalDate.now().year, m.toInt(), d.toInt(), h.toInt(), min.toInt(), s.toIntOrNull() ?: 0)
            }.getOrNull()
        }
        return null
    }

    private fun localMs(y: Int, m: Int, d: Int, h: Int, min: Int, s: Int): Long =
        LocalDateTime.of(y, m, d, h, min, s)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
