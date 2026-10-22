import dns from 'node:dns/promises';
import net from 'node:net';

const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36';

function isPrivateV4(ip) {
  const [a, b] = ip.split('.').map(Number);
  return (
    a === 0 || a === 10 || a === 127 ||
    (a === 100 && b >= 64 && b <= 127) ||
    (a === 169 && b === 254) ||
    (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && b === 168) ||
    a >= 224
  );
}

function isPrivateV6(ip) {
  const s = ip.toLowerCase();
  if (s === '::1' || s === '::') return true;
  if (s.startsWith('::ffff:')) {
    const v4 = s.slice(7);
    return net.isIPv4(v4) ? isPrivateV4(v4) : true;
  }
  return s.startsWith('fc') || s.startsWith('fd') || s.startsWith('fe8') ||
    s.startsWith('fe9') || s.startsWith('fea') || s.startsWith('feb');
}

export function isPrivateIp(ip) {
  return net.isIPv4(ip) ? isPrivateV4(ip) : isPrivateV6(ip);
}

/** Throws unless `raw` is an http(s) URL resolving only to public addresses. */
export async function assertPublicUrl(raw) {
  let u;
  try { u = new URL(raw); } catch { throw httpError(400, 'Invalid URL'); }
  if (u.protocol !== 'http:' && u.protocol !== 'https:') throw httpError(400, 'Only http(s) links are allowed');
  if (u.username || u.password) throw httpError(400, 'Credentials in URL are not allowed');
  if (process.env.ALLOW_PRIVATE === '1') return u;
  const host = u.hostname.replace(/^\[|\]$/g, '');
  const addrs = net.isIP(host) ? [{ address: host }] : await dns.lookup(host, { all: true }).catch(() => {
    throw httpError(400, 'Could not resolve host');
  });
  if (!addrs.length || addrs.some((a) => isPrivateIp(a.address))) throw httpError(400, 'This address is not allowed');
  return u;
}

export function httpError(status, message) {
  const e = new Error(message);
  e.status = status;
  return e;
}

/** fetch with SSRF checks on every redirect hop, timeout and size cap. */
export async function safeFetch(url, { headers = {}, method = 'GET', body, timeout = 20000, maxBytes = 30 * 1024 * 1024 } = {}) {
  let current = url;
  for (let hop = 0; hop < 5; hop++) {
    await assertPublicUrl(current);
    const res = await fetch(current, {
      method, body, redirect: 'manual',
      headers: { 'user-agent': UA, accept: '*/*', ...headers },
      signal: AbortSignal.timeout(timeout),
    });
    if (res.status >= 300 && res.status < 400 && res.headers.get('location')) {
      current = new URL(res.headers.get('location'), current).href;
      method = 'GET'; body = undefined;
      continue;
    }
    const len = Number(res.headers.get('content-length') || 0);
    if (len > maxBytes) throw httpError(413, 'Response too large');
    return { res, url: current };
  }
  throw httpError(502, 'Too many redirects');
}

export async function fetchText(url, opts) {
  const { res, url: finalUrl } = await safeFetch(url, { maxBytes: 8 * 1024 * 1024, ...opts });
  if (!res.ok) throw httpError(502, `Source answered ${res.status}`);
  const buf = Buffer.from(await res.arrayBuffer());
  if (buf.length > 8 * 1024 * 1024) throw httpError(413, 'Page too large');
  return { text: buf.toString('utf8'), url: finalUrl };
}

export async function fetchJson(url, opts) {
  const { text } = await fetchText(url, { headers: { accept: 'application/json' }, ...opts });
  try { return JSON.parse(text); } catch { throw httpError(502, 'Source returned invalid JSON'); }
}

export async function fetchImage(url, referer) {
  const { res } = await safeFetch(url, { headers: { referer, accept: 'image/*,*/*;q=0.8' }, timeout: 30000 });
  if (!res.ok) throw httpError(502, `Image answered ${res.status}`);
  const type = (res.headers.get('content-type') || '').split(';')[0].trim().toLowerCase();
  const buf = Buffer.from(await res.arrayBuffer());
  if (!type.startsWith('image/') && !/\.(jpe?g|png|webp|avif|gif)(\?|$)/i.test(url)) throw httpError(502, 'Not an image');
  if (type.startsWith('text/') || buf.length < 64) throw httpError(502, 'Not an image');
  return { buf, type };
}
