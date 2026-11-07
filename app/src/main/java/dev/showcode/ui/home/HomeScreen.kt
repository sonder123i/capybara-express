package dev.showcode.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as rotateScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.showcode.R
import dev.showcode.ui.AnimatedValueText
import dev.showcode.core.CodeKind
import dev.showcode.core.MINUTE_MS
import dev.showcode.core.Parcel
import dev.showcode.core.PickupPointGroup
import dev.showcode.core.SuspectPair
import dev.showcode.core.SearchWindow
import dev.showcode.core.Urgency
import dev.showcode.core.account.ChannelAction
import dev.showcode.core.account.ChannelState
import dev.showcode.core.account.SyncChannel
import dev.showcode.core.account.SyncRailState
import dev.showcode.core.searchParcels
import dev.showcode.core.urgencyOf
import dev.showcode.ui.closingLabel
import dev.showcode.ui.GoodsThumbnail
import dev.showcode.ui.URGENCY_DUE_SOON_ICON
import dev.showcode.ui.parcels.goodsThumbnailUrl
import dev.showcode.ui.parcels.showsGoodsCard
import dev.showcode.ui.navBarClearance
import dev.showcode.ui.pointIconOf
import dev.showcode.ui.pointKindLabel
import dev.showcode.ui.stageLabel
import dev.showcode.ui.relativeTimeText
import dev.showcode.ui.theme.AccentInk
import dev.showcode.ui.theme.AccentTint
import dev.showcode.ui.theme.BorderDueSoon
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.InkWatermark
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.ModuleMotion
import dev.showcode.ui.theme.MotionEasing
import dev.showcode.ui.theme.MotionTokens
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.moduleEntrance
import dev.showcode.ui.theme.rememberReducedMotion
import dev.showcode.ui.theme.environmentBackground
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.withTabular
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.SurfaceSunken
import dev.showcode.ui.theme.TabularNumbers
import dev.showcode.ui.theme.UrgencyAtRiskInk
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencyAtRiskInk
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencyDueSoonInk
import dev.showcode.ui.theme.UrgencyDueSoonTint
import dev.showcode.ui.theme.UrgencySafe
import dev.showcode.ui.theme.UrgencySafeTint
import dev.showcode.ui.points.MergeUndoBar
import dev.showcode.ui.points.SuspectHintCard
import dev.showcode.ui.motion.BreathingRing
import dev.showcode.ui.CardState
import dev.showcode.ui.cardState
import dev.showcode.ui.motion.ScanSweep
import dev.showcode.ui.motion.pressLift
import dev.showcode.ui.urgencyColor
import dev.showcode.ui.urgencyLabel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * 首页的三种空态。edge meta-3 的红线：本产品有 6 种语义完全不同的空态，绝不可共用文案。
 * 这里出现的两种语义正好相反——FIRST_TIME 是「还没开始」，ALL_DONE 是「已经干完了」，
 * 后者是成就不是缺失，所以它是绿色的成功态。
 */
enum class HomeEmpty { NONE, FIRST_TIME, ALL_DONE }

data class HomeUiState(
    val loaded: Boolean,
    val groups: List<PickupPointGroup>,
    val empty: HomeEmpty,
    val monthlyPicked: Int,
    val lastSyncAt: Long?,
    /** 正在读短信收件箱：轨道上「短信」那格会转起来，用户才知道这不是卡住了。 */
    val syncing: Boolean = false,
    /** 读短信库失败。这时 lastSyncAt 故意不更新，所以红条必须立刻出现而不是等两小时。 */
    val lastSyncFailed: Boolean = false,
    /** 首页只放入口行与计数；完整列表住在未识别收容页（story-3 AC3）。 */
    val unrecognizedCount: Int,
    /** 收到第一个取件点后才出现的反向绑定横幅（story-3 AC5）。 */
    val showWidgetHint: Boolean,
    val smsPermissionGranted: Boolean,
    val notificationAccessGranted: Boolean,
    /** 疑似重复的一对（story-6 AC4）：只问一句，绝不自动合并。 */
    val suspect: SuspectPair? = null,
    /**
     * 已取走的件。检索用它回答空结果的两种：
     * 「没有这串」和「这串你今天已经取过了」是两句完全不同的话，而前者会让人白跑一趟。
     */
    val pickedParcels: List<Parcel> = emptyList(),
    /**
     * 在途/待补信息的件。不参与待取计数、不显示取件码，只回答「东西走到哪了」。
     * 历史已签收件永远不进这里。
     */
    val inTransit: List<Parcel> = emptyList(),
    /** 最近一次账号同步的结果摘要；轨道那一行会短暂改说这句话。 */
    val digest: SyncDigest? = null,
    /**
     * 四条同步通道的状态（短信 + 三家平台）。null = 演示态，顶栏不出现轨道。
     */
    val rail: SyncRailState? = null,
)

/**
 * 一次同步的「变了什么」。只报数，不替用户解释成「到站」——
 * 新增里可能包含在途件，把话说满就是另一种撒谎。
 */
data class SyncDigest(
    val added: Int,
    val updated: Int,
    val skipped: Int,
    /** 有账号这次没成功。摘要里要留一句，不然失败会被「新增 0」盖过去。 */
    val failed: Boolean,
    val at: Long,
)

/** 数据超过这个时长没同步过就算陈旧。陈旧的码比没有码更危险。 */
private const val STALE_THRESHOLD_MS = 120 * MINUTE_MS

/** 卡片内最多列出的件数，多出来的收成「+N」。卡片高度因此不会随件数撑破布局。 */
private const val CARD_ITEM_LIMIT = 3

/** 卡片内一行的固定高度：有柜号与没柜号的行必须一样高，否则卡片高度会随数据抖动。 */
private val CARD_ROW_HEIGHT = 30.dp

/** 有商品缩略图的行：高度固定，图片 44dp，整行不随加载状态跳。 */
private val GOODS_ROW_HEIGHT = 52.dp

