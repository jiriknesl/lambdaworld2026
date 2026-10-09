/* The browser only draws Clojure results. No optimizer, Bellman backup, or
   Shapley calculation is reimplemented here. Geometry and interpolation are UI. */
'use strict';
const $ = id => document.getElementById(id);
const canvas = $('universe'), ctx = canvas.getContext('2d');
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
const colors = ['#b9e8cc','#e3bb85','#b2d1ef'];
const anchors = [-1600, 0, 1600];
let W=innerWidth,H=innerHeight,DPR=1,scenes=[],token='',index=0,entered=false;
let busy=false,playing=false,cursor=0,lastTime=0,codeVisible=true;
let sceneVersion=0,toastTimer,visuals={},cache={},drafts={},sceneLoaded=false;
let replayMode=false,recordedResults={},pane='explain',selectedBoat=0,inspectionKey='';
const camera={x:-1600,z:0,distance:2100,tilt:1.0,center:.76};
const target={...camera};

function fract(n){return n-Math.floor(n)}
function clamp(n,a,b){return Math.max(a,Math.min(b,n))}
function lerp(a,b,t){return a+(b-a)*t}
function alpha(hex,a){const opacity=hex.length===9?parseInt(hex.slice(7),16)/255:1;return hex.slice(0,7)+Math.round(clamp(a*opacity,0,1)*255).toString(16).padStart(2,'0')}
function resize(){W=innerWidth;H=innerHeight;DPR=Math.min(devicePixelRatio||1,2);canvas.width=W*DPR;canvas.height=H*DPR;ctx.setTransform(DPR,0,0,DPR,0,0);updateTarget()}
addEventListener('resize',resize);

function project(x,y,z){
  const dx=x-camera.x,dz=z-camera.z,st=Math.sin(camera.tilt),ct=Math.cos(camera.tilt);
  const depth=Math.max(80,camera.distance-y*st-dz*ct);
  const scale=Math.min(W*.85,1150)/depth;
  return {x:W*camera.center+dx*scale,y:H*.54+(-y*ct+dz*st)*scale,s:scale,depth};
}
function line(points,color,width=1,dash=[]){
  ctx.beginPath();points.forEach((v,i)=>{const p=project(...v);i?ctx.lineTo(p.x,p.y):ctx.moveTo(p.x,p.y)});
  ctx.strokeStyle=color;ctx.lineWidth=width;ctx.setLineDash(dash);ctx.stroke();ctx.setLineDash([]);
}
function polygon(points,fill,stroke){ctx.beginPath();points.forEach((v,i)=>{const p=project(...v);i?ctx.lineTo(p.x,p.y):ctx.moveTo(p.x,p.y)});ctx.closePath();if(fill){ctx.fillStyle=fill;ctx.fill()}if(stroke){ctx.strokeStyle=stroke;ctx.lineWidth=.7;ctx.stroke()}}
function ring(x,y,z,r,color,width=1){line(Array.from({length:81},(_,i)=>{const a=i/80*Math.PI*2;return [x+Math.cos(a)*r,y,z+Math.sin(a)*r]}),color,width)}
function label(text,x,y,z,color='#91a7b2',size=11,align='center'){
  const p=project(x,y,z);if(p.x<-300||p.x>W+300)return;
  ctx.font=`${Math.max(7,Math.min(22,size*p.s))}px "Rescue Mono","Rescue Sans","Rescue Symbols"`;
  ctx.textAlign=align;ctx.textBaseline='middle';ctx.fillStyle=color;ctx.fillText(text,p.x,p.y);
}
function glow(x,y,r,color){const g=ctx.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,color);g.addColorStop(1,'transparent');ctx.fillStyle=g;ctx.fillRect(x-r,y-r,r*2,r*2)}
function dot(x,y,z,r,color){const p=project(x,y,z);glow(p.x,p.y,r*p.s*4,alpha(color,.14));ctx.fillStyle=color;ctx.beginPath();ctx.arc(p.x,p.y,Math.max(1,r*p.s),0,Math.PI*2);ctx.fill()}

function worldBase(time){
  oceanSurface(time);
  currentArrows(anchors[0],time);currentArrows(anchors[1],time);
  anchors.forEach((x,i)=>{
    ring(x,-8,0,420,alpha(colors[i],.11));ring(x,-8,0,430,alpha(colors[i],.035));
    for(let a=0;a<12;a++){const angle=a/12*Math.PI*2;line([[x+Math.cos(angle)*422,-8,Math.sin(angle)*422],[x+Math.cos(angle)*432,-8,Math.sin(angle)*432]],alpha(colors[i],.25))}
    label(['01  /  SEARCH','02  /  DECIDE','03  /  EXPLAIN'][i],x,0,-420,alpha(colors[i],.75),16);
  });
  for(let i=0;i<2;i++){
    const path=Array.from({length:61},(_,j)=>{const t=j/60;return [lerp(anchors[i]+430,anchors[i+1]-430,t),Math.sin(t*Math.PI)*110,0]});
    line(path,'#b9e8cc27',1);
    for(let n=0;n<3;n++){const t=(time*.000045+n/3)%1;dot(lerp(anchors[i]+430,anchors[i+1]-430,t),Math.sin(t*Math.PI)*110,0,2,colors[i])}
    label(i===0?'same iterate':'functions as inputs',lerp(anchors[i],anchors[i+1],.5),140,0,'#b3c9bd',17);
  }
  if(entered&&scenes[index]?.station===3){
    ring(0,-30,0,2450,'#b9e8cc0b');
    label('FUNCTIONS THAT ACCEPT FUNCTIONS. FUNCTIONS THAT RETURN FUNCTIONS.',0,0,760,'#b9e8cc88',30);
    label('Applied Higher Order Functions  /  Jiri Knesl',0,0,960,'#dfb884bb',25);
  }
}

