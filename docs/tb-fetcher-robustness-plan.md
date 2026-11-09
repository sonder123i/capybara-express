# 淘宝抓取链路健壮性对齐计划（对齐拼多多）

> 目标：让淘宝（TbFetcher）达到与拼多多（PddFetcher）同等的「有单必现、单条失败不拖垮整批」的健壮性。
> 版本基线：dev.showcode v0.4.3 / versionCode 8
> 日期：2026-11-09

---

## 0. 现象与结论（为什么不是单一状态词问题）

用户实测两张淘宝订单都不显示：

| 时间 | 订单 | 淘宝侧状态 | App 在途 |
|---|---|---|---|
| 15:12 | 中通 000000000000000001（明日方舟 山山兔） | 已揽件 11-09 09:22 | 不显示 |
| 17:47 | 中通 000000000000000002（明日方舟 大地巡旅） | 运输中 11-09 17:45 | 不显示 |
| — | 拼多多 中通 79157779048305 | 已发货 | 显示 |

关键判断：**「运输中」是 StageMachine 已经认识的词**（StageMachine.kt:71-73），如果这单被淘宝链路正常产出，它一定会显示。因此淘宝的问题不是（只是）状态词表，而是**订单在到达「可显示阶段」之前就被丢掉了**：

1. **详情解析失败即整单消失**：TbFetcher.detail() 返回 DetailOutcome(null, ...) 时，该单不计入结果；若候选全部失败，allDetailsFailed 让整次淘宝同步返回 Failure（TbFetcher.kt:119-138）。
2. **淘宝没有兜底**：全仓只有 PddFetcher 调用 fallbackFetchedParcel（PddFetcher.kt:239,524）；TbFetcher/JdFetcher 都没有。拼多多详情失败仍会按列表状态把件塞进「在途」（轨迹待补），淘宝不会。
3. **状态词表缺「已揽件」**：揽件 在 Kotlin 源码中 0 处（grep 全仓无命中）；StageMachine.IN_TRANSIT 只有「已揽收」，detailRank/isActiveOrderPrompt/stageOfPrompt 同样只认「已揽收」。
4. **详情名额硬上限**：MAX_DETAILS=8（TbFetcher.kt:163），超出候选不进结果也不兜底——在途单多时直接消失。
5. **UNKNOWN 阶段被 UI 静默过滤**：ShowCodeRoot.kt:272-282 的在途过滤只收 IN_TRANSIT/DELIVERING/空点 AT_STATION，Stage.UNKNOWN 的件入库后哪里都不显示。

**一句话：拼多多有「列表状态 → 兜底条目」的保底通道，淘宝只有「详情解析成功」这一条独木桥。**

---

## 1. 目标（验收不变式）

| 编号 | 不变式 | 判定方式 |
|---|---|---|
| I1 | **有单必现**：订单列表返回且状态属于活跃集合的单，本次同步后首页「在途」必有一行（详情轨迹或「轨迹待补」） | 单测：候选 N = 结果 N（含 fallback） |
| I2 | **单条失败隔离**：任一单详情异常不影响其他单；仅「全部候选且全部无兜底」才整批失败 | 单测 + 真机断网/404 |
| I3 | **状态全覆盖**：已揽件/揽件/揽收/待收货/运输中/已发货/已发出/已发往/已到达/转运/清关/异地/派送中/派件中 全部唯一映射 | 表驱动单测（≥30 条） |
| I4 | **零静默**：任何有身份的件（运单号或订单号）即使阶段未知也进「信息待补」，不消失 | UI 过滤单测 + 截图 |
| I5 | **可观测**：每单一行结构化日志（sn / listStatus / detail结果 / stage / 是否 fallback） | 日志样例 + 真机跑 |

---

## 2. 差距矩阵（PDD vs TB 现状 vs 目标）

