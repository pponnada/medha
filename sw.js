const CACHE_NAME = 'medha-v12';

function isDesmosScript(url) {
  return url.includes('desmos.com/api') && url.includes('calculator.js');
}

const APP_SHELL = [
  'index.html',
  'style.css',
  'manifest.json',
  'config.js',
  'src/state.cljs',
  'src/db.cljs',
  'src/router.cljs',
  'src/curriculum.cljs',
  'src/student/timer.cljs',
  'src/student/hints.cljs',
  'src/student/responses.cljs',
  'src/student/desmos.cljs',
  'src/student/lesson.cljs',
  'src/student/export.cljs',
  'src/app.cljs',
];

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      // Cache app shell — individual failures don't abort install
      const shellPromises = APP_SHELL.map((url) =>
        cache.add(url).catch((err) => console.warn('Shell cache miss:', url, err))
      );
      // Desmos is cached on first use via the fetch handler — no install-time fetch needed
      return Promise.all(shellPromises);
    })
  );
  self.skipWaiting();
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (e) => {
  const url = e.request.url;

  // Cache-first: app shell + Desmos bundle (matched by URL pattern, key-agnostic)
  if (APP_SHELL.some((p) => url.endsWith(p)) || isDesmosScript(url)) {
    e.respondWith(
      caches.match(e.request).then((cached) => {
        if (cached) return cached;
        return fetch(e.request).then((res) => {
          const clone = res.clone();
          caches.open(CACHE_NAME).then((c) => c.put(e.request, clone));
          return res;
        });
      })
    );
    return;
  }

  // Network-first with cache fallback: curriculum EDN files
  if (url.endsWith('.edn')) {
    e.respondWith(
      fetch(e.request)
        .then((res) => {
          const clone = res.clone();
          caches.open(CACHE_NAME).then((c) => c.put(e.request, clone));
          return res;
        })
        .catch(() => caches.match(e.request))
    );
    return;
  }

  // Default: network with cache fallback
  e.respondWith(
    fetch(e.request).catch(() =>
      caches.match(e.request).then((cached) =>
        cached || new Response('Not found', { status: 404 })
      )
    )
  );
});
