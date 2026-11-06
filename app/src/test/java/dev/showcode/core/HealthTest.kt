package dev.showcode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * story-5 AC2：多项同时出问题时横条只显示最严重的一项加「还有 N 项」。
 * 所以严重度顺序本身就是产品决策，必须被测试钉住。
 */
class HealthTest {

    private val now = 1_760_000_000_000L

    @Test
    fun `全正常时没有失效项 - 横条完全不出现`() {
        val report = healthReport(
            smsGranted = true, notificationEnabled = true, lastSyncAt = now - HOUR_MS, now = now, isXiaomi = false, hasUnpicked = true,
        )
        assertTrue(report.issues.isEmpty())
        assertNull(report.worst)
        assertTrue(report.allGood)
    }

    @Test
    fun `短信与通知监听都未开也不产生首页失效项 - 它们只是补充手段`() {
        val report = healthReport(
            smsGranted = false, notificationEnabled = false, lastSyncAt = null, now = now, isXiaomi = false, hasUnpicked = true,
        )
        assertTrue("权限缺失只进设置页，不进首页，实际 ${report.issues}", report.issues.isEmpty())
        assertNull(report.worst)
    }

    @Test
    fun `只缺通知监听时首页也没有失效项`() {
        val report = healthReport(
            smsGranted = true, notificationEnabled = false, lastSyncAt = now, now = now, isXiaomi = false, hasUnpicked = true,
        )
        assertTrue(report.issues.isEmpty())
        assertNull(report.worst)
    }

    @Test
    fun `通知监听关掉后长时间没同步也不怀疑后台 - 没有可信信号`() {
        val report = healthReport(
            smsGranted = true, notificationEnabled = false,
            lastSyncAt = now - 24 * HOUR_MS, now = now, isXiaomi = false, hasUnpicked = true,
        )
        assertTrue("关掉通知后「该同步却没过」不可信，报警只会变成误报，实际 ${report.issues}", report.issues.isEmpty())
    }

    @Test
    fun `权限都正常但六小时没同步 - 怀疑后台被限制`() {
        val report = healthReport(
            smsGranted = true, notificationEnabled = true,
            lastSyncAt = now - BACKGROUND_SUSPECT_MS - 1, now = now, isXiaomi = false, hasUnpicked = true,
        )
        assertEquals(listOf(HealthIssue.BACKGROUND_SUSPECTED), report.issues)
    }

    @Test
    fun `短信没开不影响后台判定 - 通知监听正常时照常怀疑后台`() {
        val report = healthReport(
            smsGranted = false, notificationEnabled = true,
            lastSyncAt = now - 24 * HOUR_MS, now = now, isXiaomi = false, hasUnpicked = true,
        )
        assertEquals(listOf(HealthIssue.BACKGROUND_SUSPECTED), report.issues)
    }

    @Test
    fun `刚同步过就不怀疑后台`() {
        val report = healthReport(
            smsGranted = true, notificationEnabled = true,
            lastSyncAt = now - BACKGROUND_SUSPECT_MS + HOUR_MS, now = now, isXiaomi = false, hasUnpicked = true,
        )
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun `没件可取时不报后台被限制 - 不然闲一天就常驻红条`() {
        // lastSyncAt 只在真的收到消息时盖章。「今天没买东西」也会让它超时——
        // 真机上闲 7 小时就挂着一条「后台可能被系统限制了」，那是误报。
        // 一条常驻的误报会把真正要看的两条（短信权限、通知权限）一起废掉。
        val report = healthReport(
            smsGranted = true, notificationEnabled = true,
            lastSyncAt = now - BACKGROUND_SUSPECT_MS - 1, now = now, isXiaomi = false, hasUnpicked = false,
        )
        assertTrue("没件可取时不该报任何失效项，实际 ${report.issues}", report.issues.isEmpty())
    }

    @Test
    fun `小米项只进体检表不进横条 - 它无法程序化判定 常驻报警会变成狼来了`() {
        val report = healthReport(
            smsGranted = true, notificationEnabled = true, lastSyncAt = now, now = now, isXiaomi = true, hasUnpicked = true,
        )
        assertTrue(report.issues.isEmpty())
        assertNull(report.worst)
        assertEquals(listOf(HealthIssue.MIUI_SMS_CLASS), report.manualChecks)
        // 体检表不全是绿的：有一项需要人工确认
        assertEquals(false, report.allGood)
    }

    @Test
    fun `没给短信权限时不提示小米通知类短信 - 先解决主通道`() {
        val report = healthReport(
            smsGranted = false, notificationEnabled = false, lastSyncAt = null, now = now, isXiaomi = true, hasUnpicked = true,
        )
        assertTrue(report.manualChecks.isEmpty())
    }

    @Test
    fun `首页说明只在后台可疑时出现 - 关掉后同一轮同步内不再出现`() {
        assertTrue(shouldShowBackgroundNote(HealthIssue.BACKGROUND_SUSPECTED, now, dismissedForSyncAt = 0L))
        assertFalse(shouldShowBackgroundNote(HealthIssue.BACKGROUND_SUSPECTED, now, dismissedForSyncAt = now))
    }

    @Test
    fun `下一轮真的同步过之后说明重新可见 - 一次关闭不会永久失明`() {
        assertTrue(shouldShowBackgroundNote(HealthIssue.BACKGROUND_SUSPECTED, now, dismissedForSyncAt = now - HOUR_MS))
    }

    @Test
    fun `没有后台可疑时首页没有任何权限横幅`() {
        assertFalse(shouldShowBackgroundNote(null, now, dismissedForSyncAt = 0L))
        assertFalse(shouldShowBackgroundNote(HealthIssue.MIUI_SMS_CLASS, now, dismissedForSyncAt = 0L))
    }
}
