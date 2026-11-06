const fs = require('node:fs');
function validateRange(tag, previous, version) {
  const pattern = /^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;
  if (!pattern.test(tag) || !pattern.test(previous)) throw new Error('必须使用稳定版本 tag：vX.Y.Z');
  if (tag === previous || tag !== `v${version}`) throw new Error('版本范围或 app versionName 不匹配');
  const a = tag.slice(1).split('.').map(Number), b = previous.slice(1).split('.').map(Number);
  const different = a.findIndex((part, i) => part !== b[i]);
  if (different < 0 || a[different] < b[different]) throw new Error('本次版本必须大于上次版本');
}
async function prepare({ github, context, core }) {
  const repo = context.repo;
  const tag = process.env.RELEASE_TAG;
  const previous = process.env.PREVIOUS_TAG;
  // Validate before using refs in URLs; git refs are never interpolated into shell.
  validateRange(tag, previous, tag?.slice(1));
  const target = (await github.rest.repos.getCommit({...repo,ref:tag})).data.sha;
  const base = (await github.rest.repos.getCommit({...repo,ref:previous})).data.sha;
  const ancestry = (await github.rest.repos.compareCommitsWithBasehead({...repo,basehead:`${target}...main`})).data;
  if (!['ahead','identical'].includes(ancestry.status)) throw new Error('目标 tag 不在 main 历史上');
  const range = (await github.rest.repos.compareCommitsWithBasehead({...repo,basehead:`${base}...${target}`})).data;
  if (range.status !== 'ahead') throw new Error('上次 tag 必须是本次 tag 的祖先且包含新提交');
  const config = (await github.rest.repos.getContent({...repo,path:'app/build.gradle.kts',ref:target})).data;
  const version = Buffer.from(config.content,'base64').toString('utf8').match(/versionName\s*=\s*"([^"]+)"/)?.[1];
  validateRange(tag, previous, version);
  const previousRelease = (await github.rest.repos.getReleaseByTag({...repo,tag:previous})).data;
  if (previousRelease.draft || previousRelease.prerelease) throw new Error('上次 tag 必须对应已发布的正式版本');
  const releases = await github.paginate(github.rest.repos.listReleases,{...repo,per_page:100});
  if (releases.some(release => release.tag_name === tag)) throw new Error('该版本已有 Release，不覆盖现有说明与附件');
  const runs = await github.paginate(github.rest.actions.listWorkflowRuns,{...repo,workflow_id:'android.yml',head_sha:target,per_page:100});
  const latest = runs.filter(run => run.head_branch === 'main' && ['push','workflow_dispatch'].includes(run.event))
    .sort((a,b) => b.id - a.id)[0];
  if (latest?.conclusion !== 'success' || latest.status !== 'completed' ||
      latest.head_sha !== target) throw new Error('目标提交最新一次 main Android CI 尚未通过');
  // Require the actual Release APK and lint; a lightweight
  // skipped build must never authorize a stable release draft.
  const jobs = await github.paginate(github.rest.actions.listJobsForWorkflowRun,
    {...repo, run_id:latest.id, filter:'latest', per_page:100});
  const requiredSteps = ['Build and verify unsigned CI release', 'Upload unsigned CI APK'];
  if (!jobs.some(job => job.name === 'build' && job.conclusion === 'success' &&
      requiredSteps.every(name => job.steps?.some(step =>
        step.name === name && step.conclusion === 'success'))))
    throw new Error('目标提交缺少完整 Release 构建或 Lint 验证');
  const artifacts = await github.paginate(github.rest.actions.listWorkflowRunArtifacts,
    {...repo, run_id:latest.id, per_page:100});
  if (!artifacts.some(artifact => artifact.name === 'ci-unsigned' &&
      !artifact.expired && artifact.size_in_bytes > 0))
    throw new Error('目标提交的 CI Release 产物不存在或已过期');
  const notes = (await github.rest.repos.generateReleaseNotes({ ...repo,tag_name:tag,previous_tag_name:previous,target_commitish:target,configuration_file_path:'.github/release.yml' })).data;
  // Preserve GitHub's author, PR, new contributor and full changelog links verbatim.
  const body = `## 更新说明\n\n${process.env.RELEASE_SUMMARY.trim()}\n\n${notes.body}\n\n## Installation & Verification\n\n- 此草稿基于 ${target}，Android CI 的 Release 编译、Lint 及 CI 产物已核验（运行 #${latest.run_number}）。\n- 待上传同一提交构建的正式签名 APK 与 SHA-256 校验文件。\n- 真机验收、签名和下载校验尚待维护者填写；CI 不代表真机验收。\n`;
  fs.writeFileSync('release-draft.txt',body);
  core.setOutput('target',target);
  core.setOutput('tag',tag);
}
async function create({github,context,core}) {
  const repo=context.repo;
  const tag=process.env.RELEASE_TAG;
  // Recheck before publication; never overwrite a previously created release.
  const releases=await github.paginate(github.rest.repos.listReleases,{...repo,per_page:100});
  if (releases.some(release=>release.tag_name===tag)) throw new Error('该版本已存在，停止创建');
  const release=await github.rest.repos.createRelease({...repo,tag_name:tag,target_commitish:process.env.RELEASE_TARGET,name:`CurrentMusic ${tag}`,body:fs.readFileSync('release-draft.txt','utf8'),draft:true,prerelease:false});
  core.info(`Created draft ${release.data.html_url}`);
}
module.exports={validateRange,prepare,create};
