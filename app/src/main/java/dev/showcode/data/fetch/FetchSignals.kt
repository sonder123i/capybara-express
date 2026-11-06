package dev.showcode.data.fetch

import dev.showcode.core.account.FailureKind

/**
 * 只看 URL 形状判断「是不是被带去登录」：通行证域名、login/signin 路径。
 * 其它意外跳转（风控页、活动页、改版页）不说明会话失效，归瞬时失败。
 */
internal fun looksLikeLogin(url: String): Boolean {
    val u = url.lowercase()
    return u.contains("passport") || u.contains("/login") || u.contains("login.") ||
        u.contains("signin") || u.contains("plogin")
}

/** 401/403 是「没登录/没权限」的硬证据；其余 4xx/5xx 先当可重试。 */
internal fun httpFailureKind(code: Int): FailureKind =
    if (code == 401 || code == 403) FailureKind.AUTH else FailureKind.TRANSIENT
