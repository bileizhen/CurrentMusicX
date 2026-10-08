# Phonk Visualizer M1 验收报告

本阶段范围：原生 VSync 渲染调度、音频帧插值、基础实时频谱、帧率偏好、性能监测、自动优化和生命周期。M2 四套预设、AGSL、沉浸舞台尚未实现。本报告位于仓库根目录，可随 Git 提交交付；不依赖被忽略的 docs/ 目录。

## 架构

唯一音频来源仍是 MusicService 的 ExoPlayer audioSessionId。M0 Visualizer 回调通过独立 HandlerThread 和 conflated Channel 向 Default 分析线程交付拥有自身缓冲的 FFT/波形。UI 不另建播放器或采集器。M1 修正采集时间戳为 System.nanoTime，与 Choreographer 使用相同的单调时钟；elapsedRealtime 包含深睡眠时间，不可直接与 VSync 比较。

VisualizerFrameClock 的生产实现使用 Compose withFrameNanos，测试实现使用可控通道。帧调度按当前实际显示频率整数分频，动画用实测 dt，长停顿重置积分并限制最大 dt；没有用固定 delay 模拟生产帧率。Auto 请求同分辨率模式中最高可用频率，最高 120。30/60/90/120 都是局部 Canvas preferredFrameRate 偏好；系统是否切换由系统决定。120 Hz 未切换成 90 Hz 时，90 档采用 60 FPS 整数分频并明确显示限制。省电、温控、硬件上限和性能档位均只限制有效请求，不覆盖用户保存的档位。

VisualizerFrameInterpolator 在主线程持有可复用 FloatArray，按捕获间隔估计信号过期容限，用 Attack/Release 指数包络跨帧率平滑频谱和能量，并平滑波形。原始频谱绕过分析与渲染包络。Kick/瞬态使用序列号与事件时间戳，即使 StateFlow 合并中间数据仍能消费最新真实事件；每个事件只触发一次，立即响应并按秒衰减，过期事件不补造鼓点。seek、切歌、会话或采样格式变化会重置 generation，避免沿用旧歌曲的能量。

VisualizerRenderLayer 是独立 Canvas。120 Hz revision 只在 draw 阶段读取；播放器和调试文本不订阅高频帧状态。路径、Stroke 和音频输出数组复用，统计文本及音频读数每 500 ms 更新。四套 M2 预设可消费同一插值帧与调度器。当前只绘制真实频谱、波形、能量线与基本鼓点标记。

## 性能与自动优化

统计明确区分：用户请求、偏好 FPS、有效目标、显示 Hz、VSync tick Hz、Canvas draw FPS、Window 帧报告 FPS、真实呈现 FPS。应用内 Window FrameMetrics 不能证明屏幕呈现，真实呈现字段保持未知，需外部 SurfaceFlinger 原始纳秒时间戳验证。SurfaceFlinger timestats 的毫秒直方图有量化误差，不用其平均 FPS 冒充实际 120 FPS。

有界 1024 项环形缓冲存储 5 秒内的帧间隔、Canvas CPU 耗时、音频回调和 Window 指标。计算均值/P95/P99、音频帧龄、Window deadline 超时、报告丢失数、目标绘制迟帧比例与估计缺失时隙。Window deadline 可能仍按物理 120 Hz 判断 30/60 FPS 帧，因此自动优化依据目标绘制迟帧，而非将低 FPS 请求误判为严重掉帧。

持续约 3 秒压力后逐级 ULTRA → HIGH → BALANCED → ECO，先降低频谱/波形绘制密度，再限制 60/30 FPS；至少 5 秒降级冷却。连续健康约 15 秒才恢复一档。切后台重新进入、用户切档或关闭自动优化会重置控制器，不改 DataStore 中的用户目标。

只在页面可见且 RESUMED 时捕获和绘制。暂停后允许 600 ms 能量衰减，然后停止时钟、清零并撤销刷新率偏好；后台、关闭、异常、无权限、DLNA/MV 切换立即停止并释放 FrameMetrics 监听与线程。采集或 Window 指标失败不影响播放。DataStore 保存目标帧率、自动优化、原始/平滑选项；采集默认关闭。生产代码不上传、不记录音频或性能数据，测试文件仅来自合成音源的隔离验证包。

## 测试方法与记录

