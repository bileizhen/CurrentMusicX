import asyncio
import importlib
import json
from pathlib import Path
import sys
import tempfile
import time
from types import ModuleType, SimpleNamespace
import unittest
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from astrbot_plugin_currentmusic_feedback.core import FIELDS, Store, UNKNOWN, clean, marker, parse_report, render
from astrbot_plugin_currentmusic_feedback.github import GitHub, GitHubError, Rejected


def report(**changes):
    data = {"is_bug": True, "confidence": 0.96, "title": "播放时闪退", "result": "实际：闪退；预期：正常播放", "version": "1.3.3"}
    data.update(changes)
    return data


class CoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "drafts.sqlite3"
        self.store = Store(self.path)
        self.report = parse_report(json.dumps(report()), 0.85)

    def tearDown(self):
        self.store.close()
        self.temp.cleanup()

    def test_unknown_fields_and_template(self):
        self.assertEqual(self.report["device"], UNKNOWN)
        title, body = render(self.report)
        self.assertTrue(title.startswith("[Bug] "))
        for label in FIELDS.values():
            self.assertIn("### " + label, body)

    def test_invalid_model_decisions_fail_closed(self):
        for value in ["{}", "[]", "not json", json.dumps(report(is_bug="true")), json.dumps(report(confidence=float("nan"))), json.dumps(report(confidence=True)), json.dumps(report(steps=["x"]))]:
            with self.subTest(value=value), self.assertRaises((ValueError, TypeError)):
                parse_report(value, 0.85)
        self.assertIsNone(parse_report(json.dumps(report(is_bug=False)), 0.85))
        self.assertIsNone(parse_report(json.dumps(report(confidence=0.5)), 0.85))
        self.assertIsNone(parse_report(json.dumps(report(result="")), 0.85))

    def test_json_fences(self):
        self.assertEqual(parse_report("```json\n" + json.dumps(report()) + "\n```", 0.85), self.report)

    def test_redaction_and_untrusted_markup(self):
        raw = "ghp_abcdef1234\nCookie: secret\ntoken=supersecret\nx@example.com 123456789 https://x.test/?key=secret @all <img> [CQ:at,qq=all]"
        output = clean(raw)
        for secret in ["ghp_abcdef1234", "supersecret", "x@example.com", "123456789", "https://", "@all", "<img>", "[CQ:"]:
            self.assertNotIn(secret, output)
        self.assertEqual(clean(output), output)

    def test_only_one_connection_can_claim(self):
        draft = self.store.add("group-a", "event-a", self.report)
        other = Store(self.path)
        try:
            self.assertTrue(self.store.transition(draft["id"], "group-a", "pending", "submitting", unexpired=True))
            self.assertFalse(other.transition(draft["id"], "group-a", "pending", "submitting", unexpired=True))
        finally:
            other.close()

    def test_scope_expiry_and_event_dedup(self):
        draft = self.store.add("group-a", "event-a", self.report, ttl_hours=-1)
        self.assertIsNone(self.store.get(draft["id"], "group-b"))
        self.assertFalse(self.store.transition(draft["id"], "group-b", "pending", "submitting"))
        self.assertFalse(self.store.transition(draft["id"], "group-a", "pending", "submitting", unexpired=True))
        self.assertIsNone(self.store.add("group-a", "event-a", {**self.report, "title": "不同的模型输出"}))
        self.assertIsNone(self.store.add("group-a", "event-b", self.report))
        self.assertIsNotNone(self.store.add("group-b", "event-c", self.report))

    def test_edit_invalidates_old_approval_and_persists(self):
        draft = self.store.add("g", "event", self.report)
        edited = self.store.edit(draft["id"], "g", "version", "1.3.4")
        self.assertNotEqual(draft["id"], edited["id"])
        self.assertFalse(self.store.transition(draft["id"], "g", "pending", "submitting"))
        self.store.close()
        self.store = Store(self.path)
        self.assertEqual(json.loads(self.store.get(edited["id"], "g")["report"])["version"], "1.3.4")
        self.assertTrue(self.store.transition(edited["id"], "g", "pending", "submitting", unexpired=True))
        self.assertIsNone(self.store.edit(edited["id"], "g", "version", "different"))

    def test_daily_limit(self):
        self.store.add("g", "a", self.report, daily_limit=1)
        self.assertIsNone(self.store.add("g", "b", {**self.report, "title": "歌词错位"}, daily_limit=1))

    def test_crash_state_survives_restart(self):
        draft = self.store.add("g", "a", self.report)
        self.store.transition(draft["id"], "g", "pending", "submitting")
        self.store.close()
        self.store = Store(self.path)
        self.assertEqual(self.store.get(draft["id"], "g")["state"], "submitting")
        self.assertFalse(self.store.transition(draft["id"], "g", "pending", "submitting"))


