# 视觉参考基线：capybara-course-helper v1.0.6

> 参考目录：`C:\Users\example\projects\example-course-helper`（**只读**）。
> 本文所有值都标了来源文件与行号；没有「凭印象」的条目。
>
> 只读证据：抽取时对 `capybara-course-helper/app/src/main` + `gradle.properties`
> 共 **565 个源文件**做「相对路径 + 字节数 + mtime」的 SHA-256，基线值为
> `4F0ADF846967DB696E10688B46607C0D3237DC97551F4125751B9237B1B99251`；
> 改完复查以**参考工程内层 git 工作树干净 + 565 文件 mtime 全部早于本轮**为准（见文末「只读校验」）。

---

## 1. 色板

### 1.1 原始常量（参考工程 `ui/theme/Color.kt`）

| 角色 | 值 | 出处 |
|---|---|---|
| BrandPrimary | `#0A84FF` | `Color.kt:10` |
| BrandPrimaryStrong | `#0069D9` | `Color.kt:11` |
| BrandPrimaryContainer | `#D6E9FF` | `Color.kt:12` |
| BrandSecondary / Container | `#5C6772` / `#E6EBF0` | `Color.kt:13-14` |
| Neutral50（暗色 onSurface） | `#F6F7FB` → 实际 `Neutral50` | `Color.kt:21` |
| Neutral300（暗色 onSurfaceVariant） | `#B3BDCC` | `Color.kt:24` |
| Neutral500（暗色 outline） | `#667085` | `Color.kt:25` |
| Neutral700（暗色 outlineVariant） | `#344054` | `Color.kt:26` |
| Neutral900（亮色正文近似） | `#101828` | `Color.kt:27` |
| SurfaceDark / BackgroundDark | `#171B22` / `#0F1218` | `Color.kt:33-34` |
| SemanticSuccess / Warning / Danger | `#1F845A` / `#E58A00` / `#C73A2F` | `Color.kt:36-41` |
| NavSelectedAccentLight / Dark | `#0057D9` / `#4DA3FF` | `Color.kt:193-194` |
| IOSBlueLight / Dark | `#007AFF` / `#0A84FF` | `Color.kt:203-204` |
| IOSFillLight / Dark | `#E9E9EB` / `#2C2C2E` | `Color.kt:211-212` |
| GlassHighlight | `#AAFFFFFF` | `Color.kt:138` |
| GlassBorderLight / Dark | `#CCFFFFFF` / `#33A6B4C9` | `Color.kt:144-147` |
| GlassOverlay | `#18FFFFFF` | `Color.kt:156` |

### 1.2 主题槽位（参考工程 `ui/theme/Theme.kt`）

**亮色** `LightColorScheme`（`Theme.kt:73-104`）：
`primary = BrandPrimaryStrong #0069D9`、`background = NeuBackground #F0F2F4`、
`surface = NeuSurface #F4F5F6`、`onSurface = NeuOnSurface #2E3A4D`、
`onSurfaceVariant = #51545A`、`outline = NeuDarkShadow #D1D9E6`、`outlineVariant = NeuDivider #D8DFE9`、
`surfaceContainerLowest = #FFFFFF`、`Low = #F6F6F8`、`Container = #F0F0F3`、
`High = IOSFillLight #E9E9EB`、`Highest = #E2E2E7`、`error = SemanticDanger #C73A2F`。

**暗色** `DarkColorScheme`（`Theme.kt:40-71`）：
`background #0F1218`、`surface #171B22`、`onSurface Neutral50 #F6F7FB`、
`onSurfaceVariant Neutral300 #B3BDCC`、`surfaceVariant #29313D`、
`surfaceContainerLowest #0B0E13` / `Low #171B22` / `Container #1D222B` / `High #252B35` / `Highest #303744`、
`primary #88BFFF`、`onPrimary #002F55`、`primaryContainer #183F68`、`onPrimaryContainer #D6E9FF`、
`outline Neutral500 #667085`、`outlineVariant Neutral700 #344054`、
`error #FFB4AB`、`errorContainer #742A27`、`onErrorContainer #FFDAD6`。

### 1.3 快递侧的落点（`Palette.kt`）