/**
 * 首页 = 管理模式。第一级是取件点，不是包裹（Story 2 AC2）。
 * 排序由 [dev.showcode.core.groupByPoint] 决定：紧急度优先，件数只是次级排序。
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    now: Long,
    /** 与底栏同一个滚动意图：往下滚收起顶栏，往上滚展开。搜索开着时强制在场。 */
    headerMinimized: Boolean,
    sampleGroups: List<PickupPointGroup>,
    /** 宣传片录制态：不出现任何权限/同步/收容提示，数据全部来自演示内存集。 */
    isDemo: Boolean = false,
    onExitDemo: () -> Unit = {},
    onOpenPoint: (String) -> Unit,
    /** 在途项进详情。它没有取件点，只能按 parcel 打开展示。 */
    onOpenParcel: (Parcel) -> Unit = {},
    onOpenDemo: () -> Unit,
    /** 首页唯一的常驻说明：后台可疑（件可能真的没进来）。中性灰、可关闭。 */
    showBackgroundNote: Boolean,
    onDismissBackgroundNote: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUnrecognized: () -> Unit,
    onManualImport: () -> Unit,
    onDismissWidgetHint: () -> Unit,
    onPinWidget: () -> Unit,
    /** 主路径：绑定平台账号。 */
    onOpenAccounts: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRefresh: () -> Unit,
    /** 平台同步（自动或手动）进行中：刷新键禁用并在原地转。 */
    accountsSyncing: Boolean = false,
    /** 手动同步的一句话结局；null = 这一轮没有话要说。 */
    manualSyncMessage: String? = null,
    /** 每次「真的抓取且失败」自增；刷新键据此晃两下。 */
    manualSyncFailedSignal: Int = 0,
    onManualSync: () -> Unit = {},
    /** 长按刷新键：强制重抓，忽略「状态没变就跳过」。 */
    onForceSync: () -> Unit = {},
    /** 轨道上某一格的重试：只跑这一家。 */
    onRetryChannel: (String) -> Unit = {},
    onManagePoint: ((PickupPointGroup) -> Unit)?,
    onSuspectYes: () -> Unit,
    onSuspectNo: () -> Unit,
    mergeUndoName: String?,
    onUndoMerge: () -> Unit,
) {
    /* 检索的三个状态留在这一屏，没有提到 Root：它是「此刻正在找东西」这种临时意图，
       切去现场模式再回来还挂着搜索框，会让人以为屏上少掉的件被 App 藏起来了。 */
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var window by remember { mutableStateOf(SearchWindow.ALL) }
    /* 同步轨道的明细默认收起：日常它只占一行，点开才看每家平台。 */
    var railExpanded by rememberSaveable { mutableStateOf(false) }

    // 只选了窗（没打字）也算在筛；光打开搜索框但什么都不选，列表保持原样。
    val filtering = searchOpen && (query.isNotBlank() || window != SearchWindow.ALL)
    val result = if (searchOpen) searchParcels(state.groups, state.pickedParcels, query, window, now) else null
    val shownGroups = result?.groups ?: state.groups
    val reducedMotion = rememberReducedMotion()


    /* 陈旧提示对任何一条收录通道都成立。以前它只被短信权限门住，
       于是「拒了短信、只开通知监听」这个完全合法的配置下，用户永远看不到「可能不是最新」。 */
    val isStale = state.lastSyncFailed ||
        ((state.smsPermissionGranted || state.notificationAccessGranted) &&
            (state.lastSyncAt == null || now - state.lastSyncAt > STALE_THRESHOLD_MS))

    // 底色与系统栏内边距住在这儿。浮动胶囊搬走之后它只剩一个子层，
    // 但那三条修饰符挪进 Column 要把整屏重排一遍缩进——为一个空壳改动两百行不值当。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .environmentBackground()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            /* 顶栏随滚动收起（教务「往下翻的时候一屏能多看一节」）：与底栏共用同一个滚动意图——
               往下滚，顶栏收起、底栏收成胶囊；往上滚，两边一起回来。
               搜索开着时顶栏强制在场：收起形态下没有开关能把它关掉。 */
            AnimatedVisibility(
                visible = !headerMinimized || searchOpen,
                enter = ModuleMotion.expand(reducedMotion),
                exit = ModuleMotion.collapse(reducedMotion),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_title),
                                style = ShowCodeText.Headline,
                                color = Ink,
                            )
                            if (isDemo) {
                                /* 录制态用一句固定的标识替代同步时间：那里放真实时间会每天不同，
                                   而且演示数据本来就不该被读成「刚同步过的真实件」。 */
                                Text(
                                    text = manualSyncMessage ?: stringResource(R.string.demo_home_marker),
                                    style = ShowCodeText.Meta,
                                    color = AccentInk,
                                )
                            } else {
                                /* 「更新于 X」升级成四格轨道：谁答了、谁没答、谁在跑，一眼看完。
                                   点一下才展开明细——日常它仍然只占一行。
                                   读库还没回来时不出声：那时说「还没同步过」是把一次正常的加载演成缺了什么。 */
                                val rail = state.rail
                                if (state.loaded && rail != null) {
                                    SyncRailRow(
                                        rail = rail,
                                        now = now,
                                        manualMessage = manualSyncMessage,
                                        digest = state.digest,
                                        expanded = railExpanded,
                                        onToggle = { railExpanded = !railExpanded },
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isDemo) {
                                Text(
                                    text = stringResource(R.string.demo_exit),
                                    modifier = Modifier
                                        .clickable(onClick = onExitDemo)
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    style = ShowCodeText.Action,
                                    color = AccentInk,
                                )
                            }
                            /* 手动刷新：搜索左边、同一行。没有件、演示态也留着——
                               「没有新件」恰恰是最需要手动问一次的时候，演示态则只回一句提示。
                               长按 = 强制重抓（忽略「状态没变就跳过」）。 */
                            RefreshAction(
                                busy = accountsSyncing,
                                enabled = !accountsSyncing,
                                failedSignal = manualSyncFailedSignal,
                                onClick = onManualSync,
                                onLongClick = onForceSync,
                            )
                            /* 一件都没有时不给这颗按钮：那时候搜索框不是路，是一条死路。
                               演示态也不给：打开搜索会弹软键盘，录制时不允许任何系统面板。 */
                            if (state.groups.isNotEmpty() && !isDemo) {
                                IconButton(
                                    onClick = { searchOpen = !searchOpen },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        imageVector = if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                                        contentDescription = stringResource(
                                            if (searchOpen) R.string.home_search_close else R.string.home_search,
                                        ),
                                        tint = if (searchOpen) Ink else InkSecondary,
                                    )
                                }
                            }
                            // 顶栏不再有那颗设置图标：设置现在是底栏三颗键之一，
                            // 同一屏上留两个入口会让人怀疑它们去的是不是同一个地方。
                        }
                    }
                    val rail = state.rail
                    if (!isDemo && rail != null) {
                        AnimatedVisibility(
                            visible = railExpanded,
                            enter = ModuleMotion.expand(reducedMotion),
                            exit = ModuleMotion.collapse(reducedMotion),
                        ) {
                            /* 面板和下面的取件点卡片同一条左边线（16dp）；
                               标题与轨道行保持 20dp 的页边距。 */
                            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                                SyncRailPanel(
                                    rail = rail,
                                    busy = accountsSyncing || state.syncing,
                                    onRefreshAll = onManualSync,
                                    onRetry = onRetryChannel,
                                    onOpenAccounts = onOpenAccounts,
                                )
                            }
                        }
                    }
                }
            }

            // 首页只保留「后台可能被限制」这一条中性说明（可关闭）。
            // 权限缺失（短信 / 通知监听）一律只进设置·权限体检，不再上首页。
            if (!isDemo && showBackgroundNote) {
                BackgroundNote(
                    lastSyncAt = state.lastSyncAt,
                    now = now,
                    onView = onOpenSettings,
                    onDismiss = onDismissBackgroundNote,
                )
            }

            // error-stale-data：不会被滚动隐藏——它挂在 LazyColumn 外面
            if (isStale && !isDemo) {
                StaleBar(
                    failed = state.lastSyncFailed,
                    lastSyncAt = state.lastSyncAt,
                    now = now,
                    onRefresh = onRefresh,
                )
            }

            /* 合并后的 5 秒撤销（AC2 的「立刻有一个明显的撤销入口」）。
               和权限条一样住在 LazyColumn 外面：一个能被滚走的反悔入口等于没有反悔。 */
            mergeUndoName?.let { MergeUndoBar(keptName = it, onUndo = onUndoMerge) }

            /* 挂在 LazyColumn 外面，和权限条同一个理由：会随列表滚走的控件等于没有。
               搜索框尤其如此——手指还在打字，框自己往上跑出可视区，用户只会以为 App 卡了。 */
            if (searchOpen && !isDemo) {
                SearchRow(
                    query = query,
                    window = window,
                    onQuery = { query = it },
                    onWindow = { window = it },
                )
            }

            /* 下拉刷新是第二条入口（第一条是右上那颗键）。演示态关掉：
               录制时不允许出现任何额外的系统表面。 */
            RefreshableHomeList(
                enabled = !isDemo,
                /* 下拉那一圈只在手指拉的时候出现；松手就退场，进度交给轨道说。
                   若让它跟 isRefreshing 一起常驻，它会在首张卡上盖一个转圈——
                   那正是这次重设计要消灭的东西。 */
                isRefreshing = false,
                onRefresh = onRefresh,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                /* 刷新时一道光从上往下扫过整列：比转圈多说的是「正在一页一页看过去」。
                   扫完自己停，不留任何常驻层。 */
                ScanSweep(
                    trigger = if (accountsSyncing && !isDemo) accountsSyncing else null,
                    modifier = Modifier.matchParentSize(),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBarClearance()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!state.loaded) return@LazyColumn

                    if (state.empty == HomeEmpty.FIRST_TIME) {
                        item {
                            // CTA 进排序最靠前那个点：一进演示就该看到最大的那个码，
                            // story-1 的风险写得很清楚——演示必须在 5 秒内让人看到它。
                            FirstTimeHeader(
                                onOpenAccounts = onOpenAccounts,
                                onOpenDemo = onOpenDemo,
                            )
                        }
                        items(sampleGroups, key = { "sample-${it.key}" }) { group ->
                            Box(modifier = Modifier.moduleEntrance(0)) {
                                SamplePointCard(group = group, now = now, onClick = onOpenDemo)
                            }
                        }
                        item {
                            Text(
                                text = stringResource(R.string.home_empty_first_time_hint),
                                style = ShowCodeText.Caption,
                                color = InkMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        items(shownGroups, key = { it.key }) { group ->
                            Box(modifier = Modifier.moduleEntrance(0)) {
                                val hint = state.suspect
                                /* 提示插在两张卡里更紧急那张的上方：它讲的是「这两张是同一件事」，
                                   放在其中任何一张上面都比放在列表尾巴上读得懂。 */
                                if (hint != null && hint.first.key == group.key) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SuspectHintCard(pair = hint, onYes = onSuspectYes, onNo = onSuspectNo)
                                        PointCard(
                                            group = group,
                                            now = now,
                                            onClick = { onOpenPoint(group.key) },
                                            onManage = onManagePoint?.let { manage -> { manage(group) } },
                                        )
                                    }
                                } else {
                                    PointCard(
                                        group = group,
                                        now = now,
                                        onClick = { onOpenPoint(group.key) },
                                        onManage = onManagePoint?.let { manage -> { manage(group) } },
                                    )
                                }
                            }
                        }
                        /* 待取清单下方：在途件单独一组，可折叠。它不参与待取计数。 */
                        if (!filtering && state.inTransit.isNotEmpty()) {
                            item {
                                Box(modifier = Modifier.moduleEntrance(1)) {
                                    InTransitSection(
                                        parcels = state.inTransit,
                                        onOpen = onOpenParcel,
                                    )
                                }
                            }
                        }
                        // 空态与招揽横幅这些是「没人找东西时」的路；正在筛的时候它们是噪声。
                        if (!filtering && state.empty == HomeEmpty.ALL_DONE) {
                            item { AllDoneCard(monthlyPicked = state.monthlyPicked) }
                        }

                        if (filtering && shownGroups.isEmpty()) {
                            item {
                                val picked = result?.pickedMatches ?: 0
                                Text(
                                    text = when {
                                        // 「那串你可能已经取过了」只在人真的打了字时才说：
                                        // 判据那边空白查询就不报已取，这里反过来造一句也是空话。
                                        picked > 0 -> stringResource(R.string.home_search_miss_picked, query.trim(), picked)
                                        query.isBlank() -> stringResource(R.string.home_search_miss_window)
                                        else -> stringResource(R.string.home_search_miss, query.trim())
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SurfaceCard, ShowCodeShapes.Md)
                                        .padding(16.dp),
                                    style = ShowCodeText.Meta,
                                    color = InkSecondary,
                                )
                            }
                        }
                    }

                    // 解析失败必须显性化（story-2 的 risk）：首页给入口行，完整列表在收容页。
                    // 这一行**不跟着筛选让位**——「你没搜到」不等于「没有东西没认出来」。
                    if (state.unrecognizedCount > 0) {
                        item {
                            Box(modifier = Modifier.moduleEntrance(1)) {
                                UnrecognizedLinkRow(count = state.unrecognizedCount, onOpen = onOpenUnrecognized)
                            }
                        }
                    }

                    if (!filtering) {
                        // story-3 AC5：收到第一个取件点后才提示反向绑定。落点在 App 内横幅而不是组件——
                        // 组件是全产品空间最紧张的表面，它必须永远保住「点名」那一行
                        // （stories.json ac_amendment 的回归教训）。
                        if (state.showWidgetHint) {
                            item {
                                Box(modifier = Modifier.moduleEntrance(2)) {
                                    WidgetHintBanner(onPin = onPinWidget, onDismiss = onDismissWidgetHint)
                                }
                            }
                        }

                        /* 短信导入不再占首页横幅位：它只是补充手段，入口在设置里。
                           首页只保留「绑定平台账号」这条主路径与通知监听这类真正影响自动收件的提示。 */
                        if (state.notificationAccessGranted.not()) {
                            item {
                                Box(modifier = Modifier.moduleEntrance(2)) {
                                    EnableIngestCard(
                                        title = stringResource(R.string.home_enable_notification),
                                        body = stringResource(R.string.home_enable_notification_hint),
                                        onClick = onOpenNotificationSettings,
                                    )
                                }
                            }
                        }
                    }

                    // 手动导入是「拒绝短信权限也能用」的一等退路（edge s1 permission-sms-denied），
                    // 引导结束之后也必须常驻在列表末尾，**检索时也不藏**：
                    // 手上有条码而 App 认不出来的那一刻，正是它最值钱的时候。
                    // 演示态不提供：它会弹出一屏输入框与软键盘，录制时不允许系统面板。
                    if (!isDemo) {
                        item {
                            Text(
                                text = stringResource(R.string.home_manual_entry),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onManualImport)
                                    .padding(vertical = 10.dp),
                                style = ShowCodeText.Body,
                                color = InkMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

/**
 * 一次同步的结果摘要露多久。
 *
 * 它不是常驻数据：8 秒后轨道回到「刚刚 · N/M 通道」。不拿页面那个 30 秒一跳的 `now` 比——
 * 那个刻度是给倒计时用的，用它这条会时准时不准；而摘要本身是「刷过了」的唯一证据，不能飘。
 */
private const val SYNC_RESULT_MS = 8_000L

@Composable
private fun InTransitSection(parcels: List<Parcel>, onOpen: (Parcel) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .semantics { contentDescription = "在途" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "在途", style = ShowCodeText.Item, color = Ink)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${parcels.size} 件",
                style = ShowCodeText.Caption,
                color = InkMuted,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                parcels.forEach { parcel ->
                    InTransitRow(parcel = parcel, onClick = { onOpen(parcel) })
                }
            }
        }
    }
}

