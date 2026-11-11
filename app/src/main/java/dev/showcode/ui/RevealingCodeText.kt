package dev.showcode.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import dev.showcode.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/*
 * 卡面上的取件码：换码时逐位收敛（react-bits SplitFlapText / Shuffle 范式）。
 *
 * 现场屏不挂这一层——OnsiteCodeBody 的码必须瞬间全墨，扫码机可能在动画途中取帧，
 * 一张还在回弹的码会被读成模糊。这里只服务首页/卡片里的码：它不是被扫的那一块，
 * 而是「这一件刚解析出来」被看见的地方，语义正好是本地解析的落点。
 *
 * 三条契约与 AnimatedValueText 同源：只在码本身变化时播、回到页面不重播、
 * 减少动画时直接落在终态。等宽 + tabular 由传入的 style 保证，逐位替换不会让整行晃。
 * 乱码字符集是确定的（按位索引与帧号取模），不引入随机数，截图回归才稳；
 * 且刻意不用数字/连字符，避免动画途中被读成一个完整的、其实还没出全的取件码。
 */
private const val REVEAL_STEP_MS = 26L
private const val SCRAMBLE_TICKS = 2
/*
 * 未揭示位用非码字符而不是 0-9/-：动画途中若显示「真码前缀 + 同字符集假数字」，
 * 整行看起来就是一个完整的取件码，用户可能把还没收敛完的码读走或截图。
 * 几个深浅不一的圆点让「先跑两拍再逐位收敛」的层次仍在，且保持确定性
 * （按位索引与帧号取模），不引入随机数、等宽不晃、截图回归仍稳。
 */
private const val SCRAMBLE_GLYPHS = "•·∘"

@Composable
fun RevealingCodeText(
    code: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val shown = remember(code) { mutableIntStateOf(if (reduced) code.length else 0) }
    val tick = remember(code) { mutableIntStateOf(0) }

    LaunchedEffect(code, reduced) {
        if (reduced) {
            shown.intValue = code.length
            return@LaunchedEffect
        }
        shown.intValue = 0
        /* 先让乱码跑两拍，再逐位收敛成真码：读起来像「从短信里认出来」。 */
        repeat(SCRAMBLE_TICKS) {
            tick.intValue++
            delay(REVEAL_STEP_MS)
        }
        while (shown.intValue < code.length) {
            shown.intValue++
            tick.intValue++
            delay(REVEAL_STEP_MS)
        }
    }

    val n = shown.intValue
    val t = tick.intValue
    val display = buildString(code.length) {
        code.forEachIndexed { i, c ->
            append(
                if (i < n) c
                else SCRAMBLE_GLYPHS[(i * 7 + t * 3) % SCRAMBLE_GLYPHS.length],
            )
        }
    }
    Text(text = display, style = style, color = color, maxLines = 1, modifier = modifier)
}
