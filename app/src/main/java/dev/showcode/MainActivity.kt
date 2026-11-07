package dev.showcode

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import dev.showcode.core.LaunchOutcome
import dev.showcode.data.OfficialAppLauncher
import dev.showcode.ui.ShowCodeRoot
import dev.showcode.ui.theme.ThemeController
import dev.showcode.ui.theme.ShowCodeTheme
import dev.showcode.ui.theme.StartupLogoAnimation
import dev.showcode.widget.WidgetNav

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        /* 必须早于 super.onCreate：系统 splash 的退出监听要在 Activity 创建前挂上，
           否则第一帧已经画完，拿不到图标的实际 bounds。 */
        StartupLogoAnimation.install(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        /* 桌面快捷方式要在建 Compose 之前就跳走：这条路不读库、不建状态，
           目标是「按住图标 → 一下 → 已经在对方 App 里」。savedInstanceState 非空时
           不重放：那是转屏/重建，快捷方式早就处理过了，再跳一次会把用户拽走。 */
        if (savedInstanceState == null && handleShortcut(intent)) return
        // 冷启动也走一次翻译：划词进来时 onCreate 拿到的就是那条 PROCESS_TEXT，
        // 直接塞给 StateFlow 的话 routeOf 是 null，App 会安静地停在首页。
        WidgetNav.request.value = WidgetNav.intakeOf(intent)
        /* 在 setContent 之前把主题快照定下来：否则暗色首帧会先按亮色画一遍，
           正是「换主题闪一下」的来源。
           来源是「用户偏好」而不是系统——用户在设置里固定亮/暗时，系统怎么变都不该跟。 */
        val systemNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        ThemeController.install(
            mode = (application as ShowCodeApp).repository.prefs.themeMode,
            systemDark = systemNight,
        )
        setContent {
            ShowCodeTheme(darkTheme = ThemeController.isDark) {
                /* 系统栏图标跟着**实际**明暗走，而不是跟系统：用户在亮底上强制暗色时，
                   状态栏图标也必须变白。所以它挂在主题里面，主题一变就跟着变。 */
                val dark = ThemeController.isDark
                LaunchedEffect(dark) {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                ShowCodeRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        /* singleTask：App 已经在前台时，长按快捷方式走的是这一条。 */
        if (handleShortcut(intent)) return
        WidgetNav.request.value = WidgetNav.intakeOf(intent)
    }

    /**
     * 快捷方式不经过页面：直接走 [OfficialAppLauncher] 那条链，然后把自己收掉。
     * 失败时不静默——竞品 issue #39 的全部内容就是一句「点了没反应」，而它的答案
     * 是 ROM 里的一个开关；那句话必须出现在用户眼前。
     */
    private fun handleShortcut(intent: Intent?): Boolean {
        val entry = ShortcutDestinations.entryOf(intent) ?: return false
        val outcome = OfficialAppLauncher.open(this, entry.packageName, entry.purpose)
        shortcutMessage(outcome)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        finish()
        return true
    }

    private fun shortcutMessage(outcome: LaunchOutcome): Int? = when (outcome) {
        LaunchOutcome.Opened -> null
        LaunchOutcome.OpenedHome -> R.string.identity_launch_home
        LaunchOutcome.Blocked -> R.string.identity_launch_blocked
        LaunchOutcome.NotInstalled -> R.string.identity_launch_not_installed
        LaunchOutcome.NoTarget -> R.string.identity_launch_no_target
    }
}
