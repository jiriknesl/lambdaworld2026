// node scripts/shield-smoke.cjs /path/to/playwright [public-url]
// Check the rendered reaction against a controlled clock, offline by default.
const path = require('path');
const {chromium} = require(process.argv[2] || 'playwright');
const target = process.argv[3] || 'file://' + path.resolve(__dirname, '../public/index.html');
(async () => {
  const browser = await chromium.launch();
  try {
    const ctx = await browser.newContext({extraHTTPHeaders: {'ngrok-skip-browser-warning':'true'}});
    if (target.startsWith('file:')) await ctx.setOffline(true);
    const page = await ctx.newPage(), errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(target);
    await page.waitForFunction(() => window.talkReady === true, null, {timeout:180000});
    await page.evaluate(() => { performance.now = () => window.shieldTestTime ?? 0; });
    const sample = async time => {
      await page.evaluate(t => { window.shieldTestTime = t; }, time);
      await page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
      return page.evaluate(() => {
        const root = document.querySelector('#shield-animation > div');
        return {action:root.dataset.action, detected:root.dataset.detected,
          angle:Number(root.querySelector('[data-rotor-angle]').dataset.rotorAngle),
          bird:root.querySelector('.bird').parentElement.getAttribute('transform'),
          text:root.textContent, encounter:root.dataset.encounter,
          flash:!!root.querySelector('.strike-flash'), fallen:!!root.querySelector('.bird.struck')};
      });
    };
    for (const [width,height] of [[1600,900],[1920,1080],[1024,768]]) {
      await page.setViewportSize({width,height});
      let unshieldedBird;
      for (const phase of [1,2]) {
        // Visit another slide first so repeated checks reset the animation's start.
        await page.evaluate(() => {location.hash='s21';window.shieldTestTime=0;});
        await page.waitForFunction(() => document.querySelector('.count').textContent.startsWith('21 /'));
        await page.evaluate(p => {location.hash='s20a'+p;}, phase);
        await page.waitForFunction(p => document.querySelector('#shield-animation > div')?.dataset.shield === String(p===2), phase);
        const before = await sample(1999), at = await sample(2000), later = await sample(4000);
        if (before.action!=='run'||before.detected!=='false'||at.detected!=='true') throw Error('Detection timing mismatch');
        if (phase===1) {
          if (at.action!=='run'||later.angle===at.angle||!/check: [1-9]\d* states/.test(later.text)) throw Error('Missing unshielded failure');
          unshieldedBird=later.bird;
        } else {
          if (at.action!=='stop'||later.angle!==at.angle||!later.text.includes('check: 0 states')) throw Error('Shield did not stop or pass its check');
          if (later.bird!==unshieldedBird) throw Error('Different bird trajectory in comparison');
        }
        const contact = await sample(4000 / 0.9), impact = await sample(4700), end = await sample(6000);
        if (phase===1) {
          if (contact.encounter!=='strike'||!impact.flash||!impact.fallen||end.encounter!=='strike') throw Error('Missing impact or falling bird');
          if (!end.bird.includes(',250.0)')) throw Error('Struck bird did not fall');
        } else if (contact.encounter!=='pass'||impact.flash||impact.fallen||end.encounter!=='pass') throw Error('Shielded bird did not pass');
        await sample(4700);
        if (width===1600 && target.startsWith('file:')) await page.screenshot({path:`/private/tmp/lambda-shield-phase-${phase}.png`});
      }
    }
    // Refresh recovers the selected shield view and starts the event again.
    await page.reload();
    await page.waitForFunction(() => window.talkReady === true, null, {timeout:180000});
    await page.waitForFunction(() => document.querySelector('#shield-animation > div')?.dataset.shield==='true');
    const [notes]=await Promise.all([ctx.waitForEvent('page'),page.keyboard.press('n')]);
    await notes.waitForFunction(() => document.querySelector('.n-text')?.textContent.includes('wrap the decision function with a shield'));
    await page.keyboard.press('ArrowLeft');
    await notes.waitForFunction(() => document.querySelector('.n-text')?.textContent.includes('Its decision table has no entry'));
    if(errors.length) throw Error(errors.join('\n'));
    console.log('Shield passed: same bird, detection boundary, actual RUN/STOP, rotor motion/freeze, impact/fall versus passage, failure counts, refresh and presenter speech at three sizes.');
  } finally { await browser.close(); }
})().catch(e => {console.error(e);process.exit(1);});
