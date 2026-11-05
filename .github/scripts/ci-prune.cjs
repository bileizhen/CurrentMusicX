'use strict';

// GitHub Release retention policy: retain one newest, successfully published CI APK.
// Stable releases, drafts, and unrelated prereleases must never be touched.
const REPO = 'bileizhen/CurrentMusicX';
const CI_TAG = /^v\d+\.\d+\.\d+-ci\.([1-9]\d*)$/;

function parsePublishedCi(release) {
  if (!release || release.draft !== false || release.prerelease !== true ||
      typeof release.tag_name !== 'string') return null;
  const match=CI_TAG.exec(release.tag_name);
  if (!match) return null;
  const build=Number(match[1]);
  if (!Number.isSafeInteger(build) || build < 1 ||
      !Number.isSafeInteger(release.id) || release.id < 1 ||
      release.name !== 'CurrentMusic CI #' + build) return null;

  // Published CI releases should be complete, not half-uploaded drafts.
  const assets=(release.assets || []).map(a => a?.name);
  const apk='CurrentMusic-Android-' + release.tag_name + '.apk';
  if (!assets.includes(apk) || !assets.includes('SHA256SUMS.txt')) return null;
  return {id:release.id,tag:release.tag_name,build};
}

function planCiPrune(releases) {
  if (!Array.isArray(releases)) throw new Error('Expected complete paginated release list');
  const ci=releases.map(parsePublishedCi).filter(Boolean);
  if (!ci.length) return {keep:null,remove:[],skipped:releases.length};

  // Build numbers are unique within a repo's Android CI workflow.
  if (new Set(ci.map(r=>r.build)).size!==ci.length)
    throw new Error('Ambiguous duplicate CI run numbers: refusing to delete releases');

  ci.sort((a,b)=>b.build-a.build);
  return {keep:ci[0],remove:ci.slice(1),skipped:releases.length-ci.length};
}

function trustedUpstream(run,repo) {
  return run?.status==='completed' && run.conclusion==='success' &&
    run.head_branch==='main' && run.head_repository?.full_name===repo &&
    run.path==='.github/workflows/ci-distribution.yml' && ['workflow_run','workflow_dispatch'].includes(run.event);
}

async function prune({github,context,core}) {
  const repo=context.repo;
  const fullName=repo.owner+'/'+repo.repo;
  if (fullName!==REPO) throw new Error('Unexpected repository');
  if (context.eventName==='workflow_run') {
    const run=context.payload.workflow_run;
    if (!trustedUpstream(run,fullName)) throw new Error('Ignoring untrusted upstream publication run');
    const jobs=await github.paginate(github.rest.actions.listJobsForWorkflowRunAttempt,
      {...repo,run_id:run.id,attempt_number:run.run_attempt,per_page:100});
    if (!jobs.some(job=>job.name==='publish' && job.conclusion==='success')) {
      core.info('Upstream publish job did not succeed; skipping CI release cleanup');
      return;
    }
  }
  if (!['workflow_run','push','workflow_dispatch'].includes(context.eventName))
    throw new Error('Unsupported trigger');

  const releases=await github.paginate(github.rest.repos.listReleases,{...repo,per_page:100});
  const plan=planCiPrune(releases);
  if (!plan.keep) {
    core.info('No verified published CI release found; nothing will be removed');
    return plan;
  }

  core.info('Keeping latest CI '+plan.keep.tag+' (build #'+plan.keep.build+')');
  const errors=[];
  for (const old of plan.remove) {
    try {
      await github.rest.repos.deleteRelease({...repo,release_id:old.id});
      core.info('Deleted old CI release '+old.tag);
    } catch (error) {
      errors.push(old.tag+': cannot delete release (HTTP '+(error.status||'unknown')+')');
      continue; // Never delete the tag if its Release deletion failed.
    }
    try {
      await github.rest.git.deleteRef({...repo,ref:'tags/'+old.tag});
      core.info('Deleted old CI tag '+old.tag);
    } catch (error) {
      if (error.status!==404) errors.push(old.tag+': tag deletion failed (HTTP '+(error.status||'unknown')+')');
    }
  }
  await core.summary.addHeading('CurrentMusicX CI Release 保留策略')
    .addRaw('保留：'+plan.keep.tag+'；清理的旧 CI Release：'+plan.remove.length+
      '；保留 '+plan.skipped+' 个不匹配 CI 模式的 Release（正式版、草稿或其他预发布）。')
    .write();
  if (errors.length) throw new Error('Some old CI release cleanup failed: '+errors.join('; '));
  return plan;
}

module.exports={parsePublishedCi,planCiPrune,trustedUpstream,prune};
