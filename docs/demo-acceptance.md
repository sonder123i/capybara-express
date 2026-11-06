# 演示模式验收记录（宣传片录制标准）

验收设备：MuMu 模拟器（1080×1920，Android，debug 包）。
验收时间：2026-11-04。视觉 token（明暗色板 / 字重 / 16dp 圆角 / 底栏玻璃 / 动效）统一后，
本目录下 `demo-*` / `real-*` 截图已于同日 15:19–15:48 全部重拍；底栏改成运行时对齐的
「一格宽胶囊 + 12sp 标签 + 描边图标」后，16:20–16:40 又全部重拍一次；最终底栏改为
**实底**（撤掉模糊/采样）后，17:45–17:50 再全部重拍；底栏改为**1:1 照抄参考**
（White@0.28 / blur 10dp / highlight 0.16 / 胶囊 0.34）后，19:10–19:13 重拍带底栏的
`demo-home` / `demo-settings` / `demo-accounts` / `demo-entry` / `demo-entry-result` /
`demo-exit-result` / `real-home-after-demo`。无底栏的现场/详情页截图不受影响。
所有截图在 `docs/screenshots/`，文件名以 `demo-` / `real-` 开头。

## 逐条对照

| # | 要求 | 结论 | 证据 |
|---|------|------|------|
| 1 | 确定性数据：独立内存源，不读真实账号、不联网、不依赖登录 | 做到 | 见下方 [1] |
| 2 | 图片全本地：打包素材，无网络图，无加载/无图帧 | 做到 | 见下方 [2] |
| 3 | 不弹任何系统框：权限、通知、键盘、系统提示全不出现 | 做到 | 见下方 [3] |
| 4 | 场景全覆盖：8 个场景每个都能演 | 做到 | 见下方 [4] |
| 5 | 节奏稳定：布局不动、不自动跳转/刷新，结束态干净 | 做到 | 见下方 [5] |
| 6 | 进出一致：一键进出，退出不影响真实数据，连进三次不变 | 做到 | 见下方 [6] |
| 7 | 稳定性：连续 10 分钟不崩、不 ANR、无内存明显增长 | 做到 | 见下方 [7] |

---

## [1] 确定性数据

- 演示件全部由 `DemoData.parcels(anchor)` 在内存生成，id 为负数，`pointKey` / 码形态 / 商品信息写死；不经过 `ParcelRepository`，不写 SQLite。
- 演示账号由 `DemoData.accounts(anchor)` 生成，只用于账号列表展示；不读加密账号表。
- 进入演示时 `demo` 分支接管首页、现场、详情、设置、账号列表，真实数据不参与渲染。
- 演示态不触发 `refreshNow`、不启动账号同步、不打开 WebView；商品图全部走本地 drawable，因此演示过程不产生任何网络请求。
- 设备验收前执行 `pm clear dev.showcode`：真实账号表为空、未登录，演示里仍固定显示「已绑定 2 个」与 6 件；说明演示不依赖真实账号或登录态。
- 单元测试 `DemoDataTest` 5 条全绿：同一锚点生成两次相等、6 件、分组 `2/3/1`、三种码形态齐全、每件都有 `demo:` 本地图与商品信息。
- 连进三次：每次点「退出」都先确认回到欢迎页（`uiautomator` 文本含「先看完整演示」），再重新进入；三次首页 App 文本节点 SHA-256 完全一致：
  - `u1` / `u2` / `u3` = `96-78-BB-7A-EF-7`，长度 371，`identical=True`。
- 设备证据：[demo-home.png](screenshots/demo-home.png)（3 个点、6 件、固定顺序）。

## [2] 图片全本地

- 6 张商品图打包在 `app/src/main/res/drawable-nodpi/`：
  `demo_goods_ramen.jpg`、`demo_goods_tissues.jpg`、`demo_goods_coffee.jpg`、
  `demo_goods_phone_case.jpg`、`demo_goods_snack.jpg`、`demo_goods_shampoo.jpg`。
- 演示件的 `goodsImage` 使用 `demo:ramen` 等 key，`GoodsThumbnail` 命中后用
  `painterResource` 同步取图，不经过 `ThumbnailLoader` 的网络与加载态。
- 首页、现场、详情三处的商品图均无「无图」占位，没有一次加载闪烁：
  [demo-home.png](screenshots/demo-home.png)、[demo-pickup.png](screenshots/demo-pickup.png)、
  [demo-detail.png](screenshots/demo-detail.png)。

## [3] 不弹任何系统框

- 演示态首页隐藏：权限体检条、同步/过期条、提示卡、搜索、手动导入、收容入口、桌面组件提示。
- 演示态设置页隐藏：权限体检、提醒、通知来源、规则、家人分组名输入框；只保留账号、阈值、外观。
- 演示态所有权限请求回调都加了 `if (!demo)` 守护；进入/退出演示不触发任何 `requestPermissions`。
- 现场「复制单号 / 淘宝查询」在演示态只显示 App 内浮层，不写剪贴板、不拉起淘宝：
  [demo-feedback.png](screenshots/demo-feedback.png)、[demo-feedback-cleared.png](screenshots/demo-feedback-cleared.png)。
