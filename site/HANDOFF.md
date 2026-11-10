# 卡皮巴拉快递助手 · 官网重做交接文档

> 给下一个接手 / 评审的模型或设计伙伴。
> 最后更新：2026-11-10（第二轮主控指令执行后）
> 状态：**第二轮已落地；未提交、未推送；build 通过；零第三方运行时请求已验证**
> 预览：`http://127.0.0.1:4173/`（当前 `vite preview` 还在跑）
>
> 如果对方看不到本地图片：把 `docs/handoff/` 里的 jpg 一起上传；下面的文字描述已经尽量自包含。
>
> **HTML 快照也给评审模型看：`docs/handoff/page.html`**（完整渲染 DOM + 内联编译 CSS，已移除脚本；用来核对真实 class / inline style / 文案 / 卡片结构）。`docs/handoff/index-entry.html` 是 Vite 入口 HTML。**注意：本文 §2–§8 是第二轮之前的早期记录；当前实现状态以 §13 为准。**

---

## 0. 先读这一段

这是一个 **React 19 + Vite 7** 的官网重做，不是改版。旧的纯静态页正在被 `/site` 里的新站替代。

用户对首屏已经认可。用户后来把另一份「Awwwards 级 React Bits 主控指令」交给我，要求把下半页从「第二种整齐卡片模板」继续推进成**每节不同构、有真实物理交互的互动标本板**。第二轮已落地：

1. **首屏**：保留版式；丝带**只保留一张静态 WebP**（`GridDistortion` 已移除：它和底图会叠成第二层，扭曲时露出底图边缘）；kicker 用 `DecryptedText`，英文标语用 `VariableProximity`。
0. **全局背景**：首屏的 Iridescence 已提升为固定 `site-field` 贯穿全站；所有 `.poster-section` 透明，实色卡片浮在同一片虹彩场上；顶部 CardNav 已移除。
2. **过渡**：`CurvedLoop` 下方加 `ScrollVelocity` 双轨变速封条；安全 → 省心之间加扫描交接带（`Interstitial`）。
3. **01 方便**：`LaserFlow` 解析带、`ElectricBorder` 本地 PARSER、`SplitFlapText` 取件码、`TiltedCard` 取件点卡。
4. **02 安全**：反转构图（左 `0` 标本台 + 右 `PRIVATE` 头卡）；`Shredder` 实时粉碎隐私负资产；信封封签换成可掀的 `StickerPeel`；审计表保留。
5. **03 省心**：`CometDial` 表盘与 `ClockDemo` 双向联动；取件凭证用 `TiltedCard`。
6. **04 边界**：左侧 sticky 宣言卡 + `Ferrofluid` 底纹；右侧 6 条 `ScrollStack` 档案卡；`NO` 用 `GlitchText`。
7. **05 安装**：`TearTicket` 可撕副券提货单；`SpotlightCard` 两道校验；`GlassIcons` 规格卡。
8. **响应式**：平板 681–980 的 companion 改成左大数字 / 右内容两栏；0 卡在平板改双栏；手机隐藏 CometDial，TearTicket 降级为普通提货单。`VERSION` 已去除硬编码。
9. **运行时**：已用 Playwright 记录 Network，除本地资源外 **0 请求**（此前 `VariableProximity.css` 的 Google Fonts @import 已删除）。
10. **顶部 CardNav 已移除**（用户觉得悬浮灰条多余）；首屏直接进入，导航由左侧 Rail / 首屏 stage nav 承担。
11. **没有 commit，没有 push。** 不要替用户 commit。

仍未做 / 可继续：`Lanyard` 吊牌、`HoloCard` 点卡、`WakeSlider`/`ScrubField`/`SlideCommit`、来源卡 `CardSwap`/`Folder`、`Scanner`/`Dither` 背景、`TextPressure` 大标题。它们属于第二轮指令的剩余装饰层，不是现有页面的必要条件。

---

## 1. 用户目标与不可违反的约束

### 1.1 用户想要的感觉

- PC 优先，内容宽度约 1700px，移动端自适应。
- 高端、有质感、原创，**不要模板站，不要极简**。
- 国际化、大胆、允许复杂和冲击力，可以「炫技」。
- 首屏大标题用英文：`CAPYBARA EXPRESS`，中文名 `卡皮巴拉快递助手` 作为副标识。
- 核心叙事：**方便 / 安全 / 省心**。功能是证据，不是主角。
- 不要「柜机」这类行业黑话，不要教程式的取件流程。
- 首页**不放真机截图**。真机截图以后单独做一页；`Screens.jsx` 和 assets 先保留。
- 参考图的感觉：全息 X / C 海报卡片、粉紫青薄荷奶油渐变、黑底、点阵网格、细边框、编号 / 标签 / 条形码、编辑设计（editorial）而不是 SaaS 落地页。

### 1.2 硬约束

- **零第三方运行时请求**：不能有 Google Fonts、CDN、analytics、统计 SDK、远程图片。字体全部系统字体或本地资源。
- 所有资源本地：`public/assets/`。
- 所有数字必须能在仓库里核对。演示数据必须标注 `演示数据` / `DEMO DATA`。
- 不夸大能力，不虚构能力。具体边界见 §6。
- 主色**不能是绿色**。绿色只作为安全 / 临期 / 过期风险的状态色。
- 不要把取件码做成一张巨大的海报字体元素（用户明确讨厌）。
- 不 commit / push，除非用户明确同意。
- 可访问性：skip link、aria、键盘 Escape / Enter、焦点返回、`prefers-reduced-motion`（组件里已有的尽量保留）。