| token | 亮色 | 暗色 | 来源/推导 |
|---|---|---|---|
| canvas | `#FFFFFF` | `#0F1218` | 亮色沿用本仓库纯白；暗色 = 参考 `background` |
| surfaceCard | `#F9FAFD` | `#1D222B` | 亮色 = 参考 `Neutral20/SurfaceAlt`（`Color.kt:30`）；暗色 = 参考 `surfaceContainer` |
| surfaceSunken | `#E8ECF3` | `#252B35` | 亮 `Neutral100`；暗 `surfaceContainerHigh` |
| surfaceDeep | `#D5DCE7` | `#303744` | 亮 `Neutral200`；暗 `surfaceContainerHighest` |
| ink | `#101828` | `#F6F7FB` | `Neutral900` / `Neutral50` |
| inkSecondary | `#344054` | `#D5DCE7` | `Neutral700` / `Neutral200` |
| inkMuted | `#59616F` | `#B3BDCC` | 本仓库 AA 校验值 / `Neutral300` |
| accent | `#0A84FF` | `#0A84FF` | 参考 `BrandPrimary` / `IOSBlueDark` |
| accentInk | `#0057D9` | `#88BFFF` | 参考 `NavSelectedAccentLight` / 暗色 `primary` |
| accentTint | `#E6F3FF` | `#183F68` | 亮色 `tintOf(Accent,0.10)`；暗色 `primaryContainer` |
| urgencySafe / DueSoon / AtRisk | `#00B578` / `#FF8F1F` / `#FF3141` | `#3DDC97` / `#FFB020` / `#FF6B6B` | 品牌三态；暗色保持色相、提亮以满足深底 AA |
| 三态 tint | `#E6F8F2` / `#FFF4E9` / `#FFEAEC` | `#16332A` / `#3A2C14` / `#3A1D22` | 10% 叠在卡面上的不透明合成值 |
| onsiteInk / onsiteSurface | 黑 / 白 | `#F6F7FB` / `#0F1218` | 保持「码 vs 底」最大对比；暗色反相 |
| glassHighlight / BorderLight / BorderDark / Overlay | `#AAFFFFFF` / `#CCFFFFFF` / `#33A6B4C9` / `#18FFFFFF` | 同左（透明度略降） | `Color.kt:136-159` |

> 暗色推导规则：没有出现在参考工程里的三态色与现场黑白，**不新造色相**，只按参考的
> dark container 明度关系提亮/反相；其余中性色与主色逐值抄参考 `Theme.kt`。

---

## 2. 字体层级与字重

来源：参考 `ui/theme/Type.kt:9-108`（Material `Typography`）与 `115-139`（等宽数据）。

| role | 参考值 | 快递侧 token | 落点 |
|---|---|---|---|
| displayLarge | 36/44 Bold | —（我们不用 36） | — |
| headlineLarge | 28/36 Bold | — | — |
| headlineMedium | 24/32 SemiBold | — | — |
| headlineSmall | 20/28 SemiBold | `ShowCodeText.Headline 20/28 SemiBold` | 屏标题 |
| titleLarge | 22/28 SemiBold | — | — |
| titleMedium | 16/24 SemiBold | `Section 16/24 SemiBold` | 区块标题 |
| titleSmall | 14/20 SemiBold | `Item 15/22 SemiBold`（保留 15 以维持密度） | 条目主文字 |
| bodyLarge | 16/24 Normal | — | — |
| bodyMedium | 14/20 Normal | `Body 14/20 Normal` | 正文 |
| bodySmall | 12/18 Normal | `Caption 12/18 Normal` | 辅助文字 |
| labelLarge | 14/20 Medium | `Action 13/19 Medium`（保留 13 以塞进列表行） | 行内动作 |
| labelMedium | 12/16 Medium | `Meta 13/19 Normal` + `Micro 11/16 Medium` | 说明 / 角标 |
| labelSmall | 11/16 Medium | `Micro 11/16 Medium` | 状态角标 |
| MonoDataLarge | 20/28 Bold Monospace | `CodeLarge 20 Bold Mono` | 现场大字码 |
| MonoDataMedium | 14/20 SemiBold Mono | `Code 17 Bold Mono` | 卡片码 |
| MonoDataSmall | 11/16 Medium Mono | `CodeSmall 15 Bold Mono` | 最近已取 |

**只保留 400 / 500 / 600 / 700 四档字重**。参考没有 800；本仓库原来大面积用
`ExtraBold(800)`，已全部收掉——「全都很重等于没有层级」与参考 `Type.kt` 的用意一致。

