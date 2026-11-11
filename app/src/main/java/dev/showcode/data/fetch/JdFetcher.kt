package dev.showcode.data.fetch

import android.app.Activity
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.showcode.core.Parcel
import dev.showcode.core.Slog
import dev.showcode.core.Stage
import dev.showcode.core.account.FetchOutcome
import dev.showcode.core.account.FailureKind
import dev.showcode.core.account.FetchedParcel
import dev.showcode.core.account.GoodsInfo
import dev.showcode.core.account.OrderStage
import dev.showcode.core.account.Platform
import dev.showcode.core.account.Session
import dev.showcode.core.account.SyncPhase
import dev.showcode.core.account.SyncProgress
import dev.showcode.core.account.TraceParser
import dev.showcode.core.account.detailNeeded
import dev.showcode.core.account.fallbackFetchedParcel
import dev.showcode.core.account.goodsPriceText
import dev.showcode.core.account.isBlockedOrderPrompt
import dev.showcode.core.account.isFinishedOrderPrompt
import dev.showcode.core.account.isVirtualGoodsTitle
import dev.showcode.core.account.needsDetailForPrompt
import dev.showcode.core.account.normalizeImageUrl
import dev.showcode.core.account.orderTimeMs
import dev.showcode.core.account.stageOfPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * 京东：订单中心用隐藏 WebView + JSONP 回调改写捕获，物流页读 DOM。
 *
 * 京东的订单接口是 JSONP（不是 fetch），所以钩子要做的是把 `callback=xxx` 改写成我们自己的
 * 回调名，再在 document-start 阶段挂上去；这是参考实现验证过的那条路。
 *
 * ⚠️ 未在真机京东账号上验证（P5 需要用户登录一次）。
 */
object JdFetcher {

    private const val TAG = "JdFetcher"
    private const val ORDER_CENTER =
        "https://trade.m.jd.com/order/orderlist_jdm.shtml?orderType=all&source=m_outer_jx_order"
    /** 每单详情要整页加载一次；上限保留，但只从真正待取/在途的单里按状态排序取。 */
    private const val MAX_DETAILS = 8
    /* 首屏轮询上限：数据一到就继续；京东 SPA 在冷启动/慢机上可能 3~4 秒才发列表请求。 */
    private const val FIRST_SCREEN_WAIT_MS = 4_000L
    private const val PAGE_WAIT_MS = 2_500L
    /* 京东订单页是重 SPA；冷启动（刚安装/刚清进程）在模拟器上会超过 20 秒。 */
    private const val PAGE_LOAD_TIMEOUT_MS = 30_000L
    /* 单件详情导航的超时：从 20s 收紧到和淘宝单件详情同档，慢页快速放弃、由列表兜底顶上。 */
    private const val DETAIL_LOAD_TIMEOUT_MS = 12_000L
    /** 京东运单号：带标签的优先，其次 JD 段位码 / 通用段位码。 */
    private val TRACKING_LABELED = Regex("(?:运单号|快递单号|物流单号|货运单号)[：:\\s]*([A-Za-z0-9\\-]{8,})")
    private val TRACKING_CODE = Regex("(JD[VA]?[0-9]{8,}|[A-Z]{2,4}[0-9]{8,})")
    /** 注入页面钩子的 origin：订单页和可能承载订单接口的域。 */
    private val SCRIPT_HOSTS = setOf(
        "https://trade.m.jd.com",
        "https://wqs.jd.com",
        "https://www.jd.com",
        "https://api.m.jd.com",
        "https://o.m.jd.com",
        "https://wq.jd.com",
    )
    /** 登录会话分散在这些 host 上；读取、恢复、清理都按同一组走。 */
    private val COOKIE_HOSTS = listOf(
        "https://www.jd.com",
        "https://trade.m.jd.com",
        "https://wqs.jd.com",
        "https://api.m.jd.com",
        "https://plogin.m.jd.com",
        "https://home.m.jd.com",
        "https://jingfen.jd.com",
    )

