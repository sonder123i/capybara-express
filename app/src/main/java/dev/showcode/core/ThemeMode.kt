package dev.showcode.core

/**
 * 外观模式。
 *
 * 三档而不是两档：多数人要的是「跟系统」，但夜里想固定暗、白天想固定亮的人也不少。
 * [SYSTEM] 是默认值，也是唯一不需要用户做决定的那一档。
 */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun of(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
