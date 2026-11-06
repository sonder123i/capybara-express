package dev.showcode.core

/**
 * 打开一家官方 App 的兜底链。
 *
 * 存在的理由：竞品 issue #39 的全部内容是「点了没反应」，而维护者给的解法是去系统里开
 * 「链式启动管理」。**一条路走不通是常态，不是一种意外**——所以这一格必须自己走完几级，
 * 并且最后一级失败时说话，而不是静默。
 *
 * ⚠️ 深链的出处是 `shareven/parcel` 的 `util/ThirdPartyEntry.kt`、`EidenLiu/pickup-code-launcher`
 * 的 `Destination.java`（两个都逐字读过，不是猜的）。它们**随对方版本变**，而我们看不到
 * 对方的版本：这正是我把它们做成「第一级尝试」而不是「唯一一条路」的原因。竞品自己也在
 * 后面留了退路。每一条都必须在装了最新版对应 App 的真机上重测——这里没有一条是官方 API。
 *
 * 层级从「最有用」排到「最保底」：
 * 1. 直达那家 App 里的那一页（省三步，但可能坏）；
 * 2. 该 App 的启动入口（只到首页，却几乎不会坏）；
 * 2.5 同一个 https 页面交回那家 App（它注册了域名时仍然能落到那一页）；
 * 3. 用系统浏览器打开那个页面（**那个页面本身是 H5**，没装 App 的人也走得通）。
 */
object DeepLinkLadder {

    const val TAOBAO = "com.taobao.taobao"
    const val PINDUODUO = "com.xunmeng.pinduoduo"
    /** 菜鸟（驿站会员码/取件码那一类）。包名核实自应用宝的官方详情页 `a.app.qq.com/o/simple.jsp?pkgname=…`。 */
    const val CAINIAO = "com.cainiao.wireless"
    /** 丰巢（柜机寄件码）。包名核实自应用宝的详情页 `sj.qq.com/appdetail/…`，单一来源。 */
    const val FENGCHAO = "com.fcbox.hiveconsumer"
    /** 京东。账号平台之一。 */
    const val JD = "com.jingdong.app.mall"
    /** 小红书。只有订单列表这一栏，没有账号接入。 */
    const val XHS = "com.xingin.xhs"

    /**
     * 账号平台 id（`bound_account.id` / [Platform.id]）→ 官方 App 包名。
     * 空码件在详情页和现场模式都靠它给出口；认不出平台就返回 null，不猜。
     */
    fun packageForPlatform(accountId: String?): String? =
        when (accountId?.trim()?.lowercase()) {
            "pdd" -> PINDUODUO
            "tb" -> TAOBAO
            "jd" -> JD
            else -> null
        }

    /* ---------- 淘宝：待取列表与身份码是两页，不能共用一个 h5Url ---------- */

    /** 淘宝驿站待取列表。竞品那条 tbopen 的 h5Url 就是它。 */
    private const val TAOBAO_PENDING_PAGE =
        "https://pages-fast.m.taobao.com/wow/z/uniapp/1100333/last-mile-fe/m-end-school-tab/home"

    /**
     * 淘宝身份码那一页本身。竞品 `Destination.TAOBAO` 用它，比待取列表再深一跳。
     * 注意 uniapp 编号也不同（1011717 对 1100333），不是把后缀换一换。
     *
     * ⚠️ 这两页不能合并，不只是「两个 URL」：竞品 issue #3 记了实测差异——身份码页给的是
     * CS 开头的码（和菜鸟同码），待取列表给的是 XNST 开头的码，而有的驿站扫码机只认 XNST。
     * 所以「我是谁」和「我有哪些件」各走各的；取件码最终以本 App 里那件包裹自己的码为准。
     */
    private const val TAOBAO_IDENTITY_PAGE =
        "https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code?x-ssr=true"

    private const val TAOBAO_OPEN_PREFIX = "taobao://m.taobao.com/tbopen/index.html?h5Url="
    private const val TBOPEN_PREFIX = "tbopen://m.taobao.com/tbopen/index.html?h5Url="

