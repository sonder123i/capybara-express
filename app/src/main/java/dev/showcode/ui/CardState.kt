package dev.showcode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.showcode.ui.theme.BorderDueSoon
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.SurfaceSunken
import dev.showcode.ui.theme.UrgencyDueSoonTint
import dev.showcode.ui.theme.UrgencySafe
import dev.showcode.ui.theme.UrgencySafeTint

/**
 * 卡片的四种状态。
 *
 * 借的是多邻国选项块的做法：**状态靠「填充 + 描边」表达，不靠一条色条**。
 * 色条仍然保留（首页唯一的颜色编码位置，紧急度必须能在余光里扫到），
 * 但它现在是「多急」，而这四态是「这条记录现在处于什么状态」——两件事。
 *
 * 判据是灰度：遮住颜色只看明暗，四种状态的填充深浅应当仍能分开。
 * 所以这里用的都是**已有的面与 tint**，不新增色值。
 */
enum class CardState {
    /** 待取、一切正常。 */
    IDLE,

    /** 临期或已过期风险：底色转暖、描边加深。 */
    URGENT,

    /** 刚被处理完（已取）。浅绿底，带一圈同色描边。 */
    JUST_DONE,

    /** 不再关心：已退休、已忽略。压到下沉面，文字降一档。 */
    RETIRED,
}

/** 四态共用的形状：卡片一律 Md。 */
private val CardShape = ShowCodeShapes.Md

/**
 * 把 [state] 画成填充 + 描边。
 *
 * 顺序很重要：**先 background 再 border**，这样描边压在填充上、不会被子层的背景盖住。
 * 调用方仍然可以在这之后叠自己的 `clickable` / `padding`。
 */
fun Modifier.cardState(state: CardState): Modifier = this
    .background(fillOf(state), CardShape)
    .border(widthOf(state), strokeOf(state), CardShape)

private fun fillOf(state: CardState) = when (state) {
    CardState.IDLE -> SurfaceCard
    CardState.URGENT -> UrgencyDueSoonTint
    CardState.JUST_DONE -> UrgencySafeTint
    CardState.RETIRED -> SurfaceSunken
}

private fun strokeOf(state: CardState) = when (state) {
    CardState.IDLE -> Hairline
    CardState.URGENT -> BorderDueSoon
    CardState.JUST_DONE -> UrgencySafe
    CardState.RETIRED -> Hairline
}

private fun widthOf(state: CardState) = when (state) {
    CardState.URGENT, CardState.JUST_DONE -> 1.5.dp
    else -> 1.dp
}
