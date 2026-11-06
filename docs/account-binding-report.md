# 平台账号自动收录 · 实施汇报

> 日期：2026-11-03
> 环境：MuMu 模拟器 `emulator-5554`，Android 15（API 35），WebView 110.0.5481.154.1
> 真机：未接入，所有结论仅代表模拟器环境

---

## 1. 阶段结果

| 阶段 | 结果 | 证据 |
|---|---|---|
| P0 可行性 | **GO** | PDD 登录、document-start 捕获、30 条订单、offset 翻页 10→30、`goods_express` 运单号 + 1250 字轨迹 |
| P1 存储迁移 | **完成** | DB v8、`bound_account`、Keystore AES-GCM、索引重建；`migration-check` 全绿 |
| P2 登录与账号页 | **完成（PDD 已验证）** | 设置入口、三张平台卡、官方登录页、删除/启停；PDD 登录后账号列表显示「已绑定」 |
| P3 拼多多端到端 | **完成（模拟器验证）** | 真实 4 单 → 3 条轨迹 → 1 件入首页；`76416` + 「后五位」；现场「出示单号后五位」 |
| P4 淘宝 | **完成（模拟器验证）** | mtop 链路通（12 单）、SSR 详情通（含运单号）；2 件无取件码的到站件按新规则入首页，可进详情、现场正确降级 |
| P5 京东 | **部分验证** | 绑定成立；拉取返回 0 条可入件；库内 4 件未被误清；UI 有「这次没有新的到站件」；真实订单解析未验 |
| P6 加固 | **部分完成** | 凭证 BROKEN 降级已设备验证；迁移路径模拟；真机 v7→v8 未验 |

---

## 2. P3 逐条验收

### 2.1 首页账号件

- 抓取：`orders=4`，其中 3 条拿到详情（运单号 `465700000020382` / `YT0000000010257` / `JT0000000089980`），1 条详情超时。
- 入库：1 件 `source=ACCOUNT`、`stage=AT_STATION`、`code_kind=SUFFIX`、`picked=0`、`tracking_no=465700000076416`、`account_id=pdd`。
- 首页：出现「菜鸟驿站示例大学天…」卡片，`1 件`，`76416` + 「后五位」。

### 2.2 现场模式

- 屏上显示：
  - 承运商：韵达快递
  - 「出示单号后五位」
  - `76416`（纯黑、等宽、字号防溢出不变）
- `76416` 来自轨迹里的「单号后五位 76416」，优先于运单号末五位；这符合规则。

### 2.3 后五位规则

- `465700000020382`.takeLast(5) = `20382`（推导已验）。
- 有正文数字时优先用正文（本例 `76416`）；没有数字时才回退 `trackingNo.takeLast(5)`。
- 该规则只影响 `code` 和标签，不影响 P0 判定。

### 2.4 已取不复活 / 签收不误标

- **签收不误标**：3 条已签收/待评价订单没有被自动标成 `picked`；DB 里没有 `picked=1` 的账号件。
  轨迹没有「本人取件 / 凭码取件」时，不自动标已取。
- **已取不复活**：设备完整回路已跑。
  现场点 `76416` 的「已取」→ `picked=1`、`picked_at` 有值；
  再同步后仍是 4 件，`picked` 保持 1，没有复活、没有重复插入。
- 保守取舍：不明确的「已签收」在插入时会先当到站件（`stage=AT_STATION`），
  让用户自己点已取；宁可多一张卡，不吞掉一件真待取的件。

### 2.5 遗留的点名问题

- 第一次入库的点名是 `菜鸟驿站示例大学示例校区菜鸟驿站驿站`（轨迹里品牌名与后缀叠在一起）。
- 已加 `cleanPointName` 规则并补单测（9/9 绿）：出现第二次「菜鸟驿站」时去掉、折叠重复「驿站」。
- 该修正版**未在设备上重新同步**，所以库里那条旧点名还没被刷新；下次同步会按 `source=ACCOUNT` 更新。

