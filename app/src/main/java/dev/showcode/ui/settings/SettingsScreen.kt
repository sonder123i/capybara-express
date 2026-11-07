package dev.showcode.ui.settings

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.showcode.BuildConfig
import dev.showcode.R
import dev.showcode.core.HOUR_MS
import dev.showcode.core.HealthIssue
import dev.showcode.core.HealthReport
import dev.showcode.core.Parcel
import dev.showcode.core.Urgency
import dev.showcode.core.UrgencyThresholds
import dev.showcode.core.urgencyOf
import dev.showcode.data.Prefs
import dev.showcode.data.update.UpdateChecker
import dev.showcode.data.update.UpdateInfo
import dev.showcode.data.update.UpdateInstaller
import dev.showcode.data.update.UpdateResult
import androidx.compose.material3.Switch
import dev.showcode.ui.navBarClearance
import dev.showcode.ui.inbox.ScreenTopBar
import dev.showcode.ui.theme.AccentInk
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.InkDisabled
import dev.showcode.ui.theme.ShowCodePaletteState
import dev.showcode.ui.theme.ThemeToggle
import dev.showcode.ui.theme.environmentBackground
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.moduleEntrance
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.SurfaceSunken
import dev.showcode.ui.theme.UrgencyAtRisk
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencyDueSoonTint
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.showcode.ui.theme.UrgencySafe
import kotlin.math.roundToInt

/**
 * 设置页（edge s9-settings）。两件事住在这里：
 * 1. 两个临期阈值滑块 + 实时预览——不给预览的话用户无法理解 6 小时和 4 小时的差别，
 *    这个设置项等于没有（edge 原话）。顺带让用户自己校准 frame 标注的「默认值是推演的」。
 * 2. 权限体检表——story-5 的完整视图，首页横条只报最严重一项，这里逐项给修复入口。
 */
