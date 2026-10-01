// 播放器 UI：底部迷你条 + 全屏播放页（封面/歌词/进度/音质/点赞/收藏/加入歌单）。
import { mdui } from './md.js';
import { createWaveform, WAVE_STYLE } from './waveform.js';
import { waveStyle, waveTilt } from './customize.js';
import { api, auth, settings } from './api.js';
import { esc, toast, fmtDur, tierLabel, QUALITY_TIERS, getStatus, setStatus, ensureStatus, openLikeMenu, isNcmLiked, ensureNcmLiked, skelComments, coverColors, defaultLyricSizeKey, getColorSchemeKey, applyColorScheme } from './ui.js';
import { player, wave, on } from './player.js';
import { castIconHTML, openCastDialog } from './cast.js';

// Apple 风媒体图标（仿 SF Symbols：play.fill / pause.fill / backward.end.fill / forward.end.fill）。
// 特征是**实心 + 圆角、无外圈**；Material 的 play_circle/pause_circle 自带圆圈，
// 换成 play_arrow/pause 虽无圈但棱角生硬，故直接用 SVG 画出圆角。
// stroke+linejoin:round 把三角形的角变圆（Apple 的标志性细节），fill=currentColor 随文字色。
const AM_ICON = {
  play: '<svg viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M8 5.1v13.8L19.2 12z"/></svg>',
  pause: '<svg viewBox="0 0 24 24" fill="currentColor"><rect x="6.4" y="5" width="3.7" height="14" rx="1.5"/><rect x="13.9" y="5" width="3.7" height="14" rx="1.5"/></svg>',
  prev: '<svg viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"><rect x="5" y="5" width="2.1" height="14" rx="1.05" stroke="none"/><path d="M19 5.6v12.8L9.6 12z"/></svg>',
  next: '<svg viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"><rect x="16.9" y="5" width="2.1" height="14" rx="1.05" stroke="none"/><path d="M5 5.6v12.8L14.4 12z"/></svg>',
};
/** 播放键内容：加载中仍用 Material 的沙漏（瞬态提示，不属于走带图标集） */
function amPlayIcon() {
  return player.loading
    ? '<span class="material-icons-outlined">hourglass_empty</span>'
    : (player.isPlaying() ? AM_ICON.pause : AM_ICON.play);
}


// ---------- 网易云评论表情：方括号码 → emoji ----------
// NCM 客户端表情在接口里是纯文本码（如 [爱心]/[呲牙]），网页端直接显示会看到
// 原始方括号。这里映射为语义等价的通用 emoji（选用 Chrome58 即可渲染的早期码位）。

const overlay = () => document.getElementById('playerOverlay');

// 播放页波形：由**真实音频频谱**驱动（见 waveform.js），占位只有一张 canvas。
// 之前这里是一组固定高度的柱子 + CSS 呼吸动画 —— 那与音乐无关，只是看起来在动；
// 现在只有拿到真实频谱才画起伏，拿不到就画静默基线（不假装有律动）。
let waveCtl = null;          // 当前播放页的波形控制器（关闭时销毁）
function waveformHTML() {
  return `<div class="pl-waveform" id="plWaveform" aria-hidden="true"><canvas id="plWaveCanvas"></canvas></div>`;
}

// ---------- 迷你条 ----------

function renderMini() {
  const el = document.getElementById('mini');
  if (!player.meta) { el.innerHTML = ''; el.style.display = 'none'; return; }
  el.style.display = '';
  const m = player.meta;
  el.innerHTML = `
    <div class="cm-mini-prog"><i id="miniBuf"></i><i id="miniProg"></i></div>
    <div class="cm-mini-inner">
      ${m.pic ? `<img src="${esc(m.pic)}">` : `<div class="cm-mini-ph"><span class="material-icons-outlined">music_note</span></div>`}
      <div class="cm-mini-text">
        <div class="cm-mini-name">${esc(m.name)}</div>
        <div class="cm-mini-sub">${esc(m.artists)}${player.urlInfo ? ' · ' + tierLabel(player.urlInfo.level) : player.loading ? ' · 解析音质中…' : ''}</div>
      </div>
      <span class="cm-mini-btn" id="miniPlay"><span class="material-icons-outlined">${player.isPlaying() ? 'pause_circle' : 'play_circle'}</span></span>
      <span class="cm-mini-btn" id="miniNext"><span class="material-icons-outlined">skip_next</span></span>
    </div>`;
  el.querySelector('#miniPlay').onclick = e => { e.stopPropagation(); player.toggle(); };
  el.querySelector('#miniNext').onclick = e => { e.stopPropagation(); player.next(); };
  el.querySelector('.cm-mini-inner').onclick = () => openFull();
}

on('song', renderMini);
on('state', renderMini);

on('time', () => {
  // 进度线紧贴迷你播放条上缘。位置/总长一律走 player.posMs()/durMs()：
  // 投屏时它们来自设备上报，直接读 audio 会永远是 0（进度条与歌词卡住）。
  const pos = player.posMs(), dur = player.durMs();
  const bar = document.getElementById('miniProg');
  if (bar) bar.style.width = dur ? (pos / dur * 100) + '%' : '0%';
  const mbuf = document.getElementById('miniBuf');
  if (mbuf) mbuf.style.width = player.bufferPct() + '%';
  const pbuf = document.getElementById('plBuf');
  if (pbuf) pbuf.style.width = player.bufferPct() + '%';
  const pfill = document.getElementById('plFill');
  if (pfill && dur) pfill.style.width = (pos / dur * 100) + '%';
  syncLyric();
  const t = document.getElementById('plCur'), d = document.getElementById('plDur'), s = document.getElementById('plSeek');
  if (t) t.textContent = fmtDur(pos);
  if (d && dur) d.textContent = fmtDur(dur);
  if (s && !s.dataset.drag) s.value = dur ? (pos / dur) * 100 : 0;
});

// ---------- 全屏播放页 ----------

let lyricLines = [];
let lyricLoadedFor = null;

