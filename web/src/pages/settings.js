// 设置页：配色/音质/主题/下载目录/服务器/关于（与「我的」分离，顶栏齿轮直达）
import { mdui } from '../md.js';
import { api, settings, auth } from '../api.js';
import { esc, toast, promptDialog, COLOR_SCHEMES, getColorSchemeKey, setColorSchemeKey, QUALITY_TIERS, tierLabel, FONT_SCALES, getFontScaleKey, setFontScaleKey } from '../ui.js';
import { checkUpdate } from '../update.js';
import { currentVersion, engineChrome, engineOutdated, ENGINE_MIN_RECOMMENDED } from '../version.js';
import { UI_PRESETS, uiPresetKey, uiPresetName, setUiPreset } from '../uipreset.js';
import { bgImage, glassBlur, glassTint, glassRange, setBgImage, setGlass, resetGlass, applyCustomize } from '../customize.js';
import { waveStyle, setWaveStyle, waveTilt, setWaveTilt, TILT_DEFAULT } from '../customize.js';
import { WAVE_STYLES } from '../waveform.js';

export function applyTheme() {
  const root = document.documentElement;
  root.classList.remove('mdui-theme-light', 'mdui-theme-dark', 'mdui-theme-auto');
  if (settings.theme === 'light') root.classList.add('mdui-theme-light');
  else if (settings.theme === 'dark') root.classList.add('mdui-theme-dark');
  else {
    // auto：Android 壳里跟随系统（桥查询），浏览器里用 media query
    const sysDark = typeof window.NativeApi !== 'undefined' && window.NativeApi.isDarkMode
      ? window.NativeApi.isDarkMode()
      : window.matchMedia('(prefers-color-scheme: dark)').matches;
    root.classList.add(sysDark ? 'mdui-theme-dark' : 'mdui-theme-light');
  }
}

export function themeName() {
  return { auto: '跟随系统', light: '浅色', dark: '深色' }[settings.theme] || settings.theme;
}

