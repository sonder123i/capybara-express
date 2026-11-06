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
import dev.showcode.core.Slog
import dev.showcode.core.account.FetchOutcome
import dev.showcode.core.account.FailureKind
import dev.showcode.core.account.FetchedParcel
import dev.showcode.core.account.GoodsInfo
import dev.showcode.core.account.Platform
import dev.showcode.core.account.Session
import dev.showcode.core.account.TraceParser
import dev.showcode.core.account.allDetailsFailed
import dev.showcode.core.account.goodsPriceText
import dev.showcode.core.account.isBlockedOrderPrompt
import dev.showcode.core.account.isFinishedOrderPrompt
import dev.showcode.core.account.normalizeImageUrl
import dev.showcode.core.account.orderTimeMs
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
    private val HOSTS = setOf(
        "https://trade.m.jd.com",
        "https://wqs.jd.com",
        "https://www.jd.com",
        "https://api.m.jd.com",
    )

    suspend fun fetch(activity: Activity, accountId: String, cookie: String): FetchOutcome =
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
                WebViewCompat.addDocumentStartJavaScript(web, HOOK_JS, HOSTS)
                web.loadUrl(ORDER_CENTER)
                val first = withTimeoutOrNull(20_000) { pages.receive() }
                    ?: return@withContext FetchOutcome.Failure("订单页加载超时")
                /* 跳到通行证页才是登录失效的硬证据；其它意外跳转按瞬时失败处理。 */
                if (looksLikeLogin(first)) {
                    return@withContext FetchOutcome.Failure(
                        "订单页跳到了登录页", FailureKind.AUTH,
                    )
                }
                httpCode?.let {
                    return@withContext FetchOutcome.Failure(
                        "订单页没打开：HTTP $it（$httpUrl）", httpFailureKind(it),
                    )
                }

                val captures = mutableListOf<String>()
                var nameReadable = false
                repeat(6) { round ->
                    if (round > 0) swipeUp(web)
                    delay(2500)
                    if (readsCaps(web, captures)) nameReadable = true
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
                val history = orders.filter { closesOrder(it.status) }.map { it.id }
                val candidates = orders.filter { needsDetail(it) }
                    .sortedBy { detailRank(it.status) }
                    .take(MAX_DETAILS)
                Slog.safe(TAG, "orders=${orders.size} candidates=${candidates.size} history=${history.size}")
                val result = mutableListOf<FetchedParcel>()
                var detailsFailed = 0
                candidates.forEach { order ->
                    val item = try {
                        detail(web, pages, accountId, order)
                    } catch (t: Throwable) {
                        Slog.w(TAG, t) { "detail failed ${Slog.mask(order.id)}" }
                        null
                    }
                    if (item != null) result.add(item) else detailsFailed++
                }
                /* 一条都没解析出来时说「成功·没有新件」等于骗人：这批发失败，可重试。 */
                if (allDetailsFailed(candidates.size, detailsFailed)) {
                    return@withContext FetchOutcome.Failure(
                        "${detailsFailed} 件详情都没抓到，稍后再试",
                    )
                }
                /* 只在接口给出「没有下一页」的结束信号时才交 seen：
                   抓取不完整时保持空集，不让退休逻辑误删没翻到的在途件。 */
                val seen = if (listEnded(captures, orders.size)) {
                    orders.map { it.id }.toSet()
                } else {
                    emptySet()
                }
                FetchOutcome.Success(result, history, seen, detailsFailed)
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

    private data class Order(
        val id: String,
        val status: String,
        val trackLink: String,
        val goods: GoodsInfo? = null,
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
        FetcherCookies.clear(HOSTS)
        CookieManager.getInstance().let { cm ->
            Session.parse(cookie).values.forEach { (k, v) ->
                HOSTS.forEach { host -> cm.setCookie(host, "$k=$v") }
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
                    out[id] = Order(id, status, track, goodsOf(o))
                }
            }
        }
        return out.values.toList()
    }

    /**
     * 历史/阻断状态：不进详情、但要让库里同号的在途卡退休。
     * 词表在 core 里，和拼多多共用一套口径，不在这里写死平台名。
     */
    private fun closesOrder(status: String): Boolean =
        isBlockedOrderPrompt(status) || isFinishedOrderPrompt(status)

    /** 待付款/未支付的单根本没有物流可抓，别浪费详情名额。 */
    private fun needsDetail(order: Order): Boolean =
        !closesOrder(order.status) &&
            !order.status.contains("待付款") && !order.status.contains("未支付")

    /** 真正待取/在途的排在前面；历史单已经在外面被剔除了。 */
    private fun detailRank(status: String): Int = when {
        status.contains("待取") || status.contains("已到") -> 0
        status.contains("派件") || status.contains("派送") || status.contains("待收货") -> 1
        status.contains("运输") || status.contains("已发货") || status.contains("已发出") ||
            status.contains("已揽收") -> 2
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
        if (order.trackLink.isBlank()) return null
        drain(pages)
        web.loadUrl(order.trackLink)
        val loaded = withTimeoutOrNull(20_000) { pages.receive() } ?: return null
        if (!loaded.startsWith("http")) return null
        delay(2200)
        val text = evaluate(web, "document.body ? document.body.innerText : ''").orEmpty()
        val tracking = Regex("(?:运单号|快递单号|物流单号)[：:\\s]*([A-Za-z0-9\\-]{8,})")
            .find(text)?.groupValues?.getOrNull(1)
            ?: Regex("(JD[VA][0-9]{8,}|[A-Z]{2,4}[0-9]{8,})").find(text)?.groupValues?.getOrNull(1)
            ?: return null
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