async function loadLyric() {
  const m = player.meta;
  if (!m) return;
  // 关键：不清空旧歌词——让旧内容在 fetch 期间保持可见（消除"突然消失又出现"的空窗）。
  // 新数据到达后才一次性替换并重置行号。
  const oldLines = lyricLines;
  lyricLoadedFor = m.ncm_id;
  try {
    const d = await api.lyric(m.ncm_id);
    if (!player.meta || player.meta.ncm_id !== m.ncm_id) return;
    lyricLines = (d.lines || []).filter(l => l.txt);
  } catch {
    lyricLoadedFor = m.ncm_id + ':err';
    lyricLines = [];            // 仅在获取失败时清空
  }
  if (lyricLines !== oldLines) {
    curLyricIdx = -1;           // 行集变了，重置行号以触发首次滚动
    karaokeSpans = null;
  }
  renderLyric();
  syncLyric();
}

const PLAY_MODES = [
  { key: 'list', icon: 'repeat', label: '列表循环' },
  { key: 'one', icon: 'repeat_one', label: '单曲循环' },
  { key: 'order', icon: 'trending_flat', label: '顺序播放' },
  { key: 'shuffle', icon: 'shuffle', label: '随机播放' },
];

// ---------- 歌词多语言模式 ----------

const LYRIC_MODES = [
  { key: 'bi', label: '双语' },          // 原文 + 翻译
  { key: 'orig', label: '原文' },
  { key: 'trans', label: '翻译' },
  { key: 'roma', label: '罗马音' },
  { key: 'orig-roma', label: '原文+罗马' },
];
let lyricMode = localStorage.getItem('cm.lyricMode') || 'bi';

function lineParts(l) {
  const mode = lyricMode;
  let main = l.txt, sub = '';
  if (mode === 'trans') main = l.trans || l.txt;
  else if (mode === 'roma') main = l.roma || l.txt;
  else if (mode === 'bi') sub = l.trans || '';
  else if (mode === 'orig-roma') sub = l.roma || '';
  return { main: main || l.txt, sub };
}

const LYRIC_SIZES = [
  { key: 's', label: 'Aa·小', fs: 13 },
  { key: 'm', label: 'Aa·标准', fs: 15 },
  { key: 'l', label: 'Aa·大', fs: 17 },
  { key: 'xl', label: 'Aa·特大', fs: 19 },
];
let lyricSizeKey = localStorage.getItem('cm.lyricSize') || defaultLyricSizeKey();
let bgGradOn = localStorage.getItem('cm.bgGrad') === '1';   // 沉浸模式（原动态渐变背景），默认关闭
let lyricKaraoke = localStorage.getItem('cm.karaoke') !== '0';   // 逐字歌词（卡拉OK），默认开启

// 窄屏播放页视图：cover（封面+控制） ↔ lyric（整屏歌词），点击主区域切换
let playerView = sessionStorage.getItem('cm.playerView') || 'cover';
function setPlayerView(v) {
  playerView = v;
  try { sessionStorage.setItem('cm.playerView', v); } catch { /* 隐私模式 */ }
  const bodyEl = document.querySelector('.pl-body');
  if (bodyEl) bodyEl.className = `pl-body view-${v}`;
  if (v === 'lyric') {
    // 视图尺寸变化后：重算垫片并让当前行重新居中
    requestAnimationFrame(() => {
      layoutLyricPads();
      userScrollUntil = 0;
      curLyricIdx = -1;      // 强制 syncLyric 重新滚动到当前行
      syncLyric();
    });
  }
}

function renderLyric() {
  const box = document.getElementById('plLyric');
  if (!box) return;
  if (!lyricLines.length) {
    box.innerHTML = `<div class="pl-lyric-empty">暂无歌词</div>`;
    return;
  }
  const size = LYRIC_SIZES.find(s => s.key === lyricSizeKey) || LYRIC_SIZES[1];
  // 歌词字号 = 用户选的档位 × 全局字体大小倍率：否则选了「特大」字体后歌词还是原大小
  box.style.setProperty('--pl-fs', `calc(${size.fs}px * var(--cm-fs, 1))`);
  // 关键：每行都渲染（缺翻译/罗马音回退原文），行号与 lyricLines 一一对应；
  // 首尾垫片让第一句/最后一句也能停在中线
  box.innerHTML =
    `<div class="pl-lyric-pad" id="padTop"></div>` +
    lyricLines.map((l, i) => {
      const { main, sub } = lineParts(l);
      // 逐字：仅当该行有逐字/逐词轴、且当前显示的是原文时启用（翻译/罗马音无逐字轴）
      const karaokeOK = lyricKaraoke && l.w && l.w.length
        && (lyricMode === 'bi' || lyricMode === 'orig' || lyricMode === 'orig-roma');
      const body = karaokeOK
        ? `<div class="lyr">${l.w.map(([wt, tx]) => `<span class="ch" data-t="${wt}">${esc(tx)}</span>`).join('')}</div>`
        : `<div>${esc(main)}</div>`;
      return `<div class="pl-lyric-line" data-i="${i}">${body}${sub ? `<div class="pl-lyric-trans">${esc(sub)}</div>` : ''}</div>`;
    }).join('') +
    `<div class="pl-lyric-pad" id="padBot"></div>`;
  box.querySelectorAll('.pl-lyric-line').forEach(el => {
    let lp = null, lpFired = false;
    el.onclick = e => {
      e.stopPropagation();
      if (lpFired) { lpFired = false; return; }   // 长按已摘录：不要再 seek
      player.seek(lyricLines[+el.dataset.i].t / 1000);
    };
    // 长按摘录歌词（阶段二）：/song/lyrics/mark/add 是写操作，需登录 + 绑定网易云
    el.addEventListener('pointerdown', () => {
      lp = setTimeout(async () => {
        lp = null; lpFired = true;
        const line = lyricLines[+el.dataset.i];
        if (!line || !player.meta) return;
        const { addLyricMark } = await import('./pages/lyricmarks.js');
        addLyricMark(player.meta, line.txt);
      }, 600);
    });
    ['pointerup', 'pointerleave', 'pointercancel'].forEach(evt =>
      el.addEventListener(evt, () => { if (lp) { clearTimeout(lp); lp = null; } }));
  });
  layoutLyricPads();
  // 只在真实用户输入时暂停自动跟随 3 秒。
  // 不要用 onscroll + 标志位判定——scrollTo 的平滑动画也会触发 scroll 事件，
  // 动画期间标志位被 setTimeout 重置后，后续动画事件会被误判为用户操作，
  // 误设 userScrollUntil → 自动跟随被禁 → 视觉上歌词"卡住不滚"。
  ['pointerdown', 'wheel', 'touchstart'].forEach(evt => {
    box.addEventListener(evt, () => {
      if (springRaf) { cancelAnimationFrame(springRaf); springRaf = 0; }   // 终止弹簧，交给用户
      userScrollUntil = Date.now() + 3000;
    }, { passive: true });
  });
}

