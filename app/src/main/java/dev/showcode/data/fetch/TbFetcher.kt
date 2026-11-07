package dev.showcode.data.fetch

import dev.showcode.core.Parcel
import dev.showcode.core.Slog
import dev.showcode.core.account.FetchOutcome
import dev.showcode.core.account.FailureKind
import dev.showcode.core.account.FetchedParcel
import dev.showcode.core.account.GoodsInfo
import dev.showcode.core.account.Platform
import dev.showcode.core.account.StageMachine
import dev.showcode.core.account.SyncPhase
import dev.showcode.core.account.SyncProgress
import dev.showcode.core.account.TraceEntry
import dev.showcode.core.account.TraceParser
import dev.showcode.core.account.allDetailsFailed
import dev.showcode.core.account.detailNeeded
import dev.showcode.core.account.goodsPriceText
import dev.showcode.core.account.isBlockedOrderPrompt
import dev.showcode.core.account.isFinishedOrderPrompt
import dev.showcode.core.account.normalizeImageUrl
import dev.showcode.core.account.orderTimeMs
import dev.showcode.core.account.stageOfPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * 淘宝：先用 mtop 拿订单列表，再逐单取 SSR 物流详情（含 mailNo 与多段轨迹）。
 *
 * 这条链路不需要 WebView：签名是 `_m_h5_tk` 的 MD5，接口只读。代价是 mtop 改签/改字段时
 * 会静默返回空——所以失败只影响淘宝这一家，短信/通知/手动导入照旧。
 *
 * ⚠️ 未在真机淘宝账号上验证过（P4 需要用户登录一次）；代码按参考实现的真实字段编写。
 */
object TbFetcher {