@Composable
private fun InTransitRow(parcel: Parcel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        goodsThumbnailUrl(parcel)?.let {
            GoodsThumbnail(url = it, size = 46.dp)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = parcel.goodsTitle ?: "包裹 ${parcel.trackingNo.orEmpty().takeLast(4).ifBlank { "信息待补" }}",
                style = ShowCodeText.Body,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = carrierAndTracking(parcel),
                style = ShowCodeText.Caption,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = inTransitStatus(parcel),
                style = ShowCodeText.Meta,
                color = InkSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun carrierAndTracking(parcel: Parcel): String {
    val carrier = parcel.carrier.ifBlank { "快递" }
    val tracking = parcel.trackingNo.orEmpty()
    return if (tracking.isBlank()) "$carrier · 运单号待出"
    else "$carrier · $tracking"
}

/** 在途件的一句最新状态；到站但没点的件说清楚「信息待补」，不冒充一张待取卡。 */
@Composable
internal fun inTransitStatus(parcel: Parcel): String {
    if (parcel.stage == dev.showcode.core.Stage.AT_STATION && parcel.pointKey.isBlank()) {
        return "待取 · 取件信息待补，下次同步会补全"
    }
    /* 物流页的 innerText 第一行往往是承运商和运单号，直接取会得出「运输中 · 申通快递」这种废话。
       优先找带「预计…送达/到达」的那句，它才是用户真正想知道的状态。 */
    val eta = IN_TRANSIT_ETA.find(parcel.raw)?.value
    /* 淘宝给的是「物流运输中」，但用户看到的是「已发货」这一层；
       原文里出现显式的「已发货」时就用它，不再退化到泛化的「运输中」。 */
    val label = if (parcel.stage == dev.showcode.core.Stage.IN_TRANSIT &&
        parcel.raw.contains("已发货")
    ) {
        "已发货"
    } else {
        stageLabel(parcel.stage)
    }
    if (eta != null) return "$label · $eta"
    val lines = parcel.raw.lineSequence().map { it.trim() }.filter { it.isNotBlank() }
    val latest = lines
        .firstOrNull {
            !it.startsWith("订单号") && !it.startsWith("收货地址") &&
                !it.startsWith("复制") && !it.startsWith(":") &&
                !CARRIER_ONLY.containsMatchIn(it)
        }
        .orEmpty()
        .take(42)
    return if (latest.isBlank() || latest == label) label else "$label · $latest"
}

private val IN_TRANSIT_ETA = Regex("预计[^，。；]{1,12}(送达|到达)")
private val CARRIER_ONLY = Regex("[\\u4e00-\\u9fa5]{2,6}(快递|速运|物流|运输)")

/* ── 同步轨道 ───────────────────────────────────────────────────────────
 *
 * 收起的形态只占原来那行小字的位置：四个点 + 一句话。点开才摊开四条通道，
 * 每条给出「上次结果 / 失败原因 / 正在读第几件」和一个只针对它的动作。
 *
 * 为什么不是一个大按钮：一次刷新有四个互不相干的来源，一个全局转圈无法回答
 * 「是哪一家没答」。这条轨道就是那个问题的常驻答案。
 */

@Composable
private fun SyncRailRow(
    rail: SyncRailState,
    now: Long,
    manualMessage: String?,
    digest: SyncDigest?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    /* 结果摘要只露 8 秒，和原来那行小字的节奏一致——它不是常驻数据。 */
    var showDigest by remember(digest?.at) { mutableStateOf(digest != null) }
    LaunchedEffect(digest?.at) {
        if (digest != null) {
            delay(SYNC_RESULT_MS)
            showDigest = false
        }
    }
    val runningLabel = rail.channels.firstOrNull { it.state == ChannelState.RUNNING }?.label
    val text = when {
        manualMessage != null -> manualMessage
        rail.running -> runningLabel
            ?.let { stringResource(R.string.sync_rail_running, it) }
            ?: stringResource(R.string.sync_rail_running_any)
        showDigest && digest != null -> digestText(digest)
        /* 登录失效要单独说：这时候「重试」按钮是死路，用户得知道该去重新登录。 */
        rail.needsLoginLabels.isNotEmpty() -> stringResource(
            R.string.sync_rail_needs_login,
            rail.needsLoginLabels.joinToString("、"),
        )
        rail.failedLabels.isNotEmpty() -> stringResource(
            R.string.sync_rail_failed,
            rail.failedLabels.joinToString("、"),
        )
        rail.newestAt == null -> stringResource(R.string.sync_rail_never)
        else -> stringResource(
            R.string.sync_rail_channels,
            relativeTimeText(rail.newestAt, now),
            rail.answered,
            rail.total,
        )
    }
    val warning = manualMessage == null && !rail.running && rail.failedLabels.isNotEmpty()

    /* 收束脉冲：从「有通道在跑」变成「一个都没在跑」的那一下，四个点一起弹一下。
       它是「都回来了」的统一信号——不然同步结束只是某个弧悄悄消失。 */
    var wasRunning by remember { mutableStateOf(rail.running) }
    var pulse by remember { mutableIntStateOf(0) }
    LaunchedEffect(rail.running) {
        if (wasRunning && !rail.running) pulse++
        wasRunning = rail.running
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "同步状态：" +
                    rail.channels.joinToString("，") { it.label + channelStateWord(it.state) } +
                    "。点开查看每个通道"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rail.channels.forEach { channel ->
            ChannelDot(channel.state, pulse)
            Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.width(2.dp))
        Text(
            text = text,
            style = ShowCodeText.Caption,
            color = if (warning) UrgencyDueSoon else InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = InkMuted,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun digestText(digest: SyncDigest): String {
    if (digest.added == 0 && digest.updated == 0 && digest.skipped == 0) {
        return if (digest.failed) {
            stringResource(R.string.home_refresh_failed)
        } else {
            stringResource(R.string.sync_rail_digest_none)
        }
    }
    val base = stringResource(R.string.sync_rail_digest, digest.added, digest.updated)
    val withSkipped = if (digest.skipped > 0) {
        base + stringResource(R.string.sync_rail_digest_skipped, digest.skipped)
    } else {
        base
    }
    return if (digest.failed) withSkipped + stringResource(R.string.sync_rail_digest_partial) else withSkipped
}

@Composable
private fun SyncRailPanel(
    rail: SyncRailState,
    busy: Boolean,
    onRefreshAll: () -> Unit,
    onRetry: (String) -> Unit,
    onOpenAccounts: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(SurfaceCard, ShowCodeShapes.Md)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.sync_rail_title),
                style = ShowCodeText.Meta,
                color = InkSecondary,
            )
            Spacer(Modifier.weight(1f))
            if (busy) {
                /* 正在跑时不给第二个入口：按了也不会再开一条，只会让人以为坏了。 */
                Text(
                    text = stringResource(R.string.sync_rail_running_any),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    style = ShowCodeText.Action,
                    color = InkMuted,
                )
            } else {
                Text(
                    text = stringResource(R.string.sync_rail_refresh_all),
                    modifier = Modifier
                        .clickable(onClick = onRefreshAll)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    style = ShowCodeText.Action,
                    color = AccentInk,
                )
            }
        }
        rail.channels.forEach { channel ->
            SyncChannelRow(
                channel = channel,
                busy = busy,
                onRetry = onRetry,
                onOpenAccounts = onOpenAccounts,
            )
        }
    }
}

@Composable
private fun SyncChannelRow(
    channel: SyncChannel,
    busy: Boolean,
    onRetry: (String) -> Unit,
    onOpenAccounts: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelDot(channel.state)
        Spacer(Modifier.width(10.dp))
        Text(
            text = channel.label,
            style = ShowCodeText.Body,
            color = Ink,
            modifier = Modifier.width(52.dp),
            maxLines = 1,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.detail,
                style = ShowCodeText.Meta,
                color = InkMuted,
                /* 展开才有的明细允许两行：被截掉的「跳过 3（已签收）」正是用户要的答案。 */
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            /* 正在跑的通道：文字下面一条 2dp 的细进度。
               不知道分母时画一条来回走的「不确定」段，而不是空的轨道。 */
            if (channel.state == ChannelState.RUNNING) {
                Spacer(Modifier.height(4.dp))
                ChannelProgress(fraction = channel.fraction)
            }
        }
        val action = channel.action
        if (action != ChannelAction.NONE) {
            val label = stringResource(
                when (action) {
                    ChannelAction.RETRY -> R.string.sync_rail_retry
                    ChannelAction.BIND -> R.string.sync_rail_bind
                    ChannelAction.RELOGIN -> R.string.sync_rail_relogin
                    ChannelAction.NONE -> R.string.sync_rail_bind
                },
            )
            val enabled = !busy
            Text(
                text = label,
                modifier = (if (enabled) {
                    Modifier.clickable {
                        when (action) {
                            ChannelAction.RETRY -> channel.accountId?.let(onRetry)
                            /* 去绑定和重新登录都落在账号列表：那一屏才有登录入口。 */
                            ChannelAction.BIND, ChannelAction.RELOGIN -> onOpenAccounts()
                            ChannelAction.NONE -> Unit
                        }
                    }
                } else {
                    Modifier
                }).padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                style = ShowCodeText.Action,
                color = if (enabled) AccentInk else InkMuted,
            )
        }
    }
}

