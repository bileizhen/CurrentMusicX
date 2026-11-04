const {test} = require('node:test');
const assert = require('node:assert/strict');
const {skipReason, run} = require('./update-pr-branches.cjs');
const pr = () => ({number: 13, state: 'open', draft: false, merged: false, mergeable: true,
  maintainer_can_modify: true, labels: [], base: {ref: 'main', sha: 'previous-main-tip', repo: {full_name: 'owner/app'}},
  head: {sha: 'head', repo: {full_name: 'contributor/app'}}});

test('only open, non-draft, mergeable and writable PRs targeting main qualify', () => {
  assert.equal(skipReason(pr(), 'owner/app'), null);
  for (const change of [{state: 'closed'}, {draft: true}, {merged: true}, {mergeable: false},
    {mergeable: null}, {maintainer_can_modify: false}, {labels: [{name: 'bot: skip'}]},
    {head: {repo: null}}, {base: {ref: 'develop'}},
    {base: {ref: 'main', repo: {full_name: 'another/app'}}}]) {
    assert.ok(skipReason({...pr(), ...change}, 'owner/app'));
  }
  assert.equal(skipReason({...pr(), maintainer_can_modify: false,
    head: {sha: 'head', repo: {full_name: 'owner/app'}}}, 'owner/app'), null);
});

function fixture({behind = 1, updateError, pull = pr(), event = true} = {}) {
  const updates = [], comparisons = [], warnings = [];
  const github = {paginate: async () => [{number: 13}], rest: {
    pulls: {list() {}, get: async () => ({data: pull}), updateBranch: async args => {
      updates.push(args); if (updateError) throw Object.assign(new Error('denied'), {status: updateError});
    }}, repos: {compareCommitsWithBasehead: async args => {
      comparisons.push(args); return {data: {behind_by: behind}};
    }},
  }};
  return {updates, comparisons, warnings, args: {github,
    context: {repo: {owner: 'owner', repo: 'app'}, payload: event ? {pull_request: {number: 13}} : {}},
    core: {info() {}, warning: text => warnings.push(text)}}};
}
test('updates behind PR with an exact head guard on PR events and main pushes', async () => {
  for (const event of [true, false]) {
    const f = fixture({event}); await run(f.args);
    assert.equal(f.updates.length, 1);
    assert.equal(f.updates[0].expected_head_sha, 'head');
    assert.equal(f.comparisons[0].basehead, 'main...head');
  }
});
test('up-to-date branches never produce an update or another CI run', async () => {
  const f = fixture({behind: 0}); await run(f.args); assert.equal(f.updates.length, 0);
});
test('conflicts are not sent to the update API', async () => {
  const f = fixture({pull: {...pr(), mergeable: false}}); await run(f.args);
  assert.equal(f.comparisons.length, 0); assert.equal(f.updates.length, 0);
});
test('permission and concurrent-head failures are reported without retrying writes', async () => {
  for (const updateError of [403, 404, 409, 422]) {
    const f = fixture({updateError}); await run(f.args);
    assert.equal(f.updates.length, 1); assert.equal(f.warnings.length, 1);
  }
});
test('unexpected API errors fail the job visibly', async () => {
  const f = fixture({updateError: 500}); await assert.rejects(run(f.args));
});