function flockPoint(p){return [anchors[0]+(p[0]-3)*82,26,(p[1]-1.8)*82]}
function drawFlock(data,time){
  if(!data?.frames?.length)return;
  const active=entered&&scenes[index]?.station===0;
  const t=!entered?0:active?clamp(cursor,0,data.frames.length-1):data.frames.length-1;
  const fi=Math.floor(t),fraction=t-fi,current=data.frames[fi],next=data.frames[Math.min(fi+1,data.frames.length-1)];
  const tx=flockPoint(data.target);
  rescueBeacon(tx[0],tx[2],time);
  line([[tx[0]-12,0,tx[2]],[tx[0]+12,0,tx[2]]],'#e3bb85aa');line([[tx[0],0,tx[2]-12],[tx[0],0,tx[2]+12]],'#e3bb85aa');
  label('LAST REPORT',tx[0],0,tx[2]+61,'#dfb884',10);
  for(let i=0;i<current.positions.length;i++){
    const color=i%3===0?'#e4bd87':'#bee9cf';
    if(active&&scenes[index].id==='constraints'){const pt=flockPoint(current.positions[i]);ring(pt[0],0,pt[2],1.1*82/2,alpha(color,.28),1,[4,4]);}
    if(fi>0){const trace=[];for(let n=0;n<=fi;n+=Math.max(1,Math.floor(fi/60)))trace.push(flockPoint(data.frames[n].positions[i]));line(trace,alpha(color,.23),1)}
    const pt=current.positions[i].map((v,j)=>lerp(v,next.positions[i][j],fraction));
    const prev=data.frames[Math.max(0,fi-1)].positions[i],dx=pt[0]-prev[0],dy=pt[1]-prev[1];
    const angle=Math.abs(dx)+Math.abs(dy)>.0001?Math.atan2(dy,dx)+Math.PI/2:Math.PI*.2;
    const wp=flockPoint(pt);boat(...wp,angle,color);
    if(active&&pane==='inspect'&&i===selectedBoat){ring(wp[0],0,wp[2],23,'#ffe0a7',2);label('INSPECT',wp[0],55,wp[2],'#ffe0a7',11);}
    if(active&&(fi<3||stageMode))label(String(i+1).padStart(2,'0'),wp[0],wp[1]+27,wp[2],'#8caaa8',9);
  }
  label(`OBJECTIVE  ${current.value.toFixed(3)}`,anchors[0],0,280,'#9ab6b5',13);
  label(`${current.positions.length} BOATS     ${fi} UPDATES`,anchors[0],0,312,'#587986',10);
  if(active&&data.execution){const e=data.execution;
    label(`map  ${e['serial-ms'].toFixed(1)} ms    pmap  ${e['parallel-ms'].toFixed(1)} ms`,anchors[0],0,-260,'#d9bc91',16);
    label(`${e['population-size']} CANDIDATES  ·  IDENTICAL RESULTS: ${e['equal?']?'YES':'NO'}`,anchors[0],0,-220,'#9ab6b5',11);
  }
}

