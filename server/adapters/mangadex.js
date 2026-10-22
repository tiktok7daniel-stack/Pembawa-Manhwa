import { fetchJson, httpError } from '../security.js';

const API = 'https://api.mangadex.org';
const ID = /\/title\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/i;

export default {
  name: 'MangaDex',

  match(url) {
    const u = new URL(url);
    return /(^|\.)mangadex\.org$/i.test(u.hostname) && ID.test(u.pathname);
  },

  async getSeries(url, { lang = 'en' } = {}) {
    const id = new URL(url).pathname.match(ID)[1];
    const info = await fetchJson(`${API}/manga/${id}?includes[]=cover_art`);
    const attrs = info.data.attributes;
    const title = attrs.title.en || attrs.title[lang] || Object.values(attrs.title)[0] || 'Manga';
    const cover = info.data.relationships.find((r) => r.type === 'cover_art')?.attributes?.fileName;

    const byNumber = new Map();
    for (let offset = 0; offset < 10000; offset += 500) {
      const qs = new URLSearchParams({ limit: '500', offset: String(offset) });
      qs.append('translatedLanguage[]', lang);
      qs.append('order[chapter]', 'asc');
      for (const r of ['safe', 'suggestive', 'erotica', 'pornographic']) qs.append('contentRating[]', r);
      const feed = await fetchJson(`${API}/manga/${id}/feed?${qs}`);
      for (const c of feed.data) {
        const a = c.attributes;
        if (!a.pages || a.externalUrl) continue; // hosted elsewhere / empty
        const key = a.chapter ?? `oneshot-${c.id}`;
        if (!byNumber.has(key)) {
          byNumber.set(key, { id: c.id, number: a.chapter ?? null, title: a.title || '', url: `https://mangadex.org/chapter/${c.id}` });
        }
      }
      if (offset + 500 >= feed.total) break;
    }
    const chapters = [...byNumber.values()];
    if (!chapters.length) throw httpError(404, `No readable chapters found in language "${lang}"`);
    return {
      title,
      description: attrs.description?.en || attrs.description?.[lang] || '',
      cover: cover ? `https://uploads.mangadex.org/covers/${id}/${cover}.256.jpg` : null,
      chapters,
    };
  },

  async getPages(chapter) {
    const data = await fetchJson(`${API}/at-home/server/${chapter.id}`);
    const { baseUrl, chapter: ch } = data;
    return ch.data.map((f) => `${baseUrl}/data/${ch.hash}/${f}`);
  },

  referer: 'https://mangadex.org/',
};
