const {getQueueHead} = require('./pr-ci-queue.cjs');
const {rerunWaitingBuild} = require('./rerun-waiting-build.cjs');

// All write operations use GitHub metadata and scripts from trusted main.
async function run({github, context, core}) {
  const repo = context.repo;
  const first = await getQueueHead({github, context});
  if (!first) { core.info('No PR is waiting for Android CI'); return; }

  // GitHub calculates mergeability asynchronously. Retry reads, never writes.
  let pr;
  for (let attempt = 0; attempt < 3; attempt++) {
    pr = (await github.rest.pulls.get({...repo, pull_number: first.number})).data;
    if (pr.mergeable !== null || attempt === 2) break;
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  if (pr.state !== 'open' || pr.draft || pr.base.ref !== 'main' ||
      pr.labels.some(label => label.name === 'bot: skip') || !pr.head.repo || pr.head.sha !== first.head.sha) {
    core.info(`#${pr.number} changed while selecting the queue head`); return;
  }
  if (pr.mergeable !== true) {
    core.warning(`#${pr.number} holds the queue; resolve conflicts or move it to draft to continue`); return;
  }
  const comparison = (await github.rest.repos.compareCommitsWithBasehead({
    ...repo, basehead: `${pr.base.sha}...${pr.head.sha}`, per_page: 1,
  })).data;
  if (comparison.behind_by === 0) {
    await rerunWaitingBuild({github, context, core, pr});
    return;
  }
  if (!(comparison.behind_by > 0)) throw new Error(`Unknown ahead/behind result for #${pr.number}`);
  if (pr.head.repo.full_name !== `${repo.owner}/${repo.repo}` && !pr.maintainer_can_modify) {
    core.warning(`#${pr.number}: ask the contributor to bring their branch up to date with main`);
    return;
  }
  try {
    await github.rest.pulls.updateBranch({...repo, pull_number: pr.number, expected_head_sha: pr.head.sha});
    core.info(`#${pr.number}: requested update to main; GitHub may require approval for the new checks`);
  } catch (error) {
    if (![403, 404, 409, 422].includes(error.status)) throw error;
    core.warning(`#${pr.number}: update not performed (HTTP ${error.status}): ${error.message}`);
  }
}

module.exports = {run};