/**
 * 一个通道点。形状先于颜色：实心=有应答、空环=没启用/没成功过、缺口环=正在转。
 * 失败是唯一带颜色的（琥珀），因为它必须能从一行灰字里跳出来。
 */
@Composable
private fun ChannelDot(state: ChannelState, pulse: Int = 0) {
    /* 一次 0.22 秒的脉冲：1 → 1.45 → 1，弹簧收尾。pulse 每变一次播一遍。 */
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (pulse == 0) return@LaunchedEffect
        scale.snapTo(1f)
        scale.animateTo(1.45f, tween(90, easing = MotionEasing.FastOutSlowIn))
        scale.animateTo(1f, MotionTokens.Settle)
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ChannelState.FRESH -> Box(Modifier.size(6.dp).background(Ink, CircleShape))
            ChannelState.STALE -> Box(Modifier.size(6.dp).background(InkMuted, CircleShape))
            ChannelState.FAILED -> Box(Modifier.size(6.dp).background(UrgencyDueSoon, CircleShape))
            ChannelState.OFF, ChannelState.NEVER ->
                Box(Modifier.size(6.dp).border(1.dp, InkMuted, CircleShape))
            ChannelState.RUNNING -> RunningDot()
        }
    }
}

/** 正在跑的通道：一圈缓慢呼吸的环，只有这个状态在动。 */
@Composable
private fun RunningDot() {
    /* 从「转动的弧」换成「呼吸的环」：弧在说「在转」，环在说「在等」。
       同步常常要十几秒，一个匀速转动的圈会让人以为它卡住了；呼吸的节奏更接近「有人在干活」。 */
    BreathingRing(color = AccentInk, modifier = Modifier.size(8.dp), periodMs = 1_400)
}

