const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

function validateMetadata(metadata, number) {
  if (!Number.isInteger(number) || number < 1 || number > 99999) throw new Error('Invalid CI build number');
  const items = metadata.elements;
  if (metadata.applicationId !== 'com.bileizhen.currentmusic' || items?.length !== 1) throw new Error('Expected one universal CurrentMusic APK');
  const item = items[0];
  if (item.filters?.length) throw new Error('Expected a universal APK without ABI or density filters');
  if (!/^\d+\.\d+\.\d+-ci\.\d+$/.test(item.versionName) || !item.versionName.endsWith(`-ci.${number}`) ||
      !Number.isInteger(item.versionCode) || item.versionCode < 100000 || item.versionCode % 100000 !== number ||
      path.basename(item.outputFile) !== item.outputFile || !item.outputFile.endsWith('.apk')) throw new Error('CI version or APK path mismatch');
  return {tag: `v${item.versionName}`, file: `CurrentMusic-Android-v${item.versionName}.apk`};
}

function trustedRun(run, repo) {
  return run?.conclusion === 'success' && run.status === 'completed' && run.head_branch === 'main' &&
    ['push', 'workflow_dispatch'].includes(run.event) && run.head_repository?.full_name === repo &&
    /^[a-f0-9]{40}$/.test(run.head_sha) && Number.isSafeInteger(run.id) && Number.isInteger(run.run_number) &&
    run.path === '.github/workflows/android.yml';
}

function hasSuccessfulAndroidBuild(jobs) {
  return jobs.some(job => job.name === 'build' && job.conclusion === 'success' &&
    job.steps?.some(step => step.name === 'Build debug APK and run unit tests' && step.conclusion === 'success'));
}

function prepare() {
  const directory = 'app/build/outputs/apk/release';
  const metadata = JSON.parse(fs.readFileSync(`${directory}/output-metadata.json`, 'utf8'));
  const result = validateMetadata(metadata, Number(process.env.CI_BUILD_NUMBER));
  fs.mkdirSync('ci-dist', {recursive:true});
  fs.copyFileSync(path.join(directory, metadata.elements[0].outputFile), `ci-dist/${result.file}`);
  fs.writeFileSync('ci-dist/output-metadata.json', JSON.stringify(metadata));
  fs.appendFileSync(process.env.GITHUB_OUTPUT, `tag=${result.tag}\n`);
}

async function publish({github, context, core}) {
  const repo = context.repo, fullName = `${repo.owner}/${repo.repo}`;
  const eventRun = context.payload.workflow_run;
  if (!trustedRun(eventRun, fullName)) throw new Error('Only successful main Android CI may publish');
  const run = (await github.rest.actions.getWorkflowRun({...repo, run_id:eventRun.id})).data;
  if (!trustedRun(run, fullName) || run.head_sha !== eventRun.head_sha || run.run_attempt !== eventRun.run_attempt) throw new Error('CI run changed');
  const main = (await github.rest.repos.getBranch({...repo,branch:'main'})).data.commit.sha;
  if (main !== run.head_sha) { core.info('A newer main commit exists; skip this superseded build'); return; }
  const metadata = JSON.parse(fs.readFileSync('ci-dist/output-metadata.json', 'utf8'));
  const {tag,file} = validateMetadata(metadata, run.run_number);
  if (tag !== process.env.CI_TAG) throw new Error('Unexpected CI tag');
  try {
    await github.rest.repos.getReleaseByTag({...repo,tag});
    core.info('This immutable CI release already exists; do not replace its files'); return;
  } catch (error) { if (error.status !== 404) throw error; }
  const apk = fs.readFileSync(`ci-dist/${file}`);
  if (apk.length < 4 || apk.length > 128*1024*1024 || apk.readUInt32LE(0) !== 0x04034b50) throw new Error('Invalid APK');
  const sha256 = crypto.createHash('sha256').update(apk).digest('hex');
  const previous = (await github.rest.repos.getLatestRelease(repo)).data;
  const notes = (await github.rest.repos.generateReleaseNotes({...repo,tag_name:tag,target_commitish:run.head_sha,
    previous_tag_name:previous.tag_name,configuration_file_path:'.github/release.yml'})).data.body;
  const body = `## 更新说明\n\n主线 CI 构建 #${run.run_number}，包含自上次正式版 ${previous.tag_name} 以来的改动。\n\n${notes}\n\n## Installation & Verification\n\n提交 ${run.head_sha}。[Android CI](https://github.com/${fullName}/actions/runs/${run.id}) 已通过；本次签名分发前已完成 Release 构建、单元测试和 Lint。未进行真机验收，不代表正式版稳定性。\n\n使用与正式版相同的签名，可保留数据覆盖安装。切回正式版需等待后续更高版本，不支持降级覆盖。\n\nSHA-256: \`${sha256}\`\n`;
  // Publish only after all assets are present. A partial upload remains a draft and is never offered by the app.
  const release = (await github.rest.repos.createRelease({...repo,tag_name:tag,target_commitish:run.head_sha,name:`CurrentMusic CI #${run.run_number}`,body,draft:true,prerelease:true,make_latest:'false'})).data;
  const upload = async (name, data, type) => github.rest.repos.uploadReleaseAsset({url:release.upload_url,name,data,headers:{'content-type':type,'content-length':data.length}});
  const asset = (await upload(file,apk,'application/vnd.android.package-archive')).data;
  if (asset.size !== apk.length || asset.digest !== `sha256:${sha256}`) throw new Error('GitHub asset verification failed; leave draft unpublished');
  await upload('SHA256SUMS.txt',Buffer.from(`${sha256}  ${file}\n`),'text/plain');
  await github.rest.repos.updateRelease({...repo,release_id:release.id,draft:false,prerelease:true,make_latest:'false'});
  core.info(`Published ${release.html_url}`);
}

if (require.main === module && process.argv[2] === 'prepare') prepare();
module.exports = {validateMetadata,trustedRun,hasSuccessfulAndroidBuild,publish};
