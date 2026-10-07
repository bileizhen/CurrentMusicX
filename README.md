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

应用源码位于本仓库的 `main` 分支，同时维护于 [CurrentMusic 的 app 分支](https://github.com/backrooms-yrc/CurrentMusic/tree/app)。歌曲、音质和歌词的可用性取决于所选服务器、音源及账号权限。

## 功能特性

### 播放与控制

- 圆形封面、封面取色背景，支持进度拖动、音质选择与播放队列管理
- 列表循环、单曲循环、随机播放和心动模式
- 迷你播放器支持播放 / 暂停、打开队列与拖拽展开播放页；暂停后仍保留当前歌曲
- 播放页支持拖拽收起，左右滑动切换封面与歌词，上滑打开播放列表
- 后台播放、通知栏、锁屏与蓝牙控制
- 定时关闭，可延长到当前歌曲播放结束；支持同一局域网内的 DLNA 投屏

### 歌词与横屏

- 支持 LRC、YRC 和 TTML，提供逐行、逐字高亮、翻译与罗马音
- 卡拉 OK 动画兼容策略可选“仅当前行”“拓展全部行”和“总是”
- “总是”模式可按行时间为普通歌词生成近似逐字高亮
- 歌词字体、字号、粗细和时间偏移可调整
- 横屏采用左侧封面、右侧内容布局，左右滑动或拖拽切换歌词与控制栏

### 歌单与网易云音乐库

- 歌单封面网格、取色头部、歌单内搜索、排序、随机播放与继续播放
- 后台刷新对比歌曲变化，保留已有内容和滚动位置，新增歌曲通过动画显示
- 网易云账号支持扫码和手机验证码绑定，二维码在切到后台时保留
- 可将网易云设为主音乐库，“我的 → 我喜欢”直接打开网易云我喜欢的音乐
- 开启主音乐库后，播放页短按红心直接喜欢 / 取消喜欢；长按可选择其他可收录歌单
- 主音乐库开关可随时关闭，原有 CurrentMusic 音乐库数据保留

### 搜索与发现

- 首页搜索框联动搜索页，支持歌曲、歌手和专辑分类搜索
- 搜索历史、热搜与推荐搜索词
- 每日推荐、最近播放、音乐风格分类与艺人主页
- MV 播放与艺人、专辑浏览

### 歌曲下载与存储

- 从歌曲操作中发起下载，可选择音质和保存目录
- 后台下载进度、取消与失败重试
- 将歌名、艺人、专辑、封面和歌词直接写入音频文件，同时保存配套 LRC 与封面
- 支持 MP3、FLAC、M4A、Vorbis OGG、WAV；裸 AAC 无损封装为 M4A
- 音频缓存、下一首预加载与分项存储清理

### 一起听与账号

- 创建或加入一起听房间，与听友同步播放
- 房间密码、点歌审批、成员权限与断线恢复
- 多账号切换、个人资料、头像框和听歌统计
- 账号凭据按服务器和账户分别保存

### 界面与更新

- Miuix 分组卡片、悬浮底栏、跟手拖拽与预测返回
- 深浅主题、Monet 动态颜色、模糊与可选玻璃效果
- 启动加载动画、头像联动与一级页面切换动画
- 启动时自动检查更新，也可在设置中手动检查；支持正式版与预发布渠道
- 更新说明支持图片，默认使用专用镜像下载，失败时切换备用源
- 更新包通过文件校验后请求系统安装，并核对包名与签名

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
| 目标 Android 版本 | Android 16（API 36） |
| 布局 | 竖屏、横屏与宽屏导航 |
| Monet | Android 12（API 31）及以上 |
| 实时模糊 | Android 13（API 33）及以上；低版本或关闭时使用普通材质 |
| 下载保存位置 | Android 系统文件夹选择器 / SAF；Android 10 及以上默认使用 `Download/CurrentMusic` |

## 安装

从 [Releases](https://github.com/bileizhen/CurrentMusicX/releases/latest) 下载 `CurrentMusic-Android-v版本号.apk`，按系统提示安装。正式版沿用同一签名，可覆盖升级。

1. 打开应用，登录 CurrentMusic 账号。
2. 在首页搜索歌曲，或打开每日推荐、歌单和最近播放。
3. 需要使用网易云收藏时，在网易云账户绑定页通过扫码或手机验证码完成绑定。
4. 按需要开启“网易云作为主音乐库”，并在设置中调整音质、歌词与外观。

服务器地址可以在“设置 → 网络与播放”中修改，默认为 `https://music.20110208.xyz/cm/`。

## 常见问题

### 网易云我喜欢与 CurrentMusic 我喜欢有什么区别？

绑定页开启“网易云作为主音乐库”后，“我的 → 我喜欢”和播放页红心直接使用网易云收藏。关闭后使用 CurrentMusic 音乐库。长按播放页红心仍可选择其他可收录歌单，包括自己的网易云歌单。

切换这个开关不会删除已有 CurrentMusic 歌单。

### 默认服务器地址里的 `/cm/` 是什么？

`/cm/` 是 CurrentMusic 服务的接口路径。原生客户端的登录、歌单与一起听等功能使用这组接口；部署自己的服务器时，填写对应的 CurrentMusic 服务地址。

### 下载的歌曲和歌词保存在哪里？

下载时可以选择保存文件夹。Android 10 及以上默认保存在 `Download/CurrentMusic`；使用系统文件夹选择器指定目录时，需要授予该目录的访问权限。

歌曲信息、封面与歌词会写入音频文件，并保存配套歌词和封面文件。其他播放器能否显示内嵌歌词、翻译或逐字效果，取决于文件格式和该播放器的支持情况。

### 普通歌词也能逐字显示吗？

在歌词设置中将卡拉 OK 动画兼容策略改为“总是”，应用会按每行时间生成近似逐字高亮。有真实逐字时间的歌词优先使用原始时间信息。

### 如何检查更新？

在“设置 → 检查更新”手动检查，也可开启启动时自动检查。更新默认使用 `updates.bileizhen.top` 专用镜像，下载失败时会尝试备用源。

下载完成并通过校验后，由 Android 系统确认安装。首次更新可能需要允许应用安装未知来源的安装包。自行构建的包与官方包签名不同时，无法直接覆盖安装。

### 如何反馈问题？

在 [Issues](https://github.com/bileizhen/CurrentMusicX/issues) 附上应用版本、手机型号、Android 版本、复现步骤与实际现象。需要日志时，可从设置中的“导出日志”生成诊断文件；分享截图前请遮挡个人信息。

## 隐私

- 登录、搜索、歌单、账号绑定与一起听请求由所选服务器处理；音频、MV 和图片从对应服务返回的地址加载。
- 本机 Token 使用 Android Keystore / AES-GCM 加密保存；密码和验证码不持久化，网易云 Cookie 由服务器管理。
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

正式包使用 `:app:assembleRelease`，签名配置从未入库的 `keystore.properties` 读取，字段为 `storeFile`、`storePassword`、`keyAlias` 和 `keyPassword`。未配置签名时生成未签名 Release 包。官方更新镜像的本地配置见 [更新代理说明](services/update-proxy/README.md)；无需该配置也可以使用 GitHub 下载源。

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
