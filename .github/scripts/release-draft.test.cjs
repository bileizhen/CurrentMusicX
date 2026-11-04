const { test } = require('node:test');
const assert = require('node:assert/strict');
const { validateRange, prepare, create } = require('./release-draft.cjs');
const fs = require('node:fs');
test('release range requires increasing stable versions matching app',()=>{
  assert.doesNotThrow(()=>validateRange('v1.3.3','v1.3.2','1.3.3'));
  assert.doesNotThrow(()=>validateRange('v1.10.0','v1.9.9','1.10.0'));
  for (const [tag,previous,version] of [
    ['v1.3.2','v1.3.2','1.3.2'],['v1.3.1','v1.3.2','1.3.1'],
    ['v1.3.3','v1.3.2','1.3.2'],['v1.3.3-beta','v1.3.2','1.3.3-beta'],
    ['$(echo evil)','v1.3.2','1.3.3'],['v1.03.3','v1.3.2','1.03.3'],
  ]) assert.throws(()=>validateRange(tag,previous,version));
});
function fake(options = {}) {
  const calls = [];
  const github = { rest: { repos: {
    getCommit: async ({ref}) => ({data:{sha:ref === 'v1.3.3' ? 'target' : 'base'}}),
    compareCommitsWithBasehead: async () => ({data:{status:options.ancestry ?? 'ahead'}}),
    getContent: async () => ({data:{content:Buffer.from('versionName = "1.3.3"').toString('base64')}}),
    getReleaseByTag: async () => ({data:{draft:false,prerelease:false}}),
    listReleases: 'releases',
    generateReleaseNotes: async args => {calls.push(args);return {data:{body:'## Bug Fixes\n* Fix by @author in https://github.com/example/repo/pull/2\n\n**Full Changelog**: compare'}};},
    createRelease: async args => {calls.push(args);return {data:{html_url:'draft'}};},
  }, actions:{listWorkflowRuns:'runs',listJobsForWorkflowRun:'jobs',listWorkflowRunArtifacts:'artifacts'} },
    paginate: async endpoint => endpoint === 'releases' ? (options.releases ?? []) :
      endpoint === 'runs' ? (options.runs ?? [{id:1,run_number:15,status:'completed',head_sha:'target',conclusion:'success',head_branch:'main',event:'push'}]) :
      endpoint === 'jobs' ? (options.jobs ?? [{name:'build',conclusion:'success',steps:[
        {name:'Build and verify unsigned CI release',conclusion:'success'},
        {name:'Upload unsigned CI APK',conclusion:'success'}]}]) :
      (options.artifacts ?? [{name:'ci-unsigned',expired:false,size_in_bytes:123}]) };
  return {github,context:{repo:{owner:'example',repo:'repo'}},core:{setOutput:()=>{},info:()=>{}},calls};
}
async function withEnv(task) {
  const keys = ['RELEASE_TAG','PREVIOUS_TAG','RELEASE_SUMMARY','RELEASE_TARGET'];
  const values = keys.map(key=>process.env[key]);
  Object.assign(process.env,{RELEASE_TAG:'v1.3.3',PREVIOUS_TAG:'v1.3.2',RELEASE_SUMMARY:'修复播放问题',RELEASE_TARGET:'target'});
  try {await task();} finally { keys.forEach((key,i)=>values[i] === undefined ? delete process.env[key] : process.env[key]=values[i]); }
}
test('release notes preserve native attribution and explicit tag range', async t => {
  let body;
  t.mock.method(fs,'writeFileSync',(_path,value)=>body=value);
  await withEnv(async()=>{
    const client=fake(); await prepare(client);
    assert.equal(client.calls[0].previous_tag_name,'v1.3.2');
    assert.equal(client.calls[0].configuration_file_path,'.github/release.yml');
    assert.match(body,/by @author in https:\/\/github.com\/example\/repo\/pull\/2/);
    assert.match(body,/Full Changelog/);
    assert.match(body,/尚待/);
  });
});
test('release preparation refuses existing release, failed CI and divergent history',async()=>{
  await withEnv(async()=>{
    for(const options of [{releases:[{tag_name:'v1.3.3'}]},{runs:[]},{ancestry:'diverged'},
      {runs:[{id:1,status:'completed',head_sha:'target',conclusion:'success',head_branch:'main',event:'push'},{id:2,conclusion:'failure',head_branch:'main',event:'push'}]},
      {jobs:[]},{artifacts:[]},{artifacts:[{name:'ci-unsigned',expired:true,size_in_bytes:123}]},
      {runs:[{id:1,status:'completed',head_sha:'wrong',conclusion:'success',head_branch:'main',event:'push'}]}]) {
      const client=fake(options); await assert.rejects(()=>prepare(client)); assert.equal(client.calls.length,0);
    }
  });
});
test('publication only creates a draft and refuses overwrite even on late races',async t=>{
  t.mock.method(fs,'readFileSync',()=> 'notes');
  await withEnv(async()=>{
    const client=fake(); await create(client);
    assert.equal(client.calls[0].draft,true);
    const existing=fake({releases:[{tag_name:'v1.3.3'}]});
    await assert.rejects(()=>create(existing)); assert.equal(existing.calls.length,0);
  });
});
