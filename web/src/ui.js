// UI 工具：转义、toast、时长格式、歌曲行渲染（含点赞/收藏状态）、对话框封装。
import { mdui } from './md.js';
import { api, auth, decorScale } from './api.js';

export const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

export function toast(message) {
  mdui.snackbar({ message, placement: 'bottom', closeable: false, timeout: 2200 });
}

export const fmtDur = ms => {
  const s = Math.round((ms || 0) / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
};

// 听歌时长（累计）文案：分钟以下按秒、分钟、小时逐级。全站统一口径（原 profile/square 各有一份且输出不一致）
export const fmtListen = ms => {
  const min = Math.floor((ms || 0) / 60000);
  if (min < 1) return `${Math.round((ms || 0) / 1000)} 秒`;
  if (min < 60) return `${min} 分钟`;
  const h = Math.floor(min / 60), m = min % 60;
  return m ? `${h} 小时 ${m} 分` : `${h} 小时`;
};

export const fmtCount = n => n >= 10000 ? (n / 10000).toFixed(1).replace(/\.0$/, '') + '万' : String(n ?? 0);

// 音质档位（高 → 低），与后端探测逻辑一致
export const QUALITY_TIERS = [
  { key: 'auto', label: '自动·最高' },
  // 后三个属「高解析度/多声道」：对设备解码与重采样要求高，部分机型会有杂音，
  // 标签里直接标出规格，让用户选之前就有预期。
  { key: 'jymaster', label: '超清母带', spec: '192kHz/24bit' },
  { key: 'jyeffect', label: '臻音全景声', spec: '96kHz/24bit' },
  { key: 'sky', label: '沉浸环绕声', spec: '6 声道' },
  { key: 'hires', label: '高清臻音' },
  { key: 'lossless', label: '无损 FLAC' },
  { key: 'exhigh', label: '极高 320k' },
  { key: 'standard', label: '标准 128k' },
];
export const tierLabel = k => (QUALITY_TIERS.find(t => t.key === k) || {}).label || k;

export function loadingBar() {
  return `<div class="cm-loading"><mdui-circular-progress></mdui-circular-progress></div>`;
}

// ---------- MD3 涟漪（事件委托，零模板侵入） ----------

const RIPPLE_SEL = [
  '.cm-song', '.cm-quick', '.cm-setting', '.cm-qm-item', '.cm-pl-pick-item',
  '.cm-plcard', '.cm-card', '.nav-ic', '.cm-mini-inner', '.cm-song-like',
  '.cm-song-more', '.cm-plmenu', '.cm-bindbanner', '.pl-btn', '#topAction', '#topSearch', '.cm-dl-apk-top', '.cm-ucard',
  '.pl-quality', '.pl-lyric-mode', '.cm-sec-more', '.cmt-del', '#cmtMore',
  '.cm-ava-wrap', '.cmt-like', '.cmt-reply', '.cmt-floor-btn',
  // 不含 mdui-chip 等影子 DOM 组件：光节点不进插槽会错位，且 mdui 自带涟漪
].join(',');

export function initRipple() {
  document.addEventListener('pointerdown', e => {
    if (e.button !== 0) return;                 // 右键/中键不产生涟漪
    const t = e.target.closest(RIPPLE_SEL);
    if (!t || !e.isPrimary) return;
    if (t.shadowRoot) return;                   // 影子 DOM 宿主：涟漪会落在错误位置
    const r = t.getBoundingClientRect();
    if (!r.width && !r.height) return;
    const d = Math.max(r.width, r.height) * 2.2;
    const rip = document.createElement('i');
    rip.className = 'cm-rip';
    rip.style.width = rip.style.height = d + 'px';
    rip.style.left = (e.clientX - r.left - d / 2) + 'px';
    rip.style.top = (e.clientY - r.top - d / 2) + 'px';
    t.appendChild(rip);
    // animationend 在部分内核/headless 下不触发，定时兜底清理
    rip.addEventListener('animationend', () => rip.remove(), { once: true });
    setTimeout(() => rip.remove(), 700);
  }, { passive: true });
}

// ---------- 封面取色（动态取色 / 渐变背景共用，带缓存） ----------

const _coverColorCache = new Map();

// 亮度收敛：让取出的主色在深浅色主题上都可读（L 收敛到 0.46~0.62）
function _clampL(hex) {
  let n = parseInt(hex.slice(1), 16);
  let r = ((n >> 16) & 255) / 255, g = ((n >> 8) & 255) / 255, b = (n & 255) / 255;
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b), l = (mx + mn) / 2;
  let h = 0, s = 0;
  if (mx !== mn) {
    const d = mx - mn;
    s = l > .5 ? d / (2 - mx - mn) : d / (mx + mn);
    if (mx === r) h = ((g - b) / d + (g < b ? 6 : 0));
    else if (mx === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h /= 6;
  }
  const cl = Math.min(.62, Math.max(.46, l));
  const q = cl < .5 ? cl * (1 + s) : cl + s - cl * s;
  const p = 2 * cl - q;
  const f = t => {
    if (t < 0) t += 1;
    if (t > 1) t -= 1;
    if (t < 1 / 6) return p + (q - p) * 6 * t;
    if (t < 1 / 2) return q;
    if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
    return p;
  };
  const to = x => Math.round(x * 255);
  return '#' + ((1 << 24) + (to(f(h + 1 / 3)) << 16) + (to(f(h)) << 8) + to(f(h - 1 / 3))).toString(16).slice(1);
}

function _shadeL(hex, dl) {
  let n = parseInt(hex.slice(1), 16);
  const r = ((n >> 16) & 255) / 255, g = ((n >> 8) & 255) / 255, b = (n & 255) / 255;
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b), l = Math.min(1, Math.max(0, (mx + mn) / 2 + dl));
  const k = l / ((mx + mn) / 2 || 1e-6);
  const to = x => Math.min(255, Math.round(x * 255 * k));
  return '#' + ((1 << 24) + (to(r) << 16) + (to(g) << 8) + to(b)).toString(16).slice(1);
}

