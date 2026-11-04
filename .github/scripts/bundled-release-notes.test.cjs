const {test} = require('node:test');
const assert = require('node:assert/strict');
const {generate} = require('./bundled-release-notes.cjs');

test('bundled notes bind the exact CI version and compare against the immutable build commit', async () => {
  const sha = 'a'.repeat(40);
  let range;
  const result = await generate({version: '1.3.3', number: 119,
    context: {repo: {owner:'example',repo:'app'}, sha},
    github: {rest:{repos:{
      getLatestRelease: async () => ({data:{tag_name:'v1.3.3'}}),
      compareCommitsWithBasehead: async args => {
        range = args.basehead;
        return {data:{total_commits:101, commits:[{sha,commit:{message:'fix: 修复更新说明\n\nextra body'}}]}};
      },
    }}},
  });
  assert.equal(result.version, '1.3.3-ci.119');
  assert.equal(range, `v1.3.3...${sha}`);
  assert.match(result.notes, /fix: 修复更新说明/);
  assert.match(result.notes, /仅列出前 100 项/);
  assert.match(result.notes, /查看完整改动/);
  assert.doesNotMatch(result.notes, /extra body/);
});

test('invalid build identity fails before querying releases', async () => {
  await assert.rejects(generate({version:'1.3.3',number:0,context:{sha:'a'.repeat(40)}}), /Invalid build identity/);
  await assert.rejects(generate({version:'1.3.3',number:119,context:{sha:'main'}}), /Invalid build identity/);
});