class Response:
    def __init__(self, status, data):
        self.status, self.data = status, data

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass

    async def json(self):
        return self.data


class Session(Response):
    def __init__(self, responses):
        self.responses = iter(responses)
        self.calls = []

    def post(self, url, **kwargs):
        self.calls.append(("POST", url, kwargs))
        response = next(self.responses)
        if isinstance(response, BaseException):
            raise response
        return response

    def get(self, url, **kwargs):
        self.calls.append(("GET", url, kwargs))
        return next(self.responses)


class GitHubTests(unittest.IsolatedAsyncioTestCase):
    async def test_create_payload_and_canonical_url(self):
        session = Session([Response(201, {"number": 321, "html_url": "https://untrusted.example/"})])
        with patch.object(GitHub, "session", return_value=session):
            url = await GitHub("test-token").create("abc", parse_report(json.dumps(report()), 0.85))
        self.assertEqual(url, "https://github.com/bileizhen/CurrentMusicX/issues/321")
        payload = session.calls[0][2]["json"]
        self.assertIn(marker("abc"), payload["body"])
        self.assertEqual(payload["labels"], ["bug"])
        self.assertFalse(session.calls[0][2]["allow_redirects"])

    async def test_rejections_and_uncertain_outcomes_never_retry(self):
        for status, error in [(401, Rejected), (403, Rejected), (422, Rejected), (429, Rejected), (500, GitHubError), (302, GitHubError), (201, GitHubError)]:
            session = Session([Response(status, {})])
            with self.subTest(status=status), patch.object(GitHub, "session", return_value=session), self.assertRaises(error):
                await GitHub("token").create("abc", parse_report(json.dumps(report()), 0.85))
            self.assertEqual(len(session.calls), 1)

    async def test_timeout_never_retries(self):
        session = Session([TimeoutError()])
        with patch.object(GitHub, "session", return_value=session), self.assertRaises(TimeoutError):
            await GitHub("token").create("abc", parse_report(json.dumps(report()), 0.85))
        self.assertEqual(len(session.calls), 1)

    async def test_reconcile_pagination_and_exclude_prs(self):
        first_page = [{"number": i, "body": marker("abc"), "pull_request": {}} for i in range(100)]
        session = Session([Response(200, first_page), Response(200, [{"number": 999, "body": marker("abc")}])])
        with patch.object(GitHub, "session", return_value=session):
            url = await GitHub("token").reconcile({"id": "abc", "created": time.time()})
        self.assertTrue(url.endswith("/999"))
        self.assertEqual([call[2]["params"]["page"] for call in session.calls], [1, 2])


class Plain:
    def __init__(self, text):
        self.text = text


class At:
    def __init__(self, qq):
        self.qq = qq


def load_plugin_with_stubs():
    """Adapter harness only, not a claim that the real AstrBot runtime was tested."""
    modules = {name: ModuleType(name) for name in ["astrbot", "astrbot.api", "astrbot.api.event", "astrbot.api.message_components", "astrbot.api.star", "astrbot.core", "astrbot.core.utils", "astrbot.core.utils.astrbot_path"]}
    modules["astrbot.api"].AstrBotConfig = dict
    modules["astrbot.api"].logger = SimpleNamespace(warning=lambda *args: None)
    decorator = lambda *args, **kwargs: lambda fn: fn
    modules["astrbot.api.event"].filter = SimpleNamespace(event_message_type=decorator, platform_adapter_type=decorator, EventMessageType=SimpleNamespace(GROUP_MESSAGE=1), PlatformAdapterType=SimpleNamespace(AIOCQHTTP=1))
    modules["astrbot.api.event"].AstrMessageEvent = object
    modules["astrbot.api.message_components"].At = At
    modules["astrbot.api.message_components"].Plain = Plain
    class Star:
        def __init__(self, context):
            self.context = context
    modules["astrbot.api.star"].Star = Star
    modules["astrbot.api.star"].Context = object
    modules["astrbot.api.star"].register = decorator
    modules["astrbot.core.utils.astrbot_path"].get_astrbot_data_path = lambda: "unused"
    with patch.dict(sys.modules, modules):
        return importlib.import_module("astrbot_plugin_currentmusic_feedback.main")