---

## 3. 改动文件

### 核心（纯 Kotlin）
- `core/Model.kt`：`Stage` / `CodeKind` / `Source.ACCOUNT` / `Parcel` 新字段
- `core/Grouping.kt`：首页放 `AT_STATION + 有点 +（有码 / 后五位 / 有运单号）`
- `core/Search.kt`：检索加入运单号 / 平台订单号
- `core/account/PlatformSpec.kt`：三家 loginUrl / cookieHost / Cookie 判据
- `core/account/Account.kt`：`BoundAccount` / `AccountState`
- `core/account/FetchedParcel.kt`
- `core/account/TraceParser.kt`：状态 / 点 / 码 / 后五位 / 格口 / 明确取件
- `core/account/AccountMerge.kt`：运单号优先、码冲突保留原码、已取不复活、状态不降级；
  有运单号即收录，空点名归 `UNLOCATED_POINT_NAME`
- `core/DeepLinkLadder.kt`：`packageForPlatform(accountId)`（pdd/tb/jd → 官方 App 包名）

### 数据与安全
- `data/ShowCodeDb.kt`：VERSION 8；新列；`bound_account`；`parcel_dedupe` 排除空码；运单号唯一索引
- `data/AccountStore.kt`：bind / cookieOf / enabled / mark / remove
- `data/AccountSyncEngine.kt`：解密 → 抓取 → 合并 → 写库 → 记状态
- `data/fetch/PddFetcher.kt`：PDD 全尺寸隐藏 WebView + `window.name` 钩子 + offset 翻页 + 详情
- `data/fetch/TbFetcher.kt`：淘宝 mtop + SSR 物流详情（HttpURLConnection，无 OkHttp）
- `data/fetch/JdFetcher.kt`：京东订单中心 JSONP 改写 + 物流页 DOM
- `security/SecretBox.kt`：Android Keystore AES-256-GCM
- `data/ParcelRepository.kt`：`applyAccountSync` / `AccountSyncResult`

### UI
- `ui/account/AccountScreens.kt`：账号列表 + 官方登录页 + 删除确认
- `ui/ShowCodeRoot.kt`：`ACCOUNT_LIST` / `ACCOUNT_LOGIN` 路由、同步状态
- `ui/settings/SettingsScreen.kt`：平台账号入口
- `ui/home/HomeScreen.kt`：卡内「后五位」；空码件显示「暂无取件码」+ 阶段
- `ui/onsite/OnsiteCodeBody.kt`：「出示单号后五位」；空码件换成 `NoCodeBody`（解释 + 运单号 + 去官方 App + 查看详情）
- `ui/onsite/OnsiteScreen.kt`：`onOpenOfficial` / `onOpenParcelDetail` 回传
- `ui/parcels/ParcelDetailScreen.kt`（新建）：运单号、驿站、阶段、完整轨迹原文；空码时「暂无取件码」+ 去官方入口
- `ui/ShowCodeRoot.kt`：`PARCEL_DETAIL` 路由（depth 2，返回现场模式）
- `ui/Format.kt`：`stageLabel(Stage)`
- `res/values/strings.xml`：`no_pickup_code` / `detail_*` / `stage_*` 等

### 工程与文档
- `app/build.gradle.kts`：`androidx.webkit` 转 implementation
- `app/src/main/AndroidManifest.xml`：`INTERNET`
- `app/src/debug/…`：P0 探针（debug 专用，不进 release）
- `docs/account-binding-plan.md`：方案 + P0/P1 记录
- `docs/verification-checklist.md`：H1–H9
- `README.md`：权限、隐私、账号能力与 MIT 出处

---

## 4. 验证证据