// 色相（0~1），用于挑「与主色差得够远」的强调色
function _hue(r, g, b) {
  r /= 255; g /= 255; b /= 255;
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
  if (!d) return 0;
  let h;
  if (mx === r) h = ((g - b) / d) % 6;
  else if (mx === g) h = (b - r) / d + 2;
  else h = (r - g) / d + 4;
  h /= 6;
  return h < 0 ? h + 1 : h;
}

// 画布取色（crossOrigin 匿名；CDN 带 ACAO:* 不污染 canvas）
//
// 从「饱和度加权平均」改成**分桶直方图**：平均会把封面里的大面积灰底/白边一起拌进来，
// 结果普遍发灰发闷；分桶后按「频次 × 饱和度 × 中间亮度权重」取最高分桶，既鲜活又稳定，
// 再挑一个色相相距较远的桶做强调色（同色系双色会让界面发闷）。
function _extract(imgUrl) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      try {
        const cv = document.createElement('canvas');
        cv.width = cv.height = 32;
        const cx = cv.getContext('2d', { willReadFrequently: true });
        cx.drawImage(img, 0, 0, 32, 32);
        const d = cx.getImageData(0, 0, 32, 32).data;
        const buckets = new Map();
        for (let i = 0; i < d.length; i += 4) {
          const R = d[i], G = d[i + 1], B = d[i + 2], A = d[i + 3];
          if (A < 128) continue;                       // 透明像素不算
          const mx = Math.max(R, G, B), mn = Math.min(R, G, B);
          const lum = mx / 255;
          if (lum < .08 || lum > .97) continue;        // 死黑/纯白不算（多是边框留白）
          const sat = (mx - mn) / (mx || 1);
          const key = ((R >> 4) << 8) | ((G >> 4) << 4) | (B >> 4);
          const cur = buckets.get(key) || { r: 0, g: 0, b: 0, n: 0, sat: 0 };
          cur.r += R; cur.g += G; cur.b += B; cur.n++; cur.sat += sat;
          buckets.set(key, cur);
        }
        if (!buckets.size) return reject(new Error('empty'));
        const avg = v => ({ r: v.r / v.n, g: v.g / v.n, b: v.b / v.n, sat: v.sat / v.n, n: v.n });
        const ranked = [...buckets.values()].map(avg).map(c => {
          const lum = Math.max(c.r, c.g, c.b) / 255;
          // 中间亮度（.45~.65）最讨喜：太亮在深色播放页上发飘，太暗又看不出色
          c.score = c.n * (.35 + c.sat) * (1 - Math.abs(lum - .55) * 1.2);
          return c;
        }).sort((a, b) => b.score - a.score);
        const toHex = c => '#' + ((1 << 24) + (Math.round(c.r) << 16) + (Math.round(c.g) << 8) + Math.round(c.b)).toString(16).slice(1);
        const main = ranked[0];
        const h0 = _hue(main.r, main.g, main.b);
        const accent = ranked.find(c => {
          const dh = Math.abs(_hue(c.r, c.g, c.b) - h0);
          return Math.min(dh, 1 - dh) > .08;           // 色相至少差 ~29°
        }) || ranked[1] || main;
        resolve({ hex: toHex(main), accent: toHex(accent) });
      } catch (e) { reject(e); }   // canvas 污染等
    };
    img.onerror = () => reject(new Error('load'));
    img.src = imgUrl;
  });
}

