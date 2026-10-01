// 通用评论抽屉（阶段二）：一套组件覆盖 6 个维度
//   type: 0 歌曲 / 1 MV / 2 歌单 / 3 专辑 / 4 电台节目 / 5 视频 / 6 动态
// 读：/comment/{music|mv|playlist|album|dj|video|event}（T0，游客可看）
// 写：/comment（t=0 发表 / 1 删除 / 2 回复）、/comment/like、/hug/comment（T2，需 confirm=1）
// 楼层：/comment/floor    统计：/comment/info/list    抱一抱列表：/comment/hug/list
// 举报：/comment/report
import { api, auth } from './api.js';
import { esc, toast, skelComments } from './ui.js';
import { renderEmoticons, fmtCmtTime } from './commentutil.js';

// 显式路径表：上游每个维度是独立路由，写清楚比拼接更可读、也便于覆盖率工具识别
const COMMENT_PATH = {
  0: '/comment/music', 1: '/comment/mv', 2: '/comment/playlist', 3: '/comment/album',
  4: '/comment/dj', 5: '/comment/video', 6: '/comment/event',
};
export const COMMENT_TYPE = { song: 0, mv: 1, playlist: 2, album: 3, dj: 4, video: 5, event: 6 };

/** 上游原始评论 → 内部结构。上游字段在 hotComments/comments 间一致，此处统一。 */
function normComment(c) {
  const u = c.user || {};
  const be = (c.beReplied && c.beReplied[0]) || null;
  return {
    id: c.commentId,
    uid: u.userId,
    nickname: u.nickname || '网易云用户',
    avatar: (u.avatarUrl || '').replace(/^http:/, 'https:'),
    content: c.content || '',
    liked: c.likedCount || 0,
    likedByMe: !!c.liked,
    time: c.time,
    beNickname: be ? ((be.user || {}).nickname || '') : '',
    beContent: be ? (be.content || '') : '',
    replyCount: (c.showFloorComment && c.showFloorComment.replyCount) || c.replyCount || 0,
  };
}

/**
 * 打开评论抽屉。
 * @param {object} o { type, id, title }
 */
