package dev.showcode.ui.account

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.showcode.R
import dev.showcode.core.Slog
import dev.showcode.core.account.AccountState
import dev.showcode.core.account.BoundAccount
import dev.showcode.core.account.Platform
import dev.showcode.core.account.Session
import dev.showcode.ui.inbox.ScreenTopBar
import dev.showcode.ui.navBarClearance
import dev.showcode.ui.theme.AccentInk
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.environmentBackground
import dev.showcode.ui.theme.moduleEntrance
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 平台账号列表。三张卡永远都在，未绑定的直接给「去登录」——
 * 空态不是一屏空白，而是「三家都还没绑」这个事实本身。
 */
@Composable
fun AccountListScreen(
    accounts: List<BoundAccount>,
    onBack: () -> Unit,
    onLogin: (Platform) -> Unit,
    onToggle: (BoundAccount, Boolean) -> Unit,
    onDelete: (BoundAccount) -> Unit,
    /** 正在同步的平台 id；null = 没在同步。 */
    syncing: String? = null,
    syncMessage: String? = null,
    onSync: (BoundAccount) -> Unit = {},
    /** 仅 debug：把探针/P0 留在 WebView 里的会话直接绑定，省掉一次重复登录。 */
    onDebugImport: (() -> Unit)? = null,
) {
    var pendingDelete by remember { mutableStateOf<BoundAccount?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .environmentBackground()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        ScreenTopBar(title = stringResource(R.string.account_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = navBarClearance()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(modifier = Modifier.moduleEntrance(0)) {
                    Column {
                        Text(
                            text = stringResource(R.string.account_intro),
                            style = ShowCodeText.Meta,
                            color = InkMuted,
                        )
                        syncMessage?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = it,
                                style = ShowCodeText.Caption,
                                color = InkMuted,
                            )
                        }
                    }
                }
            }
            Platform.entries.forEach { platform ->
                item(key = platform.id) {
                    Box(modifier = Modifier.moduleEntrance(1)) {
                        AccountCard(
                            platform = platform,
                            account = accounts.firstOrNull { it.platform == platform },
                            syncing = syncing == platform.id,
                            onLogin = { onLogin(platform) },
                            onToggle = onToggle,
                            onDelete = { pendingDelete = it },
                            onSync = onSync,
                        )
                    }
                }
            }
            /* 账号列表之后的一句补充说明：不是警告，也不催人——只把「抖音/小红书这类
               没有网页订单入口的平台该走哪条路」说清楚。 */
            item {
                Box(modifier = Modifier.moduleEntrance(1)) {
                    Text(
                        text = stringResource(R.string.account_platform_note),
                        style = ShowCodeText.Caption,
                        color = InkMuted,
                    )
                }
            }
            if (onDebugImport != null) {
                item {
                    Text(
                        text = "导入当前 WebView 会话（调试）",
                        modifier = Modifier
                            .clickable(onClick = onDebugImport)
                            .padding(vertical = 12.dp),
                        style = ShowCodeText.Action,
                        color = InkMuted,
                    )
                }
            }
        }
    }

    pendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = { Text(stringResource(R.string.account_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(account)
                }) { Text(stringResource(R.string.account_delete), color = AccentInk) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.account_cancel), color = InkMuted)
                }
            },
        )
    }
}