// 垫片高度 = 容器半高，使任意行（含首尾）都能滚动到垂直中线
function layoutLyricPads() {
  const box = document.getElementById('plLyric');
  if (!box) return;
  const h = Math.max(0, Math.round(box.clientHeight / 2) - 12);
  const top = document.getElementById('padTop'), bot = document.getElementById('padBot');
  if (top) top.style.height = h + 'px';
  if (bot) bot.style.height = h + 'px';
}
window.addEventListener('resize', () => {
  layoutLyricPads();
  // 旋转/分栏/改窗口大小后重新量取画布（控制器随播放页存在与否，安全空转）
  if (waveCtl) waveCtl.resize();
});
if (window.addEventListener) window.addEventListener('orientationchange', () => { if (waveCtl) waveCtl.resize(); });

// ---------- 逐字点亮（卡拉OK） ----------
let karaokeRaf = 0, karaokeLine = -1, karaokeIdx = -1, karaokeTick = 0;

// 支持连续填充（background-clip:text）时走 Apple 风格的「扫光」，
// 否则退化为逐字跳色（老引擎兜底）
const CAN_SWEEP = (() => {
  try {
    return CSS.supports('-webkit-background-clip', 'text') || CSS.supports('background-clip', 'text');
  } catch (e) {
    return false;
  }
})();

let karaokeSpans = null;
let karaokeVals = [];

function fireKaraoke() {
  if (!lyricKaraoke) return;
  if (curLyricIdx < 0) return;
  const line = lyricLines[curLyricIdx];
  if (!line || !line.w) return;
  const now = player.posMs();
  const w = line.w;

  // 获取或重建当前行的 DOM 缓存
  if (karaokeLine !== curLyricIdx || !karaokeSpans) {
    const box = document.getElementById('plLyric');
    if (!box) return;
    const el = box.querySelector(`.pl-lyric-line[data-i="${curLyricIdx}"]`);
    if (!el) return;
    karaokeSpans = el.querySelectorAll('.ch');
    karaokeVals = new Array(karaokeSpans.length).fill(-1);
    karaokeLine = curLyricIdx;
    karaokeTick = 0;                 // 换行后首帧立即绘制
  }
  const spans = karaokeSpans;
  if (!spans.length) return;

  if (!CAN_SWEEP) {                  // 老引擎兜底：逐字跳色
    let idx = -1;
    for (let k = 0; k < w.length; k++) { if (w[k][0] <= now) idx = k; else break; }
    if (idx === karaokeIdx) return;
    karaokeIdx = idx;
    spans.forEach((sp, k) => sp.classList.toggle('on', k <= idx));
    return;
  }

  // 节流到 ~30fps
  const t = performance.now();
  if (t - karaokeTick < 33) return;
  karaokeTick = t;

  for (let k = 0; k < spans.length && k < w.length; k++) {
    const start = w[k][0];
    const end = (k + 1 < w.length) ? w[k + 1][0] : start + 280;
    let p = (now - start) / Math.max(60, end - start);
    p = p < 0 ? 0 : (p > 1 ? 1 : p);
    // 5% 量化后比较（避免无变化时重写样式导致重栅格化闪烁）
    const q = Math.round(p * 20) / 20;
    if (q === karaokeVals[k]) continue;
    karaokeVals[k] = q;
    spans[k].style.setProperty('--p', q.toFixed(2));
    const sweeping = q > 0 && q < 1;
    if (spans[k].classList.contains('sweep') !== sweeping) spans[k].classList.toggle('sweep', sweeping);
  }
}

function startKaraoke() {
  if (karaokeRaf) return;
  const step = () => { karaokeRaf = requestAnimationFrame(step); fireKaraoke(); };
  karaokeRaf = requestAnimationFrame(step);
}
function stopKaraoke() {
  if (karaokeRaf) cancelAnimationFrame(karaokeRaf);
  karaokeRaf = 0;
  karaokeLine = -1;
  karaokeIdx = -1;
  karaokeSpans = null;
  karaokeVals = [];
  karaokeTick = 0;
}

let curLyricIdx = -1;
let userScrollUntil = 0;

// ---------- 弹簧滚动引擎 ----------
// 替代 CSS behavior:'smooth'（固定缓动、无弹性），用胡克定律做带微弹的跟随：
//   force = -k·(位移) - c·速度     → 轻微过冲后自然回稳（灵动感的来源）
const SPRING = { k: 130, c: 16, snapDist: 0.4, snapVel: 12 };
let springRaf = 0;

function springScrollTo(box, target) {
  if (springRaf) cancelAnimationFrame(springRaf);
  const start = box.scrollTop;
  const dist = target - start;
  if (Math.abs(dist) < 2) { box.scrollTop = target; updateLyricDepth(box); return; }
  let pos = start;
  let vel = dist * 3;            // 初速度朝目标方向（减少起步延迟）
  let last = performance.now();
  const step = (now) => {
    const dt = Math.min((now - last) / 1000, 0.05);
    last = now;
    const force = -SPRING.k * (pos - target) - SPRING.c * vel;
    vel += force * dt;
    pos += vel * dt;
    box.scrollTop = pos;
    updateLyricDepth(box);
    if (Math.abs(pos - target) < SPRING.snapDist && Math.abs(vel) < SPRING.snapVel) {
      box.scrollTop = target;    // 收敛后吸附
      updateLyricDepth(box);
      springRaf = 0;
      return;
    }
    springRaf = requestAnimationFrame(step);
  };
  springRaf = requestAnimationFrame(step);
}

