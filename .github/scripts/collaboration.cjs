// Repository metadata only. Never load or execute files from a pull request head.
const {STATUS_LABELS,labelColor}=require('./label-palette.cjs');
const types = {
  feat: 'enhancement', fix: 'bug', perf: 'performance', docs: 'documentation',
  deps: 'dependencies', chore: 'maintenance', ci: 'maintenance', build: 'maintenance',
  refactor: 'maintenance', test: 'maintenance', release: 'maintenance',
};
const modules = {
  '播放与音效': 'module: playback', '歌词': 'module: lyrics',
  '音源与网络': 'module: network', '界面与外观': 'module: appearance',
  '下载与缓存': 'module: storage', '其他': 'module: general',
};
const managedLabels = {
  ...Object.fromEntries([...new Set(Object.values(types))].map(name => [name, '发布分类'])),
  ...Object.fromEntries(Object.values(modules).map(name => [name, '问题所属模块'])),
  'breaking-change': '不兼容变更', 'needs-info': '等待作者补充信息',
  stale: '长期没有活动', 'bot: skip': '跳过自动分流与过期处理',
  'bot: auto-closed': '自动关闭，等待补充信息后可由维护者重开',
  'scope too large': '建议拆分过大的 PR', 'announcement': '维护公告',
  'needs follow-up': '需要继续跟进，不自动关闭',
};
const names = issue => issue.labels.map(label => typeof label === 'string' ? label : label.name);
const maintainer = item => ['OWNER', 'MEMBER', 'COLLABORATOR'].includes(item.author_association);
const human = item => item.user?.type !== 'Bot' && !item.user?.login?.endsWith('[bot]');
function section(body, heading) {
  const lines = String(body ?? '').split(/\r?\n/);
  const start = lines.indexOf(`### ${heading}`);
  if (start < 0) return '';
  const end = lines.findIndex((line, i) => i > start && line.startsWith('### '));
  const value = lines.slice(start + 1, end < 0 ? undefined : end).join('\n').trim();
  return /^(_No response_|无|N\/A)$/i.test(value) ? '' : value;
}
function prLabels(title, body = '') {
  const match = /^(\w+)(?:\([^)]*\))?(!)?:\s*\S/.exec(title.trim());
  return [...new Set([
    match && types[match[1].toLowerCase()],
    (match?.[2] || /^BREAKING CHANGE:/m.test(body)) && 'breaking-change',
  ].filter(Boolean))];
}
function obsoleteBotLabels(labels, desired, events) {
  const typeLabels = new Set([...Object.values(types), 'breaking-change']);
  return labels.filter(label => {
    if (!typeLabels.has(label) || desired.includes(label)) return false;
    const last = events.filter(e => ['labeled','unlabeled'].includes(e.event) && e.label?.name === label).at(-1);
    return last?.event === 'labeled' && last.actor?.login === 'github-actions[bot]';
  });
}
function issuePlan(issue) {
  const labels = names(issue);
  if (issue.state !== 'open' || labels.includes('bot: skip') || labels.includes('announcement')) return null;
  const module = modules[section(issue.body, '问题模块')];
  // Free-form issues are allowed: do not claim their information is missing merely
  // because headings differ from the form. Maintainer reports need no questionnaire.
  const form = labels.includes('bug') && String(issue.body).includes('### 应用版本');
  const missing = form && !maintainer(issue)
    ? ['应用版本', '设备与 Android 版本', '复现步骤', '实际与预期结果'].filter(h => !section(issue.body, h)) : [];
  return { module: labels.some(l => l.startsWith('module: ')) ? null : module, missing, form };
}
function staleDecision(issue, comments, events, now = Date.now()) {
  const labels = names(issue);
  if (labels.some(label => Object.hasOwn(STATUS_LABELS,label))) return null;
  if (issue.state !== 'open' || issue.locked || issue.assignees?.length || issue.milestone || maintainer(issue) ||
      labels.some(l => ['bot: skip', 'announcement', 'needs follow-up', 'help wanted', 'priority: high', 'provider-request'].includes(l))) return null;
  const activity = Math.max(Date.parse(issue.created_at), ...comments.filter(human).map(c => Date.parse(c.updated_at ?? c.created_at)));
  const labelAt = label => events.filter(e => e.event === 'labeled' && e.label?.name === label).at(-1)?.created_at;
  const age = timestamp => (now - Date.parse(timestamp)) / 86400000;
  const reopened = events.some(e => e.event === 'reopened');
  const mayClose = !issue.pull_request && !labels.includes('enhancement') && !reopened &&
    !labels.includes('bot: auto-closed') && labels.some(l => ['bug', 'question'].includes(l));
  const infoAt = labelAt('needs-info');
  if (labels.includes('needs-info') && infoAt && age(infoAt) >= 14 && activity <= Date.parse(infoAt) && mayClose) return 'close-info';
  const staleAt = labelAt('stale');
  if (labels.includes('stale')) {
    if (!staleAt) return null;
    if (activity > Date.parse(staleAt)) return 'revive';
    return mayClose && age(staleAt) >= 30 ? 'close-stale' : null;
  }
  return (now - activity) / 86400000 >= 60 ? 'mark-stale' : null;
}
async function run({ github, context, core }, mode = 'event') {
  const repo = context.repo;
  if (process.env.TRIAGE_MODE && !['off', 'dry-run', 'live'].includes(process.env.TRIAGE_MODE)) throw new Error('Invalid TRIAGE_MODE');
  const dry = process.env.TRIAGE_MODE === 'off' ? null : process.env.TRIAGE_DRY_RUN === 'true' || process.env.TRIAGE_MODE === 'dry-run';
  if (dry === null) return;
  const mutate = async (description, call) => { core.info(description); if (!dry) await call(); };
  const ensureLabel = async name => {
    try { await github.rest.issues.getLabel({ ...repo, name }); }
    catch (error) { if (error.status !== 404) throw error; await mutate(`Create label ${name}`, () => github.rest.issues.createLabel({ ...repo, name, color: labelColor(name), description: managedLabels[name] ?? name })); }
  };
  const add = async (number, labels) => {
    if (!labels.length) return;
    for (const label of labels) await ensureLabel(label);
    await mutate(`Label #${number}: ${labels.join(', ')}`, () => github.rest.issues.addLabels({ ...repo, issue_number: number, labels }));
  };
  const remove = async (number, name) => mutate(`Remove ${name} from #${number}`, async () => {
    try { await github.rest.issues.removeLabel({ ...repo, issue_number: number, name }); }
    catch (error) { if (error.status !== 404) throw error; }
  });
  const commentsFor = number => github.paginate(github.rest.issues.listComments, { ...repo, issue_number: number, per_page: 100 });
  const comment = async (number, text, kind) => {
    const marker = `<!-- currentmusic:${kind} -->`;
    const existing = (await commentsFor(number)).find(c => c.user.login === 'github-actions[bot]' && c.body?.startsWith(marker));
    const body = `${marker}\n${text}`;
    if (existing?.body === body) return;
    await mutate(`Update ${kind} comment #${number}`, () => existing
      ? github.rest.issues.updateComment({ ...repo, comment_id: existing.id, body })
      : github.rest.issues.createComment({ ...repo, issue_number: number, body }));
  };
  if (mode === 'stale') {
    const issues = await github.paginate(github.rest.issues.listForRepo, { ...repo, state: 'open', per_page: 100 });
    let closed = 0;
    for (const item of issues) {
      // Re-fetch before acting, rather than relying on the earlier collection snapshot.
      const issue = (await github.rest.issues.get({ ...repo, issue_number: item.number })).data;
      const comments = await commentsFor(issue.number);
      const events = await github.paginate(github.rest.issues.listEventsForTimeline, { ...repo, issue_number: issue.number, per_page: 100 });
      const action = staleDecision(issue, comments, events);
      if (action === 'revive') await remove(issue.number, 'stale');
      if (action === 'mark-stale') {
        await comment(issue.number, '此条目已 60 天没有讨论活动。如仍可复现或仍有需求，请补充信息。功能建议与 PR 不因长期无活动自动关闭；符合条件的 Bug／问题将在标记后 30 天关闭。', 'stale');
        await add(issue.number, ['stale']);
      }
      if (action?.startsWith('close-') && closed < 2) {
        await comment(issue.number, action === 'close-info'
          ? '等待补充信息已超过 14 天，暂时关闭；这不代表问题已经修复。补充后可请维护者重开。'
          : '标记长期无活动后已超过 30 天，暂时关闭；这不代表问题已经修复。仍有问题请补充信息，请维护者重开。', 'closed');
        await add(issue.number, ['bot: auto-closed']);
        await mutate(`Close #${issue.number}`, () => github.rest.issues.update({ ...repo, issue_number: issue.number, state: 'closed', state_reason: 'not_planned' }));
        closed++;
      }
    }
    return;
  }
  if (context.payload.sender?.type === 'Bot') return;
  const number = context.payload.pull_request?.number ?? context.payload.issue?.number;
  if (!number) return;
  const issue = (await github.rest.issues.get({ ...repo, issue_number: number })).data;
  if (names(issue).includes('bot: skip')) return;
  if (issue.pull_request) {
    // issue_comment events must never process PRs with the issue questionnaire.
    if (context.eventName !== 'pull_request_target' || issue.state !== 'open') return;
    const desired = prLabels(issue.title, issue.body);
    const events = await github.paginate(github.rest.issues.listEventsForTimeline, { ...repo, issue_number: number, per_page: 100 });
    for (const label of obsoleteBotLabels(names(issue), desired, events)) await remove(number, label);
    await add(number, desired);
    const pr = (await github.rest.pulls.get({ ...repo, pull_number: number })).data;
    if (!maintainer(issue) && (pr.changed_files > 40 || pr.additions + pr.deletions > 3000)) {
      await add(number, ['scope too large']);
      await comment(number, '此 PR 改动较多，请核对是否可以按独立功能拆分，并补充验证范围。此提示不表示代码审查已通过。', 'scope');
    }
    return;
  }
  if (context.eventName === 'issue_comment' && issue.state === 'open') {
    if (names(issue).includes('stale')) await remove(number, 'stale');
    if (context.payload.comment?.user.login === issue.user.login && names(issue).includes('needs-info')) {
      await remove(number, 'needs-info');
      await add(number, ['needs follow-up']);
    }
    return;
  }
  const plan = issuePlan(issue);
  if (!plan) return;
  if (context.payload.action === 'edited' && names(issue).includes('stale')) await remove(number, 'stale');
  if (plan.module) await add(number, [plan.module]);
  if (plan.missing.length) {
    await comment(number, `请补充：${plan.missing.join('、')}。请勿公开凭据或未脱敏日志。14 天未补充且无人跟进时会暂时关闭，之后可由维护者重开。`, 'info');
    if (names(issue).includes('needs-info') && context.payload.action === 'edited') await remove(number, 'needs-info');
    if (!names(issue).includes('needs-info') || context.payload.action === 'edited') await add(number, ['needs-info']);
  } else if (plan.form && names(issue).includes('needs-info')) {
    await remove(number, 'needs-info');
    await add(number, ['needs follow-up']);
    await comment(number, '已补充模板所需信息，等待维护者进一步检查。', 'info');
  }
}
module.exports = { prLabels, obsoleteBotLabels, section, issuePlan, staleDecision, run };