export async function render(el) {
  const u = auth.user || {};
  const isApp = !!(window.NativeApi && window.NativeApi.versionCode);   // App 内不显示网页专属入口
  el.innerHTML = `
    <div class="cm-sec-head"><h2>外观</h2></div>
    <div class="cm-setting-list">
      <div class="cm-setting" id="scheme"><span class="material-icons-outlined">palette</span>配色方案<i>${esc((COLOR_SCHEMES.find(s => s.key === getColorSchemeKey()) || {}).label || '动态取色')}</i></div>
      <div class="cm-setting" id="theme"><span class="material-icons-outlined">dark_mode</span>外观主题<i>${themeName()}</i></div>
      <div class="cm-setting" id="uiPreset"><span class="material-icons-outlined">auto_awesome</span>界面风格<i>${esc(uiPresetName())}</i></div>
        <div class="cm-setting" id="bgImage"><span class="material-icons-outlined">wallpaper</span>背景图片<i>${bgImage() ? '已自定义' : '默认'}</i></div>
        <div class="cm-setting" id="glassFx"><span class="material-icons-outlined">blur_on</span>玻璃效果<i>${glassTint() == null ? '默认' : '已自定义'}</i></div>
        <div class="cm-setting" id="waveStyle"><span class="material-icons-outlined">graphic_eq</span>波形样式<i>${(WAVE_STYLES.find(x => x.key === waveStyle()) || WAVE_STYLES[0]).name}</i></div>
        <div class="cm-setting" id="waveTilt"><span class="material-icons-outlined">trending_up</span>频谱倾斜<i>${waveTilt() <= 0 ? '关闭' : waveTilt().toFixed(1) + ' dB/oct'}</i></div>
        <div class="cm-setting" id="fontScale"><span class="material-icons-outlined">format_size</span>字体大小<i>${esc((FONT_SCALES.find(x => x.key === getFontScaleKey()) || {}).label || '标准')}</i></div>
    </div>
    <div class="cm-sec-head"><h2>播放与下载</h2></div>
    <div class="cm-setting-list">
      <div class="cm-setting" id="quality"><span class="material-icons-outlined">high_quality</span>默认音质<i>${tierLabel(settings.quality)}</i></div>
      <div class="cm-setting" id="dlDir"><span class="material-icons-outlined">download</span>下载目录<i>Music/${esc(localStorage.getItem('cm.downloadDir') || 'CurrentMusic')}</i></div>
    </div>
    <div class="cm-sec-head"><h2>隐私</h2></div>
    <div class="cm-setting-list">
      <div class="cm-setting" id="sqPublic">
        <span class="material-icons-outlined">public</span>在「发现」页公开我
        <i>${!auth.token ? '登录后可设置' : (u.publicSquare === false ? '已关闭' : '公开中')}</i>
        <mdui-switch id="sqPublicSw" aria-label="公开到发现页" ${u.publicSquare === false || !auth.token ? '' : 'checked'} ${auth.token ? '' : 'disabled'} style="margin-left:8px"></mdui-switch>
      </div>
    </div>
    <div class="cm-sec-head"><h2>服务器</h2></div>
    <div class="cm-setting-list">
      <div class="cm-setting" id="secure"><span class="material-icons-outlined">lock</span>安全连接<i>${settings.secureMode ? 'HTTPS 已启用' : '已关闭'}</i></div>
      <div class="cm-setting" id="server"><span class="material-icons-outlined">dns</span>服务器地址<i>${esc(settings.base)}</i></div>
    </div>
    <div class="cm-sec-head"><h2>关于</h2></div>
    <div class="cm-setting-list">
      ${!isApp ? `<div class="cm-setting" id="dlApk"><span class="material-icons-outlined">android</span>下载安卓版 APP<i>APK · 支持锁屏控制</i></div>` : ''}
      <div class="cm-setting" id="checkUpd"><span class="material-icons-outlined">system_update</span>检查更新<i>v${esc(currentVersion().name)}<span class="material-icons-outlined" style="font-size:calc(15px * var(--cm-fs, 1));vertical-align:-3px;margin-left:4px">chevron_right</span></i></div>
      <div class="cm-setting"><span class="material-icons-outlined">person</span>当前账号<i>${esc(u.nickname || u.username || '未登录')}${u.isSuper ? ' · 超级管理员' : u.isAdmin ? ' · 管理员' : ''}</i></div>
      ${(auth.user && auth.user.isAdmin) ? `<div class="cm-setting" id="adminEntry"><span class="material-icons-outlined">admin_panel_settings</span>管理员面板<i>用户/设备/系统</i></div>` : ''}
      ${auth.token ? `<div class="cm-setting" id="devices"><span class="material-icons-outlined">devices</span>登录设备<i id="devCount">—</i></div>` : ''}
      <div class="cm-setting" id="engine"><span class="material-icons-outlined">public</span>系统 WebView<i>${engineChrome() ? 'Chromium ' + engineChrome() : (isApp ? '未知' : '浏览器')}${engineOutdated() ? ' · 建议更新' : ''}</i></div>
    </div>`;

  // 公开到发现页广场：关闭后不出现在广场列表（主页仍可被链接访问）
  const sqSw = el.querySelector('#sqPublicSw');
  if (sqSw) {
    // 本地缓存的用户对象可能没有该字段（老会话）：进页面时用服务端值纠正显示
    if (auth.token) api.me().then(me => {
      const on = me.publicSquare !== false;
      sqSw.checked = on;
      const i = el.querySelector('#sqPublic i');
      if (i) i.textContent = on ? '公开中' : '已关闭';
    }).catch(() => {});
    sqSw.addEventListener('change', async () => {
      if (!auth.token) return;
      const on = !!sqSw.checked;
      sqSw.disabled = true;
      try {
        const r = await api.setSquarePublic(on);
        auth.saveLogin(auth.token, r);            // 同步本地缓存的用户信息
        const i = el.querySelector('#sqPublic i');
        if (i) i.textContent = on ? '公开中' : '已关闭';
        toast(on ? '已公开到「发现」页' : '已从「发现」页隐藏');
      } catch (e) {
        sqSw.checked = !on;                     // 回滚
        toast('设置失败：' + e.message);
      } finally { sqSw.disabled = false; }
    });
  }

  el.querySelector('#dlApk')?.addEventListener('click', () => {
    location.href = settings.base + '/download/latest';   // 后端 302 到最新版安装包
  });

  el.querySelector('#scheme').onclick = () => {
    const cur = getColorSchemeKey();
    const diag = mdui.dialog({
      headline: '配色方案',
      body: `<div class="cm-more">
        <div class="cm-more-chips">${COLOR_SCHEMES.map(s =>
          `<mdui-chip ${s.key === cur ? 'selected' : ''} data-k="${s.key}"><i class="cm-swatch ${s.key === 'dynamic' ? 'dyn' : ''}" style="background:${s.color || 'linear-gradient(135deg,#6750a4,#e65100)'}"></i>${s.label}</mdui-chip>`).join('')}</div>
        <div class="cm-more-s" style="margin-top:10px">动态取色：主色随正在播放的封面自动变化</div>
      </div>`,
      actions: [{ text: '关闭' }],
    });
    setTimeout(() => {
      diag.querySelectorAll('mdui-chip').forEach(ch => {
        ch.onclick = () => {
          setColorSchemeKey(ch.dataset.k);
          toast(`配色：${ch.textContent.trim()}`);
          diag.open = false;
          render(el);
        };
      });
    }, 0);
  };
  el.querySelector('#uiPreset').onclick = () => {
    const cur = uiPresetKey();
    // 「液体玻璃」暂不对外提供：实现完整保留（uipreset.js / app.css / glass.js），
    // 想恢复入口删掉 HIDING_PRESETS 里的 'glass' 即可。
    // 当前正处于该皮肤时仍要显示，否则用户会被锁在里面无法切走。
    const HIDING_PRESETS = ['glass'];
    const picks = UI_PRESETS.filter(p => !HIDING_PRESETS.includes(p.key) || p.key === cur);
    const diag = mdui.dialog({
      headline: '界面风格',
      body: `<div class="cm-more">
        <div class="cm-more-chips">${picks.map(p =>
          `<mdui-chip ${p.key === cur ? 'selected' : ''} data-k="${p.key}"><i class="cm-swatch ${p.swatch}"></i>${p.name}</mdui-chip>`).join('')}</div>
        <div class="cm-more-s" style="margin-top:10px">${esc((UI_PRESETS.find(p => p.key === cur) || {}).desc || '')}</div>
        <div class="cm-more-s" style="margin-top:6px">「简约玻璃」把玻璃只用在顶栏/底栏/弹窗等浮层，内容卡片走磨砂白 + 发丝线、不用投影。旧版系统会自动降级为半透明纯色，不影响使用。Material 3 为无玻璃的原版样式。</div>
      </div>`,
      actions: [{ text: '关闭' }],
    });
    setTimeout(() => {
      diag.querySelectorAll('mdui-chip').forEach(ch => {
        ch.onclick = () => {
          setUiPreset(ch.dataset.k);
          toast(`界面风格：${ch.textContent.trim()}`);
          diag.open = false;
          render(el);
        };
      });
    }, 0);
  };
  // ---- 背景图片：URL 或本地上传（存 localStorage，dataURL 过大时拒绝）----
  el.querySelector('#bgImage').onclick = () => {
    const cur = bgImage();
    const diag = mdui.dialog({
      headline: '背景图片',
      body: `<div class="cm-more">
        <mdui-text-field id="bgUrl" label="图片地址（https:// 或 http://）" variant="outlined" value="${esc(cur.startsWith('data:') ? '' : cur)}" style="width:100%"></mdui-text-field>
        <div class="cm-more-row">
          <div>
            <div class="cm-more-t">本地上传</div>
            <div class="cm-more-s">转成数据内联保存，仅适合小图（&lt; 1.5MB）</div>
          </div>
          <mdui-button variant="tonal" id="bgPick">选择图片</mdui-button>
        </div>
        <div class="cm-more-s" style="margin-top:8px">设置后图片铺满屏幕并固定，内容与玻璃浮层浮在其上；「简约玻璃」下配合玻璃效果可直观看到磨砂变化。留空并点确定即恢复默认背景。</div>
      </div>`,
      actions: [
        { text: '清除', onClick: () => { setBgImage(''); toast('已恢复默认背景'); render(el); } },
        { text: '取消' },
        {
          text: '确定',
          onClick: () => {
            const v = (diag.querySelector('#bgUrl')?.value || '').trim();
            setBgImage(v);
            toast(v ? '背景图片已应用' : '已恢复默认背景');
            diag.open = false;
            render(el);
          },
        },
      ],
    });
    setTimeout(() => {
      const pick = diag.querySelector('#bgPick');
      if (!pick) return;
      pick.onclick = () => {
        const fi = document.createElement('input');
        fi.type = 'file'; fi.accept = 'image/*';
        fi.onchange = () => {
          const f = fi.files && fi.files[0];
          if (!f) return;
          if (f.size > 1.5 * 1024 * 1024) { toast('图片过大（' + Math.round(f.size / 1024) + 'KB），请压缩到 1.5MB 以内或改用图片链接'); return; }
          const r = new FileReader();
          r.onload = () => {
            const data = String(r.result);
            try { localStorage.setItem('cm.bgImage', data); } catch (e) {
              toast('保存失败：本地存储空间不足，建议改用图片链接'); return;
            }
            setBgImage(data);
            toast('背景图片已应用');
            diag.open = false;
            render(el);
          };
          r.readAsDataURL(f);
        };
        fi.click();
      };
    }, 0);
  };

  // ---- 玻璃效果：模糊 + 浓度，拖动即时生效（关闭时才重建折射透镜）----
  el.querySelector('#glassFx').onclick = () => {
    let blur = glassBlur(), tint = glassTint();
    const tintDef = 0.58;
    const diag = mdui.dialog({
      headline: '玻璃效果',
      body: `<div class="cm-more" style="min-width:min(84vw,340px)">
        <div class="cm-more-row">
          <div class="cm-more-t">模糊强度</div>
          <span id="fxBlurV" style="font-size:calc(12px * var(--cm-fs, 1));opacity:.7">${blur}</span>
        </div>
        <input type="range" id="fxBlur" min="${glassRange.BLUR_MIN}" max="${glassRange.BLUR_MAX}" step="0.5" value="${blur}" style="width:100%">
        <div class="cm-more-row" style="margin-top:10px">
          <div class="cm-more-t">玻璃浓度</div>
          <span id="fxTintV" style="font-size:calc(12px * var(--cm-fs, 1));opacity:.7">${tint == null ? '默认' : Math.round(tint * 100) + '%'}</span>
        </div>
        <input type="range" id="fxTint" min="${Math.round(glassRange.TINT_MIN * 100)}" max="${Math.round(glassRange.TINT_MAX * 100)}" step="5" value="${Math.round((tint == null ? tintDef : tint) * 100)}" style="width:100%">
        <div class="cm-more-s" style="margin-top:10px">作用于「简约玻璃」的底栏胶囊、迷你播放条与桌面侧栏。浓度调低更通透、调高更实；模糊影响折射的柔和度。</div>
      </div>`,
      actions: [
        { text: '恢复默认', onClick: () => { resetGlass(); toast('玻璃效果已恢复默认'); diag.open = false; render(el); } },
        { text: '关闭', onClick: () => { applyCustomize({ rebuild: true }); } },
      ],
    });
    setTimeout(() => {
      const b = diag.querySelector('#fxBlur'), t = diag.querySelector('#fxTint');
      const bv = diag.querySelector('#fxBlurV'), tv = diag.querySelector('#fxTintV');
      if (!b || !t) return;
      b.oninput = () => {
        blur = parseFloat(b.value);
        bv.textContent = String(blur);
        setGlass({ blur });          // 即时生效（不重建透镜，拖动才流畅）
      };
      t.oninput = () => {
        tint = parseInt(t.value, 10) / 100;
        tv.textContent = Math.round(tint * 100) + '%';
        setGlass({ tint });
      };
    }, 0);
  };

  el.querySelector('#waveTilt').onclick = () => {
    let cur = waveTilt();
    const diag = mdui.dialog({
      headline: '频谱倾斜补偿',
      body: `<div class="cm-more" style="min-width:min(84vw,340px)">
        <div class="cm-more-row">
          <div class="cm-more-t">补偿强度</div>
          <span id="tiltV" style="font-size:12px;opacity:.7">${cur <= 0 ? '关闭' : cur.toFixed(1) + ' dB/oct'}</span>
        </div>
        <input type="range" id="tiltR" min="0" max="9" step="0.5" value="${cur}" style="width:100%">
        <div class="cm-more-s" style="margin-top:10px">
          音乐能量天然集中在低频（实测左右相差约 30dB），所以原始频谱总是"左高右低"。
          按 +N dB/倍频程 抬高高频可以让它更均衡：<b>0 = 关闭</b>（原始频谱），
          <b>4.5 = 标准补偿</b>（行业常用），再高更平。
        </div>
      </div>`,
      actions: [
        { text: '恢复默认', onClick: () => { setWaveTilt(TILT_DEFAULT); toast('已恢复标准补偿'); diag.open = false; render(el); } },
        // 关闭时重渲染，否则行上显示的数值不会随滑块更新
        { text: '关闭', onClick: () => { render(el); } },
      ],
    });
    setTimeout(() => {
      const r = diag.querySelector('#tiltR'), v = diag.querySelector('#tiltV');
      if (!r) return;
      r.oninput = () => {
        cur = parseFloat(r.value);
        v.textContent = cur <= 0 ? '关闭' : cur.toFixed(1) + ' dB/oct';
        setWaveTilt(cur);            // 即时生效（播放页开着也会立刻重建波形）
      };
    }, 0);
  };

  el.querySelector('#waveStyle').onclick = () => {
    const cur = waveStyle();
    const diag = mdui.dialog({
      headline: '波形样式',
      body: `<div class="cm-more" style="min-width:min(84vw,340px)">
        ${WAVE_STYLES.map(x => `<div class="cm-more-row" data-k="${x.key}">
          <div><div class="cm-more-t">${x.name}${x.key === cur ? ' ✓' : ''}</div>
            <div class="cm-more-s">${x.desc}</div></div>
          <span class="material-icons-outlined">${x.key === cur ? 'radio_button_checked' : 'radio_button_unchecked'}</span>
        </div>`).join('')}
        <div class="cm-more-s" style="margin-top:8px">波形只做「随音乐起伏」的指示，不表达播放进度（进度看下方进度条）。
          想看实际效果可打开<a href="/ui-preview/waveform.html" target="_blank" rel="noopener">波形对比预览页</a>。</div>
      </div>`,
      actions: [{ text: '关闭' }],
    });
    setTimeout(() => {
      diag.querySelectorAll('.cm-more-row').forEach(row => {
        row.onclick = () => {
          setWaveStyle(row.dataset.k);      // 即时生效（播放页开着也会立刻换）
          toast('波形样式：' + ((WAVE_STYLES.find(x => x.key === row.dataset.k) || {}).name || ''));
          diag.open = false;
          render(el);
        };
      });
    }, 0);
  };

  // 字体大小：档位 + **实时预览**。点一下立即全站生效（对话后面的界面同时变化），
  // 预览区再给一份直观对照（列表行 / 歌词 / 正文三种典型文字）。
  el.querySelector('#fontScale').onclick = () => {
    const preview = k => {
      const f = FONT_SCALES.find(x => x.key === k) || FONT_SCALES[1];
      return `
      <div class="cm-fsprev" style="--cm-fs:${f.f}">
        <div class="cm-fsprev-row">
          <div class="cm-fsprev-pic"><span class="material-icons-outlined">music_note</span></div>
          <div class="cm-fsprev-main">
            <div class="cm-fsprev-name">晴天</div>
            <div class="cm-fsprev-sub">周杰伦 · 叶惠美</div>
          </div>
          <span class="material-icons-outlined cm-fsprev-play">play_circle</span>
        </div>
        <div class="cm-fsprev-lyric">故事的小黄花，从出生那年就飘着</div>
        <div class="cm-fsprev-body">列表、歌词、设置项都会跟着变；只放大文字，行高与间距不变，版面不会被撑乱。</div>
      </div>`;
    };
    const cur = getFontScaleKey();
    const diag = mdui.dialog({
      headline: '字体大小',
      body: `<div class="cm-more">
        <div class="cm-more-chips">${FONT_SCALES.map(x =>
          `<mdui-chip ${x.key === cur ? 'selected' : ''} data-k="${x.key}">${x.label}</mdui-chip>`).join('')}</div>
        <div class="cm-more-s" style="margin-top:10px" id="fsDesc"></div>
        <div class="cm-more-s" style="margin:16px 0 8px">预览</div>
        <div id="fsPrev">${preview(cur)}</div>
        <div class="cm-more-s" style="margin-top:12px">默认「标准」。本 App <b>不跟随系统字体大小</b>
          （否则同一份设置在不同手机上大小不一致），只看这里的档位。</div>
      </div>`,
      actions: [{ text: '完成' }],
    });
    setTimeout(() => {
      const desc = diag.querySelector('#fsDesc'), prev = diag.querySelector('#fsPrev');
      const paint = k => {
        const f = FONT_SCALES.find(x => x.key === k) || FONT_SCALES[1];
        desc.textContent = f.desc;
        prev.innerHTML = preview(k);
      };
      paint(cur);
      diag.querySelectorAll('mdui-chip').forEach(ch => {
        ch.onclick = () => {
          const k = setFontScaleKey(ch.dataset.k);          // 立即生效
          diag.querySelectorAll('mdui-chip').forEach(c => { c.selected = c.dataset.k === k; });
          paint(k);
          toast('字体大小：' + ((FONT_SCALES.find(x => x.key === k) || {}).label || ''));
        };
      });
    }, 0);
  };

  el.querySelector('#theme').onclick = () => {
    const order = ['auto', 'light', 'dark'];
    const next = order[(order.indexOf(settings.theme) + 1) % 3];
    settings.theme = next;
    applyTheme();
    toast(`主题：${themeName()}`);
    render(el);
  };
  el.querySelector('#quality').onclick = () => {
    const diag = mdui.dialog({
      headline: '默认音质',
      body: `<div class="cm-qm">${QUALITY_TIERS.map(t =>
        `<div class="cm-qm-item" data-v="${t.key}">${t.label}${t.key === settings.quality ? '<span class="material-icons-outlined">check</span>' : ''}</div>`).join('')}</div>`,
      actions: [{ text: '关闭' }],
    });
    setTimeout(() => {
      diag.querySelectorAll('.cm-qm-item').forEach(it => {
        it.onclick = () => {
          settings.quality = it.dataset.v;
          toast(`默认音质：${it.textContent.trim()}`);
          diag.close();
          render(el);
        };
      });
    }, 0);
  };
  el.querySelector('#dlDir').onclick = () => promptDialog({
    title: '下载目录', label: 'Music/ 下的子目录名', value: localStorage.getItem('cm.downloadDir') || 'CurrentMusic',
    onOk: v => { localStorage.setItem('cm.downloadDir', v.trim() || 'CurrentMusic'); toast('已保存'); render(el); },
  });
  el.querySelector('#engine').onclick = () => {
    const v = engineChrome();
    if (!engineOutdated()) return toast(`当前 WebView：Chromium ${v || '未知'}，无需更新`);
    mdui.dialog({
      headline: '建议更新系统 WebView',
      body: `<div style="font-size:calc(13.5px * var(--cm-fs, 1));line-height:1.8">
        当前内核为 <b>Chromium ${v}</b>，低于建议版本 ${ENGINE_MIN_RECOMMENDED}，
        部分界面效果或播放体验可能受影响。<br>
        在应用商店更新「Android System WebView」（或 Chrome）后重启 App 即可恢复最佳效果。
      </div>`,
      actions: [
        { text: '稍后' },
        { text: '去更新', onClick: () => {
            if (window.NativeApi && window.NativeApi.openWebViewUpdate) window.NativeApi.openWebViewUpdate();
            else toast('请到应用商店搜索「Android System WebView」');
          } },
      ],
    });
  };
  el.querySelector('#adminEntry')?.addEventListener('click', () => { location.hash = '#/admin'; });

  const devRow = el.querySelector('#devices');
  if (devRow) {
    const fmtTime = ts => {
      if (!ts) return '—';
      const d = new Date(ts * 1000), now = new Date();
      const diff = (now - d) / 1000;
      if (diff < 120) return '刚刚活跃';
      if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前';
      if (d.toDateString() === now.toDateString()) return '今天 ' + d.getHours() + ':' + String(d.getMinutes()).padStart(2, '0');
      return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;
    };
    let sessions = [];
    const refresh = async () => {
      try {
        const d = await api.sessions();
        sessions = d.sessions || [];
        const c = el.querySelector('#devCount');
        if (c) c.textContent = `${sessions.length}/${d.max} 台`;
        return d.max;
      } catch (e) {
        const c = el.querySelector('#devCount');
        if (c) c.textContent = '查看';
        return 5;
      }
    };
    refresh();
    devRow.onclick = async () => {
      const max = await refresh();
      const diag = mdui.dialog({
        headline: `登录设备（最多 ${max} 台）`,
        body: `<div class="cm-devs">
          ${sessions.map(s => `
            <div class="cm-dev">
              <span class="material-icons-outlined">${s.platform === 'web' ? 'language' : 'smartphone'}</span>
              <div class="cm-dev-main">
                <div>${esc(s.device)}${s.current ? '<span class="cm-dev-cur">本机</span>' : ''}</div>
                <div class="cm-dev-sub">登录于 ${fmtTime(s.createdAt)} · ${fmtTime(s.lastSeen)}</div>
              </div>
              ${s.current ? '' : `<span class="cm-dev-kick" data-id="${s.id}">退出</span>`}
            </div>`).join('') || '<div class="cm-empty small">暂无设备</div>'}
        </div>`,
        actions: [
          { text: '关闭' },
          { text: '退出其他设备', onClick: () => {
              api.revokeOtherSessions().then(r => {
                toast(`已退出 ${r.removed} 台设备`);
                diag.open = false;
                render(el);
              }).catch(e => toast(e.message));
              return false;
            } },
        ],
      });
      setTimeout(() => {
        diag.querySelectorAll('.cm-dev-kick').forEach(k => {
          k.onclick = async () => {
            try {
              await api.revokeSession(k.dataset.id);
              toast('该设备已下线');
              k.closest('.cm-dev').remove();
              refresh();
            } catch (e) { toast(e.message); }
          };
        });
      }, 0);
    };
  }

  el.querySelector('#checkUpd').onclick = async () => {
    toast('正在检查更新…');
    await checkUpdate({ silent: false });
  };
  el.querySelector('#secure').onclick = () => {
    const next = !settings.secureMode;
    settings.secureMode = next;
    toast(next ? '安全连接已启用（HTTPS）' : '安全连接已关闭（允许 HTTP）');
    render(el);
  };
  el.querySelector('#server').onclick = () => promptDialog({
    title: '服务器地址', label: 'API 基址', value: settings.base,
    onOk: v => { settings.base = v.replace(/\/+$/, ''); toast('已保存'); render(el); },
  });
}