    private const val TAG = "TbFetcher"
    private const val APPKEY = "12574478"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 12; M2102K1C) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/105.0.0.0 Mobile Safari/537.36 EdgA/105.0.1343.48"

    suspend fun fetch(
        accountId: String,
        cookie: String,
        /** 库里这个账号已有的件（键 = 订单号）。状态没变的单不再进详情页。 */
        known: Map<String, Parcel> = emptyMap(),
        /** 这一轮走到哪一步：顶栏的「读取详情 2/8」就是它。 */
        onProgress: suspend (SyncProgress) -> Unit = {},
    ): FetchOutcome =
        withContext(Dispatchers.IO) {
            if (cookie.isBlank()) {
                return@withContext FetchOutcome.Failure("Cookie 为空，需要重新登录", FailureKind.AUTH)
            }
            val page = try {
                listOrders(cookie)
            } catch (f: FetchFailure) {
                Slog.w(TAG, f) { "listOrders failed" }
                return@withContext FetchOutcome.Failure(f.message ?: "订单列表请求失败", f.kind)
            } catch (t: Throwable) {
                Slog.w(TAG, t) { "listOrders failed" }
                return@withContext FetchOutcome.Failure("订单列表请求失败，稍后再试")
            }
            val orders = page.orders
            Slog.safe(TAG, "orders=${orders.size} ended=${page.ended}")
            orders.forEachIndexed { i, o ->
                Slog.i(TAG) {
                    "list[$i] sn=${Slog.mask(o.id)} status=${o.status.ifBlank { "<空>" }} " +
                        "goods=${o.goods?.title?.take(32) ?: "<无>"}"
                }
            }
            /* 取消/关闭/退款/待付款的单不进详情，但要让库里同号的在途卡退休；
               历史单也不再占用详情名额。 */
            val history = orders.filter { closesOrder(it.status) }.map { it.id }
            val pending = orders.filter { needsDetail(it) }.sortedBy { detailRank(it.status) }
            /* 上限保留（每单详情都是一次整页请求），但只从真正待取/在途的单里取。
               状态没变、运单号与码都在的单先剔掉：它们这次拿回来的会和上次一样，
               还白占一个详情名额。判据见 core/account/DetailGate.kt。 */
            val now = System.currentTimeMillis()
            val candidates = pending
                .filter { detailNeeded(it.id, stageOfPrompt(it.status), known, now) }
                .take(MAX_DETAILS)
            Slog.safe(
                TAG,
                "pending=${pending.size} candidates=${candidates.size} " +
                    "skip=${pending.size - candidates.size} history=${history.size}",
            )
            /* 详情是纯 HTTP、彼此独立：四路并发把 8 单的等待从「串行累加」压成「两轮」。
               进度按**完成数**报，所以并发下也只增不减。 */
            val done = AtomicInteger()
            val attempts = coroutineScope {
                candidates.map { order ->
                    async(detailDispatcher) {
                        val attempt = try {
                            Attempt(order, detail(cookie, accountId, order), null)
                        } catch (f: FetchFailure) {
                            Attempt(order, null, f)
                        } catch (t: Throwable) {
                            Attempt(order, DetailOutcome(null, "exception:${t.message}", ""), null)
                        }
                        onProgress(SyncProgress(SyncPhase.DETAILS, done.incrementAndGet(), candidates.size))
                        attempt
                    }
                }.awaitAll()
            }
            /* 详情页明确 401/403：整批按登录失效处理，不再把剩下的单当成普通失败。 */
            val authFailure = attempts.firstNotNullOfOrNull { it.auth }
            if (authFailure != null) {
                return@withContext FetchOutcome.Failure(
                    authFailure.message ?: "物流详情没拿到", authFailure.kind,
                )
            }
            val result = mutableListOf<FetchedParcel>()
            var detailsFailed = 0
            attempts.forEachIndexed { index, attempt ->
                val order = attempt.order
                val outcome = attempt.outcome ?: DetailOutcome(null, "unknown", "")
                Slog.i(TAG) {
                    "result[$index] sn=${Slog.mask(order.id)} status=${order.status.ifBlank { "<空>" }} " +
                        "mailNo=${Slog.mask(outcome.mailNo)} goods=${order.goods?.title?.take(28) ?: "<无>"} " +
                        "parcel=${outcome.parcel != null} reason=${outcome.reason.ifBlank { "ok" }}"
                }
                val item = outcome.parcel
                if (item != null) result.add(item) else detailsFailed++
            }
            /* 一件都没解析出来：不能说成「成功·没有新件」，这批算瞬时失败。 */
            if (allDetailsFailed(candidates.size, detailsFailed)) {
                return@withContext FetchOutcome.Failure("${detailsFailed} 件详情都没抓到，稍后再试")
            }
            /* 只在订单接口给出结束信号时才交 seen；只有一页、又说不清是否看全时保持空集。 */
            val seen = if (page.ended) orders.map { it.id }.toSet() else emptySet()
            FetchOutcome.Success(result, history, seen, detailsFailed)
        }

    private data class Order(val id: String, val status: String, val goods: GoodsInfo? = null)

    /** 订单列表的一页：订单本身，外加「接口有没有明确说没有下一页」。 */
    private data class OrderPage(val orders: List<Order>, val ended: Boolean)

    private data class DetailOutcome(
        val parcel: FetchedParcel?,
        val reason: String,
        val mailNo: String,
    )

    /** 一单详情的结局。401/403 单独拎出来：它要让整批按登录失效处理。 */
    private data class Attempt(
        val order: Order,
        val outcome: DetailOutcome?,
        val auth: FetchFailure?,
    )

    private const val MAX_DETAILS = 8

    /** 详情是独立请求，并发四路；再高只会让 mtop 更容易触发风控。 */
    private const val DETAIL_PARALLELISM = 4
    private val detailDispatcher = Dispatchers.IO.limitedParallelism(DETAIL_PARALLELISM)

    /** SSR 页面正文里的预计送达文案（`logistics` JSON 不含这个字段）。 */
    private val ETA = Regex("预计[^，。；\"\\\\<]{0,18}")

    /**
     * 历史/阻断状态：不进详情，但要让库里同号的在途卡退休。
     * 词表在 core 里，和拼多多/京东共用一套口径，不在这里写死平台名。
     */
    private fun closesOrder(status: String): Boolean =
        isBlockedOrderPrompt(status) || isFinishedOrderPrompt(status)

    /** 待付款/未支付的单没有物流可抓，别浪费详情名额。 */
    private fun needsDetail(order: Order): Boolean =
        !closesOrder(order.status) &&
            !order.status.contains("待付款") && !order.status.contains("未支付")

    /** 真正待取/在途优先；历史单已在候选集合外。 */
    private fun detailRank(status: String): Int = when {
        status.contains("待取") || status.contains("已到") -> 0
        status.contains("派件") || status.contains("派送") || status.contains("待收货") -> 1
        status.contains("运输") || status.contains("已发货") || status.contains("已发出") ||
            status.contains("已揽收") -> 2
        else -> 3
    }

    private fun listOrders(cookie: String): OrderPage {
        val data = JSONObject()
            .put("tabCode", "all")
            .put("page", 1)
            .put("OrderType", "OrderList")
            .put("templateConfigVersion", "0")
            .put("appName", "tborder")
            .put("appVersion", "3.0")
            .put("condition", "{\"version\":\"1.0.0\",\"appChannel\":\"\"}")
            .put("ttid", "201200@taobao_h5_9.18.0")
            .put("requestIdentity", "#t#ip#h5")
            .toString()
        val body = mtopCall(cookie, "mtop.taobao.order.queryboughtlistv2", "1.0", data)
        Slog.i(TAG) { "boughtlist len=${body.length}" }
        if (body.isBlank()) throw FetchFailure("订单接口返回空响应，稍后再试", FailureKind.TRANSIENT)
        /* 响应正文里带会话失效标记才是登录失效的硬证据；其余解析失败都归瞬时。 */
        if (authSignal(body)) throw FetchFailure("登录已失效，需要重新登录", FailureKind.AUTH)
        val root = runCatching { JSONObject(jsonpBody(body)) }.getOrNull()
            ?: throw FetchFailure("订单接口返回不是 JSON（可能已改版）", FailureKind.TRANSIENT)
        val dataObj = root.optJSONObject("data")
            ?: throw FetchFailure("订单接口返回结构不可识别（可能已改版）", FailureKind.TRANSIENT)
        val inner = runCatching { JSONObject(dataObj.optString("result")) }.getOrNull()
            ?: dataObj.optJSONObject("data")
            ?: throw FetchFailure("订单接口返回结构不可识别（可能已改版）", FailureKind.TRANSIENT)
        val out = mutableListOf<Order>()
        val mainOrders = inner.optJSONArray("mainOrders")
        if (mainOrders != null) {
            for (i in 0 until mainOrders.length()) {
                val o = mainOrders.getJSONObject(i)
                val id = o.optString("id")
                if (id.isBlank()) continue
                val status = o.optJSONObject("statusInfo")?.optString("text").orEmpty()
                out.add(Order(id, status, goodsOf(o)))
            }
            /* 只有接口明确说「没有下一页」才敢把这张单看全。 */
            return OrderPage(out, orderListEnded(inner, out.size))
        }
        val keys = inner.keys()
        var sawMainKey = false
        while (keys.hasNext()) {
            val k = keys.next()
            if (!k.startsWith("Main_")) continue
            sawMainKey = true
            val fields = inner.optJSONObject(k)?.optJSONObject("fields") ?: continue
            val id = fields.optString("orderId").ifBlank { k.removePrefix("Main_") }
            if (id.isNotBlank()) out.add(Order(id, fields.optString("orderStatus")))
        }
        if (!sawMainKey) {
            throw FetchFailure("订单接口没有订单列表字段（可能已改版）", FailureKind.TRANSIENT)
        }
        return OrderPage(out, orderListEnded(inner, out.size))
    }

    private fun detail(cookie: String, accountId: String, order: Order): DetailOutcome {
        val url = "https://pages-g.m.taobao.com/wow/z/app/mtb/logisticsV2/h5-detail" +
            "?x-ssr=true&bizOrderId=${URLEncoder.encode(order.id, "UTF-8")}"
        val (detailCode, html) = httpGet(url, cookie)
        /* 401/403 是会话失效的硬证据；其它错误只算这一件详情失败，不拖垮整批。 */
        if (detailCode == 401 || detailCode == 403) {
            throw FetchFailure("物流详情 HTTP $detailCode", FailureKind.AUTH)
        }
        Slog.i(TAG) { "detail html order=${Slog.mask(order.id)} len=${html.length}" }
        if (html.isBlank()) return DetailOutcome(null, "empty-html", "")
        val logistics = ssrLogistics(html)
        Slog.i(TAG) { "detail logistics order=${Slog.mask(order.id)} found=${logistics != null}" }
        if (logistics == null) return DetailOutcome(null, "no-ssr-logistics", "")
        val company = logistics.optJSONObject("logisticCompany")
            ?: return DetailOutcome(null, "no-logisticCompany", "")
        val mailNo = logistics.optString("mailNo").ifBlank { company.optString("mailNo") }
        Slog.i(TAG) { "detail mailNo=${Slog.mask(mailNo)} company=${company.optString("name")}" }
        /* 「预计明天送达」只出现在 SSR 页面正文里（logistics JSON 没有这个字段）。
           订单列表的 statusInfo 只给「物流运输中」，所以必须从这里读，不能编造。 */
        val eta = ETA.find(html)?.value?.trim().orEmpty()
        Slog.i(TAG) { "detail eta order=${Slog.mask(order.id)} text=${eta.ifBlank { "<无>" }}" }
        if (order.status.contains("待付款") || order.status.contains("退款") ||
            order.status.contains("关闭") || order.status.contains("取消")
        ) return DetailOutcome(null, "closed-status:${order.status}", mailNo)
        val stages = logistics.optJSONArray("multiStage") ?: JSONArray()
        val now = System.currentTimeMillis()
        val traces = buildList {
            for (i in 0 until stages.length()) {
                val st = stages.getJSONObject(i)
                val label = stageText(st).trim()
                val subtitle = st.optString("subtitle").trim()
                val line = listOf(label, subtitle).filter { it.isNotBlank() }.joinToString(" ")
                if (line.isBlank()) continue
                /* 时间能读出来就带上；读不出就留 null，由状态机按单调规则兜底，
                   绝不拿数组顺序当成「最新在前」。 */
                val at = StageMachine.traceTimeMs(st.optString("time"))
                    ?: StageMachine.traceTimeMs(subtitle)
                add(TraceEntry(line, at))
            }
        }
        /* 必要取证：数组顺序、每条原文与时间、订单状态，真机日志里能直接看出是哪一步判错。 */
        Slog.i(TAG) {
            "detail stages order=${Slog.mask(order.id)} status=${order.status} n=${traces.size} " +
                "traces=${traces.joinToString(" | ") { "${it.text.take(28)}@${it.at}" }}"
        }
        val parsed = TraceParser.parseTraces(
            platform = Platform.TB,
            accountId = accountId,
            orderSn = order.id,
            trackingNo = mailNo,
            traces = traces,
            at = now,
            goods = order.goods,
            /* 状态只给订单汇总；预计送达来自 SSR 页面正文，拼在汇总里供首页读，不伪造在轨迹里。
               淘宝订单列表只给「物流运输中」，但用户看到的是「已发货」这一层口径，
               补一个显式词，首页据此显示「已发货」而不是泛化的「运输中」。 */
            status = buildList {
                if (order.status.contains("运输中")) add("已发货")
                if (order.status.isNotBlank()) add(order.status)
                if (eta.isNotBlank()) add(eta)
            }.joinToString(" "),
        )
        Slog.i(TAG) {
            "detail parsed=${parsed != null} stage=${parsed?.stage} explicit=${parsed?.explicitPickup} " +
                "point=${parsed?.pointName} code=${Slog.mask(parsed?.code)}/${parsed?.codeKind} " +
                "textLen=${traces.sumOf { it.text.length }}"
        }
        return DetailOutcome(
            parcel = parsed,
            reason = if (parsed != null) "" else "parser-null(mailNo=${mailNo.ifBlank { "<无>" }})",
            mailNo = mailNo,
        )
    }

    /**
     * mainOrders 里的商品：第一个 subOrder 的 `itemInfo` 给 title / pic；
     * 价格、店铺、下单时间可能挂在 itemInfo、shopInfo、extra、orderInfo 上，逐个候选读。
     * 读不到就留 null——淘宝各版本的字段并不稳定。
     */
    private fun goodsOf(order: JSONObject): GoodsInfo? {
        val item = order.optJSONArray("subOrders")?.optJSONObject(0)?.optJSONObject("itemInfo")
        val title = item?.optString("title")?.trim()?.takeIf { it.isNotEmpty() }
        val image = normalizeImageUrl(item?.optString("pic"))
        val price = goodsPriceText(
            item?.optJSONObject("priceInfo")?.optString("price")
                ?: item?.optString("price")
                ?: order.optJSONObject("payInfo")?.optString("actualFee"),
        )
        val shop = sequenceOf(
            order.optJSONObject("shopInfo")?.optString("shopName"),
            order.optJSONObject("sellerInfo")?.optString("sellerNick"),
            order.optString("shopName"),
            order.optString("sellerNick"),
        ).firstOrNull { !it.isNullOrBlank() }
        val orderedAt = sequenceOf(
            order.optJSONObject("extra")?.opt("createTime"),
            order.optJSONObject("orderInfo")?.opt("createTime"),
            order.opt("createTime"),
            order.optJSONObject("payInfo")?.opt("payTime"),
        ).mapNotNull { orderTimeMs(it) }.firstOrNull()
        if (title == null && image == null && price == null && shop == null && orderedAt == null) return null
        return GoodsInfo(title, image, price, shop, orderedAt)
    }

    private fun ssrLogistics(html: String): JSONObject? {
        val marker = "__ICE_SUSPENSE_LOADER__']['undefined'] = "
        val i = html.indexOf(marker)
        if (i < 0) return null
        return try {
            val obj = JSONTokener(html.substring(i + marker.length)).nextValue() as JSONObject
            val data = obj.optJSONObject("result")?.optJSONObject("data") ?: return null
            data.optJSONObject("newLogistics")?.optJSONObject("fields")
                ?: data.optJSONObject("logisticsDetailH5")?.optJSONObject("fields")
        } catch (e: Throwable) {
            null
        }
    }

    private fun stageText(stage: JSONObject): String {
        val sb = StringBuilder()
        val ld = stage.optJSONObject("labelDesc")
        if (ld != null) {
            val rich = ld.optJSONArray("richContent")
            if (rich != null) {
                for (r in 0 until rich.length()) sb.append(rich.optJSONObject(r)?.optString("text") ?: "")
            } else {
                sb.append(ld.optString("text"))
            }
        }
        if (sb.isEmpty()) sb.append(stage.optString("text"))
        return sb.toString()
    }

    private fun mtopCall(cookie: String, api: String, version: String, dataRaw: String): String {
        val base = "https://h5api.m.taobao.com/h5/$api/$version/"
        val headers = mapOf(
            "User-Agent" to UA,
            "Origin" to "https://h5.m.taobao.com",
            "Referer" to "https://h5.m.taobao.com/",
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to "zh-CN,zh;q=0.9",
            "Cookie" to cookie,
        )
        var ck = cookie
        runCatching {
            val (_, body, setCookies) = httpRequest("$base?appKey=$APPKEY", headers)
            val newTk = Regex("_m_h5_tk=([^;]+)").find(setCookies.joinToString("\n"))?.groupValues?.get(1)
            val newEnc = Regex("_m_h5_tk_enc=([^;]+)").find(setCookies.joinToString("\n"))?.groupValues?.get(1)
            if (!newTk.isNullOrBlank()) ck = ck.replace(Regex("_m_h5_tk=[^;]*"), "_m_h5_tk=$newTk")
            if (!newEnc.isNullOrBlank()) ck = ck.replace(Regex("_m_h5_tk_enc=[^;]*"), "_m_h5_tk_enc=$newEnc")
        }
        val token = Regex("_m_h5_tk=([^;]+)").find(ck)?.groupValues?.get(1)?.substringBefore("_")
            ?: throw FetchFailure("Cookie 里没有 mtop 令牌，稍后再试", FailureKind.TRANSIENT)
        val t = System.currentTimeMillis().toString()
        val sign = md5("$token&$t&$APPKEY&$dataRaw")
        val url = base + "?jsv=2.3.18&appKey=$APPKEY&t=$t&sign=$sign" +
            "&type=jsonp&dataType=jsonp&data=" + URLEncoder.encode(dataRaw, "UTF-8")
        val (code, responseBody, _) = httpRequest(url, headers.toMutableMap().apply { put("Cookie", ck) })
        if (code !in 200..299) throw FetchFailure("订单接口 HTTP $code", httpFailureKind(code))
        return responseBody
    }

    /**
     * 会话失效的响应形状：mtop 的会话错误码与登录页文案。
     * 不按平台名判断，只看接口是否明确说「你这个会话不行」。
     */
    private fun authSignal(body: String): Boolean {
        val b = body.lowercase()
        return b.contains("session_expired") || b.contains("session_invalid") ||
            b.contains("fail_sys_session") || b.contains("fail_sys_token") ||
            b.contains("need_login") || b.contains("请登录") || b.contains("passport.")
    }

    private fun httpGet(url: String, cookie: String): Pair<Int, String> =
        httpRequest(url, mapOf("User-Agent" to UA, "Cookie" to cookie)).let { it.first to it.second }

    private fun httpRequest(url: String, headers: Map<String, String>): Triple<Int, String, List<String>> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        val setCookies = conn.headerFields["Set-Cookie"].orEmpty()
        conn.disconnect()
        return Triple(code, body, setCookies)
    }

    private fun jsonpBody(body: String): String {
        val start = body.indexOf('(')
        val end = body.lastIndexOf(')')
        return if (start in 0 until end) body.substring(start + 1, end) else body
    }

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/**
 * 抓取内部用来携带「失败类别」的异常：HTTP 状态码、接口错误形状这些证据
 * 到这里已经判完，上层只需要原样落到 [FetchOutcome.Failure] 上。
 */
internal class FetchFailure(
    message: String,
    val kind: FailureKind,
) : Exception(message)
