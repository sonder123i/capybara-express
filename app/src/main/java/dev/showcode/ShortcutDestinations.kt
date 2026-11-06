package dev.showcode

import android.content.Intent
import dev.showcode.core.DeepLinkLadder
import dev.showcode.core.EntryPurpose
import dev.showcode.core.PlatformEntry

/**
 * 桌面快捷方式 → 目的地。
 *
 * 静态快捷方式在 XML 里只能写 action，写不了 extra，所以每个目的地一个 action；
 * 钉到桌面的那颗是运行时建的，用同一个 action + 两个 extra。两条路最后都收在这里，
 * 并且**只认 [DeepLinkLadder.knownEntries] 里真有的那一行**——MainActivity 是导出的，
 * 不能让任意包名借这颗按钮把别的 App 拉起来。
 */
object ShortcutDestinations {

    const val ACTION_OPEN = "dev.showcode.action.OPEN_DESTINATION"
    const val EXTRA_PACKAGE = "sc_package"
    const val EXTRA_PURPOSE = "sc_purpose"

    const val ACTION_TAOBAO_IDENTITY = "dev.showcode.action.OPEN_TAOBAO_IDENTITY"
    const val ACTION_PINDUODUO_IDENTITY = "dev.showcode.action.OPEN_PINDUODUO_IDENTITY"
    const val ACTION_CAINIAO_IDENTITY = "dev.showcode.action.OPEN_CAINIAO_IDENTITY"
    const val ACTION_TAOBAO_PENDING = "dev.showcode.action.OPEN_TAOBAO_PENDING"
    const val ACTION_JD_PENDING = "dev.showcode.action.OPEN_JD_PENDING"
    const val ACTION_XHS_PENDING = "dev.showcode.action.OPEN_XHS_PENDING"

    private val byAction: Map<String, PlatformEntry> = mapOf(
        ACTION_TAOBAO_IDENTITY to PlatformEntry(DeepLinkLadder.TAOBAO, EntryPurpose.IDENTITY),
        ACTION_PINDUODUO_IDENTITY to PlatformEntry(DeepLinkLadder.PINDUODUO, EntryPurpose.IDENTITY),
        ACTION_CAINIAO_IDENTITY to PlatformEntry(DeepLinkLadder.CAINIAO, EntryPurpose.IDENTITY),
        ACTION_TAOBAO_PENDING to PlatformEntry(DeepLinkLadder.TAOBAO, EntryPurpose.PENDING),
        ACTION_JD_PENDING to PlatformEntry(DeepLinkLadder.JD, EntryPurpose.PENDING),
        ACTION_XHS_PENDING to PlatformEntry(DeepLinkLadder.XHS, EntryPurpose.PENDING),
    )

    /** 认不出来返回 null：这不是「默认首页」的信号，是「这条 intent 不归我管」。 */
    fun entryOf(intent: Intent?): PlatformEntry? {
        val action = intent?.action ?: return null
        byAction[action]?.let { return it }
        if (action != ACTION_OPEN) return null

        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return null
        val purpose = intent.getStringExtra(EXTRA_PURPOSE)
            ?.let { name -> EntryPurpose.entries.firstOrNull { it.name == name } }
            ?: EntryPurpose.IDENTITY
        return DeepLinkLadder.knownEntries.firstOrNull {
            it.packageName == pkg && it.purpose == purpose
        }
    }

    /** 快捷方式的图标。认不出的包不给图标——那条路本来就不该走到。 */
    fun iconFor(entry: PlatformEntry): Int = when (entry.packageName) {
        DeepLinkLadder.TAOBAO -> R.drawable.ic_shortcut_taobao
        DeepLinkLadder.PINDUODUO -> R.drawable.ic_shortcut_pinduoduo
        DeepLinkLadder.CAINIAO -> R.drawable.ic_shortcut_cainiao
        DeepLinkLadder.JD -> R.drawable.ic_shortcut_jd
        DeepLinkLadder.XHS -> R.drawable.ic_shortcut_xhs
        else -> R.mipmap.ic_launcher
    }
}
