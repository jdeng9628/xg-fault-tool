// Service Worker for PWA - handles file sharing
const CACHE = 'fault-tool-v1';
const PREFIX = self.location.pathname.replace(/[^\/]*$/, '') || '';

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE).then(cache => cache.addAll([
      PREFIX + '/fault-tool.html',
      PREFIX + '/fault_codes.json',
      PREFIX + '/dbc_signals.json',
      PREFIX + '/can_mapping.json',
      PREFIX + '/manifest.json'
    ]))
  );
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);

  // Handle share target POST → redirect with file data
  if (url.pathname.endsWith('/share-handler') && event.request.method === 'POST') {
    event.respondWith(handleSharedFile(event.request));
    return;
  }

  // Cache strategy: network first, fallback to cache
  event.respondWith(
    fetch(event.request).catch(() => caches.match(event.request))
  );
});

async function handleSharedFile(request) {
  try {
    const formData = await request.formData();
    const file = formData.get('ascFile');
    if (!file) {
      return Response.redirect(PREFIX + '/fault-tool.html?shared=no-file', 303);
    }

    // Store file in cache for the main page to read
    const clients = await self.clients.matchAll({type: 'window'});
    const target = clients.find(c => c.url.includes('fault-tool.html'));
    if (target) {
      // Send file data to existing client
      const buffer = await file.arrayBuffer();
      target.postMessage({
        type: 'shared-file',
        name: file.name,
        data: Array.from(new Uint8Array(buffer)),
        size: file.size
      });
      await target.focus();
      return Response.redirect(PREFIX + '/fault-tool.html', 303);
    }

    // No existing client, store and redirect
    const buffer = await file.arrayBuffer();
    const cache = await caches.open(CACHE);
    await cache.put('__shared_file__', new Response(buffer));
    await cache.put('__shared_file_name__', new Response(JSON.stringify({
      name: file.name,
      size: file.size,
      timestamp: Date.now()
    })));

    return Response.redirect(PREFIX + '/fault-tool.html?shared=1&name=' + encodeURIComponent(file.name), 303);
  } catch (err) {
    console.error('Share handler error:', err);
    return Response.redirect(PREFIX + '/fault-tool.html?shared=error', 303);
  }
}

// Listen for messages from clients to get shared file
self.addEventListener('message', event => {
  if (event.data && event.data.type === 'get-shared-file') {
    event.waitUntil(
      caches.open(CACHE).then(async cache => {
        const fileResp = await cache.match('__shared_file__');
        const nameResp = await cache.match('__shared_file_name__');
        if (!fileResp || !nameResp) return;

        const clients = await self.clients.matchAll({type: 'window'});
        const target = clients.find(c => c.url.includes('fault-tool.html'));
        if (target) {
          const buffer = await fileResp.arrayBuffer();
          const meta = await nameResp.json();
          target.postMessage({
            type: 'shared-file',
            name: meta.name,
            data: Array.from(new Uint8Array(buffer)),
            size: meta.size
          });
          // Clean up
          await cache.delete('__shared_file__');
          await cache.delete('__shared_file_name__');
        }
      })
    );
  }
});
