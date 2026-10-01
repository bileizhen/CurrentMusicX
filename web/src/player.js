// 播放引擎：单例 audio、队列、音质选择（默认自动最高，后端并行探测母带档）、循环/随机、MediaSession。
import { mdui } from './md.js';
import { api, auth, settings, ncmSongs } from './api.js';
import { toast, tierLabel } from './ui.js';
import { resolveSongUrl } from './songurl.js';   // 三级音源回退（P2）：
// 曾因漏 import 上线即 ReferenceError 全站无法播放——见 tools/check-js-refs.py

// ---------- 音频元素（可替换） ----------
// 为什么要"可替换"：可视化需要把 <audio> 接入 Web Audio 图，而**一旦接入，
// 元素音频就只走图输出**（不再直出）。若某个音源是跨域"污染"的（无 CORS 头），
// 图输入恒为静音 → 声音会彻底消失。所以遇到这种情况必须**换一个新元素**才能
// 恢复直出，不能只是断开节点。详见下文 loadUrl() 的 CORS 策略。
// 分析器档位（波形模块据此把 byte 还原成 dB 并做倾斜补偿）
const MIN_DB = -110, MAX_DB = -10;

let audio = makeAudio();

// Web Audio 分析器状态：analyser 非空即代表「可视化可用」
let actx = null, analyser = null, srcNode = null, graphEl = null;
let vizDisabled = false;      // 系统级不可用（无 AudioContext / 上下文起不来）→ 本次会话不再尝试
const corsFailHosts = {};     // 不支持 CORS 的**域名**（AI 审查建议：按域名降级，
                              // 换到别的 CDN 域名仍可尝试可视化，不因一次失败永久放弃）

/** 供可视化层读取频谱；不可用时返回 false（调用方据此画静默基线）。 */
export const wave = {
  get ready() { return !!analyser; },
  get bins() { return analyser ? analyser.frequencyBinCount : 1024; },
  get sampleRate() { return analyser ? analyser.context.sampleRate : 48000; },
  get minDb() { return MIN_DB; },
  get maxDb() { return MAX_DB; },
  read(u8) {
    if (!analyser) return false;
    try { analyser.getByteFrequencyData(u8); return true; } catch (e) { return false; }
  },
  /** 用户手势后恢复上下文（自动播放策略可能让新建的上下文停在 suspended）。 */
  resume() {
    try { if (actx && actx.state === 'suspended') actx.resume(); } catch (e) { /* 忽略 */ }
  },
};

// 音频图看门狗：**接了图之后声音只从图输出**——上下文一旦不在 running
// （后台挂起、系统回收、自动播放策略），用户就会"歌在放但没声音"。
// 持续巡检：能恢复就恢复，恢复不了就拆图换元素回到直出——宁可没可视化，不能没声音。
let watchTimer = 0, watchStrikes = 0;
function watchGraph() {
  if (watchTimer || !graphEl) return;
  watchStrikes = 0;
  watchTimer = setInterval(() => {
    if (!graphEl) { clearInterval(watchTimer); watchTimer = 0; return; }
    if (audio.paused) { watchStrikes = 0; return; }        // 暂停时上下文挂起属正常
    if (actx && actx.state === 'running') { watchStrikes = 0; return; }
    watchStrikes++;
    try { if (actx && actx.state === 'suspended') actx.resume(); } catch (e) { /* 忽略 */ }
    if (watchStrikes >= 2) {                                // 约 4s 仍未恢复 → 回退直出
      clearInterval(watchTimer); watchTimer = 0;
      fallbackNoViz();
    }
  }, 2000);
}

/** 丢弃一个音频元素：停播 + 清源。监听器都有 el!==audio 守卫，不会误触发换歌。 */
function disposeAudio(old) {
  try { old.pause(); } catch (e) { /* 忽略 */ }
  try { old.removeAttribute('src'); old.src = ''; old.load(); } catch (e) { /* 忽略 */ }
}

function teardownGraph() {
  if (watchTimer) { clearInterval(watchTimer); watchTimer = 0; }
  try { if (srcNode) srcNode.disconnect(); } catch (e) { /* 忽略 */ }
  try { if (analyser) analyser.disconnect(); } catch (e) { /* 忽略 */ }
  try { if (actx && actx.close) actx.close(); } catch (e) { /* 忽略 */ }
  actx = analyser = srcNode = null;
  graphEl = null;
}

/** 建图：**只在确认音源 CORS 干净之后**调用（否则会把声音吞掉）。 */
function buildGraph(el) {
  if (analyser || vizDisabled) return false;
  el = el || audio;
  const AC = window.AudioContext || window.webkitAudioContext;
  if (!AC) { vizDisabled = true; return false; }
  try {
    actx = new AC();
    srcNode = actx.createMediaElementSource(el);
    analyser = actx.createAnalyser();
    //  · fftSize 2048 → 频率分辨率更高，柱与柱之间过渡自然
    //  · smoothing 0.72 → FFT 层面的平滑（柱高另有一套快攻击/慢释放包络）
    analyser.fftSize = 2048;
    // 档位：**-110 ~ -10（100dB）**。原先 -85/-25 会把高频段（常见 -90dB 以下）
    // 直接截断成 0，右侧永远是平的、也没法用"频谱倾斜"抬起来。
    analyser.minDecibels = MIN_DB;
    analyser.maxDecibels = MAX_DB;
    analyser.smoothingTimeConstant = 0.72;
    srcNode.connect(analyser);
    analyser.connect(actx.destination);      // 必须接回目的地，否则没声音
    graphEl = el;
  } catch (e) {
    teardownGraph();
    vizDisabled = true;
    return false;
  }
  // 上下文若因自动播放策略停在 suspended，图会静音 → 尝试恢复，失败则立刻拆图回退
  if (actx.state !== 'running') {
    try { actx.resume(); } catch (e) { /* 忽略 */ }
    setTimeout(() => {
      if (actx && actx.state !== 'running') fallbackNoViz();
    }, 1200);
  }
  return true;
}