---

## 3. 间距、圆角、阴影/玻璃

### 3.1 圆角

参考 `Theme.kt:32-38` 的 `AppShapes`：

| 档 | 值 | 出处 |
|---|---|---|
| extraSmall | 8dp | `Theme.kt:33` |
| small | 12dp | `Theme.kt:34` |
| medium | 16dp | `Theme.kt:35` |
| large | 24dp | `Theme.kt:36` |
| extraLarge | 28dp | `Theme.kt:37` |

快递侧落点（`TypeScale.kt` 的 `ShowCodeShapes`）：`Sm = 8dp`、`Md = 16dp`、`Pill = 50`。
所有卡片从原来的 14dp 统一到 16dp；胶囊与色条仍用 `Pill`。
弹层/详情页在参考里用 28dp（`CourseDetailContent.kt:89` 的 `RoundedCornerShape(28.dp)`），
本仓库没有独立弹层，保留现有页面壳，不强行引入新档。

### 3.2 间距节奏

参考详情页的节奏（`CourseDetailContent.kt:82-207`）：
- 页面横向 12dp，内容内衬 18dp；
- 区块间距 12dp（`Arrangement.spacedBy(12.dp)`，第 122 行）；
- 卡内 14dp（第 162 行），信息块 13dp（第 201 行）；
- 行间 8/10dp。

快递侧保留自己的密度契约：
- 页面边距 **20dp**（列表信息更密，20dp 比参考的 18dp 多留一点拇指安全区）；
- 卡内 16dp、行间 8/12dp；
- 不为了「像参考」把 20dp 改成 18dp——取件码场景的字号与行高更需要横向余量。
这条是**有意的品牌差异**，不是漏改。

### 3.3 卡片结构与阴影

参考 `CourseDetailContent.kt:160-208`：
- 卡 = `Surface` + `scheduleCardColor(course.color, 0.025f)` + `scheduleCardBorder(course.color)`
  + `RoundedCornerShape(18.dp)`，内衬 14dp；
- 信息块 = `Surface` + 2% 底色 + 同色描边 + 16dp 圆角，内衬 13dp。

快递侧对应约定：
- 卡 = `SurfaceCard` + `Hairline` 1dp 描边 + `ShowCodeShapes.Md(16dp)`，内衬 16dp；
- 信息块 = `SurfaceCard` + `Hairline` 1dp + `ShowCodeShapes.Md`，内衬 12-14dp；
- **不叠投影**：参考的内容卡同样靠面阶+描边分离，阴影只给浮层/玻璃。

### 3.4 玻璃

参考 `ui/system/GlassMaterial.kt` 按角色分档（第 137-235 行）：

| 角色 | blur | 折射高/量 | 表面 α | 边 α | 阴影 α | 出处 |
|---|---|---|---|---|---|---|
| Navigation | 10dp | 8dp / 12dp | 0.16 | 0.14 | 0.12 | `GlassMaterial.kt:138-151` |
| Control | 5dp | 6dp / 10dp | 0.10 | 0.12 | 0.08 | `:153-164` |
| Modal | 6dp | 10dp / 14dp | 0.78/0.84 亮/暗 | 0.30 | 0.24 | `:166-173`、`:68-72` |
| Interactive | 0dp | 12dp / 18dp | 0.08 | 0.16 | 0.12 | `:175-187` |

底栏实际用法（`GlassRecipe`，`GlassMaterial.kt:241-451`）：
- 轨道 White@0.28 + highlight 0.16 + shadow 0.08（第 56-60、271-287 行）；
- 选中胶囊：有折射 White 0.34 → 按下 0.14；无折射 Black@0.1 → 按下 0.03；
- 暗色：选中胶囊 0.20、无折射轨道 White@0.18、选中指示从黑换白；
- 指示胶囊按压胀度 `78/56 ≈ 1.39`（`GlassMaterial.kt:287`）。

快递侧落点：`ui/glass/GlassMaterials.kt` 同源搬运 Navigation 档；暗色新增
`navSelectedSolidAlpha(0.20)` / `navSelectedGlassAlpha(0.12)` / 无折射 White@0.18 与白色指示，
值见同文件新增函数。

