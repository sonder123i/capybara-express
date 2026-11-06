package dev.showcode.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.showcode.core.searchParcels
import dev.showcode.core.urgencyOf
import dev.showcode.data.SyncResult
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
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencyDueSoonTint
import dev.showcode.ui.theme.UrgencySafe
import dev.showcode.ui.theme.UrgencySafeTint
import dev.showcode.ui.points.MergeUndoBar
import dev.showcode.ui.points.SuspectHintCard
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
    /** 正在读收件箱。胶囊要当场说出来，否则用户以为没点上会连着点。 */
    val syncing: Boolean = false,
    /** 最近一次刷新的结果。只在结束后的一小段时间里显示。 */
    val lastSyncResult: SyncResult? = null,
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
                            // 胶囊搬到这里来了：底栏要占的是它那个位置，而这句话不能跟着一起没了。
                            SyncLine(state = state, now = now, manualMessage = manualSyncMessage)
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
                           「没有新件」恰恰是最需要手动问一次的时候，演示态则只回一句提示。 */
                        RefreshAction(
                            busy = accountsSyncing,
                            enabled = !accountsSyncing,
                            failedSignal = manualSyncFailedSignal,
                            onClick = onManualSync,
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

/**
 * 顶栏那行小字：现在看到的是什么时候的数据。
 *
 * 它以前住在底部那颗胶囊里，还兼作「立即刷新」的按钮。首页改成每次进入自动刷之后按钮
 * 没必要了，这句话必须留下——自动的东西失效方式是静默的（权限被 ROM 收回不会弹通知），
 * 用户得能一眼分清「这是三分钟前的数据」和「今天根本没刷成功」。
 *
 * 刚刷完那句只露 [SYNC_RESULT_MS]，且不拿页面那个 30 秒一跳的 `now` 比：那个刻度是给
 * 倒计时用的，用它这条会时准时不准——自动刷新之后这是首页唯一的「刷过了」的证据，不能飘。
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

@Composable
private fun SyncLine(state: HomeUiState, now: Long, manualMessage: String? = null) {
    // 没加载完不出声：那时「还没同步过」是把一次正常的读库演成一件缺了什么的事。
    if (!state.loaded) return
    val result = state.lastSyncResult
    val resultAt = result?.at
    var showResult by remember(resultAt) { mutableStateOf(result != null) }
    LaunchedEffect(resultAt) {
        if (resultAt != null) {
            delay(SYNC_RESULT_MS)
            showResult = false
        }
    }
    val text = when {
        /* 手动平台的结局优先：它刚刚发生，比「更新于」更值得占这一行。 */
        manualMessage != null -> manualMessage
        state.syncing -> stringResource(R.string.home_syncing)
        state.lastSyncFailed -> stringResource(R.string.home_sync_failed)
        showResult && result != null && result.added > 0 -> stringResource(R.string.home_sync_added, result.added)
        showResult && result != null -> stringResource(R.string.home_sync_none)
        else -> state.lastSyncAt?.let {
            stringResource(R.string.home_synced_at, relativeTimeText(it, now))
        } ?: stringResource(R.string.home_never_synced)
    }
    Text(
        text = text,
        style = ShowCodeText.Caption,
        color = if (manualMessage == null && state.lastSyncFailed) UrgencyDueSoon else InkMuted,
        /* 顶栏高度是定稿的：这一行永远只占一行，再长的原因也只截断，不许把 header 撑高。 */
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .clickable(onClick = onClick)
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
                color = if (group.worst == Urgency.SAFE) InkMuted else urgencyColor(group.worst),
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
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
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
