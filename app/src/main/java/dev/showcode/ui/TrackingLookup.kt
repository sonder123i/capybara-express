package dev.showcode.ui

import android.content.Context
import android.widget.Toast
import dev.showcode.R
import dev.showcode.core.LaunchOutcome
import dev.showcode.core.Parcel
import dev.showcode.ui.onsite.trackingLookupText

/**
 * 「去淘宝查询」的共同动作：先把完整单号留在剪贴板，再只拉起淘宝 App。
 *
 * 拉不起来（没装 / 被 ROM 拦住）时不把用户扔进浏览器，而是停在原屏给一句
 * 「已复制单号，去淘宝粘贴搜索」——剪贴板里的单号仍然有效。
 */
fun performTrackingLookup(
    context: Context,
    parcel: Parcel,
    onOpenTaobao: (Parcel) -> LaunchOutcome,
) {
    trackingLookupText(parcel)?.let { copyText(context, it) }
    if (onOpenTaobao(parcel) != LaunchOutcome.Opened) {
        Toast.makeText(context, R.string.onsite_query_taobao_fallback, Toast.LENGTH_SHORT).show()
    }
}
