# 官网 / 落地页

Capybara Express（卡皮巴拉快递助手）的官网。**React + Vite**，构建产物是纯静态文件，不加载任何第三方字体、脚本、CDN 或分析代码。

首页是**品牌页 + 互动标本板**：英文主字标 `CAPYBARA / EXPRESS`、静态虹彩 3D 丝带、液态玻璃；**同一片固定虹彩场贯穿全站**，所有 section 透明，五组海报卡浮在同一个背景上，并挂上撕票、碎纸机、保管表盘、滚动档案栈等 React Bits 交互。顶部悬浮导航已移除。**首页不放真机演示图**——真机画面会单独做一页，组件和素材先保留。

```
site/
├── index.html            Vite 入口（双语元信息、favicon、OG）
├── src/
│   ├── App.jsx           页面骨架、滚动索引、深链接恢复、ClickSpark
│   ├── data.js           链接、版本、截图清单、校验值
│   ├── components/
│   │   ├── SiteHeader.jsx      旧版 CardNav（已不挂载，保留源码）
│   │   ├── Hero.jsx            Iridescence + 英文主字标 + 3D 丝带 + 玻璃镜片
│   │   ├── MarqueeBand.jsx     CurvedLoop 弧形标语
│   │   ├── ClockDemo.jsx       LiquidChrome 保管时钟
│   │   ├── Install.jsx         StarBorder 下载 + GlassIcons 规格行
│   │   ├── Screens.jsx         真机图组件（首页不挂载，留给单独页面）
│   │   ├── reactbits/          React Bits 组件源码（保留出处）
│   │   └── ...
│   └── styles/           base / chrome / header / sections / hero / marquee / responsive
├── public/assets/        3D 渲染、品牌条码、截图、图标、OG 卡
└── vite.config.js        base: './'
```

## 本地预览

需要 Node 22.12+（`site/.node-version` 已写 `22`）。

```bash
cd site
npm install
npm run dev                 # http://127.0.0.1:5173
```

生产构建与本地静态预览：

```bash
npm run build
npm run preview             # http://127.0.0.1:4173
```

也可以直接起一个静态服务器看构建产物：

```bash
cd site && npm run build
cd dist && python -m http.server 8899   # http://127.0.0.1:8899
```

## 部署

**Render（主站）**：新建 **Static Site**（不是 Web Service），连接仓库，Root Directory `site`，Build Command `npm ci && npm run build`，Publish Directory `dist`。Node 版本 22（`site/.node-version` 已写 22）。静态站免费、不休眠，地址是 `https://capybara-express.onrender.com/`，`index.html` 的 canonical / OG / Twitter image 已指向这里。

**GitHub Pages（可选镜像）**：推 `main` 时 `.github/workflows/pages.yml` 会 `npm ci` + `npm run build`，然后发布 `site/dist`。如果只想要 Render，把这个 workflow 禁用或删掉，否则推 main 会同时部署一份 Pages；保留时 canonical 仍指 Render。

`vite.config.js` 的 `base: './'` 是故意的：同一份产物既能放在 Render 根路径，也能放在 GitHub Pages 的 `/capybara-express/` 子路径下。**不要把资源改成以 `/` 开头的绝对路径。**

## 首页结构

1. **无顶栏首屏**：顶部悬浮 `CardNav` 已移除，首屏直接进入；章节导航由左侧 Rail / 首屏右侧 stage nav 承担。
2. **Hero**：英文主字标 `CAPYBARA / EXPRESS`（Bodoni / Didot / Times 衬线 + `ShinyText` 扫光），中文名「卡皮巴拉快递助手」降为小字注脚；英文/中文双标语；全站 `Iridescence` 虹彩场、Blender 渲的静态虹彩丝带、自定义玻璃镜片（LOCAL 100%）、品牌条码形状玻璃条；下载按钮包在 `Magnet` 里。
3. **弧形标语**：`CurvedLoop` 的巨型弧形跑马灯，内容是 `LOCAL-FIRST · NO ACCOUNT · NO ADS · NO ANALYTICS · GPL-3.0`。
4. **01 方便 / EFFORTLESS**：黑卡大标题 + 渐变 `01.` 标本卡；`LaserFlow` 光纤解析带、`ElectricBorder` 本地解析器、A/B/C 来源卡、`TiltedCard` 取件点卡、`SplitFlapText` 取件码翻牌；底部加一组 `TiltedCard` 纸箱材质研究（三张本地照片，不是 App 截图）。
5. **02 安全 / PRIVATE**：反转构图——左侧白色 `0` 标本台 + `Shredder` 实时粉碎「云端备份 / 广告 SDK / 统计遥测」，右侧粉紫 `PRIVATE` 大标题；全息信封 + `StickerPeel` 可掀封签 + `HalftoneReveal` 本地显影窗口；数据边界审计表叠 `FaultyTerminal` 字符底纹与 `StatusMark` 状态环。
6. **03 省心 / CALM**：黑色 `CALM` 头 + 渐变 `18` 卡；`CometDial` 保管表盘与下方 `ClockDemo` 双向联动；白色取件凭证用 `TiltedCard` 倾斜。
7. **04 边界 / BOUNDARIES**：左侧 sticky `MARGIN / 04` 宣言卡（`Ferrofluid` 底纹 + 粉色印章），右侧 `ScrollStack` 的 6 张档案卡逐张叠压；`NO` 大字用 `GlitchText`。
8. **05 安装 / INSTALL**：`TearTicket` 可撕副券提货单（撕下副券直接下载 APK）、`SpotlightCard` 两道校验、`GlassIcons` 规格卡；`StarBorder` 与 `Magnet` 继续用于下载动作。
9. **Footer**：英文 `SLOWLY.` + 中文「慢一点，也能到。」

