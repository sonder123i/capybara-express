package dev.showcode.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * 会「流」的胶囊。
 *
 * 底栏那颗指示胶囊在滑动时不再只是被 `scaleX/scaleY` 拉扁——它**真的变形**：
 * 前进的那一端被拉长、圆角收小，拖在后面那一端保持饱满。
 * 顶点一一对应地插值（`androidx.graphics.shapes.Morph`），不是两个形状交叉淡入。
 *
 * 为什么值得单独写一个 Shape：缩放是**仿射**变换，一个胶囊无论怎么拉，两端永远是
 * 同一条圆弧。而液体的特征是「前面尖、后面钝」——这需要顶点级别的插值，
 * 缩放做不到。`graphics-shapes` 就是干这个的。
 *
 * @param flow 0 = 静止的胶囊；1 = 拉到最开的流动形态。
 *   由速度驱动，松手后回 0，所以静止时它和 `CircleShape` 完全等价。
 *
 * 传的是**取值函数**而不是数值：形状要每帧重算，而 `Shape` 本身必须是稳定对象，
 * 否则每帧都会新建一个实例、让 `drawBackdrop` 反复失效。
 */
class FlowingPillShape(private val flow: () -> Float) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val flowValue = flow()
        if (w <= 0f || h <= 0f) return Outline.Rectangle(Rect(0f, 0f, w, h))
        /* 静止时直接用圆角矩形：省掉一次 Morph 计算，也让「没在动」这一帧绝对干净。 */
        if (flowValue < 0.01f) {
            val r = minOf(w, h) / 2f
            return Outline.Rounded(
                RoundRect(
                    rect = Rect(0f, 0f, w, h),
                    cornerRadius = CornerRadius(r, r),
                ),
            )
        }
        return Outline.Generic(morphPath(w, h, flowValue.coerceIn(0f, 1f)))
    }
}

/**
 * 两帧形状之间插值出一条 Path。
 *
 * 两个多边形都只用 4 个顶点，顶点数一致，`Morph` 才能一一配对。
 * 差别只在两端的位置和圆角：
 * - **静止**：左右对称、四角满圆（就是胶囊）；
 * - **流动**：右端外拉 6%、左端内收 2%，且右端两个角收窄到 62%——
 *   「前面尖、后面钝」正是液体被拖着走的形状。
 */
private fun morphPath(w: Float, h: Float, flow: Float): Path {
    val rest = RoundedPolygon(
        pillVertices(w, h, flow = 0f),
        CornerRounding(radius = h / 2f),
    )
    val flowing = RoundedPolygon(
        pillVertices(w, h, flow = 1f),
        CornerRounding(radius = h / 2f),
        listOf(
            CornerRounding(radius = h / 2f),          // 左上：保持饱满
            CornerRounding(radius = h * 0.31f),       // 右上：收窄（前缘）
            CornerRounding(radius = h * 0.31f),       // 右下：收窄（前缘）
            CornerRounding(radius = h / 2f),          // 左下：保持饱满
        ),
    )
    val cubics = Morph(rest, flowing).asCubics(flow)

    /* 多边形坐标是以 (0,0) 为中心的，Path 要从左上角起画——这里整体平移。 */
    val dx = w / 2f
    val dy = h / 2f
    return Path().apply {
        if (cubics.isEmpty()) return@apply
        val first = cubics.first()
        moveTo(first.anchor0X + dx, first.anchor0Y + dy)
        cubics.forEach { c ->
            cubicTo(
                c.control0X + dx, c.control0Y + dy,
                c.control1X + dx, c.control1Y + dy,
                c.anchor1X + dx, c.anchor1Y + dy,
            )
        }
        close()
    }
}

/**
 * 胶囊的四个顶点（顺时针，以中心为原点）。
 *
 * [flow] 只动 x：纵向不拉，因为「厚薄」由调用方的 scaleY 管，
 * 两个地方各改一次会让胶囊在滑动中忽胖忽瘦，看起来像抽搐。
 */
private fun pillVertices(w: Float, h: Float, flow: Float): FloatArray {
    val halfW = w / 2f
    val halfH = h / 2f
    val lead = halfW + w * 0.06f * flow
    val trail = -halfW - w * 0.02f * flow
    return floatArrayOf(
        trail, -halfH,
        lead, -halfH,
        lead, halfH,
        trail, halfH,
    )
}
