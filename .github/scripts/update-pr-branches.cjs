function eligible(pr) {
  return pr.state === 'open' && pr.base.ref === 'main' && !pr.draft && pr.head?.repo && pr.head.sha &&
    !pr.labels.some(label => label.name === 'bot: skip');
}

// All write operations use GitHub metadata and scripts from trusted main.
async function updateBranch({github, repo, core, candidate, baseSha}) {
  // GitHub calculates mergeability asynchronously. Retry reads, never writes.
  let pr;
  for (let attempt = 0; attempt < 3; attempt++) {
    pr = (await github.rest.pulls.get({...repo, pull_number: candidate.number})).data;
    if (pr.mergeable !== null || attempt === 2) break;
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  if (!eligible(pr) || pr.head.sha !== candidate.head.sha) {
    core.info(`#${pr.number} changed while checking its branch`); return;
  }
  if (pr.mergeable !== true) {
    core.warning(`#${pr.number} cannot currently be updated cleanly`); return;
  }
  const comparison = (await github.rest.repos.compareCommitsWithBasehead({
    ...repo, basehead: `${baseSha}...${pr.head.sha}`, per_page: 1,
  })).data;
  if (comparison.behind_by === 0) return;
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

async function run({github, context, core}) {
  const repo = context.repo;
  const candidates = context.payload.pull_request ? [context.payload.pull_request] :
    await github.paginate(github.rest.pulls.list, {...repo, state: 'open', base: 'main', per_page: 100});
  const prs = candidates.filter(eligible);
  if (!prs.length) return;
  // A PR can retain its previous base SHA after another PR merges.
  const {data: main} = await github.rest.repos.getBranch({...repo, branch: 'main'});
  for (const candidate of prs) {
    await updateBranch({github, repo, core, candidate, baseSha: main.commit.sha});
  }
}

module.exports = {run};