| 项 | 结果 |
|---|---|
| `python tools/migration-check.py` | 全绿：v1→v9、表/列一致、21 条 SELECT 可执行 |
| `./gradlew :app:testDebugUnitTest` | 全绿，共 305 条（含 `AccountMergeTest` 16、`GroupingTest` 15、`GoodsCardTest` 7、`GoodsInfoTest` 7、`NoCodeActionsTest` 5） |
| `connectedDebugAndroidTest`（只跑 `SecretBoxTest`） | 2/2 绿：往返一致、密文不含明文、篡改返回 null |
| 模拟器 v8→v9 迁移 | `user_version` 8→9；旧 7 件包裹逐列保留，商品五列为 null |
| P0 单轮 | 4 单、10→30 条、1250 字轨迹、运单号 `465700000020382` |
| P3 设备同步 | `orders=4`、3 条轨迹、1 件入首页、`code_kind=SUFFIX` |

---

## 5. 遗留问题与风险

1. **淘宝带码件未验**：2 件无码件已按新规则入首页并通过设备验证；
   「带取件码 / 后五位」的淘宝真实到站件仍未出现，`PICKUP` / `SUFFIX` 展示只由 PDD 件与单测覆盖。
2. **京东真实订单解析未验**：用户确认近期无京东订单，本轮只验到「绑定 / 空结果 / 不误清库 / UI 提示」。
3. **真机未验证**：所有结果来自 MuMu Android 15 / WebView 110；真机 WebView 版本、风控与指纹不同。
4. **真机 v7→v8 未验**：模拟器上是「v8 数据 + user_version=7」的代码路径，不是真正的旧列结构；完整列链由 `migration-check` 在宿主 SQLite 验证。
5. **详情偶发超时**：PDD 4 单里 1 单在 15 s 内没等到 `goods_express`；已改成单条失败继续、部分结果照样入库。
6. **未做运输中 / 未定位列表**：不满足「有点 + 有码」的账号件留在账号页统计，不进首页。
7. **无后台轮询**：按方案刻意不做；账号同步只在用户点「立即同步」时发生。
8. **登录失效未显式标记**：抓取返回空时目前报「这次没有新的到站件」，没有把过期会话置 `EXPIRED`。
9. **F-Droid**：使用第三方非自由服务可能触发 `NonFreeNet` 反特性标记，提交前需按 `fdroidserver` 源码确认。

---

## 6. 等待淘宝 / 京东登录期间完成的小尾巴

### 6.1 点名清洗 + pointKey 回填（已在真实数据上验证）

- 第一次同步后，4 件账号件的点名被清洗：
  - `示例大学示例校区驿站` ×2（`76416` 后五位 + `38-1-17031` 取件码）
  - `示例大学多多驿站` ×2（`6-5-15031`、`1-2-14161`）
  - 同步结果：`新增 3 · 更新 1`
- 发现同一个「示例校区驿站」因为旧 pointKey 未跟着清洗而裂成两张卡；
  修正 `AccountMerge` 后再次同步：`新增 0 · 更新 4`，首页合并为 **1 张示例校区卡（2 件）**。
- 仍保留「多多驿站 / 示例校区驿站 可能是同一个点?」的提示——这是两条不同点名的**用户判断**，
  不自动合并。

### 6.2 P4/P5 验收清单

已写入 `docs/verification-checklist.md` 的 **I1–I7**：淘宝绑定 → mtop 同步 → 轨迹字段；
京东绑定 → JSONP 同步 → 运单号；共同红线（已取不复活、签收不误标、不误清库、检索）；
真机复跑。账号一到位即可照单执行。

### 6.3 P6 凭证解不开的降级（设备已验证）

- 备份 DB → 把 `bound_account.secret` 改成垃圾串 → 点同步；
- 结果：`state=BROKEN`、`last_sync_result=凭据解不开，需要重新登录`，App 不崩；
- 恢复备份后：三家账号回到 `ACTIVE`，4 件包裹、1 件已取均完整。
- 这条证明「密文失效」和「登录过期」走的是不同的降级路径，且不会静默清数据。

### 6.4 P4 / P5 设备结果