### 1.3 可以改文案

用户给过明确授权：

> 「给你最高的权限，文案和内容也允许你改变，为了设计做更好的优化。」

所以为了版式可以改标题、缩短段落、重组卡片；但不能违反 §1.2 的真实性约束。

---

## 2. 现在长什么样

以下截图是 **1920 × 1400 视口截图**，不是整页长图；每张只覆盖当前屏。除特别说明外，都是加包裹层之前的构建。

### 2.1 首屏（用户已认可）

![桌面首屏](docs/handoff/desktop-hero.jpg)

- 固定深色烟熏玻璃 `CardNav`：左 Menu、中 CAPYBARA、右 下载 APK；滚动进度条在顶部。
- 英文 wordmark `CAPYBARA` / `EXPRESS`，用 ShinyText 做金属 / 全息流光；最后一个 A 和句点带洋红。
- 中文名、英文 tagline `PARCELS ARRIVE. HASSLE DOESN'T.`、中文 `快递到了，麻烦没到。`
- 背景 Iridescence + 白色雾层；右侧是 Blender 渲的 3D 虹彩丝带 WebP。
- 丝带上盖了一个自定义「玻璃透镜」：复制一份丝带做 `background-image` 裁切，用 SVG `feTurbulence` + `feDisplacementMap` 弯曲，写着 `LOCAL / 100%`。
- 左下 Magnet 下载按钮；右下标尺 / 条形码；左侧竖排 stage 导航；底部 facts；再往下是弯曲 marquee。

### 2.2 方便（01 EFFORTLESS）

![桌面方便区](docs/handoff/desktop-convenience.jpg)

- 黑卡头：`EFFORTLESS` + `件，自己进来。` + 全息 X。
- 右上是粉紫青渐变 companion：巨大的 `01.`、三个来源索引、条形码。
- 下面 `INPUT → LOCAL PARSER → GROUPED BY POINT` 的细线路由。
- 三张来源卡：A 平台账号（黑）、B 短信 / 通知（渐变）、C 手动 / 选中文字（白）。每张右侧有巨大的描边字母 A/B/C。
- 黑卡「本地 PARSER」：点阵网格里的 `本地` 印章。
- 两张点海报：丰巢 2 件（1 件超时，risk）、菜鸟 3 件（安全，safe）；都标 `DEMO DATA`。
- 黑底大字引用：「翻三个 App、在短信里找那串码、到了柜子不知道第几格——这些动作，本来就不该存在。」
- 三张 note 卡：不是更漂亮的列表 / 不用记也不用找 / 搜索只过滤不重排。

### 2.3 安全（02 PRIVATE）

![桌面安全区](docs/handoff/desktop-security.jpg)

- 粉紫渐变头：`PRIVATE` + `它没有服务器。`，右侧巨大全息 C。
- 白卡 companion：巨大的 `0`，三行「账号 / 广告 / 统计 SDK」都是 0。
- 白卡信封：`NO CLOUD` + 粉色斜杠；信封上写「收件人：这台手机 / 寄件人：你自己 / 内容：取件码、取件点、时间 / 备注：不出门」；粉色的「不出门 / LOCAL」印章。
- 黑卡数据边界：解析 / 取件数据 / 平台凭据 / 广告统计 / 平台同步 / 更新检查，带 LOCAL / ENCRYPTED / NONE / OPTIONAL 标签。
- 全宽引用：「隐私不是一句承诺，是没有那条路。」

### 2.4 省心（03 CALM）

![桌面省心区](docs/handoff/desktop-calm.jpg)

- 黑卡头：`CALM` + `打开，就知道。`，右侧巨大全息圆环。
- 渐变 companion：巨大的 `18`，下面 `HOURS FREE · 丰巢默认 · 超时 0.5 元 / 12 小时 · 封顶 3 元`；刻度尺 + 条形码。
- 全宽黑卡 `03 / CUSTODY CLOCK / LIVE DEMO`：交互式保管时钟。
  - 0–24h 已存放滑块；18h 内安全；超过 18h 进入临期 / 计费；费用 `min(3, ceil(超时小时 / 12) * 0.5)`。
  - 左侧大号时间 + 状态；中间时间轴；右侧白色「取件凭证」卡；背景是 LiquidChrome。
- 三张 note 卡：只有三种颜色 / 8 秒界面自己退场 / 取完最后一件下一站已经在了。
- 全宽大字：「你不需要记住任何事。打开它，就知道。」

### 2.5 边界（04 BOUNDARIES）

![桌面边界区](docs/handoff/desktop-boundaries.jpg)

