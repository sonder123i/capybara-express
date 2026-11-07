package dev.showcode.data.fetch

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
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
import dev.showcode.core.account.GoodsImageCandidate
import dev.showcode.core.account.GoodsInfo
import dev.showcode.core.account.Platform
import dev.showcode.core.account.Session
import dev.showcode.core.account.SyncPhase
import dev.showcode.core.account.SyncProgress
import dev.showcode.core.account.TraceParser
import dev.showcode.core.account.allDetailsFailed
import dev.showcode.core.account.conflictsWithOrderStatus
import dev.showcode.core.account.detailNeeded
import dev.showcode.core.account.fallbackFetchedParcel
import dev.showcode.core.account.isActiveOrderPrompt
import dev.showcode.core.account.isBlockedOrderPrompt
import dev.showcode.core.account.isFinishedOrderPrompt
import dev.showcode.core.account.isTerminalTrackStatus
import dev.showcode.core.account.normalizeImageUrl
import dev.showcode.core.account.orderTimeMs
import dev.showcode.core.account.pddPriceText
import dev.showcode.core.account.pickGoodsImage
import dev.showcode.core.account.stageOfPrompt
import dev.showcode.core.account.trackStatusPrompt
import dev.showcode.core.account.trackStatusStage
import kotlinx.coroutines.CompletableDeferred
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
 * 拼多多 H5 抓取。
 *
 * 它只做一件事：把官方页面自己发出去的订单/物流响应读出来。签名、anti_content 都由页面 JS 生成，
 * 这里不伪造任何请求。WebView 全尺寸挂在 decor 最底层，不可见但保留正常视口——
 * 1×1 会把虚拟列表饿死（P0 的差异清单里写了）。
 */
object PddFetcher {

    private const val TAG = "PddFetcher"
    /* 和原来那两处固定睡眠对齐：上限不变，只是数据到了就提前返回。 */
    private const val FIRST_SCREEN_WAIT_MS = 2_500L
    private const val PAGE_WAIT_MS = 2_500L
    private const val HOST = "https://mobile.yangkeduo.com"
    private const val ORDERS = "$HOST/orders.html"
    private const val ORDER_DETAIL = "$HOST/order.html?order_sn="

    private val HOSTS = setOf(
        "https://mobile.yangkeduo.com",
        "https://yangkeduo.com",
        "https://pinduoduo.com",
        "https://mobile.pinduoduo.com",
        "https://passport.pinduoduo.com",
    )

