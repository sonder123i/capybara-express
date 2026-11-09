package dev.showcode.data.fetch

import android.app.Activity
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.showcode.core.Slog
import dev.showcode.core.account.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

/**
 * 淘宝第二数据源：用 WebView 打开 H5 订单页，读取页面自己发出的订单响应。
 *
 * 它不伪造任何 mtop 签名/风控参数——签名由页面 JS 生成，我们只是把响应抄下来。
 * 用途只有一个：mtop 主接口改版/被风控/返回空时，仍能拿到订单列表。
 *
 * 只在 [TbFetcher.fetch] 主源失败或为空时调用；拿不到就返回 null，不抛。
 */
internal object TbWebSource {

    private const val TAG = "TbWebSource"
    private const val ORDERS = "https://h5.m.taobao.com/order/index.htm?tabCode=all"

    private val HOSTS = setOf(
        "https://h5.m.taobao.com",
        "https://h5api.m.taobao.com",
        "https://trade.m.taobao.com",
        "https://www.taobao.com",
        "https://taobao.com",
        "https://m.taobao.com",
    )

    suspend fun fetchOrders(activity: Activity, cookie: String): List<TbFetcher.Order>? =
        withContext(Dispatchers.Main) {
            if (cookie.isBlank()) return@withContext null
            val pages = Channel<String>(Channel.BUFFERED)
            val web = WebView(activity)
            try {
                prepare(activity, web, cookie, pages)
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    Slog.i(TAG) { "document-start unsupported" }
                    return@withContext null
                }
                WebViewCompat.addDocumentStartJavaScript(web, HOOK_JS, HOSTS)
                web.loadUrl(ORDERS)
                /* 页面加载完成不是必须条件：首屏可能很慢，但订单接口通常先发。最多等 8 秒。 */
                withTimeoutOrNull(8_000) { pages.receive() }
                delay(1_200)

                val captures = mutableListOf<String>()
                val start = SystemClock.uptimeMillis()
                var lastSize = 0
                var quietSince = start
                while (SystemClock.uptimeMillis() - start < 8_000) {
                    val raw = evaluate(web, READ_JS) ?: break
                    val arr = runCatching { JSONArray(raw) }.getOrNull()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val s = arr.optJSONObject(i)?.optString("res").orEmpty()
                            if (s.isNotBlank()) captures.add(s)
                        }
                    }
                    val now = SystemClock.uptimeMillis()
                    if (captures.size > lastSize) {
                        lastSize = captures.size
                        quietSince = now
                    }
                    if (captures.isNotEmpty() && now - quietSince >= 800) break
                    delay(200)
                }
                val orders = captures
                    .mapNotNull { raw -> runCatching { TbFetcher.parseOrderResponse(raw) }.getOrNull() }
                    .flatMap { it.orders }
                    .distinctBy { it.id }
                Slog.safe(TAG, "captures=${captures.size} orders=${orders.size}")
                orders.ifEmpty { null }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Slog.w(TAG, t) { "fetchOrders failed" }
                null
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
    ) {
        WebView.setWebContentsDebuggingEnabled(false)
        web.setBackgroundColor(Color.WHITE)
        /* 只借它发请求：不可见、不抢焦点，别让订单页在同步时闪一下。 */
        web.visibility = View.INVISIBLE
        web.isFocusable = false
        web.isClickable = false
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
                HOSTS.forEach { host -> cm.setCookie(host, "$k=$v; path=/") }
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
        }
    }

    private suspend fun evaluate(web: WebView, js: String): String? =
        withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                web.evaluateJavascript(js) { value ->
                    if (cont.isActive) cont.resume(decodeJsString(value))
                }
            }
        }

    private const val READ_JS =
        "(function(){var a=window.__tbcaps||[];window.__tbcaps=[];return JSON.stringify(a);})()"

    /**
     * 捕获页面自己发出的订单响应。
     * - fetch / XHR：直接读 responseText；
     * - mtop JSONP：把 script src 的 callback 改写成 __tbcb，由它把 JSON 推进缓冲。
     * 只保留看起来像订单的响应，避免把广告/埋点塞满缓冲。
     */
    private val HOOK_JS = """
        (function(){
          if (window.__tbHook) return;
          window.__tbHook = true;
          var CAP = 20, MAX = 200000;
          function buf(){ var a = window.__tbcaps; if (!a) { a = []; window.__tbcaps = a; } return a; }
          function push(u, s){
            try {
              var t = String(s || '');
              if (!/orderId|orderList|mainOrders|orderStatus|orderlist/i.test(t)) return;
              var a = buf();
              a.push({ u: String(u || '').slice(0, 300), res: t.slice(0, MAX) });
              if (a.length > CAP) a.splice(0, a.length - CAP);
            } catch (e) {}
          }
          window.__tbcb = function(data){ try { push('jsonp', JSON.stringify(data)); } catch (e) {} };
          function rewrite(v){
            if (v && /callback=/.test(String(v))) {
              return String(v).replace(/callback=[^&]+/, 'callback=__tbcb');
            }
            return v;
          }
          try {
            var desc = Object.getOwnPropertyDescriptor(HTMLScriptElement.prototype, 'src');
            Object.defineProperty(HTMLScriptElement.prototype, 'src', {
              get: function(){ return desc.get.call(this); },
              set: function(v){ return desc.set.call(this, rewrite(v)); },
              configurable: true
            });
          } catch (e) {}
          var origCreate = document.createElement.bind(document);
          document.createElement = function(tag){
            var el = origCreate(tag);
            if (String(tag).toLowerCase() === 'script') {
              var origSet = el.setAttribute.bind(el);
              el.setAttribute = function(k, v){ if (k === 'src') v = rewrite(v); return origSet(k, v); };
            }
            return el;
          };
          var of = window.fetch;
          if (of) {
            window.fetch = function(input, init){
              var u = (typeof input === 'string') ? input : ((input && input.url) || '');
              var p = of.apply(this, arguments);
              if (p && p.then) {
                p.then(function(r){
                  try { r.clone().text().then(function(t){ push(u, t); }); } catch (e) {}
                });
              }
              return p;
            };
          }
          var oo = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function(m, u){ this.__tbu = u; return oo.apply(this, arguments); };
          var os = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.send = function(b){
            var x = this;
            x.addEventListener('load', function(){ try { push(x.__tbu, x.responseText); } catch (e) {} });
            return os.apply(this, arguments);
          };
        })();
    """.trimIndent()
}
