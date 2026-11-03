const fs = require('node:fs');
const { execFileSync } = require('node:child_process');

// Keep the allowlist outside runtime assets: legal Markdown and app images can
// affect the APK and must still exercise the normal Android checks.
function isLightweightOnly(paths) {
  const checkedAutomation = new Set([
    '.github/workflows/android.yml',
    '.github/workflows/collaboration-checks.yml',
    '.github/scripts/android-ci-scope.cjs',
    '.github/scripts/android-ci-scope.test.cjs',
    '.github/workflows/ci-distribution.yml',
    '.github/scripts/ci-release.cjs',
    '.github/scripts/ci-release.test.cjs',
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

if (require.main === module) {
  const result = detectScope({ eventName: process.env.GITHUB_EVENT_NAME,
    event: JSON.parse(fs.readFileSync(process.env.GITHUB_EVENT_PATH, 'utf8')),
    sha: process.env.GITHUB_SHA });
  fs.appendFileSync(process.env.GITHUB_OUTPUT, `required=${result.required}\n`);
  console.log(`Android build required: ${result.required}. ${result.reason}`);
}

module.exports = { isLightweightOnly, detectScope };