M1 基础门禁已通过：assembleDebug、246 项 JVM 测试（本阶段新增 32 项）、lintDebug；Lint 0 error、47 warning、7 hint。原生 VSync 短测通过，15 分钟长测通过（总计 909335 ms，含随后生命周期检查），横屏播放器 2 项回归通过。全套设备回归结果见下表，不能称为全绿。首次长测的 15 分钟绘制段已完成，但生命周期操作使用 ActivityScenario.moveToState 等待主线程空闲，与持续 VSync 冲突，最终整体超时；该次不能计为通过。其弱混合音源的能量断言随后失败，因此该次帧数也不能作为真实有信号频谱的最终验收。48 kHz 独立 M0 回归再次识别正确频率；当前设备音量下 RMS 仅约 0.006，弱混合音源会被 AS_PLAYED 的 8 位量化压低到断言阈值以下。已替换成 M0 相同幅度的 48 kHz 交替音调/鼓点 WAV，并加入能量断言，通过测试内 localhost HTTP 范围请求以固定 16 KiB 缓冲流式提供，仍由原服务播放器播放。生命周期测试使用真实 Activity 后台/前台、旋转、recreate 操作，避免等待持续渲染进入 idle。

## 已知限制与下一阶段

- 最高 120 FPS 是能力上限，不能保证所有系统模式、硬件、温控或 GPU 都达标。
- 当前没有 Android 8–12 真机验证，不把 API 26 编译/Lint 检查当作旧设备通过。
- M0 基线的 unifiedDockFadesNavigationAndSlidesMiniWithoutResizingThePage 已在改动前 3bcb6f9 复现，保留失败断言与基线证据，独立记录回归结果。
- 未混入 MarkdownText.kt 与 UpdateDialog.kt 的既有修改；不推送远端。
- M2 接入建议：预设只消费 VisualizerInterpolatedFrame、quality 和真实 dt，在独立绘制层复用调度器；先 Canvas 降级画法，再 API 33+ Shader；保持 PlayerPagerArtwork 状态和转场不变。各预设必须重新测量 GPU/呈现时间，不沿用基础频谱的性能结论。
## 可复现命令

```powershell
# M: 是指向本仓库的既有 ASCII subst；只对本进程设置变量。
$env:JAVA_HOME='C:\Program Files\Android\openjdk\jdk-21.0.8'
$env:TEMP='D:\Android\tmp'
$env:TMP=$env:TEMP
$env:ANDROID_SERIAL='5PXS9TSKLFIJAU9T'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug `
  -PisolatedDebug=true --no-daemon --max-workers=1 `
  '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC -XX:CICompilerCount=2 -XX:HeapBaseMinAddress=8g -Dfile.encoding=UTF-8' `
  '-Pkotlin.compiler.execution.strategy=in-process' '-Pkotlin.incremental=false'
# 单独运行 native VSync 长测，不能使用 ComposeTestRule 的虚拟时钟证明 FPS。
.\gradlew.bat :app:connectedDebugAndroidTest -PisolatedDebug=true `
  '-Pandroid.testInstrumentationRunnerArguments.class=io.github.currencortex.music.VisualizerRenderDeviceTest#realAudioFrameRatesLifecycleAndFifteenMinuteStability' `
  '-Pandroid.testInstrumentationRunnerArguments.timeout_msec=1800000'
