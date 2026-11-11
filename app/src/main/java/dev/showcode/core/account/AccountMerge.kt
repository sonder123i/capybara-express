package dev.showcode.core.account

import dev.showcode.core.CodeKind
import dev.showcode.core.Parcel
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import dev.showcode.core.Stage
import dev.showcode.core.pointKey

/** 一次账号同步对库里某一行的处置。插入/更新/Skip 全部可单测，Repository 只执行。 */
sealed interface AccountSyncAction {
    val item: FetchedParcel

    data class Insert(
        override val item: FetchedParcel,
        val pointKey: String,
        val pointKind: PointKind,
    ) : AccountSyncAction

    data class Update(
        override val item: FetchedParcel,
        val id: Long,
        val pointKey: String,
        val pointName: String,
        val pointKind: PointKind,
        /** 合并后的最终码；冲突时保留原值。 */
        val code: String,
        val codeKind: CodeKind,
        val stage: Stage,
        /** 只有轨迹明确「本人取件 / 凭码取件」才为 true。 */
        val markPicked: Boolean,
    ) : AccountSyncAction

    data class Skip(
        override val item: FetchedParcel,
        val reason: SkipReason,
    ) : AccountSyncAction
}

/** 有运单号但认不出点名时的归组名；不让它和真实点名混在一起。 */
const val UNLOCATED_POINT_NAME = "驿站未定位"

enum class SkipReason {
    /** 轨迹没认出取件点。不入首页，不伪造一个点。 */
    NO_POINT,

    /** 没有取件码，也没有「单号后五位」。 */
    NO_CODE,

    /** 这个件已经取过了，账号同步不许复活它。 */
    ALREADY_PICKED,

    /** 还在运输/派送；没有到站就不进待取列表。 */
    NOT_AT_STATION,
}

/** 还在走流程的订单状态。历史单（已签收/待评价/交易成功）绝不再当候选。 */
internal fun isActiveOrderPrompt(prompt: String): Boolean = OrderStage.isActive(prompt)

/**
 * 明确结束的历史单。只用来让库里旧卡片退休，不再抓取。
 *
 * 必须同时认 [OrderStage] 的 DONE：否则「已收货 / 确认收货 / 签收成功」这类
 * 既不在旧词表、又被 isActive 排除的状态会两边落空——不抓详情、也不退休，
 * 老卡永远停在「在途」。两边词表重合是退休正确性的前提。
 */
internal fun isFinishedOrderPrompt(prompt: String): Boolean =
    listOf(
        "已签收", "待评价", "交易成功", "订单完成", "已完成", "退款成功", "交易已取消",
        "处理成功", "充值成功",
    ).any { prompt.contains(it) } || OrderStage.stageOf(prompt) == Stage.DONE

/**
 * 订单列表状态里的「往回走」词。订单列表只有状态文案、没有按钮，可以用宽词表；
 * 这类件不会再送到用户手上，轨迹再新也不能把它带进「在途」。
 */
private val RETURN_OR_REFUND_PROMPT = listOf(
    "退款", "退货", "售后", "退回", "退件", "拒收", "拒签", "原路退回",
    "待商家收货", "待卖家收货", "等待商家收货",
)

internal fun isReturnOrRefundPrompt(prompt: String): Boolean =
    prompt.isNotBlank() && RETURN_OR_REFUND_PROMPT.any { prompt.contains(it) }

/**
 * 详情页顶部状态 / 物流轨迹正文里的退回迹象。
 *
 * 这里刻意用**严格短语**，不收裸的「退款 / 退货 / 售后」：详情页和轨迹页会带
 * 「申请退款 / 售后」这类入口按钮文本，用宽词表会把一张正常在途单按自己的按钮
 * 误伤成退回单。严格短语只命中真正的退回归属状态。
 */
private val RETURN_TEXT_STRICT = listOf(
    "退款成功", "退款中", "退款完成", "退款关闭", "已退款",
    "退货退款", "退货中", "已退货", "退货完成",
    "售后处理中", "售后完成",
    "已退回", "退回中", "退件", "原路退回", "拒收", "拒签",
    "待商家收货", "待卖家收货", "等待商家收货",
)

