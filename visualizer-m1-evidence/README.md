# M1 本地验收证据

所有音频数值和截图来自 localhost 合成 WAV，经当前 MusicService 唯一 ExoPlayer 实际播放；不包含音频文件、账户令牌或上传数据。

- performance-long.jsonl：15 分钟播放/绘制和随后生命周期动作的本机统计。
- latency-long/、presented-long.csv：SurfaceFlinger 第二列 actualPresentTime 原始值和有效窗口统计；12866 个间隔，约 598.7 秒跨度中采样 107.1 秒。不是整段连续追踪。
- native-long.xml、native-smoke.xml：真实帧时钟测试；不采用 Compose 虚拟测试时钟测 FPS。
- regression-all.xml：M1 155 项设备套件初次结果，138 通过 / 14 失败 / 3 跳过。
- m0-baseline-regression-all.xml：修改前 efa03dd 的同设备 155 项套件结果，138 通过 / 14 失败 / 3 跳过。13 个共同失败；M1 独有能量检查失败，基线独有用户页滚动失败。不能将两组失败集合说成完全相同。
- m0-baseline-known-failure.xml：M0 阶段保留的更早 3bcb6f9 底栏失败证据。
- landscape-regression.xml：横屏播放器 2 项通过。
- TEST-*.xml、lint-debug.txt：可视化 JVM 验证与 Lint 输出。
- *.png：每档调试页、长测结束、横屏与重建截图。屏幕上显示的刷新率/FPS 读数不代替原始实际呈现时间戳。

完整解释、命令和最终复测结果见仓库根目录 PHONK_VISUALIZER_M1.md。
- targeted-final.xml：最终四项针对性验证全部通过。
- performance-modes-final.jsonl、presented-modes-final.csv、latency-modes-final/：Thermal Status 2 下五档的真实 60/30 FPS 降级测量。
- regression-final-interrupted.xml：挂在既有下载测试后人工停止验证包的复测，不能算整套通过。
