package dev.showcode.data.fetch

import dev.showcode.core.account.FailureKind
import dev.showcode.core.account.Platform
import dev.showcode.core.account.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchSignalsTest {

    /**
     * 真实日志里的那一串：
     * `https://mobile.yangkeduo.com/login.html?from=https%3A%2F%2Fmobile.yangkeduo.com%2Forders.html`
     * 旧判据用 `contains("orders.html")`，于是登录页被当成订单页，最后报成「接口没返回数据」
     * 的瞬时失败——用户看到的是一句没有出路的「这次没同步上」。
     */
    @Test
    fun `拼多多登录页的回跳参数里带着 orders_html - 不能当成订单页`() {
        val login = "https://mobile.yangkeduo.com/login.html" +
            "?from=https%3A%2F%2Fmobile.yangkeduo.com%2Forders.html&refer_page_name=my_order"
        assertTrue(looksLikeLogin(login))
        assertFalse(isPddOrdersPage(login))
    }

    @Test
    fun `拼多多订单页带查询串也认得出`() {
        assertTrue(isPddOrdersPage("https://mobile.yangkeduo.com/orders.html"))
        assertTrue(isPddOrdersPage("https://mobile.yangkeduo.com/orders.html?tab=all"))
        assertFalse(isPddOrdersPage("https://mobile.yangkeduo.com/other.html"))
        assertFalse(isPddOrdersPage("https://example.com/orders.html"))
    }

    @Test
    fun `查询串里的 login 不会把普通页面误判成登录页`() {
        assertFalse(looksLikeLogin("https://mobile.yangkeduo.com/orders.html?refer=login"))
        assertTrue(looksLikeLogin("https://mobile.yangkeduo.com/login.html"))
        assertTrue(looksLikeLogin("https://passport.pinduoduo.com/"))
        assertTrue(looksLikeLogin("https://plogin.m.jd.com/login/login?returnurl=x"))
    }

    @Test
    fun `401 403 才是登录失效 - 其它状态码可重试`() {
        assertEquals(FailureKind.AUTH, httpFailureKind(401))
        assertEquals(FailureKind.AUTH, httpFailureKind(403))
        assertEquals(FailureKind.TRANSIENT, httpFailureKind(500))
    }

    /**
     * 进程级 CookieManager 会残留上一个账号的会话。登录身份键若「只补缺失」，
     * 同步账号 B 时仍是 A 的 pt_key/pt_pin，就会把 A 的订单挂到 B 名下。
     */
    @Test
    fun `身份键必须无条件覆盖 - 辅助键以 WebView 现有值为准`() {
        assertTrue(shouldRestoreCookieKey("pt_key", "上一个账号的值"))
        assertTrue(shouldRestoreCookieKey("pt_pin", "上一个账号的值"))
        /* 运行时刷出的风控/辅助键不能被登录时的旧值盖回去。 */
        assertFalse(shouldRestoreCookieKey("thor", "运行时新值"))
        /* 缺失的键仍要补，这条是恢复会话的本意。 */
        assertTrue(shouldRestoreCookieKey("thor", null))
    }

    @Test
    fun `三家平台的身份键覆盖各自的登录判据`() {
        assertEquals(setOf("pt_key", "pt_pin"), Session.identityKeys(Platform.JD))
        assertEquals(
            setOf("cookie2", "unb", "cookie1"),
            Session.identityKeys(Platform.TB),
        )
        assertEquals(
            setOf("PDDAccessToken", "pdd_user_id"),
            Session.identityKeys(Platform.PDD),
        )
    }

    /**
     * 隐藏 WebView 注入了页面钩子，详情页链接来自接口响应（服务端可控）。
     * 旧判定 `endsWith("jd.com")` 会让 eviljd.com 混进来，把带钩子的 WebView
     * 导到第三方站点，伪造 DOM 被 TraceParser 写进本地库。
     */
    @Test
    fun `只认京东自有域 - 防后缀伪造`() {
        listOf(
            "https://trade.m.jd.com/order/x",
            "https://www.jd.com/",
            "https://api.m.jd.com/?x=1",
            "https://o.m.jd.com/list",
            "https://jd.com/order",
            "https://item.jd.hk/a",
        ).forEach { assertTrue(it, isSafeJdUrl(it)) }

        listOf(
            "https://eviljd.com/order",
            "https://notjd.com/order",
            "https://jd.com.evil.com/order",
            "https://xjd.hk/order",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "ftp://trade.m.jd.com/x",
        ).forEach { assertFalse(it, isSafeJdUrl(it)) }
    }

    @Test
    fun `isJdHost 接受裸 host 与 www 前缀`() {
        assertTrue(isJdHost("trade.m.jd.com"))
        assertTrue(isJdHost("www.jd.com"))
        assertTrue(isJdHost("JD.COM"))
        assertFalse(isJdHost("eviljd.com"))
        assertFalse(isJdHost("jd.com.attacker.net"))
    }

    /** 初始链接合法不代表落地页合法：302 到第三方时 detail() 必须拒收。 */
    @Test
    fun `重定向后的落地页也要过域名校验`() {
        assertTrue(isSafeJdUrl("https://trade.m.jd.com/order/deal_wuliu_jdm.shtml"))
        assertFalse(isSafeJdUrl("https://attacker.net/fake-tracking"))
        assertFalse(isSafeJdUrl("https://jd.com.attacker.net/x"))
    }
}
