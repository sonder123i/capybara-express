package dev.showcode.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.showcode.core.account.AccountState
import dev.showcode.core.account.BoundAccount

/**
 * 前台静默同步的判据。
 *
 * 为什么是 15 分钟：一次账号抓取要开 WebView、跑页面脚本，代价以十秒计；而快递到站这件事的
 * 粒度本来就是「小时」——短信/通知那条路仍然是实时的。15 分钟足够让「打开 App 顺手看一眼」
 * 拿到新件，又不会在切进切出时反复烧电。**只认成功过的章**：失败不推进 `last_sync_at`
 * （见 AccountSyncEngine），所以失败之后不会被这句节流挡住，下一次进前台仍会再试。
 */
const val AUTO_SYNC_THROTTLE_MS = 15L * 60 * 1000

/** 进前台后先让首屏画完再起同步，避免和首帧抢主线程。 */
const val AUTO_SYNC_DELAY_MS = 1_000L

/**
 * 瞬时失败（断网/超时/5xx/结构异常）的退避窗口。
 * 失败不推进 last_sync_at，所以不单独记一笔退避，就会每次启动都重撞一次同一堵墙；
 * 30 分钟后自动再试，手动刷新不受这条限制（用户主动按的，就该现在跑）。
 */
const val AUTO_SYNC_TRANSIENT_BACKOFF_MS = 30L * 60 * 1000

sealed interface AutoSyncDecision {
    /** 可以同步的账号（已排除停用与 BROKEN）。 */
    data class Run(val accounts: List<BoundAccount>) : AutoSyncDecision

    data class Skip(
        val reason: String,
        /** 无网络是唯一需要给用户一句中性提示的跳过。 */
        val offline: Boolean = false,
    ) : AutoSyncDecision
}

/**
 * 纯判定，单测不需要 Activity/WebView/数据库。
 *
 * 顺序就是标题里的「跳过条件」：本会话已跑 → 演示 → 无账号 → 已有同步 → 无网络 →
 * 节流 → 没有可用账号（停用 / BROKEN）。
 */
internal fun decideAutoSync(
    demo: Boolean,
    accounts: List<BoundAccount>,
    syncing: Boolean,
    online: Boolean,
    lastSuccessAt: Long?,
    now: Long,
    alreadyRanThisSession: Boolean,
    /** 最近一次瞬时失败的时刻；0/null = 没有待退避的失败。 */
    lastTransientFailureAt: Long? = null,
): AutoSyncDecision {
    /* 演示先判：演示态一旦成立，无论这个会话跑过没有都不该碰真实账号。 */
    if (demo) return AutoSyncDecision.Skip("演示模式")
    if (alreadyRanThisSession) return AutoSyncDecision.Skip("本会话已经跑过一次")
    if (accounts.isEmpty()) return AutoSyncDecision.Skip("没有已绑定账号")
    if (syncing) return AutoSyncDecision.Skip("已有同步在跑")
    if (!online) return AutoSyncDecision.Skip("设备无网络", offline = true)
    /* 上一次是瞬时失败：半小时内不再自动重撞。登录失效不在这个窗口里——
       那种失败要用户重新登录才有意义，退避也等不出结果。 */
    if (lastTransientFailureAt != null && lastTransientFailureAt > 0 &&
        now - lastTransientFailureAt < AUTO_SYNC_TRANSIENT_BACKOFF_MS
    ) {
        return AutoSyncDecision.Skip("上次是临时失败，等一会儿再试")
    }
    if (lastSuccessAt != null && now - lastSuccessAt < AUTO_SYNC_THROTTLE_MS) {
        return AutoSyncDecision.Skip("距上次成功同步不足 15 分钟")
    }
    /* BROKEN = 凭据解不开，重试没有意义，还会在账号卡上刷出一条同样的红字。
       停用的账号同理（用户主动关掉的开关不该被自动流程绕过去）。 */
    val eligible = accounts.filter { it.enabled && it.state != AccountState.BROKEN }
    if (eligible.isEmpty()) return AutoSyncDecision.Skip("账号都不可用（已停用或 BROKEN）")
    return AutoSyncDecision.Run(eligible)
}

/**
 * 自动同步完成后的那一句话。和手动同步共用「成功但零新增」的措辞，
 * 只在前面点明这是「刚刚」发生的——用户没有按任何按钮，得知道他看到的是新鲜的。
 */
internal fun autoSyncMessage(result: AccountSyncResult): String {
    val base = syncSuccessMessage(result)
    return if (base == "同步成功 · 没有新件") "刚刚同步 · 没有新件" else "刚刚同步 · " + base.removePrefix("同步成功 · ")
}

/** 网络可用且系统认为真的能出去（VALIDATED），不是「连上了个没有出口的 Wi-Fi」。
 *  读不到就当作「有网」：这条判据只用来省掉一次注定失败的抓取，不该反过来挡住同步。 */
fun hasValidatedNetwork(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}.getOrDefault(true)
