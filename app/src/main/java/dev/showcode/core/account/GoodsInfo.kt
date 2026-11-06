package dev.showcode.core.account

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 订单里的商品信息。三家能给多少算多少——缺的字段就是 null，不编造。
 *
 * 不存「价格数值」：拼多多的字段可能是分、淘宝的可能带区间、京东的可能带促销文案，
 * 统一成数值反而会让同一件东西在详情页显示成错的价。这里只负责把平台给的原文整理干净。
 */
data class GoodsInfo(
    val title: String? = null,
    val image: String? = null,
    val price: String? = null,
    val shop: String? = null,
    val orderedAt: Long? = null,
) {
    val isEmpty: Boolean
        get() = title.isNullOrBlank() && image.isNullOrBlank() && price.isNullOrBlank() &&
            shop.isNullOrBlank() && orderedAt == null
}

/**
 * 详情页里的一张候选图。详情页下方是推荐商品流，光看「最大/第一张」一定会抓到别人的图；
 * 所以每张图都带上它所在商品块（第一个含价格的祖先）的文本，由 [pickGoodsImage] 用标题判定同源。
 */
data class GoodsImageCandidate(
    val src: String,
    val area: Float,
    val ctx: String,
)

/**
 * 只按同一件商品绑定图：图的容器文本必须包含该件标题的开头。
 * 找不到同一件的图时返回 null——宁可显示「无图」，也不跨单借用推荐位的图。
 */
internal fun pickGoodsImage(candidates: List<GoodsImageCandidate>, title: String?): String? {
    val head = title
        ?.filterNot { it.isWhitespace() }
        ?.take(10)
        ?.takeIf { it.length >= 2 }
        ?: return null
    return candidates
        .asSequence()
        .filter { it.src.startsWith("http") && it.area >= 40f * 40f }
        .filter { it.ctx.filterNot { c -> c.isWhitespace() }.contains(head) }
        .maxByOrNull { it.area }
        ?.src
}

/** 价格原文 → 屏上那一行。已经带 ¥ 的不重复加；纯数字补一个；其它原样保留。 */
fun goodsPriceText(raw: String?): String? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (text.contains('¥') || text.contains('￥')) {
        return text.replace('￥', '¥').replace(Regex("\\s+"), " ")
    }
    return if (text.matches(Regex("^\\d+(\\.\\d+)?$"))) "¥$text" else text
}

/**
 * 拼多多的 `goods_price` 是**分**（2840 = ¥28.40），和淘宝/京东的元不一样。
 * 纯整数才按分换算；带小数点或货币符号的原文按原样整理。
 */
fun pddPriceText(raw: String?): String? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (!text.matches(Regex("^\\d+$"))) return goodsPriceText(text)
    val fen = text.toLongOrNull() ?: return goodsPriceText(text)
    val yuan = java.math.BigDecimal(fen).divide(java.math.BigDecimal(100))
        .stripTrailingZeros().toPlainString()
    return "¥$yuan"
}

/**
 * 下单时间。数字按「秒 / 毫秒」推断；文本按常见的 `yyyy-MM-dd HH:mm:ss` 解析。
 * 解析不了就返回 null——宁可不显示这一行，也不写一个猜出来的日期。
 */
fun orderTimeMs(raw: Any?): Long? = when (raw) {
    is Number -> normalizeEpoch(raw.toLong())
    is String -> {
        val text = raw.trim()
        if (text.isEmpty()) null
        else text.toLongOrNull()?.let(::normalizeEpoch) ?: parseDateTime(text)
    }
    else -> null
}

private fun normalizeEpoch(value: Long): Long? = when {
    value <= 0L -> null
    // 10 位数是秒；13 位是毫秒。小于 1e11 的其它值也按秒处理。
    value < 100_000_000_000L -> value * 1000L
    else -> value
}

private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val DATE_TIME_T = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
private val DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun parseDateTime(text: String): Long? {
    val trimmed = text.take(19)
    return runCatching {
        LocalDateTime.parse(trimmed, if (trimmed.contains('T')) DATE_TIME_T else DATE_TIME)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.recoverCatching {
        LocalDate.parse(trimmed.take(10), DATE_ONLY)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.recoverCatching {
        Instant.parse(text).toEpochMilli()
    }.getOrNull()
}

/** 图片地址：协议相对地址 `//…` 补 https，淘宝有时给 `http://` 也统一升到 https。 */
fun normalizeImageUrl(raw: String?): String? {
    val url = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    /* 拼多多的图床默认回 AVIF，而低版本系统的解码器不支持——屏上会变成「无图」。
       imageMogr2 的 format 参数换成 JPEG 后同一张图仍能取到，所以在这里统一降级。 */
    val jpeg = url.replace("format/avif", "format/jpeg")
    return when {
        jpeg.startsWith("//") -> "https:$jpeg"
        jpeg.startsWith("http://") -> "https://" + jpeg.removePrefix("http://")
        jpeg.startsWith("https://") -> jpeg
        else -> null
    }
}