private fun matchesReturnText(text: String?): Boolean {
    val t = text?.takeIf { it.isNotBlank() } ?: return false
    return RETURN_TEXT_STRICT.any { t.contains(it) }
}

/** 详情页顶部状态是否是「退款/退货/售后」这类往回走的状态。 */
internal fun isReturnStatusBanner(text: String?): Boolean = matchesReturnText(text)

/** 物流轨迹正文里是否出现退回迹象（「已退回」「退件」「拒收」这类）。 */
internal fun isReturnOrRefundTrace(text: String?): Boolean = matchesReturnText(text)

/** 取消、关闭、退款、退货、售后、退回：轨迹再新也不能盖过订单状态。 */
internal fun isBlockedOrderPrompt(prompt: String): Boolean =
    isReturnOrRefundPrompt(prompt) || listOf("取消", "交易关闭").any { prompt.contains(it) }

/**
 * 这条订单状态还值不值得抓详情。
 *
 * 只排除「明确关闭」和「没有物流可抓」的待付款单。**认不出的活跃状态也要抓**：
 * 词表没收录不等于件不存在——「已揽件」就是这么被漏掉的。详情页会给出真实阶段，
 * 词表只负责排序和兜底。
 */
internal fun needsDetailForPrompt(prompt: String): Boolean =
    !isFinishedOrderPrompt(prompt) && !isBlockedOrderPrompt(prompt) &&
        !prompt.contains("待付款") && !prompt.contains("未支付")

/**
 * 卡券/虚拟商品：只有「发货」没有物流签收，永远不会有运单号和取件码。
 *
 * 判据只取商品标题里的**强特征词**：像「虚拟」这种会命中「虚拟现实眼镜」的词不收，
 * 宁可漏掉一张卡券，也不能把真包裹判成虚拟件。
 */
internal fun isVirtualGoodsTitle(title: String?): Boolean {
    val text = title?.trim().orEmpty()
    if (text.isEmpty()) return false
    return VIRTUAL_GOODS.any { text.contains(it) }
}

private val VIRTUAL_GOODS = listOf(
    "兑换码", "卡密", "激活码", "电子卡", "卡券", "充值卡", "礼品卡", "代金券",
    "提货券", "兑换券", "虚拟商品", "虚拟物品", "虚拟卡", "话费充值", "流量充值",
    "会员月卡", "月卡", "季卡", "年卡", "会员卡", "点卡",
)

/**
 * 在途轨迹与订单状态的交叉校验：
 * 订单列表里同一 order_sn 的状态只要不是「待收货」口径，轨迹就不许把它带进在途区。
 * 订单列表里没有这个单（例如它只在「待收货」接口里出现）时不在这里否决——
 * 真正的残留轨迹靠「这次没再出现」的退休流程清掉，见 [isStaleTransitRow]。
 */
internal fun conflictsWithOrderStatus(sn: String, promptBySn: Map<String, String>): Boolean {
    val prompt = promptBySn[sn] ?: return false
    return !isActiveOrderPrompt(prompt)
}

/**
 * 库里这条账号行是不是「上次进来、这次彻底不见」的在途残留：
 * 只退休在途阶段（运输中/派送中/异常），不动到站件——
 * 到站件哪怕这次没出现在待收货列表，也可能是真没取的件，宁可多留一张卡。
 */
internal fun isStaleTransitRow(
    parcel: Parcel,
    accountId: String,
    seenOrderSns: Set<String>,
): Boolean = !parcel.retired &&
    !parcel.picked &&
    parcel.source == Source.ACCOUNT &&
    parcel.accountId == accountId &&
    !parcel.orderSn.isNullOrBlank() &&
    parcel.orderSn !in seenOrderSns &&
    parcel.stage != Stage.AT_STATION &&
    parcel.stage != Stage.DONE

