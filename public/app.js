(() => {
  const $ = (id) => document.getElementById(id);
  const store = { get: (k) => { try { return localStorage.getItem(k); } catch { return null; } }, set: (k, v) => { try { localStorage.setItem(k, v); } catch { /* private mode */ } } };

  // ---------- language & theme ----------
  let lang = store.get('lang') || ((navigator.language || '').toLowerCase().startsWith('ar') ? 'ar' : 'en');
  const t = (k) => I18N[lang][k] ?? I18N.en[k] ?? k;
  function applyLang() {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
    document.querySelectorAll('[data-i18n]').forEach((el) => { el.textContent = t(el.dataset.i18n); });
    document.querySelectorAll('[data-i18n-ph]').forEach((el) => { el.placeholder = t(el.dataset.i18nPh); });
    $('langBtn').textContent = lang === 'ar' ? 'English' : 'العربية';
    $('langSel').value = lang;
    if (state.job) renderJob(state.job);
  }
  $('langBtn').onclick = () => { lang = lang === 'ar' ? 'en' : 'ar'; store.set('lang', lang); applyLang(); };

  let theme = store.get('theme') || 'dark';
  const applyTheme = () => { document.documentElement.dataset.theme = theme; };
  $('themeBtn').onclick = () => { theme = theme === 'dark' ? 'light' : 'dark'; store.set('theme', theme); applyTheme(); };

  // ---------- state ----------
  const state = { token: null, chapters: [], selected: new Set(), job: null, timer: null };

  async function api(path, opts = {}) {
    let res;
    try {
      res = await fetch(path, { ...opts, headers: { 'content-type': 'application/json' }, body: opts.body && JSON.stringify(opts.body) });
    } catch { throw new Error(t('net')); }
    if (res.status === 204) return null;
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || t('net'));
    return data;
  }
  const showError = (msg) => { $('error').textContent = msg || ''; $('error').hidden = !msg; };
  const chLabel = (c) => {
    if (c.number == null) return c.title || '—';
    if (c.title && new RegExp(`(^|[^\\d.])${c.number.replace('.', '\\.')}($|[^\\d])`).test(c.title)) return c.title; // title already says the number
    return `${t('chapter')} ${c.number}` + (c.title ? ` · ${c.title}` : '');
  };

  // ---------- parse ----------
  $('parseForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    showError('');
    const btn = $('parseBtn');
    btn.disabled = true; btn.querySelector('.spin').hidden = false; $('parseTxt').textContent = t('parsing');
    try {
      const d = await api('/api/parse', { method: 'POST', body: { url: $('urlInput').value.trim(), lang: $('langSel').value, browser: $('browserMode').checked } });
      stopPolling(); state.job = null; $('progress').hidden = true;
      state.token = d.token; state.chapters = d.chapters; state.selected = new Set(d.chapters.map((c) => c.id));
      $('cover').src = d.cover || ''; $('cover').hidden = !d.cover;
      $('srcName').textContent = d.source; $('sTitle').textContent = d.title; $('sDesc').textContent = d.description || '';
      $('chCount').textContent = d.chapters.length;
      $('filter').value = '';
      renderList(); $('result').hidden = false;
      $('result').scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (err) { showError(err.message); }
    finally { btn.disabled = false; btn.querySelector('.spin').hidden = true; $('parseTxt').textContent = t('parse'); }
  });

  // ---------- chapter list ----------
  function renderList() {
    const q = $('filter').value.trim().toLowerCase();
    const frag = document.createDocumentFragment();
    for (const c of state.chapters) {
      const label = chLabel(c);
      if (q && !label.toLowerCase().includes(q)) continue;
      const li = document.createElement('li');
      const lab = document.createElement('label');
      const cb = document.createElement('input');
      cb.type = 'checkbox'; cb.checked = state.selected.has(c.id);
      cb.onchange = () => { cb.checked ? state.selected.add(c.id) : state.selected.delete(c.id); updateCount(); };
      const sp = document.createElement('span'); sp.textContent = label; sp.title = label;
      lab.append(cb, sp); li.append(lab); frag.append(li);
    }
    $('chList').replaceChildren(frag);
    updateCount();
  }
  const updateCount = () => { $('dlCount').textContent = `(${state.selected.size})`; };
  $('filter').oninput = renderList;
  $('selAll').onclick = () => { state.chapters.forEach((c) => state.selected.add(c.id)); renderList(); };
  $('selNone').onclick = () => { state.selected.clear(); renderList(); };
  $('applyRange').onclick = () => {
    const from = parseFloat($('from').value), to = parseFloat($('to').value);
    state.selected.clear();
    state.chapters.forEach((c, i) => {
      const n = c.number != null ? parseFloat(c.number) : i + 1;
      if ((isNaN(from) || n >= from) && (isNaN(to) || n <= to)) state.selected.add(c.id);
    });
    renderList();
  };

  // ---------- download ----------
  $('dlBtn').onclick = async () => {
    showError('');
    if (!state.selected.size) return showError(t('pick_one'));
    $('dlBtn').disabled = true;
    try {
      const mode = document.querySelector('input[name=mode]:checked').value;
      const ids = state.chapters.filter((c) => state.selected.has(c.id)).map((c) => c.id);
      const job = await api('/api/jobs', { method: 'POST', body: { token: state.token, chapterIds: ids, mode, lang: $('langSel').value } });
      state.job = job; $('progress').hidden = false; $('saveBtn').hidden = true;
      renderJob(job); poll(job.id);
      $('progress').scrollIntoView({ behavior: 'smooth', block: 'center' });
    } catch (err) { showError(err.message); $('dlBtn').disabled = false; }
  };
  $('cancelBtn').onclick = async () => { if (state.job) { await api(`/api/jobs/${state.job.id}`, { method: 'DELETE' }).catch(() => {}); } };

  function stopPolling() { clearTimeout(state.timer); }
  function poll(id) {
    state.timer = setTimeout(async () => {
      try {
        const job = await api(`/api/jobs/${id}`);
        state.job = job; renderJob(job);
        if (job.status === 'running') return poll(id);
        $('dlBtn').disabled = false;
      } catch (err) { showError(err.message); $('dlBtn').disabled = false; }
    }, 700);
  }
  const fmtSize = (b) => (b > 1048576 ? (b / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(b / 1024)) + ' KB');

  function renderJob(job) {
    const active = job.chapters.find((c) => c.state === 'active');
    const partial = active && active.total ? active.done / active.total : 0;
    const pct = job.status === 'done' ? 100 : Math.min(99, Math.round(((job.doneChapters + partial) / job.totalChapters) * 100));
    $('barFill').style.width = pct + '%';
    const statusText = { running: `${t('st_running')} ${pct}% · ${job.doneChapters}/${job.totalChapters}`, done: t('st_done'), error: `${t('st_error')}: ${job.error}`, cancelled: t('st_cancelled') }[job.status];
    $('pStatus').textContent = statusText;
    $('cancelBtn').hidden = job.status !== 'running';
    const frag = document.createDocumentFragment();
    job.chapters.forEach((c) => {
      const li = document.createElement('li'); li.className = c.state;
      const name = document.createElement('span'); name.textContent = chLabel(c);
      const st = document.createElement('em'); st.textContent = t('s_' + c.state) + (c.state === 'active' && c.total ? ` ${c.done}/${c.total}` : '');
      const mini = document.createElement('div'); mini.className = 'mini';
      const fill = document.createElement('i'); fill.style.width = (c.state === 'done' || c.state === 'partial' || c.state === 'failed' ? 100 : c.total ? (c.done / c.total) * 100 : 0) + '%';
      mini.append(fill); li.append(name, st, mini); frag.append(li);
    });
    $('pList').replaceChildren(frag);
    if (job.status === 'done') {
      $('saveBtn').hidden = false; $('saveBtn').href = `/api/jobs/${job.id}/file`; $('saveBtn').setAttribute('download', job.fileName);
      $('saveSize').textContent = `${job.fileName} · ${fmtSize(job.size)}`;
    }
  }

  fetch('/api/info').then((r) => r.json()).then((i) => {
    $('srcPills').replaceChildren(...[...i.sources, 'Generic'].map((s) => Object.assign(document.createElement('span'), { textContent: s })));
  }).catch(() => {});

  applyTheme(); applyLang();
})();
