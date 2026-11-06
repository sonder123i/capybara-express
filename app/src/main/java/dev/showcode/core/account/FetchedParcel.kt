package dev.showcode.core.account

import dev.showcode.core.CodeKind
import dev.showcode.core.Stage

/**
 * 一次账号抓取拿回来的一件。
 *
 * 它还不是 [dev.showcode.core.Parcel]：没有 id、没有 picked、没有用户表态。
 * 它只描述「平台说这件在哪、什么状态、有没有码」，合并由 [AccountMerge] 决定。
 */
data class FetchedParcel(
    val platform: Platform,
    val accountId: String,
    val trackingNo: String,
    val orderSn: String?,
    val carrier: String,
    val stage: Stage,
    val pointName: String?,
    val lockerNo: String?,
    /** 取件码或单号后五位；`codeKind=NONE` 时为空串。 */
    val code: String,
    val codeKind: CodeKind,
    /** 轨迹是否明确说「本人取件 / 凭取件码取件」；只有这种才允许自动标已取。 */
    val explicitPickup: Boolean,
    val raw: String,
    val at: Long,
    /** 商品信息。三家能给多少算多少，缺的留 null，不伪造。 */
    val goodsTitle: String? = null,
    val goodsImage: String? = null,
    val goodsPrice: String? = null,
    val shopName: String? = null,
    val orderedAt: Long? = null,
)
