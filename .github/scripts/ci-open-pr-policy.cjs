// Only genuine, self-contained Dependabot dependency PRs may be ignored by
// the CI release queue. Fail closed for unexpected files, identities or API errors.
'use strict';

const DEPENDABOT_ID = 49699333;
const MAX_FILES = 40;

function isOfficialDependabotPr(pr, fullName) {
  const ref = pr?.head?.ref;
  return pr?.user?.login === 'dependabot[bot]' &&
    pr.user.id === DEPENDABOT_ID &&
    pr.head?.repo?.full_name === fullName &&
    typeof ref === 'string' &&
    (ref.startsWith('dependabot/gradle/') ||
      ref.startsWith('dependabot/github_actions/'));
}

function isExpectedDependencyFile(ref, filename) {
  if (typeof filename !== 'string') return false;
  if (ref.startsWith('dependabot/github_actions/')) {
    return /^\.github\/workflows\/[\w.-]+\.ya?ml$/.test(filename);
  }
  if (ref.startsWith('dependabot/gradle/')) {
    return /(^|\/)(build|settings)\.gradle(\.kts)?$/.test(filename) ||
      /(^|\/)gradle\.lockfile$/.test(filename) ||
      /^gradle\/[^/]+\.versions\.toml$/.test(filename) ||
      /^gradle\/wrapper\/gradle-wrapper\.properties$/.test(filename) ||
      /^gradle\/verification-metadata\.xml$/.test(filename);
  }
  return false;
}

async function isIgnorableDependabotPr({github, repo, pr}) {
  const fullName = repo.owner + '/' + repo.repo;
  if (!isOfficialDependabotPr(pr, fullName)) return false;
  const files = await github.paginate(github.rest.pulls.listFiles, {
    ...repo, pull_number: pr.number, per_page: 100,
  });
  return Array.isArray(files) && files.length > 0 && files.length <= MAX_FILES &&
    files.every(file => file?.status === 'modified' &&
      isExpectedDependencyFile(pr.head.ref, file.filename));
}

async function findBlockingOpenPr({github, repo}) {
  // Always inspect every page: the first PR might be Dependabot while a
  // blocking human/feature PR is on another page.
  const open = await github.paginate(github.rest.pulls.list, {
    ...repo, state: 'open', base: 'main', per_page: 100,
  });
  if (!Array.isArray(open)) throw new Error('Unexpected GitHub PR listing');
  for (const pr of open) {
    // Drafts are not ready for review and must not delay releases from main.
    // This affects only the release queue, not PR checks or merge protection.
    if (pr.draft === true) continue;
    if (!(await isIgnorableDependabotPr({github, repo, pr}))) return pr;
  }
  return null;
}

module.exports = {
  isOfficialDependabotPr, isExpectedDependencyFile,
  isIgnorableDependabotPr, findBlockingOpenPr,
};
