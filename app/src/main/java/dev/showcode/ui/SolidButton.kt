package dev.showcode.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkDisabled
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.SurfaceDeep
import dev.showcode.ui.theme.MotionProfile
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.UrgencySafe
import dev.showcode.ui.theme.rememberReducedMotion
import androidx.compose.material3.Text

/**
 * 主行动按钮：**有厚度的那种**。
 *
 * 借的是多邻国的主按钮：底下垫一条更深的边，按下去整颗键沉进去。
 * 那条边不是阴影——它是一个「键程」的视觉声明：这颗键有 3dp 可以按下去。
 *
 * 为什么值得为它单独写一个组件：一个 App 里真正的主行动通常只有五六颗，
 * 而它们决定了「这个 App 摸起来是实心的还是纸片」。其余按钮保持文字按钮。
 *
 * @param tone 主色调。Ink（默认，最高对比）/ Safe（完成类）/ Accent（引导类）
 * @param enabled false 时整体降为 InkDisabled，不响应按压，也不给触觉
 */
@Composable
fun SolidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: SolidTone = SolidTone.Ink,
    enabled: Boolean = true,
    heightDp: Int = 52,
) {
    val reduced = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink = if (enabled && !reduced && pressed) 3f else 0f
    val sinkPx by animateFloatAsState(
        targetValue = sink,
        animationSpec = MotionProfile.iconSpring(),
        label = "solid-button-sink",
    )
    val face = when {
        !enabled -> InkDisabled
        tone == SolidTone.Safe -> UrgencySafe
        tone == SolidTone.Accent -> dev.showcode.ui.theme.AccentInk
        else -> Ink
    }
    /* 底边不用半透明色（那会被视觉棘轮判成「手调文字透明度」）：
       直接取一个已有的下沉面，暗色下也自然成立。 */
    val edge = when {
        !enabled -> Hairline
        tone == SolidTone.Safe -> SurfaceDeep
        tone == SolidTone.Accent -> SurfaceDeep
        else -> InkMuted
    }
    val label = if (enabled) OnsiteSurface else InkMuted

    Box(modifier = modifier.fillMaxWidth()) {
        /* 底边：比按钮低 3dp，颜色是主色的深版本。按下时按钮沉到它上面。 */
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 3.dp)
                .background(edge, ShowCodeShapes.Md),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .graphicsLayer { translationY = sinkPx.dp.toPx() }
                .background(face, ShowCodeShapes.Md)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = ShowCodeText.Section, color = label)
        }
    }
}

enum class SolidTone { Ink, Safe, Accent }

/** 浅色的次要按钮：只有文字，没有面。 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = InkMuted,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = ShowCodeText.Body, color = color)
    }
}
