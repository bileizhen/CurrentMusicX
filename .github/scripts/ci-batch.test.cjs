const {test} = require('node:test');
const assert = require('node:assert/strict');
const {releaseDecision} = require('./ci-batch.cjs');

const tip = 'a'.repeat(40);
const previous = 'b'.repeat(40);
const tag = 'v1.3.3-ci.173';
const published = {id: 1, tag_name: tag, name: 'CurrentMusic CI #173', draft: false, prerelease: true,
  target_commitish: 'main', assets: [{name: `CurrentMusic-Android-${tag}.apk`}, {name: 'SHA256SUMS.txt'}]};

function scenario({open = [], main = tip, releases = [published], baseline = previous,
  paths = ['app/src/main/a.kt'], tagError} = {}) {
  const calls = [];
  const repo = {owner: 'owner', repo: 'project'};
  const repos = {
    getBranch: async args => {
      calls.push('main'); assert.deepEqual(args, {...repo, branch: 'main'});
      return {data: {commit: {sha: main}}};
    },
    listReleases() {},
    getCommit: async args => {
      calls.push('tag'); assert.deepEqual(args, {...repo, ref: 'tags/' + tag});
      if (tagError) throw tagError;
      return {data: {sha: baseline}};
    },
    compareCommitsWithBasehead: async args => {
      calls.push('diff'); assert.equal(args.basehead, `${baseline}...${tip}`);
      return {data: {status: 'ahead', base_commit: {sha: baseline}, merge_base_commit: {sha: baseline},
        files: paths.map(filename => ({filename, status: 'modified'}))}};
    },
  };
  const github = {rest: {repos, pulls: {list: async args => {
    calls.push('pulls'); assert.deepEqual(args, {...repo, base: 'main', state: 'open', per_page: 1});
    return {data: open.slice(0, 1)};
  }}}, paginate: async (method, args) => {
    calls.push('releases'); assert.equal(method, repos.listReleases); assert.deepEqual(args, {...repo, per_page: 100});
    return releases;
  }};
  return {calls, decide: (options = {}) => releaseDecision({github, context: {repo}, sha: tip, ...options})};
}

test('every open PR blocks Release builds, including drafts and bot-skipped PRs', async () => {
  for (const open of [[{number: 48}, {number: 49}], [{number: 48, draft: true}],
    [{number: 49, labels: [{name: 'bot: skip'}]}]]) {
    const s = scenario({open});
    assert.equal((await s.decide()).required, false);
    assert.deepEqual(s.calls, ['pulls']);
  }
});

test('a stale main run exits before consulting release history', async () => {
  const s = scenario({main: previous});
  assert.equal((await s.decide()).required, false);
  assert.deepEqual(s.calls, ['pulls', 'main']);
});

test('draining the queue after a docs merge or an unmerged close includes earlier Android changes', async () => {
  for (const paths of [['app/src/main/a.kt', 'README.md'], ['app/src/main/a.kt']]) {
    const s = scenario({paths});
    assert.equal((await s.decide()).required, true);
    assert.deepEqual(s.calls, ['pulls', 'main', 'releases', 'tag', 'diff']);
  }
});

test('an already published commit or a docs-only batch needs no new CI release', async () => {
  const duplicate = scenario({baseline: tip});
  assert.equal((await duplicate.decide()).required, false);
  assert.equal(duplicate.calls.includes('diff'), false);
  assert.equal((await scenario({paths: ['README.md', '.github/workflows/android.yml']}).decide()).required, false);
});

test('stable releases and incomplete CI drafts cannot suppress the first CI release', async () => {
  const s = scenario({releases: [{...published, draft: true}, {...published, prerelease: false}, {...published, assets: []}]});
  assert.equal((await s.decide()).required, true);
  assert.deepEqual(s.calls, ['pulls', 'main', 'releases']);
});

test('missing or inaccessible tags stop the decision instead of using a mutable release target', async () => {
  for (const status of [404, 503]) {
    const error = Object.assign(new Error('cannot resolve tag'), {status});
    await assert.rejects(scenario({tagError: error}).decide(), error);
  }
});

test('manual toolchain verification still waits for every PR and requires current main', async () => {
  assert.equal((await scenario({open: [{number: 50}]}).decide({verifyOnly: true})).required, false);
  assert.equal((await scenario({main: previous}).decide({verifyOnly: true})).required, false);
  assert.equal((await scenario({baseline: tip}).decide({verifyOnly: true})).required, true);
});
