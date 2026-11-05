async function getQueueHead({github, context}) {
  const prs = await github.paginate(github.rest.pulls.list, {
    ...context.repo, state: 'open', base: 'main', per_page: 100,
  });
  // A failing or conflicting PR keeps its place until a maintainer defers it.
  const eligible = prs.filter(pr => !pr.draft && pr.head?.repo && pr.head.sha &&
    !pr.labels.some(label => label.name === 'bot: skip'));
  return eligible.sort((left, right) => left.number - right.number)[0] ?? null;
}

async function inspectTurn({github, context, pr}) {
  const first = await getQueueHead({github, context});
  if (!first) return {ready: false, reason: 'No eligible PR'};
  if (first.number !== pr?.number) return {ready: false, reason: `Waiting for PR #${first.number}`};
  if (first.head.sha !== pr.head?.sha || !pr.base?.sha || first.base.sha !== pr.base.sha) {
    return {ready: false, reason: 'Event no longer matches the live PR head and base'};
  }

  const {data: main} = await github.rest.repos.getBranch({...context.repo, branch: 'main'});
  if (main.commit.sha !== pr.base.sha) return {ready: false, reason: 'Event predates the latest main'};
  const {data: diff} = await github.rest.repos.compareCommitsWithBasehead({
    ...context.repo, basehead: `${main.commit.sha}...${pr.head.sha}`, per_page: 1,
  });
  if (diff.behind_by !== 0) return {ready: false, reason: 'PR head must include the latest main'};
  return {ready: true, reason: `PR #${pr.number} is first and includes main`};
}

module.exports = {getQueueHead, inspectTurn};
