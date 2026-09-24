import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve, extname, sep } from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const allowed = new Set(['index.html', 'styles.css', 'icon.svg', 'manifest.webmanifest', 'sw.js']);
const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.webmanifest': 'application/manifest+json', '.mp3': 'audio/mpeg' };
const port = Number(process.env.PORT || 4173);
const host = process.env.HOST || '127.0.0.1';
createServer(async (req, res) => {
  try {
    if (!['GET', 'HEAD'].includes(req.method)) { res.writeHead(405); res.end(); return; }
    const path = decodeURIComponent(new URL(req.url, 'http://localhost').pathname).replace(/^\/+/, '') || 'index.html';
    const target = resolve(root, path);
    if (!target.startsWith(root.endsWith(sep) ? root : root + sep)
      || !(allowed.has(path) || /^(src|icons)\/[\w.-]+$/.test(path) || /^audio\/numbers\/(0[1-9]|[1-8][0-9]|90)\.mp3$/.test(path))) {
      res.writeHead(404); res.end('Not found'); return;
    }
    const data = await readFile(target);
    res.writeHead(200, { 'Content-Type': types[extname(target)] || 'application/octet-stream', 'Cache-Control': 'no-cache', 'X-Content-Type-Options': 'nosniff' });
    res.end(req.method === 'HEAD' ? undefined : data);
  } catch { res.writeHead(404); res.end('Not found'); }
}).listen(port, host, () => console.log(`Tambola is ready at http://${host}:${port}`));
