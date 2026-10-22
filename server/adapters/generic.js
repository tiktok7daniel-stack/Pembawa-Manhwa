import * as cheerio from 'cheerio';
import { fetchText, httpError } from '../security.js';
import { render, cookieHeader } from '../browser.js';

const CH_HINT = /chapter|chap\b|\bch[-_. ]?\d|episode|\bep[-_. ]?\d|capitulo|الفصل|فصل|الحلقة|حلقة/i;
const NUM = /(?:chapter|chap|ch|episode|ep|capitulo|الفصل|فصل|الحلقة|حلقة)[\s\-_.:#/]*?(\d+(?:[.\-]\d+)?)/i;
const IMG_EXT = /\.(jpe?g|png|webp|avif|gif)(\?|#|$)/i;
const BAD_IMG = /logo|avatar|banner|icon|sprite|emoji|favicon|loading|placeholder|spinner|\/ads?\/|gravatar|button|badge|social/i;
const READER = [
  '.reading-content', '#readerarea', '.chapter-content', '.chapter-container', '#chapter-reader',
  '.page-break', '.reader-area', '#reader', '.reader', '.entry-content', '#chapter_body', '.viewer', '#viewer', 'article',
];
const LAZY = ['data-src', 'data-lazy-src', 'data-original', 'data-cfsrc', 'data-url', 'src'];

const abs = (href, base) => { try { return new URL(href, base).href; } catch { return null; } };
const sameSite = (a, b) => new URL(a).hostname.replace(/^www\./, '') === new URL(b).hostname.replace(/^www\./, '');

function chapterNumber(text, href) {
  const m = text.match(NUM) || decodeURIComponent(href).match(NUM);
  return m ? m[1].replace('-', '.') : null;
}

function extractChapters($, base) {
  const seen = new Map();
  $('a[href]').each((_, el) => {
    const href = abs($(el).attr('href'), base);
    if (!href || !/^https?:/.test(href) || !sameSite(href, base)) return;
    if (href.split('#')[0] === base.split('#')[0]) return;
    const text = $(el).text().replace(/\s+/g, ' ').trim();
    if (!CH_HINT.test(text) && !CH_HINT.test(decodeURIComponent(new URL(href).pathname))) return;
    const clean = href.split('#')[0];
    if (!seen.has(clean)) seen.set(clean, { href: clean, text, number: chapterNumber(text, clean) });
  });
  return [...seen.values()];
}

function finishChapters(list) {
  const withNum = list.filter((c) => c.number !== null).length;
  let out = list;
  if (withNum >= Math.max(1, list.length * 0.6)) {
    const byNum = new Map();
    for (const c of list.filter((c) => c.number !== null)) if (!byNum.has(c.number)) byNum.set(c.number, c);
    out = [...byNum.values()].sort((a, b) => parseFloat(a.number) - parseFloat(b.number));
  } else if (list.length > 1) {
    // No usable numbers: sites list newest first, so reverse to reading order.
    out = [...list].reverse();
  }
  return out.map((c, i) => ({ id: String(i), number: c.number, title: c.text.slice(0, 120), url: c.href }));
}

const NEXT = 'a[rel=next], .pagination a.next, .page-numbers.next, a.next.page-numbers, li.next a';

function readSeries(html, finalUrl) {
  const $ = cheerio.load(html);
  const title = ($('meta[property="og:title"]').attr('content') || $('h1').first().text() || $('title').text() || 'Manga')
    .replace(/\s+/g, ' ').trim().slice(0, 150);
  const cover = abs($('meta[property="og:image"]').attr('content') || '', finalUrl) || null;
  const description = ($('meta[property="og:description"]').attr('content') || '').trim().slice(0, 600);
  return { $, title, cover, description, list: extractChapters($, finalUrl) };
}

function pageImages(html, url) {
  const $ = cheerio.load(html);
  let scope = null;
  for (const sel of READER) {
    const el = $(sel);
    if (el.find('img').length >= 2) { scope = el; break; }
  }
  const imgs = (scope || $('body')).find('img, noscript').toArray();
  const urls = [];
  const push = (u) => { const a = u && abs(u.trim(), url); if (a && /^https?:/.test(a) && !BAD_IMG.test(a) && !urls.includes(a)) urls.push(a); };

  for (const el of imgs) {
    if (el.tagName === 'noscript') {
      cheerio.load($(el).text())('img').each((_, i) => push(i.attribs.src));
      continue;
    }
    for (const attr of LAZY) {
      const v = el.attribs?.[attr];
      if (v && !v.startsWith('data:')) { push(v); break; }
    }
  }
  // Reader scripts that embed page lists as JSON (ts_reader, Next.js data, etc).
  if (urls.length < 2) {
    for (const s of $('script').toArray()) {
      const code = $(s).html() || '';
      for (const m of code.matchAll(/https?:\\?\/\\?\/[^"'\s\\]+(?:\\\/[^"'\s\\]+)*\.(?:jpe?g|png|webp|avif)/gi)) push(m[0].replace(/\\\//g, '/'));
    }
  }
  return scope ? urls : urls.filter((u) => IMG_EXT.test(u));
}

const authHeaders = (session) => (session ? { cookie: session.cookie, 'user-agent': session.userAgent } : {});
// Statuses that usually mean "bot protection", where a real browser may still get through.
const blocked = (e) => [401, 403, 429, 503].includes(e.upstream) || e.upstream >= 500;

export default {
  name: 'Generic',
  match: () => true,
  referer: null,

  async getSeries(url, { browser = false } = {}) {
    let found = null;
    let session = null;
    let rendered = false;

    if (!browser) {
      try {
        const { text: html, url: finalUrl } = await fetchText(url);
        found = readSeries(html, finalUrl);
        let { list } = found;

        // Madara (WordPress) themes load the chapter list through admin-ajax.
        if (/wp-manga|madara/i.test(html) && list.length < 3) {
          try {
            const ajaxUrl = new URL(finalUrl.replace(/\/?$/, '/') + 'ajax/chapters/').href;
            const r = await fetchText(ajaxUrl, { method: 'POST', headers: { 'x-requested-with': 'XMLHttpRequest' } });
            const extra = extractChapters(cheerio.load(r.text), finalUrl);
            if (extra.length > list.length) list = extra;
          } catch { /* keep what the page showed */ }
        }
        // Paginated chapter lists: follow "next" links on the same site.
        let next = found.$(NEXT).first().attr('href');
        const visited = new Set([finalUrl]);
        for (let i = 0; i < 30 && next; i++) {
          const nextUrl = abs(next, finalUrl);
          if (!nextUrl || visited.has(nextUrl) || !sameSite(nextUrl, finalUrl)) break;
          visited.add(nextUrl);
          const r = await fetchText(nextUrl).catch(() => null);
          if (!r) break;
          const more = readSeries(r.text, r.url);
          list = list.concat(more.list);
          next = more.$(NEXT).first().attr('href');
        }
        found.list = list;
      } catch (e) {
        if (!blocked(e)) throw e;
      }
    }

    // Too little found (or blocked): load it in a real browser so JavaScript-built pages work.
    if (!found || found.list.length < 2) {
      const r = await render(url, { expand: true });
      const second = readSeries(r.html, r.url);
      if (!found || second.list.length > found.list.length) {
        found = { ...second, title: second.title || found?.title, cover: second.cover || found?.cover };
        session = { cookie: cookieHeader(r.cookies, r.url), userAgent: r.userAgent };
        rendered = true;
      }
    }
    if (!found.list.length) {
      throw httpError(422, 'No chapters found on this page. Make sure it is the series page (not a chapter), or the site may block automated access (login / CAPTCHA).');
    }
    return {
      title: found.title, description: found.description, cover: found.cover, chapters: finishChapters(found.list),
      session, forceBrowser: browser || rendered,
    };
  },

  async getPages(chapter, { session, forceBrowser } = {}) {
    if (!forceBrowser) {
      try {
        const { text: html, url } = await fetchText(chapter.url, { headers: authHeaders(session) });
        const urls = pageImages(html, url);
        if (urls.length >= 2) return urls;
      } catch (e) {
        if (!blocked(e) && e.status !== 422) throw e;
      }
    }
    const r = await render(chapter.url, { scroll: true });
    const seen = new Set();
    const urls = [];
    for (const i of r.images) {
      if (!i.src || i.skip || i.w < 300 || i.h < 200 || !/^https?:/.test(i.src) || seen.has(i.src)) continue;
      seen.add(i.src);
      urls.push(i.src);
    }
    if (!urls.length) urls.push(...pageImages(r.html, r.url));
    if (!urls.length) throw httpError(422, 'No page images found for this chapter');
    return urls;
  },
};
