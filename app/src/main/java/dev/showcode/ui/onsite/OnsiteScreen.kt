package dev.showcode.ui.onsite

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.showcode.R
import dev.showcode.core.LaunchOutcome
import dev.showcode.core.NextStop
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.PointKind
import dev.showcode.core.Urgency
import dev.showcode.core.countdownOf
import dev.showcode.core.staleDaysOf
import dev.showcode.core.urgencyOf
import dev.showcode.ui.ConfettiBurst
import dev.showcode.ui.copyCode
import dev.showcode.ui.performTrackingLookup
import dev.showcode.ui.pointIconOf
import dev.showcode.ui.deadlineText
import dev.showcode.ui.lockerLabelOf
import dev.showcode.ui.relativeTimeText
import dev.showcode.ui.rememberTickingNow
import dev.showcode.ui.theme.AccentInk
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkFaint
import dev.showcode.ui.theme.MotionDuration
import dev.showcode.ui.theme.MotionEasing
import dev.showcode.ui.theme.MotionProfile
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.OnsiteInk
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceFloating
import dev.showcode.ui.theme.SurfaceInsetStrong
import dev.showcode.ui.theme.TabularNumbers
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencyDueSoonTint
import dev.showcode.ui.theme.environmentBackground
import dev.showcode.ui.theme.UrgencySafe
import dev.showcode.ui.theme.UrgencySafeTint
import dev.showcode.ui.theme.rememberReducedMotion
import kotlin.math.abs
import kotlinx.coroutines.delay

/** 平铺入口的件数门槛（与 H5 参照一致：>3 件时用户不想滑那么多次）。 */
private const val TILES_ENTRY_THRESHOLD = 3

/** 撤销窗口。现场误触成本极高（手抖、戴手套、屏幕有水、后面有人催），没有撤销的「已取」是危险的。 */
private const val UNDO_WINDOW_MS = 3_000L

/**
 * 现场模式（全屏出示）。story-4 —— 整个产品唯一不可妥协的一屏，
 * 其他 6 条故事都是为了让用户能走到这里。
 *
 * 使用环境极差：户外强光、单手、戴手套、屏幕有水、身后有人、注意力在柜机而不在手机上。
 * 所以这一屏里任何需要精确点击、需要读小字、需要等待转圈的东西都是失败。
 */
