'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {classify}=require('./pr-risk.cjs');
test('docs are low risk',()=>assert.equal(classify(['README.md','.github/img/home.png']).level,'low'));
test('app changes medium',()=>assert.equal(classify(['app/src/main/java/example/ui/Home.kt']).level,'medium'));
test('audio changes high',()=>assert.equal(classify(['app/src/main/java/example/audio/Engine.kt']).level,'high'));
test('workflows high',()=>assert.equal(classify(['.github/workflows/android.yml']).level,'high'));
test('missing or huge diffs fail closed',()=>{assert.equal(classify([]).level,'high');assert.equal(classify(Array(101).fill('README.md')).level,'high');});
