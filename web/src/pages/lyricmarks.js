// 歌词摘录 / 我的歌词本（阶段二）
// 读：/song/lyrics/mark/user/page（T1）  写：/song/lyrics/mark/add、/del（T2，需 confirm=1）
// 摘录是网易云账号维度数据，必须用用户自己的 cookie，未绑定则整页提示绑定。
import { api, auth } from '../api.js';
import { esc, toast } from '../ui.js';

export async function render(el) {
  el.innerHTML = `
    <div class="cm-sec-head"><h2>我的歌词本</h2><span class="cm-sec-sub">来自你的网易云账号</span></div>
    <div id="lmBody"><div class="cm-loading"><mdui-circular-progress></mdui-circular-progress></div></div>`;

  const body = el.querySelector('#lmBody');
  if (!auth.token) {
    body.innerHTML = '<div class="cm-empty">登录后可查看歌词摘录</div>';
    return;
  }

  let bound = false;
  try { bound = !!(await api.bindStatus()).bound; } catch { /* 忽略 */ }
  if (!bound) {
    body.innerHTML = '<div class="cm-empty">需先绑定网易云账号（我的 → 网易云账号）</div>';
    return;
  }

  async function load() {
    body.innerHTML = '<div class="cm-loading"><mdui-circular-progress></mdui-circular-progress></div>';
    let list = [];
    try {
      const d = await api.ncm('/song/lyrics/mark/user/page');
      const raw = (d.data || {}).data || (d.data || {}).list || d.data || [];
      list = Array.isArray(raw) ? raw : [];
    } catch (e) {
      body.innerHTML = `<div class="cm-empty">加载失败：${esc(e.message)}</div>`;
      return;
    }
    if (!list.length) {
      body.innerHTML = '<div class="cm-empty small">还没有摘录。在播放页歌词里选中一句即可摘录。</div>';
      return;
    }
    body.innerHTML = `<div id="lmList">${list.map((m, i) => `
      <div class="cm-lm-item" data-i="${i}">
        <div class="cm-lm-lyric">${esc(m.lyric || m.content || '')}</div>
        <div class="cm-lm-meta">
          <span>${esc(m.songName || m.name || '')}${m.artistName ? ` · ${esc(m.artistName)}` : ''}</span>
          <span class="material-icons-outlined" data-del="${i}" title="删除摘录">delete_outline</span>
        </div>
      </div>`).join('')}</div>`;

    body.querySelectorAll('[data-del]').forEach(btn => {
      btn.onclick = async ev => {
        ev.stopPropagation();
        const m = list[+btn.dataset.del];
        const markId = m.markId || m.id;
        if (!markId) return toast('缺少摘录 id，无法删除');
        try {
          await api.ncm('/song/lyrics/mark/del', { markId, confirm: 1 });
          toast('已删除');
          load();
        } catch (e) { toast('删除失败：' + e.message); }
      };
    });
    body.querySelectorAll('.cm-lm-item').forEach(it => {
      it.onclick = () => {
        const m = list[+it.dataset.i];
        if (m.songId) location.hash = `#/home`;   // 无独立歌曲页，回到首页由用户自行搜索
        toast(m.songName ? `歌曲：${m.songName}` : '');
      };
    });
  }

  load();
}

/**
 * 添加歌词摘录（供播放页歌词视图调用）。
 * @param {object} meta 当前歌曲（ncm_id / name）
 * @param {string} lyricText 选中的歌词文本
 * @param {number} markId 若为修改已有摘录则传入
 */
export async function addLyricMark(meta, lyricText, markId = 0) {
  if (!auth.token) return toast('登录后才能摘录歌词');
  if (!lyricText || !lyricText.trim()) return toast('请先选中一句歌词');
  try {
    const params = { id: meta.ncm_id, data: JSON.stringify({ lyric: lyricText.trim() }), confirm: 1 };
    if (markId) params.markId = markId;
    await api.ncm('/song/lyrics/mark/add', params);
    toast('已加入歌词本');
  } catch (e) {
    toast(e.message.includes('绑定') ? '请先绑定网易云账号' : `摘录失败：${e.message}`);
  }
}
