// Re-run the original, already approved pull_request workflow. Its check stays
// associated with the PR and fork code never runs in the controller's context.
async function rerunWaitingBuild({github, context, core, pr}) {
  const repo = context.repo;
  const actions = github.rest.actions;
  const runs = await github.paginate(actions.listWorkflowRuns, {
    ...repo, workflow_id: 'android.yml', event: 'pull_request', head_sha: pr.head.sha, per_page: 100,
  });
  const candidates = runs.filter(run =>
    run.event === 'pull_request' && run.head_sha === pr.head.sha &&
    run.head_branch === pr.head.ref && run.head_repository?.id === pr.head.repo.id &&
    run.repository?.id === pr.base.repo.id &&
    (!run.pull_requests?.length || run.pull_requests.some(p => p.number === pr.number)));
  // A new attempt awaiting approval, in progress, or failed supersedes an
  // older deferred run. Never look past it to re-run an older green attempt.
  const latest = candidates.sort((a, b) => b.run_number - a.run_number)[0];
  if (!latest || latest.status !== 'completed' || latest.conclusion !== 'success') return;
  // Completion can reconcile a race with queue advancement, but a rerun must
  // never trigger another rerun of itself just by reporting the same marker.
  if (context.payload?.workflow_run?.id === latest.id && latest.run_attempt > 1) return;

  const jobs = await github.paginate(actions.listJobsForWorkflowRunAttempt, {
    ...repo, run_id: latest.id, attempt_number: latest.run_attempt, per_page: 100,
  });
  // run.pull_requests can be empty on forks or reflect a newer base. The
  // marker captures the native event's immutable base and head at run time.
  const marker = `waiting-for-pr-turn / ${pr.number} / ${pr.base.sha} / ${pr.head.sha}`;
  if (!jobs.some(job => job.name === 'scope' && job.conclusion === 'success') ||
      !jobs.some(job => job.name === marker && job.conclusion === 'success') ||
      jobs.some(job => job.name === 'build')) return;

  try {
    await actions.reRunWorkflow({...repo, run_id: latest.id});
    core.info(`#${pr.number}: requested Android CI on its original PR run ${latest.id}`);
  } catch (error) {
    if (![403, 409, 422].includes(error.status)) throw error;
    // Only suppress an API refusal if another caller actually started a rerun.
    const {data: current} = await actions.getWorkflowRun({...repo, run_id: latest.id});
    if (current.run_attempt > latest.run_attempt || current.status !== 'completed') return;
    throw error;
  }
}

module.exports = {rerunWaitingBuild};
