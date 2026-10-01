// 无额外测试依赖的源码回归检查；服务端联调和真机触控需另外执行。
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const src = async name => readFile(new URL(`../${name}`, import.meta.url), 'utf8');

test('确认后才能保存歌单顺序，失败保留确认弹窗', async () => {
  const [playlist, ui] = await Promise.all([src('src/pages/playlist.js'), src('src/ui.js')]);
  assert.match(playlist, /confirmDialog\(\{\s*title: '保存当前排序？'/);
  assert.match(playlist, /onOk: async \(\) => \{[\s\S]*?song\/order\/update/);
  assert.doesNotMatch(playlist, /await confirmDialog\(`/);
  assert.match(ui, /return false; \/\/ 异步提交失败时留在弹窗里/);
});

test('点赞只更新当前歌曲行，避免分页重复追加', async () => {
  const ui = await src('src/ui.js');
  assert.match(ui, /openLikeMenu\(songs\[i\], \(\) => \{/);
  assert.match(ui, /row\.querySelector\('\[data-act="like"\]'\)/);
  assert.doesNotMatch(ui, /openLikeMenu\(songs\[i\], \(\) => renderSongList/);
});

test('搜索和评论请求具备过期响应防护及可恢复错误', async () => {
  const [search, comments] = await Promise.all([src('src/pages/search.js'), src('src/comments.js')]);
  assert.match(search, /if \(state !== S \|\| !el\.isConnected\) return 0/);
  assert.match(search, /retrySearch/);
  assert.match(comments, /requestVersion\+\+/);
  assert.match(comments, /selectedMode !== mode/);
  assert.match(comments, /cmtRetry/);
});

test('路由提供返回与滚动位置恢复', async () => {
  const [html, main] = await Promise.all([src('index.html'), src('src/main.js')]);
  assert.match(html, /id="topBack"/);
  assert.match(main, /routeScroll\.set/);
  assert.match(main, /currentVersion !== routeVersion/);
});

test('首页一起听和推荐结果独立，并对撤销风险给出确认', async () => {
  const home = await src('src/pages/home.js');
  assert.match(home, /<h2>一起听<\/h2>/);
  assert.match(home, /title: '减少这首歌的推荐？'/);
  assert.match(home, /retryDaily/);
});

test('播放器无分页，摘录入口在「更多」内且长按可滑动取消', async () => {
  const [player, mark, marks] = await Promise.all([
    src('src/player-ui.js'), src('src/lyricmark.js'), src('src/pages/lyricmarks.js'),
  ]);
  // 播放器页已回退到非分页形态：歌词区上方不再有提示行/独立按钮
  assert.doesNotMatch(player, /pl-lyric-action/);
  assert.doesNotMatch(player, /id="plLyricMark"/);
  // 摘录入口在「更多」抽屉里，并与长按共用同一段逻辑
  assert.match(player, /id="mMark"/);
  assert.match(player, /markCurrentLyric/);
  // 长按摘录：600ms 定时 + 移动超阈值取消（避免滑歌词误摘）
  assert.match(player, /Math\.hypot\(e\.clientX - sx/);
  assert.match(player, /600/);
  // 提交逻辑收敛在 lyricmark.js：登录/绑定校验、去重提示、失败文案
  assert.match(mark, /export async function markLyric/);
  assert.match(mark, /登录后才能摘录歌词/);
  assert.match(mark, /song\/lyrics\/mark\/add/);
  assert.match(mark, /confirm: 1/);
  // 页面模块不再自带提交函数（避免两套实现）
  assert.doesNotMatch(marks, /export async function addLyricMark/);
});

test('高级搜索折叠，复制、缩放、焦点及减少动效有明确支持', async () => {
  const [html, search, css, controls] = await Promise.all([
    src('index.html'), src('src/pages/search.js'), src('src/ui-overhaul.css'), src('src/ux-controls.js'),
  ]);
  assert.doesNotMatch(html, /user-scalable=no/);
  assert.match(search, /<details class="cm-advanced-search"/);
  assert.match(search, /id="searchGo"/);
  assert.match(css, /user-select: text/);
  assert.match(css, /focus-visible/);
  assert.match(css, /prefers-reduced-motion/);
  assert.match(controls, /MutationObserver/);
});

test('全部历史浏览器 prompt/confirm 已替换为应用内表单', async () => {
  const [comments, songinfo, cloud, albums] = await Promise.all([
    src('src/comments.js'), src('src/songinfo.js'), src('src/pages/cloud.js'), src('src/pages/albums.js'),
  ]);
  for (const file of [comments, songinfo, cloud, albums]) {
    assert.doesNotMatch(file, /\bprompt\(/);
    assert.doesNotMatch(file, /\bconfirm\(/);
  }
});
