package dev.showcode.core.account

import dev.showcode.core.CodeKind
import dev.showcode.core.Stage

/**
 * 轨迹文本 → 一件可入首页的账号件。
 *
 * 这一层是 P3 的判据中心：平台只给一段中文轨迹，状态、取件点、码、格口全要从这里读出来。
 * 它刻意不做任何「猜」：
 * - 没有取件点 → 不入首页（宁可少一张卡，不可伪造一个点）；
 * - 没有码且没有「单号后五位」→ 不冒充取件码；
 * - 「签收人：门口」不算已取，只有明确的本人/凭码取件才算。
 */
object TraceParser {

    private val CARRIER = Regex(
        "(中通快递|圆通速递|圆通快递|韵达快递|申通快递|顺丰速运|顺丰快递|极兔速递|" +
            "邮政快递|EMS|京东物流|德邦快递|百世快递|丰网速运)",
        RegexOption.IGNORE_CASE,
    )

    private val PICKUP_CODE = Regex(
        "(?:取件码|取货码|提货码|自提码|驿站码|取件验证码)[:：\\s]*([A-Za-z0-9][A-Za-z0-9\\-]{1,12})",
    )

    /** 「出示单号后五位 76416」：数字就在短语后面。 */
    private val SUFFIX_EXPLICIT = Regex("(?:出示)?单号后五位[:：\\s]*([0-9]{3,8})")

    /**
     * 只认带后缀的取件点，避免把「示例技术学院」这种长地址当成点名。
     *
     * 后一半 `·东门` 是可选的：像「菜鸟驿站·东门」这种点名，前面那截只匹配到「驿站」会把
     * 分店名丢掉；而「丰巢快递柜」又没有分店名。两种都要。
     */
    private val POINT = Regex(
        "([\\u4e00-\\u9fa5A-Za-z0-9]{2,20}(?:驿站|代收点|快递柜|自提柜|超市|便利店|门卫|服务点)" +
            "(?:·[\\u4e00-\\u9fa5A-Za-z0-9]{1,12})?)",
    )

    /** 先取「已暂存至 / 已投递…在 / 到」后面的那一段，再在那一段里找点名，避免把整条地址都当地名。 */
    private val POINT_VERB = Regex(
        "(?:已暂存至|已投放至|已投递，?收件人凭取件码在|已到|存放于|请到|请凭取件码在|到)" +
            "\\s*【?([^，。；\\n（(]{2,60})",
    )

    /** 括号里的点最可靠：`[菜鸟驿站](示例大学示例校区菜鸟驿站驿站)`。 */
    private val BRACKET_POINT = Regex(
        "[（(]([^）)]{2,40}(?:驿站|代收点|快递柜|自提柜|服务点)[^）)]*)[）)]",
    )

    /**
     * 中文方括号里的门店：`已从【兔喜生活-…示例校区店】取出`、`已送达【…示例校区店，电话：…】`。
     *
     * 只认紧跟在「已从 / 已送达」后面的那一处，`【客服热线：…】`、`【示例员：…】` 这类
     * 联系方式括号根本没有门店后缀，不会命中。内容必须收在门店类后缀上，且后缀后面
     * 紧接 `】` 或逗号——于是「…店，电话：166…」只取门店名，电话留在括号里被丢掉；
     * 也不会把「示例市站前店」这种名字截成「示例市站」。
     */
    private val CN_STORE_BRACKET = Regex(
        "(?:已从|已送达)\\s*【([^】，,。；\\n]{2,40}?" +
            "(?:驿站|便利店|超市|代收点|快递柜|自提柜|服务点|店|站))(?:】|[，,])",
    )

    /**
     * 括号里装的是人/联系方式，不是取件点。
     *
     * 真实轨迹里同一件会同时出现两处「X驿站」：
     * 「您的包裹已放入示例大学示例驿站…」与
     * 「快递员(示例驿站/13800000000)正在为您派送」。
     * 后者括号里是派件网点名和电话，按「括号优先」会把真实的取件点覆盖掉。
     *
     * 判据只用形状和紧邻的岗位词，不认平台、不认品牌：带电话、带斜杠分隔，
     * 或左括号前紧挨着「快递员 / 网点 / 客服 / 电话」这类词，就说明它是在说人。
     */
    private val PHONE_NUMBER = Regex("1[3-9]\\d{9}")

    private val CONTACT_BEFORE = Regex(
        "(?:快递员|派件员|投递员|揽收员|业务员|司机|网点|客服|热线|电话|营业时间)\\s*$",
    )

