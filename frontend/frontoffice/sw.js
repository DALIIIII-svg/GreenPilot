const CACHE = 'greenpilot-frontoffice-v3';
const SHELL = [
  './',
  'index.html',
  'callback.html',
  'manifest.webmanifest',
  'css/style.css',
  'icons/icon.svg',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'js/app.js',
  'js/api.js',
  'js/auth.js',
  'js/config.js',
  'js/live.js',
  'js/router.js',
  'js/state.js',
  'js/ui.js',
  'js/pages/dashboard.js',
  'js/pages/incidents.js',
  'js/pages/incident.js',
  'js/pages/history.js',
  'js/pages/nearby.js',
  'vendor/leaflet/leaflet.js',
  'vendor/leaflet/leaflet.css',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)));
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key)))),
  );
  self.clients.claim();
});

// App files: network first, so a new version is used as soon as it is online,
// and the cached copy when offline. API, login and map tiles are never cached.
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== location.origin) return;
  if (url.pathname.startsWith('/greenpilot/') || url.pathname.startsWith('/iam/')) return;

  event.respondWith(
    fetch(event.request)
      .then((response) => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(CACHE).then((cache) => cache.put(event.request, copy));
        }
        return response;
      })
      .catch(() =>
        caches.match(event.request, { ignoreSearch: true }).then((hit) => hit || caches.match('index.html')),
      ),
  );
});
