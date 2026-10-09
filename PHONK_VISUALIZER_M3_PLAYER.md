# Phonk Visualizer — 播放页标准模式接入

用户要求效果直接显示在播放页。本次在 M2.7 视觉优化基础上接入标准圆形封面模式；独立预览保留为菜单中主动选择的入口。完整沉浸导航、自动隐藏控制、逐曲预设记忆仍属于后续 M3 工作，不能称为 M3 全部完成。

## 行为

播放页“播放与歌词”菜单 → “音乐可视化”现在打开设置，开启特效后返回播放页即可显示。默认关闭，仅开启按钮请求 RECORD_AUDIO 权限。开关、四套预设、画质、帧率、强度、减少动态与自动优化沿用原 DataStore。

标准模式在原 PlayerArtwork 下方绘制四套预设，开启时为频谱预留空间，关闭恢复原封面尺寸和布局。竖屏仍由原 PlayerPagerArtwork 桥接封面页与歌词页；没有替换封面图片、重写转场或新增播放器。横屏保持左封面、右歌词/控制区。

标准模式保留圆形原图，Canvas 特效围绕它绘制；方形封面、RGB 色差和封面切片仍在沉浸预览显示。没有将方形封面的运动变换叠加到原转场图层。

## 架构和生命周期

PlayerVisualizerArtwork 复用现有 VisualizerRenderLayer 的唯一 VSync、FrameInterpolator、四个 Renderer 与 PlayerViewModel 的 AudioAnalysisEngine。新增绘制回调只调整标准舞台比例，不创建第二个时钟、Visualizer 或 ExoPlayer。

音频可见性使用所有者租约求并集，防止某个预览窗口退出误停仍可见的播放页。收起播放器、竖屏滑向歌词、队列/菜单覆盖、后台时停止标准绘制与采集，释放刷新率偏好。暂停保留原 600ms 包络衰减。横屏封面始终可见，因此在右侧查看歌词时仍可绘制。

沿用原 CAST/MV 门限，不对远程投屏、视频采集。一起听使用已有本地 ExoPlayer 会话，不另建音源。小尺寸 Coil 图在后台提取封面色，URL 改变取消旧请求，图像加载失败采用默认色，不回收 Coil 的共享 Bitmap。

高频状态仅由 Canvas 绘制读取；PlayerScreen 不订阅 FFT/帧状态。控制、歌词和队列继续使用原状态来源。

## 修改文件

- `core/visualizer/CaptureVisibilityRegistry.kt` 与测试：采集可见性租约。
- `core/visualizer/AudioAnalysisEngine.kt`：隐藏状态文案适配正式页面。
- `feature/player/PlayerViewModel.kt`：共享引擎的可见性所有者管理。
- `feature/player/PlayerScreen.kt`：标准圆形封面周围的绘制层与菜单设置入口。
- `feature/visualizer/PlayerVisualizerArtwork.kt`：Canvas、封面取色、帧钟与窗口生命周期。
- `feature/visualizer/VisualizerSettingsDialog.kt`：主动启用、预设、参数与可选沉浸预览。
- `feature/visualizer/VisualizerRenderLayer.kt`：统一调度器的可选绘制回调。
- `VisualizerDebugUiTest.kt`、`PlayerVisualizerDeviceTest.kt`：菜单行为和真实歌曲的播放页验证。

## 验证

最终门禁包含 assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug 和定向 connectedDebugAndroidTest：**BUILD SUCCESSFUL，2m42s**。JVM **280/280**，Lint **0 error、49 warning、7 hint**，与 M2.7 警告数量一致。使用相同 JDK21、ASCII M:\ 路径、isolatedDebug 和既有 Gradle 参数。

设备定向回归 **28/28 通过，52.035s**，包含新菜单默认关闭、设置保存、开启后保留原圆形桥接封面、歌词切换、原封面手势/迷你播放器、队列、歌词显示和横屏布局。将横屏类放在本轮最后执行。M2.7 初轮三个间歇失败与单独通过的原始结果仍保留，不因这次全绿删除；调整顺序与通过相关，但尚不能认定根因。未重跑完整 155 项。

实际播放页原生测试 **OK (1 test)，26.384s**。音源限定 **This Feeling — my!lane**，四套预设保持同一个 Audio Session，实际 48kHz 波形/FFT 进入原引擎；此值是输入信号采样率，不是 Visualizer 回调频率。Canvas 日志约 59–60次/秒，但未单独测量标准模式 SurfaceFlinger 呈现率，不能据此宣称 120fps。关闭页面后断言音乐继续、采集清零、帧钟和刷新率偏好释放；暂停/恢复和横竖屏恢复通过，结束恢复原队列/播放/设置，清除测试窗口亮屏 flag。

主机日志、JUnit XML 与截图在 `visualizer-m3-player-evidence/`。所有图均来自真实播放页，画面含原进度条、控制按钮与歌词预览。主机实际检查了 Neon、Orbit 与横屏截图，未发现封面/控制区裁剪。

| 图 | 实际内容 |
| --- | --- |
| [Neon](visualizer-m3-player-evidence/neon-pulse.png) | 原圆形封面、菱形电流；歌曲开头低能量，未制造假频谱 |
| [Orbit](visualizer-m3-player-evidence/orbit-spectrum.png) | 原封面外围双环和真实环形频谱 |
| [Bass](visualizer-m3-player-evidence/bass-impact.png) | 标准封面周围的低音/径向几何 |
| [Dark](visualizer-m3-player-evidence/dark-glitch.png) | 原彩色圆形封面及外围扫描/频谱，不套用方形封面切片 |
| [横屏](visualizer-m3-player-evidence/landscape.png) | 左舞台、右侧进度与控制，无独立预览页面 |

首次测试代码编译使用了不存在的 play 方法，已改为项目真实 playList。原生测试第一次在 UiAutomation 重复授予权限时被设备拒绝（13.229s）；未进入视觉断言。隔离包已获 RECORD_AUDIO，因此改为明确检查已有授权，不再尝试重复 grant；正式界面仍仅由用户点击开启请求权限。失败日志保留为 native-permission-failed.log。

## 尚未完成与下一步

API26–32 设备兼容、标准播放页物理 120 FPS、一起听/DLNA/MV 的完整在线回归尚不能以源码检查代替。现有采集门限已复用，但本轮真实歌曲测试只覆盖本地 Media3 播放。

标准模式目前不对原转场封面施加缩放、震动、灰度、RGB 和切片；这些封面动作仍在沉浸预览生效，外围几何继续响应强度与运动参数。完整 M3 需增加正式沉浸舞台、自动隐藏控制、逐曲预设记忆，并继续对开启效果时的完整 sheet 转场与业务模式进行设备验证。复用本次引擎/预设，避免再建采集器或播放器。
