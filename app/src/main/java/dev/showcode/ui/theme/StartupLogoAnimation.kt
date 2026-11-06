package dev.showcode.ui.theme

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.doOnPreDraw
import dev.showcode.R

/**
 * 系统 splash 只等到 App 第一帧；真正的动画是这层可随时移除的覆盖层。
 *
 * 机制、守卫顺序、时长与曲线照只读参考工程
 * `capybara-course-helper/.../StartupLogoAnimation.kt`：
 * 1. 进程内只播一次（`played`），热启动/重建不重播；
 * 2. 从系统 splash 的 iconView 读实际 bounds，覆盖层在同一位置接住，做到无跳变；
 * 3. 1100ms 线性时钟，850ms 前内容不可见，850–1100ms 整层淡出、页面/底栏上浮入场。
 *
 * 渲染不再是“一张 ic_splash 整体缩放”，而是从一张图集里逐层 blit 这 7 个语义层，
 * 包络全部来自 [StartupChoreography]。层与层之间的遮挡已在离线导出时补绘/裁切好，
 * 所以 t=0 与 t≥650ms 的合成就等于母版本身（无位移、无缩放、无额外遮罩）。
 */
object StartupLogoAnimation {
    private var played = false

    /** 初值 1f：跳过动画时内容必须立即可见，首帧不能空白。 */
    var contentProgress by mutableFloatStateOf(1f)
        private set

    fun install(activity: Activity) {
        val splash = activity.installSplashScreen()
        splash.setOnExitAnimationListener { provider ->
            if (played || activity.isFinishing || activity.isDestroyed) {
                provider.remove()
                return@setOnExitAnimationListener
            }
            played = true
            if (!ValueAnimator.areAnimatorsEnabled()) {
                provider.remove()
                return@setOnExitAnimationListener
            }
            val root = activity.window.decorView as? ViewGroup
            if (root == null) {
                provider.remove()
                return@setOnExitAnimationListener
            }
            showOverlay(activity, root, provider.iconView) { provider.remove() }
        }
    }