**亮色边界补偿（品牌适配，不是抄错）**：参考轨道铺在 `NeuBackground #F0F2F4` 上，白 0.28
本身就浮得起来；本仓库卡面 `#F9FAFD` 比页面还亮，白叠白看不出轨道。因此 `GlassMaterials.kt`
新增 `navTrackShadeAlpha(dark)=0.06/0`、`navTrackBorderAlpha(dark)=0.10/0.18`、
`navTrackBorderColor(dark)=Black/White`，在 `ShowCodeNavBar.kt` 的轨道 `onDrawSurface` 叠一层
6% 中性压暗，并加 1dp 描边（对应参考 `GlassRecipe.Navigation.borderAlpha = 0.14` 这一档）。
暗色下不加压暗，描边取白 0.18。

**取样层（关键）**：参考的模糊是对真实内容取样。本仓库此前把 `rememberLayerBackdrop`
建好了却**没有挂到内容上**，底栏实际只是「半透明白叠在列表上」（截屏里卡片标题原样压进图标）。
本轮在 `ShowCodeRoot.kt` 给承载三个主屏的 `AnimatedContent` 加
`.layerBackdrop(glassBackdrop)`，底栏才真正折到模糊内容；底栏背后文字不再与图标打架。

### 3.5 按压反馈

参考 `GlassIndication.kt:21-75`：中性灰 `#787880 @0.16`，按下 80ms tween，松手
`spring(0.72, 380)`。快递侧 `ui/GlassIndication.kt` 逐值同源。

---

## 4. 图标风格

参考 `ui/system/AnimatedIconSpec.kt`：
- 24×24 单位画布、`Stroke(1.8f, cap=Round, join=Round)`（第 108 行）；
- 一律线稿，状态变化用描边生长/旋转/脉冲（第 48-100 行）；
- 底栏 `AppSymbol` 用 Material Outlined 语义 + 自定义线稿（`AppSymbol.kt:25-45`）。

快递侧：
- 底栏三枚是 256 网格、16f 圆头描边的 duotone 线稿（`NavSymbol.kt:22-64`），与参考的
  Phosphor 做法同源；这是最显眼的图标层，已对齐。
- 内容区保留 Material 图标，但只用**必要语义**（搜索/更多/勾/警告）；密集列表里换成
  24dp 线稿会牺牲扫描速度，这是有意的品牌例外。

---

## 5. 页面与导航

- 页面壳 `GlassPageScaffold`：`Scaffold(containerColor = Transparent)`、只吃底部 inset、
  顶栏自绘（`GlassPageScaffold.kt:20-51`）。
- 底栏几何（`CapsuleNavigationBar.kt:117-162`）：轨道高 72/62（有折射外观）、64/58（无折射）、
  轨道到边缘 8/6、横向 16/10、指示胶囊 56/48。
- 屏幕余量 `ScreenMetrics.kt:43-90`：可用高 720dp 以上不压缩，720→560 线性压到 0；
  宽度 411→360 线性收紧。
- 快递侧 `ShowCodeNavBar.kt` 的几何表与上述值逐条一致（见文件头 104-125 行注释）。

---

## 6. 动效

### 6.1 时长（参考 `MotionTokens.kt:16-46`）

| token | 值 | 用途 |
|---|---|---|
| Fast | 120ms | 按压/退出 |
| Medium | 220ms | 标准过渡、数值入场 |
| Number | 180ms | 单个数字位滚动 |
| Slow | 400ms | 强调过渡 |
| TabTransition | 220ms | 标签切换 |
| DialogEnter | 400ms | 弹层进入 |
| ExpandCollapse | 250ms | 展开/收起 |
| NavIndicator | 260ms | 底栏指示 |
| NavLabel | 180ms | 选中标签显现 |

### 6.2 缓动（`MotionTokens.kt:48-63`）

`Standard (0.4,0,0.2,1)`、`Emphasized (0.2,0,0,1)`、`FastOutSlowIn (0,0,0.2,1)`、
`Linear`、`Accelerate (0.4,0,1,1)`。

### 6.3 弹簧（`MotionTokens.kt:65-150`）

`bounce 0.6/200`、`snappy noBouncy/medium`、`gentle 0.85/low`、`extraBouncy 0.5/300`、
`liquidTap 0.62/460`、`liquidSettle 0.80/420`、`segmentedSettle 0.60/400`、
`navSettle 0.60/310`、`navRelease 0.55/290`、`liquidJellyRebound 0.66/320`、
`liquidFollow 1/1100`、`liquidMenu 0.78/520`。

