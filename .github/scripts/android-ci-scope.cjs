// Keep the allowlist outside runtime assets: legal Markdown and app images can
// affect the APK and must still count toward a CI release.
function isInternalCiPath(path) {
  return typeof path === 'string' && (/^\.github\/(?:scripts|workflows)\//.test(path) ||
    ['.github/release.yml', '.github/CI_RELEASE_POLICY.md', '.github/REVIEW_POLICY.md', 'AGENTS.md'].includes(path));
}

function isLightweightOnly(paths) {
  return paths.length > 0 && paths.every(path => isInternalCiPath(path) ||
    /^README(?:-[^/]+)?\.md$/.test(path) ||
    /^\.github\/img\/.+\.(?:svg|png|jpe?g|webp|gif)$/.test(path));
}

module.exports = { isInternalCiPath, isLightweightOnly };
