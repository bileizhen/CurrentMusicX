// 零依赖预览服务器；先 npm run build，再 npm run preview。
import http from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { resolve, extname, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(fileURLToPath(new URL('../app/src/main/assets/www/', import.meta.url)));
const HOST = process.env.HOST || '127.0.0.1';
const PORT = Number(process.env.PORT || 4173);
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.woff2': 'font/woff2', '.png': 'image/png', '.svg': 'image/svg+xml', '.ico': 'image/x-icon' };

http.createServer(async (req, res) => {
  try {
    const pathname = new URL(req.url, `http://${req.headers.host || 'localhost'}`).pathname;
    const path = resolve(ROOT, '.' + decodeURIComponent(pathname === '/' ? '/index.html' : pathname));
    if (path !== ROOT && !path.startsWith(ROOT + sep)) { res.writeHead(403).end('Forbidden'); return; }
    if (!(await stat(path)).isFile()) { res.writeHead(404).end('Not found'); return; }
    res.writeHead(200, { 'Content-Type': TYPES[extname(path)] || 'application/octet-stream', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
    res.end(await readFile(path));
  } catch (e) { res.writeHead(e.code === 'ENOENT' ? 404 : 400).end('Resource unavailable'); }
}).listen(PORT, HOST, () => console.log(`CurrentMusic 网页预览：http://${HOST}:${PORT}/`));