    /** 紧随其后的「/13xxxxxxxxx」：网点名后面直接挂电话，也是联系方式。 */
    private val CONTACT_AFTER = Regex("^\\s*[/／]\\s*1[3-9]\\d{9}")

    private const val CONTACT_LOOKBACK = 12

    /**
     * 通用描述：只有品牌/承运商/模板动词 + 类别词，没有具体门店标识。
     *
     * 「菜鸟驿站」「多多驿站」「包裹已从代收点」都是这一类。它们能兜底成卡，
     * 但只要原文里出现过带门店的写法（「示例大学多多驿站」），就必须用后者——
     * 否则用户会站在 A 点看着写着 B 点的卡。
     */
    private val GENERIC_POINT = Regex(
        "^(?:(?:菜鸟驿站|菜鸟|多多驿站|多多|兔喜生活|兔喜|妈妈驿站|丰巢|京东|顺丰|中通|圆通|韵达|申通|" +
            "邮政|EMS|极兔|德邦|百世|丰网|快递|快件|包裹|您|请|已|将|被|从|在|到|至|由|放入|取出|取走|" +
            "送达|签收|到达|领取|取件|暂存|代收|通知|联系|咨询|致电|找|问题|如遇|服务|的)[·\\-]?)*" +
            "(?:驿站|代收点|自提点|取件点|便民点|快递柜|自提柜|智能柜|服务点|生活超市|便利店|超市|" +
            "门店|商店|柜机|柜)$",
        RegexOption.IGNORE_CASE,
    )

    private val LOCKER = Regex("(\\d+号柜|第\\d+格|格口\\s*[A-Za-z0-9\\-]+)")

    /** 点名后缀。用于识别「一个候选里拼了不止一处点名」。 */
    private val POINT_SUFFIX = Regex("驿站|代收点|快递柜|自提柜|自提点|取件点|服务点|生活超市|便利店|超市")

    /** 轨迹时间戳：整页里既可能是「2026-10-20 10:19:12」，也可能是淘宝语料的「10-30 19:38」。 */
    private val TRACE_TIME = Regex(
        "(?:\\d{4}-\\d{1,2}-\\d{1,2}[ T]\\d{1,2}:\\d{2}(?::\\d{2})?)" +
            "|(?:\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)",
    )

    /** 整页切分结果：第一个时间戳之前的顶部内容，以及按时间戳切开的一条条轨迹。 */
    internal data class PageSplit(val header: String, val traces: List<TraceEntry>)

    /**
     * 把一段整页文本切成一条条轨迹。规则只用时间戳，不猜平台：
     *
     * - 每条轨迹都从时间戳开始，到下一条时间戳前结束；
     *   如拼多多的 `已签收2026-10-20 10:19:12 您的快件已取出签收…`，
     *   或淘宝库内拼接文本的 `10-13 19:54 您的快件已暂存至…`。
     * - 第一个时间戳之前的那一段叫 header：
     *   拼多多/京东那里是页面提示（取件码、单号后五位、点名片名），不得参与阶段判定；
     *   淘宝那里是订单摘要（完成取件 / 已签收），按生产里的 `status` 参与合并。
     * - 没有时间戳时不切分，header 是整页，traces 为空，调用方回退整页判定。
     */
    internal fun splitPage(text: String): PageSplit {
        val hits = TRACE_TIME.findAll(text).toList()
        if (hits.isEmpty()) return PageSplit(text, emptyList())
        val header = text.substring(0, hits.first().range.first)
        val traces = hits.mapIndexed { i, m ->
            val end = if (i + 1 < hits.size) hits[i + 1].range.first else text.length
            TraceEntry(text.substring(m.range.first, end).trim(), StageMachine.traceTimeMs(m.value))
        }
        return PageSplit(header, traces)
    }

