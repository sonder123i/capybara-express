package dev.showcode.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureRoboImage
import dev.showcode.ui.NavBarTab
import dev.showcode.ui.ShowCodeNavBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 截图回归：把**最容易悄悄坏掉**的几个画面钉成基准图。
 *
 * 为什么需要它：`tools/visual-audit.py` 只查得动**代码**（裸字号、裸圆角、
 * 手调透明度），查不动**画面**。这一轮就出过两次只有看图才发现的问题——
 * 大字号下底栏标签被裁、亮色太阳下缘挂一道灰弧。那类问题本该被机器抓住。
 *
 * 为什么是这几个画面而不是全部：截图测试的成本在**维护**，不在运行。
 * 每加一张基准图，以后每次有意的视觉改动都要重录一次。所以只钉三类：
 * 1. **主题两态**（亮/暗）——颜色改错是最常见、也最容易被忽略的回归；
 * 2. **大字号底栏**——刚修过的那类「特定用户读不全」；
 * 3. **亮暗开关**——它自己就是一张画，改动频率最高。
 *
 * 更新基准图：`./gradlew :app:recordRoborazziDebug`
 * 校验：`./gradlew :app:verifyRoborazziDebug`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotRegressionTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * 基准图不是逐像素相等，而是「差异面积占比」小于一个阈值。
     *
     * 为什么必须有容差：基准图是在开发机上录的，CI 跑在另一个 OS 上。
     * 字体光栅化（hinting / subpixel）在两边的抗锯齿边缘差 1–4/255 是常态，
     * 而 Roborazzi 默认 `ThresholdValidator(0f)` 要求 0% 差异——
     * 于是同一份代码在 Windows 上绿、在 Linux 上红。
     * 实测过：三次 CI 全红，差异 519/813780 像素（0.064%）、单通道最大差 4/255。
     *
     * 阈值取 0.2%：实测 CI 那次跨平台抖动是 0.064%（519/813780 像素），
     * 0.2% 是它的三倍，够吞掉字体抗锯齿；
     * 又拦得住「色板改一格」「圆角从 12 变 16」这类真实回归——
     * 实测一个 100×100 的色块改动是 1.2%，会被拦下。
     * 不要再往上调：0.5% 会吞掉 40×40（0.2%）这一档的改动，那已经是肉眼可见的错色了。
     */
    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            resultValidator = ThresholdValidator(0.002f),
        ),
    )

    /**
     * 基准图写到 `app/src/test/screenshots/`。
     *
     * ⚠️ 路径必须是绝对的，而且**不能靠 Gradle 的 `roborazzi.output.dir`**：
     * 那个属性在这里被忽略，文件会散落到模块根目录（实测落在 `app/` 下）。
     * 基准图要进版本库，位置必须是确定的、相对仓库根的。
     */
    private fun shoot(name: String) {
        compose.onRoot().captureRoboImage(
            filePath = baselineFile(name).absolutePath,
            roborazziOptions = options,
        )
    }

    private fun baselineFile(name: String): java.io.File {
        /* 测试的工作目录是模块目录（app/），所以从它往上找仓库根再拼。 */
        var dir = java.io.File(".").absoluteFile
        while (dir.parentFile != null && !java.io.File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return java.io.File(dir, "app/src/test/screenshots/$name.png").also {
            it.parentFile?.mkdirs()
        }
    }

    /**
     * 一张有代表性的卡片：标题、正文、两行说明、一个主行动。
     * 它不测业务，只测「这套色板与字阶在真实组合下是什么样」。
     */
    @Test
    fun homeCardLight() = captureThemeCard(dark = false, name = "home-card-light")

    @Test
    fun homeCardDark() = captureThemeCard(dark = true, name = "home-card-dark")

    /**
     * 亮暗开关的两个静止态。它是最容易被反复微调的一张画，
     * 而「太阳太暗」「月亮外有圈脏光」这类问题都是看图才发现的。
     */
    @Test
    fun themeToggleLight() = captureToggle(dark = false, name = "theme-toggle-light")

    @Test
    fun themeToggleDark() = captureToggle(dark = true, name = "theme-toggle-dark")

    /**
     * 大字号底栏。这一条针对的是真实发生过的缺陷：
     * 字号 1.5×/2× 时轨道高度只按屏幕尺寸算，标签被裁一截、文字溢出压住内容。
     *
     * `qualifiers` 里带 `fontScale` 就能在 Robolectric 上复现系统字号设置。
     *
     * ⚠️ 这一条**必须渲染真的 `ShowCodeNavBar`**。曾经它画的是「一个 Box 里居中
     * 一行 `NavLabel`」，而那条缺陷发生在 `ShowCodeNavBar` 内部的 `tallWithText`
     * 上——基准图守着一个不含缺陷代码的画面，等于没守。
     * 三枚标签都要出现在图里，缺一枚就说明轨道高度算错了。
     */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2.0f)
    fun navBarAtDoubleFontScale() {
        compose.setContent {
            ShowCodeTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .size(width = 411.dp, height = 240.dp)
                        .background(Canvas),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    ShowCodeNavBar(
                        current = NavBarTab.HOME,
                        onHome = {},
                        onIdentity = {},
                        onSettings = {},
                    )
                }
            }
        }
        shoot("nav-font-2x")
    }

    private fun captureThemeCard(dark: Boolean, name: String) {
        compose.setContent {
            ShowCodeTheme(darkTheme = dark) {
                Column(
                    modifier = Modifier
                        .size(width = 411.dp, height = 220.dp)
                        .background(Canvas)
                        .padding(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceCard, ShowCodeShapes.Md)
                            .padding(16.dp),
                    ) {
                        Text(text = "在途 2 件", style = ShowCodeText.Section, color = Ink)
                        Box(Modifier.height(8.dp))
                        Text(
                            text = "FOUR ONE水洗牛仔托特包多口袋百搭大容量日常休闲通勤单肩斜挎包",
                            style = ShowCodeText.Body,
                            color = Ink,
                        )
                        Box(Modifier.height(4.dp))
                        Text(
                            text = "极兔速递 · JT5544439499351",
                            style = ShowCodeText.Caption,
                            color = InkMuted,
                        )
                        Box(Modifier.height(4.dp))
                        Text(
                            text = "已发货 · 预计后天送达",
                            style = ShowCodeText.Caption,
                            color = UrgencySafeInk,
                        )
                    }
                }
            }
        }
        shoot(name)
    }

    private fun captureToggle(dark: Boolean, name: String) {
        compose.setContent {
            ShowCodeTheme(darkTheme = dark) {
                Box(
                    modifier = Modifier
                        .size(width = 411.dp, height = 120.dp)
                        .background(Canvas),
                    contentAlignment = Alignment.Center,
                ) {
                    ThemeToggle(dark = dark, onToggle = {})
                }
            }
        }
        shoot(name)
    }
}
