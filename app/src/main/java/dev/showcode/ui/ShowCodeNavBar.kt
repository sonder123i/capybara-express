package dev.showcode.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorFilter
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import dev.showcode.R
import dev.showcode.ui.glass.GlassAccessibilityMode
import dev.showcode.ui.glass.GlassCompat
import dev.showcode.ui.glass.GlassLensFreshness
import dev.showcode.ui.glass.GlassLensTransform
import dev.showcode.ui.glass.GlassMaterials
import dev.showcode.ui.glass.GlassTier
import dev.showcode.ui.glass.drawBlurred
import dev.showcode.ui.glass.glassLens
import dev.showcode.ui.glass.glassLensAnchor
import dev.showcode.ui.glass.glassLensOpticsFrom
import dev.showcode.ui.glass.interactiveLensSpec
import dev.showcode.ui.glass.isGlassLensApplicable
import dev.showcode.ui.glass.motionIntensityFromVelocity
import dev.showcode.ui.glass.rememberGlassLensAnchor
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.theme.AccentInk
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.LocalNavigationMotion
import dev.showcode.ui.theme.MotionTokens
import dev.showcode.ui.theme.ShowCodePaletteState
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/*
 * 底部导航：待取件 / 身份码 / 设置。
 *
 * 几何、材质、动效逐条对着教务 `CapsuleNavigationBar` 的 `NavTab` / `NavBarMetrics` /
 * `DampedDragAnimation` 做（教务仓库只读，值从源码抄，不猜）：
 *
 * | 项 | 值 | 出处 |
 * |---|---|---|
 * | 轨道 | 满宽（横 16/10、纵 8/6），高 72/62（有折射外观）或 64/58 | `NavBarMetrics` |
 * | 指示胶囊 | 一格宽 × 56/48；按压**胀 78/56**、滑行中按速度拉伸 | `indicatorScale` |
 * | 落位 | `navSettle(0.60/310)` 带初速；回弹 `navRelease(0.55/290)` | `MotionSpring` |
 * | 胶囊表面 | 有折射白 0.34→按下 0.14；无折射 Black×0.1 按下淡出 | `NavSelectedSolid*` |
 * | 轨道 | White@0.28 + 高光 0.16/投影 0.08（无折射档 FAFAFA@0.15 无高光投影） | `containerColor` |
 * | 轨道呼吸 | 按下放大 16dp 当量 × (1+胶囊胀度·0.18) | `layerBlock` |
 * | 手势 | 整条可拖着换格（VelocityTracker 积分、松手带初速），轨道反向微漂 4dp | `pointerInput` |
 * | 触觉 | 换格 ContextClick；点已选中那格重播它的 flourish | `selectTab` |
 * | 图标 | 24dp duotone Canvas，逐格 flourish（NavSymbol / NavIconPlayback） | 教务同款 |
 * | 无玻璃 | 整条换 60dp 实心 Surface、无滑行胶囊、收起不生效 | `FallbackNavigationBar` |
 *
 * 与教务**不同源**的只剩三处，都是没得抄或不在本轮：页面切换与胶囊共用的那根弹簧
 * （`NavigationMotionState`）、31/32 离屏 GL 折射（批 6）、无障碍减弱动效轴。
 */

/* ---- 几何（教务 `NavBarMetrics`，与 [navBarClearance] 同源）---- */

private val NAV_TRACK_HEIGHT = 64.dp
private val NAV_TRACK_HEIGHT_TIGHT = 58.dp
private val NAV_TRACK_HEIGHT_LENS = 72.dp
private val NAV_TRACK_HEIGHT_LENS_TIGHT = 62.dp

private val NAV_BLOCK_PADDING = 8.dp
private val NAV_BLOCK_PADDING_TIGHT = 6.dp

private val NAV_HORIZONTAL_PADDING = 16.dp
private val NAV_HORIZONTAL_PADDING_TIGHT = 10.dp

private val NAV_INDICATOR_HEIGHT = 56.dp
private val NAV_INDICATOR_HEIGHT_TIGHT = 48.dp