@Composable
fun SettingsScreen(
    thresholds: UrgencyThresholds,
    parcels: List<Parcel>,
    now: Long,
    report: HealthReport,
    smsGranted: Boolean,
    notificationEnabled: Boolean,
    unrecognizedCount: Int,
    /** 已写下的解析规则条数。0 条时入口仍然要给——不然没人知道有这条路。 */
    ruleCount: Int,
    onOpenRules: () -> Unit,
    /** 推过通知的 App 个数。0 个也要有入口：那一屏的空态本身是诊断信息。 */
    notifyCount: Int,
    onOpenNotify: () -> Unit,
    /** 巡检提醒的开关。它离「会响」还差两道：证实过盲区 + 通知权限已给。 */
    reminderOn: Boolean,
    onReminderOn: (Boolean) -> Unit,
    notificationGranted: Boolean,
    /** 已绑定的平台账号数。0 也要显示入口：那一格本身就是「还能这么用」的说明。 */
    accountCount: Int,
    /** 有账号登录失效或凭据解不开。 */
    accountIssue: Boolean,
    onOpenAccounts: () -> Unit,
    /** 代取件分组的名字（story-7 AC2 要求用户能改）。 */
    familyLabel: String,
    onFamilyLabel: (String) -> Unit,
    onOpenUnrecognized: () -> Unit,
    onLockerHours: (Int) -> Unit,
    onStationMinutes: (Int) -> Unit,
    onRestoreDefaults: () -> Unit,
    onRequestSms: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
    /** 外观模式（跟随系统 / 亮 / 暗）。开关是二元的，三档偏好留给以后。 */
    themeMode: dev.showcode.core.ThemeMode = dev.showcode.core.ThemeMode.SYSTEM,
    onThemeToggle: () -> Unit = {},
    /** 底栏玻璃效果的开关。 */
    glassBarOn: Boolean,
    /** 这台设备的平台能不能出模糊（API 31+）。不能就当场说清楚，别让那颗开关看起来是坏的。 */
    glassBarAvailable: Boolean,
    onGlassBarOn: (Boolean) -> Unit,
    /** 宣传片录制态：权限体检、提醒与文本输入都不出现，避免任何系统面板。 */
    isDemo: Boolean = false,
    onExitDemo: () -> Unit = {},
    /** 真实态的一键入口：固定示例数据、本地图、不碰任何权限与真实账号。 */
    onStartDemo: () -> Unit = {},
) {
    var familyLabelDraft by remember { mutableStateOf(familyLabel) }
    val dueSoon = parcels.filter { !it.picked && urgencyOf(it, now, thresholds) == Urgency.DUE_SOON }

    /* ── 更新：只有用户按「检查更新」时才会访问网络 ─────────────────── */
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }

    fun checkUpdate() {
        if (updateState is UpdateUi.Checking) return
        updateState = UpdateUi.Checking
        scope.launch {
            updateState = when (val result = UpdateChecker.check(BuildConfig.VERSION_NAME)) {
                UpdateResult.UpToDate -> UpdateUi.UpToDate
                UpdateResult.NoRelease -> UpdateUi.NoRelease
                is UpdateResult.Failed -> UpdateUi.Failed(result.reason)
                is UpdateResult.Available -> UpdateUi.Available(result.info)
            }
        }
    }

    fun download(info: UpdateInfo) {
        if (info.sha256.isNullOrBlank()) {
            openUpdatePage(context, info.pageUrl)
            return
        }
        val id = UpdateInstaller.enqueue(context, info)
        updateState = if (id != null) {
            UpdateUi.Available(info, downloading = id)
        } else {
            UpdateUi.Failed("下载没起来，稍后再试")
        }
    }

    fun install(file: File) {
        when (val result = UpdateInstaller.install(context, file)) {
            UpdateInstaller.Install.Started -> Unit
            UpdateInstaller.Install.NeedsPermission -> {
                context.startActivity(UpdateInstaller.permissionSettings(context))
                updateState = UpdateUi.Failed("允许「安装未知应用」后再点一次安装")
            }
            is UpdateInstaller.Install.Failed -> updateState = UpdateUi.Failed(result.reason)
        }
    }

    /* 下载在系统里跑，回到这一屏时看结果；下载完成后也算一遍 SHA-256，不对就删。 */
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                val current = updateState
                if (current !is UpdateUi.Available || current.downloading != id) return
                scope.launch {
                    val file = UpdateInstaller.fileFor(context, current.info.versionName)
                    val ok = withContext(Dispatchers.IO) {
                        UpdateInstaller.verify(file, current.info.sha256)
                    }
                    updateState = if (ok) {
                        current.copy(downloaded = file, downloading = null)
                    } else {
                        runCatching { file.delete() }
                        UpdateUi.Failed("校验失败，已删除下载")
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    /* 上一次已经下好、用户这次才回来：不再重复下载。 */
    LaunchedEffect(updateState) {
        val current = updateState as? UpdateUi.Available ?: return@LaunchedEffect
        if (current.downloaded != null || current.downloading != null) return@LaunchedEffect
        val file = UpdateInstaller.fileFor(context, current.info.versionName)
        if (!file.exists()) return@LaunchedEffect
        val ok = withContext(Dispatchers.IO) {
            UpdateInstaller.verify(file, current.info.sha256)
        }
        if (ok) {
            updateState = current.copy(downloaded = file)
        } else {
            runCatching { file.delete() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .environmentBackground()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        ScreenTopBar(title = stringResource(R.string.settings_title))

        if (isDemo) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.demo_home_marker),
                    modifier = Modifier.weight(1f),
                    style = ShowCodeText.Meta,
                    color = InkMuted,
                )
                Text(
                    text = stringResource(R.string.demo_exit),
                    modifier = Modifier
                        .clickable(onClick = onExitDemo)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    style = ShowCodeText.Action,
                    color = AccentInk,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = navBarClearance()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            /* 平台账号是这一页唯一会「带进新件」的入口，放第一组；
               后面的阈值、分组名、规则都只影响已收录的件。 */
            item {
                Box(modifier = Modifier.moduleEntrance(0)) {
                    SectionTitle(stringResource(R.string.settings_accounts_section))
                }
            }
            /* 收件来源：四行是同一件事——「件从哪来」。以前是四张各自浮着的卡，
               扫一眼要先数卡片；现在一张卡里四行，像一份设置，而不是一叠传单。 */
            item {
                Box(modifier = Modifier.moduleEntrance(0)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsCard()
                            .border(1.dp, Hairline, ShowCodeShapes.Md),
                    ) {
                        SettingsNavRow(
                            title = stringResource(R.string.settings_accounts),
                            value = when {
                                accountIssue -> stringResource(R.string.settings_accounts_issue)
                                accountCount > 0 -> stringResource(R.string.settings_accounts_count, accountCount)
                                else -> stringResource(R.string.settings_accounts_none)
                            },
                            emphasize = accountIssue || accountCount > 0,
                            warning = accountIssue,
                            onClick = onOpenAccounts,
                        )
                        /* 演示态只留平台账号：其余三行会引出真实数据或系统设置。 */
                        if (!isDemo) {
                            SettingsRowDivider()
                            SettingsNavRow(
                                title = stringResource(R.string.unrecognized_title),
                                value = unrecognizedCount.toString(),
                                emphasize = unrecognizedCount > 0,
                                warning = unrecognizedCount > 0,
                                onClick = onOpenUnrecognized,
                            )
                            SettingsRowDivider()
                            SettingsNavRow(
                                title = stringResource(R.string.settings_rules),
                                value = stringResource(R.string.settings_rules_count, ruleCount),
                                emphasize = ruleCount > 0,
                                onClick = onOpenRules,
                            )
                            SettingsRowDivider()
                            SettingsNavRow(
                                title = stringResource(R.string.settings_notify),
                                value = stringResource(R.string.settings_notify_count, notifyCount),
                                emphasize = notifyCount > 0,
                                onClick = onOpenNotify,
                            )
                        }
                    }
                }
            }

            item {
                Box(modifier = Modifier.moduleEntrance(0)) {
                    ThresholdCard(
                        lockerHours = (thresholds.lockerDueSoonMs / HOUR_MS).toInt(),
                        stationMinutes = thresholds.stationDueSoonMinutes,
                        previewText = if (dueSoon.isEmpty()) {
                            stringResource(R.string.settings_preview_none)
                        } else {
                            stringResource(
                                R.string.settings_preview_some,
                                dueSoon.size,
                                dueSoon.joinToString("、") { it.code },
                            )
                        },
                        previewIsWarning = dueSoon.isNotEmpty(),
                        onLockerHours = onLockerHours,
                        onStationMinutes = onStationMinutes,
                        onRestoreDefaults = onRestoreDefaults,
                    )
                }
            }

            /* 演示态只保留能安全展示的项：账号、阈值、外观、权限体检。
               提醒、通知来源、规则、收容页都会引出真实数据或系统设置，
               家人分组名还会弹软键盘——录制时一律不出现。 */
            if (!isDemo) {
            /* 分组名可改：写死「帮妈妈取」等于替用户决定他的家庭结构。
               输入框用本地草稿状态，否则清空时会回落到默认值、把正在编辑的那一格弹走。 */
            item {
                Box(modifier = Modifier.moduleEntrance(1)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsCard()
                            .border(1.dp, Hairline, ShowCodeShapes.Md)
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_family_label),
                                style = ShowCodeText.Action,
                                color = InkMuted,
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(12.dp))
                            /* 行内输入：32dp 高的下沉面 + 1dp 基线，行高仍是 48dp 触控。
                               不再用 56dp 的 OutlinedTextField，省下的就是它上下两圈内衬。 */
                            BasicTextField(
                                value = familyLabelDraft,
                                onValueChange = { familyLabelDraft = it; onFamilyLabel(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(SurfaceSunken, ShowCodeShapes.Sm)
                                    .padding(horizontal = 10.dp),
                                singleLine = true,
                                textStyle = ShowCodeText.Item,
                                cursorBrush = SolidColor(AccentInk),
                                decorationBox = { inner ->
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                        inner()
                                    }
                                },
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = InkMuted,
                            )
                        }
                    }
                }
            }

            /* 巡检提醒的开关一直显示，不因为「还没证实盲区」就藏起来：
               想提前关掉的人必须找得到它，而找不到一个开关比看见一个暂时不响的开关更让人不安。 */
            item {
                Box(modifier = Modifier.moduleEntrance(2)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsCard()
                            .border(1.dp, Hairline, ShowCodeShapes.Md)
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.settings_reminder),
                                modifier = Modifier.weight(1f),
                                style = ShowCodeText.Body,
                                color = Ink,
                            )
                            Switch(checked = reminderOn, onCheckedChange = onReminderOn)
                        }
                        /* 只在开关打开时说一句「它现在还不会响」的原因。
                           关着的时候解释这些，是在给一个不问的人讲规则。 */
                        if (reminderOn) {
                            Spacer(Modifier.height(4.dp))
                            val needsPermission = !notificationGranted
                            Text(
                                text = stringResource(
                                    if (needsPermission) {
                                        R.string.settings_reminder_needs_permission
                                    } else {
                                        R.string.settings_reminder_state
                                    }
                                ),
                                modifier = if (needsPermission) {
                                    Modifier
                                        .background(UrgencyDueSoonTint, ShowCodeShapes.Sm)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                } else {
                                    Modifier
                                },
                                style = ShowCodeText.Caption,
                                color = if (needsPermission) Ink else InkMuted,
                            )
                        }
                    }
                }
            }

            }

            /* 权限体检在演示里也照常出现：它是这台 App 的一种真实能力，
               只是把状态换成演示值（已开启 / 全部正常），不读也不改系统设置。 */
            item {
                Box(modifier = Modifier.moduleEntrance(3)) {
                    SectionTitle(stringResource(R.string.settings_health_section))
                }
            }
            item {
                Box(modifier = Modifier.moduleEntrance(3)) {
                    HealthTable(
                        report = if (isDemo) HealthReport(emptyList(), emptyList()) else report,
                        smsGranted = smsGranted,
                        notificationEnabled = notificationEnabled,
                        onRequestSms = onRequestSms,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onOpenAppDetails = onOpenAppDetails,
                    )
                }
            }

            item {
                Box(modifier = Modifier.moduleEntrance(3)) {
                    SectionTitle(stringResource(R.string.settings_appearance_section))
                }
            }
            /* 开关一直显示，和上面那条提醒同一个理由：想关掉它的人必须找得到。
               但「开着却没有玻璃」要当场说明白——那不是开关坏了，是这台设备、
               或者上一次崩溃留下的结论（GlassRuntimeGuard）。 */
            /* 亮暗开关排在外观第一格：它是这一屏唯一「一眼看得出效果」的设置。 */
            item {
                Box(modifier = Modifier.moduleEntrance(3)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsCard()
                            .border(1.dp, Hairline, ShowCodeShapes.Md)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_theme),
                                style = ShowCodeText.Body,
                                color = Ink,
                            )
                            Text(
                                text = stringResource(
                                    when (themeMode) {
                                        dev.showcode.core.ThemeMode.SYSTEM ->
                                            R.string.settings_theme_system
                                        dev.showcode.core.ThemeMode.LIGHT ->
                                            R.string.settings_theme_light
                                        dev.showcode.core.ThemeMode.DARK ->
                                            R.string.settings_theme_dark
                                    }
                                ),
                                style = ShowCodeText.Caption,
                                color = InkMuted,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        ThemeToggle(
                            dark = ShowCodePaletteState.isDark,
                            onToggle = onThemeToggle,
                        )
                    }
                }
            }
            item {
                Box(modifier = Modifier.moduleEntrance(3)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsCard()
                            .border(1.dp, Hairline, ShowCodeShapes.Md)
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.settings_glass),
                                modifier = Modifier.weight(1f),
                                style = ShowCodeText.Body,
                                color = Ink,
                            )
                            Switch(checked = glassBarOn, onCheckedChange = onGlassBarOn)
                        }
                        /* 开着却没有玻璃时只说原因：这不是开关坏了，是这台设备做不到。 */
                        if (!glassBarAvailable) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.settings_glass_needs_os),
                                style = ShowCodeText.Caption,
                                color = InkMuted,
                            )
                        }
                    }
                }
            }

            /* 关于：版本 + 手动检查更新。只有按下的那一下才会访问 Gitee / GitHub。 */
            if (!isDemo) {
                item {
                    Box(modifier = Modifier.moduleEntrance(3)) {
                        SectionTitle(stringResource(R.string.settings_about_section))
                    }
                }
                item {
                    Box(modifier = Modifier.moduleEntrance(3)) {
                        AboutCard(
                            state = updateState,
                            onCheck = ::checkUpdate,
                            onDownload = ::download,
                            onInstall = ::install,
                            onOpenPage = { openUpdatePage(context, it) },
                            onDismiss = { updateState = UpdateUi.Idle },
                        )
                    }
                }
            }

            /* 宣传片录制的一键入口。放在最后，不抢真实设置的主路径。 */
            if (!isDemo) {
                item {
                    Box(modifier = Modifier.moduleEntrance(3)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingsCard()
                                .border(1.dp, Hairline, ShowCodeShapes.Md)
                                .clickable(onClick = onStartDemo)
                                .padding(16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.demo_entry),
                                style = ShowCodeText.Body,
                                color = Ink,
                            )
                        }
                    }
                }
            }

            /* 隐私承诺只说一次，放在最末：不再复制到每张卡里。 */
            item {
                Box(modifier = Modifier.moduleEntrance(3)) {
                    Text(
                        text = stringResource(R.string.settings_privacy_note),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        style = ShowCodeText.Caption,
                        color = InkMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 8.dp),
        style = ShowCodeText.Action,
        color = InkMuted,
    )
}

