const path = require('path');
const {chromium} = require(process.argv[2] || 'playwright');
(async () => {
  const browser = await chromium.launch();
  try {
    const context = await browser.newContext();
    await context.setOffline(true);
    const page = await context.newPage();
    const external = [];
    page.on('request', r => { if (!/^(file:|data:)/.test(r.url())) external.push(r.url()); });
    await page.goto('file://' + path.resolve(__dirname, '../../artifacts/validate-rainbow-study.html'));
    await page.evaluate(() => document.fonts.ready);
    const text = await page.locator('body').innerText();
    for (const required of ['The state and the event', 'Read the exact proof syntax',
      'What the residual says', 'soundness', 'Questions to practise aloud', '275', '165']) {
      if (!text.includes(required)) throw Error('Missing guide section: ' + required);
    }
    if (external.length) throw Error('External guide assets');
    await page.pdf({path: path.resolve(__dirname, '../../artifacts/validate-rainbow-study.pdf'),
      printBackground: true, preferCSSPageSize: true});
    console.log('Printed study guide offline; all required sections present.');
  } finally { await browser.close(); }
})().catch(e => {console.error(e);process.exit(1);});
