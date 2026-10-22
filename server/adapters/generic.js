import * as cheerio from 'cheerio';
import { fetchText, httpError } from '../security.js';

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

export default {
  name: 'Generic',
  match: () => true,
  referer: null,

  async getSeries(url) {
    const { text: html, url: finalUrl } = await fetchText(url);
    const $ = cheerio.load(html);
    const title = ($('meta[property="og:title"]').attr('content') || $('h1').first().text() || $('title').text() || 'Manga')
      .replace(/\s+/g, ' ').trim().slice(0, 150);
    const cover = abs($('meta[property="og:image"]').attr('content') || '', finalUrl) || null;
    const description = ($('meta[property="og:description"]').attr('content') || '').trim().slice(0, 600);

    let list = extractChapters($, finalUrl);

    // Madara (WordPress) themes load the chapter list through admin-ajax.
    if (/wp-manga|madara/i.test(html) && list.length < 3) {
      try {
        const ajaxUrl = new URL(finalUrl.replace(/\/?$/, '/') + 'ajax/chapters/').href;
        const r = await fetchText(ajaxUrl, { method: 'POST', headers: { 'x-requested-with': 'XMLHttpRequest' } });
        const extra = extractChapters(cheerio.load(r.text), finalUrl);
        if (extra.length > list.length) list = extra;
      } catch { /* fall back to what the page already showed */ }
    }
    if (!list.length) throw httpError(422, 'Could not find chapters on this page. Paste the series (not chapter) link, or the site may need JavaScript/anti-bot support.');
    return { title, description, cover, chapters: finishChapters(list) };
  },

  async getPages(chapter) {
    const { text: html, url } = await fetchText(chapter.url);
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
    // Reader scripts that embed page lists as JSON.
    if (urls.length < 2) {
      for (const s of $('script').toArray()) {
        const code = $(s).html() || '';
        for (const m of code.matchAll(/https?:\\?\/\\?\/[^"'\s\\]+(?:\\\/[^"'\s\\]+)*\.(?:jpe?g|png|webp|avif)/gi)) push(m[0].replace(/\\\//g, '/'));
      }
    }
    const pages = scope ? urls : urls.filter((u) => IMG_EXT.test(u));
    if (!pages.length) throw httpError(422, 'No page images found for this chapter');
    return pages;
  },
};