// 取色入口：缩略图直连 → 后端代理兜底 → null（调用方用默认色组）
// 最近一次成功的色组：某张封面取不到色时沿用它，而不是掉回默认紫
// （默认紫闪一下比"沿用上一个封面色"更割裂；这也直接提高了动态取色的覆盖率）
let _lastPalette = null;

/** 主色 + 深浅档 + 强调色（强调色给进度条/高亮等次级元素用）。 */
function _paletteOf(raw) {
  const p = _clampL(raw.hex), a = _clampL(raw.accent || raw.hex);
  return {
    primary: p,
    deep: _shadeL(p, -.16),
    dark: _shadeL(p, -.30),
    accent: a,
    accentDeep: _shadeL(a, -.22),
  };
}

export async function coverColors(pic) {
  if (!pic) return _lastPalette;
  if (_coverColorCache.has(pic)) return _coverColorCache.get(pic);
  const thumb = pic + (pic.includes('?') ? '&' : '?') + 'param=240y240';
  const base = localStorage.getItem('cm.base') || '';
  // 多源依次尝试：直连缩略图 → 后端代理缩略图 → 直连原图 → 后端代理原图。
  // 任一条成功即可出图（热链/跨域被拦时后面的兜住），这也是"覆盖率"的一部分。
  const sources = [
    thumb,
    base ? `${base}/img?url=${encodeURIComponent(thumb)}` : null,
    pic,
    base ? `${base}/img?url=${encodeURIComponent(pic)}` : null,
  ].filter(Boolean);
  let raw = null;
  for (const src of sources) {
    try { raw = await _extract(src); break; } catch { /* 换下一个源 */ }
  }
  if (raw) _lastPalette = _paletteOf(raw);
  const out = raw ? _lastPalette : _lastPalette;    // 取不到就沿用上一个色组
  _coverColorCache.set(pic, out);
  return out;
}

// ---------- 字体大小（默认「标准」；**不跟随系统字号**，由用户在设置页决定） ----------
// 全站字号都写成 calc(Npx * var(--cm-fs))，所以这里只要改一个变量，正文/列表/歌词一起变，
// 而布局（行高间距）不受影响。系统/浏览器的文字缩放已在 CSS 里用 text-size-adjust:none 退出，
// 免得出同一份设置在不同机器上大小不一致。
export const FONT_SCALES = [
  { key: 'small', label: '小', f: 0.88, desc: '一屏放得下更多内容' },
  { key: 'standard', label: '标准', f: 1, desc: '默认，适合大多数手机' },
  { key: 'large', label: '大', f: 1.14, desc: '字更清楚，适合长辈或远看' },
  { key: 'xl', label: '特大', f: 1.28, desc: '最大档，注意列表会变高' },
];
const LS_FONT = 'cm.fontScale';

