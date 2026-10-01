// 新碟与数字专辑（阶段三新增）
// 新碟：/album/new（分地区翻页）、/album/newest（最新）
// 数字专辑：/album/list（在售列表）、/album/songsaleboard（销量榜）、
//          /album/list/style（语种风格馆）、/album/detail（数字专辑详情）
// 我的：/album/sublist（收藏的专辑，T1）
// 说明：/album/detail 与 /album/list/style 是**数字专辑**（vipmall）接口，参数 id 必须来自
//      /album/list 返回的 albumId；传普通音乐专辑 id 会被上游判 404（已踩过）。
import { api, auth } from '../api.js';
import { esc, toast, skelGrid, fmtCount } from '../ui.js';

const KEY = 'cm.albumsTab';
const PAGE = 24;
const AREA_NEW = [{ k: 'ALL', l: '全部' }, { k: 'ZH', l: '华语' }, { k: 'EA', l: '欧美' }, { k: 'KR', l: '韩国' }, { k: 'JP', l: '日本' }];
const AREA_STYLE = [{ k: 'Z_H', l: '华语' }, { k: 'E_A', l: '欧美' }, { k: 'KR', l: '韩国' }, { k: 'JP', l: '日本' }];

const albumCard = a => `
  <div class="cm-plcard cm-album-card" data-aid="${a.id}">
    <div class="cm-plcover">${(a.picUrl || a.pic) ? `<img src="${esc(a.picUrl || a.pic)}?param=300y300" loading="lazy" onerror="this.remove()">` : ''}<span class="material-icons-outlined">album</span></div>
    <div class="cm-plname">${esc(a.name)}</div>
    <div class="cm-plsub">${esc((a.artist && a.artist.name) || a.artistName || '')}${a.size ? ` · ${a.size} 首` : ''}</div>
  </div>`;

const productCard = p => `
  <div class="cm-plcard cm-product-card" data-pid="${p.albumId}">
    <div class="cm-plcover">${p.coverUrl ? `<img src="${esc(p.coverUrl)}?param=300y300" loading="lazy" onerror="this.remove()">` : ''}<span class="material-icons-outlined">album</span></div>
    <div class="cm-plname">${esc(p.albumName || p.name || '')}</div>
    <div class="cm-plsub">${esc(p.artistName || '')}${p.price || p.saleNum ? ` · ${p.price ? '¥' + (p.price / 100).toFixed(2) : ''}${p.saleNum ? ` 售 ${fmtCount(p.saleNum)}` : ''}` : ''}</div>
  </div>`;

