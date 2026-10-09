# Phonk Visualizer M2.7 — 视觉优化与验证

验证日期：2026-10-09。开发分支 `app`，M2 本地基线 `cc3c972`。本阶段只整理现有视觉预设与诊断预览；正式播放器、导航、PlayerPagerArtwork、Media3 播放核心和 M0 FFT 未改动。UpdateDialog、MarkdownText 的无关工作区修改保留，排除于本阶段提交。

## A. 视觉优化与架构

先审查 M1/M2 报告、原始实机图、渲染状态和生命周期。M2 浅色弹窗的舞台偏小、默认调试信息过多、横屏底部裁剪；频谱硬截断让较强频率趋同；Dark 的事件断言没有证明实际 RGB/切片显示；旧长测也没有整个测试完成证据。

保持同一音频分析源和同一个 `withFrameNanos` 时钟。舞台分为取色氛围、低分辨率 Shader 背景、Canvas 音频几何、Coil 封面、前景边框。控制区域独立布局，不跟随封面的缩放或震动。高频 revision 只在绘制与 graphicsLayer 中读取，统计仍低速发布。新增弹窗继续走已有音频权限、用户开关与生命周期路径，默认不采集。

| 预设 | 原有问题与修改 | 音频响应与降级 | 剩余限制 |
| --- | --- | --- | --- |
| Neon Pulse | 每侧最多 28 条镜像竖向柱，低频较厚、端点圆润、主/辅色渐变、柔和边缘光；双层菱形增加纵深、慢转和 Kick 轻脉冲；细分支电流、取色背景和封面近光 | 高度来自原 FFT 插值，绘制映射采用有限软肩，保留频率之间差异，不改 FFT；ECO 保留 14 条、双菱形和封面边界，关闭电流/Shader | 真机比较选择 A；两段捕获采样率不同，不宣称由其证明 A 性能优于 B |
| Orbit Spectrum | 各档独立缓存均匀角度，96/72/48/32 根；封面色渐变、圆端点；内环 1.08r、第二环 1.19r、频谱从 1.27r 起，封面外光分层 | 原频谱映射；ECO 仍是完整 32 根环形，ULTRA 96 根；此预设本来就使用 Canvas | 原生 PlayerPagerArtwork 接入属于 M3，此次不改变其裁剪/转场 |
| Bass Impact | 径向线按尺寸与画质有界，长短/远近有区别；低强度黑金环境和橙金高光，真实 Kick 的池化冲击波复用 | 封面用真实 dt 的阻尼弹簧，Bass 目标约 1.028、Kick 上限 1.08（Bass 预设 1.12），同一次事件只加一次速度；ECO 保留基本线条、频谱和封面 | 静态图不能证明回弹节奏，动态行为结合真实采集记录与弹簧测试判断 |
| Dark Glitch | RGB 只在真实 Transient 包络为正时应用；缓存弱/强两档 1.2px/5px effect；水平切片移至封面纹理的 37%/59% 区域，反向短偏移；黑红、低对比扫描线 | 原 75ms、450ms 冷却与 1500ms 全局门限保留，无新增伪事件。减少动态的偏移/运动显著衰减；BALANCED/ECO 不建 AGSL，保留 Canvas/切片能力 | 低采样率实机帧序列能证明实际像素出现，不能替代高帧率系统录屏对 75ms 动画连续性的验收 |

封面保持真实原图，方形/圆形按比例 Crop，8dp 圆角与发光边缘一致。根据用户后续要求，取色现用于背景、频谱、边框、粒子和 Shader 主/辅色，不再只有背景少量混色。每次成功加载采样 12×12 像素，后台线程按有界 16 个色相桶提取彩色主体，忽略透明像素/深黑边框，选择另一显著颜色作为辅色；灰度封面保持中性。Neon/Orbit 使用提取色，Bass/Dark 保留 35% 原暖金/红色识别。切歌按新封面更新，异步结果校验当前 URL，避免旧结果覆盖新色；不逐帧读像素，不主动回收 Coil bitmap。未加载或加载失败保留占位与预设默认色。原 240ms 封面切换保留。