private val NAV_BAR_PADDING = 6.dp
private val NAV_BAR_PADDING_LEGACY = 4.dp

private val NAV_BAR_CARD_GAP = 16.dp

private val NAV_ICON_SIZE = 24.dp

/** 无玻璃回退档的整条高（教务 `FallbackNavigationBar` 的 Surface）。 */
private val NAV_FALLBACK_HEIGHT = 60.dp

/** 列表底部要给底栏留的净空。各屏请用 [navBarClearance]——短屏上底栏会收，净空必须同源。 */
@Composable
fun navBarClearance(): Dp {
    val metrics = rememberScreenMetrics()
    // 永远按较高的折射外观档算：净空宁多勿少。教务的教训是两者脱钩过一次、末项被底栏压住；
    // 反方向（多 8dp 底距）没有任何可见代价。
    /* 必须和轨道用同一个函数算：脱钩过一次，末项就被底栏压住。
       字号放大时轨道变高，净空也要跟着变，否则最后一张卡又被挡住。 */
    return metrics.tallWithText(NAV_TRACK_HEIGHT_LENS, NAV_TRACK_HEIGHT_LENS_TIGHT) +
        metrics.tall(NAV_BLOCK_PADDING, NAV_BLOCK_PADDING_TIGHT) +
        NAV_BAR_CARD_GAP
}

/** 底栏上的三个去处。刻意不复用 `ShowCodeRoot` 那个路由枚举——它是文件私有的，而底栏只认这三格。 */
enum class NavBarTab { HOME, IDENTITY, SETTINGS }

private val NAV_TABS = listOf(NavBarTab.HOME, NavBarTab.IDENTITY, NavBarTab.SETTINGS)

