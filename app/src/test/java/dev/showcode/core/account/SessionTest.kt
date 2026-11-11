package dev.showcode.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三家平台的登录判据。真机登录页会变，但这几条 key 与拆分规则不该变；
 * 它们错的时候症状是「登录页明明成功了，App 却不认」——所以钉在这里。
 */
class SessionTest {

    @Test
    fun `拼多多 - token 与 user_id 齐了才算登录`() {
        val s = Session.parse("PDDAccessToken=abc; pdd_user_id=123")
        assertTrue(s.has(Platform.PDD))
    }

    @Test
    fun `拼多多 - pdduid 是 user_id 的旧写法`() {
        assertTrue(Session.parse("PDDAccessToken=abc; pdduid=123").has(Platform.PDD))
    }

    @Test
    fun `拼多多 - 缺 token 或 user_id 都不算`() {
        assertFalse(Session.parse("PDDAccessToken=abc").has(Platform.PDD))
        assertFalse(Session.parse("pdd_user_id=123").has(Platform.PDD))
        assertFalse(Session.parse("PDDAccessToken=; pdd_user_id=123").has(Platform.PDD))
    }

    @Test
    fun `淘宝 - cookie2 加上 unb 或 cookie1 任一`() {
        assertTrue(Session.parse("cookie2=a; unb=b").has(Platform.TB))
        assertTrue(Session.parse("cookie2=a; cookie1=b").has(Platform.TB))
        assertFalse(Session.parse("cookie2=a").has(Platform.TB))
        assertFalse(Session.parse("unb=b").has(Platform.TB))
    }

    @Test
    fun `京东 - pt_key 与 pt_pin 两把都要`() {
        assertTrue(Session.parse("pt_key=a; pt_pin=b").has(Platform.JD))
        assertFalse(Session.parse("pt_key=a").has(Platform.JD))
        assertFalse(Session.parse("pt_pin=b").has(Platform.JD))
    }

    @Test
    fun `解析 - 值里含等号不被截断`() {
        val s = Session.parse("token=abc==; other=x")
        assertEquals("abc==", s["token"])
        assertEquals("x", s["other"])
    }

    @Test
    fun `解析 - 空 key 与空白被丢掉`() {
        val s = Session.parse("; =x; k=v ;")
        assertEquals(null, s[""])
        assertEquals("v", s["k"])
        assertTrue(s.values.keys.none { it.isBlank() })
    }

    @Test
    fun `合并 - 刷新集新补的键保留，旧键不丢`() {
        val merged = Session.merge("pt_key=a; pt_pin=b", "thor=x; jxsid=y")
        assertEquals("a", merged["pt_key"])
        assertEquals("b", merged["pt_pin"])
        assertEquals("x", merged["thor"])
        assertEquals("y", merged["jxsid"])
        assertTrue(merged.has(Platform.JD))
    }

    @Test
    fun `合并 - 同名键以刷新集为准`() {
        val merged = Session.merge("pt_key=old; pt_pin=b", "pt_key=new")
        assertEquals("new", merged["pt_key"])
        assertEquals("b", merged["pt_pin"])
    }

    @Test
    fun `合并 - 匿名刷新集仍不满足登录判据`() {
        assertFalse(Session.merge("pt_key=a; pt_pin=b", "thor=x; jxsid=y").has(Platform.TB))
        val anonJd = Session.merge("pt_key=a; pt_pin=b", "pt_key=; thor=x")
        assertFalse(anonJd.has(Platform.JD))
    }

    @Test
    fun `header - 拼回后能再次解析`() {
        val s = Session.parse("PDDAccessToken=abc==; pdd_user_id=123")
        assertEquals(s.values, Session.parse(s.header()).values)
    }

    @Test
    fun `平台 id 大小写不敏感，未知返回 null`() {
        assertEquals(Platform.PDD, Platform.of(" PDD "))
        assertEquals(Platform.TB, Platform.of("tb"))
        assertNull(Platform.of("mystery"))
        assertNull(Platform.of(null))
    }
}