- **淘宝**：
  - mtop `queryboughtlistv2` 返回 12 单；SSR 详情 HTML 找到物流字段；
  - 一单解析出 `mailNo=79040000038478`、点 `示例大学示例校区驿站`、阶段 `派件中`；
  - 另 2 件解析成功但**轨迹里没有取件码**；旧规则下 `新增 0 · 更新 0 · 2 件没有取件码`，
    新规则下改为 `新增 2 · 更新 0`（见第 7 节）；
  - 结论：**链路通，无码件入首页已完成设备验证；带码件仍未出现**。
- **京东**：
  - 绑定成立；拉取 `orders=4`（历史单），没有可入件；
  - 库内 4 件未被误清；账号卡显示「新增 0 · 更新 0」，顶部提示「这次没有新的到站件」；
  - 结论：**绑定 / 空结果 / 不误清库 / UI 提示已验证；真实订单解析未验**（用户确认近期无京东订单）。

### 6.5 需求变更：有运单号就收录（已完成）

- 判定门槛：`stage=AT_STATION` + 非空 `pointKey` +（有码 / `SUFFIX` / 有 `trackingNo`）。
- 无点名归 `驿站未定位`（`UNLOCATED_POINT_NAME`），不伪造真实驿站名。
- 首页空码件：码区 `Caption` + `InkMuted`「暂无取件码」，紧跟 `Micro` 阶段，不新增状态色、不加角标。
- 现场空码件：不画空码框，换成解释 + 运单号 +「去官方 App 查看取件码」+「查看详情」。
- 去重仍以运单号为主；`parcel_dedupe` 的 `WHERE picked=0 AND code <> ''` 保持不变，
  空码件不会撞唯一约束；`parcel_tracking_dedupe` 兜住同一运单号重复插入。
- 已签收规则不变：只有 `stage=DONE && explicitPickup` 才 `markPicked`；
  「签收人：门口」仍保持 `AT_STATION`，不误标已取。
- 新增/改写单测：`AccountMergeTest` 14 条（无码入列、空点名归组、无码不误标已取）、
  `GroupingTest` 11 条（无码有运单号进首页、既无码也无运单号不进）。
- 文档：`docs/account-binding-plan.md` 的不可逆决策与 9.6/9.7 已同步；
  `docs/verification-checklist.md` 新增 **J1–J5**。

---

## 7. 设备验证：淘宝 2 件无码件

环境：MuMu `emulator-5554`，Android 15，安装最新 debug APK 后重新同步淘宝。

### 7.1 同步

- 账号卡结果：`新增 2 · 更新 0`，顶部提示同为「新增 2 · 更新 0」。
- 库内新增两行（`sqlite3` 直查）：

```
id|code|code_kind|stage|tracking_no|point_name|picked|account_id|source
6||NONE|AT_STATION|79030000003029|示例市示例区示例驿站|0|tb|ACCOUNT
7||NONE|AT_STATION|79030000099213|示例大学示例校区驿站|0|tb|ACCOUNT
```

### 7.2 首页

- `示例大学示例校区驿站`：2 件 → 第 1 行 `38-1-17031`（PDD），第 2 行「暂无取件码 待取件」（淘宝）。
- `示例市示例区示例驿站`：1 件 → 「暂无取件码 待取件」。
- 卡片结构、件数、排序不变；没有新增状态色或角标。

### 7.3 现场模式

- 选中无码件后屏上显示：承运商「中通快递」→「这件还没有取件码」→
  「运单号 79030000003029」→「取件码只在淘宝里：粘贴单号搜索，或打开「身份码」出示后五位。」
  → 黑色主按钮「去淘宝查询」→ 次级动作「复制快递单号」→「查看详情」。
- **没有空白码框**，没有「长按可复制」；静默态下这块内容仍然全墨可见。

### 7.4 详情页

- 标题「件详情」；点名完整；「中通快递 · 待取件」；
  「暂无取件码」+ 去官方入口；元数据行（运单号 / 状态 / 取件点 / 承运商 / 订单号）；
  「最新轨迹」展示完整原文（中通全文，含 D 柜暂存、派件、转运各节点）。
