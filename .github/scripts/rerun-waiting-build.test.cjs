const {test} = require('node:test');
const assert = require('node:assert/strict');
const {rerunWaitingBuild} = require('./rerun-waiting-build.cjs');

const pr = {number: 15, head: {sha: 'head', ref: 'fix', repo: {id: 2}},
  base: {sha: 'base', ref: 'main', repo: {id: 1}}};
const run = {id: 101, run_number: 10, run_attempt: 2, event: 'pull_request', head_sha: 'head',
  head_branch: 'fix', repository: {id: 1}, head_repository: {id: 2}, pull_requests: [],
  status: 'completed', conclusion: 'success'};
const scope = {name: 'scope', conclusion: 'success'};
const marker = {name: 'waiting-for-pr-turn / 15 / base / head', conclusion: 'skipped'};

function fixture({runs = [run], jobs = [scope, marker], payload = {}, denied, current} = {}) {
  const calls = [];
  const actions = {
    listWorkflowRuns() {}, listJobsForWorkflowRunAttempt() {},
    async reRunWorkflow(args) { calls.push(args); if (denied) throw denied; },
    async getWorkflowRun() { return {data: current}; },
  };
  const github = {rest: {actions}, async paginate(method, args) {
    if (method === actions.listWorkflowRuns) {
      assert.equal(args.workflow_id, 'android.yml');
      assert.equal(args.event, 'pull_request');
      assert.equal(args.head_sha, pr.head.sha);
      return runs;
    }
    assert.equal(method, actions.listJobsForWorkflowRunAttempt);
    assert.equal(args.attempt_number, run.run_attempt);
    return jobs;
  }};
  return {calls, start: () => rerunWaitingBuild({github, pr,
    context: {repo: {owner: 'owner', repo: 'app'}, payload}, core: {info() {}}})};
}

test('promotes the exact deferred attempt with or without fork PR links', async () => {
  for (const pull_requests of [[], [{number: 15}]]) {
    const f = fixture({runs: [{...run, pull_requests}]}); await f.start();
    assert.deepEqual(f.calls, [{owner: 'owner', repo: 'app', run_id: 101}]);
  }
});

test('does not rerun a workflow for a different ref, repo, PR, SHA or event', async () => {
  for (const changes of [{head_branch: 'other'}, {head_repository: {id: 4}}, {repository: {id: 3}},
    {pull_requests: [{number: 16}]}, {head_sha: 'new-head'}, {event: 'workflow_dispatch'}]) {
    const f = fixture({runs: [{...run, ...changes}]}); await f.start();
    assert.equal(f.calls.length, 0);
  }
});

test('a pending approval, active build or failure supersedes all older eligible runs', async () => {
  for (const changes of [{status: 'queued'}, {status: 'in_progress'},
    {conclusion: 'action_required'}, {conclusion: 'failure'}, {conclusion: 'cancelled'}]) {
    const f = fixture({runs: [run, {...run, id: 102, run_number: 11, ...changes}]}); await f.start();
    assert.equal(f.calls.length, 0);
  }
});

test('ordinary CI, missing markers, and stale or unrelated markers are never promoted', async () => {
  for (const jobs of [[scope], [marker], [scope, marker, {name: 'build', conclusion: 'success'}],
    [scope, {...marker, conclusion: 'success'}], [scope, {...marker, name: 'waiting-for-pr-turn / 15 / old / head'}],
    [scope, {...marker, name: 'waiting-for-pr-turn / 16 / base / head'}]]) {
    const f = fixture({jobs}); await f.start();
    assert.equal(f.calls.length, 0);
  }
});

test('ignores a duplicate API request only when another attempt actually started', async () => {
  const denied = Object.assign(new Error('re-run rejected'), {status: 403});
  for (const current of [{...run, status: 'queued'}, {...run, run_attempt: 3}]) {
    const f = fixture({denied, current}); await f.start();
    assert.equal(f.calls.length, 1);
  }
  await assert.rejects(fixture({denied, current: run}).start(), /re-run rejected/);
});

test('completion of a rerun cannot recursively rerun the same deferred workflow', async () => {
  const f = fixture({payload: {workflow_run: {id: run.id}}}); await f.start();
  assert.equal(f.calls.length, 0);
});
