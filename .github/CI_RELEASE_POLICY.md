# CurrentMusicX CI Release 保留规则

## 目标

**GitHub Releases 中始终只保留最新一个完整发布的 CurrentMusic CI 版本**。正式版（如 `v1.3.0`、`v1.3.1`、`v1.3.2`）必须永久排除在自动清理范围之外。

## CI 版发布时机

PR 合并前并行运行源码与协作检查。Android Release 构建和 Lint 集中在主线进行。只要还有目标为 `main` 的开放 PR，仓库就推迟构建和发布 CI 版，草稿和带 `bot: skip` 标签的 PR 也计入。中途合并只触发轻量的 **Check pending CI release** 检查。

最后一个 PR 合并、关闭或改投其他分支后，检查最新 `main` 和上一个完整 CI 版之间的累计改动。有应用改动时构建并发布一个 CI 版。若只改了文档或 CI，则跳过打包。签名发布流程会再次核对 PR 和主线状态。

Android CI 成功构建并上传 APK 后，通过 `workflow_dispatch` 显式启动 **Publish CI build**，传入构建的 `run_id` 和 `run_attempt`，避免依赖机器人调度后可能不继续触发的 `workflow_run` 事件。发布端从 GitHub API 核验来源、运行次数、完成状态及 APK 构建/上传步骤；通知作业尚未结束时最多等待两分钟，超时则失败。签名仍只在 `ci-signing` 环境的独立作业内执行。

若需要补发已有构建，可在 `main` 上手动运行 **Publish CI build** 并填写原 Android CI 的运行 ID 和运行次数。补发也必须满足无开放 PR、构建提交仍为最新主线、存在累计应用改动等发布条件，且不能替换已发布的同名版本。

## 判断规则

- 仅修改 GitHub 工作流或 CI 脚本时，由协作检查运行脚本语法检查和工作流校验，自动构建跳过 Android 工具链、APK 和预发布。队列清空后，可手动启动 Android CI 验证工具链；是否发布仍取决于累计改动。
- CI 版和正式版的自动更新说明都排除仅修改 CI 的 PR。若同一 PR 还修改应用代码，保留该 PR 的更新条目。

- 仅匹配 `vX.Y.Z-ci.N` 格式的 tag、`CurrentMusic CI #N` 名称、`prerelease=true` 且 `draft=false` 的完整 CI Release。
- 必须具有 `CurrentMusic-Android-vX.Y.Z-ci.N.apk` 和 `SHA256SUMS.txt` 两个附件；不符合标准的 Release、草稿及其他预发布版本不自动删除。
- 跨版本比较 CI 构建编号 `N`，保留编号最大的一个。**不依赖 GitHub Releases 列表的展示顺序**。
- 若检测到重复构建编号、列表异常或权限错误，停止或报告失败，不进行不确定的删除。
- 清理旧 CI Release 后删除对应 `vX.Y.Z-ci.N` Git tag；如果 Release 删除失败，不删除其 tag。
- 发布作业成功后显式调度清理；兼容保留的 `workflow_run` 入口同时要求发布工作流及该次运行的 `publish` 作业成功。发布作业跳过时不调度清理。
- 规则首次合并到 `main` 时自动运行一次，清理既有旧 CI Release；也可以从 Actions 手动执行。

## 安全边界

- `.github/workflows/ci-prune.yml` 只检出 `main` 可信脚本，不检出、执行 Fork PR 的文件。
- 清理任务使用 `actions: read` 核验发布作业，使用 `contents: write` 管理 CI Release 和 tag。
- **不会删除正式版 Release 或正式版 tag**，也不会自动删除草稿、其他项目的预发布或未完整发布的 CI 构建。
- 当新 CI 尚未成功公开发布时，旧的最新 CI 版本仍然保留，保证应用更新入口可用。
