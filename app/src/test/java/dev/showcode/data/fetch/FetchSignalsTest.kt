package dev.showcode.data.fetch

import dev.showcode.core.account.FailureKind
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
}
