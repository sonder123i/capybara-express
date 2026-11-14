import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi") version "1.77.0"
}

/* 签名材料两条来源：环境变量（CI 用），或仓库外的一个 keystore.properties（本机用）。
   指针写在 gitignore 掉的 local.properties 里，所以仓库里永远不会有口令。
   环境变量优先，这样 CI 不需要伪造一个本机文件。 */
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val keystoreProperties = Properties().apply {
    val pointer = System.getenv("SHOWCODE_KEYSTORE_PROPERTIES")
        ?: localProperties.getProperty("showcode.keystore.props")
    val f = pointer?.let { file(it) }
    if (f != null && f.exists()) f.inputStream().use { load(it) }
}

fun signingProperty(key: String, envVar: String): String? =
    System.getenv(envVar) ?: keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }

android {
    namespace = "dev.showcode"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.showcode"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "0.4.10"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /* Roborazzi 需要 JVM 上的 Android 资源与 Compose 运行时（Robolectric 提供）。
       `unitTests.isIncludeAndroidResources` 必须开：不开的话截图里没有字体、
       没有主题色，测出来的是空白色块。 */
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                /* Roborazzi 的行为由这两个开关决定，**必须显式写**：
                   - record：把当前画面写成基准图；
                   - verify：和基准图逐像素比，有差异就红。
                   不写的话 `verifyRoborazziDebug` 只是「跑一遍测试」，
                   截图有差异也不会失败——那这条回归就形同虚设（实测过）。 */
                /* 默认 **verify**：本地跑单测时也希望「画面变了就红」。
                   要更新基准图时显式传 -Proborazzi.record=true，
                   也就是 `./gradlew :app:testDebugUnitTest -Proborazzi.record=true`。 */
                val recording = (project.findProperty("roborazzi.record") as String?) == "true"
                it.systemProperty("roborazzi.test.record", recording.toString())
                it.systemProperty("roborazzi.test.verify", (!recording).toString())
                /* 基准图放仓库里（`app/src/test/screenshots`）而不是 build/：
                   build/ 会被清掉、也不进版本库，CI 上没有可比的对象。
                   这一条是截图回归能不能成立的关键。 */
                /* ⚠️ 必须是**绝对路径**，而且不能靠 file() 的相对解析：
                   它相对的是模块目录，`file("../app/...")` 会绕回同一个地方。
                   实测落到了 `app/` 根下，基准图就散在工作区里。 */
                it.systemProperty(
                    "roborazzi.output.dir",
                    project.projectDir.resolve("src/test/screenshots").absolutePath,
                )
            }
        }
    }

    signingConfigs {
        create("release") {
            /* 密钥与口令一律不进仓库（仓库是公开的）。两条来源：
               1) CI：设 SHOWCODE_KEYSTORE / SHOWCODE_KEYSTORE_PASSWORD /
                  SHOWCODE_KEY_ALIAS / SHOWCODE_KEY_PASSWORD 四个环境变量；
               2) 本机：在 gitignore 掉的 local.properties 里写一行
                  showcode.keystore.props=<仓库外>\\keystore.properties，
               文件里是 storeFile / storePassword / keyPassword / keyAlias。
               自己签一个：
                 keytool -genkeypair -keystore release.keystore -storetype PKCS12 \
                     -alias showcode -keyalg RSA -keysize 2048 -validity 10000
               两者都没有时 assembleRelease 直接失败，而不是悄悄产出一个装不上的包。 */
            storeFile = signingProperty("storeFile", "SHOWCODE_KEYSTORE")?.let { file(it) }
            storePassword = signingProperty("storePassword", "SHOWCODE_KEYSTORE_PASSWORD")
            keyAlias = signingProperty("keyAlias", "SHOWCODE_KEY_ALIAS")
            keyPassword = signingProperty("keyPassword", "SHOWCODE_KEY_PASSWORD")
        }
    }

    /* 宏基准跑在 benchmark 变体上。`testBuildType` 决定 androidTest 挂在哪个
       buildType 上——不写就只挂在 debug 上，而 debug 包会被宏基准拒绝
       （ERROR: Benchmark Target is Debuggable）。 */

    buildTypes {
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            /* 宏基准要关掉混淆，否则 profiler 拿到的符号对不上。 */
            isMinifyEnabled = false
            /* ⚠️ 必须显式 false：宏基准会拒绝在 debuggable 的包上跑
               （报「ERROR: Benchmark Target is Debuggable」）——
               debuggable 会为了支持调试大幅拖慢执行，测出来的数不代表真实用户。 */
            isDebuggable = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        // 折射引擎（GlassLens*）的调试日志挂在 BuildConfig.DEBUG 上
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    /* 底栏那层玻璃。取件码只用到 backdrop，不引 shapes：shapes 是给 lens() 的圆角变体用的，
       本仓库的折射形状只有 CircleShape（CornerBasedShape 系），见 ui/glass/GlassLensCompat。 */
    implementation("io.github.kyant0:backdrop:2.0.0")
    /* 形状变形：底栏指示胶囊在滑动中从「胶囊」连续变形到「略鼓的胶囊」，
       顶点一一对应，不是两个形状交叉淡入。Google 官方库，Apache 2.0。 */
    implementation("androidx.graphics:graphics-shapes:1.1.0")
    /* 商品缩略图。替掉自写的 ThumbnailLoader——那个只有内存缓存，
       没有磁盘缓存、没有请求去重、没有取消。
       ⚠️ 版本钉在 3.3.0：3.6.x 的 kotlin-stdlib 是 2.4.10，而本工程 Kotlin 是 2.2.10，
       升上去会报「Module was compiled with an incompatible version of Kotlin」。
       要升 Coil 必须先升 Kotlin。 */
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.16.0")
    /* 系统 splash 只负责首帧；启动动画靠 setOnExitAnimationListener 接住图标 bounds。
       版本与只读参考工程一致。 */
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    /* document-start JS 注入（账号同步的 WebView 钩子）从 P1 起转正。 */
    implementation("androidx.webkit:webkit:1.14.0")

    /* 折射引擎的 GL 冒烟探针（androidTest）：EGL/着色器在这台设备上起不起来。 */
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")

    testImplementation("junit:junit:4.13.2")
    /* JVM 单测里的 android.jar 只是空桩（org.json 一调就抛「not mocked」）。
       更新检查要解析 Release JSON，单测必须拿真实现。 */
    testImplementation("org.json:json:20240303")
    /* profileinstaller：让 Baseline Profile 在 Android 12 以下也能装
       （12+ 系统自己装，12 以下要这个库代劳）。 */
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    /* 截图回归：JVM 上跑 Compose 截图，不用模拟器。
       存在的理由：visual-audit 只查得动**代码**（裸字号、裸圆角、透明度），
       查不动**画面**——大字号下底栏被裁那种问题，只有截图能抓到。 */
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.77.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.77.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
