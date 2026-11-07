# 直达链验证记录

这些地址是第三方 App 的内部页面，不是开放 API。它们随对方版本变，所以每一条都要在
装了最新版对应 App 的真机上重测；这张表记的是「哪一天、在什么版本上、怎么验的」。

## 2026-11-05 · MuMu 模拟器（Android 15 / SM-A5360 伪装）

设备上只装了淘宝（`com.taobao.taobao` 10.37.20），**未登录**。所以这一轮验的是
「链路有没有被对方接住」，不是「登录后显示什么码」。

| 目的地 | 分级 | 结果 | 证据 |
|---|---|---|---|
| 淘宝身份码 | 1 `taobao://` | 通过 | 冷启动 `Status: ok`；logcat 拉取 `end-collect-platform/1.0.426/js/p_identity-code-index.js` 与 `css/p_identity-code-index.css` |
| 淘宝待取 | 1 `taobao://` | 通过 | logcat 拉取 `last-mile-fe/m-end-school-tab/0.0.152/js/p_home-index.js` |
| 淘宝两条网页兜底 | 3 | 通过 | `curl` HTTP 200；`__ICE_APP_CONTEXT__` 的 `routePath` 分别为 `/identity-code` 与 `/home` |
| 菜鸟身份码 | 1 `cainiao://` | 未测 | 模拟器未装菜鸟；网页 `authCode.html` 200 |
| 拼多多身份码 | 1 `pinduoduo://…tab=ID_CODE` | 未测 | 模拟器未装拼多多 |
| 京东待取 | 1 `openjd://` | 未测 | 模拟器未装京东；网页 200 但重定向到 `passport.jd.com` 登录 |
| 小红书待取 | 1 `xhsdiscover://` | 未测 | 模拟器未装小红书；网页只是首页，不是订单列表 |
| 丰巢 | 2 启动入口 | 未测 | 没有已知 scheme（不是漏了） |

## 桌面入口

- 静态长按快捷方式：`dumpsys shortcut` 里 6 条全部注册（`taobao_identity` /
  `pinduoduo_identity` / `cainiao_identity` / `taobao_pending` / `jd_pending` / `xhs_pending`）。
- 模拟器的 Lawnchair 只在长按弹窗里显示前 2 条；这是桌面限制，不是注册失败。
- 冷启动与热启动两条都验过：冷启动 `am start -W` 直接报 Taobao Welcome；热启动走 `onNewIntent`。

## 已知差异（竞品 issue #3）

淘宝身份码页给的是 `CS` 开头的码（和菜鸟同码），待取列表给的是 `XNST` 开头的码；
有的驿站扫码机只认 `XNST`。所以这两页不能合并——身份码那一行走前者，
取件码最终以本 App 里那件包裹自己的码为准。

## 发版前重测

在装了最新版对应 App 的真机上按「目的地 × 冷/热启动」各点一次；失败的那条先看
`logcat | grep -i 'WindVane.H5HA'`（淘宝）确认是不是页面资源换了路径。
