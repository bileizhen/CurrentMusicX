// Run with: node --test .github/scripts/update-pr-branches.test.cjs
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {eligible, run} = require('./update-pr-branches.cjs');

const makePr = (overrides = {}) => ({
  number: 10, state: 'open', base: {ref: 'main'}, draft: false,
  labels: [],
  head: {sha: '0123456789012345678901234567890123456789', ref: 'feature/ui',
    repo: {full_name: 'bileizhen/CurrentMusicX'}},
  user: {login: 'contributor', id: 123},
  ...overrides,
});

test('Dependabot PRs are never automatically updated with GITHUB_TOKEN', () => {
  const bot = makePr({user: {login: 'dependabot[bot]', id: 49699333},
    head: {sha: '0123456789012345678901234567890123456789',
      ref: 'dependabot/gradle/org.json-json-20260814',
      repo: {full_name: 'bileizhen/CurrentMusicX'}}});
  assert.equal(eligible(bot), false);
});

test('human PRs remain eligible for automatic update', () => {
  assert.ok(eligible(makePr()));
});

test('draft, closed, other-base and bot: skip PRs remain excluded', () => {
  assert.equal(eligible(makePr({draft:true})), false);
  assert.equal(eligible(makePr({state:'closed'})), false);
  assert.equal(eligible(makePr({base:{ref:'develop'}})), false);
  assert.equal(eligible(makePr({labels:[{name:'bot: skip'}]})), false);
});

test('run() filters out Dependabot PRs before attempting updates', async () => {
  const bot=makePr({number:166, user:{login:'dependabot[bot]',id:49699333}});
  const callbacks=[];
  const repo={owner:'bileizhen',repo:'CurrentMusicX'};
  const github={
    rest:{pulls:{list(){throw Error('only metadata marker')}},repos:{getBranch() {throw Error('getBranch should not be called')}}},
    paginate:async (_endpoint, _params)=>[bot],
  };
  await run({github,context:{repo,payload:{}},core:{info:x=>callbacks.push(x),warning:x=>callbacks.push(x)}});
  assert.equal(callbacks.length,0);
});