function drawBellman(data){
  if(!data?.frames?.length)return;
  const active=entered&&scenes[index]?.station===1;
  const fi=active?Math.floor(clamp(cursor,0,data.frames.length-1)):data.frames.length-1;
  const frame=data.frames[fi],size=76;
  const stateMap=new Map(data.states.map((p,i)=>[p.join(','),i]));
  for(let gy=0;gy<data.height;gy++)for(let gx=0;gx<data.width;gx++){
    const x=anchors[1]+(gx-3)*size,z=(gy-2)*size,idx=stateMap.get(`${gx},${gy}`);
    const wall=idx===undefined,goal=data.goal[0]===gx&&data.goal[1]===gy,trap=data.trap[0]===gx&&data.trap[1]===gy;
    const val=wall?0:frame.values[idx],elev=wall?28:Math.max(0,val)*5.5;
    const corners=[[x-34,elev,z-34],[x+34,elev,z-34],[x+34,elev,z+34],[x-34,elev,z+34]];
    if(elev>1){polygon([corners[2],corners[3],[x-34,0,z+34],[x+34,0,z+34]],wall?'#1b2830':'#153631','#6aab8a15');polygon([corners[1],corners[2],[x+34,0,z+34],[x+34,0,z-34]],wall?'#152028':'#112a29')}
    const strength=clamp(val/10,0,1),fill=wall?'#25323a':trap?'#57332f':goal?'#d4b485':`rgb(${Math.round(18+strength*30)},${Math.round(35+strength*94)},${Math.round(40+strength*56)})`;
    polygon(corners,fill,active&&gx===5&&gy===0?'#ffe0a7':goal?'#ffe1ab99':'#b9e8cc20');
    if(wall){line([[x-16,elev+.1,z-16],[x+16,elev+.1,z+16]],'#71859344');line([[x+16,elev+.1,z-16],[x-16,elev+.1,z+16]],'#71859344');continue}
    if(goal||trap){label(goal?'✦':'×',x,elev+3,z,goal?'#162722':'#edb1a0',27);label(goal?'+10':'−6',x,elev+3,z+20,goal?'#263930':'#d7a292',10)}
    else{
      label(val.toFixed(1),x,elev+2,z-10,'#cce2d3',14);
      const a=frame.policy[idx];if(a){const dir=[a[0]*12,a[1]*12];const p=project(x,elev+3,z+13),q=project(x+dir[0],elev+3,z+13+dir[1]);ctx.strokeStyle='#b9e8cc88';ctx.lineWidth=1.2;ctx.beginPath();ctx.moveTo(p.x,p.y);ctx.lineTo(q.x,q.y);ctx.stroke();const angle=Math.atan2(q.y-p.y,q.x-p.x);ctx.beginPath();ctx.moveTo(q.x-4*Math.cos(angle-.55),q.y-4*Math.sin(angle-.55));ctx.lineTo(q.x,q.y);ctx.lineTo(q.x-4*Math.cos(angle+.55),q.y-4*Math.sin(angle+.55));ctx.stroke()}
    }
  }
  label(`V${String(fi).padStart(2,'0')} : STATE → EXPECTED RETURN`,0,0,285,'#b3c9bd',13);
  label('HEIGHT = VALUE   /   ARROW = GREEDY ACTION',0,0,322,'#587986',10);
}

function drawShapley(data,time){
  if(!data?.orders?.length)return;
  const active=entered&&scenes[index]?.station===2;
  const t=active?clamp(Math.floor(cursor),0,data.orders.length*4-1):data.orders.length*4-1;
  const oi=Math.floor(t/4),arrival=t%4,order=data.orders[oi],state=order.trace[arrival];
  if(active)coverageMap(data);
  const x=anchors[2],locs=[[-215,40],[0,-65],[215,40]],joined=new Set(state.coalition);
  for(let i=0;i<data.players.length;i++){
    const player=data.players[i],[dx,z]=locs[i],on=joined.has(player),color=colors[i];
    ring(x+dx,0,z,43,alpha(color,on?.6:.17),1);ring(x+dx,0,z,49,alpha(color,.08));
    dot(x+dx,28,z,on?8:5,alpha(color,on?1:.35));
    if(on)line([[x+dx,28,z],[x,150,-20]],alpha(color,.3),1);
    label(player.toUpperCase(),x+dx,0,z+73,on?color:'#698593',11);
    if(on)label(`+${Number(state.credits[player].toFixed(2))}`,x+dx,0,z+102,color,14);
  }
  dot(x,160,-20,12,'#e5edda');label(Number(state.value.toFixed(2)).toString(),x,205,-20,'#edf2e6',31);
  label('COALITION VALUE',x,235,-20,'#9aadb4',10);
  for(let i=0;i<data.players.length;i++){
    const dx=(i-1)*175,credit=data.credits[data.players[i]],height=credit*3;
    polygon([[x+dx-31,0,235],[x+dx+31,0,235],[x+dx+31,height,235],[x+dx-31,height,235]],alpha(colors[i],.32),alpha(colors[i],.6));
    label(String(Number(credit.toFixed(2))),x+dx,height+20,235,colors[i],20);
    label(data.players[i].toUpperCase(),x+dx,0,273,'#8fa7b0',9);
  }
  label(`ORDER ${oi+1} / ${data.orders.length}   ·   ${arrival} / 3 ARRIVALS`,x,0,-395,'#91aab6',12);
  label('CREDIT = AVERAGE MARGINAL COVERAGE',x,0,337,'#b1c7bd',11);
  label(`COVERAGE  ${fmt(data.total-data.baseline)} pp`,x,0,372,'#587986',10);
}