/**
 * 通道行内的 2dp 细进度。
 *
 * 确定态（有 done/total）：填充宽度用 `NavSettle` 弹簧推过去——过冲让它像「推进」而不是「加载」。
 * 不确定态（还在读订单）：一段 30% 宽的亮块在轨道上来回走。
 */
@Composable
private fun ChannelProgress(fraction: Float?) {
    val track = Hairline
    val fill = AccentInk
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(track, ShowCodeShapes.Pill),
    ) {
        if (fraction == null) {
            val transition = rememberInfiniteTransition(label = "channel-indeterminate")
            val x by transition.animateFloat(
                initialValue = -0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1_100, easing = MotionEasing.Standard),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "channel-indeterminate-x",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(2.dp)
                    .offset(x = (x * 100).dp)
                    .background(fill, ShowCodeShapes.Pill),
            )
        } else {
            val width by animateFloatAsState(
                targetValue = fraction,
                animationSpec = MotionTokens.NavSettle,
                label = "channel-progress",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(width.coerceIn(0.02f, 1f))
                    .height(2.dp)
                    .background(fill, ShowCodeShapes.Pill),
            )
        }
    }
}

private fun channelStateWord(state: ChannelState): String = when (state) {
    ChannelState.FRESH -> "正常"
    ChannelState.STALE -> "已过期"
    ChannelState.RUNNING -> "同步中"
    ChannelState.FAILED -> "失败"
    ChannelState.NEVER -> "还没成功过"
    ChannelState.OFF -> "未启用"
}

