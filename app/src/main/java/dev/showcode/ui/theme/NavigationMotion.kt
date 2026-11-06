package dev.showcode.ui.theme

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 页面转场的运动档案。数值逐条取自教务 `ui/theme/NavigationMotion.kt` 的 `MotionProfile`。
 *
 * 之前「仍不同源的三处」的第一处（页面切换与胶囊共用弹簧）由本文件补上。
 * 只声明**这轮真的有人消费**的条目——层级页 22dp/6dp、弹层 220ms、图标 280ms 那几条
 * 等子屏转场落地时再进档，不留没人消费的死配置。
 */
object MotionProfile {
    /** 主标签页：进场 8dp、离场 12dp / 0.985——小得要留意才看得出，这正是它显得「稳」的原因。 */
    val Navigation = PageTransitionSpec()

    /** 页面切换：主行程的弹簧（教务 pageSpring 0.74/180）。 */
    fun pageSpring() = spring<Float>(0.74f, 180f)

    /** 图标/数值状态切换：入场走完的时间（教务 IconMillis 280，AnimatedValueText 消费）。 */
    const val IconMillis = 280

    /** 按压类快速反馈：退出一档（教务 `MotionProfile.PressMillis = MotionDuration.Fast = 120`）。 */
    const val PressMillis = MotionDuration.Fast

    /** 层级页（子屏）进场位移：从右侧 +22dp 滑入。 */
    const val HierarchyEnterDp = 22f

    /** 被盖住的前页只退 6dp——它还在原地，只是让了一步。 */
    const val HierarchyBehindDp = 6f

    /** 层级页进出：比主屏切换略快略稳（教务 hierarchySpring 0.76/220）。 */
    fun hierarchySpring() = spring<Float>(0.76f, 220f)

    /** 按压/数值类小控件的状态弹簧（教务 iconSpring 0.68/420）。 */
    fun iconSpring() = spring<Float>(0.68f, 420f)
}

data class PageTransitionSpec(
    val enterDistance: Float = 8f,
    val exitDistance: Float = 12f,
    val exitScale: Float = 0.985f,
)

data class PageMotion(val x: Float = 0f, val alpha: Float = 1f, val scale: Float = 1f)

/**
 * 单进度页面运动。教务 `NavigationMotionState` 的同源移植。
 *
 * 它换来的四条体验（教务审计里「四个核心体验」的后三条）：
 * 1. **中断接续**：快速连点、反向切换时，从当前进度与速度继续，不跳回初始帧；
 * 2. **方向一致**：位移方向由 (目标 - 当前) 决定，反向切换自动反向；
 * 3. **手势交接**：底栏拖动松手时把当前位置和速度交进来（`releasedPosition/Velocity`），
 *    页面与胶囊是同一个动作的两面；
 * 4. **入场属于换屏**：模块错峰的时间线由这里发（[moduleProgress]），页面自己不重启。
 */
@Stable
class NavigationMotionState(initial: Int, private val scope: CoroutineScope) {
    private val progress = Animatable(1f)
    private val entrance = Animatable(0f)
    private var entranceStarts = mapOf(initial to 0f)
    private var initialized = false
    private var startPosition = initial.toFloat()
    private var endPosition = initial.toFloat()
    private var direction = 1f
    private var animation: Job? = null
    var target by mutableIntStateOf(initial)
        private set
    var pages by mutableStateOf(mapOf(initial to PageMotion()))
        private set

    /** 当前页面位置（可为小数）。哪一页在「近处」由它决定。 */
    val position: Float get() = startPosition + (endPosition - startPosition) * progress.value

    /** 页面权重：离目标越近越大，玻璃/内容可用它做连续的视觉权重。 */
    fun weight(page: Int): Float = (1f - abs(position - page)).coerceIn(0f, 1f)

    /** 某页模块入场时间线的当前值。 */
    fun moduleProgress(page: Int): Float {
        val start = entranceStarts[page] ?: 0f
        return if (page == target) start + (1f - start) * entrance.value else start
    }

    fun transform(page: Int): PageMotion {
        val start = pages[page] ?: PageMotion(alpha = 0f)
        val p = progress.value.coerceIn(0f, 1f)
        val incoming = page == target
        // 进场画布要早一步显形，否则错峰模块会整组缺席头几帧；离场按 0.35 的进度走完淡出。
        val alphaProgress = if (incoming) (p / 0.15f).coerceIn(0f, 1f) else (p / 0.35f).coerceIn(0f, 1f)
        val end = if (incoming) {
            PageMotion()
        } else {
            PageMotion(-direction * MotionProfile.Navigation.exitDistance, 0f, MotionProfile.Navigation.exitScale)
        }
        return PageMotion(
            start.x + (end.x - start.x) * p,
            start.alpha + (end.alpha - start.alpha) * alphaProgress,
            start.scale + (end.scale - start.scale) * p,
        )
    }

