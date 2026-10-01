// 搜索页：回车触发搜索 + 历史记录（不做逐字防抖——中间态查询会同时打爆上游与碎片化历史）
//
// 加载策略（本页性能的关键）：
//   上游四个类型的耗时差异极大（实测 歌手 6.1s / 专辑 4.2s / 单曲 3.0s / 歌单 2.1s），
//   而默认展示的是「单曲」。所以**首屏只请求单曲**（~3s 即可出结果），其余类型在
//   首屏渲染完成后于后台补齐，用于填 TAB 上的计数；用户切到某个 TAB 时若还没取，
//   再按需取一次。每个类型每批只取 10 条，底部给「显示更多」按需追加。
//
// 动效约定（曲线与时长跟评论区批次渐入 cmtIn 保持一致）：
//   · 切 TAB：面板内容按切换方向轻微横移 + 淡入
//   · 显示更多：只把**新增条目**阶梯渐入，已有条目保持不动
//   · TAB 栏就地更新计数而不整块重建，否则指示条的 cmTabIn 动画会被反复重播
//   · prefers-reduced-motion 下全部跳过
import { api } from '../api.js';
import { esc, renderSongList, toast } from '../ui.js';
import { player } from '../player.js';
import { addToPlaylist } from '../player-ui.js';

const HIST_KEY = 'cm.searchHistory';
const BATCH = 10;              // 每个类型每批取的条数（首屏与「显示更多」共用）
const EASE = 'cubic-bezier(0.05, 0.7, 0.1, 1)';   // 与 app.css 里 cmtIn 同一曲线

// TAB 定义：k 直接用作后端 type 参数，arr 是响应里的数组字段
const TABS = [
  { k: 'song', label: '单曲', arr: 'songs' },
  { k: 'album', label: '专辑', arr: 'albums' },
  { k: 'artist', label: '歌手', arr: 'artists' },
  { k: 'playlist', label: '歌单', arr: 'playlists' },
];
const ARR = Object.fromEntries(TABS.map(t => [t.k, t.arr]));

const hist = () => { try { return JSON.parse(localStorage.getItem(HIST_KEY)) || []; } catch { return []; } };
const pushHist = kw => {
  const h = [kw, ...hist().filter(x => x !== kw)].slice(0, 10);
  localStorage.setItem(HIST_KEY, JSON.stringify(h));
};

// 搜索结果骨架屏：保留歌曲行结构，避免请求期间结果区跳空
const searchResultSkeleton = (n = 6) => `<div class="cm-search-result-skeleton" aria-busy="true" aria-label="搜索结果加载中">
  <div class="cm-skel-list">${Array.from({ length: n }, () => `<div class="cm-skel-song"><div class="sk sk-pic"></div><div class="cm-skel-main"><div class="sk sk-l1"></div><div class="sk sk-l2"></div></div></div>`).join('')}</div>
</div>`;

const panelLoading = () => '<div class="cmt-more-loading"><mdui-circular-progress></mdui-circular-progress> 加载中…</div>';

/** 是否应减少动效。实时查询，用户改系统设置后无需重载页面。 */
const reduceMotion = () => typeof matchMedia === 'function'
  && matchMedia('(prefers-reduced-motion: reduce)').matches;

/** 新条目阶梯渐入。fill:'backwards' 保证延迟期间不可见，不会先闪一下再开始动。 */
function animateIn(els, { y = 8, stagger = 26, cap = 160 } = {}) {
  if (!els.length || reduceMotion() || !els[0].animate) return;
  els.forEach((el, i) => el.animate(
    [{ opacity: 0, transform: `translateY(${y}px)` }, { opacity: 1, transform: 'none' }],
    { duration: 260, delay: Math.min(i * stagger, cap), easing: EASE, fill: 'backwards' }));
}

let seq = 0;