/** 可视化不可用时的兜底：拆图重建元素，保住声音（位置与播放态尽量保留）。 */
function fallbackNoViz() {
  if (vizDisabled && !graphEl) return;
  vizDisabled = true;
  const pos = audio.currentTime || 0;
  const wasPlaying = !audio.paused;
  const url = audio.src;
  teardownGraph();
  disposeAudio(audio);
  const fresh = makeAudio();
  audio = fresh;
  if (url) {
    fresh.src = url;                       // 新元素直出，不再经过图
    try { if (pos > 0) fresh.currentTime = pos; } catch (e) { /* 元数据未就绪时忽略 */ }
    // 新元素上不会触发 pause，scheduleResume 不会自己跑 → 显式兜底重试
    if (wasPlaying) fresh.play().catch(() => scheduleResume());
  }
  emit('state');      // 通知 UI 重绘（元素换了、可视化已降级）
}

/** 取 URL 的域名（按域名记 CORS 失败）。 */
function hostOf(url) {
  try { return new URL(url, location.href).host; } catch (e) { return ''; }
}

/** 装载音源：带 crossOrigin 尝试（可视化前提），失败由 error 处理器回退。 */
let corsPending = null;         // 待回退重试的 { url, pos, host }（带 CORS 加载失败时用）
function loadUrl(url, pos) {
  corsPending = null;
  const host = hostOf(url);
  const badHost = !!(host && corsFailHosts[host]);   // 该域名已知不支持 CORS
  // 【危险路径防御】已知不支持 CORS 的域名 + 当前元素已挂音频图 → 必须**先换新元素**
  // 再普通加载。否则跨域污染的音源会让音频图输出静音，且**不会触发 error 事件**
  // （context.state 仍是 running，看门狗也发现不了）→ 表现为"歌在放但没声音"，
  // 是最难排查的一类故障。
  if (badHost && graphEl) { teardownGraph(); disposeAudio(audio); audio = makeAudio(); }
  if (!vizDisabled && !badHost) {
    audio.crossOrigin = 'anonymous';       // CDN 实测带 access-control-allow-origin: *
    corsPending = { url: url, pos: pos || 0, host: host };
  } else {
    audio.crossOrigin = null;              // 普通播放（该源无可视化）
  }
  audio.src = url;
  if (pos > 0) { try { audio.currentTime = pos; } catch (e) { /* 忽略 */ } }
}

function makeAudio() {
  const a = new Audio();
  a.preload = 'auto';
  bindAudio(a);
  return a;
}

// 调试/测试入口（未挂 DOM 的 audio 实例经此可达）
const listeners = {};
export function on(evt, fn) { (listeners[evt] = listeners[evt] || []).push(fn); }
function emit(evt, data) { (listeners[evt] || []).forEach(fn => fn(data)); }
/** 供外部触发一次时间刷新（投屏时本机没有 timeupdate，由投屏侧按帧驱动）。 */
// 页面被隐藏/切走（切后台、锁屏、关闭标签）时立即结账：不依赖 timeupdate 继续触发
document.addEventListener('visibilitychange', () => { if (document.hidden) flushListen(); });

export function tick() {
  accumulateCast();              // 投屏：本机 audio 不前进，按设备播放态用墙钟累计
  maybeFlushListen();
  emit('time');
}
/** 供外部触发一次状态刷新（投屏时设备端播放态变化，需重绘播放键图标）。 */
export function notifyState() { emit('state'); }

// 播放时钟外部接管（DLNA 投屏）：投屏时本机音频暂停，audio.currentTime 不前进，
// 若不接管，进度条与歌词都会停在原地（用户反馈的「歌词不跟随滚动」）。
let clockSource = null;
export function setClockSource(fn) { clockSource = fn; }
const clock = () => (typeof clockSource === 'function' ? clockSource() : null);

// 高解析度档位提示：192kHz/24bit 母带、96kHz、多声道全景声对**设备解码与重采样**
// 要求高，部分机型会出现杂音（用户反馈的"爆音"多为此类）。每个会话提示一次，
// 不反复打扰；规格来自服务端解析的文件头（sr/bits/ch），拿不到就不提示。
let hiresWarned = false;
function warnHiRes(info) {
  if (hiresWarned || !info) return;
  const sr = info.sr || 0, ch = info.ch || 0;
  if (sr <= 48000 && ch <= 2) return;            // 常规档位：不打扰
  hiresWarned = true;
  const spec = [
    sr ? (sr % 1000 === 0 ? (sr / 1000) + 'kHz' : (sr / 1000).toFixed(1) + 'kHz') : '',
    info.bits ? info.bits + 'bit' : '',
    ch > 2 ? ch + '声道' : '',
  ].filter(Boolean).join('/');
  const name = tierLabel(info.level) || '该音质';
  setTimeout(() => {
    toast(`${name}${spec ? '（' + spec + '）' : ''}对设备要求较高，若出现杂音可在音质中切到「无损 FLAC」`);
  }, 1500);
}