    /**
     * 切到 [page]。[releasedPosition] / [releasedVelocity] 来自手势松手：给了就从那里接续，
     * 不从静止态起步。
     */
    fun select(
        page: Int,
        reduced: Boolean,
        releasedPosition: Float? = null,
        releasedVelocity: Float? = null,
    ) {
        if (initialized && releasedPosition == null && page == target &&
            (!reduced || !progress.isRunning && !entrance.isRunning)
        ) return
        /* 只有冷启动第一次进场做模块错峰；之后的主屏切换全部直接到位。
           单层容器里若还从 0 起播，会露出一整帧「只有标题、内容空白」的页面。 */
        val animateEntrance = !initialized
        initialized = true
        val current = releasedPosition ?: position
        val velocity = releasedVelocity ?: (progress.velocity * (endPosition - startPosition))
        val snapshots = pages.keys.associateWith(::transform).filterValues { it.alpha > 0.001f }.toMutableMap()
        val moduleSnapshots = pages.keys.associateWith(::moduleProgress).toMutableMap()
        moduleSnapshots.putIfAbsent(page, 0f)
        entranceStarts = moduleSnapshots
        animation?.cancel()
        direction = if (page >= current) 1f else -1f
        target = page
        // 单层容器只画目标页：入场 alpha 必须直接是 1，否则旧页移除后会露出一帧空白底。
        // 位移仍从 ±enterDistance 起，弹簧曲线与时长不变。
        snapshots.putIfAbsent(page, PageMotion(direction * MotionProfile.Navigation.enterDistance, 1f, 1f))
        pages = snapshots
        startPosition = current
        endPosition = page.toFloat()
        animation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            coroutineScope {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    progress.snapTo(0f)
                    if (reduced) {
                        progress.snapTo(1f)
                    } else {
                        progress.animateTo(
                            1f,
                            MotionProfile.pageSpring(),
                            initialVelocity = if (abs(endPosition - startPosition) > 0.01f) {
                                velocity / (endPosition - startPosition)
                            } else {
                                0f
                            },
                        )
                    }
                }
                launch(start = CoroutineStart.UNDISPATCHED) {
                    entrance.snapTo(0f)
                    if (reduced || !animateEntrance) {
                        entrance.snapTo(1f)
                    } else {
                        entrance.animateTo(
                            1f,
                            tween(((1f - (moduleSnapshots[page] ?: 0f)) * ModuleMotion.TimelineMillis).toInt(), easing = LinearEasing),
                        )
                    }
                }
            }
            pages = mapOf(page to PageMotion())
            entranceStarts = mapOf(page to 1f)
        }
    }

    fun dispose() {
        animation?.cancel()
    }
}

val LocalNavigationMotion = staticCompositionLocalOf<NavigationMotionState?> { null }

@Composable
fun rememberNavigationMotionState(selected: Int, reduced: Boolean): NavigationMotionState {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { NavigationMotionState(selected, scope) }
    DisposableEffect(state) { onDispose { state.dispose() } }
    LaunchedEffect(state, selected, reduced) { state.select(selected, reduced) }
    return state
}

/**
 * 页面容器：**同一时刻只画目标页**，整层裁到窗口内。旧页在点击那一刻就移除，
 * 不再以半透明叠在新页上（那正是切换重影的来源）；位移/缩放/透明度仍由同一个
 * [NavigationMotionState] 发条驱动，曲线与时长不变。
 */
@Composable
fun NavigationPages(state: NavigationMotionState, modifier: Modifier = Modifier, content: @Composable (Int) -> Unit) {
    val density = LocalDensity.current
    val page = state.target
    Box(modifier.fillMaxSize().clipToBounds()) {
        key(page) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer {
                        val frame = state.transform(page)
                        translationX = with(density) { frame.x.dp.toPx() }
                        alpha = frame.alpha
                        scaleX = frame.scale
                        scaleY = frame.scale
                    },
            ) {
                CompositionLocalProvider(
                    LocalModuleEntrance provides remember(state, page) { { state.moduleProgress(page) } },
                ) {
                    content(page)
                }
            }
        }
    }
}

/**
 * 系统动画时长缩放为 0 = 用户要的是「关掉装饰动画」。
 * 自定义时长必须自己读这一项——Compose 的 tween/spring 会吃系统缩放，
 * 但我们手写的 Animatable 时长不会。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(readAnimatorScale(context) == 0f) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = readAnimatorScale(context) == 0f
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            true,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun readAnimatorScale(context: Context): Float =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
