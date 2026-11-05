const {test} = require('node:test');
const assert = require('node:assert/strict');
const {validateMetadata,trustedRun,hasSuccessfulAndroidBuild,onlyLightweightChangesSince,publish} = require('./ci-release.cjs');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path'), crypto = require('node:crypto');
const metadata = () => ({applicationId:'com.bileizhen.currentmusic',elements:[{versionName:'1.3.2-ci.42',versionCode:3500042,outputFile:'app-release-unsigned.apk'}]});
test('CI metadata requires exact universal package and monotonic code slot', () => {
  assert.deepEqual(validateMetadata(metadata(),42),{tag:'v1.3.2-ci.42',file:'CurrentMusic-Android-v1.3.2-ci.42.apk'});
  for (const number of [0, -1, 100000, NaN, 43]) assert.throws(()=>validateMetadata(metadata(),number));
  const data=metadata(); data.elements[0].versionCode=35;
  assert.throws(()=>validateMetadata(data,42));
  data.elements[0].versionCode=3500042; data.applicationId+='.debug';
  assert.throws(()=>validateMetadata(data,42));
});
test('reject split APKs and path escapes', () => {
  const data=metadata(); data.elements.push({...data.elements[0]});
  assert.throws(()=>validateMetadata(data,42));
  data.elements.pop(); data.elements[0].outputFile='../other.apk';
  assert.throws(()=>validateMetadata(data,42));
  data.elements[0].outputFile='app-release.apk'; data.elements[0].filters=[{filterType:'ABI',value:'arm64-v8a'}];
  assert.throws(()=>validateMetadata(data,42));
});
test('publishing requires successful main Android CI in the same repository', () => {
  const run={conclusion:'success',status:'completed',head_branch:'main',event:'push',head_repository:{full_name:'owner/app'},head_sha:'a'.repeat(40),id:1,run_number:42,path:'.github/workflows/android.yml'};
  assert.equal(trustedRun(run,'owner/app'),true);
  for (const patch of [{event:'pull_request'},{conclusion:'failure'},{status:'in_progress'},{head_branch:'feature'},
    {head_repository:{full_name:'fork/app'}},{path:'.github/workflows/other.yml'},{head_sha:'invalid'}])
    assert.equal(trustedRun({...run,...patch},'owner/app'),false);
});

test('documentation-only upstream success does not build or publish an APK', () => {
  const job = {name:'build',conclusion:'success',steps:[
    {name:'Build and verify unsigned CI release',conclusion:'success'},
    {name:'Upload unsigned CI APK',conclusion:'success'}]};
  assert.equal(hasSuccessfulAndroidBuild([job]), true);
  for (const conclusion of ['skipped','failure','cancelled']) {
    for (const index of [0,1]) assert.equal(hasSuccessfulAndroidBuild([
      {...job,steps:job.steps.map((step,i)=>i===index ? {...step,conclusion} : step)}]), false);
  }
  assert.equal(hasSuccessfulAndroidBuild([]), false);
  assert.equal(hasSuccessfulAndroidBuild([{...job,conclusion:'failure'}]), false);
});

