package dev.showcode.data.fetch

import dev.showcode.core.account.FailureKind

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
