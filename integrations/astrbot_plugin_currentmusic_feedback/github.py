"""GitHub API: never automatically retry POST after an uncertain response."""

from datetime import datetime, timezone

import aiohttp

from .core import REPOSITORY, marker, render


class GitHubError(Exception):
    pass


class Rejected(GitHubError):
    """GitHub explicitly rejected the request; safe to return to pending."""


class GitHub:
    def __init__(self, token: str):
        if not token.strip():
            raise ValueError("请先配置 GitHub Token")
        self.token = token.strip()

    def session(self):
        return aiohttp.ClientSession(
            headers={"Authorization": "Bearer " + self.token, "Accept": "application/vnd.github+json",
                     "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "CurrentMusicX-AstrBot-Feedback"},
            timeout=aiohttp.ClientTimeout(total=25),
        )

    async def create(self, draft_id: str, report: dict) -> str:
        title, body = render(report)
        async with self.session() as session:
            async with session.post(f"https://api.github.com/repos/{REPOSITORY}/issues",
                                    json={"title": title, "body": body + "\n\n" + marker(draft_id), "labels": ["bug"]},
                                    allow_redirects=False) as response:
                if response.status in (400, 401, 403, 404, 410, 422, 429):
                    raise Rejected(f"GitHub 拒绝提交（HTTP {response.status}），请检查 Token 权限、限流或仓库设置")
                if response.status != 201:
                    raise GitHubError("提交结果不确定，请核对，不能直接重试")
                data = await response.json()
                number = data.get("number")
                if type(number) is not int or number <= 0:
                    raise GitHubError("响应不完整，请核对")
                return f"https://github.com/{REPOSITORY}/issues/{number}"

    async def reconcile(self, draft: dict) -> str | None:
        since = datetime.fromtimestamp(draft["created"] - 60, timezone.utc).isoformat()
        async with self.session() as session:
            for page in range(1, 21):
                async with session.get(f"https://api.github.com/repos/{REPOSITORY}/issues",
                                       params={"state": "all", "sort": "created", "direction": "desc", "since": since,
                                               "per_page": 100, "page": page}, allow_redirects=False) as response:
                    if response.status != 200:
                        raise GitHubError(f"核对失败（HTTP {response.status}）")
                    items = await response.json()
                    for item in items:
                        if "pull_request" not in item and marker(draft["id"]) in (item.get("body") or ""):
                            return f"https://github.com/{REPOSITORY}/issues/{int(item['number'])}"
                    if len(items) < 100:
                        return None
        raise GitHubError("核对范围超过 2000 条，请手动检查仓库")
