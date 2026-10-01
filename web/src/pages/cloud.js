// 我的音乐云盘与电台（阶段四新增）
// 云盘：/user/cloud（列表，含容量）、/user/cloud/detail（单曲详情）、/user/cloud/del（删除，T2）、
//      /song/cloud/download（取下载直链）、/cloud/lyric/get（云盘歌词）、/cloud/match（信息匹配纠正，T2）
// 我的：/user/dj（我的电台）、/user/audio（我的音频）
//
// 未接入（下一轮）：上传三步 /cloud/upload/token → 直传对象存储 → /cloud/upload/complete，
// 以及 /cloud（单文件直传）。原因是它们需要在前端算 MD5，而 WebCrypto 不提供 MD5
// （只有 SHA 系列），必须自带实现；这件事要单独做并单独验证，不混在这一轮里假装完成。
import { api, auth } from '../api.js';
import { md5Hex } from '../md5.js';
import { esc, toast, fmtCount, skelList, confirmDialog, promptDialog } from '../ui.js';

const KEY = 'cm.cloudTab';

const fmtSize = n => {
  const v = Number(n) || 0;
  if (v >= 1024 ** 3) return `${(v / 1024 ** 3).toFixed(2)} GB`;
  if (v >= 1024 ** 2) return `${(v / 1024 ** 2).toFixed(1)} MB`;
  if (v >= 1024) return `${(v / 1024).toFixed(0)} KB`;
  return `${v} B`;
};

function needBind(el, text = '云盘与电台跟你的网易云账号绑定，请先绑定') {
  el.innerHTML = `<div class="cm-login-tip page">
    <span class="material-icons-outlined" style="font-size:calc(44px * var(--cm-fs, 1))">cloud</span>
    <div>${esc(text)}</div>
    <mdui-button variant="filled" href="#/user">去绑定网易云</mdui-button></div>`;
}

