<div align="center">

<img src=".github/img/icon.svg" width="104" height="104" alt="CurrentMusicX 蓝色圆润音符图标">

# CurrentMusic

让封面、歌词与音乐一起流动

基于 Kotlin · Jetpack Compose · Miuix 的原生 Android 音乐客户端

<p>
  <a href="https://github.com/bileizhen/CurrentMusicX/releases/latest"><img src="https://img.shields.io/github/v/release/bileizhen/CurrentMusicX?style=flat-square&color=1677FF" alt="最新版本"></a>
  <a href="#兼容性"><img src="https://img.shields.io/badge/Android-8.0%2B-1677FF?style=flat-square" alt="Android 8.0 及以上"></a>
  <a href="#开源与致谢"><img src="https://img.shields.io/badge/License-GPL--3.0_%2B_AGPL--3.0-555555?style=flat-square" alt="GPL-3.0 与 AGPL-3.0 组件"></a>
</p>

**[下载应用](https://github.com/bileizhen/CurrentMusicX/releases/latest)** · [界面预览](#界面预览) · [更新记录](https://github.com/bileizhen/CurrentMusicX/releases) · [反馈问题](https://github.com/bileizhen/CurrentMusicX/issues)

</div>

## 简介

CurrentMusicX 是 [CurrentMusic](https://github.com/backrooms-yrc/CurrentMusic) 的原生 Android 客户端，支持网易云音乐库、Navidrome 自建曲库和双人一起听。圆形封面、动态取色、逐字歌词与悬浮播放器组成日常听歌体验，适配竖屏、横屏和宽屏布局。

本页介绍当前源码的功能；已发布版本的具体改动请查看[GitHub Releases](https://github.com/bileizhen/CurrentMusicX/releases)。Android 客户端源码维护于本仓库 `main` 分支。

## 开始使用

1. 前往 **[Releases](https://github.com/bileizhen/CurrentMusicX/releases/latest)** 下载 `CurrentMusic-Android-v版本号.apk`，按系统提示安装。同签名正式版可覆盖升级。
2. 在「设置 → 音源与网络」选择音乐音源和歌词来源。两项独立设置，**歌词默认按 Folia 方式自动择优：逐字优先，逐行兜底**。
3. 按需登录 CurrentMusic、绑定网易云，或在「我的 → 自建曲库」连接 Navidrome，即可搜索、收藏和播放。

歌曲、音质、会员音效及推荐的可用性，以对应服务和账号权限为准。

## 界面预览

<div align="center">

<img src=".github/img/home.jpg" width="30%" alt="首页：推荐与悬浮播放器">
<img src=".github/img/player.jpg" width="30%" alt="播放器：封面与歌词">
<img src=".github/img/search.jpg" width="30%" alt="搜索：歌曲、歌手与专辑">

<sub>首页 · 播放器 · 搜索（RMX5060 真机截图，2026-10-21）</sub>

</div>

<details>
<summary>查看更多界面</summary>

<div align="center">

<img src=".github/img/lyrics.jpg" width="30%" alt="莫奈歌词与翻译">
<img src=".github/img/artist.jpg" width="30%" alt="艺人主页">
<img src=".github/img/discover.jpg" width="30%" alt="发现与音乐风格">

<sub>歌词 · 艺人主页 · 发现（界面以实际安装版本为准）</sub>

</div>

</details>

## 功能一览

| 功能 | 支持内容 |
| --- | --- |
| **播放与控制** | 后台、通知栏、锁屏与蓝牙控制；队列、循环、随机、心动模式；定时关闭、DLNA 投屏、MV 播放与下载 |
| **歌词与动效** | 六源自动匹配与匹配度、单曲来源和偏移、多格式导入及 FIA / LRC 导出；逐字高亮、翻译与罗马音、莫奈动画、流烟、星尘及透视效果 |
| **音乐库** | 网易云我喜欢与歌单管理、CurrentMusic 自建歌单；Navidrome 专辑、艺术家、收藏、搜索和原音质播放 |
| **私人漫游** | 默认、熟悉、探索、拼图及动态场景；原播放器内切换模式、连续推荐，结束后可恢复原队列 |
| **双人一起听** | 网易云原生邀请与短链接、共享队列、同步切歌、断线恢复；一起听记录、排行、结束报告与缘分报告 |
| **网易云任务** | 原生任务中心、云贝与会员成长值、音乐人任务；每日签到、已完成奖励领取、前台自动签到与本机记录，默认关闭 |
| **发现与交流** | 搜索候选词建议，歌曲、歌词、专辑、作者和歌单分类搜索；每日推荐、热搜、音乐风格；用户、帖子、公共聊天与 AI 对话；播放页私有「问 AI」 |
| **音效与过渡** | 本机均衡器、原版标准及智能音效、AI 情绪调音师、AI 调音大师与外放场景；智能过渡或淡入淡出 |
| **下载与分享** | 音质与目录选择、后台进度、取消及重试；音频标签、封面与歌词写入；封面取色海报、播放链接与二维码 |
| **外观与桌面** | 深浅主题、Monet 取色、经典或光环开屏、可选模糊与玻璃效果；自定义播放背景与模糊度；悬浮底栏、桌面歌词灵动岛、双人头像与头像挂件 |
| **账号与更新** | 多账号、个人资料、原密码或邮箱验证码修改密码、邮箱找回密码、听歌统计、全部公告；正式版 / 预发布更新渠道，后台下载及安装包校验 |

外观页使用统一的原生组件，提供可切换页面的实时预览、主题与纯黑模式缩略图、主题色板与网格 / 光谱 / HSV 滑块取色面板、滑动或淡入淡出切换以及独立文字缩放。预览内容使用 CurrentMusic 的首页、发现、我的、设置及迷你播放器布局，并随主题颜色更新。字体子页提供默认字体、以自身字体显示的系统字体列表和底部搜索，并支持导入 TTF / OTF / TTC 字体，复制到应用内保存后可反复选择。首页搜索入口与展开后的输入框共用玻璃搜索组件，保留原有的上移动画、手动提交、建议与草稿。字体页隐藏迷你播放器；底部搜索使用玻璃搜索组件与弹性取消按钮，清除保留焦点、取消清空并收起键盘，支持按空白分词匹配全部关键词。导航栏固定使用悬浮样式；液态玻璃提供“标准 / 液态”两项列表选择，开屏动画、歌单样式、显示缩放与刷新率仍可设置。

首页、发现、我的和设置共用 24sp 固定渐隐顶栏，列表内容可滚动到状态栏后方，分组标题使用相同的无图标文字样式。首页品牌字样和头像固定在顶栏；发现的四个栏目使用滑动选中控件。

设置首页按个性化、音乐与应用分组，采用固定渐隐顶栏和无图标文字分组标题，内容可滚动到状态栏后方；“音源与网络”只管理音乐/歌词来源、CurrentMusic 线路与 Navidrome。“播放”集中音质、衔接、预加载、队列与红心设置。音效、音频输出、桌面歌词和存储空间各保留独立入口；子页统一使用分组圆角列表、行内选择菜单与容器变换返回，存储空间保留总览和各类占用的独立卡片设计。

“我的”页使用统一的资料与统计卡片，音乐库、音乐服务和账号分别以带图标的圆角列表呈现。网易云本机账号与 CurrentMusic 账号共用列表组件，保留头像预览、资料编辑、公告、歌曲播放和歌单入口。Chat 使用一体式多行输入区，图片和音乐引用收进添加菜单，保留失败草稿与显式重试；进入 Chat 后隐藏整块底栏，离开后恢复。

<details>
<summary>播放、歌词与音效说明</summary>

- 搜索结果点击单曲只插入并播放该曲，保留原队列；歌单和专辑支持整列表播放。
- 自动切歌的智能过渡与淡入淡出择一开启，默认关闭；淡入淡出可调 1–12 秒。允许与其他应用同时播放可单独设置。
- 「设置 → 外观 → 开屏动画」可切换经典与光环样式，并直接预览；经典为默认，光环开屏显示当前用户名。
- 「播放器右上角菜单 → 播放背景」可从系统选择图片，预览并调整 0–100% 模糊度；图片仅保存在本机，重启保留，可恢复默认封面取色背景。
- 「我的 → 网易云账号 → 网易云任务」开启后，“我的”页会在网易云账号下方显示任务入口。需要本机网易云登录；自动签到另行开启，仅在 App 前台进行日常与云贝签到。任务进度和奖励由网易云确认，听歌使用实际播放记录。
- 歌词优先使用真实逐字时间，过滤服务端估算轴。卡拉 OK 兼容策略选「总是」时，可为普通逐行歌词生成近似高亮，属于本地显示效果。
- 音效页面使用原生 Compose；已内置适配的 ARM64 引擎，无需另装网易云。会员权益由服务端核验，未接入的算法会明确提示；外放场景仅在当前运行期间保留。
- 应用内不常驻显示歌词 / FPS 灵动岛，仅临时显示音效、下载与错误反馈；桌面悬浮歌词需系统授权，支持位置、字号、翻译和锁定。

</details>

<details>
<summary>音乐库与一起听说明</summary>

- 网易云登录需先注册并登录 CurrentMusic 账号，再使用扫码或手机验证码。本机登录后仍直连网易云搜索、歌单、收藏、推荐与评论。没有本机登录时，部分服务可复用 CurrentMusic 账号的云端绑定，云端凭据不下载到本机。
- 「网易云作为主音乐库」决定默认音乐库入口；「设置 → 播放 → 默认红心位置」独立控制收藏目标。Navidrome 歌曲使用自己的服务器收藏。
- 歌单支持搜索、排序、创建与管理，可选 Depth Ribbon 立体缎带或 CD 唱片架浏览；唱片架支持搜索收拢、随机挑歌、高潮片段或整首试听、循环与自动下一首，取消喜欢后可在灵动岛撤销。CurrentMusic 自建歌单可导入网易云「我喜欢」。切换音乐库不会删除原有歌单。
- 搜索首页支持收起历史、双列推荐词和可横滑的网易云搜索热榜（含 ACG、日语等分类）；输入片段显示候选词，点选直接搜索；「歌词」分类支持输入记得的歌词搜歌，并展示命中的歌词片段。
- 搜索页「歌单」分类展示网易云歌单封面、创建者和歌曲数，支持分页与失败重试；点击沿用原有歌单详情，切换分类及返回后保留结果与滚动位置。
- 网易云一起听复用原有搜索与播放队列，支持双方口味推荐、移除、清空、整队列下载和收藏；推荐、历史、排行与报告受服务端权限限制。
- 冷启动检查并恢复中断的网易云会话；启动时主动开始其他播放会取消本次自动恢复。结束会话后，确认远端结束才展示报告，可收藏歌曲并导出分享海报。
- CurrentMusic 服务器房间另支持密码、点歌审批和成员权限。Navidrome 私有曲库当前仅在本机播放，不参与 CurrentMusic 房间或 DLNA 投屏。

</details>

## 音源与歌词

在「设置 → 音源与网络」分别调整，切换歌词来源会重新加载当前歌词，缓存按来源隔离。

| 设置 | 选项 | 请求方式 |
| --- | --- | --- |
| 音乐音源 | **CurrentMusic（默认）** | 通过配置的 CurrentMusic 服务器取流，需站内账号 |
| 音乐音源 | 网易云原生 | 手机直连网易云获取歌曲与 MV，使用本机网易云账号权限，不自动回退到服务器取流 |
| 音乐音源 | LeiZ API | 使用对应音源服务 |
| 歌词来源 | **自动择优（默认）** | 按 QQ → 网易云 → AMLL → 酷狗 → 汽水 → CurrentMusic 的顺序查找；歌曲信息匹配 ≥75% 时参与选择，遇到真实逐字即采用，没有逐字时保留首份可用逐行歌词 |
| 歌词来源 | CurrentMusic 歌词 | 使用服务器歌词，含其提供的 LDDC 真实逐字数据 |
| 歌词来源 | 网易云歌词 | 手机直连网易云 YRC / LRC；没有真实逐字数据时显示逐行歌词 |

**希望歌曲和歌词都直连网易云时，请同时选择「网易云原生」音源和「网易云歌词」。** 仅切换音乐音源不会改变歌词来源。本机网易云音乐库与原生双人一起听也独立直连网易云。

播放页菜单的「歌词管理」可搜索、预览并保存指定歌词，或导入 LRC、TTML、FIA、VTT、YRC、明文 QRC 和 KRC 文件。候选列表和预览使用 Folia 的歌曲信息评分：歌名 45、歌手 25、专辑 30，再按时长差折扣；每个来源只读取前十个结果中最匹配的可靠候选，自动门槛为 75 分。自动选择不再要求取得网易云基准歌词，也不再比较不同来源的歌词正文和时间轴。默认先查 QQ，按来源顺序保留首份逐行歌词，遇到真实逐字就采用；AMLL 自动查询使用匹配到的网易云歌曲 ID，即使网易云歌词暂不可用仍可查询。慢来源会超时跳过。手动保存和导入的单曲歌词、本地内嵌歌词仍优先于自动选择。手动匹配预览可保留已有的自适应对齐功能。原始逐字时间保留，校正后的时间轴由播放页、迷你播放器与桌面歌词共用。单曲偏移正值延后、负值提前，在已有对齐后额外叠加，也可独立归零。FIA / LRC 导出保留原时间轴，不将显示层估算写成真实逐字数据；FIA 另行保存并恢复自适应对齐信息。

「设置 → 音源与网络 → CurrentMusic 服务器」提供固定顺序的自动、`music.20110208.xyz` 主站、`cm.bileizhen.top` CDN、`currentmusic.geekertao.com` CDN、`music.ctcehs.com` CDN 和自定义选项，并显示公告接口的响应延迟，不按延迟排序。新安装默认自动，启动时测速选择较快的可用预设线路，也可手动重新检测；已有服务器配置保留原选择。主站和 CDN 切换保留登录，自定义地址独立保存，切换到其他服务器需重新登录。完整接口路径为 `/cm/`。 更新下载中的 CurrentMusic 专用源同步跟随自动或手动选择的内置 CDN，探测与后台下载使用同一路由，安装包继续按 GitHub 发布信息校验大小和 SHA-256。

此地址用于站内账号、CurrentMusic 音源与歌词、云端绑定、个人资料、社区及服务器房间。网易云原生、LeiZ API、外部音频及歌词来源继续使用各自的地址；Navidrome 音频直接从自己的服务器播放。

## 兼容性

| 项目 | 要求 |
| --- | --- |
| 系统 | Android 8.0 / API 26 及以上；目标 Android 16 / API 36 |
| 架构 | 正式安装包面向 ARM64（arm64-v8a），包含适配的原版音效引擎 |
| 布局 | 竖屏、横屏与宽屏导航 |
| 动态颜色 | Monet 需要 Android 12 / API 31 及以上 |
| 实时模糊 | Android 13 / API 33 及以上；低版本或关闭时使用普通材质 |
| 下载目录 | 系统文件夹选择器 / SAF；Android 10 及以上默认 `Download/CurrentMusic` |

## 常见问题

<details>
<summary>下载的歌曲、封面和歌词保存在哪里？</summary>

下载时可指定目录，并授予该目录访问权限。歌曲信息、封面与歌词写入音频文件，同时保存配套 LRC 和封面。支持 MP3、FLAC、M4A、Vorbis OGG、WAV，裸 AAC 无损封装为 M4A。其他播放器的内嵌歌词与逐字显示能力取决于格式及其自身支持。

</details>

<details>
<summary>如何更新？自行构建的包能覆盖官方版吗？</summary>

在「设置 → 检查更新」查看新版本，可调整启动检查、更新渠道和下载来源。启动发现新版本时可先忽略或查看详情；来源列表显示状态与延迟，专用镜像置顶，其余可用来源按延迟排序。后台下载支持取消、重试和换源；默认镜像失败或文件大小、SHA-256 不符时尝试备用源。校验包名与签名后，由 Android 系统确认安装；首次可能需要允许安装未知来源应用。

自行构建的包若与官方签名不同，无法直接覆盖。开发与设备测试请使用[独立测试包](#从源码构建与设备测试)，与日常安装分开。

</details>

<details>
<summary>如何反馈问题？</summary>

请在 [Issues](https://github.com/bileizhen/CurrentMusicX/issues) 提供应用版本、手机型号、Android 版本、复现步骤与现象。可从设置中导出诊断日志，分享前检查并遮挡个人信息。

</details>

## 隐私与开发

本机账号凭据加密保存，服务分别请求对应的网易云、CurrentMusic 或 Navidrome 接口；音频、MV 与图片从对应服务返回的地址加载。下载文件保存在用户选择的目录。数据用途及保存方式见[隐私说明](PRIVACY.md)。

- [从源码构建与设备测试](#从源码构建与设备测试)
- [更新记录](https://github.com/bileizhen/CurrentMusicX/releases)
- [第三方依赖与许可证](THIRD_PARTY_NOTICES.md)

欢迎提交 Issue 或 Pull Request。请说明解决的问题、实际验证结果，并按独立功能或修复拆分提交。

## 从源码构建与设备测试

准备 JDK 17 及以上、Android SDK Platform 37.0 和 Build Tools 35.0.0。通过 `ANDROID_HOME` 或本机 `local.properties` 的 `sdk.dir` 指定 SDK 路径。

Windows / PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Linux / macOS 使用 `./gradlew` 执行相同任务。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

连接设备后，可使用独立测试包，与日常安装的数据隔离：

```powershell
.\gradlew.bat -PisolatedDebug=true :app:assembleDebug :app:connectedDebugAndroidTest
```

独立 Debug 包名为 `com.bileizhen.currentmusic.verification`。设备测试可能需要登录、网络、系统权限或指定素材；构建和单元测试通过不代表实际接口与界面已经验证。

正式包使用 `:app:assembleRelease` 构建。签名从本机未入库的 `keystore.properties` 读取 `storeFile`、`storePassword`、`keyAlias` 和 `keyPassword`；未配置时生成未签名包。密钥、账号配置与验证产物不入库。

Release 启用 R8 与资源压缩，仍发布一个包含现有 ABI 的通用 APK；保留网易云原版音效、MNN 和对应 JNI 名称。可复现体积报告：`python scripts/apk_size_report.py app/build/outputs/apk/release/app-release.apk --json output/apk-size.json`。报告区分 ZIP 压缩大小、各 ABI 解压占用与 ELF 页对齐，不将体积变化当作运行速度改善。部分既有供应商原生库仍不满足 16 KiB 页要求。

性能测试使用独立的 `com.bileizhen.currentmusic.benchmark` 应用，不访问用户账号或在线服务。`baselineprofile` 与 `benchmark` 模块覆盖冷启动、首页、播放器、播放、歌词、歌单、设置和输出模式；执行 `:app:generateBaselineProfile` 显式采集，再用 `:benchmark:connectedPerformanceAndroidTest` 比较 `compilation=none` 与 `compilation=baseline`。未实际生成 Profile 时，后者应失败，不把框架或依赖自带 Profile 视为本项目已完成采集。设备需支持 AndroidX Macrobenchmark（测试模块最低 Android 9，不改变正式应用 Android 8 的最低版本）。

压缩运行时回归可用 `:benchmark:connectedPerformanceAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.currencortex.music.benchmark.MinifiedRuntimeSmokeTest` 单独执行，验证脚本、序列化、Room、JNI 加载和本地音频标签读写；它不证明真实 USB DAC、蓝牙或最终硬件 Bit-perfect。测试 APK 的静音前台服务仅用于防止厂商冻结测试进程，不进入正式 APK。性能数据、录屏与诊断日志保存在本机，不入库。

## 开源与致谢

既有代码按 [GPL-3.0-only](LICENSE) 分发；Folia 莫奈歌词动画与播放暂停渐变移植组件按 [AGPL-3.0-only](app/src/main/assets/legal/AGPL-3.0.txt) 分发，组合遵循两份许可证第 13 节。各上游组件保留自己的版权与许可，完整说明见[第三方声明](THIRD_PARTY_NOTICES.md)。

感谢 [CurrentMusic](https://github.com/backrooms-yrc/CurrentMusic)、[Folia](https://github.com/chthollyphile/folia-major)、[Miuix](https://github.com/compose-miuix-ui/miuix)、[SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)、[AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)、[LeiFetch](https://github.com/bileizhen/LeiFetch)、[123PanX](https://github.com/bileizhen/123PanX)、[XBlocker](https://github.com/bileizhen/XBlocker)、[AMLL TTML DB](https://github.com/amll-dev/amll-ttml-db)、[LXGW WenKai](https://github.com/lxgw/LxgwWenKai)，以及 AndroidX、Media3、Coil、Jaudiotagger、ZXing 等开源项目。

## 浏览量

<div align="center">

![访问统计](https://count.getloli.com/@bileizhen_CurrentMusicX?name=bileizhen_CurrentMusicX&theme=original-new&padding=7&offset=0&align=center&scale=1&pixelated=1&darkmode=auto)

</div>