/**
 * 下拉刷新：刷新键之外的第二条入口。
 *
 * 它不改变刷新的语义（仍然走同一条全量路径），只是把「我现在就想问一次」
 * 放到手上最顺的位置。指示器用 Material 默认款——这条手势是顺手，
 * 主角仍然是顶栏那条轨道。
 */
@Composable
private fun RefreshableHomeList(
    enabled: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        content()
    }
}

/**
 * 搜索那一行：一个输入框 + 三个互斥的时间窗。
 *
 * 窗做成一排单选而不是可叠加的开关，因为首页只有一个语义——「现在要去看哪些件」。
 * 叠成「今天到 且 临期 且 含已取」那种组合器，是档案柜的用法，不是作战面板的。
 */
@Composable
private fun SearchRow(
    query: String,
    window: SearchWindow,
    onQuery: (String) -> Unit,
    onWindow: (SearchWindow) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.home_search_hint)) },
            trailingIcon = if (query.isEmpty()) null else {
                {
                    IconButton(onClick = { onQuery("") }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.home_search_clear),
                            tint = InkMuted,
                        )
                    }
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WindowChip(R.string.home_window_all, window == SearchWindow.ALL) { onWindow(SearchWindow.ALL) }
            WindowChip(R.string.home_window_urgent, window == SearchWindow.URGENT) { onWindow(SearchWindow.URGENT) }
            WindowChip(R.string.home_window_today, window == SearchWindow.TODAY) { onWindow(SearchWindow.TODAY) }
        }
    }
}

@Composable
private fun WindowChip(@StringRes labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = stringResource(labelRes),
        modifier = Modifier
            .background(if (selected) AccentTint else SurfaceCard, ShowCodeShapes.Sm)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Ink else Hairline,
                shape = ShowCodeShapes.Sm,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        style = ShowCodeText.Action,
        color = if (selected) Ink else InkMuted,
    )
}

/**
 * 首页唯一的常驻说明（中性灰、可关闭）。
 * 只有「后台可能被限制」会走到这里：它对应「件可能根本没进来」。
 * 短信、通知监听未开启不在这里出现——那些只是补充手段，只在设置·权限体检里中性表述。
 */
@Composable
private fun BackgroundNote(
    lastSyncAt: Long?,
    now: Long,
    onView: () -> Unit,
    onDismiss: () -> Unit,
) {
    val whenText = lastSyncAt?.let { relativeTimeText(it, now) }
        ?: stringResource(R.string.home_never_synced)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceSunken)
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.health_bar_background, whenText),
            modifier = Modifier.weight(1f),
            style = ShowCodeText.Meta,
            color = InkSecondary,
        )
        Text(
            text = stringResource(R.string.health_bar_view),
            modifier = Modifier
                .clickable(onClick = onView)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = ShowCodeText.Action,
            color = InkMuted,
        )
        Text(
            text = "✕",
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            style = ShowCodeText.Meta,
            color = InkMuted,
        )
    }
}

@Composable
private fun StaleBar(failed: Boolean, lastSyncAt: Long?, now: Long, onRefresh: () -> Unit) {
    val neverSynced = stringResource(R.string.home_never_synced)
    val whenText = lastSyncAt?.let { relativeTimeText(it, now) } ?: neverSynced
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrgencyDueSoonTint)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = UrgencyDueSoon)
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (failed) stringResource(R.string.home_stale_failed)
            else stringResource(R.string.home_stale, whenText),
            modifier = Modifier.weight(1f),
            style = ShowCodeText.Meta,
            color = Ink,
        )
        Text(
            text = stringResource(if (failed) R.string.home_stale_retry else R.string.refresh_action),
            modifier = Modifier
                .clickable(onClick = onRefresh)
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            style = ShowCodeText.Action,
            color = Ink,
        )
    }
}

/** 取件点卡片：首页的一级公民。整张卡片点一下就进该点的现场模式。 */
@Composable
private fun PointCard(
    group: PickupPointGroup,
    now: Long,
    onClick: () -> Unit,
    /* 示例卡不带管理入口：演示数据不落库，合并一个假取件点会造出一条真登记。 */
    onManage: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val kindText = pointKindLabel(group.kind)
    val closingText = group.closesAtMinuteOfDay?.let { closingLabel(it) }
    val urgencyText = if (group.worst == Urgency.SAFE) null else urgencyLabel(group.worst)
    val subtitle = listOfNotNull(kindText, closingText, urgencyText).joinToString(" · ")

    /* 按下时整张卡被「拎起来」2dp 并放大 1.2%：列表卡片要的是「拿起一件东西」，
       不是「按下一个按钮」。松手落回，弹簧参数与底栏同一套。 */
    val press = remember { MutableInteractionSource() }
    /* 四态用填充 + 描边表达：临期转暖、其余保持白面。
       色条仍然保留——它说的是「多急」，四态说的是「这条记录是什么状态」，两件事。 */
    val state = when (group.worst) {
        Urgency.AT_RISK, Urgency.DUE_SOON -> CardState.URGENT
        else -> CardState.IDLE
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressLift(press)
            .cardState(state)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(14.dp)
    ) {
        // 色条：首页唯一的颜色编码位置。颜色之外永远有文字冗余（brief 的人群适配策略）
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(40.dp)
                .background(urgencyColor(group.worst), ShowCodeShapes.Pill)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 类型锚点：柜机要念格口、驿站要等关门，现场要做的动作不同，
                // 而这三类取件点在短信里长得几乎一样（都叫「XX驿站」「XX店」）。先给一个图形。
                Icon(
                    painter = painterResource(id = pointIconOf(group.kind)),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = InkSecondary,
                )
                Spacer(Modifier.width(8.dp))
                // boundary-long-text：首页截断，现场模式完整显示——这是两个模式的真实分歧
                Text(
                    text = group.name,
                    modifier = Modifier.weight(1f),
                    style = ShowCodeText.Section,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (group.worst == Urgency.DUE_SOON) {
                    // 临期档以前一个图形都没有，而它恰恰是最需要被一眼认出的那一档。
                    // 安全用已有的勾、过期风险用已有的告警三角，不在这里重复造。
                    Icon(
                        painter = painterResource(id = URGENCY_DUE_SOON_ICON),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = UrgencyDueSoon,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.width(8.dp))
                AnimatedValueText(
                    value = group.items.size.toString(),
                    style = ShowCodeText.Count,
                    color = Ink,
                )
                Text(
                    text = stringResource(R.string.home_count_unit),
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                    style = ShowCodeText.Caption,
                    color = InkMuted,
                )
                /* 管理入口必须另给一个角：点卡片直进现场模式那个 1 步承诺
                   不能被「合并取件点」这种操作污染。 */
                onManage?.let { manage ->
                    IconButton(
                        onClick = manage,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.home_manage),
                            tint = InkMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = ShowCodeText.Caption,
                /* 副标题用同色系降一档，不是灰色：压在暖底/绿底上时灰字会显脏。
                   SAFE 态没有底色，仍用中性灰。 */
                color = when (group.worst) {
                    Urgency.SAFE -> InkMuted
                    Urgency.DUE_SOON -> UrgencyDueSoonInk
                    Urgency.AT_RISK -> UrgencyAtRiskInk
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))
            group.items.take(CARD_ITEM_LIMIT).forEach { parcel ->
                CardParcelRow(parcel = parcel, now = now)
            }
            // boundary-overflow：多出来的收成角标，不撑破卡片高度
            val rest = group.items.size - CARD_ITEM_LIMIT
            if (rest > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CARD_ROW_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_card_overflow, rest),
                        style = ShowCodeText.Caption.withTabular(),
                        color = InkMuted,
                    )
                }
            }
        }
    }
}

