# Baseline Profile

冷启动快 48%。这不是估算，是宏基准在同一台设备、同一份代码上量出来的：

| | timeToInitialDisplay（中位） |
|---|---|
| 无 profile | 623.2 ms |
| 有 profile | **325.8 ms** |

（`emulator-5554` / Android 15 / x86_64，5 次冷启动取中位。绝对值在别的设备上会不同，
但**比例**是稳的——它省掉的是「解释执行 → JIT 预热」那一段，而那段在每台机器上都占大头。）

## 它是什么

ART 默认用「先解释、热了再编译」的方式跑代码，所以每次冷启动都要重新预热一遍。
Baseline Profile 是一份**热点方法清单**（`app/src/main/baseline-prof.txt`），
系统在**安装时**就按它把那些方法编译好，App 一启动就是编译过的状态。

清单里现在有 13032 行，其中：
- **927 行**是本 App 自己的方法（`dev/showcode/**`）；
- 9154 行是 Compose / Material3 等库的方法（库自己也带 profile，这里合并了）。

## 怎么重录

改了启动路径、加了新的首屏组件之后要重录，否则新代码不在清单里，享受不到预编译。

```bash
# 1. 起一台模拟器或接一台真机（API 28+）
adb devices

# 2. 跑宏基准，采集 profile
./gradlew :baselineprofile:connectedBenchmarkAndroidTest

# 3. 把产物拷到 app 模块
cp "baselineprofile/build/outputs/connected_android_test_additional_output/benchmark/connected/<设备>/BaselineProfileGenerator_generate-baseline-prof.txt" \
   app/src/main/baseline-prof.txt

# 4. 确认里面有 App 的规则（不是 0 条）
grep -c "dev/showcode" app/src/main/baseline-prof.txt

# 5. 重新打包，确认 profile 进包了
./gradlew :app:assembleBenchmark
unzip -l app/build/outputs/apk/benchmark/app-benchmark.apk | grep baseline.prof
```

## 怎么验有没有用

宏基准里带了两条对照测试，跑一次就能看到差：

```bash
./gradlew :baselineprofile:connectedBenchmarkAndroidTest
# 然后看
cat "baselineprofile/build/outputs/connected_android_test_additional_output/benchmark/connected/<设备>/additionaltestoutput.benchmark.message_*startupWithProfile.txt"
cat "...*startupWithoutProfile.txt"
```

⚠️ **不要用 `adb shell am start -W` 来判断**。在模拟器上它的噪声比效果还大
（实测同一份包能在 236~696ms 之间跳），因为它把宿主机负载、暖机状态全算进去了。
宏基准会先 `pressHome`、走 COLD 启动模式、多次取中位，这才是能信的数。

## 踩过的坑

这些都是实测撞出来的，改配置前先看一遍：

**1. `MacrobenchmarkRule` 不产生 profile**
第一版用它，结果「测试全绿、有耗时数据、但没有任何 profile 文件」。
产生 profile 的是 `BaselineProfileRule.collect {}`，两者不能混。

**2. `targetProjectPath` 在 AGP 9.2.1 上不生效**
生成的 manifest 里 `targetPackage` 指向测试模块**自己**
（`dev.showcode.baselineprofile`），测试于是跑在测试进程里、根本没驱动 App。
症状极隐蔽：测试全绿、BUILD SUCCESSFUL，但 profile 里一条 App 规则都没有。
现在的做法是**先手动装一份 benchmark APK**，再跑测试模块：

```bash
./gradlew :app:assembleBenchmark
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
./gradlew :baselineprofile:connectedBenchmarkAndroidTest
```

**3. debug 包会被宏基准直接拒绝**
报 `ERROR: Benchmark Target is Debuggable`。所以 app 有一个 `benchmark` 变体
（`isDebuggable = false`、`isMinifyEnabled = false`），测试模块也有同名的变体来对齐。

**4. 删了 `baseline-prof.txt` 不等于 APK 里没有 profile**
库（Compose、Material3）自带 profile，Gradle 会把它们合并进包。
所以「删掉 App profile」的对照实验里，APK 仍然带着 11941 字节的库 profile——
这是为什么那个实验的差距看起来不明显。要做干净对照，只能看宏基准自己跑的两条测试。

**5. `CompilationMode.Partial()` 不传参会抛异常**
`IllegalArgumentException: Must set baselineProfileMode != Ignore, or warmup iterations > 0`。
没有 profile 的那条对照用 `CompilationMode.None()`。

## 本机跑（CI 里不跑）

这些是**本机命令**，改启动路径时手动跑一次。

为什么不在 CI 里跑：`connectedBenchmarkAndroidTest` 要一台真机或模拟器，
跑一次 3-4 分钟（5 次冷启动 × 3 条测试），而 CI 的 runner 上现起模拟器要再等
2-3 分钟、还得开 KVM，且没有 GPU（GL 探针会因 swiftshader 假红）。
本地已经有常驻模拟器，没必要为它加一个每天都要付时间的 job。

### 本机模拟器上的一条龙

```bash
# 0. 确认模拟器在
adb devices

# 1. 装 app 的 benchmark 变体（debuggable=false、未混淆）
./gradlew :app:assembleBenchmark
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk

# 2. 采集 profile（3-4 分钟）
./gradlew :baselineprofile:connectedBenchmarkAndroidTest

# 3. 拷回仓库
cp "baselineprofile/build/outputs/connected_android_test_additional_output/benchmark/connected/<设备>/BaselineProfileGenerator_generate-baseline-prof.txt"    app/src/main/baseline-prof.txt

# 4. 确认 App 规则不是 0 条（0 条 = 测试没驱动到 App，见上面坑 2）
grep -c "dev/showcode" app/src/main/baseline-prof.txt
```

### 顺带能跑的两条设备测试

```bash
./gradlew :app:connectedDebugAndroidTest
```

它跑的是 GL 折射探针（EGL + 着色器在这台设备上起不起来）和 Keystore
（JVM 里没有 AndroidKeyStore，只能在设备上验）。这两条不需要 benchmark 变体，
用普通的 debug 包就行。

`baseline-prof.txt` 本身进版本库——它是产物，但也是「这次发布用的是哪份清单」的记录。