    suspend fun fetch(
        activity: Activity,
        accountId: String,
        cookie: String,
        /** 库里这个账号已有的件（键 = 订单号）。状态没变的单不再进详情页。 */
        known: Map<String, Parcel> = emptyMap(),
        /** 这一轮走到哪一步：顶栏的「读取详情 2/4」就是它。 */
        onProgress: suspend (SyncProgress) -> Unit = {},
    ): FetchOutcome =
        withContext(Dispatchers.Main) {
            val pages = Channel<String>(Channel.BUFFERED)
            val web = WebView(activity)
            /* 只记「主文档 / 订单物流接口」的错误：广告图、埋点、字体的 404 与同步无关。 */
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
                WebViewCompat.addDocumentStartJavaScript(web, HOOK_JS, HOSTS)
                web.loadUrl(ORDERS)
                var first = withTimeoutOrNull(20_000) { pages.receive() }
                    ?: return@withContext FetchOutcome.Failure("订单页加载超时")
                httpCode?.let {
                    return@withContext FetchOutcome.Failure(
                        "订单页没打开：HTTP $it（$httpUrl）", httpFailureKind(it),
                    )
                }
                if (!isPddOrdersPage(first)) {
                    Slog.w(TAG) { "订单页没打开，重试一次: $first" }
                    /* 拼多多偶尔把一次正常请求带去登录页（风控/会话刷新），紧接着重载又好了。
                       一次重定向不足以判死会话：再要一次，仍不是订单页才认。 */
                    drain(pages)
                    delay(700)
                    web.loadUrl(ORDERS)
                    val again = withTimeoutOrNull(20_000) { pages.receive() }
                        ?: return@withContext FetchOutcome.Failure("订单页加载超时")
                    if (!isPddOrdersPage(again)) {
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
                Slog.i(TAG) { "orders page=$first" }

                val captures = mutableListOf<JSONObject>()
                var nameReadable = false
                /* 首屏保留固定等待：虚拟列表要先把首屏渲染出来才吃得下滑动。
                   从这里往后才是条件等待——每轮有响应就立刻进下一轮，没有就收工。 */
                delay(FIRST_SCREEN_WAIT_MS)
                if (readsCaps(web, captures)) nameReadable = true
                var rounds = 0
                while (rounds < 4) {
                    clickMore(web)
                    swipeUp(web)
                    rounds++
                    /* 这一轮一个新响应都没有：更靠后的页不会自己出现，直接收工。
                       上限仍是每轮 2.5s，所以最坏情况和原来的固定睡眠一样慢。 */
                    if (awaitCaptures(web, captures, PAGE_WAIT_MS) <= 0) break
                }
                httpCode?.let {
                    return@withContext FetchOutcome.Failure(
                        "订单接口请求失败：HTTP $it（$httpUrl）", httpFailureKind(it),
                    )
                }

                /* 钩子一次都没读到、且两个订单来源都没有响应：页面没按预期发请求。
                   这和「页面通了、这次确实没有件」不是一回事，必须报失败。 */
                val orderResponses = captures.count { it.optString("u").contains("order_list") }
                val trackResponses = captures.count { it.optString("u").contains("express/track/status") }
                if (orderResponses == 0 && trackResponses == 0) {
                    return@withContext FetchOutcome.Failure(
                        if (!nameReadable) {
                            "页面脚本没有响应（window.name 读不到）"
                        } else {
                            "订单接口没有返回数据（可能已改版）"
                        },
                    )
                }

                /* 两条来源互补：
                   - order_list_v4 是「全部」列表，只把还在路上的单当候选；
                   - express/track/status 是首屏「待收货」区实际用的接口，A/B 只在它里面。
                   两条来源都要先过订单状态交叉校验，才允许进在途/待取。 */
                val listOrders = parseOrders(captures)
                val tracks = trackEntries(captures)
                Slog.safe(TAG, "orders=${listOrders.size} tracks=${tracks.size}")

                /* 交叉校验的第一层：订单列表里同一 order_sn 的状态不是「待收货」口径的，
                   轨迹再新也不许进在途区（取消单残留的 DEPARTURE 就在这里被拦住）。 */
                val promptBySn = listOrders.associate { it.sn to it.prompt }
                tracks.filter { conflictsWithOrderStatus(it.sn, promptBySn) }.forEach { t ->
                    Slog.i(TAG) {
                        "dropTrack sn=${Slog.mask(t.sn)} track=${t.status} reason=order:${promptBySn[t.sn]}"
                    }
                }
                val activeTracks = tracks.filter {
                    !isTerminalTrackStatus(it.status) && !conflictsWithOrderStatus(it.sn, promptBySn)
                }
                val activeList = listOrders.filter { isActiveOrderPrompt(it.prompt) }
                val historySns = (
                    tracks.filter { isTerminalTrackStatus(it.status) }.map { it.sn } +
                        listOrders.filter {
                            isFinishedOrderPrompt(it.prompt) || isBlockedOrderPrompt(it.prompt)
                        }.map { it.sn }
                    ).distinct()

                /* 本次实际「看见」的订单号：待收货 + 订单列表。库里上次进来、
                   这次两处都不见的在途行，是残留轨迹（如取消后接口不再返回的单）。
                   但只有订单接口明确说「没有下一页」时才算看全；看不全就交空集，
                   退休逻辑不会误删没翻到的在途件。 */
                val seenSns = (tracks.map { it.sn } + listOrders.map { it.sn }).toSet()
                val ordersEnded = ordersEnded(captures, listOrders.size)
                Slog.safe(TAG, "ordersEnded=$ordersEnded")

                val candidates = buildList {
                    activeTracks.forEach { t ->
                        add(
                            OrderHit(
                                sn = t.sn,
                                prompt = trackStatusPrompt(t.status),
                                goods = null,
                                /* 首屏这个接口的状态比订单文案更准，直接拿它和库里阶段比。 */
                                stage = trackStatusStage(t.status),
                            )
                        )
                    }
                    activeList.forEach { hit -> add(hit) }
                }.distinctBy { it.sn }

                val result = mutableListOf<FetchedParcel>()
                var detailsFailed = 0
                val now = System.currentTimeMillis()
                /* 状态没变、运单号与码都在的件不再进详情页：那是这次刷新里最贵的一步，
                   而它这次拿回来的会和上次是同一段文字。判据见 core/account/DetailGate.kt。 */
                val toFetch = candidates.filter { detailNeeded(it.sn, it.stage, known, now) }
                val unchanged = candidates.size - toFetch.size
                toFetch.forEachIndexed { index, order ->
                    onProgress(SyncProgress(SyncPhase.DETAILS, index + 1, toFetch.size))
                    /* 单条详情超时/改版不能把整批已拿到的件一起丢掉；
                       一条失败就继续下一条，最后把成功的那部分交出去。 */
                    val item = try {
                        fetchTrace(web, pages, accountId, order)
                    } catch (t: Throwable) {
                        Slog.w(TAG, t) { "trace failed ${Slog.mask(order.sn)}" }
                        null
                    } ?: fallbackFetchedParcel(
                        platform = Platform.PDD,
                        accountId = accountId,
                        orderSn = order.sn,
                        prompt = order.prompt,
                        goods = order.goods,
                        at = System.currentTimeMillis(),
                    )
                    if (item != null) result.add(item) else detailsFailed++
                }
                Slog.safe(TAG, "skip unchanged=$unchanged")
                /* 一条都没解析出来时不能说成「成功·没有新件」；这批算瞬时失败，可重试。 */
                if (allDetailsFailed(toFetch.size, detailsFailed)) {
                    return@withContext FetchOutcome.Failure(
                        "${detailsFailed} 件详情都没抓到，稍后再试",
                    )
                }
                Slog.safe(TAG, "candidates=${candidates.size} parcels=${result.size} history=${historySns.size}")
                FetchOutcome.Success(
                    result,
                    historySns,
                    if (ordersEnded) seenSns else emptySet(),
                    detailsFailed,
                )
            } catch (t: Throwable) {
                Slog.w(TAG, t) { "fetch failed" }
                FetchOutcome.Failure("抓取中断：${t.message ?: "未知错误"}")
            } finally {
                runCatching {
                    (web.parent as? ViewGroup)?.removeView(web)
                    web.loadUrl("about:blank")
                    web.destroy()
                }
                FetcherCookies.clear(HOSTS)
            }
        }

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
        s.userAgentString = WebSettings.getDefaultUserAgent(activity)
            .replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)")
            .replace("Version/4.0 ", "")
            .replace(" Mobile", "")

