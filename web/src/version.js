// 版本信息：App 内取原生真实版本；浏览器调试用构建常量（发版时与 APK 同步更新）
export const WEB_VERSION = { name: '1.28.11', code: 124 };

export function currentVersion() {
  const b = window.NativeApi;
  if (b && b.versionCode) {
    try {
      const code = b.versionCode();
      const name = b.versionName ? b.versionName() : WEB_VERSION.name;
      if (code > 0) return { name, code };
    } catch { /* 回退 */ }
  }
  return WEB_VERSION;
}

/** 版本比较：优先比 versionCode，缺失时比语义化版本串。 */
export function isNewer(latest, cur) {
  if (latest.versionCode > 0 && cur.code > 0) return latest.versionCode > cur.code;
  const pa = String(latest.version || '0').split('.').map(Number);
  const pb = String(cur.name || '0').split('.').map(Number);
  for (let i = 0; i < 3; i++) {
    const a = pa[i] || 0, b = pb[i] || 0;
    if (a !== b) return a > b;
  }
  return false;
}

/** 系统 WebView（Chromium）版本；Android 下用于判断渲染/脚本能力是否足够。 */
export function engineChrome() {
  const m = (navigator.userAgent || '').match(/Chrome\/(\d+)/);
  return m ? parseInt(m[1], 10) : 0;
}

/** 低于此版本时给出更新 WebView 的引导（CSS/JS 已做降级，但更新后体验最佳）。 */
export const ENGINE_MIN_RECOMMENDED = 70;

export function engineOutdated() {
  const v = engineChrome();
  return v > 0 && v < ENGINE_MIN_RECOMMENDED;
}
