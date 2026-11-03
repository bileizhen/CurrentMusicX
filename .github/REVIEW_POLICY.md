# CurrentMusicX PR 审查规范

## 目标

优先复用仓库已有的 Codex 代码审查能力，不新增付费 AI API。Codex 审查意见应跟随 PR 提交者描述正文的主要自然语言：简体中文 PR 使用简体中文、繁体中文 PR 使用繁体中文、英文 PR 使用英文；描述缺失或语言不明确时参考标题，仍不明确则默认简体中文。保留技术术语、文件路径、代码标识符和 P0–P3 等标记。具体代理指引见仓库根目录 `AGENTS.md`。此规则属于对 Codex 的指引，能否被 GitHub 上的 Codex Review 自动遵循取决于该集成是否读取仓库指引。

## 自动化边界

- `pr-risk.yml` 根据变更文件路径分级，不能代替完整代码审查。
- `pr-review-summary.yml` 只读取 GitHub 上已有的行级审查讨论并输出 Actions Summary，不调用 AI，不声称 Codex 已审查当前提交。
- 审查摘要不是合并门槛；现有分支保护、CI 和维护者决定仍然有效。
- 未解决讨论、敏感文件、权限、签名、音频路径变更建议人工复核。
- 不自动解决审查讨论、不自动批准、不自动合并、不将密钥提供给来自 fork 的 PR。
- AI 审查是否自动触发及其额度取决于 Codex/GitHub 连接设置，应在 GitHub 中单独确认。

## 人工复核清单

1. 音频：采样率、缓冲、播放生命周期、Bit-perfect 和后台播放。
2. Android：协程取消、Compose 重组、生命周期、权限。
3. 安全：Token、下载校验、网络请求、数据迁移。
4. 发布：签名环境、构建来源、APK 摘要、版本号。
5. 验证：CI 结果、复现步骤、真机覆盖范围及未验证内容。

## Fork PR 自动请求 Codex Review

- 对外部 Fork 的非草稿 PR，`codex-review-bridge.yml` 会自动发送一次以 `@codex review` 开头的触发评论；同仓库 PR 继续使用 Codex 原生自动审查，不重复请求。
- 仅识别和删除自动化创建且带特定 SHA 标记的触发评论；收到同一提交的 Codex Review Summary（已完成）后自动删除，超出六小时无确认也会清理，但这**不代表审查成功**。
- 新工作流首次合入 `main` 时会为最多 10 个现有的待审外部 Fork PR 请求一次；之后可手动运行并填写 PR 编号，计划任务仅负责清理，不自动反复请求。
- GitHub Actions 的 `GITHUB_TOKEN` 以机器人身份发表评论，Codex 未保证接受此类触发。若实际测试没有 Codex 回应，可考虑在 Actions Secrets 配置 `CODEX_REVIEW_TOKEN`（由仓库维护者创建的细粒度 GitHub PAT，仅授权本仓库所需的 Issues: Read/Write、Pull requests: Read/Write），以维护者身份发送。请勿直接把 Token 写入 PR、仓库文件或日志。
- 整套机制不执行 Fork 代码，不共享签名凭据，不自动批准或合并，且不能修复 Codex 服务端的 Fork 审查限制。

- GitHub Actions 对 PR Conversation 评论的创建可能同时要求 `issues: write` 与 `pull-requests: write`；该工作流已明确声明二者，修复了 #26 评论接口返回的 HTTP 403。评论能成功发布不等于 Codex 一定接受机器人 @ 提及，仍需通过真实外部 PR 验证。
