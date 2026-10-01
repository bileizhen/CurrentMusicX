import './polyfill.js';   // 旧版 WebView 兼容垫片（必须在 mdui 之前）
// CurrentMusic 入口：mdui 注册、主题、路由、播放器 UI 挂载。
import 'mdui/mdui.css';
import '@material-design-icons/font/outlined.css';
import './app.css';
import './ui-overhaul.css';
import { auth, settings, setAuthExpiredHandler, warmDecorScales } from './api.js';
import { toast, esc, initRipple, bootColorScheme, bootFontScale, initImageFade } from './ui.js';
import { initAccessibleControls } from './ux-controls.js';
import { checkUpdate } from './update.js';
import { initPullToRefresh } from './ptr.js';
import { engineChrome, engineOutdated } from './version.js';
import { player } from './player.js';
import { initPlayerUI } from './player-ui.js';
import { initMv } from './mv.js';
import { initCast } from './cast.js';
import { bootUiPreset } from './uipreset.js';
import { applyCustomize } from './customize.js';
import { initGlass } from './glass.js';
import { initNavDrop } from './navdrop.js';
import { applyTheme } from './pages/settings.js';

import * as home from './pages/home.js';
import * as search from './pages/search.js';
import * as library from './pages/library.js';
import * as playlist from './pages/playlist.js';
import * as plazaPage from './pages/plaza.js';
import * as user from './pages/user.js';
import * as settingsPage from './pages/settings.js';
import * as adminPage from './pages/admin.js';
import * as roomsPage from './pages/rooms.js';
import * as roomPage from './pages/room.js';
import * as squarePage from './pages/square.js';
import * as profilePage from './pages/profile.js';
import * as artistPage from './pages/artist.js';
import * as artistsPage from './pages/artists.js';
import * as followedPage from './pages/followed.js';
import * as stylePage from './pages/style.js';
import * as lyricMarksPage from './pages/lyricmarks.js';
import * as assetsPage from './pages/assets.js';
import * as albumPage from './pages/album.js';
import * as mvPage from './pages/mv.js';
import * as videoPage from './pages/video.js';
import * as albumsPage from './pages/albums.js';
import * as ugcPage from './pages/ugc.js';
import * as footprintPage from './pages/footprint.js';
import * as messagesPage from './pages/messages.js';
import * as followPage from './pages/follow.js';
import * as eventsPage from './pages/events.js';
import * as accountPage from './pages/account.js';
import * as cloudPage from './pages/cloud.js';
import * as radioPage from './pages/radio.js';
import * as podcastPage from './pages/podcast.js';
import * as memberPage from './pages/member.js';
import * as servicesPage from './pages/services.js';
import * as repPage from './pages/rep.js';

