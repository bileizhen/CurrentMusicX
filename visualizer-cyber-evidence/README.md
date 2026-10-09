# 视频参考预设的验证证据

原视频只在本地读取与抽帧分析，未复制进仓库，未使用其音轨进行测试。正式测试使用已有 MusicRepository / PlayerController / MusicService 播放 This Feeling — my!lane，RMX5060 / Android16 / 隔离 Debug。

`preview-trial.log/jsonl` 是显示增益优化前的原生测试，88.238s、OK 1 test，保留为试验。最终 preview-final.log/jsonl 为128.21s、OK1test；player-ui-final.log 为最终 APK 的9项原生/设备检查，39.728s全部通过。layout-final.xml/log另记此前8项 connected 验证。build-final.log、unit-summary.json、contour-unit.xml、lint-final.xml 均为最终286项/6个新增单测的源码门禁。

preview.mp4 按 frames.jsonl 编码，568个实际样本/29.969s，约18.95Hz。只使用索引中列出的实际JPEG，目录中的旧未列出图片不进入编码。video-decode.json确认能完整解码。player*.png为实际播放页，preview*.png为新预设预览与真实横屏。

动态 MP4 来自真实 UiAutomation 截图及单调时间索引，无插帧、无音轨；采样率远低于生产帧钟，不用于证明120 FPS。正常动态仅在用户已授权的隔离验证进程临时将 ValueAnimator multiplier 设为1，结束恢复。正式系统动画、音量、显示和温控均不改动。

预览左封面右仪表；标准播放页保持原圆形封面与桥接转场。照片是实际设备像素，不是 AI 设计图。完整测试与范围见根目录 PHONK_VISUALIZER_CYBER_REACTOR.md。