export const player = {
  queue: [],
  index: -1,
  meta: null,
  urlInfo: null,
  // 播放模式四合一：list 列表循环 | one 单曲循环 | order 顺序播放 | shuffle 随机
  playMode: (() => {
    const m = localStorage.getItem('cm.playMode');
    if (m) return m;
    // 旧版 loop/shuffle 迁移
    if (localStorage.getItem('cm.shuffle') === '1') return 'shuffle';
    return localStorage.getItem('cm.loop') === 'one' ? 'one'
      : localStorage.getItem('cm.loop') === 'none' ? 'order' : 'list';
  })(),
  loading: false,

  // ---------- 心动模式（/playmode/intelligence/list）----------
  // 与上面「播放模式四合一」正交：那四个管**队列怎么轮**，这个管**队列怎么续**。
  // 开启后，队列快见底时自动以当前曲目为种子取一批续播追加到队尾，实现持续智能播放。
  // 下一首预取：提前解析地址并按网络情况预拉（见 _prefetchNext）
  _prefetch: null,
  _prefetchBusy: false,

  /** 已缓冲到的百分比（进度条据此画出缓冲区间）。 */
  bufferPct() { updateBufPct(); return bufPct; },
  /** 是否正在卡顿（UI 显示"缓冲中"）。 */
  get stalling() { return stallNotified; },

  /**
   * 提前解析下一首的播放地址，并按网络情况用隐藏 audio 预拉一段。
   * 省流模式与 2G/3G 下直接跳过——宁可慢一点，也不偷跑用户流量。
   * 无论是否预拉成功，"地址已解析"本身就省掉了切歌时的一次往返。
   */
  async _prefetchNext() {
    if (!this.queue.length || this._prefetchBusy) return;
    const nx = this.queue[this.index + 1] || this.queue[0];
    if (!nx || !nx.ncm_id) return;
    if (this._prefetch && String(this._prefetch.id) === String(nx.ncm_id)) return;
    try {
      const c = navigator.connection || navigator.mozConnection || {};
      if (c.saveData) return;
      if (c.effectiveType && /(^|-)2g$|(^|-)3g$/.test(c.effectiveType)) return;
    } catch { /* 忽略 */ }
    this._prefetchBusy = true;
    try {
      const info = await resolveSongUrl(nx, settings.quality);
      // 期间可能已切歌或又预取了别的：过期结果直接丢弃
      const still = this.queue[this.index + 1] || this.queue[0];
      if (!still || String(still.ncm_id) !== String(nx.ncm_id)) return;
      const el = new Audio();
      el.preload = 'auto';
      el.muted = true;                       // 不发声（也不接 Web Audio 图）
      try { el.src = info.url; el.load(); } catch { /* 忽略 */ }
      this._prefetch = { id: nx.ncm_id, url: info.url, type: info.type, level: info.level, el };
    } catch { /* 预取失败不影响正常播放 */ }
    finally { this._prefetchBusy = false; }
  },

  heartMode: localStorage.getItem('cm.heart') === '1',
  _heartBusy: false,
  _heartMiss: 0,          // 连续取不到新曲目的次数（上游异常时不无脑续）
  _heartErr: '',

  setHeartMode(on) {
    this.heartMode = !!on;
    try { localStorage.setItem('cm.heart', this.heartMode ? '1' : '0'); } catch { /* 忽略 */ }
    if (this.heartMode) { this._heartMiss = 0; this._heartErr = ''; }
    emit('state');
    return this.heartMode;
  },
  toggleHeartMode() { return this.setHeartMode(!this.heartMode); },

  _heartPidCache: null,

  /**
   * 心动模式要的「上游歌单 id」：**只支持「我喜欢的音乐」（specialType=5）**。
   * 实测：pid 留空 → 400 参数错误；传普通自建歌单 → 400 不支持该歌单类型；
   * 传我喜欢 → 200 并返回一个 150 首的池子（其中约一半 recommended=true）。
   * 故这里先解析出本人「我喜欢的音乐」的歌单 id 并缓存。
   */
  async _heartPid() {
    if (this._heartPidCache) return this._heartPidCache;
    const st = await api.bindStatus();                 // 项目专用入口（T3 那条不泛化转发）
    const uid = ((st || {}).profile || {}).uid;
    if (!uid) throw new Error('需要先绑定网易云账号');
    const d = await api.ncm('/user/playlist', { uid, limit: 200 });
    const liked = (d.playlist || []).find(x => x && !x.subscribed && Number(x.specialType) === 5);
    if (!liked) throw new Error('没找到「我喜欢的音乐」（心动模式只支持这个歌单类型）');
    this._heartPidCache = liked.id;
    return liked.id;
  },

  /**
   * 取一批心动推荐（已剔除队内已有曲目）。公开：播客页要展示同一份列表，
   * 复用这里才能避免「pid 解析」与「songInfo 解包」两处各错一遍。
   */
  async heartList(seedId, count = 10) {
    const pid = await this._heartPid();
    const d = await api.ncm('/playmode/intelligence/list', { id: seedId, sid: seedId, pid, count });
    // 上游返回的是 { alg, id, recommended, songInfo } 包装：歌曲本体在 songInfo 里，
    // 直接对 data 调 ncmSongs 会全部映射成空（原实现取不到数据的第二个原因）。
    const raw = (d.data || []).map(x => (x && x.songInfo) ? x.songInfo : x);
    const have = new Set(this.queue.map(x => x && String(x.ncm_id)));
    return ncmSongs(raw).filter(x => x && x.ncm_id && !have.has(String(x.ncm_id))).slice(0, count);
  },

  /** 心动模式：以 seed（默认当前曲目）为种子续一批到队尾，返回新增条数。 */
  async heartExtend(seed) {
    if (this._heartBusy) return 0;
    const id = ((seed || this.meta) || {}).ncm_id;
    if (!id) return 0;
    this._heartBusy = true;
    try {
      const fresh = await this.heartList(id);
      if (!fresh.length) { this._heartMiss++; this._heartErr = '上游没有返回新的续播推荐'; return 0; }
      this.queue.push(...fresh);
      this._heartMiss = 0; this._heartErr = '';
      this.persist();
      emit('song');                      // 播放列表与角标刷新
      toast(`心动模式：续上 ${fresh.length} 首`);
      return fresh.length;
    } catch (e) {
      this._heartMiss++;
      this._heartErr = (e && e.message) || '取续播推荐失败';
      return 0;
    } finally { this._heartBusy = false; }
  },

  /** 队列快见底就提前续（异步补齐，不阻塞切歌）。 */
  _heartEnsure() {
    if (!this.heartMode || this._heartBusy) return;
    if (this._heartMiss >= 3) return;                 // 连续失败：停手，不给上游刷请求
    if (this.queue.length - this.index > 3) return;   // 还够听
    this.heartExtend(this.meta);
  },

  /** 以某首歌为种子开播心动模式（歌曲详情页 / 发现页 / 私人 FM 都走这里）。 */
  async startHeart(seed) {
    if (!seed || !seed.ncm_id) { toast('这首没有可用的网易云曲目 ID'); return false; }
    this.setHeartMode(true);
    this._heartMiss = 0; this._heartErr = '';
    let fresh = [];
    try {
      fresh = await this.heartList(seed.ncm_id, 12);
    } catch (e) {
      toast((e && e.message) || '心动模式取歌失败');
      this.playList([seed], 0);
      return false;
    }
    const rest = fresh.filter(x => String(x.ncm_id) !== String(seed.ncm_id));
    this.playList([seed, ...rest], 0);
    toast(rest.length ? `心动模式：已开播 ${rest.length + 1} 首，播完自动续` : '心动模式：暂时没取到续播，先播这首');
    return true;
  },

  playList(songs, startIndex = 0) {
    this.queue = songs.filter(Boolean).slice();
    if (this.playMode === 'shuffle' && startIndex === 0) startIndex = Math.floor(Math.random() * this.queue.length);
    this.playAt(startIndex);
    this.persist();
  },

  async playAt(i) {
    if (i < 0 || i >= this.queue.length) return;
    flushListen();                 // 上一首结账（此刻 meta 还是旧的那首）
    lastTickT = -1;                // 新歌重置基准，避免把两首之间的间隔算进去
    this.index = i;
    this.meta = this.queue[i];
    this.urlInfo = null;
    this.loading = true;
    emit('song');
    emit('state');
    if (clock()) {
      // 投屏中：声音由设备输出。这里只更新队列/元数据与界面，
      // 既不取本机播放地址也不本地播放（否则手机会与电视同时出声）。
      this.loading = false;
      this.report();
      this.persist();
      this.updateMediaSession();
      this._heartEnsure();          // 投屏中同样要续队列
      return;
    }
    let info;
    try {
      const pf = this._prefetch;
      if (pf && String(pf.id) === String(this.meta.ncm_id) && pf.url) {
        info = { url: pf.url, type: pf.type || 'mp3', level: pf.level || 'auto' };
        this._prefetch = null;             // 用完即弃（元素随之可回收）
      } else {
        info = await resolveSongUrl(this.meta, settings.quality);
      }
    } catch (e) {
      this.loading = false;
      toast(`「${this.meta.name}」${e.message || '播放失败'}`);
      emit('song');
      // 解析失败与 audio error 是两条失败路径，都要计入熔断
      // （否则整队解析失败时仍会「失败→换下一首→再失败」无限循环）
      errStreak++;
      const cap = Math.min(3, Math.max(1, this.queue.length));
      if (errStreak >= cap) {
        errStreak = 0;
        this.wantPlaying = false;
        return;
      }
      // 自动跳下一首（避免队列卡死）
      if (this.queue.length > 1) setTimeout(() => this.next(true), 800);
      return;
    }
    if (!this.meta || this.queue[this.index] !== this.meta) return; // 已切歌
    this.urlInfo = info;
    this.loading = false;
    warnHiRes(info);               // 192kHz/多声道档位：提示设备兼容性
    loadUrl(info.url, 0);          // 优先 CORS 干净加载（可视化前提），失败自动回退
    this.wantPlaying = true;
    audio.play().catch(() => toast('点击播放键开始播放'));
    emit('song');
    emit('state');
    this._scrobbled = null;
    this.report();
    this.persist();
    this.updateMediaSession();
    this._heartEnsure();            // 队列快见底就提前续（心动模式）
  },

  async report() {
    if (!auth.token || !this.meta) return;
    try { await api.recordPlay(this.meta); } catch { /* 静默 */ }
    // 听歌打卡（/scrobble）延后到「听够一半或 30 秒」再发，避免只点一下就污染网易云历史
    this._scrobblePending = { id: this.meta.ncm_id, at: Date.now() };
  },

  /** 听歌打卡：达到阈值后每首只报一次；未绑定网易云时静默跳过（401）。
   *  优先用新版 /scrobble/v1（字段更全，能带上歌曲名/歌手/码率），失败回退旧版。 */
  async scrobble() {
    const pend = this._scrobblePending;
    if (!pend || !auth.token) return;
    if (this._scrobbled === pend.id) return;
    const m = this.meta || {};
    const sec = Math.floor(this.position() / 1000);
    const src = (this.queue && this.queue[this.index] && this.queue[this.index].source_pl_id) || 0;
    try {
      await api.ncm('/scrobble/v1', {
        id: pend.id, sourceid: src || 0, time: sec, source: 'list',
        name: m.name || '', artist: m.artists || '',
        bitrate: 320000, level: 'exhigh', total: Math.floor((m.duration || 0) / 1000),
        confirm: 1,
      });
      this._scrobbled = pend.id;
    } catch {
      try {
        await api.ncm('/scrobble', { id: pend.id, sourceid: src || 0, time: sec, confirm: 1 });
        this._scrobbled = pend.id;
      } catch { /* 未绑定/风控：不打扰用户 */ }
    }
    // 播放状态上报（网易云「一起听」链路；失败无副作用）
    try {
      if (!this._relaySession) {
        this._relaySession = Array.from({ length: 12 },
          () => 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'[Math.floor(Math.random() * 36)]).join('');
      }
      await api.ncm('/relay/play/state/submit', {
        id: pend.id, sessionId: this._relaySession, progress: sec, playMode: 'list_loop', type: 'song', confirm: 1,
      });
    } catch { /* 忽略 */ }
  },

  // wantPlaying：我们「期望」的播放状态。系统打断（音频焦点被抢、锁屏瞬断、车机接管）
  // 会触发 pause，若不自动恢复就表现为「锁屏播放突然暂停」。
  wantPlaying: false,

  play() {
    if (!this.urlInfo) {
      // urlInfo 不持久化：重启后 restore() 只恢复了队列与元数据。
      // 此时按播放应当对**当前曲目**重新解析——原先只在 index === -1 时
      // 才 playAt(0)，index 有效时直接 return，表现为
      // 「退出软件重进后点迷你条/播放键没有任何反应」。
      if (this.index >= 0 && this.meta) this.playAt(this.index);
      else if (this.index === -1 && this.queue.length) this.playAt(0);
      return;
    }
    this.wantPlaying = true;
    audio.play().catch(() => { /* 交由 paused 兜底重试 */ });
  },

  pause() {
    this.wantPlaying = false;
    audio.pause();
  },

  toggle() {
    if (this.audio.paused) this.play(); else this.pause();
  },

  next(auto = false) {
    if (!this.queue.length) return;
    if (this.playMode === 'one' && auto) {
      if (clock()) { this.playAt(this.index); return; }   // 投屏中：重投同一首（不本地播放）
      audio.currentTime = 0; audio.play(); return;
    }
    let i = this.index;
    if (this.playMode === 'shuffle') i = Math.floor(Math.random() * this.queue.length);
    else if (i + 1 >= this.queue.length) {
      if (this.playMode === 'order' && auto) { this.pause(); stopPlayback(); return; }
      i = 0;
    } else i++;
    this.playAt(i);
  },

  prev() {
    if (!this.queue.length) return;
    if (audio.currentTime > 4) { audio.currentTime = 0; return; }
    const i = this.playMode === 'shuffle'
      ? Math.floor(Math.random() * this.queue.length)
      : (this.index > 0 ? this.index - 1 : this.queue.length - 1);
    this.playAt(i);
  },

  seek(sec) { if (this.urlInfo && isFinite(sec)) audio.currentTime = sec; },

  /** 当前播放位置（毫秒）：投屏时取设备上报值，否则取本机音频。 */
  posMs() {
    const c = clock();
    return c ? c.posMs : (this.audio.currentTime || 0) * 1000;
  },

  /** 当前曲目总长（毫秒）。 */
  durMs() {
    const c = clock();
    if (c && c.durMs > 0) return c.durMs;
    return (isFinite(this.audio.duration) && this.audio.duration > 0)
      ? this.audio.duration * 1000 : (this.meta && this.meta.duration) || 0;
  },

  /** 是否正在播放（投屏时以设备状态为准，否则看本机音频）。 */
  isPlaying() {
    const c = clock();
    return c ? c.playing : !this.audio.paused;
  },

  setPlayMode(mode) {
    this.playMode = mode;
    localStorage.setItem('cm.playMode', mode);
    emit('state');
  },

  async changeQuality(level) {
    settings.quality = level;
    if (!this.meta) return;
    const at = audio.currentTime;
    const wasPlaying = !audio.paused;
    try {
      const info = await resolveSongUrl(this.meta, level);
      this.urlInfo = info;
      loadUrl(info.url, at);       // 换档保留播放位置
      if (wasPlaying) audio.play();
      emit('song');
      toast(`音质：${tierLabel(info.level)}`);
    } catch (e) { toast(e.message); }
  },

  persist() {
    try {
      localStorage.setItem('cm.lastq', JSON.stringify({ queue: this.queue.slice(0, 100), index: this.index }));
    } catch { /* 忽略超限 */ }
  },
  restore() {
    try {
      const d = JSON.parse(localStorage.getItem('cm.lastq'));
      if (d && d.queue && d.queue.length) { this.queue = d.queue; this.index = d.index; this.meta = this.queue[this.index] || null; }
    } catch { /* 忽略 */ }
  },

  updateMediaSession() {
    if (!this.meta) return;
    const b = window.NativeApi;
    // App 内：走原生 MediaSession（通知栏常驻媒体信息 + 蓝牙 AVRCP 曲目/进度）
    if (b && b.mediaMeta) {
      try {
        // 时长单位是毫秒：meta.duration 来自 NCM 的 dt（本就为 ms），不可再乘 1000
        // （曾错乘一次 → 蓝牙/车机把 9 分 36 秒的歌显示成 160 小时）；
        // 优先用 <audio> 实测时长（更准，且能纠正元数据缺失/偏差）
        const durMs = Math.round(isFinite(audio.duration) && audio.duration > 0
          ? audio.duration * 1000
          : (this.meta.duration || 0));
        b.mediaMeta(this.meta.name || '', this.meta.artists || '', this.meta.album || '',
          durMs, this.meta.pic || '');
        b.mediaState(!audio.paused, Math.round(audio.currentTime * 1000));
        return;
      } catch { /* 桥异常时回退浏览器 MediaSession */ }
    }
    if (!('mediaSession' in navigator)) return;
    try {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: this.meta.name,
        artist: this.meta.artists,
        album: this.meta.album,
        artwork: this.meta.pic ? [{ src: this.meta.pic, sizes: '300x300' }] : [],
      });
      navigator.mediaSession.setActionHandler('play', () => audio.play());
      navigator.mediaSession.setActionHandler('pause', () => audio.pause());
      navigator.mediaSession.setActionHandler('previoustrack', () => this.prev());
      navigator.mediaSession.setActionHandler('nexttrack', () => this.next());
    } catch { /* 部分内核不支持 */ }
  },

  /** 原生 MediaSession 播放态同步（通知按钮态/蓝牙进度）。App 外静默。 */
  pushMediaState() {
    const b = window.NativeApi;
    if (b && b.mediaState) {
      try { b.mediaState(!audio.paused, Math.round(audio.currentTime * 1000)); } catch { /* 忽略 */ }
    }
  },

  get audio() { return audio; },
};