### 6.4 入场与换屏

- 模块入场（`ModuleMotion.kt:17-60`）：**380ms 入场 + 75ms 组间错峰，最多 4 组**，
  位移 16dp，曲线 `FastOutSlowIn`；退出 170ms。
- 换屏（`NavigationMotion.kt:24-146`）：
  进场 8dp、离场 12dp、离场缩放 0.985；
  `pageSpring 0.74/180`；进场 alpha 在 15% 进度内显形，离场在 35% 内淡出；
  中断从当前进度/速度接续。
- 数字变化（`AnimatedCount.kt:39-99`）：位宽固定、每位 `180ms` 上/下滑，容器尺寸不变；
  非纯数字 `AnimatedValueText` 入场 220ms、位移 1/4、退出 120ms。
- 现场切件（`NavigationMotion.kt:163-166` 的 `SchedulePagerMotion`）：邻页缩 **0.97**、淡 **0.22**。

快递侧的落点：
- `MotionProfile.pageSpring()` = `0.74/180`；`Navigation` = 8/12/0.985（同源）。
- `ModuleMotion` 380/75/16dp（同源）。
- `AnimatedValueText` 本轮从 280ms/半高改为 **220ms/1/4 高 + 退出 120ms**；
  `MotionProfile.PressMillis` 从 150 改为 **120**。
- 现场切件已有 0.97/0.22（`OnsiteScreen.kt:292-294`）。

---

## 7. 差异清单（改前 → 改后）

| # | 维度 | 参考 | 改前 | 本轮处理 |
|---|---|---|---|---|
| 1 | 暗色主题 | 完整 `DarkColorScheme` + 夜间壁纸 | 强制浅色，无暗色 | 新增 `Palette.kt` 明/暗两套；`ShowCodeTheme` 跟随系统；`MainActivity` 预置快照避免首帧闪 |
| 2 | 正文/标题字重 | 400/600/700 | 大面积 700/800 | `ShowCodeText` 收成 400/500/600/700；ExtraBold 清零 |
| 3 | 标题字阶 | 20/28 SemiBold | 20/27 ExtraBold | 20/28 SemiBold |
| 4 | 卡片圆角 | 16dp medium | 14dp | `ShowCodeShapes.Md = 16dp`（96 处引用统一生效） |
| 5 | 数值变化 | 220ms、位移 1/4、退出 120 | 280ms、位移 1/2 | `AnimatedValueText` 改成参考值 |
| 6 | 按压退出 | 120ms | 150ms | `PressMillis = 120` |
| 7 | 暗色环境层 | 夜间预设 base/glow/shade | 无 | `drawEnvironment` 读主题快照，色值按 `AppAppearance.kt:35-41` |
| 8 | 暗色底栏 | 选中白 0.20、无折射 White@0.18 | 固定亮色档 | `GlassMaterials` 新增暗色档，底栏三处读取 |
| 9 | 页面边距 | 18dp | 20dp | **保留 20dp**（品牌密度，见 3.2） |
| 10 | 三态色 | 语义 success/warning/danger | 品牌鲜色 | **保留品牌三态**，只补暗色提亮 |
| 11 | 图标 | 全线稿 | 底栏线稿 + 内容区 Material | 底栏已同源；内容区保留可读性优先，记为品牌例外 |
| 12 | 二维码 | — | 明暗跟随表面色 | **固定黑白**（反色码多数扫码器读不出） |
| 13 | 玻璃取样 | blur 折真实内容 | `layerBackdrop` 建了但没挂，底栏只是半透明叠加 | `ShowCodeRoot.kt` 的 `AnimatedContent` 接上 `.layerBackdrop(glassBackdrop)`，背后内容真模糊 |
| 14 | 亮色底栏边界 | 白 0.28 铺在 #F0F2F4 上 | 白叠白，轨道/选中胶囊在 #F9FAFD 卡面上几乎不可见 | 6% 中性压暗 + 1dp 描边（亮墨 0.10 / 暗白 0.18） |
| 15 | 系统栏图标 | 暗色用浅色图标 | 固定深色（暗色下时间/电池消失在深底） | `MainActivity` 按 night 设置 `isAppearanceLightStatusBars/NavigationBars` |
| 16 | 演示反馈浮层 | 浮层自带面与边 | 6% 灰底，压在商品标题上 | `SurfaceFloating` + `Hairline` + 6dp 阴影，落在「已取」按钮上方 |