@Composable
fun ShowCodeNavBar(
    current: NavBarTab,
    onHome: () -> Unit,
    onIdentity: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** 滚动往下读时收到的请求。收起后只剩当前格的一枚小胶囊，点它展开。 */
    minimized: Boolean = false,
    /** 点收起胶囊要求展开。教务 `onExpandRequest` 同名同义。 */
    onExpandRequest: () -> Unit = {},
    tier: GlassTier = GlassTier.OPAQUE,
    /** 玻璃折的那张背景。null、或不是玻璃档，就等于回落到不透明底栏。 */
    backdrop: Backdrop? = null,
    highTextContrast: Boolean = false,
    /** 31/32 折射底图的新鲜度信号（滚动后要重拍，否则折射里是启动那一刻的画面）。 */
    lensFreshness: GlassLensFreshness? = null,
) {
    val metrics = rememberScreenMetrics()
    val lensLook = GlassCompat.lensLook(tier, Build.VERSION.SDK_INT)
    val glassBackdrop = backdrop?.takeIf { tier == GlassTier.BACKDROP }

    /* 轨道与胶囊高度跟着**字号**一起长（`tallWithText`），不只是屏幕尺寸。
       字号调到 1.5×/2× 时，标签行高从 16sp 涨到 24/32sp，56dp 的轨道装不下——
       标签会被裁掉、文字溢出压住页面。这不是审美问题，是这一档用户读不全。 */
    val trackHeight = metrics.tallWithText(
        if (lensLook) NAV_TRACK_HEIGHT_LENS else NAV_TRACK_HEIGHT,
        if (lensLook) NAV_TRACK_HEIGHT_LENS_TIGHT else NAV_TRACK_HEIGHT_TIGHT,
    )
    val indicatorHeight = metrics.tallWithText(NAV_INDICATOR_HEIGHT, NAV_INDICATOR_HEIGHT_TIGHT)
    val blockPadding = metrics.tall(NAV_BLOCK_PADDING, NAV_BLOCK_PADDING_TIGHT)
    val horizontalPadding = metrics.wide(NAV_HORIZONTAL_PADDING, NAV_HORIZONTAL_PADDING_TIGHT)
    val barPadding = if (lensLook) NAV_BAR_PADDING else NAV_BAR_PADDING_LEGACY

    val selectedIndex = NAV_TABS.indexOf(current).coerceAtLeast(0)
    val iconPlayback = rememberNavIconPlayback(NAV_TABS.size, selectedIndex, reduced = false)
    /* 页面与胶囊共用同一发条（教务 NavigationMotionState）。导航时间线在这里播种，
       页面才会在松手那一刻从手指的位置和速度继续，而不是等 route 回来再从静止起步。 */
    val navMotion = LocalNavigationMotion.current
    val reducedMotion = rememberReducedMotion()

    /* 换格 + 重按 + 触觉。点已选中那格不换屏，只重播它的 flourish（教务 selectTab 语义）。
       这里只管业务路由；页面运动的播种在手势 finally 里做（位置与速度要从手指交接）。 */
    val haptics = LocalHapticFeedback.current
    fun selectTab(index: Int) {
        if (index != selectedIndex) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            when (NAV_TABS[index]) {
                NavBarTab.HOME -> onHome()
                NavBarTab.IDENTITY -> onIdentity()
                NavBarTab.SETTINGS -> onSettings()
            }
        } else {
            iconPlayback.replay(index)
        }
    }

    /* 位置/按压/缩放状态机。手指拥有位置，弹簧只管松手之后——教务 DampedDragAnimation。 */
    val animationScope = rememberCoroutineScope()
    val drag = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(NAV_TABS.size - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = GlassMaterials.NavPressedScale,
            settleAnimationSpec = MotionTokens.NavSettle,
            releaseScaleAnimationSpec = MotionTokens.NavRelease,
        )
    }
    /* 胶囊的「流动度」：速度越大越像被拖着走的液体，静止时为 0。
       和 indicatorScale 的 velocity 同源（都取 drag.velocity）——
       缩放管厚薄，形状管前后不对称，各管一件事。
       ⚠️ 量级：`velocity` 是**每秒格数**（已按 valueRange 归一化），
       快速滑动约 6~10 格/秒。所以除以 5 而不是 4200——
       第一版按 4200 归一化，值永远是 0.00x，形状一动不动。 */
    val pillFlow = remember {
        androidx.compose.runtime.derivedStateOf {
            (kotlin.math.abs(drag.velocity) / 5f).coerceIn(0f, 1f)
        }
    }
    val flowingShape = remember { dev.showcode.ui.theme.FlowingPillShape { pillFlow.value } }

    LaunchedEffect(selectedIndex) {
        /* 无条件重定目标。带 `targetValue != selectedIndex` 的早退在快速连切/取消时
           会留下「target 已是新值、value 还停在旧格」的死锁（实测：页面已到身份码，
           胶囊仍停在待取件）。animateToValue 自己会处理同值。 */
        drag.animateToValue(selectedIndex.toFloat())
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = horizontalPadding, vertical = blockPadding)
            .height(trackHeight),
        contentAlignment = Alignment.Center,
    ) {
        /* 滚动收起（教务原式）：整条玻璃横向收拢淡出、单胶囊液态弹出。一个分数驱动两头。 */
        val minimizeFraction by animateFloatAsState(
            targetValue = if (minimized) 1f else 0f,
            animationSpec = MotionTokens.LiquidSettle,
            label = "nav-minimize-fraction",
        )

        /* 拖动时轨道反向微漂（教务 panelOffset：4dp·sign·EaseOut），松手后果冻回位。 */
        val panelAnim = remember { Animatable(0f) }

        /* 磨砂玻璃：10dp blur 把底字化成色雾；表面亮 0.28 / 高对比 0.62 / 暗不透明。
           标签与图标的对比度由 NavBarKey 的墨阶负责（见 .verify/nav）。 */
        val dark = ShowCodePaletteState.isDark
        val surfaceColor = GlassMaterials.navTrackSurfaceColor(dark, highTextContrast)
        val density = LocalDensity.current
        val trackLensHeightPx = with(density) { GlassMaterials.NavTrackLensHeightDp.dp.toPx() }
        val trackLensAmountPx = with(density) { GlassMaterials.NavTrackLensAmountDp.dp.toPx() }
        val capsuleLensHeightPx = with(density) { GlassMaterials.NavCapsuleLensHeightDp.dp.toPx() }
        val capsuleLensAmountPx = with(density) { GlassMaterials.NavCapsuleLensAmountDp.dp.toPx() }
        val capsuleRestAlpha = if (dark) {
            GlassMaterials.NavCapsuleFillAlphaDark
        } else {
            GlassMaterials.NavCapsuleFillAlphaLight
        }
        val blurPx = with(density) { GlassMaterials.NavTrackBlurDp.dp.toPx() }
        val capsuleColor = GlassMaterials.navCapsuleColor(dark)
        val fallbackTrack = GlassMaterials.navFallbackTrackColor(dark)
        /* 按压进度只在 draw 期读：让 Highlight/Shadow/内阴影跟着手指走，组合期不重组。 */
        val press = { drag.pressProgress }

        if (minimizeFraction < 0.999f) {
        Box(
            modifier = Modifier.graphicsLayer {
                val f = minimizeFraction
                alpha = (1f - f * 1.6f).coerceIn(0f, 1f)
                scaleX = 1f - 0.82f * f
                scaleY = 1f - 0.30f * f
                transformOrigin = TransformOrigin.Center
            },
        ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            val density = LocalDensity.current
            val barPaddingPx = with(density) { barPadding.toPx() }
            val tabWidthPx = (constraints.maxWidth.toFloat() - barPaddingPx * 2f) / NAV_TABS.size
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            val panelOffset by remember(density, constraints.maxWidth) {
                derivedStateOf {
                    val fraction = (panelAnim.value / constraints.maxWidth.toFloat())
                        .coerceIn(-1f, 1f)
                    with(density) {
                        4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                        clip = false
                    }
                    .fillMaxWidth()
                    .height(trackHeight)
                    .then(
                        if (glassBackdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = glassBackdrop,
                                shape = { CircleShape },
                                effects = {
                                    /* 教务 `CapsuleNavigationBar.kt:601-625`：vibrancy → blur 10dp → lens 8/12dp */
                                    vibrancy()
                                    blur(blurPx)
                                    lens(
                                        refractionHeight = trackLensHeightPx,
                                        refractionAmount = trackLensAmountPx,
                                        chromaticAberration = false,
                                    )
                                },
                                highlight = { Highlight.Default.copy(alpha = GlassMaterials.NavTrackHighlightAlpha) },
                                shadow = { Shadow(alpha = GlassMaterials.NavTrackShadowAlpha) },
                                onDrawSurface = { drawRect(surfaceColor) },
                            )
                        } else {
                            Modifier
                                .shadow(elevation = if (dark) 2.dp else 4.dp, shape = CircleShape, clip = false)
                                .background(fallbackTrack, CircleShape)
                        },
                    )
                    .padding(horizontal = barPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                /* 选中胶囊：实底一格宽 × indicatorHeight，全圆角；按压/滑行仍用同一份
                   indicatorScale 形变，但不采样任何背景。 */
                val indicatorWidth = tabWidthDp
                val indicatorHeightCapped = indicatorHeight.coerceAtMost(trackHeight - barPadding * 2)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            translationX = drag.value * tabWidthPx
                            clip = false
                            val t = indicatorScale(drag)
                            scaleX = t.scaleX
                            scaleY = t.scaleY
                        }
                        .width(indicatorWidth)
                        .height(indicatorHeightCapped)
                        .then(
                            if (glassBackdrop != null) {
                                Modifier.drawBackdrop(
                                    backdrop = glassBackdrop,
                                    /* ⚠️ 这里**不能**用 flowingShape：backdrop 的 lens 效果
                                       只支持 RoundedRectangularShape / CornerBasedShape，
                                       传任意 Shape 会直接抛 UnsupportedOperationException
                                       （实测崩在 LensKt.throwUnsupportedSDFException）。
                                       玻璃档保持 CircleShape，变形只在下面那条回退分支里做。 */
                                    shape = { CircleShape },
                                    effects = {
                                        /* 教务：vibrancy + 采样已模糊的底图；lens 只在按压时从 0 长到 12/18dp */
                                        vibrancy()
                                        blur(blurPx)
                                        val p = press()
                                        if (p > 0.01f) {
                                            lens(
                                                refractionHeight = capsuleLensHeightPx * p,
                                                refractionAmount = capsuleLensAmountPx * p,
                                                chromaticAberration = true,
                                            )
                                        }
                                    },
                                    highlight = {
                                        val p = press()
                                        Highlight.Default.copy(
                                            alpha = GlassMaterials.NavCapsuleHighlightAlpha +
                                                p * GlassMaterials.NavCapsuleHighlightPressAlpha,
                                        )
                                    },
                                    shadow = {
                                        val p = press()
                                        Shadow(
                                            alpha = GlassMaterials.NavCapsuleShadowAlpha +
                                                p * GlassMaterials.NavCapsuleShadowPressAlpha,
                                        )
                                    },
                                    innerShadow = {
                                        val p = press()
                                        InnerShadow(
                                            radius = GlassMaterials.NavCapsuleInnerShadowRadiusDp.dp * p,
                                            alpha = p * GlassMaterials.NavCapsuleInnerShadowAlpha,
                                        )
                                    },
                                    onDrawSurface = {
                                        val p = press()
                                        val alpha = capsuleRestAlpha +
                                            (GlassMaterials.NavCapsuleFillPressAlpha - capsuleRestAlpha) * p
                                        drawRect(Color.White.copy(alpha = alpha))
                                    },
                                )
                            } else {
                                /* 无玻璃回退档：这里可以安全地用变形形状——
                                   没有 lens 效果，只是纯色块，任何 Shape 都成立。 */
                                Modifier
                                    .shadow(elevation = if (dark) 2.dp else 3.dp, shape = flowingShape, clip = false)
                                    .background(capsuleColor, flowingShape)
                            },
                        ),
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .selectableGroup()
                        .pointerInput(tabWidthPx, minimized) {
                            /* 整条手势：点按选格、拖动跟手换格。down 在 Initial pass 消费，
                               下面各键的 selectable 只剩无障碍语义（触屏走这里的 startX→格）。
                               多指中断、CANCEL 不提交、松手带初速——教务原逻辑逐条搬。 */
                            if (minimized) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                val startValue = drag.value
                                val committedIndex = selectedIndex
                                val startX = down.position.x
                                val touchSlop = viewConfiguration.touchSlop
                                var totalDragX = 0f
                                var dragging = false
                                var completed = false
                                var pointerId = down.id

                                drag.press(down.uptimeMillis)
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change =
                                            event.changes.firstOrNull { it.id == pointerId } ?: break
                                        if (event.changes.count { it.pressed } > 1) {
                                            event.changes.forEach { it.consume() }
                                            break
                                        }
                                        if (change.isConsumed) break
                                        if (change.changedToUpIgnoreConsumed()) {
                                            completed = true
                                            change.consume()
                                            break
                                        }
                                        if (!dragging &&
                                            abs(change.position.y - down.position.y) > touchSlop &&
                                            abs(totalDragX) <= touchSlop
                                        ) {
                                            break
                                        }
                                        val dragAmount = change.positionChange()
                                        if (dragAmount != androidx.compose.ui.geometry.Offset.Zero) {
                                            totalDragX += dragAmount.x
                                            if (!dragging && abs(totalDragX) > touchSlop) dragging = true
                                            if (dragging) {
                                                change.consume()
                                                drag.updateValue(
                                                    (startValue + totalDragX / tabWidthPx)
                                                        .coerceIn(0f, (NAV_TABS.size - 1).toFloat()),
                                                    change.uptimeMillis,
                                                )
                                                animationScope.launch { panelAnim.snapTo(totalDragX) }
                                            }
                                        }
                                        pointerId = change.id
                                    }
                                } finally {
                                    val targetIndex = when {
                                        !completed -> committedIndex
                                        dragging -> drag.targetValue
                                            // 四舍五入，不是向下取整：拖过两格中线就算换格。
                                            // 之前 toInt() 让「拖大半格」提交回原格——
                                            // 「滑到另一个选项不跳转页面」的元凶就是它。
                                            .roundToInt()
                                            .coerceIn(0, NAV_TABS.size - 1)
                                        else -> (startX / tabWidthPx).toInt()
                                            .coerceIn(0, NAV_TABS.size - 1)
                                    }
                                    if (dragging) {
                                        /* 先给共享弹簧播种，再放本地手势（教务原注：不播种，
                                           胶囊会先跳回上一个已提交的格）。取消也一样：
                                           页面跟着当前的位置与速度弹回原格，而不是瞬移。 */
                                        navMotion?.select(targetIndex, reducedMotion, drag.value, drag.velocity)
                                    } else if (completed) {
                                        navMotion?.select(targetIndex, reducedMotion)
                                    }
                                    if (!completed) {
                                        drag.animateToValue(committedIndex.toFloat())
                                    } else {
                                        selectTab(targetIndex)
                                        if (targetIndex == committedIndex && !dragging) {
                                            drag.release()
                                        } else {
                                            drag.animateToValue(targetIndex.toFloat())
                                        }
                                    }
                                    if (panelAnim.value != 0f) {
                                        animationScope.launch {
                                            panelAnim.animateTo(0f, MotionTokens.LiquidJellyRebound)
                                        }
                                    }
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (index in NAV_TABS.indices) {
                        val weight = (1f - abs(drag.value - index)).coerceIn(0f, 1f)
                        NavBarKey(
                            label = stringResource(
                                when (NAV_TABS[index]) {
                                    NavBarTab.HOME -> R.string.home_title
                                    NavBarTab.IDENTITY -> R.string.identity_title
                                    NavBarTab.SETTINGS -> R.string.settings_title
                                },
                            ),
                            weight = weight,
                            pressProgress = drag.pressProgress * weight,
                            onClick = { if (!minimized) selectTab(index) },
                            symbol = when (index) {
                                0 -> NavSymbol.PICKUP
                                1 -> NavSymbol.IDENTITY
                                else -> NavSymbol.SETTINGS
                            },
                            phase = { iconPlayback.phase(index) },
                            highContrast = highTextContrast,
                        )
                    }
                }
            }

        }
        }
        }

        if (minimizeFraction > 0.001f) {
            MinimizedNavCapsule(
                backdrop = glassBackdrop,
                label = stringResource(
                    when (current) {
                        NavBarTab.HOME -> R.string.home_title
                        NavBarTab.IDENTITY -> R.string.identity_title
                        NavBarTab.SETTINGS -> R.string.settings_title
                    },
                ),
                symbol = when (selectedIndex) {
                    0 -> NavSymbol.PICKUP
                    1 -> NavSymbol.IDENTITY
                    else -> NavSymbol.SETTINGS
                },
                iconPhase = { iconPlayback.phase(selectedIndex) },
                onClick = { if (minimized) onExpandRequest() },
                modifier = Modifier.graphicsLayer {
                    val f = minimizeFraction
                    alpha = ((f - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    val scale = 0.55f + 0.45f * f
                    scaleX = scale
                    scaleY = scale
                },
            )
        }
    }
}