/**
 * 卡片内的一件。Story 2 AC4：柜号紧贴取件码；没有柜号时那一块整个消失、
 * 取件码变得更大——不留空位，也不显示「--」。
 */
@Composable
private fun CardParcelRow(parcel: Parcel, now: Long) {
    val hasLocker = parcel.lockerNo != null
    val urgency = urgencyOf(parcel, now)
    val hasGoods = showsGoodsCard(parcel)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (hasGoods) GOODS_ROW_HEIGHT else CARD_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasGoods) {
            goodsThumbnailUrl(parcel)?.let { GoodsThumbnail(url = it, size = 44.dp) }
            Spacer(Modifier.width(8.dp))
        }
        if (parcel.code.isBlank()) {
            /* 空码不伪造数字：说清楚「暂无取件码」，并让阶段顶上来。 */
            Text(
                text = stringResource(R.string.no_pickup_code),
                style = ShowCodeText.Caption,
                color = InkMuted,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stageLabel(parcel.stage),
                style = ShowCodeText.Micro,
                color = InkMuted,
            )
        } else {
            Text(
                text = parcel.code,
                style = if (hasLocker) ShowCodeText.Code else ShowCodeText.CodeLarge,
                color = Ink,
            )
            if (parcel.codeKind == CodeKind.SUFFIX) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.suffix_micro),
                    style = ShowCodeText.Micro,
                    color = InkMuted,
                )
            }
            if (hasLocker) {
                Spacer(Modifier.width(8.dp))
                Text(text = parcel.lockerNo.orEmpty(), style = ShowCodeText.Caption, color = InkMuted)
            }
        }
        /* 商品信息只在这一行里做「这是什么快递」的注脚：标题单行，店铺/价格一行小字。
           有码时码仍然是最重的那一档，信息列只吃剩余宽度。 */
        if (hasGoods) {
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                parcel.goodsTitle?.takeIf { it.isNotBlank() }?.let { title ->
                    Text(
                        text = title,
                        style = ShowCodeText.Meta,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                /* 价格给固定宽度优先显示，店铺吃剩余宽度并截断：
                   价格是「这是什么件」最硬的线索，不能因为店铺名长就被省略掉。 */
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    parcel.goodsPrice?.takeIf { it.isNotBlank() }?.let { price ->
                        Text(
                            text = price,
                            style = ShowCodeText.Caption,
                            color = InkSecondary,
                            maxLines = 1,
                        )
                    }
                    parcel.shopName?.takeIf { it.isNotBlank() }?.let { shop ->
                        Text(
                            text = shop,
                            modifier = Modifier.weight(1f),
                            style = ShowCodeText.Caption,
                            color = InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else if (urgency != Urgency.SAFE) {
            Spacer(Modifier.weight(1f))
        }
        if (urgency != Urgency.SAFE) {
            Text(
                text = urgencyLabel(urgency),
                style = ShowCodeText.Micro,
                color = urgencyColor(urgency),
            )
        }
    }
}

/** 示例卡片：降饱和度 + 斜向水印。必须一眼看出不是真数据，否则用户会以为自己有待取件。 */
@Composable
private fun SamplePointCard(group: PickupPointGroup, now: Long, onClick: () -> Unit) {
    Box {
        // 先画水印再画卡：PointCard 只有描边没有底色，所以压到底下照样看得见，
        // 却不会再糊掉取件码数字——和现场模式那条水印同一个层级理由。
        Text(
            text = stringResource(R.string.sample_badge),
            modifier = Modifier
                .align(Alignment.Center)
                .rotate(-24f),
            style = ShowCodeText.Watermark,
            color = InkWatermark,
        )
        PointCard(group = group, now = now, onClick = onClick, modifier = Modifier.alpha(0.5f))
    }
}

/**
 * 首次启动的空态承担教学职责（edge s2-home.empty-first-time）。
 * 演示入口必须在权限请求之前出现——「先给价值，再要权限」。
 */
@Composable
private fun FirstTimeHeader(onOpenAccounts: () -> Unit, onOpenDemo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_first_time_title),
            style = ShowCodeText.Body,
            color = InkSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        /* 主 CTA 是绑定平台账号：它才是自动收件的主路径；演示是次级的「先看看」。 */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Ink, ShowCodeShapes.Md)
                .clickable(onClick = onOpenAccounts),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.home_empty_first_time_cta),
                style = ShowCodeText.Section,
                color = OnsiteSurface,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.home_empty_first_time_demo),
            modifier = Modifier
                .clickable(onClick = onOpenDemo)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = ShowCodeText.Action,
            color = AccentInk,
        )
        Spacer(Modifier.height(6.dp))
    }
}

/** empty-collection：绿色成功态，不是灰色空态。它消掉的是「我是不是漏了」的焦虑。 */
@Composable
private fun AllDoneCard(monthlyPicked: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrgencySafeTint, ShowCodeShapes.Md)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(40.dp), tint = UrgencySafe)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_collection, monthlyPicked),
            style = ShowCodeText.Item,
            color = UrgencySafe,
            textAlign = TextAlign.Center,
        )
    }
}

/** 未识别入口行。完整列表与结构化原因住在收容页，首页只负责「让你知道有这件事」。 */
@Composable
private fun UnrecognizedLinkRow(count: Int, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrgencyDueSoonTint, ShowCodeShapes.Md)
            .border(1.dp, BorderDueSoon, ShowCodeShapes.Md)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = UrgencyDueSoon)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_unrecognized, count),
            modifier = Modifier.weight(1f),
            style = ShowCodeText.Item,
            color = Ink,
        )
        Text(
            text = stringResource(R.string.home_unrecognized_action),
            style = ShowCodeText.Action,
            color = AccentInk,
        )
    }
}

