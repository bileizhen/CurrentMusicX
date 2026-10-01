// 歌词摘录（重写）：把「摘哪一句」「怎么提交」「怎么反馈」收敛到一处。
//
// 背景：这个功能先前散在播放器里——一个长按定时器 + 一个页面模块里的提交函数，
// 还额外在歌词区上方挂了一行提示文字和一个按钮（已移除）。现在：
//   · 播放器只负责指出目标行（当前高亮行 / 被长按的那一行）
//   · 提交、去重、登录与绑定校验、失败文案全部在本模块
//   · 长按与「更多 → 摘录当前句」走同一条路径，不会出现两套行为
//
// 上游：读 /song/lyrics/mark/user/page（T1）、写 /song/lyrics/mark/add（T2，需 confirm=1）。
// 摘录是网易云账号维度数据，必须登录 + 绑定网易云。
import { api, auth } from './api.js';
import { toast } from './ui.js';

// 已摘录内容的会话内缓存：用于"这句已经在歌词本里了"的提示。
// 键 = 歌曲 id + 歌词文本——上游的摘录是**按歌曲**存的，只比文本会把
// "别的歌里出现过同一句"误判成重复（AI 审查指出）。
// 仅取第一页，属尽力而为；拿不到就不提示重复，不影响摘录本身。
let marks = { set: null, at: 0 };
const MARKS_TTL_MS = 60000;

/** 让缓存失效：歌词本页增删后调用，避免之后给出过期的重复提示。 */
export function invalidateMarks() { marks = { set: null, at: 0 }; }

const markKey = (songId, text) => `${songId || 0}\u0001${String(text || '').trim()}`;

async function loadMarks() {
  if (marks.set && Date.now() - marks.at < MARKS_TTL_MS) return marks.set;
  let set = null;
  try {
    const d = await api.ncm('/song/lyrics/mark/user/page');
    const raw = (d && d.data && (d.data.data || d.data.list)) || (d && d.data) || [];
    if (Array.isArray(raw)) {
      set = new Set(raw
        .map(m => markKey(m && (m.songId || m.song_id), m && (m.lyric || m.content || m.text)))
        .filter(k => k.split('\u0001')[1]));
    }
  } catch (e) { /* 忽略：去重提示是加分项，失败不影响摘录 */ }
  marks = { set, at: Date.now() };
  return set;
}

/** 制作信息行（作词/作曲/编曲…）：不是歌词，不该被「摘录当前句」选中。 */
const CREDIT_RE = /^(作词|作曲|编曲|制作人|监制|出品|发行|录音|混音|母带|吉他|贝斯|鼓|键盘|和声|弦乐|统筹|策划|企划|OP|SP|词|曲|Lyricist|Composer|Arranger|Producer)\s*[:：]/i;
export const isCreditLine = t => CREDIT_RE.test(String(t || '').trim());

/**
 * 取「当前该摘录哪一句」：优先当前高亮行，否则退回播放位置之后的第一句；
 * 两者都落在制作信息行上时，向后找第一句真正的歌词（开头常是作词/作曲行）。
 */
export function pickCurrentLine(lines, curIndex, posMs) {
  if (!lines || !lines.length) return null;
  const ok = l => l && l.txt && l.txt.trim() && !isCreditLine(l.txt);
  const cur = lines[curIndex];
  if (ok(cur)) return cur;
  const after = lines.find(l => l && l.t >= (posMs || 0) && ok(l));
  return after || lines.find(ok) || cur || null;
}

/**
 * 摘录一句歌词。所有失败都有明确文案，不抛异常。
 * @returns {Promise<boolean>} 是否已写入（重复句也返回 true——目标状态已达成）
 */
export async function markLyric(meta, text) {
  const line = String(text || '').trim();
  if (!meta || !meta.ncm_id) { toast('当前没有正在播放的歌曲'); return false; }
  if (!line) { toast('这句没有可摘录的歌词'); return false; }
  if (!auth.token) { toast('登录后才能摘录歌词'); return false; }
  try {
    const key = markKey(meta.ncm_id, line);
    const known = await loadMarks();
    if (known && known.has(key)) { toast('这首的这句已经在歌词本里了'); return true; }
    await api.ncm('/song/lyrics/mark/add', {
      id: meta.ncm_id, data: JSON.stringify({ lyric: line }), confirm: 1,
    });
    if (marks.set) marks.set.add(key);
    toast('已加入歌词本（我的 → 歌词本）');
    return true;
  } catch (e) {
    const msg = String((e && e.message) || '未知错误');
    toast(/绑定/.test(msg) ? '请先绑定网易云账号（我的 → 网易云账号）' : `摘录失败：${msg}`);
    return false;
  }
}