/**
 * 一张组卡里的一行：左边是什么，右边是现在的值。
 *
 * 组卡取代了「一行一张浮卡」：四张圆角卡并排时，眼睛先看到的是四个轮廓，
 * 而不是四行内容。值的那一列只用颜色表意——灰=没有，墨=有，浅橙=要处理。
 */
@Composable
private fun SettingsNavRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    emphasize: Boolean = false,
    warning: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = ShowCodeText.Body,
            color = Ink,
        )
        Text(
            text = value,
            modifier = if (warning) {
                Modifier
                    .background(UrgencyDueSoonTint, ShowCodeShapes.Sm)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            } else {
                Modifier
            },
            style = ShowCodeText.Item,
            color = if (emphasize) Ink else InkMuted,
        )
    }
}

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = Hairline,
    )
}

/**
 * 设置页卡片的「白打底」：亮色 SurfaceCard 近白（#F9FAFD），暗色对应深面（#1D222B）。
 * 1dp 轻投影只负责把卡片从页面渐变上托起来，暗色下几乎不可见；不改任何布局高度。
 */
private fun Modifier.settingsCard(): Modifier = this
    .shadow(elevation = 1.dp, shape = ShowCodeShapes.Md, clip = false)
    .background(SurfaceCard, ShowCodeShapes.Md)

