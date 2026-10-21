// Browser check for the deck.
//   node scripts/smoke.cjs /path/to/node_modules/playwright [url] [--shots dir]
// Default url: the release build from disk (file://…/public/index.html), so it also proves offline use.
// Visits every slide, every short reveal and sampled long traces; fails on render errors, overflow, external requests or a broken hash restore.
const path = require('path');
const fs = require('fs');

const args = process.argv.slice(2);
const pw = require(args[0] || 'playwright');
const url = args[1] && !args[1].startsWith('--') ? args[1] : 'file://' + path.resolve(__dirname, '../public/index.html');
const allPhases = args.includes('--all-phases');
const shotsAt = args.indexOf('--shots');
const shots = shotsAt >= 0 ? args[shotsAt + 1] : null;

(async () => {
  const browser = await pw.chromium.launch();
  const problems = [];
  const external = [];
  let traceSlide = null;
  let playbackSlide = null;
  let checkedFrames = 0;
  for (const [w, h] of [[1600, 900], [1920, 1080], [1024, 768]]) {
    const page = await browser.newPage({ viewport: { width: w, height: h } });
    if (url.startsWith('file:')) await page.context().setOffline(true);
    page.on('request', r => { const u = r.url(); if (!u.startsWith('file:') && !u.startsWith('data:') && !u.includes('localhost') && !u.includes('127.0.0.1')) external.push(u); });
    page.on('pageerror', e => problems.push(`${w}x${h} page error: ${e.message}`));
    await page.goto(url);
    await page.waitForSelector('.slide .top');
    await page.waitForFunction(() => window.talkReady === true, null, { timeout: 180000 });
    const total = await page.evaluate(() => parseInt(document.querySelector('.count').textContent.split('/')[1]));
    for (let s = 1; s <= total; s++) {
      if (s > 1) await page.keyboard.press('ArrowDown');
      await page.keyboard.press('End');
      const steps = await page.evaluate(() => {
        const label = document.querySelector('.steps-text');
        return label ? parseInt(label.textContent.split('/')[1]) : 1;
      });
      if (!Number.isInteger(steps) || steps < 1) throw Error(`Invalid step count on slide ${s}`);
      const frames = allPhases || steps <= 12 ? Array.from({length: steps}, (_, i) => i + 1)
        : [...new Set([1, 2, Math.ceil(steps / 2), steps])];
      for (const frame of frames) {
        await page.evaluate(({s, frame}) => { location.hash = '#s' + s + (frame > 1 ? 'a' + frame : ''); }, {s, frame});
        await page.waitForFunction(({s, frame, steps}) => {
          const count = document.querySelector('.count').textContent.trim();
          const label = document.querySelector('.steps-text');
          return count.startsWith(s + ' /') && (steps === 1 || label.textContent.trim().startsWith(frame + ' /'));
        }, {s, frame, steps});
        const info = await page.evaluate(() => {
          const body = document.querySelector('.slide .body');
          const stage = document.getElementById('stage').getBoundingClientRect();
          const scale = stage.width / 1600;
          const over = [];
          for (const el of body.querySelectorAll('*')) {
            if (el.closest('svg.scene') && el.tagName !== 'svg') continue;
            const r = el.getBoundingClientRect();
            if (r.width === 0 || r.height === 0) continue;
            if (r.right > stage.right + 2 || r.bottom > stage.bottom - 20 * scale + 2) over.push(el.tagName + '.' + (el.className.baseVal ?? el.className));
          }
          for (const pre of body.querySelectorAll('pre')) if (pre.scrollWidth > pre.clientWidth + 2 || pre.scrollHeight > pre.clientHeight + 2) over.push('pre clipped');
          return { title: (document.querySelector('.title') || {}).textContent || document.title, error: !!body.querySelector('.error'),
                   over: [...new Set(over)].slice(0, 4), hash: location.hash };
        });
        checkedFrames++;
        if (info.title === 'Same updates, different objectives') traceSlide = s;
        if (info.title === 'Play the resulting day') playbackSlide = s;
        if (info.error) problems.push(`${w}x${h} slide ${s}: render error`);
        if (info.over.length) problems.push(`${w}x${h} slide ${s} (${info.title}): overflow ${info.over.join(', ')}`);
      }
      if (shots && w === 1600) {
        fs.mkdirSync(shots, { recursive: true });
        await page.screenshot({ path: path.join(shots, `s${String(s).padStart(2, '0')}.png`) });
      }
    }
    if (traceSlide === null) throw Error('Cannot find the optimisation trace for control checks');
    // hash restore
    await page.goto(url.split('#')[0] + `#s${traceSlide}a5`);
    await page.waitForSelector('.slide .top');
    const restored = await page.evaluate(() => [document.querySelector('.count').textContent.trim(), location.hash]);
    if (!restored[0].startsWith(traceSlide + ' ') || restored[1] !== `#s${traceSlide}a5`) problems.push(`${w}x${h} hash restore failed: ${restored}`);
    await page.close();
  }
  // controls: steps, playback, theme, notes window following the stage
  {
    const context = await browser.newContext({ viewport: { width: 1600, height: 900 } });
    const page = await context.newPage();
    const hash = () => page.evaluate(() => location.hash);
    await page.goto(url.split('#')[0] + `#s${traceSlide}`);
    await page.waitForSelector('.slide .top');
    await page.waitForFunction(() => window.talkReady === true, null, { timeout: 180000 });
    await page.keyboard.press('ArrowRight');
    if (await hash() !== `#s${traceSlide}a2`) problems.push('ArrowRight did not advance the step: ' + await hash());
    await page.keyboard.press('Shift+ArrowRight');
    if (await hash() !== `#s${traceSlide}a12`) problems.push('Shift+ArrowRight did not jump 10 steps: ' + await hash());
    await page.keyboard.press('ArrowLeft');
    if (await hash() !== `#s${traceSlide}a11`) problems.push('ArrowLeft did not go back: ' + await hash());
    await page.keyboard.press('Space');
    await page.waitForTimeout(600);
    await page.keyboard.press('Space');
    const played = parseInt((await hash()).split('a')[1]);
    if (!(played > 13)) problems.push('Space did not play the steps: ' + await hash());
    await page.keyboard.press('ArrowDown');
    if (await hash() !== `#s${traceSlide + 1}`) problems.push('ArrowDown did not go to the next slide: ' + await hash());
    await page.keyboard.press('ArrowUp');
    if (!(await hash()).startsWith(`#s${traceSlide}a`)) problems.push('ArrowUp did not go back a slide: ' + await hash());
    await page.keyboard.press('d');
    if (await page.evaluate(() => document.documentElement.dataset.theme) !== 'dark') problems.push('D did not switch to dark');
    await page.keyboard.press('d');
    const [notes] = await Promise.all([context.waitForEvent('page'), page.keyboard.press('n')]);
    await notes.waitForSelector('.notes-view h1');
    await page.keyboard.press('ArrowDown');
    await notes.waitForTimeout(300);
    const notesTitle = await notes.evaluate(() => document.querySelector('.notes-view h1').textContent);
    const stageTitle = await page.evaluate(() => document.querySelector('.title').textContent);
    if (notesTitle !== stageTitle) problems.push(`notes window did not follow: "${notesTitle}" vs "${stageTitle}"`);
    await notes.keyboard.press('ArrowDown');
    await page.waitForTimeout(300);
    const driven = await page.evaluate(() => document.querySelector('.title').textContent);
    if (driven === stageTitle) problems.push('keys in the notes window did not drive the stage');
    // Speech must follow reveals, not merely the slide title.
    await page.evaluate(() => { location.hash = '#s5'; });
    await notes.waitForFunction(() => document.querySelector('.n-text').textContent.includes('positive starting guess'));
    await page.keyboard.press('ArrowRight');
    await notes.waitForFunction(() => document.querySelector('.n-text').textContent.includes('Map is higher-order'));
    if ((await notes.locator('.n-text').innerText()).includes('positive starting guess')) problems.push('presenter speech stayed on previous reveal');
    await page.evaluate(() => { location.hash = '#s18a2'; });
    await notes.waitForFunction(() => document.querySelector('.n-text').textContent.includes('Every update revises all ten'));
    await page.keyboard.press('End');
    await notes.waitForFunction(() => document.querySelector('.n-text').textContent.includes('largest change is now below'));
    if (await notes.locator('.n-reference[open]').count()) problems.push('technical reference should start collapsed');
    // The schedule highlight must advance with the 3D clock in both day replays.
    if (playbackSlide === null) throw Error('Cannot find day playback');
    await page.evaluate(s => { location.hash = '#s' + s; }, playbackSlide);
    await page.waitForSelector('#played-schedule rect.now');
    for (const frame of [0, 1]) {
      if (frame) await page.keyboard.press('ArrowRight');
      const sample = () => page.evaluate(() => {
        const marker = document.querySelector('#played-schedule rect.now');
        const hour = parseInt(document.querySelector('#played-3d .clock').textContent);
        return {hour, x: +marker.getAttribute('x'), width: +marker.getAttribute('width')};
      });
      const a = await sample();
      await page.waitForFunction(hour => parseInt(document.querySelector('#played-3d .clock').textContent) !== hour, a.hour);
      const b = await sample();
      for (const value of [a, b]) {
        if (Math.abs(value.x - (120 + (value.hour - 8) * value.width)) > 1e-6)
          problems.push(`day replay ${frame}: hour ${value.hour} disagrees with schedule highlight`);
      }
      if (a.x === b.x) problems.push(`day replay ${frame}: current-hour marker did not move`);
    }
    await context.close();
  }
  await browser.close();
  if (external.length) problems.push('external requests: ' + [...new Set(external)].join(', '));
  console.log(problems.length ? problems.join('\n') : `OK: ${checkedFrames} frames checked at three sizes; ${allPhases ? 'every slide and phase' : 'all slides and short reveals, sampled long traces'}; no overflow, hash restore works, day marker stays synchronized, no external requests`);
  process.exit(problems.length ? 1 : 0);
})();
