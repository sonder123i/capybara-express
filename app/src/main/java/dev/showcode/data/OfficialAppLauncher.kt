package dev.showcode.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.showcode.core.Attempt
import dev.showcode.core.DeepLinkLadder
import dev.showcode.core.EntryPurpose
import dev.showcode.core.LaunchOutcome
import dev.showcode.core.NotifyApps
import dev.showcode.core.launchOutcome
import dev.showcode.core.runLadder

/**
 * 「打开那家官方 App」的兜底链，只依赖 [Context]。
 *
 * 从 [ParcelRepository] 里抽出来，是因为桌面快捷方式必须在建库、建 Compose 之前就跳走：
 * 为了一条 URI 去初始化数据库、读一遍收件箱，是拿几百毫秒换一次「点了没反应」。
 * 判据和顺序仍然只有这一份——快捷方式与 App 内那颗按钮走的是同一条链，不许各写一遍。
 */
object OfficialAppLauncher {

    /**
     * 按「最有用 → 最保底」试完，并把每一级的结果如实记下来。
     *
     * 深链成功就停：再把启动入口拉一次，会把刚打开的那一页拽回首页。
     * 2.5 级是把同一个 https 页面交回那家 App（它注册了域名就还能落到那一页），
     * 它不接才交给系统浏览器——[allowWebFallback] 为 false 时连这一步也不做，
     * 「去淘宝查询」要的是「单号已复制，去淘宝粘贴搜索」，不是把用户扔进浏览器。
     */
    fun open(
        context: Context,
        packageName: String?,
        purpose: EntryPurpose,
        allowWebFallback: Boolean = true,
    ): LaunchOutcome {
        val pkg = NotifyApps.normalizePackage(packageName)
            ?: return launchOutcome(hasPackage = false, attempts = emptyList())

        val ladder = runLadder(DeepLinkLadder.deepLinksFor(pkg, purpose)) { uri ->
            startView(context, uri, pkg)
        }
        val attempts = ladder.attempts.toMutableList()
        if (ladder.hit != null) return launchOutcome(true, attempts)

        attempts += runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return@runCatching Attempt.NO_HANDLER
            context.startActivity(
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
            Attempt.OPENED
        }.getOrElse { classifyStartFailure(it) }
        /* 这一档只把 App 拉起来了，用户落在首页——必须报 OpenedHome，不能混进 Opened。
           竞品在同一格弹的是「已打开官方 App，请在 App 内查看」，静默是最坏的那种成功。 */
        if (attempts.last() == Attempt.OPENED) {
            return launchOutcome(true, attempts, openedHome = true)
        }

        if (allowWebFallback) {
            DeepLinkLadder.webFallbackFor(pkg, purpose)?.let { url ->
                attempts += startView(context, url, pkg)
                if (attempts.last() == Attempt.OPENED) return launchOutcome(true, attempts)
                attempts += startView(context, url, null)
            }
        }
        return launchOutcome(true, attempts)
    }

    /**
     * 一个 VIEW intent。带包名时只问那一家：它不接就是 NO_HANDLER，不会被浏览器兜住。
     *
     * `CLEAR_TOP|SINGLE_TOP` 是跟竞品学的：同一家 App 已经被深链拉起一次时，
     * 不再叠一层新的任务栈。
     */
    internal fun startView(context: Context, uri: String, packageName: String?): Attempt = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        if (packageName != null) {
            intent.setPackage(packageName)
            /* `startActivity` 成功只说明「有人接了」，不说明接的是谁。
               先解一次处理器：落到浏览器或别的 App 就当这一级没接，换下一条候选。
               页面本身对不对（身份码 vs 首页）只能真机用 dumpsys/截图确认。 */
            val resolved = context.packageManager.resolveActivity(intent, 0)
            if (resolved?.activityInfo?.packageName != packageName) return@runCatching Attempt.NO_HANDLER
        }
        context.startActivity(intent)
        Attempt.OPENED
    }.getOrElse { classifyStartFailure(it) }

    /**
     * 只有 `ActivityNotFoundException` 能说明「这台机器上没有能接它的东西」。
     * 其余异常（安全策略、后台启动限制、ROM 拦截）都说明**有**，只是不让——那是另一句话。
     */
    internal fun classifyStartFailure(error: Throwable): Attempt =
        if (error is ActivityNotFoundException) Attempt.NO_HANDLER else Attempt.REFUSED
}
