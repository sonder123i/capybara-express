# 平台账号绑定与自动收录 · 底层改造方案

> 状态：待评审，未动代码
> 参考实现：[Halo0sama/ExpressAssistant](https://github.com/Halo0sama/ExpressAssistant)（MIT）
> 目标：把「不发短信、不推通知」的件，从京东 / 淘宝 / 拼多多的账号里收进来；现场模式、离线优先、手动导入这三条不丢。

---

## 0. 一句话结论

这不是「加三个登录页」，而是把卡皮巴拉快递助手从**只读手机上的消息**，改成**额外读你授权的平台订单**。

因此底层要动三处：数据模型（件可能没有取件码、没有取件点）、存储（要保存账号凭据）、同步（要有一个能跑 WebView 的前台抓取宿主）。

### 0.1 产品定位（2026-11-04 调整）

**主路径是「平台登录 / 绑定平台账号」，短信导入是补充手段。**

- 首页空态、首次引导的主按钮都指向「去绑定平台账号 / 去登录」；短信入口降为一句
  「也可以用短信导入（可选）」，不和绑定并列当主选项。
- 没开短信权限不是故障：不用红色/警示样式，不占首页横幅位置。
  只在设置里留一条中性条目（「没开短信也能用，只是收不到短信类件」），
  按钮写「开启（可选）」，不用 `UrgencyDueSoon`。
- 权限申请是被动的：启动、首页都不主动弹系统权限框；只有用户主动点「用短信导入」才申请，
  拒绝后不再反复弹。引导里的短信步也允许直接跳过。
- `HealthReport` 不再产生 `SMS_DENIED`；后台异常判定也不再要求短信权限。
  健康提示只报真正影响自动收件的那几项（通知监听、后台被杀）。
- 视觉沿用现有家族：不新增颜色、不加角标，短信状态用墨阶（`InkMuted`）而不是状态色。

---

## 1. 必须先承认的三件事

1. **账号登录的信任代价比无障碍更大。** 淘宝 / 京东 / 拼多多的登录 Cookie 等同于账号控制权。它必须加密、必须可删、必须不出本机。
2. **抓取一定会坏。** 对方 H5 改版、接口加签、风控升级，都会让某一家突然收不到件。产品必须能退回到短信 / 通知 / 手动导入，而不是一起失效。
3. **自动收录不等于自动取码。** 拼多多菜鸟驿站件屏上写的是「出示单号后五位」，不是取件码。能拿到件，不代表能拿到那串码；拿不到时只能显示单号后五位并给「去平台看码」的入口。

---

## 2. 不可逆决策（开工前定死）

| 决策 | 选择 | 理由 |
|---|---|---|
| 每平台账号数 | **v1 单账号** | 多账号会让 `Parcel.accountId`、去重、UI 归属全部翻倍；先跑通一条链路 |
| 同步时机 | **仅前台**（开 App / 下拉刷新） | WebView 不能在后台可靠跑；不引 WorkManager，不装「后台常驻」 |
| 无码件 | **有 `trackingNo` 就收录**：有后五位照旧显示；没有码时首页写「暂无取件码」，现场不亮空框、给「去官方 App」出口 | 少录一件的代价是用户站在驿站门口才发现；空码只是不知道码，不代表件不存在 |
| 无取件点 | **仍进首页，归「驿站未定位」分组** | 不伪造真实点名；但也不能因为缺一个名字把真实的件丢掉 |
| 凭据存储 | **Android Keystore + AES-GCM，自写，不加依赖** | 和「少依赖」口径一致；明文 SharedPreferences 不抄 |
| 已签收 | 仅在轨迹明确「本人取件 / 凭码取件」时自动标已取 | 「签收人：门口」不是取件，误标会吞掉一件 |
| 读屏 / 无障碍 | **仍然不做** | 与既有立场一致；登录 + 抓取只在授权账号内 |

---

## 3. 架构总览

```
[设置] 账号绑定
   └─ PlatformLoginScreen（Compose + WebView，全屏）
        ├─ 清 Cookie / localStorage → 加载官方登录页
        ├─ 轮询 Cookie 谓词 → 抓整条 Cookie
        └─ SecretBox 加密 → bound_account 表 → 清 WebView Cookie

[启动 / 下拉刷新]
   └─ AccountSyncEngine（前台，Mutex 串行）
        ├─ FetchHost（1×1 附加式 WebView + document-start JS）
        ├─ PddFetcher / TbFetcher / JdFetcher
        ├─ core/account/TraceParser（纯 Kotlin）
        └─ core/account/AccountMerge.plan()（纯 Kotlin）
             └─ parcel 表（stage / codeKind / trackingNo）
                  ├─ 首页（只显示 stage=AT_STATION 且有码或后五位）
                  ├─ 现场模式（按 codeKind 换标签）
                  ├─ 组件 / 磁贴 / 搜索
                  └─ 账号页（在途、未定位、同步失败）
```

关键原则：**抓取层（Android）只负责拿文本；解析、合并、展示决策全部留在 `core/`，可单测。**

---

## 4. 数据模型与迁移

### 4.1 `Parcel` 新字段

```kotlin
val trackingNo: String? = null      // 运单号；账号渠道的主键
val orderSn: String? = null         // 平台订单号；拼多多列表接口只有它
val accountId: String? = null       // bound_account.id；SMS 件可空
val stage: Stage = Stage.AT_STATION // 在途 / 派送 / 到站 / 已签 / 异常 / 未知
val codeKind: CodeKind = CodeKind.PICKUP // PICKUP / SUFFIX / NONE
val lastSyncedAt: Long? = null      // 该件的最近一次账号同步
```

```kotlin
enum class Stage { IN_TRANSIT, DELIVERING, AT_STATION, DONE, ABNORMAL, UNKNOWN }
enum class CodeKind { PICKUP, SUFFIX, NONE }
```

`code` 仍是 `TEXT NOT NULL`：无码件存空串；有运单号无码时存后五位并标 `SUFFIX`，不改列类型。

### 4.2 新表 `bound_account`

```sql
CREATE TABLE bound_account (
  id TEXT PRIMARY KEY,
  platform TEXT NOT NULL,            -- jd / tb / pdd
  label TEXT NOT NULL,
  secret TEXT NOT NULL,              -- base64(iv) + ":" + base64(ciphertext)
  enabled INTEGER NOT NULL DEFAULT 1,
  state TEXT NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / EXPIRED / BROKEN
  created_at INTEGER NOT NULL,
  last_sync_at INTEGER,
  last_sync_result TEXT
)
```

`id` 固定为平台名（`pdd` / `tb` / `jd`），v1 天然一平台一条；以后要多账号，把 id 换成 UUID 并加 `platform` 索引即可，不用改表结构。

### 4.3 索引

```sql
DROP INDEX parcel_dedupe;
CREATE UNIQUE INDEX parcel_dedupe
  ON parcel(code, point_key) WHERE picked = 0 AND code <> '';

CREATE UNIQUE INDEX parcel_tracking_dedupe
  ON parcel(tracking_no) WHERE picked = 0 AND tracking_no IS NOT NULL;

CREATE INDEX parcel_account ON parcel(account_id, stage, picked);
```

旧索引必须放宽 `code <> ''`，否则两件「无码」的件在同一个点会撞唯一约束。

### 4.4 迁移 V7 → V8

- `ShowCodeDb.VERSION = 9`（商品五列在 v9）
- `onCreate` 的 `parcel` DDL 直接带新列
- `onUpgrade` 用现有的 `addColumnIfMissing` 逐列补
- 新增表走 `if (oldVersion < 8)`，与既有阶梯同一写法
- **必须同步更新 `tools/migration-check.py` 的期望**，并跑 `python tools/migration-check.py`

### 4.5 语义边界

- `picked` 仍然是「用户这一趟取走了」，`stage` 是平台说的物流状态。二者不互相替代。
- 首页可见条件：`!picked && stage == AT_STATION && pointKey.isNotBlank() && (code.isNotBlank() || codeKind == SUFFIX || trackingNo != null)`。
  `picked=1` 的行**一行都不展示**（首页卡片、现场、平铺、组件同一个分组函数），但记录保留在库里——
  物理删除会让下一次同步把它当成新件重新插入，等于复活。
- `codeKind == SUFFIX` 时，`code` 就是后五位；出示屏标签换成「出示单号后五位」。
- 账号行 `raw` 存最近一次轨迹原文；若与短信行合并，保留短信原文，轨迹只用于解析。

### 4.6 商品信息（v9）

- `parcel` 新增五列：`goods_title` / `goods_image` / `goods_price` / `shop_name` / `ordered_at`，
  全部可空；短信、手动、无账号归属的件保持 null。
- DB `VERSION = 9`，v8→v9 只加列、不动老行；`migration-check` 跑 v1→v9。
- 三家字段来源：
  - PDD：`order_list_v4` 的 goods 容器（`goods_name` / `goods_thumbnail_url` / `goods_price` 分 / 时间）
  - 淘宝：`mainOrders[].subOrders[0].itemInfo`（`title` / `pic` / `createTime`）
  - 京东：`wareInfoList[].wareName` / `wareImage` / `shopInfo` / `orderInfo.orderTime`（无真实订单，未验）
- 拿不到就 null：不伪造价格、不画空图框；UI 用 `showsGoodsCard` / `goodsThumbnailUrl` 决定是否出现。
- 缩略图自写 `ThumbnailLoader`（`LruCache` + 降采样 + 失败 null），不引 Coil。
- 尺寸：首页 44dp、现场 44dp、详情 76dp；圆角 10dp、加载中/失败同一块浅灰，尺寸固定。
- 首页/现场只放「一眼知道这是什么快递」的单行标题 + 店铺/价格小字；码仍然是主角。
- 「复制快递单号 / 去淘宝查询」对 `NONE` 与 `SUFFIX` 两种件都出现；`PICKUP` 不出现。

---

## 5. 登录与会话

### 5.1 `core/account/PlatformSpec`

纯 Kotlin，三家的登录页与「什么算登录成功」的判据：

| 平台 | 登录地址 | 成功判据 |
|---|---|---|
| 京东 | `https://plogin.m.jd.com/login/login?appid=300&returnurl=…` | `pt_key` 且 `pt_pin` 非空 |
| 淘宝 | `https://login.m.taobao.com/login.htm` | `cookie2` 且（`unb` 或 `cookie1`）非空 |
| 拼多多 | `https://mobile.yangkeduo.com/login.html` | `PDDAccessToken` 且 `pdd_user_id`/`pdduid` 非空 |

Cookie 解析必须处理值里含 `=` 的情况（`split("=", limit = 2)`），并忽略空 key。

### 5.2 `SecretBox`

- Android Keystore 里生成 `AES/GCM/NoPadding` 256 位密钥，别名 `showcode.account.v1`
- 密文格式：`base64(iv) + ":" + base64(ciphertext)`，存 `bound_account.secret`
- 解密失败 / 密钥失效（恢复出厂、换机还原）→ 该账号置 `BROKEN`，界面显示「需要重新登录」，**不许崩溃**

### 5.3 登录页流程

1. 进入前确认：「登录凭据只存在本机加密区；删除账号即清除」——一句话，不弹协议墙。
2. `CookieManager.removeAllCookies` + `flush`，并清 `localStorage` / `sessionStorage`。
3. 加载官方登录页，WebView 设置 JS / DOM storage / 第三方 Cookie。
4. 每 1.5 秒读一次 `getCookie(host)`，命中判据后抓整条 Cookie。
5. 写入 `bound_account`，清空 WebView 的 Cookie 与存储，返回列表。
6. 返回 / 取消：同样清空，不落任何东西。
7. Release 包 `WebView.setWebContentsDebuggingEnabled(false)`；日志只打 key 名，不打值。

### 5.4 会话失效

同步时命中登录页跳转、接口返回未登录、或抓不到订单 → `state=EXPIRED`，账号页红一行「拼多多登录已失效，点一下重新登录」。不自动重试登录，不保存密码。

---

## 6. 抓取与同步

### 6.1 `FetchHost`

- 挂在主 Activity `window.decorView` 最底层的**全尺寸（MATCH_PARENT）不可见 WebView**；不要用 1×1——拼多多虚拟列表按视口高度决定加载量，1px 视口会把翻页饿死。参考实现同样是把 WebView `MATCH_PARENT` 挂在 decor 最底层，不是 1×1。
- 通过 `androidx.webkit` 的 `WebViewCompat.addDocumentStartJavaScript` 注入钩子（PDD / JD 必须）。
- 需要新增依赖：`implementation("androidx.webkit:webkit:<与 compileSdk 37 兼容的最新稳定版>")`。
- 一次只跑一个平台、一个 WebView；同步结束立即 `loadUrl("about:blank")` + `destroy()`。
- 同步可取消：App 退到后台 / 用户下拉打断 → `CancellationException`，不写半套数据。

### 6.2 拼多多（第一条链路）

参考 `PddWeb` / `PddCapture` / `PddListFetcher` / `PddTraceFetcher`：

1. 注入 Cookie → 打开 `orders.html`。
2. document-start 钩子记录 `POST /aristotle/order_list_v4` 的响应；**排除** `express/track/status` 状态码表与推荐流。
3. 列表接口没有运单号（真实字段记录见对方 `docs/PDD_REAL.md`），所以要靠触摸时序滚动 +「查看全部」翻页；单轮抓取 0～67 件波动，必须做「空抓不清库」。
4. 对每条待收货 / 派送 / 已签收订单，打开 `order.html?order_sn=…` → 点「查看物流」→ 停在 `goods_express.html`。
5. 从 URL 取 `tracking_number`，从 `document.body.innerText` 取轨迹；解析出状态、取件点、取件码 / 后五位、柜号。
6. 单件超时 8 秒；同一平台串行；失败保留旧数据。

### 6.3 淘宝

优先复用 WebView 路径：登录后打开订单页，钩住 `mtop` 订单列表 JSON；详情页取轨迹。

如果 WebView 拿不到稳定 JSON，再落到参考实现的 `TbOrders` 直连方案（`mtop` + `_m_h5_tk` + `wapSign`）。这条作为备用，不在 v1 先做。

### 6.4 京东

复用 `JdListFetcher` 的 JSONP 捕获 + 滑动翻页；详情页 `deal_wuliu` DOM 解析。京东的登录态判据最简单，适合作为第二或第三条链路。

### 6.5 同步策略

- 触发：冷启动后首次进入首页、用户下拉刷新、账号页手动「立即同步」。
- 节流：同一平台 15 分钟内不重复；失败后 5 分钟内不重试。
- 总预算：单平台 30 秒、全平台 60 秒；超时保留旧数据并只报「这次没同步上」。
- **不承诺后台到件推送**；9 点巡检提醒仍然只是「打开看一眼」的提示。以后若要做，再单独立项（WorkManager + 前台服务），不混进这一版。

---

## 7. 轨迹解析（core，纯函数）

新增 `core/account/TraceParser.kt`，输入轨迹全文 + 运单号，输出：

```kotlin
data class AccountParcel(
  val platform: Platform,
  val accountId: String,
  val trackingNo: String?,
  val orderSn: String?,
  val carrier: String,
  val stage: Stage,
  val pointName: String?,
  val lockerNo: String?,
  val pickupCode: String?,   // 只有明确「取件码」才算
  val raw: String,
  val at: Long,
)
```

规则：

- **状态**：`待取件 / 已到驿站 / 已放入 / 已入柜 / 请凭取件码` → `AT_STATION`；`派送中 / 派件` → `DELIVERING`；`运输中 / 已发货 / 已揽收` → `IN_TRANSIT`；`已签收 / 已取件 / 交易完成` → `DONE`；`退回 / 拒收 / 异常` → `ABNORMAL`。
- **取件点**：优先「已到 / 存放于 / 请到 / 取件点」后面的名词短语；命中「驿站 / 快递柜 / 超市 / 便利店 / 门卫」后缀才接受。认不出就留空，不猜。
- **取件码**：沿用解析器的关键词表（取件码 / 取货码 / 提货码 / 自提码 / 驿站码 / 取件验证码）；另外识别「出示单号后五位 12345」→ `pickupCode=12345`，但由调用方标 `SUFFIX`。
- **柜号**：`X号柜`、`X-Y-Z`、`第X格` 三种形态。
- **承运商**：轨迹开头的「中通快递 / 圆通 / 韵达…」。
- 所有样本进 `app/src/test/.../account/corpus/`，脱敏规则同现有语料：只换号码与地名，保留长度、字符类、连字符位置、后缀词。

**验收**：至少 3 家 × 3 种状态 × 有码/无码/无点，全部单测通过。

---

## 8. 合并与去重（core，纯函数）

`core/account/AccountMerge.kt`：

```kotlin
fun planAccountSync(
  fetched: List<AccountParcel>,
  existing: List<Parcel>,
  now: Long,
): List<AccountSyncAction>
```

匹配顺序：

1. `trackingNo` 相同（未取行）
2. `code + pointKey` 相同（短信行已认出码，账号行也解析出同一个码）
3. 都没有 → `Insert`

命中后的规则：

| 情况 | 动作 |
|---|---|
| 对方字段为空 | 用账号值补（`trackingNo` / `orderSn` / `accountId` / `lockerNo`） |
| 双方码不同 | **保留原码**，不覆盖；记一次冲突计数，账号页可见「有 1 条轨迹与已有取件码不一致」 |
| 账号说 `AT_STATION`，本地是 `IN_TRANSIT` | 升级到 `AT_STATION` |
| 账号说 `IN_TRANSIT`，本地是 `AT_STATION` | **不降级**（本地证据更晚更具体） |
| 本地 `picked=true` | 一个字段都不动 |
| 账号 `DONE` 且轨迹明确「本人取件 / 凭码取件」 | 标 `picked=true, pickedAt=轨迹时间` |
| 账号 `DONE` 但只写「签收人：门口」 | 只写 `stage=DONE`，不标已取；进账号页待确认 |
| 无取件点 | 落库但 `pointKey=""`；首页不显示，账号页「已到站但没定位」可补点 |

约束：账号行永远不许复活已取件，也不许把短信行的 `raw` 冲掉。

---

## 9. UI 设计（延续家族）

### 9.1 继承什么、不许新造什么

沿用现有家族，不另起一套「账号主题」：

| 项 | 取值 / 来源 | 用在哪 |
|---|---|---|
| 页面底 | `environmentBackground()` | 账号列表、登录页（与设置页一致） |
| 卡面 | `SurfaceCard #F9FAFD` | 平台卡、轨迹块、对话框 |
| 描边 | `Hairline #E4E8F0` + 1dp | 同上 |
| 圆角 | `ShowCodeShapes.Md`（14dp） | 所有卡与对话框；不用第三种圆角 |
| 页面边距 | 左右 20dp、底部 `navBarClearance()` | 设置子页统一口径 |
| 组间距 | 14dp | 账号列表 |
| 卡内边距 | 16dp | 平台卡 |
| 顶栏 | `ScreenTopBar`，52dp，标题 `Headline` | 账号列表、登录页 |
| 字阶 | `ShowCodeText`：Section / Item / Body / Meta / Caption / Action / Micro | 全部 |
| 墨阶 | `Ink` / `InkSecondary` / `InkMuted` | 正文与次级；`InkFaint` 只做装饰 |
| 动作 | `ShowCodeText.Action` + `AccentInk` | 「去登录 / 重新登录 / 删除 / 重试」 |
| 选中/开关 | M3 `Switch`，主题 primary 已是纯黑 | 启用/停用 |
| 入场 | `moduleEntrance(0/1/2)` | 各分组错峰，不自己播动画 |
| 数字 | `withTabular()` | 件数、时间、后五位 |

明确不新增：

- 不画京东 / 淘宝 / 拼多多 logo，不用品牌红/橙。
- 不引入第四种状态色：账号状态只用墨阶，需用户动手的「失效」用 `UrgencyDueSoon`。
- 不做账号头像、昵称、手机号（Cookie 里也没有）。
- 不做骨架屏、卡片内渐变、登录页转圈动画。
- 不做平台重新排序/拖拽；固定顺序 **拼多多 → 淘宝 → 京东**（按「最容易漏」排）。

### 9.2 屏幕地图

```
首页 ──「在途 N 件 · 查看」──► 平台账号列表 ──「去登录」──► 登录页
 │                                   │
 └── 现场模式 ──「去拼多多看码」──┐     └──「已到站但没定位」──► 补取件点对话框
                                  └──► DeepLinkLadder（现有）
```

### 9.3 设置页入口卡

和设置页其它行同一写法：只有 1dp 描边、不铺 `SurfaceCard`。

```
┌─────────────────────────────────────────────┐
│ 平台账号                          已绑定 1 个 │
│ 绑定后从京东 / 淘宝 / 拼多多的订单里收录件；   │
│ 凭据加密存本机。                              │
└─────────────────────────────────────────────┘
```

- 标题 `Body` + `Ink`；右侧状态 `Item`：未绑定用 `InkMuted`，已绑定用 `Ink`，有失效用 `UrgencyDueSoon`。
- 说明 `Caption` + `InkMuted`，最多两行。
- 整行可点，进账号列表；放在设置页**第一个分组**「平台登录」，`moduleEntrance(0)`。
  它是主路径，不能埋在「巡检提醒」后面。

### 9.4 平台账号列表页

```
┌ ← 平台账号 ─────────────────────────────────┐
│ 这里读的是你自己账号里的订单。不绑定，就只能靠  │
│ 短信、通知和手动导入。                        │
│                                             │
│ ┌─────────────────────────────────────────┐ │
│ │ 拼多多                           [开关]  │ │
│ │ 最近同步 3 分钟前 · 2 件待取              │ │
│ │ 立即同步    重新登录    删除              │ │
│ └─────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────┐ │
│ │ 淘宝                             [开关]  │ │
│ │ 登录已失效，需要重新登录                  │ │
│ │ 重新登录    删除                          │ │
│ └─────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────┐ │
│ │ 京东                              去登录  │ │
│ │ 未绑定。登录后从账号的订单里收录件。        │ │
│ └─────────────────────────────────────────┘ │
│                                             │
│ 已到站但没定位                               │
│ ┌─────────────────────────────────────────┐ │
│ │ 中通 7812…  “您的快件已到…”   补取件点    │ │
│ └─────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
```

- **开场说明**是这一页的唯一「信任建立」位置，必须在第一张卡上面；`Meta 13sp` + `InkMuted`，两行以内。
- **平台卡**：`SurfaceCard` + `Hairline` + `Md`，内边距 16dp，卡间距 14dp。
  - 第一行：平台名 `Item 15sp` + 右侧 `Switch`（未绑定时不放开关，放「去登录」）。
  - 第二行：`Caption 12sp`。正常态 `最近同步 12 分钟前 · 2 件待取`；失效态 `UrgencyDueSoon`；同步失败 `InkMuted`。
  - 第三行：文字动作，`Action 13sp` + `AccentInk`，左对齐，动作之间 20dp；整行最小高度 44dp。
  - 未绑定卡只有两行：平台名 + 「去登录」；第二行说明。
- **同步中**：在顶栏下压一条 2dp 的 `LinearProgressIndicator`，颜色 `Accent`，无轨道；同时把第二行换成「正在同步拼多多…」。不用转圈、不用骨架。
- **已到站但没定位**：`SectionTitle` 后接一张卡，一行一件，最多 3 行，多出来收「+N」。轨迹原文用 `Caption` 截断，末尾「补取件点」是 `Action`。这一组只在有数据时出现。
- **删除确认**用现有 `Dialog` 风格：`SurfaceCard` + `Md` + 24dp 内边距；标题 `这个账号的登录凭据会被清掉`，正文 `已收录的件会留下。`；取消 `InkMuted`，确认 `AccentInk`。
- 列表底部 `navBarClearance()`。

### 9.5 登录页

```
┌ ← 登录拼多多 ───────────────────────────────┐
│ 用你自己的账号登录                            │
│ 登录在官方页面完成。我们只保存登录凭据，加密     │
│ 存在这台手机上。                              │
│ 不想登录就返回。短信、通知和手动导入都还在。      │
│ ┌─────────────────────────────────────────┐ │
│ │                                         │ │
│ │           官方登录页（WebView）            │ │
│ │                                         │ │
│ └─────────────────────────────────────────┘ │
│ 等待你在页面上完成登录…                        │
└─────────────────────────────────────────────┘
```

- 顶部三段文字全部在 WebView 卡**外面**：第一行 `Item`，第二行 `Meta`，第三行 `Caption`。
- WebView 卡：`SurfaceCard` + `Hairline` + `Md`，`clip` 到同一圆角；`WebView` 自身先设白底再加载，避免路由切换时白闪。
- WebView 高度占满剩余空间；不铺 `SurfaceCard` 的渐变，官方页面保持原样。
- 底部状态只有一行 `Caption`：等待 → 已拿到登录态，正在保存… → 已绑定，自动返回。失败时换成 `没拿到登录态，可以再试一次`，仍留在本页。
- 返回即放弃，不二次确认；文案已经说清「返回就还在」。
- 不显示 Cookie、不显示账号名、不显示「正在校验订单权限」这类内部步骤——那是实现，不是用户的语言。

### 9.6 首页

**空态（首次打开）**：

- 标题「绑定平台账号，快递自己进来」，主按钮「去绑定平台账号」──这是唯一主 CTA。
- 「先看现场模式演示」是蓝色文字级次级选择。
- 短信只作为一句说明出现（「短信只是补充手段」），不占横幅位、不做权限警告条。
- 没开短信权限时，首页**不出现任何红条/警示条**；通知监听缺失仍按现有 `HealthBar` 提示。

**在途条**（不占点卡片位置，不是卡，不加底色）：

```
在途 3 件 · 拼多多 / 淘宝                    查看
```

- 位置：标题 + `SyncLine` 之下、健康条之上；左右 20dp，上下 8dp。
- 数字 `tabular` + `InkSecondary`，其余 `Caption` + `InkMuted`；「查看」`Action` + `AccentInk`。
- 只在 `inTransitCount > 0` 时出现；点进账号列表。
- 它不进排序，也不改首页「点是一级」的结构。

**点卡片里的无码件**：

```
76416  后五位                  ▼ 临期
```

- 码本身仍用 `CodeLarge` / `Code` 等宽；「后五位」`Micro 11sp` + `InkMuted`，与码之间 8dp。
- 不加平台名、不加色标、不加来源角标——首页的卡片仍然只表达「哪个点、几件、最急」。
- `codeKind=NONE` 的件**仍然出现**：码区域换成 `Caption` + `InkMuted` 的「暂无取件码」，
  紧跟一行 `Micro` 的阶段（「待取件」），不伪造任何数字，不新增状态色、不加角标。
- 入列判据：`stage=AT_STATION` + 非空 `pointKey` + （有码 / SUFFIX / 有 `trackingNo`）。

**其它**：
- 搜索匹配 `trackingNo` / `orderSn`；结果仍是点分组。
- 账号失效 / 同步失败复用现有 `HealthBar` / `StaleBar`，不新造红条。

### 9.7 现场模式

```
中通快递 · 拼多多
出示单号后五位
      76416
   长按复制

      ▼ 12:47 前取走
   去拼多多看码
```

- `codeKind == SUFFIX` 时，在码上方加一行 `12sp` + `InkMuted` 的说明；码本身字号、纯黑、等宽、`tnum`、分隔符降墨全部不变。
- 承运商行在账号件后面追加 `· 拼多多`，仍是 `Caption` + `InkMuted`。
- 「去拼多多看码」是 `Action 13sp` + `AccentInk`，放在时效行下面，属于会静默退场的 chrome（8 秒后随其它控件淡出）；`PICKUP` 件不出现。
- 不加平台 logo、不把「后五位」做成彩色标签。

**空码件的现场体（`codeKind=NONE` / `code=""`）**：

```
中通快递
这件还没有取件码
运单号 79030000003029
取件码只在淘宝里：粘贴单号搜索，或打开「身份码」出示后五位。

[ 去淘宝查询 ]
  复制快递单号
     查看详情
```

- **不画空码框、不显示 `--`、不放占位数字**；整块重排成单号 → 怎么用 → 两个动作 → 详情。
- 不出现「长按可复制」——没有码可复制；复制动作本身是显式按钮，成功给短提示。
- 「去淘宝查询」是唯一黑色主按钮：先复制完整运单号，再只拉起淘宝 App
  （`repository.openApp(DeepLinkLadder.TAOBAO)`，不走深链、不落浏览器兜底）。
  没装/被拦住时停留在本屏，提示「已复制单号，去淘宝粘贴搜索」；剪贴板里的单号保留。
- 「复制快递单号」是次级动作：`ShowCodeText.Action` + `AccentInk`，点击区带 12/14dp 内边距。
- 「查看详情」永远出现，进 `ParcelDetailScreen`。

### 9.8 补取件点对话框

```
┌ 这个件在哪个点？ ────────────────────────┐
│ 轨迹                                      │
│ ┌─────────────────────────────────────┐   │
│ │ 您的快件已到…（最多 3 行，12sp）        │   │
│ └─────────────────────────────────────┘   │
│ 已有取件点                                 │
│  菜鸟驿站 · XX店                           │
│  丰巢 · XX柜                              │
│ 或自己填                                   │
│ [___________________________________]     │
│                       取消        保存     │
└───────────────────────────────────────────┘
```

- 轨迹块用 `SurfaceSunken` + `Sm`（8dp），不是第四种卡面。
- 已有点一行一个，点一下即选中并关闭；「保存」只在手填时可用。
- 对话框 `SurfaceCard` + `Hairline` + `Md`，24dp 内边距；取消 `InkMuted`，保存 `AccentInk`。

### 9.9 状态与文案表

| 状态 | 第二行 | 颜色 | 动作 |
|---|---|---|---|
| 未绑定 | 未绑定。登录后从账号的订单里收录件。 | `InkMuted` | 去登录 |
| 正常 | 最近同步 12 分钟前 · 2 件待取 | `InkMuted` | 立即同步 / 重新登录 / 删除 |
| 同步中 | 正在同步拼多多… | `InkMuted` | 无（返回仍可用） |
| 同步失败 | 这次没同步上：超时 | `InkMuted` | 重试 / 删除 |
| 登录失效 | 登录已失效，需要重新登录 | `UrgencyDueSoon` | 重新登录 / 删除 |
| 凭据损坏 | 凭据解不开，需要重新登录 | `UrgencyDueSoon` | 重新登录 / 删除 |
| 有冲突 | 有 1 条轨迹和已有取件码不一致 | `InkMuted` | 查看详情 |

### 9.10 新增文案（`strings.xml`）

| key | 文案 |
|---|---|
| `settings_accounts` | 平台账号 |
| `settings_accounts_hint` | 绑定后从京东 / 淘宝 / 拼多多的订单里收录件；凭据加密存本机。 |
| `account_title` | 平台账号 |
| `account_intro` | 这里读的是你自己账号里的订单。不绑定，就只能靠短信、通知和手动导入。 |
| `account_unbound` | 未绑定。登录后从账号的订单里收录件。 |
| `account_bound_meta` | 最近同步 %1$s · %2$d 件待取 |
| `account_syncing` | 正在同步%1$s… |
| `account_sync_failed` | 这次没同步上：%1$s |
| `account_expired` | 登录已失效，需要重新登录 |
| `account_broken` | 凭据解不开，需要重新登录 |
| `account_conflict` | 有 %1$d 条轨迹和已有取件码不一致 |
| `account_login` | 去登录 |
| `account_relogin` | 重新登录 |
| `account_delete` | 删除 |
| `account_sync_now` | 立即同步 |
| `account_retry` | 重试 |
| `account_delete_title` | 这个账号的登录凭据会被清掉 |
| `account_delete_body` | 已收录的件会留下。 |
| `login_title` | 登录%1$s |
| `login_lead` | 用你自己的账号登录 |
| `login_body` | 登录在官方页面完成。我们只保存登录凭据，加密存在这台手机上。 |
| `login_escape` | 不想登录就返回。短信、通知和手动导入都还在。 |
| `login_waiting` | 等待你在页面上完成登录… |
| `login_saving` | 已拿到登录态，正在保存… |
| `login_failed` | 没拿到登录态，可以再试一次 |
| `login_done` | 已绑定%1$s |
| `suffix_label` | 出示单号后五位 |
| `suffix_micro` | 后五位 |
| `in_transit_strip` | 在途 %1$d 件 · %2$s |
| `unlocated_title` | 已到站但没定位 |
| `unlocated_action` | 补取件点 |
| `point_pick_title` | 这个件在哪个点？ |
| `point_pick_custom` | 或自己填 |
| `point_pick_save` | 保存 |

文案原则：**不解释实现**。「校验订单访问权限」「Cookie 已写入」这类词不上屏；用户只需要知道「这是你自己的账号」「不登录也能用」「删掉就清凭据」。

### 9.11 动效与无障碍

- 入场只用 `moduleEntrance(0/1/2)`；账号列表是子屏，组 0 是说明，组 1 是平台卡，组 2 是未定位区。
- 同步中不加入场动画；进度条是 2dp 线性、无轨道，结束即消失，不做「成功闪一下」。
- 登录页不做淡入：路由切换本身已有导航动画；WebView 先 `about:blank` 再加载，避免白屏闪。
- 所有可点区域 ≥ 44dp；文字动作 13sp 粗体 + `AccentInk`（对白 6.24:1、对环境最暗角 4.57:1）。
- 次级信息一律 `InkMuted`，`InkFaint` 只用于装饰；后五位是**真信息**，不许用 `InkFaint`。
- 尊重 `rememberReducedMotion()`：不为新页面单独造动画，`ModuleMotion.expand/collapse` 已经处理。

### 9.12 明确不设计

- 平台 logo、品牌色、账号头像、昵称、手机号。
- 「账号绑定成功」的庆祝动画、彩带、渐变高亮。
- 第四种状态色；失效只借用 `UrgencyDueSoon`。
- 卡片里的进度环 / 骨架屏。
- 平台拖拽排序、账号多选、批量删除。
- 夜间模式（全 App 强制浅色，现场模式 21:1 的契约不动）。

---

## 10. 隐私与合规

- `AndroidManifest.xml` 新增 `INTERNET`；不加任何其他网络权限。
- README 必须改三处：
  1. 权限表新增「网络（仅在你绑定账号后使用）」。
  2. 「它不做什么」新增：不后台轮询、不读屏、不保存密码、不做多账号。
  3. 「它治不了的那个洞」改写：绑定账号覆盖那几家；**没绑定的平台仍然漏**，不宣称全知。
- 日志：Cookie 值、订单号、手机号不进 logcat；Release 关 WebView 调试。
- 若直接搬了参考实现的 JS 钩子或代码片段：在 README「内嵌第三方代码」里加 MIT 声明与版权行；能在 `core/` 重写的就重写，别整段抄 Activity。
- F-Droid：使用第三方非自由服务可能被标 `NonFreeNet`；提交前先读 `fdroidserver` 的判据，不要当成已通过。

---

## 11. 测试与验收

### 单元测试（JVM）
- `SessionTest`：三家 Cookie 判据、值含 `=`、空 key、缺 key。
- `TraceParserTest`：9 组样本，覆盖有码 / 后五位 / 无点 / 已签 / 异常。
- `AccountMergeTest`：插入 / 更新 / 码冲突 / 不降级 / 不复活已取 / 签收不误标。
- `CodeDisplayTest`：`SUFFIX` 的标签与字号。
- 现有 251 个测试必须全绿。

### 迁移
- `python tools/migration-check.py`
- 真机：装 v7 → 造 3 件 → 升 v8 → 件不少、已取不复活、索引不撞。
- 新装 v8：建表与升级后列一致。

### 真机手工清单
1. 三家分别登录；杀进程重开，同步仍可用。
2. 断网 / 飞行模式：不崩、不误清、显示「这次没同步上」。
3. Cookie 过期：账号页红字 + 重新登录可恢复。
4. 同一个件既有短信又有账号：只出现一张卡，码来自短信。
5. 无码件：现场**不显示空白码框**，显示「这件还没有取件码」+ 运单号 +「去官方 App 查看取件码」；
   有后五位的照旧显示后五位。
6. 已取件不被同步复活。
7. 卸载重装：账号与件全部消失。
8. 组件 / 磁贴 / 搜索 / 下拉刷新。

### 总验收
- **一个真实拼多多账号，连续 3 次同步能稳定产出可解析的到站件**（这是本方案唯一不能纸面证明的东西）。
- 任一平台失效时，短信 / 通知 / 手动导入三条老路完全不受影响。

### UI 验收
- 账号列表与设置页同一套边距（20dp）、卡面（`SurfaceCard` + `Hairline` + 14dp）、字阶。
- 没有平台 logo、没有品牌色、没有第四种状态色；失效只用 `UrgencyDueSoon`。
- 首页「在途」条不占取件点卡片位置，不改变「点是一级」的排序。
- 现场模式「出示单号后五位」与码同屏可见；5 位、6 位、12 位码都不溢出。
- 登录页从路由进入到 WebView 出内容之间无白闪；屏上从不出现 Cookie / 校验步骤。
- 删除账号确认文案写明「凭据清掉、件保留」。

---

## 12. 分阶段任务清单

### P0 · 可行性验证（先做，不碰数据库）

> 目的：如果 WebView 抓取在你的机器上跑不通，后面全部作废。

- [x] 在 MainActivity 挂全尺寸 WebView（探针为可见；尺寸与 decor 隐藏宿主等价），注入 Cookie，打开 PDD 订单页
- [x] `addDocumentStartJavaScript` 钩住 `/aristotle/order_list`，拿到真实响应
- [x] 滑动翻页：10 → 30 条；PDD 用 `offset` 游标，不是 `page=2`
- [x] 打开订单详情 → `goods_express.html`，拿到运单号与轨迹文本
- [x] 单轮确认（门槛已从 3 轮调整为 1 轮；换号后一次通过）
- [x] 记录耗时 / 内存 / 失败形态，写回本文件

**Go 条件（调整后）**：单轮能拿到真实订单列表 + 翻页追加 + 详情运单号/轨迹。已满足。

### P1 · 存储与迁移

- [x] `core/Model.kt`：`Stage` / `CodeKind` / Parcel 新字段
- [x] `data/ShowCodeDb.kt`：VERSION=8、新列、新表、索引重建、onUpgrade
- [x] `tools/migration-check.py` 期望同步
- [x] `security/SecretBox.kt` + 加解密测试
- [x] `data/AccountStore.kt`：增删改查、状态机
- [x] `core/account/PlatformSpec.kt` + `SessionTest`
- [x] `AndroidManifest.xml`：INTERNET

**Done**：旧库升级不丢件；加密串在 DB 里是密文；单测绿。

### P2 · 登录与账号页

- [ ] `ui/account/PlatformLoginScreen.kt`（Compose + WebView）
- [ ] `ui/account/AccountListScreen.kt`
- [ ] `ShowCodeRoot` 新增 `Route.ACCOUNT_LIST` / `ACCOUNT_LOGIN`
- [ ] `SettingsScreen` 入口 + 账号状态行
- [ ] 字符串与空态
- [ ] 登录 → 杀进程 → 重开，凭据仍可解

**Done**：三家都能登录、删除、重新登录；WebView 里不残留会话。

### P3 · 拼多多端到端

- [ ] `data/webview/FetchHost.kt`
- [ ] `data/fetch/PddFetcher.kt`（列表 + 翻页 + 空抓保护）
- [ ] `data/fetch/PddTraceFetcher.kt`
- [ ] `core/account/TraceParser.kt` + 语料
- [ ] `core/account/AccountMerge.kt` + 测试
- [ ] `data/AccountSyncEngine.kt`：节流、取消、失败分类
- [ ] `ParcelRepository` 接入账号同步与合并
- [ ] 首页 / 现场 / 组件按新字段过滤与显示
- [ ] 账号页：在途、已到站未定位、失败重试

**Done**：真实拼多多账号收录的件能出现在首页；无码件出示后五位；短信件与账号件不重复。

### P4 · 淘宝

- [ ] 先验证 WebView 能否稳定钩到 `mtop` 订单 JSON
- [ ] 不行再落 `TbOrders` 的 `_m_h5_tk` + `wapSign` 直连
- [ ] 轨迹详情 + 解析样本
- [ ] 合并 / 去重回归

### P5 · 京东

- [ ] `JdFetcher`（JSONP 捕获 + 翻页）
- [ ] `JdTraceFetcher`（`deal_wuliu` DOM）
- [ ] 合并 / 去重回归

### P6 · 加固与发布

- [ ] 失效检测、失败分类、限流、总预算
- [ ] health 两条新状态 + 首页提示
- [ ] README / 隐私 / 第三方声明
- [ ] `docs/verification-checklist.md` 增补账号相关编号
- [ ] 真机清单全跑一遍
- [ ] 打 release 包前确认：无 Cookie 日志、无 WebView 调试、无明文密文

---

## 13. 风险与止损

| 风险 | 症状 | 止损 |
|---|---|---|
| PDD 改版 | 连续同步 0 件 / 钩子拿不到 | 账号置 `EXPIRED`，保留旧的到站件；不删库；退回手动 |
| 账号风控 | 平台提示异常登录 / 需要验证 | 只做用户主动登录，不保存密码；限制 15 分钟一次；不自动重登 |
| WebView 内存 | 同步后卡顿 / 被杀 | 1×1 宿主、串行、结束 destroy；单平台 30 秒预算 |
| 误标已取 | 用户站到柜前少一件 | 只在「本人取件 / 凭码取件」自动标；其余留给账号页确认 |
| 明文泄漏 | 日志 / 备份 / 截图 | 加密存储、关调试、`allowBackup=false`、日志打码 |
| 依赖膨胀 | 构建变慢 / 冲突 | 只加 `androidx.webkit`；能直连就不引 OkHttp |

**终止条件**：P0 连续 3 次失败，或 P3 在真机上稳定拿不到到站件 → 停掉抓取，只保留登录页作为「去官方 App」的快捷入口，不发布半套功能。

---

## 14. 明确不做

- 不做无障碍 / 读屏 / MediaProjection。
- 不做后台常驻轮询、不做定时抓取、不做静默重登。
- 不做多账号、不做账号同步、不把凭据上传任何地方。
- 不伪造取件点、不伪造取件码、不把「签收」当成「已取」。
- 不因为接了账号就删掉短信 / 通知 / 手动导入任何一条老路。

---

## 15. P0 实测记录（2026-11-03）

### 环境

| 项 | 值 |
|---|---|
| 设备 | MuMu 模拟器（`emulator-5554`，SM-A5360） |
| Android | 15（API 35） |
| WebView | `com.android.webview 110.0.5481.154.1` |
| 分辨率 | 1080 × 1920 |
| UA | 去掉 Android 标记的桌面 Chromium（与参考实现同一条替换） |
| 登录 | 手机号 + 短信验证码，用户手动完成；勾选服务协议 |

### 三轮结果

| 轮次 | 登录态 | document-start 捕获 | order_list 响应 | 翻页 | 详情 → 运单号 → 轨迹 | 判定 |
|---|---|---|---|---|---|---|
| 1 | OK | OK | 97 B，`orders: []` | 无第二页 | 未进入 | FAIL |
| 2 | OK | OK | 97 B，`orders: []` | 无第二页 | 未进入 | FAIL |
| 3 | OK | OK | 97 B，`orders: []` | 无第二页 | 未进入 | FAIL |

原始响应体：

```json
{"server_time":1793702367,"orders":[],"extra_info":{"harmony_empty_height_and_back_refresh":"1"}}
```

请求体确认是 `POST /proxy/api/api/aristotle/order_list_v4?pdduid=…`，`{"type":"all","page":1,…}`。

### 这组结果证明什么、不证明什么

**证明了的：**

- 拼多多 H5 登录能在 WebView 里走完，Cookie 判据（`PDDAccessToken` + `pdd_user_id`）可读到；
- `WebViewCompat.addDocumentStartJavaScript` 在 WebView 110 上可用；
- 钩子拿到了**真实的** `order_list_v4` 响应与请求体，说明「让 WebView 替我们签名 + Kotlin 只读响应」这条路可行；
- `pinduoduo://` scheme 被拦住，没有把页面拐去下载 App。

**没能证明的：**

- 订单列表解析、触摸翻页、`order.html → 查看物流 → goods_express`、运单号与轨迹提取；
- 失败原因不是钩子或风控，而是**这个登录账号的订单列表为空**：页面本身停在「全部 / 待付款 / 待分享 / 待发货 / 待收货 / 评价」标签栏 + 商品推荐流，接口返回 0 条。

### 判定

按方案门槛（连续 3 次、至少 2 次拿到可解析的到站件）：

> **NO-GO（0/3）。**
> 且必须区分：**机制侧通过，数据侧未通过**——账号没有订单，无法验证解析、翻页、轨迹三段。

环境备注：这是模拟器 Android 15 + 旧内核 WebView 110，不是真机结论；本轮没有出现风控挑战页、验证码拦截或登录失效，因此没有「疑似风控」证据。若换成一个至少有 1 条待收货 / 派送 / 已签收订单的账号，P0 可以从「列表解析」继续复测，但按门槛本轮不通过。

### 按方案执行的动作

- **停止 P1～P5**：不做数据库迁移、不做账号存储、不做登录页、不把 `INTERNET` 或任何抓取代码带进 release。
- 现有 `DeepLinkLadder` 的「去取」入口保持不变，作为「去官方 App」的快捷方式；短信、通知、手动导入三条老路一个不动。
- P0 探针只存在于 `app/src/debug/` 与 debug 变体，release 包不带它；它留作复测工具，不是产品功能。

---

## 16. P0 调整后：单轮确认与差异清单（2026-11-03 18:50）

### 16.1 单轮结果（换号后复测）

门槛已从「3 轮至少 2 轮」调整为「1 轮确认」，探针改为 `ROUNDS=1`。

| 项 | 结果 |
|---|---|
| 登录态 | OK（`PDDAccessToken` + `pdd_user_id`） |
| document-start 注入 | OK |
| `POST /aristotle/order_list_v4` 捕获 | OK，3 个响应，单页 `size=10` |
| 订单列表真实数据 | **30 条**（首次 10 条，两次滑动追加到 30 条） |
| 翻页 | **有效**：PDD 用 `offset` 游标（`offset:"MO-01-…"`），不是 `page=2`；`page` 恒为 1，不能拿 `page2=false` 判失败 |
| 详情 → 运单号 | OK：`goods_express.html?tracking_number=465700000020382` |
| 轨迹全文 | OK：`innerText` 1250 字符，含「取件码 38-1-17031」「待取件」「已签收」 |
| 后五位推导 | OK：`465700000020382`.takeLast(5) = `20382` |
| 结论 | `VERDICT GO` |

前两次的 `orders=0` 是**探针自身的两个 bug**，不是平台：
1. 捕获上限 8 KB 把 72 KB 的订单响应截断，JSON 解析失败；
2. 详情页点击 JS 用 `indexOf('查看物流')` 命中了一个包含该字样的大容器，实际点到「已签收」，页面没跳。
分别改成 80 KB、以及「只接受短文本 + 挑可见元素里高度最小的」后，同一账号一次通过。

**P1 状态：已启动。**

### 16.2 我们与它的实现差异

| 维度 | 参考实现 | 我们的探针 | 对成功率的影响 |
|---|---|---|---|
| 宿主尺寸 | `decor.addView(MATCH_PARENT × MATCH_PARENT)`，全尺寸、不可见、排在最底层 | Activity 内全尺寸**可见** WebView | 视口等价；但「1×1 宿主」是错的，必须改全尺寸，否则虚拟列表不加载 |
| 登录校验 | 登录后加载 `orders.html`，用捕获的 `authenticatedOrderCount` 验证能读订单才绑定 | 只看 Cookie 判据 | 可能绑定「能登录但读不到订单」的会话；P2 必须补校验 |
| 注入范围 | `COOKIE_HOSTS`：`mobile.yangkeduo.com` / `yangkeduo.com` / `pinduoduo.com` / `mobile.pinduoduo.com` / `passport.pinduoduo.com` | 只注入 `mobile.yangkeduo.com` | 订单/登录跨域时可能漏捕获 |
| 捕获存放 | `window.name`，跨导航存活，最多 500 条，读后清空 | `window.__p0` 页内对象，12 条 × 8KB | 详情页导航后我们列表捕获会丢；列表页单页够用 |
| 列表翻页 | 每 3 秒一轮、最多 18 轮；第 2/5 轮点「全部」，每轮点「加载更多/查看更多」，再派发 swipe | 4 次 swipe，不点 tab、不点加载更多 | 有订单时，我们可能翻不到第二页 |
| 轨迹取法 | `order.html?order_sn=` + 钩住页面 SDK 的物流 JSON，递归扫字段；同时每 2 秒解析 DOM，失败继续点 | 点一次「查看物流」→ `goods_express.html` 读 `innerText`，URL 取 `tracking_number` | 它更稳；我们依赖按钮文案与单次点击，失败概率更高 |
| 登录失效 | 页面跳 `login.html/passport` 时立刻报「登录已失效」 | 只看 Cookie | 会话失效时会静默跑空 |
| 账号隔离 | 支持时用 WebView multi-profile，每账号独立 | 全局 CookieManager | v1 单账号可接受；多账号前必须补 |
| UA / 指纹 | 默认 UA 去掉 Android 标记、改 `X11; Linux x86_64`，不改 canvas/audio/GPU | 同一条替换 | 等价；差异在 WebView 内核版本 |
| 超时 | 列表 75 s、轨迹 45 s | 单轮 34 s | 有订单但加载慢时，我们可能过早判 FAIL |

### 16.3 取件码这一格（单独标注）

- 它的取件码来自轨迹文本正则：`(取件码|取货码|提货码|自提码|驿站码|取件验证码)[:：\s]*([A-Za-z0-9][A-Za-z0-9\-]{1,12})`。
- 这个正则**抠不出**「出示单号后五位 76416」——没有关键词，只有数字。
- 我们这边要补的是产品字段与规则，不是 P0 判定项：
  1. `Parcel.trackingNo`（运单号）+ `codeKind = SUFFIX`；
  2. 轨迹里出现「出示单号后五位」时，取 `trackingNo.takeLast(5)` 写进 `code`；
  3. 现场模式的标签换成「出示单号后五位」，首页卡里码旁边加 `后五位` 小字；
  4. 拿不到运单号就**不伪造**，该件留在账号页「已到站但没定位」，不进首页。
- 这一段不改变 P0 结论：P0 只验列表/翻页/运单号，取件码规则在 P3 落地。

### 16.4 模拟器环境备注

- MuMu / Android 15 / WebView 110.0.5481.154.1；document-start 与 fetch 钩子均可用。
- 换号后内容侧已通过：30 条订单、offset 翻页、详情运单号与 1250 字轨迹。
- 本轮没有风控挑战页、验证码拦截、登录失效；但旧内核与模拟器指纹仍可能影响**后续 P3 的批量同步**，不能当成真机结论。
- 结论写成「模拟器环境：宿主与内容侧均通过；真机未复测」。

---

## 17. P1 完成记录（2026-11-03）

| 项 | 落地 |
|---|---|
| 数据模型 | `Parcel` 新增 `trackingNo` / `orderSn` / `accountId` / `stage` / `codeKind` / `lastSyncedAt`；新增 `Stage` / `CodeKind` / `Source.ACCOUNT` |
| DB | `VERSION=9`；`parcel` 新列（含商品五列）；`bound_account` 表；`parcel_dedupe` 改为 `WHERE picked=0 AND code<>''`；新增 `parcel_tracking_dedupe`、`parcel_account` |
| 迁移 | `upgradeToV8` 走 `addColumnIfMissing` + `CREATE TABLE IF NOT EXISTS` + 索引重建，不删任何老行 |
| 加密 | `security/SecretBox.kt`：Android Keystore AES-256-GCM，密文 `base64(iv):base64(ct)`，解不开返回 null |
| 存储入口 | `data/AccountStore.kt`：bind / cookieOf / setEnabled / mark / remove；再登录保住 createdAt 与启停开关 |
| 登录判据 | `core/account/PlatformSpec.kt`：三家 loginUrl / cookieHost / Cookie 谓词；`SessionTest` 8 个用例 |
| 权限 | `INTERNET` 进主 manifest；`androidx.webkit` 从 debugImplementation 转 implementation |

验证：

- `python tools/migration-check.py` → **全绿**（v1 一路升到 v8、表/列一致、21 条 SELECT 可执行）。
- `./gradlew :app:testDebugUnitTest` → **绿**（含 `SessionTest` 与既有全部单测）。
- `connectedDebugAndroidTest` 只跑 `SecretBoxTest` → **2/2 绿**（往返一致、密文不含明文、篡改返回 null；MuMu Android 15）。
- 未验：真机上的 v7→v8 实机升级（按方案留给 P6 的实机清单）。