export function getFontScaleKey() {
  const k = localStorage.getItem(LS_FONT);
  return FONT_SCALES.some(x => x.key === k) ? k : 'standard';   // 默认标准
}
export function fontScaleFactor(key = getFontScaleKey()) {
  return (FONT_SCALES.find(x => x.key === key) || FONT_SCALES[1]).f;
}
/** 应用字号（返回实际生效的 key）。 */
export function applyFontScale(key = getFontScaleKey()) {
  const k = FONT_SCALES.some(x => x.key === key) ? key : 'standard';
  document.documentElement.style.setProperty('--cm-fs', String(fontScaleFactor(k)));
  return k;
}
export function setFontScaleKey(key) {
  localStorage.setItem(LS_FONT, key);
  return applyFontScale(key);
}
/** 启动时应用（放在首屏渲染前，避免先按标准画一遍再跳）。 */
export function bootFontScale() { applyFontScale(); }

// ---------- 配色方案（默认动态取色） ----------

export const COLOR_SCHEMES = [
  { key: 'dynamic', label: '动态取色（随封面）', color: null, swatch: 'gradient' },
  { key: 'purple', label: '紫罗兰', color: '#6750A4' },
  { key: 'blue', label: '海洋蓝', color: '#1565C0' },
  { key: 'teal', label: '青碧', color: '#00695C' },
  { key: 'green', label: '森林绿', color: '#2E7D32' },
  { key: 'pink', label: '樱花粉', color: '#AD1457' },
  { key: 'orange', label: '落日橙', color: '#E65100' },
];

export const getColorSchemeKey = () => localStorage.getItem('cm.colorScheme') || 'dynamic';
export function setColorSchemeKey(key) {
  localStorage.setItem('cm.colorScheme', key);
  if (key !== 'dynamic') applyColorScheme((COLOR_SCHEMES.find(s => s.key === key) || {}).color || '#6750A4');
}

export function applyColorScheme(hex, pal) {
  if (!hex) return;
  try { mdui.setColorScheme(hex); } catch { /* 部分内核 */ }
  const st = document.documentElement.style;
  st.setProperty('--cm-primary', hex);
  // 深色档 + 强调色：播放页背景、缓冲条底衬、进度条等若继续用写死色，
  // 换封面时就只有零散几处跟着变，观感割裂——这几个变量把取色"覆盖"到它们。
  if (pal) {
    if (pal.deep) st.setProperty('--cm-primary-deep', pal.deep);
    if (pal.dark) st.setProperty('--cm-primary-dark', pal.dark);
    if (pal.accent) st.setProperty('--cm-accent', pal.accent);
    if (pal.accentDeep) st.setProperty('--cm-accent-deep', pal.accentDeep);
  }
}

export function bootColorScheme() {
  const key = getColorSchemeKey();
  if (key !== 'dynamic') {
    applyColorScheme((COLOR_SCHEMES.find(s => s.key === key) || {}).color || '#6750A4');
  }
}

// ---------- 全局图片加载动画（骨架微光占位 → 渐显） ----------
// CSS 对 img:not(.ld) 显示微光占位；这里负责标记：load 事件（捕获期，img 的 load 不冒泡）
// + 缓存图兜底（插入时已 complete 的，load 事件可能已错过）+ error 也标记（停止微光，多数图另有 onerror 隐藏）
export function initImageFade() {
  const mark = t => { if (t && t.tagName === 'IMG') t.classList.add('ld'); };
  document.addEventListener('load', e => mark(e.target), true);
  document.addEventListener('error', e => mark(e.target), true);
  let pending = false;
  const sweep = () => {
    document.querySelectorAll('img').forEach(i => { if (i.complete) i.classList.add('ld'); });
  };
  const mo = new MutationObserver(() => {
    if (pending) return;
    pending = true;
    requestAnimationFrame(() => { pending = false; sweep(); });
  });
  mo.observe(document.body, { childList: true, subtree: true });
  sweep();
}