    internal fun showOverlay(activity: Activity, root: ViewGroup, icon: View, removeSplash: () -> Unit) {
        // 通知启动在 API 31+ 可能没有 splash 图标。AndroidX 这时给的是未附着、
        // 空 View，位置 (0,0) 而不是 logo——不能拿它当图标。
        if (!root.isAttachedToWindow || !icon.isAttachedToWindow) {
            removeSplash()
            return
        }
        var splashRemoved = false
        val removeOnce = {
            if (!splashRemoved) {
                splashRemoved = true
                removeSplash()
            }
        }
        val overlay = LogoOverlay(activity, removeOnce)
        // 图集在这里解码，而不是等覆盖层第一次 preDraw：系统 splash 此刻还在屏幕上，
        // 11MB 的 inflate 不会露出任何一帧底色；如果放到 preDraw 里，解码头耗时会让
        // 这一帧来不及绘制，splash 又被移除，就会闪出一帧未覆盖的 App 页面。
        overlay.prepare()
        root.addView(
            overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        overlay.doOnPreDraw {
            // 布局之后、移除系统视图之前读几何。绝不伪造不存在/隐藏/未附着图标的 bounds。
            val bounds = iconBounds(root, icon)
            if (bounds == null || activity.isFinishing || activity.isDestroyed) {
                removeOnce()
                root.removeView(overlay)
                return@doOnPreDraw
            }
            // 准备工作已在 splash 期间完成，这里只设几何、起表、交出 splash，
            // 三者都发生在同一帧的 preDraw 内，因此覆盖层和 splash 的交接没有空档。
            overlay.start(bounds) { root.removeView(overlay) }
            removeOnce()
        }
    }

    internal fun iconBounds(root: ViewGroup, icon: View): Rect? {
        if (!root.isAttachedToWindow || !icon.isAttachedToWindow || icon.windowToken != root.windowToken ||
            !root.isLaidOut || !icon.isLaidOut || !icon.isShown || icon.alpha <= 0f ||
            root.width <= 0 || root.height <= 0 || icon.width <= 0 || icon.height <= 0
        ) return null
        val location = IntArray(2)
        val rootLocation = IntArray(2)
        icon.getLocationOnScreen(location)
        root.getLocationOnScreen(rootLocation)
        val left = location[0] - rootLocation[0]
        val top = location[1] - rootLocation[1]
        return Rect(left, top, left + icon.width, top + icon.height).takeIf {
            it.left >= 0 && it.top >= 0 && it.right <= root.width && it.bottom <= root.height
        }
    }

    private class LogoOverlay(context: Context, private val releaseSplash: () -> Unit) : View(context) {
        /**
         * 回退路径（图集解码失败）仍然用 108dp 画布的 ic_splash：A 版图标四周各有 16dp
         * 透明留空，平台 splash 会裁掉留白把 76dp 插画铺满 192dp 图标视图（实测 576px @3x），
         * 覆盖层若按整张 108dp 画到同一 bounds 会缩水 76/108，这里反向放大接上。
         * 图集路径的 tile 本身就是 76dp 插画，不含留白，所以直接铺满同一 bounds，不再乘这个系数。
         */
        private val logoContentScale = 108f / 76f
        private var logoBounds: Rect? = null
        private var fallbackLogo: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_splash)?.mutate()
        private var atlas: Bitmap? = null
        private val backgroundPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.ic_launcher_background)
        }
        private val drawPaint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
        private val srcRect = Rect()
        private val dstRect = RectF()
        private var fraction = 0f
        private var animator: ValueAnimator? = null
        private var finish: (() -> Unit)? = null
        private var finished = false
        private var ending = false

        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

        /** 在 splash 仍可见时完成图集解码；只做一次，失败则整段走 ic_splash 回退。 */
        fun prepare() {
            atlas = decodeAtlas()
        }

        fun start(bounds: Rect, onFinish: () -> Unit) {
            if (finished) {
                onFinish()
                return
            }
            logoBounds = Rect(bounds)
            finish = onFinish
            contentProgress = 0f
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = StartupChoreography.DurationMillis
                interpolator = LinearInterpolator()
                addUpdateListener {
                    fraction = it.animatedValue as Float
                    val milliseconds = fraction * StartupChoreography.DurationMillis
                    contentProgress = StartupChoreography.content(milliseconds)
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { complete() }
                })
                start()
            }
        }

        /**
         * 单次解码，整段动画共用一张 bitmap。图集是 drawable-nodpi，不需要按密度缩放；
         * `inScaled = false` 防止平台再插值一遍。OOM 时退回 ic_splash，绝不因为显存不够而崩溃。
         */
        private fun decodeAtlas(): Bitmap? = try {
            val bitmap = BitmapFactory.decodeResource(
                resources,
                R.drawable.ic_launch_atlas,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                },
            )
            val minWidth = StartupChoreography.AtlasCellPx * StartupChoreography.AtlasColumns
            val minHeight = StartupChoreography.AtlasCellPx * 2
            if (bitmap == null || bitmap.width < minWidth || bitmap.height < minHeight) {
                bitmap?.recycle()
                null
            } else {
                bitmap
            }
        } catch (oom: OutOfMemoryError) {
            null
        }

        private fun complete() {
            if (finished || ending) return
            ending = true
            fraction = 1f
            contentProgress = 1f
            invalidate()
        }

        private fun releaseAfterDraw() {
            if (finished) return
            finished = true
            releaseAtlas()
            finish?.invoke()
            finish = null
        }

        private fun releaseAtlas() {
            atlas?.recycle()
            atlas = null
            fallbackLogo = null
        }

        override fun onDraw(canvas: Canvas) {
            val bounds = logoBounds ?: return
            val milliseconds = fraction * StartupChoreography.DurationMillis
            // 整层一起淡出。图标自带薄荷方底，若底色与图标分别叠 alpha，
            // 850–1100ms 会在图标范围内出现一个更浓的薄荷方块；saveLayer 让两者
            // 共用同一个不透明度，视觉等价且不会露色差。
            val alpha = ((1f - StartupChoreography.content(milliseconds)) * 255f).toInt().coerceIn(0, 255)
            canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), alpha)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
            val bitmap = atlas
            if (bitmap != null && !bitmap.isRecycled) {
                val settle = StartupChoreography.settleScale(milliseconds)
                canvas.save()
                canvas.scale(settle, settle, bounds.exactCenterX(), bounds.exactCenterY())
                drawLayers(canvas, bitmap, bounds, milliseconds)
                canvas.restore()
            } else {
                drawFallback(canvas, bounds, milliseconds)
            }
            canvas.restore()
            // 透明终帧先提交再移除视图，避免「最终内容更新 + decor relayout」并到同一帧。
            if (ending) post { releaseAfterDraw() }
        }

        private fun drawLayers(canvas: Canvas, bitmap: Bitmap, bounds: Rect, milliseconds: Float) {
            val artSize = bounds.width().toFloat()
            val pxPerDp = artSize / StartupChoreography.ArtSizeDp
            val tile = StartupChoreography.AtlasTilePx
            val pad = StartupChoreography.AtlasPadPx
            val cell = StartupChoreography.AtlasCellPx
            dstRect.set(0f, 0f, artSize, artSize)
            for (layer in StartupChoreography.Layers) {
                val spread = StartupChoreography.spread(milliseconds, layer.index)
                val scale = StartupChoreography.layerScale(milliseconds, layer)
                val dx = layer.dxDp * spread * pxPerDp
                val dy = layer.dyDp * spread * pxPerDp
                val col = layer.index % StartupChoreography.AtlasColumns
                val row = layer.index / StartupChoreography.AtlasColumns
                srcRect.set(col * cell + pad, row * cell + pad, col * cell + pad + tile, row * cell + pad + tile)
                drawPaint.alpha = if (layer.speedLine) {
                    (255f * StartupChoreography.speedAlpha(milliseconds)).toInt().coerceIn(0, 255)
                } else {
                    255
                }
                if (drawPaint.alpha == 0) continue
                val pivotX = layer.pivotX * artSize
                val pivotY = layer.pivotY * artSize
                canvas.save()
                canvas.translate(bounds.left + dx, bounds.top + dy)
                canvas.translate(pivotX, pivotY)
                canvas.scale(scale, scale)
                canvas.translate(-pivotX, -pivotY)
                canvas.drawBitmap(bitmap, srcRect, dstRect, drawPaint)
                canvas.restore()
            }
        }

        private fun drawFallback(canvas: Canvas, bounds: Rect, milliseconds: Float) {
            val logo = fallbackLogo ?: return
            val scale = StartupChoreography.settleScale(milliseconds) * logoContentScale
            canvas.save()
            canvas.scale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY())
            logo.bounds = bounds
            logo.alpha = 255
            logo.draw(canvas)
            canvas.restore()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                animator?.cancel()
                complete()
                // 和参考一样下一帧再移除，避免在自身触摸派发里把视图摘掉。
                post { releaseAfterDraw() }
            }
            // 必须吃掉整段手势：返回 false 时 FrameLayout 会把同一次点击继续派发给
            // 下面的 ComposeView，跳过动画的那一下会误触页面（实测会直接打开取件详情）。
            return true
        }

        override fun onDetachedFromWindow() {
            releaseSplash()
            contentProgress = 1f
            finished = true
            finish = null
            animator?.removeAllListeners()
            animator?.cancel()
            animator = null
            releaseAtlas()
            super.onDetachedFromWindow()
        }
    }
}
