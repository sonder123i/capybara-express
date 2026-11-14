package dev.showcode.core.account

/**
 * 一条「可展示的物流轨迹」。
 *
 * 只服务详情页的时间线，不参与任何业务判定（阶段 / 取件码仍由 [TraceParser] 负责）。
 */
data class TraceRow(
    /** 阶段词，例如「派送中」「运输中」「已揽件」「已发货」；原文没写就是空串。 */
    val stage: String,
    /** 已缩短的时间文本，例如「11-10 09:42」；原文没带时间就是空串。 */
    val time: String,
    /** 明细正文（已剥掉阶段词与时间），多段用换行连接。 */
    val detail: String,
)

/**
 * 整页物流原文 → 时间线条目。
 *
 * 存在的理由：拼多多 / 京东的 `parcel.raw` 抓的是整个物流详情页的 innerText，
 * 除了轨迹还混着订单头（运单号、订单编号、收货地址）、商品卡、店铺统计（全店回购）、
 * 保障文案（正品险 / 假一赔十）和推荐位。详情页原来把 raw 整段 dump 出来，
 * 于是满屏无关商品介绍。这里只挑「带时间戳的轨迹行」，其余一律丢弃。
 *
 * 规则（只认 timestamp，不猜平台）：
 * - 每个时间戳开启一条轨迹，时间戳之前的文字（同一行）是阶段词，之后到下一个时间戳之间是明细；
 * - 第一条时间戳之前的所有内容（订单头）整段丢弃；
 * - 最后一条轨迹之后若出现「展开 / 全店回购 / 正品险 / 纯价格」这类营销或商品区，立即截止；
 * - 解析不出任何带时间的轨迹时返回空表，调用方回退到原文展示。
 */
private val TIMESTAMP = Regex(
    "(?:\\d{4}-\\d{1,2}-\\d{1,2}[ T]\\d{1,2}:\\d{2}(?::\\d{2})?)" +
        "|(?:\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)" +
        "|(?:(?:今天|昨天)\\s*\\d{1,2}:\\d{2}(?::\\d{2})?)",
)

/** 详情页里的营销/商品区：看到这些整块就到此为止，不许进轨迹。 */
private val TAIL_EXACT = Regex(
    "^(展开|收起|查看全部|更多|进店逛逛|相似推荐|猜你喜欢|商品详情|商品参数|服务承诺|已选|宝贝评价|店铺评分)$",
)
private val TAIL_TOKEN = Regex(
    "全店回购|正品险|假一赔十|已售|人付款|加入购物车|立即购买|运费险|七天无理由|7天无理由",
)
private val PRICE_ONLY = Regex("^[¥￥]?\\s*\\d+(\\.\\d+)?\\s*$")

private val FULL_TIME = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})[ T](\\d{1,2}):(\\d{2})")
private val MD_TIME = Regex("(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})")
private val RELATIVE_TIME = Regex("(今天|昨天)\\s*(\\d{1,2}):(\\d{2})")

private fun isTailStop(line: String): Boolean =
    TAIL_EXACT.matches(line) || TAIL_TOKEN.containsMatchIn(line) || PRICE_ONLY.matches(line)

private fun shortTime(raw: String): String {
    FULL_TIME.find(raw)?.let {
        val (_, m, d, h, min) = it.destructured
        return "%02d-%02d %02d:%02d".format(m.toInt(), d.toInt(), h.toInt(), min.toInt())
    }
    MD_TIME.find(raw)?.let {
        val (m, d, h, min) = it.destructured
        return "%02d-%02d %02d:%02d".format(m.toInt(), d.toInt(), h.toInt(), min.toInt())
    }
    RELATIVE_TIME.find(raw)?.let {
        val (label, h, min) = it.destructured
        return "%s %02d:%02d".format(label, h.toInt(), min.toInt())
    }
    return raw.trim()
}

private fun cleanStage(raw: String): String =
    raw.trim().trim('：', ':', '·', '-', '|', ' ', '\u3000')

/**
 * 淘宝的 raw 没有逐条时间（时间在抓取时是独立字段，没拼进正文），无法按时间戳切。
 * 退而求其次：按「阶段词」在正文里切——拼多多/京东的整页 innerText 同样含这些词，
 * 但它们有时间戳，走上面的时间戳路径，不会用到这里。
 *
 * 只在「前一个字符是空白/中文标点」且「后面紧跟空白或【（(」时才认作条目起点，
 * 避免把明细里的「快件已到达 南昌前湖」这种动宾短语切成新条目。
 */