    /**
     * 带「打开淘宝内 H5 页面」动作的壳。
     *
     * 出处：淘宝开放平台文档里给出的唤端样例就是
     * `tbopen://m.taobao.com/tbopen/index.html?&action=ali.open.nav&module=h5&bootImage=0&…&h5Url=…`
     * （`developer.alibaba.com/docs/api.htm?apiId=55438`）。裸 `?h5Url=` 旧壳在部分版本里
     * 只落到淘宝首页；这一段 action/模块参数是告诉淘宝「按 H5 目标页导航」。
     */
    private const val TAOBAO_NAV_PREFIX =
        "taobao://m.taobao.com/tbopen/index.html?action=ali.open.nav&module=h5&bootImage=0&h5Url="
    private const val TBOPEN_NAV_PREFIX =
        "tbopen://m.taobao.com/tbopen/index.html?action=ali.open.nav&module=h5&bootImage=0&h5Url="

    /**
     * 第三个候选：菜鸟「我的快递」页。
     * 出处：菜鸟官方页面 `page.cainiao.com/guoguo/app-myexpress-taobao/`（待收 / 已收包裹列表）。
     * 它同样能由淘宝的 H5 容器承接，所以放在两页淘宝原生页之后——不是淘宝站内最深的那一跳，
     * 但比只落在首页近一步。
     */
    private const val CAINIAO_PACKAGE_PAGE = "https://page.cainiao.com/guoguo/app-myexpress-taobao/"

    /* ---------- 拼多多：身份码是 mdkd 页里的一个 tab ---------- */

    private const val PDD_PACKAGE_PAGE = "pinduoduo://com.xunmeng.pinduoduo/mdkd/package"

    /** 带 `tab=ID_CODE` 才落到身份码；裸页是默认 tab。三条按「最准到最保底」排。 */
    private const val PDD_IDENTITY_PAGE =
        "$PDD_PACKAGE_PAGE?tab=ID_CODE&entry_source=11&refer_page_name=login&refer_page_sn=10169"

    private const val PDD_IDENTITY_PAGE_SHORT = "$PDD_PACKAGE_PAGE?tab=ID_CODE&entry_source=11"

    /** 网页版没有 `tab=ID_CODE` 这个参数，只能落到 mdkd 那一页；这是它的第三级，不是深链。 */
    private const val PDD_WEB =
        "https://m.pinduoduo.net/mdkd/package?entry_source=18&extra_params=from_wx_jump%3D1&p_channel=0"

    /* ---------- 菜鸟：竞品挖到的那条 scheme，我们之前不知道 ---------- */

    private const val CAINIAO_IDENTITY = "cainiao://desktop/station_code"

    private const val CAINIAO_WEB =
        "https://market.m.taobao.com/app/cn-yz/multi-activity/authCode.html?bizEntry=ALIPAY_GUOGUO"

    /* ---------- 京东 / 小红书：只有「待取」那一栏，没有身份码 ---------- */

    private const val JD_ORDER =
        "openjd://virtual?params=%7B%22category%22%3A%22jump%22%2C%22des%22%3A%22orderlist%22%7D"

    private const val JD_WEB = "https://order.jd.com/center/list.action"

    private const val XHS_ORDER = "xhsdiscover://rn/lancer-order/order/list"

    /** 小红书没有订单列表的网页版，这条只是首页；只在启动入口也失败时才会走到。 */
    private const val XHS_WEB = "https://www.xiaohongshu.com/"

    /**
     * 预置名单，顺序就是屏上的顺序。
     *
     * 前四家有「身份码」这一页，排在前面——这一屏的标题是身份码；京东、小红书只有待取栏，
     * 排后面，动作词也换成「看待取」。
     *
     * ⚠️ 丰巢仍然**没有直达链**：我们确实不知道它的 scheme，走第二级（启动入口），
     * 点了落在那家 App 首页。这不是漏了，是没挖到——不许为了让表好看而编一条。
     */
    val knownEntries: List<PlatformEntry> = listOf(
        PlatformEntry(TAOBAO, EntryPurpose.IDENTITY),
        PlatformEntry(PINDUODUO, EntryPurpose.IDENTITY),
        PlatformEntry(CAINIAO, EntryPurpose.IDENTITY),
        PlatformEntry(FENGCHAO, EntryPurpose.IDENTITY),
        PlatformEntry(JD, EntryPurpose.PENDING),
        PlatformEntry(XHS, EntryPurpose.PENDING),
    )

