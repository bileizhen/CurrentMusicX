// 首页：每日推荐 + 排行榜 + 猜你喜欢（登录后） + 最近播放（登录后）
import { api, auth, ncmSongs } from '../api.js';
import { esc, renderSongList, skelCards, skelList } from '../ui.js';
import { player } from '../player.js';

// 头像挂件更新横幅：仅在「已登录 + 听歌时长达标 + 尚未设置挂件」时出现，设好后自动消失
// （永久占位会打扰已设置的用户；未达标者由「我的」页设置行引导，首页不做催促）
function decorBannerFromMe(me) {
  if (!me || me.avatarDecoration || !me.decorUnlocked) return '';
  return `
    <a class="cm-decor-banner" href="#/user?decor=1">
      <span class="material-icons-outlined">face_retouching_natural</span>
      <span class="cm-decor-banner-t">
        <b>重大更新：听歌时长 2 小时及以上的用户现已支持设置动态头像挂件！</b>
        <span class="cm-decor-banner-sub">挂件会在个人主页、发现页等位置公开展示</span>
      </span>
      <span class="cm-decor-banner-go">点此设置<span class="material-icons-outlined">chevron_right</span></span>
    </a>`;
}

async function decorBannerHTML() {
  if (!auth.token) return '';
  try { return decorBannerFromMe(await api.me()); } catch (e) { return ''; }   // 未登录/限流/断网都静默不显示
}

