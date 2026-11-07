package dev.showcode.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * 颜色统一收口到 `Palette.kt`。这里保留**同名顶层 val**，但它们现在是读
 * `ShowCodePaletteState` 的 getter——这样全 App 600 多处既有引用不用改，
 * 明/暗切换会通过 Compose 快照自动重组。
 *
 * 只有两类值保持常量：桌面组件 RemoteViews 只能吃 Int 的三态色，以及纯装饰的
 * 玻璃高光（它们没有暗色变体，仍是白色叠层）。
 */

const val UrgencySafeInt = 0xFF00B578.toInt()
const val UrgencyDueSoonInt = 0xFFFF8F1F.toInt()
const val UrgencyAtRiskInt = 0xFFFF3141.toInt()

const val AccentInt = 0xFF0A84FF.toInt()
const val AccentInkInt = 0xFF0057D9.toInt()

val UrgencySafe: Color get() = ShowCodePaletteState.current.urgencySafe
val UrgencyDueSoon: Color get() = ShowCodePaletteState.current.urgencyDueSoon
val UrgencyAtRisk: Color get() = ShowCodePaletteState.current.urgencyAtRisk
val UrgencySafeTint: Color get() = ShowCodePaletteState.current.urgencySafeTint
val UrgencyDueSoonTint: Color get() = ShowCodePaletteState.current.urgencyDueSoonTint
val UrgencyAtRiskTint: Color get() = ShowCodePaletteState.current.urgencyAtRiskTint
val BorderDueSoon: Color get() = ShowCodePaletteState.current.borderDueSoon

/** 压在各自 tint 上的副标题色：同色相、足够深，不是半透明。 */
val UrgencySafeInk: Color get() = ShowCodePaletteState.current.urgencySafeInk
val UrgencyDueSoonInk: Color get() = ShowCodePaletteState.current.urgencyDueSoonInk
val UrgencyAtRiskInk: Color get() = ShowCodePaletteState.current.urgencyAtRiskInk

val Accent: Color get() = ShowCodePaletteState.current.accent
val AccentInk: Color get() = ShowCodePaletteState.current.accentInk
val AccentTint: Color get() = ShowCodePaletteState.current.accentTint

/** 现场模式的黑白两端。暗色下整体反相，仍是同一屏的「码 vs 底」最大对比契约。 */
val OnsiteInk: Color get() = ShowCodePaletteState.current.onsiteInk
val OnsiteSurface: Color get() = ShowCodePaletteState.current.onsiteSurface

val Canvas: Color get() = ShowCodePaletteState.current.canvas
val SurfaceCard: Color get() = ShowCodePaletteState.current.surfaceCard
val SurfaceSunken: Color get() = ShowCodePaletteState.current.surfaceSunken
val SurfaceDeep: Color get() = ShowCodePaletteState.current.surfaceDeep
val SurfaceFloating: Color get() = ShowCodePaletteState.current.surfaceFloating

val Hairline: Color get() = ShowCodePaletteState.current.hairline
val HairlineStrong: Color get() = ShowCodePaletteState.current.hairlineStrong

val Ink: Color get() = ShowCodePaletteState.current.ink
val InkSecondary: Color get() = ShowCodePaletteState.current.inkSecondary
val InkMuted: Color get() = ShowCodePaletteState.current.inkMuted
val InkFaint: Color get() = ShowCodePaletteState.current.inkFaint

/** 示例水印：只在「这不是真数据」这一处保留 alpha。 */
val InkWatermark: Color get() = Ink.copy(alpha = 0.11f)

/** 禁用态文字：次要墨压到 35%，给不可点的步进按钮。 */
val InkDisabled: Color get() = InkMuted.copy(alpha = 0.35f)

/** 现场专用极淡遮罩：亮色是黑上叠黑，暗色换成白上叠白。 */
val SurfaceInset: Color get() = ShowCodePaletteState.current.surfaceInset
val SurfaceInsetStrong: Color get() = ShowCodePaletteState.current.surfaceInsetStrong

val GlassHighlight: Color get() = ShowCodePaletteState.current.glassHighlight

/** 玻璃外描边的墨色：亮色档叠黑、暗色档叠白。纯装饰，没有主题状态。 */
val GlassContourLight: Color = Color.Black
val GlassContourDark: Color = Color.White
val GlassBorderLight: Color get() = ShowCodePaletteState.current.glassBorderLight
val GlassBorderDark: Color get() = ShowCodePaletteState.current.glassBorderDark
val GlassOverlay: Color get() = ShowCodePaletteState.current.glassOverlay
