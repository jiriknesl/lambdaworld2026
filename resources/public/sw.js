/* Retire the previous app's worker on this reused preview origin.
   Do not delete caches, cookies, local storage, or other app data. */
self.addEventListener('install', event => {
  event.waitUntil(self.skipWaiting());
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    await self.clients.claim();
    const windows = await self.clients.matchAll({type: 'window'});
    await self.registration.unregister();
    await Promise.all(windows.map(async client => {
      const url = new URL(client.url);
      if (url.origin !== self.location.origin ||
          !['/', '/index.html'].includes(url.pathname)) return;
      // A changed query bypasses the old HTML response in the HTTP cache.
      url.searchParams.set('preview-recovered', '1');
      try { await client.navigate(url.href); } catch (_) { /* Tab may close. */ }
    }));
  })());
});

self.addEventListener('fetch', event => {
  event.respondWith(fetch(event.request));
});
