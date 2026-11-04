# CurrentMusicX CI Release 保留规则

## 目标

**GitHub Releases 中始终只保留最新一个完整发布的 CurrentMusic CI 版本**。正式版（如 `v1.3.0`、`v1.3.1`、`v1.3.2`）必须永久排除在自动清理范围之外。

## 判断规则

- 仅修改 GitHub 工作流或 CI 脚本时，由协作检查运行脚本测试和工作流校验，自动构建跳过 Android 工具链、APK 和预发布。需要验证工具链时可在 Actions 手动启动 Android CI。
- CI 版和正式版的自动更新说明都排除仅修改 CI 的 PR。若同一 PR 还修改应用代码，保留该 PR 的更新条目。

- 仅匹配 `vX.Y.Z-ci.N` 格式的 tag、`CurrentMusic CI #N` 名称、`prerelease=true` 且 `draft=false` 的完整 CI Release。
- 必须具有 `CurrentMusic-Android-vX.Y.Z-ci.N.apk` 和 `SHA256SUMS.txt` 两个附件；不符合标准的 Release、草稿及其他预发布版本不自动删除。
- 跨版本比较 CI 构建编号 `N`，保留编号最大的一个。**不依赖 GitHub Releases 列表的展示顺序**。
- 若检测到重复构建编号、列表异常或权限错误，停止或报告失败，不进行不确定的删除。
- 清理旧 CI Release 后删除对应 `vX.Y.Z-ci.N` Git tag；如果 Release 删除失败，不删除其 tag。
- 自动清理要求 `Publish CI build` 工作流及该次运行的 `publish` 作业均已成功。发布作业跳过时保留现有 Release。
- 规则首次合并到 `main` 时自动运行一次，清理既有旧 CI Release；也可以从 Actions 手动执行。

## 安全边界

- `.github/workflows/ci-prune.yml` 只检出 `main` 可信脚本，不检出、执行 Fork PR 的文件。
- 清理任务使用 `actions: read` 核验发布作业，使用 `contents: write` 管理 CI Release 和 tag。
- **不会删除正式版 Release 或正式版 tag**，也不会自动删除草稿、其他项目的预发布或未完整发布的 CI 构建。
- 当新 CI 尚未成功公开发布时，旧的最新 CI 版本仍然保留，保证应用更新入口可用。