function updateTarget(){
  if(!entered){Object.assign(target,{x:-1600,z:0,distance:1800,tilt:1.02,center:.76});return}
  const station=scenes[index]?.station??0,overview=station===3;
  const stageDistance=Math.max(1250,1450*982/Math.max(H,650));
  Object.assign(target,{x:overview?0:anchors[station],z:overview?0:0,distance:overview?(stageMode?5800:4400):(W<700?850:stageDistance),tilt:overview?.96:1.02,center:overview&&!stageMode?.5:((stageMode||codeVisible)&&W>=700?.295:.5)});
}
function visualKey(scene){return scene.id==='pomdp'?'belief':scene.id==='rainbow'?'rainbow':scene.station}
function currentVisual(){return visuals[visualKey(scenes[index]||{})]}
function maxFrame(){const d=currentVisual();return d?.kind==='shapley'?d.orders.length*4-1:Math.max(0,(d?.frames?.length||1)-1)}
function updateTransport(){const max=maxFrame();$('scrub').max=max;$('scrub').value=cursor;$('play').textContent=playing?'Ⅱ':'▶';$('play').setAttribute('aria-label',playing?'Pause computed trajectory':'Play computed trajectory');$('frame-label').textContent=(currentVisual()?.kind==='shapley'?'ARRIVAL ':'STEP ')+String(Math.floor(cursor)).padStart(3,'0')}
function animate(time){
  const dt=Math.min((time-lastTime)/1000||0,.08);lastTime=time;
  const ease=reducedMotion?1:1-Math.exp(-dt*3.3);
  for(const key of Object.keys(camera))camera[key]=lerp(camera[key],target[key],ease);
  if(playing&&entered&&!busy){const rate=['shapley','belief','rainbow'].includes(currentVisual()?.kind)?0.65:currentVisual()?.kind==='bellman'?3:13;cursor=Math.min(maxFrame(),cursor+dt*rate);if(cursor>=maxFrame())playing=false;updateTransport()}
  worldBase(time);drawFlock(visuals[0],time);
  if(entered&&scenes[index]?.id==='pomdp')drawBelief(visuals.belief);
  else if(entered&&scenes[index]?.id==='rainbow')drawRainbow(visuals.rainbow);
  else drawBellman(visuals[1]);
  drawShapley(visuals[2],time);updateInspection();stageTick(time);renderTrace();mapCompass();
  const vignette=ctx.createRadialGradient(W*.5,H*.5,W*.25,W*.5,H*.5,W*.78);vignette.addColorStop(0,'transparent');vignette.addColorStop(1,'#02050b88');ctx.fillStyle=vignette;ctx.fillRect(0,0,W,H);
  requestAnimationFrame(animate);
}