- 白卡头：`BOUNDARIES` + `有些事，它不做。`，右侧全息 X（会轻微压到标题末尾，是有意的大胆叠压；可接受，也可以再收）。
- 渐变 companion：巨大的 `NO` + 三条 `NO GUESS / NO MERGE / NO PATCH`。
- 白卡 dossier：`CE-BND-04 / 使用边界`，6 条条款：
  1. 不猜取件点（主动放弃）
  2. 不自动合并（主动放弃）
  3. 不假装补全漏件（不骗你）
  4. 不重现会话码（系统限制）
  5. 不做后台自动同步（主动放弃）
  6. 不做 iOS，也不做锁屏组件（系统限制）
- 右侧黑卡 `MARGIN / 04`：sticky，粉色「不猜 / 不补」印章；「这份清单不是免责声明。有些是主动放弃，有些是系统不给。」；开 issue 链接。

### 2.6 安装（05 INSTALL）

![桌面安装区](docs/handoff/desktop-install.jpg)

- 黑卡头：`INSTALL` + `拿走它。`，右侧巨大全息 C。
- 渐变 companion：巨大的 `0.4.6`，Android 8.0+ / 12.7 MB / GPL-3.0。
- 白色 `dl-ticket`：巨大的 `0.4.6`、规格行、StarBorder 主下载按钮（黑底 / 粉青流光）、GitHub 备用源、无 iOS 说明。
- 白色 verify 卡：两道校验，SHA-256 与签名指纹，复制按钮和命令；底部说明签名密钥在仓库外。
- 黑卡 spec：GlassIcons 2×2（Android / LOCAL ONLY / NO CLOUD / GPL-3.0），标签是白色 8px。
- 底部安装脚注：Android only、GPL-3.0、GitHub / Gitee / 反馈。

### 2.7 移动端 390px

![手机方便区](docs/handoff/mobile-convenience.jpg)
![手机安全区](docs/handoff/mobile-security.jpg)
![手机省心区](docs/handoff/mobile-calm.jpg)

- 所有 span 收成单列；头部标题 `clamp(2.6rem, 13vw, 4rem)`；`BOUNDARIES` 最紧，但当前 390 仍在卡内，右侧约 16px 余量。
- companion 仍是「大数字在上、索引在下」；`18` 卡的时间说明会换成两行，最后一行只剩「元」——可接受，但可以再压一版文案。
- 卡片阴影缩到 6px；holo X/C 缩到 68% / 64%，右下角出血；body `overflow-x: clip` 让 8px 的 scrollWidth（holo + StarBorder canvas）不可见。
- 移动端导航只保留 CAPYBARA + Menu；Menu 展开是 CardNav 的卡片菜单。

### 2.8 平板 768px（当前弱点）

![平板方便区](docs/handoff/tablet-convenience.jpg)
![平板安全区](docs/handoff/tablet-security.jpg)

- 980px 以下所有 span 变全宽；head / companion 上下堆叠。
- 问题：companion 全宽后，`0` / `01.` / `18` / `NO` / `0.4.6` 都在左边，索引也在左下，右侧是一大片渐变。看起来像一条宽 banner，不如桌面和手机紧凑。
- 这就是 §8 要修的第一件事。

### 2.9 给评审模型的 HTML 快照

直接在浏览器打开：

- **`docs/handoff/page.html`** — 从 4173 实时渲染抓下来的**完整 DOM**。`dist/assets/index-5uMcUldI.css` 已内联进 `<head>`，`<script>` 已移除，资源路径改成 `../../public/assets/`。
  - 能看到真实的 class、inline style、文案、SVG、卡片 / dossier / ticket 结构，适合让另一个模型直接读 HTML。
  - 它和截图一样，是**第二轮主控指令之后的当前构建**。
  - 静态文件里 **canvas 驱动的效果不会动**：Iridescence 背景、LiquidChrome 时钟、GlassIcons 折射、ClickSpark 都会空白或静止。首屏丝带是 `<img>`，仍然看得到；玻璃透镜、标题、marquee、海报卡片都在。
  - 因为没有 JS，CardNav 菜单不能展开，ClockDemo 滑块不能拖，复制按钮不能点。要看交互请用 4173。
- **`docs/handoff/index-entry.html`** — `site/index.html` 的源入口，只有 meta / title / `#root`。真正的页面结构在 `page.html` 和 `src/components/*.jsx`。

重新生成快照（下一轮 build 后）：

```bash
# 1. 从预览抓渲染后的 DOM
chrome --headless=new --disable-gpu --no-sandbox --hide-scrollbars \
  --window-size=1920,1400 --virtual-time-budget=5000 \
  --dump-dom http://127.0.0.1:4173/ > docs/handoff/_raw.html

# 2. 用 Node 把 dist 里的 CSS 内联、去掉 script、改写 ./assets/ 路径
#    见 git 历史里的生成脚本；或按 HANDOFF 里描述手工处理
```

注意：`page.html` 已是当前构建，`theme-color` 为 `#f2f3f6`；它不含 JS，所以 canvas 效果是静止的。视觉以截图为准。

---

## 3. 技术栈与运行

| 项 | 值 |
|---|---|
| 框架 | React 19.2 |
| 构建 | Vite 7.1 |
| Node | `>=22.12`（`package.json` engines） |
| 样式 | 纯 CSS，无 Tailwind |
| 路由 | 无；单页锚点滚动 |
| 部署 | Render 根路径 / GitHub Pages 子路径，`vite.config.js` 里 `base: './'` |
| 运行时第三方请求 | 0 |
| 字体 | 系统字体栈，见 `base.css` 的 `--sans / --serif / --latin / --mono` |
| 图标 | 内联 SVG；没有图标库运行时 |

