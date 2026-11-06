package dev.showcode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「打开官方 App」这条兜底链的判据。
 *
 * 竞品 issue #39 的标题是「不能添加淘宝和拼多多身份码」，正文只有一句「点击时无反应」，
 * 维护者的答案是一个系统开关。**静默的失败不会告诉人下一步**，所以这一格的每一档
 * 都必须对应一句不同的话——尤其不能把「被拦住」说成「没装」。
 */
class LaunchOutcomeTest {

    private fun outcome(hasPackage: Boolean, vararg attempts: Attempt) =
        launchOutcome(hasPackage, attempts.toList())

    @Test
    fun `任何一级真起来了就没有第二句可说`() {
        assertEquals(LaunchOutcome.Opened, outcome(true, Attempt.NO_HANDLER, Attempt.OPENED))
        assertEquals(LaunchOutcome.Opened, outcome(true, Attempt.OPENED))
    }

    @Test
    fun `没选过应用时不许说没装`() {
        // 这张码根本没关联 App。说「这台手机上没装」是把它没记过这件事推给装机状态。
        assertEquals(LaunchOutcome.NoTarget, outcome(false))
        assertEquals(LaunchOutcome.NoTarget, outcome(false, Attempt.NO_HANDLER, Attempt.NO_HANDLER))
    }

    @Test
    fun `每一级都没有处理器才是没装`() {
        assertEquals(LaunchOutcome.NotInstalled, outcome(true, Attempt.NO_HANDLER, Attempt.NO_HANDLER))
    }

    @Test
    fun `掺进一次被拦住就不是没装`() {
        // 这是整条判据的意义所在：说成没装，用户会去重装一个已经装好的 App，
        // 而真正该做的是去系统里那个开关。
        assertEquals(LaunchOutcome.Blocked, outcome(true, Attempt.NO_HANDLER, Attempt.REFUSED))
        assertEquals(LaunchOutcome.Blocked, outcome(true, Attempt.REFUSED))
    }

    @Test
    fun `一级都没试过不许当成打开成功`() {
        // 取数层真出这种结果时，宁可报「拦住」也不静默——静默就是 issue #39。
        assertEquals(LaunchOutcome.Blocked, outcome(true))
    }

    @Test
    fun `只从启动入口起来时要报首页，不许装成到了那一页`() {
        assertEquals(
            LaunchOutcome.OpenedHome,
            launchOutcome(true, listOf(Attempt.NO_HANDLER, Attempt.OPENED), openedHome = true),
        )
        // 深链自己起来的，仍然是 Opened——这一档不需要说话。
        assertEquals(
            LaunchOutcome.Opened,
            launchOutcome(true, listOf(Attempt.OPENED), openedHome = false),
        )
        // 没起来的时候，openedHome 这个标记不能凭空造出一个「成功」。
        assertEquals(
            LaunchOutcome.NotInstalled,
            launchOutcome(true, listOf(Attempt.NO_HANDLER), openedHome = true),
        )
    }

    // ---------- 目的地表本身 ----------

    @Test
    fun `预置那几家固定常驻，顺序按已知表`() {
        // 装没装不再参与判断：入口长期在，未装的点了走既有降级链（网页兜底或明确提示）。
        assertEquals(
            listOf(
                PlatformEntry(DeepLinkLadder.TAOBAO, EntryPurpose.IDENTITY),
                PlatformEntry(DeepLinkLadder.PINDUODUO, EntryPurpose.IDENTITY),
                PlatformEntry(DeepLinkLadder.CAINIAO, EntryPurpose.IDENTITY),
                PlatformEntry(DeepLinkLadder.FENGCHAO, EntryPurpose.IDENTITY),
                PlatformEntry(DeepLinkLadder.JD, EntryPurpose.PENDING),
                PlatformEntry(DeepLinkLadder.XHS, EntryPurpose.PENDING),
            ),
            DeepLinkLadder.knownEntries,
        )
        // 列表与「存一张」的预置那一排共用同一份顺序，不许各自漂。
        assertEquals(
            listOf(
                DeepLinkLadder.TAOBAO,
                DeepLinkLadder.PINDUODUO,
                DeepLinkLadder.CAINIAO,
                DeepLinkLadder.FENGCHAO,
            ),
            DeepLinkLadder.knownEntries
                .filter { it.purpose == EntryPurpose.IDENTITY }
                .map { it.packageName },
        )
    }

