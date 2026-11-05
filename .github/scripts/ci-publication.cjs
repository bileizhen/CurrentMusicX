const {trustedRun, hasSuccessfulAndroidBuild} = require('./ci-release.cjs');

// Dispatch may arrive before the notifying Android job has finished. Only a
// completed, successful run may cross into the secret-bearing signing job.
async function resolveUpstream({github, context, sleep = ms => new Promise(resolve => setTimeout(resolve, ms))}) {
  if (context.ref !== 'refs/heads/main') throw new Error('Publication must run on main');
  const eventRun = context.payload.workflow_run;
  const dispatched = context.eventName === 'workflow_dispatch';
  if (!dispatched && context.eventName !== 'workflow_run') throw new Error('Unsupported publication trigger');
  const input = context.payload.inputs || {};
  const id = dispatched ? Number(input.run_id) : eventRun?.id;
  const attempt = dispatched ? Number(input.run_attempt) : eventRun?.run_attempt;
  if (!Number.isSafeInteger(id) || id < 1 || !Number.isSafeInteger(attempt) || attempt < 1) {
    throw new Error('Invalid upstream run or attempt');
  }
  const fullName = `${context.repo.owner}/${context.repo.repo}`;
  for (let poll = 0; poll <= 12; poll++) {
    const {data: run} = await github.rest.actions.getWorkflowRun({...context.repo, run_id: id});
    // Check identity before waiting; never wait for PRs, forks or other workflows.
    if (!trustedRun({...run, status: 'completed', conclusion: 'success'}, fullName) ||
        run.id !== id || run.run_attempt !== attempt || (!dispatched && run.head_sha !== eventRun.head_sha)) {
      throw new Error('Untrusted or changed upstream run');
    }
    if (run.status === 'completed') {
      if (!trustedRun(run, fullName)) throw new Error('Upstream Android CI did not succeed');
      const jobs = await github.paginate(github.rest.actions.listJobsForWorkflowRunAttempt,
        {...context.repo, run_id: id, attempt_number: attempt, per_page: 100});
      return hasSuccessfulAndroidBuild(jobs) ? run : null;
    }
    if (!dispatched || poll === 12) throw new Error('Upstream Android CI has not completed');
    await sleep(10000);
  }
}

module.exports = {resolveUpstream};