### 命令

```bash
cd E:\express\site
npm install
npm run dev                 # http://127.0.0.1:5173
npm run build               # dist/
npm run preview             # http://127.0.0.1:4173
```

当前 4173 是旧构建；改完源码要重新 `npm run build`，`vite preview` 才会更新（如果 preview 进程缓存了 dist，重启一下）。

### 截图 / QA 注意

- Chrome headless 对 `#hash` 深链 + 懒加载 / GPU 不稳定：直接打开 `#security` 经常截到首屏或白屏。
- 可靠做法：打开首页，等 `domcontentloaded`，然后
  ```js
  window.scrollTo({
    top: document.getElementById('security').offsetTop - 90,
    behavior: 'instant'
  })
  ```
  再等 700–1000ms 截图。
- 真实浏览器里 hash 深链是可用的：`App.jsx` 在 mount 后 60ms 会 `scrollIntoView`。

---

## 4. 文件地图

```text
E:\express
├─ .github/workflows/pages.yml      # Node 22, npm ci, build, upload site/dist
├─ .gitignore                       # site/node_modules, site/dist
├─ index.html                       # 旧站入口（当前工作不在这里）
├─ site/
│  ├─ package.json
│  ├─ vite.config.js                # base './'
│  ├─ index.html                    # 双语 title, OG/Twitter, favicon
│  ├─ README.md                     # ⚠️ 旧设计说明，尚未更新
│  ├─ HANDOFF.md                    # 本文
│  ├─ docs/handoff/                 # 当前截图 + HTML 快照
│  │  ├─ page.html                  # 渲染后的完整 DOM + 内联 CSS（无 JS）
│  │  ├─ index-entry.html           # site/index.html 源入口
│  │  └─ *.jpg                      # 桌面 / 平板 / 手机截图
│  ├─ public/assets/
│  │  ├─ holo-ribbon-v4.webp        # Blender 丝带（55KB）
│  │  ├─ brand-bars.svg
│  │  ├─ og-card.png                # 1200×630
│  │  ├─ icon.png
│  │  └─ *.jpg                      # 真机截图，留给以后的单独页面
│  └─ src/
│     ├─ main.jsx
│     ├─ App.jsx                    # Header / Rail / Hero / Marquee / 5 sections / Footer
│     ├─ data.js                    # VERSION, APK_SIZE, LINKS, NAV, SCREENS, hashes
│     ├─ components/
│     │  ├─ SiteHeader.jsx          # CardNav 深色玻璃
│     │  ├─ Rail.jsx                # 左侧 stage 导航
│     │  ├─ Hero.jsx                # 英文 wordmark + 3D 丝带 + 玻璃透镜
│     │  ├─ MarqueeBand.jsx         # CurvedLoop
│     │  ├─ Poster.jsx              # ⭐ PosterHead / PosterCompanion / PosterCard / HoloX / HoloC / HoloRing / BarcodeFoot
│     │  ├─ Convenience.jsx         # 01
│     │  ├─ Security.jsx            # 02
│     │  ├─ Calm.jsx                # 03 + ClockDemo
│     │  ├─ ClockDemo.jsx           # 18h 交互 demo
│     │  ├─ Boundaries.jsx          # 04 + dossier
│     │  ├─ Install.jsx             # 05 + GlassIcons + StarBorder
│     │  ├─ SiteFooter.jsx
│     │  ├─ Common.jsx              # Arrow / Seal / Barcode / Tag
│     │  ├─ Screens.jsx             # ⚠️ 未挂到首页，留给真机截图页
│     │  ├─ Masthead.jsx            # ⚠️ 未使用
│     │  ├─ GlassSurface.jsx/.css   # ⚠️ 已试过但玻璃在 Chrome 里发白，未用
│     │  └─ reactbits/              # 本地复制的 React Bits 组件
│     │     ├─ Iridescence
│     │     ├─ ShinyText
│     │     ├─ CurvedLoop
│     │     ├─ CardNav
│     │     ├─ GlassIcons
│     │     ├─ StarBorder
│     │     ├─ Magnet
│     │     ├─ ClickSpark
│     │     ├─ LiquidChrome
│     │     ├─ SpotlightCard      # 未使用
│     │     └─ DepthText          # 未使用
│     └─ styles/
│        ├─ base.css               # tokens, paper/ink, noise, shell
│        ├─ chrome.css             # 旧模块内部细节（envelope / audit / clock / dossier / ticket）
│        ├─ header.css             # CardNav 覆盖
│        ├─ hero.css               # 首屏
│        ├─ marquee.css
│        ├─ sections.css           # 旧 section-head / ghost 等，部分仍被 dossier / clock / ticket 使用
│        ├─ poster.css             # ⭐ 下半页海报系统（~1240 行）
│        └─ responsive.css         # 媒体查询（部分旧规则）
```

### 数据入口

`src/data.js`：