// ---------- 实际收听时长（给「听歌时长」统计用）----------
// 旧口径是"开始播放就把整首时长记进去"：跳歌按整首计（虚高），同一首重播只留最后一条
// 记录（虚低），跟真实收听无关。现在只统计**真的在播**的时间：
//   · timeupdate 按 currentTime 的前进量累加；暂停、拖动、后台挂起都不计
//   · 每 30s 冲一次增量；切歌 / 暂停 / 播放结束 / 页面隐藏时补冲，避免丢时长
//   · 投屏时本机 audio 不前进，改由 tick()（投屏侧驱动）按设备播放态用墙钟累计
let listenedMs = 0;          // 待上报的毫秒增量
let lastTickT = -1;          // 上次 timeupdate 的 currentTime（秒）
let lastFlushAt = 0;
let lastCastAt = 0;

function accumulateLocal(el) {
  if (el.paused || el.seeking) { lastTickT = el.currentTime; return; }
  const t = el.currentTime;
  if (lastTickT >= 0 && t > lastTickT) {
    const d = (t - lastTickT) * 1000;
    // 单次跳变过大（后台被挂起后恢复、时钟抖动）不计，否则会凭空多出时长
    if (d > 0 && d <= 3000) listenedMs += d;
  }
  lastTickT = t;
}

