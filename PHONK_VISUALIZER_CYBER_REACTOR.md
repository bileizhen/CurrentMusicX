# Cyber Reactor · 视频参考预设

参考为用户本地视频 `黄飞杨_20261009_012320.mp4`（86.07s，1024×576，30fps）。检查了 0/5/10/15/25/35/50/65/80s 的实际帧：左侧液态发光环和双尖峰，右侧技术仪表、波形、频谱和垂直电流，背景电路；音乐强段包含大幅画面放大/模糊。视频仅作视觉参考，不作为采集音源，不复制其中歌曲、品牌图或音轨。

新增 `CYBER_REACTOR`，沿用 DataStore 的稳定名称保存，旧预设和默认关闭不变。播放页原圆形封面仍居中，外围为音频环、电路、右侧电流、底部波形/频谱仪表；沉浸预览左封面右仪表。实际歌曲信息保留在原播放器/预览控件中，无伪造 BPM 或时间显示。

轮廓使用真实 FFT、波形、低音/高音包络与 BeatPulse 构成上下起伏和双尖峰；4 个缓存轮廓分别为 192/144/96/64 点。暂停沿用原 600ms 衰减，静默为基圆，不自行生成鼓点。波形显示按当前 RMS 归一化，设置噪声底线、48 倍上限及最终限幅，仅改变绘制，保留原信号和极性。冲击波和粒子复用已有有界池。减少动态降低轮廓幅度，封面回弹仍由原真实 dt 弹簧处理。预览封面缩放保持原上限，未加入视频中的全屏强闪或持续放大模糊。

默认冰蓝，加载封面后由原主/辅色提取驱动圆环、电流和仪表，继续遵守用户“根据封面动态取色”。仅 Canvas，无新依赖、Shader、播放器、Visualizer 或帧钟；原 API26 最低要求不变。

## 修改文件

- VisualizerEffectConfig.kt：新增稳定枚举名称。
- CyberReactorContour.kt / CyberReactorContourTest.kt：缓存音频轮廓、静默/边界/减少动态/信号源不变验证。
- CyberReactorRenderer.kt：发光环、HUD、波形、频谱、电路与电流。
- VisualizerPresetRenderer.kt：统一调度和资源清理，预览封面位置匹配。
- VisualizerPresetPreview.kt：预览左右舞台与五个预设布局。
- PlayerVisualizerArtwork.kt：标准圆形封面布局标记，保留原桥接转场。
- VisualizerPresetDeviceTest.kt：This Feeling 的预设原生验证入口。
- measure_visualizer_surface_frames_m2.ps1：将新预设纳入原工具的阶段识别，未新增 FPS 工具。

## 验证

最终基础门禁（assembleDebug / assembleDebugAndroidTest / testDebugUnitTest / lintDebug）**BUILD SUCCESSFUL，3m26s**。JVM **286 项全部通过**，新增 6 项；Lint **0 error、49 warning、7 hint**，警告数与前阶段一致。使用既有 JDK21、ASCII M:\、isolatedDebug 与 Gradle 参数。

显示增益版本 connectedDebugAndroidTest 定向 **8 项全部通过**；最后增加强度0的纯轮廓检查后，最终 APK 再通过 **9 项原生/设备测试，39.728s，OK 9 tests**（真实播放页1项、同样的8项菜单/小屏/大字体/设置/原圆形桥接转场检查）。不将定向测试冒充完整155项回归。

最终预览原生测试 **128.21s，OK 1 test**，实际 This Feeling — my!lane。正常动态仅临时设置隔离进程动画倍率1，finally 恢复原值0，恢复队列/播放/设置。包含预设动态、ECO、seek、暂停/恢复、后台/熄屏、横屏/重建与关闭。测试只使用既有 ExoPlayer，会话未因选择预设而切换。

原生记录6个新预设窗口：采集 **18.94–19.31Hz**、Canvas **59.54–60.07次/秒**、已有 Canvas 计时范围 **2.32–2.91ms**，真实 Kick 序列到44。后两项不是全窗CPU或物理呈现FPS；本轮未做 SurfaceFlinger 120fps 验收。基础286项包含静默不制造尖峰、减少动态、异常输入、有界输出/复用、显示增益极性/限幅及强度0轮廓停止。

**实际视频**：[preview.mp4](visualizer-cyber-evidence/preview.mp4)，568 个实际样本/29.969s，约18.95Hz，按原始毫秒时间间隔编码，无插帧、无音轨。编码只读取 frames.jsonl 所列文件，不混入测试目录中未列出的旧图片。视频解码通过，原始索引与记录随交付；该记录不代表生产FPS。

| 实机图 | 内容 |
| --- | --- |
| [预览](visualizer-cyber-evidence/preview.png) | 左侧真实封面、音频轮廓双尖峰、右側波形/频谱及电流 |
| [横屏预览](visualizer-cyber-evidence/preview-landscape.png) | 新预设实机横屏与必要控件 |
| [播放页](visualizer-cyber-evidence/player.png) | 原播放器、原圆形封面、外围新特效及底部仪表 |
| [播放页横屏](visualizer-cyber-evidence/player-landscape.png) | 原左封面/右进度和控制，不进入独立预览也能显示 |

播放页截图遵守设备实际动画倍率0对应的减少动态状态，因此尖峰幅度低于正常动态预览。系统偏好没有修改。实际图已检查，舞台和控制区无明显重叠或裁剪。

尚未验证：API26–32 真机、该新预设的物理120 FPS、15分钟独立长测与完整155项回归。前阶段的长测不能冒充本预设长测。下一步可随正式沉浸舞台集成继续验证完整宽屏布局与高刷；本次未新增全屏导航或改动播放核心。
