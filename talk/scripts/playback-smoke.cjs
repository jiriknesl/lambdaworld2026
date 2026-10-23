// node scripts/playback-smoke.cjs /path/to/playwright [public-url]
// Hold the playback clock at known instants to compare the same bird in both plans.
const path = require('path');
const {chromium} = require(process.argv[2] || 'playwright');
const target = process.argv[3] || 'file://' + path.resolve(__dirname, '../public/index.html');

(async () => {
  const browser = await chromium.launch();
  try {
    const context = await browser.newContext({extraHTTPHeaders: {'ngrok-skip-browser-warning': 'true'}});
    if (target.startsWith('file:')) await context.setOffline(true);
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(target);
    const visit = page.getByRole('button', {name: 'Visit Site', exact: false});
    if (await visit.isVisible()) await visit.click();
    await page.waitForFunction(() => window.talkReady === true, null, {timeout: 180000});
    await page.evaluate(() => {
      const original = performance.now.bind(performance);
      performance.now = () => window.playbackTime ?? original();
    });
    let frames = 0;
    for (const [width, height] of [[1600, 900], [1920, 1080], [1024, 768]]) {
      await page.setViewportSize({width, height});
      for (const [slide, phase, time, mode] of [
        [15, 1, 6300, null], [15, 2, 6300, null],
        [16, 1, 6300, 'approach'], [16, 1, 6975, 'strike'],
        [16, 1, 7350, 'strike'], [16, 2, 6975, 'pass']
      ]) {
        await page.evaluate(({slide, phase, time}) => {
          window.playbackTime = time;
          location.hash = 's' + slide + 'a' + phase;
        }, {slide, phase, time});
        await page.waitForFunction(({slide, phase, mode}) =>
          document.querySelector('.count').textContent.trim().startsWith(slide + ' /') &&
          document.querySelector('.steps-text').textContent.trim().startsWith(phase + ' /') &&
          (!mode || document.querySelector('.encounter')?.dataset.mode === mode), {slide, phase, mode});
        // Allow the existing live renderer to update at the requested timestamp.
        await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
        const result = await page.evaluate(() => {
          const body = document.querySelector('.body'), stage = document.querySelector('#stage').getBoundingClientRect();
          return {
            text: body.textContent,
            title: !!document.querySelector('.title'),
            flash: !!body.querySelector('.strike-flash'),
            fallen: !!body.querySelector('.bird.struck'),
            clock: body.querySelector('.clock')?.textContent,
            marker: body.querySelector('rect.now')?.getAttribute('x'),
            ring: body.querySelector('.strike-flash circle')?.getAttribute('r'),
            overflow: [...body.querySelectorAll('*')].filter(e => {
              if (e.closest('svg') && e.tagName !== 'svg') return false;
              const r = e.getBoundingClientRect();
              return r.width && r.height && (r.right > stage.right + 2 || r.bottom > stage.bottom - 10);
            }).map(e => e.tagName)
          };
        });
        if (result.overflow.length) throw Error('Overflow: ' + width + ' slide ' + slide);
        if (result.text.includes('Same cost, rate, start and stopping test.')) throw Error('Old momentum caption');
        if (mode) {
          if (result.clock !== '12:00') throw Error('Clock mismatch');
          if (Math.abs(Number(result.marker) - 312) > 0.01) throw Error('Hour marker mismatch');
          if (mode === 'strike' && Math.abs(Number(result.ring) - (12 + 30 * ((time / 1500 - 4 - 0.45) / 0.55))) > 0.01) throw Error('Impact timing mismatch');
          if (result.flash !== (mode === 'strike') || result.fallen !== (mode === 'strike')) throw Error('Strike graphics mismatch');
          if (result.title || result.text.includes('Illustrative strikes')) throw Error('Removed slide copy remains');
          if (width === 1600 && target.startsWith('file:')) {
            await page.screenshot({path: '/private/tmp/playback-' + phase + '-' + time + '.png'});
          }
        }
        frames++;
      }
    }
    const [notes] = await Promise.all([context.waitForEvent('page'), page.keyboard.press('n')]);
    await notes.waitForSelector('.notes-view');
    await notes.waitForFunction(() => document.querySelector('.n-text').textContent.includes('same illustrated bird'));
    if (errors.length) throw Error(errors.join('\n'));
    console.log(`Playback passed ${frames} frames at three sizes: approach, impact, fall, stopped-turbine passage, clock and hour marker; presenter speech matches.`);
  } finally {
    await browser.close();
  }
})().catch(e => { console.error(e); process.exit(1); });