        FetcherCookies.clear(HOSTS)
        CookieManager.getInstance().let { cm ->
            Session.parse(cookie).values.forEach { (k, v) ->
                cm.setCookie(ORDERS, "$k=$v; path=/")
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

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (!url.startsWith("http")) return true
                if (url.contains("express_service.html")) return true
                return false
            }

            /* HTTP 非 2xx 不会抛异常：不接这个回调就只能等到超时再猜。 */
            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?,
            ) {
                val code = errorResponse?.statusCode ?: return
                val url = request?.url?.toString().orEmpty()
                /* 广告图、埋点、字体的 404 不该把整次同步打成失败；只有主文档和
                   订单/物流接口算数。 */
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

    private data class TrackEntry(val sn: String, val status: String)

    /** 只有订单列表/物流详情这类接口的失败才值得让整次同步失败。 */
    private fun isOrderEndpoint(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("order_list") || u.contains("express/track/status") ||
            u.contains("order.html") || u.contains("goods_express.html")
    }

    /** 「待收货」列表不走 order_list：首屏 HTML 用 express/track/status 拿每个单的物流状态。 */
    private fun trackEntries(captures: List<JSONObject>): List<TrackEntry> {
        val out = mutableListOf<TrackEntry>()
        captures.forEach { cap ->
            if (!cap.optString("u").contains("express/track/status")) return@forEach
            val parsed = runCatching { JSONTokener(cap.optString("res")).nextValue() }.getOrNull()
                ?: return@forEach
            val list = (parsed as? JSONObject)?.optJSONArray("result_list") ?: return@forEach
            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                val sn = o.optString("order_sn")
                if (sn.isNotBlank()) out.add(TrackEntry(sn, o.optString("status")))
            }
        }
        return out.distinctBy { it.sn }
    }

