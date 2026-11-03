'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {requestBody,requestSha,reviewState,eligible,plan,run,EXPIRY_MS}=require('./codex-review-bridge.cjs');

const sha='a'.repeat(40);
const oldSha='b'.repeat(40);
function pr(fork=true) {
  return {state:'open',draft:false,number:26,
    base:{ref:'main',repo:{full_name:'bileizhen/CurrentMusicX'}},
    head:{sha,repo:{full_name:fork?'someone/CurrentMusicX':'bileizhen/CurrentMusicX'}}};
}
function pending(when=Date.now(),s=sha,author='github-actions[bot]') {
  return {id:12,user:{login:author},body:requestBody(s),created_at:new Date(when).toISOString()};
}
function completed(s=sha) {
  return {user:{login:'chatgpt-codex-connector[bot]'},
    body:'<!-- codex-review-state:v1 '+JSON.stringify({
      code_review:{commit_sha:s,status:'completed',has_any_findings:false}
    })+' -->'};
}
test('request includes exact codex mention and safe identifying marker',()=>{
  const x=pending();
  assert.equal(x.body.startsWith('@codex review\n'),true);
  assert.equal(requestSha(x),sha);
  assert.equal(requestSha({...x,user:{login:'contributor'}}),null);
  assert.equal(requestSha({...x,body:'@codex review'}),null);
});
test('only forked ready PRs request reviews',()=>{
  assert.equal(eligible(pr()),true);
  assert.equal(eligible(pr(false)),false);
  assert.equal(eligible({...pr(),draft:true}),false);
  assert.equal(eligible({...pr(),base:{ref:'other',repo:{full_name:'bileizhen/CurrentMusicX'}}}),false);
});
test('same commit does not request duplicate while pending',()=>{
  assert.deepEqual(plan(pr(),[pending()],{allowCreate:true}).toDelete,[]);
  assert.equal(plan(pr(),[pending()],{allowCreate:true}).create,false);
});
test('Codex completion skips request and cleans only matching temporary comment',()=>{
  const x=plan(pr(),[pending(),completed(),pending(Date.now(),oldSha)],{allowCreate:true});
  assert.deepEqual(x.toDelete,[12]);
  assert.equal(x.create,false);
  assert.equal(x.completed,true);
});
test('cleanup happens at six hours, without automatically retrying same SHA',()=>{
  const now=Date.now();
  const x=plan(pr(),[pending(now-EXPIRY_MS)],{allowCreate:true,now});
  assert.deepEqual(x.toDelete,[12]);
  assert.equal(x.create,false);
  const recent=plan(pr(),[pending(now-EXPIRY_MS+1000)],{allowCreate:false,now});
  assert.deepEqual(recent.toDelete,[]);
});
test('PR closed removes own request, never removes human text',()=>{
  const p={...pr(),state:'closed'};
  const human={...pending(),id:99,user:{login:'someone'}};
  assert.deepEqual(plan(p,[pending(),human],{allowCreate:false}).toDelete,[12]);
});
test('ignore spoofed summary and invalid state',()=>{
  assert.equal(reviewState({...completed(),user:{login:'attacker'}}),null);
  assert.equal(reviewState({...completed(),body:'<!-- codex-review-state:v1 bad-json -->'}),null);
  assert.equal(plan(pr(),[pending(),{...completed(),user:{login:'attacker'}}],{allowCreate:true}).create,false);
});
test('new fork PR is requested, Codex completion deletes request',async()=>{
  const comments=[];const calls=[];
  const github={
    rest:{
      pulls:{get:async()=>({data:pr()})},
      issues:{
        listComments:async()=>({data:comments}),
        createComment:async args=>{calls.push('post');comments.push({...pending(),body:args.body});},
        deleteComment:async args=>{calls.push('delete:'+args.comment_id);}
      }
    },
    paginate:async fn=>{if(fn===github.rest.issues.listComments)return comments;return [];}
  };
  const core={info(){}};
  const context={
    repo:{owner:'bileizhen',repo:'CurrentMusicX'},
    eventName:'pull_request_target',
    payload:{action:'opened',pull_request:{number:26}}
  };
  await run({github,core,context});
  assert.deepEqual(calls,['post']);
  await run({github,core,context});
  assert.deepEqual(calls,['post']);
  comments.push(completed());
  await run({github,core,context});
  assert.deepEqual(calls,['post','delete:12']);
});
