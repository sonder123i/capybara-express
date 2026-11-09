# 代码评审报告 · 卡皮巴拉快递助手（dev.showcode v0.4.3 / versionCode 8）

> 评审日期：2026-11-09 · 评审方式：静态走查 + 本地工具核验（不是只看文档）
> 覆盖范围：`app/`（Android/Kotlin+Compose，主交付物）、`showcode/`（Vite+React 原型）、`site/`（落地页）
> 已实际执行：`migration-check.py --ci`、`visual-audit.py --ci`、`oxlint`、`tsc -b`，均通过。

---

## 一、总体结论

**工程质量在同类开源 Android 项目里属于上游水平，不需要返工重写。** 分层清晰、纯函数裁决可测、数据迁移梯与安全基线做得比大多数个人项目认真。主要短板集中在三处：**运行时异常边界、少数跨版本兼容分支、以及"能测到却没人跑"的测试**。按下面的清单收敛即可，不需要大改。

| 维度 | 评级 | 一句话 |
|---|---|---|
| 架构 / 分层 | A- | core/data/ui 职责干净，裁决全在纯函数 |
| 可读性 / 文档 | A | 注释密度高且解释"为什么"，README 级文档罕见地完整 |
| 正确性 / 健壮性 | B+ | 业务判据扎实，但写路径缺异常围栏、少一处降级崩溃 |
| 安全 / 隐私 | B+ | 基线正确（无 JS 桥、Keystore 加密、无明文流量），差 FLAG_SECURE 与几处纵深 |
| 性能 | B | 结构合理，但主线程读库/大文件/重组稳定性有可量化改进 |
| 可测试性 | B+ | core 层 500+ 用例很好；data 层靠 python 棘轮、androidTest 从不跑 |
| 发布工程 | B | 棘轮齐、CI 有想法；lint 缺失、wrapper 无校验、无 onDowngrade 回滚 |

---

## 二、值得肯定的地方（这些不用再动）

- **裁决与执行分离**：`core/Ingest.kt` 的 `planIngest` 是纯函数，把"这条消息该不该动库"从 Android 类里剥出来，直接可单测。这是整个仓库最正确的一个决定。
- **数据层**：`ShowCodeDb.kt` 12 级迁移梯每级都有"为什么不能删表重建"的说明；部分唯一索引 `parcel_dedupe / parcel_tracking_dedupe` 的谓词写得很准；`ParcelRepository` 用 `Dispatchers.IO.limitedParallelism(1)` 把写库串行化，`pendingPicks` + 同锁发布解决了"点了已取又被并发 reload 闪回"这类经典竞态。
- **安全基线**：全仓 **0 个 `addJavascriptInterface`**；Cookie 用 Android Keystore AES-256-GCM（随机 IV、解密失败返回 null 而非抛）；`allowBackup=false`；FileProvider 只暴露 `external-files/Download`；快捷方式目的地有 `DeepLinkLadder.knownEntries` 白名单挡越权拉起；`Slog` 分级（debug 才拼串、单号强制打码），没有任何统计/崩溃 SDK。
- **自更新链路**：DownloadManager 下载 + SHA-256 校验（`expected` 为空一律失败）+ 用户手动点安装，三道红线想得很清楚。
- **可测性**：50 个测试文件、500+ `@Test`；两道"棘轮"（视觉审计、库结构迁移）实跑全绿；`oxlint` 0 error、`tsc -b` 通过。
- **UI 工程**：`LazyColumn` 全带 key、预测式返回实现完整、168 个 @Composable 拆分粒度合理。

---

## 三、问题清单（按优先级）

### P0 · 发版前建议修（会导致崩溃 / 跨版本失效 / 数据风险）

1. **写路径没有异常围栏，一次 SQLiteException 会杀进程**
   `ParcelRepository.kt:139` 的 `scope = CoroutineScope(SupervisorJob() + io)` 没有 `CoroutineExceptionHandler`。除 `markPicked`/`undoPicked`（264/300 行有 try/finally）外，其余 10+ 处 `scope.launch { db.xxx(...) }` 直接裸调。磁盘满、库损坏、约束冲突都会以未捕获异常终结进程。
   → 给 scope 加 `CoroutineExceptionHandler`（记 Slog.failure 并降级），或每个写 launch 包 try/catch。

2. **缺 `onDowngrade` → 覆盖安装旧版必崩**
   `ShowCodeDb.kt` 没有 `onDowngrade`。同签名的旧 APK 可覆盖安装，一打开就因 "Can't downgrade database from version 12 to N" 崩溃。App 是自更新分发，回滚是真实场景。
   → 实现 `onDowngrade`（至少明确拒绝并提示不要降级），或在安装层拦截降级。

