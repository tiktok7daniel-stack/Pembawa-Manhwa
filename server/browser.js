import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright-core';
import { assertPublicUrl, httpError } from './security.js';

const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36';
const MAX_PAGES = Number(process.env.BROWSER_CONCURRENCY || 2);
const CHALLENGE = /just a moment|attention required|checking your browser|verify you are human|ddos-guard/i;
const EXPAND = /show all|load more|view all|more chapters|see all|عرض الكل|المزيد|تحميل المزيد|كل الفصول/i;

function findChromium() {
  if (process.env.CHROMIUM_PATH) return process.env.CHROMIUM_PATH;
  const root = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const dir = fs.readdirSync(root).filter((d) => /^chromium-\d+$/.test(d)).sort().pop();
    const exe = dir && path.join(root, dir, 'chrome-linux', 'chrome');
    if (exe && fs.existsSync(exe)) return exe;
  } catch { /* fall through to Playwright's own lookup */ }
  return undefined;
}

let browserPromise = null;
async function getBrowser() {
  if (!browserPromise) {
    browserPromise = chromium
      .launch({ executablePath: findChromium(), args: ['--no-sandbox', '--disable-dev-shm-usage'] })
      .catch((e) => { browserPromise = null; console.error('Browser launch failed:', e.message); throw httpError(501, 'Browser mode is not available on this server'); });
  }
  const b = await browserPromise;
  if (!b.isConnected()) { browserPromise = null; return getBrowser(); }
  return b;
}

let active = 0;
const waiters = [];
const acquire = () => (active < MAX_PAGES ? (active++, Promise.resolve()) : new Promise((r) => waiters.push(r)));
const release = () => { const next = waiters.shift(); if (next) next(); else active--; };

/**
 * Loads a page in headless Chromium so JavaScript-built content exists, scrolls it so lazy images load,
 * and returns the HTML, the loaded <img> info (with natural sizes) and the session cookies.
 * Every request the page makes goes through the same public-address check as server-side fetches.
 */
export async function render(url, { expand = false, scroll = true } = {}) {
  await acquire();
  let ctx;
  try {
    const browser = await getBrowser();
    ctx = await browser.newContext({ userAgent: UA, viewport: { width: 1280, height: 900 } });
    const okHosts = new Map();
    await ctx.route('**/*', async (route) => {
      const req = route.request();
      const u = req.url();
      if (u.startsWith('data:') || u.startsWith('blob:')) return route.continue();
      if (['font', 'media'].includes(req.resourceType())) return route.abort();
      try {
        const host = new URL(u).host;
        if (!okHosts.has(host) || okHosts.get(host) < Date.now()) {
          await assertPublicUrl(u);
          okHosts.set(host, Date.now() + 60_000);
        }
        return await route.continue();
      } catch { return route.abort().catch(() => {}); }
    });
    const page = await ctx.newPage();
    page.setDefaultTimeout(20_000);
    await assertPublicUrl(url);
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30_000 });
    for (let i = 0; i < 15 && CHALLENGE.test(await page.title().catch(() => '')); i++) await page.waitForTimeout(1000);
    await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {});

    if (expand) {
      for (let i = 0; i < 6; i++) {
        const clicked = await page.evaluate((src) => {
          const re = new RegExp(src, 'i');
          const el = [...document.querySelectorAll('button, a, div[role=button], span')].find(
            (e) => re.test((e.textContent || '').trim()) && (e.textContent || '').length < 40 && e.offsetParent !== null);
          if (el) { el.click(); return true; }
          return false;
        }, EXPAND.source);
        if (!clicked) break;
        await page.waitForTimeout(900);
      }
    }
    if (scroll) {
      let last = -1;
      for (let i = 0; i < 80; i++) {
        const h = await page.evaluate(() => { window.scrollBy(0, Math.max(600, innerHeight * 0.9)); return document.documentElement.scrollHeight; });
        await page.waitForTimeout(250);
        const atBottom = await page.evaluate(() => innerHeight + scrollY >= document.documentElement.scrollHeight - 4);
        if (atBottom && h === last) break;
        last = h;
      }
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
    }

    const images = await page.evaluate(() => [...document.images].map((i) => ({
      src: i.currentSrc || i.src || i.dataset.src || i.dataset.lazySrc || '',
      w: i.naturalWidth, h: i.naturalHeight,
      skip: !!i.closest('header, footer, nav, aside, .sidebar, .related, .recommend, .comments, #comments, .ads, [class*=advert]'),
    })));
    return { html: await page.content(), url: page.url(), images, cookies: await ctx.cookies(), userAgent: UA };
  } catch (e) {
    if (e.status) throw e;
    throw httpError(504, `Browser mode failed: ${e.message.split('\n')[0]}`);
  } finally {
    await ctx?.close().catch(() => {});
    release();
  }
}

export const cookieHeader = (cookies, url) => {
  const host = new URL(url).hostname;
  return cookies.filter((c) => host === c.domain.replace(/^\./, '') || host.endsWith(c.domain.replace(/^\./, ''))).map((c) => `${c.name}=${c.value}`).join('; ');
};

export async function closeBrowser() {
  if (browserPromise) { const b = await browserPromise.catch(() => null); await b?.close().catch(() => {}); browserPromise = null; }
}