真机图片不在首页出现。`Screens.jsx` 和 `data.js` 里的 `SCREENS` 保留，等单独页面做好后再接；首页的导航、页脚都没有 `#screens` 入口。

## React Bits 组件（已使用）

| 组件 | 位置 |
|---|---|
| `Iridescence` | Hero 虹彩 WebGL 背景 |
| `ShinyText` | `CAPYBARA / EXPRESS` 扫光 |
| `Magnet` | Hero 下载按钮的磁吸 |
| `CurvedLoop` | 弧形跑马灯 |
| `GlassIcons` | 安装区规格玻璃图标 |
| `StarBorder` | 安装区主下载按钮的星轨边 |
| `ClickSpark` | 全站点击火花 |
| `LiquidChrome` | 省心时钟的液态金属背景 |
| `DecryptedText` | Hero kicker、交接带、审计标签的解码文字 |
| `VariableProximity` | Hero 英文标语的指针距离场 |
| `ScrollVelocity` | 首屏下双轨变速封条 |
| `LaserFlow` | 方便区的本地解析光纤带 |
| `ElectricBorder` | 本地 PARSER 芯片电弧边框 |
| `SplitFlapText` | 取件码机械翻牌 |
| `TiltedCard` | 取件点卡与时钟凭证的 3D 倾斜 |
| `Shredder` | 安全区把隐私负资产切碎 |
| `StickerPeel` | 信封上的可掀全息封签 |
| `HalftoneReveal` | 信封上的本地显影窗口（卡皮巴拉半色调） |
| `FaultyTerminal` | 数据边界卡的终端字符底纹 |
| `StatusMark` | 审计标签的本地 / 加密 / 无状态环 |
| `CometDial` | 18h 保管表盘，和时钟双向联动 |
| `ScrollStack` | 边界 6 条档案卡的滚动堆叠 |
| `Ferrofluid` | 边界宣言卡的暗色磁流体底纹 |
| `GlitchText` | 边界 `NO` 的切片故障 |
| `TearTicket` | 安装区可撕副券提货单 |
| `SpotlightCard` | 安装区两道校验的指针高光 |

