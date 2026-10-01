// 播放地址解析（阶段二）：三级回退，解决「地区受限 / 已下架」的曲目播放
//
//   1) 项目路由 /ncm/song/url —— 服务端 SVIP 八档并行探测，正常曲目走这条
//   2) /song/url/match        —— 解灰：从其它音源取直链（周杰伦等地区受限曲目的关键）
//   3) /song/url              —— 老接口，兜底
//   4) /check/music           —— 都失败时判断「是版权不可播还是网络问题」，给出准确文案
import { api } from './api.js';

/** 把上游三种不同的响应形状统一成 { url, type, level }。 */
function pickUrl(d) {
  if (!d) return null;
  if (typeof d === 'string') return d;
  if (typeof d.url === 'string' && d.url) return d.url;                       // /song/url
  if (typeof d.data === 'string' && d.data) return d.data;                    // /song/url/match
  if (d.data && typeof d.data.url === 'string' && d.data.url) return d.data.url; // /song/url/v1
  if (Array.isArray(d.data) && d.data[0] && d.data[0].url) return d.data[0].url;
  return null;
}

/**
 * 解析可播放地址。
 * @param {object} meta 歌曲对象（需 ncm_id）
 * @param {string} level 音质档（auto 时由服务端择优）
 * @returns {Promise<{url:string,type:string,level:string}>}
 */
export async function resolveSongUrl(meta, level) {
  if (!meta || !meta.ncm_id) throw new Error('缺少歌曲信息');
  const id = meta.ncm_id;
  let lastErr = null;

  // 1) 主路径：服务端 SVIP 档位择优
  try {
    const info = await api.songUrl(id, level);
    const url = pickUrl(info);
    // 透传上游字段：sr/bits/ch（文件头解析出的真实规格，客户端据此提示高解析度档位）、
    // br/size/md5（展示与缓存判断）—— 曾经只挑 url/type/level，导致规格信息丢失。
    if (url) return Object.assign({}, info, {
      url, type: (info && info.type) || 'mp3', level: (info && info.level) || level || 'auto',
    });
    lastErr = new Error('该音质档位无可用地址');
  } catch (e) { lastErr = e; }

  // 2) 解灰：地区受限/下架曲目从其它音源取直链
  try {
    const d = await api.ncm('/song/url/match', { id });
    const url = pickUrl(d);
    if (url) return { url, type: 'mp3', level: 'unblock' };
  } catch { /* 解灰服务不可用时继续兜底 */ }

  // 3) 老接口兜底
  try {
    const d = await api.ncm('/song/url', { id, br: 320000 });
    const url = pickUrl(d);
    if (url) return { url, type: (d && d.type) || 'mp3', level: 'legacy' };
  } catch { /* 继续 */ }

  // 4) 明确失败原因：能区分「版权不可播」与「网络/上游异常」
  try {
    const c = await api.ncm('/check/music', { id });
    if (c && c.success === false) throw new Error('该歌曲当前不可播放（版权或地区限制）');
  } catch (e) {
    if (e && /不可播放/.test(e.message || '')) throw e;
  }

  throw new Error(lastErr && lastErr.message ? lastErr.message : '无法获取播放地址');
}
