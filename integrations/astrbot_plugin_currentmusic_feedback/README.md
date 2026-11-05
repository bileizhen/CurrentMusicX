# CurrentMusicX 群反馈（AstrBot 插件）

在指定 QQ 群识别 CurrentMusicX 的文字 Bug 反馈，按仓库 Bug 模板生成草稿，**@当前群所用 AstrBot 配置文件中的“管理员 ID”，管理员明确确认后才创建 Issue**。

目标仓库固定为 [`bileizhen/CurrentMusicX`](https://github.com/bileizhen/CurrentMusicX/issues)。插件按 AstrBot 4.26.7 接口开发，支持 OneBot v11（`aiocqhttp`，例如 NapCat）。

## 安装

1. 将此目录复制到 `AstrBot/data/plugins/astrbot_plugin_currentmusic_feedback/`，确认 `main.py`、`metadata.yaml` 在该目录下。
2. 安装本目录 `requirements.txt` 的依赖，或在 AstrBot 插件页通过插件 ZIP 安装，让 AstrBot 安装依赖。重启或重载插件。
3. 打开插件配置，填写 `allowed_groups`（每项一个群号）。默认空列表，**不会监听任何群**。
4. 沿用 AstrBot「配置文件 → 平台配置 → 管理员 ID」。读取当前群对应的配置文件，不写死 QQ 号，也不新增重复的管理员配置。配置多个管理员时会逐一 @，其中任何一人均可确认。管理员列表为空时不分析、不生成草稿。
5. 填入 `github_token`，或向 AstrBot 进程注入 `CURRENTMUSIC_GITHUB_TOKEN` 环境变量。建议使用仅授权 CurrentMusicX、具有 **Issues: Read and write** 权限的 fine-grained PAT。
6. 配置可用聊天模型。`provider_id` 留空跟随当前群的聊天模型，也可指定专门用于识别的模型 ID。

仓库同时包含 Android 项目；**不要把整个 CurrentMusicX 仓库当作插件 URL 安装或在线更新**，需使用单独打包的插件 ZIP 或复制这个子目录。升级前备份插件数据，停止旧实例后再替换文件。

如 AstrBot 自身开启了会话白名单，也需要允许目标群，插件不会绕过 AstrBot 的访问控制。群内消息无需 @机器人即可被插件分析。启用“隔离会话”后，群友生成的草稿仍可被同群管理员处理。

## 使用

群友发送具体异常，例如“CurrentMusicX 1.3.3 在 Android 15 上点播放立即闪退，重新打开也一样”，插件会调用配置的模型判断是否为 Bug。缺失内容保留“待补充”，不会臆造设备、步骤或根因。群内收到完整草稿、查重链接和确认编号后，管理员操作：

| 命令 | 行为 |
| --- | --- |
| `/bug帮助` | 查看指令 |
| `/bug列表` | 查看当前群最近 20 条待处理/结果不明草稿 |
| `/bug查看 编号` | 查看状态、完整草稿和查重链接 |
| `/bug修改 编号 version 1.3.3` | 修改字段，生成新编号，旧编号立即失效 |
| `/bug确认 编号` | 提交当前展示版本，返回 Issue 链接 |
| `/bug取消 编号` | 拒绝待确认草稿，不提交 |
| `/bug核对 编号` | 查询结果不明的请求是否已在 GitHub 创建 Issue |

修改字段：`title`、`version`、`device`、`build`、`module`、`steps`、`result`、`diagnostics`。正文每个字段最多 350 字，标题最多 100 字；超长内容截取后会在草稿中重新展示。确认前请补充必要信息、检查公开内容并打开查重链接检查已有问题。

只接受当前配置文件内管理员在原群发送的明确指令。群管理头衔、引用消息中的“确认”、其他群或私聊、模型输出均不能代替确认。修改会使旧编号失效；默认 24 小时过期，超时不会发布。新消息不会悄悄改动已展示的草稿。

## 行为与限制

- 每条不超过 4000 字的普通文字消息独立识别，置信度默认至少 0.85；闲聊和建议由模型过滤。不会保证识别率，不会合并多条零散反馈，也不做图片 OCR、语音识别或截图上传。仅有图片时，请群友补充文字。
- 每个允许群的文字会发给配置的模型；无群聊历史、工具调用、私人聊天或原始附件。基本脱敏在发模型前和生成草稿后执行，处理常见凭据、链接、邮箱、长数字与 @。脱敏可能误伤长数字版本号，也不能替代人工审阅。
- 同一事件重复投递、24 小时内完全相同的结构化草稿会被去重。语义相近但措辞不同的反馈仍可能分别生成草稿；不宣称完成全仓库语义查重。
- 默认每群每 24 小时最多 20 条草稿，最多 2 个并发模型请求、16 条处理中消息。超长消息、超限消息或模型失败不会发布，诊断信息写入插件日志，不打印消息正文、Token 或服务响应。
- 草稿与状态存入 `data/plugin_data/astrbot_plugin_currentmusic_feedback/drafts.sqlite3`，重启保留。此文件含群范围和脱敏后的草稿，需按本地数据备份管理；目前不自动删除历史记录。
- 明确的 GitHub 拒绝会退回待确认；超时、断线、服务端异常或取消请求可能已经创建 Issue，因此保持锁定，只允许核对，**不会自动重试 POST**。进程崩溃遗留的 `submitting` 同样可核对；未查到时继续锁定，管理员应人工检查仓库。不要通过删除数据库解除锁定并重发。
- `secret` 只遮罩 Token 在面板中的显示，AstrBot 配置仍可能明文保存；可改用环境变量。

## 开发验证

在仓库根目录执行：

```sh
python -m pip install -r integrations/astrbot_plugin_currentmusic_feedback/requirements.txt
python -m unittest discover -s integrations/astrbot_plugin_currentmusic_feedback/tests -v
python integrations/astrbot_plugin_currentmusic_feedback/package.py dist/astrbot_plugin_currentmusic_feedback-v0.1.0.zip
```

测试包含纯逻辑、模拟 AstrBot 事件及模拟 GitHub HTTP 响应；不创建真实 Issue，不等同于 QQ 群/AstrBot 部署实测。安装后先用指定群、非白名单群、非管理员三种身份检查草稿和权限，再用真实反馈完成一次管理员确认验收。

PR 的 `AstrBot feedback tests` 工作流通过后，会提供 `astrbot-currentmusic-feedback` 安装包 artifact。下载并解开 artifact 外层 ZIP 后，使用里面的插件 ZIP 安装。插件随仓库采用 GPL-3.0 许可，安装包附带 LICENSE。

接口参考：[AstrBot 消息事件](https://docs.astrbot.app/dev/star/guides/listen-message-event.html)、[模型调用](https://docs.astrbot.app/dev/star/guides/ai.html)、[插件配置](https://docs.astrbot.app/dev/star/guides/plugin-config.html)、[GitHub Issues API](https://docs.github.com/en/rest/issues/issues#create-an-issue)。