export async function render(el, params = {}) {
  const tab = params.tab || sessionStorage.getItem(KEY) || 'new';
  const TABS = [
    { k: 'new', l: '新碟上架' },
    { k: 'newest', l: '最新专辑' },
    { k: 'digital', l: '数字专辑' },
    { k: 'style', l: '语种风格馆' },
    { k: 'sub', l: '我的收藏' },
    { k: 'paid', l: '已购数字专辑' },
  ];
  el.innerHTML = `
    <div class="cm-sqtabs" id="alTabs">
      ${TABS.map(t => `<button class="cm-sqtab${t.k === tab ? ' on' : ''}" data-k="${t.k}">${t.l}</button>`).join('')}
    </div>
    <div id="alFilter"></div>
    <div id="alBody">${skelGrid(8)}</div>`;
  el.querySelectorAll('#alTabs .cm-sqtab').forEach(b => {
    b.onclick = () => {
      sessionStorage.setItem(KEY, b.dataset.k);
      render(el, { tab: b.dataset.k });
    };
  });
  const body = el.querySelector('#alBody');
  const filter = el.querySelector('#alFilter');

  const bindAlbumCards = () => {
    body.querySelectorAll('.cm-album-card').forEach(c => {
      c.onclick = () => { location.hash = `#/album/${c.dataset.aid}`; };
    });
    body.querySelectorAll('.cm-product-card').forEach(c => {
      c.onclick = () => openDigitalAlbum(c.dataset.pid);
    });
  };

  const withMore = (offset, len, load) => {
    body.querySelector('#alMore')?.remove();
    if (len < PAGE) return;
    body.insertAdjacentHTML('beforeend',
      '<div class="cm-sq-more" id="alMore"><mdui-button variant="tonal">加载更多</mdui-button></div>');
    body.querySelector('#alMore mdui-button').onclick = async ev => {
      const btn = ev.currentTarget;
      btn.loading = true;
      try { await load(offset + len); } catch (e) { toast(e.message); } finally { btn.loading = false; }
    };
  };

  if (tab === 'new') {
    let area = params.area || 'ALL', offset = 0;
    const chips = () => {
      filter.innerHTML = `<div class="cm-plsort"><span class="cm-plsort-l">地区</span>
        ${AREA_NEW.map(a => `<mdui-chip ${a.k === area ? 'selected' : ''} data-a="${a.k}">${a.l}</mdui-chip>`).join('')}</div>`;
      filter.querySelectorAll('[data-a]').forEach(c => {
        c.onclick = () => { area = c.dataset.a; chips(); load(0).catch(e => toast(e.message)); };
      });
    };
    const load = async off => {
      const d = await api.ncm('/album/new', { area, limit: PAGE, offset: off });
      const list = d.albums || [];
      if (!off) body.innerHTML = '';
      body.insertAdjacentHTML('beforeend', list.map(albumCard).join(''));
      offset = off + list.length;
      bindAlbumCards();
      withMore(offset, list.length, load);
    };
    chips();
    try { await load(0); } catch (e) { body.innerHTML = `<div class="cm-empty">加载失败：${esc(e.message)}</div>`; }
    return;
  }

  if (tab === 'newest') {
    try {
      const d = await api.ncm('/album/newest');
      const list = (d.albums || []).slice(0, 60);
      body.innerHTML = list.length ? list.map(albumCard).join('') : '<div class="cm-empty small">暂无新专辑</div>';
      bindAlbumCards();
    } catch (e) { body.innerHTML = `<div class="cm-empty">加载失败：${esc(e.message)}</div>`; }
    return;
  }

  if (tab === 'digital') {
    let area = params.area || 'Z_H', offset = 0, mode = params.mode || 'list';
    let saleType = 'daily', saleYear = new Date().getFullYear();
    const SALE = [{ k: 'daily', l: '日榜' }, { k: 'week', l: '周榜' }, { k: 'year', l: '年榜' }, { k: 'total', l: '总榜' }];
    const chips = () => {
      filter.innerHTML = `
        <div class="cm-plsort"><span class="cm-plsort-l">视图</span>
          <mdui-chip ${mode === 'list' ? 'selected' : ''} data-m="list">在售</mdui-chip>
          <mdui-chip ${mode === 'sale' ? 'selected' : ''} data-m="sale">销量榜</mdui-chip></div>
        ${mode === 'list' ? `<div class="cm-plsort"><span class="cm-plsort-l">地区</span>
          ${AREA_STYLE.map(a => `<mdui-chip ${a.k === area ? 'selected' : ''} data-a="${a.k}">${a.l}</mdui-chip>`).join('')}</div>`
        : `<div class="cm-plsort"><span class="cm-plsort-l">周期</span>
          ${SALE.map(x => `<mdui-chip ${x.k === saleType ? 'selected' : ''} data-st="${x.k}">${x.l}</mdui-chip>`).join('')}</div>
          ${saleType === 'year' ? `<div class="cm-plsort"><span class="cm-plsort-l">年份</span>
          ${[0, 1, 2].map(d => `<mdui-chip ${saleYear === new Date().getFullYear() - d ? 'selected' : ''} data-sy="${new Date().getFullYear() - d}">${new Date().getFullYear() - d}</mdui-chip>`).join('')}</div>` : ''}`}`;
      filter.querySelectorAll('[data-m]').forEach(c => {
        c.onclick = () => { mode = c.dataset.m; chips(); load(0).catch(e => toast(e.message)); };
      });
      filter.querySelectorAll('[data-a]').forEach(c => {
        c.onclick = () => { area = c.dataset.a; chips(); load(0).catch(e => toast(e.message)); };
      });
      filter.querySelectorAll('[data-st]').forEach(c => {
        c.onclick = () => { saleType = c.dataset.st; chips(); load(0).catch(e => toast(e.message)); };
      });
      filter.querySelectorAll('[data-sy]').forEach(c => {
        c.onclick = () => { saleYear = Number(c.dataset.sy); chips(); load(0).catch(e => toast(e.message)); };
      });
    };
    const load = async off => {
      if (mode === 'sale') {
        // 销量榜（/album/songsaleboard）：type ∈ daily/week/total/year，
        // 只有 year 需要 year 参数（写错成 yearly 会被上游判 404，已踩过）
        const params = { albumType: 0, type: saleType };
        if (saleType === 'year') params.year = saleYear;
        const d = await api.ncm('/album/songsaleboard', params);
        const list = d.products || d.saleBoardList || [];
        body.innerHTML = list.length ? list.map(productCard).join('')
          : '<div class="cm-empty small">暂无销量数据</div>';
        bindAlbumCards();
        return;
      }
      const d = await api.ncm('/album/list', { area, limit: PAGE, offset: off });
      const list = d.products || [];
      if (!off) body.innerHTML = '';
      body.insertAdjacentHTML('beforeend', list.map(productCard).join(''));
      bindAlbumCards();
      withMore(off + list.length, list.length, load);
    };
    chips();
    try { await load(0); } catch (e) { body.innerHTML = `<div class="cm-empty">加载失败：${esc(e.message)}</div>`; }
    return;
  }

  if (tab === 'style') {
    let area = params.area || 'Z_H', offset = 0;
    const chips = () => {
      filter.innerHTML = `<div class="cm-plsort"><span class="cm-plsort-l">语种</span>
        ${AREA_STYLE.map(a => `<mdui-chip ${a.k === area ? 'selected' : ''} data-a="${a.k}">${a.l}</mdui-chip>`).join('')}</div>`;
      filter.querySelectorAll('[data-a]').forEach(c => {
        c.onclick = () => { area = c.dataset.a; chips(); load(0).catch(e => toast(e.message)); };
      });
    };
    const load = async off => {
      const d = await api.ncm('/album/list/style', { area, limit: PAGE, offset: off });
      const list = d.albumProducts || d.products || [];
      if (!off) body.innerHTML = '';
      body.insertAdjacentHTML('beforeend', list.map(productCard).join(''));
      bindAlbumCards();
      // 上游给的是 hasNextPage 而不是条数，按它决定是否还有下一页
      if (d.hasNextPage) {
        body.querySelector('#alMore')?.remove();
        body.insertAdjacentHTML('beforeend',
          '<div class="cm-sq-more" id="alMore"><mdui-button variant="tonal">加载更多</mdui-button></div>');
        body.querySelector('#alMore mdui-button').onclick = async ev => {
          const btn = ev.currentTarget;
          btn.loading = true;
          try { await load(off + list.length); } catch (e) { toast(e.message); } finally { btn.loading = false; }
        };
      } else {
        body.querySelector('#alMore')?.remove();
      }
    };
    chips();
    try { await load(0); } catch (e) { body.innerHTML = `<div class="cm-empty">加载失败：${esc(e.message)}</div>`; }
    return;
  }

  if (tab === 'paid') {
    // 已购数字专辑：/digitalAlbum/purchased（T1）+ 详情/下单/销量
    if (!auth.token) {
      body.innerHTML = `<div class="cm-login-tip page">
        <span class="material-icons-outlined" style="font-size:calc(40px * var(--cm-fs, 1))">album</span>
        <div>已购数字专辑与你的网易云账号绑定，需先登录并绑定</div>
        <mdui-button variant="filled" href="#/user">去绑定网易云</mdui-button></div>`;
      return;
    }
    let offset = 0;
    const load = async off => {
      const d = await api.ncm('/digitalAlbum/purchased', { limit: 20, offset: off });
      const list = d.data || d.albums || [];
      if (!off) body.innerHTML = '';
      if (!list.length && !off) { body.innerHTML = '<div class="cm-empty small">还没有购买数字专辑</div>'; return; }
      body.insertAdjacentHTML('beforeend', list.map(a => `
        <div class="cm-lm-item" data-daid="${a.albumId || a.id || ''}">
          <div class="cm-lm-lyric">${esc(a.albumName || a.name || '')}</div>
          <div class="cm-lm-meta"><span>${esc(a.artistName || '')}</span>
            <span><i class="cm-si-act" data-ddetail="1">详情</i><i class="cm-si-act" data-dbuy="1">购买</i></span></div>
        </div>`).join(''));
      body.querySelectorAll('[data-ddetail]').forEach(x => {
        x.onclick = () => digitalAlbumDialog(x.closest('[data-daid]').dataset.daid, false);
      });
      body.querySelectorAll('[data-dbuy]').forEach(x => {
        x.onclick = () => digitalAlbumDialog(x.closest('[data-daid]').dataset.daid, true);
      });
      if (list.length >= 20) {
        body.querySelector('#daMore')?.remove();
        body.insertAdjacentHTML('beforeend',
          '<div class="cm-sq-more" id="daMore"><mdui-button variant="tonal">加载更多</mdui-button></div>');
        body.querySelector('#daMore mdui-button').onclick = async ev => {
          const btn = ev.currentTarget;
          btn.loading = true;
          try { offset = off + list.length; await load(offset); } catch (e) { toast(e.message); } finally { btn.loading = false; }
        };
      }
    };
    try { await load(0); } catch (e) {
      body.innerHTML = /401|绑定/.test(e.message) ? '<div class="cm-empty">需先绑定网易云账号</div>'
        : `<div class="cm-empty">加载失败：${esc(e.message)}</div>`;
    }
    return;
  }

  // 我的专辑收藏（T1）
  if (!auth.token) {
    body.innerHTML = `<div class="cm-login-tip page">
      <span class="material-icons-outlined" style="font-size:calc(40px * var(--cm-fs, 1))">cloud_off</span>
      <div>收藏的专辑与你的网易云账号同步，需先绑定</div>
      <mdui-button variant="filled" href="#/user">去绑定网易云</mdui-button></div>`;
    return;
  }
  let offset = 0;
  const load = async off => {
    const d = await api.ncm('/album/sublist', { limit: 25, offset: off });
    const list = d.data || d.albums || [];
    if (!off) body.innerHTML = '';
    if (!list.length && !off) { body.innerHTML = '<div class="cm-empty small">还没有收藏专辑</div>'; return; }
    body.insertAdjacentHTML('beforeend', list.map(a => albumCard({
      id: a.id, name: a.name, pic: a.picUrl || a.blurPicUrl, artist: a.artist, size: a.size,
    })).join(''));
    bindAlbumCards();
    if (d.hasMore) {
      body.querySelector('#alMore')?.remove();
      body.insertAdjacentHTML('beforeend',
        '<div class="cm-sq-more" id="alMore"><mdui-button variant="tonal">加载更多</mdui-button></div>');
      body.querySelector('#alMore mdui-button').onclick = async ev => {
        const btn = ev.currentTarget;
        btn.loading = true;
        try { await load(off + list.length); } catch (e) { toast(e.message); } finally { btn.loading = false; }
      };
    }
  };
  try { await load(0); } catch (e) {
    body.innerHTML = /401|绑定/.test(e.message) ? '<div class="cm-empty">需先绑定网易云账号</div>'
      : `<div class="cm-empty">加载失败：${esc(e.message)}</div>`;
  }
}

