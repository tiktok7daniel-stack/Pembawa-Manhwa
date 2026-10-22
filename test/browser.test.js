import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import zlib from 'node:zlib';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

process.env.ALLOW_PRIVATE = '1';
const { app } = await import('../server/index.js');
const { closeBrowser } = await import('../server/browser.js');

function png(w, h) {
  const crcT = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = (b) => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
  const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 0;
  const raw = Buffer.concat(Array.from({ length: h }, () => Buffer.concat([Buffer.from([0]), Buffer.alloc(w, 128)])));
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
const PAGE = png(400, 600), LOGO = png(64, 64);

let mock, api, mockBase, apiBase;
before(async () => {
  mock = http.createServer((req, res) => {
    const h = (b, t = 'text/html') => { res.writeHead(200, { 'content-type': t }); res.end(b); };
    // Everything is built by JavaScript: no chapter links or <img> exist in the raw HTML.
    if (req.url === '/js/series') {
      return h(`<html><head><title>JS Series</title></head><body><h1>JS Series</h1><div id="list"></div>
        <script>setTimeout(()=>{ document.getElementById('list').innerHTML =
          [1,2,3].map(n => '<a href="/js/read/'+n+'">Chapter '+n+'</a>').join(''); }, 300)</script></body></html>`);
    }
    if (/^\/js\/read\/\d$/.test(req.url)) {
      return h(`<html><body><img src="/img/logo.png"><div id="r"></div>
        <script>
          const n = location.pathname.split('/').pop();
          setTimeout(()=>{ for (let p=1;p<=3;p++){ const i=new Image(); i.src='/img/'+n+'-'+p+'.png'; document.getElementById('r').appendChild(i);} }, 300);
        </script></body></html>`);
    }
    if (req.url === '/img/logo.png') return h(LOGO, 'image/png');
    if (req.url.startsWith('/img/')) return h(PAGE, 'image/png');
    res.writeHead(404); res.end();
  });
  await new Promise((r) => mock.listen(0, '127.0.0.1', r));
  mockBase = `http://127.0.0.1:${mock.address().port}`;
  api = http.createServer(app);
  await new Promise((r) => api.listen(0, '127.0.0.1', r));
  apiBase = `http://127.0.0.1:${api.address().port}`;
});
after(async () => { await closeBrowser(); mock.close(); api.close(); });

const post = (p, body) => fetch(apiBase + p, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });

test('JS-rendered site: chapters and pages are found through the headless browser', { timeout: 120_000 }, async () => {
  const pr = await post('/api/parse', { url: `${mockBase}/js/series` });
  const d = await pr.json();
  assert.equal(pr.status, 200, JSON.stringify(d));
  assert.deepEqual(d.chapters.map((c) => c.number), ['1', '2', '3']);

  const jr = await post('/api/jobs', { token: d.token, chapterIds: d.chapters.map((c) => c.id), mode: 'single' });
  const id = (await jr.json()).id;
  let job;
  for (let i = 0; i < 400; i++) {
    job = await (await fetch(`${apiBase}/api/jobs/${id}`)).json();
    if (job.status !== 'running') break;
    await new Promise((r) => setTimeout(r, 250));
  }
  assert.equal(job.status, 'done', job.error);
  const file = path.join(os.tmpdir(), 'mv-js.cbz');
  fs.writeFileSync(file, Buffer.from(await (await fetch(`${apiBase}/api/jobs/${id}/file`)).arrayBuffer()));
  const names = spawnSync('unzip', ['-Z1', file], { encoding: 'utf8' }).stdout.trim().split('\n');
  assert.equal(names.filter((n) => n.endsWith('.png')).length, 9); // logo (64x64) filtered out by size
});