export async function render(el, params = []) {
  el.innerHTML = `
    <div class="cm-search-bar cm-search-entry">
      <mdui-text-field id="kw" label="搜索歌曲 / 歌手 / 专辑" variant="outlined" clearable style="width:100%"></mdui-text-field>
      <button type="button" class="cm-search-go" id="searchGo" aria-label="搜索"><span class="material-icons-outlined" aria-hidden="true">search</span></button>
    </div>
    <div id="sugBox"></div>
    <div id="histBox"></div>
    <details class="cm-advanced-search" id="matchTools">
      <summary><span class="material-icons-outlined" aria-hidden="true">tune</span>高级音频匹配工具<span class="cm-advanced-hint">需预先准备文件指纹</span></summary>
      <div class="cm-plsort" id="matchRow"><mdui-chip id="matchLocal">本地文件匹配</mdui-chip><mdui-chip id="matchMulti">多重匹配</mdui-chip></div>
      <div id="matchBox"></div>
    </details>
    <div id="resultBox"></div>`;

  const input = el.querySelector('#kw');
  const sugBox = el.querySelector('#sugBox');
  const histBox = el.querySelector('#histBox');
  const resultBox = el.querySelector('#resultBox');
  const hotBox = document.createElement('div');
  histBox.after(hotBox);

  // 两个"匹配"接口（/search/match 本地文件匹配、/search/multimatch 多重匹配）：
  // 上游都要把音频指纹/文件摘要作为入参，浏览器端生成不了，所以这里**只做结果查询**，
  // 参数由调用方粘贴（例如从桌面端工具算好后带过来），不伪造指纹。
  const matchBox = el.querySelector('#matchBox');
  const runMatch = async (path, label) => {
    matchBox.innerHTML = `<div class="cm-pe-field"><label>${label}：粘贴上游需要的指纹/摘要参数</label>
      <input id="matchArg" placeholder="例如 md5 或 fingerprint（上游要求）"></div>
      <div class="cm-pe-acts"><mdui-button variant="tonal" id="matchGo">查询</mdui-button></div>
      <div class="cm-pe-hint" id="matchHint">没有指纹参数时上游会返回参数错误——这是预期行为，不做假数据。</div>`;
    matchBox.querySelector('#matchGo').onclick = async () => {
      const arg = matchBox.querySelector('#matchArg').value.trim();
      const hint = matchBox.querySelector('#matchHint');
      hint.textContent = '查询中…';
      try {
        const d = path === '/search/match'
          ? await api.ncm('/search/match', { md5: arg })
          : await api.ncm('/search/multimatch', { md5: arg });
        hint.textContent = `返回：${JSON.stringify(d).slice(0, 200)}`;
      } catch (e) { hint.textContent = `失败：${e.message}`; }
    };
  };
  el.querySelector('#matchLocal').onclick = () => runMatch('/search/match', '本地文件匹配');
  el.querySelector('#matchMulti').onclick = () => runMatch('/search/multimatch', '多重匹配');

  // 默认搜索关键词（占位提示）：失败保持原 label，不影响使用
  api.searchDefault().then(d => {
    const k = ((d.data || {}).realkeyword || (d.data || {}).showKeyword || '').trim();
    if (k) input.setAttribute('label', `搜索歌曲 / 歌手 / 专辑（试试「${k}」）`);
  }).catch(() => {});

  async function renderHot() {
    if ((input.value || '').trim() || (hotBox.dataset.for ?? '') !== '') return;
    hotBox.innerHTML = '<div class="cm-loading"><mdui-linear-progress style="width:120px"></mdui-linear-progress></div>';
    try {
      // 优先用「热搜详情」（带热度分与标签）；不可用时回退到简略热搜
      let chips = '';
      try {
        const data = (await api.searchHotDetail()).data || [];
        chips = data.slice(0, 12).map((x, i) =>
          `<span class="cm-hot" data-k="${esc(x.searchWord)}"><b>${i + 1}</b>${esc(x.searchWord)}</span>`).join('');
      } catch { /* 回退 */ }
      if (!chips) {
        // 简略热搜的形状是 { result: { hots: [{ first }] } }
        const hots = ((await api.ncm('/search/hot')).result || {}).hots || [];
        chips = hots.slice(0, 12).map((x, i) =>
          `<span class="cm-hot" data-k="${esc(x.first)}"><b>${i + 1}</b>${esc(x.first)}</span>`).join('');
      }
      hotBox.innerHTML = chips ? `<div class="cm-sec-head"><h2>热门搜索</h2></div>
        <div class="cm-chips">${chips}</div>` : '';
      hotBox.querySelectorAll('.cm-hot').forEach(c => { c.onclick = () => { input.value = c.dataset.k; doSearch(c.dataset.k); }; });
    } catch { hotBox.innerHTML = ''; }
  }

  const renderHist = () => {
    const h = hist();
    if (!h.length) { histBox.innerHTML = ''; renderHot(); return; }   // 无历史也要出热搜
    histBox.innerHTML = `<div class="cm-sec-head"><h2>搜索历史</h2><span class="cm-sec-more" id="clearHist">清空</span></div>
      <div class="cm-chips">${h.map(k =>
        `<span class="cm-hist"><span class="cm-hist-k" data-k="${esc(k)}">${esc(k)}</span><i class="cm-hist-x" data-x="${esc(k)}"><span class="material-icons-outlined">close</span></i></span>`).join('')}</div>`;
    histBox.querySelector('#clearHist').onclick = () => { localStorage.removeItem(HIST_KEY); renderHist(); renderHot(); };
    histBox.querySelectorAll('.cm-hist-k').forEach(c => { c.onclick = () => { input.value = c.dataset.k; doSearch(c.dataset.k); }; });
    histBox.querySelectorAll('.cm-hist-x').forEach(x => {
      x.onclick = e => {
        e.stopPropagation();
        localStorage.setItem(HIST_KEY, JSON.stringify(hist().filter(v => v !== x.dataset.x)));
        renderHist();
      };
    });
    renderHot();
  };
  renderHist();

  /* ---------- 一次搜索的状态 ---------- */
  let S = null;                                   // { kw, items, total, more, loaded, loading, cur }
  const newState = kw => ({ kw, items: {}, total: {}, more: {}, loaded: {}, loading: {}, cur: 'song' });

  const panel = () => resultBox.querySelector('#srchPanel');
  const loadedCount = t => (S.items[t] || []).length;

  /** 取某一类型的一页（offset 为 0 时覆盖，否则追加）。返回本次新增条数。 */
  async function load(t, offset) {
    const state = S; // 每个响应只能更新发起它的搜索状态
    if (!state) return 0;
    const d = await api.search(state.kw, offset, BATCH, t);
    if (state !== S || !el.isConnected) return 0;
    const got = d[ARR[t]] || [];
    state.items[t] = offset ? (state.items[t] || []).concat(got) : got;
    state.total[t] = (d.totals && d.totals[t]) || state.items[t].length;
    state.more[t] = !!(d.hasMore && d.hasMore[t]);
    state.loaded[t] = true;
    return got.length;
  }

  /** TAB 条数：未加载的类型不显示数字（避免先显示 0 再跳变）。 */
  const tabCount = t => (S.loaded[t] ? (S.total[t] || loadedCount(t)) : '');

  /** 已加载且为空的类型不占 TAB 位；当前选中的始终保留。 */
  const visibleTabs = () => TABS.filter(t =>
    !S.loaded[t.k] || loadedCount(t.k) > 0 || t.k === S.cur);

  /**
   * 绘制 TAB 栏。
   * 可见集合没变时**就地更新**计数与选中态——整块重建会让 `.on` 上那条指示条的
   * cmTabIn 动画被反复重播（后台补齐计数时尤其明显，看起来像整条 TAB 栏在闪）。
   */
  function renderTabs() {
    const box = resultBox.querySelector('#srchTabs');
    if (!box) return;
    const list = visibleTabs();
    const sig = list.map(t => t.k).join(',');
    if (box.dataset.sig === sig && box.children.length === list.length) {
      list.forEach((t, i) => {
        const btn = box.children[i];
        btn.classList.toggle('on', t.k === S.cur);
        const n = tabCount(t.k);
        const badge = btn.querySelector('i');
        if (n === '') { if (badge) badge.remove(); }
        else if (badge) { badge.textContent = n; }
        else { btn.insertAdjacentHTML('beforeend', `<i>${n}</i>`); }
      });
      return;
    }
    box.dataset.sig = sig;
    box.innerHTML = list.map(t => {
      const n = tabCount(t.k);
      return `<button class="cm-srchtab${t.k === S.cur ? ' on' : ''}" data-k="${t.k}">${t.label}${n !== '' ? `<i>${n}</i>` : ''}</button>`;
    }).join('');
    box.querySelectorAll('.cm-srchtab').forEach(b => { b.onclick = () => selectTab(b.dataset.k); });
  }

  /** 「显示更多」按钮（沿用 square.js 的按钮与进度写法）。 */
  function moreButtonHTML(t) {
    if (!S.more[t]) return '';
    const shown = loadedCount(t), total = S.total[t] || shown;
    return `<div class="cm-sq-more" data-more-wrap="${t}"><mdui-button variant="tonal" data-more="${t}">显示更多（${shown}/${total}）</mdui-button></div>`;
  }

  /** 把底部按钮换到面板末尾（并重新绑定）。追加后计数变了，整块替换最简单。 */
  function refreshMore(t) {
    const p = panel();
    if (!p) return;
    p.querySelectorAll('[data-more-wrap]').forEach(x => x.remove());
    const html = moreButtonHTML(t);
    if (html) p.insertAdjacentHTML('beforeend', html);
    bindMore(t);
  }

  function bindMore(t) {
    const btn = panel()?.querySelector(`[data-more="${t}"]`);
    if (!btn) return;
    btn.onclick = async () => {
      if (S.loading[t]) return;
      S.loading[t] = true;
      btn.disabled = true;
      btn.textContent = '加载中…';
      const my = seq;
      const before = loadedCount(t);           // 追加前的条数 = 本次渲染的起点
      try {
        await load(t, before);
        if (my !== seq) return;
        renderTabs();
        renderPanel(before, { items: true });  // 只画新增的那一批
      } catch (e) {
        if (my !== seq) return;
        toast('加载失败：' + e.message);
        btn.disabled = false;
        btn.textContent = `显示更多（${loadedCount(t)}/${S.total[t] || loadedCount(t)}）`;
      } finally {
        if (my === seq && S) S.loading[t] = false;
      }
    };
  }

  /* ---------- 各类型面板 ----------
     约定：from = 0 建容器并画第一批；from > 0 只追加新增条目。
     items 为真时才给条目加渐入动画（切 TAB 由面板整体动画负责，避免双重动效）。 */

  async function renderSongPanel(from, items) {
    const p = panel();
    if (!from) {
      p.innerHTML = `<div class="cm-sec-head"><h2>单曲</h2>
        <span class="cm-sec-more" id="addAll"><span class="material-icons-outlined">playlist_add</span> 全部收入歌单</span></div>
        <div id="songList"></div>`;
      p.querySelector('#addAll').onclick = () => addToPlaylist(S.items.song || []);
      refreshMore('song');
    }
    const state = S;
    const rows = await renderSongList(p.querySelector('#songList'), state.items.song || [],
      { from, onPlay: i => player.playList(state.items.song, i) });
    if (state !== S || S.cur !== 'song' || !el.isConnected) return;
    // 追加时动画新增行；首次出结果时也渐入（那时没有面板整体动画）
    if (from) { animateIn(rows); refreshMore('song'); }
    else if (items) animateIn(rows);
  }

  function renderAlbumPanel(from, items) {
    const p = panel();
    const all = S.items.album || [];
    if (!from) {
      p.innerHTML = `<div class="cm-sec-head"><h2>专辑</h2><span class="cm-sec-sub">点击整专辑播放</span></div>
        <div class="cm-hscroll cm-album-row" id="srchAlbum"></div>`;
      refreshMore('album');
    }
    const box = p.querySelector('#srchAlbum');
    const added = all.slice(from);
    if (!added.length) return;
    box.insertAdjacentHTML('beforeend', added.map(a => `
      <div class="cm-card cm-album-card" data-id="${a.id}" title="播放专辑「${esc(a.name)}」">
        <img src="${esc(a.pic)}?param=300y300" loading="lazy" onerror="this.classList.add('none')">
        <div class="cm-card-name">${esc(a.name)}</div>
        <div class="cm-card-sub">${esc(a.artist)}</div>
      </div>`).join(''));
    const els = [...box.querySelectorAll('.cm-album-card')].slice(-added.length);
    els.forEach(c => {
      c.onclick = () => { location.hash = `#/album/${c.dataset.id}`; };
    });
    if (from) { animateIn(els); refreshMore('album'); }
    else if (items) animateIn(els);
  }

  function renderArtistPanel(from, items) {
    const p = panel();
    const all = S.items.artist || [];
    if (!from) {
      p.innerHTML = `<div class="cm-sec-head"><h2>歌手</h2><span class="cm-sec-sub">点击查看全部作品</span></div>
        <div class="cm-hscroll cm-artist-row" id="srchArtist"></div>`;
      refreshMore('artist');
    }
    const box = p.querySelector('#srchArtist');
    const added = all.slice(from);
    if (!added.length) return;
    box.insertAdjacentHTML('beforeend', added.map(a => `
      <div class="cm-artist-card" data-id="${a.id}">
        <div class="cm-artist-ava">${a.pic ? `<img src="${esc(a.pic)}?param=120y120" loading="lazy">` : '<span class="material-icons-outlined">person</span>'}</div>
        <div class="cm-artist-name">${esc(a.name)}</div>
        ${a.alias ? `<div class="cm-artist-sub">${esc(a.alias)}</div>` : ''}
      </div>`).join(''));
    const els = [...box.querySelectorAll('.cm-artist-card')].slice(-added.length);
    els.forEach(c => {
      c.onclick = () => { location.hash = `#/artist/${c.dataset.id}`; };
    });
    if (from) { animateIn(els); refreshMore('artist'); }
    else if (items) animateIn(els);
  }

  const fmtPlay = n => n >= 100000000 ? (n / 100000000).toFixed(1) + ' 亿'
    : n >= 10000 ? Math.round(n / 10000) + ' 万' : String(n || 0);

  function renderPlaylistPanel(from, items) {
    const p = panel();
    const all = S.items.playlist || [];
    if (!from) {
      p.innerHTML = `<div class="cm-sec-head"><h2>歌单</h2><span class="cm-sec-sub">点击查看歌单内容</span></div>
        <div class="cm-plgrid" id="srchPls"></div>`;
      refreshMore('playlist');
    }
    const box = p.querySelector('#srchPls');
    const added = all.slice(from);
    if (!added.length) return;
    box.insertAdjacentHTML('beforeend', added.map(pl => `
      <div class="cm-plcard" data-id="${pl.id}">
        <div class="cm-plcover">${pl.pic ? `<img src="${esc(pl.pic)}?param=300y300" loading="lazy" onerror="this.remove()">` : ''}<span class="material-icons-outlined">queue_music</span></div>
        <div class="cm-plname">${esc(pl.name)}</div>
        <div class="cm-plsub">${pl.trackCount} 首 · ${fmtPlay(pl.playCount)}播放</div>
      </div>`).join(''));
    const els = [...box.querySelectorAll('.cm-plcard')].slice(-added.length);
    els.forEach(c => {
      c.onclick = () => { location.hash = `#/ncmpl/${c.dataset.id}`; };
    });
    if (from) { animateIn(els); refreshMore('playlist'); }
    else if (items) animateIn(els);
  }

  const RENDERERS = { song: renderSongPanel, album: renderAlbumPanel, artist: renderArtistPanel, playlist: renderPlaylistPanel };

  function renderPanel(from = 0, { items = false } = {}) {
    const fn = RENDERERS[S.cur];
    if (fn) fn(from, items);
  }

  /** 切 TAB：内容按切换方向轻微横移 + 淡入（左右顺序决定方向）。 */
  function animatePanel(dir) {
    const p = panel();
    if (!dir || !p || reduceMotion() || !p.animate) return;
    p.animate(
      [{ opacity: 0, transform: `translateX(${dir * 16}px)` }, { opacity: 1, transform: 'none' }],
      { duration: 230, easing: EASE });
  }

  async function selectTab(k) {
    if (!S || S.cur === k) return;
    const order = TABS.map(t => t.k);
    const dir = order.indexOf(k) > order.indexOf(S.cur) ? 1 : -1;
    S.cur = k;
    renderTabs();
    if (!S.loaded[k]) {
      // 切到尚未取过的类型：按需取一次（首屏为了速度没有预取完）
      panel().innerHTML = panelLoading();
      const my = seq;
      try {
        await load(k, 0);
      } catch (e) {
        if (my !== seq) return;
        panel().innerHTML = `<div class="cm-empty">加载失败：${esc(e.message)}</div>`;
        return;
      }
      if (my !== seq || !S || S.cur !== k || !el.isConnected) return;
      renderTabs();
    }
    if (!S || S.cur !== k || !el.isConnected) return;
    renderPanel(0, { items: false });
    animatePanel(dir);
  }

  function renderShell() {
    resultBox.innerHTML = `
      <div class="cm-srchtabs" id="srchTabs"></div>
      <div id="srchPanel"></div>`;
    renderTabs();
    renderPanel(0, { items: true });   // 首次出结果：条目阶梯渐入
  }

  const anyResult = () => TABS.some(t => loadedCount(t.k) > 0);

  async function doSearch(kw) {
    const my = ++seq;
    pushHist(kw);
    renderHist();
    hotBox.innerHTML = '';   // 有搜索内容后隐藏热搜
    resultBox.innerHTML = searchResultSkeleton(6);
    S = newState(kw);

    // 首屏只等「单曲」：上游「歌手」实测可达 6s+，没必要让它拖住首屏
    try {
      await load('song', 0);
    } catch (e) {
      if (my === seq && el.isConnected) {
        resultBox.innerHTML = `<div class="cm-empty cm-error" role="alert">搜索失败：${esc(e.message)}<button type="button" class="cm-retry">重试搜索</button></div>`;
        resultBox.querySelector('.cm-retry').onclick = () => doSearch(kw);
      }
      return;
    }
    if (my !== seq) return;

    const others = TABS.filter(t => t.k !== 'song').map(t => t.k);
    if (!loadedCount('song')) {
      // 单曲为空：先把其余类型取回来再判断，避免先闪一下「没有结果」
      const extraResults = await Promise.allSettled(others.map(t => load(t, 0)));
      if (my !== seq) return;
      const hit = TABS.find(t => loadedCount(t.k) > 0);
      if (!hit) {
        const failed = extraResults.some(r => r.status === 'rejected');
        resultBox.innerHTML = failed
          ? `<div class="cm-empty cm-error" role="alert">部分搜索请求失败，无法确认是否有结果<button class="cm-retry" id="retrySearch" type="button">重试搜索</button></div>`
          : `<div class="cm-empty">没有找到「${esc(kw)}」的相关结果</div>`;
        resultBox.querySelector('#retrySearch')?.addEventListener('click', () => doSearch(kw));
        return;
      }
      S.cur = hit.k;
    }

    renderShell();
    if (!anyResult()) {
      resultBox.innerHTML = `<div class="cm-empty">没有找到「${esc(kw)}」的相关结果</div>`;
      return;
    }

    // 首屏已经出内容了，再在后台补齐其余类型（只为填 TAB 计数，不挡首屏）
    if (loadedCount('song')) {
      setTimeout(() => {
        if (my !== seq) return;
        others.forEach(t => {
          if (S.loaded[t]) return;
          load(t, 0).then(() => { if (my === seq) renderTabs(); }).catch(() => {});
        });
      }, 300);
    }
  }

  // 输入不触发搜索：只有回车（或点击热搜/历史词条这类明确动作）才发起请求
  el.querySelector('#searchGo').onclick = () => { const kw = (input.value || '').trim(); if (kw) { sugBox.innerHTML = ''; doSearch(kw); } else input.focus(); };
  input.addEventListener('input', () => {
    if (!(input.value || '').trim()) { seq++; S = null; resultBox.innerHTML = ''; sugBox.innerHTML = ''; renderHist(); renderHot(); return; }
    queueSuggest(input.value.trim());
  });
  input.addEventListener('keydown', e => {
    if (e.key === 'Enter') { const kw = (input.value || '').trim(); if (kw) { sugBox.innerHTML = ''; doSearch(kw); } }
  });

  /* ---------- 搜索建议：输入防抖 300ms，逐字不发请求 ---------- */
  let sugTimer = null, sugSeq = 0;
  function queueSuggest(kw) {
    clearTimeout(sugTimer);
    sugBox.innerHTML = '';
    if (!kw) return;
    sugTimer = setTimeout(async () => {
      const my = ++sugSeq;
      try {
        let words = [];
        try {
          const d = await api.searchSuggest(kw);
          words = (((d || {}).result || {}).allMatch || []).map(x => x.keyword).filter(Boolean);
        } catch { /* 回退 PC 端 */ }
        if (!words.length) {
          try {
            const d2 = await api.ncm('/search/suggest/pc', { keywords: kw });
            words = ((((d2 || {}).data || {}).suggests) || []).map(x => x.keyword || x.name).filter(Boolean);
          } catch { /* 两路都失败 */ }
        }
        if (my !== sugSeq) return;                       // 过期响应丢弃
        words = words.slice(0, 10);
        if (!words.length || (input.value || '').trim() !== kw) { sugBox.innerHTML = ''; return; }
        sugBox.innerHTML = `<div class="cm-chips">${words.map(w =>
          `<span class="cm-hist-k" data-k="${esc(w)}">${esc(w)}</span>`).join('')}</div>`;
        sugBox.querySelectorAll('[data-k]').forEach(c => {
          c.onclick = () => { input.value = c.dataset.k; sugBox.innerHTML = ''; doSearch(c.dataset.k); };
        });
      } catch { if (my === sugSeq) sugBox.innerHTML = ''; }
    }, 300);
  }

  // 从发现页等入口带关键词直达（#/search?q=xxx）
  const preset = ((params && params.query) || {}).q || '';
  if (preset.trim()) { input.value = preset.trim(); doSearch(preset.trim()); }

  setTimeout(() => input.focus(), 100);
}