    private data class OrderHit(
        val sn: String,
        val prompt: String,
        val goods: GoodsInfo?,
        /** 列表级阶段；null = 这次没有可用状态，详情门控只能按时间判断。 */
        val stage: Stage? = null,
    )

    private fun parseOrders(captures: List<JSONObject>): List<OrderHit> {
        val hits = mutableListOf<OrderHit>()
        captures.forEach { cap ->
            val url = cap.optString("u")
            if (!url.contains("order_list")) return@forEach
            val parsed = runCatching { JSONTokener(cap.optString("res")).nextValue() }.getOrNull()
                ?: return@forEach
            collect(parsed, hits)
        }
        val unique = hits.distinctBy { it.sn }
        unique.forEach { hit ->
            Slog.i(TAG) { "order sn=${Slog.mask(hit.sn)} prompt=${hit.prompt}" }
        }
        /* 不在解析层丢掉退款/取消单：它们的状态是交叉校验轨迹、退休旧行的重要证据，
           过滤放到组装候选时做。 */
        return unique
    }

    private fun collect(node: Any?, out: MutableList<OrderHit>) {
        when (node) {
            is JSONObject -> {
                val sn = node.optString("order_sn")
                if (sn.isNotBlank() && sn.length >= 8) {
                    val prompt = node.optString("order_status_prompt")
                    out.add(OrderHit(sn, prompt, goodsOf(node), stageOfPrompt(prompt)))
                }
                val keys = node.keys()
                while (keys.hasNext()) collect(node.opt(keys.next()), out)
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out)
        }
    }

    /**
     * 订单列表是不是真的看全了。和淘宝/京东同一条判据：[orderListEnded] 只认接口
     * 自己给的结束信号。任何一页说「没有下一页」就算到底；一页都没拿到、或所有页
     * 都说还有下一页，就当作没看全——此时不给 seen，退休逻辑宁可留着也不误删。
     */
    private fun ordersEnded(captures: List<JSONObject>, seen: Int): Boolean {
        val nodes = captures
            .filter { it.optString("u").contains("order_list") }
            .mapNotNull { cap ->
                runCatching { JSONTokener(cap.optString("res")).nextValue() }.getOrNull() as? JSONObject
            }
        if (nodes.isEmpty()) return false
        return nodes.any { node ->
            orderListEnded(node, seen) ||
                orderListEnded(node.optJSONObject("data"), seen) ||
                orderListEnded(node.optJSONObject("result"), seen)
        }
    }