    /**
     * 一段整页文本（PDD / 京东的 innerText）。
     *
     * 先把整页切成一条条带时间戳的轨迹，只让**最新一条**参与阶段判定：
     * 顶部固定提示（单号后五位 / 取件码 / 待取件）落在最新轨迹之外，不参与。
     * 最新一条认不出才逐级回退；一条都认不出时才回退整页关键词。
     * 淘宝这段文本的 header 是订单摘要，交给 [parseTraces] 的摘要规则合并。
     * 取件码 / 点 / 格口仍从整页原文读（rawText 保留整页），切分不丢码。
     */
    fun parse(
        platform: Platform,
        accountId: String,
        orderSn: String?,
        trackingNo: String,
        text: String?,
        at: Long,
        goods: GoodsInfo? = null,
    ): FetchedParcel? {
        val raw = text.orEmpty()
        val split = splitPage(raw)
        val timed = split.traces.filter { it.at != null }
        val traces = if (timed.isNotEmpty() && StageMachine.pickStage(timed).stage != Stage.UNKNOWN) {
            timed
        } else {
            listOf(TraceEntry(raw))
        }
        /* 淘宝整页里的 header 是订单摘要（完成取件 / 已签收）；
           拼多多的 header 是页面提示（取件码 / 待取件），不能当摘要。 */
        val summary = split.header.trim().takeIf { platform == Platform.TB && it.isNotBlank() }
        return parseTraces(
            platform = platform,
            accountId = accountId,
            orderSn = orderSn,
            trackingNo = trackingNo,
            traces = traces,
            at = at,
            goods = goods,
            status = summary,
            rawText = text,
        )
    }

    /**
     * 与生产链路同源的阶段决策，供语料审计逐条断言用（含用了第几条、为什么）。
     * 淘宝按 header（订单摘要）+ 时间戳轨迹合并，生产走结构化 [parseTraces]；
     * 拼多多/京东只看轨迹，header 是页面提示，不参与。
     */
    internal fun decideStage(text: String, platform: Platform): StageMachine.StagePick {
        val split = splitPage(text)
        val timed = split.traces.filter { it.at != null }
        val tracePick = timed.takeIf { it.isNotEmpty() }?.let(StageMachine::pickStage)

        if (platform != Platform.TB) {
            if (tracePick != null && tracePick.stage != Stage.UNKNOWN) return tracePick
            val full = StageMachine.pickStage(listOf(TraceEntry(text)))
            return if (tracePick != null) {
                StageMachine.StagePick(full.stage, null, null, "最新轨迹判不出，回退全页关键词")
            } else {
                full
            }
        }

        val header = split.header.trim()
        val statusPick = StageMachine.pickStage(listOf(TraceEntry(header)))
        val traceStage = tracePick?.stage ?: Stage.UNKNOWN
        val statusStage = statusPick.stage
        val stage = when {
            traceStage == Stage.DONE -> Stage.DONE
            statusStage == Stage.DONE &&
                (traceStage != Stage.AT_STATION || StageMachine.isExplicitPickup(header)) -> Stage.DONE
            traceStage != Stage.UNKNOWN -> traceStage
            else -> statusStage
        }
        val reason = if (tracePick != null && stage == traceStage) {
            tracePick.reason
        } else {
            "摘要「${header.take(20)}」+ 轨迹：${tracePick?.reason ?: "无时间戳轨迹"}"
        }
        return StageMachine.StagePick(stage, tracePick?.index, tracePick?.at, reason)
    }