@Composable
private fun AccountCard(
    platform: Platform,
    account: BoundAccount?,
    syncing: Boolean,
    onLogin: () -> Unit,
    onToggle: (BoundAccount, Boolean) -> Unit,
    onDelete: (BoundAccount) -> Unit,
    onSync: (BoundAccount) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = platform.label,
                modifier = Modifier.weight(1f),
                style = ShowCodeText.Item,
                color = Ink,
            )
            if (account == null) {
                Text(
                    text = stringResource(R.string.account_login),
                    modifier = Modifier
                        .clickable(onClick = onLogin)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    style = ShowCodeText.Action,
                    color = AccentInk,
                )
            } else {
                Switch(
                    checked = account.enabled,
                    onCheckedChange = { onToggle(account, it) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val (line, color) = when {
            account == null -> stringResource(R.string.account_unbound) to InkMuted
            account.state == AccountState.EXPIRED -> stringResource(R.string.account_expired) to UrgencyDueSoon
            account.state == AccountState.BROKEN -> stringResource(R.string.account_broken) to UrgencyDueSoon
            syncing -> stringResource(R.string.account_syncing, platform.label) to InkMuted
            else -> (account.lastSyncResult ?: stringResource(R.string.account_bound_meta)) to InkMuted
        }
        Text(
            text = line,
            style = ShowCodeText.Caption,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (account != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (account.enabled && !syncing) {
                    Text(
                        text = stringResource(R.string.account_sync_now),
                        modifier = Modifier
                            .clickable { onSync(account) }
                            .padding(vertical = 12.dp),
                        style = ShowCodeText.Action,
                        color = AccentInk,
                    )
                }
                Text(
                    text = stringResource(R.string.account_relogin),
                    modifier = Modifier
                        .clickable(onClick = onLogin)
                        .padding(vertical = 12.dp),
                    style = ShowCodeText.Action,
                    color = AccentInk,
                )
                Text(
                    text = stringResource(R.string.account_delete),
                    modifier = Modifier
                        .clickable { onDelete(account) }
                        .padding(vertical = 12.dp),
                    style = ShowCodeText.Action,
                    color = AccentInk,
                )
            }
        }
    }
}

private enum class LoginPhase { WAITING, SAVING }

/**
 * 官方登录页。它只做一件事：让用户在官方页面里登录，拿到 Cookie 后立刻交给上层加密保存。
 * 这里不读密码、不读验证码、不保存任何表单。
 */
@Composable
fun PlatformLoginScreen(
    platform: Platform,
    onBack: () -> Unit,
    /** 保存成功返回 true；加密/落库失败返回 false，界面留在原地给一次重试。 */
    onBound: suspend (String) -> Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var webRef by remember { mutableStateOf<WebView?>(null) }
    var phase by remember { mutableStateOf(LoginPhase.WAITING) }
    var bound by remember { mutableStateOf(false) }
    /* 拿到登录态却存不下加密凭据。停在登录页而不是带着一个没写进库的「成功」离开。 */
    var saveFailed by remember { mutableStateOf(false) }
    /* 只有用户明确点「改用手机号登录」时才自动点手机号 tab；正常进入保留扫码可选。 */
    var pendingPhone by remember { mutableStateOf(false) }
    /* 识别到风控问卷页：只在我们自己的区域给一句出口，绝不覆盖网页内容。 */
    var riskPage by remember { mutableStateOf(false) }

    fun leave() {
        clearSession(context)
        onBack()
    }

    /* 已经登录过的账号会被拼多多直接带进地址页；这里先清干净再回登录页，
       免得用户对着一个没有登录表单的页面干等。 */
    fun reload() {
        phase = LoginPhase.WAITING
        riskPage = false
        clearSession(context) {
            webRef?.let { web -> web.post { web.loadUrl(loginUrl(platform)) } }
        }
    }

    fun usePhoneLogin() {
        pendingPhone = true
        reload()
    }

    /* 风控页可能因为桌面 UA 按桌面断点排版（选项挤成一列、按钮被挤出屏幕）。
       这里换成手机版 UA 重载当前页：不清 Cookie，登录态与验证 token 都留在
       URL/Cookie 里，只是换一种页面版式；是否使用由用户点按钮决定。 */
    fun reloadAsMobile() {
        riskPage = false
        webRef?.let { web ->
            web.settings.userAgentString = mobileChromeUa(context)
            web.post { web.reload() }
            Slog.i("PddWeb") { "reloadAsMobile" }
        }
    }

    BackHandler {
        if (!bound) leave()
    }

    LaunchedEffect(webRef) {
        val web = webRef ?: return@LaunchedEffect
        while (isActive && !bound) {
            delay(1500)
            if (platform == Platform.PDD) {
                web.evaluateJavascript(RISK_PROBE_JS) { value ->
                    val parts = value?.trim('"')?.split("|").orEmpty()
                    val hit = parts.firstOrNull() == "1"
                    if (hit != riskPage) {
                        riskPage = hit
                        Slog.i("PddWeb") { "riskPage=$hit" }
                    } else if (hit) {
                        // 留在问卷页时持续记录页面高度/按钮数，便于核实是否整页可达。
                        Slog.i("PddWeb") { "riskMetrics ok=${parts.size}" }
                    }
                }
            }
            val cookie = CookieManager.getInstance().getCookie(platform.cookieHost).orEmpty()
            if (!saveFailed && Session.parse(cookie).has(platform)) {
                phase = LoginPhase.SAVING
                if (onBound(cookie)) {
                    bound = true
                    break
                }
                /* 加密/落库失败：账号没有被改动，留在登录页等用户重试。 */
                phase = LoginPhase.WAITING
                saveFailed = true
            }
        }
    }

    /* 登录页在前台时绝不让 WebView 停摆：这里不调用 onPause/pauseTimers；
       回到前台立即恢复计时器，扫码轮询才不会因为短暂切后台而断掉。 */
    DisposableEffect(lifecycleOwner, webRef) {
        val web = webRef
        if (web == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    web.resumeTimers()
                    runCatching { web.onResume() }
                    Slog.i("PddWeb") { "resumeTimers" }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            clearSession(context)
            webRef?.let {
                (it.parent as? android.view.ViewGroup)?.removeView(it)
                it.destroy()
            }
            webRef = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .environmentBackground()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        ScreenTopBar(
            title = stringResource(R.string.login_title, platform.label),
            onBack = { leave() },
            action = {
                Text(
                    text = stringResource(R.string.login_switch_account),
                    modifier = Modifier
                        .clickable { reload() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    style = ShowCodeText.Action,
                    color = InkMuted,
                )
            },
        )
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = stringResource(R.string.login_lead),
                style = ShowCodeText.Item,
                color = Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.login_body),
                style = ShowCodeText.Meta,
                color = InkMuted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.login_escape),
                style = ShowCodeText.Caption,
                color = InkMuted,
            )
            Spacer(Modifier.height(14.dp))
        }
        /* 风控验证页的说明与出口都放在页面之外，独立成行，不叠在网页内容上，
           不遮挡选项与提交按钮。 */
        if (platform == Platform.PDD && riskPage) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(
                    text = stringResource(R.string.login_risk_hint),
                    style = ShowCodeText.Caption,
                    color = InkMuted,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.login_risk_reload),
                        modifier = Modifier
                            .clickable { reloadAsMobile() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        style = ShowCodeText.Action,
                        color = InkMuted,
                    )
                    Text(
                        text = stringResource(R.string.login_use_phone),
                        modifier = Modifier
                            .clickable { usePhoneLogin() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        style = ShowCodeText.Action,
                        color = Ink,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
        }
        /* 官方页面自己占满标题下方到状态栏的全部高度：不再套白色圆角卡，
           否则窄屏上表单被卡边界裁掉、也滚不到底。 */
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // 页面是 edge-to-edge：不主动让出 IME 高度的话，键盘会盖住登录按钮。
                .imePadding(),
            factory = { ctx ->
                WebView(ctx).also { web ->
                    configureWeb(
                        web,
                        context = ctx,
                        platform = platform,
                        shouldOpenPhone = { pendingPhone },
                        onOpenPhone = { pendingPhone = false },
                    )
                    webRef = web
                    // 先真正清掉旧会话再加载，否则已登录的账号会直接被带到地址页。
                    clearSession(context) {
                        web.post { web.loadUrl(loginUrl(platform)) }
                    }
                }
            },
        )
        Text(
            text = stringResource(
                if (phase == LoginPhase.WAITING) R.string.login_waiting else R.string.login_saving,
            ),
            modifier = Modifier.padding(20.dp),
            style = ShowCodeText.Caption,
            color = InkMuted,
        )
        if (saveFailed) {
            Text(
                text = stringResource(R.string.login_save_failed),
                modifier = Modifier.padding(horizontal = 20.dp),
                style = ShowCodeText.Caption,
                color = UrgencyDueSoon,
            )
            Text(
                text = stringResource(R.string.login_save_retry),
                modifier = Modifier
                    .clickable { saveFailed = false; phase = LoginPhase.WAITING }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                style = ShowCodeText.Action,
                color = Ink,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureWeb(
    web: WebView,
    context: android.content.Context,
    platform: Platform,
    shouldOpenPhone: () -> Boolean,
    onOpenPhone: () -> Unit,
) {
    WebView.setWebContentsDebuggingEnabled(false)
    web.setBackgroundColor(android.graphics.Color.WHITE)
    val s = web.settings
    s.javaScriptEnabled = true
    s.domStorageEnabled = true
    s.databaseEnabled = true
    s.useWideViewPort = true
    s.loadWithOverviewMode = true
    /* 风控页偶尔比手机屏宽，允许双指缩放；否则右侧的提交按钮会被挤出屏幕。 */
    s.setSupportZoom(true)
    s.builtInZoomControls = true
    s.displayZoomControls = false
    s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    s.userAgentString = if (platform == Platform.PDD) {
        // 手机版 UA 下 PDD 只给「打开拼多多APP / 手机登录」，扫码入口直接消失；
        // 桌面 UA 才有「扫码登录」二维码。所以首屏固定用桌面 UA。
        // 若扫码后的风控页因此按桌面断点排版、显示不全，由页面外的
        // 「用手机版重新加载」手动切换（不清 Cookie，不打断登录态）。
        WebSettings.getDefaultUserAgent(context)
            .replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)")
            .replace("Version/4.0 ", "")
            .replace(" Mobile", "")
    } else {
        WebSettings.getDefaultUserAgent(context)
    }
    with(CookieManager.getInstance()) {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(web, true)
    }
    /* 没有 WebChromeClient 时页面里的 alert/confirm/prompt 会被静默吞掉，
       风控页只要弹一次确认就永远等不到「下一步」。这里把三种对话框接回原生。 */
    web.webChromeClient = object : WebChromeClient() {
        override fun onJsAlert(
            view: WebView,
            url: String,
            message: String,
            result: JsResult,
        ): Boolean {
            android.app.AlertDialog.Builder(context)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }

        override fun onJsConfirm(
            view: WebView,
            url: String,
            message: String,
            result: JsResult,
        ): Boolean {
            android.app.AlertDialog.Builder(context)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String,
            message: String,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean {
            val input = EditText(context).apply { setText(defaultValue.orEmpty()) }
            android.app.AlertDialog.Builder(context)
                .setMessage(message)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    result.confirm(input.text.toString())
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }
    }
    web.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            Slog.i("PddWeb") { "page=$url" }
            /* 记录页面实际拿到的 UA 与视口，便于对照「风控页是否按手机版渲染」。 */
            view.evaluateJavascript(
                "(navigator.userAgent||'')+'|'+window.innerWidth+'x'+window.innerHeight" +
                    "+'|dpr'+window.devicePixelRatio",
            ) { Slog.i("PddWeb") { "ua=$it" } }
            // 只有用户主动选择手机号登录时才切 tab；扫码流程保持页面原本的样子。
            if (platform == Platform.PDD && shouldOpenPhone()) {
                onOpenPhone()
                view.evaluateJavascript(PDD_OPEN_PHONE_TAB_JS, null)
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            // 风控验证可能连续跳转（人机校验、二次验证、成功页），http(s) 一律放行；
            // 只拦会离开浏览器的自定义 scheme，避免把登录态打断。
            val leaveBrowser = !url.startsWith("http")
            Slog.i("PddWeb") { "nav=$url leaveBrowser=$leaveBrowser" }
            return leaveBrowser
        }
    }
}

/** 手机版 Chrome UA：只抹掉 WebView 的 "; wv" 与 "Version/4.0" 标记。 */
private fun mobileChromeUa(context: android.content.Context): String =
    WebSettings.getDefaultUserAgent(context)
        .replace("; wv", "")
        .replace("Version/4.0 ", "")

/** 只在用户点了「改用手机号登录」后执行：等 React 首屏渲染完再点手机号 tab。 */
private const val PDD_OPEN_PHONE_TAB_JS = """
(function () {
  var tries = 0;
  var timer = setInterval(function () {
    var nodes = document.querySelectorAll('div,span,button,a');
    for (var i = 0; i < nodes.length; i++) {
      var e = nodes[i];
      if (e.children.length === 0 && e.textContent.trim() === '手机登录') {
        e.click();
        clearInterval(timer);
        return;
      }
    }
    if (++tries > 25) clearInterval(timer);
  }, 200);
})();
"""

/**
 * 问卷/安全验证页探针：只看特征，不改 DOM、不点任何选项。
 * 末尾多带一个「内容高于视口」的标记，用来判断页面到底能不能滚到底。
 */
private const val RISK_PROBE_JS = """
(function () {
  try {
    var radios = document.querySelectorAll('input[type=radio],input[type=checkbox]').length;
    var href = (location.href || '').toLowerCase();
    var title = (document.title || '');
    var text = (document.body ? document.body.innerText || '' : '').slice(0, 3000);
    var buttons = document.querySelectorAll('button,[role=button],input[type=submit]').length;
    var hit = radios >= 2
      || /risk|verify|validate|captcha|challenge/.test(href)
      || /验证|问卷/.test(title)
      || /安全验证|人机验证|依次点击|请按顺序|拖动滑块|滑动验证|点击下方/.test(text);
    var scrollable = document.body ? document.body.scrollHeight > window.innerHeight : false;
    return (hit ? '1' : '0') + '|' + (document.body ? document.body.scrollHeight : 0) +
      '|' + window.innerHeight + '|' + buttons + '|' + (scrollable ? '1' : '0');
  } catch (e) {
    return '0|0|0|0|0';
  }
})();
"""

private fun clearSession(context: android.content.Context, onDone: () -> Unit = {}) {
    val cookies = CookieManager.getInstance()
    cookies.removeAllCookies {
        cookies.flush()
        WebStorage.getInstance().deleteAllData()
        onDone()
    }
}

/** 不带 from/_x_no_login_launch：带上它们拼多多会先给选择页，落不到手机号表单。 */
private fun loginUrl(platform: Platform): String = platform.loginUrl
