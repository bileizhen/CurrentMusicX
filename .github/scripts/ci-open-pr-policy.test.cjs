// Run with: node --test .github/scripts/ci-open-pr-policy.test.cjs
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
  isOfficialDependabotPr, isExpectedDependencyFile,
  isIgnorableDependabotPr, findBlockingOpenPr,
} = require('./ci-open-pr-policy.cjs');

const repo = {owner: 'bileizhen', repo: 'CurrentMusicX'};
function pr(number, changes = {}) {
  return {
    number,
    user: {login: 'dependabot[bot]', id: 49699333},
    head: {ref: 'dependabot/gradle/org.json-json-20260814',
      repo: {full_name: 'bileizhen/CurrentMusicX'}},
    ...changes,
  };
}

function api(open, filesByPr = {}) {
  const list = Symbol('list');
  const listFiles = Symbol('listFiles');
  const calls = [];
  return {
    calls,
    github: {
      rest: {pulls: {list, listFiles}},
      async paginate(endpoint, params) {
        calls.push({endpoint, params});
        if (endpoint === list) {
          assert.equal(params.base, 'main');
          assert.equal(params.state, 'open');
          assert.equal(params.per_page, 100);
          return open;
        }
        if (endpoint === listFiles) {
          assert.equal(params.per_page, 100);
          return filesByPr[params.pull_number] || [];
        }
        throw new Error('Unexpected endpoint');
      },
    },
  };
}
const modified = filename => ({filename, status: 'modified'});

test('official Gradle Dependabot with dependency-only edits is nonblocking', async () => {
  const p = pr(171);
  const {github} = api([p], {171: [modified('app/build.gradle.kts')]});
  assert.equal(await findBlockingOpenPr({github, repo}), null);
});

test('official GitHub Actions dependency update is nonblocking', async () => {
  const p = pr(166, {
    head: {ref: 'dependabot/github_actions/actions/setup-node-7',
      repo: {full_name: 'bileizhen/CurrentMusicX'}},
  });
  const {github} = api([p], {166: [
    modified('.github/workflows/ci-prune.yml'),
    modified('.github/workflows/collaboration-checks.yml'),
  ]});
  assert.equal(await isIgnorableDependabotPr({github, repo, pr: p}), true);
});

test('draft feature PR does not block the main release queue', async () => {
  const draft = pr(179, {draft: true, user: {login: 'contributor', id: 1234}});
  const {github, calls} = api([draft]);
  assert.equal(await findBlockingOpenPr({github, repo}), null);
  assert.equal(calls.length, 1); // draft is ignored without inspecting its files
});

test('ready-for-review feature PR blocks while drafts stay ignored', async () => {
  const draft = pr(179, {draft: true, user: {login: 'contributor', id: 1234}});
  const ready = pr(180, {draft: false, user: {login: 'contributor', id: 1234}});
  const {github} = api([draft, ready]);
  assert.equal((await findBlockingOpenPr({github, repo})).number, 180);
});

test('a converted draft resumes blocking when marked ready', async () => {
  const ready = pr(179, {draft: false, user: {login: 'contributor', id: 1234}});
  const {github} = api([ready]);
  assert.equal((await findBlockingOpenPr({github, repo})).number, 179);
});

test('a human PR blocks even when a Dependabot PR appears first', async () => {
  const human = pr(173, {user: {login: 'contributor', id: 1234}});
  const {github, calls} = api([pr(171), human], {
    171: [modified('app/build.gradle.kts')],
  });
  assert.equal((await findBlockingOpenPr({github, repo})).number, 173);
  assert.equal(calls.length, 2);
});

test('untrusted identity, fork or branch cannot bypass the gate', () => {
  assert.equal(isOfficialDependabotPr(pr(1, {user: {login: 'dependabot[bot]', id: 4}}),
    'bileizhen/CurrentMusicX'), false);
  assert.equal(isOfficialDependabotPr(pr(1, {
    head: {ref: 'dependabot/gradle/update', repo: {full_name: 'fork/CurrentMusicX'}},
  }), 'bileizhen/CurrentMusicX'), false);
  assert.equal(isOfficialDependabotPr(pr(1, {
    head: {ref: 'feature/change', repo: {full_name: 'bileizhen/CurrentMusicX'}},
  }), 'bileizhen/CurrentMusicX'), false);
});

test('mixed or unexpected files, additions and deletions remain blocking', async () => {
  for (const files of [
    [modified('app/build.gradle.kts'), modified('app/src/main/java/Unexpected.kt')],
    [{filename: 'app/build.gradle.kts', status: 'added'}],
    [{filename: 'app/build.gradle.kts', status: 'removed'}],
    [],
    Array.from({length: 41}, () => modified('app/build.gradle.kts')),
  ]) {
    const {github} = api([pr(171)], {171: files});
    assert.equal((await findBlockingOpenPr({github, repo})).number, 171);
  }
});

test('Gradle dependency paths cannot be confused with arbitrary sources', () => {
  assert.equal(isExpectedDependencyFile('dependabot/gradle/example', 'build.gradle.kts'), true);
  assert.equal(isExpectedDependencyFile('dependabot/gradle/example', 'gradle/wrapper/gradle-wrapper.properties'), true);
  assert.equal(isExpectedDependencyFile('dependabot/gradle/example', '.github/workflows/android.yml'), false);
  assert.equal(isExpectedDependencyFile('dependabot/github_actions/example', '.github/workflows/android.yml'), true);
  assert.equal(isExpectedDependencyFile('dependabot/github_actions/example', 'app/build.gradle.kts'), false);
});

test('GitHub file API errors do not silently allow a release', async () => {
  const {github} = api([pr(171)]);
  github.paginate = async (endpoint) => {
    if (endpoint === github.rest.pulls.list) return [pr(171)];
    throw new Error('API unavailable');
  };
  await assert.rejects(findBlockingOpenPr({github, repo}), /API unavailable/);
});

test('no open PRs leaves the release queue unblocked', async () => {
  const {github} = api([]);
  assert.equal(await findBlockingOpenPr({github, repo}), null);
});
