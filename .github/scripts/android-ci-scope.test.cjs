const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { execFileSync } = require('node:child_process');
const { isLightweightOnly, detectScope, determineBuildScope } = require('./android-ci-scope.cjs');

test('a lightweight main push that replaces a pending app build must cover unpublished changes', async () => {
  const published = 'a'.repeat(40), before = 'b'.repeat(40), sha = 'c'.repeat(40);
  const release = { id: 119, name: 'CurrentMusic CI #119', tag_name: 'v1.3.3-ci.119',
    draft: false, prerelease: true, target_commitish: published,
    assets: [{ name: 'CurrentMusic-Android-v1.3.3-ci.119.apk' }, { name: 'SHA256SUMS.txt' }] };
  let releaseList = [release], cumulativePaths = 'app/build.gradle.kts\0README.md\0', queries = 0;
  const github = { rest: { repos: { listReleases() {} } }, paginate: async () => {
    queries++;
    if (releaseList instanceof Error) throw releaseList;
    return releaseList;
  } };
  const git = args => {
    if (args[0] === 'rev-parse') return sha;
    if (args[0] === 'cat-file') return '';
    if (args[0] === 'diff') return args[4] === published ? cumulativePaths : 'README.md\0';
    throw new Error('unexpected git command');
  };
  const run = eventName => determineBuildScope({ github, git, context: {
    eventName, sha, repo: { owner: 'owner', repo: 'app' },
    payload: { before, pull_request: { base: { sha: before } } },
  } });
  assert.equal((await run('pull_request')).required, false);
  assert.equal((await run('workflow_dispatch')).required, true);
  assert.equal(queries, 0);
  assert.equal((await run('push')).required, true);
  cumulativePaths = 'README.md\0.github/img/cover.jpg\0';
  assert.equal((await run('push')).required, false);
  for (releaseList of [[], [{ ...release, draft: true }], [{ ...release, assets: [] }],
    [{ ...release, target_commitish: 'main' }], new Error('API unavailable')]) {
    assert.equal((await run('push')).required, true);
  }
});

test('documentation and independently checked CI files may skip Android', () => {
  assert.equal(isLightweightOnly(['README.md', 'README-en.md', '.github/img/home.jpg', '.github/img/新截图.png']), true);
  assert.equal(isLightweightOnly(['README.md',
    '.github/workflows/collaboration-checks.yml', '.github/scripts/android-ci-scope.cjs',
    '.github/scripts/android-ci-scope.test.cjs']), true);
  assert.equal(isLightweightOnly(['.github/workflows/ci-distribution.yml',
    '.github/scripts/ci-release.cjs', '.github/scripts/ci-release.test.cjs']), true);
  assert.equal(isLightweightOnly(['.github/workflows/update-pr-branches.yml',
    '.github/scripts/update-pr-branches.cjs', '.github/scripts/update-pr-branches.test.cjs']), true);
  for (const files of [[], ['app/src/main/assets/legal/PRIVACY.md'], ['PRIVACY.md'],
    ['app/src/main/cpp/oboe/UPSTREAM.md'], ['app/src/main/res/raw/cover.png'],
    ['README.md', 'app/build.gradle.kts'], ['.github/workflows/android.yml'],
    ['.github/scripts/android-ci-scope.cjs', 'app/src/main/AndroidManifest.xml'],
    ['.github/img/script.sh'], ['README.md\napp/code.kt']]) {
    assert.equal(isLightweightOnly(files), false, JSON.stringify(files));
  }
});

test('Release draft automation is lightweight but app changes are not', () => {
  assert.equal(isLightweightOnly([
    '.github/workflows/release-draft.yml',
    '.github/scripts/release-draft.cjs',
    '.github/scripts/release-draft.test.cjs',
    '.github/scripts/android-ci-scope.cjs',
    '.github/scripts/android-ci-scope.test.cjs'
  ]), true);
  assert.equal(isLightweightOnly(['.github/workflows/release-draft.yml', 'app/build.gradle.kts']), false);
});

test('manual runs, missing commits and unavailable diffs retain the full build', () => {
  const sha = 'a'.repeat(40), base = 'b'.repeat(40);
  for (const options of [
    { eventName: 'workflow_dispatch', event: {}, sha },
    { eventName: 'push', event: { before: '0'.repeat(40) }, sha },
    { eventName: 'pull_request', event: {}, sha },
    { eventName: 'push', event: { before: base }, sha: '$(malicious)' },
    { eventName: 'push', event: { before: base }, sha },
  ]) {
    assert.equal(detectScope({ ...options, git() { throw Error('unavailable'); } }).required, true);
  }
  assert.equal(detectScope({ eventName: 'push', event: { before: base }, sha,
    git() { return base; } }).required, true);
});