    private suspend fun fetchTrace(
        web: WebView,
        pages: Channel<String>,
        accountId: String,
        order: OrderHit,
    ): FetchedParcel? {
        drain(pages)
        web.loadUrl(ORDER_DETAIL + Uri.encode(order.sn))
        val loaded = withTimeoutOrNull(15_000) { pages.receive() } ?: return null
        if (!loaded.contains("order.html")) {
            Slog.w(TAG) { "detail miss ${Slog.mask(order.sn)}: $loaded" }
            return null
        }
        /* 商品图是懒加载的：等正文与首图就绪，而不是固定睡 2.2 秒。
           上限仍是 2.2 秒，所以慢页不会比以前更慢。 */
        WebProbe.awaitBody(evaluate = { evaluate(web, it) })
        val detailNode = parseJsJson(evaluate(web, DETAIL_EXTRACT_JS).orEmpty())
        val detailLines = detailNode?.optJSONArray("lines")
        val lead = detailLines?.optString(0).orEmpty()
        val imageCandidates = detailImageCandidates(detailNode)
        val detailGoods = detailNode?.optJSONObject("goods")?.let { g ->
            val title = g.optString("title").takeIf { it.isNotBlank() && it != "null" }
            /* 标题与图必须同源：只用「容器文本里含这同一件标题」的图，否则就是别人的图。 */
            val image = normalizeImageUrl(pickGoodsImage(imageCandidates, title))
            GoodsInfo(
                title = title,
                image = image,
                price = pddPriceText(g.optString("price")),
                shop = g.optString("shop").takeIf { it.isNotBlank() && it != "null" },
                orderedAt = null,
            )
        }
        val goods = order.goods ?: detailGoods
        Slog.i(TAG) {
            "detail ${Slog.mask(order.sn)} goods=${goods?.title} lead=${lead.take(40)}"
        }
        /* 把每张候选图的归属判定摊开：同源才留，推荐位一律 same=false。 */
        val head = goods?.title?.filterNot { it.isWhitespace() }?.take(10)
        imageCandidates.forEachIndexed { idx, c ->
            val same = head != null &&
                c.ctx.filterNot { it.isWhitespace() }.contains(head) &&
                c.area >= 40f * 40f
            Slog.i(TAG) {
                "goodsCandidate sn=${Slog.mask(order.sn)} #$idx area=${c.area.toInt()} same=$same " +
                    "ctx=${c.ctx.replace('\n', ' ').take(24)}"
            }
        }
        Slog.i(TAG) {
            "goodsBind sn=${Slog.mask(order.sn)} source=${if (order.goods != null) "order-list" else "order-detail"} " +
                "candidates=${imageCandidates.size} title=${goods?.title}"
        }

        var clicked = false
        repeat(4) {
            if (clicked) return@repeat
            val r = evaluate(web, CLICK_LOGISTICS_JS).orEmpty()
            Slog.i(TAG) { "click[$it]=$r" }
            if (r.startsWith("clicked")) clicked = true else delay(400)
        }

        val traceUrl = if (clicked) {
            withTimeoutOrNull(20_000) {
                var url = pages.receive()
                while (!url.contains("goods_express.html")) url = pages.receive()
                url
            }
        } else {
            Slog.w(TAG) { "logistics not found ${Slog.mask(order.sn)}" }
            null
        }

        val parsed = if (traceUrl != null) {
            WebProbe.awaitBody(evaluate = { evaluate(web, it) })
            val text = evaluate(web, "document.body ? document.body.innerText : ''").orEmpty()
            val tracking = Uri.parse(traceUrl).getQueryParameter("tracking_number").orEmpty()
            Slog.i(TAG) { "trace tracking=${Slog.mask(tracking)} len=${text.length}" }
            TraceParser.parse(
                platform = Platform.PDD,
                accountId = accountId,
                orderSn = order.sn,
                trackingNo = tracking,
                text = text,
                at = System.currentTimeMillis(),
                goods = goods,
            )
        } else {
            null
        }
        Slog.i(TAG) {
            "parsed sn=${Slog.mask(order.sn)} tracking=${Slog.mask(parsed?.trackingNo)} " +
                "stage=${parsed?.stage} code=${Slog.mask(parsed?.code)}/${parsed?.codeKind} " +
                "point=${parsed?.pointName}"
        }
        /* 轨迹页没点到/解析失败：用详情页拿到的商品与列表阶段先入列，轨迹标记待补。 */
        return parsed ?: fallbackFetchedParcel(
            platform = Platform.PDD,
            accountId = accountId,
            orderSn = order.sn,
            prompt = order.prompt,
            goods = goods,
            at = System.currentTimeMillis(),
        )
    }

