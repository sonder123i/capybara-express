package dev.showcode.debug

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * P0 可行性探针。**只在 debug 变体**，release 不带 INTERNET、不带这段代码。
 *
 * 它要回答的问题只有四个：
 * 1. 拼多多 H5 登录在 WebView 里能不能走完；
 * 2. document-start 注入能不能拿到 order_list 的真实响应；
 * 3. 触摸时序滑动能不能翻页；
 * 4. order.html → 查看物流 → goods_express 能不能拿到运单号与轨迹文本。
 *
 * 结论写进 logcat（tag=P0Probe），不在这一层做任何产品逻辑。
 */
class P0ProbeActivity : Activity() {

    companion object {
        private const val TAG = "P0Probe"
        private const val HOST = "https://mobile.yangkeduo.com"
        private const val ORDERS = "$HOST/orders.html"
        /** P0 门槛已调整为「一轮确认」：不再跑 3 轮。 */
        private const val ROUNDS = 1
        private const val MAX_CAPS = 20
        /* 上一轮 8000 把订单响应截断了，JSON 解析失败 → orders 恒为 0。
           真实订单页一页可能有几十条，先给 80KB；仍要保留截断，避免把整页真名/地址搬进内存。 */
        private const val MAX_RES = 80_000
    }

    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView
    private val logBuffer = StringBuilder()

    private val handler = Handler(Looper.getMainLooper())

    private var loggedIn = false
    private var running = false
    private var round = 0
    private var runToken = 0
    private val roundOk = mutableListOf<Boolean>()

    private var lastHits: List<Hit> = emptyList()
    private var pageTwo = false
    private var detailOk = false
    private var traceStarted = false
    private var pendingSn: String? = null
    private var logisticsStarted = false
    private var clickSucceeded = false

