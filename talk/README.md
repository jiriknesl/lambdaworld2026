# Rebuilding Operations Research in Higher-Order Functions — the deck

Lambda World 2026, Jiri Knesl. One ClojureScript namespace (`src/talk/core.cljs`) holds the slides,
the algorithms they show and the drawing code; `src/talk/notation.cljc` holds the macros.
Demo values are computed by that code. Definition panels use the exact source of the running
function (captured at compile time by `src`). Explanatory calls and mathematical proof steps are
labelled separately; Rainbow verdicts are recorded evidence, not browser kernel execution.

Design and decisions: `../docs/superpowers/specs/2026-10-15-lambda-world-talk-design.md`.

## Public preview

[Open the revised deck](https://haphazard-sanction-pavestone.ngrok-free.dev/?v=lean-results-20261027).
Updated 2026-10-27: 26 slides. The HOF introduction is two slides: iterate and its
stopping test, using one square-root example throughout. Validation is one animated shield demonstration, followed
by the shared iterate summary. Rainbow explains its AI-oriented design goal and information density, explains Clojure as a generation target, and presents the Algeciras case. The deeper
controller, proof-term and plant-certificate explanations remain optional study material.
The spoken script, study guide and PDFs follow this shorter route.
This URL is **public and unauthenticated**;
the compiled deck includes all slides, displayed source, the full spoken script, technical reference notes and recorded proof evidence.
It now serves this deck, replacing the old Function Observatory preview at the same URL.

The detached tmux session `lambdaworld-preview` serves
`artifacts/preview/talk-release/` through ngrok's file server. Its six files are
`index.html`, `talk.css`, `fonts.css`, `font-license.txt`, `js/main.js` and the old-origin
recovery worker `sw.js`. The release was copied explicitly to exclude leftover development
runtime files and source maps. There is no REPL or evaluation server behind this tunnel.
The ngrok inspector is on loopback port 4041; another project's tunnel on 4040 is unchanged.
Rebuilding `talk/public` alone does not refresh this copied preview.

Slide 14's highlighted bird uses a scripted crossing of the most exposed turbine. A running
rotor shows an impact flash and falling bird; the same crossing continues past a stopped rotor.
It reads the actual displayed schedule and playback hour. Both the flock and the featured
bird use `floor(2.5 × hourly activity)` visible markers. The featured bird replaces one flock
marker rather than appearing as an extra bird every hour. At 18:00 and 19:00 this count is
zero, so no birds or strike graphics appear even when turbines restart. This is display
quantization: the numerical exposure index still includes the small fractional evening tail. This illustrates the reason for
curtailment; it is not a blade-collision simulation or a count of predicted fatalities. The
optimization continues to use the same exposure index. `scripts/playback-smoke.cjs` checks
both outcomes, impact timing and the synchronized schedule marker with a controlled clock.

## Run

Node and a JDK are needed to build. Presenting the release build needs only a browser.

```sh
npm install
npm run release        # → public/js/main.js
open public/index.html # from disk: no server, no network
```

While working on it:

```sh
npm run watch          # http://localhost:7350 — save core.cljs and the page hot-reloads,
                       # staying on the same slide and step. nREPL on port 7351.
npm test               # algorithms, notation, number formatting, and a 60-character limit
                       # on every line of code shown on a slide
node scripts/smoke.cjs /path/to/node_modules/playwright
                       # every slide, all short reveals and sampled long traces at three projector sizes:
                       # overflow, hash restore, external requests, keys, notes window
```

## Keys

| Key | Action |
|---|---|
| ↓ / ↑ (PageDown / PageUp) | next / previous slide (clickers work) |
| → / ← | next / previous step of the slide's animation |
| Shift + → / ← | 10 steps |
| Space | play / pause the steps |
| Home / End | first / last step |
| N | presenter window: speech for this reveal, stage cue, optional technical notes, next slide and clock (T starts/pauses); its keys drive the stage |
| F | fullscreen |
| D | dark / light |
| ? | key help |

The address bar shows `#s{slide}a{step}`; a refresh returns to the same place. The stage is a fixed
1600×900 canvas scaled to the window, so 16:9 and 4:3 projectors show the same layout.

## Live editing on stage

1. Before the talk: `npm run watch`, open `http://localhost:7350`, connect Cursor with Calva
   (*Connect to a running REPL* → shadow-cljs → port 7351 → build `:talk`).
2. Edit and save `core.cljs`: the slide redraws in about a second. Good candidates:
   - move a 🌀 turbine or the 🐦 flyway in the `defmap hills` drawing, and watch tomorrow's plan change;
   - change the risk limit/weight in `risk-cost`, or the momentum `0.9`;
   - change `supply-calendar`, `demand-probabilities` (keep their sum at one), or the future weight in `expected-return`;
   - remove `with-shield` from the third controller and watch reachability turn red.
3. A new random wind farm and flyway, from the REPL: `(talk.notation/random-map {:seed 7})`, then paste it.

The optimisation runs take a few seconds each. The deck computes them one state per animation
frame from the moment it opens (about half a minute in all), so they are ready long before the
Optimise section. After a hot reload they start again.

**Fallback.** Every slide's default example is compiled in. A broken edit shows shadow-cljs's error
overlay and the last good build keeps running: undo, save. If the watch process or the editor fails,
open the release build (`public/index.html`) — it needs nothing else.

## Talk arc — revised 2026-10-27

HOFs are the subject. Step size and momentum are integrated into optimization; Rainbow follows the three examples.
The deck has 26 slides; reveals are steps within a slide, not additional topics.

| Slides | Estimated time | What the audience sees |
| --- | ---: | --- |
| 1–2: Jiri | 0:42 | Developer, economist, Barcelona meetup and interviews. |
| 3: Clojure | 0:27 | Why I chose it; no language prerequisite. |
| 4: OR | 0:38 | What optimization, decisions and validation ask. |
| 5–6: HOFs | 2:22 | Returned update function, iterate trace, fixed point and stopping test. |
| 7–14: Example 1 — wind farm | 8:51 | Build the update, choose its pace, change the objective, compare momentum, replay the day. |
| 15–19: Example 2 — hydrogen station | 8:59 | Supply cycle, demand, value-table updates, explicit policy extraction and comparison. |
| 20–21: Validation and summary | 2:12 | Animated shield, exhaustive model result, shared iterate. |
| 22–24: Rainbow | 2:44 | AI-oriented proving, useful progress per token, Clojure generation, business case. |
| 25–26: Book and thanks | 0:38 | Higher-order functions for OR and algorithm design; contact. |

The current 3,072-word script estimates **27:33 including visual holds**, or **29:33 with two
minutes for questions**, at 125 words/minute. This is calculated, not an aloud rehearsal. The shorter route leaves room within the 40-minute slot for live demonstrations and audience questions. Slower
speech needs shortening; see `speaker/timing.json` and `speaker/ANSWERS.md`. The macro compiler,
Adam race, falsification and trailer LP remain offstage.

## Spoken script and revision record

- [Full speech](speaker/SCRIPT.md): 26 slides, 48 cues. Numerical phase ranges have one passage to
  speak over the animation, plus distinct opening and closing passages. Every actual phase is covered.
- [Revision log](speaker/REVISIONS.md): 21 passes, each with the problem found and the applied change.
  `speaker/revisions.json` retains before/after fingerprints from those editorial passes.
- [Optional answers](speaker/ANSWERS.md): assumptions, numerical details, proof scope and parallelism.
- [Printable HTML](../artifacts/lambda-world-current-speaker-script.html) and
  [PDF](../artifacts/lambda-world-current-speaker-script.pdf): current local handoff artifacts,
  generated with embedded fonts; these files are **not** served by the six-file ngrok preview.

Edit `speaker/script.json`, then run `npm run speaker:build`. It generates the Markdown speech,
revision log, timing data, printable HTML and `src/talk/script_data.cljs`. Test/release prechecks
reject stale tracked exports. The presenter imports that same data, selects the cue for the current
phase and keeps old technical notes collapsed. Changing a slide title or phase count requires
updating the script; tests require exactly one nonempty speech/cue match for every rendered phase.
To regenerate the PDF after building the script:

```sh
node scripts/print-speaker.cjs /path/to/node_modules/playwright
```

## Where the running definitions live

`src/talk/core.cljs` contains algorithms, their visualizations, slide content and notes. `src`
captures definitions for display, so definition panels show the implementation actually running.

| Area | Functions |
| --- | --- |
| Iteration | `newton-sqrt`, `fixed-point`, stopping predicates |
| Turbine costs | `electricity-lost`, `bird-risk`, `as-plan`, `energy-cost`, `with-risk-penalty`, `risk-cost` |
| Optimization | `gradient`, `descent-step`, `clamped`, `solver-step` |
| Composition | `with-momentum`, `with-observer`, `record-cost!` |
| Hydrogen | `supply-calendar`, `orders`, `next-state`, `weekly-profit`, `expected-return`, `bellman`, `evaluate-policy` |
| Validation | `tick-controller`, `react-to-detections`, `with-shield`, `controller-successors`, `reach-step`, `reachable` |
| Visuals | `schedule`, `scene`, `coordinate-plot`, `convergence-chart`, `hydrogen-scene`, `controller-branches` |

There is no `minimize` or `subject-to` in the spoken optimization example. In the old custom
macro they were syntax markers, not functions. That notation obscured the HOFs and is retired
from the stage. The source still contains those macros for legacy examples; no symbolic algebra
or Hessian computation is involved in this gradient-descent example.

## Does fixed-point compute the whole limit?

No. `take` is lazy: `(take 10001 (iterate step start))` puts a ceiling on a sequence, without
asking for all its elements. `map` and `take-while` are lazy too. `count` eagerly consumes only
the prefix that `take-while` yields, stopping when it encounters the first converged pair.
The final `vec` is eager, but collects just the required states from the already-realized,
cached sequence; it does not rerun `step`.

A counter-instrumented check with `max-steps` 10000 and convergence on update 12 produces exactly
12 calls and 13 returned states (the starting state plus twelve updates), in both Clojure and
the deck's ClojureScript. The default cap in the displayed function is 1000 updates. Exact
realization counts are specific to this pipeline; lazy operations over chunked inputs can
realize ahead, but the `iterate` pipeline here does not batch thousands of updates.

## Learning the optimization example

Start at [Optimization is iterate (slide 9)](https://haphazard-sanction-pavestone.ngrok-free.dev/?v=lean-results-20261027#s9).
Use **Right** for each reveal and **N** for the explanation.

| Function | Accepts | Returns |
| --- | --- | --- |
| `gradient` | numerical cost | function computing one slope per setting |
| `descent-step` | gradient function, rate | state-update function |
| `clamped` | step, bounds | step limiting its output settings |
| `with-risk-penalty` | cost, risk function, limit, weight | new cost function |
| `with-momentum` | step, carry factor | step storing and reusing velocity |
| `with-observer` | step, observer | step reporting each realized update |
| `iterate` | step, initial state | lazy sequence of states |

A state initially has 72 settings at 0.5: six turbines × twelve hours. This is an arbitrary
interior starting guess for the search, not a wind forecast or the physical state of the farm.
The forecast supplies available generation; a setting chooses the fraction retained. At noon,
84 kWh available × 0.5 = 42 kWh retained. The demo uses the same illustrative generation forecast
for all six turbines; it does not calculate a wind field or turbine power curve. `as-plan` gives them
`[turbine hour]` keys. The cost scores the whole day. Numerical differentiation holds 71 settings
fixed and nudges the remaining one up and down. For turbine 1 at noon, cost changes from
5214.00084 to 5213.99916 across a setting change of 0.00002. The slope is −84.
Subtract rate × slope: `0.5 − 0.0001 × (−84) = 0.5084`. Every coordinate's slope uses the same
old vector. One call returns 72 updated settings, then the clamp limits them to 0…1. Slide 9
shows all six noon settings changing together. Other hours update in the same call.

Slide 8 visualizes all turbine-hour exposure inputs. Risk at full operation is `exposure(t) ×
birds(h)`: location relative to the flyway gives each turbine a different weight, while all share
one hourly traffic pattern. These are exposure indices, not independent probability distributions.

Slide 11 compares two real candidate plans. A retains half everywhere; B only changes T3 at noon
to stopped. B sacrifices another 42 kWh but lowers exposure. A wins on electricity alone; B wins
with the chosen penalty. The target 12 and weight 100 are modelling choices. Risk 14 adds 400
score points; risk at or below 12 adds zero. The comparison teaches the tradeoff before the HOF
that implements it. Neither candidate is claimed to be the final solution.

Electricity-only descent eventually runs everything. `with-risk-penalty` returns a different
cost: electricity lost + 100 × max(0, risk − 12)². Both runs use the same start, rate and stopping
test. The schedules are shown side by side; an update revises the plan for the same day, rather
than advancing the clock. After the momentum comparison, the 3D playback advances time through the resulting day. Its current-hour rectangle
and 3D clock use the same time argument, on both the full-operation and optimized-plan reveals.

Stopping turbines sacrifices generation. Risk is an illustrative exposure index, not a count
of deaths. The penalty is a soft tradeoff and can settle above twelve; it is not a hard constraint.
Tests compare final objective values with an independent convex lower bound, not an assumption
that all optimizers produce identical settings. Settling alone does not certify an optimum.

Slide 10 changes the rate immediately after gradient descent is introduced. Slide 13 follows the
plain risk-aware run with the question of whether momentum can reduce update count. Momentum
calls the base step once, adds 90% of the old velocity to its proposed displacement, and returns
new position and velocity. The outer clamp clips position, not velocity. The cost chart and update
counts compare actual runs with the same start/rate/tolerance; fewer updates here do not promise
universal improvement or a wall-clock speedup.

The observer example records four costs from five realized states, including the start. Its
callback deliberately writes to an atom; the underlying numerical step remains pure. Reusing
cached states does not repeat logging. A new realization of a new sequence runs the effects again.

## Reading the decision and validation examples

Slides 15–19 concern a **hydrogen filling station** throughout. The physical tank holds four
units; one customer truck uses one unit. The station observes stock and the supplier window,
orders before demand is known, receives the delivery, serves customers and carries leftovers.

| Supply week | Purchase price/unit | Maximum purchase | Following week |
| --- | ---: | ---: | --- |
| Regular | 2 | 3 units | Tight |
| Tight | 6 | 1 unit | Regular |

The supplier limits vary; the tank capacity stays four. Customer arrivals form a **discrete
mixture**: 0 trucks with probability .40, 1 with .15, 3 with .45. No Gaussian approximation is
used. Draws are independent across weeks in this compact teaching model. Selling price is 10,
holding cost is 1 per leftover unit, future discount .9. No evaporation, backlog, delivery fee
or extra service-level penalty is included; turning a customer away loses that sale.

State is `[supply-week stock]`: two phases × five stock levels = ten states. The repeating calendar
becomes part of the state, allowing the same stationary Bellman update to handle varying purchase
prices and supplier capacity. `orders` obeys both tank space and supplier availability. `next-state`
advances the week and carries leftovers. `expected-return` averages current profit plus .9 times
that next state's value. The generic `bellman` implementation remains unchanged.

Slide 16 poses the non-obvious choice: in a regular week with one unit, order two to cover any
current convoy, or three to carry an extra unit into tight supply? All possible next inventories
are shown. Slide 17 scores those orders. With zero future values order two wins; after three value
backups order three wins. The fourth reveal exposes Bellman’s two levels: the model constructs an
update function, which maps ten old profit estimates to ten new best scores. The fifth reveal shows
actual `best-order` source: `max-key` retains the winning order, whereas `max` retains its score.
Both use the same q function. `best-order` returns a state-to-order map without modifying V.
**Iterations refine a plan; they are not weeks or actual purchases.**

Slide 18 shows expected future profit, in money units, with a labelled bar for each `[week stock]`.
Regular/tight bars are grouped by inventory. A worked table reads both stock-1 bars and the orders.
After n backups, V is the n-week optimal value; the displayed greedy order looks one week beyond
that table. The final policy is:

| Starting stock | Order in regular week | Order in tight week |
| ---: | ---: | ---: |
| 0 | 3 | 1 |
| 1 | 3 | 0 |
| 2 | 2 | 0 |
| 3 | 1 | 0 |
| 4 | 0 | 0 |

Slide 19 shows the policy, comparison and tight-week example together in one view. It compares five feasible rules over twenty weeks, starting at `[:regular 1]`. Expected
discounted profit is approximately 50.11 (fill to two), 70.36 (fill to three), 66.36 (fill to four),
70.48 (optimize this week only) and 72.20 (Bellman's stationary policy). All target rules are clipped
to available supply and tank space. Always filling to capacity is worse than adapting to the phase;
filling to three is already fairly good. A second non-obvious action is buying one in an empty
tight-week tank: immediate expected profit is −0.40, but the possible carried stock helps before
the next capped cheap delivery. The myopic policy skips that purchase; Bellman takes it.
These are weighted evaluations over all demand paths,
not selected simulations. The stationary policy is evaluated over twenty weeks for a comparable
score; it is not claimed to be the optimal finite-horizon terminal-week policy. This example is
an illustrative procurement model, not a hydrogen price or customer forecast.

Slide 20 is the entire Validation section. Two animated views send the same detection
through the original reacting controller and the simpler `stop-for-birds` wrapper.
The wrapper stops on any detection; otherwise it delegates to the supplied decision.
The rotor follows the actual command. Detection arrives at two seconds; the shielded
rotor freezes then, while the original continues. At the rotor the unshielded bird
shows an impact flash and falls; the shielded bird continues past it. The moving blade
is aligned with the scripted contact point. This illustrates a software command,
not physical braking or a guarantee about bird survival.

The displayed failure counts come from exhaustive finite reachability using the same
controller as the animation. `iterate` expands possible states until the set stops growing;
failures have a confident detection and RUN. The shielded set closes with no failures.
The optimized 72-setting day schedule is not what this separate model validates.
The older table-specific shield and three-controller walkthrough remain study background.
`node scripts/shield-smoke.cjs /path/to/playwright` checks the detection boundary, real
commands, rotor motion, model verdicts, hash recovery and matching presenter speech.

Slide 21 names `iterate` underneath optimization, value iteration and reachability. Their
states, updates and stopping tests differ. Rate and momentum already demonstrated composition.

## Rainbow: purpose and one familiar property — 2026-10-26

[Current public preview](https://haphazard-sanction-pavestone.ngrok-free.dev/?v=lean-results-20261027#s22).
It remains publicly accessible without authentication.

Slide 22 introduces Rainbow's Clojure/OR focus and its design for AI-assisted proving.
“More verified progress per token” is a design goal, supported by section 3.5 of
`../rainbow/internal-rfnbo/Rainbow_AI_Worker_Implementation_Specification.md`.
This is not a measured compression ratio or a claim of superiority over another prover.
The proposed 5:1 proof-to-generated-code ratio awaits its unit and example; it is not
asserted on stage. Reusable statements, checked lemmas and local checking explain the goal.

Slide 22 also includes Jiri's reported 2026-10-27 results: all 1,721 theorems in the
default checkpoint accepted by Lean 4.35 with exact types and matching axiom reports;
search solved 171/200 for Rainbow versus 131/200 for Lean core automation. The slide
states our own held-out corpus, 30 seconds per theorem, no AI and Lean without Mathlib.
The script specifies best of four Lean setups. These supplied results were not rerun
for this slide edit. No general speed/superiority claim or kernel-soundness claim is made.
No private artifact link or report is being published by this edit.

Slide 23 explains Clojure as a generation target: structured forms,
higher-order function composition and REPL inspection. The generator currently covers a
modeled subset; the runtime remains trusted. Slide 24 keeps the Algeciras business case.
Proof syntax, comparison tables and generated-code listings remain optional study material.

Direction was reconciled with the live `rainbow-claude-4` worker and
`../rainbow/internal-rfnbo/doc/north-star-2026-10-23.md` plus `BACKLOG.md`, at
revision `215f6525`. The older `internal-north-star` worktree lacked the latest update.
These internal strategy documents were read, not copied into the presentation or preview.
The useful public-facing distinction is implemented proof data versus planned interfaces;
no commercial roadmap or unsupported competitive claims were imported.

## The proof examples and their limits

The optional study guide explains Rainbow explicit term syntax from `src/talk/composition.edn`. It proves
function composition for arbitrary propositions P, Q, R: given `f : P → Q`, `g : Q → R` and `p : P`,
`g(f(p))` proves R. `:forall` binds propositions, `:->` is implication, `:fn` binds arguments and
`:app` applies a term. The containing `:type`/`:value` map is the checker script's input.

`src/talk/composition-check.edn` records a fresh kernel check against the clean Rainbow revision
`d9080c7285c7cd28a81e2d184b25ecae333510fd`, in an empty environment with no axioms. Returning only
`f(p)` is rejected because Q does not establish R. The original, removed and restored proof terms are elaborated and checked;
these verdicts are recorded evidence, not executed by a browser kernel. Reproduce from the
**repository root** (requires the local Rainbow checkout):

```sh
clojure -Sdeps '{:paths ["../rainbow/rainbow/src" "talk/src"]}' -M talk/scripts/check-proof.clj
```

The proof-check script and receipt preserve the original example. This optional proof teaches
the syntax; it is not a proof of the optimization or Bellman code.

The study guide retains two general theorems about exact models: finite action selection dominates
every candidate's score, and LP weak duality bounds every feasible primal objective by a
feasible dual objective. Both are stated with their nonnegative-rational assumptions.
The pictures illustrate the statements; they are not additional kernel-checked instances.

The study guide retains the actual generated Clojure for `clj/clamp-program`, with recorded executions
at bounds 0 and 1. It distinguishes the modeled program's refinement and the compiler's
scoped-correctness theorem from the trusted printer/primitives/runtime boundary. It does not
claim that arbitrary Clojure or the browser optimizer is proved.

`src/talk/or-checks.edn` records four checks made on 2026-10-24 from Rainbow snapshot
`0257fd657d897843f8f2c3acbf19115631ebcfb1`, with source-tree and checkpoint SHA-256 hashes:

- `decision/finite-nnrat-argmax-dominates`
- `nnrat/lp-weak-duality`
- `clj/clamp-program-denotes-clamp`
- `clj/compile-correct-scoped`

Each was checked after replaying the pinned base checkpoint and installing its modules;
each has an empty transitive axiom profile. `scripts/check-rainbow-examples.clj` reproduces
the checks and generated function, reading an isolated source snapshot. See the study guide
for the command. The saved receipt includes emitted and displayed source; earlier stage builds checked that both read to the recorded form.
This run does not prove kernel soundness or close the plant checker's soundness theorem.

The Bellman contraction explanation is now optional study material, not a stage walkthrough.
Its 2026-10-09 receipt at Rainbow `3f411ae` remains historical and pending repaired-kernel replay.
No correspondence proof to floating-point ClojureScript is asserted. Gradient and momentum
have no general convergence guarantee here. Forecasts and bird data remain illustrative.

## E-methanol certificate example

The optional study guide retains the methanol benchmark, using Jiri’s supplied
benchmark account (2026-10-23). This revision inspected the producer and design record, but did
not rerun the benchmark.
The plant diagram shows 120 MW of electrolysis, a 20 t hydrogen tank and a 10 t/h methanol loop,
with a 588 t weekly contract. Minimum loads and start-up costs make the schedule mixed-integer.
The speaker’s technical notes retain the tank’s initial/terminal inventory, conversion rates,
turndown and start-up assumptions.

Reported results: 52/52 weekly certificates accepted for 2023; largest reported gap EUR 0.26 on
an approximately EUR 388,000 week; relative tolerance one part per million; median check 0.64 s.
The four-stack variant reaches 51/52 weeks, with a 15 s median check (technical notes only).
No weekly chart or hourly schedule is drawn. Benchmark records were located in
`../rainbow/PLANT-CERTIFICATES`; the summary is documented in
`../rainbow/DESIGN-2026-10-18-proved-milp-certificates.md`.

The implementation in `../rh2edin-challenge/proofs/rainbow_bb_export.clj` separates the work:
our Clojure code builds the plant model; HiGHS finds the MIP incumbent; our branch-and-bound
producer builds a certificate tree using HiGHS LP solves and exact bound construction; Rainbow
checks the certificate. This is not a run of the presentation's gradient-descent or Bellman
algorithm. HiGHS is a third-party solver, not a Flexiana implementation.

**Scope:** the checker runs as a kernel-certified program, but the theorem that acceptance
implies optimality is still open. These are reported certificate-checking results, not completed
end-to-end formal optimality proofs. The model knows a whole week of prices in advance and omits
ramp limits, degradation, CO₂/compressor limits, grid fees and renewable-fuel sourcing rules.
Monthly certificates remain out of reach. The work checks solver answers; it does not claim to
outperform HiGHS at finding them. The study guide preserves these boundaries; the methanol benchmark is now offstage.

### Savings are a separate comparison

Slide 24 describes an assumed 50,000-tonne/year e-SAF plant near San Roque / Los Barrios,
in the Bay of Algeciras (36.18° N, 5.40° W). It combines historical hourly OMIE Spanish prices
for 2023–2025 with local ERA5/Open-Meteo wind and solar history and PVGIS calibration.
Electrolysis, storage, CO₂ supply and fuel production have explicit limits and costs;
equipment sizes and costs are assumptions, not a named customer's engineering design.

The displayed **€7.9m/year** is a design comparison: stochastic plant design versus B1,
a deterministic design based on an average year. The source value is
`out_of_sample.paired_B1_minus_main_eur_t.mean = 157.2451162402811` in
`../rh2edin-challenge/challenge-1-esaf/results/results.json`.
Multiplying by the assumed 50,000 tonnes/year gives €7,862,255.81/year. This total-cost
comparison includes modeled shortfall assumptions. Nine weather/price combinations
are evaluated; the price years were reused in tuning. It is an estimate, not realized
customer savings or a formal proof of annual savings.

Rainbow's recorded `c1/blend` result proves the modeled fuel-blending optimum:
28 units of e-SPK in a 60-unit blend, with no axioms reported. See
`challenge-1-esaf/proofs/rainbow-results.edn` and `proofs/bundles/c1_blend.bundle.edn`
in the sibling `rh2edin-challenge` repository. A separate exact checker accepted 24
operating LP certificates. Neither result certifies the full annual savings comparison.
These existing records were inspected, not rerun for this copy change.

The earlier €4.4m/year energy/backup-hydrogen operating comparison remains in
`speaker/plant-case-evidence.json` as historical evidence; it is a different baseline.
The requested €74m/year figure has not been traced to a result or baseline and is not
asserted. The source question remains pending.

Data references: [OMIE market results](https://www.omie.es/en/market-results/daily/daily-market/day-ahead-price?scope=daily),
[PVGIS hourly data](https://joint-research-centre.ec.europa.eu/photovoltaic-geographical-information-system-pvgis/using-pvgis-5/pvgis-5-tools/hourly-radiation_en),
and `../rh2edin-challenge/data/manifest.json`. OMIE wholesale prices are not a
renewable-specific supply-contract price. The separate methanol benchmark does not
certify this e-SAF study.

## Typography and slide copy

Aeonik and Aeonik Mono were copied from `../flexiana-web/site/public/fonts/` at Jiri's request.
The original WOFF2 files are in `fonts/`; `public/fonts.css` embeds them as data URLs for offline
use. Body/headings use Aeonik; code and numeric traces use Aeonik Mono. The available regular
faces are used, with browser-synthesized bold. Bundled DejaVu remains for missing glyphs;
its license does not apply to the Aeonik assets. Emoji coverage is still platform-dependent.

Visible text is intentionally brief; fuller explanations and proof assumptions remain in
speaker notes. OR's opening slide does not reveal the concrete examples. Slide 25 uses the
supplied `../flexiana-web/site/src/assets/book/book-wood-angle.web.webp` photograph in its
390 × 520 area. A byte-identical copy lives at `src/talk/book-photo.webp`; the build embeds it
as a data URL, so the book slide also works offline without adding a public asset endpoint.

## Study Validate and Rainbow

[Study guide](speaker/VALIDATE-RAINBOW-STUDY.md) and
[printable PDF](../artifacts/validate-rainbow-study.pdf) start with the shorter stage route (20–24), followed by optional background: state fields,
the decision table and controller call flow, reachability closure, proof syntax, OR theorem scope, generated Clojure, optional Bellman
contraction and residuals, solver attribution, and the precise boundaries of each claim.
Practice questions include answer checks. The full spoken script remains in `speaker/SCRIPT.md`.

`npm run study` runs examples against the deck's actual functions, including a counterexample
showing that the older context-specific shield trusts bad known rows. That limitation does
not apply to the current stage wrapper, which stops on every positive detection. The checked output is in
`speaker/study-results.json`; regenerate with `npm run study -- speaker/study-results.json`.
`python3 scripts/build-study.py` builds the offline HTML reading copy using pandoc.
`node scripts/print-study.cjs /path/to/playwright` regenerates its PDF with networking disabled.
These study files are kept outside the copied ngrok directory.

## Verification and remaining delivery work

Full-suite baseline (hof-ai-20261026): **22 tests / 2,154 assertions** passed. Offline
Chromium checks **156 frames** at three projector sizes. The dedicated shield check verifies
the detection threshold, actual commands, matching bird paths, rotor freeze, failure counts,
refresh and presenter speech. The release builds without warnings. The public bundle matches
the local build; current details are recorded in `speaker/VERIFICATION.md`.

Historical verification: the flow-20261022 revision passed **18 tests / 2,199 assertions**, including a regression that first
failed on the old pre-tick transition, closure checks for every controller, and speech coverage for
all **445 rendered phases**. The release builds without warnings.
Chromium checked **1,335 frames**: every phase at 1600×900, 1920×1080 and 1024×768, with networking
disabled. No overflow, clipped code, render errors or external requests. Playback, hash recovery,
theme, synchronized presenter controls, per-reveal speech and the current-hour marker passed.
Screenshots of changed code panels and proof slides were inspected. The step-size, momentum and Bellman source panels were inspected in their new positions. Public verification is recorded in
`speaker/VERIFICATION.md`; the tunnel configuration is unchanged.

The model tests independently enumerate two-week demand pairs, check supply-phase transitions and
purchase limits, compare finite-horizon policies against optimal backups, check gradient and penalty
calculations, and verify observer effects. The full script distinguishes numerical convergence,
model exploration, exact theorems and the unproved correspondence to browser arithmetic.

- Jiri: rehearse aloud against the 40-minute target.
- At the venue: check projector readability and controls.

The prior Function Observatory ZIP, PDFs and rehearsal worksheet describe an older presentation;
they must not be treated as current handoff assets. The current release in `talk/public` runs
from disk and is the offline fallback for this revision.