- 返回键回到现场模式。

### 7.5 未覆盖

- 淘宝「带取件码 / 后五位」的真实到站件未出现；
- 「已取 → 再同步不复活」已在无码件上补跑，见第 8 节。

---

## 8. 已取件从待取区移除（设备验证）

### 8.1 改动

- `core/Grouping.kt`：`filterNot { it.picked }` 是唯一入口；首页、现场、平铺、桌面组件都走它。
  **不物理删库**——`picked=1` 的行保留，下一次同步靠它判 `ALREADY_PICKED`。
- `ui/home/HomeScreen.kt`：移除首页「最近已取」栏；已取件不出现在首页任何位置。
- `ui/ShowCodeRoot.kt`：同步移除 `recentlyPicked` 状态与恢复回调。
- `data/ParcelRepository.kt`：`markPicked` / `undoPicked` 的乐观更新同时调整 `monthlyPicked`，
  最后一件取完时首页当场进入「都取完了」空态，不会先闪一帧「还没有快递」。
- 删除 `core/RecentlyPicked.kt` 与其单测（唯一的消费方已移除）。
- 单测：`GroupingTest` 补「同点 2→1」「已取的无码件不进首页」；`AccountMergeTest` 补「已取账号件再同步仍 Skip」。

### 8.2 设备回路

MuMu `emulator-5554`，Android 15，最新 debug APK。

1. 多多驿站 2 件：现场取走 1 件 → 回首页，卡上**当场**显示 1 件。
2. 示例市驿站只剩 1 件（无码、`tracking_no=79030000003029`）：取走后整张卡消失。
3. 剩下两处全部取完 → 首页显示「都取完了 🎉 本月共取 6 件」，无残卡、无「最近已取」栏。
4. 淘宝再同步：`新增 0 · 更新 0`，顶部「这次没有新的到站件」；
   无码件 `id=6` 仍为 `picked=1`，首页不出现。
5. 拼多多再同步：旧的 4 件全部保持 `picked=1`；
   同时出现 1 件**新的真实订单**（`tracking_no=79140000040095`、码 `40095`），不是已取件复活。

DB 终态（`select id,tracking_no,picked`）：

```
2|465700000076416|1
3|465700000020382|1
4|YT0000000010257|1
5|JT0000000089980|1
6|79030000003029|1
7|79030000099213|1
8|79140000040095|0   ← 新订单，不是复活
```

### 8.3 未覆盖

- 「轨迹明确本人取件 → 自动 `picked`」这条路径本轮没有真实轨迹可触发，只由 `AccountMergeTest` 覆盖；
  它与手动「已取」在展示层走同一个 `picked` 字段和同一个分组过滤。

---

## 9. 无码件交给淘宝查询（设备验证）

### 9.1 改动

- `ui/onsite/OnsiteCodeBody.kt`：`NoCodeBody` 换成「运单号 → 淘宝用法一句话 → 去淘宝查询（黑色主按钮）
  → 复制快递单号（次级 `Action + AccentInk`）→ 查看详情」。不画空码框、不出现「长按可复制」。
- `ui/onsite/NoCodeActions.kt`（新建纯函数）：`noCodeTrackingText` 决定复制内容与入口是否出现；
  只有 `code=""` 且有完整 `trackingNo` 时返回单号；有码件（含 `SUFFIX`）一律 `null`。
- `ui/onsite/OnsiteScreen.kt`：复制走 `copyCode`（Toast）；去淘宝先静默复制单号再拉起，
  返回非 `Opened` 时 Toast「已复制单号，去淘宝粘贴搜索」，剪贴板保留。
- `ui/Clipboard.kt`：拆出 `copyText`（只写剪贴板不出声）。
- `data/ParcelRepository.kt`：新增 `openApp(packageName)`——只走 launch intent，不走深链、不落浏览器兜底。
  现场「去淘宝查询」用它；否则没装淘宝时 `tbopen://` 会被 Chrome 接住。