/**
 * 一条在途账号件「放太久」了：仍停在运输中 / 派送中（或身份待补的未知），
 * 却已经超过 [MAX_TRANSIT_AGE_MS] 没有任何一次同步确认过它。
 *
 * 这不是「这次没翻到」的残留在途（那条靠 [isStaleTransitRow] 在翻全时退休）；
 * 它处理的是**翻页永远翻不到、平台也不再返回**的老幽灵——九月的单十一月还挂在
 * 「在途」，靠「翻页是否结束」永远等不到。到站件不在此列：驿站里真没人取的件另说。
 */
const val MAX_TRANSIT_AGE_MS: Long = 30L * 24 * 60 * 60 * 1000

/**
 * 只看年龄的版本：不区分账号，够老的账号在途件就不该再显示。
 * 首页展示层用这个（同步登录失效、一直没成功同步时，也能把老幽灵先挡住）。
 */
fun isAgedOutTransit(
    parcel: Parcel,
    now: Long,
    maxAgeMs: Long = MAX_TRANSIT_AGE_MS,
): Boolean {
    if (parcel.retired || parcel.picked) return false
    if (parcel.source != Source.ACCOUNT) return false
    if (parcel.orderSn.isNullOrBlank()) return false
    if (parcel.stage == Stage.AT_STATION || parcel.stage == Stage.DONE) return false
    val seenAt = parcel.lastSyncedAt ?: parcel.receivedAt
    return now - seenAt >= maxAgeMs
}

internal fun isAgedOutTransitRow(
    parcel: Parcel,
    accountId: String,
    now: Long,
    maxAgeMs: Long = MAX_TRANSIT_AGE_MS,
): Boolean = parcel.accountId == accountId && isAgedOutTransit(parcel, now, maxAgeMs)

internal fun isTerminalTrackStatus(status: String): Boolean {
    val s = status.trim()
    return s.equals("OUT_CABINET", true) || s.equals("SIGNED", true) ||
        s.equals("FINISHED", true) || s.equals("CANCELED", true) ||
        /* 退回/退款/拒收的物流状态：这件在往回走，同样不许再进「在途」。 */
        s.equals("RETURN", true) || s.equals("RETURNED", true) ||
        s.equals("RETURNING", true) || s.equals("REFUND", true) ||
        s.equals("REFUNDED", true) || s.equals("REJECT", true) || s.equals("REJECTED", true) ||
        isReturnOrRefundTrace(s)
}

/** 首屏「待收货」区用的是 express/track/status，而不是 order_list。 */
internal fun trackStatusStage(status: String): Stage? = when (status.uppercase()) {
    "IN_CABINET", "ARRIVAL", "ARRIVED" -> Stage.AT_STATION
    "DEPARTURE" -> Stage.IN_TRANSIT
    "DELIVERING" -> Stage.DELIVERING
    else -> null
}

internal fun trackStatusPrompt(status: String): String = when (status.uppercase()) {
    "IN_CABINET" -> "待取件"
    "DEPARTURE" -> "运输中"
    else -> "待收货"
}

/**
 * 详情没打开时的兜底：用列表里已有的阶段先入列，轨迹标「待补」。
 * 没有运单号也保留——订单号仍是身份，漏掉一件真待取的件比多一张待补卡更糟。
 */
internal fun fallbackFetchedParcel(
    platform: Platform,
    accountId: String,
    orderSn: String,
    prompt: String,
    goods: GoodsInfo?,
    at: Long,
): FetchedParcel? {
    val stage = when (OrderStage.stageOf(prompt)) {
        Stage.AT_STATION -> Stage.AT_STATION
        Stage.IN_TRANSIT -> Stage.IN_TRANSIT
        Stage.DELIVERING -> Stage.DELIVERING
        else -> return null
    }
    return FetchedParcel(
        platform = platform,
        accountId = accountId,
        trackingNo = "",
        orderSn = orderSn,
        carrier = "快递",
        stage = stage,
        pointName = null,
        lockerNo = null,
        code = "",
        codeKind = dev.showcode.core.CodeKind.NONE,
        explicitPickup = false,
        raw = if (prompt.isBlank()) "轨迹待补" else prompt,
        at = at,
        goodsTitle = goods?.title,
        goodsImage = goods?.image,
        goodsPrice = goods?.price,
        shopName = goods?.shop,
        orderedAt = goods?.orderedAt,
        fromListFallback = true,
    )
}

