package dev.showcode.ui.onsite

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/** 静默阈值：8 秒没有任何交互。 */
private const val SILENCE_AFTER_MS = 8_000L

/**
 * 退场时长。比换屏的 200ms 略长一点点：这是在「收」，不是在「换」，
 * 太快会像闪了一下，太慢会让人以为卡住。
 */
private const val SILENCE_FADE_MS = 220

/**
 * 静默态。
 *
 * [chromeAlpha] 只喂给「控件」，不喂给码、格口和倒计时——那三样是这一屏存在的理由，
 * 任何时候都不许淡。控件保留布局占位（只改 alpha），所以码不会因为按钮消失而重新排版。
 */
@Stable
class SilenceState internal constructor(
    val silent: Boolean,
    val chromeAlpha: Float,
    val wake: () -> Unit,
)

/**
 * 现场模式的「静默退场」：闲置 8 秒后把控件收掉，只留码、格口、倒计时。
 *
 * 为什么不是纯装饰：身后有人排队时，屏幕上每一个多余的字都是噪音；更实际的是，
 * 举着手机对格口时最容易被误触的正是那颗「已取」。
 *
 * [resetKey] 变化（翻页、切组、撤销条出现、从平铺页回来）就重新计时并醒过来——
 * 用户刚做完一个动作，不该立刻看着一屏被收掉的控件。
 *
 * [enabled] 为 false 时永远不静默：演示态藏按钮会让人以为坏了；
 * 读屏开着时控件必须一直在（用户需要它来导航，而不是靠「碰一下」）。
 */
@Composable
fun rememberSilenceState(enabled: Boolean, resetKey: Any?): SilenceState {
    var silent by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableIntStateOf(0) }

    LaunchedEffect(resetKey, enabled) {
        silent = false
        lastInteraction++
    }

    LaunchedEffect(lastInteraction, enabled) {
        if (!enabled) {
            silent = false
            return@LaunchedEffect
        }
        delay(SILENCE_AFTER_MS)
        silent = true
    }

    val chromeAlpha by animateFloatAsState(
        targetValue = if (silent) 0f else 1f,
        animationSpec = tween(durationMillis = SILENCE_FADE_MS),
        label = "onsiteSilence",
    )

    return remember(silent, chromeAlpha) {
        SilenceState(silent = silent, chromeAlpha = chromeAlpha) {
            silent = false
            lastInteraction++
        }
    }
}

/**
 * 任何一次按下都重新计时，不只是「语义动作」（翻页、切组、撤销条出现）。
 *
 * 挂在根 Box 上，走 Initial pass 且**不消费**：它只是看一眼「有人碰了屏幕」，
 * 事件照常往下传给真正的手势。长按复制这种不改任何状态的交互，也靠它把 8 秒推后。
 *
 * 用 `rememberUpdatedState` 在调用方接住最新的 wake：pointerInput(Unit) 不会因重组重启，
 * 直接捕获会一直拿着第一次组合时那个对象。
 */
fun Modifier.onsiteTouchObserver(onTouch: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press) onTouch()
        }
    }
}

/**
 * 静默时盖在最上面的那一层。
 *
 * 用 `onPress` 而不是 `onTap`：**按下就醒**，不能等手指抬起来——
 * 举着手机的人常常是拇指一蹭，不是规规矩矩点一下。
 * 这一整个手势由它吃掉，所以唤醒的那一下既不会翻页，也不会落在「已取」上。
 *
 * 也就是说「静默后的第一下只负责唤醒」是一条刻意保留的代价：
 * 想连点两下取件时，第一下会白按。比误触「已取」便宜得多。
 */
@Composable
fun SilenceCatcher(state: SilenceState) {
    if (!state.silent) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onPress = { state.wake() })
            }
    )
}
