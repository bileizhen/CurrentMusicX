// 底栏「液态玻璃水滴」指示器（Apple Liquid Glass / iOS 26 的选中态）
//
// 它是包裹在当前选中 tab 外的一颗玻璃液滴：切换 tab 时会**流动**过去，
// 移动过程中略微拉伸（squash & stretch），落位时回弹 —— 液滴的手感就在这一步。
//
// 只负责两件事：
//   1) 把 #navDrop 的位移与宽度对齐到「当前选中项的图标+文字」外框；
//   2) 切换时给出方向感（拉伸方向朝行进方向，落位后复位）。
// 外观（玻璃本体、高光边、圆角、过渡曲线）全部在 app.css 里，且限定在 frost 皮肤下。
// 默认皮肤（MD3）不显示它，仍用原来的胶囊指示器。

const DROP_ID = 'navDrop';
const NAV_SEL = '#bottomNav';
const ITEM_SEL = '.navItem';
// 尺寸与动效参数取自参考实现（github.com/mikonyaa/LiquidGlassTabBars，
// 其 LiquidTabBar.swift / LiquidTabBarTheme.swift 把 iOS 26 浮动标签栏的参数写成了默认值）：
//   · 动画：Animation.spring(response: 0.32, dampingFraction: 0.82)
//     → 换算成 CSS：时长 0.439s（稳定时间），缓动用 linear() 精确复刻该弹簧
//   · 尺寸：.frame(minWidth: minimumItemHeight, minHeight: barHeight) + .padding(6)
//     → 内项高度 = 条高 - 12；且**宽度不得小于自身高度**（这是"太窄"的根因，
//       原来 52×57 是宽比高还小，看起来是竖条而不是胶囊）
//   · 选中态左右留白 16（我们按比例取 20，视觉上更像横向胶囊）
const PAD_X = 20;          // 水滴比「图标 + 文字」外框左右各多出的留白
const PAD_Y = 6;           // 上下留白（与参考实现一致）
const MIN_W_RATIO = 1.35;  // 宽度下限 = 高度 × 1.35（参考实现是 ≥1.0，取 1.35 更耐看）
const STRETCH = 1.05;      // 移动中的轻微拉伸（原来 1.16 太夸张，是"生硬"的来源之一）
const MOVE_MS = 439;       // 对应 response 0.32 / dampingFraction 0.82 的稳定时间

let drop = null, nav = null;
let lastX = null, lastW = null;
let animTimer = null;

function currentItem() {
  return nav ? nav.querySelector(ITEM_SEL + '.cur') : null;
}

/** 计算指定项（默认当前选中项）相对 #bottomNav 的外框（包裹图标 + 文字）。 */
function measure(target) {
  const item = target || currentItem();
  if (!item || !nav) return null;
  const nr = nav.getBoundingClientRect();
  const icon = item.querySelector('.nav-ic');
  // 文字是 .navItem 的**直接子** span；不能用 span:not(.nav-ic)——
  // 那样会先匹配到 .nav-ic 里的图标字形 span，文字就被漏掉了。
  let label = null;
  for (let i = 0; i < item.children.length; i++) {
    const ch = item.children[i];
    if (ch.tagName === 'SPAN' && !ch.classList.contains('nav-ic')) { label = ch; break; }
  }
  const parts = [icon, label].filter(Boolean).map(el => el.getBoundingClientRect())
    .filter(r => r.width > 0 && r.height > 0);
  if (!parts.length) return null;
  const left = Math.min.apply(null, parts.map(r => r.left));
  const right = Math.max.apply(null, parts.map(r => r.right));
  const top = Math.min.apply(null, parts.map(r => r.top));
  const bottom = Math.max.apply(null, parts.map(r => r.bottom));
  // 高度取「条内高度」（参考实现：条高 - 2×6 的内边距），保证水滴与胶囊同心
  const h = Math.max(24, nr.height - PAD_Y * 2);
  let w = (right - left) + PAD_X * 2;
  const minW = h * MIN_W_RATIO;          // 宽度下限：不得窄于自身高度（再乘 1.35）
  if (w < minW) w = minW;
  const y = (nr.height - h) / 2;
  // 横向以「图标 + 文字」的中心为基准，超出导航条时贴边收回来
  let x = (left + right) / 2 - nr.left - w / 2;
  if (x < 4) x = 4;
  if (x + w > nr.width - 4) x = Math.max(4, nr.width - 4 - w);
  return { x, w, h, y };
}