3. **`POST_NOTIFICATIONS` 判断没有 `SDK_INT < 33` 分支**
   `PickupReminder.kt` 的 `permissionGranted()` 直接 `checkSelfPermission(POST_NOTIFICATIONS)==GRANTED`。该权限 API 33 才引入，`minSdk=26`，在 Android 8–12 上这条判断在部分 ROM 返回 DENIED，导致**巡检提醒永远不响**、设置页常挂一条修不掉的红字。
   → `if (Build.VERSION.SDK_INT < 33) true else check(...)`。

4. **`addParseRule` / `addIdentityCode` 是 suspend 但没切 IO，从 UI 主线程写库**
   `ParcelRepository.kt:826`、`:905`。前者写库后还同步重判最多 50 条未识别正文，全在主线程。
   → 用 `withContext(io) { ... }` 包住（和 `manualAdd:405` 一样）。

5. **三个 Fetcher 用 `catch (t: Throwable)` 吞掉 `CancellationException`**
   `JdFetcher.kt:182`、`PddFetcher.kt:263`、`TbFetcher.kt:359`。用户离开登录页触发的 cancel 会被记成"同步失败"，进而写 `lastTransientFailureAt` 触发 30 分钟退避。
   → catch 里先 `if (t is CancellationException) throw t`，再统一返回 Failure。

### P1 · 中期收敛（安全纵深 / 性能 / 体验）

| # | 问题 | 位置 | 建议 |
|---|---|---|---|
| 1 | 现场模式/身份码屏无 `FLAG_SECURE`，取件码会进系统截图与最近任务缩略图 | `OnsiteScreen.kt`、`IdentityShowScreen.kt` | 进屏 `window.addFlags(FLAG_SECURE)`、离屏清除 |
| 2 | `UpdateInstaller.install()` 自身不校验，校验只在 UI 层调用 | `UpdateInstaller.kt:71`、`SettingsScreen.kt:214/241` | 把 verify 收进 install()，不变式放对层 |
| 3 | `apkUrl` / 商品图 URL 无 host 白名单 | `UpdateChecker.kt:140`、`GoodsThumbnail.kt` | 校验下载域名 ∈ {gitee, github, 各平台 CDN}；否则 Coil 会向任意第三方 CDN 发请求，"完全离线"措辞需对齐 |
| 4 | `TbFetcher` `httpRequest` 默认跟随重定向并携带整条 Cookie | `TbFetcher.kt:419-434` | 手控重定向或校验 host，防 Cookie 外泄 |
| 5 | 安装 intent 未 `setPackage` | `UpdateInstaller.kt:80` | 加 `setPackage("com.android.packageinstaller")` 类收敛 |
| 6 | `accounts.list()` 在 composition 初始化里同步读库 | `ShowCodeRoot.kt:225`（另 1414/1420/1460 同） | 挪进 `LaunchedEffect + Dispatchers.IO`，或走 StateFlow |
| 7 | 数据类缺 `@Immutable/@Stable`，重组稳定性在运行时失效 | `Parcel`/`AppData`/`PointCard` 等 | 加注解（零成本） |
| 8 | 大文件/大参数：`ShowCodeRoot.kt` 1625 行、23 个 `rememberSaveable`、单函数 376 行路由；`HomeScreen` 约 30 个参数 | 见文件 | 拆 `SyncController`/`DemoState`/`PermissionState` 三个 holder，7 个 `xxxReturn` 合并成 1 个 `parent` 栈 |
| 9 | 无 `lint` 配置；Gradle wrapper 无 `distributionSha256Sum`；CI 无 `concurrency`、第三方 action 用 tag 非 SHA | `app/build.gradle.kts`、`gradle-wrapper.properties`、`.github/workflows/ci.yml` | 开 `lintVitalRelease`，补 wrapper 校验与并发取消 |

### P2 · 记录在案（不影响发版）

- `Modifier.settingsCard()` 是空实现被调 10 次；80 个未使用 import；根目录残留 `pelican-bicycle.html`（未跟踪）、多个历史 APK 与 `SHA256SUMS.txt`（已被 gitignore，但工作区应清）。
- 用户自定义正则虽有长度(200)/编译校验，仍存在 ReDoS 风险（跑在串行 io 线程、正文短、上限 50 条，影响可控）——可在 `matchCode` 外层再加 `withTimeout`。
- `collectAsState()` 而非 `collectAsStateWithLifecycle()`（当前 StateFlow 无害，但换 Flow 时需注意）。
- `showcode/`（Vite 原型）与 `site/`（落地页）质量 OK，但被跟踪进 Android 仓库，clone 第一眼不"像 Android 项目"。