// ---------- 歌词默认字号：移动端「大」、平板「特大」（用户手动选过后尊重选择） ----------

export function defaultLyricSizeKey() {
  return window.matchMedia('(min-width: 600px)').matches ? 'xl' : 'l';
}

// ---------- 骨架屏（MD3 形状 + 微妙 shimmer） ----------

export const skelList = (n = 6) =>
  `<div class="cm-skel-list">${Array.from({ length: n }, () =>
    `<div class="cm-skel-song"><div class="sk sk-pic"></div><div class="cm-skel-main"><div class="sk sk-l1"></div><div class="sk sk-l2"></div></div></div>`).join('')}</div>`;

export const skelCards = (n = 8) =>
  `<div class="cm-hscroll">${Array.from({ length: n }, () =>
    `<div class="cm-skel-card"><div class="sk sk-cover"></div><div class="sk sk-l1"></div><div class="sk sk-l2"></div></div>`).join('')}</div>`;

export const skelGrid = (n = 6) =>
  `<div class="cm-plgrid">${Array.from({ length: n }, () =>
    `<div><div class="sk sk-cover"></div><div class="sk sk-l1"></div><div class="sk sk-l2"></div></div>`).join('')}</div>`;

export const skelComments = (n = 5) =>
  Array.from({ length: n }, () =>
    `<div class="cmt-item cmt-skel">
      <div class="sk cmt-skava"></div>
      <div class="cmt-main">
        <div class="sk cmt-sknick"></div>
        <div class="sk cmt-skl1"></div>
        <div class="sk cmt-skl2"></div>
      </div>
    </div>`).join('');

export const skelProfile = () =>
  `<div class="cm-skel-profile">
    <div class="cm-skel-head"><div class="sk sk-ava"></div><div class="cm-skel-main"><div class="sk sk-l1"></div><div class="sk sk-l2"></div></div></div>
    ${skelGrid(6)}
  </div>`;

// ---------- 歌曲列表渲染（状态一次拉取） ----------

const statusCache = new Map(); // ncm_id -> {liked, faved, count}

async function fetchStatus(ids) {
  const need = ids.filter(i => !statusCache.has(i));
  if (need.length) {
    try {
      const st = await api.songsStatus(need);
      need.forEach(i => statusCache.set(i, {
        liked: (st.liked || []).includes(i),
        faved: (st.faved || []).includes(i),
        count: (st.counts || {})[i] || 0,
      }));
    } catch { /* 未登录等情况：保持默认 */ }
  }
  return ids.map(i => statusCache.get(i) || { liked: false, faved: false, count: 0 });
}

export function setStatus(ncmId, patch) {
  const cur = statusCache.get(ncmId) || { liked: false, faved: false, count: 0 };
  statusCache.set(ncmId, Object.assign(cur, patch));
}
export const getStatus = ncmId => statusCache.get(ncmId) || { liked: false, faved: false, count: 0 };

/** 强制刷新单首歌的喜欢/计数状态（切歌用——直接读缓存会让红心停留在上一首状态）。 */
export async function ensureStatus(ncmId) {
  try {
    const st = await api.songsStatus([ncmId]);
    const s = {
      liked: (st.liked || []).includes(ncmId),
      faved: (st.faved || []).includes(ncmId),
      count: (st.counts || {})[ncmId] || 0,
    };
    statusCache.set(ncmId, s);
    return s;
  } catch {
    return getStatus(ncmId);
  }
}

// ---------- 网易云红心状态（会话级缓存，来自绑定账号 likelist） ----------

let _ncmLikedSet = null;

export async function ensureNcmLiked() {
  if (_ncmLikedSet === null) {
    try { _ncmLikedSet = new Set((await api.ncmLikelist()).ids || []); }
    catch { _ncmLikedSet = new Set(); }   // 未绑定/未同步：空集
  }
  return _ncmLikedSet;
}
export const isNcmLiked = id => !!(_ncmLikedSet && _ncmLikedSet.has(id));
export function setNcmLiked(id, on) { if (_ncmLikedSet) { on ? _ncmLikedSet.add(id) : _ncmLikedSet.delete(id); } }
export function resetNcmLiked() { _ncmLikedSet = null; }

