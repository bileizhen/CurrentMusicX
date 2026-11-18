# 安全政策 / Security Policy

感谢你帮助保护 **流声音乐（CurrentMusicX）** 及其用户。本政策适用于本仓库中的 Android 客户端代码与由本仓库发布的安装包。

## 支持的版本

| 版本 | 安全维护 |
| --- | --- |
| [最新正式版](https://github.com/bileizhen/CurrentMusicX/releases/latest) | ✅ 优先接收、评估和修复安全问题 |
| `main` 分支及 CI 测试版 | 🧪 接收安全反馈，但不保证测试构建的稳定性或独立补丁 |
| 早期正式版及其他非当前版本 | ⚠️ 通常不单独提供安全更新，请优先升级 |

本表说明维护重点，不构成固定响应或补丁时限承诺。

## 私下报告安全漏洞

**请勿在公开 Issue、Pull Request、Discussion 或评论中发布尚未修复的漏洞细节、可直接复用的攻击步骤、真实用户数据或任何凭据。**

请访问本仓库的 [Security Advisories（安全公告）](https://github.com/bileizhen/CurrentMusicX/security/advisories)，点击 **Report a vulnerability（报告漏洞）**，通过 GitHub 的私密漏洞报告功能提交信息（该入口需要仓库管理员先启用私密漏洞报告）。

如果暂时看不到私密报告入口，请不要改为公开披露漏洞细节。等待私密报告入口启用后再提交。

为便于排查，请尽量提供：

1. 问题概要、可能影响，以及受影响的应用版本或提交号。
2. 可复现的操作步骤、必要的测试环境与预期/实际结果。
3. 已**脱敏**的日志、截图或最小复现代码；不要附带真实账号 Token、Cookie、密码、API Key 等敏感信息。
4. 已采取的缓解措施（如有）。

## 处理方式

- 维护者将评估报告是否涉及本项目，以及问题的影响范围和可复现性。
- 如需补充信息，将优先在同一私密报告中沟通。修复方案可能通过受控的安全公告、补丁或后续正式版本发布。
- 在完成风险评估、必要修复和协调披露前，请尽量保持漏洞细节私密。感谢负责任的披露。
- 由于项目为社区维护，**不保证固定回复时间或修复时限**。

## 报告范围与注意事项

本仓库重点处理 Android 客户端中的鉴权和凭据保护、网络通信、文件与下载处理、组件暴露、WebView 或脚本执行、隐私泄露等安全问题。

如果漏洞仅存在于独立部署的 **CurrentMusic 服务端** 或其他第三方服务，请优先向对应项目维护者私下报告；若它同时影响 CurrentMusicX 客户端，请在本仓库私密报告中说明关联性。

请在合法、获得授权的环境中验证问题，避免扫描或访问他人的真实账号、数据与服务，也不要通过破坏性测试影响其他用户。

---

**English summary:** Report potential security vulnerabilities privately using [GitHub Security Advisories](https://github.com/bileizhen/CurrentMusicX/security/advisories) → **Report a vulnerability** (available after private vulnerability reporting is enabled). Please do not disclose unpatched vulnerabilities or credentials in public issues. Include affected versions, reproducible steps, potential impact, and redacted evidence. The latest stable release is prioritized for security maintenance; no fixed response or remediation time is guaranteed.