// ---------- 距离感知渐变：离中心越远的行越淡（灵动层次感） ----------
let depthRaf = 0;
function updateLyricDepth(box) {
  if (depthRaf) return;
  depthRaf = requestAnimationFrame(() => {
    depthRaf = 0;
    const centerY = box.scrollTop + box.clientHeight / 2;
    const maxDist = box.clientHeight * 0.55;
    const lines = box.querySelectorAll('.pl-lyric-line');
    for (let j = 0; j < lines.length; j++) {
      const el = lines[j];
      const lineCenter = el.offsetTop + el.offsetHeight / 2;
      const d = Math.abs(lineCenter - centerY);
      // 近处 0.68 → 远处 0.30（.cur 行的 opacity 由 CSS 覆盖为 1）
      const fade = Math.max(0.30, 0.68 - (d / maxDist) * 0.38);
      el.style.setProperty('--depth', fade.toFixed(2));
    }
  });
}

function syncLyric() {
  if (!lyricLines.length) return;
  const box = document.getElementById('plLyric');
  if (!box) return;
  const t = player.posMs();
  let i = 0;
  for (let k = 0; k < lyricLines.length; k++) {
    if (lyricLines[k].t <= t) i = k; else break;
  }
  if (i === curLyricIdx) return;
  curLyricIdx = i;
  box.querySelectorAll('.pl-lyric-line').forEach(el => el.classList.toggle('cur', +el.dataset.i === i));
  karaokeSpans = null;
  const cur = box.querySelector(`.pl-lyric-line[data-i="${i}"]`);
  if (!cur) return;
  if (Date.now() < userScrollUntil) return;
  const target = Math.max(0, cur.offsetTop - box.clientHeight / 2 + cur.clientHeight / 2);
  springScrollTo(box, target);
}

