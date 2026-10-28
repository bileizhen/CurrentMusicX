# 网易云原生 USB 独占输出适配

## 来源和边界

实际提取自用户 PLR110 的 `com.netease.cloudmusic` 9.6.15 / 9006015，arm64-v8a。
手机与本地 base.apk SHA-256 均为
`e2b52565f3ca037277ba9850d61750d28102c03abba02219b9839f0136ed06c9`。
原 APK 留在临时分析目录，不随项目提交。应用只内置未经修改的
`libncmusbaudio.so`，SHA-256 为
`150daa62df3b2f5a9b9bb5d54a3108e90495e50853c4b5ac7df3f63137f70a7a`。
原驱动归网易及其组件作者所有，适配不表示网易授权或背书。

同包的 `libc++_shared.so` 与项目已内置音效运行库哈希一致：
`3e6d529147ffd66bd31fab85c8902b526abd1c06e43554ce64b97519e255e4b3`。
新 JNI 适配器单独链接此共享运行库，原 Oboe 后端继续使用原来的静态运行库。
启动前校验驱动摘要及全部必需的导出符号；不能兼容其他版本的任意替换库。

这是独立 USB Host 路径，不调用 Android MixerAttributes，不加载网易云完整播放器、
FFmpeg、账号模块或网络接口，不绕过签名、登录、付费授权或设备访问授权。
它与现有严格 Android Bit-perfect 模式并列。

## 静态逆向证据

最初的 9.6.15 32 位参考包中，`im0.b.l(UsbDevice)` 先暂停播放器，再调用
`UsbManager.openDevice()`，把 `getFileDescriptor()` 交给
`NCMNativeAudioPlayer._useUSBDevice(int)` 并设置设备类型。
最终实现采用手机实际安装的 arm64 原生库，重新核对其接口：

| arm64 函数 | 地址 | 观察与适配 |
| --- | --- | --- |
| `DeviceManager::CreateDevice(int)` | `0x338ac` | 以授权 FD 包装设备；返回非平凡的 `shared_ptr<AudioDevice>`，保留原始析构器 |
| `AudioDeviceImpl::IsSupportAudioFormat` | `0x22be8` | 跳过位深 24 的候选；整数 16/32 以原生枚举 1/2 查询 |
| `AudioDeviceImpl::SelectAudioFormat` | `0x23314` | claim 接口、选 alternate setting、执行 UAC 采样率控制；ring 至少 512 KiB |
| `AudioDeviceImpl::Push` | `0x28428` | 布尔返回；ring 溢出仍可能返回成功，需要适配层实施背压 |
| `AudioDeviceImpl::Start` | `0x28a50` | 清空 ring 并启用原驱动启动淡入；不能当作严格逐位输出 |
| `AudioDeviceImpl::GetXferStatus` | `0x27518` | 查询传输错误；失败停止 |
| `AudioDeviceImpl::GetLeftDataTime` | `0x2898c` | 队列时长估计，毫秒精度；不是 DAC 硬件时间戳 |

`AudioFormat` 的 ABI 为 12 字节：channel/bit 各一字节、两字节保留、32 位采样率、
32 位采样格式。arm64 按两个通用寄存器传递；代码有大小及字段偏移静态断言。
通过导出函数适配，不修改二进制、不使用对象私有字段偏移。

## 播放与格式规则

1. 在设置中选择 USB Host 设备；使用显式、不可导出的 receiver 请求系统 USB 授权。
2. 以授权设备的 FD 初始化原引擎，FD 单独 dup，完整生命周期保留 Java connection。
3. 查询格式，必须找到原引擎报告支持的同采样率、同声道格式，协商失败停止。
4. 先 Start 再 Pause 初始化清空的队列；写入后播放只 Resume，避免 Start 丢掉预缓冲。
5. 原生单生产者、每次最多 8192 字节，队列达 100 ms 时不消费输入。适配限制为
   8–192 kHz、1–2 声道、16/32 位线性整数，队列上界低于原引擎保证的最小容量。
6. 24-bit packed 明确无损左对齐扩展为 signed 32-bit：`b0 b1 b2` → `00 b0 b1 b2`，
   不经 float、不重采样。设备必须提供同采样率、同声道、4 字节子槽且至少 24 位有效数据的配置。
   同时接受 24 valid bits / 4 bytes 和 32 valid bits / 4 bytes，不能强制要求 32 有效位。
   诊断分别记录解码格式、请求格式与转换，绝不将两种容器当成同一格式。
7. 应用音效、变速、静音跳过、过渡关闭。原驱动自身的淡入、音量与尾音处理仍存在；
   此模式**不宣称 Bit-perfect**。用户静音或失去音频焦点时暂停输出。
8. 进度使用原驱动队列与在途时长估计。零队列估计额外稳定 20 ms 才表示排空，
   避免毫秒取整让末尾不足一毫秒的 USB 包被过早关闭。
9. 拔出、权限撤销、写入失败及持续有待输出数据却不前进时，停止并释放。
   切歌格式变化或 flush 时销毁并重建；没有 AudioTrack 回退。