---

## 8. 只读校验

抽取时对参考工程 **source-only**（`app/src/main` + `gradle.properties`）565 个文件做
「相对路径 + 字节数 + mtime」的 SHA-256：

| 时点 | 文件数 | SHA-256 |
|---|---|---|
| 抽取前 | 565 | `4F0ADF846967DB696E10688B46607C0D3237DC97551F4125751B9237B1B99251` |

**改完复查（本轮收尾）**：

- 参考工程内层 `capybara-course-helper\` 是 git 工作树（外层 `capybara-course-helper-v1.0.6\`
  不是）。`git status --short` **无输出**（干净），分支 `## v2-main`，
  HEAD `f4e4a02e20c9ceb0ae78ae4d5cb8ea2ca7023886`（2026-10-26 23:08:35 +0800）。
- 565 个 source-only 文件中，Latest mtime = `2026-10-26T22:00:02+08:00`；
  按「今天 2026-11-04 之后有修改」筛选的结果为 **0 个**。
- 即：参考目录没有写入、没有新增文件、没有改配置；本轮全部截图与产物都落在
  `e:\express\docs\screenshots\`。

---

## 9. 对照截图

明 / 暗 × 首页 / 现场 / 详情，均在 MuMu（Android 15，1080×1920）实机截取：

| 场景 | 亮色 | 暗色 |
|---|---|---|
| 首页 | [design-home-light.png](screenshots/design-home-light.png) | [design-home-dark.png](screenshots/design-home-dark.png) |
| 现场 | [design-onsite-light.png](screenshots/design-onsite-light.png) | [design-onsite-dark.png](screenshots/design-onsite-dark.png) |
| 详情 | [design-detail-light.png](screenshots/design-detail-light.png) | [design-detail-dark.png](screenshots/design-detail-dark.png) |

暗色逐屏核对另存：[nav-solid-dark.png](screenshots/nav-solid-dark.png)、
[nav-solid-dark-settings.png](screenshots/nav-solid-dark-settings.png)、
[_verify_onboarding_dark.png](screenshots/_verify_onboarding_dark.png)。
二维码画布在明暗下都固定黑模块 / 白底（`QrCanvas.kt`），不跟随主题反色。

---

## 10. 导航栏（运行态实测）

参考应用 `com.example.course` 直接跑在 MuMu（1080×1920，density 480 → 360×640dp）。
下面每一项都从**运行截图**量得，截图存 `docs/screenshots/`；源码只用来解释数值成因，
不作为唯一依据。

### 10.1 量到的值

| 项 | 实测 | 来自哪张截图 / 怎么量 |
|---|---|---|
| 轨道高度 | **65dp**（y=1703–1898，195px @3x） | `ref-nav-list.png`；x=900 竖线扫边缘 |
| 轨道左右边距 | 轨道外缘距屏边约 **13dp**（10dp 外边距 + 6dp 内边距 + 1dp 阴影） | `ref-nav-list.png`；左缘约 x=40px |
| 轨道形状 | 全圆角胶囊（radius = 高/2），玻璃底、真实模糊 | `ref-nav-list.png` / `ref-nav-settings-dark.png` |
| 标签字形 | **12sp / 16dp 行高**（文本节点 48px 高 @3x） | `ref-nav-list.png`；uiautomator 文本 bounds `121,1817–187,1865` |
| 标签字重 | 选中加粗，未选中常规 | `ref-nav-settings.png`（设置）/ `ref-nav-list.png`（课表） |
| 图标画布 | **24dp**；线宽约 6px = 2dp；选中约 20% 同色填充 + 描边 | `ref-nav-list.png`；选中日历字形 56×62px（18.7×20.7dp） |
| 图标与标签 | 布局间距 2dp；视觉间隙 8–10dp（图标 24dp 盒 + 文本行高留白） | `ref-nav-list.png`；图标底 1798、文本顶 1828（30px=10dp） |
| 选中态胶囊 | **占满一格**：65.6dp × 51dp（201×153px），白色半透明，圆角全圆 | `ref-nav-list.png`；放大图 `ref-nav-tab-zoom.png` |
| 选中色 | 图标 + 标签同色 **#5794FF**（亮）/ **#53BAFF**（暗） | `ref-nav-list.png` / `ref-nav-settings-dark.png` 像素采样 |
| 未选中色 | 图标/标签中性灰 **#8A8A8E**（亮）/ **#656C78**（暗） | 同上 |
| 底栏与内容 | 无分割线，浮动玻璃；内容可滚到 bar 底下被模糊 | `ref-nav-list.png` / `ref-nav-settings.png` |
| 安全区 | 轨道底 1898，屏底 1920 → 底边距约 **7dp** | `ref-nav-list.png` |
| 触控区 | 每格 **64.7 × 53.7dp**（194×161px），≥48dp | `ref-nav-list.png`；uiautomator clickable bounds |
| 详情页 | **不显示底栏**（详情是盖住底部的 sheet） | `ref-nav-detail.png` |
| 切换动效 | 胶囊整体滑动（没有独立滑块轨道）；落定约 **300ms**，内容先切、胶囊后落定，未见回弹 | 运行录屏 `ref-nav-motion.mp4` + 连拍 `ref-nav-motion-sheet.png`；源码弹簧 `navSettle 0.60/310` |
| 暗色胶囊 | 约 **#454953**（在轨道上叠约 18% 白），不是亮色白胶囊 | `ref-nav-settings-dark.png`（采样 x=900,y=1750） |

### 10.2 我们重做后的落点

| 项 | 我们 | 与参考的关系 |
|---|---|---|
| 结构 | 3 项：待取件 / 身份码 / 设置 | 故意不同（我们的实际页面）；几何按同一公式 |
| 轨道 | **实底**：亮 `#FAFAFE` / 暗 `#191E26` 不透明面；高 65dp；10dp 外边距 | 几何照搬；材质改成实底（用户最终决定，不再是参考的玻璃） |
| 分界 | 1dp hairline（亮 `#E3E5EE` / 暗 `#2A303B`）+ 轻投影（亮 4dp / 暗 2dp） | 参考靠模糊+高光；我们改成实底分界，避免在内容区上切硬线 |
| 标签 | 12sp / 16dp 行高；选中加粗 | 照搬 |
| 图标 | 24dp 画布、16/256 圆头描边、duotone（选中填充 24%） | 照搬同一网格与笔画；字形换成我们的箱子/二维码/滑杆 |
| 选中胶囊 | **实底**：宽 = 一格（tabWidth），高 = indicatorHeight，全圆；亮 `#FFFFFF` / 暗 `#2A303A` + 1dp hairline | 结构与尺寸照搬；材质改实底 |
| 选中色 | 亮色图标 `#0A84FF`、标签 `#0057D9`；暗色 `#88BFFF` | 同一色相；标签压深是为了 12sp 的 AA（参考 `#5794FF` 约 2.5:1，我们约 6.3:1） |
| 未选中色 | `InkMuted #59616F` | 比参考 `#8A8A8E` 深；参考约 3.2:1，我们约 5.6:1，故意不同 |
| 触控区 | 每格约 107 × 66dp | 比参考的 54dp 高；故意放大 |
| 动效 | `navSettle (0.60, 310)` 弹簧；页面切换复用同一发条（8dp 滑入，180ms） | 照搬节奏；连拍未见跳帧/抖动 |

