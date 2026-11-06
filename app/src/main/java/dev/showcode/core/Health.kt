package dev.showcode.core

/**
 * 权限健康。story-5 的前提：权限失效是**静默**的——系统不会通知用户，
 * 他只会觉得「最近好像没新快递」，然后把锅甩给「最近没买东西」。
 * 所以这个模型必须常驻自检，不能只在 onboarding 检查一次。
 */
enum class HealthIssue {
    /**
     * 权限都正常却长时间没同步过：最可能的解释是后台进程被国产 ROM 杀了。
     * 这是唯一会进 [HealthReport.issues] 的项——它对应「件可能根本没进来」，
     * 而短信、通知监听只是补充手段：没开不该在首页常驻报警，只在设置里留中性条目。
     */
    BACKGROUND_SUSPECTED,

    /** 小米「通知类短信」开关。无法程序化查询，只能按厂商常驻提醒，且只进体检表不进横条。 */
    MIUI_SMS_CLASS,
}

data class HealthReport(
    /** 可程序化判定、会进首页说明的失效项。权限缺失不在其中，只进设置页体检表。 */
    val issues: List<HealthIssue>,
    /** 只能人工确认的提醒项（分机型开关）。只出现在体检表里，不参与首页。 */
    val manualChecks: List<HealthIssue>,
) {
    val worst: HealthIssue? get() = issues.firstOrNull()

    val allGood: Boolean get() = issues.isEmpty() && manualChecks.isEmpty()
}

/**
 * 权限都正常却这么久没同步过，就怀疑后台被限制。6 小时是保守值：宁可晚报，不可误报。
 *
 * ⚠️ 这条判据必须和 [hasUnpicked] 一起用：`lastSyncAt` 只在**真的收到消息**时盖章，
 * 所以「今天没买东西」也会让它超时。真机上闲 7 小时就常驻红条——那是误报，
 * 而一条常驻的误报会把真正需要看的两条（短信权限、通知权限）一起废掉。
 */
const val BACKGROUND_SUSPECT_MS = 6 * HOUR_MS

fun healthReport(
    smsGranted: Boolean,
    notificationEnabled: Boolean,
    lastSyncAt: Long?,
    now: Long,
    isXiaomi: Boolean,
    /** 一件都没得取时，长时间没同步是正常状态，不是后台被杀。 */
    hasUnpicked: Boolean,
): HealthReport {
    val issues = buildList {
        /* 短信、通知监听都只是补充手段：没开不进 issues、不上首页，只在设置·权限体检里
           用中性灰说明。主路径是平台账号，后台异常判定也要求通知监听正常——
           关掉通知后「该同步却没同步」失去可信信号，此时报警只会变成误报。 */
        if (notificationEnabled && hasUnpicked &&
            lastSyncAt != null && now - lastSyncAt > BACKGROUND_SUSPECT_MS
        ) {
            add(HealthIssue.BACKGROUND_SUSPECTED)
        }
    }
    val manualChecks = buildList {
        if (isXiaomi && smsGranted) add(HealthIssue.MIUI_SMS_CLASS)
    }
    return HealthReport(issues = issues, manualChecks = manualChecks)
}

/**
 * 首页只保留这一条中性灰、可关闭的说明：后台可疑（件可能真的没进来）。
 * 关闭记在当时的 [lastSyncAt] 上——真的同步过一次之后时间戳会变，问题再来时说明会重新出现，
 * 既不会一次关掉永久失明，也不会每进一次首页就再弹一次。
 */
fun shouldShowBackgroundNote(
    worst: HealthIssue?,
    lastSyncAt: Long?,
    dismissedForSyncAt: Long,
): Boolean = worst == HealthIssue.BACKGROUND_SUSPECTED &&
    lastSyncAt != null &&
    lastSyncAt != dismissedForSyncAt