/**
 * 阈值卡：从「两条 Material Slider + 独立预览块 + 独立恢复链接」压成一张「台账」。
 *
 * 省空间的三处，都不动字号：
 * 1. 标题「临期阈值」和「恢复默认」合并进卡头一行（原先是卡外标题 + 卡内底部链接）；
 * 2. 滑块换成 48dp 高的档位步进器（`− 值 ＋`）：标签 13sp 在左、值 15sp SemiBold 在右，
 *    每行 48dp（原来 label 22 + Slider 48 + 14 间距 ≈ 84dp）；
 * 3. 预览从「12dp 内衬的独立色块」改成一条 32dp、上缘 1dp Hairline 的轻分隔带。
 */
@Composable
private fun ThresholdCard(
    lockerHours: Int,
    stationMinutes: Int,
    previewText: String,
    previewIsWarning: Boolean,
    onLockerHours: (Int) -> Unit,
    onStationMinutes: (Int) -> Unit,
    onRestoreDefaults: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .settingsCard()
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_threshold_section),
                modifier = Modifier.weight(1f),
                style = ShowCodeText.Action,
                color = InkMuted,
            )
            Box(
                modifier = Modifier
                    .widthIn(min = 48.dp)
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onRestoreDefaults),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.settings_restore),
                    style = ShowCodeText.Action,
                    color = AccentInk,
                )
            }
        }

        StepRow(
            title = stringResource(R.string.settings_locker_title),
            value = stringResource(R.string.settings_locker_value, lockerHours),
            options = Prefs.LOCKER_OPTIONS,
            current = lockerHours,
            onSelect = onLockerHours,
        )
        /* 行与行只隔 1dp Hairline（参考的白卡就是这样切行），不靠加间距。 */
        HorizontalDivider(thickness = 1.dp, color = Hairline)
        StepRow(
            title = stringResource(R.string.settings_station_title),
            value = stringResource(R.string.settings_station_value, stationMinutes),
            options = Prefs.STATION_OPTIONS,
            current = stationMinutes,
            onSelect = onStationMinutes,
        )

        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp),
            thickness = 1.dp,
            color = Hairline,
        )
        // 实时预览：阈值一改，这一行立刻告诉你哪几件跨进了临期
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                /* 白卡里嵌一条浅橙带：颜色留在底上，字用 Ink，12sp 也能过 4.5:1。 */
                .then(
                    if (previewIsWarning) {
                        Modifier.background(UrgencyDueSoonTint, ShowCodeShapes.Sm)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = if (previewIsWarning) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = previewText,
                style = ShowCodeText.Caption,
                color = if (previewIsWarning) Ink else InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 一行阈值：13sp 标签 + 15sp SemiBold 值 + 两个 48dp 步进按钮。档位仍是离散集合。 */
@Composable
private fun StepRow(
    title: String,
    value: String,
    options: List<Int>,
    current: Int,
    onSelect: (Int) -> Unit,
) {
    val index = options.indexOf(current).coerceAtLeast(0)
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = ShowCodeText.Action,
            color = InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StepButton(
            minus = true,
            enabled = index > 0,
            label = stringResource(R.string.settings_threshold_decrease, title),
        ) { onSelect(options[(index - 1).coerceAtLeast(0)]) }
        Text(
            text = value,
            modifier = Modifier.widthIn(min = 62.dp),
            style = ShowCodeText.Value,
            color = Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        StepButton(
            minus = false,
            enabled = index < options.lastIndex,
            label = stringResource(R.string.settings_threshold_increase, title),
        ) { onSelect(options[(index + 1).coerceAtMost(options.lastIndex)]) }
    }
}

@Composable
private fun StepButton(minus: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(28.dp).border(1.dp, Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (minus) "−" else "+",
                style = ShowCodeText.Glyph,
                color = if (enabled) Ink else InkDisabled,
            )
        }
    }
}

