package dev.showcode.ui.glass

import androidx.compose.ui.graphics.Color

/**
 * 玻璃材质表。唯一权威源是教务 `ui/system/GlassMaterial.kt` 的 `GlassMaterials` 与 `GlassRecipe`
 * （.design-qa/DESIGN.md 里那张按**角色**而不是按组件分档的表）。
 *
 * 这里只搬本仓库真的有面的那些档。教务的角色表另有 Control / Modal / Interactive，
 * 等有面了再搬——留一个没人消费的 role，和「M3 给了 5 个容器 role、代码消费 0 个」是同一种毛病。
 *
 * 折射与色散在批 6 之前只由 33+ 的库 `lens()` 出；教务的 Navigation 轨道本来也是
 * 「blur 10 / 无色散 / 不折射」，所以轨道这一层两案完全一致。
 */
data class GlassRecipe(
    /**
     * blur 半径，**dp**。库的 `blur()` 收裸 Float，单位是 **px**——字节码里它直通
     * `BlurEffect(radiusX, radiusY)` → `RenderEffect.createBlurEffect`，没有换算。
     * 调用点必须 `.toPx()`。上一版把裸 10f 直接传进去（≈4dp），这是一处实打实的 bug。
     */
    val blurDp: Float,
    val surfaceAlpha: Float,
    val borderAlpha: Float,
)

object GlassMaterials {

    /**
     * 教务那张表的 Navigation 行：blur 10 / 遮罩 0.16 / 描边 0.14。
     * 读它的一个要点：`surfaceAlpha` 是**遮罩** alpha，不是「玻璃有多白」——可读性来自
     * blur 把背后的东西雾化均匀，掩码只是把整体亮度拉回一点。
     */
    private val Navigation = GlassRecipe(
        blurDp = 10f,
        surfaceAlpha = 0.16f,
        borderAlpha = 0.14f,
    )

    /**
     * 高对比度文字下的改写（教务那套：遮罩 +0.20 上限 0.90、描边 +0.18 上限 0.60）。
     * 这条轴对本仓库比对教务更实在：底栏压着的是一条会滚动的取件码列表。
     */
    fun navigation(highTextContrast: Boolean): GlassRecipe =
        if (highTextContrast) {
            Navigation.copy(
                surfaceAlpha = (Navigation.surfaceAlpha + 0.20f).coerceAtMost(0.90f),
                borderAlpha = (Navigation.borderAlpha + 0.18f).coerceAtMost(0.60f),
            )
        } else {
            Navigation
        }

    /* ── 底栏：逐值照抄教务 `CapsuleNavigationBar.kt:590-1065` + `GlassRecipe` ─────
       本机 API35，走 `hasLensLook` 的 AGSL 路径，所以取的是「有折射外观」那一套真值：
       轨道 blur 10dp + Navigation lens 8/12dp + 白 0.28（暗色 = surface #171B22），
       高光 0.16 / 投影 0.08；选中胶囊白 0.34（暗 0.20，按下 0.14）、高光 0.12+0.35p、
       投影 0.10+0.15p、内阴影 4dp·p / 0.5p；指示器 lens 12/18dp，静止为 0。 */
    const val NavTrackSurfaceAlphaLight = 0.28f

    /**
     * 系统「高对比度文字」下的轨道表面：亮档把白遮罩从 0.28 提到 0.62（仍然透、仍看得见
     * blur，玻璃质感不用拆），暗档把底再压深一档——暗色下轨道本来就不透明，能提的只有
     * 「底更暗 → 浅色字更亮」这一条。只改表面，不动 blur/lens/高光/投影。
     */
    const val NavTrackSurfaceAlphaLightHighContrast = 0.62f
    fun navTrackSurfaceColor(dark: Boolean, highTextContrast: Boolean = false): Color = when {
        !highTextContrast -> if (dark) Color(0xFF171B22) else Color.White.copy(alpha = NavTrackSurfaceAlphaLight)
        dark -> Color(0xFF0B0E13)
        else -> Color.White.copy(alpha = NavTrackSurfaceAlphaLightHighContrast)
    }

    const val NavTrackBlurDp = 10f
    const val NavTrackHighlightAlpha = 0.16f
    const val NavTrackShadowAlpha = 0.08f
    const val NavTrackLensHeightDp = 8f
    const val NavTrackLensAmountDp = 12f

    const val NavCapsuleFillAlphaLight = 0.34f
    const val NavCapsuleFillAlphaDark = 0.20f
    const val NavCapsuleFillPressAlpha = 0.14f
    const val NavCapsuleHighlightAlpha = 0.12f
    const val NavCapsuleHighlightPressAlpha = 0.35f
    const val NavCapsuleShadowAlpha = 0.10f
    const val NavCapsuleShadowPressAlpha = 0.15f
    const val NavCapsuleLensHeightDp = 12f
    const val NavCapsuleLensAmountDp = 18f
    const val NavCapsuleInnerShadowRadiusDp = 4f
    const val NavCapsuleInnerShadowAlpha = 0.5f

