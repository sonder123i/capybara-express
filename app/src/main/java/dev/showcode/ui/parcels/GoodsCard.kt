package dev.showcode.ui.parcels

import dev.showcode.core.Parcel
import dev.showcode.core.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 详情页那张商品卡出不出现。
 *
 * 只有「账号渠道 + 有账号归属 + 至少拿到一个商品字段」才显示。
 * 短信/手动来源、或者账号件但商品信息全缺时都不显示——不画空卡、不占位。
 */
fun showsGoodsCard(parcel: Parcel): Boolean =
    parcel.source == Source.ACCOUNT &&
        !parcel.accountId.isNullOrBlank() &&
        (
            !parcel.goodsTitle.isNullOrBlank() ||
                !parcel.goodsImage.isNullOrBlank() ||
                !parcel.goodsPrice.isNullOrBlank() ||
                !parcel.shopName.isNullOrBlank() ||
                parcel.orderedAt != null
            )

/** 缩略图地址：商品卡不出现、或图缺失时返回 null——调用方据此不留空框。 */
fun goodsThumbnailUrl(parcel: Parcel): String? =
    if (showsGoodsCard(parcel)) parcel.goodsImage?.trim()?.takeIf { it.isNotEmpty() } else null

/** 下单时间：只显示月日与时分，年份在这一屏没有意义。 */
fun orderedAtText(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