async function openFull() {
  const ov = overlay();
  const m = player.meta;
  if (!m) return;
  if (!localStorage.getItem('cm.lyricSize')) lyricSizeKey = defaultLyricSizeKey();   // 未手动选择：按当前视口自适应（移动=大 / Pad=特大）
  const st = getStatus(m.ncm_id);
  ov.hidden = false;
  ov.innerHTML = `
    <div class="pl-bg" id="plBg" style="${m.pic ? `background-image:linear-gradient(rgba(24,22,30,.45),rgba(24,22,30,.62)),url('${esc(m.pic)}')` : ''}"></div>
    <div class="pl-page">
      <div class="pl-top">
        <span class="pl-btn" id="plClose"><span class="material-icons-outlined">keyboard_arrow_down</span></span>
        <div class="pl-quality" id="plQuality">${player.urlInfo ? tierLabel(player.urlInfo.level) : tierLabel(settings.quality)}</div>
        <span class="pl-topact">
          ${castIconHTML()}
          <span class="pl-btn" id="plMore" title="更多（歌词/背景/下载）"><span class="material-icons-outlined">more_vert</span></span>
        </span>
      </div>
      <div class="pl-body view-${playerView}">
        <div class="pl-left">
          <div class="pl-cover-wrap" id="plCoverWrap" title="点击查看歌词">
            <img id="plCover" src="${esc(m.pic)}" onerror="this.style.visibility='hidden'">
          </div>
          <div class="pl-info">
            <div class="pl-name">${esc(m.name)}</div>
            <div class="pl-artist" id="plArtist">${artistLineHTML(m)}</div>
            <div class="pl-stats" id="plStats"><mdui-linear-progress style="width:120px"></mdui-linear-progress></div>
          </div>
          <div class="pl-ctrl">
            <span class="pl-btn ${st.liked ? 'on' : ''}" id="plLike" title="选择收录到哪个我喜欢"><span class="material-icons-outlined">${st.liked ? 'favorite' : 'favorite_border'}</span><i class="cm-ncmdot" id="plNcmBadge" hidden></i></span>
            <span class="pl-btn" id="plAdd"><span class="material-icons-outlined">playlist_add</span></span>
            <span class="pl-btn" id="plComments" title="评论区"><span class="material-icons-outlined">forum</span></span>
            <span class="pl-btn ${player.playMode !== 'order' ? 'on' : ''}" id="plMode" title="${PLAY_MODES.find(x => x.key === player.playMode).label}"><span class="material-icons-outlined">${PLAY_MODES.find(x => x.key === player.playMode).icon}</span></span>
            <span class="pl-btn ${player.heartMode ? 'on' : ''}" id="plHeart" title="${player.heartMode ? '心动模式已开启：队列快见底自动续播（点击关闭）' : '心动模式：以当前曲目为种子持续智能续播（点击开启）'}"><span class="material-icons-outlined">auto_awesome</span></span>
            <span class="pl-btn" id="plQueue" title="当前播放列表"><span class="material-icons-outlined">queue_music</span></span>
          </div>
          <div class="pl-progress-stack">
            ${waveformHTML()}
            <div class="pl-seek">
              <span id="plCur">0:00</span>
              <div class="pl-seekbar" id="plSeekbar">
                <i class="pl-buf" id="plBuf" title="已缓冲"></i>
                <i class="pl-fill" id="plFill"></i>
                <input type="range" id="plSeek" min="0" max="100" step="0.1" value="0" aria-label="播放进度">
              </div>
              <span id="plDur">0:00</span>
            </div>
            <div class="pl-stall" id="plStall" hidden>
              <mdui-circular-progress style="width:13px;height:13px"></mdui-circular-progress>
              <span id="plStallText">缓冲中…</span>
            </div>
          </div>
          <div class="pl-transport">
            <span class="pl-btn big" id="plPrev" title="上一首">${AM_ICON.prev}</span>
            <span class="pl-btn huge" id="plPlay" title="播放/暂停">${amPlayIcon()}</span>
            <span class="pl-btn big" id="plNext" title="下一首">${AM_ICON.next}</span>
          </div>
        </div>
        <div class="pl-right">
          <div class="pl-lyric" id="plLyric"></div>
        </div>
      </div>
    </div>`;

  ov.querySelector('#plClose').onclick = closeFull;
  bindArtistLinks(ov);

  // 窄屏：点击封面/周围空白 ↔ 整屏歌词 双视图切换（≥600px 双栏不参与）
  const bodyEl = ov.querySelector('.pl-body');
  bodyEl.addEventListener('click', e => {
    if (window.matchMedia('(min-width: 600px)').matches) return;
    if (e.target.closest('.pl-ctrl, .pl-progress-stack, .pl-transport')) return;      // 控制区不触发
    if (e.target.closest('.pl-lyric-line')) return;                          // 歌词行点击=跳播
    setPlayerView(playerView === 'cover' ? 'lyric' : 'cover');
  });

  ov.querySelector('#plPlay').onclick = () => player.toggle();
  ov.querySelector('#plPrev').onclick = () => player.prev();
  ov.querySelector('#plNext').onclick = () => player.next();
  const modeBtn = ov.querySelector('#plMode');
  modeBtn.onclick = () => {
    const idx = PLAY_MODES.findIndex(x => x.key === player.playMode);
    const next = PLAY_MODES[(idx + 1) % PLAY_MODES.length];
    player.setPlayMode(next.key);
    modeBtn.classList.toggle('on', next.key !== 'order');
    modeBtn.title = next.label;
    modeBtn.querySelector('.material-icons-outlined').textContent = next.icon;
    toast(next.label);
  };
  // 心动模式开关：开了就立即补一批（队列够长时也会等到快见底才续）
  const heartBtn = ov.querySelector('#plHeart');
  heartBtn.onclick = async () => {
    const on = player.toggleHeartMode();
    heartBtn.classList.toggle('on', on);
    heartBtn.title = on
      ? '心动模式已开启：队列快见底自动续播（点击关闭）'
      : '心动模式：以当前曲目为种子持续智能续播（点击开启）';
    if (!on) { toast('心动模式已关闭'); return; }
    toast('心动模式已开启');
    if (player.queue.length - player.index <= 3) {
      const n = await player.heartExtend();
      if (!n) toast(player._heartErr || '暂时没取到续播推荐');
    }
  };
  ov.querySelector('#plQueue').onclick = openQueue;
  const seek = ov.querySelector('#plSeek');
  // 波形控制器：数据来自真实频谱（wave.read），进度来自 posMs/durMs
  // （投屏时这两个值来自设备上报，直接读 audio 会是 0）
  const canvas = ov.querySelector('#plWaveCanvas');
  if (waveCtl) { waveCtl.destroy(); waveCtl = null; }
  if (canvas) {
    // 纯波形指示：不接收进度（进度由下面专门的可拖动进度条表达）
    waveCtl = createWaveform(canvas, {
      read: u8 => wave.read(u8),
      bins: () => wave.bins,
      rate: () => wave.sampleRate,
      tilt: waveTilt(),
      style: waveStyle(),
    });
    waveCtl.setDbRange(wave.minDb, wave.maxDb);
    waveCtl.setPlaying(player.isPlaying());
    window.__cmWave = waveCtl;          // 调试/测试入口（只读引用）
  }
  seek.oninput = () => { seek.dataset.drag = '1'; };
  seek.onchange = () => {
    const dur = player.durMs();
    if (dur) player.seek(seek.value / 100 * dur / 1000);
    delete seek.dataset.drag;
  };
  ov.querySelector('#plQuality').onclick = qualityMenu;
  ov.querySelector('#plMore').onclick = openMoreDrawer;
  const castBtn = ov.querySelector('#plCast');
  if (castBtn) castBtn.onclick = () => openCastDialog();
  applyPlayerBg();
  applyDynamicScheme();
  ov.querySelector('#plComments').onclick = () => openComments(player.meta);
  ov.querySelector('#plLike').onclick = () => openLikeMenu(m, () => {
    const st2 = getStatus(m.ncm_id);
    const btn = document.getElementById('plLike');
    if (btn) {
      btn.classList.toggle('on', st2.liked);
      btn.querySelector('.material-icons-outlined').textContent = st2.liked ? 'favorite' : 'favorite_border';
    }
    loadStats();
  });
  ov.querySelector('#plAdd').onclick = () => addToPlaylist([player.meta]);

  loadLyric();
  loadStats();
  startKaraoke();   // 逐字点亮（暂停时在 state 回调里停）
  // 网易云红心小徽标（绑定账号已红心时显示；未登录不请求）
  if (auth.token) ensureNcmLiked().then(() => {
    const badge = document.getElementById('plNcmBadge');
    if (badge) badge.hidden = !isNcmLiked(m.ncm_id);
  });
}

async function loadStats() {
  const m = player.meta;
  if (!m) return;
  const el = document.getElementById('plStats');
  if (!el) return;
  let pop = m.pop || 0, comments = null, count = getStatus(m.ncm_id).count;
  try {
    const d = await api.songDetail([m.ncm_id]);
    if (d.songs && d.songs[0]) pop = d.songs[0].pop || pop;
  } catch { /* 忽略 */ }
  let topLiked = 0;
  try {
    const cc = await api.commentCount(m.ncm_id);
    comments = cc.total; topLiked = cc.topLiked || 0;
  } catch { /* 忽略 */ }
  if (!player.meta || player.meta.ncm_id !== m.ncm_id) return;
  el.innerHTML = [
    `<span><span class="mi">favorite</span> ${count || 0} 人点赞</span>`,
    pop ? `<span><span class="mi">local_fire_department</span> 热度 ${pop}</span>` : '',
    comments !== null ? `<span><span class="mi">forum</span> 评论 ${comments}</span>` : '',
    topLiked ? `<span><span class="mi">thumb_up</span> 网易云最热评论 ${topLiked} 赞</span>` : '',
  ].filter(Boolean).join('');
}

function closeFull() {
  const ov = overlay();
  if (ov.hidden) return;
  stopKaraoke();
  // 关键：销毁波形控制器。否则关闭播放页后 rAF 仍会每帧读频谱并往**已脱离 DOM**
  // 的 canvas 上绘制——后台空转费电（AI 审查指出的一处真实泄漏）。
  if (waveCtl) { waveCtl.destroy(); waveCtl = null; window.__cmWave = null; }
  if (springRaf) { cancelAnimationFrame(springRaf); springRaf = 0; }
  ov.classList.add('closing');
  setTimeout(() => { ov.hidden = true; ov.innerHTML = ''; ov.classList.remove('closing'); }, 240);
}

