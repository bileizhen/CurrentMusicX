'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {summarize,markdown}=require('./pr-review-summary.cjs');
test('high risk always asks for human review',()=>assert.equal(summarize(['.github/workflows/android.yml'],[]).needsHuman,true));
test('unresolved review threads are counted',()=>{const x=summarize(['README.md'],[{isResolved:false,isOutdated:false,comments:{nodes:[{body:'P1: bug'}]}}]);assert.equal(x.open,1);assert.equal(x.high,1);assert.match(markdown(x),/人工复核/);});
test('outdated or resolved threads excluded',()=>assert.equal(summarize(['README.md'],[{isResolved:false,isOutdated:true},{isResolved:true,isOutdated:false}]).open,0));
