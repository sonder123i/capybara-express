# 淘宝抓取链路 · 健壮性强化详细任务清单

> 目标：把淘宝（TbFetcher）做到「尽量不出问题、出问题不静默、单点故障可降级」——以拼多多为基线，并在数据源冗余与可观测性上做到同等或更强。
> 基线：dev.showcode v0.4.3 / versionCode 8 · 2026-11-09
> 关联文档：[tb-fetcher-robustness-plan.md](tb-fetcher-robustness-plan.md)

---

## 0. 设计原则（先定义「尽量强」）

| 编号 | 原则 | 落地要求 |
|---|---|---|
| R0 | **不静默** | 任何失败必须出现在 UI（在途/账号状态/诊断页），不能只打日志 |
| R1 | **有单必现** | 订单列表可见的活跃单，必须至少生成一条「轨迹待补」条目 |
| R2 | **多源冗余** | ≥2 个独立订单来源（mtop + WebView H5）+ ≥2 个状态来源（列表状态 + 详情轨迹） |
| R3 | **降级不丢数据** | 任一源失败不退休、不置成功、不删已有卡片；宁多一张待补，不少一件 |
| R4 | **单点隔离** | 单条详情/单个订单失败不影响同批；单源失败不影响另一源 |
| R5 | **可观测** | 每单一行结构化日志；每次同步一份本地诊断摘要（保留最近 10 次） |
| R6 | **可回归** | 纯函数单测 + JSON golden + 语料 + 真机清单，四层验证 |

---

## 1. 任务编号与优先级

- 编号：TB-xxx（TaiBao）。
- P0 = 不做就不算达标；P1 = 强烈建议同批完成；P2 = 后续增强。
- 估时按 1 人日 ≈ 6 小时可专注投入计算。

---

## 2. 任务总表

### A. 统一状态词表（Phase 1）

| ID | 任务 | 优先级 | 依赖 | 估时 | 产出与验收 |
|---|---|---|---|---|---|
| TB-001 | 新建 `core/account/OrderStage.kt`：`fun stageOf(prompt: String): Stage?` 全平台唯一状态表 | P0 | — | 0.3d | 覆盖 DONE / AT_STATION / DELIVERING / IN_TRANSIT / null 五档 |
| TB-002 | `DetailGate.stageOfPrompt` 改为委托 `OrderStage.stageOf` | P0 | TB-001 | 0.1d | 行为等价，已有用例全绿 |
| TB-003 | `AccountMerge.isActiveOrderPrompt` 改为 `stageOf != null && stage != DONE` | P0 | TB-001 | 0.1d | 活跃状态判定单一入口 |
| TB-004 | `AccountMerge.fallbackFetchedParcel` 的 `when(prompt)` 改委托 `OrderStage.stageOf` | P0 | TB-001 | 0.1d | `已揽件/揽收/待收货` 全部产 IN_TRANSIT |
| TB-005 | `TbFetcher.detailRank` 改委托 `OrderStage.stageOf`；`StageMachine.IN_TRANSIT` 补 `揽件|揽收` | P0 | TB-001 | 0.1d | `已揽件` 在轨迹与列表两条路都识别 |
| TB-006 | `OrderStageTest`：40+ 真实文案表驱动 | P0 | TB-001 | 0.3d | 覆盖淘宝/拼多多/京东三平台文案；全绿 |

### B. 双数据源与降级（Phase 2，核心）