原库报告的格式与控制请求成功并不等于 DAC 实际时钟或逐位传输已验证。
错误不归咎为硬件不支持；它可能来自当前原驱动版本或设备配置。

## 192 kHz / 24-bit 格式选择修正

用户日志显示 USB 授权成功、解码器实际输出 192000 Hz / packed24，但快捷格式检查拒绝请求的 32-bit 配置。
进一步核对 `FormatType`、`GetStreamingInfo` 和完整网易云播放器：

- `GetStreamingInfo` 分别返回 `validBits` 与 `subslotBytes`；快捷 `IsSupportAudioFormat` 无条件跳过有效位数 24。
- 网易云完整播放器在快捷检查失败后调用 `GetSuggestAudioFormat`；该建议允许改变位深、声道或采样率。
  因而“网易云能播放”本身不能证明它正在使用当前音源的采样率和精度。
- 当前适配按 libusb active configuration 的原始接口顺序调用公开 `getStreamingInterface`、`IsOutput`、
  `GetInfos`，复现引擎的候选索引顺序。使用已核对的公开返回值 ABI，不读取设备对象私有内存。
- 独立校验整数 PCM、采样率实际列表成员、声道、容器大小及有效位数。32-bit 音源不允许选 24 有效位；
  packed24 则可以使用 24 有效位的 4 字节容器。仅当每一项通过后，才交给原引擎协商指定候选。
- 所有原生候选经 JNI 诊断文本写入统一 `CurrentMusicAudio`，包括接口、alternate、有效位数、子槽、PCM 类型和采样率。
  原驱动的降级建议仅记录为 `applied=false`，不自动采用。

当前日志没有候选列表，尚不能确认用户的设备是否提供 24 valid bits / 4 bytes；新诊断用于进一步确定。

该修正通过：1001 项 JVM 测试、14 项 RMX5060 隔离设备测试（包括生产原生选择函数的容器、
有效位数、采样率、声道及 PCM 类型用例），Debug APK / AndroidTest APK 构建和 Lint。
这些检查不包含真实 DAC 的配置读取、协商或播放，不能据此宣布用户设备已修复。

## 验证范围

### 启动后 USB_TRANSFER 误判修正

上一版在 `stream()` 中以 `GetXferStatus()!=0` 判定失败，这与原引擎的状态枚举不符：

| 值 | 原生含义 | 证据 |
| --- | --- | --- |
| 0 | 空闲／线程停止 | 构造函数及 stopStreamingThread 清零 |
| 1 | 线程运行 | startStreamingThread 在 `0x2661c` 显式写入 1 |
| 2 | 全部传输超时 | handleTransferCompleteCallback 在 `0x27a10` 写入 2 |
| 3 | 传输错误 | 同函数在 `0x27a3c` 写入 3 |

完整网易云播放器也只在状态 2/3 时分派超时/传输错误通知。当前适配保留 0/1 为正常状态，
2/3、设备丢失及未知状态继续停止并释放。新增回归检查直接调用生产使用的原生状态判定。
这不是 libusb 单个 transfer 的状态枚举，也不是 Start/Resume 的成功返回值，两者不能混用。

新日志包含 `adapter=ncm-usb-v3-status-init`、`transfer stage=... raw=... state=... detached=...`；
诊断队列逐次取走，覆盖启动、暂停恢复、写入及进度检查，不再以笼统 USB_TRANSFER 隐藏状态值。
用户最新日志没有 precision / adapter / supported 信息，应核对所安装的 APK，不能仅凭时间戳认定为最新版。
v3 状态修正验证：1001 项 JVM 测试、16 项 RMX5060 隔离设备测试及 Lint 通过。
其中新增运行态 1、超时 2、错误 3、断连与未知状态，以及诊断版本和队列消费回归检查。
尚未进行真实 DAC 播放验证。

JVM 测试使用可注入 driver 和连接替身验证格式、背压、逐字节保真、授权失败、
断连、原生错误、暂停恢复、重建及资源释放。设备测试只校验真实库装载、
导出接口及无 DAC 错误，不申请 USB 权限、不播放、不读取账号或播放队列。
本机没有 TANCHJIM BUNNY DSP / NICEHCK NK1 MAX，仍需设备用户验证：授权、
首次播放、192 kHz/24-bit 扩展输出、暂停恢复、切歌、尾音及热拔出。

本次验证：完整 Debug APK / AndroidTest APK 构建通过，1001 项 JVM 测试通过；
Lint 为 0 errors、115 warnings、7 hints。RMX5060（API 36、4 KiB 内存页）隔离包中，
4 项 USB 驱动测试和 9 项既有 Bit-perfect 测试共 13 项通过。
其中实际执行了原库摘要校验、加载及导出接口检查；未连接 DAC、未进行 USB 播放。
原库 ELF 的 LOAD alignment 为 4 KiB，16 KiB 内存页系统兼容性仍需验证，
不能通过更改 ELF 标记或忽略装载错误来伪称兼容。
Release 安装包与签名结果另见本次交付记录。模拟测试不视为硬件通过。
