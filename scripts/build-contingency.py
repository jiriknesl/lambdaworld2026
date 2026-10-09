"""Build a self-contained, script-free emergency page from actual exported results."""
from pathlib import Path
from html import escape
from hashlib import sha256
import json

ROOT = Path(__file__).resolve().parent.parent
raw = (ROOT / ".preview/api/scenes").read_bytes()
m = json.loads(raw)
visual = {key:value.get("visual") for key,value in m["results"].items()}
fmt = lambda value: f"{value:.4f}".rstrip("0").rstrip(".") if isinstance(value, (int,float)) else str(value)
pair = lambda values: "[" + ", ".join(fmt(x) for x in values[:2]) + "]"


def table(rows):
    return '<table>'+''.join('<tr>'+''.join('<td>'+escape(str(v))+'</td>' for v in row)+'</tr>' for row in rows)+'</table>'


def code(text):
    return '<pre>'+escape(text)+'</pre>'


cases = []


def add(id, title, frame, failure, show, say, limit):
    cases.append({"id":id,"title":title,"frame":frame,"failure":failure,"show":show,"say":say,"limit":limit})

a=visual['flock']['frames'][0]['inspection']
add('flock','Formation macro + gradient descent',0,
    'Macro expansion or live evaluation fails.',
    code('(f/fleet [⛵ · · ⛵]\n         [· · ⛵ ·])\n;; => [0.0 0.0 3.0 0.0 2.0 1.0]\n\n(take 101 (iterate step {:x boats}))')+
    table([['Recorded boat 1: x',pair(a['x'])],['−rate × gradient',pair(a['base-delta'])],['Next x',pair(a['next-x'])]]),
    'The macro produces ordinary coordinates. Descent returns State → State; iterate supplies the history. The first movement is visible as vector addition even without animation.',
    'These are candidate formations, not physical boat dynamics.')
a=visual['constraints']['frames'][-1]['inspection']
add('constraints','A penalty decorates the objective',100,
    'The boats do not animate or the edited penalty cell fails.',
    code('augmented(x) = f(x) + weight × Σ max(0, gᵢ(x))²\n\n(h/penalize objective constraints 3.0)')+
    table([['Final distance cost',fmt(a['roost-cost'])],['Weighted penalty',fmt(a['penalty-cost'])],['Closest pair',fmt(a['closest-distance'])],['Desired separation','1.1']]),
    'The optimizer still receives one function. Positive violations add cost; the loop stays the same. Point to the closest distance to show the compromise.',
    'A finite soft penalty does not guarantee feasibility or collision avoidance.')
a=visual['momentum']['frames'][1]['inspection']
add('momentum','Momentum decorates the step',1,
    'The beta edit, comparison control, or evaluator fails.',
    code('v′ = βv + delta\nx′ = x + v′\n\n(with-momentum plain-step 0.75)')+
    table([['Boat 1 position',pair(a['x'])],['Base movement',pair(a['base-delta'])],['Carried movement',pair(a['carry'])],['Next position',pair(a['next-x'])]]),
    'Add base movement and carried velocity to the current position. Beta zero removes the carry and recovers the base descent trajectory. Iterate receives the same function signature.',
    'Momentum can overshoot; this is not an acceleration guarantee.')
a=visual['parallel']['execution']
add('parallel','Pass the execution strategy as a function',0,
    'pmap is slower, stalls, or the runtime is unavailable.',
    code('(evaluate-population map  fitness population)\n(evaluate-population pmap fitness population)\n\n(vec (map-fn score population))')+
    table([['Recorded candidates',a['population-size']],['Recorded map / ms',fmt(a['serial-ms'])],['Recorded pmap / ms',fmt(a['parallel-ms'])],['Identical ordered results',a['equal?']]]),
    'Independent candidate trajectories can be mapped in parallel. Vec forces realization before timing. A slower pmap result illustrates overhead and is a valid outcome.',
    'These are recorded timings from one run, not a benchmark or current measurement.')
a=visual['bellman']['frames'][0]['inspection']
add('bellman','Bellman transforms a value function',0,
    'The value landscape or live backup fails.',
    code('Q(s,a) = Σ p × (reward + γV(next))\n(T V)(s) = maxₐ Q(s,a)\n\n(iterate improve initial-value)')+
    table([['Cell',str(a['state'])],['Old value',fmt(a['before'])],['Best action',str(a['chosen'])]]+
          [[f'Outcome {i+1}: p × (r + γV)',fmt(t['weighted'])] for i,t in enumerate(a['terms'])]+[['New value',fmt(a['after'])]]),
    'Map the outcomes, reduce their weighted returns, then maximize across actions. Every calculation reads the same old value function.',
    'This routes to a fixed confirmed location; heights are values, not probabilities.')
a=visual['belief' if 'belief' in visual else 'pomdp']['frames'][2]['inspection']
plan=a['plan']; best=next(row for row in plan['actions'] if row['action']==plan['best-actions'][0])
add('pomdp','Belief update + two-search decision',2,
    'The probability map, Bayesian inspector, or planner display fails.',
    code('(reductions (belief-step drift sensor)\n            prior observations)\n\nQ(b,a) = P(seen) + P(miss) × V₁(b′miss)')+
    table([[f'First search: {row["action"]}',fmt(row['score'])] for row in plan['actions']])+
    code(f"One optimal plan: {best['action']}; if missed, {best['second-best'][0]}\n{fmt(best['seen'])} + {fmt(best['miss'])} × {fmt(best['continuation'])} ≈ {fmt(best['score'])}"),
    'After two recorded misses, compare the three hypothetical first actions. On another miss, condition the belief, predict another drift, then choose the second search. V₁ is the supplied continuation function.',
    'Recorded observations are not an optimal-policy rollout. Travel cost is abstracted away; this planner is not proved by the Rainbow audit.')