    suspend fun fetch(
        activity: Activity,
        accountId: String,
        cookie: String,
        /** 库里这个账号已有的件（键 = 订单号）。状态没变的单不再进详情页。 */
        known: Map<String, Parcel> = emptyMap(),
        /** 这一轮走到哪一步：顶栏的「读取详情 2/8」就是它。 */
        onProgress: suspend (SyncProgress) -> Unit = {},
        /** 抓取过程中京东刷新出的完整 Cookie；由上层并回登录时保存的那份。 */
        onCookie: (String) -> Unit = {},
    ): FetchOutcome =
        withContext(Dispatchers.Main) {
            val pages = Channel<String>(Channel.BUFFERED)
            val web = WebView(activity)
            /* 只记「主文档 / 订单接口」的错误。广告图、埋点、字体的 404 不该让整次同步失败。 */
            var httpCode: Int? = null
            var httpUrl: String = ""
            try {
                prepare(activity, web, cookie, pages) { code, url ->
                    if (httpCode == null) {
                        httpCode = code
                        httpUrl = url
                    }
                }
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    return@withContext FetchOutcome.Failure("这台设备的 WebView 不支持页面脚本注入")
                }
                WebViewCompat.addDocumentStartJavaScript(web, HOOK_JS, SCRIPT_HOSTS)
                web.loadUrl(ORDER_CENTER)
                var first = withTimeoutOrNull(PAGE_LOAD_TIMEOUT_MS) { pages.receive() }
                    ?: return@withContext FetchOutcome.Failure("订单页加载超时")
                httpCode?.let {
                    return@withContext FetchOutcome.Failure(
                        "订单页没打开：HTTP $it（$httpUrl）", httpFailureKind(it),
                    )
                }
                /* 京东风控/会话刷新也会把一次正常请求瞬时带到通行证页，紧接着重载又好了。
                   一次重定向不足以判死会话：再要一次，仍不是订单页才认（对齐拼多多）。 */
                if (!isJdOrdersPage(first)) {
                    Slog.w(TAG) { "订单页没打开，重试一次: $first" }
                    drain(pages)
                    delay(700)
                    web.loadUrl(ORDER_CENTER)
                    val again = withTimeoutOrNull(PAGE_LOAD_TIMEOUT_MS) { pages.receive() }
                        ?: return@withContext FetchOutcome.Failure("订单页加载超时")
                    if (!isJdOrdersPage(again)) {
                        Slog.w(TAG) { "订单页重试仍失败: $again" }
                        val auth = looksLikeLogin(again)
                        return@withContext FetchOutcome.Failure(
                            if (auth) "登录已失效，需要重新登录" else "订单页跳到了其它页面",
                            if (auth) FailureKind.AUTH else FailureKind.TRANSIENT,
                        )
                    }
                    first = again
                    Slog.i(TAG) { "订单页重试成功: $first" }
                }

                val captures = mutableListOf<String>()
                var nameReadable = false
                /* 首屏轮询：列表接口响应一到就进滑动，不再白等固定 2.5 秒；
                   慢机/冷启动最多等 FIRST_SCREEN_WAIT_MS。 */
                val firstStart = SystemClock.uptimeMillis()
                while (SystemClock.uptimeMillis() - firstStart < FIRST_SCREEN_WAIT_MS) {
                    if (readsCaps(web, captures)) nameReadable = true
                    if (captures.isNotEmpty()) break
                    delay(120)
                }
                if (readsCaps(web, captures)) nameReadable = true
                var rounds = 0
                while (rounds < 5) {
                    swipeUp(web)
                    rounds++
                    /* 这一轮没有新响应就收工；再滑也不会凭空多出页来。 */
                    if (awaitCaptures(web, captures, PAGE_WAIT_MS) <= 0) break
                }
                /* 京东会在页面壳加载完之后才用 JS 跳到通行证页：只看 onPageFinished
                   的第一跳会把它误判成「接口没返回」。这里补一次当前位置判定。 */
                if (looksLikeLogin(evaluate(web, "location.href").orEmpty())) {
                    return@withContext FetchOutcome.Failure(
                        "登录已失效，需要重新登录", FailureKind.AUTH,
                    )
                }
                httpCode?.let {
                    return@withContext FetchOutcome.Failure(
                        "订单接口请求失败：HTTP $it（$httpUrl）", httpFailureKind(it),
                    )
                }
                if (captures.isEmpty()) {
                    return@withContext FetchOutcome.Failure(
                        if (!nameReadable) {
                            "页面脚本没有响应（抓取缓冲区读不到）"
                        } else {
                            "订单接口没有返回数据（可能已改版）"
                        },
                    )
                }

                /* 取消/关闭/退款/待付款的单也要带回来：它们既是退休旧卡的证据，
                   也是「这次见过哪些单」的一部分。历史单不再占用详情名额。 */
                val orders = parseOrders(captures)
                /* 覆盖度自检（对齐淘宝）：库里有活跃件却一条订单都没读到，更可能是接口改版/被风控，
                   不是「件都没了」。当瞬时失败，不写空快照、不退休已有卡。 */
                val activeKnown = known.values.count { !it.retired && !it.picked }
                if (orders.isEmpty() && activeKnown > 0) {
                    Slog.safe(TAG, "empty-list-with-known=$activeKnown treat-as-breakage")
                    return@withContext FetchOutcome.Failure("订单列表为空（接口可能改版），本次先保留已有数据")
                }
                /* 数量骤降：老库有 4 件以上、这次读到的订单比活跃件还少——更可能是翻页丢失/改版，
                   不把这次当完整结果，避免退休还没读到的在途件。 */
                if (activeKnown >= 4 && orders.size < activeKnown) {
                    Slog.safe(TAG, "order-count-drop=$activeKnown->${orders.size} treat-as-breakage")
                    return@withContext FetchOutcome.Failure("订单数量异常减少（可能接口改版），本次先保留已有数据")
                }
                /* 卡券/虚拟商品没有物流签收：不进详情，也不留在首页；老行交给退休。 */
                val virtualIds = orders.filter {
                    isVirtualGoodsTitle(it.goods?.title) || isVirtualGoodsTitle(known[it.id]?.goodsTitle)
                }.map { it.id }.toSet()
                val history = orders.filter { closesOrder(it.status) }.map { it.id }
                /* 先剔除「状态没变、运单号与码都在」的单再占详情名额：
                   它们这次进详情拿回来的会和上次是同一段文字。判据见 core/account/DetailGate.kt。 */
                val now = System.currentTimeMillis()
                val pending = orders.filter { needsDetail(it) && it.id !in virtualIds }
                    .sortedBy { detailRank(it.status) }
                val detailTargets = pending
                    .filter { detailNeeded(it.id, it.stage, known, now) }
                    .take(MAX_DETAILS)
                val detailIds = detailTargets.map { it.id }.toSet()
                /* 详情之外也必须覆盖到：超出名额的新单、或详情被门控跳过但库里还没有的活跃单，
                   不能因为「没进详情」就从结果里消失。新单进兜底，老行保持不动（对齐淘宝 worklist）。 */
                val worklist = pending.filter { order ->
                    order.id in detailIds ||
                        known[order.id] == null ||
                        known[order.id]?.let { row ->
                            row.retired || row.trackingNo.isNullOrBlank() ||
                                (order.stage != null && row.stage != order.stage)
                        } == true
                }
                Slog.safe(
                    TAG,
                    "orders=${orders.size} pending=${pending.size} detailTargets=${detailTargets.size} " +
                        "worklist=${worklist.size} history=${history.size} virtual=${virtualIds.size}",
                )
                /* 详情是单 WebView 串行导航：只对真正要读的 detailTargets 逐单进详情，
                   单件失败不影响整批，结果按 orderId 暂存。 */
                val byOrder = HashMap<String, FetchedParcel?>()
                var detailIndex = 0
                for (order in detailTargets) {
                    detailIndex++
                    onProgress(SyncProgress(SyncPhase.DETAILS, detailIndex, detailTargets.size))
                    val item = try {
                        detail(web, pages, accountId, order)
                    } catch (auth: DetailAuthExpired) {
                        return@withContext FetchOutcome.Failure("物流详情登录失效，需要重新登录", FailureKind.AUTH)
                    } catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException) throw t
                        Slog.w(TAG, t) { "detail failed ${Slog.mask(order.id)}" }
                        null
                    }
                    byOrder[order.id] = item
                }
                /* 详情阶段若明确 401/403：整批按登录失效处理（对齐淘宝的 auth 聚合）。 */
                httpCode?.let {
                    if (httpFailureKind(it) == FailureKind.AUTH) {
                        return@withContext FetchOutcome.Failure("物流详情登录失效（HTTP $it）", FailureKind.AUTH)
                    }
                }
                val result = mutableListOf<FetchedParcel>()
                var detailsFailed = 0
                var fallbackCount = 0
                worklist.forEach { order ->
                    val parsed = byOrder[order.id]
                    /* 兜底优先级：详情解析结果 > 列表状态。详情没抓到（或没名额/被跳过）时
                       用列表状态先入列，标「轨迹待补」，绝不让一件活跃单从结果里消失。 */
                    val item = parsed ?: fallbackFetchedParcel(
                        platform = Platform.JD,
                        accountId = accountId,
                        orderSn = order.id,
                        prompt = order.status,
                        goods = order.goods,
                        at = now,
                    )
                    if (item != null) {
                        result.add(item)
                        if (parsed == null) fallbackCount++
                    } else {
                        detailsFailed++
                        Slog.i(TAG) { "worklist-null status=${order.status.take(16)}" }
                    }
                }
                Slog.safe(
                    TAG,
                    "result parcels=${result.size} detailsFailed=$detailsFailed fallback=$fallbackCount",
                )
                /* 只在接口给出「没有下一页」的结束信号时才交 seen：
                   抓取不完整时保持空集，不让退休逻辑误删没翻到的在途件。 */
                val seen = if (listEnded(captures, orders.size)) {
                    orders.map { it.id }.toSet()
                } else {
                    emptySet()
                }
                /* 有候选却一条结果都拼不出来：只有至少一条状态是认识的活跃单时，
                   才算抓取失败。全是认不出的状态（例如新出现的已结束文案）时，
                   说明列表链路是通的，只是没有可用件——不能拿「没同步上」吓用户。 */
                if (worklist.isNotEmpty() && result.isEmpty()) {
                    if (worklist.any { it.stage != null }) {
                        return@withContext FetchOutcome.Failure(
                            "${detailsFailed} 件都没有可用信息，稍后再试",
                        )
                    }
                    return@withContext FetchOutcome.Success(
                        parcels = emptyList(),
                        historyOrderSns = history,
                        seenOrderSns = seen,
                        detailsFailed = detailsFailed,
                        retireOrderSns = virtualIds.toList(),
                    )
                }
                FetchOutcome.Success(
                    parcels = result,
                    historyOrderSns = history,
                    seenOrderSns = seen,
                    detailsFailed = detailsFailed,
                    fallbackCount = fallbackCount,
                    retireOrderSns = virtualIds.toList(),
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Slog.w(TAG, t) { "fetch failed" }
                FetchOutcome.Failure("抓取中断：${t.message ?: "未知错误"}")
            } finally {
                /* 先收 Cookie 再销毁/清理：页面跑完后 trade/api 上的会话键才是完整的。
                   空串或匿名集合由 AccountStore 挡掉，不会覆盖登录态。 */
                runCatching { collectCookies() }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { refreshed -> runCatching { onCookie(refreshed) } }
                runCatching {
                    (web.parent as? ViewGroup)?.removeView(web)
                    web.loadUrl("about:blank")
                    web.destroy()
                }
                /* 不清 Cookie：下一次同步还要靠这份会话。登录页进入时会统一清。 */
                runCatching { CookieManager.getInstance().flush() }
            }
        }

    private data class Order(
        val id: String,
        val status: String,
        val trackLink: String,
        val goods: GoodsInfo? = null,
        /** 列表级阶段；null = 认不出，详情门控只能按时间判断。 */
        val stage: Stage? = null,
    )

    private fun prepare(
        activity: Activity,
        web: WebView,
        cookie: String,
        pages: Channel<String>,
        onHttpError: (Int, String) -> Unit,
    ) {
        WebView.setWebContentsDebuggingEnabled(false)
        web.setBackgroundColor(Color.WHITE)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        /* 默认 WebView UA 带 "; wv" / "Version/4.0"，京东风控会把它当非浏览器环境；
           去掉这两个标记，和登录页保持一致。 */
        s.userAgentString = WebSettings.getDefaultUserAgent(activity)
            .replace("; wv", "")
            .replace("Version/4.0 ", "")
        /* 京东的会话不能每轮清空后平铺恢复：清掉再按 header 写回会丢 domain/属性，
           下一次请求就被判未登录。这里只补 WebView 里缺的键，已有的以浏览器为准。
           但登录身份键（pt_key/pt_pin）例外——必须无条件覆盖成当前账号的值：
           CookieManager 是进程级的，同步完上一个账号后这两个键会残留，
           「只补缺失」会让本轮拿上一个账号的会话去抓订单，结果挂错账号。 */
        CookieManager.getInstance().let { cm ->
            val saved = Session.parse(cookie).values
            COOKIE_HOSTS.forEach { host ->
                val existing = Session.parse(cm.getCookie(host)).values
                saved.forEach { (k, v) ->
                    if (shouldRestoreCookieKey(k, existing[k])) cm.setCookie(host, "$k=$v")
                }
            }
            cm.flush()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(web, true)
        }
        (activity.window.decorView as ViewGroup).addView(
            web,
            0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pages.trySend(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.toString().let { !it.startsWith("http") }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?,
            ) {
                val code = errorResponse?.statusCode ?: return
                val url = request?.url?.toString().orEmpty()
                /* 广告图、埋点、字体的 404 与同步无关；只有主文档和订单/物流接口算数。 */
                val significant = request?.isForMainFrame == true || isOrderEndpoint(url)
                if (!significant) {
                    Slog.i(TAG) { "ignore sub-resource http $code $url" }
                    return
                }
                Slog.w(TAG) { "http $code $url" }
                onHttpError(code, url)
            }
        }
    }

    private fun parseOrders(captures: List<String>): List<Order> {
        val out = LinkedHashMap<String, Order>()
        captures.forEach { raw ->
            runCatching {
                val root = JSONTokener(raw).nextValue()
                val body = when (root) {
                    is JSONObject -> root.optJSONObject("body") ?: root
                    else -> return@runCatching
                }
                val list = body.optJSONArray("orderList") ?: return@runCatching
                for (i in 0 until list.length()) {
                    val o = list.getJSONObject(i)
                    val id = o.optString("orderId")
                    if (id.isBlank()) continue
                    val status = o.optJSONObject("orderStatusInfo")?.optString("orderStatusName").orEmpty()
                    val track = o.optJSONObject("progressInfo")?.optString("progressLink").orEmpty().ifBlank {
                        val skuId = o.optJSONArray("wareInfoList")?.optJSONObject(0)?.optString("skuId").orEmpty()
                        val shopId = o.optJSONObject("shopInfo")?.optString("shopId").orEmpty()
                        val dealState = o.optJSONObject("orderStatusInfo")?.optString("originOrderStatus").orEmpty()
                        "https://trade.m.jd.com/order/deal_wuliu_jdm.shtml?from=orderdetail" +
                            "&dealState=$dealState&dealId=$id&orderType=${o.optString("orderType")}" +
                            "&skuid=$skuId&shopid=$shopId&source=m_inner_orderList.track_orderTrack"
                    }
                    out[id] = Order(id, status, track, goodsOf(o), stageOfPrompt(status))
                }
            }
        }
        /* 状态原文只在 debug 打；release 只留阶段计数（UNKNOWN 占比能说明词表是否又漏了）。 */
        Slog.i(TAG) {
            out.values.groupingBy { it.status.take(16) }.eachCount()
                .entries.joinToString(",") { "${it.key}:${it.value}" }
        }
        val stages = out.values.groupingBy { OrderStage.stageOf(it.status)?.name ?: "UNKNOWN" }.eachCount()
        Slog.safe(TAG, "stages=$stages")
        return out.values.toList()
    }

    /**
     * 历史/阻断状态：不进详情、但要让库里同号的在途卡退休。
     * 词表在 core 里，和拼多多共用一套口径，不在这里写死平台名。
     */
    private fun closesOrder(status: String): Boolean =
        isBlockedOrderPrompt(status) || isFinishedOrderPrompt(status)

    /** 待付款/未支付的单根本没有物流可抓；认不出的活跃状态仍要抓（见 core 的判据）。 */
    private fun needsDetail(order: Order): Boolean = needsDetailForPrompt(order.status)

    /** 真正待取/在途的排在前面；历史单已经在外面被剔除了。 */
    private fun detailRank(status: String): Int = when (OrderStage.stageOf(status)) {
        Stage.AT_STATION -> 0
        Stage.DELIVERING -> 1
        Stage.IN_TRANSIT -> 2
        else -> 3
    }

    /** 任一页响应给出「没有下一页」就算看全了；说不清就是没看全，不交 seen。 */
    private fun listEnded(captures: List<String>, seen: Int): Boolean = captures.any { raw ->
        runCatching {
            val root = JSONTokener(raw).nextValue()
            val body = (root as? JSONObject)?.optJSONObject("body") ?: root as? JSONObject
            orderListEnded(body, seen)
        }.getOrDefault(false)
    }

    /** 京东订单页：jd 域、path 里带 order；新域 o.m.jd.com 的 /list 也算。登录页不算。 */
    private fun isJdOrdersPage(url: String): Boolean {
        if (looksLikeLogin(url)) return false
        val bare = urlWithoutQuery(url).lowercase()
        val host = bare.substringAfter("://", "").substringBefore('/')
        val path = bare.substringAfter("://", "").substringAfter('/', "")
        val orderPath = path.contains("order") ||
            (host == "o.m.jd.com" && (path == "list" || path.startsWith("list/")))
        return isJdHost(host) && orderPath
    }

    /**
     * 把所有京东 host 的 Cookie 并成一条 header。
     *
     * 不能只读 `www.jd.com`：登录集被拆在 www / trade / wqs / api / plogin 上，
     * 页面跑完时 trade/api 才会补齐风险与会话键。读取顺序保持稳定，后者覆盖同名键。
     */
    private fun collectCookies(): String {
        val cm = CookieManager.getInstance()
        val merged = LinkedHashMap<String, String>()
        COOKIE_HOSTS.forEach { host ->
            val raw = runCatching { cm.getCookie(host) }.getOrNull()
            Session.parse(raw).values.forEach { (k, v) -> merged[k] = v }
        }
        return Session(merged).header()
    }

    /** 详情页导航落到登录页：会话在抓取途中失效，整批按 AUTH 处理。 */
    private class DetailAuthExpired : Exception("detail auth expired")

    /** 只有订单列表/物流详情这类接口的失败才值得让整次同步失败。 */
    private fun isOrderEndpoint(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("orderlist") || u.contains("deal_wuliu") || u.contains("/order/") ||
            u.contains("api.m.jd.com")
    }

    private suspend fun detail(
        web: WebView,
        pages: Channel<String>,
        accountId: String,
        order: Order,
    ): FetchedParcel? {
        /* progressLink 来自接口响应，服务端可控：先钉死在京东域再加载，
           否则带页面钩子的隐藏 WebView 会被导到第三方站点。 */
        if (!isSafeJdUrl(order.trackLink)) {
            Slog.safe(TAG, "detail-link-rejected")
            return null
        }
        drain(pages)
        web.loadUrl(order.trackLink)
        val loaded = withTimeoutOrNull(DETAIL_LOAD_TIMEOUT_MS) { pages.receive() } ?: return null
        if (!loaded.startsWith("http")) return null
        /* 初始链接过了校验，但服务端仍可 302 到第三方：带钩子的 WebView 会读它的 DOM
           交给 TraceParser。重定向后的实际地址必须同样落在京东域。 */
        if (!isSafeJdUrl(loaded)) return null
        /* 详情页导航落到通行证/登录页 = 会话失效的硬证据，整批按 AUTH 处理。 */
        if (looksLikeLogin(loaded)) throw DetailAuthExpired()
        /* 等正文就绪而不是固定睡眠；上限收紧到详情超时同档，慢页快速放弃、由列表兜底顶上。 */
        WebProbe.awaitBody(evaluate = { evaluate(web, it) })
        if (looksLikeLogin(evaluate(web, "location.href").orEmpty())) throw DetailAuthExpired()
        val text = evaluate(web, "document.body ? document.body.innerText : ''").orEmpty()
        val tracking = TRACKING_LABELED.find(text)?.groupValues?.getOrNull(1)
            ?: TRACKING_CODE.find(text)?.groupValues?.getOrNull(1)
        if (tracking == null) {
            /* Slog.safe 的契约只允许计数/错误码：URL path 可能带订单号，不能进 release 日志。 */
            Slog.safe(TAG, "detail-no-tracking len=${text.length} has-label=${TRACKING_LABELED.containsMatchIn(text)}")
            return null
        }
        Slog.i(TAG) { "detail tracking=${Slog.mask(tracking)} len=${text.length}" }
        return TraceParser.parse(
            platform = Platform.JD,
            accountId = accountId,
            orderSn = order.id,
            trackingNo = tracking,
            text = text,
            at = System.currentTimeMillis(),
            goods = order.goods,
        )
    }

    /**
     * 京东订单列表的 `wareInfoList[0]`（`wareName` / `wareImage`），
     * 价格与店铺、下单时间在 `orderInfo` / `shopInfo` 上。字段缺失就留 null。
     */
    private fun goodsOf(order: JSONObject): GoodsInfo? {
        val ware = order.optJSONArray("wareInfoList")?.optJSONObject(0)
        val info = order.optJSONObject("orderInfo")
        val title = sequenceOf(ware?.optString("wareName"), ware?.optString("skuName"), ware?.optString("name"))
            .firstOrNull { !it.isNullOrBlank() }
        val image = normalizeImageUrl(
            sequenceOf(ware?.optString("wareImage"), ware?.optString("image"), ware?.optString("imgUrl"))
                .firstOrNull { !it.isNullOrBlank() },
        )
        val price = goodsPriceText(
            sequenceOf(ware?.optString("jdPrice"), ware?.optString("price"), info?.optString("orderPrice"))
                .firstOrNull { !it.isNullOrBlank() },
        )
        val shop = sequenceOf(order.optJSONObject("shopInfo")?.optString("shopName"), order.optString("shopName"))
            .firstOrNull { !it.isNullOrBlank() }
        val orderedAt = sequenceOf(
            info?.opt("orderTime"), info?.opt("createTime"), order.opt("orderTime"),
        ).mapNotNull { orderTimeMs(it) }.firstOrNull()
        if (title == null && image == null && price == null && shop == null && orderedAt == null) return null
        return GoodsInfo(title, image, price, shop, orderedAt)
    }

    private suspend fun evaluate(web: WebView, js: String): String? =
        withTimeoutOrNull(6_000) {
            suspendCancellableCoroutine { cont ->
                web.evaluateJavascript(js) { value ->
                    if (cont.isActive) cont.resume(decodeJsString(value))
                }
            }
        }

    /** 返回这一轮抓取缓冲是否读得到；读不到但页面没发请求就是失败，不是「没有件」。 */
    private suspend fun readsCaps(web: WebView, out: MutableList<String>): Boolean {
        val raw = evaluate(
            web,
            "(function(){var a=window.__jdcaps||[];window.__jdcaps=[];return JSON.stringify(a);})()",
        ) ?: return false
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return false
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.contains("orderList")) out.add(s)
        }
        return true
    }

    /**
     * 轮询捕获缓冲区，直到「有新增且安静了」或到 [maxMs]。
     *
     * @return 新增条数；-1 = 页面脚本根本读不到（和「读到了但这次没数据」要分开）。
     */
    private suspend fun awaitCaptures(
        web: WebView,
        out: MutableList<String>,
        maxMs: Long,
        quietMs: Long = 650,
    ): Int {
        val start = SystemClock.uptimeMillis()
        val before = out.size
        var last = before
        var lastGrowAt = start
        var readable = false
        while (true) {
            if (readsCaps(web, out)) readable = true
            val now = SystemClock.uptimeMillis()
            if (out.size > last) {
                last = out.size
                lastGrowAt = now
            }
            if (out.size > before && now - lastGrowAt >= quietMs) return out.size - before
            if (now - start >= maxMs) return if (readable) out.size - before else -1
            delay(120)
        }
    }

    private fun drain(pages: Channel<String>) {
        while (pages.tryReceive().isSuccess) Unit
    }

    private fun swipeUp(web: WebView) {
        val w = web.width
        val h = web.height
        if (w <= 0 || h <= 0) return
        val x = w / 2f
        val down = SystemClock.uptimeMillis()
        fun obtain(action: Int, y: Float, at: Long) = MotionEvent.obtain(down, at, action, x, y, 0)
        web.dispatchTouchEvent(obtain(MotionEvent.ACTION_DOWN, h * 0.9f, down))
        for (i in 1..8) {
            web.dispatchTouchEvent(obtain(MotionEvent.ACTION_MOVE, h * (0.9f - 0.11f * i), down + i * 30L))
        }
        web.dispatchTouchEvent(obtain(MotionEvent.ACTION_UP, h * 0.08f, down + 270L))
    }

    private val HOOK_JS = """
        (function(){
          /* 只留订单/物流响应：条数与单条体积都压到必要水位。
             缓冲放在独立属性里，**不动** window.name——页面自己也用它传数据。 */
          var CAP = 12, MAX = 60000;
          function buf(){
            var a = window.__jdcaps;
            if (!a) { a = []; window.__jdcaps = a; }
            return a;
          }
          function wanted(u, s){
            var url = String(u || '');
            if (/orderlist|order|deal_wuliu|wuliu|logistic|waybill|express/i.test(url)) return true;
            return /"orderId"|"orderList"|"orderInfo"|"wuliu"|"waybill"|"logistics"|"delivery"/i
              .test(String(s || '').slice(0, 2048));
          }
          function push(s){
            try {
              var a = buf();
              a.push(String(s || '').slice(0, MAX));
              if (a.length > CAP) a.splice(0, a.length - CAP);
            } catch(e) {}
          }
          window.__jdcb = function(data){
            try {
              var s = JSON.stringify(data);
              if (wanted('', s)) push(s);
            } catch(e) {}
          };
          function rewrite(v){
            if (v && /callback=/.test(String(v))) {
              return String(v).replace(/callback=[a-zA-Z0-9_]+/, 'callback=__jdcb');
            }
            return v;
          }
          var origCreate = document.createElement.bind(document);
          document.createElement = function(tag){
            var el = origCreate(tag);
            if (String(tag).toLowerCase() === 'script') {
              var origSet = el.setAttribute.bind(el);
              el.setAttribute = function(k, v){ if (k === 'src') v = rewrite(v); return origSet(k, v); };
            }
            return el;
          };
          try {
            var desc = Object.getOwnPropertyDescriptor(HTMLScriptElement.prototype, 'src');
            Object.defineProperty(HTMLScriptElement.prototype, 'src', {
              get: function(){ return desc.get.call(this); },
              set: function(v){ return desc.set.call(this, rewrite(v)); },
              configurable: true
            });
          } catch(e) {}
          var oo = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function(m,u){ this.__u=u; return oo.apply(this,arguments); };
          var os = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.send = function(){
            var self=this, url=self.__u;
            this.addEventListener('load', function(){
              try { if (wanted(url, self.responseText)) push(self.responseText); } catch(e) {}
            });
            return os.apply(this,arguments);
          };
          var of = window.fetch;
          if (of) {
            window.fetch = function(input, init){
              var u = (typeof input === 'string') ? input : ((input && input.url) || '');
              var p = of.apply(this, arguments);
              if (p && p.then) {
                p.then(function(r){
                  try {
                    r.clone().text().then(function(t){ if (wanted((r && r.url) || u, t)) push(t); });
                  } catch(e) {}
                });
              }
              return p;
            };
          }
        })();
    """.trimIndent()
}