    @Test
    fun `预置那几家的包名大小写与空格不算两家`() {
        assertEquals(
            DeepLinkLadder.deepLinksFor(DeepLinkLadder.TAOBAO),
            DeepLinkLadder.deepLinksFor(" COM.Taobao.Taobao "),
        )
    }

    @Test
    fun `京东和小红书在名单里是待取，不是身份码`() {
        // 这两家没有身份码。摆进那一排必须靠动作词说清，不能让人点「去取」落到订单列表。
        assertEquals(
            EntryPurpose.PENDING,
            DeepLinkLadder.knownEntries.first { it.packageName == DeepLinkLadder.JD }.purpose,
        )
        assertEquals(
            EntryPurpose.PENDING,
            DeepLinkLadder.knownEntries.first { it.packageName == DeepLinkLadder.XHS }.purpose,
        )
    }

    @Test
    fun `不认识的那家哪一级都不走`() {
        assertEquals(emptyList<String>(), DeepLinkLadder.deepLinksFor("com.someone.else"))
        assertEquals(emptyList<String>(), DeepLinkLadder.deepLinksFor("com.someone.else", EntryPurpose.PENDING))
        assertEquals(null, DeepLinkLadder.webFallbackFor("com.someone.else"))
    }

    @Test
    fun `直达链接的包名大小写不算两家`() {
        assertEquals(
            DeepLinkLadder.deepLinksFor(DeepLinkLadder.TAOBAO),
            DeepLinkLadder.deepLinksFor("  COM.Taobao.Taobao "),
        )
    }

    // ---------- 身份码那一页到底落在哪儿 ----------

    @Test
    fun `淘宝先身份码，再我的快递，最后菜鸟包裹页`() {
        // 改造前只有裸 h5Url 两种壳：淘宝接了 intent 却落在首页。
        // 带 ali.open.nav 的壳才是「按 h5 目标页导航」，并按页面优先级逐级降。
        val identity = DeepLinkLadder.deepLinksFor(DeepLinkLadder.TAOBAO, EntryPurpose.IDENTITY)
        val pending = DeepLinkLadder.deepLinksFor(DeepLinkLadder.TAOBAO, EntryPurpose.PENDING)

        assertTrue("身份码页至少要有两种壳 + 后备页面", identity.size >= 5)
        assertTrue(pending.size >= 3)
        assertTrue("第一跳必须是带导航动作的 tbopen 壳", identity.first().startsWith("tbopen://m.taobao.com/tbopen/index.html?action=ali.open.nav&module=h5&bootImage=0&h5Url="))
        assertTrue("第二跳换 taobao 正门", identity[1].startsWith("taobao://m.taobao.com/tbopen/index.html?action=ali.open.nav&module=h5&bootImage=0&h5Url="))

        val identityPage = "https%3A%2F%2Fpages-fast.m.taobao.com%2Fwow%2Fz%2Funiapp%2F1011717%2Flast-mile-fe%2Fend-collect-platform%2Fidentity-code%3Fx-ssr%3Dtrue"
        val pendingEncoded = DeepLinkLadder.percentEncode(
            "https://pages-fast.m.taobao.com/wow/z/uniapp/1100333/last-mile-fe/m-end-school-tab/home"
        )
        val cainiaoEncoded = DeepLinkLadder.percentEncode(
            "https://page.cainiao.com/guoguo/app-myexpress-taobao/"
        )
        assertEquals(identityPage, identity.first().substringAfter("h5Url="))
        assertEquals(pendingEncoded, pending.first().substringAfter("h5Url="))

        val pendingFirst = identity.indexOfFirst { it.substringAfter("h5Url=") == pendingEncoded }
        val cainiaoFirst = identity.indexOfFirst { it.substringAfter("h5Url=") == cainiaoEncoded }
        assertTrue("我的快递列表要排在菜鸟包裹页前面", pendingFirst >= 0 && pendingFirst < cainiaoFirst)
        assertTrue("菜鸟包裹页是最后一级", identity.last().substringAfter("h5Url=") == cainiaoEncoded)
    }

