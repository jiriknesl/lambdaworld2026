# Rebuilding Operations Research in Higher-Order Functions — the deck

Lambda World 2026, Jiri Knesl. One ClojureScript namespace (`src/talk/core.cljs`) holds the slides,
the algorithms they show and the drawing code; `src/talk/notation.cljc` holds the macros.
Demo values are computed by that code. Definition panels use the exact source of the running
function (captured at compile time by `src`). Explanatory calls and mathematical proof steps are
labelled separately; Rainbow verdicts are recorded evidence, not browser kernel execution.

Design and decisions: `../docs/superpowers/specs/2026-10-15-lambda-world-talk-design.md`.

## Public preview

[Open the revised deck](https://haphazard-sanction-pavestone.ngrok-free.dev/?v=inside-the-functions-20261019).
Updated 2026-10-19: numerical optimization walk-through, explicit hydrogen inventory model, visible shield behaviour and a literal Rainbow proof. This URL is **public and unauthenticated**;
the compiled deck includes all slides, displayed source, speaker notes and recorded proof evidence.
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
| N | notes window: speaker notes, next slide, clock (T starts/pauses); its keys drive the stage |
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
   - change the bird `budget`, `(penalize day-plan 100)` or the momentum `0.9`;
   - change `:discount 0.9` or a demand probability in `defbellman restock`;
   - remove `with-shield` from the third controller and watch reachability turn red.
3. A new random wind farm and flyway, from the REPL: `(talk.notation/random-map {:seed 7})`, then paste it.

The optimisation runs take a few seconds each. The deck computes them one state per animation
frame from the moment it opens (about half a minute in all), so they are ready long before the
Optimise section. After a hot reload they start again.

**Fallback.** Every slide's default example is compiled in. A broken edit shows shadow-cljs's error
overlay and the last good build keeps running: undo, save. If the watch process or the editor fails,
open the release build (`public/index.html`) — it needs nothing else.

## Where things are in `core.cljs`

| Section | What |
|---|---|
| 1. Rendering kit | hiccup → HTML, Clojure highlighting, formulas rendered from model source, SVG helpers |
| 2. The shared loop | `fixed-point` (iterate + step + converged?), Newton's √2 |
| 3. Optimise | tomorrow's stop plan for a wind farm on Andalusian hills: `defmap hills`, `defmodel day-plan`, `electricity-lost`, `bird-risk`, `penalize`, `gradient`, `descent-step`, `clamped`, `with-momentum`, `with-adam`; runs computed one state per frame; `schedule` draws the plan, `project` and `scene` play it in 3D |
| 4. Decide | `defbellman restock`, `bellman`, the stopping bound |
| 5. Verify | `table-decision`, `with-shield`, `tick-controller`, `react-to-detections`, `reachable`, rules (`always`, `implies`, `within`), the worst-case search |
| 6. Receipt | `defmodel trailers` (RH2EDIN Reto 2), `linear-rows`, `pivot`, `check-receipt`, recorded Rainbow verdicts |
| 7. Slides | 30 slides with speaker notes, following the revised arc below |
| 8. App | keyboard, URL, notes window, playback, scaling |

## Talk arc — revised 2026-10-19

The central question is **which function do we supply, and what does it change?**
The audience meets OR before Clojure, and higher-order functions before the examples.
Each example shows its problem, supplied function, visible updates and resulting behaviour.

| Block | Minutes | What the audience sees |
| --- | ---: | --- |
| Briefly about Jiri | 1 | Lifelong developer, economist by education; started Barcelona Meetup Group 13 years ago. |
| What OR is | 2 | Choices, objectives, constraints; three questions, with concrete examples kept as surprises. |
| Why Clojure | 2 | Functions as values, immutable states, the REPL and code as data. |
| HOFs → iterate → fixed point | 6 | A function returned by `newton-sqrt`, passed to `map`, then to `iterate`; reveal the state chain; distinguish an exact fixed point, a tolerance and a work limit. |
| One optimisation example | 8 | Wind-farm settings: map → model → one numerical update → penalty → momentum; computed schedule and 3D playback. |
| One decision-making example | 6 | Restocking: score each order, average possible demand, select the best; animate Bellman value tables and the resulting policy. |
| One validation example | 5 | Bird-stop controller: supply successors to reachability, watch the set grow, read a counterexample, inspect the shielded result. Close with the shared HOF structure. |
| Rainbow and a proof | 7 | Introduce statement/term/kernel; read a checked composition proof and rejected variant; return to Bellman’s contraction argument and recorded evidence. |
| Book and closing | 1 | Connect the three examples to *Applied Higher-Order Functions*. |
| Questions | 2 | Discussion. |

These sum to 40 minutes; they are targets, not measured speaking duration. The old long macro
introduction, Adam race, falsification demo, trailer/simplex/LP detour and open-core/Pro catalogue
are outside the spoken arc. Their supporting algorithms remain in the source and tests.

## Wind-farm model and notation

Stopping reduces both generation and collision exposure. The two goals compete. This demo chooses
**maximum electricity under a bird-risk limit**, implemented as minimum electricity lost; lowering
the limit prioritizes birds. The hourly `energy` forecast is illustrative kWh per fully running
turbine. `bird-risk` is an exposure index, not a calibrated count of birds killed. Revenue and
gearbox wear are no longer part of the objective.

`defmodel` is a macro in `src/talk/notation.cljc`. `minimize` and `subject-to` are syntax markers
consumed by that macro, not standalone functions or macros. Slide 10 shows the real helper
functions, explains the markers, and shows the generated objective/constraint functions.

The first run passes only `electricity-lost` to gradient descent. The next wraps it with
`penalize`: electricity lost + 100 × excess-risk², where excess-risk = max(0, risk − limit).
The same solver now trades some generation for less risk. The penalty is soft: the displayed
risk can exceed the limit. Slide 13 shows both objective definitions and a numerical comparison
with all turbines running. Formulas use named quantities and separate lines; Bellman's update
likewise separates outcome return, averaging and action selection.

Removing wear also removes the old assumption that different optimizers give nearly identical
coordinates. Tests now compare their objectives with an independent convex lower bound; each
gap must be below 1% of total available energy. Additional checks verify off/on/half-power energy
and risk directly. This is numerical validation, not a Rainbow certificate.

## Learning the optimization example

Start at [Build one update (slide 11)](https://haphazard-sanction-pavestone.ngrok-free.dev/?v=inside-the-functions-20261019#s11).
Use **Right** for each reveal, **N** for the fuller explanation. These are the layers:

| Expression | Takes | Returns |
| --- | --- | --- |
| `on-flat` | model containing an objective | a function from 72 settings to one cost |
| `gradient` | that cost function | a function from settings to 72 slopes |
| `descent-step` | gradient function and learning rate | a function from state to next state |
| `clamped` | step function and bounds | a step that keeps settings in 0…1 |
| `penalize` | model and penalty weight | a model with a new objective function |
| `with-momentum` | step function and carry factor | a step that also carries velocity in its state |
| `iterate` | step function and initial state | a lazy sequence of successive states |

A state initially looks like `{:x [0.5 0.5 …]}`: six turbines × twelve hours = 72 numbers.
A number is the fraction of operation for one turbine-hour. `as-plan` gives those numbers their
`[turbine hour]` keys. The objective scores the **whole day**, not just the selected turbine.

The numerical walk-through holds 71 settings fixed and changes turbine 1 at noon by ±0.00001.
At 50% everywhere, electricity lost is 5214 kWh. Nudging this coordinate down gives 5214.00084;
nudging up gives 5213.99916. The central difference is −84: more operation loses less electricity.
Subtract rate × slope: `0.5 − 0.0001 × (−84) = 0.5084`. This becomes 50.84% operation.
The code computes **every slope from the same old vector**, then produces all 72 new settings.
Clamping keeps them between 0 and 1. Repeating that pure step is the algorithm.

Slide 12 animates this electricity-only objective. It eventually runs everything. The `day-plan`
map contains a bird constraint, but gradient descent reads only its objective; the first run
intentionally demonstrates what happens when that constraint is not yet turned into a cost.

Slide 13 changes the supplied objective to electricity lost + `100 × excess-risk²`. Increasing
operation now can make the bird penalty grow faster than electricity loss falls, reversing the
slope. The code finds this by evaluating the new function; the gradient and step algorithms do
not know what birds are. Both baseline and penalized runs use the same rate (0.0001), bounds
and stopping test. This is a soft penalty, so it does not enforce the risk cap exactly.

Slide 14 changes the **step**, through `with-momentum`, rather than the objective. Call the old
step, measure its proposed movement, add 90% of the previous velocity, then add this new velocity
to the old settings. The numerical row below the schedule shows those three quantities for the
same turbine-hour. Velocity is stored in the returned state; there is no hidden mutable history.
The outer clamp clips position but does not reset velocity. Consequently stored velocity can
differ from actual position change at a bound. Momentum is not a convergence guarantee.

## Reading the decision and validation examples

Slides 16–19 stock a **hydrogen filling station**, in trailer loads. We observe storage, order
before uncertain weekly demand is known, then sell and carry leftovers. Capacity is four loads;
demand is 0/1/2/3 with probabilities 0.1/0.3/0.4/0.2. Prices/costs are illustrative money units.
For stock 1/order 2/demand 2: three loads available, two sold, one left. Profit is 20−8−1 = 11.

The score for an order averages `profit + 0.9 × V(leftovers)` across all four demand outcomes.
With `V = 0`, ordering two has profits −11/0/11/22 and expected profit 7.7. Compare every feasible
order, take the best score, and repeat for every stock level: that is one Bellman update.
**Iterations improve value estimates; they are not simulated weeks.** The eventual policy orders
up to three loads. The generated `q` function is displayed with its weighted `reduce`, followed
by the HOF that accepts this model and returns the value-table update.

Slides 20–22 validate a controller command: **a confident detection requires STOP within the same
modeled minute**. A timer-only controller can keep running for nine minutes. Reacting immediately
fixes that delay, but an unseen context has no row in the decision table and defaults to never
stopping. The shield delegates known contexts and stops on any detection in unseen contexts.
The slide shows its actual definition and both controllers' outputs for the same event.

Reachability then tries every modeled next event from every reached state, unions in the results,
and repeats until the set stops growing. Any reached state with confident detection + RUN is a
failure. The counterexample slide earns its place by turning the failed check into a **reproducible
bug report**: the path explains whether to fix timing or the decision rule. This finite check does
not model physical braking, sensor failure or bird survival, and is not a Rainbow kernel proof.

## The proof examples and their limits

Slide 25 shows actual Rainbow explicit term syntax from `src/talk/composition.edn`. It proves
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

Slides 26–28 return to the **Bellman contraction bound**: if two value functions are pointwise
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
speaker notes. OR's opening slide does not reveal the concrete examples. Slide 29 reserves a
390 × 520 area for the book photograph; replace its `.book-photo` placeholder with the supplied
image when available.

## Before the talk

Mechanics revision checks (2026-10-19): 12 tests / 448 assertions passed; the new checks cover
the displayed gradient update, momentum history, all restocking outcome decompositions and the
shield's same-event comparison. The Rainbow composition proof was accepted and its wrong-result
variant rejected. The release built without warnings. Chromium checked 261 frames across
1600×900, 1920×1080 and 1024×768: no overflow, clipped code, render errors or external requests;
navigation, playback, hash restore, theme and presenter controls passed. A separate browser
context with networking disabled completed every demo computation and checked the new numerical
walk-through, shield results and proof reveals. The refreshed public ngrok edition was checked
for the same content and working Rainbow/email links. These checks do not replace speaking the
talk aloud or testing the venue projector.

- Replace the book-photo placeholder when Jiri supplies the photograph.
- Emoji in the `defmap` source are drawn by the system emoji font; check alignment in Cursor's font.
- A timed spoken rehearsal and a projector check remain.
