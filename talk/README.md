# Rebuilding Operations Research in Higher-Order Functions — the deck

Lambda World 2026, Jiri Knesl. One ClojureScript namespace (`src/talk/core.cljs`) holds the slides,
the algorithms they show and the drawing code; `src/talk/notation.cljc` holds the macros.
Demo values are computed by that code. Definition panels use the exact source of the running
function (captured at compile time by `src`). Explanatory calls and mathematical proof steps are
labelled separately; Rainbow verdicts are recorded evidence, not browser kernel execution.

Design and decisions: `../docs/superpowers/specs/2026-10-15-lambda-world-talk-design.md`.

## Public preview

[Open the revised deck](https://haphazard-sanction-pavestone.ngrok-free.dev/?v=flow-20261022).
Updated 2026-10-22: step size and momentum now sit inside the optimization example (slides 12
and 15). The Bellman walkthrough shows its value-table input/output and the separate best-order
extraction (slide 19, reveals 4–5). The spoken script and PDF follow the new order.
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

## Talk arc — revised 2026-10-22

HOFs are the subject. Step size and momentum are integrated into optimization; observation follows the three examples.
The deck has 32 slides; reveals are steps within a slide, not additional topics.

| Slides | Estimated time | What the audience sees |
| --- | ---: | --- |
| 1–2: Jiri | 0:44 | Developer, economist, Barcelona meetup and interviews. |
| 3: Clojure | 0:28 | Why I chose it; no language prerequisite. |
| 4: OR | 0:37 | What optimization, decisions and validation ask. |
| 5–8: HOFs | 4:14 | Function construction, map, iterate and stopping. |
| 9–16: Optimization | 10:17 | Build the update, choose its pace, change the objective, compare momentum, replay the day. |
| 17–21: Decision-making | 8:31 | Supply cycle, demand, value-table updates, explicit policy extraction and comparison. |
| 22–24: Validation | 5:35 | Controller rule, reachable states and shared iterate. |
| 25: Observation | 1:06 | Observe a supplied step without changing its result. |
| 26–30: Rainbow | 5:26 | Composition proof, Bellman bound and its exact residual consequence. |
| 31–32: Book and thanks | 0:51 | Broader toolkit and contact. |

The current 4,179-word script estimates **37:49 including visual holds**, or **39:49 with two
minutes for questions**, at 125 words/minute. This is calculated, not an aloud rehearsal. Slower
speech needs shortening; see `speaker/timing.json` and `speaker/ANSWERS.md`. The macro compiler,
Adam race, falsification and trailer LP remain offstage.

## Spoken script and revision record

- [Full speech](speaker/SCRIPT.md): 32 slides, 81 cues. Numerical phase ranges have one passage to
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

Start at [Optimization is iterate (slide 11)](https://haphazard-sanction-pavestone.ngrok-free.dev/?v=flow-20261022#s11).
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
old vector. One call returns 72 updated settings, then the clamp limits them to 0…1. Slide 11
shows all six noon settings changing together. Other hours update in the same call.

Slide 10 visualizes all turbine-hour exposure inputs. Risk at full operation is `exposure(t) ×
birds(h)`: location relative to the flyway gives each turbine a different weight, while all share
one hourly traffic pattern. These are exposure indices, not independent probability distributions.

Slide 13 compares two real candidate plans. A retains half everywhere; B only changes T3 at noon
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

Slide 12 changes the rate immediately after gradient descent is introduced. Slide 15 follows the
plain risk-aware run with the question of whether momentum can reduce update count. Momentum
calls the base step once, adds 90% of the old velocity to its proposed displacement, and returns
new position and velocity. The outer clamp clips position, not velocity. The cost chart and update
counts compare actual runs with the same start/rate/tolerance; fewer updates here do not promise
universal improvement or a wall-clock speedup.

The observer example records four costs from five realized states, including the start. Its
callback deliberately writes to an atom; the underlying numerical step remains pure. Reusing
cached states does not repeat logging. A new realization of a new sequence runs the effects again.

## Reading the decision and validation examples

Slides 17–21 concern a **hydrogen filling station** throughout. The physical tank holds four
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

Slide 18 poses the non-obvious choice: in a regular week with one unit, order two to cover any
current convoy, or three to carry an extra unit into tight supply? All possible next inventories
are shown. Slide 19 scores those orders. With zero future values order two wins; after three value
backups order three wins. The fourth reveal exposes Bellman’s two levels: the model constructs an
update function, which maps ten old profit estimates to ten new best scores. The fifth reveal shows
actual `best-order` source: `max-key` retains the winning order, whereas `max` retains its score.
Both use the same q function. `best-order` returns a state-to-order map without modifying V.
**Iterations refine a plan; they are not weeks or actual purchases.**

Slide 20 shows expected future profit, in money units, with a labelled bar for each `[week stock]`.
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

Slide 21 compares five feasible rules over twenty weeks, starting at `[:regular 1]`. Expected
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

Slides 22–23 validate controller commands: a confident detection must produce STOP in the same
modeled minute. Slide 22 first identifies the sensor input, controller and output command,
and demonstrates a failure before showing code. Bird recognition is assumed input; the property
concerns the controller response. This is a separate wind-farm controller, not a verification of
the optimized day schedule. A timer-only controller delays; immediate reactions fix delay but an unseen
context has no row in its learned table. The shield handles that missing case. Its actual source
and the same event's outputs with/without shielding are shown.

Reachability asks whether **any event sequence can reach confident detection + RUN**. Each step
unions in successors; equality detects when no new states can be added. The graph illustrates one
branch point; the accompanying counts cover the full finite search. Tick-only and unshielded
controllers fail. The shielded set closes without a failure. This covers encoded command states,
not physical braking or bird survival. The separate counterexample slide has been removed. Detection at the end of a slot is retained
before a separate tick resets it; a regression test prevents a reset from hiding that failure.
Slide 23 has three reveals per controller (nine total), with selected expansion rounds.

Slide 24 names `iterate` underneath optimization, value iteration and reachability. Their states,
update functions and stopping tests differ. Slide 25 observes an update through the common interface;
rate and momentum have already demonstrated composition within the optimization example.

## The proof examples and their limits

Slide 27 shows actual Rainbow explicit term syntax from `src/talk/composition.edn`. It proves
function composition for arbitrary propositions P, Q, R: given `f : P → Q`, `g : Q → R` and `p : P`,
`g(f(p))` proves R. `:forall` binds propositions, `:->` is implication, `:fn` binds arguments and
`:app` applies a term. The containing `:type`/`:value` map is the checker script's input.

`src/talk/composition-check.edn` records a fresh kernel check against the clean Rainbow revision
`d9080c7285c7cd28a81e2d184b25ecae333510fd`, in an empty environment with no axioms. Returning only
`f(p)` is rejected because Q does not establish R. Both proof terms are elaborated and checked;
these verdicts are recorded in the deck, not executed by a browser kernel. Reproduce from the
**repository root** (requires the local Rainbow checkout):

```sh
clojure -Sdeps '{:paths ["../rainbow/rainbow/src"]}' -M talk/scripts/check-proof.clj
```

The build embeds the exact checked file and fails if it differs from the receipt. This small
proof teaches the syntax; it is not presented as a proof of the optimization or Bellman code.

Slides 28–30 return to the **Bellman contraction bound**: if two value functions are pointwise
within ε, one update puts them within γ × ε. The four-step mathematical outline cancels identical
rewards, averages bounded future differences with normalized nonnegative probabilities, takes
max over the same nonempty action sets, then swaps V/W for the reverse inequality. Strict
contraction additionally requires 0 ≤ γ < 1. The numerical 10 → 9 example runs the browser's
Bellman function; it is an illustration, not a proof for every input.

The Bellman receipt is the existing `../resources/rainbow-evidence.edn`, recorded 2026-10-09 at
Rainbow `3f411ae`: `value/signed-optimality-step-additively-contractive`, with empty axiom closure.
That exact-term theorem was **not rerun in this revision**. No correspondence proof to the
floating-point ClojureScript implementation is asserted. Gradient and momentum have no general
convergence guarantee here. Illustrative forecasts and synthetic bird data remain illustrative.

## Typography and slide copy

Aeonik and Aeonik Mono were copied from `../flexiana-web/site/public/fonts/` at Jiri's request.
The original WOFF2 files are in `fonts/`; `public/fonts.css` embeds them as data URLs for offline
use. Body/headings use Aeonik; code and numeric traces use Aeonik Mono. The available regular
faces are used, with browser-synthesized bold. Bundled DejaVu remains for missing glyphs;
its license does not apply to the Aeonik assets. Emoji coverage is still platform-dependent.

Visible text is intentionally brief; fuller explanations and proof assumptions remain in
speaker notes. OR's opening slide does not reveal the concrete examples. Slide 31 reserves a
390 × 520 area for the book photograph; replace its `.book-photo` placeholder with the supplied
image when available.

## Verification and remaining delivery work

The flow-20261022 revision passes **18 tests / 2,199 assertions**, including a regression that first
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

- Supply the book photograph to replace its placeholder.
- Jiri: rehearse aloud against the 40-minute target.
- At the venue: check projector readability and controls.

The prior Function Observatory ZIP, PDFs and rehearsal worksheet describe an older presentation;
they must not be treated as current handoff assets. The current release in `talk/public` runs
from disk and is the offline fallback for this revision.
