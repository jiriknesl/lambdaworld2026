// Optional UI check: node scripts/browser-smoke.cjs /path/to/playwright
const {chromium} = require(process.argv[2] || 'playwright');
const fs = require('node:fs');
const path = require('node:path');
const preview=!!process.argv[3]&&process.argv[4]!=='live';
const url=process.argv[3]||'http://127.0.0.1:7340';
(async () => {
  const browser = await chromium.launch({headless:true});
  const page = await browser.newPage({viewport:{width:1512,height:982},deviceScaleFactor:1,extraHTTPHeaders:{"ngrok-skip-browser-warning":"1"}});
  const errors=[];
  page.on('pageerror', e=>errors.push(e.message));
  await page.goto(url);
  await page.getByRole('button',{name:/Enter the computation/}).waitFor({timeout:30000});
  fs.mkdirSync('artifacts/preview',{recursive:true});
  await page.screenshot({path:'artifacts/preview/01-entry.png'});
  await page.getByRole('button',{name:/Enter the computation/}).click();
  await page.waitForFunction(()=>!document.querySelector('#run').disabled);
  await page.waitForTimeout(1700);
  await page.screenshot({path:'artifacts/preview/02-flock.png'});
  for(const name of ['WRAP','COMPOSE','PARALLEL','BELLMAN','BELIEF','SHAPLEY','RAINBOW','REVEAL']){
    await page.getByRole('button',{name,exact:true}).click();
    await page.waitForFunction(()=>!document.querySelector('#run').disabled);
    if(name!=='REVEAL'){
      await page.locator('#scrub').evaluate(el=>{el.value=el.max;el.dispatchEvent(new Event('input',{bubbles:true}))});
    }
    await page.waitForTimeout(1400);
    await page.screenshot({path:`artifacts/preview/${name.toLowerCase()}.png`});
    if(name!=='REVEAL'){
      if(!(await page.locator('#pane-explain h2').textContent()))throw Error('Missing explanation');
      await page.getByRole('tab',{name:'One update',exact:true}).click();
      await page.waitForTimeout(100);
      if(!(await page.locator('#pane-inspect').textContent()).includes('ACTUAL CLOJURE VALUES'))throw Error('Missing trace');
      await page.screenshot({path:`artifacts/preview/${name.toLowerCase()}-trace.png`});
    }
  }
  await page.getByRole('button',{name:'BELIEF',exact:true}).click();
  await page.waitForFunction(()=>!document.querySelector('#run').disabled);
  await page.getByRole('tab',{name:'One update',exact:true}).click();
  await page.getByRole('button',{name:'Next computed step',exact:true}).click();
  await page.getByText('central search → missed',{exact:true}).waitFor();
  await page.getByRole('button',{name:'Next computed step',exact:true}).click();
  await page.getByText('east search → missed',{exact:true}).waitFor();
  if(preview){
    await page.getByRole('tab',{name:'Clojure',exact:true}).click();
    if(!await page.locator('#editor').evaluate(el=>el.readOnly))throw Error('Preview editor must be read only');
    await page.getByRole('button',{name:/Replay result/}).click();
    await page.waitForFunction(()=>!document.querySelector('#run').disabled);
    if(errors.length)throw Error(errors.join('\n'));
    console.log('PASS: public preview, nine chapters, explanations, computed traces, belief steps, recorded replay.');
    await browser.close();return;
  }
  // Expanding the HOF exposes code that is also independently runnable.
  await page.getByRole('button',{name:'COMPOSE',exact:true}).click();
  await page.waitForFunction(()=>!document.querySelector('#run').disabled);
  await page.getByRole('tab',{name:'Clojure',exact:true}).click();
  await page.getByRole('button',{name:/Unfold HOF/}).click();
  if(!(await page.locator('#editor').inputValue()).includes('(defn with-momentum'))throw Error('HOF not unfolded');
  await page.getByRole('button',{name:/Run Clojure/}).click();
  await page.waitForFunction(()=>!document.querySelector('#run').disabled);
  if(await page.locator('#eval-status').evaluate(el=>el.classList.contains('error')))throw Error('Unfolded HOF failed');
  // An edited cell must really reach the JVM and change the returned visual.
  await page.getByRole('button',{name:'SHAPLEY',exact:true}).click();
  await page.waitForFunction(()=>!document.querySelector('#run').disabled);
  await page.getByRole('tab',{name:'Clojure',exact:true}).click();
  await page.locator('#editor').fill('(s/view (fn [team] (* 10 (count team))) s/players)');
  const responsePromise=page.waitForResponse(r=>r.url().endsWith('/api/eval')&&r.request().method()==='POST');
  await page.getByRole('button',{name:/Run Clojure/}).click();
  const response=await responsePromise;const data=await response.json();
  if(!data.ok||Object.values(data.visual.credits).some(v=>v!==10))throw Error('Edited Clojure did not update attribution');
  await page.waitForFunction(()=>!document.querySelector('#run').disabled);
  // Errors are visible and recoverable.
  await page.locator('#editor').fill('(throw (ex-info "deliberate smoke test" {}))');
  await page.getByRole('button',{name:/Run Clojure/}).click();
  await page.getByText('Cell failed',{exact:true}).waitFor();
  await page.getByRole('button',{name:/Reset/}).click();
  await page.getByRole('button',{name:/Run Clojure/}).click();
  await page.waitForFunction(()=>!document.querySelector('#run').disabled);
  if(await page.locator('#eval-status').evaluate(el=>el.classList.contains('error')))throw Error('Recovery failed');
  await page.locator('#editor').blur();await page.keyboard.press('c');
  if(!await page.locator('body').evaluate(el=>el.classList.contains('code-hidden')))throw Error('Code toggle failed');
  // Small screens retain navigation and offer code as a deliberate overlay.
  await page.setViewportSize({width:390,height:844});
  await page.getByRole('button',{name:'FLEET',exact:true}).click();
  await page.waitForFunction(()=>!document.querySelector('#run').disabled);
  await page.waitForTimeout(1000);
  await page.screenshot({path:'artifacts/preview/mobile.png'});
  if(errors.length)throw Error(errors.join('\n'));
  console.log('PASS: all chapters, edited JVM evaluation, error recovery, keyboard controls, mobile viewport.');
  await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