@Composable
private fun HealthTable(
    report: HealthReport,
    smsGranted: Boolean,
    notificationEnabled: Boolean,
    onRequestSms: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .settingsCard()
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        /* 「全部正常」不能盖住一条「未开启」：通知监听没开时自动收录不完整，
           即使它只是可选补充手段，也不该和一句「全部正常」同时出现。 */
        if (report.allGood && notificationEnabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = UrgencySafe)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_health_all_good),
                    style = ShowCodeText.Item,
                    color = Ink,
                )
            }
        }

        /* 短信是补充手段：未开启用中性灰，不用警示色，也不催「必须补上」。
           开着时不再解释它覆盖谁——那句话属于账号页，不该在体检表里常驻。 */
        HealthRow(
            title = stringResource(R.string.health_sms),
            ok = smsGranted,
            statusText = stringResource(if (smsGranted) R.string.health_on else R.string.health_off),
            hint = if (smsGranted) null else stringResource(R.string.health_sms_hint),
            fixLabel = stringResource(R.string.health_fix_sms),
            onFix = onRequestSms,
            showFix = !smsGranted,
            statusColor = if (smsGranted) null else InkMuted,
        )
        /* 通知监听同样是补充手段：未开启用中性灰 + 「未开启 / 部分平台走短信」，
           不设红点、不催。 */
        HealthRow(
            title = stringResource(R.string.health_notification),
            ok = notificationEnabled,
            statusText = stringResource(if (notificationEnabled) R.string.health_on else R.string.health_off),
            hint = if (notificationEnabled) null else stringResource(R.string.health_notification_hint),
            fixLabel = stringResource(R.string.health_fix_notification),
            onFix = onOpenNotificationSettings,
            showFix = !notificationEnabled,
            statusColor = if (notificationEnabled) null else InkMuted,
        )
        if (HealthIssue.BACKGROUND_SUSPECTED in report.issues) {
            HealthRow(
                title = stringResource(R.string.health_background),
                ok = false,
                statusText = stringResource(R.string.health_off),
                hint = stringResource(R.string.health_background_hint),
                fixLabel = stringResource(R.string.health_fix_background),
                onFix = onOpenAppDetails,
                showFix = true,
                statusColor = UrgencyDueSoon,
            )
        }
        if (HealthIssue.MIUI_SMS_CLASS in report.manualChecks) {
            HealthRow(
                title = stringResource(R.string.health_miui),
                ok = false,
                statusText = stringResource(R.string.health_unknown),
                hint = stringResource(R.string.health_miui_hint),
                fixLabel = stringResource(R.string.health_fix_miui),
                onFix = onOpenAppDetails,
                showFix = true,
                statusColor = UrgencyDueSoon,
            )
        }
    }
}

