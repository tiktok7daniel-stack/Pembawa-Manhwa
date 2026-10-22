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
- **Any other site** – generic adapter (`server/adapters/generic.js`):
  1. fast server-side fetch: chapter links, paginated lists, Madara/WordPress AJAX lists, lazy-loaded images, script-embedded page lists;
  2. **automatic fallback to headless Chromium** (`server/browser.js`) when the page is built by JavaScript or the plain fetch is blocked: it waits out simple "checking your browser" screens, expands "load more" buttons, scrolls to trigger lazy loading, keeps only real page images (by rendered size) and reuses the session cookies for image downloads. The "Browser mode" checkbox forces this path.
  Not possible: sites behind a login, a CAPTCHA/Turnstile challenge, or DRM-style image scrambling. They fail with a clear error.
  For one-off special cases, add an adapter in `server/adapters/` exposing `match`, `getSeries`, `getPages`.

Browser mode needs Chromium: the Docker image includes it; locally set `CHROMIUM_PATH` (or `PLAYWRIGHT_BROWSERS_PATH`).
`BROWSER_CONCURRENCY` (default 2) limits parallel browser pages. Every request the browser makes goes through the same private-address check.

## Notes

- The server fetches on the user's behalf, so it blocks private/internal addresses (SSRF), re-checking every redirect.
  Residual risk: DNS rebinding between check and fetch; run it in a network without access to internal services.
- Files live in the OS temp dir and are deleted ~30 min after creation.
- MangaVault hosts no content. Download only from sources that permit it, and support creators by buying official releases.
