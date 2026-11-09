# 淘宝健壮性实现状态（第一轮）

> 日期：2026-11-09 · 基线 v0.4.3（versionCode 8）
> 本轮目标：先落地 M0 止血 + M1 核心（详情失败不丢件、已揽件可识别、UNKNOWN 可见）。

## 已完成

| 任务 | 状态 | 说明 |
|---|---|---|
| TB-001 统一 OrderStage | ✅ | 新增 `core/account/OrderStage.kt`，全平台唯一状态表 |
| TB-002 DetailGate 委托 | ✅ | `stageOfPrompt` 走 OrderStage（DONE 仍返回 null 供详情门控） |
| TB-003 isActiveOrderPrompt | ✅ | 委托 `OrderStage.isActive` |
| TB-004 fallbackFetchedParcel | ✅ | 委托 OrderStage；已揽件/揽收/待收货均进 IN_TRANSIT |
| TB-005 detailRank / StageMachine | ✅ | detailRank 委托；StageMachine 补 `揽件/揽收` |
| TB-006 OrderStageTest | ✅ | 30+ 状态词表驱动，全绿 |
| TB-010 列表翻页 | ✅（基础版） | `MAX_LIST_PAGES=5`，按 orderId 去重；接口忽略 page 时保持不退休 |
| TB-013~017 WebView 第二数据源 | ✅（基础版，待真机验证） | 新增 `TbWebSource`：主源失败/为空且有活跃件时，加载 H5 订单页抓页面自身响应；支持 fetch/XHR/JSONP 捕获 |
| TB-019 数量骤降自检 | ✅（基础版） | 活跃件≥4 且本次订单数不足一半 → 按瞬时失败保留已有数据 |
| TB-018 覆盖度自检 | ✅（基础版） | 列表 0 单但库有活跃账号件 → 按瞬时失败处理，不退休 |
| TB-030 候选统一 | ✅ | `OrderStage.isActive` 过滤，不再用 needsDetail |
| TB-031 详情预算 | ✅（基础版） | 单条沿用现有超时，整批通过 MAX_DETAILS 控制 |
| TB-032 每单兜底 | ✅ | `detail` 失败/跳名额 → `fallbackFetchedParcel`，活跃单必有结果 |
| TB-033 UNKNOWN 升级 | ✅ | 详情 UNKNOWN + 列表状态可识别 → 用列表阶段重写 |
| TB-034 allDetailsFailed 语义 | ✅ | 改为「worklist 非空且 result 为空」 |
| TB-035 动态详情名额 | ✅ | MAX_DETAILS 8 → 16，未抓的走兜底 |
| TB-036 每单结构化日志 | ✅ | `result[...] detail=... fallback=... stage=...` |
| TB-037 CancellationException | ✅ | catch 中原样抛出 |
| TB-060 在途显示 UNKNOWN | ✅ | 有运单号/订单号的 UNKNOWN 件进入在途 |
| TB-061 UNKNOWN 文案 | ✅ | 「状态未知 · 轨迹待补，下次同步会补全」 |
| TB-054/055 同步结果计数 | ✅ | `fallbackCount` 进入 AccountSyncResult，状态行显示「N 件轨迹待补」 |
| TB-090/092/095/096 测试 | ✅ | OrderStageTest、TbFallbackTest、tb-9021/9022 语料 |

## MuMu 真机验证（2026-11-09 18:50）

- 设备：MuMu `SM-A5360` / Android 15；安装并覆盖升级到 0.4.3（签名一致，数据保留）。
- 淘宝账号：`ACTIVE`，真实同步一次：
  - `TbFetcher`：`orders=69 ended=false`
  - `statuses=物流派件中:1|卖家已发货:1|交易关闭:6|交易成功:61`
  - `pending=2 detailTargets=2 result=2 fallback=1 detailsFailed=0`
  - 账号结果：`同步成功 · 新增 0 · 更新 1 · 跳过 1（已签收 1） · 1 件轨迹待补`
- 首页「在途」实际出现兜底条目：`【李佳琦直播间双11彩妆节】千问办公会员月卡…`，`快递 · 运单号待出` / `已发货 · 卖家已发货`。
- 说明：该绑定淘宝账号共 69 单，状态分布只有 2 单活跃；截图里的礼物订单不在这个账号的「我买到的」列表里。
- 礼物订单自动抓取**不采用**：`mtop.alibaba.catapult.gift.list.gift` 从 App 内 HTTP 请求返回 `FAIL_SYS_USER_VALIDATE / RGV587_ERROR`（淘宝风控），不可靠；礼物件走短信/通知/手动导入。

## 本轮验证

- `./gradlew --offline :app:compileDebugKotlin`：通过
- `./gradlew --offline :app:testDebugUnitTest`：**522 tests，0 failures**
- `:app:assembleRelease` + MuMu 覆盖安装 + 真实淘宝同步：通过（见上）
- `python3 tools/visual-audit.py --ci`：全绿
- `python3 tools/migration-check.py --ci`：全绿

