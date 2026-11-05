const { test } = require('node:test');
const assert = require('node:assert/strict');
const { isLightweightOnly } = require('./android-ci-scope.cjs');

test('documentation and independently checked CI files may skip Android', () => {
  assert.equal(isLightweightOnly(['README.md', 'README-en.md', '.github/img/home.jpg', '.github/img/新截图.png']), true);
  assert.equal(isLightweightOnly(['README.md',
    '.github/workflows/android.yml', '.github/release.yml', '.github/workflows/collaboration-checks.yml', '.github/scripts/android-ci-scope.cjs',
    '.github/scripts/android-ci-scope.test.cjs']), true);
  assert.equal(isLightweightOnly(['.github/workflows/ci-distribution.yml',
    '.github/scripts/ci-release.cjs', '.github/scripts/ci-release.test.cjs']), true);
  assert.equal(isLightweightOnly(['.github/workflows/update-pr-branches.yml',
    '.github/scripts/update-pr-branches.cjs', '.github/scripts/update-pr-branches.test.cjs']), true);
  for (const files of [[], ['app/src/main/assets/legal/PRIVACY.md'], ['PRIVACY.md'],
    ['app/src/main/cpp/oboe/UPSTREAM.md'], ['app/src/main/res/raw/cover.png'],
    ['README.md', 'app/build.gradle.kts'],
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
