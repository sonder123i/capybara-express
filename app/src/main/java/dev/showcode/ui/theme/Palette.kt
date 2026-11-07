package dev.showcode.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 全 App 唯一色板。明/暗两套值都从教务 `ui/theme/Color.kt` 与 `Theme.kt` 的
 * `LightColorScheme` / `DarkColorScheme` 取，差异只在「我们的三态色、现场黑白」这两组
 * 业务专用色上——它们不在参考工程里，按下述规则从参考的 dark container 推导。
 *
 * 为什么用全局快照状态而不是 CompositionLocal：
 * 现有屏大量直接引用 `Ink` / `SurfaceCard` 这类顶层 val，约 600 处。把每个引用都改成
 * `LocalShowCodePalette.current` 是一次没有收益的大迁移；让这些同名 val 变成读状态的
 * getter，主题层只改一处，所有屏自动跟着换。Compose 的快照读同样会触发重组。
 */
@Immutable
data class ShowCodePalette(
    val isDark: Boolean,
    val canvas: Color,
    val surfaceCard: Color,
    val surfaceSunken: Color,
    val surfaceDeep: Color,
    val surfaceFloating: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val borderDueSoon: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val accent: Color,
    val accentInk: Color,
    val accentTint: Color,
    val urgencySafe: Color,
    val urgencyDueSoon: Color,
    val urgencyAtRisk: Color,
    val urgencySafeTint: Color,
    val urgencyDueSoonTint: Color,
    val urgencyAtRiskTint: Color,
    /* 同色系「降一档」的文字色：压在各自的 tint 上当副标题用。
       不是半透明（那会掉对比度），是把同一个色相压深/提亮到能过 4.5:1 的值。 */
    val urgencySafeInk: Color,
    /** 成功色按钮的「键程」底边：同一色相压深一档。 */
    val urgencySafeEdge: Color,
    val urgencyDueSoonInk: Color,
    val urgencyAtRiskInk: Color,
    val surfaceInset: Color,
    val surfaceInsetStrong: Color,
    val onsiteInk: Color,
    val onsiteSurface: Color,
    val glassHighlight: Color,
    val glassBorderLight: Color,
    val glassBorderDark: Color,
    val glassOverlay: Color,
)

/** 明亮档：逐值等于教务 `LightColorScheme` + 本仓库三态色（见 `Color.kt` 的原始清单）。 */
val ShowCodeLightPalette = ShowCodePalette(
    isDark = false,
    canvas = Color(0xFFFFFFFF),
    surfaceCard = Color(0xFFF9FAFD),
    surfaceSunken = Color(0xFFE8ECF3),
    surfaceDeep = Color(0xFFD5DCE7),
    surfaceFloating = Color(0xFFF1F1F1),
    hairline = Color(0xFFE4E8F0),
    hairlineStrong = Color(0xFFD5DCE7),
    borderDueSoon = Color(0xFFFFD2A5),
    ink = Color(0xFF101828),
    inkSecondary = Color(0xFF344054),
    inkMuted = Color(0xFF59616F),
    inkFaint = Color(0xFF98A2B3),
    accent = Color(0xFF0A84FF),
    accentInk = Color(0xFF0057D9),
    accentTint = Color(0xFFE6F3FF),
    urgencySafe = Color(0xFF00B578),
    urgencyDueSoon = Color(0xFFFF8F1F),
    urgencyAtRisk = Color(0xFFFF3141),
    urgencySafeTint = Color(0xFFE6F8F2),
    urgencyDueSoonTint = Color(0xFFFFF4E9),
    urgencyAtRiskTint = Color(0xFFFFEAEC),
    urgencySafeInk = Color(0xFF00734C),
    urgencySafeEdge = Color(0xFF00A16B),
    urgencyDueSoonInk = Color(0xFFA85A00),
    urgencyAtRiskInk = Color(0xFFB0121D),
    surfaceInset = Color(0x09000000),
    surfaceInsetStrong = Color(0x0F000000),
    onsiteInk = Color.Black,
    onsiteSurface = Color.White,
    glassHighlight = Color(0xAAFFFFFF),
    glassBorderLight = Color(0xCCFFFFFF),
    glassBorderDark = Color(0x33A6B4C9),
    glassOverlay = Color(0x18FFFFFF),
)

/**
 * 暗色档。中性/语义/主色直接取教务 `Theme.kt` 的 `DarkColorScheme`（background #0F1218、
 * surface #171B22、surfaceContainer #1D222B、containerHigh #252B35、Highest #303744、
 * onSurface Neutral50、onSurfaceVariant Neutral300、outline Neutral500、outlineVariant Neutral700、
 * primary #88BFFF、primaryContainer #183F68、error #FFB4AB、errorContainer #742A27）。
 * 三态色保持色相、提高明度以在 #1D222B 上过 4.5:1；三块 tint 取 10% 叠在卡面上的不透明合成值。
 */
val ShowCodeDarkPalette = ShowCodePalette(
    isDark = true,
    canvas = Color(0xFF0F1218),
    surfaceCard = Color(0xFF1D222B),
    surfaceSunken = Color(0xFF252B35),
    surfaceDeep = Color(0xFF303744),
    surfaceFloating = Color(0xFF252B35),
    hairline = Color(0xFF29313D),
    hairlineStrong = Color(0xFF344054),
    borderDueSoon = Color(0xFF5A3A16),
    ink = Color(0xFFF8F9FC),
    inkSecondary = Color(0xFFD5DCE7),
    inkMuted = Color(0xFFB3BDCC),
    inkFaint = Color(0xFF667085),
    accent = Color(0xFF0A84FF),
    accentInk = Color(0xFF88BFFF),
    accentTint = Color(0xFF183F68),
    urgencySafe = Color(0xFF3DDC97),
    urgencyDueSoon = Color(0xFFFFB020),
    urgencyAtRisk = Color(0xFFFF6B6B),
    urgencySafeTint = Color(0xFF16332A),
    urgencyDueSoonTint = Color(0xFF3A2C14),
    urgencyAtRiskTint = Color(0xFF3A1D22),
    urgencySafeInk = Color(0xFF6FE3B4),
    urgencySafeEdge = Color(0xFF2FB37B),
    urgencyDueSoonInk = Color(0xFFFFC46B),
    urgencyAtRiskInk = Color(0xFFFF9A9A),
    surfaceInset = Color(0x0AFFFFFF),
    surfaceInsetStrong = Color(0x14FFFFFF),
    onsiteInk = Color(0xFFF8F9FC),
    onsiteSurface = Color(0xFF0F1218),
    glassHighlight = Color(0x80FFFFFF),
    glassBorderLight = Color(0x66FFFFFF),
    glassBorderDark = Color(0x33A6B4C9),
    glassOverlay = Color(0x14FFFFFF),
)

/**
 * 主题层唯一写入口。`isDark` 是快照状态：所有颜色 getter 都读它，改一次全 App 同时重组。
 * 初值由 `MainActivity` 在 `setContent` 之前写入，避免暗色首帧先画一遍亮色再翻。
 */
object ShowCodePaletteState {
    var isDark by mutableStateOf(false)
        private set

    val current: ShowCodePalette
        get() = if (isDark) ShowCodeDarkPalette else ShowCodeLightPalette

    fun applyDark(value: Boolean) {
        if (isDark != value) isDark = value
    }
}