@Composable
private fun HealthRow(
    title: String,
    ok: Boolean,
    statusText: String,
    hint: String? = null,
    fixLabel: String,
    onFix: () -> Unit,
    showFix: Boolean,
    statusColor: Color? = null,
) {
    val tint = statusColor ?: if (ok) UrgencySafe else UrgencyAtRisk
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = ShowCodeText.Body,
                color = Ink,
            )
            Text(
                text = statusText,
                modifier = Modifier
                    .background(color = tint.copy(alpha = 0.16f), shape = ShowCodeShapes.Sm)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                style = ShowCodeText.Micro,
                /* 11sp 小字不靠彩色本身扛对比：颜色退到浅底，字回到 Ink。 */
                color = Ink,
            )
            if (showFix) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = fixLabel,
                    modifier = Modifier
                        .clickable(onClick = onFix)
                        .padding(vertical = 4.dp),
                    style = ShowCodeText.Action,
                    color = AccentInk,
                )
            }
        }
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = hint, style = ShowCodeText.Caption, color = InkMuted)
        }
    }
}

/* ── 更新 ───────────────────────────────────────────────────────────── */

private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data object NoRelease : UpdateUi
    data class Failed(val message: String) : UpdateUi
    data class Available(
        val info: UpdateInfo,
        /** DownloadManager 的 id；非空 = 正在下载。 */
        val downloading: Long? = null,
        /** 已经下载并通过 SHA-256 校验的包。 */
        val downloaded: File? = null,
    ) : UpdateUi
}