function accumulateCast() {
  const c = clock();
  const now = Date.now();
  if (c && c.playing) {
    const d = now - lastCastAt;
    if (lastCastAt && d > 0 && d <= 5000) listenedMs += d;
  }
  lastCastAt = now;
}

/** 把增量上报给后端；失败则留着下次一起报（不丢时长）。不足 1s 不值得发。 */
async function flushListen() {
  const id = player.meta && player.meta.ncm_id;
  const ms = Math.round(listenedMs);
  if (!id || ms < 1000 || !auth.token) return;
  listenedMs = 0;
  try { await api.addListenMs(id, ms); } catch { listenedMs += ms; }
}

/** 30s 一次的节流冲（本地以 timeupdate 驱动、投屏以 tick 驱动，都不需要定时器）。 */
function maybeFlushListen() {
  const now = Date.now();
  if (!lastFlushAt) { lastFlushAt = now; return; }
  if (now - lastFlushAt >= 30000) { lastFlushAt = now; flushListen(); }
}

// ---------- 播放抗卡顿（缓冲可视化 / 卡顿自愈 / 下一首预取）----------
// 网络抖动是移动端最主要的"听着听着顿一下"来源。这里做三件事：
//   ① 把浏览器真实缓冲到的位置暴露给 UI（进度条上画出已缓冲区间）；
//   ② 卡住不动超过 6s 就**用新地址在原位置续播**（签名地址失效/连接半死都能救回来），
//      而不是直接跳下一首；
//   ③ 提前解析下一首的播放地址并按网络情况悄悄预拉，切歌时省掉一次往返。
let bufPct = 0;              // 已缓冲到的百分比（相对总时长）
let stallTimer = 0;
let lastPos = 0;
let recovering = false;
let stallNotified = false;

