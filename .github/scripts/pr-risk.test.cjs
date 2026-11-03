'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {classify}=require('./pr-risk.cjs');
const prefix='app/src/main/java/io/github/currencortex/music/';
test('documentation is low',()=>assert.equal(classify(['README.md','.github/img/home.png']).level,'low'));
test('ordinary app UI is medium',()=>assert.equal(classify([prefix+'feature/player/PlayerScreen.kt']).level,'medium'));
test('core audio output is high',()=>assert.equal(classify([prefix+'core/media/AudioTrack.kt']).level,'high'));
test('production manifest high, test manifest medium',()=>{
  assert.equal(classify(['app/src/main/AndroidManifest.xml']).level,'high');
  assert.equal(classify(['app/src/androidTest/AndroidManifest.xml']).level,'medium');
});
test('release workflow high',()=>assert.equal(classify(['.github/workflows/ci-distribution.yml']).level,'high'));
test('unknown automation medium',()=>assert.equal(classify(['.github/workflows/pr-review-summary.yml']).level,'medium'));
test('missing or huge changes fail closed',()=>{
  assert.equal(classify([]).level,'high');
  assert.equal(classify(Array(101).fill('README.md')).level,'high');
});
test('renamed sensitive source remains high',()=>assert.equal(classify([{filename:'docs/old.md',previous_filename:'.github/workflows/android.yml',status:'renamed'}]).level,'high'));
test('PR #19 Hi-Res quality tier medium',()=>assert.equal(classify(['README.md',prefix+'core/media/AudioQuality.kt',prefix+'data/lx/LxSource.kt',prefix+'feature/player/AudioQualitySheet.kt']).level,'medium'));
test('PR #20 landscape player medium',()=>assert.equal(classify(['README.md',prefix+'feature/player/PlayerImmersiveCover.kt',prefix+'feature/player/PlayerScreen.kt']).level,'medium'));
test('PR #21 local folder scan medium',()=>assert.equal(classify(['app/src/androidTest/AndroidManifest.xml',prefix+'feature/library/LocalFolderScanner.kt',prefix+'feature/library/LocalMusicScreen.kt']).level,'medium'));
