package dev.showcode.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 的生成与对比。
 *
 * 跑法（必须走 benchmark 变体，debug 包会被宏基准拒绝）：
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkAndroidTest
 * ```
 *
 * ⚠️ 这个模块的 `targetProjectPath = ":app"` 是硬要求。少了它，生成的 manifest 里
 * `targetPackage` 会指向测试模块**自己**，测试于是跑在测试进程里、根本没驱动 App。
 * 症状很隐蔽：测试全绿、BUILD SUCCESSFUL，但生成的 profile 里一条 `dev/showcode/`
 * 的规则都没有（实测踩过）。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    /**
     * 生成 profile 用 [BaselineProfileRule]，不是 [MacrobenchmarkRule]。
     *
     * 区别是**产物**：MacrobenchmarkRule 只测量、不写 profile；
     * BaselineProfileRule 会把 ART 的热点写成 `baseline-prof.txt`
     * 并落到设备的 `additional_output` 里，由 Gradle 拉回本机。
     * 第一版用错了 rule，结果是「测试全绿、有耗时数据、但没有任何 profile 文件」。
     */
    @get:Rule
    val profileRule = BaselineProfileRule()

    @Test
    fun generate() = profileRule.collect(
        packageName = PACKAGE,
        /* 迭代 5 次：一次的热度不够稳，profile 会漏掉只在第二轮才走到的路径
           （比如图片解码、列表复用）。 */
        maxIterations = 5,
    ) {
        pressHome()
        startActivityAndWait()

        /* ⚠️ 先过引导页。
         *
         * 这一条是上一版最大的漏洞：全新安装的机器第一次启动落在**引导页**，
         * 而引导页上没有「待取件」这三个字——所以下面那个 wait 必然超时，
         * 而它的返回值被丢弃了，于是采集在引导页上"成功"走完。
         * 产物里 OnboardingScreenKt 有 43 条规则，HomeScreenKt 和
         * ShowCodeNavBarKt 各 **0 条**——底栏和首页从来没被组合过。
         * 实测确认：DrawBackdropNode（只在底栏）0 条，而 LayerBackdropNode
         * （在根布局，任何路由都会组合）有 10 条，这一对是决定性证据。
         *
         * 后果：623ms → 326ms 只对全新安装成立，而存量用户升级后首屏是首页，
         * 拿不到那部分收益。
         */
        skipOnboardingIfShown()

        /* 首页要真的画完：只 startActivity 的话，Compose 的测量/布局/首帧绘制
           都可能落在计时窗口外，那些方法就进不了 profile。
           `wait` 的返回值必须检查——它返回 false 就说明首页根本没出来，
           这时候继续走等于采一份错的 profile，而且是静默的。 */
        check(device.wait(Until.hasObject(By.text("待取件")), 10_000)) {
            "首页 10 秒内没出现。采集中止——宁可没有 profile，也不要一份采错屏的。"
        }
        walkTheApp()
    }

    /* 下面两条用 MacrobenchmarkRule：它们只测耗时，不产生 profile。 */
    @get:Rule
    val rule = MacrobenchmarkRule()

    /**
     * 有 profile 时的冷启动。
     *
     * 注意这一条只是**记录**，不做断言：模拟器上的绝对值没有意义
     * （宿主机负载、暖机状态都会漂），要看的是它和下面那条的**差**。
     */
    @Test
    fun startupWithProfile() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(),
        measureBlock = {
            pressHome()
            startActivityAndWait()
            /* 这一条的时长才是被测的东西，所以 wait 必须**成功**：
               超时了说明量的是引导页，不是首页。 */
            check(device.wait(Until.hasObject(By.text("待取件")), 10_000)) {
                "首页 10 秒内没出现，这次测量量的不是首页。"
            }
        },
    )

    /** 没有 profile 时的冷启动。基线。 */
    @Test
    fun startupWithoutProfile() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        /* 没有 profile 时用 None：`CompilationMode.Partial` 必须指定
           baselineProfileMode 或 warmupIterations，而 Disable 不合法。 */
        compilationMode = CompilationMode.None(),
        measureBlock = {
            pressHome()
            startActivityAndWait()
            /* 这一条的时长才是被测的东西，所以 wait 必须**成功**：
               超时了说明量的是引导页，不是首页。 */
            check(device.wait(Until.hasObject(By.text("待取件")), 10_000)) {
                "首页 10 秒内没出现，这次测量量的不是首页。"
            }
        },
    )
}

/**
 * 引导页如果出现了就跳过它。
 *
 * 全新安装第一次启动落在欢迎页，那一屏没有「待取件」；已经过了引导的机器
 * 直接进首页，这里什么都不做。两种都要能跑——所以是「如果出现就点」，
 * 不是「必须点」。
 *
 * 文案取自 `strings.xml` 的 `ob_skip`（「跳过，直接进入」）。
 * 换文案不会让这里静默失效：点不到的话下面那个 `check` 会红。
 */
private fun MacrobenchmarkScope.skipOnboardingIfShown() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    /* 与 walkTheApp 同一个道理：查一次就点会撞 StaleObjectException，
       所以每轮重新查。引导页的淡入动画很容易让对象失效。 */
    for (attempt in 0 until 10) {
        val skip = device.findObject(By.text("跳过，直接进入"))
        if (skip != null) {
            try {
                skip.click()
                device.waitForIdle()
                Thread.sleep(800)
                return
            } catch (stale: androidx.test.uiautomator.StaleObjectException) {
                // 下一轮重新查
            }
        }
        Thread.sleep(300)
    }
}

/**
 * 把用户最常走的那几屏点一遍。
 *
 * 只走三屏（待取件 → 身份码 → 设置）：底栏每次打开都要画，
 * 三屏覆盖了底栏、列表、设置卡片这三类主要组件。
 * 再往里点（详情、规则、账号）收益递减——那些页面大多数人一天进不了一次，
 * 却会把 profile 撑大，而 profile 是要随 APK 分发的。
 *
 * ⚠️ 点不到就报错，不再用 `?.click()` 静默跳过。
 * 上一版三个标签全都点不到（因为还在引导页上），但每一步都是 `?.`，
 * 于是采集"成功"、profile 是空的。一个静默失败的热点采集器比没有更糟：
 * 它产出的文件看起来是合法的。
 */
private fun MacrobenchmarkScope.walkTheApp() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    listOf("身份码", "设置", "待取件").forEach { label ->
        /* ⚠️ 找到对象和点它之间，Compose 的动画/重组会让那个 UiObject2 失效
           （StaleObjectException）。所以要**每轮重新查**，不能查一次再重试点击。
           实测踩过：`wait(Until.findObject(...))` 之后 `waitForIdle()` 再点，
           直接抛 StaleObjectException。 */
        var clicked = false
        for (attempt in 0 until 10) {
            val tab = device.findObject(By.text(label))
            if (tab != null) {
                try {
                    tab.click()
                    clicked = true
                    break
                } catch (stale: androidx.test.uiautomator.StaleObjectException) {
                    // 树变了，下一轮重新查。
                }
            }
            Thread.sleep(300)
        }
        check(clicked) { "底栏标签「$label」点了 10 次都没成功。首页没画出来，或标签文案改了。" }
        device.waitForIdle()
        Thread.sleep(400)
    }
}

private const val PACKAGE = "dev.showcode"
