package dev.showcode.ui.onsite

import dev.showcode.core.CodeKind
import dev.showcode.core.Parcel

/**
 * 「复制快递单号 / 去淘宝查询」这一组动作交出去的那串单号。
 *
 * 没有取件码的件有两种：`NONE`（只有运单号）和 `SUFFIX`（屏上出示后五位）。
 * 两种都需要去淘宝查；**已经拿到取件码的 `PICKUP` 件一律不需要**，保持干净。
 * 复制的是完整运单号，不是后五位——淘宝搜索框认的是完整单号。
 */
fun trackingLookupText(parcel: Parcel): String? {
    if (parcel.codeKind == CodeKind.PICKUP) return null
    return parcel.trackingNo?.trim()?.takeIf { it.isNotEmpty() }
}
