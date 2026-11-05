const {test} = require('node:test');
const assert = require('node:assert/strict');
const {getQueueHead, inspectTurn} = require('./pr-ci-queue.cjs');

const context = {repo: {owner: 'owner', repo: 'app'}};
const pr = (number, fields = {}) => ({
  number, draft: false, labels: [], head: {repo: {full_name: 'fork/app'}, sha: 'head-' + number},
  base: {ref: 'main', sha: 'base'}, ...fields,
});
function fixture(rows, {mainSha = 'base', behind = 0} = {}) {
  const comparisons = [];
  const github = {
    paginate: async (method, args) => {
      assert.equal(method, github.rest.pulls.list);
      assert.deepEqual(args, {...context.repo, state: 'open', base: 'main', per_page: 100});
      return rows;
    },
    rest: {pulls: {list() {}}, repos: {
      getBranch: async args => {
        assert.deepEqual(args, {...context.repo, branch: 'main'});
        return {data: {commit: {sha: mainSha}}};
      },
      compareCommitsWithBasehead: async args => {
        comparisons.push(args);
        return {data: {behind_by: behind}};
      },
    }},
  };
  return {context, github, comparisons};
}

test('oldest eligible PR holds the queue even if blocked or conflicting', async () => {
  const first = pr(5, {mergeable: false, maintainer_can_modify: false});
  const rows = [pr(7), first, pr(4, {head: {repo: null}}),
    pr(3, {labels: [{name: 'bot: skip'}]}), pr(2, {draft: true})];
  assert.equal(await getQueueHead(fixture(rows)), first);
  assert.equal(await getQueueHead(fixture(rows.slice(2))), null);
});

test('only starts the first PR on its exact head, base and main', async () => {
  const f = fixture([pr(2), pr(1)]);
  assert.equal((await inspectTurn({...f, pr: pr(1)})).ready, true);
  assert.deepEqual(f.comparisons, [{...context.repo, basehead: 'base...head-1', per_page: 1}]);
});

test('empty queue, queued PR and obsolete event never reach the comparison', async () => {
  for (const [rows, event, settings] of [
    [[], pr(1)], [[pr(1), pr(2)], pr(2)], [[pr(1)], null],
    [[pr(1)], pr(1, {head: {sha: 'previous'}})],
    [[pr(1)], pr(1, {base: {sha: 'previous'}})],
    [[pr(1)], pr(1), {mainSha: 'newer-main'}],
  ]) {
    const f = fixture(rows, settings);
    assert.equal((await inspectTurn({...f, pr: event})).ready, false);
    assert.deepEqual(f.comparisons, []);
  }
});

test('a behind branch and an indeterminate comparison cannot start a build', async () => {
  for (const behind of [1, null]) {
    const f = fixture([pr(1)], {behind});
    assert.equal((await inspectTurn({...f, pr: pr(1)})).ready, false);
  }
});

test('API errors fail the gate visibly', async () => {
  const f = fixture([pr(1)]);
  f.github.rest.repos.getBranch = async () => { throw new Error('API failure'); };
  await assert.rejects(inspectTurn({...f, pr: pr(1)}), /API failure/);
});