/** 落位（不带动画）：首次定位、尺寸变化、皮肤刚切过来时用。 */
function place(noAnim) {
  if (!drop || !nav) return;
  const m = measure();
  if (!m) { drop.style.opacity = '0'; return; }
  drop.style.opacity = '';
  if (noAnim) drop.classList.add('no-anim');
  drop.style.setProperty('--nd-x', m.x.toFixed(1) + 'px');
  drop.style.setProperty('--nd-w', m.w.toFixed(1) + 'px');
  drop.style.setProperty('--nd-y', m.y.toFixed(1) + 'px');
  drop.style.setProperty('--nd-h', m.h.toFixed(1) + 'px');
  if (noAnim) {
    // 强制一次回流后再打开过渡，避免"首次也飞过来"。
    // 注意：不能只靠 requestAnimationFrame —— 后台标签/被节流时 rAF 可能不触发，
    // 那样 no-anim（transition:none）会一直留着，水滴变成"瞬移"。
    // 故 rAF 与定时器双保险，谁先到谁生效（移除不存在的 class 是幂等的）。
    void drop.offsetWidth;
    const clear = () => drop.classList.remove('no-anim');
    requestAnimationFrame(clear);
    setTimeout(clear, 60);
  }
  lastX = m.x; lastW = m.w;
}

/** 带液滴感地移动到当前选中项。 */
function move() {
  if (!drop || !nav) return;
  const m = measure();
  // 隐藏（opacity=0）只发生在"无选中项"时（比如停在 #/artist/123 这类非底栏页面）。
  // **恢复可见必须在成功路径里做**：曾经只写在 place() 里，而从非底栏页面切回
  // 底栏页面走的是 move()，水滴就一直藏着不出来（表现为"水滴包裹样式消失"）。
  if (!m) { drop.style.opacity = '0'; return; }
  drop.style.opacity = '';
  const first = lastX === null;
  if (first) { place(true); return; }

  const dx = m.x - lastX;
  if (Math.abs(dx) < 0.5 && Math.abs(m.w - lastW) < 0.5) {   // 没变，仅同步尺寸
    drop.style.setProperty('--nd-w', m.w.toFixed(1) + 'px');
    drop.style.setProperty('--nd-h', m.h.toFixed(1) + 'px');
    lastW = m.w;
    return;
  }

  // 拉伸方向朝行进方向：向右移动时把原点放左边（向右拉长），反之亦然
  drop.style.transformOrigin = dx > 0 ? 'left center' : 'right center';
  drop.style.setProperty('--nd-x', m.x.toFixed(1) + 'px');
  drop.style.setProperty('--nd-w', m.w.toFixed(1) + 'px');
  drop.style.setProperty('--nd-y', m.y.toFixed(1) + 'px');
  drop.style.setProperty('--nd-h', m.h.toFixed(1) + 'px');

  // 拉伸（liquid 的关键）：与位移动画共用同一条过渡；
  // 落位后由 transitionend 复位到 1，并用定时器兜底 —— 过渡即使被中断也不会卡在"被压扁"的状态。
  drop.classList.add('moving');
  drop.style.setProperty('--nd-sx', String(STRETCH));
  clearTimeout(animTimer);
  animTimer = setTimeout(resetStretch, MOVE_MS + 160);

  lastX = m.x; lastW = m.w;
}

/** 收尾：复位拉伸、去掉 moving 态（可重复调用）。 */
function resetStretch() {
  if (!drop) return;
  clearTimeout(animTimer);
  drop.classList.remove('moving');
  drop.style.setProperty('--nd-sx', '1');
  drop.style.transformOrigin = 'center';
}