a=visual['shapley']
add('shapley','Credit is a fold averaged over orders',23,
    'The arrival animation or attribution cell fails.',
    code('1 − (1 − 0.8)² = 0.96\n\ngain = value(team ∪ {boat}) − value(team)\n\n(map #(arrival-trace value-of %)\n     (permutations boats))')+
    table([[boat,fmt(a['credits'][boat])+' pp'] for boat in a['players']]+[['Total coverage',fmt(a['total']-a['baseline'])+' pp']]),
    'A coalition-value function configures the fold. Each arrival receives its marginal coverage. Average all six orders to remove the ordering advantage.',
    'This attributes expected coverage under one frozen belief, not causal credit for an observed rescue. Enumeration is factorial.')
a=visual['rainbow']['evidence']
add('rainbow','Checked proof terms + a rejected term',3,
    'The kernel audit is slow, unavailable, or fails.',
    code('(kernel/check env proof-term claimed-type universes)\n\nβ = rate = gradient = 1\n[x,v] = [0,2] → [1,1]\n\n|V − W| ≤ ε  ⇒  |B(V) − B(W)| ≤ γε')+
    table([['Recorded declarations checked',len(a['checks'])],['Extra axioms','0'],['Number substituted for proof',a['negative-check']['status']],['Audit timestamp',a['checked-at']]]),
    'Show the recorded checked recurrence and Bellman bound, then the rejected number. State explicitly that these are previously checked exact terms, not a live kernel run.',
    'No end-to-end proof of the floating-point program, renderer, POMDP, or general permutation Shapley implementation. Strict contraction requires γ < 1 and the theorem assumptions.')
add('overview','Closing reveal',0,
    'The camera animation or rendering fails.',
    code('Objective → Objective\nStep → Step\nValueFunction → ValueFunction\n\niterate · reductions · map')+
    '<p>Applied Higher Order Functions · Jiri Knesl<br>Rainbow · proof terms as Clojure data</p>',
    'The state spaces changed; the functional vocabulary stayed small. Find the state, the step, the functions configuring it, and the property worth checking.',
    'Use this static overview and finish the talk. The camera is optional.')

css=(ROOT/'resources/public/fonts.css').read_text()+'''
*{box-sizing:border-box}body{margin:0;color:#152c36;background:#fff;font:16px/1.55 var(--sans)}main{max-width:1000px;margin:auto;padding:30px}h1{font-size:32px}h2{font-size:25px}h3{font-size:17px}a{color:#164f70}nav a{display:inline-block;margin:4px 14px 4px 0}section{border-top:2px solid #174755;padding:25px 0;break-before:page}code,kbd{font-family:var(--mono)}pre{font:14px/1.6 var(--mono);background:#eff5f3;padding:16px;white-space:pre-wrap;overflow-wrap:anywhere}table{border-collapse:collapse;width:100%}td{padding:7px;border-bottom:1px solid #c8d4d9}td:last-child{font-family:var(--mono)}.limit{border-left:3px solid #97732d;padding-left:12px}.small{font-size:12px}@media print{body{font-size:10pt}main{padding:0}h1{font-size:25pt}h2{font-size:19pt}pre{font-size:10pt}td{padding:5px}nav{display:none}@page{size:A4;margin:17mm}}
'''
html='<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Lambda World · Demo contingency</title><style>'+css+'</style><main>'
html+='<h1>If the live demo fails</h1><p>Keep this file and the contingency PDF open before the talk. This page is self-contained: no scripts, server, network, or installed fonts required.</p>'
html+='<ol><li>For a failed edit: Reset, then Run once. If it still fails, switch to the extracted replay at <code>http://127.0.0.1:7342</code>.</li><li>For internet/ngrok failure: use the local replay. Start it with <code>python3 -m http.server 7342 --bind 127.0.0.1</code> inside the extracted ZIP.</li><li>For server, Java, or browser-demo failure: open this HTML file directly, or show the corresponding section of <code>lambda-world-contingency.pdf</code>. All numbers below are recorded Clojure results, rounded for display.</li><li>If the projector itself fails, use these same statements as the spoken explanation; do not spend the talk debugging the room.</li></ol>'
html+='<nav>'+''.join(f'<a href="#{c["id"]}">{i+1}. {escape(c["title"])}</a>' for i,c in enumerate(cases))+'</nav>'
for i,c in enumerate(cases):
    route='press Plan two searches' if c['id']=='pomdp' else 'press S for stage and T for the calculation' if c['id']!='overview' else 'show the closing overview'
    html+=f'<section id="{c["id"]}"><h2>{i+1:02d} / {escape(c["title"])}</h2><p><strong>Trigger:</strong> {escape(c["failure"])}</p><p><strong>Replay route:</strong> chapter #{c["id"]}, step {c["frame"]}; {route}.</p><h3>Show this if the live demo is unavailable</h3>{c["show"]}<h3>Say</h3><p>{escape(c["say"])}</p><p class="limit"><strong>Keep the claim precise:</strong> {escape(c["limit"])}</p></section>'
html+='<section><h2>Bundled font license</h2><pre style="font-size:8pt">'+escape((ROOT/'resources/fonts/LICENSE.txt').read_text())+'</pre></section>'
html+=f'<p class="small">Recorded-data SHA-256: {sha256(raw).hexdigest()}. Font license accompanies the deck as font-license.txt.</p></main></html>'
for path in ['resources/public/contingency.html','.preview/contingency.html','artifacts/lambda-world-contingency.html']:
    (ROOT/path).write_text(html)
print('Built 9 demo contingencies with embedded fonts and recorded numerical checkpoints.')