/**
 * 喜欢/红心二级菜单：选择收录到 CurrentMusic 我喜欢 还是 网易云·我喜欢的音乐。
 * @param song    歌曲元数据
 * @param onChange 状态变化回调（用于刷新所在 UI）
 */
export function openLikeMenu(song, onChange) {
  if (!auth.token) return toast('登录后才能使用喜欢功能');
  const diag = mdui.dialog({
    headline: '收录到哪个「我喜欢」？',
    body: `<div class="cm-qm">
      <div class="cm-qm-item" id="lkCm"></div>
      <div class="cm-qm-item" id="lkNcm"></div>
    </div>`,
    actions: [{ text: '关闭' }],
  });

  function renderItems(ncmState) {
    const cm = getStatus(song.ncm_id);
    diag.querySelector('#lkCm').innerHTML =
      `<span class="material-icons-outlined ${cm.liked ? 'red' : ''}">${cm.liked ? 'favorite' : 'favorite_border'}</span>
       <div><div>CurrentMusic · 我喜欢的音乐</div>
       <div class="sub">${cm.liked ? '已收录，点击移出' : '未收录，点击加入'}</div></div>
       <span class="material-icons-outlined tail">${cm.liked ? 'check' : 'add'}</span>`;
    diag.querySelector('#lkNcm').innerHTML =
      `<span class="material-icons-outlined ${ncmState.on ? 'red' : ''}">${ncmState.on ? 'favorite' : 'favorite_border'}</span>
       <div><div>网易云音乐 APP · 我喜欢的音乐</div>
       <div class="sub">${ncmState.msg}</div></div>
       <span class="material-icons-outlined tail">${ncmState.tail}</span>`;
  }

  renderItems({ on: isNcmLiked(song.ncm_id), msg: '红心将同步到网易云 APP', tail: 'cloud_upload' });

  diag.querySelector('#lkCm').onclick = async () => {
    try {
      const r = await api.toggleLike(song);
      setStatus(song.ncm_id, { liked: r.on, count: r.count });
      renderItems({ on: isNcmLiked(song.ncm_id), msg: '红心将同步到网易云 APP', tail: 'cloud_upload' });
      onChange && onChange();
    } catch (e) { toast(e.message); }
  };

  diag.querySelector('#lkNcm').onclick = async () => {
    const on = isNcmLiked(song.ncm_id);
    try {
      await api.ncmLike(song, !on);
      setNcmLiked(song.ncm_id, !on);
      renderItems({ on: !on, msg: !on ? '已红心（网易云侧可能有数秒延迟）' : '已取消红心', tail: 'cloud_done' });
      onChange && onChange();
      toast(!on ? '已红心到网易云·我喜欢的音乐' : '已取消网易云红心');
    } catch (e) {
      toast(e.message.includes('未绑定') ? '请先在「我的」页绑定网易云账号' : e.message);
    }
  };

  // 预热网易云红心集合（首次打开菜单时后台加载，二次点击即有准确状态）
  ensureNcmLiked().then(() => {
    if (!diag.open) return;
    renderItems({ on: isNcmLiked(song.ncm_id), msg: '红心将同步到网易云 APP', tail: 'cloud_upload' });
  });
}

/**
 * 生成歌曲列表 HTML。
 * @param offset data-i 的起始下标——追加渲染时传已有条数，保证 data-i 始终是
 *               在**完整数组**里的绝对下标（点击行为依赖它）
 */