/** 数字专辑详情（/album/detail，参数是 /album/list 给的 albumId，不是音乐专辑 id）。 */
function openDigitalAlbum(albumId) {
  const diag = document.createElement('mdui-dialog');
  diag.headline = '数字专辑';
  diag.innerHTML = '<div id="daBox"><div class="cm-loading" style="padding:24px 0"><mdui-circular-progress></mdui-circular-progress></div></div>';
  document.body.appendChild(diag);
  diag.open = true;
  api.ncm('/album/detail', { id: albumId }).then(d => {
    const p = d.product || d.data || {};
    const info = p.extInfo || {};
    const box = diag.querySelector('#daBox');
    const price = p.price !== undefined ? `¥${(p.price / 100).toFixed(2)}` : '—';
    box.innerHTML = `
      <div class="cm-detail-head">
        <div class="cm-detail-cover">${p.coverUrl ? `<img src="${esc(p.coverUrl)}?param=300y300" onerror="this.remove()">` : ''}</div>
        <div>
          <h2>${esc(p.albumName || p.name || '数字专辑')}</h2>
          <div class="cm-detail-sub">${esc(p.artistName || '')}</div>
          <div class="cm-detail-sub">价格 ${price}${p.saleNum ? ` · 已售 ${fmtCount(p.saleNum)}` : ''}</div>
          <div class="cm-detail-sub">${esc(info.saleEntranceTips || info.tips || '')}</div>
          <div class="cm-detail-actions">
            ${p.albumId ? `<mdui-button variant="tonal" id="daOpen">看专辑曲目</mdui-button>` : ''}
          </div>
        </div>
      </div>`;
    const open = box.querySelector('#daOpen');
    if (open) open.onclick = () => { diag.open = false; location.hash = `#/album/${p.albumId}`; };
  }).catch(e => {
    const box = diag.querySelector('#daBox');
    if (box) box.innerHTML = `<div class="cm-empty small">数字专辑详情加载失败：${esc(e.message)}</div>`;
  });
}