function updateBufPct() {
  try {
    const d = audio.duration;
    if (!d || !isFinite(d) || !audio.buffered || !audio.buffered.length) { bufPct = 0; return; }
    const t = audio.currentTime;
    let end = 0;
    // 优先取「包含当前播放位置」的那段缓冲的末端：多段 Range 时这才是用户理解的"缓存到哪"
    for (let i = 0; i < audio.buffered.length; i++) {
      if (audio.buffered.start(i) <= t + .25 && t <= audio.buffered.end(i) + .25) end = audio.buffered.end(i);
    }
    if (!end) end = audio.buffered.end(audio.buffered.length - 1);
    bufPct = Math.max(0, Math.min(100, (end / d) * 100));
  } catch { bufPct = 0; }
}

/** 卡顿看门狗：只在"想播却在等待"时武装，位置一前进就解除。 */
function armStallWatch() {
  clearTimeout(stallTimer);
  lastPos = audio.currentTime || 0;
  stallTimer = setTimeout(() => {
    stallTimer = 0;
    if (!player.wantPlaying || audio.paused) return;
    if (Math.abs((audio.currentTime || 0) - lastPos) < .3) recoverStalled();
  }, 6000);
}
function clearStallWatch() {
  if (stallTimer) { clearTimeout(stallTimer); stallTimer = 0; }
  if (stallNotified) { stallNotified = false; emit('stalling', false); }
}