@Composable
fun OnsiteScreen(
    pointKey: String,
    pointName: String,
    items: List<Parcel>,
    now: Long,
    isDemo: Boolean,
    focusParcelId: Long?,
    lastPickupAt: Long?,
    allPickedHere: Boolean,
    /** 这个点清空后的下一站。null = 今天没有别的要拿了，或正在演示。 */
    nextStop: NextStop?,
    /** 取件点类型：顶栏的类型图标靠它画。深链兜底时可能为 null。 */
    pointKind: PointKind?,
    /** 代取件分组的名字，用户可改（story-7 AC2）。 */
    familyLabel: String,
    onBack: () -> Unit,
    onExitDemo: () -> Unit,
    onOpenTiles: (Long) -> Unit,
    onMarkPicked: (Parcel) -> Unit,
    onUndoPicked: (Parcel) -> Unit,
    onOpenNextPoint: (NextStop) -> Unit,
    onRefresh: () -> Unit,
    /** 空码件的出口：拉起淘宝查询单号。返回值决定失败时要不要给降级提示。 */
    onOpenTaobao: (Parcel) -> LaunchOutcome = { LaunchOutcome.NoTarget },
    /** 空码件的详情页入口：运单号、阶段、完整轨迹。 */
    onOpenParcelDetail: (Parcel) -> Unit = {},
) {
    val context = LocalContext.current
    var group by remember { mutableStateOf(Owner.SELF) }
    var undoTarget by remember { mutableStateOf<Parcel?>(null) }
    var staleNoticeDismissed by remember { mutableStateOf(false) }
    /* 演示态的点击反馈留在 App 内：不写剪贴板（Android 13+ 会冒系统浮层），
       也不拉起淘宝。录制时任何一帧都不该出现系统 UI。 */
    var demoFeedback by remember { mutableStateOf<String?>(null) }
    val copiedCodeFeedback = stringResource(R.string.onsite_copied)
    val copiedTrackingFeedback = stringResource(R.string.demo_feedback_copied)
    val queryFeedback = stringResource(R.string.demo_feedback_query)
    LaunchedEffect(demoFeedback) {
        if (demoFeedback != null) {
            delay(2_200)
            demoFeedback = null
        }
    }

    val familyCount = items.count { it.owner == Owner.FAMILY }
    val selfCount = items.count { it.owner == Owner.SELF }
    val showGroupSwitch = familyCount > 0 && selfCount > 0
    // 代取件单独成组，轮播只在当前组内进行 → 天然不跨组（boundary-daqu 红线：
    // 在自己的码和家人的码之间迷失，会直接抵消「找得准」这个核心价值）
    val groupItems = remember(items, group, showGroupSwitch) {
        if (showGroupSwitch) items.filter { it.owner == group } else items
    }

    // 临期件需要逐秒变化的倒计时，所以有临期件时 1 秒一跳，否则 30 秒足够
    val hasDueSoon = groupItems.any { urgencyOf(it, now) == Urgency.DUE_SOON }
    /* 演示态冻结时钟：倒计时文本在整段录制里保持不动，方便截帧，也不会自己跨状态。 */
    val tickingNow = if (isDemo) now else rememberTickingNow(if (hasDueSoon) 1_000L else 30_000L)

    val pagerState = rememberPagerState { groupItems.size }

    /* 静默只在「有码可看」时启用：收工卡上还藏着出口，等于把用户关在空屏里。
       读屏开着时也不启用——那些控件不是装饰，是导航。 */
    val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
        as? android.view.accessibility.AccessibilityManager
    val silenceEnabled = !isDemo && groupItems.isNotEmpty() &&
        (accessibility == null || (!accessibility.isEnabled && !accessibility.isTouchExplorationEnabled))
    val silence = rememberSilenceState(
        enabled = silenceEnabled,
        resetKey = listOf(pagerState.currentPage, group, undoTarget?.id, staleNoticeDismissed, focusParcelId),
    )

    // 从平铺总览点回来的那一件可能属于另一个组（代取件），所以先切组。
    LaunchedEffect(focusParcelId) {
        val target = items.firstOrNull { it.id == focusParcelId } ?: return@LaunchedEffect
        if (showGroupSwitch && target.owner != group) group = target.owner
    }
    // 定位只跟 (group, focusParcelId) 走，**不能**跟列表长度走：
    // 点「已取」后列表会少一件，若把 size 当 key，这里就会滚回第 0 页，
    // 而正确的行为是保持索引不动——列表缩短一格，下一件自然滑到眼前。
    LaunchedEffect(group, focusParcelId) {
        val target = groupItems.indexOfFirst { it.id == focusParcelId }
        val page = if (target >= 0) target else 0
        if (pagerState.currentPage != page) pagerState.scrollToPage(page)
    }

    LaunchedEffect(undoTarget) {
        val target = undoTarget ?: return@LaunchedEffect
        delay(UNDO_WINDOW_MS)
        if (undoTarget == target) undoTarget = null
    }

    /* 从收工卡直接跳到下一个点时，这一屏不会重建（route 仍是 ONSITE），
       所以本地状态必须自己归零。少了这一笔，新点只有代取件时会出现一个空的现场模式；
       而 staleNotice 挂着上一个点的判断，也是同一类错。

       首次组合必须跳过：从平铺页带着 focusParcelId 进来时，
       上面那条定位 effect 已经把页码挪到目标件，这里再拉回 0 就是把它抹掉。 */
    var lastPointKey by remember { mutableStateOf(pointKey) }
    LaunchedEffect(pointKey) {
        if (lastPointKey == pointKey) return@LaunchedEffect
        lastPointKey = pointKey
        group = Owner.SELF
        staleNoticeDismissed = false
        // 换点了，上一个点的撤销窗口就结束了：留在这儿的话，撤销条会对着新点的码
        // 去撤销旧点的那一件——屏幕上看起来完全正常，而那正是最坏的错。
        undoTarget = null
        pagerState.scrollToPage(0)
    }

    /* pointerInput(Unit) 不随重组重启，所以必须由 rememberUpdatedState 接住最新的 wake。 */
    val latestWake by rememberUpdatedState(silence.wake)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .environmentBackground()
            /* 任何一次按下都重新计时——包括长按复制这种不改状态的动作。 */
            .onsiteTouchObserver { latestWake() }
    ) {
        /* 演示态不铺整屏水印：顶部常驻「这是演示数据」已经说清了来源，
           而 60sp 的「示例」会压在取件码上，录制截帧时要干净。 */
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            /* 控件整块淡出：布局占位不动，所以码不会因为按钮消失而跳一下。 */
            Column(modifier = Modifier.graphicsLayer { alpha = silence.chromeAlpha }) {
                OnsiteTopBar(pointName = pointName, pointKind = pointKind, isDemo = isDemo, onBack = onBack, onExitDemo = onExitDemo)

                if (showGroupSwitch) {
                    GroupSwitch(
                        selfCount = selfCount,
                        familyCount = familyCount,
                        familyLabel = familyLabel,
                        selected = group,
                        onSelect = { group = it },
                    )
                }
            }

            if (groupItems.isEmpty()) {
                val undoSlot: (@Composable () -> Unit)? = undoTarget?.let { target ->
                    {
                        UndoBar(isDemo = isDemo, onUndo = {
                            onUndoPicked(target)
                            undoTarget = null
                        })
                    }
                }
                OnsiteEmpty(
                    allPickedHere = allPickedHere,
                    lastPickupAt = lastPickupAt,
                    now = now,
                    isDemo = isDemo,
                    nextStop = nextStop,
                    onBack = onBack,
                    onOpenNextPoint = onOpenNextPoint,
                    undo = undoSlot,
                    modifier = Modifier.weight(1f),
                )
            } else {
                val page = pagerState.currentPage.coerceIn(0, groupItems.lastIndex)
                val current = groupItems[page]

                // error-stale-code：告知风险，但绝不阻断出示——他可能确实需要这个码
                val staleDays = staleDaysOf(current, tickingNow)
                if (staleDays > 1 && !staleNoticeDismissed) {
                    Box(modifier = Modifier.graphicsLayer { alpha = silence.chromeAlpha }) {
                        StaleCodeNotice(
                            days = staleDays,
                            showRefresh = !isDemo,
                            onDismiss = { staleNoticeDismissed = true },
                            onRefresh = onRefresh,
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { index ->
                    val parcel = groupItems.getOrElse(index) { return@HorizontalPager }
                    val urgency = urgencyOf(parcel, tickingNow)
                    // 邻页露边（教务 SchedulePagerMotion 同式）：缩 0.97、淡 0.22，
                    // 多件时暗示「旁边还有一件」；单件时 offset 恒为 0，不出现。
                    val pageOffset = abs(pagerState.currentPage - index + pagerState.currentPageOffsetFraction)
                        .coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier.graphicsLayer {
                            scaleX = 1f - 0.03f * pageOffset
                            scaleY = 1f - 0.03f * pageOffset
                            alpha = 1f - 0.22f * pageOffset
                        },
                    ) {
                    OnsiteCodeBody(
                        parcel = parcel,
                        urgency = urgency,
                        lockerLabel = lockerLabelOf(parcel.pointKind),
                        deadlineText = deadlineText(parcel, urgency, tickingNow),
                        chromeAlpha = silence.chromeAlpha,
                        onCopyCode = {
                            if (isDemo) demoFeedback = copiedCodeFeedback
                            else copyCode(context, parcel.code, R.string.onsite_copied)
                        },
                        onCopyTracking = {
                            if (isDemo) demoFeedback = copiedTrackingFeedback
                            else trackingLookupText(parcel)?.let {
                                copyCode(context, it, R.string.onsite_tracking_copied)
                            }
                        },
                        onQueryTaobao = {
                            if (isDemo) demoFeedback = queryFeedback
                            else performTrackingLookup(context, parcel, onOpenTaobao)
                        },
                        onOpenDetail = { onOpenParcelDetail(parcel) },
                    )
                    }
                }

                undoTarget?.let { target ->
                    UndoBar(isDemo = isDemo, onUndo = {
                        onUndoPicked(target)
                        undoTarget = null
                    })
                }

                Column(modifier = Modifier.graphicsLayer { alpha = silence.chromeAlpha }) {
                    PositionRow(
                        index = page,
                        total = groupItems.size,
                        showTilesEntry = groupItems.size > TILES_ENTRY_THRESHOLD,
                        onOpenTiles = { onOpenTiles(current.id) },
                    )

                    // 黑底白字，与整屏的纯黑 on 纯白一致：直射阳光下饱和蓝比黑更容易发灰
                    PickedButton(onClick = {
                        // 视觉反馈必须和点击同一帧发生：状态改在父级同步完成，这里只负责起撤销窗口
                        undoTarget = current
                        onMarkPicked(current)
                    })
                }
            }
        }

        /* 静默时的一行出口。只在控件已经退掉（alpha < 1）时才进组合：
           常驻一个 alpha=0 的文本会让读屏在正常态也读到「轻触恢复」。 */
        if (silence.chromeAlpha < 1f) {
            Text(
                text = stringResource(R.string.onsite_silence_hint),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 14.dp)
                    .graphicsLayer { alpha = 1f - silence.chromeAlpha },
                fontSize = 11.sp,
                color = InkMuted,
            )
        }

        /* 演示态的点击反馈：App 内浮层，不碰剪贴板、不拉起外部 App。 */
        demoFeedback?.let { message ->
            /* 浮层要自带面与边：原来只铺 6% 灰，压在商品卡上时字和卡标题会叠在一起。 */
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    /* 落在「已取」按钮上方：顶部会压住商品标题，底部这段只有件数，损失最小。 */
                    .padding(bottom = 104.dp),
                shape = ShowCodeShapes.Pill,
                color = SurfaceFloating,
                contentColor = Ink,
                border = BorderStroke(1.dp, Hairline),
                shadowElevation = 6.dp,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = ShowCodeText.Meta,
                    color = Ink,
                )
            }
        }

        /* 最上层：第一下按下就唤醒，并把这个手势吃掉。 */
        SilenceCatcher(silence)
    }
}

