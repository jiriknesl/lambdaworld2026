'use strict';
const el=id=>document.getElementById(id),session=new URLSearchParams(location.search).get('session');
const channel=session&&'BroadcastChannel' in window?new BroadcastChannel('lambda-world:'+session):null;
let scenes=[],state={index:0,cueIndex:0,elapsed:0,clockRunning:false},lastRender='',lastMessage=0;
const escape=s=>String(s??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;');
const time=s=>{const n=Math.max(0,Math.floor(s));return String(Math.floor(n/60)).padStart(2,'0')+':'+String(n%60).padStart(2,'0')};
function send(type,extra={}){channel?.postMessage({type,...extra})}
function chooseChapter(index){index=Math.max(0,Math.min(scenes.length-1,index));if(channel)send('chapter',{index});else{state.index=index;state.cueIndex=0;render()}}
function render(){
  const scene=scenes[state.index];if(!scene)return;const s=scene.speaker;
  el('trace-button').textContent=state.traceMode?'Show explanation':'Show calculation';
  el('elapsed').textContent=time(state.elapsed);el('clock-toggle').textContent=state.clockRunning?'Pause timer':'Start timer';
  const deadline=s['start-seconds']+s.minutes*60,left=deadline-state.elapsed;
  el('pace').textContent=`Chapter ends ${time(deadline)} · ${left>=0?time(left)+' remaining':time(-left)+' over target'}`;
  const key=state.index+':'+state.cueIndex;if(key===lastRender)return;lastRender=key;
  el('outline').innerHTML=scenes.map((c,i)=>`<button class="${i===state.index?'active':''}" data-chapter="${i}">${i+1}. ${escape(c.id)}<small>${time(c.speaker['start-seconds'])} · ${c.speaker.minutes} min</small></button>`).join('');
  el('outline').querySelectorAll('button').forEach(b=>b.onclick=()=>chooseChapter(Number(b.dataset.chapter)));
  el('chapter-heading').innerHTML=`<span class="eyebrow">${escape(s.mission)}</span><h2>${escape(scene.title)}</h2>`;
  el('beats').innerHTML=s.beats.map((b,i)=>`<section class="beat ${i===state.cueIndex?'active':''}"><div class="beat-top"><h3>${escape(b.title)}</h3><time>${time(s['start-seconds']+b.at)}</time></div><p class="say">${escape(b.say)}</p><div class="demo"><strong>Demo cue</strong> · ${escape(b.demo)}</div><pre>${escape(b.code)}</pre><button data-cue="${i}">Show cue ${i+1} on stage</button></section>`).join('');
  el('beats').querySelectorAll('button').forEach(b=>b.onclick=()=>{if(channel)send('cue',{index:Number(b.dataset.cue)});else{state.cueIndex=Number(b.dataset.cue);render()}});
  el('fallback').textContent='Recovery: '+s.fallback;el('cut').textContent='If behind: '+s.cut;el('handoff').textContent=s.handoff;
  if(channel)requestAnimationFrame(()=>el('beats').querySelector('.active')?.scrollIntoView({block:'start'}));
}
function printScript(){
  el('print-script').innerHTML='<h1>One rescue. Functions, all the way down.</h1><p>Lambda World · Jiri Knesl · 40 minutes</p><p>Timed speaking draft. Times include demos and deliberate pauses. Rehearse aloud on the venue machine; use each chapter’s cut and recovery instructions when needed.</p>'+scenes.map(c=>{const s=c.speaker;return `<h2>${time(s['start-seconds'])} / ${escape(c.title)}</h2><p>${s.minutes} minutes · ${escape(s.mission)}</p>${s.beats.map(b=>`<section class="print-beat"><h3>${time(s['start-seconds']+b.at)} — ${escape(b.title)}</h3><p>${escape(b.say)}</p><div class="demo">${escape(b.demo)}</div><pre>${escape(b.code)}</pre></section>`).join('')}<p><strong>Transition:</strong> ${escape(s.handoff)}</p><p><strong>Recovery:</strong> ${escape(s.fallback)}</p><p><strong>Cut if behind:</strong> ${escape(s.cut)}</p>`}).join('');
}
if(channel){channel.onmessage=({data})=>{if(data?.type!=='state')return;state=data;lastMessage=Date.now();el('sync-status').textContent='Connected to audience window · frame '+state.frame;el('sync-status').classList.remove('offline');render()};send('hello')}
el('chapter-back').onclick=()=>chooseChapter(state.index-1);el('chapter-next').onclick=()=>chooseChapter(state.index+1);
el('trace-button').onclick=()=>send('trace');el('stage-button').onclick=()=>send('stage');el('code-button').onclick=()=>send('code');el('play-button').onclick=()=>send('play');el('step-button').onclick=()=>send('step',{delta:1});el('clock-toggle').onclick=()=>send('clock');el('clock-reset').onclick=()=>send('reset-clock');el('print-button').onclick=()=>window.print();
if(!channel){for(const id of ['stage-button','trace-button','code-button','play-button','step-button','clock-toggle','clock-reset']){
  el(id).disabled=true;el(id).title='Open Presenter from the audience window to use this control.';
}}
for(const [selector,property] of [['header','--presenter-header'],['.toolbar','--presenter-toolbar']]){
  const element=document.querySelector(selector);
  new ResizeObserver(()=>document.documentElement.style.setProperty(property,element.getBoundingClientRect().height+'px')).observe(element);
}
setInterval(()=>{if(channel&&Date.now()-lastMessage>4000){el('sync-status').textContent='Waiting for audience window — open Presenter from the presentation.';el('sync-status').classList.add('offline');send('hello')}},1500);
(async()=>{try{const build=document.querySelector('meta[name="presentation-build"]').content;const response=await fetch('/api/scenes?v='+encodeURIComponent(build),{cache:'no-store'});if(!response.ok)throw Error('Cannot load script');scenes=(await response.json()).scenes;render();printScript();if(!channel)el('sync-status').textContent='Script reader · open Presenter from the audience window for synchronized controls.';send('hello')}catch(e){el('sync-status').textContent=e.message}})();