| ID | 任务 | 优先级 | 依赖 | 估时 | 产出与验收 |
|---|---|---|---|---|---|
| TB-010 | `TbFetcher.listOrders` 支持翻页：page=1..5，直到 `orderListEnded` 或空页 | P0 | — | 0.4d | 单测：3 页数据合并去重、顺序稳定 |
| TB-011 | 列表解析兼容 `mainOrders` 与 `Main_*` 两种 schema；缺字段不抛 | P0 | — | 0.3d | 两份 golden JSON 均可解析 |
| TB-012 | mtop 令牌刷新加固：合并 Set-Cookie 的 `_m_h5_tk/_m_h5_tk_enc`，token 失效重试 1 次 | P0 | — | 0.3d | 单测：token 过期 → 刷新 → 成功；不重试 AUTH |
| TB-013 | **WebView 第二数据源**：加载淘宝 H5 订单页，document-start 注入捕获 `order_list` 响应 | P0 | — | 1.0d | 真机可抓到订单 JSON；无 `addJavascriptInterface` |
| TB-014 | 第二源解析器：JSON → `TbOrder(id,status,goods)`，与 mtop 结果同构 | P0 | TB-013 | 0.3d | 单测覆盖两种页面响应结构 |
| TB-015 | 双源合并：按 orderId 取并集；字段互补（详情以 mtop 为准，goods 以非空为准） | P0 | TB-010/014 | 0.3d | 单测：同单不同字段、仅单源存在、重复单 |
| TB-016 | 第二源能力检测：WebView 存在、`DOCUMENT_START_SCRIPT` 支持、Cookie 非空；不满足时明确降级 | P0 | TB-013 | 0.2d | 不支持机型不崩，走单源并记录原因 |
| TB-017 | 主源失败自动降级：mtop 抛错/空/结构不可识别 → 尝试第二源，成功则继续 | P0 | TB-013/015 | 0.3d | 单测：主源失败→第二源成功→结果非空 |
| TB-018 | 覆盖度自检 A：列表 0 单但库内有该账号活跃件 → 本次不置成功、不退休，报「接口可能改版」 | P0 | TB-015 | 0.2d | 单测 + 真机状态文案 |
| TB-019 | 覆盖度自检 B：本次列表数量 < 上次成功数量 50% → 标记 suspicious，不退休 | P1 | TB-020 | 0.2d | 单测阈值边界 |
| TB-020 | 持久化 `last_good_order_count` / `last_success_source` / `last_breakage_at` 到 meta | P1 | — | 0.2d | 升级不丢；读取失败兜底 0 |

### C. TbFetcher 管线重构（Phase 2，核心）

| ID | 任务 | 优先级 | 依赖 | 估时 | 产出与验收 |
|---|---|---|---|---|---|
| TB-030 | 候选集统一：`orders.filter { OrderStage.stageOf(it.status) != null }`，替换 `needsDetail` | P0 | TB-001 | 0.2d | 单测：待付款/关闭不进候选，运输中/已揽件进 |
| TB-031 | 详情抓取单条超时/重试预算：单条 ≤20s，整批 ≤90s | P0 | — | 0.3d | 超时不拖垮整批 |
| TB-032 | **每单兜底**：`detail` 失败/返回 null → `fallbackFetchedParcel(TB, orderSn, status, goods)` | P0 | TB-004 | 0.3d | 单测：detail null + 已揽件 → IN_TRANSIT 条目 |
| TB-033 | `parsed.stage == UNKNOWN` 且列表状态可识别 → 用列表阶段重写（保留 tracking/goods） | P0 | TB-030/032 | 0.2d | 单测：UNKNOWN + 运输中 → IN_TRANSIT |
| TB-034 | `allDetailsFailed` 语义改为「候选非空且 result 为空」；有兜底不再整批失败 | P0 | TB-032 | 0.1d | 单测：10 单全失败仍返回 10 条 fallback |
| TB-035 | `MAX_DETAILS` 动态化：`min(16, candidates.size)`；超出部分全部走 fallback | P0 | TB-032 | 0.2d | 单测：20 候选 → 20 条结果 |
| TB-036 | 每单结构化日志：`sn / listStatus / detail=ok|empty|no-ssr|exception / stage / fallback` | P0 | — | 0.2d | logcat 一行可判因；无 PII（Slog.mask） |
| TB-037 | `CancellationException` 透传（所有 catch 块） | P0 | — | 0.1d | 单测：取消不写退避、不返回 Failure |
| TB-038 | 错误分类：AUTH / TRANSIENT / FORMAT，分别映射账号状态与退避 | P1 | — | 0.2d | 单测 decideSync 各分支 |
| TB-039 | 列表级错误兜底到第二源（含超时/HTTP 5xx/JSON 解析失败） | P0 | TB-017 | 0.2d | 与 TB-017 合并验收 |
| TB-040 | 详情级错误只影响单条；连续失败阈值仅记录不阻断 | P1 | — | 0.2d | 日志计数 + 诊断摘要 |
| TB-041 | 全链路去重：orderId > trackingNo > code+point，禁止同单两行 | P0 | — | 0.2d | 单测：同单两源只产一条 |
| TB-042 | 退休语义收紧：只有「至少一个源完整（ended）+ 双源并集」才提交 seen | P0 | TB-015/035 | 0.2d | 单测：源不完整 → seen 空 → 不退休 |