// 设置页切换波形样式 / 频谱倾斜时，若播放页正开着就立刻换（不必重开播放页）
['cm-wavestyle', 'cm-wavecfg'].forEach(ev => document.addEventListener(ev, () => {
  const ov = overlay();
  if (!ov || ov.hidden) return;
  const canvas = ov.querySelector('#plWaveCanvas');
  if (!canvas) return;
  if (waveCtl) { waveCtl.destroy(); waveCtl = null; }
  waveCtl = createWaveform(canvas, {
    read: u8 => wave.read(u8), bins: () => wave.bins, rate: () => wave.sampleRate,
    tilt: waveTilt(), style: waveStyle(),
  });
  waveCtl.setDbRange(wave.minDb, wave.maxDb);
  waveCtl.setPlaying(player.isPlaying());
  window.__cmWave = waveCtl;
}));

// ---------- 更多抽屉：语言 / 字号 / 渐变背景 / 下载 ----------

function chipsHTML(list, curKey, group) {
  return list.map(it =>
    `<mdui-chip ${it.key === curKey ? 'selected' : ''} data-g="${group}" data-k="${it.key}">${it.label.replace(/^Aa·/, '')}</mdui-chip>`).join('');
}

function openMoreDrawer() {
  const diag = mdui.dialog({
    headline: '更多',
    body: `<div class="cm-more">
      <div class="cm-more-sec">歌词语言</div>
      <div class="cm-more-chips" id="chipsLang">${chipsHTML(LYRIC_MODES, lyricMode, 'lang')}</div>
      <div class="cm-more-sec">歌词字号</div>
      <div class="cm-more-chips" id="chipsSize">${chipsHTML(LYRIC_SIZES, lyricSizeKey, 'size')}</div>
      <div class="cm-more-row">
        <div><div class="cm-more-t">逐字歌词</div><div class="cm-more-s">有逐字数据的歌曲逐字点亮（卡拉OK）</div></div>
        <mdui-switch id="mKara" ${lyricKaraoke ? 'checked' : ''}></mdui-switch>
      </div>
      <div class="cm-more-row">
        <div><div class="cm-more-t">沉浸模式</div><div class="cm-more-s">封面主色流动渐变铺满播放页</div></div>
        <mdui-switch id="mGrad" ${bgGradOn ? 'checked' : ''}></mdui-switch>
      </div>
      <div class="cm-more-sec">歌曲信息</div>
      <div class="cm-more-row" id="mWiki" style="cursor:pointer">
        <div><div class="cm-more-t">歌曲百科</div><div class="cm-more-s">创作信息 / 基本信息 / 百科正文</div></div>
        <span class="material-icons-outlined">chevron_right</span>
      </div>
      <div class="cm-more-row" id="mSongInfo" style="cursor:pointer">
        <div><div class="cm-more-t">查看歌曲详情</div><div class="cm-more-s">音质档位 / 副歌时间 / 红心数 / 创作者 / 百科 / 相似歌曲 / 乐谱</div></div>
        <span class="material-icons-outlined">chevron_right</span>
      </div>
      <div class="cm-more-sec">下载到本机</div>
      <div class="cm-more-chips" id="chipsDl">${QUALITY_TIERS.map(t =>
        `<mdui-chip data-g="dl" data-k="${t.key}" ${t.key === settings.quality ? 'selected' : ''}>${t.label}</mdui-chip>`).join('')}</div>
      <div class="cm-more-s" style="margin-top:6px">目录：Music/${esc(localStorage.getItem('cm.downloadDir') || 'CurrentMusic')}（可在「我的-快捷功能」修改）</div>
    </div>`,
    actions: [{ text: '关闭' }],
  });
  setTimeout(() => {
    const wk = diag.querySelector('#mWiki');
    if (wk) wk.onclick = () => {
      diag.open = false;
      import('./wiki.js').then(m => m.openWiki(player.meta)).catch(() => toast('百科加载失败'));
    };
    const si = diag.querySelector('#mSongInfo');
    if (si) si.onclick = () => {
      diag.open = false;
      import('./songinfo.js').then(m => m.openSongInfo(player.meta)).catch(() => {});
    };
    diag.querySelectorAll('mdui-chip[data-g="lang"]').forEach(ch => {
      ch.onclick = () => {
        lyricMode = ch.dataset.k;
        localStorage.setItem('cm.lyricMode', lyricMode);
        diag.querySelectorAll('mdui-chip[data-g="lang"]').forEach(x => x.selected = x === ch);
        curLyricIdx = -1;
        renderLyric(); syncLyric();
      };
    });
    diag.querySelectorAll('mdui-chip[data-g="size"]').forEach(ch => {
      ch.onclick = () => {
        lyricSizeKey = ch.dataset.k;
        localStorage.setItem('cm.lyricSize', lyricSizeKey);
        diag.querySelectorAll('mdui-chip[data-g="size"]').forEach(x => x.selected = x === ch);
        renderLyric(); syncLyric();
      };
    });
    diag.querySelector('#mKara').addEventListener('change', e => {
      lyricKaraoke = e.target.checked;
      localStorage.setItem('cm.karaoke', lyricKaraoke ? '1' : '0');
      renderLyric();
      stopKaraoke(); startKaraoke(); fireKaraoke();
    });
    diag.querySelector('#mGrad').addEventListener('change', e => {
      bgGradOn = e.target.checked;
      localStorage.setItem('cm.bgGrad', bgGradOn ? '1' : '0');
      applyPlayerBg();
    });
    diag.querySelectorAll('mdui-chip[data-g="dl"]').forEach(ch => {
      ch.onclick = () => { diag.open = false; doDownload(ch.dataset.k); };
    });
  }, 0);
}

