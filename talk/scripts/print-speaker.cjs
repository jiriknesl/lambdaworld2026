// node scripts/print-speaker.cjs /path/to/node_modules/playwright
const path = require('path');
const {chromium} = require(process.argv[2] || 'playwright');
const root = path.resolve(__dirname, '../..');
(async () => {
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({offline: true});
    const external = [];
    page.on('request', r => { if (!/^(file:|data:)/.test(r.url())) external.push(r.url()); });
    await page.goto('file://' + path.join(root, 'artifacts/lambda-world-current-speaker-script.html'));
    await page.evaluate(() => document.fonts.ready);
    if (await page.locator('section').count() !== 32) throw Error('Missing slide scripts');
    if (await page.locator('section h2').count() !== 80) throw Error('Missing phase cues');
    if (external.length) throw Error('External resources in printable script');
    await page.pdf({path: path.join(root, 'artifacts/lambda-world-current-speaker-script.pdf'),
      printBackground: true, preferCSSPageSize: true});
    console.log('Printed 32 slide scripts / 80 cues with networking disabled.');
  } finally { await browser.close(); }
})().catch(e => {console.error(e);process.exit(1);});