- 件详情页的两个交接动作在演示态同样被 `isDemo` 守护：只渲染按钮，不写剪贴板、不拉起外部 App、不弹 Toast。
- `docs/screenshots/demo-*.png` 与 `real-home-after-demo.png` 全部录制态截图里，没有权限弹窗、没有通知监听引导、没有软键盘。
- 演示前后 `READ_SMS` / `RECEIVE_SMS` 保持未授予（从未主动申请）。

## [4] 场景全覆盖

| 场景 | 截图 |
|------|------|
| 首页多驿站多件（图/标题/价格/店铺） | [demo-home.png](screenshots/demo-home.png) |
| 真实取件码 PICKUP 大字码 | [demo-pickup.png](screenshots/demo-pickup.png) |
| 后五位 SUFFIX + 淘宝查询/复制单号 | [demo-suffix.png](screenshots/demo-suffix.png) |
| 无码件 NONE 交接页 | [demo-none.png](screenshots/demo-none.png) |
| 件详情（商品卡 + 元数据 + 轨迹） | [demo-detail.png](screenshots/demo-detail.png)、[demo-detail-trace.png](screenshots/demo-detail-trace.png) |
| 点已取后卡片消失 | [demo-after-pick.png](screenshots/demo-after-pick.png)（京东自提点整张卡消失） |
| 单个点取完 | [demo-point-done.png](screenshots/demo-point-done.png) |
| 全部取完的空态 | [demo-all-done.png](screenshots/demo-all-done.png)（本月共取 6 件） |
| 设置页平台账号（已绑定） | [demo-settings.png](screenshots/demo-settings.png)、[demo-accounts.png](screenshots/demo-accounts.png) |

## [5] 节奏稳定

- 演示数据用固定锚点 `demoNow`，所有渲染取它而不是真实时钟；首页倒计时/紧急度不跳。
- 现场屏在演示态跳过 `rememberTickingNow`，倒计时文本不变。
- 演示态不触发 `refreshNow`，不显示同步/过期条，不自动跳转。
- 现场多余的格口大胶囊已收成紧凑一行，后五位屏不再出现底部半截白条。
- 同一首页间隔 5 秒截两帧，去掉状态栏后 App 内容区域 SHA-256 完全一致：
  `A1-84-0D-29-EB-42-12-0F-AE-99-C1-3D`（两帧同值），说明停住时没有动画残留或自动刷新。
  （底栏 1:1 照抄参考后重测；旧值 `D9-3C-B7-DC-0F-3E-FE-7B`、`D3-6E-68-E6-2D-C3-14-5C`、
  `0B-E7-A4-26-33-67-B7-C9`、`3C-A2-30-01-F3-11-14-AE` 已失效。）

## [6] 进出一致

- 首页、设置页顶部都有「退出」；现场屏有「退出演示」；首页/现场返回只回演示首页，不误退出。
- 退出时 `demoPicked.clear()`，不写库；再次进入从完整 6 件开始。
- 连进三次（每次都先用 uiautomator 确认已回到欢迎页，再重新进入）首页 App 文本哈希完全一致（见 [1]）。
- 进入/退出各一次的设备截图：[demo-entry-result.png](screenshots/demo-entry-result.png)（真实设置点「演示模式」后落到演示首页）、[demo-exit-result.png](screenshots/demo-exit-result.png)（演示首页点「退出」后回到真实首页）。
- 退出后直接查真实 SQLite：`select id from parcel;`、`select id from bound_account;` 均返回空输出（0 行），演示取过的 6 件、绑定的 2 个账号一个都没落库。
- 真实首页只保留首次引导的示例卡（带「示例」水印），没有演示里真实操作过的件：[real-home-after-demo.png](screenshots/real-home-after-demo.png)。
- 一键入口：[demo-entry.png](screenshots/demo-entry.png)（真实设置页底部的「演示模式（宣传片录制）」）。

## [7] 稳定性（10 分钟）

- 录制态循环：首页 ↔ 丰巢 PICKUP ↔ 菜鸟 PICKUP/SUFFIX ↔ 设置 ↔ 首页，按墙钟连续跑 **10 分 30 秒**，共 **54 轮**。
- 设备记录：
  - 循环前 PSS `TOTAL 106998 KB`（Java 14720 / Native 16136）。
  - 循环后 PSS `TOTAL 106607 KB`（Java 13304 / Native 23176）。
  - 10.5 分钟后总 PSS 反而下降 391 KB，没有明显增长。
  - `logcat` 无 `FATAL EXCEPTION`、无 `ANR in dev.showcode`、无 `Force finishing`。