- `VERSION = '0.4.6'`
- `APK_SIZE = '12.7 MB'`
- `LINKS`：apk / releases / releaseTag / github / gitee / issues / readme / privacy
- `NAV`：5 项：convenience / security / calm / boundaries / install，编号 01–05
- `SCREENS`：8 张真机截图元数据（保留，首页不用）
- `FINGERPRINT`、`APK_SHA`
- `asset()` 处理 `base` 相对路径

⚠️ `PosterHead` / `PosterCompanion` 的底部文字里还硬编码了 `0.4.6`，版本升级时要一起改（或者改成读 `VERSION`）。

---

## 5. 设计系统

### 5.1 Tokens

`base.css` 的核心：

```css
--paper: #f2f3f6;
--paper-hi: #ffffff;
--ink: #08090b;
--ink-3: #5d636e;
--line: rgba(8, 9, 11, 0.16);
--red: #c21f72;          /* 洋红，主强调色 */
--red-on-dark: #ff7ac8;
--cream: #f7f8fa;
--dark-line: rgba(247, 248, 250, 0.16);
--holo: linear-gradient(135deg, #ff9edb, #c9b8ff 25%, #7ee8fa 50%, #b8ffd9 72%, #ffd6b8);
--safe / --amber / --risk 只做状态语义
--gutter: clamp(20px, 4vw, 64px);
--page: 1700px;
```

字体：

```css
--latin: "Bodoni MT", "Bodoni 72", Didot, "Times New Roman", Georgia, serif;
--serif: "Songti SC", "STSong", "Noto Serif CJK SC", "Source Han Serif SC", SimSun, Georgia, serif;
--sans: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", system-ui, sans-serif;
--mono: "SFMono-Regular", "Cascadia Mono", Consolas, monospace;
```

### 5.2 Poster board

- `.poster-section`：浅色 section；`--dark` 黑底。
- `.poster-board`：12 列 grid，gap `clamp(12px, 1.4vw, 20px)`。
- `.span-4/5/6/7/8/12`。
- `.poster-card`：`position:relative; overflow:hidden; display:flex; flex-direction:column; gap:...; padding:...; border; box-shadow: 9px 9px 0`。
- 伪元素 `::before` 内缩 9px 的细线框；`::after` 点阵（黑卡白点、渐变卡黑点）。
- 变体：`--dark`、`--pastel`、`--white`、`--paper`。
- `.poster-meta` 顶栏；`.poster-foot` 底栏（`margin-top:auto`）。
- `.poster-head`：大号拉丁 + 衬线中文 + lede；`.poster-head-title` 用 clamp。
- `.poster-companion`：巨大数字 / 单词 + 索引；新增 `.poster-companion-body` 包裹索引、条形码、说明。
- `.poster-big`：`clamp(4.4rem, 8vw, 8.6rem)`，Bodoni，`line-height:.78`，负字距。
- `.poster-index`：像运单一样的分隔行；`span` 编号、`em` 右侧英文。
- `HoloX`：两个旋转 45° 的渐变条交叉，带点阵；`HoloC` / `HoloRing`：conic 渐变环，radial mask 切出 C 的缺口。
- 条形码用 `Common.jsx` 的 `Barcode`，纯 CSS 渐变，无图片。

### 5.3 为什么用卡片板

参考图是「全息 X / C 的海报卡片」：一张黑卡、一张粉紫渐变卡、细框、编号、标签、条形码、圆环。它不是普通 SaaS 的卡片网格，而是把每个 section 当成 **一张展览海报 / 一张 specimen board**：大标题卡 + 数字卡 + 内容卡 + 引用卡。这样下半页与首屏的丝带、玻璃、全息是同一个世界，而不是「首屏很飞，下面突然 PPT」。

---

## 6. 逐节内容与真实性

### 6.1 方便

- 三个来源：平台账号（淘宝 / 拼多多 / 京东，官方页面登录，凭据加密存本机）、短信 / 通知（装完回填最近 14 天）、手动 / 选中文字。
- 本地 parser 解析、归组、排序；数据不外发。
- 按取件点分组；点内按紧急度排。
- 认不出的放进「没认出来」，等用户确认；不猜点。
- 演示数据：丰巢 2 件（1 件超时）、菜鸟 3 件；标 `DEMO DATA`。
- 搜索只过滤，不重排。

### 6.2 安全

- 没有 App 账号、没有云端、没有广告、没有统计 SDK。
- 取件码在手机解析、留在手机。
- 解析：本机 CPU；取件数据：本机 SQLite；平台凭据：Android Keystore，AES-256-GCM，只存本机。
- 平台同步：只在用户点「立即同步」时发生；只访问官方域名。
- 更新检查：启动时检查，6 小时最多一次，可在「设置 → 关于」关闭。
- 源码可核对：`LINKS.privacy`。

### 6.3 省心

- 第一屏 = 哪个点、几件、最急的还剩多久。
- 只有三种状态色：安全 / 临期 / 过期风险；紧迫度靠倒计时数字，不再加颜色。
- 8 秒后界面自己退场，只留码、格口、倒计时；碰一下回来。
- 取完最后一件，收工卡显示下一站；没有下一站就说今天没有了。
- 18h 免费是丰巢默认；超时 0.5 元 / 12 小时；封顶 3 元。数字来自仓库，不要改。

### 6.4 边界（must stay honest）

