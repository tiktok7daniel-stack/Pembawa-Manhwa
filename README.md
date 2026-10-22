# MangaVault

Paste a manga/manhwa series link → pick chapters (all, a range, or hand-picked) → get **one CBZ file**
(or a folder of per-chapter CBZs named after the series). English / العربية (RTL), dark / light.

## Run

```bash
npm install
npm start            # http://localhost:3000
npm test             # end-to-end tests against a local mock manga site
```

Docker: `docker build -t mangavault . && docker run -p 3000:3000 mangavault`

| Env var | Default | Meaning |
|---|---|---|
| `PORT` | 3000 | HTTP port |
| `MAX_CHAPTERS` | 500 | Max chapters per download |
| `MAX_JOBS` | 3 | Concurrent downloads on the server |
| `PAGE_CONCURRENCY` | 5 | Parallel page fetches per chapter |
| `TRUST_PROXY` | – | Set when behind a reverse proxy so rate limits use the real client IP |

## Sources

- **MangaDex** – via its official public API (`/title/<uuid>` links), English and Arabic chapters.
- **Other sites** – generic adapter (`server/adapters/generic.js`): finds chapter links, Madara/WordPress
  chapter lists, lazy-loaded images and script-embedded page lists. It cannot handle sites that need
  JavaScript rendering, a login, or anti-bot checks (e.g. Cloudflare challenges); those fail with a clear error.
  To support such a site, add an adapter in `server/adapters/` exposing `match`, `getSeries`, `getPages`.

## Notes

- The server fetches on the user's behalf, so it blocks private/internal addresses (SSRF), re-checking every redirect.
  Residual risk: DNS rebinding between check and fetch; run it in a network without access to internal services.
- Files live in the OS temp dir and are deleted ~30 min after creation.
- MangaVault hosts no content. Download only from sources that permit it, and support creators by buying official releases.