    /**
     * 结构化轨迹（淘宝 `multiStage` 的每一条单独一入）。
     * 状态由 [StageMachine.stageFromTraces] 判；点/码仍从全部轨迹里读，避免最新一条只写「已签收」时丢掉驿站与取件码。
     */
    fun parseTraces(
        platform: Platform,
        accountId: String,
        orderSn: String?,
        trackingNo: String,
        traces: List<TraceEntry>,
        at: Long,
        goods: GoodsInfo? = null,
        status: String? = null,
        /** 整页原文。传了就优先用它做 raw/head（保住顶部取件码与点名），阶段仍只看 traces。 */
        rawText: String? = null,
    ): FetchedParcel? {
        val summary = status.orEmpty().trim()
        val joined = (traces.map { it.text.trim() } + summary)
            .filter { it.isNotEmpty() }.joinToString(" ").trim()
        val raw = rawText?.takeIf { it.isNotBlank() }?.trim() ?: joined
        if (raw.isEmpty()) return null
        val head = raw.take(1200)
        /* 轨迹按时间/单调规则判；订单列表的状态只是汇总：
           它说「交易成功 / 已签收」时可以提前归档，但它说「物流派件中」时不许盖住更新的「完成取件」。 */
        val traceStage = StageMachine.stageFromTraces(traces)
        val statusStage = StageMachine.stageOfTrace(summary)
        /* 订单摘要只是汇总：它可以补上轨迹缺失的终态，也可以压过更早的派送/运输，
           但**不能单独覆盖一条更新的到站轨迹**。摘要说「已签收」而轨迹说「已放入驿站待取」时，
           尊重轨迹，留在待取；只有摘要本身是明确取件（本人取件/完成取件）才归档。 */
        val stage = when {
            traceStage == Stage.DONE -> Stage.DONE
            statusStage == Stage.DONE &&
                (traceStage != Stage.AT_STATION || StageMachine.isExplicitPickup(summary)) -> Stage.DONE
            traceStage != Stage.UNKNOWN -> traceStage
            else -> statusStage
        }
        /* 运输中的件没有「取件点」；从地址里抓出来的那截是收货地址，不能当点。 */
        val pointName = if (stage == Stage.AT_STATION || stage == Stage.DONE) pointOf(head) else null
        val carrier = CARRIER.find(head)?.groupValues?.getOrNull(1)
            ?: CARRIER.find(raw)?.groupValues?.getOrNull(1)
            ?: "快递"
        val lockerNo = LOCKER.find(head)?.groupValues?.getOrNull(1)?.trim()
        /* 只有真正判成完成态才算「明确取件」。旧轨迹里出现「已取出」但最新轨迹仍是待取件时，
           不能把 explicitPickup 置真——否则合并层会以为这件已经取走了。
           词表只在 StageMachine 里有一份：祈使句在那里归 AT_STATION，不会再被当成已取。 */
        val explicit = stage == Stage.DONE &&
            (traces.any { StageMachine.isExplicitPickup(it.text) } ||
                StageMachine.isExplicitPickup(summary))

        val explicitCode = PICKUP_CODE.find(head)?.groupValues?.getOrNull(1)
        val (code, kind) = when {
            explicitCode != null -> explicitCode to CodeKind.PICKUP
            else -> {
                val digits = SUFFIX_EXPLICIT.find(head)?.groupValues?.getOrNull(1)
                val suffix = digits ?: trackingNo.takeLast(5)
                if (digits != null || "单号后五位" in head) suffix to CodeKind.SUFFIX
                else "" to CodeKind.NONE
            }
        }

        /* 在途件不要求运单号：淘宝常见「已发货但运单还在路上」，只要有商品标题就能进在途区，
           空运单号由首页显示「运单号待出」，下次同步补齐。
           待取件仍守原门槛：有取件码/后五位，或者有运单号；光有商品标题不许进（那是「什么都进」）。 */
        val inTransitAllowed = (stage == Stage.IN_TRANSIT || stage == Stage.DELIVERING) &&
            !goods?.title.isNullOrBlank()
        val pickupCodeAllowed = stage == Stage.AT_STATION && code.isNotBlank() &&
            kind != CodeKind.NONE
        if (trackingNo.isBlank() && !inTransitAllowed && !pickupCodeAllowed) return null

        return FetchedParcel(
            platform = platform,
            accountId = accountId,
            trackingNo = trackingNo,
            orderSn = orderSn,
            carrier = carrier,
            stage = stage,
            pointName = pointName,
            lockerNo = lockerNo,
            code = code,
            codeKind = kind,
            explicitPickup = explicit,
            raw = raw,
            at = at,
            goodsTitle = goods?.title,
            goodsImage = goods?.image,
            goodsPrice = goods?.price,
            shopName = goods?.shop,
            orderedAt = goods?.orderedAt,
        )
    }