const ROUTES = [
  { re: /^#\/home$/, page: home, title: 'CurrentMusic' },
  { re: /^#\/search$/, page: search, title: '搜索' },
  { re: /^#\/square$/, page: squarePage, title: '发现' },
  { re: /^#\/u\/(\d+)$/, page: profilePage, title: '用户主页', fixed: m => [m[1]] },
  { re: /^#\/artist\/(\d+)$/, page: artistPage, title: '歌手', fixed: m => [m[1]] },
  { re: /^#\/artists$/, page: artistsPage, title: '歌手分类' },
  { re: /^#\/style\/(\d+)$/, page: stylePage, title: '曲风', fixed: m => [m[1]] },
  { re: /^#\/lyricmarks$/, page: lyricMarksPage, title: '我的歌词本' },
  { re: /^#\/assets$/, page: assetsPage, title: '我的音乐资产' },
  { re: /^#\/album\/(\d+)$/, page: albumPage, title: '专辑', fixed: m => [m[1]] },
  { re: /^#\/mv\/(\d+)$/, page: mvPage, title: 'MV', fixed: m => [m[1]] },
  { re: /^#\/mvlist$/, page: mvPage, title: 'MV', fixed: ['list'] },
  { re: /^#\/albums$/, page: albumsPage, title: '新碟' },
  { re: /^#\/ugc$/, page: ugcPage, title: '百科贡献' },
  { re: /^#\/footprint$/, page: footprintPage, title: '听歌足迹' },
  { re: /^#\/messages$/, page: messagesPage, title: '消息' },
  { re: /^#\/follow$/, page: followPage, title: '关注与状态' },
  { re: /^#\/events$/, page: eventsPage, title: '动态与话题' },
  { re: /^#\/account$/, page: accountPage, title: '网易云账号' },
  { re: /^#\/cloud$/, page: cloudPage, title: '音乐云盘' },
  { re: /^#\/radio$/, page: radioPage, title: '电台', fixed: ['list'] },
  { re: /^#\/radio\/(\d+)$/, page: radioPage, title: '电台', fixed: m => [m[1]] },
  { re: /^#\/podcast$/, page: podcastPage, title: '播客与 FM' },
  { re: /^#\/member$/, page: memberPage, title: '会员与云贝' },
  { re: /^#\/services$/, page: servicesPage, title: '网易云服务' },
  { re: /^#\/rep$/, page: repPage, title: '云小编' },
  { re: /^#\/video\/([A-Za-z0-9]+)$/, page: videoPage, title: '视频', fixed: m => [m[1]] },
  { re: /^#\/videohome$/, page: videoPage, title: '视频广场', fixed: ['home'] },
  { re: /^#\/followed$/, page: followedPage, title: '关注的歌手' },
  { re: /^#\/library$/, page: library, title: '歌单' },
  { re: /^#\/plaza$/, page: plazaPage, title: '歌单广场' },
  { re: /^#\/user$/, page: user, title: '我的' },
  { re: /^#\/settings$/, page: settingsPage, title: '设置' },
  { re: /^#\/admin$/, page: adminPage, title: '管理员' },
  { re: /^#\/rooms$/, page: roomsPage, title: '一起听' },
  { re: /^#\/room\/(\d+)$/, page: roomPage, title: '听歌房', fixed: m => [m[1]] },
  { re: /^#\/pl\/(.+)$/, page: playlist, title: '歌单', fixed: m => ['pl', m[1]] },
  { re: /^#\/daily$/, page: playlist, title: '每日推荐', fixed: ['daily'] },
  { re: /^#\/ncmpl\/(\d+)$/, page: playlist, title: '排行榜', fixed: m => ['ncmpl', m[1]] },
];

const out = () => document.getElementById('out');

// ---------- 路由方向感知（前进=上入 / 后退=左入） ----------
let navStack = [];
let navDirection = 'fwd';
let activeRouteHash = null;
let routeVersion = 0;
const routeScroll = new Map();
const ROOT_TABS = new Set(['#/home', '#/search', '#/square', '#/library', '#/user']);

function navigateBack() {
  if (navStack.length > 1) history.back();
  else location.hash = '#/home';
}

window.addEventListener('hashchange', () => {
  const hash = location.hash || '#/home';
  const idx = navStack.lastIndexOf(hash);
  if (idx >= 0 && idx < navStack.length - 1) {
    // 跳回栈内更早页面（点导航返回/返回键）→ 后退，栈截断
    navStack = navStack.slice(0, idx + 1);
    navDirection = 'back';
  } else if (navStack[navStack.length - 1] !== hash) {
    navStack.push(hash);
    navDirection = 'fwd';
  }
});

async function router() {
  const hash = location.hash || '#/home';
  if (!navStack.length) navStack.push(hash);
  // 路由允许带查询串（如 #/user?decor=1 直达挂件设置）：匹配时只取路径部分，
  // 查询参数解析成 params.query 交给页面（不解析的话 ?xxx 会让所有路由都匹配不上）
  const qIdx = hash.indexOf('?');
  const routePath = qIdx < 0 ? hash : hash.slice(0, qIdx);
  const query = {};
  if (qIdx >= 0) {
    hash.slice(qIdx + 1).split('&').forEach(kv => {
      if (!kv) return;
      const eq = kv.indexOf('=');
      const k = decodeURIComponent(eq < 0 ? kv : kv.slice(0, eq));
      query[k] = eq < 0 ? '1' : decodeURIComponent(kv.slice(eq + 1));
    });
  }
  let matched = null, params = null;
  for (const r of ROUTES) {
    const m = routePath.match(r.re);
    if (m) {
      matched = r;
      params = r.fixed ? (typeof r.fixed === 'function' ? r.fixed(m) : r.fixed) : m.slice(1);
      break;
    }
  }
  if (!matched) { location.hash = '#/home'; return; }
  if (params) params.query = query;   // 页面按需读取（大多忽略）

  // 同步返回按钮和页面标题；Tab 页不需要重复的返回入口。
  const pathIsTab = ROOT_TABS.has(routePath);
  const back = document.getElementById('topBack');
  back.hidden = pathIsTab;
  document.getElementById('topbar').classList.toggle('has-back', !pathIsTab);
  document.getElementById('pageTitle').textContent = matched.title;
  document.querySelectorAll('#bottomNav .navItem').forEach(a => {
    a.classList.toggle('cur', a.getAttribute('href') === hash.split('/').slice(0, 2).join('/'));
  });
  // MD3 页面过渡：每页包一层；后退时用 back 变体（左入）
  const changed = activeRouteHash !== hash;
  if (changed && activeRouteHash) routeScroll.set(activeRouteHash, out().scrollTop);
  activeRouteHash = hash;
  const currentVersion = ++routeVersion;
  const wrap = document.createElement('div');
  wrap.className = 'cm-page' + (navDirection === 'back' ? ' cm-page-back' : '');
  out().replaceChildren(wrap);
  if (changed) out().scrollTop = navDirection === 'back' ? (routeScroll.get(hash) || 0) : 0;
  try {
    await matched.page.render(wrap, params);
  } catch (e) {
    if (currentVersion !== routeVersion) return;
    wrap.innerHTML = `<div class="cm-empty cm-error" role="alert">页面加载失败：${esc(e.message)}<button class="cm-retry" id="cmPageRetry" type="button">重新加载</button></div>`;
    wrap.querySelector('#cmPageRetry').onclick = () => router();
  }
  if (currentVersion !== routeVersion) return;
  // 只有后退才恢复旧位置；主动刷新保留当前位置，新页面归零。
  if (changed && navDirection === 'back') out().scrollTop = routeScroll.get(hash) || 0;
}

function boot() {
  applyTheme();
  bootColorScheme();   // 配色方案（默认动态取色，取色随播放封面变化）
  bootFontScale();     // 字体大小（默认标准；不跟随系统字号，见 ui.js FONT_SCALES）
  // 原生窗口 insets（env(safe-area-inset-*) 在多数 WebView 上恒为 0，用桥值兜底）
  if (window.NativeApi && window.NativeApi.insets) {
    try {
      const i = JSON.parse(window.NativeApi.insets());
      const rt = document.documentElement.style;
      rt.setProperty('--safe-t', (i.t || 0) + 'px');
      rt.setProperty('--safe-b', (i.b || 0) + 'px');
      rt.setProperty('--safe-l', (i.l || 0) + 'px');
      rt.setProperty('--safe-r', (i.r || 0) + 'px');
    } catch { /* 忽略 */ }
  }
  initRipple();
  initImageFade();   // 全局图片加载动画（骨架微光 → 渐显）
  warmDecorScales(); // 预热头像挂件比例表（本地缓存即时可用，后台校验版本号）
  window.__cmBooted = true;   // 供 index.html 的启动诊断判定
  document.getElementById('boot').remove();

  document.getElementById('topAction').onclick = () => { location.hash = '#/settings'; };
  document.getElementById('topBack').onclick = navigateBack;
  try { history.scrollRestoration = 'manual'; } catch { /* 旧版 WebView 不支持 */ }
  initAccessibleControls(); // 动态页面补齐键盘操作、焦点和可访问名称

  // 网页版顶栏「下载安卓版 APP」入口（App 内有 NativeApi，不渲染）
  if (!(window.NativeApi && window.NativeApi.versionCode)) {
    const dl = document.createElement('a');
    dl.id = 'dlApkTop';
    dl.className = 'cm-dl-apk-top';
    dl.title = '下载安卓版 APP';
    dl.innerHTML = '<span class="material-icons-outlined">android</span><span>下载 APP</span>';
    dl.href = settings.base + '/download/latest';
    document.getElementById('topAction').before(dl);
  }
  // MD3 top app bar：内容滚动时切换 surface 层级 + elevation
  const outEl = out();
  outEl.addEventListener('scroll', () => {
    document.getElementById('topbar').classList.toggle('scrolled', outEl.scrollTop > 8);
  }, { passive: true });
  window.addEventListener('hashchange', router);
  if (!location.hash) location.hash = '#/home';
  router();

  // 全局下拉刷新：重跑当前路由（页面 render 自带骨架屏与数据重取）
  initPullToRefresh(() => router());

  bootUiPreset();            // UI 皮肤：把预设属性打到 <html>（默认不生效）
  applyCustomize();          // 个性化：自定义背景图 + 玻璃效果值（写到 <html> 内联）
  initGlass();               // 液体玻璃折射（仅 frost 皮肤 + 支持的内核）
  initNavDrop();             // 底栏液态玻璃「水滴」指示器（仅 frost 皮肤）
  player.restore();          // 恢复上次队列（不自动播放）
  initPlayerUI();
  initMv();
  initCast();   // DLNA 投屏：还原投屏状态并接管传输指令

  setAuthExpiredHandler(() => toast('登录已失效，请重新登录'));

  // 旧版渲染引擎一次性提示（引导更新 WebView，更新后可恢复最佳效果）
  if (engineOutdated() && !sessionStorage.getItem('cm.engineHint')) {
    sessionStorage.setItem('cm.engineHint', '1');
    setTimeout(() => toast(`当前系统 WebView 较旧（Chromium ${engineChrome()}），建议在应用商店更新以获得最佳体验`), 4000);
  }

  // 启动自动检测更新（延迟启动，不与首屏渲染抢资源）
  setTimeout(() => { checkUpdate({ silent: true }).catch(() => {}); }, 1500);

  // 系统深浅色切换时（Android 壳转发 cmthemechange），auto 模式即时跟随
  window.addEventListener('cmthemechange', applyTheme);

  // Android 系统返回统一入口（壳调用）：关闭浮层（带动画）→ 原生历史栈返回（吃到后退动画）→ 逐级回首页
  window.__cmHandleBack = function () {
    var cb = document.getElementById('cmtClose');
    if (cb) { cb.click(); return true; }
    var ov = document.getElementById('playerOverlay');
    if (ov && !ov.hidden) {
      var pc = document.getElementById('plClose');
      if (pc) { pc.click(); return true; }
    }
    var d = document.querySelector('mdui-dialog');
    if (d && d.open) { d.open = false; return true; }
    if (navStack.length > 1 && (location.hash || '#/home') !== navStack[0]) {
      history.back();          // 原生栈返回 → hashchange → 后退左入动画
      return true;
    }
    if (location.hash && location.hash !== '#/home') {
      location.hash = '#/home';
      return true;
    }
    return false;
  };
}

boot();
