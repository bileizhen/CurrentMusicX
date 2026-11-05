import asyncio
import hashlib
import json
import os
import time
from pathlib import Path
from urllib.parse import urlencode

from astrbot.api import AstrBotConfig, logger
from astrbot.api.event import AstrMessageEvent, filter
from astrbot.api.message_components import At, Plain
from astrbot.api.star import Context, Star, register
from astrbot.core.utils.astrbot_path import get_astrbot_data_path

from .core import REPOSITORY, SYSTEM_PROMPT, Store, clean, parse_report, render
from .github import GitHub, Rejected

HELP = """Bug 反馈管理（仅当前配置文件里的 AstrBot 管理员可用）：
/bug列表
/bug查看 编号
/bug确认 编号
/bug取消 编号
/bug修改 编号 字段 内容
/bug核对 编号
可修改字段：title、version、device、build、module、steps、result、diagnostics。
修改后会生成新确认编号；提交结果不明时只能核对，禁止自动重发。"""
COMMANDS = {"bug帮助", "bug列表", "bug查看", "bug确认", "bug取消", "bug修改", "bug核对"}


@register("astrbot_plugin_currentmusic_feedback", "bileizhen", "群聊 Bug 识别，@管理员确认后提交 CurrentMusicX Issue", "0.1.0")
class CurrentMusicFeedback(Star):
    def __init__(self, context: Context, config: AstrBotConfig):
        super().__init__(context)
        self.config = config
        self.store = Store(Path(get_astrbot_data_path()) / "plugin_data" / "astrbot_plugin_currentmusic_feedback" / "drafts.sqlite3")
        self.semaphore = asyncio.Semaphore(2)
        self.active = 0
        self.tasks = set()
        self.closed = False

    def scope(self, event):
        # Group scope deliberately ignores per-member session isolation.
        return json.dumps([str(event.get_platform_id()), str(event.get_self_id()), str(event.get_group_id())])

    def allowed(self, event):
        groups = {str(x).strip() for x in self.config.get("allowed_groups", [])}
        platforms = {str(x).strip() for x in self.config.get("platform_ids", [])}
        return (self.config.get("enabled", True) and event.get_platform_name() == "aiocqhttp"
                and bool(event.get_group_id()) and str(event.get_group_id()) in groups
                and (not platforms or str(event.get_platform_id()) in platforms)
                and str(event.get_sender_id()) != str(event.get_self_id()))

    def admins(self, event):
        # The built-in UI field is admins_id, resolved using this group's profile.
        values = self.context.get_config(event.unified_msg_origin).get("admins_id", [])
        return sorted({str(x).strip() for x in values if str(x).strip().isdigit() and int(str(x).strip()) > 0})

    async def say(self, event, text):
        await event.send(event.plain_result(text))

    async def preview(self, event, draft):
        title, body = render(json.loads(draft["report"]))
        identifier = draft["id"]
        search_url = "https://github.com/" + REPOSITORY + "/issues?" + urlencode({"q": "is:issue " + title[6:]})
        text = (f"\n发现疑似 Bug，请核查以下将公开提交的草稿。\n目标：{REPOSITORY}\n"
                f"编号：{identifier}\n\n{title}\n\n{body}\n\n查重：{search_url}\n"
                f"确认：/bug确认 {identifier}\n取消：/bug取消 {identifier}\n"
                f"修改：/bug修改 {identifier} 字段 内容\n"
                "缺失信息可先补充；不会自动提交。脱敏仅为辅助，请检查内容。")
        await event.send(event.chain_result([*[At(qq=admin) for admin in self.admins(event)], Plain(text)]))

    @filter.event_message_type(filter.EventMessageType.GROUP_MESSAGE)
    @filter.platform_adapter_type(filter.PlatformAdapterType.AIOCQHTTP)
    async def on_group(self, event: AstrMessageEvent):
        if self.closed:
            return
        task = asyncio.current_task()
        self.tasks.add(task)
        try:
            await self.handle_group(event)
        finally:
            self.tasks.discard(task)

    async def handle_group(self, event):
        if self.closed or not self.allowed(event):
            return
        # Read actual text segments only: a quoted command cannot approve a draft.
        text = " ".join(part.text for part in event.get_messages() if isinstance(part, Plain)).strip()
        command_text = text.lstrip("/").strip()
        command = command_text.split(maxsplit=1)[0].lower() if command_text else ""
        if command in COMMANDS:
            event.stop_event()
            if str(event.get_sender_id()) not in self.admins(event):
                await self.say(event, "只有当前配置文件中的 AstrBot 管理员可以操作反馈。")
                return
            await self.command(event, command_text)
            return
        if not text or text.startswith("/") or not self.admins(event):
            return
        if len(text) > 4000:
            logger.warning("CurrentMusic feedback: ignored a message exceeding 4000 characters")
            return
        # Bound both queued and active LLM work. No raw messages or credentials in logs.
        if self.active >= 16:
            logger.warning("CurrentMusic feedback: analysis queue is full")
            return
        self.active += 1
        try:
            async with self.semaphore:
                provider = str(self.config.get("provider_id", "")).strip()
                if not provider:
                    provider = await self.context.get_current_chat_provider_id(umo=event.unified_msg_origin)
                response = await asyncio.wait_for(self.context.llm_generate(
                    chat_provider_id=provider, system_prompt=SYSTEM_PROMPT,
                    prompt=json.dumps({"message": clean(text)}, ensure_ascii=False),
                    contexts=[], tools=None,
                ), timeout=45)
                report = parse_report(response.completion_text, max(0.5, min(1.0, float(self.config.get("confidence_threshold", 0.85)))))
                if not report or self.closed or not self.allowed(event) or not self.admins(event):
                    return
                message_id = str(getattr(event.message_obj, "message_id", ""))
                if not message_id:
                    logger.warning("CurrentMusic feedback: ignored event without message ID")
                    return
                source = hashlib.sha256((self.scope(event) + ":" + message_id).encode()).hexdigest()
                draft = self.store.add(self.scope(event), source, report,
                                       max(1, min(168, int(self.config.get("approval_ttl_hours", 24)))),
                                       max(1, min(100, int(self.config.get("daily_draft_limit", 20)))))
                if draft:
                    await self.preview(event, draft)
        except Exception as exc:
            logger.warning("CurrentMusic feedback: analysis/notification failed (%s); drafts remain available via bug列表", type(exc).__name__)
        finally:
            self.active -= 1

    async def command(self, event, text):
        args = text.split(maxsplit=3)
        command = args[0].lower()
        scope = self.scope(event)
        if command == "bug帮助":
            await self.say(event, HELP)
            return
        if command == "bug列表":
            drafts = self.store.pending(scope)
            lines = [f"{r['id']} · {'expired' if r['state'] == 'pending' and r['expires'] <= time.time() else r['state']} · {json.loads(r['report'])['title']}" for r in drafts]
            await self.say(event, "\n".join(lines) or "当前群没有待处理反馈。")
            return
        if len(args) < 2:
            await self.say(event, HELP)
            return
        draft_id = args[1].lower()
        draft = self.store.get(draft_id, scope)
        if not draft:
            await self.say(event, "当前群找不到这个反馈编号。")
            return
        if command == "bug查看":
            await self.say(event, f"状态：{draft['state']}" + (f"\n{draft['url']}" if draft['url'] else ""))
            await self.preview(event, draft)
            return
        if command == "bug修改":
            if len(args) != 4:
                await self.say(event, HELP)
                return
            try:
                edited = self.store.edit(draft_id, scope, args[2], args[3])
            except ValueError:
                edited = None
            if edited:
                await self.preview(event, edited)
            else:
                await self.say(event, "修改失败：请检查字段名、内容，以及草稿是否仍待确认且未过期。")
            return
        if len(args) != 2:
            await self.say(event, "请单独发送命令和编号，不要附加其他内容。")
            return
        if command == "bug取消":
            changed = self.store.transition(draft_id, scope, "pending", "rejected")
            await self.say(event, "已取消，不会提交。" if changed else "该草稿已不在待确认状态；已发出的请求不能通过取消撤回。")
            return
        if draft["state"] == "submitted":
            await self.say(event, "已经提交：" + draft["url"])
            return
        try:
            github = GitHub(str(self.config.get("github_token", "")) or os.environ.get("CURRENTMUSIC_GITHUB_TOKEN", ""))
        except ValueError as exc:
            await self.say(event, str(exc))
            return
        if command == "bug核对":
            if draft["state"] not in ("submitting", "unknown"):
                await self.say(event, "仅提交中或结果不明的反馈需要核对。")
                return
            try:
                url = await github.reconcile(draft)
                if url:
                    self.store.transition(draft_id, scope, draft["state"], "submitted", url=url)
                    await self.say(event, "已找到提交：" + url)
                else:
                    await self.say(event, "暂未找到对应 Issue。仍保持锁定，稍后再次核对或人工检查仓库，避免重复提交。")
            except Exception as exc:
                logger.warning("CurrentMusic feedback: reconciliation failed (%s)", type(exc).__name__)
                await self.say(event, "核对失败，请稍后重试；不会自动重新提交。")
            return
        if command != "bug确认":
            return
        if not self.store.transition(draft_id, scope, "pending", "submitting", unexpired=True):
            await self.say(event, "无法提交：草稿已处理、已过期或提交结果待核对。")
            return
        try:
            url = await github.create(draft_id, json.loads(draft["report"]))
        except Rejected as exc:
            self.store.transition(draft_id, scope, "submitting", "pending")
            await self.say(event, str(exc) + "。处理后需重新确认。")
        except BaseException as exc:
            # Cancellation/crash may happen after GitHub accepted the POST.
            self.store.transition(draft_id, scope, "submitting", "unknown")
            if isinstance(exc, (asyncio.CancelledError, KeyboardInterrupt, SystemExit)):
                raise
            logger.warning("CurrentMusic feedback: submit outcome uncertain (%s)", type(exc).__name__)
            await self.say(event, f"提交结果不确定，已禁止重发。请使用 /bug核对 {draft_id}")
        else:
            self.store.transition(draft_id, scope, "submitting", "submitted", url=url)
            await self.say(event, "已提交：" + url)

    async def terminate(self):
        self.closed = True
        for task in list(self.tasks):
            task.cancel()
        if self.tasks:
            await asyncio.gather(*list(self.tasks), return_exceptions=True)
        self.store.close()
