/* Audience stage and a separate, same-origin presenter window. */
'use strict';
const recoveryKey='lambda-world:audience:v1';
function readRecovery(){try{return JSON.parse(sessionStorage.getItem(recoveryKey))}catch(_){return null}}
const recoveredAudience=readRecovery();
let pendingRecovery=recoveredAudience;
let stageMode=false,cueIndex=0,beliefLayer='posterior',beliefTask='update',presenterChannel=null,presenterWindow=null;
let clockElapsed=0,clockStarted=null,lastPresenterBroadcast=0;
const presenterSession=typeof recoveredAudience?.session==='string'?recoveredAudience.session:crypto.randomUUID();
function elapsedSeconds(){return (clockElapsed+(clockStarted===null?0:performance.now()-clockStarted))/1000}
function clockToggle(){if(clockStarted===null)clockStarted=performance.now();else{clockElapsed+=performance.now()-clockStarted;clockStarted=null}broadcastPresenter()}
function stageSceneChanged(){
  cueIndex=0;beliefLayer='posterior';beliefTask='update';traceMode=false;traceKey='';
  if(pendingRecovery){
    const saved=pendingRecovery;pendingRecovery=null;
    if(saved.sceneId===scenes[index]?.id){
      cueIndex=Number.isInteger(saved.cueIndex)?clamp(saved.cueIndex,0,scenes[index].speaker.beats.length-1):0;
      cursor=Number.isFinite(saved.frame)?clamp(saved.frame,0,maxFrame()):0;
      beliefLayer=saved.beliefLayer==='predicted'?'predicted':'posterior';
      traceMode=!!saved.traceMode;beliefTask=saved.beliefTask==='plan'?'plan':'update';
      playing=false;updateTransport();
    }
  }
  renderStage();broadcastPresenter();saveRecovery();
}
function saveRecovery(){
  if(!entered)return;
  try{sessionStorage.setItem(recoveryKey,JSON.stringify({...presenterState(),session:presenterSession,savedAt:Date.now()}))}catch(_){}
}
function setStage(value){stageMode=value;document.body.classList.toggle('stage',value);$('stage-toggle').setAttribute('aria-pressed',String(value));updateTarget();renderStage();broadcastPresenter()}
function renderStage(){
  const scene=scenes[index],speaker=scene?.speaker,beat=speaker?.beats[cueIndex];if(!beat)return;
  $('mission-line').textContent=speaker.mission;
  $('focus-title').textContent=beat.title;$('focus-text').textContent=beat.text;$('focus-code').textContent=beat.code;
  $('cue-count').textContent=`${cueIndex+1} / ${speaker.beats.length}`;
  $('cue-back').disabled=cueIndex===0;$('cue-next').disabled=cueIndex===speaker.beats.length-1;
  $('belief-layers').hidden=scene.id!=='pomdp';
  $('layer-posterior').setAttribute('aria-pressed',String(beliefLayer==='posterior'));
  $('layer-predicted').setAttribute('aria-pressed',String(beliefLayer==='predicted'));
  $('plan-searches').setAttribute('aria-pressed',String(beliefTask==='plan'));
  $('stage-hof').textContent=scene.id==='pomdp'&&beliefTask==='plan'?'ValueFunction → ValueFunction':scene.signature;
  $('focus-trace').hidden=scene.id==='overview';
  document.querySelector('.focus-top>span').textContent=traceMode?'ACTUAL CLOJURE VALUES':'FOLLOW THE FUNCTION';
  $('focus-trace').setAttribute('aria-pressed',String(traceMode));
  $('focus-trace').textContent=traceMode?'Explanation':'One update';
  $('focus-card').classList.toggle('showing-trace',traceMode);
  traceKey='';renderTrace();
}
function selectCue(n){
  const beats=scenes[index]?.speaker?.beats;if(!beats||busy)return;
  traceMode=false;cueIndex=clamp(n,0,beats.length-1);const beat=beats[cueIndex];
  if(Number.isFinite(beat.frame)){cursor=clamp(beat.frame,0,maxFrame());playing=false;updateTransport()}
  beliefLayer=beat.layer||'posterior';beliefTask=beat.calculation||'update';renderStage();updateInspection();broadcastPresenter();
}
function setBeliefLayer(layer){beliefTask='update';beliefLayer=layer==='predicted'?'predicted':'posterior';renderStage();broadcastPresenter()}
function presenterState(){const scene=scenes[index];return {type:'state',index,cueIndex,frame:Math.floor(cursor),playing,stageMode,entered,elapsed:elapsedSeconds(),clockRunning:clockStarted!==null,sceneId:scene?.id,beliefLayer,beliefTask,traceMode}}
function broadcastPresenter(){presenterChannel?.postMessage(presenterState())}
function setupPresenterChannel(){
  if(!('BroadcastChannel' in window)){toast('Presenter synchronization is unavailable in this browser.');return}
  if(!presenterChannel){
    presenterChannel=new BroadcastChannel('lambda-world:'+presenterSession);
    presenterChannel.onmessage=async ({data})=>{
      if(!data||typeof data!=='object')return;
      switch(data.type){
        case 'hello':broadcastPresenter();break;
        case 'chapter':if(Number.isInteger(data.index)&&data.index>=0&&data.index<scenes.length){
          if(!entered){location.hash=scenes[data.index].id;enter()}
          else await go(data.index);
          broadcastPresenter();
        }break;
        case 'cue':if(Number.isInteger(data.index))selectCue(data.index);break;
        case 'step':if(data.delta===1||data.delta===-1)stepBy(data.delta);broadcastPresenter();break;
        case 'play':playPause();broadcastPresenter();break;
        case 'stage':setStage(!stageMode);break;
        case 'trace':if(entered&&scenes[index]?.id!=='overview'){setStage(true);setTrace(!traceMode)}break;
        case 'clock':clockToggle();break;
        case 'reset-clock':clockElapsed=0;clockStarted=null;broadcastPresenter();break;
        case 'code':setStage(false);setCodeVisible(true);setPane('code');broadcastPresenter();break;
      }
    };
  }
}
function connectPresenter(){
  setupPresenterChannel();
  presenterWindow=window.open('/presenter.html?session='+encodeURIComponent(presenterSession),'lambda-world-notes','width=1180,height=850');
  if(!presenterWindow)toast('Allow the presenter window in your browser, then try again.');
}
function stageTick(time){if(time-lastPresenterBroadcast>500){lastPresenterBroadcast=time;broadcastPresenter();saveRecovery()}}
function initStage(){
  setupPresenterChannel();
  if(recoveredAudience){
    if(recoveredAudience.stageMode)setStage(true);
    if(Number.isFinite(recoveredAudience.elapsed)&&recoveredAudience.elapsed>=0){
      clockElapsed=recoveredAudience.elapsed*1000;
      if(recoveredAudience.clockRunning&&Number.isFinite(recoveredAudience.savedAt)){
        clockElapsed+=Math.max(0,Date.now()-recoveredAudience.savedAt);
        clockStarted=performance.now();
      }
    }
  }
  addEventListener('pagehide',saveRecovery);
  $('stage-toggle').onclick=()=>setStage(!stageMode);$('presenter-toggle').onclick=connectPresenter;
  $('enter-stage').onclick=()=>{setStage(true);enter()};
  $('cue-back').onclick=()=>selectCue(cueIndex-1);$('cue-next').onclick=()=>selectCue(cueIndex+1);
  $('focus-trace').onclick=()=>setTrace(!traceMode);
  $('experiment-choice').onchange=e=>chooseExperiment(e.target.value);
  $('focus-open-code').onclick=()=>{setStage(false);setCodeVisible(true);setPane('code')};
  $('plan-searches').onclick=()=>{beliefTask='plan';if(stageMode)setTrace(true);else{setPane('inspect');renderStage();$('decision-plan')?.scrollIntoView({block:'start'})}};
  $('layer-posterior').onclick=()=>setBeliefLayer('posterior');$('layer-predicted').onclick=()=>setBeliefLayer('predicted');
  document.addEventListener('keydown',e=>{
    if(e.target.matches('input,textarea,select')||$('help').open||e.metaKey||e.ctrlKey||e.altKey)return;
    if(e.key.toLowerCase()==='s'&&entered){e.preventDefault();setStage(!stageMode)}
    if(e.key.toLowerCase()==='t'&&entered&&scenes[index]?.id!=='overview'){e.preventDefault();setStage(true);setTrace(!traceMode)}
    if(e.key.toLowerCase()==='p'){e.preventDefault();connectPresenter()}
    if(e.key==='ArrowDown'&&entered){e.preventDefault();selectCue(cueIndex+1)}
    if(e.key==='ArrowUp'&&entered){e.preventDefault();selectCue(cueIndex-1)}
  });
}