### D. 数据层与账号状态（Phase 2–3）

| ID | 任务 | 优先级 | 依赖 | 估时 | 产出与验收 |
|---|---|---|---|---|---|
| TB-050 | `FetchedParcel` 允许 `Stage.UNKNOWN` 且身份非空；文档写清与 fallback 的关系 | P0 | — | 0.1d | 模型注释 + 单测 |
| TB-051 | `planAccountSync`：UNKNOWN + 有身份 → Insert（保持现状），补回归测试 | P0 | TB-050 | 0.1d | 单测：UNKNOWN 不因阶段被跳过 |
| TB-052 | `mergeExisting`：UNKNOWN 不覆盖已到站；已到站不回退（保持现状），补测试 | P0 | — | 0.1d | 单测：AT_STATION + 新 UNKNOWN → 仍 AT_STATION |
| TB-053 | `fallbackFetchedParcel` 状态词与 `OrderStage` 对齐测试 | P0 | TB-004 | 0.1d | 已揽件/揽收/待收货/运输中 全通过 |
| TB-054 | `AccountSyncResult` 增加 `listCount / candidateCount / detailOk / fallbackCount` | P1 | — | 0.2d | 账号状态文案可解释「平台 N 件→本页 M 件」 |
| TB-055 | 成功文案：有 fallback 时说「N 件轨迹待补」，不说「全部成功」 | P1 | TB-054 | 0.1d | 文案单测 |
| TB-056 | 每次同步摘要写入 meta（JSON：时间/源/计数/错误码），保留最近 10 次 | P1 | TB-054 | 0.3d | 诊断页读取；向后兼容 |

### E. 首页/详情可见性（Phase 3）

| ID | 任务 | 优先级 | 依赖 | 估时 | 产出与验收 |
|---|---|---|---|---|---|
| TB-060 | `ShowCodeRoot.realInTransit`：纳入 `Stage.UNKNOWN && (trackingNo 或 orderSn 非空)` | P0 | — | 0.1d | 单测/组合测试：UNKNOWN 件出现 |
| TB-061 | `HomeScreen.inTransitStatus`：UNKNOWN → 「状态未知 · 轨迹待补，下次同步会补全」 | P0 | TB-060 | 0.1d | 文案评审 + 截图 |
| TB-062 | 在途列表排序：真正运输中在前，轨迹待补在后，避免刷屏 | P1 | TB-060 | 0.1d | 排序函数单测 |
| TB-063 | 包裹详情页支持 UNKNOWN 件：显示承运商/运单号/原文摘要，不显示假取件码 | P1 | — | 0.2d | 手工验收 |
| TB-064 | 空态/计数不被待补件影响（不参与待取计数） | P0 | — | 0.1d | 单测：组数/件数不变 |
| TB-065 | 截图回归新增「在途·轨迹待补」基准图 | P2 | TB-061 | 0.2d | Roborazzi 用例 |

### F. 诊断与可观测（Phase 3–4）

| ID | 任务 | 优先级 | 依赖 | 估时 | 产出与验收 |
|---|---|---|---|---|---|
| TB-070 | 设置 → 平台账号 → 「同步诊断」页：每平台最近一次源/计数/错误/耗时 | P1 | TB-056 | 0.5d | 真机可读；无 Cookie 明文 |
| TB-071 | 诊断摘要环形缓冲（最近 10 次），支持清空 | P1 | TB-056 | 0.2d | 单测解析/序列化 |
| TB-072 | 结构化日志规范：tag=TbFetcher，字段固定；release 只保留计数 | P1 | TB-036 | 0.1d | 代码审查清单 |
| TB-073 | 脱敏测试：日志/诊断不出现 Cookie、完整单号、手机号 | P0 | TB-070 | 0.2d | grep 断言 + 单测 |
| TB-074 | 「复制诊断信息」按钮（脱敏后的文本） | P2 | TB-070 | 0.1d | 手工验收 |

### G. 网络与稳定性加固（Phase 2）