/** 卡顿自愈：重新解析地址并在**原位置**续播（比"卡住就跳下一首"体面得多）。 */
async function recoverStalled() {
  if (recovering || !player.meta) return;
  recovering = true;
  if (!stallNotified) { stallNotified = true; emit('stalling', true); }
  try {
    const at = audio.currentTime || 0;
    const info = await resolveSongUrl(player.meta, settings.quality);
    player.urlInfo = info;
    loadUrl(info.url, at);
    if (player.wantPlaying) audio.play().catch(() => {});
    toast('网络波动，已在原位置续播');
  } catch { /* 自愈失败：交给上层失败计数与跳曲逻辑 */ }
  finally {
    recovering = false;
    if (stallNotified) { stallNotified = false; emit('stalling', false); }
  }
}

// ---------- 音频元素事件（集中绑定；元素被 CORS 回退重建时重新绑定）----------
let lastMediaPushSec = -1;
let resumeTimer = 0, resumeTries = 0;
let errStreak = 0;

function bindAudio(el) {
  el.addEventListener('ended', () => {
  if (el !== audio) return;      // 已丢弃的旧元素：事件一律忽略
  flushListen();
  player.next(true);
});
  el.addEventListener('timeupdate', () => {
  if (el !== audio) return;      // 已丢弃的旧元素：事件一律忽略
    emit('time');
    accumulateLocal(el);
    maybeFlushListen();
    updateBufPct();
    if (stallNotified) { stallNotified = false; emit('stalling', false); }   // 位置前进了 = 卡顿结束
    if (stallTimer) clearStallWatch();
    // 缓冲过半后悄悄预取下一首（省流/弱网下跳过，见 _prefetchNext）
    if (el.buffered && el.buffered.length && el.duration && isFinite(el.duration)
        && el.buffered.end(el.buffered.length - 1) / el.duration > .5) player._prefetchNext();
    // 蓝牙/通知进度：每 5s 同步一次（AVRCP 由系统按 playbackState 自推进度，无需逐帧）
    const sec = Math.floor(el.currentTime);
    if (!el.seeking && sec !== lastMediaPushSec && sec % 5 === 0) {
      lastMediaPushSec = sec;
      player.pushMediaState();
      // 打卡阈值：听过 30s，或已过曲目一半
      const half = isFinite(el.duration) && el.duration > 0 ? el.duration / 2 : Infinity;
      if (sec >= 30 || sec >= half) player.scrobble();
    }
  });
  el.addEventListener('waiting', () => {
    if (el !== audio) return;
    // 缓冲等待：先让 UI 说话（"缓冲中"），6s 还没动静才自愈
    if (!stallNotified) { stallNotified = true; emit('stalling', true); }
    armStallWatch();
  });
  el.addEventListener('stalled', () => {
    if (el !== audio) return;
    armStallWatch();                   // 上游断流：同样交给看门狗
  });
  el.addEventListener('canplay', () => {
    if (el !== audio) return;
    clearStallWatch();
  });
  el.addEventListener('progress', () => {
    if (el !== audio) return;
    updateBufPct();
    emit('buffer');                    // 让进度条重画已缓冲区间
  });
  el.addEventListener('play', () => {
  if (el !== audio) return;      // 已丢弃的旧元素：事件一律忽略
    wave.resume();                     // 用户手势后恢复音频上下文
    watchGraph();                      // 后台/系统挂起上下文时兜底（否则会没声音）
    player.wantPlaying = true;
    clearTimeout(resumeTimer); resumeTries = 0;
    errStreak = 0;                     // 真正开始播放了：连续失败计数清零
    emit('state'); keepAlive(true); player.pushMediaState();
  });
  el.addEventListener('pause', () => {
  if (el !== audio) return;      // 已丢弃的旧元素：事件一律忽略
    flushListen();                     // 暂停即结账：暂停期间的时间本来就不该计入
    emit('state'); player.pushMediaState();
    // 注意：暂停不改保活状态——锁屏/系统音频焦点抖动会触发 pause，
    // 此时若释放唤醒锁+停前台服务，进程可能在息屏下被冻结，出现「突然暂停且不再恢复」。
    if (player.wantPlaying && !player.audio.ended) scheduleResume();
  });
  el.addEventListener('seeked', () => {
  if (el !== audio) return;      // 已丢弃的旧元素：事件一律忽略
  player.pushMediaState();
});
  el.addEventListener('loadedmetadata', () => {
  if (el !== audio) return;      // 已丢弃的旧元素：事件一律忽略
    // 带 crossOrigin 能走到 loadedmetadata = 该音源 CORS 干净 → 可安全建图
    if (el.crossOrigin && graphEl !== el && !vizDisabled) {
      if (buildGraph(el)) { wave.resume(); corsPending = null; }
    }
    if (isFinite(el.duration) && el.duration > 0) player.updateMediaSession();
  });
  el.addEventListener('error', () => {
  if (el !== audio) return;      // 已丢弃的旧元素：事件一律忽略
    // ① CORS 回退：带 crossOrigin 加载失败（该音源无 CORS 头）→
    // 去掉 crossOrigin 重试一次，**保证有声音**，可视化降级为静默基线。
    // 必须换新元素：图一旦建立，元素音频只走图输出，仅断开节点无法恢复直出。
  if (corsPending && el === audio) {
      const pend = corsPending; corsPending = null;
      // 只记这个**域名**（下次同域名直接普通播放），不做全会话降级——
      // 换到别的 CDN 域名仍可继续尝试可视化
      if (pend.host) corsFailHosts[pend.host] = 1;
      teardownGraph();
      disposeAudio(el);
      const fresh = makeAudio();
      audio = fresh;
      fresh.crossOrigin = null;
      fresh.src = pend.url;
      if (pend.pos > 0) { try { fresh.currentTime = pend.pos; } catch (e) { /* 忽略 */ } }
      if (player.wantPlaying) fresh.play().catch(() => scheduleResume());
      return;                       // 不计入连续失败熔断
    }
    if (!player.urlInfo) return;
    errStreak++;
    const cap = Math.min(3, Math.max(1, player.queue.length));
    if (errStreak >= cap) {
      errStreak = 0;
      player.wantPlaying = false;
      toast('连续播放失败，已停止（请检查网络后重试）');
      return;
    }
    toast('播放出错，尝试下一首');
    player.next(true);
  });
}