预览采用局部 MIUIX 深色配色，不修改应用全局主题。纵向中央方形舞台、底部歌曲信息及 2×2 预设；参数、FPS、Kick 和性能信息默认折叠。横向左舞台右滚动控件，safeDrawing Insets 避开系统栏。窗口首选刷新率与 Window FrameMetrics 绑定真实 DialogWindowProvider，退出恢复原偏好。

Path、Stroke、频率角度数组、粒子与冲击波池复用。Shader resolution 只在尺寸变化时更新，RGB 遵循预乘 alpha，暂停/切换/关闭释放 Shader 与 RenderEffect 引用。新增 Atmosphere/foreground 绘制不产生第二个帧时钟。

## B. 实机图片与动态证据

全部最终视觉截图来自 RMX5060 / Android 16 隔离包，实际播放 **This Feeling — my!lane**，搜索歌曲 ID `1897255962`，时长 `163829ms`。通过现有 MusicRepository 搜索、PlayerController/MusicService 播放，不写死测试音频 URL、不使用模拟 FFT、不上传音频。

| 图片 | 内容 |
| --- | --- |
| [neon-pulse.png](visualizer-m27-evidence/neon-pulse.png) | Neon 最终默认 A、竖屏完整预览（最终长测更新） |
| [orbit-spectrum.png](visualizer-m27-evidence/orbit-spectrum.png) | Orbit 真实圆形封面、完整环谱 |
| [bass-impact.png](visualizer-m27-evidence/bass-impact.png) | Bass 实机瞬间，非动画合成图 |
| [dark-glitch.png](visualizer-m27-evidence/dark-glitch.png) | Dark 平静状态、封面取色与灰度封面 |
| [landscape.png](visualizer-m27-evidence/landscape.png) | 真正横屏、安全 Insets、全部必要控件 |
| [recreated.png](visualizer-m27-evidence/recreated.png) | Activity 重建后的真实歌曲封面与舞台 |
| [layout-narrow-short.png](visualizer-m27-evidence/layout-narrow-short.png) | Android Compose 捕获，模拟 320×400dp 内容边界；已滚到控件底部，无实际播放 |
| [layout-large-font.png](visualizer-m27-evidence/layout-large-font.png) | 请求 620×320dp、1.8 字号，实际宽度受设备 Dialog 约束；已滚到控件底部，无实际播放 |

Neon 实机 A/B：A 为 [镜像竖向柱](visualizer-m27-evidence/neon-vertical.png)，B 为两侧横向短线。选择 A，因为真实频率峰谷更清楚，减少等间距横线的机械感。B 仅保留内部诊断开关，无新增用户设置或采集器。[A 视频](visualizer-m27-evidence/neon-vertical.mp4)、[B 视频](visualizer-m27-evidence/neon-horizontal.mp4)均来自 This Feeling，分别约 19Hz 与 7Hz 捕获，工具版本不同，不据此比较物理 FPS。

[正常 Dark 视频](visualizer-m27-evidence/dark-normal.mp4)包含 575 个实际样本/29.986s；[减少动态视频](visualizer-m27-evidence/dark-reduced.mp4)含 555 个样本/29.982s。正常帧 [0012](visualizer-m27-evidence/dark-normal-glitch.jpg)有可见 RGB 边缘分离与上下水平切片，邻近的 [静态帧](visualizer-m27-evidence/dark-normal-rest.jpg)恢复灰度；[减少动态帧](visualizer-m27-evidence/dark-reduced-glitch.jpg)偏移显著较小。保持原 75ms 持续时间与事件冷却，未插入事件。frame index 与真实 Transient/request 包络随视频提供。请求时的包络不等同于截图 buffer 呈现时刻，截图可能滞后一帧；不能由这几个样本精确测定 75ms 呈现时长或证明每次事件都显示。

系统 `screenrecord` 不可正常使用：普通终止本次启动 PID 27554 被系统拒绝；用户仅授权 Root 结束该 PID，但 `su` 返回 inaccessible/not found，未能结束。多日运行的另一 PID 8052 未处理，未重启、改系统权限、显示、音量或温控。此残留进程可能干扰性能环境，不能忽略。