- 不猜取件点。
- 不自动合并；只提示「可能是同一个点？」，合并后 5 秒内可撤销，历史保留。
- 不假装补全漏件；不发短信 / 不推通知、只在平台「取快递」里出现的件，任何「不登录、不读屏、还能自动补全」的方案都在骗人。
- 不重现会话码；有些码 30 秒换一张，依赖服务器；离线算不出下一张；只当参照存着并说明可能作废。
- 不做后台自动同步；只在打开 App、点「立即同步」时同步。
- 不做 iOS，也不做锁屏组件；iOS 读不到短信正文和别的 App 通知；第三方锁屏组件 Android 5.0 就被移除。

### 6.5 安装

- Android 8.0+ / minSdk 26 / 12.7 MB / GPL-3.0。
- 没有应用商店（包括 F-Droid）；只从 GitHub / Gitee Release 分发。
- 两道校验：SHA-256（`APK_SHA`）和签名指纹（`FINGERPRINT`）。
- 签名密钥在仓库外；当前 Release 由维护者本机签名；仓库 CI 自动签名需要 secrets。
- 没有 iOS 版。

---

## 7. 已做的关键决策 / 踩过的坑

- **首屏曾经做过一版「天空 / Astrophile」竖向海报，被否**。现在是白 / 全息 / 丝带。
- **GlassSurface 在 Chrome 里渲染成不透明白盘**，无法弯曲后面的丝带；改用自定义 `.hero-glass-lens`：复制丝带背景 + SVG warp。
- ShinyText 的嵌套句点曾渲染成单独的洋红块；CSS 从 `.hero-title span` 改成 `.hero-title > span`。
- 移动端 header 的 edition 文字溢出；≤680 隐藏 `.hero-edition`。
- CardNav 菜单在自动化里点击超时，实际不是遮挡；用 `cua.click` 或坐标点击。
- 移动端菜单 731px 太高；≤680 隐藏描述、缩 padding。
- ghost 字在移动端没藏住：规则误写进 `sections.css`，应在 `responsive.css` 的 `≤980` 里。
- Calm companion 曾重复一个 `18`；已改成 `18` + `.poster-time-copy`。
- Boundaries 白 dossier 的条款标题曾经接近白色（继承 cream）；已强制 `color: var(--ink)`。
- Boundaries 右侧黑卡印章曾经因为 `mix-blend-mode: multiply` 看不见；已改成粉色 `#ff6bb5`、normal blend、sticky `top:110px`。
- Security 白信封卡上的 `NO CLOUD` 曾不可见；已加 `.poster-card--white .no-cloud { color: var(--ink-3); }`。
- 来源卡的 A/B/C 字母曾把文字挤走；改成 absolute 右下、超大描边、z0；内容 z2。
- Install 的 StarBorder 文字曾经横排；已用 `.dl-star-text` 竖排。
- Install 的 GlassIcons 曾经是 no-op；已加 href（releases / #security / #security / GitHub）。
- `poster-card > *` 的 z-index 规则会覆盖 `source-letter` / holo 的定位；已用更具体的 `.poster-card > .source-letter` / `.poster-card > .holo-*` 修正。

---

## 8. 当前未完成 / 正在进行

### 8.1 最重要：平板 companion 版式

源码里刚刚加了这个包裹层（`Poster.jsx`）：

```jsx
<PosterMeta left={kicker} right={no} />
<div className="poster-big">{big}</div>
<div className="poster-companion-body">{children}</div>
<div className="poster-foot">...</div>
```

`poster.css` 里已加基础：

```css
.poster-companion-body {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: inherit;
  min-width: 0;
  margin-top: auto;
}
```

**还没有写平板媒体查询，也还没有重新 build。** 建议补：

```css
@media (min-width: 681px) and (max-width: 980px) {
  .poster-companion {
    display: grid;
    grid-template-columns: minmax(0, 0.82fr) minmax(0, 1.18fr);
    grid-template-rows: auto 1fr auto;
    column-gap: clamp(24px, 4vw, 52px);
    row-gap: 14px;
    align-items: end;
  }

  .poster-companion > .poster-meta {
    grid-column: 1 / -1;
    grid-row: 1;
    align-self: start;
  }

  .poster-companion > .poster-big {
    grid-column: 1;
    grid-row: 2;
    align-self: end;
    margin: 0;
  }

  .poster-companion-body {
    grid-column: 2;
    grid-row: 2;
    align-self: end;
    margin-top: 0;
  }

  .poster-companion > .poster-foot {
    grid-column: 1 / -1;
    grid-row: 3;
    margin-top: 10px;
  }
}
```

预期：768px 时 `0` / `01.` / `18` / `NO` / `0.4.6` 在左，索引 / 时间说明 / 条形码在右，底部 foot 通栏；卡片不再是「左边一坨、右边空白」。

需要同时验证：