/** 数字专辑（付费）详情 / 下单 / 销量：/digitalAlbum/{detail,ordering,sales}。 */
function digitalAlbumDialog(id, buyMode) {
  const diag = document.createElement('mdui-dialog');
  diag.headline = '数字专辑';
  diag.innerHTML = '<div id="daPaidBox"><div class="cm-loading" style="padding:20px 0"><mdui-circular-progress></mdui-circular-progress></div></div>';
  document.body.appendChild(diag);
  diag.open = true;
  const box = diag.querySelector('#daPaidBox');
  Promise.allSettled([
    api.ncm('/digitalAlbum/detail', { id }),
    api.ncm('/digitalAlbum/sales', { ids: String(id) }),
  ]).then(([detailR, salesR]) => {
    const d = detailR.status === 'fulfilled' ? (detailR.value.data || detailR.value) : {};
    const p = d.album || d.product || d;
    const sales = salesR.status === 'fulfilled' ? (salesR.value.data || salesR.value) : null;
    box.innerHTML = `
      <div class="cm-ev-text" style="font-weight:650">${esc(p.albumName || p.name || '')}</div>
      <div class="cm-detail-sub">${esc(p.artistName || '')}${p.price !== undefined ? ` · ¥${(Number(p.price) / 100).toFixed(2)}` : ''}</div>
      ${sales ? `<div class="cm-kv-sub"><b>销量</b>${JSON.stringify(sales).slice(0, 200)}</div>` : ''}
      <div class="cm-pe-acts">
        <mdui-button variant="filled" id="daBuy">购买</mdui-button>
      </div>
      <div class="cm-pe-hint" id="daBuyHint">购买会调用 /digitalAlbum/ordering（T2：你的账号 + confirm=1）</div>`;
    box.querySelector('#daBuy').onclick = () => {
      if (!buyMode && !confirm('现在购买这张数字专辑？')) return;
      const hint = box.querySelector('#daBuyHint');
      hint.textContent = '下单中…';
      api.ncm('/digitalAlbum/ordering', { id, payment: 0, quantity: 1, confirm: 1 })
        .then(() => { hint.textContent = '已下单（支付在上游完成，本页不代付）'; toast('已下单'); })
        .catch(e => { hint.textContent = `下单失败：${/绑定|401/.test(e.message) ? '需先绑定网易云账号' : e.message}`; });
    };
  }).catch(e => {
    if (box) box.textContent = `详情加载失败：${e.message}`;
  });
}

/** 供专辑详情页复用的「播放权限」摘要（/album/privilege）。 */
export async function privilegeSummary(id) {
  const d = await api.ncm('/album/privilege', { id });
  const list = d.data || [];
  if (!list.length) return '';
  const paid = list.filter(x => x.fee === 1).length;
  const maxBr = Math.max(...list.map(x => Number(x.maxbr) || 0));
  const bits = [`${list.length} 首`];
  if (paid) bits.push(`${paid} 首付费`);
  if (maxBr) bits.push(`最高 ${Math.round(maxBr / 1000)}kbps`);
  return bits.join(' · ');
}
