const {test} = require('node:test');
const assert = require('node:assert/strict');
const {run} = require('./update-pr-branches.cjs');

const fullName = 'owner/app';
const pull = (number, changes = {}) => ({
  number, state: 'open', draft: false, mergeable: true, labels: [],
  maintainer_can_modify: true, base: {ref: 'main', sha: 'base', repo: {full_name: fullName}},
  head: {ref: 'topic', sha: 'head' + number, repo: {full_name: fullName}}, ...changes,
});

function fixture({prs = [pull(13)], live, mainSha = 'base', behind = 1, updateError, event = {}} = {}) {
  const updates = [], comparisons = [], warnings = [];
  const github = {rest: {
    pulls: {list() {}, get: async ({pull_number}) => ({data: live ?? prs.find(pr => pr.number === pull_number)}),
      updateBranch: async args => {
        updates.push(args); if (updateError) throw Object.assign(new Error('denied'), {status: updateError});
      }},
    repos: {getBranch: async args => {
      assert.equal(args.branch, 'main'); return {data: {commit: {sha: mainSha}}};
    }, compareCommitsWithBasehead: async args => {
      comparisons.push(args); return {data: {behind_by: behind}};
    }},
    actions: {listWorkflowRuns: async () => ({data: {workflow_runs: []}})},
  }};
  github.paginate = async method => method === github.rest.pulls.list ? prs : [];
  return {updates, comparisons, warnings, args: {github,
    context: {repo: {owner: 'owner', repo: 'app'}, payload: event},
    core: {info() {}, warning: text => warnings.push(text)}}};
}

test('a main push and a later PR event both synchronize only the oldest eligible PR', async () => {
  for (const event of [{}, {pull_request: {number: 16}}]) {
    const prs = [pull(16), pull(11, {draft: true}), pull(12, {labels: [{name: 'bot: skip'}]}), pull(13)];
    const f = fixture({prs, event}); await run(f.args);
    assert.deepEqual(f.updates.map(x => [x.pull_number, x.expected_head_sha]), [[13, 'head13']]);
    assert.deepEqual(f.comparisons.map(x => x.basehead), ['base...head13']);
  }
});

test('main can advance while the PR still reports an older base SHA', async () => {
  const f = fixture({mainSha: 'merged-47'});
  const compare = f.args.github.rest.repos.compareCommitsWithBasehead;
  f.args.github.rest.repos.compareCommitsWithBasehead = async args => {
    await compare(args);
    return {data: {behind_by: args.basehead === 'base...head13' ? 0 : 4}};
  };
  await run(f.args);
  assert.deepEqual(f.comparisons.map(x => x.basehead), ['merged-47...head13']);
  assert.deepEqual(f.updates.map(x => x.expected_head_sha), ['head13']);
});

test('after the first PR leaves, only the new head is updated', async () => {
  const f = fixture({prs: [pull(16), pull(14)], event: {action: 'closed', pull_request: {number: 13}}});
  await run(f.args);
  assert.deepEqual(f.updates.map(x => x.pull_number), [14]);
});

test('an empty queue or a concurrent edit does not update a PR', async () => {
  for (const options of [{prs: []}, {live: pull(13, {state: 'closed'})}, {live: pull(13, {draft: true})},
    {live: pull(13, {labels: [{name: 'bot: skip'}]})}, {live: pull(13, {head: pull(14).head})}]) {
    const f = fixture(options); await run(f.args);
    assert.equal(f.comparisons.length, 0); assert.equal(f.updates.length, 0);
  }
});

test('an up-to-date PR does not create a new head', async () => {
  const f = fixture({behind: 0}); await run(f.args); assert.equal(f.updates.length, 0);
});

test('conflicts and forks that disallow maintainer edits never receive updates', async () => {
  for (const live of [pull(13, {mergeable: false}), pull(13, {maintainer_can_modify: false,
    head: {...pull(13).head, repo: {full_name: 'contributor/app'}}})]) {
    const f = fixture({live}); await run(f.args);
    assert.equal(f.updates.length, 0); assert.equal(f.warnings.length, 1);
  }
});

test('permission and concurrent-head failures are reported without retrying writes', async () => {
  for (const updateError of [403, 404, 409, 422]) {
    const f = fixture({updateError}); await run(f.args);
    assert.equal(f.updates.length, 1); assert.equal(f.warnings.length, 1);
  }
});

test('unexpected API errors fail the controller', async () => {
  const f = fixture({updateError: 500}); await assert.rejects(run(f.args));
});