plugin_module = load_plugin_with_stubs()


class Event:
    def __init__(self, text="", sender="member", group="123", platform="bot-1", message_id="m1"):
        self.text, self.sender, self.group, self.platform = text, sender, group, platform
        self.message_obj = SimpleNamespace(message_id=message_id)
        self.unified_msg_origin = f"{platform}:GroupMessage:{sender}_{group}"
        self.components = [Plain(text)]
        self.sent = []
        self.stopped = False

    def get_group_id(self): return self.group
    def get_sender_id(self): return self.sender
    def get_self_id(self): return "bot"
    def get_platform_id(self): return self.platform
    def get_platform_name(self): return "aiocqhttp"
    def get_messages(self): return self.components
    def stop_event(self): self.stopped = True
    def plain_result(self, text): return text
    def chain_result(self, parts): return parts
    async def send(self, result): self.sent.append(result)


class PluginTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.context = SimpleNamespace(
            get_config=lambda umo: {"admins_id": ["10001", 10002]},
            get_current_chat_provider_id=AsyncMock(return_value="provider"),
            llm_generate=AsyncMock(return_value=SimpleNamespace(completion_text=json.dumps(report()))),
        )
        with patch.object(plugin_module, "get_astrbot_data_path", return_value=self.temp.name):
            self.plugin = plugin_module.CurrentMusicFeedback(self.context, {"allowed_groups": ["123"], "github_token": "test-token"})
        self.github = SimpleNamespace(create=AsyncMock(return_value="https://github.com/bileizhen/CurrentMusicX/issues/321"), reconcile=AsyncMock(return_value=None))
        self.github_patch = patch.object(plugin_module, "GitHub", return_value=self.github)
        self.github_patch.start()

    async def asyncTearDown(self):
        self.github_patch.stop()
        await self.plugin.terminate()
        self.temp.cleanup()

    async def make_draft(self):
        event = Event("CurrentMusicX 播放时闪退")
        await self.plugin.on_group(event)
        return self.plugin.store.pending(self.plugin.scope(event))[0], event

    async def test_detection_mentions_all_profile_admins_without_posting(self):
        draft, event = await self.make_draft()
        self.assertEqual([part.qq for part in event.sent[0] if isinstance(part, At)], ["10001", "10002"])
        self.github.create.assert_not_awaited()
        self.assertEqual(draft["state"], "pending")
        kwargs = self.context.llm_generate.call_args.kwargs
        self.assertIsNone(kwargs["tools"])
        self.assertEqual(kwargs["contexts"], [])

    async def test_any_configured_admin_can_approve_in_isolated_group_session(self):
        draft, _ = await self.make_draft()
        event = Event("/bug确认 " + draft["id"], sender="10002")
        await self.plugin.on_group(event)
        self.github.create.assert_awaited_once()
        self.assertIn("已提交", event.sent[0])

    async def test_non_admin_and_other_group_cannot_approve(self):
        draft, _ = await self.make_draft()
        self.plugin.config["allowed_groups"].append("456")
        for event in [Event("/bug确认 " + draft["id"], sender="member"), Event("/bug确认 " + draft["id"], sender="10001", group="456")]:
            await self.plugin.on_group(event)
        self.github.create.assert_not_awaited()

    async def test_current_profile_is_used_and_revoked_admin_loses_access(self):
        draft, _ = await self.make_draft()
        seen = []
        def config(umo):
            seen.append(umo)
            return {"admins_id": ["10002"]}
        self.context.get_config = config
        event = Event("/bug确认 " + draft["id"], sender="10001")
        await self.plugin.on_group(event)
        self.assertIn(event.unified_msg_origin, seen)
        self.github.create.assert_not_awaited()

    async def test_two_admins_confirm_concurrently_only_one_post(self):
        draft, _ = await self.make_draft()
        async def create(*args):
            await asyncio.sleep(0.01)
            return "https://github.com/bileizhen/CurrentMusicX/issues/321"
        self.github.create.side_effect = create
        await asyncio.gather(*[self.plugin.on_group(Event("/bug确认 " + draft["id"], sender=admin)) for admin in ["10001", "10002"]])
        self.github.create.assert_awaited_once()

    async def test_timeout_locks_and_reconcile_finds_existing_issue(self):
        draft, event = await self.make_draft()
        self.github.create.side_effect = TimeoutError()
        await self.plugin.on_group(Event("/bug确认 " + draft["id"], sender="10001"))
        await self.plugin.on_group(Event("/bug确认 " + draft["id"], sender="10002"))
        self.github.create.assert_awaited_once()
        self.assertEqual(self.plugin.store.get(draft["id"], self.plugin.scope(event))["state"], "unknown")
        self.github.reconcile.return_value = "https://github.com/bileizhen/CurrentMusicX/issues/321"
        await self.plugin.on_group(Event("/bug核对 " + draft["id"], sender="10001"))
        self.assertEqual(self.plugin.store.get(draft["id"], self.plugin.scope(event))["state"], "submitted")

    async def test_rejected_request_requires_new_confirmation(self):
        draft, event = await self.make_draft()
        self.github.create.side_effect = Rejected("HTTP 403")
        await self.plugin.on_group(Event("/bug确认 " + draft["id"], sender="10001"))
        self.github.create.assert_awaited_once()
        self.assertEqual(self.plugin.store.get(draft["id"], self.plugin.scope(event))["state"], "pending")

    async def test_unlisted_group_private_and_self_messages_are_ignored(self):
        for event in [Event("闪退", group="456"), Event("闪退", group=""), Event("闪退", sender="bot")]:
            await self.plugin.on_group(event)
        self.context.llm_generate.assert_not_awaited()
        self.plugin.config["allowed_groups"] = []
        await self.plugin.on_group(Event("闪退"))
        self.context.llm_generate.assert_not_awaited()

    async def test_no_admins_no_analysis(self):
        self.context.get_config = lambda umo: {"admins_id": []}
        await self.plugin.on_group(Event("闪退"))
        self.context.llm_generate.assert_not_awaited()

    async def test_quoted_command_cannot_confirm(self):
        draft, _ = await self.make_draft()
        event = Event(sender="10001")
        event.components = [SimpleNamespace(message_str="/bug确认 " + draft["id"])]
        await self.plugin.on_group(event)
        self.github.create.assert_not_awaited()

    async def test_edit_command_changes_approval_code(self):
        draft, event = await self.make_draft()
        await self.plugin.on_group(Event("/bug修改 " + draft["id"] + " version 1.3.4", sender="10001"))
        await self.plugin.on_group(Event("/bug确认 " + draft["id"], sender="10002"))
        self.github.create.assert_not_awaited()
        updated = self.plugin.store.pending(self.plugin.scope(event))[0]
        await self.plugin.on_group(Event("/bug确认 " + updated["id"], sender="10002"))
        self.assertEqual(self.github.create.call_args.args[1]["version"], "1.3.4")

    async def test_expired_or_cancelled_drafts_cannot_submit(self):
        draft, event = await self.make_draft()
        await self.plugin.on_group(Event("/bug取消 " + draft["id"], sender="10001"))
        await self.plugin.on_group(Event("/bug确认 " + draft["id"], sender="10002"))
        expired = self.plugin.store.add(self.plugin.scope(event), "expired", {**json.loads(draft["report"]), "title": "旧反馈"}, ttl_hours=-1)
        await self.plugin.on_group(Event("/bug确认 " + expired["id"], sender="10001"))
        self.github.create.assert_not_awaited()

    async def test_unload_cancels_inflight_submit_and_preserves_uncertain_state(self):
        draft, event = await self.make_draft()
        started = asyncio.Event()
        async def create(*args):
            started.set()
            await asyncio.Future()
        self.github.create.side_effect = create
        operation = asyncio.create_task(self.plugin.on_group(Event("/bug确认 " + draft["id"], sender="10001")))
        await started.wait()
        db_path = Path(self.temp.name) / "plugin_data/astrbot_plugin_currentmusic_feedback/drafts.sqlite3"
        await self.plugin.terminate()
        self.assertTrue(operation.cancelled())
        reopened = Store(db_path)
        try:
            self.assertEqual(reopened.get(draft["id"], self.plugin.scope(event))["state"], "unknown")
        finally:
            reopened.close()
        # Avoid a second close in the fixture cleanup.
        self.plugin.store = Store(db_path)


if __name__ == "__main__":
    unittest.main()
