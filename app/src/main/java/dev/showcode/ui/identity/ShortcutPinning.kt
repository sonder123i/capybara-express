package dev.showcode.ui.identity

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.widget.Toast
import dev.showcode.MainActivity
import dev.showcode.R
import dev.showcode.ShortcutDestinations
import dev.showcode.core.PlatformEntry

/**
 * 把一行预置入口钉成桌面图标。
 *
 * 和静态长按快捷方式不是二选一：静态那六个要长按才看得见，钉出来的是**一眼就在桌面上**，
 * 而且只有用户自己知道哪一家值得占一格。两者的 intent 走同一张目的地表，
 * 所以它们永远不会指向两套不同的页面。
 */
object ShortcutPinning {

    fun request(context: Context, entry: PlatformEntry, label: String) {
        val manager = context.getSystemService(ShortcutManager::class.java)
        if (manager == null || !manager.isRequestPinShortcutSupported) {
            Toast.makeText(context, R.string.shortcut_pin_unsupported, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(context, MainActivity::class.java)
            .setAction(ShortcutDestinations.ACTION_OPEN)
            .putExtra(ShortcutDestinations.EXTRA_PACKAGE, entry.packageName)
            .putExtra(ShortcutDestinations.EXTRA_PURPOSE, entry.purpose.name)

        val shortcut = ShortcutInfo.Builder(
            context,
            "pinned_${entry.packageName}_${entry.purpose.name.lowercase()}",
        )
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(Icon.createWithResource(context, ShortcutDestinations.iconFor(entry)))
            .setIntent(intent)
            .build()

        runCatching { manager.requestPinShortcut(shortcut, null) }
            .onFailure {
                Toast.makeText(context, R.string.shortcut_pin_failed, Toast.LENGTH_SHORT).show()
            }
    }
}
