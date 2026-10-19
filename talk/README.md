# Rebuilding Operations Research in Higher-Order Functions — the deck

Lambda World 2026, Jiri Knesl. One ClojureScript namespace (`src/talk/core.cljs`) holds the slides,
the algorithms they show and the drawing code; `src/talk/notation.cljc` holds the macros.
Demo values are computed by that code. Definition panels use the exact source of the running
function (captured at compile time by `src`). Explanatory calls and mathematical proof steps are
labelled separately; Rainbow verdicts are recorded evidence, not browser kernel execution.

Design and decisions: `../docs/superpowers/specs/2026-10-15-lambda-world-talk-design.md`.

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
| 3. Optimise | tomorrow's stop plan for a wind farm on Andalusian hills: `defmap hills`, `defmodel day-plan`, `wear`, `penalize`, `gradient`, `descent-step`, `clamped`, `with-momentum`, `with-adam`; runs computed one state per frame; `schedule` draws the plan, `project` and `scene` play it in 3D |
| 4. Decide | `defbellman restock`, `bellman`, the stopping bound |
| 5. Verify | `table-decision`, `with-shield`, `tick-controller`, `react-to-detections`, `reachable`, rules (`always`, `implies`, `within`), the worst-case search |
| 6. Receipt | `defmodel trailers` (RH2EDIN Reto 2), `linear-rows`, `pivot`, `check-receipt`, recorded Rainbow verdicts |
| 7. Slides | 29 slides with speaker notes, following the revised arc below |
| 8. App | keyboard, URL, notes window, playback, scaling |

## Talk arc — revised 2026-10-19

The central question is **which function do we supply, and what does it change?**
The audience meets OR before Clojure, and higher-order functions before the examples.
Each example shows its problem, supplied function, visible updates and resulting behaviour.

| Block | Minutes | What the audience sees |
| --- | ---: | --- |
| Briefly about Jiri | 1 | Work, book and Rainbow; a short introduction. |
| What OR is | 2 | Choices, objectives, constraints and uncertainty; the three questions for today. |
| Why Clojure | 2 | Functions as values, immutable states, the REPL and code as data. |
| HOFs → iterate → fixed point | 6 | A function returned by `newton-sqrt`, passed to `map`, then to `iterate`; reveal the state chain; distinguish an exact fixed point, a tolerance and a work limit. |
| One optimisation example | 8 | Wind-farm settings: map → model → gradient step → penalty → momentum; computed schedule and 3D playback. |
| One decision-making example | 6 | Restocking: score each order, average possible demand, select the best; animate Bellman value tables and the resulting policy. |
| One validation example | 5 | Bird-stop controller: supply successors to reachability, watch the set grow, read a counterexample, inspect the shielded result. Close with the shared HOF structure. |
| Rainbow and a proof | 7 | Introduce statement/term/kernel, return to Bellman, walk through its contraction argument, show recorded evidence and a rejected fake proof. |
| Book and closing | 1 | Connect the three examples to *Applied Higher-Order Functions*. |
| Questions | 2 | Discussion. |

These sum to 40 minutes; they are targets, not measured speaking duration. The old long macro
introduction, Adam race, falsification demo, trailer/simplex/LP detour and open-core/Pro catalogue
are outside the spoken arc. Their supporting algorithms remain in the source and tests.

## The proof example and its limits

The Rainbow example is **the Bellman contraction bound from the decision-making demo**:
if two value functions are pointwise within ε, one update puts them within γ × ε.
The proof is revealed in four steps: cancel identical rewards; average bounded future differences
with normalized nonnegative probabilities; take the maximum over the same admissible actions;
swap the two functions for the reverse inequality. Nonempty admissible actions and the finite
model assumptions are explicit. Strict contraction additionally requires 0 ≤ γ < 1.

The numerical 10 → 9 illustration runs the deck's Bellman function on two tables. It is not the
proof. The four-step explanation is readable mathematics, not literal Rainbow syntax. The
kernel receipt comes from `../resources/rainbow-evidence.edn`, recorded 2026-10-09 against
Rainbow commit `3f411ae`: `value/signed-optimality-step-additively-contractive`, checked with
empty axiom closure. That proves a claim about exact terms. No correspondence proof to the
floating-point ClojureScript implementation is asserted, and this revision does not rerun the
kernel. The fake-proof rejection is also the existing recorded momentum check, labelled as such.

Gradient and momentum have no convergence guarantee here. The controller result exhausts only
its encoded finite model, not all physical turbine behaviour, and is not a kernel proof.
The Reto 3 inspiration uses synthetic bird passages. Illustrative forecasts remain illustrative.

## Before the talk

Revision checks (2026-10-19): release build completed without warnings; 8 tests / 356 assertions
passed. The browser check exercised 237 frames across 1600×900, 1920×1080 and 1024×768,
including every short reveal and sampled long traces. No render errors, clipped code panels or
overflow remained; hash restore, navigation, playback, theme and presenter controls passed.
The release loaded from disk with no external requests. This does not replace the spoken or
physical-projector rehearsal, and the old app's hostile-room audit does not certify this new deck.

- Confirm the wording of the short personal introduction.
- Emoji in the `defmap` source are drawn by the system emoji font; check alignment in Cursor's font.
- A timed spoken rehearsal and a projector check remain.