// 时长此时才实测可得：重推一次元数据，蓝牙/车机拿到准确曲长（避免用估计值或被旧值卡住）

// 意外暂停自动恢复：系统抢焦点/锁屏瞬断后重新起播（最多 3 次，间隔递增）
function scheduleResume() {
  if (resumeTries >= 3) return;
  resumeTries++;
  clearTimeout(resumeTimer);
  resumeTimer = setTimeout(() => {
    if (player.wantPlaying && player.audio.paused && player.urlInfo) {
      player.audio.play().catch(() => scheduleResume());
    }
  }, 600 * resumeTries);
}

// 后台保活：有正在播放的曲目就保持前台服务（含暂停态——便于锁屏随时恢复），
// 仅在队列播完/清空/主动停止时释放
function keepAlive(on) {
  try { window.NativeApi && window.NativeApi.keepAlive && window.NativeApi.keepAlive(on); } catch { /* 非 App 环境 */ }
}

/** 真正停止播放（队列播完且不循环 / 清空队列）：释放前台服务与唤醒锁。 */
export function stopPlayback() {
  player.wantPlaying = false;
  clearTimeout(resumeTimer);
  keepAlive(false);
}
// 连续播放失败熔断：error 会 next() 换下一首，但若整队都解析失败
// （上游风控/断网）会无限循环「解析→失败→换下一首」刷爆接口与提示。
// 超过 3 次（或绕完整个队列）即停下并说明原因。

// 调试/测试入口
window.__cmPlayer = { get audio() { return audio; }, player, wave };

// MDUI 主色（品牌紫）
if (mdui.setColorScheme) mdui.setColorScheme('#6750A4');
