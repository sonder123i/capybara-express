package dev.showcode.data.fetch

import android.net.Uri
import android.webkit.CookieManager

/**
 * 按 host 清理抓取会话，而不是 `removeAllCookies`。
 *
 * 登录页和抓取共用一个进程级 CookieManager；`removeAllCookies` 是**整个 App** 范围，
 * 会把用户正在登录拿到的会话一起清掉，而且它是异步的，紧接 `flush()` 也不保证已经清完。
 * 这里只碰抓取平台自己的 host：读出现有 cookie 名，逐个写 `Max-Age=0`，最后统一 flush。
 *
 * CookieManager 没有「按 host 枚举」的 API，所以这是能做到的最精确粒度；
 * 同时给父域补一条过期指令，覆盖 `Domain=.xxx.com` 写下的 cookie。
 */
internal object FetcherCookies {

    fun clear(hosts: Collection<String>) {
        val cm = CookieManager.getInstance()
        hosts.forEach { host ->
            val hostName = Uri.parse(host).host?.removePrefix("www.") ?: return@forEach
            val parent = registrableDomain(hostName)
            val existing = runCatching { cm.getCookie(host) }.getOrNull().orEmpty()
            existing.split(';')
                .map { it.substringBefore('=').trim() }
                .filter { it.isNotEmpty() }
                .forEach { name ->
                    runCatching { cm.setCookie(host, "$name=; Max-Age=0; Path=/") }
                    if (parent != hostName) {
                        runCatching {
                            cm.setCookie(host, "$name=; Max-Age=0; Path=/; Domain=.$parent")
                        }
                    }
                }
        }
        cm.flush()
    }

    /** 只处理两段/三段常见域名；`mobile.yangkeduo.com` → `yangkeduo.com`。 */
    private fun registrableDomain(host: String): String {
        val parts = host.split('.')
        return when {
            parts.size <= 2 -> host
            parts.takeLast(2).joinToString(".").let { it == "com.cn" || it == "co.uk" } ->
                parts.takeLast(3).joinToString(".")
            else -> parts.takeLast(2).joinToString(".")
        }
    }
}