**实底化时撤掉的层（按绘制顺序）**：

1. 轨道 `drawBackdrop`：blur 10/14dp、饱和补偿 ColorMatrix、Highlight 0.16、Shadow 0.08、图层按压缩放。
2. 选中胶囊的 `glassLens`（31/32 离屏折射）、`drawBackdrop` 的 lens/highlight/shadow/innerShadow、白 0.34 / 黑 0.1 半透明表面。
3. 隐藏 tint 副本层（`tabsBackdrop` / `tabsTintBackdrop` / `rememberCombinedBackdrop`）——它会在宽胶囊里印出第二份标签。（2026-11-11 试恢复后撤：球采副本时 `lens()` 把副本位移，与球上的正常标签错开成重影；三格布局改为球折页面内容 + 放大玻璃圈。）
4. 为通透加的 6% 中性压暗、0.06/0.10 描边叠加、14dp 模糊与 1.15 饱和度。
5. 底栏不再读 `backdrop` / `glassTier` / `GlassLensFreshness`；没有采样，就没有“采到上一帧”的重影通道。
6. 收起形态（`MinimizedNavCapsule`）同样改为实底 + hairline，不再 blur。

### 10.3 截图

参考应用（只操作，未改其目录）：

- 列表：[_ref 列表页底栏](screenshots/ref-nav-list.png)、放大图 [ref-nav-tab-zoom.png](screenshots/ref-nav-tab-zoom.png)
- 设置：[_ref 设置页底栏](screenshots/ref-nav-settings.png)、[_ref 暗色设置页底栏](screenshots/ref-nav-settings-dark.png)
- 详情（无底栏）：[_ref 课程详情](screenshots/ref-nav-detail.png)