备用记录来自 `UiAutomation.takeScreenshot()` 的实际设备 JPEG，携带真实 elapsedRealtime 请求时间与 Transient/包络索引；编码采用毫秒时间基准、原间隔 VFR，无补帧、无模拟视觉、无音频。初版同步压缩约 6–7Hz，容易漏掉短事件；改为容量 2 的后台压缩队列（约最多 3 个在途 bitmap，失败/结束回收），正常与减少动态分别达到约 19.18/18.51Hz。仅测试记录创建截图 bitmap，生产特效不创建 bitmap。它们证明实际动态像素，仍不能作为原生 60/120fps 连续录屏验收。早期文字发黑、黑区切片问题已修正，试验在 `.verification/m27-trial-*`，不冒充最终效果。

正常动态测试在用户授权范围内仅改变隔离 Instrumentation 进程的 ValueAnimator multiplier 为 1，使用测试专用 hidden setter，finally 恢复并断言原值。没有修改 Settings.Global。减少动态路径同时记录实际系统减少动态与 DataStore 设置。

## C. 性能与稳定性

同一 RMX5060、Android 16、隔离 Debug、USB 供电、This Feeling 实际采集。完整受控长测的显示频率与呈现数据见下表。第二轮失败长测曾短暂记录 Canvas/Display 约 120，但整轮被新页面替换打断，不能据此宣称四套效果持续 120fps 达标。必须以实际 SurfaceFlinger 第二列呈现时间戳统计。

复用原 M2 SurfaceFlinger 脚本，修正同标题 Activity/Dialog 的图层选择：从顶层所属窗口 hash 找容器，再按 parentId 找 buffer，不依赖 list 顺序。多层无法识别时不猜。原统计及 phase 边界过滤脚本继续复用。

| 预设/阶段 | 请求 / 显示 | Canvas / Window 中位 FPS | SF 采样 FPS | SF P95 / P99 范围 ms | GPU 均值 / P95 中位 ms | Canvas CPU 中位 ms | 进程 CPU | PSS 范围 MiB |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| NEON_PULSE | 120 / 60 | 60.11 / 60.12 | 60.119 | 16.70–16.71 / 16.75–16.79 | 3.44 / 6.17 | 4.84 | 142.9% | 297.7–312.4 |
| ORBIT_SPECTRUM | 120 / 60 | 60.12 / 60.12 | 60.118 | 16.71–16.73 / 16.77–16.82 | 3.83 / 6.92 | 5.22 | 146.3% | 298.9–345.0 |
| BASS_IMPACT | 120 / 60 | 60.13 / 60.12 | 60.118 | 16.73–16.76 / 16.87–16.95 | 3.56 / 6.97 | 1.90 | 120.8% | 310.6–336.7 |
| DARK_GLITCH | 120 / 60 | 60.12 / 60.12 | 60.119 | 16.69–16.88 / 16.76–17.02 | 3.56 / 6.91 | 1.88 | 114.0% | 297.2–346.7 |
| soak | 120 / 60 | 60.11 / 60.11 | 59.590 | 16.66–16.88 / 16.69–66.44 | 3.46 / 5.98 | 4.97 | 131.0% | 277.4–320.3 |

以上是间歇 SurfaceFlinger 窗口，不是完整 trace：每套 4 个稳态窗口、约 8.3s；连续段 155 个窗口、共 325.744s，呈现 59.590fps。P95/P99 表示各采样窗口的范围，CPU 可超过 100%（多核全进程），不是主线程占用。原始窗口及显示/温度数据在 surface-controlled.zip，边界剔除见 presented-steady.csv；数值来源 performance-summary.json。

完整 runner **OK (1 test)，1066.335s**；其中连续真实歌曲绘制至少 900s。179 个连续段记录跨 899.720s（首个样本晚于起点约 5s），期间自然循环 5 次、绘制计数增加 53311、层重组增加 10。暂停/seek/后台/屏幕关闭/前台恢复/真实横屏/重建/关闭全部通过，finally 原隔离动画倍率恢复为 0.0，队列/播放/设置恢复。整个方法通过与恢复日志分别为 long-controlled.log / long-controlled-restored.log。