| ID | 任务 | 优先级 | 依赖 | 估时 | 产出与验收 |
|---|---|---|---|---|---|
| TB-080 | HTTP 超时：connect 8s / read 12s / 整批预算 90s，可配置常量 | P0 | — | 0.1d | 常量集中 + 单测超时分支 |
| TB-081 | 瞬时失败重试：最多 1 次，300–800ms 抖动；AUTH 不重试 | P1 | TB-038 | 0.2d | 单测：重试次数正确 |
| TB-082 | 熔断：连续 3 次同步失败 → 30 分钟内不再自动重试（复用 `lastTransientFailureAt`） | P1 | — | 0.2d | 时间边界单测 |
| TB-083 | 详情并发固定 4 路；WebView 只在主线程创建/销毁 | P0 | — | 0.1d | 代码审查 |
| TB-084 | WebView 生命周期：`finally` 只 destroy 一次；Activity 重建时安全取消 | P0 | — | 0.2d | 转屏/退出不崩 |
| TB-085 | 重定向 Cookie 白名单：只对 `*.taobao.com/*.tmall.com` 附带 Cookie | P1 | — | 0.2d | 单测：外域重定向不携带 |
| TB-086 | 所有写库走现有串行 IO dispatcher；同步事务边界不变 | P0 | — | 0.1d | 代码审查 |

### H. 测试（Phase 4）

| ID | 任务 | 优先级 | 依赖 | 估时 | 产出与验收 |
|---|---|---|---|---|---|
| TB-090 | `OrderStageTest` 40+ 文案 | P0 | TB-001 | 0.3d | 全绿 |
| TB-091 | `TbListParserTest`：mainOrders / Main_* / 缺字段 / 空数组 4 组 golden | P0 | TB-011 | 0.3d | 全绿 |
| TB-092 | `TbFallbackTest`：已揽件/运输中/待取件/已签收/无 goods/无 tracking 6 组 | P0 | TB-032 | 0.3d | 全绿 |
| TB-093 | `TbTwoSourceMergeTest`：仅主源/仅副源/双源冲突/重复单 | P0 | TB-015 | 0.3d | 全绿 |
| TB-094 | `TbCoverageGuardTest`：0 单 vs 库活跃、骤降 50%、完整 seen | P0 | TB-018/019 | 0.2d | 全绿 |
| TB-095 | 新语料：`tb-9021`（已揽件）、`tb-9022`（运输中）、`tb-9023`（空详情+已发货）、`tb-9024`（待取件）、`tb-9025`（已签收） | P0 | — | 0.2d | `TraceCorpusAuditTest` 全绿 |
| TB-096 | `AccountMergeTest` 增补 fallback/UNKNOWN 用例 | P0 | TB-051/052 | 0.2d | 全绿 |
| TB-097 | 本地 HTTP 桩（JDK HttpServer，不引依赖）测 mtop 签名/刷新/翻页 | P1 | TB-012 | 0.4d | 全绿 |
| TB-098 | 单测覆盖 `UpdateInstaller.verify`？与本任务无关，不混入 | — | — | — | 保持边界 |
| TB-099 | CI 增加上述测试分组；保持现有两道棘轮 | P0 | 全部 | 0.1d | CI 绿 |

### I. 真机验收（Phase 5）

| ID | 任务 | 优先级 | 依赖 | 估时 | 产出与验收 |
|---|---|---|---|---|---|
| TB-100 | 真实淘宝账号：已揽件 → 在途出现 | P0 | TB-032/060 | 0.2d | 截图 + logcat |
| TB-101 | 真实淘宝账号：运输中 → 在途出现 | P0 | 同上 | 0.1d | 截图 + logcat |
| TB-102 | 真实淘宝账号：待取件（有码）→ 待取卡/现场模式 | P0 | 同上 | 0.1d | 全流程走通 |
| TB-103 | 详情页不可达（hosts/飞行模式）→ 仍显示「轨迹待补」 | P0 | TB-032 | 0.1d | 截图 |
| TB-104 | mtop 不可达 → 第二源接住（若 TB-013 完成） | P0 | TB-017 | 0.2d | 截图 + 日志 |
| TB-105 | PDD/JD 回归：在途/待取/已取不变化 | P0 | — | 0.2d | 对照截图 |
| TB-106 | 7 天 soak：每天自动同步，记录 fallback 比例与失败次数 | P1 | 发布后 | 1w | 报告：无静默丢失 |

### J. 发布与回滚（Phase 6）