    /** 只要包名的旧形状。顺序与 [knownEntries] 一致。 */
    val knownPackages: List<String> get() = knownEntries.map { it.packageName }

    /**
     * 预置那一排该列哪几家：[knownEntries] ∩ 这台机器真装着的，顺序按已知表。
     *
     * 这条限制是判据不是细节：**一颗点了只会失败的按钮比没有更坏**，而站在这一格的人
     * 常常正抱着两个包裹。装没装是 data 层查出来的事实，怎么筛是纯的，所以筛法在这儿。
     */
    fun entriesFor(installedPackages: Set<String>): List<PlatformEntry> {
        val have = installedPackages.mapTo(mutableSetOf()) { normalize(it) }
        return knownEntries.filter { it.packageName in have }
    }

    /**
     * 第一级：直达页的 scheme URI。不认识的包、或这家没有这一页时返回空表，直接跳到第二级。
     *
     * 用途必须显式传：淘宝/拼多多的身份码页和待取页是两条不同的链，共用一个包名
     * 就会让「身份码」那颗按钮落到待取列表——这正是改造前发生的事。
     */
    fun deepLinksFor(
        packageName: String?,
        purpose: EntryPurpose = EntryPurpose.IDENTITY,
    ): List<String> = when (val pkg = normalize(packageName)) {
        /* 淘宝：先按「身份码页」跳，再退到「我的快递列表」，最后退到菜鸟包裹页。
           每一页先试带 ali.open.nav 的壳（真正带页面目标），再试旧的裸 h5Url 壳。
           一次跳不对就立刻换下一条——这串里没有任何一条会把用户交给浏览器。 */
        TAOBAO -> {
            val pages = if (purpose == EntryPurpose.IDENTITY) {
                listOf(TAOBAO_IDENTITY_PAGE, TAOBAO_PENDING_PAGE, CAINIAO_PACKAGE_PAGE)
            } else {
                listOf(TAOBAO_PENDING_PAGE, CAINIAO_PACKAGE_PAGE)
            }
            pages.flatMapIndexed { index, page ->
                val encoded = percentEncode(page)
                buildList {
                    add(TBOPEN_NAV_PREFIX + encoded)
                    add(TAOBAO_NAV_PREFIX + encoded)
                    /* 旧壳只留给第一优先页：它大概率只到首页，排在后面的页会白白多跳几次。 */
                    if (index == 0) {
                        add(TAOBAO_OPEN_PREFIX + encoded)
                        add(TBOPEN_PREFIX + encoded)
                    }
                }
            }
        }

        PINDUODUO -> if (purpose == EntryPurpose.IDENTITY) {
            listOf(PDD_IDENTITY_PAGE, PDD_IDENTITY_PAGE_SHORT, PDD_PACKAGE_PAGE)
        } else {
            listOf(PDD_PACKAGE_PAGE)
        }

        CAINIAO -> if (purpose == EntryPurpose.IDENTITY) listOf(CAINIAO_IDENTITY) else emptyList()

        JD -> if (purpose == EntryPurpose.PENDING) listOf(JD_ORDER) else emptyList()

        XHS -> if (purpose == EntryPurpose.PENDING) listOf(XHS_ORDER) else emptyList()

        else -> emptyList()
    }

