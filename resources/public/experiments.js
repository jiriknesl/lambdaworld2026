/* Display actual JVM values. No optimization or Bayesian updates run in JavaScript. */
'use strict';
let recordedExperiments={},traceMode=false,traceKey='';

function renderExperiments(){
  const scene=scenes[index],variants=scene?.experiments||[];
  $('experiment-bar').hidden=!variants.length;
  const code=$('editor').value,variant=variants.find(v=>v.code===code);
  $('experiment-choice').replaceChildren(new Option('Talk default','default'),
    ...variants.map(v=>new Option(v.label,v.id)));
  if(code!==scene?.code&&code!==scene?.['expanded-code']&&!variant){
    $('experiment-choice').add(new Option('Edited cell','custom'));
  }
  $('experiment-choice').value=variant?.id||(code===scene?.code||code===scene?.['expanded-code']?'default':'custom');
  $('experiment-note').textContent=variant?.note||'';
  $('unfold').hidden=!scene?.['expanded-code']||!!variant;
}
async function chooseExperiment(id){
  if(busy)return;
  const scene=scenes[index],variant=scene.experiments.find(v=>v.id===id);
  if(id!=='default'&&!variant)return;
  const frame=Math.floor(cursor);
  $('editor').value=variant?.code||scene.code;highlight();
  $('experiment-choice').disabled=true;
  try{
    await runCell({autoplay:false});
    cursor=clamp(frame,0,maxFrame());updateTransport();inspectionKey='';updateInspection();
    if(stageMode)setTrace(true);else setPane('inspect');
  }finally{$('experiment-choice').disabled=false;renderExperiments()}
}
function setTrace(value){traceMode=value;traceKey='';renderStage();renderTrace();broadcastPresenter()}
function stageCalculation(data,n){
  const frame=data?.frames?.[n],a=frame?.inspection;
  if(data?.execution){const e=data.execution;return {
    title:'Same inputs. Same scores.',
    text:'Independent objective calls can be mapped in parallel. These timings are one JVM run, not a speedup guarantee.',
    code:`candidates  ${e['population-size']}\nmap         ${fmt(e['serial-ms'])} ms\npmap        ${fmt(e['parallel-ms'])} ms\nidentical?  ${e['equal?']}`};}
  if(data?.kind==='flock'&&a){
    const pair=xs=>xs?.slice(0,2);
    if(scenes[index]?.id==='constraints')return {
      title:'Same step. New objective.',
      text:'The penalty adds a crowding cost. Finite weight permits a compromise: separation is not guaranteed.',
      code:`destination  ${fmt(a['roost-cost'])}\npenalty      ${fmt(a['penalty-cost'])}\ntotal        ${fmt(frame.value)}\nclosest pair ${fmt(a['closest-distance'])}\ndesired gap  1.1`};
    return {title:a['next-x']?`Boat 1 · update ${n} → ${n+1}`:`Boat 1 · final state ${n}`,
      text:a['next-x']?`x′ = x − η∇f + βv. Step size η = ${fmt(a.rate)}, carry β = ${fmt(a.beta??0)}. Read the two coordinates together.`:'Final recorded state. There is no next update in this trajectory.',
      code:a['next-x']?`x     ${vec(pair(a.x))}\n∇f    ${vec(pair(a.gradient))}\n−η∇f  ${vec(pair(a['base-delta']))}\nβv    ${vec(pair(a.carry)||[0,0])}\nx′    ${vec(pair(a['next-x']))}`:
        `x     ${vec(pair(a.x))}\ncost  ${fmt(frame.value)}\nclosest pair  ${fmt(a['closest-distance'])}`};
  }
  if(data?.kind==='bellman'&&a)return {
    title:`One backup at ${vec(a.state)}`,
    text:`Read old V, sum outcomes, then take the best action. γ = ${fmt(a.discount)}; the gold cell receives this next value.`,
    code:`V     ${fmt(a.before)}\na*    ${vec(a.chosen)}\np(r + γV) per outcome:\n  ${a.terms.map(t=>fmt(t.weighted)).join(' + ')}\n(T V) ${fmt(a.after)}`};
  if(data?.kind==='belief'&&a){
    if(beliefTask==='plan'){
      if(a.terminal)return {title:'A sighting ends this mission.',text:'This toy model stops at detection. There is no next search to optimize.',code:'seen → terminal success\n\nNo continuation value is needed.'};
      const plan=a.plan,best=plan.actions.find(row=>row.action===plan['best-actions'][0]);
      return {title:'One optimal two-search plan.',
        text:`Search ${best.action}; if missed, search ${best['second-best'][0]} after conditioning and drifting again. The supplied value function V₁ chooses that second search.`,
        code:plan.actions.map(row=>`Q ${row.action.padEnd(7)} ${fmt(row.score)}`).join('\n')+`\np_seen + p_miss × V₁(b′)\n${fmt(best.seen)} + ${fmt(best.miss)} × ${fmt(best.continuation)} ≈ ${fmt(best.score)}`};
    }

    const cell=a.cells.reduce((best,c)=>c.posterior>best.posterior?c:best,a.cells[0]);
    return {title:a.event?`${a.event.action} search → ${a.event.observation}`:'Start with a belief, not a point.',
      text:a.event?`Cell ${vec(cell.state)}, the most likely location after this observation. Multiply by likelihood, then normalize across all cells.`:'The last report is a distribution. Its total mass is one; drift will transport that mass before the search.',
      code:a.event?`predicted   ${fmt(cell.predicted)}\nlikelihood  ${fmt(cell.likelihood)}\nP(observed) ${fmt(a.evidence)}\nposterior   ${fmt(cell.posterior)}\nΣ posterior ${fmt(a.mass)}`:
        a.cells.filter(c=>c.posterior>0).map(c=>`${vec(c.state)}  ${fmt(c.posterior)}`).join('\n')};
  }
  if(data?.kind==='shapley'){
    const order=data.orders[Math.floor(n/4)],arrival=n%4,state=order.trace[arrival],prev=order.trace[Math.max(0,arrival-1)];
    return {title:state.player?`${state.player} joins the coalition`:'Start a fresh fold.',
      text:`Order ${Math.floor(n/4)+1}/6: ${order.order.join(' → ')}. Credit is marginal coverage in percentage points, averaged over all six orders.`,
      code:`before   ${fmt(prev.value)} pp\nafter    ${fmt(state.value)} pp\ngain     ${fmt(state.gain??0)} pp\nmean     ${state.player?fmt(data.credits[state.player])+' pp':'no arrival yet'}`};
  }
  if(data?.kind==='rainbow'&&frame)return {
    title:frame.rejected?'The kernel rejects this term.':'A checked term has a precise scope.',
    text:'Recorded Rainbow kernel evidence. These declarations do not verify the complete rescue program.',
    code:`${frame.formula}\n\n${data.evidence.checks.length} declarations checked\nextra axioms: 0\nwrong term: ${data.evidence['negative-check'].status}`};
  return null;
}
function renderTrace(){
  if(!stageMode||!traceMode||!entered)return;
  const key=`${index}:${Math.floor(cursor)}:${sceneVersion}:${beliefTask}`;
  if(key===traceKey)return;traceKey=key;
  const content=stageCalculation(currentVisual(),Math.floor(cursor));
  if(!content)return;
  $('focus-title').textContent=content.title;$('focus-text').textContent=content.text;$('focus-code').textContent=content.code;
  $('focus-card').classList.add('showing-trace');
}
