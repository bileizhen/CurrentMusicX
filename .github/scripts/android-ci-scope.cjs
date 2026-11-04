const { execFileSync } = require('node:child_process');
const { planCiPrune } = require('./ci-prune.cjs');

// Keep the allowlist outside runtime assets: legal Markdown and app images can
// affect the APK and must still exercise the normal Android checks.
function isLightweightOnly(paths) {
  const checkedAutomation = new Set([
    '.github/workflows/collaboration-checks.yml',
    '.github/scripts/android-ci-scope.cjs',
    '.github/scripts/android-ci-scope.test.cjs',
    '.github/workflows/ci-distribution.yml',
    '.github/scripts/ci-release.cjs',
    '.github/scripts/ci-release.test.cjs',
    '.github/workflows/ci-prune.yml',
    '.github/scripts/ci-prune.cjs',
    '.github/scripts/ci-prune.test.cjs',
    '.github/CI_RELEASE_POLICY.md',
    '.github/workflows/release-draft.yml',
    '.github/scripts/release-draft.cjs',
    '.github/scripts/release-draft.test.cjs',
    '.github/workflows/update-pr-branches.yml',
    '.github/scripts/update-pr-branches.cjs',
    '.github/scripts/update-pr-branches.test.cjs',
    '.github/workflows/pr-review-summary.yml',
    '.github/scripts/pr-review-summary.cjs',
    '.github/scripts/pr-review-summary.test.cjs',
    '.github/scripts/pr-risk.cjs',
    '.github/scripts/pr-risk.test.cjs',
    '.github/REVIEW_POLICY.md',
    '.github/workflows/label-palette.yml',
    '.github/workflows/pr-risk.yml',
    '.github/scripts/label-palette.cjs',
    '.github/scripts/label-palette.test.cjs',
    '.github/scripts/collaboration.cjs',
    '.github/workflows/codex-review-bridge.yml',
    '.github/scripts/codex-review-bridge.cjs',
    '.github/scripts/codex-review-bridge.test.cjs',
    'AGENTS.md',
  ]);
  return paths.length > 0 && paths.every(path =>
    checkedAutomation.has(path) ||
    /^README(?:-[^/]+)?\.md$/.test(path) ||
    /^\.github\/img\/.+\.(?:svg|png|jpe?g|webp|gif)$/.test(path));
}

function detectScope({ eventName, event, sha, git = args =>
  execFileSync('git', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }) }) {
  const full = reason => ({ required: true, reason });
  if (!['pull_request', 'push'].includes(eventName)) return full('Explicit or unrecognized event');
  const base = eventName === 'pull_request' ? event.pull_request?.base?.sha : event.before;
  const validSha = value => typeof value === 'string' && /^[0-9a-f]{40}$/.test(value) && !/^0+$/.test(value);
  if (!validSha(base) || !validSha(sha)) return full('Missing comparison commit');

  try {
    if (git(['rev-parse', 'HEAD']).trim() !== sha) return full('Checkout does not match the event');
    try { git(['cat-file', '-e', `${base}^{commit}`]); }
    catch { git(['fetch', '--no-tags', '--depth=1', 'origin', base]); }
    // PR checkout is GitHub's merge commit; compare its tree with the event's
    // exact base. Do not query a mutable PR file list or truncate the diff.
    // Disable rename detection so moving app code into a README is not skipped.
    const paths = git(['diff', '--name-only', '--no-renames', '-z', base, sha, '--'])
      .split('\0').filter(Boolean);
    return isLightweightOnly(paths)
      ? { required: false, reason: `Documentation / showcase / checked CI automation (${paths.length} files)` }
      : full('Changes include Android inputs, automation, or an empty diff');
  } catch {
    return full('Could not establish the complete diff');
  }
}

async function determineBuildScope({ github, context, git }) {
  const { eventName, payload: event, sha } = context;
  const scope = detectScope({ eventName, event, sha, git });
  if (eventName !== 'push' || scope.required) return scope;

  // A lightweight push can cancel the main build that contained the app change.
  // Only skip if the latest published CI APK already covers all Android inputs.
  try {
    const releases = await github.paginate(github.rest.repos.listReleases, { ...context.repo, per_page: 100 });
    const { keep } = planCiPrune(releases);
    const baseline = keep && releases.find(release => release.id === keep.id).target_commitish;
    if (baseline) return detectScope({ eventName, event: { before: baseline }, sha, git });
  } catch {
    // A missing, ambiguous or unreadable release must not suppress compilation.
  }
  return { required: true, reason: 'No reliable published CI build to compare' };
}

module.exports = { isLightweightOnly, detectScope, determineBuildScope };