private val STAGE_LABELS = listOf(
    "派送中", "派件中", "正在派送", "运输中", "已揽件", "已揽收", "已发货", "已发出",
    "已发往", "已到达", "已签收", "已取件", "已取走", "已投递", "待取件", "待自提",
    "待领取", "已到站", "到站", "中转", "清关中", "清关完成", "已装车", "仓库处理中",
    "已下单", "打包完成", "出库",
).distinct().sortedByDescending { it.length }

private val STAGE_LABEL = Regex(
    "(?:" + STAGE_LABELS.joinToString("|") + ")(?=$|[\\s【（(])",
)

private fun isEntryBoundary(c: Char): Boolean = c.isWhitespace() || c in "，。；、,."

private fun splitByStageLabels(raw: String): List<TraceRow> {
    val hits = STAGE_LABEL.findAll(raw)
        .filter { m -> m.range.first == 0 || isEntryBoundary(raw[m.range.first - 1]) }
        .toList()
    if (hits.isEmpty()) return emptyList()
    val rows = ArrayList<TraceRow>()
    hits.forEachIndexed { i, m ->
        val start = m.range.last + 1
        val end = if (i + 1 < hits.size) hits[i + 1].range.first else raw.length
        val detail = raw.substring(start, end).trim().trim('，', '。', ',', '.', ' ').trim()
        if (detail.isNotEmpty()) rows += TraceRow(stage = m.value, time = "", detail = detail)
    }
    return rows
}

fun parseTraceTimeline(raw: String): List<TraceRow> {
    if (raw.isBlank()) return emptyList()
    val lines = raw.lines().map { it.trim() }

    /* 记录每一条「带时间戳的行」及其时间戳匹配。 */
    val anchors = ArrayList<Pair<Int, MatchResult>>()
    lines.forEachIndexed { index, line ->
        val match = TIMESTAMP.find(line) ?: return@forEachIndexed
        anchors += index to match
    }
    if (anchors.isEmpty()) return splitByStageLabels(raw)

    /* 有些页面把「阶段词」和时间戳拆成两行：前一行是阶段，下一行只有时间。
       先把这种跨行阶段认出来，并把它从上一行的明细里剔除。 */
    val stageOf = HashMap<Int, String>()
    val consumed = HashSet<Int>()
    anchors.forEachIndexed { ai, pair ->
        val (lineIdx, match) = pair
        val inline = cleanStage(lines[lineIdx].substring(0, match.range.first))
        if (inline.isNotEmpty()) {
            stageOf[ai] = inline
        } else if (lineIdx > 0) {
            val previous = lines[lineIdx - 1]
            if (previous.isNotEmpty() && previous.length <= 12 && STAGE_LABEL.matches(previous)) {
                stageOf[ai] = cleanStage(previous)
                consumed += lineIdx - 1
            }
        }
    }

    val parsed = ArrayList<Pair<Long?, TraceRow>>()
    anchors.forEachIndexed { ai, pair ->
        val (lineIdx, match) = pair
        val line = lines[lineIdx]
        val stage = stageOf[ai].orEmpty()
        val timeRaw = match.value

        val parts = ArrayList<String>()
        val remainder = line.substring(match.range.last + 1).trim()
        if (remainder.isNotEmpty() && !isTailStop(remainder)) parts += remainder

        val nextAnchor = if (ai + 1 < anchors.size) anchors[ai + 1].first else lines.size
        var stopped = false
        var j = lineIdx + 1
        while (j < nextAnchor && !stopped) {
            if (j in consumed) {
                j++
                continue
            }
            val current = lines[j]
            if (current.isNotEmpty()) {
                if (isTailStop(current)) stopped = true else parts += current
            }
            j++
        }

        val detail = parts.joinToString("\n").trim()
        if (detail.isNotEmpty() || stage.isNotEmpty()) {
            parsed += (StageMachine.traceTimeMs(timeRaw) to TraceRow(stage, shortTime(timeRaw), detail))
        }
    }

    /* 新的在上（如参照图），时间读不出来时保持原顺序。 */
    return parsed
        .sortedWith(compareByDescending { it.first ?: Long.MIN_VALUE })
        .map { it.second }
}