/**
 * 顶部条。刻意与 H5 参照有一处分歧：参照把点名截断到 190px，
 * 但 edge 的 boundary-long-text 明确要求「现场模式必须完整显示」——
 * 现场用户要靠完整名称确认「我来对地方了」。所以这里允许换行，不设 maxLines。
 */
@Composable
private fun OnsiteTopBar(
    pointName: String,
    pointKind: PointKind?,
    isDemo: Boolean,
    onBack: () -> Unit,
    onExitDemo: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.onsite_back))
            }
            // 类型锚点：与首页 PointCard 同一颗图标。「来对地方了」多一个不看字的确认。
            pointKind?.let {
                Icon(
                    painter = painterResource(id = pointIconOf(it)),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = InkSecondary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = pointName,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                style = ShowCodeText.Section,
                color = Ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(40.dp))
        }

        // 演示态退出条：常驻，退出后不残留任何示例数据
        if (isDemo) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceInsetStrong)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.onsite_demo_bar),
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = OnsiteInk.copy(alpha = 0.6f),
                )
                Text(
                    text = stringResource(R.string.onsite_demo_exit),
                    modifier = Modifier
                        .clickable(onClick = onExitDemo)
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentInk,
                )
            }
        }
    }
}

@Composable
private fun GroupSwitch(
    selfCount: Int,
    familyCount: Int,
    familyLabel: String,
    selected: Owner,
    onSelect: (Owner) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GroupTab(
            label = stringResource(R.string.onsite_group_self, selfCount),
            selected = selected == Owner.SELF,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(Owner.SELF) },
        )
        GroupTab(
            label = stringResource(R.string.onsite_group_family, familyLabel, familyCount),
            selected = selected == Owner.FAMILY,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(Owner.FAMILY) },
        )
    }
}

