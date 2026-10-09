const {chromium}=require(process.argv[2]||'playwright');
const fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const {execFileSync}=require('node:child_process');
(async()=>{
  const browser=await chromium.launch({headless:true});
  const external=[],errors=[];
  try{
    const context=await browser.newContext({offline:true});
    const page=await context.newPage();
    page.on('request',r=>{if(/^https?:/.test(r.url()))external.push(r.url())});
    page.on('pageerror',e=>errors.push(e.message));
    await page.goto(pathToFileURL(path.resolve('artifacts/lambda-world-contingency.html')).href);
    await page.evaluate(()=>document.fonts.ready);
    if(await page.locator('section[id]').count()!==9)throw Error('Missing demo contingency');
    for(const viewport of [{width:1024,height:768},{width:1920,height:1080}]){
      await page.setViewportSize(viewport);
      if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Contingency page has horizontal overflow');
    }
    const cdp=await context.newCDPSession(page);
    await cdp.send('DOM.enable');await cdp.send('CSS.enable');
    const {root}=await cdp.send('DOM.getDocument',{depth:-1});
    const nodes=[],fonts=[];
    function visit(node){
      if(node.nodeType===1&&!['STYLE','SCRIPT','TITLE'].includes(node.nodeName)&&node.children?.some(c=>c.nodeType===3&&c.nodeValue.trim()))nodes.push(node);
      for(const child of node.children||[])visit(child);
    }
    visit(root);
    for(const node of nodes){fonts.push(...(await cdp.send('CSS.getPlatformFontsForNode',{nodeId:node.nodeId})).fonts)}
    if(!fonts.length||fonts.some(f=>!f.isCustomFont))throw Error('Fallback page uses a system font');
    await page.emulateMedia({media:'print'});await page.evaluate(()=>document.fonts.ready);
    await page.pdf({path:'artifacts/lambda-world-contingency.pdf',format:'A4',printBackground:true});
    const pdfs={};
    for(const file of ['lambda-world-contingency.pdf','lambda-world-speaker-script.pdf']){
      const report=execFileSync('pdffonts',['artifacts/'+file],{encoding:'utf8'});
      const rows=report.trim().split('\n').slice(2);
      if(rows.some(row=>!/^\S+\+(DejaVu|Rescue)/.test(row)))throw Error('Unexpected PDF font: '+file+'\n'+report);
      if(!rows.length||rows.some(row=>!row.match(/\s+yes\s+(?:yes|no)\s+(?:yes|no)\s+\d+\s+\d+\s*$/)))throw Error('Unembedded PDF font: '+file+'\n'+report);
      pdfs[file]=report;
    }
    if(external.length||errors.length)throw Error(JSON.stringify({external,errors}));
    fs.writeFileSync('artifacts/hostile-room/contingency.json',JSON.stringify({passed:true,openedAsLocalFileWithBrowserOffline:true,demos:9,externalRequests:external,fonts,pdfFonts:pdfs},null,2));
    console.log('PASS: all 9 contingencies open directly from disk with networking off; both PDFs contain embedded fonts only.');
  }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