- **> 980px**：包裹层是否改变桌面 companion 的底部对齐。基础样式里 `.poster-companion-body { margin-top:auto }`，应该仍然是「大数字在上、body 靠下、foot 最下」；但 `poster-index` 自己的 `margin-top:auto` 现在在 body 内部，可能不再把索引推到卡片底部，肉眼要确认。
- **≤ 680px**：包裹层 + 基础 flex 是否仍然是「大数字 / 索引 / 条形码 / foot」的顺序；`margin-top:auto` 是否把 body 推到底部。
- **BOUNDARIES**：companion 的 `NO` 和三条索引；**CALM**：`18` 和 time copy + ruler + barcode；**INSTALL**：`0.4.6` 和三条规格；**SECURITY**：`0` 和三行 0；**CONVENIENCE**：`01.` 和三条来源。

### 8.2 980px 以下的其他收尾

建议在 `@media (max-width: 980px)` 里补：

```css
.source-grid .source-card:last-child,
.note-grid .note-card:last-child {
  grid-column: 1 / -1;
}
```

因为 980 以下是 2 列，3 张卡会留下一个空半格；让最后一张通栏更完整。

另外：

```css
.bnd-margin-card {
  position: static;
}
```

现在这条只在 ≤680 有；681–980 全宽时 sticky 可能产生奇怪的吸附，建议一起 static。

可选：`.install-spec-card .glass-icons { justify-content: center; }` 在 980 以下，因为 spec 卡会通栏，2×2 图标挤在左边。或者保持左对齐，看审美。

### 8.3 需要重新验证的东西

- `npm run build` 是否通过（包裹层是 JSX 改动）。
- 1920×1400：五个 section 的 companion 间距；Convenience 的 01 卡；Security 的 0 卡；Calm 的 18 卡；Boundaries 的 NO 卡；Install 的 0.4.6 卡。
- 768×1024：上面的平板 grid。
- 390×844：body 包裹层 + 移动端；`18` 的时间说明行；`BOUNDARIES` 标题是否仍不溢出。
- 所有 `holo-x` / `holo-c` 是否被卡片 `overflow:hidden` 正确裁切。
- 横向 `scrollWidth`：390 下是 383 vs client 375，来自 holo 和 StarBorder canvas；`body { overflow-x: clip }` 已兜底，不要误判成布局 bug。
- 交互：CardNav 菜单、ClockDemo 滑块、复制按钮、hash 深链、Escape / 焦点。

### 8.4 文档

`site/README.md` 仍描述旧的白 / holo section 结构，**不要信它**；需要按本文更新，包括：

- React Bits 实际用了哪些；
- Poster.jsx / poster.css 的系统；
- 零第三方运行时；
- 0.4.6 数字来源；
- 部署（Render / GitHub Pages）；
- `Screens` 页暂缓。

---

## 9. 已知问题 / 需要判断

1. **平板 companion**：见 §8.1，最影响观感。
2. **companion 右侧留白**：桌面 0 / NO / 0.4.6 / 18 卡的右侧是纯渐变 + 点阵，这是有意的「海报留白」。但如果下一个模型觉得空，可以加：小号 holo 环、竖排 `SPECIMEN`、或一条 18h/24h 的时间带。不要为了填满而塞无意义模块。
3. **BOUNDARIES 的 X 压标题**：目前 X 在标题末尾后面，黑字仍可读，属于大胆叠压。如果觉得影响识别，把 X 右移 / 缩到 90% / 降透明度。
4. **Head 底部的细线**：每张 head 的 `.poster-foot` 有 `border-top`，两张卡并排时会在同一高度形成一条「看起来连续」的粉线。截图里像 stray line，其实是设计；不要误删。
5. **Install spec 的 GlassIcons 标签**：白色 8px，已确认可见；但在浅色 / 暗色切换时要复查。
6. **`poster.css` 已经 ~1240 行**：能跑，但继续加规则会难维护；如果下一个模型要重构，先保证视觉不变。
7. **未使用的组件**：`Masthead.jsx`、`Screens.jsx`、`GlassSurface.*`、`SpotlightCard`、`DepthText`。`Screens` 是留给真机截图页的，不要删；其余可清理，但先问用户。
8. **旧根站**：`E:\express\index.html` / 旧 assets 的删除是仓库里已存在的改动；新站在 `site/`。不要混。
9. **Git**：`site/` 整个是 untracked；`.github/workflows/pages.yml`、`.gitignore` 已改；大量旧 assets 显示 deleted。**没有 commit。**
10. **README**：旧说明，待更新。
11. **`page.html` 的 theme-color**：已同步为 `#f2f3f6`。

---

## 10. 建议下一个模型怎么评审

请重点回答这 6 个问题（可以直接给结论 + 具体改法）：

1. 下半页现在是否已经摆脱「PPT / 卡片模板」？如果还差，差在哪：节奏、层级、留白、字体、图形、还是内容密度？
2. companion 右侧的纯渐变留白是高级还是空？应该保留、加 holo 图形、还是加数据可视化？
3. 平板 681–980 用「左大数字 / 右索引」两栏是否最优？有没有更海报化的处理（例如整卡出血、旋转、裁切）？
4. 五个 section 都用同一个 `PosterHead` 模板，是否已经开始「另一种模板感」？哪些 section 应该换一种构图？
5. 首屏（白 / 丝带 / 玻璃）与下半页（黑 / 渐变 / 卡片板）的过渡是否成立？需要不需要一个 interstitial / 转场？
6. 移动端 390 的 pacing：首屏很长，marquee，然后 5 个 section；哪里应该压缩，哪里应该保留冲击力？

