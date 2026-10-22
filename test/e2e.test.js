import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

process.env.ALLOW_PRIVATE = '1'; // the mock site lives on localhost
const { app } = await import('../server/index.js');

// 1x1 PNG padded so it passes the "looks like an image" size check
const PNG = Buffer.concat([
  Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64'),
  Buffer.alloc(80),
]);

let mock, api, mockBase, apiBase;
before(async () => {
  mock = http.createServer((req, res) => {
    const h = (body, type = 'text/html') => { res.writeHead(200, { 'content-type': type }); res.end(body); };
    if (req.url === '/series/demo/') {
      const links = [3, 1, 2, 2].map((n) => `<li><a href="/series/demo/chapter-${n}/">Chapter ${n}</a></li>`).join('');
      return h(`<html><head><meta property="og:title" content="Demo Manhwa"><meta property="og:image" content="/cover.png"></head>
        <body><h1>Demo Manhwa</h1><a href="/about">About</a><ul>${links}</ul></body></html>`);
    }
    const m = req.url.match(/^\/series\/demo\/chapter-(\d)\/$/);
    if (m) {
      const imgs = [1, 2, 3].map((p) => `<img class="lazy" data-src="/img/${m[1]}-${p}.png" src="data:image/gif;base64,R0lGOD">`).join('');
      return h(`<html><body><img src="/logo.png"><div class="reading-content">${imgs}</div></body></html>`);
    }
    if (req.url.startsWith('/img/') || req.url === '/cover.png') return h(PNG, 'image/png');
    res.writeHead(404); res.end();
  });
  await new Promise((r) => mock.listen(0, '127.0.0.1', r));
  mockBase = `http://127.0.0.1:${mock.address().port}`;
  api = http.createServer(app);
  await new Promise((r) => api.listen(0, '127.0.0.1', r));
  apiBase = `http://127.0.0.1:${api.address().port}`;
});
after(() => { mock.close(); api.close(); });

const post = (p, body) => fetch(apiBase + p, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
async function waitDone(id) {
  for (let i = 0; i < 100; i++) {
    const j = await (await fetch(`${apiBase}/api/jobs/${id}`)).json();
    if (j.status !== 'running') return j;
    await new Promise((r) => setTimeout(r, 100));
  }
  throw new Error('timeout');
}
const listZip = (file) => spawnSync('unzip', ['-Z1', file], { encoding: 'utf8' }).stdout.trim().split('\n');

test('parses a generic series page: dedupes, sorts, ignores non-chapter links', async () => {
  const r = await post('/api/parse', { url: `${mockBase}/series/demo/` });
  const d = await r.json();
  assert.equal(r.status, 200);
  assert.equal(d.title, 'Demo Manhwa');
  assert.deepEqual(d.chapters.map((c) => c.number), ['1', '2', '3']);
});

test('downloads all chapters into one CBZ', async () => {
  const d = await (await post('/api/parse', { url: `${mockBase}/series/demo/` })).json();
  const r = await post('/api/jobs', { token: d.token, chapterIds: d.chapters.map((c) => c.id), mode: 'single' });
  const job = await waitDone((await r.json()).id);
  assert.equal(job.status, 'done', job.error);
  assert.equal(job.fileName, 'Demo Manhwa.cbz');
  const file = path.join(os.tmpdir(), 'mv-test.cbz');
  fs.writeFileSync(file, Buffer.from(await (await fetch(`${apiBase}/api/jobs/${job.id}/file`)).arrayBuffer()));
  const names = listZip(file);
  assert.equal(names.filter((n) => n.endsWith('.png')).length, 9); // logo excluded, 3 chapters x 3 pages
  assert.ok(names.includes('Ch_001_p001.png') && names.includes('ComicInfo.xml'));
});

test('zip mode: one CBZ per chapter inside a folder named after the series', async () => {
  const d = await (await post('/api/parse', { url: `${mockBase}/series/demo/` })).json();
  const r = await post('/api/jobs', { token: d.token, chapterIds: [d.chapters[0].id, d.chapters[2].id], mode: 'zip' });
  const job = await waitDone((await r.json()).id);
  assert.equal(job.status, 'done', job.error);
  const file = path.join(os.tmpdir(), 'mv-test.zip');
  fs.writeFileSync(file, Buffer.from(await (await fetch(`${apiBase}/api/jobs/${job.id}/file`)).arrayBuffer()));
  assert.deepEqual(listZip(file), ['Demo Manhwa/Demo Manhwa - Ch 001.cbz', 'Demo Manhwa/Demo Manhwa - Ch 003.cbz']);
});

test('SSRF: private addresses are refused when protection is on', async () => {
  process.env.ALLOW_PRIVATE = '0';
  const r = await post('/api/parse', { url: 'http://127.0.0.1:1/' });
  process.env.ALLOW_PRIVATE = '1';
  assert.equal(r.status, 400);
});

test('rejects non-http schemes and bad input', async () => {
  assert.equal((await post('/api/parse', { url: 'file:///etc/passwd' })).status, 400);
  assert.equal((await post('/api/parse', {})).status, 400);
  assert.equal((await post('/api/jobs', { token: 'nope', chapterIds: [] })).status, 410);
});