export async function openComments({ type = 0, id, title = '' } = {}) {
  const path = COMMENT_PATH[type];
  if (!path || !id) return toast('缺少评论对象');

  const old = document.getElementById('commentOverlay');
  if (old) old.remove();
  const ov = document.createElement('div');
  ov.id = 'commentOverlay';
  document.body.appendChild(ov);

  let offset = 0, total = 0, more = true, meUid = 0, loading = false, replyTo = null;
  // 三种排序各有独立上游：推荐=实体列表接口（热门+最新），最热=/comment/hot，最新=/comment/new
  let mode = 'default';
  const likedSet = new Set(JSON.parse(sessionStorage.getItem('cm.cmtLiked') || '[]'));
  const saveLiked = () => sessionStorage.setItem('cm.cmtLiked', JSON.stringify([...likedSet]));

  const render = () => {
    ov.innerHTML = `
      <div class="cmt-page">
        <div class="cmt-top">
          <span class="pl-btn" id="cmtClose"><span class="material-icons-outlined">keyboard_arrow_down</span></span>
          <div class="cmt-title">评论区 · ${esc(title)}</div>
          <span style="width:36px"></span>
        </div>
        <div class="cmt-modes">
          ${[['default', '推荐'], ['hot', '最热'], ['new', '最新']].map(([k, l]) =>
            `<span class="cmt-mode${k === mode ? ' on' : ''}" data-m="${k}">${l}</span>`).join('')}
        </div>
        <div class="cmt-list" id="cmtList">${skelComments(5)}</div>
        <div class="cmt-composer">
          <div id="cmtReplyBar" hidden><span></span><i id="cmtReplyCancel"><span class="material-icons-outlined">close</span></i></div>
          <input id="cmtInput" type="text" maxlength="140" placeholder="${meUid ? '以网易云账号发表评论…' : '绑定网易云账号后可发评'}" ${meUid ? '' : 'disabled'}>
          <mdui-button variant="filled" id="cmtSend" ${meUid ? '' : 'disabled'}>发送</mdui-button>
        </div>
      </div>`;
    ov.querySelector('#cmtClose').onclick = () => { ov.classList.add('closing'); setTimeout(() => ov.remove(), 240); };
    ov.querySelector('#cmtSend').onclick = send;
    ov.querySelector('#cmtInput').onkeydown = e => { if (e.key === 'Enter') send(); };
    ov.querySelector('#cmtReplyCancel').onclick = () => setReply(null);
    ov.querySelectorAll('.cmt-mode').forEach(el => {
      el.onclick = () => {
        if (el.dataset.m === mode) return;
        mode = el.dataset.m;
        ov.querySelectorAll('.cmt-mode').forEach(x => x.classList.toggle('on', x === el));
        offset = 0; more = true;
        const box = ov.querySelector('#cmtList');
        if (box) box.innerHTML = skelComments(5);
        load(true);
      };
    });
  };

  const cmtHTML = c => {
    const liked = likedSet.has(c.id) || c.likedByMe;
    const mine = c.uid && meUid && c.uid === meUid;
    return `
    <div class="cmt-item" data-cid="${c.id}">
      ${c.avatar ? `<img src="${esc(c.avatar)}" loading="lazy">` : `<div class="cmt-avaph"></div>`}
      <div class="cmt-main">
        <div class="cmt-head">
          <span class="cmt-nick">${esc(c.nickname)}</span>
          ${mine ? '<span class="cmt-mine">我</span><span class="cmt-del" title="删除">删除</span>' : ''}
        </div>
        ${c.beNickname ? `<div class="cmt-be">回复 @${esc(c.beNickname)}：${esc(c.beContent)}</div>` : ''}
        <div class="cmt-content">${renderEmoticons(esc(c.content))}</div>
        <div class="cmt-foot">
          <span>${fmtCmtTime(c.time)}</span>
          <span class="cmt-like ${liked ? 'on' : ''}" data-like="${c.id}"><span class="mi">${liked ? 'thumb_up' : 'thumb_up_alt'}</span> ${c.liked || 0}</span>
          <span class="cmt-reply" data-rep="${c.id}">回复</span>
          ${type === 0 ? `<span class="cmt-hug" data-hug="${c.id}" data-uid="${c.uid || 0}" title="抱一抱">🤗 抱一抱</span>` : ''}
          <span class="cmt-report" data-rpt="${c.id}" title="举报">举报</span>
          ${c.replyCount ? `<span class="cmt-floor-btn" data-floor="${c.id}">查看回复(${c.replyCount})</span>` : ''}
        </div>
        <div class="cmt-floor" id="floor-${c.id}" hidden></div>
      </div>
    </div>`;
  };

  function setReply(r) {
    replyTo = r;
    const bar = ov.querySelector('#cmtReplyBar');
    const input = ov.querySelector('#cmtInput');
    if (!bar || !input) return;
    if (r) { bar.hidden = false; bar.querySelector('span').textContent = `回复 @${r.nickname}`; input.placeholder = `回复 @${r.nickname}…`; input.focus(); }
    else { bar.hidden = true; input.placeholder = meUid ? '以网易云账号发表评论…' : '绑定网易云账号后可发评'; }
  }

  async function load(reset) {
    if (loading) return;
    loading = true;
    if (reset) { offset = 0; more = true; }
    if (!more) { loading = false; return; }
    try {
      const pg = ov.querySelector('#cmtMore');
      if (pg) pg.outerHTML = '<div class="cmt-more-loading" id="cmtMoreWrap"><mdui-circular-progress></mdui-circular-progress> 加载中…</div>';
      let d;
      if (mode === 'hot') {
        d = await api.ncm('/comment/hot', { id, type, offset, limit: 20 });
      } else if (mode === 'new') {
        d = await api.ncm('/comment/new', { id, type, pageNo: Math.floor(offset / 20) + 1, pageSize: 20, sortType: 2 });
      } else {
        d = await api.ncm(path, { id, type, offset, limit: 20 });
      }
      meUid = d.userId || meUid;
      total = d.total || ((d.data || {}).total) || 0;
      if (reset) render();
      const box = ov.querySelector('#cmtList');
      box.querySelectorAll('.cmt-skel').forEach(el => el.remove());
      const rawList = d.comments || ((d.data || {}).comments) || ((d.data || {}).data) || [];
      const hot = mode === 'default' ? (d.hotComments || []).map(normComment) : [];
      const list = (Array.isArray(rawList) ? rawList : []).map(normComment);
      const html = (offset === 0 && hot.length ? `<div class="cmt-sec">热门评论</div>` + hot.map(cmtHTML).join('') : '')
        + (offset === 0 ? `<div class="cmt-sec">最新评论（${total}）</div>` : '')
        + list.map(cmtHTML).join('');
      const wrapEl = document.createElement('div');
      wrapEl.className = 'cmt-batch';
      wrapEl.innerHTML = html;
      box.appendChild(wrapEl);
      ov.querySelector('#cmtMoreWrap')?.remove();
      offset += list.length;
      more = !!(d.more || (d.data || {}).hasMore);
      if (more) {
        box.insertAdjacentHTML('beforeend', `<div class="cm-empty small" id="cmtMore" style="cursor:pointer">加载更多</div>`);
        box.querySelector('#cmtMore').onclick = () => load(false);
      }
      bindActions();
    } catch (e) {
      ov.querySelector('#cmtMoreWrap')?.remove();
      toast(`评论加载失败：${e.message}`);
    } finally {
      loading = false;
    }
  }

  function bindActions() {
    ov.querySelectorAll('.cmt-del').forEach(el => {
      el.onclick = async () => {
        const cid = +el.closest('.cmt-item').dataset.cid;
        try {
          await api.ncm('/comment/delete', { id, type, cid, confirm: 1 });
          el.closest('.cmt-item').remove();
          toast('评论已删除');
        } catch (e) { toast(e.message); }
      };
    });
    ov.querySelectorAll('.cmt-like').forEach(el => {
      el.onclick = async () => {
        const cid = +el.dataset.like;
        const cur = el.classList.contains('on');
        try {
          // t：0 点赞 / 1 取消（上游约定）
          await api.ncm('/comment/like', { id, cid, type, t: cur ? 1 : 0, confirm: 1 });
          el.classList.toggle('on', !cur);
          if (cur) likedSet.delete(cid); else likedSet.add(cid);
          saveLiked();
          const mi = el.querySelector('.mi');
          const n = parseInt(el.textContent.replace(/^\D+/, '') || '0', 10);
          mi.textContent = !cur ? 'thumb_up' : 'thumb_up_alt';
          el.innerHTML = mi.outerHTML + ' ' + Math.max(0, n + (cur ? -1 : 1));
        } catch (e) {
          toast(e.message.includes('绑定') ? '点赞需先绑定网易云账号' : e.message);
        }
      };
    });
    ov.querySelectorAll('.cmt-reply').forEach(el => {
      el.onclick = () => {
        const item = el.closest('.cmt-item');
        setReply({ id: +el.dataset.rep, nickname: item.querySelector('.cmt-nick').textContent });
        const box = ov.querySelector('#cmtList');
        box.scrollTo({ top: box.scrollHeight, behavior: 'smooth' });
      };
    });
    ov.querySelectorAll('.cmt-hug').forEach(el => {
      el.onclick = async () => {
        const cid = +el.dataset.hug;
        const uid = +el.dataset.uid || 0;
        try {
          // 上游 /hug/comment 需要 sid（歌曲 id）+ uid（被抱的用户），故仅歌曲维度可用
          await api.ncm('/hug/comment', { sid: id, cid, uid, type, confirm: 1 });
          toast('已抱一抱 🤗');
          showHuggers(cid, uid);
        } catch (e) { toast(e.message.includes('绑定') ? '需先绑定网易云账号' : e.message); }
      };
    });
    ov.querySelectorAll('.cmt-report').forEach(el => {
      el.onclick = async () => {
        const cid = +el.dataset.rpt;
        const reason = prompt('举报原因（留空取消）', '');
        if (reason == null) return;
        try {
          await api.ncm('/comment/report', { id, cid, reason: reason || '其他', confirm: 1 });
          toast('已提交举报');
        } catch (e) { toast(e.message); }
      };
    });
    ov.querySelectorAll('.cmt-floor-btn').forEach(el => {
      el.onclick = async () => {
        const cid = +el.dataset.floor;
        const box = ov.querySelector(`#floor-${cid}`);
        if (!box) return;
        if (!box.hidden) { box.hidden = true; return; }
        box.hidden = false;
        if (box.dataset.loaded) return;
        box.innerHTML = '<div class="cm-loading"><mdui-circular-progress></mdui-circular-progress></div>';
        try {
          const d = await api.ncm('/comment/floor', { id, parentCommentId: cid, type, limit: 20 });
          box.dataset.loaded = '1';
          // 楼层读的是「被回复的评论 + 其回复」，第一条通常是父评论，展示时跳过
          const list = ((d.data || {}).comments || d.comments || []).map(normComment);
          box.innerHTML = list.length
            ? list.map(c => `<div class="cmt-floor-item" data-cid="${c.id}">
                <div class="cmt-head"><span class="cmt-nick">${esc(c.nickname)}</span><span class="cmt-time">${fmtCmtTime(c.time)}</span></div>
                <div class="cmt-content">${renderEmoticons(esc(c.content))}</div>
              </div>`).join('')
            : '<div class="cm-empty small">没有更多回复</div>';
        } catch (e) {
          box.innerHTML = `<div class="cm-empty small">回复加载失败：${esc(e.message)}</div>`;
        }
      };
    });
  }

  /** 「谁抱过这条评论」：/comment/hug/list 需要 sid + uid（歌曲维度专属） */
  async function showHuggers(cid, uid) {
    try {
      const d = await api.ncm('/comment/hug/list', { sid: id, cid, uid, type, pageSize: 30 });
      const list = (((d.data || {}).data) || (d.data || {}).users || []);
      const arr = Array.isArray(list) ? list : [];
      const names = arr.slice(0, 12).map(u => u.nickname || u.userName || '').filter(Boolean);
      toast(names.length ? `抱过的有：${names.join('、')}` : '还没有人抱过');
    } catch { /* 无权/未登录：静默 */ }
  }

  async function send() {
    const input = ov.querySelector('#cmtInput');
    const content = (input.value || '').trim();
    if (!content) return;
    const btn = ov.querySelector('#cmtSend');
    btn.loading = true;
    try {
      // 发表与回复用各自的上游端点（比统一 /comment + t 参数更明确）
      if (replyTo) await api.ncm('/comment/reply', { id, type, cid: replyTo.id, content, confirm: 1 });
      else await api.ncm('/comment/add', { id, type, content, confirm: 1 });
      input.value = '';
      toast(replyTo ? '回复已发表（同步网易云）' : '评论已发表（同步网易云）');
      setReply(null);
      await load(true);
    } catch (e) {
      toast(e.message.includes('绑定') ? '请先在「我的」页绑定网易云账号' : e.message);
    } finally {
      btn.loading = false;
    }
  }

  render();
  await load(true);
}