export async function songListHTML(songs, { removable = false, offset = 0 } = {}) {
  const st = await fetchStatus(songs.map(s => s.ncm_id));
  if (auth.token) ensureNcmLiked().catch(() => {});
  return `<div class="cm-songs">` + songs.map((s, i) => `
    <div class="cm-song" data-i="${offset + i}">
      ${s.pic ? `<img class="cm-song-pic" src="${esc(s.pic)}" loading="lazy">` : `<div class="cm-song-pic ph"><span class="material-icons-outlined">music_note</span></div>`}
      <div class="cm-song-main">
        <div class="cm-song-name">${esc(s.name)}</div>
        <div class="cm-song-sub">
          <span>${esc(s.artists || '未知歌手')}${s.album ? ' · ' + esc(s.album) : ''}</span>
          ${s.pop ? `<span class="cm-pop" title="网易云热度"><span class="mi">local_fire_department</span>${s.pop}</span>` : ''}
          ${st[i].count ? `<span class="cm-like-n" title="CurrentMusic 点赞数"><span class="mi">favorite</span>${fmtCount(st[i].count)}</span>` : ''}
        </div>
      </div>
      ${s.mv ? `<span class="cm-song-mv" data-act="mv" data-i="${offset + i}" title="播放 MV"><span class="material-icons-outlined">smart_display</span></span>` : ''}
      <span class="cm-song-dur">${fmtDur(s.duration)}</span>
      <span class="cm-song-like ${st[i].liked ? 'on' : ''}" data-act="like" data-i="${offset + i}" title="选择收录到哪个我喜欢">
        <span class="material-icons-outlined">${st[i].liked ? 'favorite' : 'favorite_border'}</span>
        ${isNcmLiked(s.ncm_id) ? '<i class="cm-ncmdot" title="已在网易云红心"></i>' : ''}
      </span>
      ${removable ? `<span class="cm-song-more" data-act="remove" data-i="${offset + i}"><span class="material-icons-outlined">remove_circle_outline</span></span>` : ''}
    </div>`).join('') + `</div>`;
}

/**
 * 渲染歌曲列表并绑定行为。
 * @param el    容器
 * @param songs 歌曲数组
 * @param opts  { onPlay(i), onRemove(i), from }
 *              from > 0 时进入**追加模式**：只把 songs[from..] 插到已有列表末尾，
 *              已有行原样保留（避免「显示更多」时整段重渲染导致已加载内容闪一下）。
 *              此时 songs 必须传**完整数组**——行上的 data-i 是完整数组的绝对下标，
 *              点击回调里的索引也据此对齐。
 * @returns 本次新增的行元素数组（供调用方做渐入动画）
 */
export async function renderSongList(el, songs, opts = {}) {
  const from = Math.max(0, opts.from || 0);
  if (!songs.length) {
    el.innerHTML = `<div class="cm-empty"><span class="material-icons-outlined">queue_music</span>还没有歌曲</div>`;
    return [];
  }
  const slice = songs.slice(from);
  if (!slice.length) return [];
  const html = await songListHTML(slice, { removable: !!opts.onRemove, offset: from });

  let rows;
  const wrap = el.querySelector('.cm-songs');
  if (from > 0 && wrap) {
    // 追加：把 <div class="cm-songs"> 里的内容取出后插到已有列表末尾
    const tmp = document.createElement('div');
    tmp.innerHTML = html;
    const inner = tmp.querySelector('.cm-songs');
    wrap.insertAdjacentHTML('beforeend', inner ? inner.innerHTML : '');
    rows = [...wrap.querySelectorAll('.cm-song')].slice(-slice.length);
  } else {
    el.innerHTML = html;
    rows = [...el.querySelectorAll('.cm-song')];
  }

  rows.forEach(row => {
    const i = +row.dataset.i;
    row.addEventListener('click', async e => {
      const act = e.target.closest('[data-act]');
      if (act && act.dataset.act === 'like') {
        e.stopPropagation();
        openLikeMenu(songs[i], () => {
          if (!row.isConnected) return;
          const st = getStatus(songs[i].ncm_id);
          const like = row.querySelector('[data-act="like"]');
          if (!like) return;
          like.classList.toggle('on', !!st.liked);
          like.querySelector('.material-icons-outlined').textContent = st.liked ? 'favorite' : 'favorite_border';
          like.querySelector('.cm-ncmdot')?.remove();
          if (isNcmLiked(songs[i].ncm_id)) like.insertAdjacentHTML('beforeend', '<i class="cm-ncmdot" title="已在网易云红心"></i>');
          const sub = row.querySelector('.cm-song-sub');
          if (sub) {
            sub.querySelector('.cm-like-n')?.remove();
            if (st.count) sub.insertAdjacentHTML('beforeend', `<span class="cm-like-n" title="CurrentMusic 点赞数"><span class="mi">favorite</span>${fmtCount(st.count)}</span>`);
          }
        });
        return;
      }
      if (act && act.dataset.act === 'remove') {
        e.stopPropagation();
        opts.onRemove && opts.onRemove(i);
        return;
      }
      if (act && act.dataset.act === 'mv') {
        // 用事件解耦：ui.js 不能 import player.js（player.js 已 import ui.js，会成环）
        e.stopPropagation();
        document.dispatchEvent(new CustomEvent('cm-playmv', { detail: songs[i] }));
        return;
      }
      opts.onPlay && opts.onPlay(i);
    });
  });
  return rows;
}