    /* 边缘光常量仍给**收起胶囊**用（它没有 backdrop 可采样，靠 glassRim 立边）。
       教务 `GlassMaterial.kt:378-387`。 */
    const val GlassRimAlphaLight = 0.30f
    const val GlassRimAlphaDark = 0.34f
    const val GlassContourAlphaLight = 0.20f
    const val GlassContourAlphaDark = 0.18f
    const val GlassRimPressBoost = 0.45f
    fun navCapsuleSheenAlpha(dark: Boolean): Float = if (dark) 0.14f else 0.32f

    /** 收起形态的近不透明胶囊（没有 backdrop 时的落点）。 */
    fun navCapsuleColor(dark: Boolean): Color =
        if (dark) Color(0xD92A303A) else Color(0xD1FFFFFF)

    /** 不支持真模糊时的实底回退（不是死灰：亮 #FAFAFE / 暗 #191E26）。 */
    fun navFallbackTrackColor(dark: Boolean): Color =
        if (dark) Color(0xFF191E26) else Color(0xFFFAFAFE)

    /** 无折射外观档的轨道（教务 `GlassRecipe.NavLegacy*`，cba2a09）：blur 8dp + FAFAFA @ 0.15，无高光无投影。 */
    const val NavLegacyTrackBlurDp = 8f
    const val NavLegacyTrackSurface = 0xFFFAFAFA
    const val NavLegacyTrackSurfaceAlpha = 0.15f

    /**
     * 选中胶囊的表面。⚠️ **修一处上一版引错的出处**：旧注释引的
     * `SelectedCapsuleSolidAlphaLight = 0.94` 在教务**零消费**——全仓 grep 只有定义，
     * 那是分段控件的常量（注释写「底栏与 segmented 共用」，但底栏根本没接）。
     * 教务底栏实际走的是 `NavSelectedSolid*` 这一组：
     * 有折射外观档白 **0.34**（按下降到 0.14 露出折射）、无折射档 **Black × 0.1**（按下淡出）。
     * 本仓库对号入座：33+ 的 lens 档走前者，其余走后者。
     */
    const val NavSelectedSolidAlpha = 0.34f
    const val NavSelectedGlassAlpha = 0.14f
    const val NavLegacyIndicatorAlpha = 0.1f
    const val NavLegacyIndicatorPressAlpha = 0.03f

    /** 指示胶囊的投影（教务 Shadow 0.10 + 按压 0.15）。 */
    const val NavIndicatorShadowAlpha = 0.10f

    /**
     * 指示胶囊的按压胀度：教务 `SelectionIndicatorPressedScale = 78/56 ≈ 1.39`。
     * 按压方向是**胀**不是缩——这是家族手感的一半，另一半是滑行中的速度拉伸。
     */
    const val NavPressedScale = 78f / 56f

    /**
     * 按压折射的光学档（教务角色表 `Interactive` 行：折射 12-18dp、色散 on）。
     * 上一版用的是库基线 `lens(10dp·p, 14dp·p)`——小一号，球里「没什么可揉」。
     * 静止地板仍是 0（`NavIndicatorRefractionFloor`）：静止折射会把底图色斑沿胶囊
     * 边缘挤出彩边，被指出过三次。
     */
    const val NavIndicatorLensHeightDp = 12f
    const val NavIndicatorLensAmountDp = 18f

    /* ── 暗色档（教务 `GlassRecipe.*Dark`） ─────────────────────────
       参考工程在暗色下把选中胶囊白遮罩从 0.34 降到 0.20，无折射档的轨道从
       FAFAFA@0.15 换成 White@0.18；指示器的「黑遮罩」在暗色下必须换成白色，
       否则黑底叠黑等于没有选中态。 */
    fun navSelectedSolidAlpha(dark: Boolean): Float = if (dark) 0.20f else NavSelectedSolidAlpha

    fun navSelectedGlassAlpha(dark: Boolean): Float = if (dark) 0.12f else NavSelectedGlassAlpha

    fun navLegacyTrackColor(dark: Boolean): Color =
        if (dark) Color.White else Color(NavLegacyTrackSurface)

    fun navLegacyTrackAlpha(dark: Boolean): Float = if (dark) 0.18f else NavLegacyTrackSurfaceAlpha

    fun navLegacyIndicatorColor(dark: Boolean): Color = if (dark) Color.White else Color.Black

    fun navLegacyIndicatorAlpha(dark: Boolean): Float = if (dark) 0.20f else NavLegacyIndicatorAlpha

    fun navLegacyIndicatorPressAlpha(dark: Boolean): Float =
        if (dark) 0.08f else NavLegacyIndicatorPressAlpha
}
