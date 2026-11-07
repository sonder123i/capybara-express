package dev.showcode.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot

/**
 * 换主题时的**圆形扩散**。
 *
 * 效果：按下开关的那一点向外长出一个圆，圆里已经是新主题，圆外还是旧主题，
 * 直到圆铺满整屏。用户在别的项目里看到的就是这个（Material You 的
 * "circular reveal"）。
 *
 * 为什么必须先截图：主题是**瞬时**切换的（`ShowCodePaletteState` 一改全 App 立刻重组），
 * 切完就再也拿不到旧画面了。所以顺序被钉死成：
 *
 * 1. 用 [PixelCopy] 把当前窗口截成位图（**切换之前**）；
 * 2. 截图成功后才调 [apply] 真正换主题；
 * 3. 把位图交给 [ThemeRevealOverlay]，用「全屏矩形挖掉一个圆」的路径把它画出来。
 *
 * 为什么用 [PixelCopy] 而不是 `View.draw(Canvas)`：Compose 的内容走 RenderNode，
 * 画进软件 Canvas 会得到一片空白。PixelCopy 读的是窗口的真实 surface，是唯一可靠的路。
 *
 * 截图失败（极少数机型 / 窗口正在重建）时**直接换主题、不播动画**——
 * 换主题这件事本身不能被动画绑架。
 */
object ThemeReveal {

    /** 正在播的那一次扩散。null 表示没有。 */
    var active by mutableStateOf<Reveal?>(null)
        private set

    class Reveal(
        val snapshot: ImageBitmap,
        /** 圆心，窗口坐标系（px）。 */
        val origin: Offset,
        /** 铺满整屏所需的半径。 */
        val maxRadius: Float,
    )

    /**
     * 开始一次扩散。
     *
     * @param view 任意一个挂在窗口上的 View，用来找 Window 与尺寸
     * @param origin 圆心，窗口坐标系（px）——传开关的**中心**，扩散从那里长出来
     * @param reduced 系统开了「减少动画」：跳过动画，直接换
     * @param apply 真正的主题切换。**只有它执行完，扩散才会开始**
     */
    fun start(view: View, origin: Offset, reduced: Boolean, apply: () -> Unit) {
        if (reduced) {
            apply()
            return
        }
        val window = view.context.findActivity()?.window
        val decor = window?.decorView
        if (window == null || decor == null || decor.width <= 0 || decor.height <= 0) {
            apply()
            return
        }
        val w = decor.width
        val h = decor.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        /* PixelCopy 是异步的，回调在主线程。主题必须等截图完成后再切，
           否则截到的是新画面，扩散就成了「新画面擦新画面」。 */
        PixelCopy.request(
            window,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    apply()
                    active = Reveal(
                        snapshot = bitmap.asImageBitmap(),
                        origin = origin,
                        maxRadius = farthestCorner(origin, w.toFloat(), h.toFloat()),
                    )
                } else {
                    bitmap.recycle()
                    apply()
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

    /** 动画播完清掉，别把一张整屏位图留在内存里。 */
    fun clear() {
        active = null
    }
}

/** 从 [origin] 到四角里最远的那个的距离：圆至少要这么大才能盖满整屏。 */
private fun farthestCorner(origin: Offset, w: Float, h: Float): Float {
    val dx = maxOf(origin.x, w - origin.x)
    val dy = maxOf(origin.y, h - origin.y)
    return hypot(dx, dy)
}

/** 拿窗口根 View（给 [ThemeReveal.start] 用）。拿不到时退回任意 View，调用方会走「不播动画」那条路。 */
fun Context.revealHostView(): View = findActivity()?.window?.decorView ?: View(this)

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * 扩散的遮罩层。放在根布局的**最后一层**，盖住一切（含底栏）。
 *
 * 画法：把整屏位图画出来，但路径是「全屏矩形 **减去** 一个圆」。
 * 圆越长越大，位图被吃掉的部分就越多，露出下面已经换好的新主题。
 *
 * 路径必须用 [PathOperation.Difference] 真算一次，不能把矩形和圆塞进同一个 Path：
 * 那样两个子路径同向，填充规则会取**并集**，结果永远是满屏。
 */
@Composable
fun ThemeRevealOverlay(modifier: Modifier = Modifier) {
    val reveal = ThemeReveal.active ?: return
    val progress = remember(reveal) { Animatable(0f) }

    LaunchedEffect(reveal) {
        /* 520ms：比页面转场（220-260ms）长，因为它要扫过整屏；
           又不能再长——用户按下开关后等动画，超过 0.6s 就开始不耐烦。 */
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 520, easing = RevealEasing),
        )
        ThemeReveal.clear()
    }

    Canvas(modifier = modifier) {
        /* 位图按窗口尺寸截的，这里按画布尺寸铺满：edge-to-edge 下两者相等，
           但不同 ROM 的状态栏处理会让它们差几个像素，铺满比露一条边好。 */
        val dst = IntSize(size.width.toInt(), size.height.toInt())
        val radius = reveal.maxRadius * progress.value
        val hole = Path().apply { addOval(Rect(center = reveal.origin, radius = radius)) }
        val mask = Path().apply {
            op(
                Path().apply { addRect(Rect(Offset.Zero, size)) },
                hole,
                PathOperation.Difference,
            )
        }
        clipPath(mask) {
            drawImage(
                image = reveal.snapshot,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(reveal.snapshot.width, reveal.snapshot.height),
                dstOffset = IntOffset.Zero,
                dstSize = dst,
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

/** 先快后慢：起步要跟手（按下就动），收尾要稳（别在边缘急停）。 */
private val RevealEasing = CubicBezierEasing(0.20f, 0.00f, 0.00f, 1.00f)