    @Test
    fun `h5Url 必须整段百分号编码，否则会被对方截断`() {
        for (purpose in EntryPurpose.entries) {
            for (link in DeepLinkLadder.deepLinksFor(DeepLinkLadder.TAOBAO, purpose)) {
                val query = link.substringAfter("h5Url=")
                assertEquals("裸 : 会让淘宝把参数截成 https:", false, ':' in query)
                assertEquals("裸 / 同上", false, '/' in query)
            }
        }
    }

    @Test
    fun `每一条候选都只交给淘宝 - 没有一条是浏览器链接`() {
        for (purpose in EntryPurpose.entries) {
            for (link in DeepLinkLadder.deepLinksFor(DeepLinkLadder.TAOBAO, purpose)) {
                assertTrue("候选必须以 tbopen/taobao scheme 起头：$link", link.startsWith("tbopen://") || link.startsWith("taobao://"))
                assertTrue("绝不能是 http(s) 浏览器链接：$link", !link.startsWith("http://") && !link.startsWith("https://"))
            }
        }
    }

    @Test
    fun `成功命中第一条就不再往下试`() {
        val run = runLadder(listOf("a", "b", "c")) { if (it == "a") Attempt.OPENED else Attempt.NO_HANDLER }
        assertEquals("a", run.hit)
        assertEquals(listOf(Attempt.OPENED), run.attempts)
    }

    @Test
    fun `第一条失败就走第二条`() {
        val run = runLadder(listOf("a", "b", "c")) { if (it == "b") Attempt.OPENED else Attempt.NO_HANDLER }
        assertEquals("b", run.hit)
        assertEquals(listOf(Attempt.NO_HANDLER, Attempt.OPENED), run.attempts)
    }

    @Test
    fun `全部失败时命中为空 - 调用方退到主页加复制提示`() {
        val run = runLadder(listOf("a", "b")) { Attempt.NO_HANDLER }
        assertEquals(null, run.hit)
        assertEquals(2, run.attempts.size)
        // 全部失败绝不等于去开浏览器：候选表本身已经在上面那条用例里钉死了。
    }

    @Test
    fun `淘宝身份码的网页退路是身份码页，不是待取列表`() {
        assertEquals(
            "https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code?x-ssr=true",
            DeepLinkLadder.webFallbackFor(DeepLinkLadder.TAOBAO, EntryPurpose.IDENTITY),
        )
        assertEquals(
            "https://pages-fast.m.taobao.com/wow/z/uniapp/1100333/last-mile-fe/m-end-school-tab/home",
            DeepLinkLadder.webFallbackFor(DeepLinkLadder.TAOBAO, EntryPurpose.PENDING),
        )
    }

    @Test
    fun `拼多多身份码要带 ID_CODE 那个 tab`() {
        val identity = DeepLinkLadder.deepLinksFor(DeepLinkLadder.PINDUODUO, EntryPurpose.IDENTITY)
        assertEquals(3, identity.size)
        assertTrue("第一条必须带 tab=ID_CODE", "tab=ID_CODE" in identity.first())
        // 最后一条是裸页：前两条都被对方拒了时，至少还落在那一页里。
        assertEquals("pinduoduo://com.xunmeng.pinduoduo/mdkd/package", identity.last())

        assertEquals(
            listOf("pinduoduo://com.xunmeng.pinduoduo/mdkd/package"),
            DeepLinkLadder.deepLinksFor(DeepLinkLadder.PINDUODUO, EntryPurpose.PENDING),
        )
        assertEquals(
            "https://m.pinduoduo.net/mdkd/package?entry_source=18&extra_params=from_wx_jump%3D1&p_channel=0",
            DeepLinkLadder.webFallbackFor(DeepLinkLadder.PINDUODUO, EntryPurpose.IDENTITY),
        )
    }

