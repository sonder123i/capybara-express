package dev.showcode.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.showcode.core.ThemeMode

/**
 * 外观模式的唯一真相。
 *
 * 为什么是一个进程级单例而不是从 Compose 树里传下来：主题要在 `setContent` **之前**
 * 就定下来（否则暗色首帧会先按亮色画一遍），而那时还没有 Composition。
 *
 * 为什么不重建 Activity：`recreate()` 会吃掉那颗开关的滑动动画——用户点下去，
 * 屏幕闪一下、开关归位，看起来像「没生效」。所以改成只换内存里的快照，
 * 让所有读 [ShowCodePaletteState] 的地方自然重组。窗口底色由 MainActivity 的
 * applyWindowBackground 运行时同步：`values-night/` 只在系统 uiMode=night 时生效，
 * 而本 App 允许在系统浅色下固定暗色。层级转场会露出那一层，必须跟着换，
 * 否则暗色跳页时边缘会出现一条白边（2026-11-11 实拍复现）。
 */
object ThemeController {

    /** 用户的选择。改它会立刻重组整个 App。 */
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /** 系统当前的明暗。由 Activity 在配置变化时喂进来。 */
    var systemDark by mutableStateOf(false)
        private set

    /** 现在到底该用哪套色板。 */
    val isDark: Boolean
        get() = when (mode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    /** 冷启动用：把偏好与系统状态一次装好，并同步色板快照。 */
    fun install(mode: ThemeMode, systemDark: Boolean) {
        this.mode = mode
        this.systemDark = systemDark
        ShowCodePaletteState.applyDark(isDark)
    }

    /** 系统明暗变了（Activity 被重建、或配置变化）。 */
    fun onSystemDark(dark: Boolean) {
        if (systemDark == dark) return
        systemDark = dark
        ShowCodePaletteState.applyDark(isDark)
    }

    /** 用户点了开关或选了三档。调用方负责落盘。 */
    fun set(mode: ThemeMode) {
        if (this.mode == mode) return
        this.mode = mode
        ShowCodePaletteState.applyDark(isDark)
    }

    /** 开关是二元的：从当前状态推到它的反面。跟随系统时按「系统现在是什么」来推。 */
    fun toggle() = set(if (isDark) ThemeMode.LIGHT else ThemeMode.DARK)
}
