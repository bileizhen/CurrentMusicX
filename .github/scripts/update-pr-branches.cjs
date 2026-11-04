// Only inspect GitHub metadata. Never check out, run, or merge locally a PR head.
function skipReason(pr, repo) {
  if (pr.state !== 'open' || pr.merged || pr.draft) return 'closed, merged or draft';
  if (pr.base?.ref !== 'main' || pr.base.repo?.full_name !== repo) return 'different base';
  if (!pr.head?.repo || !pr.head.sha) return 'head unavailable';
  if (pr.labels?.some(label => label.name === 'bot: skip')) return 'bot: skip';
  if (pr.head.repo.full_name !== repo && !pr.maintainer_can_modify) return 'fork does not permit maintainer updates';
  if (pr.mergeable !== true) return 'conflicting or mergeability not yet known';
  return null;
}

async function run({github, context, core}) {
  const repo = context.repo;
  const fullName = `${repo.owner}/${repo.repo}`;
  const prs = context.payload.pull_request
    ? [{number: context.payload.pull_request.number}]
    : await github.paginate(github.rest.pulls.list, {...repo, state: 'open', base: 'main', per_page: 100});
  for (const item of prs) {
    let pr;
    // GitHub calculates mergeability asynchronously; retry reads, not mutations.
    for (let attempt = 0; attempt < 3; attempt++) {
      pr = (await github.rest.pulls.get({...repo, pull_number: item.number})).data;
      if (pr.mergeable !== null) break;
      if (attempt < 2) await new Promise(resolve => setTimeout(resolve, 1000));
    }
    const reason = skipReason(pr, fullName);
    if (reason) { core.info(`#${item.number}: skipped (${reason})`); continue; }
    try {
      const comparison = (await github.rest.repos.compareCommitsWithBasehead({
        ...repo, basehead: `${pr.base.ref}...${pr.head.sha}`, per_page: 1,
      })).data;
      if (!(comparison.behind_by > 0)) {
        core.info(`#${item.number}: already includes main`);
        continue;
      }
      // Optimistic concurrency: never update a different head than the inspected one.
      await github.rest.pulls.updateBranch({...repo, pull_number: item.number, expected_head_sha: pr.head.sha});
      core.info(`#${item.number}: requested merge of main; new CI may require maintainer approval`);
    } catch (error) {
      // Conflicts, branch protection, revoked fork consent and concurrent pushes require human follow-up.
      if (![403, 404, 409, 422].includes(error.status)) throw error;
      core.warning(`#${item.number}: update not performed (HTTP ${error.status}): ${error.message}`);
    }
  }
}

module.exports = {skipReason, run};