/* ── 拖动（scrub）：iOS 的标签栏支持按住横向滑动来选择 ──────────────────
   手指按住底栏左右滑动时，水滴跟着手指走、经过的项以强调色提示，
   松手才真正切换页面。实现要点：
     · 只在 frost 皮肤下启用；
     · 位移/抬起用 Pointer Events + setPointerCapture，手指移出底栏也能继续跟；
     · 拖动超过阈值才接管手势（否则保留原生点击，普通点击照常工作）；
     · 拖动后要拦掉浏览器补发的那次 click，否则会重复导航。 */
const DRAG_THRESHOLD = 8;          // 超过这个位移才算"拖动"（px）
let scrub = null;                  // { id, startX, startY, moved, item }
let suppressClick = false;

function itemAt(clientX) {
  if (!nav) return null;
  const items = nav.querySelectorAll(ITEM_SEL);
  for (let i = 0; i < items.length; i++) {
    const r = items[i].getBoundingClientRect();
    if (clientX >= r.left && clientX <= r.right) return items[i];
  }
  return null;
}

function clearHover() {
  if (!nav) return;
  nav.querySelectorAll(ITEM_SEL + '.hover').forEach(el => el.classList.remove('hover'));
}

/** 拖动预览：水滴跟随手指，并把经过的项点亮。 */
function previewAt(clientX) {
  if (!drop || !nav) return;
  const nr = nav.getBoundingClientRect();
  const w = parseFloat(drop.style.getPropertyValue('--nd-w')) || 70;
  const h = parseFloat(drop.style.getPropertyValue('--nd-h')) || 52;
  let x = clientX - nr.left - w / 2;
  if (x < 4) x = 4;
  if (x + w > nr.width - 4) x = Math.max(4, nr.width - 4 - w);
  drop.classList.add('scrubbing');
  drop.style.setProperty('--nd-x', x.toFixed(1) + 'px');
  drop.style.setProperty('--nd-w', w.toFixed(1) + 'px');
  drop.style.setProperty('--nd-h', h.toFixed(1) + 'px');
  const it = itemAt(clientX);
  if (it !== scrub.item) {
    clearHover();
    if (it && !it.classList.contains('cur')) it.classList.add('hover');
    scrub.item = it;
  }
}

function onDown(e) {
  if (document.documentElement.getAttribute('data-ui-preset') !== 'frost') return;
  if (e.button != null && e.button !== 0) return;
  // ≥900px 是左侧**纵向**导航栏：水滴不显示（见 app.css），横向 scrub 也不适用
  if (window.matchMedia && window.matchMedia('(min-width: 900px)').matches) return;
  if (!e.target.closest || !e.target.closest(ITEM_SEL)) return;
  scrub = { id: e.pointerId, startX: e.clientX, startY: e.clientY, moved: false, item: null, captured: false };
  // 这里**不能**提前 setPointerCapture：捕获会把 pointerup 与后续 click 都重定向到
  // #bottomNav，<a> 就收不到 click —— 桌面用鼠标点导航全部失效（触摸的 click 仍落在
  // 原目标上，所以手机端一直没暴露）。改为超过拖动阈值后才捕获（见 onMove）。
}

function onMove(e) {
  if (!scrub || e.pointerId !== scrub.id) return;
  const dx = e.clientX - scrub.startX, dy = e.clientY - scrub.startY;
  if (!scrub.moved && Math.sqrt(dx * dx + dy * dy) < DRAG_THRESHOLD) return;
  if (!scrub.moved) {
    scrub.moved = true;
    // 超过阈值才接管手势并捕获指针：拖出底栏也能继续跟。
    // 普通点击永远不捕获 → click 落在 <a> 上，桌面鼠标照常跳转。
    try { nav.setPointerCapture(e.pointerId); scrub.captured = true; } catch (err) { /* 忽略 */ }
  }
  if (e.cancelable) e.preventDefault();       // 接管手势：不要再滚动/缩放
  previewAt(e.clientX);
}

