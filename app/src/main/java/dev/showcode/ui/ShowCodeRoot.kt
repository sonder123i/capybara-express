package dev.showcode.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.SharedTransitionLayout
import dev.showcode.ui.theme.LocalSharedTransition
import dev.showcode.ui.theme.SharedTransitionBridge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.showcode.BuildConfig
import dev.showcode.app
import dev.showcode.core.DeepLinkLadder
import dev.showcode.core.IdentityCode
import dev.showcode.core.IdentityKind
import dev.showcode.core.Owner
import dev.showcode.core.PointAlias
import dev.showcode.core.PickupPointGroup
import dev.showcode.core.shouldShowBackgroundNote
import dev.showcode.core.account.AccountState
import dev.showcode.core.account.Platform
import dev.showcode.core.account.SmsChannelInput
import dev.showcode.core.account.SyncProgress
import dev.showcode.core.account.SyncRailState
import dev.showcode.core.account.syncRailState
import dev.showcode.core.bodyFingerprint
import dev.showcode.core.groupByPoint
import dev.showcode.core.mergePreview
import dev.showcode.core.nextStopAfter
import dev.showcode.core.suspectDuplicates
import dev.showcode.ui.account.AccountListScreen
import dev.showcode.ui.account.PlatformLoginScreen
import dev.showcode.ui.parcels.ParcelDetailScreen
import dev.showcode.ui.glass.GlassCompat
import dev.showcode.ui.glass.GlassRuntimeGuard
import dev.showcode.ui.glass.GlassTier
import dev.showcode.ui.glass.rememberHighTextContrast
import dev.showcode.ui.home.HomeEmpty
import dev.showcode.ui.home.HomeScreen
import dev.showcode.ui.home.HomeUiState
import dev.showcode.ui.home.SyncDigest
import dev.showcode.ui.identity.IdentityEditScreen
import dev.showcode.ui.identity.IdentityListScreen
import dev.showcode.ui.inbox.ManualImportScreen
import dev.showcode.ui.inbox.UnrecognizedScreen
import dev.showcode.ui.onboarding.OnboardStep
import dev.showcode.ui.onboarding.OnboardingScreen
import dev.showcode.ui.onsite.IdentityShowScreen
import dev.showcode.ui.onsite.OnsiteScreen
import dev.showcode.ui.onsite.TilesScreen
import dev.showcode.ui.points.MergePreviewScreen
import dev.showcode.ui.points.PointDetailScreen
import dev.showcode.ui.rules.RuleEditScreen
import dev.showcode.ui.rules.RuleListScreen
import dev.showcode.ui.settings.NotifyAppsScreen
import dev.showcode.ui.settings.SettingsScreen
import dev.showcode.ui.theme.LocalNavigationMotion
import dev.showcode.ui.theme.MotionProfile
import dev.showcode.ui.theme.ThemeController
import dev.showcode.ui.theme.StartupChoreography
import dev.showcode.ui.theme.StartupLogoAnimation
import dev.showcode.ui.theme.MotionTokens
import dev.showcode.ui.theme.NavigationPages
import dev.showcode.ui.theme.drawEnvironment
import dev.showcode.ui.theme.rememberNavigationMotionState
import dev.showcode.ui.theme.ThemeReveal
import dev.showcode.ui.theme.ThemeRevealOverlay
import dev.showcode.ui.theme.rememberReducedMotion
import dev.showcode.widget.PickupWidgetProvider
import dev.showcode.widget.WidgetNav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import dev.showcode.data.AccountSyncEngine
import dev.showcode.data.AUTO_SYNC_DELAY_MS
import dev.showcode.data.AutoSyncDecision
import dev.showcode.data.syncSuccessMessage
import dev.showcode.data.AccountSyncResult
import dev.showcode.data.decideAutoSync
import dev.showcode.data.hasValidatedNetwork
import dev.showcode.data.DemoData
import dev.showcode.data.DemoIdentity
import dev.showcode.data.PickupReminder
import dev.showcode.data.InstalledApp
import dev.showcode.data.unrecognizedBadgeCount
import dev.showcode.core.Slog

private enum class Route {
    HOME, ONSITE, TILES, SETTINGS, UNRECOGNIZED, MANUAL_IMPORT, ONBOARDING, POINT_DETAIL,
    MERGE_PREVIEW, RULES, RULE_EDIT, IDENTITY_LIST, IDENTITY_ADD, IDENTITY_SHOW, NOTIFY_APPS,
    ACCOUNT_LIST, ACCOUNT_LOGIN, PARCEL_DETAIL,
}

/**
 * 三个主屏在导航里的位置。换屏的方向与连续进度都按它算（教务 NavigationMotionState 的口径）：
 * 0 → 2 是向右，2 → 0 是向左，中途被打断就从当前进度接着走。
 * 子屏不占位置——它们是另一层（层级页），不参与这排滑杆。
 */
private fun tabIndexOf(route: Route): Int? = when (route) {
    Route.HOME -> 0
    Route.IDENTITY_LIST -> 1
    Route.SETTINGS -> 2
    else -> null
}

/**
 * 子屏的层级深度：决定推入/弹出方向。主层（三个主屏）是 0，子屏按它盖在谁身上给 1/2。
 * 静态深度对这棵路由树够用：每条边要么变深（推入）要么变浅（弹出），
 * 唯一的同层互切（手动录入 ↔ 收容页）按推入处理，方向反了也只是一次 22dp 的方向感差异。
 */
private fun depthOf(route: Route?): Int = when (route) {
    null -> 0
    Route.TILES -> 2
    Route.MERGE_PREVIEW -> 2
    Route.RULE_EDIT -> 2
    Route.IDENTITY_SHOW -> 2
    Route.ACCOUNT_LOGIN -> 2
    Route.PARCEL_DETAIL -> 2
    Route.ONBOARDING -> 0
    else -> 1
}

/* 合并后撤销条的驻留时长。story-6 的 user_side 写的是「五秒内屏幕下方有个已合并 · 撤销」。 */
private const val MERGE_UNDO_MS = 5_000L