我们（实底版）：

- 亮色：[首页](screenshots/nav-solid-light.png) · [设置](screenshots/nav-solid-light-settings.png)
- 暗色：[首页](screenshots/nav-solid-dark.png) · [设置](screenshots/nav-solid-dark-settings.png)
- 连切：[20 次连切后](screenshots/nav-solid-rapid20.png) · [连切中间帧](screenshots/_solid_mid0.png)

## 11. 切换重影：根因与修复（运行帧证据）

用户反馈切页有“重影”。逐帧抓取（`screenrecord` + 连拍 `screencap`）后，按像素判据逐项排除：

| 候选原因 | 判据 | 结论 |
|---|---|---|
| 进出两页同时半透明叠一屏（crossfade 残留） | 修前帧 `ghost-before-identity-to-settings.png`：设置页 dark 文本已在最终位置，同时还能看到身份码的卡片/“+ 存一张”残影，残影亮度约为原内容的 30% | **主因**：`NavigationPages` 同时画旧页（alpha 1→0）和新页（alpha 0→1），两页各带 ±8/12dp 位移 |
| 内容滑动没有裁剪、溢出到相邻区域 | 修前帧的内容都还在窗口内，没有越出底栏或屏幕的像素 | 不是主因，但补了 `clipToBounds()` |
| 旧页未移除就新页入场 | 修前帧旧页的独有文案（“身份码”/卡片）在中间帧仍完整可见 | **同属主因**（旧页仍在 `pages` 里被绘制） |
| 底栏模糊层采到上一帧画面 | 修前透明底栏确实会同时折到两页；改为实底后底栏不采样任何内容，通道消失 | 次级原因，实底化后彻底移除 |
| 胶囊与标签同时动画留下双影 | 中间帧导航一度出现两个泛蓝标签；另发现隐藏 tint 副本会被宽胶囊折射成第二份标签 | 次级原因：颜色改为过半硬切，删掉 tint 副本层 |

**修复**：

1. `NavigationPages` 改单层：同一时刻只画 `target`，旧页在点击那刻移除；整层 `clipToBounds()`。
2. 入场 `alpha` 直接给 1，避免“旧页已移除、新页还在淡入”的一帧空白；位移仍从 ±8dp 起，`pageSpring(0.74,180)` 不变。
3. 模块错峰只在冷启动第一进场播放；主屏切换 `entrance.snapTo(1)`，不再让内容从 0 重播。
4. `LaunchedEffect(selectedIndex)` 去掉 `targetValue != selectedIndex` 早退，快速连切/取消时不会留下“页面已到新 tab、胶囊还停在旧格”的死锁。
5. 导航颜色过半硬切，胶囊与图标填充仍连续；删除隐藏 tint 副本与折射采样，宽胶囊不再印出第二份标签。
6. 最终按用户要求改成**实底**，底栏不再有 blur/backdrop 采样，采样类重影通道整体消失。

**修复后验证帧**：

- 首页→身份码：[_solid_mid0.png](screenshots/_solid_mid0.png)（单层身份码，导航已一致）
- 身份码→设置：[_solid_mid1.png](screenshots/_solid_mid1.png)（单层设置页）
- 设置→首页：[_back0.png](screenshots/_back0.png)、[nav-solid-light-back.png](screenshots/nav-solid-light-back.png)
- 20 次连切后：[nav-solid-rapid20.png](screenshots/nav-solid-rapid20.png)
- 暗色：[nav-solid-dark-mid.png](screenshots/nav-solid-dark-mid.png)、[nav-solid-dark-after.png](screenshots/nav-solid-dark-after.png)

亮/暗、三个 tab 双向均逐帧看过：中间帧只有一页内容，没有旧页卡片/标题残留；导航最后与页面一致；连切 20 次后静帧无残影。
