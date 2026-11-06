package dev.showcode.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import dev.showcode.ui.GlassPressIndication

/*
 * 两套 Material 色板都直接读 `Palette.kt` 的字面值，而不是读那些 getter：
 * getter 依赖当前主题快照，在静态 val 初始化时会被定格在亮色。
 */

private val ShowCodeLightColors = lightColorScheme(
    primary = ShowCodeLightPalette.onsiteInk,
    onPrimary = ShowCodeLightPalette.onsiteSurface,
    secondary = ShowCodeLightPalette.accent,
    onSecondary = ShowCodeLightPalette.onsiteSurface,
    background = ShowCodeLightPalette.canvas,
    onBackground = ShowCodeLightPalette.ink,
    surface = ShowCodeLightPalette.canvas,
    onSurface = ShowCodeLightPalette.ink,
    surfaceVariant = ShowCodeLightPalette.surfaceSunken,
    onSurfaceVariant = ShowCodeLightPalette.inkSecondary,
    outline = ShowCodeLightPalette.hairline,
    outlineVariant = ShowCodeLightPalette.hairline,
    surfaceContainerLowest = ShowCodeLightPalette.canvas,
    surfaceContainerLow = ShowCodeLightPalette.surfaceCard,
    surfaceContainer = ShowCodeLightPalette.surfaceCard,
    surfaceContainerHigh = ShowCodeLightPalette.surfaceSunken,
    surfaceContainerHighest = ShowCodeLightPalette.surfaceDeep,
    surfaceBright = ShowCodeLightPalette.canvas,
    surfaceDim = ShowCodeLightPalette.surfaceCard,
    secondaryContainer = ShowCodeLightPalette.accentTint,
    onSecondaryContainer = ShowCodeLightPalette.ink,
    tertiary = ShowCodeLightPalette.accent,
    onTertiary = ShowCodeLightPalette.onsiteSurface,
    tertiaryContainer = ShowCodeLightPalette.accentTint,
    onTertiaryContainer = ShowCodeLightPalette.ink,
    inverseSurface = ShowCodeLightPalette.onsiteInk,
    inverseOnSurface = ShowCodeLightPalette.onsiteSurface,
    error = ShowCodeLightPalette.urgencyAtRisk,
)

/**
 * 暗色档逐值取自教务 `Theme.kt` 的 `DarkColorScheme`（第 40–71 行），
 * 只把我们独有的三态色/现场黑白换成本地 palette。
 */
private val ShowCodeDarkColors = darkColorScheme(
    primary = Color(0xFF88BFFF),
    onPrimary = Color(0xFF002F55),
    primaryContainer = Color(0xFF183F68),
    onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFFB3BDCC),
    onSecondary = Color(0xFF101828),
    secondaryContainer = Color(0xFF293343),
    onSecondaryContainer = Color(0xFFF8F9FC),
    tertiary = Color(0xFFB8C4FF),
    onTertiary = Color(0xFF202A5E),
    tertiaryContainer = Color(0xFF303B71),
    onTertiaryContainer = Color(0xFFE0E5FF),
    background = ShowCodeDarkPalette.canvas,
    onBackground = ShowCodeDarkPalette.ink,
    surface = ShowCodeDarkPalette.canvas,
    onSurface = ShowCodeDarkPalette.ink,
    surfaceVariant = ShowCodeDarkPalette.surfaceSunken,
    onSurfaceVariant = ShowCodeDarkPalette.inkMuted,
    surfaceContainerLowest = Color(0xFF0B0E13),
    surfaceContainerLow = Color(0xFF171B22),
    surfaceContainer = ShowCodeDarkPalette.surfaceCard,
    surfaceContainerHigh = ShowCodeDarkPalette.surfaceSunken,
    surfaceContainerHighest = ShowCodeDarkPalette.surfaceDeep,
    surfaceBright = ShowCodeDarkPalette.surfaceDeep,
    surfaceDim = Color(0xFF0B0E13),
    outline = Color(0xFF667085),
    outlineVariant = Color(0xFF344054),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF742A27),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = ShowCodeDarkPalette.onsiteInk,
    inverseOnSurface = ShowCodeDarkPalette.onsiteSurface,
    surfaceTint = Color(0xFF88BFFF),
)

/**
 * 主题装配。
 *
 * 明/暗跟随系统；`ShowCodePaletteState` 在 **内容组合之前** 写入，避免暗色首帧先按亮色画一遍。
 * 页面会因系统夜间模式变更而重建 Activity，因此 `remember(darkTheme)` 每次都是干净的一次写入。
 */
@Composable
fun ShowCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val dark = remember(darkTheme) {
        ShowCodePaletteState.applyDark(darkTheme)
        darkTheme
    }
    CompositionLocalProvider(LocalIndication provides GlassPressIndication) {
        MaterialTheme(
            colorScheme = if (dark) ShowCodeDarkColors else ShowCodeLightColors,
            typography = showCodeTypography(),
            content = content,
        )
    }
}