    /** 第三级：系统浏览器兜底的那个网页。没有网页版的那家返回 null，不拿首页糊弄。 */
    fun webFallbackFor(
        packageName: String?,
        purpose: EntryPurpose = EntryPurpose.IDENTITY,
    ): String? = when (normalize(packageName)) {
        TAOBAO -> if (purpose == EntryPurpose.IDENTITY) TAOBAO_IDENTITY_PAGE else TAOBAO_PENDING_PAGE
        PINDUODUO -> PDD_WEB
        CAINIAO -> if (purpose == EntryPurpose.IDENTITY) CAINIAO_WEB else null
        JD -> if (purpose == EntryPurpose.PENDING) JD_WEB else null
        XHS -> if (purpose == EntryPurpose.PENDING) XHS_WEB else null
        else -> null
    }

    private fun normalize(packageName: String?): String = packageName?.trim()?.lowercase().orEmpty()

    /**
     * 查询参数percent编码。
     *
     * core 里不能引 `android.net.Uri`（那会把这一格从可单测变成要设备），而 `h5Url=` 后面
     * 那个地址里的 `:` 和 `/` 不编码的话，淘宝的参数解析器会把它截断成 `https:`。
     */
    fun percentEncode(raw: String): String = buildString {
        for (ch in raw) {
            val code = ch.code
            val unreserved = code in 'a'.code..'z'.code || code in 'A'.code..'Z'.code ||
                code in '0'.code..'9'.code || ch == '-' || ch == '_' || ch == '.' || ch == '~'
            if (unreserved) append(ch) else append('%').append(code.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

/**
 * 一级一级试完，记下每一级的结果。
 *
 * 只靠 `startActivity` 不抛异常是不够的：成功只说明「淘宝接了这个 intent」，
 * 不代表落在目标页（真机上要用 `dumpsys activity` 顶栈或截图确认，见验证清单）。
 * 这里负责的是「一条不行就换下一条、命中了就不再多跳」——全部失败时 [hit] 为 null，
 * 由调用方退到启动入口，绝不退到浏览器。
 */
data class LadderRun(
    val hit: String?,
    val attempts: List<Attempt>,
)

fun runLadder(candidates: List<String>, attempt: (String) -> Attempt): LadderRun {
    val attempts = mutableListOf<Attempt>()
    for (uri in candidates) {
        val result = attempt(uri)
        attempts += result
        if (result == Attempt.OPENED) return LadderRun(uri, attempts)
    }
    return LadderRun(null, attempts)
}

/** 这一行点开的是「我是谁」那一页，还是「我有哪些件」那一栏。 */
enum class EntryPurpose { IDENTITY, PENDING }

/**
 * 预置名单里的一行。同一个包只出现一次，用途就是它的主业：
 * 淘宝、拼多多两页都有，摆在这一屏上就是身份码；京东、小红书没有身份码，主业是待取。
 */
data class PlatformEntry(val packageName: String, val purpose: EntryPurpose)

/** 某一级尝试的结果。「没有处理器」和「有处理器但被拦住」必须是两个值——见 [launchOutcome]。 */
enum class Attempt { OPENED, NO_HANDLER, REFUSED }

/**
 * 由每一级的结果推结局。顺序是刻意的：
 *
 * - 只要有一级真起来了就不是失败；但**从启动入口起来的那一次要单独报**：
 *   用户落在首页，不是那一页，这一档必须能说一句话（[LaunchOutcome.OpenedHome]）。
 * - **全都是 `NO_HANDLER` 才是「没装」**。掺进一个 `REFUSED` 就说明系统里有东西在拦，
 *   那是 ROM 的开关问题，叫用户去重装一个已经装好的 App 是把他支走。
 * - 一上来就没包名（这张码没记过是哪家）是另一件事，不许和「没装」共用一句话。
 */
fun launchOutcome(
    hasPackage: Boolean,
    attempts: List<Attempt>,
    openedHome: Boolean = false,
): LaunchOutcome = when {
    !hasPackage -> LaunchOutcome.NoTarget
    openedHome && attempts.contains(Attempt.OPENED) -> LaunchOutcome.OpenedHome
    attempts.contains(Attempt.OPENED) -> LaunchOutcome.Opened
    attempts.isNotEmpty() && attempts.all { it == Attempt.NO_HANDLER } -> LaunchOutcome.NotInstalled
    else -> LaunchOutcome.Blocked
}