| 能力 | PDD（现状） | TB（现状） | TB 目标 |
|---|---|---|---|
| 订单来源 | 2 路：order_list + express/track/status | 1 路：mtop queryboughtlistv2 | 主源 + 翻页；Phase 2 可选 WebView 兜底 |
| 列表状态→Stage | trackStatusStage + stageOfPrompt | 仅 detailRank 内部词表 | 统一 OrderStage |
| 详情失败兜底 | fallbackFetchedParcel | 无 | **加** |
| 单条失败隔离 | try/catch 后继续 | catch 后 continue，但全失败整批 Failure | 单条 continue；仅全空才失败 |
| 详情数量上限 | 无（全部候选） | 8 固定 | 时间预算 + 超限也走 fallback |
| 状态词覆盖 | 待收货/已发货/已揽收等 | 缺 已揽件/待收货 | 全量词表 |
| 未识别阶段 | fallback 避免 UNKNOWN | UNKNOWN 被首页过滤 | UI 「信息待补」 |
| 列表完整性 | ordersEnded | orderListEnded | 已有，保留 |
| 日志 | 逐单 result | 逐单 result 无 fallback 结果 | 增加 fallback/detail 原因 |

---

## 3. 实施阶段（建议 5 人日，可并行）

### Phase 0 · 诊断基线与埋点（0.5 天）
**目的：先确认当前淘宝账号下到底是「列表没拿到」还是「详情解析失败」。**
- TbFetcher 每单输出一行：order sn=**** listStatus=... detail=ok|empty|no-ssr|exception parser=parsed|null stage=... fallback=true|false
- 账号同步结果面板扩展可选计数：listCount / candidateCount / detailOk / fallbackCount / skipped。
- 真机用当前账号跑一次，记录：
  - orders=0 或 list 抛错 → 列表接口/登录问题（优先 Phase 2.7/2.8）。
  - orders>0 但 candidates=0 → 状态过滤问题（Phase 1）。
  - candidates>0 但 result=0 → 详情解析问题（Phase 2.2/2.3）。

**产出：** 一份 logcat 证据 + 明确的主因归类（预计落在详情/兜底）。

### Phase 1 · 统一状态词表（0.5 天）
新增 app/src/main/java/dev/showcode/core/account/OrderStage.kt：
- 唯一入口 fun stageOf(prompt: String): Stage?，覆盖：
  - DONE: 已签收|签收成功|已代收|已投递|交易成功|订单完成|已完成|待评价|已收货|确认收货|已取件|已取走|本人签收|完成取件
  - AT_STATION: 待取件|待自提|待领取|已到驿站|已到快递柜|已放入|已暂存|已入柜|取件码|取货码|提货码
  - DELIVERING: 派送中|派件中|正在派送
  - IN_TRANSIT: 待收货|运输中|已发货|已发出|已发往|已揽收|**已揽件**|**揽件**|**揽收**|已装车|转运中|中转|清关|异地|已到达
  - null: 其余
- 替换四处重复词表，禁止各 fetcher 自写：
  - DetailGate.stageOfPrompt → OrderStage.stageOf
  - TbFetcher.detailRank → OrderStage.stageOf
  - AccountMerge.isActiveOrderPrompt → stageOf != null && stage != DONE
  - AccountMerge.fallbackFetchedParcel 的 when → OrderStage.stageOf
- StageMachine.IN_TRANSIT 增加 揽件|揽收（轨迹级）。

**测试：** OrderStageTest 表驱动，覆盖 30+ 真实文案；已揽件 必须 → IN_TRANSIT。

### Phase 2 · TbFetcher 管线重构（1.5–2 天）
按 PDD 的结构重写 fetch() 内部：

**2.1 候选统一化**
- candidates = orders.filter { OrderStage.stageOf(it.status) != null }，不再用 needsDetail 的模糊判断。
- history = orders.filter { isFinishedOrderPrompt || isBlockedOrderPrompt }（退休证据）。
- 保留 statusInfo.text 原始文案，不要只取 stage。

**2.2 每单兜底（核心）**
```kotlin
val parsed = runCatching { detail(cookie, accountId, order) }.getOrNull()
val item = parsed?.parcel ?: fallbackFetchedParcel(
    platform = Platform.TB,
    accountId = accountId,
    orderSn = order.id,
    prompt = order.status,
    goods = order.goods,
    at = now,
)
```
- parsed != null && parsed.stage == UNKNOWN：
  - OrderStage.stageOf(order.status) 有值 → 用 fallback 的阶段重写（保留 tracking/goods，stage 用列表的）；
  - 无值 → 保留 UNKNOWN，由 Phase 3 显示「信息待补」。