/**
 * 三个主屏 + 三个次屏，用一个枚举做路由。
 * 不引 navigation-compose：缓存里只有 2.7.7，与 Compose 1.12 不兼容，
 * 而这个规模的状态机比一张导航图更好读。
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun ShowCodeRoot() {
    val context = LocalContext.current
    val repository = context.app.repository
    val appData by repository.data.collectAsState()

    var route by rememberSaveable {
        mutableStateOf(if (repository.prefs.onboardingDone) Route.HOME else Route.ONBOARDING)
    }

    /* 出示类屏幕的常亮由根路由统一持有：换屏走 Crossfade，两屏同时活着时
       各管一个 Window flag 会互相清掉（见 ui/KeepScreenOn.kt）。
       现场模式、平铺总览、身份码出示——三屏都是「手机举在别人面前」的场景。 */
    KeepScreenOn(
        enabled = route == Route.ONSITE || route == Route.TILES || route == Route.IDENTITY_SHOW,
    )
    var pointKey by rememberSaveable { mutableStateOf("") }
    // 点名要在导航时存下来：该点的件全取完之后分组会消失，
    // 而现场模式的空态仍然要把「我来对地方了」这句话讲完整。
    var pointName by rememberSaveable { mutableStateOf("") }
    var focusParcelId by rememberSaveable { mutableStateOf<Long?>(null) }
    var demo by rememberSaveable { mutableStateOf(false) }
    var manualPrefill by rememberSaveable { mutableStateOf("") }
    var manualSourceBody by rememberSaveable { mutableStateOf<String?>(null) }
    var manualReturn by rememberSaveable { mutableStateOf(Route.HOME) }
    var onboardStep by rememberSaveable { mutableStateOf(OnboardStep.WELCOME) }
    var detailKey by rememberSaveable { mutableStateOf("") }
    /* 包裹详情有两个入口：现场（on-site）和在途小节。返回键要回你来的地方。 */
    var parcelDetailReturn by rememberSaveable { mutableStateOf(Route.ONSITE) }
    /* 合并预览与「刚合过一次」的撤销态。预览内容里有 List，存不进 Saveable，
       而进程在弹层开着时被杀掉是无关紧要的丢失——重开一次预览就行。 */
    var pendingMerge by remember { mutableStateOf<dev.showcode.core.MergePreview?>(null) }
    var mergeReturn by rememberSaveable { mutableStateOf(Route.HOME) }
    var unrecognizedReturn by rememberSaveable { mutableStateOf(Route.HOME) }
    /* 规则编辑器的原文预填。从没认出来的那条短信进来时带着它的正文——
       那一屏要治的就是眼前这一条，让用户再抄一遍原文等于没做这个入口。 */
    var rulePrefillRaw by rememberSaveable { mutableStateOf("") }
    var ruleReturn by rememberSaveable { mutableStateOf(Route.SETTINGS) }
    var notifyReturn by rememberSaveable { mutableStateOf(Route.SETTINGS) }
    /* 平台账号从设置、首页空态、首次引导三处都能进；返回要回你来的地方。 */
    var accountReturn by rememberSaveable { mutableStateOf(Route.SETTINGS) }
    /* 账号列表是设置页的子屏；登录页是账号列表的子屏。账号列表读的是加密表，
       登录成功后必须重读一次，否则返回时还是「未绑定」。 */
    var accounts by remember { mutableStateOf(repository.accounts.list()) }
    var loginPlatformId by rememberSaveable { mutableStateOf(Platform.PDD.id) }
    var accountSyncing by remember { mutableStateOf<String?>(null) }
    /** 正在同步的那个账号走到哪一步（读订单 / 第几件详情）。顶栏轨道用它说话。 */
    var accountProgress by remember { mutableStateOf<SyncProgress?>(null) }
    /** 这一轮手动同步的结果摘要；轨道下面那行 8 秒后回到时间。 */
    var lastDigest by remember { mutableStateOf<SyncDigest?>(null) }
    var accountSyncMessage by remember { mutableStateOf<String?>(null) }
    /** 正在跑的抓取。进入登录页前必须取消，否则它会和登录页抢同一个 CookieManager。 */
    var accountSyncJob by remember { mutableStateOf<Job?>(null) }
    var parcelDetailId by rememberSaveable { mutableStateOf<Long?>(null) }
    val accountScope = rememberCoroutineScope()
    /** 正在出示哪一张。存 id 不存对象：码被删掉时那一屏必须跟着退回去，而不是展示一张已经不存在的码。 */
    var identityShowId by rememberSaveable { mutableStateOf(0L) }
    /** 上一次按下那颗按钮的结局。只在出示那一屏说话，离开就清掉。 */
    var identityLaunch by remember { mutableStateOf<dev.showcode.core.LaunchOutcome?>(null) }
    /* 能启动的 App。只在进「存一张」那一屏时查一次：这个查询要对几百个包各解一次显示名，
       放在组合里每帧跑一遍是会卡的。 */
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    /* 代取件分组的名字。写在这里而不是每次现读 prefs：prefs 不是可观察的，
       用户在设置页改完必须当场反映到现场模式的那个分组标签上。 */
    var familyLabel by remember { mutableStateOf(repository.prefs.familyLabel) }
    var keepTarget by rememberSaveable { mutableStateOf(true) }
    var lastMerge by remember { mutableStateOf<PointAlias?>(null) }
    var widgetAdded by remember { mutableStateOf(false) }
    /* 查快递页面的剪贴板文本预填。 */

    /* 演示数据在进程内只生成一次，并用同一个锚点冻结全部时效。
       录制时渲染用 [demoNow] 而不是真实时钟：倒计时、紧急度、分组顺序都不会自己跳，
       同一场演示里每一帧都可截。每次进入只清「已取」，相对时长与排序完全一致。
       「已取」只改内存集合，一个字都不落库，退出后真实数据不受影响。 */
    val demoNow = remember { System.currentTimeMillis() }
    val demoParcels = remember(demoNow) { DemoData.parcels(demoNow) }
    val demoAccounts = remember(demoNow) { DemoData.accounts(demoNow) }
    val demoPicked = remember { mutableStateListOf<Long>() }
    /* 演示身份码：同样只在内存里。进演示时铺演示码，退出即清——真实身份码表一行都不碰。 */
    val demoIdentityCodes = remember { mutableStateListOf<IdentityCode>() }

    val now = rememberTickingNow(30_000L)
    val thresholds by repository.thresholds.collectAsState()
    /* 演示用固定阈值，不读用户改过的设置：否则同一套演示在不同设备/不同设置下
       会算出不同的临期与排序，录制素材就不确定了。 */
    val demoThresholds = remember { dev.showcode.core.UrgencyThresholds() }

    val realGroups = remember(appData.parcels, appData.aliases, now, thresholds) {
        groupByPoint(appData.parcels, now, thresholds, appData.aliases)
    }
    /* 在途/待补信息的件：独立小节，不参与待取计数。
       到站但 pointKey 为空的（详情失败先入列）也放这里，等下次同步补点。 */
    val realInTransit = remember(appData.parcels) {
        appData.parcels
            .filter {
                !it.picked && (
                    it.stage == dev.showcode.core.Stage.IN_TRANSIT ||
                        it.stage == dev.showcode.core.Stage.DELIVERING ||
                        (it.stage == dev.showcode.core.Stage.AT_STATION && it.pointKey.isBlank())
                    )
            }
            .sortedByDescending { it.receivedAt }
    }
    val sampleGroups = remember(demoParcels, demoNow, demoThresholds) {
        groupByPoint(demoParcels, demoNow, demoThresholds)
    }
    val demoGroups = remember(demoParcels, demoPicked.toList(), demoNow, demoThresholds) {
        groupByPoint(demoParcels.filterNot { demoPicked.contains(it.id) }, demoNow, demoThresholds)
    }
    val activeGroups = if (demo) demoGroups else realGroups
    /** 演示态一律用冻结时钟；真实态才走会跳的 now。 */
    val renderNow = if (demo) demoNow else now

    /* 收工卡上的下一站。演示态恒为 null：enterDemo 会清空 demoPicked，
       从收工卡跳过去会让上一个点刚取掉的件全部复活。 */
    val nextStop = remember(activeGroups, pointKey, renderNow, demo) {
        if (demo) null else nextStopAfter(pointKey, activeGroups, renderNow)
    }

    /* 主屏换屏：单进度连续运动（教务 NavigationMotionState 同源）。
       tabRoute 为 null（子屏）时主层不渲染，但 lastTabIndex 要记住「来时在哪一 tab」，
       否则从设置进子屏再回来会被当成回首页。 */
    val reducedMotion = rememberReducedMotion()
    /* PixelCopy 需要一个挂在窗口上的 View 来找 Window。用 Compose 自己的 LocalView：
       它一定已经挂进窗口，而 LocalContext 可能是个被包了几层的 ContextWrapper。 */
    val rootView = LocalView.current
    var lastTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabRoute = tabIndexOf(route)
    LaunchedEffect(tabRoute) { if (tabRoute != null) lastTabIndex = tabRoute }
    val navMotion = rememberNavigationMotionState(
        selected = tabRoute ?: lastTabIndex,
        reduced = reducedMotion,
    )

    /* 轻提示只报字面重叠的一对，且用户说过「不是」之后就永久闭嘴。
       AC4 的红线是绝不自动合并，所以这里只产生一个候选、不产生任何数据变化。 */
    val suspect = remember(realGroups, appData.dismissals) {
        suspectDuplicates(realGroups, appData.dismissals).firstOrNull()
    }

    LaunchedEffect(lastMerge) {
        if (lastMerge != null) {
            delay(MERGE_UNDO_MS)
            lastMerge = null
        }
    }

    // 装了什么 App 这件事不会在本次会话里变，所以查一次就够；离开那一屏也不清，回来不用重查。
    // 这份清单只服务「这张码属于哪个 App」那一格；身份码列表/预置那一排不再按它筛。
    LaunchedEffect(route) {
        if (route == Route.IDENTITY_ADD && installedApps.isEmpty()) {
            installedApps = repository.installedApps()
        }
        if (route == Route.ACCOUNT_LIST && !demo) accounts = repository.accounts.list()
    }

    /* 每次进待取页都刷一遍收件箱。首页那颗「立即刷新」胶囊被拿掉的前提就是这一条——
       位置要让给底栏，而这句话不能一起丢掉：自动的东西失效方式是静默的，
       所以顶栏留一行小字报「上次同步到什么时候」。冷启动第一次组合就在 HOME，也算进入。
       演示模式不刷：那条路径一个字都不该落库。 */
    LaunchedEffect(route) {
        if (route == Route.HOME && !demo) repository.refreshNow()
    }

    fun openMergePreview(a: PickupPointGroup, b: PickupPointGroup, preferTarget: PickupPointGroup? = null) {
        /* 从详情页点「并过来」时方向已经由用户定死了——并到他正在看的那个点，
           这里不能再拿件数去翻他的决定。只有轻提示那条路径两边没有主客，
           才用件数默认保留谁的名字（件多是「这个点一直在用」的证据）。 */
        val (source, target) = when {
            preferTarget != null -> (if (preferTarget.key == a.key) b else a) to preferTarget
            b.items.size > a.items.size -> a to b
            else -> b to a
        }
        pendingMerge = mergePreview(source, target)
        keepTarget = true
        mergeReturn = route
        route = Route.MERGE_PREVIEW
    }

    fun confirmMerge() {
        val preview = pendingMerge ?: return
        val source = if (keepTarget) preview.source else preview.target
        val target = if (keepTarget) preview.target else preview.source
        val record = PointAlias(
            aliasKey = source.key,
            aliasName = source.name,
            targetKey = target.key,
            targetName = target.name,
            createdAt = System.currentTimeMillis(),
        )
        repository.mergePoints(record)
        lastMerge = record
        pendingMerge = null
        route = mergeReturn
        /* 详情页刚刚看着 A 的视角，合并完要跟着活下来的那个点，
           否则回去会看见一个已经不存在的分组。 */
        if (mergeReturn == Route.POINT_DETAIL) detailKey = target.key
    }

    var smsGranted by remember { mutableStateOf(repository.hasSmsPermission()) }
    var notificationGranted by remember { mutableStateOf(repository.hasNotificationAccess()) }
    /* ⚠️ 这条和上面那条**不是同一件事**：`notificationGranted` 是「读别人的通知」（监听权），
       这条是「发我们自己的通知」（POST_NOTIFICATIONS，Android 13+ 运行时权限）。
       名字必须分开——两个都叫 notification 的布尔值放在同一个作用域里，将来一定有人用错。 */
    var canPostNotifications by remember { mutableStateOf(PickupReminder.permissionGranted(context)) }
    val postNotificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> canPostNotifications = granted }

    // 通知监听权只能在系统设置里给，回来时必须重新读一次，否则按钮会一直挂着
    val lifecycleOwner = LocalLifecycleOwner.current
    /* 前台静默同步：一个进程只自动跑一次（节流与跳过判据见 data/AutoSync.kt）。
       它不弹任何东西，也不改当前路由；失败的结局只落在账号卡的 lastSyncResult 上。 */
    var autoSyncRan by remember { mutableStateOf(false) }

    suspend fun runAccountSync(
        targets: List<dev.showcode.core.account.BoundAccount>,
        tag: String,
        /** true = 忽略「状态没变就跳过详情」，这次当全量重抓。 */
        force: Boolean = false,
    ): AccountSyncResult? {
        var added = 0
        var updated = 0
        var skipped = 0
        var succeeded = 0
        val lines = mutableListOf<String>()
        for (account in targets) {
            val activity = context as? android.app.Activity
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                Slog.i(tag) { "stop: activity 不在了" }
                break
            }
            accountSyncing = account.id
            accountProgress = null
            accountSyncMessage = null
            Slog.i(tag) { "start ${account.platform.id}" }
            /* 一个账号失败不能中止其余账号：按账号记台账，循环继续。
               取消是另一回事，必须原样抛出去，否则取消会被当成一次普通失败吞掉。 */
            val result = try {
                AccountSyncEngine.sync(
                    activity = activity,
                    repository = repository,
                    account = account,
                    force = force,
                    /* 抓取器在 IO/主线程之间切换，UI 状态只在这里（主线程）改。 */
                    onProgress = { step -> withContext(Dispatchers.Main) { accountProgress = step } },
                )
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Slog.i(tag) { "fail ${account.platform.id}" }
                null
            }
            accountSyncing = null
            accountProgress = null
            /* 账号表在加密库里，读它也要下 IO；Compose 状态只在这里（主线程）改。 */
            accounts = withContext(Dispatchers.IO) { repository.accounts.list() }
            if (result == null) {
                /* 失败原因已经由 AccountSyncEngine 写进账号状态；这里只把它抬到状态行。
                   不弹窗、不置 ACTIVE、不推进 last_sync_at——那几条都在引擎里。 */
                val reason = accounts.firstOrNull { it.id == account.id }?.lastSyncResult
                    ?: "这次没同步上"
                lines += "${account.platform.label}：$reason"
                Slog.i(tag) { "fail ${account.platform.id}: $reason" }
                continue
            }
            succeeded++
            added += result.added
            updated += result.updated
            skipped += result.skippedHistory + result.skippedNoTracking +
                result.skippedNoPoint + result.skippedNoCode
            lines += "${account.platform.label}：${syncSuccessMessage(result)}"
            Slog.i(tag) {
                "ok ${account.platform.id}: added=${result.added} updated=${result.updated}"
            }
        }
        val joined = lines.joinToString("；")
        val single = lines.singleOrNull()?.substringAfter("：").orEmpty()
        accountSyncMessage = when {
            joined.isEmpty() -> null
            // 单账号保持既有文案；多账号才逐家列出来，不让失败被最后一条覆盖。
            tag == "AutoSync" && targets.size == 1 ->
                if (single == "同步成功 · 没有新件") "刚刚同步 · 没有新件"
                else "刚刚同步 · " + single.removePrefix("同步成功 · ")
            tag == "AutoSync" -> "刚刚同步 · $joined"
            else -> joined
        }
        // 全部账号都失败才算整体失败；只要有一个成功，其余失败只留在状态行里。
        if (succeeded == 0) return null
        lastDigest = SyncDigest(
            added = added,
            updated = updated,
            skipped = skipped,
            failed = succeeded < targets.size,
            at = System.currentTimeMillis(),
        )
        return AccountSyncResult(added, updated, 0, 0, 0, 0, System.currentTimeMillis())
    }

    /* 手动刷新：用户主动按的，就该现在跑——不套那 15 分钟节流；
       但必须和任何正在跑的同步互斥，避免同时开两个 WebView 抓同一家。 */
    var manualSyncBusy by remember { mutableStateOf(false) }
    var manualSyncMessage by remember { mutableStateOf<String?>(null) }
    /* 每失败一次自增：刷新键只据此晃两下，不需要把「失败」这个词再传一遍。 */
    var manualSyncFailSignal by remember { mutableIntStateOf(0) }
    LaunchedEffect(manualSyncMessage) {
        if (manualSyncMessage != null) {
            delay(8_000)
            manualSyncMessage = null
        }
    }
    /**
     * 手动刷新。
     *
     * @param force true = 长按刷新键的「强制重抓」：忽略详情门控与节流，每一单都重新读。
     * @param onlyAccountId 轨道上某一格的重试：只跑这一家，其余不动。
     */
    fun startManualSync(force: Boolean = false, onlyAccountId: String? = null) {
        if (accountSyncing != null || manualSyncBusy) {
            Slog.i("ManualSync") { "ignore: 已有同步在跑" }
            return
        }
        if (demo) {
            manualSyncMessage = context.getString(dev.showcode.R.string.home_refresh_demo)
            Slog.i("ManualSync") { "demo: 不联网" }
            return
        }
        /* 手动刷新也要先看联网：没有出口的 Wi-Fi 会让每次点击都白等十几秒，
           而且失败还会往账号卡上写一条不该写的「没同步上」。 */
        if (!hasValidatedNetwork(context)) {
            manualSyncMessage = "这次没联网，先没同步。"
            Slog.i("ManualSync") { "offline: 不发起抓取" }
            return
        }
        manualSyncBusy = true
        manualSyncMessage = if (force) {
            context.getString(dev.showcode.R.string.sync_rail_force_started)
        } else {
            null
        }
        accountSyncJob = accountScope.launch {
            /* 账号表读取下 IO；没账号这条消息也在这里给，主线程不做 DB 读。 */
            val all = withContext(Dispatchers.IO) {
                repository.accounts.list().filter { it.enabled }
            }
            val targets = onlyAccountId?.let { id -> all.filter { it.id == id } } ?: all
            if (targets.isEmpty()) {
                manualSyncBusy = false
                manualSyncMessage = context.getString(dev.showcode.R.string.home_refresh_no_account)
                Slog.i("ManualSync") { "no account" }
                return@launch
            }
            val result = runAccountSync(targets, "ManualSync", force)
            manualSyncBusy = false
            if (result == null) manualSyncFailSignal++
            /* 全量成功时不再抢那一行：轨道会用 8 秒摘要说「新增/更新/跳过」。
               失败、部分失败、单通道重试仍走逐家台账——那几句里带着是哪一家。 */
            manualSyncMessage = when {
                result == null ->
                    accountSyncMessage ?: context.getString(dev.showcode.R.string.home_refresh_failed)
                onlyAccountId != null || lastDigest?.failed == true ->
                    accountSyncMessage ?: syncSuccessMessage(result)
                else -> null
            }
            Slog.i("ManualSync") { "done: ${manualSyncMessage ?: "轨道摘要"}" }
        }
    }

    /**
     * 下拉 / 红条 / 刷新键的共同入口：先本地（短信收件箱），再平台。
     * 本地那一步是毫秒级的，不该被「要联网」挡在门外。
     */
    fun refreshEverything(force: Boolean = false) {
        if (!demo) repository.refreshNow()
        startManualSync(force)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                smsGranted = repository.hasSmsPermission()
                notificationGranted = repository.hasNotificationAccess()
                canPostNotifications = PickupReminder.permissionGranted(context)
                widgetAdded = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, PickupWidgetProvider::class.java))
                    .isNotEmpty()
                // 身份码那条链路不再探测装没装：入口固定常驻，点了走既有降级链。

                /* 等首屏画完再起同步。用户正在登录时不跑：抓取会清 CookieManager，
                   会把登录页刚拿到的会话一起清掉。 */
                if (route != Route.ACCOUNT_LOGIN) {
                    accountSyncJob = accountScope.launch {
                        delay(AUTO_SYNC_DELAY_MS)
                        /* 账号表读 DB/Keystore 之外的列，下 IO；判据本身仍是纯函数。 */
                        val bound = withContext(Dispatchers.IO) { repository.accounts.list() }
                        val lastSuccess = bound.mapNotNull { it.lastSyncAt }.maxOrNull()
                        val decision = decideAutoSync(
                            demo = demo,
                            accounts = bound,
                            syncing = accountSyncing != null || manualSyncBusy,
                            online = hasValidatedNetwork(context),
                            lastSuccessAt = lastSuccess,
                            now = System.currentTimeMillis(),
                            alreadyRanThisSession = autoSyncRan,
                            /* 上次是瞬时失败？那半小时内不再自动重撞；
                               手动刷新不走这条退避。 */
                            lastTransientFailureAt = repository.prefs.lastTransientFailureAt
                                .takeIf { it > 0 },
                        )
                        when (decision) {
                            is AutoSyncDecision.Skip -> {
                                Slog.i("AutoSync") { "skip: ${decision.reason}" }
                                /* 无网络只留一句中性提示：不弹窗，也不写库。 */
                                if (decision.offline) accountSyncMessage = "这次没联网，先没同步。"
                            }
                            is AutoSyncDecision.Run -> {
                                autoSyncRan = true
                                Slog.i("AutoSync") {
                                    "run: " + decision.accounts.joinToString { it.platform.id }
                                }
                                // 状态行已由 runAccountSync 按账号汇总，这里不再覆盖。
                                runAccountSync(decision.accounts, "AutoSync")
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        smsGranted = result[Manifest.permission.READ_SMS] == true
        // 授权后立刻回填收件箱：用户手机上本来就躺着待取件，不该让他再等下一条短信
        if (smsGranted) repository.backfillSms()
    }

    fun requestSmsPermission() {
        smsPermissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
    }

    fun openNotificationSettings() {
        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    fun openAppDetails() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            )
        }
    }

    /**
     * 打开那张码的官方 App。结局要回到屏上说话，不许在这里吞掉——
     * 竞品 issue #39 的症状就是「点了没反应」，而它真正的答案是 ROM 里的一个开关。
     */
    fun openOfficialApp(code: IdentityCode) {
        identityLaunch = repository.openOfficialApp(code.appPackage)
    }

    /** 方案丙的反向绑定：从 App 内请求再钉一个绑定单点的组件。 */
    fun requestPinWidget() {
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            if (manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(
                    ComponentName(context, PickupWidgetProvider::class.java),
                    null,
                    null,
                )
            }
        }
    }

    fun openPoint(key: String, name: String) {
        pointKey = key
        pointName = name
        focusParcelId = null
        route = Route.ONSITE
    }

    /** 一键进入演示：从首页开场，而不是直接跳进某个点。 */
    fun enterDemo() {
        demo = true
        demoPicked.clear()
        demoIdentityCodes.clear()
        demoIdentityCodes.addAll(DemoIdentity.seed(demoNow))
        pointKey = ""
        pointName = ""
        focusParcelId = null
        route = Route.HOME
    }

    /** 退出演示即清空演示痕迹；引导没走完时回到引导，而不是首页。 */
    fun exitDemo() {
        demo = false
        demoPicked.clear()
        demoIdentityCodes.clear()
        if (repository.prefs.onboardingDone) {
            route = Route.HOME
        } else {
            /* 从引导页进的演示，退出就回到欢迎页：那颗「先看完整演示」还在原地，
               可以马上再进一遍。 */
            onboardStep = OnboardStep.WELCOME
            route = Route.ONBOARDING
        }
    }

    /* 外部入口的深链（桌面组件、巡检提醒那条通知）：冷启动与 onNewIntent 都汇到 WidgetNav.request。
       通知点开来必须**直接落在那一格里**——它叫的是「对一眼有没有漏的」，
       还要用户自己找一遍那一格在哪，这条提醒就白发了。 */
    LaunchedEffect(Unit) {
        WidgetNav.request.collect { intent ->
            val target = WidgetNav.routeOf(intent) ?: return@collect
            WidgetNav.request.value = null
            when (target) {
                WidgetNav.ROUTE_ONSITE -> {
                    val key = WidgetNav.pointKeyOf(intent).orEmpty()
                    demo = false
                    openPoint(key, realGroups.firstOrNull { it.key == key }?.name.orEmpty())
                }
                WidgetNav.ROUTE_DEMO -> enterDemo()
                WidgetNav.ROUTE_UNRECOGNIZED -> route = Route.UNRECOGNIZED
                WidgetNav.ROUTE_IDENTITY -> route = Route.IDENTITY_LIST
                /* 划词进来：手动导入页带着那段原文。这里不自动跑解析，也不自动落库——
                   用户按「自动提取」和「保存」各一下，是这一屏本来就有的两道确认。
                   空文本什么都不做：它到不了这里（`TextIntake` 已经挡掉），
                   但万一到了，停在原处比把人扔到首页更像话。 */
                WidgetNav.ROUTE_MANUAL -> {
                    val text = WidgetNav.textOf(intent).orEmpty()
                    if (text.isNotEmpty()) {
                        manualPrefill = text
                        manualSourceBody = null
                        manualReturn = Route.HOME
                        route = Route.MANUAL_IMPORT
                    }
                }
                else -> route = Route.HOME
            }
        }
    }

    /* 返回手势：用 **PredictiveBackHandler** 而不是 BackHandler。
     *
     * 区别在「预演」：开了 predictive back（manifest 的 enableOnBackInvokedCallback）之后，
     * 安卓 14+ 用户从屏幕边缘往回划时，手指一动系统就会把进度喂进来，
     * 我们可以让详情页**跟着手指缩小**、露出下面的首页；松手才真的退。
     * 旧 BackHandler 只有「已经退了」这一种时机，手势全程没有反馈。
     *
     * 进度落在 `backProgress` 上，由下面的页面层读它做缩放——
     * 手势取消（`CancellationException`）时把它归零，页面弹回原位。
     * 注意这里**不在流结束时才做事**：`collect` 收到第一个 BackEvent 就把进度写进去，
     * 结束时才执行真正的 route 跳转。
     *
     * 引导的第一步例外：那时没有「来路」可回，返回就该退出。
     *
     * ⚠️ 验证方式：`adb shell input swipe` / `input motionevent` **测不出**这条路径——
     * 注入的事件不经过 SystemUI 的手势检测层，系统根本不会把它识别成返回手势。
     * 真机上要从屏幕边缘往回划才能看到预演。这里只验证了返回键（三键导航）没回归。 */
    /* 退一级。原来这段是 BackHandler 的 lambda 体，抽成函数是为了让
       predictive back 的「手势完成」和旧系统的「按下返回键」走同一条路——
       两套各写一遍迟早会分叉。 */
    fun navigateBack() {
        when (route) {
            // 演示从首页开场，返回就回演示首页；退出演示只走那条常驻的「退出」。
            Route.ONSITE -> route = Route.HOME
            Route.TILES -> route = Route.ONSITE
            Route.POINT_DETAIL -> route = Route.HOME
            Route.MERGE_PREVIEW -> {
                pendingMerge = null
                route = mergeReturn
            }
            Route.SETTINGS -> route = Route.HOME
            Route.UNRECOGNIZED -> route = unrecognizedReturn
            Route.RULES -> route = ruleReturn
            // 编辑器写完只清预填，不清规则内容：正写着被返回键打断，回来还能接着写。
            Route.RULE_EDIT -> {
                rulePrefillRaw = ""
                route = Route.RULES
            }
            // 底栏那三格是主屏：身份码列表按返回回默认那一格（待取页），而不是退出 App——
            // 柜机前按了一下返回结果人被扔到桌面上，是比少一级面包屑更让人恼火的事。
            Route.IDENTITY_LIST -> route = Route.HOME
            Route.IDENTITY_ADD -> route = Route.IDENTITY_LIST
            Route.IDENTITY_SHOW -> route = Route.IDENTITY_LIST
            Route.NOTIFY_APPS -> route = notifyReturn
            Route.MANUAL_IMPORT -> route = manualReturn
            Route.ACCOUNT_LIST -> route = accountReturn
            Route.ACCOUNT_LOGIN -> route = Route.ACCOUNT_LIST
            Route.PARCEL_DETAIL -> route = parcelDetailReturn
            Route.ONBOARDING -> {
                val steps = OnboardStep.entries
                onboardStep = steps[(steps.indexOf(onboardStep) - 1).coerceAtLeast(0)]
            }
            Route.HOME -> Unit
        }
    }

    /* predictive back 只在 API 33+ 有。旧系统上 PredictiveBackHandler 收不到事件，
       返回键必须由 BackHandler 接住，否则那一档设备上「按返回直接退出 App」。 */
    val predictiveBackSupported = Build.VERSION.SDK_INT >= 33

    val backEnabled = route != Route.HOME &&
        !(route == Route.ONBOARDING && onboardStep == OnboardStep.WELCOME)
    val backProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    PredictiveBackHandler(enabled = backEnabled) { events ->
        try {
            events.collect { event ->
                /* progress 是 0→1 的手势进度；0.22 是「手指刚动就有反馈」的起步幅度，
                   留 78% 给真正退出的那一段，否则划一点点页面就快没了。 */
                backProgress.snapTo(event.progress * 0.22f)
            }
            /* 流正常结束 = 用户松手且手势完成 → 真的退一级。 */
            backProgress.snapTo(0f)
            navigateBack()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            /* 手势取消：页面弹回原位。这里不能吞掉异常，要重新抛出去让协程正常结束。 */
            backProgress.animateTo(0f, androidx.compose.animation.core.spring(0.8f, 420f))
            throw cancelled
        }
    }

    /* 旧 BackHandler 保留给「predictive back 不可用」的旧系统（API < 33）：
       那些设备上 PredictiveBackHandler 不会收到事件，返回键还得有人接。
       两套同时存在不冲突——系统只会在其中一条路上回调。 */
    BackHandler(enabled = backEnabled && !predictiveBackSupported) {
        navigateBack()
    }

    val healthReport = remember(appData.lastSyncAt, smsGranted, notificationGranted, now) {
        repository.healthReport(now)
    }

    /* 后台可疑是首页唯一保留的说明（中性灰、可关闭）。关闭只对这一轮 lastSyncAt 有效：
       真的同步过一次之后时间戳会变，问题再来时它会重新出现，既不会一次关掉永久失明，
       也不会每次进首页都再弹一遍。 */
    var backgroundNoteDismissedFor by remember {
        mutableStateOf(repository.prefs.backgroundNoteDismissedForSyncAt)
    }
    val showBackgroundNote = shouldShowBackgroundNote(
        worst = healthReport.worst,
        lastSyncAt = appData.lastSyncAt,
        dismissedForSyncAt = backgroundNoteDismissedFor,
    )

    /* 剪贴板自动填的「这条你已经导过了」判据。两批都要：
       只取待取件的 raw 的话，用户从收容页那条复制原文回来会被再填一次——
       而那个列表存在的理由恰恰是「这条还没处理完」，不是「这条不存在」。
       remember 是必需的：指纹是 sha256，几十条正文每次重组重算一遍没有理由。 */
    val knownBodies = remember(appData.parcels, appData.unrecognized) {
        appData.parcels.mapTo(mutableSetOf()) { bodyFingerprint(it.raw) } +
            appData.unrecognized.map { bodyFingerprint(it.body) }
    }

    /* 四条同步通道的状态。演示态不读真实账号，所以整条轨道在那边不出现。 */
    val syncRail = if (demo) {
        null
    } else {
        syncRailState(
            accounts = accounts,
            sms = SmsChannelInput(
                enabled = smsGranted,
                lastAt = appData.lastSmsSyncAt,
                failed = appData.lastSyncFailed,
                running = appData.syncing,
                lastAdded = appData.lastSyncResult?.added ?: 0,
            ),
            syncingAccountId = accountSyncing,
            progress = accountProgress,
            now = renderNow,
        )
    }
    val homeState = if (demo) {
        /* 演示态完全独立：不读真实件、不读权限、不带任何同步/收容/提醒入口。
           空态只剩「全部取完」——录制收尾那一段正好用上。 */
        HomeUiState(
            loaded = true,
            groups = demoGroups,
            empty = if (demoGroups.isEmpty()) HomeEmpty.ALL_DONE else HomeEmpty.NONE,
            monthlyPicked = demoPicked.size,
            lastSyncAt = demoNow,
            syncing = false,
            lastSyncFailed = false,
            unrecognizedCount = 0,
            pickedParcels = demoParcels.filter { it.picked },
            inTransit = emptyList(),
            showWidgetHint = false,
            smsPermissionGranted = true,
            notificationAccessGranted = true,
            suspect = null,
        )
    } else {
        HomeUiState(
            loaded = appData.loaded,
            groups = realGroups,
            empty = when {
                !appData.loaded -> HomeEmpty.NONE
                realGroups.isNotEmpty() || realInTransit.isNotEmpty() -> HomeEmpty.NONE
                appData.monthlyPicked > 0 -> HomeEmpty.ALL_DONE
                else -> HomeEmpty.FIRST_TIME
            },
            monthlyPicked = appData.monthlyPicked,
            lastSyncAt = appData.lastSyncAt,
            syncing = appData.syncing,
            lastSyncFailed = appData.lastSyncFailed,
            unrecognizedCount = unrecognizedBadgeCount(appData.unrecognized),
            /* 检索那句「你可能已经取过了」的依据。已取的件不进待取区，但检索仍要能回答
               「这串你今天已经取过了」——那是提示，不是一份已取列表。 */
            pickedParcels = remember(appData.parcels) { appData.parcels.filter { it.picked } },
            inTransit = realInTransit,
            showWidgetHint = !repository.prefs.widgetHintDismissed && realGroups.isNotEmpty(),
            smsPermissionGranted = smsGranted,
            notificationAccessGranted = notificationGranted,
            suspect = suspect,
            rail = syncRail,
            digest = lastDigest,
        )
    }

    /* 底栏那层玻璃的输入。判决在 ui/glass/GlassCompat，这里只把它收齐——
       prefs 不是可观察的，所以用户刚在设置里改完的那一下必须由本地状态接住。 */
    var glassBarOn by remember { mutableStateOf(repository.prefs.glassBarOn) }
    /* 外观模式：真值在 ThemeController 里（Activity 也要读它），这里只镜像一份给设置页显示。 */
    var themeMode by remember { mutableStateOf(repository.prefs.themeMode) }
    val glassTier = GlassCompat.tier(
        enabledByUser = glassBarOn,
        sdkInt = Build.VERSION.SDK_INT,
        backdropAllowed = GlassRuntimeGuard.isBackdropEnabled(repository.prefs),
    )
    val highTextContrast = rememberHighTextContrast()
    /* 玻璃折的那张底。先画一遍环境层再 drawContent：少了这层，内容没盖到的地方
       （比如换屏淡入的那一瞬间）会被折成透明，玻璃边缘就会发黑。 */
    val glassBackdrop = rememberLayerBackdrop {
        drawEnvironment()
        drawContent()
    }
    val glassActive = glassTier == GlassTier.BACKDROP

    /* 滚动意图：往下读的时候底栏收起成图标栏。判据是教务的 `NavScrollIntent`（阈值 40/20 不对称，
       收得难、放得容易）。挂在根 Box 上走 nested-scroll 协议，所以三块主屏的 LazyColumn
       一个都不用改。换屏就展开：新屏从收起态开始会让人以为底栏坏了。 */
    var barMinimized by remember { mutableStateOf(false) }
    val navScrollIntent = remember { NavScrollIntent() }
    /* 31/32 折射底图的新鲜度：滚动之后底图必须重拍，否则折射里是启动那一刻的画面。 */
    val lensFreshness = remember { dev.showcode.ui.glass.GlassLensFreshness() }
    val scrollDensity = LocalDensity.current
    LaunchedEffect(route) {
        barMinimized = false
        navScrollIntent.reset()
    }

    /* 主路由包一层 Box：底部导航要能叠在三个主屏之上。这一层除了给「叠」腾出位置，
       不做任何别的事——里面的分支一行没改内容。 */
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(
                /* 教务 MainActivity 的 navBarScrollConnection 同式三段：
                   ① 顶回弹（没得滚还往上拉）→ 展开并清零；② 纵轴守卫（横滑不算）；
                   ③ 意图判定只算手指驱动的滚动。 */
                remember(scrollDensity, navScrollIntent) {
                    object : NestedScrollConnection {
                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource,
                        ): Offset {
                            if (source == NestedScrollSource.UserInput) {
                                if (consumed.y == 0f && available.y > 0f) {
                                    barMinimized = false
                                    navScrollIntent.reset()
                                } else if (abs(consumed.y) > abs(consumed.x)) {
                                    navScrollIntent.scroll(consumed.y / scrollDensity.density)
                                        ?.let { barMinimized = it }
                                }
                            }
                            // 惯性滑行（source == SideEffect）也要算：手指离开后页面还在动
                            if (consumed.y != 0f) lensFreshness.onScroll()
                            return Offset.Zero
                        }
                    }
                },
            ),
    ) {
        /* 换屏分两层：三个主屏之间是**同一层连续运动**（NavigationPages，单进度、可中断接续），
           进出子屏才是层级转场——推入 22dp / 前页让 6dp / hierarchySpring 0.76-220（教务 Page 同式）。
           AnimatedContent 的 key 取「层」而不是 route：主屏互切不换层，任何子屏才真正换层。 */
        val hierarchyEnterPx = with(scrollDensity) { MotionProfile.HierarchyEnterDp.dp.roundToPx() }
        val hierarchyBehindPx = with(scrollDensity) { MotionProfile.HierarchyBehindDp.dp.roundToPx() }
        /* 共享元素转场（卡片商品图 → 详情页商品图）。
           SharedTransitionLayout 必须包住**整棵会换屏的树**：共享元素的两端都要在它的
           管辖范围内，少包一层，飞的那张图就会在到达时消失。 */
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            modifier = Modifier
                .fillMaxSize()
                /* 玻璃真正取样于这一层：没有它，底栏只是在内容上盖一层半透明白，
                   背后的卡片标题会原样压进图标里（截屏可见）。 */
                .layerBackdrop(glassBackdrop)
                /* 启动入场：只在绘制 lambda 里读 contentProgress，避免每帧重组整棵树。
                   alpha/translationY 与参考工程 MainActivity 的页面层同值（16dp）。 */
                .graphicsLayer {
                    val entry = StartupLogoAnimation.contentProgress
                    alpha = entry
                    translationY = (1f - entry) * 16.dp.toPx()
                    /* predictive back：手指往右划多少，当前页就往右退多少、缩多少。
                       和系统手势同向同量，用户看到的不是「退完之后换屏」，
                       而是「这一页正被手指拖走」。 */
                    val back = backProgress.value
                    if (back > 0f) {
                        translationX = back * size.width
                        val shrink = 1f - back * 0.06f
                        scaleX = shrink
                        scaleY = shrink
                    }
                },
            targetState = if (tabRoute != null) null else route,
            transitionSpec = {
                val forward = depthOf(targetState) >= depthOf(initialState)
                val floatSpring = MotionProfile.hierarchySpring()
                val offsetSpring = spring<IntOffset>(
                    dampingRatio = 0.76f,
                    stiffness = 220f,
                    visibilityThreshold = IntOffset.VisibilityThreshold,
                )
                if (reducedMotion) {
                    (fadeIn(snap()) togetherWith fadeOut(snap()))
                } else if (forward) {
                    // 推入：新页从右 +22dp 滑入，被盖住的前页向左让 6dp。
                    (slideInHorizontally(offsetSpring) { hierarchyEnterPx } +
                        fadeIn(floatSpring)) togetherWith
                        (slideOutHorizontally(offsetSpring) { -hierarchyBehindPx } +
                            fadeOut(floatSpring))
                } else {
                    // 弹出：顶层向右退回 +22dp，露出的前页从 -6dp 回位。
                    (slideInHorizontally(offsetSpring) { -hierarchyBehindPx } +
                        fadeIn(floatSpring)) togetherWith
                        (slideOutHorizontally(offsetSpring) { hierarchyEnterPx } +
                            fadeOut(floatSpring))
                }
            },
            label = "route",
        ) { currentRoute ->
            CompositionLocalProvider(
                LocalSharedTransition provides SharedTransitionBridge(
                    scope = this@SharedTransitionLayout,
                    visibility = this@AnimatedContent,
                    enabled = !reducedMotion,
                ),
            ) {
            if (currentRoute == null) {
                NavigationPages(state = navMotion) { page ->
                    when (page) {
                        0 -> HomeScreen(
                            state = homeState,
                            now = renderNow,
                            headerMinimized = barMinimized,
                            sampleGroups = sampleGroups,
                            isDemo = demo,
                            onExitDemo = { exitDemo() },
                            onOpenPoint = { key ->
                                openPoint(key, activeGroups.firstOrNull { it.key == key }?.name.orEmpty())
                            },
                            onOpenParcel = { parcel ->
                                parcelDetailReturn = Route.HOME
                                parcelDetailId = parcel.id
                                route = Route.PARCEL_DETAIL
                            },
                            onOpenDemo = { enterDemo() },
                            showBackgroundNote = showBackgroundNote,
                            onDismissBackgroundNote = {
                                val at = appData.lastSyncAt ?: 0L
                                backgroundNoteDismissedFor = at
                                repository.prefs.backgroundNoteDismissedForSyncAt = at
                            },
                            onOpenSettings = { route = Route.SETTINGS },
                            onOpenUnrecognized = { route = Route.UNRECOGNIZED },
                            onManualImport = {
                                manualPrefill = ""
                                manualSourceBody = null
                                manualReturn = Route.HOME
                                route = Route.MANUAL_IMPORT
                            },
                            onDismissWidgetHint = { repository.prefs.widgetHintDismissed = true },
                            onPinWidget = { requestPinWidget() },
                            onOpenAccounts = {
                                accountReturn = Route.HOME
                                route = Route.ACCOUNT_LIST
                            },
                            onOpenNotificationSettings = { openNotificationSettings() },
                            onRefresh = { if (!demo) refreshEverything() },
                            accountsSyncing = accountSyncing != null || manualSyncBusy,
                            manualSyncMessage = manualSyncMessage,
                            manualSyncFailedSignal = manualSyncFailSignal,
                            onManualSync = { refreshEverything() },
                            onForceSync = { refreshEverything(force = true) },
                            onRetryChannel = { id -> startManualSync(onlyAccountId = id) },
                            /* 演示态没有「管理这个点」：那会进真实的合并/别名流程。
                               长按菜单在这一态直接不提供。 */
                            onManagePoint = if (demo) {
                                null
                            } else {
                                { group ->
                                    detailKey = group.key
                                    route = Route.POINT_DETAIL
                                }
                            },
                            onSuspectYes = { if (!demo) suspect?.let { openMergePreview(it.first, it.second) } },
                            onSuspectNo = { if (!demo) suspect?.let { repository.dismissSuspect(it.dismissKey) } },
                            mergeUndoName = if (demo) null else lastMerge?.targetName,
                            onUndoMerge = {
                                if (!demo) {
                                    lastMerge?.let { repository.undoMerge(it.aliasKey) }
                                    lastMerge = null
                                }
                            },
                        )

                        1 -> {
                            /* 进这一格先擦掉上一轮的结局：挂着上一张那句「没打开」回到列表，
                               会被读成「这一次也没打开」——而这一次你根本还没按。 */
                            LaunchedEffect(Unit) { identityLaunch = null }
                            IdentityListScreen(
                                /* 演示态读演示码；三种写操作全部走内存分支，一行业都不进真实库。 */
                                codes = if (demo) demoIdentityCodes.toList() else appData.identityCodes,
                                // 预置那一排是固定全集，不再按装没装筛；未装的点了走既有降级链。
                                entries = DeepLinkLadder.knownEntries,
                                launch = identityLaunch,
                                onAdd = { route = Route.IDENTITY_ADD },
                                // 预置那一行不落库、不进出示屏：点一下就是那一下想要的跳转。
                                // 用途跟着这一行走：淘宝/拼多多/菜鸟是身份码，京东/小红书是订单列表。
                                onOpenEntry = { entry ->
                                    identityLaunch = repository.openOfficialApp(entry.packageName, entry.purpose)
                                },
                                onShow = { code ->
                                    identityShowId = code.id
                                    route = Route.IDENTITY_SHOW
                                },
                                onToggleDynamic = { id, dynamic ->
                                    if (demo) {
                                        DemoIdentity.setDynamic(demoIdentityCodes, id, dynamic)
                                    } else {
                                        repository.setIdentityDynamic(id, dynamic)
                                    }
                                },
                                onDelete = { id ->
                                    if (demo) {
                                        DemoIdentity.delete(demoIdentityCodes, id)
                                    } else {
                                        repository.deleteIdentityCode(id)
                                    }
                                },
                            )
                        }

                        2 -> SettingsScreen(
                            thresholds = if (demo) demoThresholds else thresholds,
                            parcels = if (demo) demoParcels else appData.parcels,
                            now = renderNow,
                            report = healthReport,
                            smsGranted = if (demo) true else smsGranted,
                            notificationEnabled = if (demo) true else notificationGranted,
                            unrecognizedCount = if (demo) 0 else unrecognizedBadgeCount(appData.unrecognized),
                            ruleCount = if (demo) 0 else appData.rules.size,
                            onOpenRules = {
                                ruleReturn = Route.SETTINGS
                                route = Route.RULES
                            },
                            notifyCount = if (demo) 0 else appData.notifyApps.size,
                            reminderOn = if (demo) false else repository.prefs.reminderOn,
                            onReminderOn = { on ->
                                if (!demo) repository.prefs.reminderOn = on
                                /* 在他**主动要这条提醒**的这一刻才问权限，不在启动时拦人。
                                   拒了也不回滚开关：那一格下面会红着一行说「没给权限它不会响」，
                                   比偷偷关掉一个他刚打开的开关诚实。 */
                                if (on && !canPostNotifications && !demo) {
                                    postNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            notificationGranted = canPostNotifications,
                            accountCount = if (demo) demoAccounts.size else accounts.size,
                            accountIssue = if (demo) false else accounts.any { it.state != AccountState.ACTIVE },
                            onOpenAccounts = {
                                accountReturn = Route.SETTINGS
                                route = Route.ACCOUNT_LIST
                            },
                            /* 开关显示的**就是**存下来的偏好：熔断那一档已经按家族的做法去掉了
                               （只取证不动作），所以不再有"开关写着开、其实没玻璃"这种状态。 */
                            themeMode = themeMode,
                            onThemeToggle = { origin ->
                                if (!demo) {
                                    /* 换主题走圆形扩散：先截图（旧画面），再切主题，
                                       然后用扩大的圆把旧图擦掉。截不到图就直接切，
                                       不播动画——换主题不能被动画绑架。 */
                                    ThemeReveal.start(
                                        view = rootView,
                                        origin = origin,
                                        reduced = reducedMotion,
                                    ) {
                                        ThemeController.toggle()
                                        themeMode = ThemeController.mode
                                        repository.prefs.themeMode = themeMode
                                    }
                                }
                            },
                            glassBarOn = glassBarOn,
                            glassBarAvailable = Build.VERSION.SDK_INT >= GlassCompat.MIN_BACKDROP_SDK,
                            onGlassBarOn = { on ->
                                if (!demo) {
                                    glassBarOn = on
                                    repository.prefs.glassBarOn = on
                                }
                            },
                            onOpenNotify = {
                                notifyReturn = Route.SETTINGS
                                route = Route.NOTIFY_APPS
                            },
                            onOpenUnrecognized = {
                                unrecognizedReturn = Route.SETTINGS
                                route = Route.UNRECOGNIZED
                            },
                            familyLabel = if (demo) "妈妈" else familyLabel,
                            onFamilyLabel = {
                                if (!demo) {
                                    repository.prefs.familyLabel = it
                                    familyLabel = repository.prefs.familyLabel
                                }
                            },
                            onLockerHours = { if (!demo) repository.setLockerDueSoonHours(it) },
                            onStationMinutes = { if (!demo) repository.setStationDueSoonMinutes(it) },
                            onRestoreDefaults = { if (!demo) repository.restoreThresholdDefaults() },
                            onRequestSms = { if (!demo) requestSmsPermission() },
                            onOpenNotificationSettings = { if (!demo) openNotificationSettings() },
                            onOpenAppDetails = { if (!demo) openAppDetails() },
                            isDemo = demo,
                            onExitDemo = { exitDemo() },
                            onStartDemo = { enterDemo() },
                        )
                    }
                }
            } else {
                when (currentRoute) {
                    Route.ONSITE -> {
                        val group = activeGroups.firstOrNull { it.key == pointKey }
                        OnsiteScreen(
                            pointKey = pointKey,
                            pointName = pointName,
                            pointKind = group?.kind,
                            items = group?.items.orEmpty(),
                            now = renderNow,
                            isDemo = demo,
                            focusParcelId = focusParcelId,
                            lastPickupAt = if (demo) demoNow else appData.lastPickupAt,
                            allPickedHere = if (demo) {
                                demoParcels.any { it.pointKey == pointKey && demoPicked.contains(it.id) }
                            } else {
                                appData.parcels.any { it.pointKey == pointKey && it.picked }
                            },
                            nextStop = nextStop,
                            onBack = { route = Route.HOME },
                            onExitDemo = { exitDemo() },
                            onOpenTiles = { currentId ->
                                focusParcelId = currentId
                                route = Route.TILES
                            },
                            familyLabel = familyLabel,
                            onMarkPicked = { parcel ->
                                if (demo) demoPicked.add(parcel.id) else repository.markPicked(parcel.id)
                            },
                            onUndoPicked = { parcel ->
                                if (demo) demoPicked.remove(parcel.id) else repository.undoPicked(parcel.id)
                            },
                            onOpenNextPoint = { stop -> openPoint(stop.key, stop.name) },
                            onRefresh = { if (!demo) repository.refreshNow() },
                            /* 只走淘宝自己：没装时不要落到浏览器兜底（那条 tbopen 深链会被
                               Chrome 接住），而是让现场体给「已复制单号，去淘宝粘贴搜索」。 */
                            onOpenTaobao = {
                                if (demo) dev.showcode.core.LaunchOutcome.Opened
                                else repository.openTaobaoLookup()
                            },
                            onOpenParcelDetail = { parcel ->
                                parcelDetailReturn = Route.ONSITE
                                parcelDetailId = parcel.id
                                route = Route.PARCEL_DETAIL
                            },
                        )
                    }

                    Route.TILES -> {
                        val group = activeGroups.firstOrNull { it.key == pointKey }
                        TilesScreen(
                            pointName = pointName,
                            items = group?.items.orEmpty(),
                            isDemo = demo,
                            onBack = { route = Route.ONSITE },
                            onPick = { parcel ->
                                focusParcelId = parcel.id
                                route = Route.ONSITE
                            },
                        )
                    }


                    Route.UNRECOGNIZED -> UnrecognizedScreen(
                        messages = appData.unrecognized,
                        mutedSenders = appData.mutedSenders,
                        now = now,
                        onBack = { route = unrecognizedReturn },
                        onExtract = { message ->
                            manualPrefill = message.body
                            manualSourceBody = message.body
                            manualReturn = Route.UNRECOGNIZED
                            route = Route.MANUAL_IMPORT
                        },
                        onDismiss = { message -> repository.dismissUnrecognized(message.id) },
                        onMuteSender = { message -> repository.muteSender(message.sender) },
                        onUnmuteSender = { key -> repository.unmuteSender(key) },
                        onWriteRule = { message ->
                            rulePrefillRaw = message.body
                            ruleReturn = Route.UNRECOGNIZED
                            route = Route.RULE_EDIT
                        },
                    )

                    Route.RULES -> RuleListScreen(
                        rules = appData.rules,
                        onBack = { route = ruleReturn },
                        onAdd = {
                            rulePrefillRaw = ""
                            route = Route.RULE_EDIT
                        },
                        onToggle = { id, enabled -> repository.setRuleEnabled(id, enabled) },
                        onDelete = { id -> repository.deleteParseRule(id) },
                    )

                    Route.RULE_EDIT -> RuleEditScreen(
                        prefillRaw = rulePrefillRaw,
                        now = now,
                        onBack = { route = Route.RULES },
                        onCheck = { pattern, pointName, sample ->
                            repository.checkRule(pattern, pointName, sample, appData.rules.size)
                        },
                        onSave = { pattern, pointName, lockerNo -> repository.addParseRule(pattern, pointName, lockerNo) },
                    )

                    Route.IDENTITY_ADD -> IdentityEditScreen(
                        existingCount = if (demo) demoIdentityCodes.size else appData.identityCodes.size,
                        apps = installedApps,
                        onBack = { route = Route.IDENTITY_LIST },
                        onSave = { label, value, kind, dynamic, note, appPackage ->
                            if (demo) {
                                /* 演示态落内存、id 取负数；校验已经在编辑屏里跑过一遍。 */
                                DemoIdentity.add(
                                    demoIdentityCodes, label, value, kind, dynamic, note, appPackage, demoNow,
                                )
                            } else {
                                repository.addIdentityCode(label, value, kind, dynamic, note, appPackage)
                            }
                        },
                    )

                    Route.IDENTITY_SHOW -> {
                        /* 按 id 现取。那张码可能刚被删掉，这时候必须退回去，而不是继续出示一张
                           已经不存在的码——它屏上看起来仍然完全正常，而那正是最坏的失效。 */
                        val codes = if (demo) demoIdentityCodes else appData.identityCodes
                        val code = codes.firstOrNull { it.id == identityShowId }
                        if (code == null) {
                            LaunchedEffect(Unit) { route = Route.IDENTITY_LIST }
                        } else {
                            LaunchedEffect(code.id) { identityLaunch = null }
                            IdentityShowScreen(
                                code = code,
                                launch = identityLaunch,
                                onBack = { route = Route.IDENTITY_LIST },
                                onOpenApp = { openOfficialApp(code) },
                            )
                        }
                    }

                    Route.NOTIFY_APPS -> NotifyAppsScreen(
                        apps = appData.notifyApps,
                        now = now,
                        onBack = { route = notifyReturn },
                        onToggle = { pkg, disabled -> repository.setNotifyAppDisabled(pkg, disabled) },
                    )

                    Route.ACCOUNT_LIST -> AccountListScreen(
                        accounts = if (demo) demoAccounts else accounts,
                        onBack = { route = accountReturn },
                        onLogin = { platform ->
                            if (!demo) {
                                /* 进入登录路由先停掉正在跑的抓取：它清理 Cookie 的时机
                                   不能落在用户刚拿到登录会话之后。 */
                                accountSyncJob?.cancel()
                                accountSyncJob = null
                                accountSyncing = null
                                manualSyncBusy = false
                                loginPlatformId = platform.id
                                route = Route.ACCOUNT_LOGIN
                            }
                        },
                        onToggle = { account, enabled ->
                            if (!demo) {
                                repository.accounts.setEnabled(account, enabled)
                                accounts = repository.accounts.list()
                            }
                        },
                        onDelete = { account ->
                            if (!demo) {
                                repository.accounts.remove(account)
                                accounts = repository.accounts.list()
                            }
                        },
                        syncing = if (demo) null else accountSyncing,
                        syncMessage = if (demo) null else accountSyncMessage,
                        onSync = { account ->
                            if (!demo && accountSyncing == null) {
                                accountSyncing = account.id
                                accountSyncMessage = null
                                accountScope.launch {
                                    val result = AccountSyncEngine.sync(
                                        context as? android.app.Activity,
                                        repository,
                                        account,
                                    )
                                    accountSyncing = null
                                    /* 账号表读在 IO；失败原因也在这份新快照里取，主线程不做 DB 读。 */
                                    val fresh = withContext(Dispatchers.IO) { repository.accounts.list() }
                                    accounts = fresh
                                    accountSyncMessage = when {
                                        /* 失败原因已经写进账号状态里；直接把它抬到这条消息上，
                                           别让用户只看到一句「没同步上」却不知道要不要重新登录。 */
                                        result == null -> fresh.firstOrNull { it.id == account.id }
                                            ?.lastSyncResult
                                            ?: "这次没同步上"
                                        result.added == 0 && result.updated == 0 -> "同步成功 · 没有新件"
                                        else -> "新增 ${result.added} · 更新 ${result.updated}"
                                    }
                                }
                            }
                        },
                        onDebugImport = if (BuildConfig.DEBUG && !demo) {
                            {
                                val cookie = android.webkit.CookieManager.getInstance()
                                    .getCookie(Platform.PDD.cookieHost).orEmpty()
                                if (dev.showcode.core.account.Session.parse(cookie).has(Platform.PDD)) {
                                    /* 调试入口也要走兜底：加密失败只是不绑定，不崩。 */
                                    if (repository.accounts.bind(Platform.PDD, null, cookie)
                                            is dev.showcode.data.AccountStore.BindResult.Ok
                                    ) {
                                        accounts = repository.accounts.list()
                                    }
                                }
                            }
                        } else {
                            null
                        },
                    )

                    Route.ACCOUNT_LOGIN -> {
                        val platform = Platform.of(loginPlatformId) ?: Platform.PDD
                        PlatformLoginScreen(
                            platform = platform,
                            onBack = { route = Route.ACCOUNT_LIST },
                            onBound = { cookie ->
                                /* 加密/写库都下 IO；成功才换路由。失败返回 false，
                                   登录页留在原地给可重试提示，既有账号状态不动。 */
                                val saved = withContext(Dispatchers.IO) {
                                    when (val bind = repository.accounts.bind(platform, null, cookie)) {
                                        is dev.showcode.data.AccountStore.BindResult.Ok ->
                                            repository.accounts.list() to true
                                        dev.showcode.data.AccountStore.BindResult.Failed ->
                                            emptyList<dev.showcode.core.account.BoundAccount>() to false
                                    }
                                }
                                if (saved.second) {
                                    accounts = saved.first
                                    route = Route.ACCOUNT_LIST
                                }
                                saved.second
                            },
                        )
                    }

                    Route.PARCEL_DETAIL -> {
                        /* 演示态从独立内存集取件，绝不回落到真实包裹表。 */
                        val parcel = if (demo) {
                            demoParcels.firstOrNull { it.id == parcelDetailId }
                        } else {
                            appData.parcels.firstOrNull { it.id == parcelDetailId }
                        }
                        ParcelDetailScreen(
                            parcel = parcel,
                            onBack = { route = parcelDetailReturn },
                            onOpenTaobao = {
                                if (demo) dev.showcode.core.LaunchOutcome.Opened
                                else repository.openTaobaoLookup()
                            },
                            isDemo = demo,
                        )
                    }

                    Route.MANUAL_IMPORT -> ManualImportScreen(
                        prefillRaw = manualPrefill,
                        knownBodies = knownBodies,
                        // 芯片只有四个位置，最常用的那个必须在第一个：按该点现有件数排。
                        knownPoints = appData.parcels.groupingBy { it.pointName }.eachCount()
                            .entries.sortedByDescending { it.value }.map { it.key },
                        onBack = { route = manualReturn },
                        onSave = { code, name, locker, raw, owner ->
                            val stored = repository.manualAdd(code, name, locker, raw, owner, resolvesUnrecognizedOf = manualSourceBody)
                            if (stored) {
                                manualSourceBody = null
                                manualPrefill = ""
                                manualReturn = Route.HOME
                                route = Route.HOME
                            }
                            // 失败时留在原地：用户已经打好的字不能被清掉，那比一句报错重要。
                            stored
                        },
                    )

                    Route.POINT_DETAIL -> {
                        val group = realGroups.firstOrNull { it.key == detailKey }
                        val history = remember(detailKey, appData.aliases) { repository.mergeHistoryFor(detailKey) }
                        PointDetailScreen(
                            group = group,
                            history = history,
                            otherGroups = realGroups,
                            aliases = appData.aliases,
                            now = now,
                            onBack = { route = Route.HOME },
                            onOpenPoint = { g -> openPoint(g.key, g.name) },
                            onSplit = { alias -> repository.undoMerge(alias.aliasKey) },
                            onSetOwner = { parcel, owner -> repository.setParcelOwner(parcel.id, owner) },
                            onMergeOtherIntoThis = { other ->
                                group?.let { openMergePreview(other, it, preferTarget = it) }
                            },
                        )
                    }

                    Route.MERGE_PREVIEW -> pendingMerge?.let { preview ->
                        MergePreviewScreen(
                            preview = preview,
                            keepTarget = keepTarget,
                            onKeepTarget = { keepTarget = it },
                            onConfirm = { confirmMerge() },
                            onCancel = { pendingMerge = null; route = mergeReturn },
                        )
                    }

                    Route.ONBOARDING -> OnboardingScreen(
                        step = onboardStep,
                        accountCount = accounts.size,
                        onStep = { onboardStep = it },
                        onEnterDemo = { enterDemo() },
                        onOpenAccounts = {
                            accountReturn = Route.ONBOARDING
                            route = Route.ACCOUNT_LIST
                        },
                        onFinish = {
                            repository.prefs.onboardingDone = true
                            onboardStep = OnboardStep.WELCOME
                            route = Route.HOME
                        },
                    )

                    // 三个主屏永远在 NavigationPages 那一层，这里不会到达。
                    else -> Unit
                }
            }
            }
        }

        /* 三个主屏共用的底栏。子屏不画它：现场模式那一屏只该有那张码和那颗按钮，一排键摆在那儿
           是挡道的；引导屏也没有「另一个去处」可言。 */
        if (route == Route.HOME || route == Route.IDENTITY_LIST || route == Route.SETTINGS) {
            /* 导航运动状态经 CompositionLocal 发下去：底栏松手时要把位置与速度播种进同一发条。
               「页面与胶囊是同一个动作」就是在这里握的手（教务同款接线）。 */
            CompositionLocalProvider(LocalNavigationMotion provides navMotion) {
                ShowCodeNavBar(
                    current = when (route) {
                        Route.IDENTITY_LIST -> NavBarTab.IDENTITY
                        Route.SETTINGS -> NavBarTab.SETTINGS
                        else -> NavBarTab.HOME
                    },
                    onHome = { route = Route.HOME },
                    onIdentity = { route = Route.IDENTITY_LIST },
                    onSettings = { route = Route.SETTINGS },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        /* 底栏比页面晚 15% 入场、位移 12dp；只加绘制层，不改底栏内部。 */
                        .graphicsLayer {
                            val entry = StartupChoreography.navEntry(StartupLogoAnimation.contentProgress)
                            alpha = entry
                            translationY = (1f - entry) * 12.dp.toPx()
                        },
                    minimized = barMinimized,
                    onExpandRequest = { barMinimized = false },
                    tier = glassTier,
                    backdrop = glassBackdrop,
                    highTextContrast = highTextContrast,
                    lensFreshness = lensFreshness,
                )
            }
        }

        /* 换主题的圆形扩散：**最上层**，连底栏一起盖住。
           它画的是切换**之前**截下来的那张图，所以必须压在所有内容之上——
           少盖一层，那一层就会提前变成新主题，扩散看上去像是漏了一块。 */
        ThemeRevealOverlay(modifier = Modifier.fillMaxSize())
        }
    }

}