@Composable
private fun GroupTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = modifier
            .background(if (selected) OnsiteInk else SurfaceInsetStrong, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = if (selected) OnsiteSurface else OnsiteInk.copy(alpha = 0.6f),
    )
}

/**
 * 该点无件：不能是空白屏——用户站在柜机前看到空白会恐慌，会怀疑自己记错了地方。
 *
 * 取完最后一件时这里不是终点，是换乘站：告诉他下一站是哪个点、还有几件、还剩多久，
 * 点一下直接落过去。这样「一趟取三个点」才有一条连续的线，而不是三次回首页再找卡片。
 */
@Composable
private fun OnsiteEmpty(
    allPickedHere: Boolean,
    lastPickupAt: Long?,
    now: Long,
    isDemo: Boolean,
    nextStop: NextStop?,
    onBack: () -> Unit,
    onOpenNextPoint: (NextStop) -> Unit,
    undo: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    /* 收工卡整块淡入 + 上浮 10dp：它是「这一趟结束了」的一次入场，不是常驻布局。 */
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) enter.animateTo(1f, tween(MotionDuration.Slow, easing = MotionEasing.FastOutSlowIn))
    }
    Box(modifier = modifier.fillMaxWidth()) {
        if (allPickedHere) {
            ConfettiBurst(trigger = "point-finished", modifier = Modifier.matchParentSize())
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 10.dp.toPx()
                }
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FinishedMark(allPicked = allPickedHere)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    if (allPickedHere) R.string.onsite_all_done_title else R.string.onsite_empty_title
                ),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = OnsiteInk,
                textAlign = TextAlign.Center,
            )
            if (lastPickupAt != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onsite_empty_anchor, relativeTimeText(lastPickupAt, now)),
                    fontSize = 14.sp,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                )
            }

            if (allPickedHere && !isDemo && nextStop != null) {
                Spacer(Modifier.height(26.dp))
                NextStopCard(
                    nextStop = nextStop,
                    now = now,
                    onClick = { onOpenNextPoint(nextStop) },
                )
            } else if (allPickedHere) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.onsite_next_none),
                    fontSize = 14.sp,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                )
            }

            /* 成功带固定在按钮上方占一格：3 秒后它消失时，下面的按钮不该往上跳。 */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                undo?.invoke()
            }

            if (allPickedHere && !isDemo && nextStop != null) {
                OnsiteWideButton(
                    text = stringResource(R.string.onsite_next_go),
                    onClick = { onOpenNextPoint(nextStop) },
                )
                Spacer(Modifier.height(4.dp))
                OnsiteTextButton(text = stringResource(R.string.onsite_back_home), onClick = onBack)
            } else {
                OnsiteWideButton(text = stringResource(R.string.onsite_back_home), onClick = onBack)
            }
        }
    }
}

