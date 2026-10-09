const {chromium} = require(process.argv[2] || 'playwright');
const fs=require('node:fs');
const url=process.argv[3]||'http://127.0.0.1:7341';
const hostile=process.argv.includes('--hostile');
(async()=>{
  const browser=await chromium.launch({headless:true});
  const context=await browser.newContext({viewport:{width:1920,height:1080},reducedMotion:'reduce',extraHTTPHeaders:{'ngrok-skip-browser-warning':'1'}});
  const externalRequests=[],fontEvidence=[];
  if(hostile){
    if(new URL(url).hostname!=='127.0.0.1')throw Error('Hostile-room test requires the loopback replay server');
    await context.route('**/*',route=>{
      if(new URL(route.request().url()).origin!==new URL(url).origin){externalRequests.push(route.request().url());return route.abort()}
      return route.continue();
    });
  }
  const page=await context.newPage(),errors=[];
  const cdp=hostile?await context.newCDPSession(page):null;
  if(cdp){await cdp.send('DOM.enable');await cdp.send('CSS.enable')}
  async function checkFonts(label){
    if(!cdp)return;
    await page.evaluate(()=>document.fonts.ready);
    const {root}=await cdp.send('DOM.getDocument',{depth:-1});
    const nodes=[];
    function visit(node){
      if(node.nodeType===1&&!['STYLE','SCRIPT','TITLE'].includes(node.nodeName)&&node.children?.some(c=>c.nodeType===3&&c.nodeValue.trim()))nodes.push(node);
      for(const child of node.children||[])visit(child);
    }
    visit(root);
    for(const node of nodes){
      const {fonts}=await cdp.send('CSS.getPlatformFontsForNode',{nodeId:node.nodeId});
      for(const font of fonts){
        fontEvidence.push({label,element:node.nodeName,...font});
        if(!font.isCustomFont)throw Error(`System font fallback: ${label} ${node.nodeName} ${font.familyName}`);
      }
    }
  }
  context.on('page',p=>p.on('pageerror',e=>errors.push(e.message)));
  page.on('pageerror',e=>errors.push(e.message));
  fs.mkdirSync('artifacts/stage',{recursive:true});
  try{
    if(process.argv.includes('--print-only')){
      await page.goto(new URL('/presenter.html',url).href);
      await page.waitForFunction(()=>document.querySelectorAll('#print-script .print-beat').length===30);
      const script=await page.locator('#print-script').textContent();
      if(!script.includes('The large T calculation card does not contain these metadata.'))throw Error('Speaker script is stale');
      await page.emulateMedia({media:'print'});
      await page.evaluate(()=>document.fonts.ready);
      await page.pdf({path:'artifacts/lambda-world-speaker-script.pdf',format:'A4',printBackground:true});
      console.log('Printed updated 30-cue speaker script; no workflow checks repeated.');
      return;
    }
    const manifestRaw=await (await page.request.get(new URL('/api/scenes',url).href)).text();
    const manifest=JSON.parse(manifestRaw);
    await page.goto(url);
    await page.getByRole('button',{name:'Present on stage ↗',exact:true}).waitFor();
    await page.waitForFunction(()=>!document.querySelector('#enter-stage').disabled);
    await page.screenshot({path:'artifacts/stage/opening.png'});
    await page.getByRole('button',{name:'Present on stage ↗',exact:true}).click();
    await page.waitForFunction(()=>!document.querySelector('#run').disabled);
    const report=[];
    if(hostile)await context.setOffline(true);
    for(const viewport of [{width:1920,height:1080},{width:1366,height:768},{width:1024,height:768}]){
      await page.setViewportSize(viewport);
      for(const name of ['FLEET','WRAP','COMPOSE','PARALLEL','BELLMAN','BELIEF','SHAPLEY','RAINBOW','REVEAL']){
        await page.getByRole('button',{name,exact:true}).click();
        await page.waitForFunction(()=>!document.querySelector('#run').disabled);
        const cueCount=Number((await page.locator('#cue-count').textContent()).split('/')[1]);
        for(let cue=0;cue<cueCount;cue++){
          if(cue)await page.getByRole('button',{name:'Next explanation',exact:true}).click();
          const metrics=await page.locator('#focus-card').evaluate(el=>({overflow:el.scrollHeight-el.clientHeight,h:el.getBoundingClientRect().bottom,w:document.documentElement.scrollWidth-innerWidth,title:document.querySelector('#focus-title').textContent}));
          if(metrics.overflow>2||metrics.w>1||metrics.h>viewport.height-60)report.push({viewport,name,cue,...metrics});
        }
        if(name!=='REVEAL'){
          await page.getByRole('button',{name:'One update',exact:true}).click();
          const maximum=Number(await page.locator('#scrub').getAttribute('max'));
          for(const frame of [...new Set([0,1,Math.min(10,maximum),maximum])]){
            await page.locator('#scrub').evaluate((el,n)=>{el.value=n;el.dispatchEvent(new Event('input',{bubbles:true}))},frame);
            await page.waitForTimeout(40);
            const metrics=await page.locator('#focus-card').evaluate(el=>({overflow:el.scrollHeight-el.clientHeight,h:el.getBoundingClientRect().bottom,w:document.documentElement.scrollWidth-innerWidth,title:document.querySelector('#focus-title').textContent}));
            if(metrics.overflow>2||metrics.w>1||metrics.h>viewport.height-60)report.push({viewport,name,frame,trace:true,...metrics});
            const trace=await page.locator('#focus-code').textContent();
            if(!trace||/NaN|undefined/.test(trace))throw Error(`Invalid trace in ${name} at ${frame}: ${trace}`);
            if(name==='BELIEF'){
              const plan=manifest.results?.pomdp.visual.frames[frame].inspection.plan;
              if(plan){
                for(const row of plan.actions){
                  if(!trace.includes(Number(row.score.toFixed(4)).toString()))throw Error('Missing actual first-action score');
                }
                if(!trace.includes('p_seen + p_miss × V₁(b′)'))throw Error('Missing continuation-function decomposition');
              }else if(frame===3&&!trace.includes('terminal success'))throw Error('Terminal sighting still plans a search');
            }
          }
        }
        await checkFonts(`${viewport.width}x${viewport.height}/${name}`);
        if(hostile&&name==='FLEET'){
          await page.getByRole('button',{name:'Clojure ↗',exact:true}).click();
          await checkFonts(`${viewport.width}x${viewport.height}/Unicode Clojure`);
          await page.getByRole('button',{name:'Stage',exact:true}).click();
        }
        if(viewport.width===1920)await page.screenshot({path:`artifacts/stage/${name.toLowerCase()}.png`});
        if(hostile&&[1024,1920].includes(viewport.width)&&['COMPOSE','BELIEF'].includes(name)){
          await page.locator('#scrub').evaluate((el,n)=>{el.value=n;el.dispatchEvent(new Event('input',{bubbles:true}))},name==='BELIEF'?2:10);
          await page.waitForTimeout(50);
          fs.mkdirSync('artifacts/hostile-room',{recursive:true});
          await page.screenshot({path:`artifacts/hostile-room/${viewport.width}x${viewport.height}-${name.toLowerCase()}.png`});
        }
      }
    }
    fs.writeFileSync('artifacts/stage/layout-report.json',JSON.stringify(report,null,2));
    // Public experiments are real recorded cells; local experiments evaluate the same source.
    await page.setViewportSize({width:1512,height:982});
    for(const [chapter,variants] of [['WRAP',['no-penalty']],['COMPOSE',['no-memory','long-memory']],['BELLMAN',['immediate-only','no-current']]]){
      await page.getByRole('button',{name:chapter,exact:true}).click();
      await page.waitForFunction(()=>!document.querySelector('#run').disabled);
      await page.locator('#scrub').evaluate(el=>{el.value=10;el.dispatchEvent(new Event('input',{bubbles:true}))});
      await page.getByRole('button',{name:'One update',exact:true}).click();
      const baseline=await page.locator('#focus-code').textContent();
      const original=await page.locator('#editor').inputValue();
      for(const variant of variants){
        await page.locator('#experiment-choice').selectOption(variant);
        await page.waitForFunction(()=>!document.querySelector('#experiment-choice').disabled);
        await page.waitForTimeout(50);
        if(Number(await page.locator('#scrub').inputValue())!==10)throw Error('Experiment lost comparison step');
        if(await page.locator('#focus-code').textContent()===baseline)throw Error(`Experiment did not change computation: ${variant}`);
        if(await page.locator('#editor').inputValue()===original)throw Error('Experiment did not reveal its source');
        await page.locator('#experiment-choice').selectOption('default');
        await page.waitForFunction(()=>!document.querySelector('#experiment-choice').disabled);
        await page.waitForTimeout(50);
        if(await page.locator('#focus-code').textContent()!==baseline)throw Error('Default result was overwritten');
        if(await page.locator('#editor').inputValue()!==original)throw Error('Default source was not restored');
      }
    }
    if(hostile)await context.setOffline(false);
    // A separate window drives the audience without displaying speaker notes there.
    await page.setViewportSize({width:1512,height:982});
    const popupPromise=page.waitForEvent('popup');
    await page.getByRole('button',{name:'Presenter ↗',exact:true}).click();
    const presenter=await popupPromise;
    await presenter.getByText(/Connected to audience window/).waitFor();
    await presenter.locator('[data-chapter="5"]').click();
    await page.getByRole('heading',{name:'The person is drifting. Our belief moves.',exact:true}).waitFor();
    await presenter.getByRole('button',{name:'Show calculation',exact:true}).click();
    await page.getByRole('heading',{name:'Start with a belief, not a point.',exact:true}).waitFor();
    await presenter.getByRole('button',{name:'Show explanation',exact:true}).click();
    await presenter.locator('[data-cue="1"]').click();
    await page.getByRole('heading',{name:'Prediction transports probability mass.',exact:true}).waitFor();
    if(await page.locator('#layer-predicted').getAttribute('aria-pressed')!=='true')throw Error('Cue did not select prediction layer');
    const predicted=await page.locator('#universe').screenshot();
    await page.getByRole('button',{name:'Posterior',exact:true}).click();
    await page.waitForTimeout(80);
    const posterior=await page.locator('#universe').screenshot();
    if(predicted.equals(posterior))throw Error('Belief layer toggle did not change the rendered computation');
    await presenter.getByRole('button',{name:'Start timer',exact:true}).click();
    await presenter.waitForFunction(()=>document.querySelector('#elapsed').textContent!=='00:00');
    // Reloading the audience must preserve its cue and reconnect the existing notes window.
    await page.reload();
    await page.getByRole('heading',{name:'Prediction transports probability mass.',exact:true}).waitFor();
    if(await page.locator('#layer-posterior').getAttribute('aria-pressed')!=='true')throw Error('Reload lost belief layer');
    if(!(await page.locator('body').evaluate(el=>el.classList.contains('stage'))))throw Error('Reload lost stage mode');
    await presenter.getByText(/Connected to audience window/).waitFor();
    await presenter.getByRole('button',{name:'Pause timer',exact:true}).click();
    await presenter.getByRole('button',{name:'Start timer',exact:true}).waitFor();
    await presenter.getByRole('button',{name:'Reset timer',exact:true}).click();
    await presenter.waitForFunction(()=>document.querySelector('#elapsed').textContent==='00:00');
    await presenter.locator('[data-cue="3"]').click();
    await page.getByRole('button',{name:'Plan two searches',exact:true}).click();
    await page.getByRole('heading',{name:'One optimal two-search plan.',exact:true}).waitFor();
    await page.reload();
    await page.getByRole('heading',{name:'One optimal two-search plan.',exact:true}).waitFor();
    if(await page.locator('#plan-searches').getAttribute('aria-pressed')!=='true')throw Error('Refresh lost decision view');
    await presenter.getByRole('button',{name:'Open code',exact:true}).click();
    await page.locator('#editor').waitFor({state:'visible'});
    if(await page.locator('body').evaluate(el=>el.classList.contains('stage')))throw Error('Code command did not leave stage mode');
    await presenter.evaluate(()=>document.fonts.ready);
    await presenter.emulateMedia({media:'print'});
    await presenter.evaluate(()=>document.fonts.ready);
    await presenter.pdf({path:'artifacts/lambda-world-speaker-script.pdf',format:'A4',printBackground:true});
    await presenter.emulateMedia({media:'screen'});
    await presenter.screenshot({path:'artifacts/stage/presenter.png'});
    await presenter.close();
    // Keyboard cue navigation and narrow-screen access.
    await page.setViewportSize({width:390,height:844});
    await page.getByRole('button',{name:'Stage',exact:true}).click();
    await page.keyboard.press('ArrowDown');
    await page.screenshot({path:'artifacts/stage/mobile.png'});
    if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Horizontal mobile overflow');
    if(errors.length)throw Error(errors.join('\n'));
    if(report.length)throw Error(`${report.length} stage layout overflows; see artifacts/stage/layout-report.json`);
    // Notes may be opened before entering the audience presentation.
    const fresh=await browser.newContext({reducedMotion:'reduce',extraHTTPHeaders:{'ngrok-skip-browser-warning':'1'}});
    const intro=await fresh.newPage();await intro.goto(url);
    await intro.waitForFunction(()=>!document.querySelector('#enter-stage').disabled);
    const notesPromise=intro.waitForEvent('popup');
    await intro.getByRole('button',{name:'Presenter ↗',exact:true}).click();
    const notes=await notesPromise;
    await notes.locator('[data-chapter="5"]').click();
    await intro.getByRole('heading',{name:'The person is drifting. Our belief moves.',exact:true}).waitFor();
    await fresh.close();
    if(hostile){
      if(!fontEvidence.some(f=>f.familyName==='Rescue Symbols'))throw Error('Unicode symbol font was not exercised');
      if(externalRequests.length)throw Error('External requests attempted: '+externalRequests.join(', '));
      fs.mkdirSync('artifacts/hostile-room',{recursive:true});
      fs.writeFileSync('artifacts/hostile-room/browser.json',JSON.stringify({passed:true,sourceSha256:require('node:crypto').createHash('sha256').update(manifestRaw).digest('hex'),viewports:['1920x1080','1366x768','1024x768'],chapters:9,cues:30,comparisons:5,allDemoNavigationWithBrowserOffline:true,coldLoadExternalRequests:externalRequests,layoutFailures:report,fonts:fontEvidence},null,2));
    }
    console.log('PASS: 9 chapters, all cues and numeric traces at 3 projector sizes; 5 experiment comparisons and default restoration; POMDP decision branches and refresh; presenter sync from intro, refresh recovery, timer, code switch, belief layers, print PDF, mobile.');
  }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