连续段保持 ULTRA，未观察到自动降级；ECO 人工检查为 Canvas 降级，自动控制策略另有 JVM 测试，本轮不能声称实机触发了自动降级/恢复。自然循环的采集重新准备会重置统计，部分 Canvas 记录为 0，SF P99 最差窗口 66.44ms，不能隐藏边界抖动。电池温度 37.1–38.7°C、Thermal Status 全程 1、USB 供电；不能将显示 60Hz 直接归因于温控。连续段 PSS 277.4–320.3MiB，闭页后旋转/重建的全进程 PSS 约 398.8MiB；未做堆保留分析，因此不宣称整窗旋转后的图像缓存已回收到初始值。Shader/渲染与采集资源释放的断言通过。


Canvas CPU 指标仅覆盖已有 VisualizerRenderLayer 绘制测量范围，不代表额外 Atmosphere/foreground、封面和整窗 CPU。Process CPU time 增量与 GPU/Window 数据是整个隔离进程/窗口范围。SurfaceFlinger latency 是间歇采样的 127 帧环，去重并剔除切换/ECO/生命周期区间，不能冒充 15 分钟所有帧的完整 trace。

## D. 构建、测试与回归

JDK `21.0.8`，ASCII 映射 `M:\`、TEMP/TMP `D:\Android\tmp`，`-PisolatedDebug=true`。正式安装包和个人数据不覆盖。实际使用命令：

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug -PisolatedDebug=true --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC -XX:CICompilerCount=2 -XX:HeapBaseMinAddress=8g -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process' '-Pkotlin.incremental=false'
```

| 项目 | 实际结果 |
| --- | --- |
| 最终源码构建、测试 APK、Lint | BUILD SUCCESSFUL，1m18s；0 error、49 warning、7 hint（与 M2 同数）；build-final.log / lint-final.xml |
| JVM | 278 项通过；5 项视觉映射/弹簧，4 项封面取色验证，新增共 9 项 |
| 初轮定向设备 | 两项布局通过，一项真实歌曲搜索网络失败；保留 first-device.xml/first-gate.log，未改用合成音源 |
| 最终真实歌曲 / 15 分钟 / 生命周期 | 完整 runner OK (1 test)，1066.335s；连续 900s、生命周期与 finally 恢复均通过 |
| 最终布局与现有设置/诊断 | connectedDebugAndroidTest 定向 6 项全部通过，1m4s；ui-final.xml/log；最终 A 默认只改变频谱绘制，不改变布局 |
| 基线回归 | 关键设备回归初轮 27 项：24 通过、3 失败；逐方法与 M1 比较见 regression-critical.json。3 项原基线通过的方法单独复测另记；全 155 项及下载取消长测未重跑 |
| API 26–32 Canvas | 源码门限、ShaderPolicy JVM 与 API36 ECO 可检查；没有对应设备/AVD，低版本运行未验证 |

旧 M1/M0 完整基线均为 155 项：138 通过、14 失败、3 跳过，13 个失败方法共有。失败包含 readiness/布局超时、歌词重复节点、NativeCapabilities 登录前置及旧采集能量。M1 最后完整复测卡在 SongDownload cancellation 后人工终止，不能称为全绿。此次不清除账号、不削弱断言、不用旧合成音源长测替代 This Feeling。本轮只运行关键回归，未冒充全 155 项通过。初轮队列两项与歌词隐藏控件一项在横屏类之后失败，保留原日志；不改断言。单独复测结果另记，不能以复测覆盖初轮失败。

第一次真实长测整个 runner 在 601.477s 失败，未达到 15 分钟通过：第三次自然循环位置从 162873ms 归零时，MusicService 按现有 STATE_ENDED 流程暂停并重新准备同曲，渲染正确释放，但原断言在准备间隙要求一直 running。原始失败与 JSONL 保留为 long-first-failed.*。检查 MusicService 后，只在实测自然位置回绕、同曲/有播放意图/准备中/无错误时允许最多 5s 恢复，且必须重获播放、采集>5Hz、音频年龄<500ms与渲染；保留之后的稳态断言。没有改变播放核心，也没有允许任意停止通过。修正后的完整长测结果另行记录。

