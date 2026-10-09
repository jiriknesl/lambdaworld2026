// Exercise the actual ZIP through the documented Python server, in a clean directory.
const {chromium}=require(process.argv[2]||'playwright');
const fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {spawn,execFileSync}=require('node:child_process');
const {createHash}=require('node:crypto');
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
(async()=>{
  const archive=path.resolve('artifacts/lambda-world-rehearsal.zip');
  const extracted=fs.mkdtempSync(path.join(os.tmpdir(),'lambda-world-offline-'));
  let server,browser;
  const external=[],errors=[];
  try{
    execFileSync('python3',['-m','zipfile','-e',archive,extracted]);
    const manifest=JSON.parse(fs.readFileSync(path.join(extracted,'api/scenes'),'utf8'));
    if(manifest.mode!=='replay'||manifest.token)throw Error('Bundle is not a credential-free replay');
    server=spawn('python3',['-u','-m','http.server','0','--bind','127.0.0.1','--directory',extracted],{stdio:['ignore','pipe','ignore']});
    const port=await new Promise((resolve,reject)=>{
      let output='';const timeout=setTimeout(()=>reject(Error('Python server did not start')),10000);
      server.on('error',reject);server.on('exit',code=>reject(Error('Python server exited: '+code)));
      server.stdout.on('data',chunk=>{output+=chunk;const m=output.match(/port (\d+)/);if(m){clearTimeout(timeout);resolve(Number(m[1]))}});
    });
    const origin=`http://127.0.0.1:${port}`;
    browser=await chromium.launch({headless:true});
    const context=await browser.newContext({viewport:{width:1366,height:768},reducedMotion:'reduce'});
    context.on('page',page=>page.on('pageerror',e=>errors.push(e.message)));
    await context.route('**/*',route=>{
      const url=route.request().url();
      if(new URL(url).origin!==origin){external.push(url);return route.abort()}
      return route.continue();
    });
    const page=await context.newPage();
    await page.goto(origin+'/?stage=1#momentum');
    await page.waitForFunction(()=>!document.querySelector('#run').disabled&&document.querySelector('#presentation').hidden===false);
    await page.locator('#experiment-choice').selectOption('no-memory');
    await page.waitForFunction(()=>!document.querySelector('#experiment-choice').disabled);
    if(!await page.locator('#editor').evaluate(el=>el.readOnly))throw Error('Replay unexpectedly exposes an editable evaluator');
    if(!(await page.locator('#focus-text').textContent()).includes('β = 0'))throw Error('Offline momentum comparison failed');
    const popup=page.waitForEvent('popup');await page.getByRole('button',{name:'Presenter ↗',exact:true}).click();
    const presenter=await popup;
    await presenter.getByText(/Connected to audience window/).waitFor();
    await presenter.locator('[data-chapter="5"]').click();
    await presenter.locator('[data-cue="3"]').click();
    await presenter.getByRole('button',{name:'Show calculation',exact:true}).click();
    await page.getByRole('heading',{name:'One optimal two-search plan.',exact:true}).waitFor();
    const plan=manifest.results.pomdp.visual.frames[2].inspection.plan;
    const trace=await page.locator('#focus-code').textContent();
    for(const row of plan.actions){
      if(!trace.includes(Number(row.score.toFixed(4)).toString()))throw Error('Offline decision trace disagrees with bundled Clojure result');
    }
    await page.reload();
    await page.getByRole('heading',{name:'One optimal two-search plan.',exact:true}).waitFor();
    await presenter.getByText(/Connected to audience window/).waitFor();
    const pdfResponse=await page.request.get(origin+'/speaker-script.pdf');
    const pdf=await pdfResponse.body();
    if(!pdfResponse.ok()||pdf.subarray(0,5).toString()!=='%PDF-')throw Error('Packaged speaker PDF is not served');
    if(digest(pdf)!==digest(fs.readFileSync('artifacts/lambda-world-speaker-script.pdf')))throw Error('Packaged PDF is stale');
    if(external.length||errors.length)throw Error(JSON.stringify({external,errors}));
    fs.mkdirSync('artifacts/stage',{recursive:true});
    await page.screenshot({path:'artifacts/stage/offline-decision.png'});
    fs.writeFileSync('artifacts/stage/offline-report.json',JSON.stringify({
      passed:true,archiveSha256:digest(fs.readFileSync(archive)),pdfSha256:digest(pdf),
      chapters:manifest.scenes.length,experiments:Object.keys(manifest.experiments).length,
      externalRequests:external,errors,
      checks:['clean extraction','Python static server','momentum comparison','POMDP branch values','presenter control','refresh recovery','current PDF']
    },null,2));
    console.log('PASS: clean ZIP extraction; Python server; comparison, POMDP decisions, presenter, refresh, current PDF; zero external requests.');
  }finally{
    if(browser)await browser.close();
    if(server)server.kill('SIGTERM');
    fs.rmSync(extracted,{recursive:true,force:true});
  }
})().catch(e=>{console.error(e);process.exitCode=1});