| ID | 任务 | 优先级 | 依赖 | 估时 | 产出与验收 |
|---|---|---|---|---|---|
| TB-110 | versionCode 9 / versionName 0.4.4；changelog 中英 | P0 | 全部 | 0.1d | 文件存在 |
| TB-111 | `:app:testDebugUnitTest` + `:app:assembleRelease` 全绿 | P0 | 全部 | 0.1d | CI 证据 |
| TB-112 | 发布前真机覆盖安装（v0.4.3 → v0.4.4）验证 DB 兼容 | P0 | — | 0.2d | 数据不丢 |
| TB-113 | 回滚预案：确认 `onDowngrade` 缺失问题是否影响本次（建议同期修，见评审 P0-2） | P1 | — | 0.1d | 方案文档 |
| TB-114 | 本地开关 `tb_second_source_enabled`（默认开），异常时可关 | P1 | TB-013 | 0.1d | 设置隐藏开关/诊断页 |
| TB-115 | 发布后监控：诊断页 fallback 比例、失败次数，7 天后复盘 | P1 | TB-070/106 | 0.1d | 复盘记录 |

---

## 3. 关键路径与执行顺序

```
第 1 天：TB-001 → TB-002..006（词表统一，先让「已揽件/揽收/待收货」全部可识别）
第 1 天下午：TB-030 → TB-032 → TB-034 → TB-035（每单兜底，止血版完成）
第 2 天：TB-060/061（UNKNOWN 可见）→ 真机跑 TB-100~103，确认症状消失
第 3–4 天：TB-010/011/012（翻页+解析加固）→ TB-013/014/015（WebView 第二源）
第 5 天：TB-016~020（降级/覆盖度自检）→ TB-036/037/041/042（隔离与退休）
第 6 天：TB-050~056（数据/状态文案）→ TB-070~074（诊断页）
第 7 天：TB-080~086（网络加固）→ TB-090~099（全部测试）
第 8 天：TB-104~106（真机/soak）→ TB-110~115（发布与回滚）
```

---

## 4. 里程碑与验收

| 里程碑 | 完成条件 | 对应任务 |
|---|---|---|
| M0 止血版（1 天） | 当前两张截图里的订单能在在途显示；详情失败也不消失 | TB-001~006、TB-030~035、TB-060~061 |
| M1 单源稳定（2 天） | 单条详情失败不影响同批；候选全部必有结果 | TB-030~042 |
| M2 双源冗余（4 天） | mtop 失败时第二源接住；列表 0 单不静默 | TB-010~020、TB-013~017 |
| M3 诊断可观测（5 天） | 诊断页可解释每次同步；日志可定位 | TB-056、TB-070~074 |
| M4 发布（6–7 天） | 真机清单全过、CI 全绿、0.4.4 发布 | 全部 |

---

## 5. 任务统计

| 分类 | P0 | P1 | P2 | 合计 |
|---|---|---|---|---|
| A 状态词表 | 6 | 0 | 0 | 6 |
| B 双数据源 | 9 | 2 | 0 | 11 |
| C TbFetcher 管线 | 10 | 3 | 0 | 13 |
| D 数据层 | 4 | 3 | 0 | 7 |
| E 可见性 | 3 | 2 | 1 | 6 |
| F 可观测 | 1 | 3 | 1 | 5 |
| G 网络稳定性 | 4 | 3 | 0 | 7 |
| H 测试 | 8 | 1 | 0 | 9 |
| I 真机验收 | 6 | 1 | 0 | 7 |
| J 发布 | 3 | 3 | 0 | 6 |
| **合计** | **54** | **21** | **2** | **77** |

> 注：统计按任务条数计，实际工时见各任务行；总工期约 5–7 人日（P0）+ 1–2 人日（P1）。

---

## 6. 明确不在本任务范围内

- 不改 PDD/JD 的抓取逻辑（只复用它们已有的 `fallbackFetchedParcel` / `OrderStage`）。
- 不解决淘宝风控/验证码/滑块（这类只能降级为「登录失效」提示，不静默）。
- 不做云端遥测；诊断数据只存本机。
- 不做自动登录/自动过验证（违反产品隐私承诺）。

---

## 7. 一句话

**先让「已揽件/揽收/待收货」被认识、让每单详情失败也有兜底、让 UNKNOWN 可见（1 天止血）；再用「mtop + WebView 双源 + 覆盖度自检 + 诊断页」把淘宝做到与拼多多同级、且失败绝不再静默。**