- `ui/ShowCodeRoot.kt`：`onOpenTaobao = { repository.openApp(DeepLinkLadder.TAOBAO) }`。
- `res/values/strings.xml`：`onsite_no_code_taobao_hint` / `onsite_query_taobao` /
  `onsite_copy_tracking` / `onsite_tracking_copied` / `onsite_query_taobao_fallback`。
- 单测 `NoCodeActionsTest`（5 条）：复制内容是完整运单号、有码件与 SUFFIX 不出现、无单号不出现、文案 key 存在。

### 9.2 设备结果

MuMu `emulator-5554`，Android 15，未安装淘宝。

- 无码件现场屏：`运单号 79030000003029` → 淘宝用法一句 →「去淘宝查询」黑色主按钮 →
  「复制快递单号」蓝色次级 →「查看详情」；无空码框、无「长按可复制」。
- 点「复制快递单号」：出现「快递单号已复制」Toast。
- 点「去淘宝查询」：**停留在本 App**，出现「已复制单号，去淘宝粘贴搜索」Toast；
  没有再落到 Chrome（修复前的深链兜底会打开空白浏览器）。
- 有码的后五位件（`40095`）屏幕保持原样：仍显示「出示单号后五位」和「长按可复制」，
  不出现淘宝查询/复制这两个入口。

### 9.3 未覆盖

- 淘宝真实安装时的拉起没有设备证据（模拟器未装淘宝）；代码只走 launch intent，
  失败分支已验，成功分支由 `LaunchOutcomeTest` 与 `openApp` 同一条判据覆盖。

---

## 10. 商品信息 / 空点过滤 / SUFFIX 淘宝交接（本轮）

### 10.1 商品信息（DB v9 + 三家解析 + 三处缩略图）

**字段来源**

| 平台 | 能拿到 | 来源 |
|---|---|---|
| 拼多多 | 标题、图、价格、下单时间 | `order_list_v4` 的 `goods`/`goods_list` 容器：`goods_name`、`goods_thumbnail_url`、`goods_price`（分→元）、`create_time` |
| 淘宝 | 标题、图、下单时间 | `mainOrders[].subOrders[0].itemInfo`：`title`、`pic`；时间在 `extra`/`orderInfo` 的 `createTime` |
| 京东 | 预留标题/图/价格/店铺/时间 | `wareInfoList[].wareName` / `wareImage` / `shopInfo.shopName` / `orderInfo.orderTime`；**无真实订单，未验证** |

拿不到的价格、店铺一律 null，不猜；PDD 的 `goods_price=2840` 按分显示为 `¥28.4`。

**改动文件**：`core/Model.kt`、`core/account/FetchedParcel.kt`、`core/account/GoodsInfo.kt`（新建）、
`core/account/TraceParser.kt`、`data/fetch/PddFetcher.kt` / `TbFetcher.kt` / `JdFetcher.kt`、
`data/ShowCodeDb.kt`（VERSION=9 + 五列）、`data/ParcelRepository.kt`（Insert/Update 写商品列、缺值不覆盖）、
`ui/parcels/GoodsCard.kt`（新建：`showsGoodsCard` / `goodsThumbnailUrl` / `orderedAtText`）、
`ui/parcels/ThumbnailLoader.kt`（新建：LruCache + 降采样，无新依赖）、
`ui/GoodsThumbnail.kt`（新建共享组件）、`ui/parcels/ParcelDetailScreen.kt`、
`ui/home/HomeScreen.kt`、`ui/onsite/OnsiteCodeBody.kt`。

**三处展示**

- 首页卡片行：44dp 图 + 码 + 标题单行 + 店铺/价格小字，行高固定 52dp；无商品数据的行不变。
- 现场屏顶部：44dp 图 + 标题单行 + 店铺/价格小字；码/后五位仍是画面最大元素。
- 详情页：元数据卡上方商品卡，76dp 图 + 标题最多两行 + 价格 + 店铺/下单时间。
- 加载中/失败统一浅灰「无图」占位，尺寸固定；没有商品数据时整块不出现。