- allDetailsFailed 改为：candidates.isNotEmpty() && result.isEmpty()（只有所有单都没有任何可用信息才整批失败）。

**2.3 未进详情的候选也要兜底**
- candidates.drop(MAX_DETAILS) 不直接丢，逐个 fallbackFetchedParcel 生成「轨迹待补」条目，计入 result。
- 或将 MAX_DETAILS 改为动态：min(16, candidates.size) + 90 秒总预算，超时后剩余候选全部走兜底。

**2.4 列表翻页**
- listOrders 从只取 page=1 改为循环 page=1..N（默认最多 5 页），每页合并，直到 orderListEnded 或空页。

**2.5 跨源校验**
- 对每个 order：isFinishedOrderPrompt/Blocked 不生成活跃条目；isActiveOrderPrompt 才生成。
- seen = 所有列表订单号；只有 page.ended 时提交，保持现有退休保护。

**2.6 异常语义**
- catch 里遇到 CancellationException 必须 rethrow；其余按单条失败处理（见代码评审 P0-5）。
- 列表接口失败仍整批 Failure（与 PDD 一致）；详情失败不整批失败。

**2.7（必做，P0）第二数据源（决定能否真正对齐 PDD）**
- 当 mtop 列表为空/结构不可识别时，用 WebView 加载淘宝 H5 订单页 + 同样的 capture 钩子，作为最后兜底。
- 这一条不是可选项：PDD 是「order_list + express/track/status」双源，淘宝只有 mtop 单源。不做第二源，mtop 一改版淘宝仍然整条静默。
- 同时增加覆盖度自检：列表返回 0 单但库里有活跃账号件 → 不退休、不置成功，账号状态报「接口可能改版」。

### Phase 3 · 首页可见性兜底（0.5 天）
- ShowCodeRoot.realInTransit 增加：(it.stage == Stage.UNKNOWN && (!it.trackingNo.isNullOrBlank() || !it.orderSn.isNullOrBlank()))。
- HomeScreen.inTransitStatus：UNKNOWN → 「状态未知 · 轨迹待补，下次同步会补全」；AT_STATION 空点已有文案保留。
- 说明：这一步让「即使解析失败也绝不消失」，是 I4 的兜底，不替代 Phase 2。

### Phase 4 · 测试（1 天）
1. OrderStageTest（30+ 状态词）。
2. TbFallbackTest：
   - 列表状态 已揽件 + detail null → 产出 IN_TRANSIT fallback；
   - 运输中 + detail null → IN_TRANSIT；
   - 待取件 + detail null → AT_STATION fallback（point 空 → 信息待补）；
   - 10 个候选超过 MAX_DETAILS → 结果仍 10 条。
3. 语料：新增 app/src/test/resources/traces/tb-9021.txt（只有「已揽件」）、tb-9022.txt（「运输中」）、tb-9023.txt（空详情 + 列表已发货）。
4. 扩充 AccountMergeTest 的 fallback 断言。
5. ParseBaseline/TraceCorpusAuditTest 跑新语料。

### Phase 5 · 验收（0.5 天）
真机三条场景逐条验：
- [ ] 淘宝订单状态「已揽件」 → 首页在途出现
- [ ] 「运输中」 → 出现
- [ ] 「待取件」（有码）→ 待取卡出现，点进现场模式
- [ ] 把详情接口临时打挂（或飞行模式后重试）→ 仍出现「轨迹待补」
- [ ] 淘宝账号同步结果：新增/更新数与列表订单数一致
- [ ] PDD/JD 回归不变；visual-audit/migration-check/单测全绿

### Phase 6 · 发布（0.5 天）
- versionCode 9 / versionName 0.4.4；changelog 中英；
- ./gradlew :app:testDebugUnitTest :app:assembleRelease；
- 发布说明注明「淘宝在途保底显示」。

---

## 4. 文件清单（预计改动）