    private fun parseJsJson(raw: String): JSONObject? {
        val node = runCatching { JSONTokener(raw).nextValue() }.getOrNull()
        return when (node) {
            is JSONObject -> node
            is String -> runCatching { JSONObject(node) }.getOrNull()
            else -> null
        }
    }

    /* 拼多多的订单对象里，商品字段藏在 goods / goods_list 这类容器里，名字随版本变。
       只在明确的容器下才接受通用的 title / pic，避免把订单本身或店铺的字段当商品。 */
    private val GOODS_CONTAINERS = setOf(
        "goods", "goods_list", "goodsList", "goods_info", "goodsInfo",
        "order_goods", "orderGoods", "items", "item_list", "itemList", "sub_goods", "subGoods",
    )
    private val TITLE_KEYS = listOf(
        "goods_name", "goods_title", "goodsName", "item_title", "itemTitle", "wareName", "ware_name",
    )
    private val IMAGE_KEYS = listOf(
        "goods_thumbnail_url", "goods_thumb_url", "goods_image_url", "goods_img_url",
        "hd_thumb_url", "thumb_url", "image_url", "img_url", "wareImage", "ware_image",
    )
    private val PRICE_KEYS = listOf("goods_price", "goods_price_str", "goodsPrice", "price_str", "price")
    private val SHOP_KEYS = listOf(
        "mall_name", "mall_title", "shop_name", "shopName", "store_name", "storeName",
        "seller_name", "sellerName",
    )
    private val TIME_KEYS = listOf(
        "create_time", "createTime", "order_time", "orderTime",
        "order_create_time", "orderCreateTime", "pay_time", "payTime",
    )

    private fun goodsOf(root: JSONObject): GoodsInfo? {
        var found: GoodsInfo? = null

        fun visit(node: Any?, inContainer: Boolean) {
            if (found != null) return
            when (node) {
                is JSONObject -> {
                    val title = firstText(node, TITLE_KEYS)
                        ?: if (inContainer) firstText(node, listOf("title", "name")) else null
                    val image = normalizeImageUrl(
                        firstText(node, IMAGE_KEYS)
                            ?: if (inContainer) firstText(node, listOf("pic", "image", "img")) else null,
                    )
                    if (title != null || image != null) {
                        found = GoodsInfo(
                            title = title,
                            image = image,
                            price = pddPriceText(firstText(node, PRICE_KEYS)),
                            shop = firstText(node, SHOP_KEYS),
                            orderedAt = orderTimeMs(firstAny(node, TIME_KEYS)),
                        )
                        return
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        visit(node.opt(key), inContainer || key in GOODS_CONTAINERS)
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i), inContainer)
            }
        }

        visit(root, false)
        return found?.let { goods ->
            /* 店铺与下单时间常常挂在订单本身而不是商品对象上。只在商品自己的值为空时补，
               不覆盖它已经给出的值。 */
            goods.copy(
                shop = goods.shop ?: firstText(root, SHOP_KEYS),
                orderedAt = goods.orderedAt ?: orderTimeMs(firstAny(root, TIME_KEYS)),
            )
        }
    }

    private fun firstText(node: JSONObject, keys: List<String>): String? {
        for (key in keys) {
            val value = node.optString(key).trim()
            if (value.isNotEmpty() && value != "null") return value
        }
        return null
    }

    private fun firstAny(node: JSONObject, keys: List<String>): Any? {
        for (key in keys) {
            if (node.has(key) && !node.isNull(key)) return node.opt(key)
        }
        return null
    }