function onUp(e) {
  if (!scrub || e.pointerId !== scrub.id) return;
  const moved = scrub.moved, target = scrub.item;
  const id = scrub.id, wasCaptured = scrub.captured;
  scrub = null;
  clearHover();
  if (drop) drop.classList.remove('scrubbing');
  if (wasCaptured) { try { nav.releasePointerCapture(id); } catch (err) { /* 忽略 */ } }
  if (!moved) return;                          // 普通点击：交给 <a> 自己处理
  suppressClick = true;                        // 拦掉这次拖动末尾补发的 click
  setTimeout(() => { suppressClick = false; }, 400);
  if (target && !target.classList.contains('cur')) {
    const href = target.getAttribute('href');
    if (href) location.hash = href;             // 真正切换（路由会更新 .cur，水滴随之落位）
    else move();
  } else {
    move();                                     // 没换项：回到当前项
  }
}

function onCancel(e) {
  if (!scrub || e.pointerId !== scrub.id) return;
  scrub = null;
  clearHover();
  if (drop) drop.classList.remove('scrubbing');
  move();
}

function ensureRefs() {
  nav = document.querySelector(NAV_SEL);
  drop = document.getElementById(DROP_ID);
  return !!(nav && drop);
}

export function refreshNavDrop(noAnim) {
  if (!ensureRefs()) return;
  if (document.documentElement.getAttribute('data-ui-preset') !== 'frost') {
    drop.classList.remove('moving');
    return;                                  // 非玻璃皮肤：不参与定位（CSS 里也不显示）
  }
  // 皮肤刚切过来时 #navDrop 可能还没有过渡状态，直接落位更稳
  if (noAnim) place(true); else move();
}

let timer = null;
const schedule = () => { clearTimeout(timer); timer = setTimeout(() => refreshNavDrop(), 60); };

export function initNavDrop() {
  if (!ensureRefs()) return;

  // 选中项变化：main.js 是给 .navItem 切换 .cur，故监听 class 变化
  try {
    new MutationObserver(schedule).observe(nav, {
      subtree: true, attributes: true, attributeFilter: ['class'],
    });
  } catch (e) { /* 忽略 */ }

  // 位移动画结束即复位拉伸（transitionend 是主路径，定时器兜底）
  drop.addEventListener('transitionend', function (e) {
    if (e.target === drop && e.propertyName === 'transform') resetStretch();
  });
  // 拖动（scrub）：按住底栏横向滑动来选 tab
  if (window.PointerEvent) {
    nav.addEventListener('pointerdown', onDown);
    nav.addEventListener('pointermove', onMove);
    nav.addEventListener('pointerup', onUp);
    nav.addEventListener('pointercancel', onCancel);
    // 拖动结束时浏览器会补发一次 click，必须拦掉，否则会重复导航
    nav.addEventListener('click', function (e) {
      if (suppressClick) { e.preventDefault(); e.stopPropagation(); }
    }, true);
  }
  window.addEventListener('hashchange', schedule);
  window.addEventListener('resize', schedule);
  window.addEventListener('orientationchange', schedule);
  document.addEventListener('cm-uipreset', schedule);
  try {
    if (typeof ResizeObserver !== 'undefined') new ResizeObserver(schedule).observe(nav);
  } catch (e) { /* 忽略 */ }

  // 兜底：字体图标加载完成、安全区迟到等会让外框变化，轻量比对即可
  setInterval(function () {
    if (document.documentElement.getAttribute('data-ui-preset') !== 'frost') return;
    const m = measure();
    if (!m) return;
    if (lastX === null || Math.abs(m.x - lastX) > 0.6 || Math.abs(m.w - lastW) > 0.6) move();
  }, 500);

  refreshNavDrop(true);
  // 首次进入时图标字体可能还没就绪，量到的宽度会偏小，稍后补一次
  [120, 420, 1200].forEach(ms => setTimeout(() => refreshNavDrop(true), ms));
}

/** 供验证脚本读取当前水滴几何。 */
export function navDropDebug() {
  // 供验证脚本读取拖动状态
  return _navDropDebug();
}

function _navDropDebug() {
  if (!drop) return null;
  return {
    x: drop.style.getPropertyValue('--nd-x'),
    w: drop.style.getPropertyValue('--nd-w'),
    visible: getComputedStyle(drop).display !== 'none',
    opacity: getComputedStyle(drop).opacity,
  };
}
