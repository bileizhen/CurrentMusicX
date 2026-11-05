const {planCiPrune} = require('./ci-prune.cjs');
const {onlyLightweightChangesSince} = require('./ci-release.cjs');

async function releaseDecision({github, context, sha, verifyOnly = false}) {
  const repo = context.repo;
  const {data: open} = await github.rest.pulls.list({...repo, state: 'open', base: 'main', per_page: 1});
  if (open.length) return {required: false, reason: `PR #${open[0].number} is still open against main`};

  const {data: main} = await github.rest.repos.getBranch({...repo, branch: 'main'});
  if (main.commit.sha !== sha) return {required: false, reason: 'A newer main commit supersedes this run'};

  // Maintainers can explicitly verify the toolchain after the queue has drained.
  // The publisher always checks the actual diff before exposing a new CI version.
  if (verifyOnly) return {required: true, reason: 'Manual toolchain verification on current main'};

  const releases = await github.paginate(github.rest.repos.listReleases, {...repo, per_page: 100});
  const {keep} = planCiPrune(releases);
  if (!keep) return {required: true, reason: 'No complete CI release exists yet'};

  // Compare the entire batch, including changes merged before the final PR closed.
  // Resolve the tag itself: target_commitish on a release can be a moving branch.
  const {data: released} = await github.rest.repos.getCommit({...repo, ref: `tags/${keep.tag}`});
  if (released.sha === sha) return {required: false, reason: `${keep.tag} already contains this commit`};
  const {data: comparison} = await github.rest.repos.compareCommitsWithBasehead({
    ...repo, basehead: `${released.sha}...${sha}`, per_page: 1,
  });
  if (onlyLightweightChangesSince(comparison, released.sha)) {
    return {required: false, reason: `Only documentation or CI files changed since ${keep.tag}`};
  }
  return {required: true, reason: `main needs an Android build after ${keep.tag}`};
}

module.exports = {releaseDecision};
