# CurrentMusicX CI Release 保留规则

## 目标

**GitHub Releases 中始终只保留最新一个完整发布的 CurrentMusic CI 版本**。正式版（如 `v1.3.0`、`v1.3.1`、`v1.3.2`）必须永久排除在自动清理范围之外。

## CI 版发布时机

PR 合并前运行源码与协作检查；非草稿、涉及应用改动的 PR 将在 `build` 中执行 `:app:compileReleaseJavaWithJavac`（含 Kotlin/KSP），同时在独立 `lint` 作业运行完整的 `:app:lintRelease`。`build` 状态名称不变以兼容现有分支保护；`lint` 只有被仓库规则明确设为必需状态时才能阻止合并，若未设为必需则合并可能早于 Lint 完成。主线 Android CI 只生成包含 R8/资源压缩的 Release APK，不重复运行完整 Lint。CI 预发布版本（`ciBuildNumber > 0`）也跳过 `assembleRelease` 隐式触发的 `lintVitalRelease`；普通正式版/本地 Release 构建（`ciBuildNumber = 0`）仍保留致命问题检查。若绕过 PR 直接向主线提交应用代码，必须另行完成 Android Lint 检查。只要还有目标为 `main` 的**阻塞型开放 PR**，仓库就推迟构建和发布 CI 版；普通 PR（包括草稿和带 `bot: skip` 标签的 PR）仍然计入。只有由官方 Dependabot 创建、位于本仓库且更改仅限依赖声明或 Actions 版本的 PR 才可以被忽略。中途合并只触发轻量的 **Check pending CI release** 检查。

最后一个 PR 合并、关闭或改投其他分支后，检查最新 `main` 和上一个完整 CI 版之间的累计改动。有应用改动时构建并发布一个 CI 版。若只改了文档或 CI，则跳过打包。签名发布流程会再次核对 PR 和主线状态。

Android CI 成功构建并上传 APK 后，通过 `workflow_dispatch` 显式启动 **Publish CI build**，传入构建的 `run_id` 和 `run_attempt`，避免依赖机器人调度后可能不继续触发的 `workflow_run` 事件。发布端从 GitHub API 核验来源、运行次数、完成状态及 APK 构建/上传步骤；通知作业尚未结束时最多等待两分钟，超时则失败。签名仍只在 `ci-signing` 环境的独立作业内执行。

若需要补发已有构建，可在 `main` 上手动运行 **Publish CI build** 并填写原 Android CI 的运行 ID 和运行次数。补发也必须满足无阻塞型开放 PR、构建提交仍为最新主线、存在累计应用改动等发布条件，且不能替换已发布的同名版本。

## Dependabot PR 的队列豁免

- 仅接受 GitHub 官方 Dependabot 机器人身份（`dependabot[bot]`、官方账户 ID），PR 分支必须来自**本仓库**，前缀仅为 `dependabot/gradle/` 或 `dependabot/github_actions/`。
- 逐页读取所有目标为 `main` 的开放 PR。只有更改文件全部为预期 Gradle 依赖声明/锁文件/Wrapper 属性，或 GitHub Actions 工作流 YAML，并且每个文件均为已有文件的修改时，才不阻塞 CI 发布。混入任意应用源码、非预期文件、增删文件、过多变更，或查询失败都不会被静默豁免。
- 豁免只影响**已合入 main 的 CI 版是否可构建及发布**，不会自动合并 Dependabot PR，也不会把尚未合并的依赖变更包含进 APK。
- `releaseDecision` 同时用于主线构建判定、发布门禁和签名附件最终公开前的复核。每个普通 PR 保留原先的阻塞行为。
- GitHub API 请求错误会使判定失败并阻止发布，避免在无法确认 PR 安全性时继续。

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