    @Test
    fun `菜鸟终于有直达身份码的那条 scheme`() {
        // 改造前这一家只会开首页，README 里也写着「不知道它们的 scheme」。
        assertEquals(
            listOf("cainiao://desktop/station_code"),
            DeepLinkLadder.deepLinksFor(DeepLinkLadder.CAINIAO, EntryPurpose.IDENTITY),
        )
        assertEquals(
            "https://market.m.taobao.com/app/cn-yz/multi-activity/authCode.html?bizEntry=ALIPAY_GUOGUO",
            DeepLinkLadder.webFallbackFor(DeepLinkLadder.CAINIAO, EntryPurpose.IDENTITY),
        )
        // 菜鸟的待取栏没有已知入口；不能把身份码那条当成它。
        assertEquals(emptyList<String>(), DeepLinkLadder.deepLinksFor(DeepLinkLadder.CAINIAO, EntryPurpose.PENDING))
    }

    @Test
    fun `京东和小红书落在订单列表`() {
        assertEquals(
            listOf(
                "openjd://virtual?params=%7B%22category%22%3A%22jump%22%2C%22des%22%3A%22orderlist%22%7D",
            ),
            DeepLinkLadder.deepLinksFor(DeepLinkLadder.JD, EntryPurpose.PENDING),
        )
        assertEquals(
            "https://order.jd.com/center/list.action",
            DeepLinkLadder.webFallbackFor(DeepLinkLadder.JD, EntryPurpose.PENDING),
        )

        assertEquals(
            listOf("xhsdiscover://rn/lancer-order/order/list"),
            DeepLinkLadder.deepLinksFor(DeepLinkLadder.XHS, EntryPurpose.PENDING),
        )
        assertEquals(
            "https://www.xiaohongshu.com/",
            DeepLinkLadder.webFallbackFor(DeepLinkLadder.XHS, EntryPurpose.PENDING),
        )

        // 两家都没有身份码。传 IDENTITY 时必须老实返回空表，不能拿订单列表顶替。
        assertEquals(emptyList<String>(), DeepLinkLadder.deepLinksFor(DeepLinkLadder.JD, EntryPurpose.IDENTITY))
        assertEquals(emptyList<String>(), DeepLinkLadder.deepLinksFor(DeepLinkLadder.XHS, EntryPurpose.IDENTITY))
        assertEquals(null, DeepLinkLadder.webFallbackFor(DeepLinkLadder.JD, EntryPurpose.IDENTITY))
        assertEquals(null, DeepLinkLadder.webFallbackFor(DeepLinkLadder.XHS, EntryPurpose.IDENTITY))
    }

    @Test
    fun `丰巢仍然是唯一没有直达链的那家`() {
        // 这一条是显式台账：名单里加一家、或偷偷删掉一条链，都必须在这里改一次。
        val missing = DeepLinkLadder.knownEntries
            .filter { DeepLinkLadder.deepLinksFor(it.packageName, it.purpose).isEmpty() }
            .map { it.packageName }
        assertEquals(listOf(DeepLinkLadder.FENGCHAO), missing)
        assertEquals(null, DeepLinkLadder.webFallbackFor(DeepLinkLadder.FENGCHAO))
    }

    @Test
    fun `预置名单不许有重复或空包名`() {
        assertEquals(
            DeepLinkLadder.knownPackages.size,
            DeepLinkLadder.knownPackages.distinct().size,
        )
        DeepLinkLadder.knownPackages.forEach { pkg -> assertTrue("$pkg 不该是空的", pkg.isNotBlank()) }
    }
}
