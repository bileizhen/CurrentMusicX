# M2.7 原生验证证据

环境：RMX5060 / Android 16，隔离 Debug 包 `com.bileizhen.currentmusic.verification`。实际音源为 This Feeling — my!lane，现有 MusicRepository 搜索，现有 Media3 播放器播放。未用模拟 FFT、未上传音频。

`neon-pulse.png`、`orbit-spectrum.png`、`bass-impact.png`、`dark-glitch.png` 是实际预览窗口截图；`landscape.png`、`recreated.png` 为真实旋转/重建后的设备截图。`layout-*.png` 是 Compose 设备测试模拟内容尺寸和大字体，已滚至控件底部，无实际播放。

四个 MP4 来自 UiAutomation 原生截图序列，保留 frames.jsonl 的真实请求间隔。没有插帧、没有音轨、没有合成视觉；这是约 7–19Hz 的记录工具，不能证明 60/120 FPS 连续性或每次 75ms Glitch 呈现。视频使用毫秒时间基准，媒体头部的 tbr 不等于实际截图频率。`video-decode.json` 使用 passthrough 和毫秒 time base 解码验证。

- Dark 正常：575 样本 / 29.986s，5 个请求时正包络。
- Dark 减少动态：555 样本 / 29.982s，3 个请求时正包络。
- Neon A 竖柱：571 样本 / 29.944s，最终选择。
- Neon B 横线：216 样本 / 29.984s，早期同步记录工具。不能据不同采样率宣称性能差异。

`*-final.log/jsonl` 为最终动态验证；不含 final 的早期 Dark 日志是试验过程。`long-first-failed.*`、`long-second-failed.*` 是失败的长测，均不得当作完整通过。第二轮有新 MainActivity 替换正在绘制的预览窗口，采集随原页面退出停止。完整结果以报告及对应 runner log 为准。

`build-final.log` 为最后的 M2.7 源码构建，JVM 共 278 项通过。`ui-final.xml/log` 为 6 项布局/设置/诊断设备测试。低版本设备未提供，编译与 ECO 不替代 API26–32 运行验证。

本次启动的 screenrecord PID27554 无法普通终止；用户授权仅对此 PID 使用 Root，但 su 不可用，仍是环境限制。另一已运行多日的录屏进程未处理。没有改变全局显示、音量或温控设置。