/**
 * 收工的样子：卡皮 + 一枚勾。不是通用的绿勾空态——这一下是用户刚做完的事，
 * 角色出面比一个图标更像「有人替他说了句好」。
 */
@Composable
private fun FinishedMark(allPicked: Boolean) {
    val scale = remember { Animatable(0.86f) }
    val badge = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, MotionProfile.iconSpring())
    }
    LaunchedEffect(allPicked) {
        if (allPicked) {
            /* 勾晚半拍落下来：先看见卡皮，再看见「成了」。 */
            delay(120)
            badge.animateTo(1f, MotionProfile.iconSpring())
        } else {
            badge.snapTo(0f)
        }
    }
    Box(modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
        Image(
            painter = painterResource(R.drawable.ic_splash),
            contentDescription = null,
            modifier = Modifier.size(88.dp),
        )
        if (allPicked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .graphicsLayer { scaleX = badge.value; scaleY = badge.value }
                    .background(UrgencySafe, CircleShape)
                    .border(2.dp, OnsiteSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = OnsiteSurface,
                )
            }
        }
    }
}

/**
 * 收工卡里的下一站。只用黑白灰——三态色是时效的语义，不借给导航。
 * 整张卡可点，和下面那颗「去下一个点」是同一个去处；箭头是提醒，不是第二个动作。
 */
@Composable
private fun NextStopCard(nextStop: NextStop, now: Long, onClick: () -> Unit) {
    val deadline = nextStop.deadlineAt
    val meta = when {
        deadline == null -> stringResource(R.string.onsite_next_count, nextStop.count)
        deadline <= now -> stringResource(R.string.onsite_next_count_closed, nextStop.count)
        else -> {
            val c = countdownOf(deadline - now)
            val left = if (c.usesHours) {
                stringResource(R.string.onsite_next_left_hours, c.hours, c.minutes)
            } else {
                stringResource(R.string.onsite_next_left_minutes, c.minutes)
            }
            stringResource(R.string.onsite_next_count_left, nextStop.count, left)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceInsetStrong, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.onsite_next_stop, nextStop.name),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnsiteInk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = meta,
                fontSize = 13.sp,
                color = InkMuted,
                style = TabularNumbers,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = InkMuted,
        )
    }
}

@Composable
private fun OnsiteWideButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(OnsiteInk, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnsiteSurface)
    }
}

@Composable
private fun OnsiteTextButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = OnsiteInk.copy(alpha = 0.6f),
    )
}

