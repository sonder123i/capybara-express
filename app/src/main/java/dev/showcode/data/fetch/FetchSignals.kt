package dev.showcode.data.fetch

import dev.showcode.core.account.FailureKind
import dev.showcode.core.account.Platform
import dev.showcode.core.account.Session

/**
 * 恢复抓取会话时，某个 Cookie 键是否要用「登录时保存的值」覆盖 WebView 里已有的值。
 *
 * 抓取 WebView 与登录页共用一个进程级 CookieManager，同步完账号 A 后 A 的 Cookie 会残留。
 * 若一律「只补缺失键」，同步账号 B 时登录身份键仍是 A 的，就会拿 A 的会话抓到 A 的订单、
 * 却挂到 B 名下。所以身份键无条件覆盖；其余风控/辅助键保留 WebView 运行时刷出的新值
 * （清空后平铺恢复会丢 domain 属性，京东会判未登录，这也是不能整批重铺的原因）。
 *
 * 抽成纯函数是为了能在 JVM 单测里钉死这条归属底线，而不必起 WebView。
 */
internal fun shouldRestoreCookieKey(
    key: String,
    existingValue: String?,
    platform: Platform = Platform.JD,
): Boolean = key in Session.identityKeys(platform) || existingValue == null

/**
 * 只看 URL 形状判断「是不是被带去登录」：通行证域名、login/signin 路径。
 * 其它意外跳转（风控页、活动页、改版页）不说明会话失效，归瞬时失败。
 *
 * ⚠️ 只看 **去掉查询串** 的地址。拼多多登录页的回跳参数长这样：
 * `login.html?from=https%3A%2F%2Fmobile.yangkeduo.com%2Forders.html`
 * ——它里面真的含有 `orders.html` 和 `login`，拿整串做子串判断会把登录页当成订单页。
 */
internal fun looksLikeLogin(url: String): Boolean {
    val bare = urlWithoutQuery(url).lowercase()
    val host = bare.substringAfter("://", "").substringBefore('/')
    val path = bare.substringAfter("://", "").substringAfter('/', "")
    return host.contains("passport") || host.contains("login") ||
        path.contains("/login") || path.contains("login.") ||
        path.contains("signin") || path.contains("plogin")
}

/** 去掉查询串与锚点：判断「这是哪一页」时，参数里的回跳地址不算数。 */
internal fun urlWithoutQuery(url: String): String = url.substringBefore('?').substringBefore('#')

/** 拼多多的订单页。只看 host + path，`from=` 里的 `orders.html` 不能冒充它。 */
internal fun isPddOrdersPage(url: String): Boolean {
    val bare = urlWithoutQuery(url).lowercase()
    val host = bare.substringAfter("://", "").substringBefore('/')
    val path = bare.substringAfter("://", "").substringAfter('/', "")
    return (host.endsWith("yangkeduo.com") || host.endsWith("pinduoduo.com")) &&
        path == "orders.html"
}

/** 401/403 是「没登录/没权限」的硬证据；其余 4xx/5xx 先当可重试。 */
internal fun httpFailureKind(code: Int): FailureKind =
    if (code == 401 || code == 403) FailureKind.AUTH else FailureKind.TRANSIENT

/**
 * 是不是京东自己的 host。
 *
 * 抓取用的隐藏 WebView 注入了页面钩子，且详情页链接来自接口响应（服务端可控），
 * 所以只能用**精确域**判定：`jd.com` 本身或 `.jd.com` 结尾的子域。
 * `endsWith("jd.com")` 会让 `eviljd.com` / `notjd.com` 混进来，把带钩子的 WebView
 * 导到第三方页面，伪造的 DOM 内容会被 TraceParser 写进本地库。
 */
internal fun isJdHost(urlOrHost: String): Boolean {
    val host = urlOrHost.lowercase()
        .substringAfter("://", urlOrHost.lowercase())
        .substringBefore('/')
        .substringBefore(':')
        .removePrefix("www.")
    return host == "jd.com" || host.endsWith(".jd.com") ||
        host == "jd.hk" || host.endsWith(".jd.hk")
}

/** 详情页链接只能落在京东域：接口返回的 progressLink 必须先过这一关再 loadUrl。 */
internal fun isSafeJdUrl(url: String): Boolean {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return false
    return isJdHost(url)
}