约束：不要建议引入 Tailwind / UI 库 / 远程字体 / 远程图片 / 统计；不要建议 commit；不要建议把真机截图放回首页；不要建议绿色主色；不要把取件码做成巨幅海报字。

---

## 11. 当前预览 & 截图说明

- `http://127.0.0.1:4173/` 返回 200，正在跑当前构建。
- 第二轮截图已替换 `site/docs/handoff/` 里的旧图，均为当前构建。
- 截图是 1920×1400 / 768×1024 / 390×844 的视口截图，不是整页；每个 section 只截首屏部分。
- 运行时的 WebGL / Canvas 在静态 `page.html` 里不会动；交互看 4173。

---

## 12. 一句话给下一个模型

「第二轮已经把五节拆成五种构图，并把 React Bits 的 Shredder、CometDial、ScrollStack、TearTicket、LaserFlow 等真实交互挂上去了。现在请判断这些交互是否服务于叙事，而不是变成 demo 堆砌；然后决定剩余装饰层（Lanyard / HoloCard / WakeSlider / Scanner）哪些值得加。」

---

## 13. 第二轮执行记录（当前事实）

### 13.1 已落地

| 区域 | 组件 / 机制 | 文件 |
|---|---|---|
| Hero | 静态虹彩丝带、`DecryptedText` kicker、`VariableProximity` 标语 | `Hero.jsx` |
| Hero / 过渡 | `ScrollVelocity` 双轨封条 | `MarqueeBand.jsx` |
| 安全 → 省心 | 扫描交接带 | `Interstitial.jsx` |
| 01 方便 | `LaserFlow`、`ElectricBorder`、`SplitFlapText`、`TiltedCard` | `Convenience.jsx` |
| 02 安全 | `Shredder`、`StickerPeel` 封签、`HalftoneReveal` 显影窗、`FaultyTerminal` 终端底纹、`StatusMark` 状态环、反转构图 | `Security.jsx` |
| 03 省心 | `CometDial` + 受控 `ClockDemo`、`TiltedCard` 凭证 | `Calm.jsx` / `ClockDemo.jsx` |
| 04 边界 | `ScrollStack`、`GlitchText`、`Ferrofluid`、sticky 宣言 | `Boundaries.jsx` |
| 05 安装 | `TearTicket`、`SpotlightCard`、`GlassIcons` | `Install.jsx` |
| 全局 | `ViewportShaderGate`、VERSION 去硬编码、无 Google Fonts | `ViewportShaderGate.jsx` / `Poster.jsx` / 各 section |
| 全局背景 | 固定 `Iridescence` 场 + 透明 section；顶栏移除 | `App.jsx` / `readability.css` |

### 13.2 验证结果

- `npm run build` 通过。
- Playwright Network 记录：除 `127.0.0.1:4173` 的本地资源外 **0 外部请求**。
- 已测 1920×1400 / 768×1024 / 390×844。
- 已知 / 有意保留：
  - 390 下 `document.scrollWidth` 比 `clientWidth` 多约 8px（holo / 阴影 / StarBorder canvas），`body { overflow-x: clip }` 已兜住，未见横向滚动条。
  - Shredder、Ferrofluid 在静态截图里会停在某一帧；真实交互看 4173。
  - TearTicket 在 ≤980 降级为普通 `.dl-ticket`，避免 1500px artboard 在手机上缩成不可读。
  - `StickerPeel` 封签在没有 WebGL 时用同图 `<img>` 回退。

### 13.3 仍未做

- `Lanyard` 纯 CSS/SVG 吊牌（0.4.6 companion 右侧）。
- `HoloCard` 点卡（当前用 `TiltedCard` 保留可读 HTML）。
- `WakeSlider` / `ScrubField` / `SlideCommit`（时钟里的细粒度控件）。
- `CardSwap` / `Folder` 来源卡洗牌。
- `Scanner` / `Dither` / `FaultyTerminal` 背景。
- `TextPressure` 大标题（需处理可变字体，当前用 Bodoni + holo 图形）。
- README 已按当前结构更新；`HANDOFF` 的 §2–§8 是历史，保留作为演进记录。

### 13.4 可读性修正（2026-11-10 最后一轮）

- 新增 `src/styles/readability.css`，在 `responsive.css` 之后加载。
- 中文正文统一回到 `var(--sans)`，不再让 8–11px 的等宽/宋体回退承担正文；正文 13–14px，标签 10–12px，颜色接近 ink。
- 小标题（source / point / note / boundary / ticket）改用 sans 600；大标题 `.poster-cn`、英雄标语、margin 宣言保留衬线。
- `ScrollStack` 的 `dimAmount` / `blurAmount` / `tiltAmount` 降低，叠压时仍能读；`SplitFlapText` 取件码从 10 调到 12；`TearTicket` 正文 / stub 提示放大；Shredder 条目中文放大。
- 仍保留 editorial 微标签，但不再把中文正文塞进 8px mono。

### 13.5 构建产物

以最终 `npm run build` 输出为准：单页 JS 约 745 KB（gzip 253 KB），CSS 约 140 KB（gzip 27 KB）。没有运行时第三方请求；`motion` 是本地打包依赖。