async function doDownload(level) {
  const m = player.meta;
  if (!m) return;
  toast('正在解析下载地址…');
  try {
    // 优先用上游「客户端下载链接」（/song/download/url/v1）：直接给出带 size/md5/type 的下载地址，
    // 拿不到再回退到播放用的音源地址。
    let info = null;
    try {
      const d = await api.ncm('/song/download/url/v1', { id: m.ncm_id, level: level || 'exhigh' });
      const dd = d.data || {};
      if (dd.url) info = { url: dd.url, type: dd.type || 'mp3' };
    } catch { /* 回退 */ }
    if (!info) {
      // 第二层：老版客户端下载链接（参数是码率 br，不是档位名）
      try {
        const d2 = await api.ncm('/song/download/url', { id: m.ncm_id, br: 320000 });
        const dd2 = d2.data || d2;
        if (dd2 && dd2.url) info = { url: dd2.url, type: dd2.type || 'mp3' };
      } catch { /* 继续回退 */ }
    }
    if (!info) info = await api.songUrl(m.ncm_id, level);
    const ext = (info.type || 'mp3').toLowerCase();
    const name = `${m.name} - ${m.artists}.${ext}`.replace(/[\\/:*?"<>|]/g, '_');
    const dir = localStorage.getItem('cm.downloadDir') || 'CurrentMusic';
    if (window.NativeApi && window.NativeApi.download) {
      window.NativeApi.download(info.url, name, dir);
    } else {
      window.open(info.url, '_blank');
      toast('浏览器环境：已打开音频，可手动保存');
    }
  } catch (e) {
    toast(`下载失败：${e.message}`);
  }
}

// ---------- 播放页背景：动态渐变（默认开）/ 封面模糊 ----------

async function applyPlayerBg() {
  const el = document.getElementById('plBg');
  if (!el || !player.meta) return;
  if (!bgGradOn) {
    el.className = 'pl-bg';
    el.style.backgroundImage = player.meta.pic
      ? `linear-gradient(rgba(24,22,30,.45),rgba(24,22,30,.62)),url('${esc(player.meta.pic)}')` : '';
    return;
  }
  el.className = 'pl-bg grad';
  const g = (cs, deg) => `linear-gradient(${deg}deg, ${cs.primary} 0%, ${cs.deep} 48%, ${cs.dark} 100%)`;
  const setG = (a, b) => {
    el.style.setProperty('--g1', a);
    el.style.setProperty('--g2', b);
  };
  setG('linear-gradient(155deg, #6750a4 0%, #4a3a78 48%, #322653 100%)',
       'linear-gradient(205deg, #4a3a78 0%, #6750a4 55%, #322653 100%)');   // 兜底色组，取色成功后覆盖
  // 先用「上一次成功的色组」立即上色（coverColors 在取不到时也会返回它），
  // 再等本次取色回来覆盖：这样切歌不会先闪一下默认紫。
  const quick = await coverColors(player.meta.pic);
  if (quick && player.meta && document.getElementById('plBg') === el) {
    setG(g(quick, 155), g({ ...quick, primary: quick.deep, deep: quick.primary }, 205));
  }
  const cs = await coverColors(player.meta.pic);
  if (!cs || !player.meta || document.getElementById('plBg') !== el) return;
  setG(g(cs, 155), g({ ...cs, primary: cs.deep, deep: cs.primary }, 205));
}

// 动态取色：配色方案为 dynamic 时随封面切换全局主色（含深色档/强调色，见 applyColorScheme）
async function applyDynamicScheme() {
  if (getColorSchemeKey() !== 'dynamic' || !player.meta) return;
  const cs = await coverColors(player.meta.pic);
  if (cs && player.meta) applyColorScheme(cs.primary, cs);
}
on('song', () => { applyDynamicScheme(); });
// 缓冲进度：progress 事件与 tick 都会驱动，这里保证进度条上的缓冲区间实时更新
on('buffer', () => {
  const pct = player.bufferPct() + '%';
  const b = document.getElementById('plBuf'); if (b) b.style.width = pct;
  const mb = document.getElementById('miniBuf'); if (mb) mb.style.width = pct;
});
// 卡顿提示：waiting/stalled 时显示「缓冲中…」，恢复或自愈成功即隐藏
on('stalling', on => {
  const box = document.getElementById('plStall');
  if (box) box.hidden = !on;
});

/** 切歌时轻量更新播放页的歌曲字段（封面/标题/歌手/统计/音质），不重建 overlay——
 * 重建会导致歌词区被清空再异步填充，产生"突然消失又出现"的闪烁。 */
/** 歌手行：每位歌手各自可点（跳其主页）；无 id 时退化为纯文本。 */
function artistLineHTML(m) {
  const ids = m.artist_ids || [];
  const names = String(m.artists || '').split(' / ');
  const parts = names.map((n, i) => ids[i]
    ? `<span class="pl-artist-link" data-aid="${ids[i]}" title="查看 ${esc(n)} 的主页">${esc(n)}</span>`
    : esc(n));
  return parts.join(' / ') + (m.album ? ' · ' + esc(m.album) : '');
}

/** 绑定歌手名的点击（打开歌手主页前先收起播放页） */
function bindArtistLinks(scope) {
  (scope || document).querySelectorAll('.pl-artist-link').forEach(el => {
    el.onclick = e => {
      e.stopPropagation();
      const aid = el.dataset.aid;
      if (!aid) return;
      const ov = overlay();
      if (ov && !ov.hidden) closeFull();          // 先收播放页，避免盖住目标页
      setTimeout(() => { location.hash = `#/artist/${aid}`; }, 180);
    };
  });
}

function updateSongInfo() {
  const ov = overlay();
  const m = player.meta;
  if (!ov || ov.hidden || !m) return;
  const cover = document.getElementById('plCover');
  if (cover) cover.src = m.pic || '';
  const name = ov.querySelector('.pl-name');
  if (name) name.textContent = m.name;
  const artist = ov.querySelector('.pl-artist');
  if (artist) { artist.innerHTML = artistLineHTML(m); bindArtistLinks(artist); }
  const q = document.getElementById('plQuality');
  if (q) q.textContent = player.urlInfo ? tierLabel(player.urlInfo.level) : tierLabel(settings.quality);
  const likeBtn = document.getElementById('plLike');
  if (likeBtn) {
    const st = getStatus(m.ncm_id);          // 先按缓存立即上屏（避免闪默认态）
    likeBtn.classList.toggle('on', st.liked);
    likeBtn.querySelector('.material-icons-outlined').textContent = st.liked ? 'favorite' : 'favorite_border';
    // 缓存里可能没有新歌/已过期（切歌时红心停留在上一首的根因）：强制拉一次，
    // 期间若又切歌则丢弃本次结果
    const songAt = m;
    ensureStatus(m.ncm_id).then(s => {
      if (!player.meta || player.meta !== songAt || !document.getElementById('plLike')) return;
      likeBtn.classList.toggle('on', s.liked);
      likeBtn.querySelector('.material-icons-outlined').textContent = s.liked ? 'favorite' : 'favorite_border';
    });
  }
  // 更新背景
  applyPlayerBg();
}

on('song', () => {
  if (!overlay().hidden) {
    updateSongInfo();           // 只更新歌曲字段，不重建整个 overlay（重建会清空歌词区 → 闪烁）
  }
  karaokeLine = -1; karaokeIdx = -1; karaokeSpans = null; karaokeTick = 0;
  loadLyric();
  loadStats();
  fireKaraoke();
});
on('state', () => {
  if (!player.isPlaying()) stopKaraoke(); else startKaraoke();
  const b = document.getElementById('plPlay');
  if (b) b.innerHTML = amPlayIcon();
  if (waveCtl) waveCtl.setPlaying(player.isPlaying());
  renderMini();
});

function qualityMenu() {
  const cur = settings.quality;
  const items = QUALITY_TIERS.map(t =>
    `<div class="cm-qm-item" data-v="${t.key}">${t.label}${t.spec ? `<em class="cm-qm-spec">${t.spec}</em>` : ''}${t.key === cur ? '<span class="material-icons-outlined">check</span>' : ''}</div>`).join('');
  const diag = mdui.dialog({
    headline: '选择音质',
    body: `<div class="cm-qm">${items}</div>`,
    actions: [{ text: '关闭' }],
  });
  setTimeout(() => {
    diag.querySelectorAll('.cm-qm-item').forEach(it => {
      it.onclick = () => {
        const v = it.dataset.v;
        const label = it.textContent.trim();
        player.changeQuality(v);
        const q = document.getElementById('plQuality');
        if (q) q.textContent = v === 'auto' ? label : tierLabel(v);
        diag.open = false;
      };
    });
  }, 0);
}


// ---------- 当前播放列表 ----------

export function openQueue() {
  const q = player.queue;
  if (!q.length) return toast('播放列表为空');
  const diag = mdui.dialog({
    headline: `当前播放（${q.length} 首）`,
    body: `<div class="cm-queue">${q.map((s, i) => `
      <div class="cm-queue-item ${i === player.index ? 'cur' : ''}" data-i="${i}">
        <span class="q-idx">${i === player.index ? '<span class="material-icons-outlined">graphic_eq</span>' : i + 1}</span>
        <div class="q-main">
          <div class="q-name">${esc(s.name)}</div>
          <div class="q-sub">${esc(s.artists)}</div>
        </div>
        <span class="q-dur">${fmtDur(s.duration)}</span>
      </div>`).join('')}</div>`,
    actions: [{ text: '关闭' }],
  });
  setTimeout(() => {
    diag.querySelectorAll('.cm-queue-item').forEach(it => {
      it.onclick = () => {
        player.playAt(+it.dataset.i);
        diag.open = false;
      };
    });
    const cur = diag.querySelector('.cm-queue-item.cur');
    if (cur) cur.scrollIntoView({ block: 'center' });
  }, 0);
}

// ---------- 评论区（内容同步网易云，发评走绑定账号） ----------


// 歌曲评论：统一走通用评论抽屉（web/src/comments.js），覆盖 6 个维度
// 说明：此处原先是一份 200 行的「只在歌曲维度可用」的实现，已抽到 comments.js，
// 以免同一套热门/最新/楼层/点赞/回复逻辑在多处分叉。
export async function openComments(meta) {
  if (!meta) return;
  const { openComments: openEntityComments } = await import('./comments.js');
  return openEntityComments({ type: 0, id: meta.ncm_id, title: meta.name || '歌曲' });
}


// ---------- 加入歌单 bottom sheet ----------

export async function addToPlaylist(songs) {
  if (!auth.token) return toast('登录后才能操作歌单');
  if (!songs || !songs.length) return;
  let pls = [];
  try { pls = ((await api.myPlaylists()).playlists || []).filter(p => p.source !== "ncm"); }
  catch (e) { return toast(e.message); }

  const diag = mdui.dialog({
    headline: `加入歌单（${songs.length} 首）`,
    body: `
      <div class="cm-pl-pick">
        ${pls.map(p => `<div class="cm-pl-pick-item" data-id="${p.id}"><span class="material-icons-outlined">queue_music</span><div><div>${esc(p.name)}</div><div class="sub">${p.track_count} 首</div></div></div>`).join('')}
        <div class="cm-pl-pick-item new" id="plNew"><span class="material-icons-outlined">add</span><div>新建歌单</div></div>
      </div>`,
    actions: [{ text: '取消' }],
  });
  setTimeout(() => {
    diag.querySelectorAll('.cm-pl-pick-item').forEach(el => {
      el.onclick = async () => {
        if (el.id === 'plNew') {
          diag.open = false;
          const { promptDialog } = await import('./ui.js');
          promptDialog({
            title: '新建歌单', label: '歌单名称',
            onOk: async name => {
              const p = await api.createPlaylist(name, '');
              await api.addPlaylistTracks(p.id, songs);
              toast(`已创建「${name}」并加入 ${songs.length} 首`);
            },
          });
          return;
        }
        try {
          await api.addPlaylistTracks(+el.dataset.id, songs);
          toast(`已加入「${el.querySelector('div div').textContent}」`);
          diag.open = false;
        } catch (e) { toast(e.message); }
      };
    });
  }, 0);
}

export function initPlayerUI() {
  renderMini();
  applyDynamicScheme();   // 启动即随上次播放的封面取色（配色为 dynamic 时）
}