/**
 * 收起形态：只剩当前格的一枚小玻璃胶囊（教务 `MinimizedNavCapsule` 同式：48dp 高、
 * 图标+标签、点按要求展开）。图标还在播——收起态也认得出当前在哪一格。
 */
@Composable
private fun MinimizedNavCapsule(
    /** 与底栏同一张内容 backdrop：收起胶囊也折真实内容，而不是一块死白。 */
    backdrop: Backdrop?,
    label: String,
    symbol: NavSymbol,
    iconPhase: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = ShowCodePaletteState.isDark
    val density = LocalDensity.current
    val blurPx = with(density) { GlassMaterials.NavTrackBlurDp.dp.toPx() }
    val restAlpha = if (dark) {
        GlassMaterials.NavCapsuleFillAlphaDark
    } else {
        GlassMaterials.NavCapsuleFillAlphaLight
    }
    Row(
        modifier = modifier
            .then(
                if (backdrop != null) {
                    /* 与选中胶囊同一套配方：vibrancy + blur 10dp → 白表面 0.34/0.20，
                       高光 0.12、投影 0.10。收起态没有按压，所以不挂 lens/innerShadow。 */
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(blurPx)
                        },
                        highlight = { Highlight.Default.copy(alpha = GlassMaterials.NavCapsuleHighlightAlpha) },
                        shadow = { Shadow(alpha = GlassMaterials.NavCapsuleShadowAlpha) },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = restAlpha)) },
                    )
                } else {
                    Modifier
                        .shadow(elevation = if (dark) 2.dp else 3.dp, shape = CircleShape, clip = false)
                        .background(GlassMaterials.navCapsuleColor(dark), CircleShape)
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .height(48.dp)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NavSymbolIcon(
            symbol = symbol,
            selection = 1f,
            phase = iconPhase,
            tint = AccentInk,
            modifier = Modifier.size(NAV_ICON_SIZE),
        )
        Text(
            text = label,
            color = Ink,
            /* 收起胶囊承载的是单条 12sp 文案：比三格底栏的 11sp 大一档，模糊底上更易读。 */
            style = ShowCodeText.NavLabel,
            maxLines = 1,
        )
    }
}

