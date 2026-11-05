const {test} = require('node:test');
const assert = require('node:assert/strict');
const {resolveUpstream} = require('./ci-publication.cjs');

const completed = () => ({id: 193, run_attempt: 2, run_number: 193,
  path: '.github/workflows/android.yml', event: 'workflow_dispatch',
  status: 'completed', conclusion: 'success', head_branch: 'main',
  head_sha: 'a'.repeat(40), head_repository: {full_name: 'owner/app'}});
const build = () => ({name: 'build', conclusion: 'success', steps: [
  {name: 'Build and verify unsigned CI release', conclusion: 'success'},
  {name: 'Upload unsigned CI APK', conclusion: 'success'},
]});
function fixture(runs = [completed()], jobs = [build()]) {
  const calls = [];
  const context = {repo: {owner: 'owner', repo: 'app'}, ref: 'refs/heads/main',
    eventName: 'workflow_dispatch', payload: {inputs: {run_id: '193', run_attempt: '2'}}};
  const github = {rest: {actions: {
    async getWorkflowRun(params) {
      calls.push(['run', params]);
      return {data: runs.length > 1 ? runs.shift() : runs[0]};
    },
    listJobsForWorkflowRunAttempt() {},
  }}, async paginate(method, params) {
    assert.equal(method, github.rest.actions.listJobsForWorkflowRunAttempt);
    calls.push(['jobs', params]); return jobs;
  }};
  return {context, github, calls, sleep: async ms => {calls.push(['sleep', ms]);}};
}

test('bot-dispatched publication waits for its notifier and validates the exact successful attempt', async () => {
  const f = fixture([{...completed(), status: 'in_progress', conclusion: null}, completed()]);
  assert.deepEqual(await resolveUpstream(f), completed());
  assert.deepEqual(f.calls.map(c => c[0]), ['run', 'sleep', 'run', 'jobs']);
  assert.equal(f.calls.at(-1)[1].attempt_number, 2);
  assert.equal(f.calls.at(-1)[1].run_id, 193);
});

test('dispatch rejects failures, PRs, forks, other workflows and rerun races before signing', async () => {
  for (const patch of [{conclusion: 'failure'}, {conclusion: 'cancelled'},
    {event: 'pull_request'}, {head_branch: 'feature'}, {head_repository: {full_name: 'fork/app'}},
    {path: '.github/workflows/other.yml'}, {head_sha: 'bad'}, {run_attempt: 3}, {id: 194}]) {
    const f = fixture([{...completed(), ...patch}]);
    await assert.rejects(resolveUpstream(f), /upstream|Upstream/);
    assert.deepEqual(f.calls.map(c => c[0]), ['run']);
  }
});

test('untrusted publication refs, events and malformed IDs never read an artifact source', async () => {
  for (const patch of [{ref: 'refs/heads/feature'}, {eventName: 'pull_request'},
    ...['', 'bad', '0', '-1', '1.5', '9007199254740992'].map(run_id =>
      ({payload: {inputs: {run_id, run_attempt: '2'}}})),
    {payload: {inputs: {run_id: '193', run_attempt: '0'}}}]) {
    const f = fixture(); Object.assign(f.context, patch);
    await assert.rejects(resolveUpstream(f));
    assert.equal(f.calls.length, 0);
  }
});

test('skipped or missing APK compilation/upload never enters signing', async () => {
  for (const jobs of [[], [{...build(), conclusion: 'skipped'}],
    ...[0, 1].map(index => [{...build(), steps: build().steps.map((s, i) =>
      i === index ? {...s, conclusion: 'skipped'} : s)}])]) {
    assert.equal(await resolveUpstream(fixture([completed()], jobs)), null);
  }
});

test('a superseding attempt during the wait is rejected, and unfinished runs time out', async () => {
  const pending = {...completed(), status: 'in_progress', conclusion: null};
  await assert.rejects(resolveUpstream(fixture([pending, {...completed(), run_attempt: 3}])), /changed/);
  const f = fixture([pending]);
  await assert.rejects(resolveUpstream(f), /has not completed/);
  assert.equal(f.calls.filter(c => c[0] === 'sleep').length, 12);
  assert.equal(f.calls.some(c => c[0] === 'jobs'), false);
});

test('workflow_run fallback is revalidated against the API and cannot change SHA or attempt', async () => {
  for (const patch of [{}, {head_sha: 'b'.repeat(40)}, {run_attempt: 1}]) {
    const f = fixture();
    f.context.eventName = 'workflow_run';
    f.context.payload = {workflow_run: {...completed(), ...patch}};
    if (Object.keys(patch).length) await assert.rejects(resolveUpstream(f), /changed/);
    else assert.deepEqual(await resolveUpstream(f), completed());
  }
});