/**
 * 账号件与库里的件怎么合。
 *
 * 身份键是完整运单号；只有老行自己也没有运单号（短信件）时才退到「码 + 点」——
 * 后五位（SUFFIX）只用于展示，绝不参与身份判定。
 * 红线：已取行一个字段都不动；码冲突时保留原码；状态只升不降。
 */
fun planAccountSync(
    fetched: List<FetchedParcel>,
    existing: List<Parcel>,
): List<AccountSyncAction> = fetched.map { item ->
    val pointName = item.pointName?.trim().orEmpty()
    /* 到站件有运单号却没认出点名时，仍然归到「驿站未定位」，不丢件。
       运输中/派送中的件本来就没有取件点，pointKey 留空，让它只出现在「在途」区。 */
    val effectivePoint = pointName.ifBlank {
        if (item.stage == Stage.AT_STATION && item.trackingNo.isNotBlank()) {
            UNLOCATED_POINT_NAME
        } else {
            ""
        }
    }
    val key = effectivePoint.takeIf { it.isNotBlank() }?.let(::pointKey)

    val matched = existing.firstOrNull { row ->
        !row.trackingNo.isNullOrBlank() && row.trackingNo == item.trackingNo
    } ?: existing.firstOrNull { row ->
        /* 详情没抓到、或平台只给订单号时，用订单号接上同一行，避免同一件裂成两张。 */
        !item.orderSn.isNullOrBlank() && row.orderSn == item.orderSn &&
            (row.accountId == item.accountId || row.source == Source.ACCOUNT)
    } ?: existing.firstOrNull { row ->
        /* 后五位（CodeKind.SUFFIX）只用于展示，**不**参与身份判定：
           只有老行自己也没有运单号（短信件）时，才允许用「码 + 点」把两路报道接上。
           老行有运单号而这只不同 → 一定还是另一件，走插入，绝不并成一行。
           本行自己是 SUFFIX 时同样不参与：它没有完整运单号，无法证明与老行同件。 */
        item.codeKind != CodeKind.SUFFIX &&
            row.trackingNo.isNullOrBlank() &&
            item.code.isNotBlank() && key != null && row.code == item.code && row.pointKey == key
    }

    if (matched != null) {
        if (matched.picked) {
            AccountSyncAction.Skip(item, SkipReason.ALREADY_PICKED)
        } else {
            mergeExisting(item, matched, key)
        }
    } else {
        val hasIdentity = item.trackingNo.isNotBlank() || !item.orderSn.isNullOrBlank() ||
            item.code.isNotBlank()
        when {
            /** 「本人取件」的已签收件不需要再进待取。 */
            item.explicitPickup && item.stage == Stage.DONE ->
                AccountSyncAction.Skip(item, SkipReason.ALREADY_PICKED)

            /** 连运单号/订单号/码都没有：没有可展示的身份，只能跳过。 */
            !hasIdentity -> AccountSyncAction.Skip(item, SkipReason.NO_CODE)

            /** 运输中/派送中：进「在途」区，不许进待取计数。 */
            item.stage != Stage.AT_STATION && item.stage != Stage.DONE -> AccountSyncAction.Insert(
                item = item.copy(pointName = ""),
                pointKey = "",
                pointKind = PointKind.STATION,
            )

            /** 到站但认不出点：先入列（在途/待补小节），下次同步补点；不伪造首页卡片。 */
            effectivePoint.isBlank() -> AccountSyncAction.Insert(
                item = item.copy(pointName = ""),
                pointKey = "",
                pointKind = PointKind.STATION,
            )

            /* 「签收人：门口」这种不明确的已签收也先插成到站件：
               误插一张卡用户点一下已取就能纠正；漏掉一件真待取的件是他站在驿站门口才发现。
               这正是 Ingest 那条「拿不准就当成新件」的同一侧。
               空码不再丢：有运单号就入列，首页显示「暂无取件码」。 */
            else -> AccountSyncAction.Insert(
                item = if (pointName.isBlank()) item.copy(pointName = effectivePoint) else item,
                pointKey = key!!,
                pointKind = pointKindOf(effectivePoint),
            )
        }
    }
}