## 剩余重点（下一轮按序）

| 任务 | 优先级 | 说明 |
|---|---|---|
| TB-013~017 第二源真机验证 | P0 | `TbWebSource` 已实现；本次 mtop 主源成功，未触发第二源，需用「主源失败」场景确认页面 URL/捕获有效 |
| TB-019/020 覆盖度阈值与持久化 | P1 | 数量骤降告警、last_good 记录 |
| TB-038~042 错误分类/去重/退休收紧 | P0/P1 | 完整性保护 |
| TB-050~056 账号状态与诊断 | P1 | 「N 件轨迹待补」文案、最近 10 次摘要 |
| TB-070~074 诊断页 | P1 | 设置页查看每次同步的来源/计数/错误 |
| TB-080~086 网络加固 | P0/P1 | 超时/重试/熔断/Cookie 白名单 |
| TB-100~106 真机验收 | P0 | 已揽件/运输中/待取件/详情打挂/双源 |
| TB-110~115 发布 | P0 | 0.4.4 打包与回滚预案 |

## 代码改动清单

- `app/src/main/java/dev/showcode/core/account/OrderStage.kt`（新增）
- `app/src/main/java/dev/showcode/core/account/StageMachine.kt`
- `app/src/main/java/dev/showcode/core/account/DetailGate.kt`
- `app/src/main/java/dev/showcode/core/account/AccountMerge.kt`
- `app/src/main/java/dev/showcode/data/fetch/TbFetcher.kt`
- `app/src/main/java/dev/showcode/data/fetch/TbWebSource.kt`（新增，第二数据源）
- `app/src/main/java/dev/showcode/ui/ShowCodeRoot.kt`
- `app/src/main/java/dev/showcode/ui/home/HomeScreen.kt`
- `app/src/test/java/dev/showcode/core/account/OrderStageTest.kt`（新增）
- `app/src/test/java/dev/showcode/core/account/TbFallbackTest.kt`（新增）
- `app/src/test/java/dev/showcode/data/fetch/TbOrderParserTest.kt`（新增）
- `app/src/test/resources/traces/tb-9021.txt` / `tb-9022.txt`（新增语料）

## 第二轮修复（评审后，2026-11-09）

| 问题 | 修复 |
|---|---|
| `TbWebSource` 未解码 `evaluateJavascript` 的 JSON 字符串 → 第二源永远读不到 | 抽 `fetch/JsBridge.kt` 的 `decodeJsString`，淘宝/京东/拼多多三处共用，补 `JsBridgeTest` |
| 兜底条目覆盖老行会把运单号/承运商/原文降级 | `FetchedParcel.fromListFallback` 显式标记；`mergeExisting` 对兜底条目保留老行的 `trackingNo/carrier/raw` |
| 兜底阶段可把「派送中」降回「在途」 | 兜底条目只允许推进（`stageProgress`），详情结果仍走轨迹语序判据 |
| `OrderStage.DONE` 与 `isFinishedOrderPrompt` 不重合 → 「已收货/确认收货」的老卡永远退不掉 | `isFinishedOrderPrompt` 并入 `OrderStage DONE`；补性质断言 |
| 候选门只收词表认得出的状态 → 未收录的活跃单静默消失 | 新增 `needsDetailForPrompt`：只排除明确关闭与待付款；认不出也抓详情。淘宝/京东共用 |
| `runCatching` 吞掉第二源的 `CancellationException` | 改为 `try/catch + rethrowIfCancellation` |
| 「待取货」「已到达菜鸟驿站」「待确认收货」被误判 | `OrderStage` 补 `待取(?!消)`、到站回声正则、`已放到`、`待确认收货`；`确认收货` 加 `(?<!待)` |
| 首装账号主源为空时不会尝试第二源 | `fetch` 新增 `neverSynced`（`account.lastSyncAt == null`） |
| 数量骤降阈值过松 | `orders.size * 2 < activeKnown` → `orders.size < activeKnown` |
| 详情最坏等待过长 | 详情页独立超时 8s/10s，整批 ≈72s 封顶 |
| 语料含真实地名与座机 | `tb-9021/9022` 改为示例值；隐私自检新增座机规则 |
| 卡券/虚拟商品被当成包裹显示 | `isVirtualGoodsTitle` 强特征词判定（含「兑换码/月卡/点卡…」）；三平台候选剔除；已入库旧卡走 `retireOrderSns` 退休，不冒充「已签收」跳过；列表缺标题时用库里 `goodsTitle` 兜底 |

仍未验证：`TbWebSource` 真机（本轮主源成功，未触发第二源）；`StageMachine` 与 `OrderStage` 词表仍是两份，后续可合并。
