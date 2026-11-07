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
        /* 首页要真的画完：只 startActivity 的话，Compose 的测量/布局/首帧绘制
           都可能落在计时窗口外，那些方法就进不了 profile。 */
        device.wait(Until.hasObject(By.text("待取件")), 5_000)
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
            device.wait(Until.hasObject(By.text("待取件")), 5_000)
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
            device.wait(Until.hasObject(By.text("待取件")), 5_000)
        },
    )
}

/**
 * 把用户最常走的那几屏点一遍。
 *
 * 只走三屏（待取件 → 身份码 → 设置）：底栏每次打开都要画，
 * 三屏覆盖了底栏、列表、设置卡片这三类主要组件。
 * 再往里点（详情、规则、账号）收益递减——那些页面大多数人一天进不了一次，
 * 却会把 profile 撑大，而 profile 是要随 APK 分发的。
 */
private fun MacrobenchmarkScope.walkTheApp() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    listOf("身份码", "设置", "待取件").forEach { label ->
        device.findObject(By.text(label))?.click()
        device.waitForIdle()
        Thread.sleep(400)
    }
}

private const val PACKAGE = "dev.showcode"