private fun mergeExisting(
    item: FetchedParcel,
    old: Parcel,
    incomingKey: String?,
): AccountSyncAction {
    val finalCode = if (old.code.isBlank() && item.code.isNotBlank()) item.code else old.code
    val finalKind = when {
        old.code.isNotBlank() -> old.codeKind
        item.code.isNotBlank() -> item.codeKind
        else -> CodeKind.NONE
    }
    val markPicked = item.stage == Stage.DONE && item.explicitPickup
    /* 新轨迹是终态时必须盖过旧的到站/派送——这正是「已签收却还挂在派送中」的来源。
       新轨迹认不出来（UNKNOWN）时保留旧阶段；新轨迹只是更早的阶段时，不把已有到站卡打回在途。
       列表兜底比详情粗，只允许它把阶段往前推，不允许降级。 */
    val stage = when {
        item.stage == Stage.DONE -> Stage.DONE
        item.stage == Stage.UNKNOWN -> old.stage
        old.stage == Stage.AT_STATION -> Stage.AT_STATION
        item.fromListFallback && stageProgress(item.stage) < stageProgress(old.stage) -> old.stage
        else -> item.stage
    }
    /* 列表兜底没有运单号与承运商细节；拿它覆盖老行会把「顺丰速运 · SF…」降级成
       「快递 · 运单号待出」。这些字段只能由详情结果更新。 */
    val incoming = if (item.fromListFallback) {
        item.copy(
            trackingNo = old.trackingNo?.takeUnless { it.isBlank() } ?: item.trackingNo,
            carrier = old.carrier.ifBlank { item.carrier },
            raw = old.raw.ifBlank { item.raw },
        )
    } else {
        item
    }
    /* 账号行的点名被清洗规则刷新时，pointKey 必须跟着换——
       否则同一个「示例大学示例校区驿站」会按旧 key 和新 key 裂成两张卡。 */
    val pointKey = if (old.source == Source.ACCOUNT && !item.pointName.isNullOrBlank() && incomingKey != null) {
        incomingKey
    } else {
        old.pointKey.ifBlank { incomingKey.orEmpty() }
    }
    val pointKind = if (old.pointKey.isNotBlank() && old.source != Source.ACCOUNT) {
        old.pointKind
    } else {
        item.pointName?.takeIf { it.isNotBlank() }?.let(::pointKindOf) ?: old.pointKind
    }

    return AccountSyncAction.Update(
        item = incoming,
        id = old.id,
        pointKey = pointKey,
        /* 账号行的点名允许被新的解析结果刷新（同一件、来源相同）；
           短信行的点名不动，避免账号轨迹里的长地址冲掉用户熟悉的写法。 */
        pointName = if (old.source == Source.ACCOUNT && !item.pointName.isNullOrBlank()) {
            item.pointName
        } else {
            old.pointName.ifBlank { item.pointName.orEmpty() }
        },
        pointKind = pointKind,
        code = finalCode,
        codeKind = finalKind,
        /* 已签收就是终态：必须离开在途区。不明确的签收不自动标已取，但阶段不再被降回待取。 */
        stage = stage,
        markPicked = markPicked,
    )
}

/** 点名后缀决定柜/驿站；认不出的一律当驿站，和读库时的兜底同一条口径。 */
fun pointKindOf(name: String): PointKind =
    if (name.contains("柜") || name.contains("格口")) PointKind.LOCKER else PointKind.STATION

/** 只用于列表兜底的单调保护；详情结果有自己的轨迹语序判据，不套这张表。 */
private fun stageProgress(stage: Stage): Int = when (stage) {
    Stage.IN_TRANSIT -> 1
    Stage.DELIVERING -> 2
    Stage.AT_STATION -> 3
    Stage.DONE -> 4
    else -> 0
}