    private fun pointOf(head: String): String? {
        /* 括号里的点优先，但要先筛掉「括号里是人」的那些：
           它们的左边是「快递员」「网点」「电话」，或者括号里本身就是「驿站名/手机号」。 */
        val bracketNames = BRACKET_POINT.findAll(head)
            .filterNot { m -> isContactReference(head, m.range.first, m.range.last + 1, m.groupValues[1]) }
            .map { cleanPointName(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .toList()
        /* 通用描述（「菜鸟驿站」「多多驿站」「包裹已从代收点」）只有在没有具体门店名时才可以用。 */
        bracketNames.firstOrNull { !isGenericPoint(it) }?.let { return it }

        /* 暂存/投递动词后面那一段，比整条轨迹干净得多。 */
        val segmentNames = POINT_VERB.find(head)?.groupValues?.getOrNull(1)?.trim()
            ?.let(::pointNamesIn).orEmpty()
        segmentNames.firstOrNull { !isGenericPoint(it) }?.let { return it }

        /* 兜底仍是原文第一处点名：地址栏里的同名点很常见，不能拿后面那句去改前一句的真实点名。 */
        val first = POINT.findAll(head)
            .firstOrNull { m -> !isContactReference(head, m.range.first, m.range.last + 1, m.groupValues[1]) }
            ?.let { cleanPointName(it.groupValues[1]) }
            ?.takeIf { it.isNotBlank() }
        /* 兜底只认出了「包裹已从代收点」这类通用描述时，中文方括号里的门店更具体：
           `已从【兔喜生活…示例校区店】取出` 必须用门店名。已经认出具体门店的行一律不动——
           旧数据里的「示例校区驿站 / 多多驿站 / 示例市」不能被重算。 */
        if (first == null || isGenericPoint(first)) {
            cnStoreOf(head)?.let { return it }
        }
        return first ?: bracketNames.firstOrNull() ?: segmentNames.firstOrNull()
    }

    /** 中文方括号里的门店名（已剥掉动词、已去掉「，电话：…」）。 */
    private fun cnStoreOf(head: String): String? = CN_STORE_BRACKET.findAll(head)
        .filterNot { m -> isContactReference(head, m.range.first, m.range.last + 1, m.groupValues[1]) }
        .map { cleanPointName(it.groupValues[1]) }
        .firstOrNull { it.isNotBlank() && !isGenericPoint(it) }

    /** 括号里是派件网点 + 联系方式，不是取件点。 */
    private fun isContactReference(text: String, start: Int, end: Int, value: String): Boolean {
        if (PHONE_NUMBER.containsMatchIn(value)) return true
        if ('/' in value || '\\' in value) return true
        if (CONTACT_AFTER.containsMatchIn(text.substring(end))) return true
        val before = text.substring(maxOf(0, start - CONTACT_LOOKBACK), start)
        return CONTACT_BEFORE.containsMatchIn(before)
    }

    private fun pointNamesIn(text: String): List<String> = POINT.findAll(text)
        .filterNot { m -> isContactReference(text, m.range.first, m.range.last + 1, m.groupValues[1]) }
        .map { cleanPointName(it.groupValues[1]) }
        .filter { it.isNotBlank() }
        .toList()

    internal fun isGenericPoint(name: String): Boolean = GENERIC_POINT.matches(name.trim())

    /**
     * 轨迹里常把「菜鸟驿站」和「驿站」叠在一起：
     * 「菜鸟驿站示例大学示例校区菜鸟驿站驿站」→「示例大学示例校区菜鸟驿站」。
     * 只在开头是品牌、且去掉后仍保留驿站类后缀时才剥前缀；
     * 「菜鸟驿站·东门」这种首词就是分店标识的要留着。
     */
    internal fun cleanPointName(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("^(您已在|您在|您的快件已到|您的包裹已到|您已到达)"), "")
        /* POINT 的 2-20 字前缀会把句中的到达动词一起吞进来：
           「您的包裹已放入示例大学多多驿站」会整段成点名。只剥这一层动词，不动后面的门店名。
           「已从」与「放入 / 送达 / 到达」同理，句首动词不许进点名。 */
        s = s.replace(
            Regex(
                "^(?:您的|你的)?(?:包裹|快件|快递)?(?:已经?|将|被)?(?:放入|暂存至|暂存|从|到达|投递至|投递到|" +
                    "投递|送达|放到|投放到|投放|存入|送到|送至)",
            ),
            "",
        )
        if (s.startsWith("菜鸟驿站") && s.length > "菜鸟驿站".length) {
            val rest = s.removePrefix("菜鸟驿站").trimStart('·', '-', ' ')
            if (Regex("驿站|代收点|快递柜|自提柜|服务点").containsMatchIn(rest)) s = rest
        }
        /* 页头会把「点名 + 方位描述」不加标点拼成一串：
           「示例大学多多驿站示例大学示例校区旁多多驿站」。
           同一候选里出现第二处点名后缀时，只保留第一处完整点名；
           否则同一点被拼成长串，point_key 变化会裂卡。 */
        val firstSuffix = POINT_SUFFIX.find(s)
        if (firstSuffix != null && POINT_SUFFIX.containsMatchIn(s.substring(firstSuffix.range.last + 1))) {
            s = s.substring(0, firstSuffix.range.last + 1)
        }
        s = s.replace(Regex("(驿站|代收点|快递柜|自提柜|服务点)\\1"), "$1")
        s = s.replace("驿站驿站", "驿站")
            .replace("代收点代收点", "代收点")
            .replace("快递柜快递柜", "快递柜")
            .replace("自提柜自提柜", "自提柜")
        return s.trim()
    }
}
