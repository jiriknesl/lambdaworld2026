// Reproduce a previous app intercepting navigation on a reused preview origin.
const {chromium} = require(process.argv[2] || 'playwright');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
let oldApp = true;
const oldHTML = '<title>new-gaia</title><h1>new-gaia</h1><script>navigator.serviceWorker.register("/sw.js")</script>';
const oldWorker = `
  self.addEventListener('install', e => e.waitUntil(self.skipWaiting()));
  self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));
  self.addEventListener('fetch', e => {
    if(e.request.mode === 'navigate') e.respondWith(Promise.resolve(new Response(
      ${JSON.stringify(oldHTML)}, {headers: {'Content-Type':'text/html'}})));
  });`;
const server = http.createServer((req, res) => {
  const pathname = new URL(req.url, 'http://localhost').pathname;
  res.setHeader('Cache-Control', 'no-store');
  if(oldApp){
    res.setHeader('Content-Type', pathname === '/sw.js' ? 'text/javascript' : 'text/html');
    res.end(pathname === '/sw.js' ? oldWorker : oldHTML); return;
  }
  const file = pathname === '/' ? 'index.html' : pathname.slice(1);
  if(!['index.html','fonts.css','app.js','style.css','sw.js','ocean.js','stage.js','experiments.js','api/scenes'].includes(file)){
    res.writeHead(404); res.end(); return;
  }
  res.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' :
    file.endsWith('.css') ? 'text/css' : file === 'api/scenes' ? 'application/json' : 'text/html');
  res.end(fs.readFileSync(path.join('.preview', file)));
});
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({headless:true});
  try {
    const page = await browser.newPage();
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.waitForFunction(() => !!navigator.serviceWorker.controller);
    await page.evaluate(async () => {
      localStorage.setItem('keep-this', 'original-data');
      await (await caches.open('previous-app-data')).put('/saved-data', new Response('keep'));
    });
    oldApp = false;
    // The old worker still supplies the wrong application despite the new server.
    await page.goto(`http://127.0.0.1:${server.address().port}/?old-worker-proof=1`);
    // An update fetch bypasses the controlling worker, just as browser update checks do.
    await page.evaluate(async () => {
      const registration = await navigator.serviceWorker.getRegistration();
      if(registration) await registration.update();
    }).catch(error => {
      if(!/context.*destroyed|navigation/i.test(error.message)) throw error;
    });
    await page.waitForFunction(() => document.title.includes('Function Observatory'), {timeout:15000});
    await page.getByRole('button', {name:/Enter the computation/}).waitFor();
    const state = await page.evaluate(async () => ({
      workers: (await navigator.serviceWorker.getRegistrations()).length,
      data: localStorage.getItem('keep-this'),
      cached: await (await (await caches.open('previous-app-data')).match('/saved-data')).text()
    }));
    if(state.workers !== 0 || state.data !== 'original-data' || state.cached !== 'keep')
      throw Error(JSON.stringify(state));
    console.log('PASS: old app worker retired; presentation loaded; local storage and cache data preserved.');
  } finally {
    await browser.close();
    await new Promise(resolve => server.close(resolve));
  }
})().catch(error => {console.error(error); server.close(); process.exitCode = 1;});
