'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {parsePublishedCi,planCiPrune,trustedUpstream,prune}=require('./ci-prune.cjs');

function ci(build,{version='1.3.2',draft=false,assets=true}={}){
  const tag='v'+version+'-ci.'+build;
  return {
    id:100000+build,tag_name:tag,name:'CurrentMusic CI #'+build,
    prerelease:true,draft,
    assets:assets?[{name:'CurrentMusic-Android-'+tag+'.apk'},{name:'SHA256SUMS.txt'}]:[],
  };
}
const stable=(tag='v1.3.2')=>({id:47,tag_name:tag,name:'CurrentMusicX '+tag,
  prerelease:false,draft:false,assets:[{name:'release.apk'}]});
test('CI tag must represent a complete published prerelease',()=>{
  assert.equal(parsePublishedCi(ci(109)).build,109);
  assert.equal(parsePublishedCi(stable()),null);
  assert.equal(parsePublishedCi(ci(110,{draft:true})),null);
  assert.equal(parsePublishedCi(ci(108,{assets:false})),null);
  assert.equal(parsePublishedCi({...ci(109),name:'Someone else release'}),null);
  assert.equal(parsePublishedCi({...ci(109),tag_name:'v1.3.2'}),null);
  assert.equal(parsePublishedCi({...ci(109),id:-1}),null);
});
test('retain CI #109, even when GitHub lists it after earlier releases',()=>{
  const items=[stable(),ci(94),ci(85),ci(84),ci(81),ci(78),ci(59),ci(109),
    stable('v1.3.1'),stable('v1.3.0')];
  const plan=planCiPrune(items);
  assert.equal(plan.keep.tag,'v1.3.2-ci.109');
  assert.deepEqual(plan.remove.map(x=>x.build),[94,85,84,81,78,59]);
  assert.equal(plan.skipped,3);
});
test('new draft cannot replace latest complete published CI',()=>{
  const plan=planCiPrune([ci(110,{draft:true}),ci(109),ci(85),stable()]);
  assert.equal(plan.keep.build,109);
  assert.deepEqual(plan.remove.map(x=>x.build),[85]);
  assert.equal(plan.skipped,2);
});
test('unrecognized releases are preserved, even if marked prerelease',()=>{
  const other={...ci(88),tag_name:'nightly-88',name:'Experimental nightly'};
  const plan=planCiPrune([other,stable()]);
  assert.equal(plan.keep,null);
  assert.deepEqual(plan.remove,[]);
});
test('duplicate CI run number fails closed',()=>{
  assert.throws(()=>planCiPrune([ci(109),ci(109,{version:'1.3.1'})]),/Ambiguous/);
});
test('only completed trusted main Publish CI build triggers cleanup',()=>{
  const run={status:'completed',conclusion:'success',head_branch:'main',event:'workflow_run',
    head_repository:{full_name:'bileizhen/CurrentMusicX'},path:'.github/workflows/ci-distribution.yml'};
  assert.equal(trustedUpstream(run,'bileizhen/CurrentMusicX'),true);
  assert.equal(trustedUpstream({...run,event:'workflow_dispatch'},'bileizhen/CurrentMusicX'),true);
  for(const patch of [
    {status:'in_progress'},{conclusion:'failure'},{head_branch:'feature'},
    {event:'pull_request'},{path:'.github/workflows/android.yml'},
    {head_repository:{full_name:'outsider/fork'}}
  ]) assert.equal(trustedUpstream({...run,...patch},'bileizhen/CurrentMusicX'),false);
});
function harness(releases,{failReleaseId,failTag}={}){
  const calls=[],github={
    rest:{
      repos:{
        listReleases:()=>{},
        deleteRelease:async params=>{
          calls.push(['release',params.release_id]);
          if(params.release_id===failReleaseId)throw Object.assign(new Error('denied'),{status:403});
        }
      },
      git:{deleteRef:async params=>{
        calls.push(['tag',params.ref]);
        if(params.ref===failTag)throw Object.assign(new Error('denied'),{status:403});
      }}
    },
    paginate:async()=>releases,
  };
  const info=[],core={info:s=>info.push(s),summary:{
    addHeading(){return this},addRaw(){return this},async write(){}
  }};
  const context={repo:{owner:'bileizhen',repo:'CurrentMusicX'},eventName:'push'};
  return {github,core,context,calls,info};
}
test('cleanup deletes only old CI releases, then their exact tags',async()=>{
  const args=harness([ci(94),stable(),ci(109),ci(85)]);
  await prune(args);
  assert.deepEqual(args.calls,[
    ['release',100094],['tag','tags/v1.3.2-ci.94'],
    ['release',100085],['tag','tags/v1.3.2-ci.85'],
  ]);
});
test('failed release deletion never deletes its tag',async()=>{
  const args=harness([ci(109),ci(94)],{failReleaseId:100094});
  await assert.rejects(prune(args),/cleanup failed/);
  assert.deepEqual(args.calls,[['release',100094]]);
});
test('failed tag deletion is reported after successful release removal',async()=>{
  const args=harness([ci(109),ci(94)],{failTag:'tags/v1.3.2-ci.94'});
  await assert.rejects(prune(args),/tag deletion failed/);
  assert.deepEqual(args.calls,[['release',100094],['tag','tags/v1.3.2-ci.94']]);
});
test('automatic cleanup requires the publish job to succeed in the triggering attempt',async()=>{
  for (const conclusion of ['skipped','success']) {
    const releases=[ci(109),ci(94)], args=harness(releases);
    const run={id:10,run_attempt:2,status:'completed',conclusion:'success',
      head_branch:'main',head_repository:{full_name:'bileizhen/CurrentMusicX'},
      event:'workflow_run',path:'.github/workflows/ci-distribution.yml'};
    args.context.eventName='workflow_run';
    args.context.payload={workflow_run:run};
    args.github.rest.actions={listJobsForWorkflowRunAttempt(){}};
    const reads=[];
    args.github.paginate=async(method,params)=>{
      if (method===args.github.rest.actions.listJobsForWorkflowRunAttempt) {
        reads.push('jobs');
        assert.equal(params.run_id,10);
        assert.equal(params.attempt_number,2);
        return [{name:'gate',conclusion:'success'},{name:'publish',conclusion}];
      }
      assert.equal(method,args.github.rest.repos.listReleases);
      reads.push('releases');
      return releases;
    };
    await prune(args);
    assert.deepEqual(reads,conclusion==='success'?['jobs','releases']:['jobs']);
    assert.deepEqual(args.calls,conclusion==='success'?
      [['release',100094],['tag','tags/v1.3.2-ci.94']]:[]);
  }
});