---

## 四、架构与可维护性

- **最值得改的是 `ShowCodeRoot.kt`**：它是导航 + 同步 + 演示态 + 权限 + 路由的合体。建议按职责拆 holder，主文件降到 ~150 行。这是长期维护成本最高的一块。
- **`Route` 是 18 成员私有枚举 + 5 处手写 `when`**：加一屏要改 6 个地方，容易漏（演示态深链绕过就是这么漏出来的——0.4.0 已修但说明结构脆弱）。
- **`accounts` 是第二个真相源**（`ShowCodeRoot.kt:225` 自己拉一份，不订阅 repository）：建议纳入 `AppData` 或独立 StateFlow，避免"页面里的账号列表"和"库里"两份数据漂移。
- 正面：`core/` 的领域模型拆分（Parcel / IdentityCode / ParseRule 各成体系而不硬塞进一张表）是成熟的建模判断。

---

## 五、测试评估

- **强项**：core 层（解析、入库裁决、合并、紧急度、QR）覆盖扎实，含"识别率棘轮"和语料审计；`migration-check.py` 用真 SQLite 回放 12 级迁移并带数据断言，比纯 JVM 桩强。
- **缺口（高价值补齐点）**：
  1. `androidTest`（SecretBox Keystore 两条、GLES 探针）**CI 从不执行**——无 `connectedAndroidTest`/managedDevices。唯一能证明"Keystore 真的可用"的用例是死代码。
  2. **Fetcher 零覆盖**（WebView 抓取只能真机，但解析子函数如 `parseOrders`/`TraceParser` 可 JVM 测）。
  3. **`UpdateInstaller.verify()` 零覆盖**——它是自更新链路唯一的信任锚（下载被中间人替换 APK 时靠它挡），必须有单测。
  4. 设备侧迁移只跑过 v7，当前是 v12：**v8→v12 五档无真机证据**。
- 建议：为 DAO/Repository 引入 in-memory SQLite（androidx.sqlite 或 Robolectric）做回归；CI 加一个 managed device 跑 androidTest。

---

## 六、CI / 发布工程

- 好的：`ci.yml` 有"tag ↔ versionName 一致"门、"没配密钥跳过而非报红"、两道棘轮、截图回归 verify。
- 待补：无 `lint`（`lintVitalRelease` 应作为门槛）、PR 不验证 `assembleRelease`、无 `concurrency` 取消旧跑、action 未钉 SHA、wrapper 无校验和、`release` 在无密钥时"成功"会误导（当前用 warning 缓解）。
- **回滚不可行**：DB v12 无 `onDowngrade`，降级安装直接崩（见 P0-2）。

---

## 七、建议执行顺序

1. 修 **P0-1**（异常围栏）→ **P0-3**（权限 SDK 分支）→ **P0-4**（suspend 切 IO）→ **P0-5**（CancellationException）——都是小改、风险低。
2. 修 **P0-2**（onDowngrade）。
3. 补 **UpdateInstaller.verify 单测** + 一个 managed device 跑 androidTest（把 SecretBox 用例救活）。
4. P1 表逐条收敛（先 FLAG_SECURE、校验收进 install、host 白名单）。
5. 结构重构 `ShowCodeRoot`（可排进下个迭代，非阻塞）。

---

## 八、方法与取证

- 阅读量：core/data/ui 关键文件逐行（ShowCodeDb、ParcelRepository、Ingest、ParseRule、MessageGate、三个 Fetcher、UpdateChecker/Installer、SecretBox、AccountStore、Prefs、AccountSyncEngine、QR、主要 UI）。
- 执行的工具：`python3 tools/migration-check.py --ci`（全 OK）、`python3 tools/visual-audit.py --ci`（全 OK）、`npx oxlint`（0 warnings/0 errors）、`tsc -b`（exit 0）。
- 已核对 0.4.0 旧评审中已修复的项：Baseline Profile 采集漏首页/底栏（bef39dd 已修）、Gitee `target_commitish` 分叉（3c5d3c9 已修）、演示态深链绕过（`leaveDemoForDeepLink` 已修）、`InstalledApp` 转屏崩溃（`InstalledAppSaver` 已修）、截图容差与 ShowCodeNavBar 覆盖（已修）。以下 P1 仍成立：主线程写库、异常围栏、CancellationException、onDowngrade、FLAG_SECURE。