/**
 * loading-submit 红线：点「已取」后立即有视觉反馈，不等任何异步。
 * 现场用户马上要放下手机去拿包裹，任何 spinner 都是失败。
 * 位置在「已取」按钮之上而不是盖住它——3 秒里挡住按钮，用户就没法连着取下一件。
 *
 * 多邻国那种「答对就有一条浅绿带从下面滑上来」的形状：绿底 + 勾 + 一句短话，
 * 撤销仍然是带下划线的文字。它只是把「你做成了」说出口，不抢下一件的操作。
 */
@Composable
private fun UndoBar(isDemo: Boolean, onUndo: () -> Unit) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = appear,
        enter = slideInVertically(
            animationSpec = tween(MotionDuration.Medium, easing = MotionEasing.FastOutSlowIn),
        ) { height -> height / 2 } + fadeIn(tween(MotionDuration.Fast)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .background(UrgencySafeTint, RoundedCornerShape(14.dp))
                .border(1.dp, UrgencySafe.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(UrgencySafe, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = OnsiteSurface,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(
                    if (isDemo) R.string.onsite_demo_toast else R.string.onsite_marked_picked
                ),
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
            )
            if (!isDemo) {
                Text(
                    text = stringResource(R.string.onsite_undo),
                    modifier = Modifier
                        .clickable(onClick = onUndo)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
    }
}

@Composable
private fun PositionRow(index: Int, total: Int, showTilesEntry: Boolean, onOpenTiles: () -> Unit) {
    /* 只有一件时「第 1/1 件」是一句废话；多件或还有平铺入口时才占这一行。 */
    if (total <= 1 && !showTilesEntry) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (total > 1) {
            Text(
                text = stringResource(R.string.onsite_position, index + 1, total),
                fontSize = 14.sp,
                color = InkMuted,
                style = TabularNumbers,
            )
        }
        if (showTilesEntry) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onOpenTiles)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.onsite_tiles_entry),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentInk,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = AccentInk,
                )
            }
        }
    }
}

/** 「已取」：底部安全区之上、单手拇指可达、64dp 高（≥56dp 硬标准，手套 + 水屏容错）。
 *  多邻国那种实体键：下面垫一条 4dp 的边，按下去时整颗键沉进去；落指一次轻触觉。 */
@Composable
private fun PickedButton(onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = MotionProfile.iconSpring(),
        label = "picked-press",
    )
    val sink by animateFloatAsState(
        targetValue = if (pressed) 3f else 0f,
        animationSpec = MotionProfile.iconSpring(),
        label = "picked-sink",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
    ) {
        /* 底边：按下时按钮沉进来，松手时弹回；这是「按到了实体键」的全部来源。 */
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 4.dp)
                .background(InkMuted, RoundedCornerShape(16.dp)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .graphicsLayer {
                    translationY = sink.dp.toPx()
                    scaleX = scale
                    scaleY = scale
                }
                .background(OnsiteInk, RoundedCornerShape(16.dp))
                .clickable(interactionSource = interaction, indication = null) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(22.dp), tint = OnsiteSurface)
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.onsite_picked), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnsiteSurface)
        }
    }
}

@Composable
private fun StaleCodeNotice(days: Int, showRefresh: Boolean, onDismiss: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrgencyDueSoonTint, ShowCodeShapes.Md)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = UrgencyDueSoon)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.onsite_stale_code_title, days),
                style = ShowCodeText.Meta,
                color = Ink,
            )
            Text(
                text = stringResource(R.string.onsite_stale_code_hint),
                style = ShowCodeText.Caption,
                color = InkMuted,
            )
        }
        if (showRefresh) {
            Text(
                text = stringResource(R.string.refresh_action),
                modifier = Modifier
                    .clickable(onClick = onRefresh)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                style = ShowCodeText.Action,
                color = Ink,
            )
        }
        Text(
            text = "✕",
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
            fontSize = 13.sp,
            color = InkFaint,
        )
    }
}

/**
 * 演示态水印。与首页示例卡片用同一套视觉语言，
 * 免得用户把演示数据当成真数据——这是很容易犯的错。
 * 不带任何 pointer 修饰符，所以左右滑的手势会穿透到下面的轮播。
 */
@Composable
internal fun DemoWatermark() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.sample_badge),
            modifier = Modifier.rotate(-24f),
            fontSize = 60.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.Black.copy(alpha = 0.06f),
            letterSpacing = 0.2.em,
        )
    }
}
