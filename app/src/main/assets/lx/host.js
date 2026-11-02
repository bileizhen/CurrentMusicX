// CurrentMusic's LX Music v2 host. Third-party scripts run only in a JavaScriptSandbox isolate.
// Protocol: https://lxmusic.toside.cn/mobile/custom-source
(() => {
  'use strict';
  const C = globalThis.CryptoJS;
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  const from = (value, encoding = 'utf8') => {
    if (value instanceof ArrayBuffer) return new Uint8Array(value.slice(0));
    if (ArrayBuffer.isView(value) || Array.isArray(value)) return Uint8Array.from(value);
    if (typeof value !== 'string') throw Error('buffer.from requires a string or byte array');
    if (value.length > (encoding === 'base64' ? 2796204 : 2097152)) throw Error('Buffer too large');
    if (encoding === 'hex') {
      if (value.length % 2 || /[^a-f0-9]/i.test(value)) throw Error('Invalid hex buffer');
      return Uint8Array.from(value.match(/../g) || [], n => parseInt(n, 16));
    }
    if (encoding === 'base64') {
      const input = value.replace(/\s/g, '').replace(/-/g, '+').replace(/_/g, '/');
      const out = []; let bits = 0, count = 0;
      for (const ch of input.replace(/=+$/, '')) {
        const v = alphabet.indexOf(ch); if (v < 0) throw Error('Invalid base64');
        bits = (bits << 6) | v; count += 6;
        if (count >= 8) { count -= 8; out.push((bits >>> count) & 255); }
      }
      return Uint8Array.from(out);
    }
    if (encoding === 'binary' || encoding === 'latin1') return Uint8Array.from(value, c => c.charCodeAt(0) & 255);
    if (!['utf8', 'utf-8'].includes(encoding)) throw Error('Unsupported buffer encoding');
    const bytes = unescape(encodeURIComponent(value));
    return Uint8Array.from(bytes, c => c.charCodeAt(0));
  };
  const bufToString = (value, encoding = 'utf8') => {
    const bytes = from(value);
    if (encoding === 'hex') return Array.from(bytes, n => n.toString(16).padStart(2, '0')).join('');
    if (encoding === 'base64') {
      let out = '';
      for (let i = 0; i < bytes.length; i += 3) {
        const n = bytes[i] * 65536 + (bytes[i + 1] || 0) * 256 + (bytes[i + 2] || 0);
        out += alphabet[(n >>> 18) & 63] + alphabet[(n >>> 12) & 63] +
          (i + 1 < bytes.length ? alphabet[(n >>> 6) & 63] : '=') + (i + 2 < bytes.length ? alphabet[n & 63] : '=');
      }
      return out;
    }
    const chunks = [];
    for (let i = 0; i < bytes.length; i += 8192) chunks.push(String.fromCharCode(...bytes.subarray(i, i + 8192)));
    const binary = chunks.join('');
    if (encoding === 'binary' || encoding === 'latin1') return binary;
    if (!['utf8', 'utf-8'].includes(encoding)) throw Error('Unsupported buffer encoding');
    try { return decodeURIComponent(escape(binary)); } catch (_) { return binary; }
  };
  const word = bytes => C.lib.WordArray.create(from(bytes));
  const bytes = words => from(words.toString(C.enc.Hex), 'hex');
  let randomPool = new Uint8Array(0), randomAt = 0;
  const randomBytes = length => {
    if (!Number.isInteger(length) || length < 0 || length > 4096 || randomAt + length > randomPool.length)
      throw Error('Random byte limit exceeded');
    const value = randomPool.slice(randomAt, randomAt + length); randomAt += length; return value;
  };
  const aesEncrypt = (input, mode, key, iv) => {
    const match = /^aes-(128|192|256)-(cbc|ecb)$/i.exec(mode);
    if (!match || from(key).length !== Number(match[1]) / 8) throw Error('Unsupported AES mode or key');
    return bytes(C.AES.encrypt(word(input), word(key), {
      iv: word(iv || []), mode: match[2].toLowerCase() === 'ecb' ? C.mode.ECB : C.mode.CBC, padding: C.pad.Pkcs7,
    }).ciphertext);
  };
  // The LX API uses raw RSA public-key encryption (no padding), not a decryption or key API.
  const rsaEncrypt = (input, pem) => {
    const der = from(String(pem).replace(/-----[^-]+-----/g, '').replace(/\s/g, ''), 'base64');
    const read = (offset, end) => {
      const nodes = [];
      while (offset < end) {
        const tag = der[offset++]; let length = der[offset++];
        if (length & 128) { const n = length & 127; length = 0; if (n > 4) throw Error('Invalid public key');
          for (let i = 0; i < n; i++) length = length * 256 + der[offset++]; }
        if (length > 16384 || offset + length > end) throw Error('Invalid public key');
        if (tag === 48) nodes.push(...read(offset, offset + length));
        else if (tag === 3) nodes.push(...read(offset + 1, offset + length));
        else if (tag === 2) nodes.push(der.slice(offset, offset + length));
        offset += length;
      }
      return nodes;
    };
    const values = read(0, der.length);
    if (values.length !== 2) throw Error('Invalid RSA public key');
    const big = b => BigInt('0x' + (bufToString(b, 'hex') || '0'));
    const modulus = big(values[0]); let exponent = big(values[1]); let base = big(from(input));
    if (base >= modulus || from(input).length > 512 || exponent > 4294967295n) throw Error('Invalid RSA input');
    let result = 1n;
    for (; exponent > 0; exponent >>= 1n, base = base * base % modulus) if (exponent & 1n) result = result * base % modulus;
    const width = Math.ceil(modulus.toString(16).length / 2);
    return from(result.toString(16).padStart(width * 2, '0'), 'hex');
  };
  const callbacks = new Map(), tasks = new Map();
  let queued = [], cancelled = [], sequence = 0, clock = 0, handler = null, initialized = null, answer = null, failure = null;
  const enqueue = (op, data, callback) => {
    if (callbacks.size >= 32 || queued.length >= 32) throw Error('Too many pending requests');
    const id = ++sequence; callbacks.set(id, callback); queued.push({ id, op, ...data });
    return () => { callbacks.delete(id); cancelled.push(id); };
  };
  const request = (url, options, callback) => {
    if (typeof options === 'function') { callback = options; options = {}; }
    if (typeof callback !== 'function') throw Error('A request callback is required');
    return enqueue('http', { url: String(url), options: options || {} }, callback);
  };
  const compressed = (op, input) => new Promise((resolve, reject) => {
    enqueue(op, { data: bufToString(from(input), 'base64') }, (error, data) => error ? reject(error) : resolve(from(data, 'base64')));
  });
  const noop = () => {};
  globalThis.console = Object.freeze({ log: noop, info: noop, warn: noop, error: noop, debug: noop, group: noop, groupEnd: noop, time: noop, timeEnd: noop });
  globalThis.setTimeout = (fn, timeout = 0, ...args) => {
    if (typeof fn !== 'function' || tasks.size >= 64) throw Error('Invalid timer');
    const id = ++sequence; tasks.set(id, { at: clock + Math.max(0, Number(timeout) || 0), fn: () => fn(...args) }); return id;
  };
  globalThis.clearTimeout = id => tasks.delete(id);
  const EVENT_NAMES = Object.freeze({ request: 'request', inited: 'inited', updateAlert: 'updateAlert' });
  const utils = Object.freeze({ buffer: Object.freeze({ from, bufToString }),
    crypto: Object.freeze({ md5: input => C.MD5(typeof input === 'string' ? input : word(input)).toString(), aesEncrypt, rsaEncrypt, randomBytes }),
    zlib: Object.freeze({ inflate: data => compressed('inflate', data), deflate: data => compressed('deflate', data) }) });
  globalThis.__cm_lx = Object.freeze({
    setup(info, random, now) {
      clock = now;
      randomPool = from(random, 'base64');
      globalThis.lx = Object.freeze({ EVENT_NAMES, version: '2.0.0', env: 'mobile', utils, request,
        currentScriptInfo: Object.freeze(info),
        on(event, callback) { if (event !== 'request' || typeof callback !== 'function') return Promise.reject(Error('Unsupported LX event'));
          handler = callback; return Promise.resolve(); },
        send(event, data) {
          if (event === 'inited') {
            if (initialized) return Promise.reject(Error('Script already initialized'));
            initialized = data; return Promise.resolve();
          }
          if (event === 'updateAlert') return Promise.resolve();
          return Promise.reject(Error('Unsupported LX event'));
        },
      });
    },
    fail(error) { failure = String(error && error.message || error).slice(0, 256); },
    clearPending() { callbacks.clear(); queued = []; cancelled = []; },
    start(data) {
      answer = null; failure = null;
      if (!handler) { failure = 'Source request handler is missing'; return; }
      Promise.resolve().then(() => handler(data)).then(value => { answer = { ok: true, value }; },
        error => { answer = { ok: false, message: String(error && error.message || error).slice(0, 256) }; });
    },
    reply(replies) {
      for (const item of replies) {
        const callback = callbacks.get(item.id); callbacks.delete(item.id); if (!callback) continue;
        try { if (item.error) callback(Error(item.error), null, null);
          else if (item.op === 'http') { item.value.raw = from(item.value.raw, 'base64'); callback(null, item.value, item.value.body); }
          else callback(null, item.value); }
        catch (error) { failure = String(error && error.message || error).slice(0, 256); }
      }
    },
    take(now) {
      clock = now;
      for (const [id, task] of Array.from(tasks)) if (task.at <= clock && tasks.has(id)) { tasks.delete(id); try { task.fn(); } catch (error) { failure = String(error.message || error); } }
      const result = JSON.stringify({ requests: queued, cancelled, initialized, answer, failure });
      queued = []; cancelled = []; return result;
    },
  });
})();
