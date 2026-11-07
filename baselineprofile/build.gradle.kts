/*
 * Baseline Profile 的生成模块。
 *
 * 它**不是**产品代码：只在需要重录 profile 时跑，产物是
 * `app/src/main/baseline-prof.txt`（一段 ART 预编译提示）。
 *
 * 为什么必须是独立的 `com.android.test` 模块而不是塞进 app 的 androidTest：
 * 宏基准要「自己插桩自己」——它得控制目标 App 的安装与启动，而普通
 * instrumentation 是跑在被测 App 进程里的，做不到。AGP 对这件事的判据是
 * **测试 APK 是否独立安装**，只有 test 模块满足。
 *
 * ⚠️ `targetProjectPath` 必须写。不写的话生成的 manifest 里 targetPackage
 * 会指向测试模块**自己**，测试跑在测试进程里、根本没驱动 App——
 * 症状很隐蔽：测试全绿、BUILD SUCCESSFUL，但 profile 里一条 App 规则都没有。
 */
plugins {
    id("com.android.test")
}

android {
    namespace = "dev.showcode.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    /* 必须和 app 的 benchmark 变体对齐：debug 包会被宏基准拒绝
       （ERROR: Benchmark Target is Debuggable）。 */
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release", "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
}