/**
 * 反向绑定横幅（story-3 AC5）。只在收到第一个取件点之后出现——
 * 还没有数据时谈「只看某个点」没有意义；可关闭，关闭后不再打扰。
 */
@Composable
private fun WidgetHintBanner(onPin: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_widget_hint),
            modifier = Modifier.weight(1f),
            style = ShowCodeText.Meta,
            color = InkSecondary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_widget_hint_action),
            modifier = Modifier
                .clickable(onClick = onPin)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = ShowCodeText.Action,
            color = AccentInk,
        )
        Text(
            text = "✕",
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            style = ShowCodeText.Meta,
            color = InkMuted,
        )
    }
}

/**
 * 权限入口。批 1 只有这一个按钮，正式的「价值前置式引导」是 story-1（批 2）。
 * 文案按 brief 的内容设计策略：说清能得到什么、说清数据不出本机，不说「权限不足」。
 */
@Composable
private fun EnableIngestCard(title: String, body: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(text = title, style = ShowCodeText.Item, color = AccentInk)
        Spacer(Modifier.height(4.dp))
        Text(text = body, style = ShowCodeText.Caption, color = InkMuted)
    }
}

/**
 * 手动刷新键：和搜索同一行。
 *
 * 可见字形 20dp / 1.5dp 圆头描边，主观重量比搜索轻一档；触控区 44dp 是隐形的。
 * 常态用 InkMuted，不放强调色；按下只缩 0.96、压一点透明度，不叠水波纹也不铺底色。
 *
 * 动画三段，全部由 busy / failedSignal 驱动，没有任何常驻循环：
 * 1. 点击那一圈：0→360°，阻尼 1.0 的弹簧（≈560ms，不回弹、不反转）；
 * 2. 超过约 700ms 还没结束：换成 20dp 环形进度，单个 90° 弧以 50°/s 慢慢走（一圈 7.2s）；
 * 3. 结束：首圈没走完就先顺向补完再归位；失败另加 ±4°×80ms 的两下轻晃。
 */
@Composable
private fun RefreshAction(
    busy: Boolean,
    enabled: Boolean,
    failedSignal: Int,
    onClick: () -> Unit,
    /** 长按 = 强制重抓；不传就只有单击。 */
    onLongClick: (() -> Unit)? = null,
) {
    val label = stringResource(R.string.home_refresh)
    val tint = InkMuted
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val rotation = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val wobble = remember { Animatable(0f) }
    var ringVisible by remember { mutableStateOf(false) }
    var seenFailure by remember { mutableIntStateOf(failedSignal) }

    LaunchedEffect(busy) {
        if (busy) {
            ringVisible = false
            rotation.snapTo(0f)
            /* 一整圈：dampingRatio 1.0 是临界阻尼，停得干净、没有回弹。 */
            rotation.animateTo(360f, spring(dampingRatio = 1f, stiffness = 300f))
            /* 还没结束：换环形进度。 */
            ringVisible = true
            ring.snapTo(0f)
            while (true) {
                ring.animateTo(ring.value + 360f, tween(7200, easing = MotionEasing.Linear))
            }
        } else {
            ringVisible = false
            ring.snapTo(0f)
            /* 首圈没走完就被叫停：顺向补完这一圈，不做反向急停。 */
            if (rotation.value < 360f) {
                rotation.animateTo(360f, tween(200, easing = MotionEasing.FastOutSlowIn))
            }
            rotation.snapTo(0f)
        }
    }

    LaunchedEffect(failedSignal) {
        if (failedSignal != seenFailure) {
            seenFailure = failedSignal
            wobble.snapTo(0f)
            wobble.animateTo(4f, tween(80, easing = MotionEasing.Standard))
            wobble.animateTo(-4f, tween(80, easing = MotionEasing.Standard))
            wobble.animateTo(0f, tween(80, easing = MotionEasing.Standard))
        }
    }

    val glyphAlpha by animateFloatAsState(
        targetValue = if (ringVisible) 0f else 1f,
        animationSpec = tween(140),
        label = "refresh-glyph-alpha",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (ringVisible) 1f else 0f,
        animationSpec = tween(140),
        label = "refresh-ring-alpha",
    )
    val pressScale = if (pressed) 0.96f else 1f
    val pressAlpha = if (pressed) 0.72f else 1f

    Box(
        modifier = Modifier
            .size(44.dp)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onLongClickLabel = onLongClick?.let { "强制重抓" },
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                    alpha = pressAlpha
                },
        ) {
            val stroke = 1.5.dp.toPx()
            val radius = size.minDimension / 2f - stroke / 2f
            val middle = center
            if (glyphAlpha > 0.01f) {
                rotateScope(rotation.value + wobble.value, middle) {
                    drawRefreshArrow(tint, middle, radius, stroke, glyphAlpha)
                }
            }
            if (ringAlpha > 0.01f) {
                val ringRadius = radius - 0.5.dp.toPx()
                drawCircle(
                    color = tint.copy(alpha = tint.alpha * 0.25f * ringAlpha),
                    radius = ringRadius,
                    center = middle,
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = tint.copy(alpha = tint.alpha * ringAlpha),
                    startAngle = ring.value,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(middle.x - ringRadius, middle.y - ringRadius),
                    size = Size(ringRadius * 2f, ringRadius * 2f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** 单支圆弧 + 箭头。坐标全部从画布推出来，20dp 与 24dp 是同一个形状。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRefreshArrow(
    tint: Color,
    middle: Offset,
    radius: Float,
    stroke: Float,
    alpha: Float,
) {
    val color = tint.copy(alpha = tint.alpha * alpha)
    drawArc(
        color = color,
        startAngle = -60f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(middle.x - radius, middle.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    /* 箭头在弧的终点（-60°+300°=240°，屏幕坐标里落在左上）；两臂沿切线与法线张开。 */
    val end = Math.toRadians(240.0)
    val tip = Offset(
        middle.x + radius * cos(end).toFloat(),
        middle.y + radius * sin(end).toFloat(),
    )
    val tangent = Offset(-sin(end).toFloat(), cos(end).toFloat())
    val normal = Offset(cos(end).toFloat(), sin(end).toFloat())
    val diameter = radius * 2f
    val arm = 0.145f * diameter
    val spread = 0.085f * diameter
    val a1 = Offset(
        tip.x - tangent.x * arm + normal.x * spread,
        tip.y - tangent.y * arm + normal.y * spread,
    )
    val a2 = Offset(
        tip.x - tangent.x * arm - normal.x * spread,
        tip.y - tangent.y * arm - normal.y * spread,
    )
    drawLine(color, a1, tip, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, a2, tip, strokeWidth = stroke, cap = StrokeCap.Round)
}