function escapeHTML(s){return s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;')}
function highlight(){
  const code=$('editor').value;
  const tokens=/(;[^\n]*|"(?:[^"\\]|\\.)*"|\b(?:defn|def|fn|let|if|map|mapv|reduce|reductions|iterate|take|apply|comp|partial|constantly)\b|:[\w?-]+|(?<![\w.-])-?\d+(?:\.\d+)?\b)/g;
  let last=0,html='',match;
  while((match=tokens.exec(code))){html+=escapeHTML(code.slice(last,match.index));const t=match[0],cls=t[0]===';'?'comment':t[0]==='"'?'string':t[0]===':'?'keyword':/^-?\d/.test(t)?'number':'symbol';html+=`<span class="${cls}">${escapeHTML(t)}</span>`;last=tokens.lastIndex}
  $('highlight').innerHTML=html+escapeHTML(code.slice(last))+'\n';syncScroll();
}
function syncScroll(){$('highlight').scrollTop=$('editor').scrollTop;$('highlight').scrollLeft=$('editor').scrollLeft}
function toast(message){$('toast').textContent=message;$('toast').classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').classList.remove('visible'),3500)}
async function execute(code){
  if(replayMode){
    for(const scene of scenes){
      if(scene.code===code||scene['expanded-code']===code)return recordedResults[scene.id];
      const variant=scene.experiments?.find(v=>v.code===code);
      if(variant)return recordedExperiments[scene.id+'/'+variant.id];
    }
    throw Error('Code editing is available in the local JVM presentation.');
  }
  const response=await fetch('/api/eval',{method:'POST',headers:{'Content-Type':'text/plain;charset=utf-8','X-Presentation-Token':token},body:code});if(!response.ok)throw Error(`Server returned ${response.status}`);return response.json()
}
async function runCell({autoplay=true}={}){
  if(busy)return;busy=true;playing=false;const version=sceneVersion;const id=scenes[index].id;
  $('run').disabled=true;$('eval-status').classList.remove('error');$('eval-status').textContent=replayMode?'Loading computed result…':'Evaluating on JVM…';
  try{
    const code=$('editor').value;drafts[id]=code;const result=await execute(code);
    if(version!==sceneVersion)return;
    $('output').textContent=(result.stdout||'')+(result.ok?result.printed:result.error);
    $('result-label').textContent=result.ok?'REPL result':'Evaluation error';
    if(!result.ok){setPane('code');$('eval-status').classList.add('error');$('eval-status').textContent='Cell failed';$('result').open=true;toast('Clojure rejected the cell. The previous visualization is preserved.');return}
    $('eval-status').textContent=replayMode?'Recorded JVM result':`Evaluated · ${result['elapsed-ms'].toFixed(1)} ms`;
    $('result').open=false;
    if(result.visual){const station={flock:0,bellman:1,shapley:2,belief:'belief',rainbow:'rainbow'}[result.visual.kind];visuals[station]=result.visual;if(code===scenes[index].code)cache[id]=result;traceKey='';cursor=0;playing=autoplay&&!reducedMotion;inspectionKey='';updateTransport()}
    else{$('result').open=true;if(scenes[index].station!==3)toast('Cell returned a value. Inspect it in the REPL result.')}
  }catch(error){$('eval-status').textContent='Connection failed';$('eval-status').classList.add('error');$('output').textContent=error.message;$('result').open=true;toast('Start clojure -M:present, then reload this page.')}
  finally{busy=false;$('run').disabled=false;renderExperiments()}
}
function setCodeVisible(value){codeVisible=value;document.body.classList.toggle('code-hidden',!value);$('code-toggle').setAttribute('aria-pressed',String(value));updateTarget()}
async function go(next){
  if(!entered||busy)return;
  if(sceneLoaded)drafts[scenes[index].id]=$('editor').value;
  sceneLoaded=true;index=clamp(next,0,scenes.length-1);sceneVersion++;
  const scene=scenes[index];playing=false;cursor=0;inspectionKey='';renderLesson(scene.lesson);setPane('explain');
  document.body.classList.toggle('overview',scene.station===3);
  $('chapter').textContent=scene.chapter;$('title').textContent=scene.title;$('subtitle').textContent=scene.subtitle;
  $('signature').textContent=scene.signature;$('note').textContent=scene.note;$('filename').textContent=scene.file;
  $('unfold').hidden=!scene['expanded-code'];
  $('editor').value=drafts[scene.id]??scene.code;$('editor').scrollTop=0;highlight();$('result').open=false;
  $('page').textContent=`${String(index+1).padStart(2,'0')} / ${String(scenes.length).padStart(2,'0')}`;
  [...$('chapters').children].forEach((el,i)=>{el.classList.toggle('active',i===index);if(i===index)el.setAttribute('aria-current','step');else el.removeAttribute('aria-current')});
  $('previous').disabled=index===0;$('next').disabled=index===scenes.length-1;
  setCodeVisible(scene.station!==3&&W>=700);updateTarget();updateTransport();
  if(cache[scene.id]&&drafts[scene.id]===scene.code){const result=cache[scene.id];if(result.visual)visuals[visualKey(scene)]=result.visual;$('eval-status').textContent='Computed on JVM · ready';$('output').textContent=result.printed;updateTransport()}
  else await runCell({autoplay:false});
  renderExperiments();stageSceneChanged();location.hash=scene.id;
}
function enter(){if(!scenes.length||entered)return;entered=true;$('intro').classList.add('exiting');$('presentation').hidden=false;const hash=location.hash.slice(1),at=scenes.findIndex(s=>s.id===hash);go(at>=0?at:0);setTimeout(()=>$('intro').hidden=true,700)}
function playPause(){if(busy||scenes[index]?.station===3)return;if(cursor>=maxFrame())cursor=0;playing=!playing;updateTransport()}
function fullscreen(){if(document.fullscreenElement)document.exitFullscreen();else document.documentElement.requestFullscreen().catch(()=>toast('Fullscreen is unavailable in this browser.'))}
$('enter').onclick=enter;$('run').onclick=()=>runCell();$('next').onclick=()=>go(index+1);$('previous').onclick=()=>go(index-1);$('play').onclick=playPause;
$('scrub').oninput=()=>{playing=false;cursor=Number($('scrub').value);updateTransport()};
$('code-toggle').onclick=()=>setCodeVisible(!codeVisible);$('fullscreen').onclick=fullscreen;
$('reset').onclick=()=>{$('editor').value=scenes[index].code;drafts[scenes[index].id]=scenes[index].code;highlight();renderExperiments();toast('Original cell restored. Run it to update the visualization.')};
$('unfold').onclick=()=>{const code=scenes[index]['expanded-code'];if(code){$('editor').value=code;$('editor').scrollTop=0;highlight();toast('The HOF is now defined in this cell. Edit it, then run.')}};
$('editor').oninput=()=>{highlight();renderExperiments()};$('editor').onscroll=syncScroll;
$('editor').onkeydown=e=>{if(e.key==='Tab'){e.preventDefault();const el=e.target,start=el.selectionStart,end=el.selectionEnd;el.setRangeText('  ',start,end,'end');highlight()}};
$('help-toggle').onclick=()=>$('help').showModal();$('close-help').onclick=()=>$('help').close();
$('home').onclick=e=>{e.preventDefault();if(entered)go(scenes.length-1)};
document.addEventListener('keydown',e=>{
  const editing=document.activeElement===$('editor')&&!replayMode;
  if((e.metaKey||e.ctrlKey)&&e.key==='Enter'){if(entered){e.preventDefault();runCell()}return}
  if(e.key==='Escape'&&editing){$('editor').blur();return}
  if(editing||$('help').open||e.target.matches('input,select'))return;
  if(e.key==='ArrowRight'&&entered){e.preventDefault();go(index+1)}
  if(e.key==='ArrowLeft'&&entered){e.preventDefault();go(index-1)}
  if(e.code==='Space'&&entered){e.preventDefault();playPause()}
  if(e.key.toLowerCase()==='c'&&entered)setCodeVisible(!codeVisible);
  if(e.key.toLowerCase()==='f')fullscreen();
  if(e.key==='?')$('help').showModal();
  if(e.key==='Enter'&&!entered)enter();
});
async function init(){
  await Promise.all([document.fonts.load('16px "Rescue Sans"'),document.fonts.load('16px "Rescue Mono"'),document.fonts.load('16px "Rescue Symbols"')]);
  try{
    const build=document.querySelector('meta[name="presentation-build"]')?.content||'local';
    const response=await fetch('/api/scenes?v='+encodeURIComponent(build),{cache:'no-store'});if(!response.ok)throw Error('Cannot load the presentation');const data=await response.json();scenes=data.scenes;token=data.token;replayMode=data.mode==='replay';recordedResults=data.results||{};recordedExperiments=data.experiments||{};
    if(replayMode){
      $('editor').readOnly=true;$('editor').setAttribute('aria-label','Clojure source code (read only in shared preview)');
      document.querySelector('.bench-top>span').innerHTML='<i class="live-dot"></i> MISSION NOTEBOOK';
      document.querySelector('.runtime').textContent='RECORDED JVM RESULTS';
      $('run').innerHTML='Replay result <kbd>⌘ ↵</kbd>';
      document.querySelector('.intro-foot').textContent='AN INTERACTIVE JOURNEY THROUGH HIGHER-ORDER FUNCTIONS';
      $('unfold').title='Reveal the higher-order function definition';
      $('help').querySelector('p:last-of-type').textContent='This shared preview replays results computed by Clojure. Code editing and live evaluation are available in the local presentation.';
    }
    for(let i=0;i<scenes.length;i++){const b=document.createElement('button');b.textContent=({flock:'FLEET',constraints:'WRAP',momentum:'COMPOSE',parallel:'PARALLEL',bellman:'BELLMAN',pomdp:'BELIEF',rainbow:'RAINBOW',shapley:'SHAPLEY',overview:'REVEAL'})[scenes[i].id];b.title=scenes[i].title;b.onclick=()=>go(i);$('chapters').appendChild(b)}
    // Compute the three overview stations through the same JVM used by the editor.
    for(const scene of scenes.filter(s=>s.id!=='overview'&&s.id!=='parallel')){const result=await execute(scene.code);if(!result.ok)throw Error(result.error);cache[scene.id]=result;if(result.visual)visuals[visualKey(scene)]=result.visual}
    $('connection').textContent=replayMode?'INTERACTIVE PREVIEW · JVM RESULTS':'CLOJURE CONNECTED · OFFLINE READY';$('enter').disabled=false;$('enter-stage').disabled=false;$('enter').textContent='Enter the computation  ↗';
    if(location.hash)enter();
  }catch(error){$('connection').textContent='CLOJURE DISCONNECTED';$('enter').textContent='Reload after starting the server';$('enter').disabled=false;$('enter').onclick=()=>location.reload();toast(error.message)}
}
function fmt(x){return x===null||x===undefined?'—':typeof x==='number'?Number(x.toFixed(4)).toString():String(x)}
function vec(xs){return xs?'['+xs.map(fmt).join(', ')+']':'—'}
function safe(x){return escapeHTML(String(x??'—'))}
function table(head,rows){return `<table><thead><tr>${head.map(h=>`<th>${safe(h)}</th>`).join('')}</tr></thead><tbody>${rows.map(row=>`<tr>${row.map(v=>`<td>${safe(v)}</td>`).join('')}</tr>`).join('')}</tbody></table>`}
function setPane(name){pane=name;for(const p of ['explain','inspect','code']){$('pane-'+p).hidden=p!==name;$('tab-'+p).setAttribute('aria-selected',String(p===name))}inspectionKey='';updateInspection()}
for(const name of ['explain','inspect','code'])$('tab-'+name).onclick=()=>setPane(name);
function renderLesson(lesson){
  $('pane-explain').innerHTML=lesson?`<div class="eyebrow">THE RESCUE / THE MECHANISM</div><h2>${safe(lesson.question)}</h2><p>${safe(lesson.answer)}</p><ol class="lesson-steps">${lesson.steps.map(s=>`<li><h3>${safe(s.title)}</h3><p>${safe(s.body)}</p><code>${safe(s.code)}</code></li>`).join('')}</ol><div class="watch"><strong>Watch for this</strong><p>${safe(lesson.watch)}</p></div><p class="insight">${safe(lesson.insight)}</p>`:'';
  $('pane-explain').scrollTop=0;
}
function stepBy(delta){playing=false;cursor=clamp(Math.floor(cursor)+delta,0,maxFrame());updateTransport();updateInspection()}
$('step-back').onclick=()=>stepBy(-1);$('step-next').onclick=()=>stepBy(1);
function updateInspection(){
  if(!entered||pane!=='inspect')return;
  const data=currentVisual();if(!data)return;
  const n=Math.floor(cursor),key=`${index}:${n}:${selectedBoat}:${sceneVersion}`;if(key===inspectionKey)return;inspectionKey=key;
  const frame=data.frames?.[n],a=frame?.inspection;let html=`<div class="eyebrow">ACTUAL CLOJURE VALUES / ${n}</div>`;
  if(data.kind==='flock'&&a){
    const pair=xs=>xs?xs.slice(selectedBoat*2,selectedBoat*2+2):null;
    html+=`<h2>One boat. One update.</h2><label class="boat-selector">Inspect boat <select id="boat-choice">${frame.positions.map((_,i)=>`<option value="${i}" ${i===selectedBoat?'selected':''}>${i+1}</option>`).join('')}</select></label>`;
    html+=table(['Quantity','[x, y]'],[['Position now',vec(pair(a.x))],['Gradient here',vec(pair(a.gradient))],['Base movement −rate × gradient',vec(pair(a['base-delta']))],['Previous velocity',vec(pair(a.velocity))],['Carried movement β × velocity',vec(pair(a.carry))],['Position next',vec(pair(a['next-x']))]]);
    html+=`<code class="formula">next = position + base movement + carry</code><p>Rate ${fmt(a.rate)} · β ${fmt(a.beta??0)}. ${a['next-x']?'Use + to advance exactly one update.':'Final recorded formation; no next update is stored.'}</p>`;
    html+=table(['Objective component','Value'],[['Distance to report',fmt(a['roost-cost'])],['Weighted crowding penalty',fmt(a['penalty-cost'])],['Total cost',fmt(frame.value)],['Closest pair distance',fmt(a['closest-distance'])]]);
    html+='<p>These are optimization iterations over a candidate formation. They are not vessel dynamics.</p>';
    if(data.execution){const e=data.execution;html+='<h3>Independent population evaluation</h3>'+table(['Measurement','Result'],[['Candidates',e['population-size']],['map / ms',fmt(e['serial-ms'])],['pmap / ms',fmt(e['parallel-ms'])],['Identical results',e['equal?']]])}
  }else if(data.kind==='bellman'&&a){
    html+=`<h2>Backup at ${vec(a.state)}</h2><p>Every action reads the same old V. Current value: <strong>${fmt(a.before)}</strong>.</p>`;
    html+=table(['Action [dx,dy]','Expected return'],a.actions.map(v=>[vec(v.action),fmt(v.score)]));
    html+=`<h3>Outcomes of the best action ${vec(a.chosen)}</h3>`+table(['Next cell','p','Reward','Old V','p × (r + γV)'],a.terms.map(t=>[vec(t.next),fmt(t.prob),fmt(t.reward),fmt(t.value),fmt(t.weighted)]));
    html+=`<p class="insight">Sum the last column: ${fmt(a.after)}. That is the new V([5,0]).</p><p>γ = ${a.discount}. The gold outline ties this calculation to the map. This is a route to a fixed confirmed location; drifting uncertainty enters in the next chapter.</p>`;
  }else if(data.kind==='belief'&&a){
    html+=`<h2>${a.event?`${safe(a.event.action)} search → ${safe(a.event.observation)}`:'Before the first search'}</h2><p>${a.event?'Predict drift, multiply by the observation likelihood, then divide by its total probability.':'The last report supplies the initial belief.'}</p>`;
    html+=table(['Normalization','Value'],[['P(observation)',fmt(a.evidence)],['Posterior total',fmt(a.mass)]]);
    html+=table(['Cell','Predicted','Likelihood','Posterior'],a.cells.filter(c=>c.predicted>0||c.posterior>0).map(c=>[vec(c.state),fmt(c.predicted),fmt(c.likelihood),fmt(c.posterior)]));
    html+=a.terminal?'<p class="insight">Sighting: this toy mission terminates. The conditional location distribution is shown, but no further action is planned.</p>':'<h3 id="decision-plan">Plan the next two searches</h3>'+table(['First sector','P(success within 2 searches)'],a.actions.map(v=>[v.action,fmt(v.score)]))+
      `<p>Optimal first sectors: ${safe(a.plan['best-actions'].join(', '))}. Each miss posterior is passed to V₁, a one-search value function. Drift is predicted again before the second search.</p>`+
      a.actions.map(row=>`<details class="proof" ${row.action===a.plan['best-actions'][0]?'open':''}><summary>First ${safe(row.action)} · on a miss: ${safe(row['second-best'].join(' or '))}</summary>`+
        table(['Backup term','Probability'],[['Immediate success',fmt(row.seen)],['First search misses',fmt(row.miss)],['Best continuation V₁(b′)',fmt(row.continuation)],['Miss × continuation',fmt(row['weighted-continuation'])],['Total success Q(b,a)',fmt(row.score)]])+
        table(['Second sector, given first miss','P(second search succeeds)'],row['second-actions'].map(next=>[next.action,fmt(next.score)]))+'</details>').join('')+
      '<code class="formula">(action-backup one-search-value belief action)</code><p>Success terminates. A miss supplies a new belief to the continuation function. Displayed decimals are rounded; the Clojure model uses exact ratios. Equal optima are retained. These hypothetical plans are separate from the scripted observations.</p>';
  }else if(data.kind==='shapley'){
    const order=data.orders[Math.floor(n/4)],arrival=n%4,state=order.trace[arrival],previous=order.trace[Math.max(0,arrival-1)];
    html+=`<h2>Order ${Math.floor(n/4)+1} of ${data.orders.length}</h2><code class="formula">${safe(order.order.join(' → '))}</code>`;
    html+=table(['Fold state','Value'],[['Coalition before',previous.coalition.join(', ')||'empty'],['Arriving boat',state.player??'none yet'],['Coverage before / pp',fmt(previous.value)],['Coverage after / pp',fmt(state.value)],['Marginal gain / pp',fmt(state.gain??0)]]);
    html+='<h3>Six orders → one average per boat</h3>'+table(['Boat','Six marginal gains','Mean / pp'],data.players.map(p=>[p,data.samples[p].map(fmt).join(', '),fmt(data.credits[p])]))+`<p class="insight">Total attributed: ${fmt(data.total-data.baseline)} percentage points of expected detection coverage.</p><p>The underlying belief comes from the previous chapter, after two misses and one additional drift prediction. Coalition comparisons use the same frozen belief.</p>`;
  }else if(data.kind==='rainbow'){
    const e=data.evidence;
    html+=`<h2>${safe(frame.title)}</h2><code class="formula">${safe(frame.formula)}</code><p>${safe(frame.detail)}</p><p class="insight">${frame.rejected?'Wrong proof: '+safe(e['negative-check'].status):'Recorded kernel acceptance · empty axiom closures'}</p><h3>${e.checks.length} declarations rechecked</h3>`;
    html+=e.checks.map(c=>`<details class="proof"><summary>${safe(c.theorem)}</summary><p>Status: ${safe(c.status)} · axioms: ${c.axioms.length}</p><code>${safe(c.id)}</code></details>`).join('');
    html+=`<p>Checked ${safe(e['checked-at'])}<br>Rainbow commit <code>${safe(e['rainbow-commit'])}</code></p><code class="formula">clojure -M:rainbow</code><p>${safe(e.scope)}</p><p>Shapley: the checked weighted-attribution identity is narrower than our permutation algorithm. POMDP: the new belief update and planner are not proved here.</p>`;
  }else html+='<p>Choose a computation chapter to inspect its values.</p>';
  $('pane-inspect').innerHTML=html;
  if($('boat-choice'))$('boat-choice').onchange=e=>{selectedBoat=Number(e.target.value);inspectionKey='';updateInspection()};
}
function boat(x,y,z,angle,color){
  const p=project(x,y,z),size=clamp(21*p.s,6,26);ctx.save();ctx.translate(p.x,p.y);ctx.rotate(angle);
  ctx.fillStyle=color;ctx.beginPath();ctx.moveTo(0,-size);ctx.lineTo(size*.45,-size*.25);ctx.lineTo(size*.4,size*.8);ctx.lineTo(-size*.4,size*.8);ctx.lineTo(-size*.45,-size*.25);ctx.closePath();ctx.fill();ctx.fillStyle='#172c34';ctx.fillRect(-size*.22,-size*.1,size*.44,size*.48);ctx.restore();
}
function drawBelief(data){
  if(!data)return;const frame=data.frames[Math.floor(clamp(cursor,0,data.frames.length-1))];
  sectorOverlay(data);
  const predicted=beliefLayer==='predicted'&&frame.inspection.event;
  for(let i=0;i<data.states.length;i++){
    const [gx,gy]=data.states[i],v=predicted?frame.inspection.cells[i].predicted:frame.values[i],x=(gx-3)*76,z=(gy-2)*76,h=v*430;
    polygon([[x-34,0,z+34],[x+34,0,z+34],[x+34,h,z+34],[x-34,h,z+34]],'#32616c77');
    polygon([[x-34,h,z-34],[x+34,h,z-34],[x+34,h,z+34],[x-34,h,z+34]],`rgba(149,211,222,${.06+Math.sqrt(v)*.9})`,'#addbe533');
    if(v>.001)label(`${(v*100).toFixed(1)}%`,x,h+10,z,'#d3edf0',14);
  }
  const event=frame.inspection.event;
  label(event?(predicted?`BEFORE ${event.action.toUpperCase()} SEARCH / PREDICTION`:`${beliefTask==='plan'?'AFTER ':''}${event.action.toUpperCase()} SEARCH / ${event.observation.toUpperCase()}`):'LAST REPORT / INITIAL BELIEF',0,0,-285,'#e3bb85',18);
  label((predicted?'PREDICTED':'POSTERIOR')+' / PROBABILITY MASS',0,0,285,'#b2d1ef',13);
  label('CURRENT → PREDICT     OBSERVATION → CONDITION',0,0,345,'#7499a9',11);
}
function drawRainbow(data){
  if(!data)return;const frame=data.frames[Math.floor(clamp(cursor,0,data.frames.length-1))];
  const c=frame.rejected?'#eba38e':'#b9e8cc';
  for(const [x,text] of [[-240,'PROOF TERM'],[0,'KERNEL'],[240,frame.rejected?'REJECTED':'CHECKED']]){ring(x,0,0,66,alpha(c,.5));dot(x,30,0,8,c);label(text,x,0,95,c,14)}
  line([[-174,25,0],[-66,25,0]],c,2);line([[66,25,0],[174,25,0]],c,2);
  label(frame.formula,0,185,0,c,22);label('EXACT TERMS / RECORDED KERNEL EVIDENCE',0,0,225,'#91a7b2',13);
  label(frame.rejected?'A PLAUSIBLE CLAIM STILL NEEDS A VALID PROOF':'NO EXTRA AXIOMS IN THESE DECLARATIONS',0,0,265,'#91a7b2',11);
}
initStage();if(new URLSearchParams(location.search).get('stage')==='1')setStage(true);resize();requestAnimationFrame(animate);init();
