// 兼容旧页面的非语义点击元素。原生 button / a 保持原样；新版页面逐步改用真正的按钮。
// 这里补充键盘可操作性和可访问名称，而不是只模拟视觉上的焦点。
const ACTIONS = [
  '.pl-btn', '.pl-quality', '.cm-mini-inner', '.cm-mini-btn',
  '.cm-song', '.cm-song-like', '.cm-song-more', '.cm-card', '.cm-plcard',
  '.cm-sec-more', '.cm-quick', '.cm-setting[id]', '.cm-hot', '.cm-hist-k',
  '.cm-hist-x', '.cm-artist-card', '.cm-more-row[id]', '.cm-ava-wrap',
  '.cmt-mode', '.cmt-del', '.cmt-like', '.cmt-reply', '.cmt-report',
  '.cmt-hug', '.cmt-floor-btn', '#cmtMore', '.pl-lyric-line',
].join(',');
const NATIVE = 'a, button, input, textarea, select, summary, mdui-button, mdui-chip, mdui-switch';

function enhanceOne(el) {
  if (el.matches(NATIVE) || el.hasAttribute('disabled') || el.closest('[inert]')) return;
  if (el.dataset.cmActionReady) return;
  el.dataset.cmActionReady = '1';
  if (!el.hasAttribute('role')) el.setAttribute('role', 'button');
  if (!el.hasAttribute('tabindex')) el.tabIndex = 0;
  if (!el.hasAttribute('aria-label')) {
    const name = el.getAttribute('title') || (el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 72);
    if (name) el.setAttribute('aria-label', name);
  }
}

function enhanceTree(root) {
  if (!(root instanceof Element)) return;
  if (root.matches(ACTIONS)) enhanceOne(root);
  root.querySelectorAll(ACTIONS).forEach(enhanceOne);
}

export function initAccessibleControls() {
  enhanceTree(document.body);
  // 路由每次重绘；播放器、评论抽屉由其它模块动态创建。
  const observer = new MutationObserver(records => {
    records.forEach(record => record.addedNodes.forEach(enhanceTree));
  });
  observer.observe(document.body, { subtree: true, childList: true });
  document.addEventListener('keydown', e => {
    if (!e.target.matches?.('[data-cm-action-ready="1"]') || e.target.closest('[inert]')) return;
    if (e.key !== 'Enter' && e.key !== ' ') return;
    e.preventDefault(); // Space 不能同时滚动页面
    e.target.click();
  });
}