export async function render(el, params = {}) {
  if (!auth.token) { needBind(el, '登录后可查看音乐云盘'); return; }
  let bind = { bound: false };
  try { bind = await api.bindStatus(); } catch { /* 未绑定 */ }
  if (!bind.bound) { needBind(el); return; }
  const uid = bind.profile && bind.profile.uid;

  const tab = params.tab || sessionStorage.getItem(KEY) || 'cloud';
  const TABS = [
    { k: 'cloud', l: '音乐云盘' },
    { k: 'radio', l: '我的电台' },
  ];
  el.innerHTML = `
    <div class="cm-sqtabs" id="clTabs">
      ${TABS.map(t => `<button class="cm-sqtab${t.k === tab ? ' on' : ''}" data-k="${t.k}">${t.l}</button>`).join('')}
    </div>
    <div id="clBody">${skelList(6)}</div>`;
  el.querySelectorAll('#clTabs .cm-sqtab').forEach(b => {
    b.onclick = () => {
      sessionStorage.setItem(KEY, b.dataset.k);
      render(el, { tab: b.dataset.k });
    };
  });
  const body = el.querySelector('#clBody');
  const fail = e => {
    body.innerHTML = /绑定|401/.test(e.message || '')
      ? '<div class="cm-empty">需先绑定网易云账号</div>'
      : `<div class="cm-empty">加载失败：${esc(e.message)}</div>`;
  };

  if (tab === 'cloud') {
    body.innerHTML = skelList(6);
    let offset = 0;
    let total = 0;
    const load = async append => {
      let d;
      try { d = await api.ncm('/user/cloud', { limit: 50, offset }); } catch (e) { fail(e); return; }
      const list = d.data || [];
      total = d.count || list.length;
      if (!append) body.innerHTML = '';
      if (!append) {
        body.insertAdjacentHTML('afterbegin', `
          <div class="cm-sq-stats"><span class="material-icons-outlined">cloud</span>
            已用 ${fmtSize(d.size)} / ${fmtSize(d.maxSize)}${d.upgradeSign ? '（可扩容）' : ''} · ${total} 首</div>
          <div class="cm-pe-acts">
            <mdui-button variant="filled" id="clUp">上传到云盘（三步）</mdui-button>
            <mdui-button variant="tonal" id="clUpLegacy">直接上传（旧接口）</mdui-button>
            <input type="file" id="clFile" accept="audio/*" hidden>
            <input type="file" id="clFileLegacy" accept="audio/*" hidden>
          </div>
          <div class="cm-pe-hint" id="clUpHint">三步走：算 md5 → /cloud/upload/token 拿上传地址 → 直传 → /cloud/upload/complete。
            直传要浏览器/WebView 允许跨域；若被拦，用「直接上传（旧接口）」经服务端走 /cloud（multipart）。</div>`);
      }
      if (!list.length && !append) {
        body.insertAdjacentHTML('beforeend', '<div class="cm-empty small">云盘还是空的</div>');
        return;
      }
      body.insertAdjacentHTML('beforeend', list.map(s => `
        <div class="cm-lm-item" data-cid="${s.songId || s.id || ''}">
          <div class="cm-lm-lyric">${esc(s.songName || s.name || '')}</div>
          <div class="cm-lm-meta">
            <span>${esc(s.artist || '')}${s.fileSize ? ` · ${fmtSize(s.fileSize)}` : ''}</span>
            <span><i class="cm-si-act" data-detail="1">详情</i><i class="cm-si-act" data-lyric="1">歌词</i><i class="cm-si-act" data-match="1">纠正匹配</i><i class="cm-si-act" data-dl="1">下载</i><i class="cm-si-act" data-del="1">删除</i></span>
          </div>
        </div>`).join(''));
      bindRows(body);
      offset += list.length;
      body.querySelector('#clMore')?.remove();
      if (offset < total) {
        body.insertAdjacentHTML('beforeend',
          '<div class="cm-sq-more" id="clMore"><mdui-button variant="tonal">加载更多</mdui-button></div>');
        body.querySelector('#clMore mdui-button').onclick = async ev => {
          const btn = ev.currentTarget;
          btn.loading = true;
          try { await load(true); } catch (e) { toast(e.message); } finally { btn.loading = false; }
        };
      }
    };
    await load(false);

    // ---- 云盘上传 ----
    // 旧接口（/cloud）：文件经我们的服务端 → 上游 multipart，一次搞定，最可靠。
    body.querySelector('#clUpLegacy').onclick = () => body.querySelector('#clFileLegacy').click();
    body.querySelector('#clFileLegacy').onchange = async ev => {
      const file = ev.target.files && ev.target.files[0];
      if (!file) return;
      const hint = body.querySelector('#clUpHint');
      hint.textContent = '上传中（经服务端转 multipart）…';
      try {
        await api.ncmUpload('/cloud', { songName: file.name.replace(/\.[^.]+$/, '') }, file);
        hint.textContent = '上传完成，正在刷新列表…';
        render(el, { tab: 'cloud' });
      } catch (e) {
        hint.textContent = `上传失败：${/绑定|401/.test(e.message) ? '需先绑定网易云账号' : e.message}`;
      } finally { ev.target.value = ''; }
    };

    // 三步走：/cloud/upload/token（前端算 md5）→ 直传对象存储 → /cloud/upload/complete。
    // 直传用 fetch 打上游给的对象存储地址：浏览器端要 CORS 放行，WebView 里可能被拦，
    // 所以失败时明确提示改用旧接口，而不是假装上传成功。
    body.querySelector('#clUp').onclick = () => body.querySelector('#clFile').click();
    body.querySelector('#clFile').onchange = async ev => {
      const file = ev.target.files && ev.target.files[0];
      if (!file) return;
      const hint = body.querySelector('#clUpHint');
      const say = t => { hint.textContent = t; };
      try {
        say('计算 md5…');
        const buf = await file.arrayBuffer();
        const md5 = md5Hex(buf);
        say(`md5=${md5}，申请上传令牌…`);
        const t = await api.ncm('/cloud/upload/token', {
          md5, fileSize: file.size, filename: file.name, bitrate: 999000,
        });
        const info = t.data || {};
        if (info.needUpload === false) {
          say('云盘已有同 md5 文件，无需重复上传');
        } else {
          say('直传对象存储…');
          const up = await fetch(info.uploadUrl, {
            method: 'POST',
            headers: { 'x-nos-token': info.uploadToken, 'Content-Type': file.type || 'audio/mpeg' },
            body: buf,
          });
          if (!up.ok) throw new Error(`对象存储返回 ${up.status}（多为跨域被拦，请改用「直接上传（旧接口）」）`);
        }
        say('登记到云盘…');
        await api.ncm('/cloud/upload/complete', {
          songId: info.songId, resourceId: info.resourceId, md5, filename: file.name,
          song: file.name.replace(/\.[^.]+$/, ''), confirm: 1,
        });
        say('完成，正在刷新列表…');
        render(el, { tab: 'cloud' });
      } catch (e) {
        say(`三步上传失败：${e.message}。可改用「直接上传（旧接口）」（经服务端 multipart，不受跨域限制）。`);
      } finally { ev.target.value = ''; }
    };

    // 导入已上传但未入库的文件（/cloud/import）：完成步骤失败时的补救
    const impBtn = document.createElement('div');
    impBtn.className = 'cm-pe-acts';
    impBtn.innerHTML = '<mdui-button variant="text" id="clImport">导入已上传文件（/cloud/import）</mdui-button>';
    body.appendChild(impBtn);
    impBtn.querySelector('#clImport').onclick = () => {
      const ids = prompt('填「md5,fileSize,songId」三项，逗号分隔', '');
      if (!ids) return;
      const [md5, fileSize, songId] = String(ids).split(',').map(x => x.trim());
      if (!md5 || !fileSize) return toast('至少要有 md5 与 fileSize');
      api.ncm('/cloud/import', {
        md5, fileSize, id: songId || -2, bitrate: 999000, fileType: 'mp3',
        song: '', artist: '', album: '', confirm: 1,
      }).then(d => { toast(d.message || '已提交导入'); render(el, { tab: 'cloud' }); })
        .catch(e => toast(/绑定|401/.test(e.message) ? '需先绑定网易云账号' : e.message));
    };
    return;
  }

  // 我的电台（/user/dj）+ 我的音频（/user/audio）
  body.innerHTML = skelList(6);
  const [djR, audioR] = await Promise.allSettled([
    api.ncm('/user/dj', { uid, limit: 50, offset: 0 }),
    api.ncm('/user/audio', { uid }),
  ]);
  const djs = djR.status === 'fulfilled' ? (djR.value.djRadios || djR.value.data || []) : [];
  const audios = audioR.status === 'fulfilled' ? (audioR.value.data || []) : [];
  body.innerHTML = `
    <section class="cm-sec">
      <div class="cm-sec-head"><h2>我的电台</h2><span class="cm-sec-sub">/user/dj</span></div>
      ${djs.length ? `<div class="cm-plgrid">${djs.map(r => `
        <div class="cm-plcard" data-rid="${r.id}">
          <div class="cm-plcover">${r.picUrl ? `<img src="${esc(r.picUrl)}?param=300y300" loading="lazy" onerror="this.remove()">` : ''}<span class="material-icons-outlined">radio</span></div>
          <div class="cm-plname">${esc(r.name)}</div>
          <div class="cm-plsub">${r.programCount || 0} 期</div>
        </div>`).join('')}</div>`
      : '<div class="cm-empty small">没有电台（上游参数或数据为空）</div>'}
    </section>
    <section class="cm-sec">
      <div class="cm-sec-head"><h2>我的音频</h2><span class="cm-sec-sub">/user/audio</span></div>
      ${audios.length ? `<div class="cm-stat-list">${audios.map(a => `
        <div class="cm-lm-item"><div class="cm-lm-lyric">${esc(a.name || a.title || '')}</div>
        <div class="cm-lm-meta"><span>${esc(a.artist || '')}</span><span>${a.duration ? fmtCount(a.duration) : ''}</span></div></div>`).join('')}</div>`
      : '<div class="cm-empty small">没有音频（上游参数或数据为空）</div>'}
    </section>`;

  /** 云盘行上的三个动作：详情 / 下载直链 / 删除。 */
  function bindRows(scope) {
    scope.querySelectorAll('[data-cid]').forEach(row => {
      const cid = row.dataset.cid;
      const detail = row.querySelector('[data-detail]');
      const dl = row.querySelector('[data-dl]');
      const del = row.querySelector('[data-del]');
      if (detail) detail.onclick = async () => {
        try {
          const d = await api.ncm('/user/cloud/detail', { id: cid });
          const item = (d.data || [])[0] || {};
          toast(`${item.songName || item.name || '云盘歌曲'} · ${item.artist || ''} ${item.fileSize ? fmtSize(item.fileSize) : ''}`);
        } catch (e) { toast(e.message); }
      };
      const lyric = row.querySelector('[data-lyric]');
      if (lyric) lyric.onclick = async () => {
        try {
          const d = await api.ncm('/cloud/lyric/get', { sid: cid, uid });
          const lrc = (d.data && (d.data.lyric || d.data.lrc)) || d.lyric || '';
          toast(lrc ? lrc.split('\n').slice(0, 2).join(' / ') : (d.message || '上游没有返回歌词'));
        } catch (e) { toast(e.message); }
      };
      const match = row.querySelector('[data-match]');
      if (match) match.onclick = () => promptDialog({
        title: '纠正云盘歌曲匹配', label: '正确的歌曲 ID（asid）', placeholder: '如 347230',
        onOk: asid => api.ncm('/cloud/match', { sid: cid, asid, uid, confirm: 1 })
          .then(() => toast('已提交匹配纠正'))
          .catch(e => toast(/绑定|401/.test(e.message) ? '需先绑定网易云账号' : e.message)),
      });
      if (dl) dl.onclick = async () => {
        try {
          const d = await api.ncm('/song/cloud/download', { id: cid });
          const url = (d.data || {}).url || d.url;
          if (url) { window.open(url, '_blank'); toast('已打开下载链接'); }
          else toast(d.msg || '上游没有返回下载地址');
        } catch (e) { toast(/绑定|401/.test(e.message) ? '需先绑定网易云账号' : e.message); }
      };
      if (del) del.onclick = () => confirmDialog({
        title: '从云盘删除这首歌？', body: '删除后不可恢复。',
        onOk: () => api.ncm('/user/cloud/del', { id: cid, confirm: 1 })
          .then(() => { row.remove(); toast('已删除'); })
          .catch(e => toast(/绑定|401/.test(e.message) ? '需先绑定网易云账号' : e.message)),
      });
    });
  }
}