test('exact git trees cover PR merges, pushes, deletions and renames', t => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'android-ci-scope-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const git = args => execFileSync('git', args, { cwd: dir, encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'] });
  const write = (name, contents) => {
    fs.mkdirSync(path.dirname(path.join(dir, name)), { recursive: true });
    fs.writeFileSync(path.join(dir, name), contents);
  };
  const commit = message => { git(['add', '-A']); git(['commit', '-m', message]); return git(['rev-parse', 'HEAD']).trim(); };
  git(['init', '-b', 'main']);
  git(['config', 'user.name', 'CI Test']);
  git(['config', 'user.email', 'ci@example.invalid']);
  write('README.md', 'Original');
  write('app/Native.kt', 'class Native');
  const base = commit('base');
  write('README.md', 'New README');
  write('.github/img/shot.png', 'showcase');
  const docs = commit('docs');
  const scope = (eventName, before, sha) => detectScope({ eventName, sha, git,
    event: eventName === 'push' ? { before } : { pull_request: { base: { sha: before } } } });
  assert.equal(scope('push', base, docs).required, false);
  assert.equal(scope('pull_request', base, docs).required, false);
  // Read the whole push, not only its last documentation commit.
  write('app/Native.kt', 'class Changed');
  commit('code');
  write('README.md', 'More documentation');
  const mixed = commit('docs after code');
  assert.equal(scope('push', docs, mixed).required, true);
  fs.renameSync(path.join(dir, 'app/Native.kt'), path.join(dir, 'README-native.md'));
  const moved = commit('move code into docs');
  assert.equal(scope('pull_request', mixed, moved).required, true);
  fs.unlinkSync(path.join(dir, '.github/img/shot.png'));
  const deleted = commit('remove showcase');
  assert.equal(scope('push', moved, deleted).required, false);
  assert.equal(scope('push', deleted, deleted).required, true);
  // A PR merge must include head changes without counting new base-only code.
  git(['checkout', '-b', 'docs', deleted]);
  write('README.md', 'Feature branch docs');
  commit('branch docs');
  git(['checkout', 'main']);
  write('app/Base.kt', 'class Base');
  const newerBase = commit('advance main');
  git(['merge', '--no-ff', 'docs', '-m', 'synthetic PR merge']);
  const merged = git(['rev-parse', 'HEAD']).trim();
  assert.equal(scope('pull_request', newerBase, merged).required, false);
  // GitHub checkout is shallow. Fetch only the exact missing base commit.
  const clone = path.join(dir, 'shallow');
  git(['clone', '--depth=1', pathToFileURL(dir).href, clone]);
  const shallowGit = args => execFileSync('git', args, { cwd: clone, encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'] });
  assert.throws(() => shallowGit(['cat-file', '-e', `${newerBase}^{commit}`]));
  assert.equal(detectScope({ eventName: 'pull_request', sha: merged, git: shallowGit,
    event: { pull_request: { base: { sha: newerBase } } } }).required, false);
  assert.doesNotThrow(() => shallowGit(['cat-file', '-e', `${newerBase}^{commit}`]));
});

test('PR review automation changes remain lightweight', () => {
  assert.equal(isLightweightOnly([
    '.github/workflows/pr-review-summary.yml',
    '.github/scripts/pr-review-summary.cjs',
    '.github/scripts/pr-review-summary.test.cjs',
    '.github/REVIEW_POLICY.md',
    '.github/scripts/android-ci-scope.cjs',
    '.github/scripts/android-ci-scope.test.cjs',
  ]), true);
});

test('Codex language instructions do not require APK compilation', () => {
  assert.equal(isLightweightOnly(['AGENTS.md', '.github/REVIEW_POLICY.md']), true);
});

test('PR risk classifier changes skip Android build but not independent checks', () => {
  assert.equal(isLightweightOnly([
    '.github/scripts/pr-risk.cjs',
    '.github/scripts/pr-risk.test.cjs',
    '.github/scripts/android-ci-scope.cjs',
    '.github/scripts/android-ci-scope.test.cjs',
  ]), true);
  assert.equal(isLightweightOnly(['.github/scripts/pr-risk.cjs', 'app/src/main/AndroidManifest.xml']), false);
});

test('Label palette automation is a lightweight CI change', () => {
  assert.equal(isLightweightOnly([
    '.github/workflows/label-palette.yml',
    '.github/workflows/pr-risk.yml',
    '.github/scripts/label-palette.cjs',
    '.github/scripts/label-palette.test.cjs',
    '.github/scripts/collaboration.cjs',
    '.github/scripts/android-ci-scope.cjs',
    '.github/scripts/android-ci-scope.test.cjs',
  ]), true);
  assert.equal(isLightweightOnly(['.github/scripts/label-palette.cjs','app/src/main/AndroidManifest.xml']), false);
});

test('CI Release retention rules are lightweight automation changes', () => {
  assert.equal(isLightweightOnly([
    '.github/workflows/ci-prune.yml',
    '.github/scripts/ci-prune.cjs',
    '.github/scripts/ci-prune.test.cjs',
    '.github/CI_RELEASE_POLICY.md',
    '.github/scripts/android-ci-scope.cjs',
    '.github/scripts/android-ci-scope.test.cjs'
  ]), true);
  assert.equal(isLightweightOnly(['.github/scripts/ci-prune.cjs','app/build.gradle.kts']),false);
});