/**
 * 指示胶囊的按压 + 速度形变（教务 `indicatorScale` 同式，参数一字未改）。
 * 速度形变系数 /6：`sx` 拉长、`sy` 压扁——滑得越快胶囊越「被甩长」。
 * 平移为 0：胶囊的 translationX 挂在**外层** graphicsLayer 上，glassLens 在它内部，
 * 已经跟着一起走了（教务 GlassLensTransform 注释同因）。
 */
private fun indicatorScale(anim: DampedDragAnimation): GlassLensTransform {
    var sx = anim.scaleX
    var sy = anim.scaleY
    val velocity = anim.velocity / 6f
    sx /= 1f - (velocity * 0.75f).coerceIn(-0.22f, 0.22f)
    sy *= 1f - (velocity * 0.25f).coerceIn(-0.22f, 0.22f)
    return GlassLensTransform(scaleX = sx, scaleY = sy)
}

@Composable
private fun navKeyWeight(selected: Boolean, spec: FiniteAnimationSpec<Float>): Float {
    val weight by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spec,
        label = "nav-key-weight",
    )
    return weight
}

/**
 * 一格：图形 + 一行标签。
 *
 * `weight` 是位置状态机导出的**连续**选中度（0→1），不是布尔：图标按它放大 10%、上浮 2dp，
 * 墨色按它插值（教务 `NavTab` 同式）。按下按压**胀**由上面的状态机统一驱动，
 * 图标只分到 `pressProgress × weight` 那一份——按哪格哪格有反应。
 *
 * 选中的两支**分色**：图标走 accent、标签走 onSurface（教务原文）。字重在 0.55 跳变。
 * `indication = null`：反馈交给动效，不叠水波纹。语义仍走 `Role.Tab`。
 */