React Bits 源码来自 [DavidHDev/react-bits](https://github.com/DavidHDev/react-bits)，许可证是 **MIT + Commons Clause**；每个文件头部保留了出处。项目本身是 GPL-3.0，组件只用于这个非售卖的官网；如果要商业售卖模板或再分发组件本身，先核对 Commons Clause 的边界。

`SpotlightCard` 已用于安装区校验；`GlassSurface` / `DepthText` 仍保留但未挂首页（GlassSurface 在 Chrome 里会把丝带渲成不透明圆片，首屏改用同原理的自定义镜片；DepthText 留作后续）。`motion` 作为本地依赖只参与打包，不产生运行时网络请求。

## 设计约束

1. **首页不放真机演示图。** 真机图会削弱英文主字标、3D 和玻璃；它们应该有一个自己的页面。不要把 `Screens` 重新挂回首页。
2. **英文主字标 + 中文正文。** 英文负责国际化的第一眼，中文负责产品解释；不要把所有正文改成英文。主字标是 `CAPYBARA / EXPRESS`，不是取件码、不是截图。
3. **白灰纸面 + 墨 + 全息。** 底色 `#f2f3f6`/`#ffffff`，字色 `#08090b`，强调色品红 `#c21f72`；虹彩渐变 `--holo` 只做材质和少量装饰。**绿色只做三态语义**，不做主调。中文正文走 `var(--sans)`，正文 13–14px，标签 10–12px；不要让中文正文落在 8px 等宽/宋体回退里。
4. **玻璃和液态金属用在有意义的地方。** 首屏圆镜、品牌条码、时钟背景、导航、规格图标是玻璃时刻；不要把每张卡都做成玻璃。Apple 风格的折射要能看见被弯曲的虹彩丝带。
5. **零第三方请求。** 字体走系统字体栈（Bodoni/Didot/Times、Songti、PingFang/微软雅黑、mono），不引 Google Fonts；没有统计、没有 CDN、没有远程图片。React Bits 与 Three/WebGL 相关代码都是本地源码/本地渲染。
6. **不编数字。** 版本、体积、SDK、价格、时限、指纹都必须能在仓库里核对；演示数据必须标注「演示数据」。
7. **边界要敢写。** 不猜取件点、不自动合并、不假装补全漏件、不重现会话码、不做后台同步。新增文案不要越过这些线。
8. **动效克制但可以炫技。** 玻璃/液态金属只服务于氛围，不阻塞阅读；尊重 `prefers-reduced-motion`；`LiquidChrome`、`Iridescence`、`GlassIcons` 离屏会暂停。
9. **响应式。** 390 / 768 / 1440 / 1920 都要看；首页不允许横向溢出。平板 681–980 的 companion 用左大数字 / 右内容两栏；手机端隐藏 CometDial、TearTicket 降级为普通提货单，弧形跑马灯缩小字号。
10. **同一片背景。** 全站用 `site-field` 的固定 Iridescence 场；`.poster-section` 一律透明，内容只靠实色卡片建立层级。不要再给单个 section 加回整块黑底/纸底，否则背景会断。
11. **组件归属。** 所有 React Bits 文件头保留出处；新增组件也照做。

## 内容真实性

| 页面上的说法 | 出处 |
|---|---|
| 0.4.6 / versionCode 11 | `../app/build.gradle.kts` |
| 12.7 MB（12,744,317 字节） | `../showcode-0.4.6.apk` 实际大小 |
| minSdk 26 / targetSdk 36 | `../app/build.gradle.kts` |
| 18 小时免费 / 0.5 元每 12 小时 / 封顶 3 元 | `../README.md`；`../app/src/main/java/dev/showcode/core/Urgency.kt` 的 `LOCKER_FREE_HOURS_DEFAULT` |
| 丰巢剩余 ≤6 小时为临期 / 驿站关门前 ≤90 分钟 | `../app/src/main/java/dev/showcode/core/Urgency.kt:19,28`；`../app/src/main/java/dev/showcode/data/Prefs.kt:180` |
| 「临期线 12h」 | 18 小时免费 − 6 小时阈值；由时间轴演示直接算出 |
| 装完回填最近 14 天 | `../README.md` |
| 8 秒静默 | `../README.md` |
| 合并后 5 秒内可撤销 | `../README.md` |
| 更新检查 6 小时最多一次 | `../README.md` |
| 签名指纹 `e18262c9…674d3` | `../README.md` + `apksigner verify --print-certs` |
| APK SHA-256 `794c84da…2707d0` | 当前 `../showcode-0.4.6.apk`；与 Release 页 `SHA256SUMS.txt` 对照 |

页面里的 2 件 / 3 件、取件码、格口、货架、商品和费用都是 **App 演示模式的数据**，页面已在输出卡片、时钟演示处标注「演示数据」。不要把演示数据写成真实统计。

英文标语 `PARCELS ARRIVE. HASSLE DOESN'T.` 与 `SLOWLY.` 是品牌文案，不是功能承诺；功能边界以上表和「04 边界」为准。

## 素材

- `public/assets/holo-ribbon-v4.webp`：Blender 渲染的虹彩丝带（1400 → 1200，透明背景，55 KB）。渲染脚本和 `.blend` 源文件在 `.workbuddy/holo/`（已被 git 忽略），需要重渲染时用 `render-ribbon.py` 的 v4 参数。
- `public/assets/brand-bars.svg`：品牌条码形状，给 Hero 的玻璃条做 mask。
- `public/assets/og-card.png`：1200×630 社交预览卡，英文主字标 + 丝带。
- `public/assets/` 里另有 23 张真机截图和 1 个图标；截图来自 `docs/screenshots/`，同一批文件也用在 `fastlane/metadata/android/*/images/phoneScreenshots/`。**首页不引用这些截图**，等真机页做好再用。
- `public/assets/parcels/`：用户提供的纸箱 / 封箱胶带 / 银色内袋照片，压缩为 WebP；只作为「材质研究」，不是 App 真机截图，也不代表真实订单。
- 所有图片都是本地文件。不要用外部图床，也不要在 CSS 里 `@import` 远程字体。

## 交付前检查

```bash
cd site
npm run build
```

- 浏览器 Network 里不应出现本站以外的请求（点击 GitHub / Gitee 链接除外）。
- 390 / 768 / 1440 / 1920 四个宽度无横向滚动。
- 首页没有任何真机演示图；`#screens` 不在首页导航里。
- 首屏玻璃镜片能看见被弯曲的虹彩丝带；时钟液态金属后面文字可读；导航菜单可开可关。
- 菜单、时间滑块可用键盘操作；`prefers-reduced-motion` 下动效降级。
- 页面上的数字与上表一致；演示数据都有标注。