# 与长测同时启动外部测量；脚本最后撤销 timestats 诊断开关。
.\scripts\measure_visualizer_surface_frames.ps1 -Serial $env:ANDROID_SERIAL -OutputDirectory .verification/final-present -MaximumSeconds 1200
.\scripts\summarize_visualizer_surface_frames.ps1 -InputDirectory .verification/final-present -OutputPath .verification/final-present/presented.csv
```

设备测试也沿用上面 JVM/worker 参数。系统音量、全局显示模式、系统刷新率开关均未修改。验证包 com.bileizhen.currentmusic.verification 与正式包独立。

JVM 覆盖：各档 VSync 分频、重复/逆序/无效时间、长停顿、30/60/90/120 一致包络、低采样到 120 Hz、可复用数组、静音/过期衰减、单次 Kick/瞬态、被合并的事件、过期事件、generation/暂停清空、NaN/Infinity、取消/自然退场、显示模式上限、59.94/119.88 Hz、节能与温控、自动档位与原始偏好隔离、压力/冷却/恢复/禁用、停止状态、Window 与实际呈现区别、目标迟帧与 Window deadline 分离、报告丢失及缺失时隙。

原理参考：[Compose preferredFrameRate 官方 API](https://developer.android.com/reference/kotlin/androidx/compose/ui/preferredFrameRate.modifier?authuser=2)；[Android 16 FrameTracker 源码](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android16-release/services/surfaceflinger/FrameTracker.cpp)。后者 dumpStats 的三列顺序为 desiredPresentTime、actualPresentTime、frameReadyTime，INT64_MAX 是尚未完成的 fence；本次从官方源码核对并按第二列测量。

## 修改文件清单

- `app/src/androidTest/java/io/github/currencortex/music/VisualizerCaptureTest.kt`
- `app/src/androidTest/java/io/github/currencortex/music/VisualizerDebugUiTest.kt`
- `app/src/androidTest/java/io/github/currencortex/music/VisualizerRenderDeviceTest.kt`
- `app/src/androidTest/java/io/github/currencortex/music/VisualizerSettingsTest.kt`
- `app/src/main/assets/legal/PRIVACY.md`
- `app/src/main/java/io/github/currencortex/music/AppContainer.kt`
- `app/src/main/java/io/github/currencortex/music/core/media/MusicService.kt`
- `app/src/main/java/io/github/currencortex/music/core/visualizer/AudioAnalysisEngine.kt`
- `app/src/main/java/io/github/currencortex/music/core/visualizer/AudioSignalAnalyzer.kt`
- `app/src/main/java/io/github/currencortex/music/core/visualizer/VisualizerCaptureSource.kt`
- `app/src/main/java/io/github/currencortex/music/core/visualizer/VisualizerFrameClock.kt`
- `app/src/main/java/io/github/currencortex/music/core/visualizer/VisualizerFrameInterpolator.kt`
- `app/src/main/java/io/github/currencortex/music/core/visualizer/VisualizerPerformanceMonitor.kt`
- `app/src/main/java/io/github/currencortex/music/core/visualizer/VisualizerQualityController.kt`
- `app/src/main/java/io/github/currencortex/music/data/settings/MusicSettingsRepository.kt`
- `app/src/main/java/io/github/currencortex/music/data/visualizer/VisualizerRenderSettings.kt`
- `app/src/main/java/io/github/currencortex/music/feature/player/PlayerViewModel.kt`
- `app/src/main/java/io/github/currencortex/music/feature/visualizer/AudioAnalysisDebugDialog.kt`
- `app/src/main/java/io/github/currencortex/music/feature/visualizer/VisualizerDisplayObserver.kt`
- `app/src/main/java/io/github/currencortex/music/feature/visualizer/VisualizerPerformanceReadout.kt`
- `app/src/main/java/io/github/currencortex/music/feature/visualizer/VisualizerRenderLayer.kt`
- `app/src/main/java/io/github/currencortex/music/feature/visualizer/VisualizerRenderState.kt`
- `app/src/main/java/io/github/currencortex/music/feature/visualizer/VisualizerWindowMetrics.kt`
- `app/src/test/java/io/github/currencortex/music/core/visualizer/AudioAnalysisEngineTest.kt`
- `app/src/test/java/io/github/currencortex/music/core/visualizer/VisualizerPerformanceTest.kt`
- `app/src/test/java/io/github/currencortex/music/core/visualizer/VisualizerRenderingTest.kt`

- `PHONK_VISUALIZER_M1.md`
- `PRIVACY.md`

- `scripts/measure_visualizer_surface_frames.ps1`
- `scripts/summarize_visualizer_surface_frames.ps1`

验证证据位于 visualizer-m1-evidence/；包含原始呈现时间戳、统计 CSV/JSONL、真机截图、JVM/设备结果及既有基线失败记录，不含测试音频文件或账户数据。

## 真机结果

RMX5060，Android 16 / API 36，隔离 Debug 包，USB 供电、电量 100%，系统音量保持 26/160。长测中电池温度约 35.8–37.3°C。显示模式维持 120.00001 Hz；90 档偏好请求未促使系统切换，采用有效 60 FPS 分频。没有改全局显示模式或刷新率设置。

下表为长测前每档 10 秒的最后一组五秒滚动统计，数值为真实音频绘制，不是虚拟测试时钟：

| 请求 | 有效目标 | Canvas FPS | Window 报告 FPS | 绘制间隔均值 / P95 / P99 ms | 音频 Hz |
|---|---:|---:|---:|---|---:|
| Auto | 120 | 117.89 | 117.89 | 8.48 / 8.98 / 19.51 | 19.38 |
| 30 | 30 | 29.97 | 29.97 | 33.37 / 45.53 / 52.45 | 18.85 |
| 60 | 60 | 59.03 | 59.05 | 16.94 / 17.95 / 32.87 | 18.59 |
| 90 | 60 | 59.06 | 59.04 | 16.93 / 18.22 / 30.57 | 18.51 |
| 120 | 120 | 119.10 | 119.11 | 8.40 / 9.21 / 19.76 | 18.71 |

15 分钟段的 179 个统计观测点：Canvas 平均 119.926 FPS，范围 118.918–120.259；各窗口 P95 平均 9.133 ms，P99 平均 16.494 ms，目标绘制迟帧比例平均 1.629%，Window deadline 超时平均 0.112%。这些是各五秒窗口统计的平均值，不冒充整段精确 P95。Canvas CPU 绘制平均 0.363 ms；整个测试进程 CPU 增量约为单核的 110.52%，包括主线程、绘制线程、服务与测试开销，不能把 Canvas 耗时视为整个进程成本。

音频回调平均 18.660 Hz，最大观测帧龄 58.684 ms；91 个观测点 RMS 超过 .005，最大 Kick 序列号 411。连续约 11 万次真实绘制时，绘制层重组计数保持 13，质量保持 ULTRA。初末 PSS 296168 → 273344 KiB，期间范围 268951–304298 KiB；Java 堆约 16.8–33.2 MiB，native 堆 62.54 → 49.66 MB。未见单调内存增长；未运行逐对象分配分析器，不宣称零分配或无任何长期泄漏。

SurfaceFlinger 原始实际呈现时间戳：103 个有效窗口、12866 个呈现间隔，跨约 598.70 秒、实际覆盖约 107.12 秒，采样窗口加权平均 **120.10 FPS**。这覆盖长测后半段的间歇性真实呈现，不能称为 900 秒完整连续呈现追踪；前半段采集脚本曾误选同名窗口容器，相关零值数据已排除。切档完整呈现补测见下表；该次设备已进入温控状态，不能与此前 120 Hz 段混为同一环境。

JVM 60 Hz / 90 Hz / fractional Hz 策略、压力降级和恢复均为确定性模拟验证。真机只验证当前 120 Hz 显示模式；不把 90 请求当作实际 90 FPS。Auto/120 达到约 120 的真实呈现水平，仍有迟帧，不能保证每一秒或所有复杂预设持续满 120。

| 验证 | 结果 | 证据 |
|---|---|---|
| Debug / JVM / Lint | 通过；246 / 0 error | [可视化 JVM](visualizer-m1-evidence/TEST-io.github.currencortex.music.core.visualizer.VisualizerRenderingTest.xml)、[性能 JVM](visualizer-m1-evidence/TEST-io.github.currencortex.music.core.visualizer.VisualizerPerformanceTest.xml)、[Lint](visualizer-m1-evidence/lint-debug.txt) |
| Native VSync 短测 | 1 通过 | [结果](visualizer-m1-evidence/native-smoke.xml) |
| 15 分钟 + 生命周期 | 1 通过 | [结果](visualizer-m1-evidence/native-long.xml)、[音频/帧统计](visualizer-m1-evidence/performance-long.jsonl)、[真实呈现 CSV](visualizer-m1-evidence/presented-long.csv)、[原始时间戳](visualizer-m1-evidence/latency-long) |
| 横屏播放器回归 | 2 通过 | [结果](visualizer-m1-evidence/landscape-regression.xml) |
| 其余完整设备套件 | 155：138 通过、14 失败、3 跳过 | [逐项结果](visualizer-m1-evidence/regression-all.xml) |
| Android 8–12 | 未运行；当前没有对应设备/模拟器 | 不以编译/Lint 代替真机兼容性验证 |

失败分类：主页 readiness、MiniOverlay、PlayerSongActions 超时各 1；歌词 TextLayoutResult 选择到重复节点 2；NativeCapabilities 前置“退出登录且播放暂停”不满足 7；已知底栏缺少返回节点 1；M0 采集能量检查 1（同一代码的独立采集测量已通过）。三项跳过属于现有需要显式授权/登录的实时契约或真实更新镜像测试。已以 M0 提交 efa03dd 独立工作树在同一设备执行同套 155 项：138 通过、14 失败、3 跳过。13 个失败方法与 M1 初测相同；M1 初测独有采集能量不足，基线独有 UserCapabilities 滚动失败。两组数量相同不等于失败集合完全相同；证据为 m0-baseline-regression-all.xml。采集测试现增加实际 isPlaying、媒体 ID 与 300 ms 进度前置检查，再执行最终复测。不删除断言、不清除账户来强行通过。

M0 独立真实采集复测：937.5 / 6000 / 93.75 Hz 均正确识别；约 18.7–18.8 Hz 回调，低音音源检测到 7 次 Kick。弱混合音源、仅一次五秒鼓点间隙采样、ActivityScenario 等待 idle、以及旧 Gradle daemon 的 native 分配失败均记录为调试过程中的失败；最终采用相同幅度的 M0 音源、至少覆盖完整音源周期的短测、真实 Activity 操作和单次 JVM 构建进程后通过。全套回归失败仍保留。

截图：[120 FPS 档](visualizer-m1-evidence/fps_120.png)、[Auto](visualizer-m1-evidence/auto.png)、[30](visualizer-m1-evidence/fps_30.png)、[60](visualizer-m1-evidence/fps_60.png)、[90](visualizer-m1-evidence/fps_90.png)、[长测结束](visualizer-m1-evidence/soak-final.png)、[横屏](visualizer-m1-evidence/landscape.png)、[重建](visualizer-m1-evidence/recreated.png)。截图只包含本地合成音源；120 档截图峰值 937 Hz、RMS .006，不能用截图中的显示 Hz 替代呈现测量。
## 最终复测与范围

最终代码的四项针对性设备验证全部通过（[targeted-final.xml](visualizer-m1-evidence/targeted-final.xml)）：真实采集及正确频率、菜单入口默认关闭、DataStore 选项持久化、20 秒/档的 Native VSync 与生命周期验证。采集测试现等待实际 isPlaying、正确 mediaId 和至少 300 ms 输出进度；未降低 RMS、频率或鼓点断言。它对已有套件中出现的会话/音轨就绪竞争增加约束，不把尚未定位的整个套件波动宣称已彻底消除。

最后一次完整套件复测停在 SongDownloadTest.corruptAudioAndCancellationDoNotLeaveFinishedFiles，超过设置的单例超时仍未退出。仅强制停止隔离验证包来结束挂起；Instrumentation 报 Process crashed 是人工停止的结果，不是本次观察到的应用自然崩溃。保存为[中断结果](visualizer-m1-evidence/regression-final-interrupted.xml)，未执行到末尾的测试不能算通过。此前完整 155 项结果与 M0 基线结果仍是完整比较依据，不能用最后四项通过代替整套通过。既有 UI/前置条件失败仍未解决，整个项目回归目前未全绿。

最终全五档实际呈现补测：设备 Thermal Status = 2（MODERATE），电池约 39.0°C，当前显示 60 Hz。自动温控限制生效，即使逐级画质优化关闭，本地偏好最高仍为 60；用户 DataStore 目标不变。此为真实温控降级验证，不是设备失去 120 Hz 硬件能力，也不是一次实际 90/120 FPS 达标测量。

| 用户档位 | 本地偏好 / 有效目标 | Canvas FPS | SurfaceFlinger 实际呈现 FPS | 有效窗口 |
|---|---|---:|---:|---:|
| Auto | 60 / 60 | 60.13 | 60.12 | 3 |
| 30 | 30 / 30 | 30.05 | 30.02 | 3 |
| 60 | 60 / 60 | 60.11 | 59.64 | 2 |
| 90 | 60 / 60 | 60.12 | 60.13 | 3 |
| 120 | 60 / 60 | 60.11 | 60.12 | 2 |

证据：[实时统计](visualizer-m1-evidence/performance-modes-final.jsonl)、[实际呈现 CSV](visualizer-m1-evidence/presented-modes-final.csv)、[原始呈现窗口](visualizer-m1-evidence/latency-modes-final)。系统全局刷新率、系统音量与温控设置均未修改。解除温控后的自动恢复由实时状态监听和策略单元测试覆盖，本次没有等待设备降温后重新做完整真机恢复验收。

呈现解析器也用独立 60 FPS 时间戳夹具验证：排除零值、INT64_MAX pending fence、重复读到的旧环形缓冲，恢复 60 FPS。统计脚本只选实际 buffer layer，排除同名窗口容器；阶段标记被厂商 Logcat 噪声淘汰后使用周期记录和最后已知阶段，切档等待五秒排除旧档数据。

交付结论：M1 渲染、真实音频驱动、120 Hz 条件下的约 120 FPS 呈现与 15 分钟运行、温控降级、后台/关闭释放均有验证证据；旧设备兼容性及整个工程的全绿回归仍不能宣称完成。后续先单独处理回归前置条件与既有测试问题，再进入 M2 四套预设并重新测量性能。本阶段没有 M2/AGSL/沉浸舞台实现，不推送远端。