| 文件 | 动作 | 说明 |
|---|---|---|
| app/src/main/java/dev/showcode/core/account/OrderStage.kt | 新增 | 全平台订单状态→Stage 唯一表 |
| core/account/StageMachine.kt | 改 | IN_TRANSIT 补 揽件/揽收 |
| core/account/AccountMerge.kt | 改 | isActiveOrderPrompt/fallbackFetchedParcel 委托 OrderStage |
| core/account/DetailGate.kt | 改 | stageOfPrompt 委托 OrderStage |
| data/fetch/TbFetcher.kt | 重写核心 | 兜底、翻页、动态名额、日志、Cancellation |
| ui/ShowCodeRoot.kt | 改 | 在途过滤包含 UNKNOWN + 身份 |
| ui/home/HomeScreen.kt | 改 | UNKNOWN 文案 |
| core/account/AccountMergeTest.kt 等 | 增 | fallback / OrderStage / 语料测试 |

---

## 5. 风险与取舍

1. **如果 Phase 0 证明 mtop 列表本身拿不到单**：Phase 2.2 的兜底无效，必须做 2.7（WebView 第二源）或修复 mtop 签名/翻页。先诊断再决定是否投入 2.7。
2. **fallback 会显示「轨迹待补」**：详情长期坏时首页会出现多条待补，这是「诚实」而非「好看」；但比静默丢件正确。可在设置页提供「同步诊断」入口解释。
3. **详情耗时**：动态上限 + 预算保证不会无限拉长；未抓的走 fallback，显示优先于完整。
4. **状态词「已揽件」的判定**：属于「快递员已揽收」，归 IN_TRANSIT；不要误判为 AT_STATION（无取件点）。
5. **不改变已取/退休语义**：fallback 只在 isActiveOrderPrompt 范围内生成，历史单仍只用于退休。

---

## 6. 验收标准（Definition of Done）

- [ ] OrderStageTest 覆盖 30+ 状态，全绿。
- [ ] TbFallbackTest 覆盖详情失败 3 种形态，全部产出条目。
- [ ] 淘宝账号真机同步：列表 N 单 → 首页在途/待取合计 N 条（无静默丢失）。
- [ ] 单条详情异常不影响同批其他单。
- [ ] Stage.UNKNOWN 有身份件在首页显示「信息待补」。
- [ ] CI 全绿（单测 + visual-audit + migration-check）。
- [ ] 发布 0.4.4 包，changelog 说明。

---

## 7. 建议排期

| 阶段 | 工期 | 可并行 |
|---|---|---|
| Phase 0 诊断 | 0.5d | — |
| Phase 1 词表 | 0.5d | 与 Phase 0 后半并行 |
| Phase 2 TbFetcher | 1.5–2d | — |
| Phase 3 可见性 | 0.5d | 与 Phase 2 并行 |
| Phase 4 测试 | 1d | 依赖 1/2 |
| Phase 5/6 验收发布 | 1d | — |
| **合计** | **约 5 人日** | |

> 若只要「止血版」（1 小时内可上）：Phase 1 的 已揽件/揽收/待收货 词 + Phase 2.2 的 TB 兜底 + Phase 3 的 UNKNOWN 显示，三处即可解决当前症状；完整计划用于保证不再复发。

---

## 8. 与 PDD 的「一样健壮」判定（重要）

| 能力 | 只做 Phase 0–6（2.7 不做） | 做完 Phase 2.7 + 覆盖度自检 |
|---|---|---|
| 有单必现（详情失败也显示） | ✅ | ✅ |
| 单条失败隔离 | ✅ | ✅ |
| 状态词全覆盖 | ✅（需真机语料校准） | ✅ |
| 双独立数据源 | ❌ 仍单源 mtop | ✅（mtop + WebView） |
| mtop 改版时整条静默 | ❌ 会 | ✅ 第二源接住 |
| 列表漏单/翻页漏单 | ❌ 可能 | ✅ 两源并集 |
| 长期可维护性 | 中（依赖一个私有接口） | 与 PDD 同级（依赖两个渠道，且都可见） |

**结论：只做当前 2.7 之前的内容，不能算「和 PDD 一样」——能解决你看到的两张图，但淘宝仍是单点故障；把 2.7 升级为必做后，才能说在架构健壮性上与 PDD 同级。** 另外两者都是非官方链路，没有绝对「不会坏」，只承诺「坏了不会静默、用户能看到、下一版能修」。