@Composable
private fun AboutCard(
    state: UpdateUi,
    onCheck: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    onOpenPage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .settingsCard()
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = ShowCodeText.Body,
                    color = Ink,
                )
                Text(
                    text = stringResource(R.string.settings_version_meta),
                    style = ShowCodeText.Caption,
                    color = InkMuted,
                )
            }
            val busy = state is UpdateUi.Checking
            Text(
                text = stringResource(
                    if (busy) R.string.settings_update_checking else R.string.settings_update_check
                ),
                modifier = Modifier
                    .clickable(enabled = !busy, onClick = onCheck)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                style = ShowCodeText.Action,
                color = if (busy) InkMuted else AccentInk,
            )
        }
        when (state) {
            UpdateUi.Idle, UpdateUi.Checking -> Unit
            UpdateUi.UpToDate -> UpdateLine(stringResource(R.string.settings_update_latest), InkMuted)
            UpdateUi.NoRelease -> UpdateLine(stringResource(R.string.settings_update_none), InkMuted)
            is UpdateUi.Failed -> UpdateLine(state.message, UrgencyDueSoon)
            is UpdateUi.Available -> AvailableUpdate(
                state = state,
                onDownload = onDownload,
                onInstall = onInstall,
                onOpenPage = onOpenPage,
                onDismiss = onDismiss,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.settings_update_note),
            style = ShowCodeText.Caption,
            color = InkMuted,
        )
        /* 只在「没查到 / 没发布」时给两条发布页兜底；正常态不摆链接。 */
        if (state is UpdateUi.Failed || state is UpdateUi.NoRelease) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Gitee",
                    modifier = Modifier
                        .clickable { onOpenPage(UpdateChecker.GITEE_PAGE) }
                        .padding(vertical = 4.dp),
                    style = ShowCodeText.Caption,
                    color = AccentInk,
                )
                Text(
                    text = "GitHub",
                    modifier = Modifier
                        .clickable { onOpenPage(UpdateChecker.GITHUB_PAGE) }
                        .padding(vertical = 4.dp),
                    style = ShowCodeText.Caption,
                    color = AccentInk,
                )
            }
        }
    }
}

