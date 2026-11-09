package dev.showcode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import dev.showcode.ui.glass.GlassTier
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.ShowCodePaletteState
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.SurfaceFloating
import dev.showcode.ui.theme.rememberReducedMotion

/**
 * 居中的玻璃弹窗。
 *
 * 为什么不直接用 Compose 的 `Dialog`：那是一个**独立窗口**，拿不到本页的 backdrop —
 * 于是只能是一块实心卡，家族那套「折页面背景」的玻璃就断了。这里把它画在根 Box 的
 * 最上层（和底栏同一张 `glassBackdrop`），背景模糊的是真实页面，而不是一张假的白面。
 *
 * 降级不用另一套组件：同一块卡，`BACKDROP` 档给玻璃，其余档退回实底 + 描边 + 投影。
 * 调用方只传档位与 backdrop，不看 SDK。
 */
@Composable
fun GlassModal(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    tier: GlassTier,
    backdrop: Backdrop?,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    BackHandler(onBack = onDismissRequest)

    val reduced = rememberReducedMotion()
    val dark = ShowCodePaletteState.isDark
    val density = LocalDensity.current
    val shape = ShowCodeShapes.Modal
    val glass = backdrop?.takeIf { tier == GlassTier.BACKDROP }
    val blurPx = with(density) { 12.dp.toPx() }

    /* 亮色白 0.74 / 暗色深底 0.82：低于这个值，正文在花背景上读不清；
       高于它，玻璃就只剩「一块浅色板」。家族 Modal 档也是这个区间。 */
    val surface = if (dark) {
        Color(0xFF171B22).copy(alpha = 0.82f)
    } else {
        Color.White.copy(alpha = 0.74f)
    }

    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(if (reduced) 0 else 180))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = appear.value * if (dark) 0.42f else 0.28f
                }
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val p = appear.value
                    alpha = p
                    val s = 0.94f + 0.06f * p
                    scaleX = s
                    scaleY = s
                }
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .then(
                    if (glass != null) {
                        Modifier.drawBackdrop(
                            backdrop = glass,
                            shape = { shape },
                            effects = {
                                vibrancy()
                                blur(blurPx)
                            },
                            highlight = { Highlight.Default.copy(alpha = 0.16f) },
                            shadow = { Shadow(alpha = 0.22f) },
                            onDrawSurface = { drawRect(surface) },
                        )
                    } else {
                        Modifier
                            .shadow(if (dark) 8.dp else 14.dp, shape, clip = false)
                            .background(SurfaceFloating, shape)
                            .border(1.dp, Hairline, shape)
                    },
                ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp), content = content)
        }
    }
}
