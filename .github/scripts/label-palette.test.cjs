'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {COLORS,labelColor,colorChanges}=require('./label-palette.cjs');
test('All palette colors are valid GitHub hex values',()=>{
  for(const [name,color] of Object.entries(COLORS)){
    assert.match(color,/^[0-9A-F]{6}$/i,name);
  }
});
test('risk severity retains familiar green amber red',()=>{
  assert.equal(labelColor('risk: low'),'3AAE95');
  assert.equal(labelColor('risk: medium'),'D5A34F');
  assert.equal(labelColor('risk: high'),'D86870');
});
test('module and status labels have distinct readable colors',()=>{
  assert.notEqual(labelColor('module: playback'),labelColor('module: lyrics'));
  assert.notEqual(labelColor('bug'),labelColor('enhancement'));
  assert.equal(labelColor(' Risk: High '),'D86870');
});
test('unknown labels are colored deterministically',()=>{
  assert.equal(labelColor('user custom label'),labelColor('user custom label'));
  assert.match(labelColor('unknown custom'),/^[0-9A-F]{6}$/i);
});
test('only changed colors are included, without mutating labels',()=>{
  const labels=[{name:'bug',color:'D87582',description:'keep'},{name:'enhancement',color:'a2eeef',description:'keep'}];
  assert.deepEqual(colorChanges(labels),[{name:'enhancement',before:'a2eeef',after:'659ED5'}]);
  assert.equal(labels[1].description,'keep');
});
