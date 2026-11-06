package dev.showcode.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import dev.showcode.ui.theme.GlassContourDark
import dev.showcode.ui.theme.GlassContourLight

/**
 * 玻璃边缘光。逐行移植教务 `ui/system/glass/GlassChipSurface.kt:299-375` 的 `glassRim`，
 * 参数取 `GlassMaterial.kt:378-387`。
 *
 * 为什么要有这一层：底栏底色是高不透明度磨砂面（亮 0.88 / 暗 0.86），库的
 * `Highlight` 只画 0.5dp 宽、0.25dp 模糊的一圈轮廓，在这么实的面上几乎融掉。
 * 参考工程真正让边缘立起来的是这条**内缘亮线**（1.2dp、白 0.30/0.34）+ 外描边
 * （黑 0.20/白 0.18）；它不采样任何内容，所以不会把背后的字折回来。
 *
 * @param pointerOffset 触点位置。给定时亮线最亮处偏向手指（参考 `ChipHighlightTravel=0.34`）；
 *                      底栏不传，按住只整体增亮 45%，避免光点游走干扰 12sp 标签。
 */
fun Modifier.glassRim(
    shape: Shape,
    isLightTheme: Boolean,
    intensity: Float = 1f,
    pressProgress: () -> Float = { 0f },
    pointerOffset: () -> Offset = { Offset.Unspecified },
): Modifier = drawWithCache {
    val contourWidth = 1.dp.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)

    // 内缘：把轮廓整体内缩一圈再描边，光就贴在玻璃内壁上
    val insetPx = 1.1.dp.toPx()
    val innerSize = Size(
        (size.width - insetPx * 2f).coerceAtLeast(0.1f),
        (size.height - insetPx * 2f).coerceAtLeast(0.1f),
    )
    val innerOutline = shape.createOutline(innerSize, layoutDirection, this)
    val innerWidth = 1.2.dp.toPx()

    // 浅色下白线画在白盘上几乎看不见，只留一点点；深色下白线才是唯一的光。
    val baseContour = if (isLightTheme) {
        GlassMaterials.GlassContourAlphaLight
    } else {
        GlassMaterials.GlassContourAlphaDark
    }
    val baseInner = if (isLightTheme) {
        GlassMaterials.GlassRimAlphaLight
    } else {
        GlassMaterials.GlassRimAlphaDark
    }

    onDrawWithContent {
        drawContent()
        val p = pressProgress().coerceIn(0f, 1f)
        val pointer = pointerOffset()
        val innerAlpha = baseInner * intensity * (1f + GlassMaterials.GlassRimPressBoost * p)

        val innerBrush = if (pointer.isSpecified && p > 0.01f && size.minDimension > 0f) {
            // 触点为中心调制边缘亮度：靠近手指的一段更亮
            val center = Offset(
                size.width / 2f + (pointer.x - size.width / 2f) * 0.34f * p,
                size.height / 2f + (pointer.y - size.height / 2f) * 0.34f * p,
            )
            Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = innerAlpha * 1.25f),
                    Color.White.copy(alpha = innerAlpha * 0.55f),
                ),
                center = center,
                radius = size.maxDimension * 0.85f,
            )
        } else {
            // 静止光：顶部最亮、中段回落、底部再抬一点（玻璃内壁的反射顺序）
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.White.copy(alpha = innerAlpha),
                    0.50f to Color.White.copy(alpha = innerAlpha * 0.55f),
                    1.00f to Color.White.copy(alpha = innerAlpha * 0.75f),
                ),
            )
        }
        translate(insetPx, insetPx) {
            drawOutline(innerOutline, brush = innerBrush, style = Stroke(innerWidth))
        }

        // 外轮廓：均匀一圈，按下时收紧变实，像被压出的应力边
        val contourColor = if (isLightTheme) {
            GlassContourLight.copy(alpha = baseContour * intensity * (1f + 0.5f * p))
        } else {
            GlassContourDark.copy(alpha = baseContour * intensity * (1f + 0.5f * p))
        }
        drawOutline(outline, color = contourColor, style = Stroke(contourWidth))
    }
}