@Composable
private fun UpdateLine(text: String, color: Color) {
    Spacer(Modifier.height(8.dp))
    Text(text = text, style = ShowCodeText.Meta, color = color)
}

@Composable
private fun AvailableUpdate(
    state: UpdateUi.Available,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    onOpenPage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val info = state.info
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.settings_update_available, info.versionName),
        style = ShowCodeText.Item,
        color = Ink,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = info.notes,
        style = ShowCodeText.Meta,
        color = InkSecondary,
        maxLines = 5,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    val meta = listOfNotNull(
        info.source.label,
        info.sizeBytes?.let(::sizeText),
        if (info.sha256.isNullOrBlank()) stringResource(R.string.settings_update_no_hash) else null,
    ).joinToString(" · ")
    Text(text = meta, style = ShowCodeText.Caption, color = InkMuted)
    Spacer(Modifier.height(6.dp))
    when {
        state.downloaded != null -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(R.string.settings_update_install),
                modifier = Modifier
                    .clickable { state.downloaded?.let(onInstall) }
                    .padding(vertical = 4.dp),
                style = ShowCodeText.Action,
                color = AccentInk,
            )
            Text(
                text = stringResource(R.string.settings_update_later),
                modifier = Modifier.clickable { onDismiss() }.padding(vertical = 4.dp),
                style = ShowCodeText.Action,
                color = InkMuted,
            )
        }
        state.downloading != null -> Text(
            text = stringResource(R.string.settings_update_downloading),
            style = ShowCodeText.Meta,
            color = InkMuted,
        )
        else -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val manual = info.sha256.isNullOrBlank()
            Text(
                text = stringResource(
                    if (manual) R.string.settings_update_manual else R.string.settings_update_download
                ),
                modifier = Modifier
                    .clickable { if (manual) onOpenPage(info.pageUrl) else onDownload(info) }
                    .padding(vertical = 4.dp),
                style = ShowCodeText.Action,
                color = AccentInk,
            )
            Text(
                text = stringResource(R.string.settings_update_later),
                modifier = Modifier.clickable { onDismiss() }.padding(vertical = 4.dp),
                style = ShowCodeText.Action,
                color = InkMuted,
            )
        }
    }
}

private fun openUpdatePage(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun sizeText(bytes: Long): String =
    if (bytes >= 1024 * 1024) {
        String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f)
    } else {
        String.format(Locale.US, "%d KB", (bytes / 1024).coerceAtLeast(1))
    }