动态捕获四预设完整测试通过（178.526s）；异步捕获正常 Dark 通过（73.567s）、减少动态通过（71.441s），A 对比通过（71.946s）。一次减少动态运行在准备阶段挂起后仅 force-stop 隔离包，runner 的 Process crashed 为人工停止结果；另一次 A 在准备超时失败，保存 neon-vertical-preparation-failed.log，均未进入音频/绘制阶段。测试现先启动真实隔离 Activity 再进行 readiness/search，保留 120s deadline，未改变账户或播放器业务来强行通过。不能确认准备卡顿的系统内部原因，不将其归因为冻结或 Shader。最后 A 重试及最终长测使用这一前台准备方式。

最初横屏/重建截图拍在 Dialog 入场首帧，整个窗口尚未完全呈现。修正测试为等待新的 renderer、封面成功与渲染后，再等待原生入场完成，最终实机图已人工检查；不以早期首帧截图认定主题错误或布局通过。

## 修改文件

- `core/visualizer/VisualizerEffectState.kt`：绘制映射与封面阻尼回弹。
- `core/visualizer/VisualizerArtworkPalette.kt` 与对应测试：封面主体/辅色提取和灰度、透明、深边框验证。
- `feature/visualizer/render/VisualizerPresetRenderer.kt`：四套 Canvas 与分层、缓存角度。
- `feature/visualizer/render/ShaderEffectRenderer.kt`：RGB 实际事件、uniform 缓存及 alpha。
- `feature/visualizer/VisualizerPresetPreview.kt`：分层、封面取色防旧结果、实际弹窗偏好。
- `feature/visualizer/VisualizerRenderLayer.kt`：实际弹窗 Window 解析。
- `feature/visualizer/AudioAnalysisDebugDialog.kt`：接入局部预览弹窗。
- `feature/visualizer/VisualizerPreviewDialog.kt`：暗色、安全 Insets、自适应布局与折叠参数。
- `VisualizerVisualPolishTest.kt`、`VisualizerPreviewLayoutTest.kt`、`VisualizerPresetDeviceTest.kt`：单元、边界布局、实际歌曲与动态证据。
- `scripts/measure_visualizer_surface_frames_m2.ps1`：修正测量图层绑定。
- `scripts/encode_visualizer_capture.py`：实际设备帧与原时间间隔录像封装。
- 本报告及 `visualizer-m27-evidence/`：实际验证证据。

## E. M3 交接

正式集成可直接复用：统一音频 frames、VisualizerFrameInterpolator/RenderLayer 单一 VSync、各预设 renderer 的 Atmosphere/render/foreground、现有 DataStore 参数与预设选择、画质控制、暂停/退出资源释放。

标准圆形播放器优先复用 Orbit：将圆形舞台几何围绕现有 PlayerPagerArtwork，保持原封面 pager 与 shared transition 所有权；不可用预览自己的 AsyncImage 替换正式封面。沉浸方形舞台复用 Neon/Bass/Dark 与独立封面绘制，正式导航、播放控制自动隐藏和横屏全屏由 M3 接入。Dialog 专用 Window 解析不能直接硬编码成所有舞台的窗口，标准页面/全屏分别按真实 LocalView 所在窗口管理偏好。

M3 仍需独立验证歌词、队列、切歌与封面 URL 改变、原转场、本地播放/一起听/DLNA/MV 能力门限和 opt-in 权限。高刷引擎保留最高 120 请求能力，但复杂四预设的物理 120fps 需要可提供实际 120Hz 且无录屏干扰的设备补测。API26–32、系统高帧率录屏的 Dark 连续性亦保留为明确门禁；不能把本次未验证项目写为完成。

第二轮长测在 971.409s 失败，约 816s 连续段后 Launcher 新建 MainActivity，替换了正在绘制的预览窗口；音乐仍播放，原页面退出使采集与绘制释放。这轮保留 long-second-failed.*，不计为完成。第三轮仅对测试 Activity 窗口临时设置 FLAG_KEEP_SCREEN_ON，并在 finally 清除；没有改变系统熄屏时间。页面身份与生命周期写入记录。


初轮关键回归的 3 个失败方法单独重跑全部通过（12.744s，OK 3 tests，regression-retry.log/json）。其余 24 项初轮通过，包括原圆形桥接转场、封面/歌词手势、迷你播放器和横屏；未修改旧断言。旋转后测试顺序可能影响显示状态，但尚未证明根因，保留为间歇测试问题。

