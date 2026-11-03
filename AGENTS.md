# CurrentMusicX — Agent instructions

## Pull request review language

When reviewing a pull request, write review comments and the review summary in the **same natural language as the PR author's description** (the PR body). Determine the primary language from the substantive explanation, not from Conventional Commit prefixes, code, file names, URLs, or technical terms.

- If the description is mainly Simplified Chinese, review in Simplified Chinese.
- If it is mainly Traditional Chinese, review in Traditional Chinese.
- If it is mainly English, review in English.
- For another clearly identifiable language, use that language where supported.
- If the description is missing, ambiguous, or genuinely multilingual, follow the primary natural language of the PR title; if still unclear, use Simplified Chinese as the repository default.
- Keep code identifiers, API names, paths, commands, error messages, and conventional severity markers (P0/P1/P2/P3) unchanged.
- Apply this to inline findings, explanations, proposed fixes, and overall summaries. Do not force English solely because source code is in English.
- Never sacrifice technical precision or invent findings to satisfy the language preference.

## Review priorities

Check correctness, regression risk, security, concurrency, Android lifecycle, audio processing, CI permissions, and release signing. Prefer actionable findings with concrete file and line references. Respect existing CI, branch protection, and maintainer review requirements.