**设备结果**：PDD 件 `40095` 详情页显示真实商品图、标题 `示例商品…`、`¥28.4`、`下单 10-15 02:30`；
首页行与现场顶部同样出现缩略图；淘宝件 `79030000003029` 有图/标题/订单时间，价格与店铺为空（接口没给）。
DB 迁移 `user_version 8→9`，旧行数据不变。

### 10.2 首页不显示没有在站件的驿站

**原因（查实，不是空点表）**：代码里**没有**读取账号收货地址/默认地址的地方，也没有独立的
`pickup_point` 表；卡片只能由 `groupByPoint(parcels)` 生成。当时那两张卡里，
`示例市示例区示例驿站` 之所以出现，是因为本轮验证商品时把该件（`id=6`）
在 sqlite 里从 `picked=1` 改回了 `picked=0`，它是一条真实的待取行，不是地址也不是空点。
其余点（多多驿站）两行都已 `picked=1`，从未生成卡片，说明现有展示过滤是对的。

**处理**：把 `id=6` 恢复为已取，重新同步 PDD/淘宝；首页只剩 `示例大学示例校区驿站`。
新增单测：卡片数 = 有在站件的点数、一个点全取完不再贡献卡片；
再同步时已取件走 `ALREADY_PICKED`，不会把空点带回来。

**改动文件**：`GroupingTest.kt`（两条新测试）；生产代码无改动（过滤本来就在 `Grouping.kt`）。

### 10.3 SUFFIX 件也走淘宝交接

- 判定从「`code=""`」扩到「`codeKind=NONE` 或 `SUFFIX`，且有完整 `trackingNo`」；`PICKUP` 不出现。
- 复制内容始终是完整运单号（测试断言 `79140000040095`，不是 `40095`）。
- 现场按三区重排：上区商品信息（缩略图 + 标题单行 + 店铺/价格一行）用间距/hairline 与码区分开；
  中区码/后五位居中、上下留白；下区一句提示 + 动作。
  `SUFFIX` 屏把两个入口收成一行蓝色「图标 + 短词」（淘宝查询 / 复制单号，触控高 40dp），
  「查看详情」灰字最后；主区只剩码 + 一句提示，不再有黑色大按钮。`NONE` 屏同一套收纳；
  去掉了这一屏的「长按可复制」旧文案。
- 详情页 `SUFFIX`/`NONE` 都显示同一句提示 + 两个动作；`NONE` 原来的「去官方 App」按钮被替换。
- 共同动作抽到 `ui/TrackingLookup.kt`：先复制完整单号再只拉起淘宝 App；没装/被拦时提示
  「已复制单号，去淘宝粘贴搜索」，不落浏览器。

**改动文件**：`ui/onsite/NoCodeActions.kt`（改名 `trackingLookupText` 并扩到 SUFFIX）、
`ui/TrackingLookup.kt`（新建）、`ui/onsite/OnsiteCodeBody.kt`、`ui/onsite/OnsiteScreen.kt`、
`ui/parcels/ParcelDetailScreen.kt`、`ui/ShowCodeRoot.kt`、`res/values/strings.xml`。

**设备结果**：`40095` 现场屏显示商品行 + 大字后五位 + 提示 + 两个动作，无元素重叠；
复制出现「快递单号已复制」；「去淘宝查询」出现降级提示并停留本 App；
`PICKUP` 件 `38-1-17031` 屏确认没有提示/两个动作，保持「长按可复制」原样。

### 10.4 未验证

- 京东商品字段无真实订单，无法验证；代码按 `wareInfoList` 候选字段写好，缺字段保持 null。
- 淘宝价格、店铺在当前 mtop 响应里没有出现，界面如实留空。
- 淘宝真实安装时的拉起成功分支未验（模拟器没装淘宝）。
