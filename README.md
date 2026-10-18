<div align="center">

<img src=".github/img/icon.png" width="128" alt="CurrentMusicX">

# [CurrentMusicX](https://github.com/bileizhen/CurrentMusicX)

让封面、歌词与音乐一起流动的原生 Android 音乐客户端

<p>
  <a href="https://github.com/bileizhen/CurrentMusicX/stargazers"><img src="https://img.shields.io/github/stars/bileizhen/CurrentMusicX" alt="GitHub Stars"></a>
  <a href="https://github.com/bileizhen/CurrentMusicX/issues"><img src="https://img.shields.io/github/issues/bileizhen/CurrentMusicX" alt="GitHub Issues"></a>
  <a href="https://github.com/bileizhen/CurrentMusicX/releases/latest"><img src="https://img.shields.io/github/v/release/bileizhen/CurrentMusicX" alt="Latest release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-green.svg" alt="GPL-3.0-only License"></a>
  <a href="#兼容性"><img src="https://img.shields.io/badge/Android-8.0%2B-blue.svg" alt="Android 8.0+"></a>
  <a href="#功能特性"><img src="https://img.shields.io/badge/UI-Compose_%2B_Miuix-3C80FF.svg" alt="Compose + Miuix"></a>
</p>

[下载应用](https://github.com/bileizhen/CurrentMusicX/releases/latest) · [界面预览](#界面预览) · [更新记录](CHANGELOG.md) · [反馈问题](https://github.com/bileizhen/CurrentMusicX/issues)

</div>

## 项目简介

CurrentMusicX 是使用 Kotlin、Jetpack Compose 和 Miuix 开发的 [CurrentMusic](https://github.com/backrooms-yrc/CurrentMusic) 原生 Android 客户端。围绕手机听歌重新设计播放页、歌词、搜索与歌单，同时适配横屏和宽屏布局。

圆形封面、封面取色背景、逐字歌词与跟手拖拽共同组成播放体验。绑定网易云后，可以直接使用网易云我喜欢和自己的歌单；也可以保留 CurrentMusic 音乐库，在两种方式之间切换。

应用源码位于本仓库的 `main` 分支，同时维护于 [CurrentMusic 的 app 分支](https://github.com/backrooms-yrc/CurrentMusic/tree/app)。音乐资料与本机登录的网易云音乐库由手机直连网易云；未在本机登录时，可复用 CurrentMusic 账号的云端网易云绑定。歌曲音源、MV 取流与网易云歌词继续使用 CurrentMusic；歌曲与 MV 音源解析需要登录 CurrentMusic 站内账号。歌曲与音质的可用性取决于所选音源和账号权限。自建曲库可独立连接 Navidrome，音频直接从自己的服务器播放。

## 功能特性

### 播放与控制

- 圆形封面、封面取色背景，支持进度拖动、音质选择与播放队列管理
- 列表循环、单曲循环、随机播放和心动模式
- 迷你播放器支持播放 / 暂停、打开队列与拖拽展开播放页；暂停后仍保留当前歌曲
- 播放页支持拖拽收起，左右滑动切换封面与歌词，上滑打开播放列表
- 后台播放、通知栏、锁屏与蓝牙控制
- MV 详情页采用横向视频封面，支持查看歌手、展开简介和直接下载；播放页支持沉浸全屏、双击播放/暂停、快进退与清晰度切换，可查看后台下载进度、取消或重试
- 可在“网络与播放”开启“允许与其他应用同时播放”，默认关闭，播放中即时生效并保存
- 自动切歌支持智能过渡或淡入淡出（择一开启，默认关闭）；淡入淡出默认 5 秒，可调 1–12 秒，智能过渡分析音频能量、节奏和静音开头后调整衔接
- 音效页使用原生 Jetpack Compose：按网易云 9.5.30 页面结构实现大师卡片轮播、精选音效与大师详情入口，复用原版图片及视频素材；视频由原生 TextureView 静音播放，仅可见卡片运行，页面不加载 WebView 或网页界面脚本。目录和声音特征直连网易云。已内置适配的 arm64 原版引擎、智能音效资源和 MNN 依赖，安装后即可使用 JSON／FIR 音效、按歌曲匹配的原版智能音效，原版 MNN 模型驱动的 AI 情绪调音师（真实调节说明／雷达）、独立原版合成算法驱动的 AI 调音大师（五项按账号保存的 DIY 偏好），以及按横竖屏／音量适配的外放音效（日常、浴室、助眠，耳机路由暂停）；会员音效核验当前账号的真实权益。智能／情绪／大师及标准音效选择按账号保存，重启重新校验，并保留本机均衡器。无需先安装网易云或导入引擎；部分个性化算法尚未接入，页面会明确提示。三种大师音效均提供独立原生详情；外放场景先浏览再应用，支持原版片段试听，AI 大师支持原声／效果对比，试听不替换主队列或已保存选择。外放选择仅在当前运行期间保留。
- 定时关闭，可延长到当前歌曲播放结束；支持同一局域网内的 DLNA 投屏

### 歌词与横屏

- 支持 LRC、YRC 和 TTML，提供逐行、逐字高亮、翻译与罗马音
- 卡拉 OK 动画兼容策略可选“仅当前行”“拓展全部行”和“总是”
- “总是”模式可按行时间为普通歌词生成近似逐字高亮
- 歌词字体、字号、粗细和时间偏移可调整
- 横屏采用左侧封面、右侧内容布局，左右滑动或拖拽切换歌词与控制栏
- 横竖屏均支持 3D 透视、逐字微幅上提和延音辉光，翻译随当前歌词平滑展开与收起
- 流烟歌词随封面取色，与 3D 倾斜互斥；开启后自动居中，中央显示当前句，翻译固定在歌词区域顶部、歌曲信息下方，进度条上方以小号文字显示下一句

歌词另有独立的“星尘歌词”开关：文字逐字聚合，切句时化作封面色粒子飘散；与流烟效果可切换使用。

### 桌面悬浮歌词

- 首次启动推荐桌面歌词灵动岛，可快捷授权开启或跳过；在系统授权后跟随后台播放，支持精致/舞台效果、翻译、字号和歌词偏移
- 应用内默认显示 FPS，返回桌面默认显示歌词；点击灵动岛可切换显示模式
- 灵动岛外观：深色药丸按物理屏幕居中，旋转封面、竖向播放进度与时间、双行歌词自适应宽度，独立字号、位置高度与不透明度设置
- 点击封面播放/暂停，点击歌词区域切换 FPS；封面操作不改变显示模式，锁定后也可操作
- 拖动、贴边与位置保存，锁定时触摸穿透，可从通知或应用内解锁
- 应用内与桌面共用歌词数据和播放时钟；桌面仅使用真实逐字时间戳，普通歌词按句切换
- [操作说明与验证范围](DESKTOP_LYRICS.md)

### 歌单与网易云音乐库

- 歌单支持可选的 Depth Ribbon 立体缎带：左右拖动穿行、点击聚焦、长按全景，最多同时绘制 72 张封面；普通列表为默认，可从歌单顶部或外观设置切换。
- 歌单封面网格、取色头部、歌单内搜索、排序、随机播放与继续播放
- 后台刷新对比歌曲变化，保留已有内容和滚动位置，新增歌曲通过动画显示
- 网易云账号支持扫码和手机验证码绑定，二维码在切到后台时保留
- 本机网易云登录后，搜索、网易云歌单、收藏、每日推荐、艺人与 MV 资料原生直连网易云；歌曲音源、MV 取流和网易云歌词走 CurrentMusic（默认 `https://music.20110208.xyz/cm/`），歌词包含逐字时间、翻译与罗马音，增强接口不可用时回退同服务的普通歌词接口
- 网易云登录凭据加密保存在本机；没有本机网易云登录时，CurrentMusic 登录后可直接使用服务器已绑定的网易云账号，无需再次扫码，凭据仍留在服务器。自己的普通歌单支持创建、重命名、删除和移出歌曲
- 登录 CurrentMusic 后，最近播放读取该账号的云端记录；歌单页同时保留 CurrentMusic 自建／已导入歌单和所选网易云音乐库，跨账号切换会重新加载
- 网易云评论区支持发布与回复；发送失败保留草稿，不自动重发，切歌／切账号时清空编辑状态
- 可将 CurrentMusic 自建歌单一键导入网易云“我喜欢”，查看进度与结果，跳过已喜欢歌曲；切换账号停止旧操作
- 可将网易云设为主音乐库，“我的 → 我喜欢”直接打开网易云我喜欢的音乐
- 开启主音乐库后，播放页短按红心直接喜欢 / 取消喜欢；长按可选择其他可收录歌单
- 主音乐库开关可随时关闭，原有 CurrentMusic 音乐库数据保留

### Navidrome 自建曲库

- 从“我的 → 自建曲库”或“设置 → 网络与播放 → Navidrome 自建曲库”连接服务器
- 支持反向代理子路径、用户名与密码登录，凭据加密保存在本机
- 专辑网格、艺术家与歌单列表、封面取色详情，搜索自建歌曲、专辑和艺术家
- 分类与详情返回保留浏览位置，支持后台刷新、自动分页及当前歌曲列表搜索
- 播放页红心直接收藏 / 取消收藏到自己的服务器；长按选择自己的歌单
- 支持新建、重命名、删除歌单与移出歌曲
- 原音质流式播放、下一首预加载、缓存复用、均衡器和服务器同步歌词
- 支持服务器允许的歌曲下载，沿用封面、歌曲信息与歌词内嵌
- 可开启“用作首页曲库”，关闭后恢复原首页，不清除其他服务的数据
- 私有曲库歌曲当前仅在本机播放，不参与 CurrentMusic 一起听与 DLNA 投屏

### 搜索与发现

- 发现页使用音乐／用户／帖子／Chat 等宽分段栏；公共聊天室支持实时消息、图片、成员提及及本人消息删除，发送失败保留草稿
- 用户资料展示上游头衔与公开 IP 属地；聊天消息上下渐隐，标题和输入栏保持清晰

- 首页搜索框联动搜索页，支持歌曲、歌手和专辑分类搜索
- 首页每日推荐采用扇形封面与黑胶展示，连续拖动跟手、松手吸附、歌名渐变切换，点击中间封面播放/暂停；刷新保留选中歌曲，支持一起听点歌与独立栏目加载状态
- 搜索历史、热搜与推荐搜索词
- 每日推荐、最近播放、音乐风格分类与艺人主页
- MV 播放与艺人、专辑浏览

### 歌曲下载与存储

- 从歌曲操作中发起下载，可选择音质和保存目录
- 后台下载进度、取消与失败重试
- 将歌名、艺人、专辑、封面和歌词直接写入音频文件，同时保存配套 LRC 与封面
- 支持 MP3、FLAC、M4A、Vorbis OGG、WAV；裸 AAC 无损封装为 M4A
- 音频缓存、下一首预加载与分项存储清理
- 完整缓存直接本地重播，缓冲进度可见，下一首音源过期前后台更新
- 当前歌曲有至少 20 秒缓冲余量（或剩余内容已全部缓冲）后再预加载下一首；余量不足 8 秒时让出网络。高码率缓冲优先满足时长，缓存统计按当前歌曲增量更新

### 一起听与账号

- 创建或加入一起听房间，与听友同步播放
- 房间密码、点歌审批、成员权限与断线恢复
- 多账号切换、个人资料、头像框和听歌统计
- 账号凭据按服务器和账户分别保存

### 界面与更新

- Miuix 分组卡片、悬浮底栏、跟手拖拽与预测返回
- 深浅主题、Monet 动态颜色、模糊与可选玻璃效果
- 可关闭的封面取色底栏光效：色雾从底边晕开，左右浓淡与高度随音乐缓缓起伏，二级页面迷你播放器同步保留
- 启动加载动画、头像联动与一级页面切换动画
- 启动时自动检查更新，也可在设置中手动检查；支持正式版与预发布渠道
- 更新说明支持图片，默认使用专用镜像下载，失败时切换备用源
- 更新包通过文件校验后请求系统安装，并核对包名与签名
- 站内公告一页一条，支持左右滑动、自动高度和图片；可选择不再显示旧公告，新公告仍会提醒

## 界面预览

<div align="center">

<img src=".github/img/home.jpg" width="252" alt="首页与迷你播放器">
<img src=".github/img/player.jpg" width="252" alt="播放页与歌词">
<img src=".github/img/search.jpg" width="252" alt="搜索页">

<img src=".github/img/artist.jpg" width="252" alt="艺人主页">
<img src=".github/img/discover.jpg" width="252" alt="音乐风格分类">

</div>

## 兼容性

| 项目 | 支持情况 |
| --- | --- |
| 最低 Android 版本 | Android 8.0（API 26） |
| 处理器架构 | ARM64（arm64-v8a），包含已适配的原版音效引擎 |
| 目标 Android 版本 | Android 16（API 36） |
| 布局 | 竖屏、横屏与宽屏导航 |
| Monet | Android 12（API 31）及以上 |
| 实时模糊 | Android 13（API 33）及以上；低版本或关闭时使用普通材质 |
| 下载保存位置 | Android 系统文件夹选择器 / SAF；Android 10 及以上默认使用 `Download/CurrentMusic` |

## 安装

从 [Releases](https://github.com/bileizhen/CurrentMusicX/releases/latest) 下载 `CurrentMusic-Android-v版本号.apk`，按系统提示安装。正式版沿用同一签名，可覆盖升级。

1. 打开应用即可浏览、搜索；使用 CurrentMusic 音源播放或下载歌曲／MV 前，先在“我的”登录 CurrentMusic 站内账号。本机网易云登录不替代站内取流鉴权。
2. 在首页搜索歌曲，或打开每日推荐、歌单和最近播放。
3. 需要使用网易云收藏时，在网易云账户绑定页通过扫码或手机验证码完成绑定。
4. 按需要开启“网易云作为主音乐库”，并在设置中调整音质、歌词与外观。

CurrentMusic 服务器地址可以在“设置 → 网络与播放”中修改，默认为 `https://music.20110208.xyz/cm/`，用于歌曲音源、CurrentMusic 账户、资料装饰、旧音乐库与一起听。本机已登录的网易云音乐资料和音乐库使用独立直连请求；云端绑定通过该服务器使用。

从旧版本升级后，登录 CurrentMusic 即可复用服务器上的网易云绑定；云端凭据不会下载到本机。若需要本机直连与原版音效，可在网易云账号页单独登录。已有本机登录优先保留，云端歌单和最近播放仍会读取。

## 常见问题

### 网易云我喜欢与 CurrentMusic 我喜欢有什么区别？

绑定页开启“网易云作为主音乐库”后，“我的 → 我喜欢”和播放页红心直接使用网易云收藏。关闭后使用 CurrentMusic 音乐库。长按播放页红心仍可选择其他可收录歌单，包括自己的网易云歌单。

切换这个开关不会删除已有 CurrentMusic 歌单。

### 默认服务器地址里的 `/cm/` 是什么？

`/cm/` 是 CurrentMusic 服务的接口路径，用于歌曲音源、CurrentMusic 登录、旧音乐库、个人资料和一起听。本机登录时，网易云资料和歌单直连网易云；云端绑定、歌曲音源和网易云歌词使用此服务器。

### 下载的歌曲和歌词保存在哪里？

下载时可以选择保存文件夹。Android 10 及以上默认保存在 `Download/CurrentMusic`；使用系统文件夹选择器指定目录时，需要授予该目录的访问权限。

歌曲信息、封面与歌词会写入音频文件，并保存配套歌词和封面文件。其他播放器能否显示内嵌歌词、翻译或逐字效果，取决于文件格式和该播放器的支持情况。

### 普通歌词也能逐字显示吗？

在歌词设置中将卡拉 OK 动画兼容策略改为“总是”，应用会按每行时间生成近似逐字高亮。有真实逐字时间的歌词优先使用原始时间信息。

### 如何检查更新？

在“设置 → 检查更新”进入独立更新页，右上角菜单可调整启动检查、更新渠道和下载来源。更新默认使用 [CurrentMusic 专用镜像](https://music.20110208.xyz/cm/x/download/latest)，下载失败或与目标发布的大小、SHA-256 不符时会尝试备用源。

更新下载由后台任务管理，关闭弹窗、切换页面或返回桌面后继续执行；可从设置或通知重新查看进度，完成后手动请求安装。已开启灵动岛时，下载进度优先于音效提示和 FPS／歌词显示，下载完成后以圆形应用图标和“点击查看”常驻显示，点击仅打开更新详情；失败或取消后短暂提示并恢复原有内容。应用重新打开后恢复任务状态；下载中可取消或更换来源，换源会从头重新下载。下载完成并通过校验后，由 Android 系统确认安装。首次更新可能需要允许应用安装未知来源的安装包。自行构建的包与官方包签名不同时，无法直接覆盖安装。

下载体验包：构建 debug 时同时传入 `-PisolatedDebug=true -PupdateDownloadExperience=true`。独立应用显示为“CurrentMusic 下载体验”，版本带 `-alpha.0` 后缀，因此同版本正式发布仍可被检查到。可用于体验真实下载、校验与灵动岛进度；完成态点击查看更新详情，再在页面中主动请求系统安装。普通构建及 release 不开启下载体验模式。版本数字预览仅限独立 debug 构建，不能带入正式包。

### 如何反馈问题？

在 [Issues](https://github.com/bileizhen/CurrentMusicX/issues) 附上应用版本、手机型号、Android 版本、复现步骤与实际现象。需要日志时，可从设置中的“导出日志”生成诊断文件；分享截图前请遮挡个人信息。

## 隐私

- 本机网易云登录、资料和网易云音乐库直接请求网易云；没有本机登录时可通过所选 CurrentMusic 服务器使用该账号的云端网易云绑定。CurrentMusic 账户、云端音乐资产、播放记录和一起听使用所选服务器。音频、MV 和图片从对应服务返回的地址加载。
- 本机 Token 和网易云 Cookie 使用 Android Keystore / AES-GCM 加密保存；密码和验证码不持久化。网易云凭据不会发送给 CurrentMusic 服务器。
- 搜索历史、歌曲元数据、歌词和队列保存在本机；多账号凭据按服务器与账户隔离。
- 下载文件保存到用户选择的目录，退出登录不会删除已下载文件。
- 日志对账户凭据脱敏；诊断文件包含应用与设备版本等定位信息。
- 完整说明见 [隐私说明](PRIVACY.md)。

## 从源码构建

需要 JDK 17 及以上、Android SDK Platform 37.0 和 Build Tools 35.0.0。Java / Kotlin 编译目标为 17。配置 `ANDROID_HOME`，或创建本地 `local.properties`：

```properties
sdk.dir=C\:/Users/your-name/AppData/Local/Android/Sdk
```

获取源码：

```bash
git clone https://github.com/bileizhen/CurrentMusicX.git
cd CurrentMusicX
```

Windows 构建与检查：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Linux / macOS：

```bash
chmod +x gradlew
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

设备测试需要连接 Android 设备或启动模拟器：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。Windows 项目路径包含中文时，可通过 `subst` 映射到盘符后构建。

正式包使用 `:app:assembleRelease`，签名配置从未入库的 `keystore.properties` 读取，字段为 `storeFile`、`storePassword`、`keyAlias` 和 `keyPassword`。未配置签名时生成未签名 Release 包。当前更新镜像使用 `https://music.20110208.xyz/cm/x/download/latest`，构建时无需配置镜像密钥；GitHub 下载源仍可用。

## 参与开发

欢迎通过 Issues 提出建议，或提交 Pull Request 修复问题、改进体验。请说明改动解决的问题与实际验证结果。

- [更新记录](CHANGELOG.md)
- [第三方依赖与许可证](THIRD_PARTY_NOTICES.md)

## 开源协议

CurrentMusicX 按 [GPL-3.0-only](LICENSE) 分发。CurrentMusic 上游、界面组件、歌词资源和下载依赖保留各自的版权与许可声明，完整来源见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 致谢

- [CurrentMusic](https://github.com/backrooms-yrc/CurrentMusic)：音乐服务与业务接口
- [LeiFetch](https://github.com/bileizhen/LeiFetch)、[123PanX](https://github.com/bileizhen/123PanX) 与 [XBlocker](https://github.com/bileizhen/XBlocker)：界面、通用组件与更新流程参考
- [Miuix](https://github.com/compose-miuix-ui/miuix)：Compose 界面组件
- [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) 与 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)：部分界面与玻璃组件的上游来源
- [AMLL TTML DB](https://github.com/amll-dev/amll-ttml-db)：逐字歌词资源
- [LXGW WenKai](https://github.com/lxgw/LxgwWenKai)：歌词字体
- AndroidX / Jetpack Compose、Media3、Coil、Jaudiotagger、ZXing 及其他开源项目

## 浏览量

<div align="center">

![访问统计](https://count.getloli.com/@bileizhen_CurrentMusicX?name=bileizhen_CurrentMusicX&theme=original-new&padding=7&offset=0&align=center&scale=1&pixelated=1&darkmode=auto)

</div>