    /* evaluateJavascript 的回调在页面卡死时可能永远不来；超时即视为「读不到」。 */
    private suspend fun evaluate(web: WebView, js: String): String? =
        withTimeoutOrNull(6_000) {
            suspendCancellableCoroutine { cont ->
                web.evaluateJavascript(js) { value ->
                    if (cont.isActive) cont.resume(jsString(value))
                }
            }
        }

    private fun jsString(value: String?): String? {
        if (value == null || value == "null") return null
        return runCatching {
            val v = JSONTokener(value).nextValue()
            if (v is String) v else value
        }.getOrDefault(value)
    }

    /** 返回这一轮 window.name 是否读得到（超时/空值/坏 JSON 都算读不到）。 */
    private suspend fun readsCaps(web: WebView, out: MutableList<JSONObject>): Boolean {
        val raw = evaluate(web, "(function(){var s=window.name||'';window.name='';return s;})()")
            ?: return false
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return false
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { out.add(it) }
        }
        return true
    }

    /**
     * 轮询 `window.name`，直到「有新增捕获且安静了」或到 [maxMs]。
     *
     * @return 新增条数；-1 = 页面脚本根本读不到（和「读到了但这次没数据」要分开）。
     */
    private suspend fun awaitCaptures(
        web: WebView,
        out: MutableList<JSONObject>,
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
            /* 已经有新数据，并且安静了 quietMs：这一页读完了。 */
            if (out.size > before && now - lastGrowAt >= quietMs) return out.size - before
            if (now - start >= maxMs) return if (readable) out.size - before else -1
            delay(120)
        }
    }

    private fun drain(pages: Channel<String>) {
        while (pages.tryReceive().isSuccess) Unit
    }

    /* 详情页没有结构化商品接口可依赖（订单 JSON 只在列表里），
       所以从正文行里取：价格行往前两行是标题、三行是店铺。
       图**不**在这里做「最大/第一张」兜底——详情页下方是推荐商品流，那样必然抓错。
       只把每张图连同它所在商品块（第一个含价格的祖先）的文本交回 Kotlin，
       由 pickGoodsImage 按标题同源判定。 */
    private val DETAIL_EXTRACT_JS = """
        (function(){
          var lines = document.body ? document.body.innerText.split('\n')
            .map(function(x){return x.trim();}).filter(function(x){return x.length>0;}) : [];
          var pi = -1;
          for (var i=0;i<lines.length;i++){ if (/^[￥¥]/.test(lines[i])) { pi = i; break; } }
          var images = [];
          var els = document.querySelectorAll('img');
          for (var i=0;i<els.length;i++){
            var el = els[i];
            var src = el.currentSrc || el.src || el.getAttribute('data-src') || '';
            if (src.indexOf('http') !== 0) continue;
            var r = el.getBoundingClientRect();
            if (r.width < 40 || r.height < 40) continue;
            var ctx = '';
            var p = el.parentElement;
            for (var d=0; d<6 && p; d++){
              var t = p.innerText || '';
              if (/[￥¥]/.test(t)) { ctx = t; break; }
              p = p.parentElement;
            }
            if (ctx) images.push({src: src, area: r.width * r.height, ctx: ctx.slice(0, 160)});
            if (images.length >= 24) break;
          }
          var goods = null;
          if (pi >= 2) goods = {title: lines[pi-2], shop: pi >= 3 ? lines[pi-3] : '', price: lines[pi]};
          return JSON.stringify({lines: lines.slice(0, 4), goods: goods, images: images});
        })();
    """.trimIndent()

    /** 详情页只交回候选图，不预设归属；这里只做 JSON 拆包，同源判定在 pickGoodsImage。 */
    private fun detailImageCandidates(node: JSONObject?): List<GoodsImageCandidate> {
        val arr = node?.optJSONArray("images") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val src = o.optString("src")
                if (src.isBlank()) continue
                add(GoodsImageCandidate(src, o.optDouble("area", 0.0).toFloat(), o.optString("ctx")))
            }
        }
    }

    private fun clickMore(web: WebView) {
        web.evaluateJavascript(
            """(function(){
              var els = document.querySelectorAll('div,span,button,a');
              for (var i=0;i<els.length;i++){
                var t=(els[i].innerText||'').trim();
                var r=els[i].getBoundingClientRect();
                if (r.width>0 && r.height>0 && (t==='加载更多'||t==='查看更多'||t==='加载更多订单')) {
                  els[i].scrollIntoView({block:'center'}); els[i].click(); return t;
                }
              }
              return 'none';
            })()""",
            null,
        )
    }

    private fun swipeUp(web: WebView) {
        val w = web.width
        val h = web.height
        if (w <= 0 || h <= 0) return
        val x = w / 2f
        val start = h * 0.82f
        val end = h * 0.22f
        val down = SystemClock.uptimeMillis()
        fun obtain(action: Int, y: Float, at: Long) = MotionEvent.obtain(down, at, action, x, y, 0)
        web.dispatchTouchEvent(obtain(MotionEvent.ACTION_DOWN, start, down))
        for (i in 1..8) {
            val at = down + i * 20L
            web.dispatchTouchEvent(obtain(MotionEvent.ACTION_MOVE, start + (end - start) * i / 8f, at))
        }
        web.dispatchTouchEvent(obtain(MotionEvent.ACTION_UP, end, down + 180L))
    }

    private val HOOK_JS = """
        (function () {
          if (window.__pddHook) return;
          window.__pddHook = true;
          function push(u, req, res) {
            try {
              var s = String(u || '');
              if (!/order|express|logistic|goods_express|track/i.test(s)) return;
              var arr = [];
              if (window.name && window.name.charAt(0) === '[') {
                try { arr = JSON.parse(window.name); } catch (e) { arr = []; }
              }
              arr.push({
                u: s.slice(0, 300),
                req: String(req || '').slice(0, 1200),
                res: String(res || '').slice(0, 100000)
              });
              if (arr.length > 40) arr = arr.slice(arr.length - 40);
              window.name = JSON.stringify(arr);
            } catch (e) {}
          }
          var of = window.fetch;
          if (of) {
            window.fetch = function (input, init) {
              var url = (typeof input === 'string') ? input : ((input && input.url) || '');
              var body = (init && init.body) ? String(init.body) : '';
              var p = of.apply(this, arguments);
              p.then(function (resp) {
                try {
                  var c = resp.clone();
                  c.text().then(function (t) { push(url, body, t); }).catch(function () {});
                } catch (e) {}
              }).catch(function () {});
              return p;
            };
          }
          var oo = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function (m, u) {
            this.__pddUrl = String(u);
            return oo.apply(this, arguments);
          };
          var os = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.send = function (b) {
            var x = this;
            x.addEventListener('load', function () {
              try { push(x.__pddUrl, b, x.responseText); } catch (e) {}
            });
            return os.apply(this, arguments);
          };
        })();
    """.trimIndent()

    private val CLICK_LOGISTICS_JS = """
        (function () {
          var nodes = Array.prototype.slice.call(document.querySelectorAll('div,span,button,a,p,label'));
          var best = null, bestH = 1e9;
          for (var i = 0; i < nodes.length; i++) {
            var t = (nodes[i].innerText || '').trim();
            var r = nodes[i].getBoundingClientRect();
            if (r.width <= 0 || r.height <= 0) continue;
            var ok = (t === '查看物流' || t === '查物流' || t === '物流详情' || t === '物流信息' ||
                      (t.indexOf('物流') >= 0 && t.length <= 12));
            if (!ok) continue;
            if (r.height < bestH) { bestH = r.height; best = nodes[i]; }
          }
          if (!best) return 'notfound';
          try { best.scrollIntoView({ block: 'center' }); } catch (e) {}
          var target = best.closest('[role="button"],button,a') || best;
          target.click();
          return 'clicked:' + (best.innerText || '').slice(0, 20);
        })();
    """.trimIndent()
}
