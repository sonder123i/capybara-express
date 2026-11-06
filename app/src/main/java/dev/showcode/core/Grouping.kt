package dev.showcode.core

/**
 * 首页的一级 IA：按取件点分组，组与组内都按三态紧急度排序。
 *
 * Story 2 AC2：第一级是取件点卡片，不是按时间排列的包裹列表。
 * Story 2 AC3：最急的在最上面，而不是最新到达的在最上面。
 */
fun groupByPoint(
    parcels: List<Parcel>,
    now: Long,
    thresholds: UrgencyThresholds = UrgencyThresholds(),
    aliases: Map<String, PointAlias> = emptyMap(),
): List<PickupPointGroup> = parcels
    .filterNot { it.picked }
    /* 账号渠道的件「到站 + 有点 + 有运单号/码」就进首页。
       空码件不再丢；运输中的件仍留在账号页统计，不冒充待取。 */
    .filter {
        it.stage == Stage.AT_STATION &&
            it.pointKey.isNotBlank() &&
            /* 有运单号就入首页，空码不再挡在外面；短信件仍然靠 code。 */
            (it.code.isNotBlank() || it.codeKind == CodeKind.SUFFIX || !it.trackingNo.isNullOrBlank())
    }
    // 分组键是解析过别名的 canonical key：合并因此只是读取侧的一次改名，
    // parcel 行一个字都不动，拆开时也就没有东西可丢。
    .groupBy { resolvePointKey(it.pointKey, aliases) }
    .map { (key, items) ->
        val sorted = items.sortedWith(parcelOrder(now, thresholds))
        // 显示名取最近一次入库的写法：同一个点在短信里可能有多种拼法，
        // 保守归一化不会合并它们，但同一个 pointKey 内部要用最新的那个名字。
        // 被合并过的点例外——那要用用户选定的目标点名字。
        val freshest = items.maxBy { it.receivedAt }
        PickupPointGroup(
            key = key,
            name = canonicalNameOf(key, aliases.values) ?: freshest.pointName,
            kind = freshest.pointKind,
            closesAtMinuteOfDay = freshest.closesAtMinuteOfDay,
            items = sorted,
            worst = urgencyOf(sorted.first(), now, thresholds),
        )
    }
    .sortedWith(pointOrder())

/** 同档里放得最久的排前面——它离失效更近。 */
fun parcelOrder(now: Long, thresholds: UrgencyThresholds = UrgencyThresholds()): Comparator<Parcel> =
    compareBy<Parcel> { urgencyOf(it, now, thresholds).order }.thenBy { it.receivedAt }

/** 紧急度优先，件数只作次级排序（与 mockup Screen6 一致）。 */
fun pointOrder(): Comparator<PickupPointGroup> =
    compareBy<PickupPointGroup> { it.worst.order }.thenByDescending { it.items.size }
