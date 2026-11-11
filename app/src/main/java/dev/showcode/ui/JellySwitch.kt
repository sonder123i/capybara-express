package dev.showcode.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.theme.MotionTokens
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.SurfaceSunken
import dev.showcode.ui.theme.rememberReducedMotion

/*
 * 果冻开关（react-bits SquishSwitch / JellyRadio 范式）。
 *
 * SettingsScreen 原来三处用的是 Material3 原生 Switch：thumb 线性滑过去，
 * 和本仓库 DampedDragAnimation / FlowingPillShape / NavSettle 那套果冻手感不是一家人。
 * 这里只换手感，不换语义：Role.Switch、contentDescription、checked 回调一条不动。
 *
 * 弹簧取 MotionTokens 里已有的两档：切换落位用 PressLift（4.6% 过冲），
 * 落位那一下 thumb 压扁再弹回用 Settle（8.4% 过冲，"事情结束了"）。
 * 不新立 token，减少动画时直接切终态。
 */
private val TRACK_WIDTH = 46.dp
private val TRACK_HEIGHT = 28.dp
private val THUMB_SIZE = 22.dp
private val THUMB_INSET = 3.dp

@Composable
fun JellySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val reduced = rememberReducedMotion()
    val target = if (checked) 1f else 0f
    val slide by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduced) snap() else MotionTokens.PressLift,
        label = "jelly-switch-slide",
    )
    val squash = remember { Animatable(1f) }
    LaunchedEffect(checked, reduced) {
        if (reduced) {
            squash.snapTo(1f)
            return@LaunchedEffect
        }
        squash.snapTo(0.86f)
        squash.animateTo(1f, MotionTokens.Settle)
    }
    val travel = TRACK_WIDTH - THUMB_SIZE - THUMB_INSET * 2
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(TRACK_WIDTH, TRACK_HEIGHT)
            .clip(CircleShape)
            .background(if (checked) Accent else SurfaceSunken, CircleShape)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
                indication = null,
                interactionSource = interaction,
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(THUMB_SIZE)
                .graphicsLayer {
                    translationX = THUMB_INSET.toPx() + travel.toPx() * slide
                    val k = 1f - squash.value
                    scaleX = 1f + k * 0.12f
                    scaleY = 1f - k * 0.08f
                }
                .clip(CircleShape)
                .background(SurfaceCard, CircleShape),
        )
    }
}