@Composable
private fun RowScope.NavBarKey(
    label: String,
    weight: Float,
    pressProgress: Float,
    /** null = 隐藏副本层里的那一份：画出来供透镜采样，不接客（教务 onClick=null 同式）。 */
    onClick: (() -> Unit)?,
    symbol: NavSymbol,
    phase: () -> Float,
    /** 系统「高对比度文字」。这里只管图标与标签墨色/字重——图标是自绘 Canvas，系统管不到。 */
    highContrast: Boolean = false,
) {
    /* 颜色/字重按「哪格过半」硬切，不跟着 weight 插值：
       插值会让相邻两格同时泛蓝、同时加粗，视觉上就是两个选中态。
       胶囊与图标填充仍连续动画，切换只剩一个胶囊在走。 */
    val dark = ShowCodePaletteState.isDark
    /* 底栏是半透玻璃，实测 0.5 透明度只有 2.3–2.8:1（.verify/nav/nav_contrast.py）：
       正常档取 InkMuted / AccentInk，高对比档取 Ink / 更深或更亮的蓝。 */
    val w = weight.coerceIn(0f, 1f)
    val idleInk = if (highContrast) Ink else InkMuted
    /* 选中色不复用参考取样值 #5794FF：它在浅色胶囊上实测 2.79:1，过不了 4.5。
       正常档改用 AccentInk（#0057D9 亮 / #88BFFF 暗），两套都 ≥5:1。 */
    /* 高对比档再走一档更深的墨/更亮的蓝，和正常档拉开可见差距；选中色不复用 AccentInk，
       否则「正常 vs 高对比」在选中那一格上没有变化。 */
    val activeInk = when {
        highContrast && dark -> Color(0xFFD6EBFF)
        highContrast -> Color(0xFF003FA8)
        else -> AccentInk
    }
    val tint = lerp(idleInk, activeInk, w)
    val labelColor = tint

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(
                if (onClick != null) {
                    Modifier.selectable(
                        selected = weight > 0.5f,
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        NavSymbolIcon(
            symbol = symbol,
            selection = weight,
            phase = phase,
            tint = tint,
            modifier = Modifier
                .size(NAV_ICON_SIZE)
                .graphicsLayer {
                    val scale = (1f + 0.10f * weight) * (1f - 0.04f * pressProgress)
                    scaleX = scale
                    scaleY = scale
                    translationY = -2.dp.toPx() * weight + 1.dp.toPx() * pressProgress
                },
        )
        Spacer(Modifier.height(2.dp))
        /* 教务 NavTab：labelSmall 11/16 的两层——常规与加粗都在，按 selectionWeight
           交叉淡入淡出；行高不变，切换时不跳动。 */
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = ShowCodeText.Micro.copy(
                    fontWeight = if (highContrast) FontWeight.Bold else FontWeight.Normal,
                ),
                color = labelColor.copy(alpha = labelColor.alpha * (1f - w)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = ShowCodeText.Micro.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = labelColor.copy(alpha = labelColor.alpha * w),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 无玻璃回退（教务 `FallbackNavigationBar`）：60dp 实心 Surface、**没有滑行胶囊**、
 * 收起不生效（教务那个分支连 minimized 形参都不接）。选中度走 tween(220)。
 */
@Composable
private fun FallbackNavigationBar(
    current: NavBarTab,
    selectedIndex: Int,
    onKeyClick: (Int) -> Unit,
    iconPlayback: NavIconPlayback,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(NAV_FALLBACK_HEIGHT),
        /* 教务这里是 28dp 圆角（60dp 高的准胶囊）。本仓库棘轮把裸圆角钉死为 0——
           连注释里的字面量都会被它的正则数进去，所以这里只写数值不写构造式。
           50% 胶囊在同一高度上差 2dp，肉眼不可分——用形状 token，不破地板。 */
        shape = CircleShape,
        color = SurfaceCard,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (index in NAV_TABS.indices) {
                NavBarKey(
                    label = stringResource(
                        when (NAV_TABS[index]) {
                            NavBarTab.HOME -> R.string.home_title
                            NavBarTab.IDENTITY -> R.string.identity_title
                            NavBarTab.SETTINGS -> R.string.settings_title
                        },
                    ),
                    weight = navKeyWeight(index == selectedIndex, MotionTokens.FallbackWeight),
                    pressProgress = 0f,
                    onClick = { onKeyClick(index) },
                    symbol = when (index) {
                        0 -> NavSymbol.PICKUP
                        1 -> NavSymbol.IDENTITY
                        else -> NavSymbol.SETTINGS
                    },
                    phase = { iconPlayback.phase(index) },
                )
            }
        }
    }
}