async function publicationScenario(options, check) {
  const previousCwd=process.cwd(), previousTag=process.env.CI_TAG;
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'currentmusic-ci-test-'));
  const run={conclusion:'success',status:'completed',head_branch:'main',event:'push',head_repository:{full_name:'owner/app'},head_sha:'a'.repeat(40),id:1,run_number:42,run_attempt:1,path:'.github/workflows/android.yml'};
  const calls=[];
  const api={actions:{getWorkflowRun:async()=>({data:{...run,...options.liveRun}})},repos:{
    getReleaseByTag:async()=>{if(options.existing)return {data:{}};throw Object.assign(new Error('missing'),{status:404});},
    getLatestRelease:async()=>({data:{tag_name:'v1.3.2'}}),
    generateReleaseNotes:async()=>({data:{body:'* Feature by @author in #10'}}),
    createRelease:async(args)=>{calls.push(['create',args]);return {data:{id:1,upload_url:'upload',html_url:'page'}};},
    uploadReleaseAsset:async(args)=>{calls.push(['upload',args.name]);return {data:{size:args.data.length,digest:options.badDigest ? '' : `sha256:${crypto.createHash('sha256').update(args.data).digest('hex')}`}};},
    getRelease:async({release_id})=>{
      assert.equal(release_id,1);
      return {data:{draft:true,tag_name:'v1.3.2-ci.42',target_commitish:run.head_sha,...options.draft}};
    },
    deleteRelease:async({release_id})=>{calls.push(['discard',release_id]);},
    updateRelease:async(args)=>{calls.push(['publish',args]);return {data:{}};},
  }};
  try {
    process.chdir(directory); process.env.CI_TAG='v1.3.2-ci.42'; fs.mkdirSync('ci-dist');
    fs.writeFileSync('ci-dist/output-metadata.json',JSON.stringify(metadata()));
    fs.writeFileSync('ci-dist/CurrentMusic-Android-v1.3.2-ci.42.apk',Buffer.from([80,75,3,4]));
    const releaseDecision = async ({sha}) => {
      assert.equal(sha, run.head_sha);
      const required = options.decisions?.shift() ?? true;
      if (required instanceof Error) throw required;
      return {required,reason:'batch status'};
    };
    const context={repo:{owner:'owner',repo:'app'},payload:options.dispatched ? {} : {workflow_run:run}};
    await check(()=>publish({github:{rest:api},context,core:{info:()=>{}},releaseDecision,
      ...(options.dispatched ? {upstreamRun:run} : {})}),calls);
  } finally {
    process.chdir(previousCwd);
    if(previousTag===undefined) delete process.env.CI_TAG; else process.env.CI_TAG=previousTag;
    fs.rmSync(directory,{recursive:true,force:true});
  }
}
test('publication uploads verified assets before exposing the prerelease and preserves PR notes',async()=>{
  await publicationScenario({},async(run,calls)=>{
    await run(); assert.deepEqual(calls.map(c=>c[0]),['create','upload','upload','publish']);
    assert.equal(calls[0][1].draft,true); assert.match(calls[0][1].body,/by @author in #10/);
    assert.equal(calls.at(-1)[1].make_latest,'false'); assert.equal(calls.at(-1)[1].prerelease,true);
  });
});
test('bad uploaded digest leaves a draft rather than exposing a broken update',async()=>{
  await publicationScenario({badDigest:true},async(run,calls)=>{
    await assert.rejects(run,/verification failed/); assert.equal(calls.some(c=>c[0]==='publish'),false);
  });
});

test('explicit publication uses the validated upstream snapshot and rejects changes before release creation',async()=>{
  await publicationScenario({dispatched:true},async(run,calls)=>{
    await run(); assert.deepEqual(calls.map(c=>c[0]),['create','upload','upload','publish']);
  });
  for (const liveRun of [{run_attempt:2},{head_sha:'b'.repeat(40)},{conclusion:'failure'}]) {
    await publicationScenario({dispatched:true,liveRun},async(run,calls)=>{
      await assert.rejects(run,/CI run changed/); assert.equal(calls.length,0);
    });
  }
});
test('reruns and superseded main builds never overwrite published assets',async()=>{
  for(const options of [{existing:true},{decisions:[false]}]) await publicationScenario(options,async(run,calls)=>{
    await run(); assert.equal(calls.length,0);
  });
});

test('a PR opening during uploads or a moved main tip withdraws only this draft',async()=>{
  await publicationScenario({decisions:[true,false]},async(run,calls)=>{
    await run(); assert.deepEqual(calls.map(c=>c[0]),['create','upload','upload','discard']);
    assert.equal(calls.at(-1)[1],1);
  });
});
test('a final batch check error leaves the run failed and frees the draft tag for retry',async()=>{
  await publicationScenario({decisions:[true,Error('API failure')]},async(run,calls)=>{
    await assert.rejects(run,/API failure/);
    assert.deepEqual(calls.map(c=>c[0]),['create','upload','upload','discard']);
  });
});
test('an externally modified release is never deleted on deferral',async()=>{
  for (const draft of [{draft:false},{tag_name:'v2.0.0'},{target_commitish:'manual'}]) {
    await publicationScenario({decisions:[true,false],draft},async(run,calls)=>{
      await run(); assert.deepEqual(calls.map(c=>c[0]),['create','upload','upload']);
    });
  }
});
test('cumulative Android change detection requires a complete, verifiable comparison',()=>{
  const sha='a'.repeat(40), doc={filename:'README.md',status:'modified'};
  const base={status:'ahead',base_commit:{sha},merge_base_commit:{sha},files:[doc]};
  assert.equal(onlyLightweightChangesSince(base,sha),true);
  for (const overrides of [
    {status:'diverged'}, {base_commit:{sha:'x'}}, {merge_base_commit:{sha:'x'}},
    {files:undefined}, {files:[]}, {files:Array(300).fill(doc)},
    {files:[{...doc,status:'renamed'}]},
    {files:[{...doc,status:'renamed',previous_filename:'app/build.gradle.kts'}]},
    {files:[{...doc,status:'unknown'}]},
  ]) assert.equal(onlyLightweightChangesSince({...base,...overrides},sha),false);
});