export async function render(el) {
  const today = new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' });
  el.innerHTML = `
    <div class="cm-greet">
      <div class="cm-greet-date">${esc(today)}</div>
      <div class="cm-greet-title">${auth.user ? `你好，${esc(auth.user.nickname)}` : '今天想听点什么？'}</div>
    </div>
    <div id="decorBanner"></div>
    <section class="cm-sec"><div class="cm-sec-head"><h2>每日推荐</h2></div>${skelCards(8)}</section>
    <section class="cm-sec">${skelList(3)}</section>`;
  // 横幅不阻塞首屏：与每日推荐并行请求；结果先存起来，整页重渲染后再插入
  let bannerHTML = '';
  let daily = [], forYou = [], recent = [], artists = [];
  let recPls = [], ncmBound = false;
  // 首屏聚合（v1.28.3）：me/daily/bind/recent 一次请求取回，服务端并行查好。
  // 原先这四条要各自建立连接（移动网络下每次 TLS 握手都要付出代价）。
  // 旧服务端没有 /bootstrap → boot 为 null，走下面的回退分支，行为与之前完全一致。
  const boot = await api.bootstrap().catch(() => null);
  if (boot && boot.daily) {
    daily = boot.daily.daily || [];
    forYou = boot.daily.forYou || [];
    artists = boot.daily.artists || [];
    bannerHTML = decorBannerFromMe(boot.me);
    ncmBound = !!(boot.bind && boot.bind.bound);
    recent = (boot.recent && boot.recent.songs) || [];
  } else {
    const bannerJob = decorBannerHTML().then(html => { bannerHTML = html; }).catch(() => {});
    const jobs = [api.daily().then(d => { daily = d.daily || []; forYou = d.forYou || []; artists = d.artists || []; }).catch(() => {})];
    if (auth.token) jobs.push(api.bindStatus().then(b => { if (b && b.bound) ncmBound = true; }).catch(() => {}));
    if (auth.token) jobs.push(api.recentPlays(20).then(d => { recent = d.songs || []; }).catch(() => {}));
    await Promise.allSettled(jobs);
    await bannerJob;   // 横幅请求通常更快，这里几乎立即返回
  }
  // 每日推荐歌单/每日推荐歌曲是账号维度接口（T1）：必须用用户自己的网易云 cookie，
  // 且是**上游**接口，所以仍单独取（放进聚合会让整个首屏等上游）。未绑定则整体跳过。
  if (auth.token && ncmBound) {
    await Promise.allSettled([
      api.ncm('/recommend/resource').then(d => { recPls = d.recommend || []; }),
      // T1：账号维度的每日推荐。拿到就用它覆盖服务端 SVIP 档（对已绑定用户更准确）
      api.ncm('/recommend/songs').then(d => {
        const own = ncmSongs(((d.data || {}).dailySongs) || []);
        if (own.length) daily = own;
      }),
    ]);
  }

  el.innerHTML = `
    <div class="cm-greet">
      <div class="cm-greet-date">${esc(today)}</div>
      <div class="cm-greet-title">${auth.user ? `你好，${esc(auth.user.nickname)}` : '今天想听点什么？'}</div>
    </div>
    ${bannerHTML}
    <section class="cm-sec">
      <div class="cm-sec-head"><h2>一键开听</h2><span class="cm-sec-sub">按听歌动线排</span></div>
      <div class="cm-quickrow">
        <a class="cm-quick" href="#/podcast"><span class="material-icons-outlined">radio</span><b>私人 FM</b><i>无限电台</i></a>
        <span class="cm-quick" id="quickHeart"><span class="material-icons-outlined">auto_awesome</span><b>心动模式</b><i>按口味续播</i></span>
        <span class="cm-quick" id="quickDaily"><span class="material-icons-outlined">play_circle</span><b>每日推荐</b><i>${daily.length ? `${daily.length} 首` : '今日待出'}</i></span>
        <a class="cm-quick" href="#/library"><span class="material-icons-outlined">queue_music</span><b>我的歌单</b><i>收藏与自建</i></a>
      </div>
    </section>
    <section class="cm-sec">
      <div class="cm-sec-head"><h2>每日推荐</h2><span class="cm-sec-more" id="playDaily"><span class="material-icons-outlined">play_circle</span> 播放全部</span></div>
      ${daily.length ? `<div class="cm-hscroll" id="dailyRow">${
        daily.slice(0, 12).map((s, i) => `
          <div class="cm-card" data-i="${i}">
            <img src="${esc(s.pic)}" loading="lazy" onerror="this.classList.add('none')">
            <div class="cm-card-name">${esc(s.name)}</div>
            <div class="cm-card-sub">${esc(s.artists)}</div>
          </div>`).join('')
      }</div>` : `<div class="cm-empty small">今日推荐暂不可用</div>`}
    </section>
    ${recent.length ? `
    <section class="cm-sec">
      <div class="cm-sec-head"><h2>继续播放</h2><span class="cm-sec-more" id="playRecent"><span class="material-icons-outlined">play_circle</span> 播放全部</span></div>
      <div id="recentList"></div>
      <div class="cm-more-row" id="recentMore" hidden></div>
    </section>` : ''}
    ${forYou.length ? `
    <section class="cm-sec">
      <div class="cm-sec-head"><h2>猜你喜欢</h2><span class="cm-sec-sub">基于你点赞的歌手：${esc(artists.join('、'))}</span></div>
      <div id="forYouList"></div>
      <div class="cm-more-row" id="forYouMore" hidden></div>
    </section>` : ''}
    ${recPls.length ? `
    <section class="cm-sec">
      <div class="cm-sec-head"><h2>每日推荐歌单</h2><span class="cm-sec-sub">来自你的网易云账号</span></div>
      <div class="cm-plgrid" id="recPlsGrid">${recPls.slice(0, 6).map(pl => `
        <div class="cm-plcard" data-id="${pl.id}">
          <div class="cm-plcover">${pl.picUrl ? `<img src="${esc(pl.picUrl)}?param=300y300" loading="lazy" onerror="this.remove()">` : ''}<span class="material-icons-outlined">queue_music</span></div>
          <div class="cm-plname">${esc(pl.name)}</div>
          <div class="cm-plsub">${pl.trackCount || 0} 首</div>
        </div>`).join('')}</div>
    </section>` : ''}
    <section class="cm-sec">
      <div class="cm-sec-head"><h2>排行榜</h2><span class="cm-sec-more" id="goNcmPls"><span class="material-icons-outlined">trending_up</span> 更多榜单</span></div>
      <div class="cm-quickrow">
        <a class="cm-quick" href="#/ncmpl/3778678"><span class="material-icons-outlined">local_fire_department</span><b>热歌榜</b></a>
        <a class="cm-quick" href="#/ncmpl/19723756"><span class="material-icons-outlined">trending_up</span><b>飙升榜</b></a>
        <a class="cm-quick" href="#/ncmpl/3779629"><span class="material-icons-outlined">star</span><b>新歌榜</b></a>
      </div>
    </section>
    ${forYou.length ? `
    <section class="cm-sec">
      <div class="cm-sec-head"><h2>一起听</h2><span class="cm-sec-more" id="goRooms"><span class="material-icons-outlined">groups</span> 房间广场</span></div>
      <div class="cm-quickrow">
        <a class="cm-quick" href="#/rooms"><span class="material-icons-outlined">meeting_room</span><b>加入房间</b><i>多人同步播放</i></a>
        <a class="cm-quick" id="quickCreateRoom"><span class="material-icons-outlined">add_circle</span><b>创建房间</b><i>可设密码</i></a>
      </div>
    </section>` : ''}
    ${!auth.token ? `
    <section class="cm-sec">
      <div class="cm-login-tip">
        <div>登录 CurrentMusic 账号，同步点赞、收藏与歌单</div>
        <mdui-button variant="filled" href="#/user">去登录</mdui-button>
      </div>
    </section>` : ''}
  `;

  const dailyRow = el.querySelector('#dailyRow');
  if (dailyRow) dailyRow.querySelectorAll('.cm-card').forEach(c => {
    c.onclick = () => player.playList(daily, +c.dataset.i);
  });
  el.querySelector('#playDaily')?.addEventListener('click', () => player.playList(daily, 0));
  // 一键开听：心动模式以「最近播放第一首」优先做种子（更贴近"接着刚才的口味听"），
  // 其次用每日推荐首曲；都没有时明确说明原因，不给假按钮。
  el.querySelector('#quickDaily')?.addEventListener('click', () => {
    if (!daily.length) return toast('今日推荐还没出来，稍后再试');
    player.playList(daily, 0);
  });
  el.querySelector('#quickHeart')?.addEventListener('click', () => {
    const seed = recent[0] || daily[0];
    if (!seed) return toast('需要先有播放记录或每日推荐才能起播心动模式');
    player.startHeart(seed);
  });
  // 「更多榜单」→ 发现页（那里有完整榜单与分组切换），别做点不动的假入口
  el.querySelector('#goNcmPls')?.addEventListener('click', () => { location.hash = '#/square'; });
  el.querySelectorAll('#recPlsGrid .cm-plcard').forEach(c => {
    c.onclick = () => { location.hash = `#/ncmpl/${c.dataset.id}`; };
  });
  // 「不感兴趣」：T2 写操作，需用户绑定 + confirm=1；成功后本地移除该曲
  el.querySelectorAll('#dailyRow .cm-card').forEach(c => {
    const i = +c.dataset.i;
    const s2 = daily[i];
    if (!s2) return;
    const btn = document.createElement('span');
    btn.className = 'material-icons-outlined';
    btn.textContent = 'block';
    btn.title = '不感兴趣';
    btn.style.cssText = 'position:absolute;top:4px;right:4px;font-size:calc(16px * var(--cm-fs, 1));opacity:.55';
    btn.addEventListener('click', async ev => {
      ev.stopPropagation();
      if (!ncmBound) return toast('需先绑定网易云账号');
      try {
        await api.ncm('/recommend/songs/dislike', { id: s2.ncm_id, confirm: 1 });
        toast('已标记不感兴趣');
        c.remove();
      } catch (e) { toast('操作失败：' + e.message); }
    });
    c.style.position = 'relative';
    c.appendChild(btn);
  });
  el.querySelector('#quickCreateRoom')?.addEventListener('click', () => {
    import('./rooms.js').then(m => m.createRoomDialog());   // 首页直达创建
  });
  el.querySelector('#playRecent')?.addEventListener('click', () => player.playList(recent, 0));

  // 分段折叠：首屏每个列表只放前 5 条，避免整页被长列表淹没（越长越乱）。
  // 展开即换一份切片重渲染——索引始终是原数组前缀，播放回调无需改。
  const PREVIEW = 5;
  async function bindCollapsible(boxId, moreId, list) {
    const box = el.querySelector('#' + boxId);
    const more = el.querySelector('#' + moreId);
    if (!box || !list.length) return;
    let expanded = false;
    const draw = async () => {
      await renderSongList(box, expanded ? list : list.slice(0, PREVIEW), {
        onPlay: i => player.playList(list, i),
      });
      if (!more) return;
      const rest = list.length - PREVIEW;
      more.hidden = rest <= 0;                       // 本来就放得下就不显示按钮
      more.innerHTML = expanded
        ? `<span class="material-icons-outlined cm-rot180">keyboard_arrow_down</span> 收起`
        : `查看全部（还有 ${rest} 首）<span class="material-icons-outlined">keyboard_arrow_down</span>`;
    };
    if (more) more.onclick = async () => { expanded = !expanded; await draw(); };
    await draw();
  }
  if (forYou.length) await bindCollapsible('forYouList', 'forYouMore', forYou);
  if (recent.length) await bindCollapsible('recentList', 'recentMore', recent);
}