// ---------- 对话框 ----------
// 注意：mdui 2 的 dialog 不支持 onAction 选项，确认逻辑必须挂在
// action 的 onClick 上（返回 promise 自动在完成后关闭，返回 false 不关闭）。

export function promptDialog({ title, label, value = '', placeholder = '', type = 'text', allowEmpty = false, onOk }) {
  const diag = mdui.dialog({
    headline: title,
    body: `<mdui-text-field label="${esc(label)}" variant="outlined" value="${esc(value)}" placeholder="${esc(placeholder)}" ${type === 'password' ? 'type="password"' : ''} style="width:100%"></mdui-text-field>`,
    actions: [
      { text: '取消' },
      {
        text: '确定',
        onClick: () => {
          const v = diag.querySelector('mdui-text-field').value.trim();
          if (!v && !allowEmpty) { toast('内容不能为空'); return false; }
          return Promise.resolve().then(() => onOk(v)).catch(e => { toast(e.message); return false; });
        },
      },
    ],
  });
  return diag;
}

export function confirmDialog({ title, body = '', onOk = () => {} }) {
  return mdui.dialog({
    headline: title, body,
    actions: [
      { text: '取消' },
      { text: '确定', onClick: () => Promise.resolve().then(onOk).catch(e => {
        toast(`操作失败：${e.message}`);
        return false; // 异步提交失败时留在弹窗里，允许重新确认
      }) },
    ],
  });
}

// 头像挂件叠加层：铺满头像盒并居中，按素材实测比例放大，使圆环外沿正好落在头像边缘。
// 直接给 <img> 像素尺寸（而不是 transform:scale）——外层 .cm-ava-wrap 有 overflow:hidden，
// transform 只是视觉溢出仍会被裁，改尺寸则不存在越界。
export function decorHTML(user, size) {
  const id = user && user.avatarDecoration;
  const url = id && api.decorUrl(id);
  if (!url) return '';
  const s = Math.round(size * decorScale(id) * 100) / 100;
  return `<span class="cm-decor" style="width:${size}px;height:${size}px">`
    + `<img src="${esc(url)}" width="${s}" height="${s}" alt="" aria-hidden="true"></span>`;
}

export function avatarHTML(user, size = 72) {
  const url = user && user.avatar && api.avatarUrl(user.avatar);
  const decor = decorHTML(user, size);
  const inner = url
    ? `<img class="cm-avatar" src="${esc(url)}?v=${Date.now() % 86400000}" style="width:${size}px;height:${size}px">`
    : `<div class="cm-avatar ph" style="width:${size}px;height:${size}px;font-size:${size / 2.6}px">${esc((user && user.nickname ? user.nickname[0] : '?').toUpperCase())}</div>`;
  if (!decor) return inner;
  // 有挂件时外层补一个定位盒（原先直接返回 img，调用方的 flex 布局不受影响）
  return `<span class="cm-ava-box" style="width:${size}px;height:${size}px">${inner}${decor}</span>`;
}