    private data class Hit(val sn: String, val prompt: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        buildWeb()
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            status.text = "系统 WebView 不支持 document-start 注入，P0 无法继续"
            log("DOCUMENT_START_SCRIPT unsupported")
            return
        }
        WebViewCompat.addDocumentStartJavaScript(web, HOOK_JS, setOf(HOST))
        log("探针就绪。登录拼多多后会自动跑 " + ROUNDS + " 轮。")
        handler.postDelayed(poll, 1500)
    }

    private fun buildUi() {
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.BLACK)
            setPadding(24, 24, 24, 8)
            text = "请用手机号 + 短信验证码登录拼多多。登录成功后会自动抓单。"
        }
        logText = TextView(this).apply {
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.DKGRAY)
            setPadding(24, 0, 24, 8)
        }
        logScroll = ScrollView(this).apply {
            addView(logText)
            isFillViewport = true
        }
        val logHeight = (150 * resources.displayMetrics.density).toInt()
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 0, 16, 8)
        }
        listOf(
            "跑一轮" to { resetAndRun() },
            "翻页" to { swipeUp() },
            "抓轨迹" to { openFirstDetail(manual = true) },
            "清 Cookie" to { clearCookies() },
        ).forEach { (label, action) ->
            buttons.addView(
                Button(this).apply {
                    text = label
                    textSize = 11f
                    setOnClickListener { action() }
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        web = WebView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(status)
            addView(logScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, logHeight))
            addView(buttons)
            addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWeb() {
        WebView.setWebContentsDebuggingEnabled(true)
        web.setBackgroundColor(Color.WHITE)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        /* PDD 对移动版 WebView UA 会走「打开 App」下载页；参考实现用的是
           去掉 Android 标记的桌面 Chromium UA，登录页才直接给 H5 手机号表单。 */
        s.userAgentString = WebSettings.getDefaultUserAgent(this)
            .replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)")
            .replace("Version/4.0 ", "")
            .replace(" Mobile", "")
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.allowFileAccess = false
        s.allowContentAccess = false
        with(CookieManager.getInstance()) {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?) {
                Log.i(TAG, "CONSOLE ${message.orEmpty().take(160)}")
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                Log.i(TAG, "PAGE $url")
                if (url.contains("express_service.html")) {
                    log("拦截 express_service，停在 goods_express")
                    view.stopLoading()
                }
                if (url.contains("goods_express.html")) traceStarted = true
            }

            override fun onPageFinished(view: WebView, url: String) {
                Log.i(TAG, "PAGE-FINISHED $url")
                if (url.contains("order.html") && !logisticsStarted && !traceStarted) {
                    logisticsStarted = true
                    handler.postDelayed({ clickLogistics(0) }, 1400)
                }
                if (url.contains("goods_express.html")) {
                    handler.postDelayed({ readTrace(url) }, 2600)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (!url.startsWith("http")) {
                    log("拦截 scheme: ${url.take(80)}")
                    return true
                }
                if (url.contains("express_service.html")) {
                    log("shouldOverride 拦截 express_service")
                    return true
                }
                return false
            }
        }
        val login = "$HOST/login.html?_x_no_login_launch=1&from=" + Uri.encode(ORDERS)
        web.loadUrl(login)
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!loggedIn) {
                val cookie = CookieManager.getInstance().getCookie(ORDERS).orEmpty()
                val map = cookieMap(cookie)
                if (map["PDDAccessToken"]?.isNotBlank() == true &&
                    (map["pdd_user_id"] ?: map["pdduid"])?.isNotBlank() == true
                ) {
                    loggedIn = true
                    log("登录态已就绪（cookie 项 ${map.size}）。3 秒后开始第 1 轮。")
                    status.text = "登录态已就绪，准备抓单"
                    handler.postDelayed({
                        if (!running && roundOk.isEmpty()) resetAndRun()
                    }, 3000)
                }
            }
            handler.postDelayed(this, 1500)
        }
    }

    private fun resetAndRun() {
        if (running) {
            log("当前轮次还在跑，忽略重跑")
            return
        }
        round = 0
        roundOk.clear()
        startRound()
    }

    private fun startRound() {
        if (!loggedIn) {
            toast("还没拿到拼多多登录态")
            return
        }
        if (round >= ROUNDS) {
            summary()
            return
        }
        running = true
        round++
        val token = ++runToken
        lastHits = emptyList()
        pageTwo = false
        detailOk = false
        traceStarted = false
        pendingSn = null
        logisticsStarted = false
        clickSucceeded = false
        log("RUN $round 开始：加载订单页")
        status.text = "第 $round/$ROUNDS 轮：加载订单列表"
        web.loadUrl(ORDERS)
        handler.postDelayed({ if (token == runToken) readCaptures("load") }, 7000)
        handler.postDelayed({ if (token == runToken) swipeUp() }, 8000)
        handler.postDelayed({ if (token == runToken) swipeUp() }, 10000)
        handler.postDelayed({ if (token == runToken) swipeUp() }, 12000)
        handler.postDelayed({ if (token == runToken) swipeUp() }, 14000)
        handler.postDelayed({ if (token == runToken) readCaptures("swipe") }, 16000)
        handler.postDelayed({ if (token == runToken) openFirstDetail() }, 17000)
        handler.postDelayed({ if (token == runToken) finishRound(token) }, 34000)
    }

    private fun readCaptures(tag: String) {
        web.evaluateJavascript("JSON.stringify(window.__p0 || {caps:[]})") { raw ->
            val text = jsString(raw) ?: return@evaluateJavascript
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return@evaluateJavascript
            val caps = root.optJSONArray("caps") ?: JSONArray()
            val hits = mutableListOf<Hit>()
            var listCaps = 0
            for (i in 0 until caps.length()) {
                val cap = caps.optJSONObject(i) ?: continue
                val url = cap.optString("url")
                val res = cap.optString("res")
                val body = cap.optString("body")
                val isList = url.contains("order_list")
                if (isList) listCaps++
                if (!isList) continue
                if (body.contains("\"page\"")) {
                    val page = Regex("\"page\"\\s*:\\s*(\\d+)")
                        .find(body)?.groupValues?.getOrNull(1)
                    if (page == "2") pageTwo = true
                }
                val parsed = runCatching { JSONTokener(res).nextValue() }.getOrNull()
                collectHits(parsed, hits)
                Log.i(TAG, "CAP[$tag] list=$isList len=${res.length} url=${url.take(140)}")
                Log.i(TAG, "CAP[$tag] body=${body.take(1200)}")
                Log.i(TAG, "CAP[$tag] head=${res.take(160).replace('\n', ' ')}")
            }
            val merged = (lastHits + hits).distinctBy { it.sn }
            lastHits = merged
            if (merged.isNotEmpty() || listCaps > 0) {
                log("CAPTURE[$tag] listCaps=$listCaps orders=${merged.size} page2=$pageTwo")
                Log.i(TAG, "HITS[$tag] ${merged.take(6).joinToString(" | ") { "${it.sn}/${it.prompt}" }}")
            }
            if (merged.isNotEmpty()) {
                status.text = "第 $round/$ROUNDS 轮：抓到 ${merged.size} 条订单，page2=$pageTwo"
            }
        }
    }

    private fun collectHits(node: Any?, out: MutableList<Hit>) {
        when (node) {
            is JSONObject -> {
                val sn = node.optString("order_sn")
                if (sn.isNotBlank() && sn.length >= 8) {
                    out.add(Hit(sn, node.optString("order_status_prompt")))
                }
                val keys = node.keys()
                while (keys.hasNext()) collectHits(node.opt(keys.next()), out)
            }
            is JSONArray -> {
                for (i in 0 until node.length()) collectHits(node.opt(i), out)
            }
        }
    }

    private fun openFirstDetail(manual: Boolean = false) {
        if (detailOk || traceStarted) return
        val hit = lastHits.firstOrNull()
        if (hit == null) {
            if (manual) toast("还没抓到 order_sn")
            return
        }
        pendingSn = hit.sn
        logisticsStarted = false
        clickSucceeded = false
        log("打开详情: ${hit.sn} / ${hit.prompt}")
        status.text = "第 $round/$ROUNDS 轮：打开订单物流"
        web.loadUrl("$HOST/order.html?order_sn=" + Uri.encode(hit.sn))
        if (manual) {
            handler.postDelayed({ if (!traceStarted) clickLogistics(0) }, 1800)
        }
    }

    private fun clickLogistics(attempt: Int) {
        if (traceStarted || clickSucceeded) return
        web.evaluateJavascript(CLICK_LOGISTICS_JS) { raw ->
            val r = jsString(raw).orEmpty()
            log("点击查看物流[$attempt]: $r")
            Log.i(TAG, "CLICK[$attempt] $r")
            if (r.startsWith("clicked")) {
                clickSucceeded = true
            } else if (attempt < 4 && !traceStarted) {
                handler.postDelayed({ if (!traceStarted) clickLogistics(attempt + 1) }, 1500)
            }
        }
    }

    private fun readTrace(url: String) {
        web.evaluateJavascript("document.body ? document.body.innerText : ''") { raw ->
            val text = jsString(raw).orEmpty()
            val uri = Uri.parse(url)
            val tracking = uri.getQueryParameter("tracking_number")
                ?: uri.getQueryParameter("mailNo")
                ?: uri.getQueryParameter("trackingNo")
                ?: ""
            val params = uri.queryParameterNames.joinToString(",")
            Log.i(TAG, "TRACE url=$url")
            Log.i(TAG, "TRACE params=$params tracking=$tracking len=${text.length}")
            Log.i(TAG, "TRACE text=${text.take(1200).replace('\n', ' ')}")
            detailOk = tracking.isNotBlank() && text.length > 20
            log("TRACE tracking=$tracking textLen=${text.length} ok=$detailOk")
            status.text = "第 $round/$ROUNDS 轮：轨迹 ${if (detailOk) "OK" else "FAIL"}"
            if (running) handler.postDelayed({ finishRound(runToken) }, 1200)
        }
    }

    private fun finishRound(token: Int) {
        if (token != runToken || !running) return
        running = false
        val ok = lastHits.isNotEmpty() && detailOk
        roundOk.add(ok)
        log("RUN $round = ${if (ok) "OK" else "FAIL"}（orders=${lastHits.size}, page2=$pageTwo, detail=$detailOk）")
        if (round < ROUNDS) {
            status.text = "第 $round/$ROUNDS 轮结束，8 秒后进入下一轮"
            handler.postDelayed({
                if (!running && round < ROUNDS) startRound()
            }, 8000)
        } else {
            summary()
        }
    }

    private fun summary() {
        val pass = roundOk.count { it } >= 1
        val detail = roundOk.mapIndexed { i, ok -> "run${i + 1}=${if (ok) "OK" else "FAIL"}" }
            .joinToString(" ")
        val emptyAccount = lastHits.isEmpty() && !detailOk
        Log.i(TAG, "SUMMARY $detail")
        Log.i(TAG, "VERDICT ${if (pass) "GO" else "NOGO"}")
        if (emptyAccount) Log.i(TAG, "NOGO-REASON empty_order_list")
        status.text = "P0 结论：${if (pass) "GO" else "NO-GO"}（$detail）"
        log("SUMMARY $detail")
        log("VERDICT ${if (pass) "GO" else "NOGO"}")
        if (emptyAccount) log("NOGO-REASON empty_order_list（账号没有订单，不是宿主失败）")
    }

    /**
     * 触摸时序滑动。拼多多的虚拟列表靠 touch 事件触发增量加载，
     * JS `scrollTop` 只能到 ~41 单——所以这里必须派发真实 MotionEvent。
     */
    private fun swipeUp() {
        val w = web.width
        val h = web.height
        if (w <= 0 || h <= 0) return
        val x = w / 2f
        val startY = h * 0.82f
        val endY = h * 0.22f
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, y: Float, at: Long) =
            MotionEvent.obtain(down, at, action, x, y, 0)
        web.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, startY, down))
        for (i in 1..8) {
            val at = down + i * 20L
            handler.postDelayed({
                web.dispatchTouchEvent(
                    event(MotionEvent.ACTION_MOVE, startY + (endY - startY) * i / 8f, at),
                )
            }, i * 20L)
        }
        handler.postDelayed({
            web.dispatchTouchEvent(event(MotionEvent.ACTION_UP, endY, down + 180L))
            Log.i(TAG, "SWIPE")
        }, 180L)
    }

    private fun clearCookies() {
        runToken++
        running = false
        loggedIn = false
        round = 0
        roundOk.clear()
        lastHits = emptyList()
        detailOk = false
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            runOnUiThread {
                log("Cookie 已清空，重新登录拼多多")
                web.loadUrl("$HOST/login.html?_x_no_login_launch=1&from=" + Uri.encode(ORDERS))
            }
        }
    }

    private fun cookieMap(header: String): Map<String, String> =
        header.split(';').mapNotNull {
            val parts = it.trim().split('=', limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
        }.toMap()

    private fun jsString(value: String?): String? {
        if (value == null || value == "null") return null
        return runCatching {
            val v = JSONTokener(value).nextValue()
            if (v is String) v else value
        }.getOrDefault(value)
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        runOnUiThread {
            logBuffer.append(message).append('\n')
            if (logBuffer.length > 6000) logBuffer.delete(0, logBuffer.length - 6000)
            logText.text = logBuffer
            logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::web.isInitialized) {
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        super.onDestroy()
    }

    private val HOOK_JS = """
        (function () {
          if (window.__p0) return;
          window.__p0 = { caps: [], errors: [] };
          function keep(url, body, res) {
            try {
              var u = String(url || '');
              if (!/order_list|express|goods_express|track/i.test(u)) return;
              window.__p0.caps.push({
                url: u.slice(0, 300),
                body: String(body || '').slice(0, 1200),
                res: String(res || '').slice(0, $MAX_RES),
                at: Date.now()
              });
              if (window.__p0.caps.length > $MAX_CAPS) window.__p0.caps.shift();
            } catch (e) {
              window.__p0.errors.push(String(e));
            }
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
                  c.text().then(function (t) { keep(url, body, t); }).catch(function () {});
                } catch (e) {}
              }).catch(function () {});
              return p;
            };
          }
          var oo = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function (m, u) {
            this.__p0u = String(u);
            return oo.apply(this, arguments);
          };
          var os = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.send = function (b) {
            var x = this;
            x.addEventListener('load', function () {
              try { keep(x.__p0u, b, x.responseText); } catch (e) {}
            });
            return os.apply(this, arguments);
          };
        })();
    """.trimIndent()

    /* 参考实现的判据：只接受「查看物流 / 查物流 / 物流详情 / 物流信息」这类短文本，
       并且在可见元素里挑高度最小的那个。上一版用 indexOf 命中了一个包含「查看物流」的大容器，
       点下去实际点到了「已签收」，页面没跳。 */
    private val CLICK_LOGISTICS_JS = """
        (function () {
          var nodes = Array.prototype.slice.call(document.querySelectorAll('div,span,button,a,p,label'));
          var best = null;
          var bestH = 1e9;
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
