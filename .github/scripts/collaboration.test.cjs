const { test } = require('node:test');
const assert = require('node:assert/strict');
const { prLabels, obsoleteBotLabels, section, issuePlan, staleDecision, run } = require('./collaboration.cjs');
const now = Date.parse('2026-11-03T00:00:00Z');
const ago = days => new Date(now - days * 86400000).toISOString();
const issue = overrides => ({ state: 'open', created_at: ago(100), labels: ['bug'], assignees: [], author_association: 'NONE', ...overrides });
test('PR titles map to existing release labels and breaking change wins separately', () => {
  assert.deepEqual(prLabels('feat(player): 增加封面'), ['enhancement']);
  assert.deepEqual(prLabels('fix!: remove old API'), ['bug', 'breaking-change']);
  assert.deepEqual(prLabels('perf: 卡顿优化'), ['performance']);
  assert.deepEqual(prLabels('ordinary title'), []);
  assert.deepEqual(prLabels('docs: update', 'BREAKING CHANGE: data migration'), ['documentation', 'breaking-change']);
});
test('form parser recognizes missing responses without guessing free-form issues', () => {
  assert.equal(section('### 应用版本\n\n_No response_\n\n### 设备\nA', '应用版本'), '');
  assert.equal(issuePlan(issue({body: '问题说明'})).missing.length, 0);
  const plan = issuePlan(issue({body: '### 应用版本\n1.3.2\n### 问题模块\n歌词'}));
  assert.equal(plan.module, 'module: lyrics');
  assert.equal(plan.missing.length, 3);
  assert.equal(issuePlan(issue({labels: ['bug', 'bot: skip']})), null);
});
test('title edits remove only stale bot-owned labels, preserving manual classification',()=>{
  const events = [{event:'labeled',label:{name:'bug'},actor:{login:'github-actions[bot]'}}, {event:'labeled',label:{name:'performance'},actor:{login:'bileizhen'}}];
  assert.deepEqual(obsoleteBotLabels(['bug','performance'],['enhancement'],events),['bug']);
  assert.deepEqual(obsoleteBotLabels(['bug'],['bug'],events),[]);
});
test('marks 60-day inactive issues but cannot close immediately', () => {
  assert.equal(staleDecision(issue(), [], [], now), 'mark-stale');
  assert.equal(staleDecision(issue({created_at: ago(10)}), [], [], now), null);
});
test('stale close uses label date, not bot-updated issue timestamp', () => {
  const events = [{event:'labeled', label:{name:'stale'}, created_at:ago(31)}];
  assert.equal(staleDecision(issue({labels:['bug','stale'], updated_at:ago(0)}), [], events, now), 'close-stale');
  assert.equal(staleDecision(issue({labels:['bug','stale']}), [], [{...events[0],created_at:ago(29)}], now), null);
  assert.equal(staleDecision(issue({labels:['bug','stale']}), [{user:{type:'User'},created_at:ago(1)}], events, now), 'revive');
});
test('feature requests, PRs, reopened and assigned reports cannot auto close', () => {
  const events = [{event:'labeled',label:{name:'stale'},created_at:ago(40)}];
  for (const overrides of [{labels:['bug','enhancement','stale']},{pull_request:{}},{assignees:[{}]}, {milestone:{}}, {author_association:'OWNER'}, {locked:true}]) {
    assert.ok(!staleDecision(issue({labels:['bug','stale'],...overrides}), [], events, now)?.startsWith('close'));
  }
  assert.equal(staleDecision(issue({labels:['bug','stale']}), [], [...events,{event:'reopened'}], now), null);
});
test('needs-info timeout does not close an issue with a later human reply', () => {
  const events = [{event:'labeled',label:{name:'needs-info'},created_at:ago(15)}];
  const item = issue({labels:['bug','needs-info']});
  assert.equal(staleDecision(item, [], events, now), 'close-info');
  assert.equal(staleDecision(item, [{user:{type:'User'},created_at:ago(1)}], events, now), null);
});
test('tracked issues are exempt from inactivity and needs-info timeouts', () => {
  const {STATUS_LABELS} = require('./label-palette.cjs');
  for (const status of Object.keys(STATUS_LABELS)) {
    for (const tag of ['bug','stale','needs-info']) {
      const events = [{event:'labeled',label:{name:tag},created_at:ago(40)}];
      assert.equal(staleDecision(issue({labels:['bug',tag,status]}), [], events, now), null);
    }
  }
});
test('dry-run emits plans without remote writes', async () => {
  const old = process.env.TRIAGE_MODE;
  process.env.TRIAGE_MODE = 'dry-run';
  let writes = 0;
  const github = {
    paginate: async () => [],
    rest: {
      issues: {
        get: async () => ({data:issue({number:1,title:'fix: issue',pull_request:{}})}),
        getLabel: async () => ({}), addLabels: async () => writes++,
      },
      pulls: {get: async () => ({data:{changed_files:1,additions:1,deletions:0}})},
    },
  };
  try { await run({github, context:{repo:{},eventName:'pull_request_target',payload:{pull_request:{number:1},sender:{type:'User'}}},core:{info:()=>{}}}); assert.equal(writes,0); }
  finally { if (old === undefined) delete process.env.TRIAGE_MODE; else process.env.TRIAGE_MODE = old; }
});
