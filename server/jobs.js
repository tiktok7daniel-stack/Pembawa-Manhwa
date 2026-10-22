import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { once } from 'node:events';
import { ZipArchive } from 'archiver';
import { fetchImage } from './security.js';
import { pickAdapter } from './adapters/index.js';

const TMP = path.join(os.tmpdir(), 'mangavault');
fs.mkdirSync(TMP, { recursive: true });

export const LIMITS = {
  maxChapters: Number(process.env.MAX_CHAPTERS || 500),
  maxJobs: Number(process.env.MAX_JOBS || 3),
  ttlMs: 30 * 60 * 1000,
  pageConcurrency: Number(process.env.PAGE_CONCURRENCY || 5),
};

const jobs = new Map();
const EXT = { 'image/jpeg': 'jpg', 'image/jpg': 'jpg', 'image/png': 'png', 'image/webp': 'webp', 'image/avif': 'avif', 'image/gif': 'gif' };

export const safeName = (s) => String(s).replace(/[\\/:*?"<>|\u0000-\u001f]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 100) || 'manga';
const pad = (n, w) => String(n).padStart(w, '0');

function extFor(type, url) {
  return EXT[type] || url.match(/\.(jpe?g|png|webp|avif|gif)(\?|#|$)/i)?.[1].toLowerCase().replace('jpeg', 'jpg') || 'jpg';
}

async function withRetry(fn, tries = 3) {
  let err;
  for (let i = 0; i < tries; i++) {
    try { return await fn(); } catch (e) { err = e; await new Promise((r) => setTimeout(r, 400 * (i + 1))); }
  }
  throw err;
}

/** Downloads pages with limited concurrency, returns buffers in page order (null = failed). */
async function downloadPages(urls, referer, headers, onPage, isCancelled) {
  const out = new Array(urls.length).fill(null);
  let next = 0;
  const worker = async () => {
    while (next < urls.length && !isCancelled()) {
      const i = next++;
      try {
        const { buf, type } = await withRetry(() => fetchImage(urls[i], referer, headers));
        out[i] = { buf, ext: extFor(type, urls[i]) };
      } catch { /* leave null, counted as failed */ }
      onPage(out[i] !== null);
    }
  };
  await Promise.all(Array.from({ length: Math.min(LIMITS.pageConcurrency, urls.length) }, worker));
  return out;
}

const comicInfo = (series, chapter, pageCount) =>
  `<?xml version="1.0" encoding="utf-8"?>\n<ComicInfo>\n  <Series>${esc(series)}</Series>\n` +
  (chapter ? `  <Number>${esc(chapter.number ?? '')}</Number>\n  <Title>${esc(chapter.title)}</Title>\n` : '') +
  (pageCount ? `  <PageCount>${pageCount}</PageCount>\n` : '') + `</ComicInfo>\n`;
const esc = (s) => String(s).replace(/[<>&"']/g, (c) => `&#${c.charCodeAt(0)};`);

async function finalize(archive, output) {
  const closed = once(output, 'close');
  await archive.finalize();
  await closed;
}

async function run(job, series, selected, mode, lang) {
  const adapter = pickAdapter(series.sourceUrl);
  const referer = adapter.referer || new URL(series.sourceUrl).origin + '/';
  const imgHeaders = series.session ? { 'user-agent': series.session.userAgent, cookie: series.session.cookie } : {};
  const cancelled = () => job.status === 'cancelled';
  const base = safeName(series.title);
  const width = Math.max(3, String(selected.length).length);
  const out = fs.createWriteStream(job.file);
  const archive = new ZipArchive({ zlib: { level: 0 }, store: true });
  archive.pipe(out);
  const failed = [];
  let ok = 0;

  try {
    for (let idx = 0; idx < selected.length && !cancelled(); idx++) {
      const ch = selected[idx];
      const st = job.chapters[idx];
      const label = ch.number != null ? `Ch ${pad(ch.number.split('.')[0], 3)}${ch.number.includes('.') ? '.' + ch.number.split('.')[1] : ''}` : `Ch ${pad(idx + 1, width)}`;
      st.state = 'active';
      try {
        const urls = await withRetry(() => adapter.getPages(ch, { lang, session: series.session, forceBrowser: series.forceBrowser }));
        st.total = urls.length;
        const pages = await downloadPages(urls, referer, imgHeaders, (good) => { st.done++; if (!good) st.failedPages++; }, cancelled);
        if (cancelled()) break;
        const good = pages.filter(Boolean);
        if (!good.length) throw new Error('no pages could be downloaded');
        const w = Math.max(3, String(pages.length).length);

        if (mode === 'single') {
          pages.forEach((p, i) => p && archive.append(p.buf, { name: `${label.replace(/ /g, '_')}_p${pad(i + 1, w)}.${p.ext}` }));
        } else {
          const cbz = new ZipArchive({ store: true });
          const chunks = [];
          cbz.on('data', (c) => chunks.push(c));
          const done = once(cbz, 'end');
          pages.forEach((p, i) => p && cbz.append(p.buf, { name: `${pad(i + 1, w)}.${p.ext}` }));
          cbz.append(comicInfo(series.title, ch, good.length), { name: 'ComicInfo.xml' });
          await cbz.finalize();
          await done;
          archive.append(Buffer.concat(chunks), { name: `${base}/${base} - ${label}.cbz` });
        }
        st.state = st.failedPages ? 'partial' : 'done';
        ok++;
      } catch (e) {
        st.state = 'failed';
        st.error = e.message;
        failed.push(ch.number ?? ch.title);
      }
      job.doneChapters = idx + 1;
    }
    if (mode === 'single' && !cancelled()) archive.append(comicInfo(series.title, null, 0), { name: 'ComicInfo.xml' });
    if (cancelled()) { archive.abort(); out.destroy(); fs.rm(job.file, { force: true }, () => {}); return; }
    if (!ok) throw new Error('None of the selected chapters could be downloaded');
    await finalize(archive, out);
    job.size = fs.statSync(job.file).size;
    job.fileName = mode === 'single' ? `${base}.cbz` : `${base}.zip`;
    job.status = 'done';
  } catch (e) {
    archive.abort();
    out.destroy();
    fs.rm(job.file, { force: true }, () => {});
    job.status = 'error';
    job.error = e.message;
  }
}

export function createJob(series, chapterIds, mode, lang) {
  const active = [...jobs.values()].filter((j) => j.status === 'running').length;
  if (active >= LIMITS.maxJobs) { const e = new Error('Server is busy, try again in a minute'); e.status = 503; throw e; }
  const ids = new Set(chapterIds);
  const selected = series.chapters.filter((c) => ids.has(c.id));
  if (!selected.length) { const e = new Error('Select at least one chapter'); e.status = 400; throw e; }
  if (selected.length > LIMITS.maxChapters) { const e = new Error(`At most ${LIMITS.maxChapters} chapters per download`); e.status = 400; throw e; }

  const id = crypto.randomUUID();
  const job = {
    id, status: 'running', error: null, createdAt: Date.now(), mode,
    file: path.join(TMP, `${id}.zip`), fileName: null, size: 0, doneChapters: 0,
    chapters: selected.map((c) => ({ id: c.id, number: c.number, title: c.title, state: 'queued', done: 0, total: 0, failedPages: 0 })),
  };
  jobs.set(id, job);
  run(job, series, selected, mode, lang);
  return job;
}

export const getJob = (id) => jobs.get(id);
export const cancelJob = (id) => { const j = jobs.get(id); if (j && j.status === 'running') j.status = 'cancelled'; return j; };

export const publicJob = (j) => ({
  id: j.id, status: j.status, error: j.error, fileName: j.fileName, size: j.size,
  totalChapters: j.chapters.length, doneChapters: j.doneChapters, chapters: j.chapters,
});

setInterval(() => {
  const now = Date.now();
  for (const [id, j] of jobs) {
    if (now - j.createdAt > LIMITS.ttlMs && j.status !== 'running') {
      fs.rm(j.file, { force: true }, () => {});
      jobs.delete(id);
    }
  }
}, 60 * 1000).unref();
