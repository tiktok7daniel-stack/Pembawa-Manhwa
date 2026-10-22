import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import express from 'express';
import { assertPublicUrl, httpError } from './security.js';
import { pickAdapter, adapterNames } from './adapters/index.js';
import { LIMITS, cancelJob, createJob, getJob, publicJob } from './jobs.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
export const app = express();
if (process.env.TRUST_PROXY) app.set('trust proxy', 1);
app.disable('x-powered-by');
app.use(express.json({ limit: '50kb' }));

app.use((_req, res, next) => {
  res.set({
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer',
    'Content-Security-Policy':
      "default-src 'self'; img-src 'self' data: https: http:; style-src 'self' https://fonts.googleapis.com; " +
      "font-src https://fonts.gstatic.com; script-src 'self'; connect-src 'self'; frame-ancestors 'none'",
  });
  next();
});

// Minimal per-IP rate limit for the expensive endpoints.
const hits = new Map();
function rateLimit(max, windowMs = 60_000) {
  return (req, _res, next) => {
    const now = Date.now();
    const key = `${req.ip}:${req.path}`;
    const arr = (hits.get(key) || []).filter((t) => now - t < windowMs);
    if (arr.length >= max) return next(httpError(429, 'Too many requests, slow down'));
    arr.push(now);
    hits.set(key, arr);
    next();
  };
}
setInterval(() => hits.clear(), 10 * 60_000).unref();

// Parsed series are cached so a download only needs ids from the client.
const seriesCache = new Map();
const SERIES_TTL = 30 * 60_000;

app.get('/api/info', (_req, res) => res.json({ sources: adapterNames(), maxChapters: LIMITS.maxChapters }));

app.post('/api/parse', rateLimit(20), async (req, res) => {
  const { url, lang = 'en' } = req.body || {};
  if (typeof url !== 'string' || url.length > 2000) throw httpError(400, 'Paste a series link');
  const u = await assertPublicUrl(url.trim());
  const adapter = pickAdapter(u.href);
  const series = await adapter.getSeries(u.href, { lang: ['en', 'ar'].includes(lang) ? lang : 'en' });
  const token = crypto.randomUUID();
  seriesCache.set(token, { ...series, sourceUrl: u.href, at: Date.now() });
  res.json({
    token, source: adapter.name, title: series.title, description: series.description, cover: series.cover,
    chapters: series.chapters.map(({ id, number, title }) => ({ id, number, title })),
  });
});

app.post('/api/jobs', rateLimit(10), (req, res) => {
  const { token, chapterIds, mode = 'single', lang = 'en' } = req.body || {};
  const series = seriesCache.get(token);
  if (!series) throw httpError(410, 'Session expired, parse the link again');
  if (!Array.isArray(chapterIds) || chapterIds.length > 5000) throw httpError(400, 'Invalid chapter selection');
  const job = createJob(series, chapterIds.map(String), mode === 'zip' ? 'zip' : 'single', lang);
  res.status(202).json(publicJob(job));
});

app.get('/api/jobs/:id', (req, res) => {
  const job = getJob(req.params.id);
  if (!job) throw httpError(404, 'Unknown job');
  res.json(publicJob(job));
});

app.delete('/api/jobs/:id', (req, res) => {
  if (!cancelJob(req.params.id)) throw httpError(404, 'Unknown job');
  res.status(204).end();
});

app.get('/api/jobs/:id/file', (req, res) => {
  const job = getJob(req.params.id);
  if (!job || job.status !== 'done' || !fs.existsSync(job.file)) throw httpError(404, 'File not ready');
  res.download(job.file, job.fileName);
});

app.use(express.static(path.join(__dirname, '..', 'public'), { extensions: ['html'] }));

app.use('/api', (_req, _res, next) => next(httpError(404, 'Not found')));
app.use((err, _req, res, _next) => {
  const status = err.status || (err.name === 'TimeoutError' ? 504 : 500);
  if (status === 500) console.error(err);
  res.status(status).json({ error: status === 500 ? 'Unexpected error' : status === 504 ? 'The source took too long to respond' : err.message });
});

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const port = Number(process.env.PORT || 3000);
  app.listen(port, () => console.log(`MangaVault running on http://localhost:${port}`));
}
