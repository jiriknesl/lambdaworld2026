# Lambda World 2026 talk: design

Talk: **Rebuilding Operations Research in Higher-Order Functions in Clojure**, Jiri Knesl, 40 minutes.
Status: revision 3, implementing Jiri's revised arc from 2026-10-19. Replaces the "Function
Observatory" as the stage version. The outline below supersedes revision 2's macro-first opening
and long LP/proof catalogue.

## 1. Goal and audience

**Audience.** Software developers who like functional programming (Scala, Kotlin, JS, Haskell, a few
Lean developers). They know `map`/`filter`/`reduce`; fewer use monoids daily. They do **not** know
operations research (OR) and most do not write Lisp.

**Goal.** Leave the room able to read an optimisation, decision or verification algorithm as code they
already know: a state, a step, and a test for "done", run by `iterate`. Show why code-as-data makes
Clojure a good home for it, and what Rainbow is and is trying to become.

**Success criteria.**
- Every idea is explained in developer terms first; mathematical names come second, if at all.
- Jiri, a developer, can answer the likely questions on every slide (speaker notes carry the answers).
- Every animation shows real computation by the code on the slide. Code on slides is the exact source
  text of the running definitions, captured at compile time.
- The talk works with no REPL, no network and no server.
- Every claim about Rainbow matches Rainbow's own documentation and recorded results.

**Non-goals.** Teaching OR theory. Arguing that Clojure beats typed languages. Claiming Rainbow verifies
the browser code.

## 2. Delivery vehicle

One local page, built with **shadow-cljs**, in `talk/`.

| Piece | Path | Role |
|---|---|---|
| Slides, algorithms, rendering | `talk/src/talk/core.cljs` | one namespace, in clearly marked sections |
| Notation macros | `talk/src/talk/notation.cljc` | `src`, `defmap`, `defmodel`, `defbellman` |
| Page | `talk/public/index.html`, `talk/public/talk.css`, `talk/public/fonts.css` | stage and notes views |
| Build | `talk/shadow-cljs.edn`, `talk/package.json` | `watch` while working, `release` for stage |
| Tests | `talk/test/talk/core_test.cljs` | algorithm and notation tests (`:node-test`) |
| Browser check | `talk/scripts/smoke.cjs` | Playwright: every slide/step, hash restore, overflow, offline |

**Controls.** Up/Down: previous/next slide. Left/Right: previous/next step. Shift+Left/Right: 10 steps.
Space: play/pause the steps. Home/End: first/last step. `N`: notes window. `F`: fullscreen.
`D`: dark/light. `?`: key help.

**Location.** `#s{slide}a{step}` (slides and steps from 1). A refresh restores slide and step.

**Stage.** A fixed 1600×900 canvas, scaled to the window and letterboxed, so the layout is identical on
16:9 and 4:3 projectors. Aeonik and Aeonik Mono from flexiana-web are embedded; DejaVu covers missing glyphs. No font network requests.

**Working mode.** `npx shadow-cljs watch talk`: save `core.cljs`, the page hot-reloads and keeps slide
and step (state in a `defonce` atom). shadow-cljs's nREPL lets Cursor (Calva) evaluate forms in the page.

**Fallback principle.** Every slide's default example is compiled in; the REPL and live edits only add.
A bad edit shows its compile error in shadow's overlay and the previous build keeps running; undo and
save to recover. `npx shadow-cljs release talk` gives one JS file that runs from `file://`.

**Numbers.** Browser arithmetic is floating point. The trailer LP and receipt use integers (exact in
doubles). Kernel-checked results are shown as recorded Rainbow output.

## 3. Notation (`notation.cljc`)

- **`src`** wraps a top-level definition, `(src (defn step ...))`, and records its exact source text
  (from the file, by reader line/column metadata) so slides show code that cannot drift from what runs.
- **`defmap`** draws a problem: a legend (glyph → role) and rows of glyphs; expands to a map of positions.
  `random-map` prints a new `defmap` form from a seed, for pasting.
- **`defmodel`**: `(defmodel name [plan] (minimize|maximize e) (subject-to [cname c] ...))` with exactly
  `sum` (Σ), `for-all` (∀), and comparisons `<=`, `>=`, `=`. Expands to the objective `plan → number`,
  constraints returning one signed gap per row, the source form (for the formula renderer) and the
  pretty-printed expansion (for the "what the macro wrote" slide). Violations, penalties and, for linear
  models, LP coefficients (by evaluating rows at unit plans) are derived from these functions.
- **`defbellman`**: `(defbellman name [state states] [action actions] [outcome distribution] :reward e
  :next e :discount g)` expands to a Q-function `(V, s, a) → number`, the state list, action function
  and source.

## 4. Talk outline (40 minutes)

| Block | Min | Purpose |
| --- | ---: | --- |
| About Jiri | 1 | Brief work/book/Rainbow introduction. |
| Operations research | 2 | Define choices, objectives, constraints and uncertainty before algorithms. |
| Why Clojure | 2 | Explain the choice through functions, immutable data, REPL and macros. |
| Higher-order functions | 6 | Accept/return functions; map vs iterate; visible state chain; exact fixed point vs tolerance; fixed-point takes step and converged?. |
| Optimisation | 8 | One wind-farm planning example: problem, model, step, penalty, momentum, schedule and 3D result. |
| Decision-making | 6 | One restocking example: possible orders, expected scores, Bellman updates, policy. |
| Validation | 5 | One bird-controller example: successors → growing reachable set → counterexample → shielded result. Summarise what changes inside the same HOF structure. |
| Rainbow and proof | 7 | Introduce theorem prover and kernel; return to Bellman; show a literal composition proof and rejected variant, then the Bellman contraction argument and recorded check. |
| Book / close | 1 | Three areas in the book; return to the HOF takeaway. |
| Questions | 2 | Discussion. |

The revised deck has 30 slides. HOFs are the subject throughout. Each example must say which
function is passed or returned and show the resulting computation. The macro is a way to describe
the optimisation problem, not a separate introduction to Lisp. Adam, falsification, trailer LP,
proof-size metrics and product catalogues are outside this spoken arc. Supporting code can stay.

## 5. Visuals and proof walkthrough

- `newton-sqrt` returns an update function. `map` calls it on independent guesses; `iterate`
  connects the calls. Reveal five computed states before introducing a stopping test.
- Wind-farm plan: a source grid becomes positions and the 3D scene. A schedule shows each turbine's
  setting by hour. The plain objective ignores birds; the penalty changes the objective; momentum
  wraps the step. No claim that the soft penalty ensures feasibility or momentum always helps.
- Restocking: fix stock at one load and show the actual Q score for every allowed order. The q
  function folds uncertain demand; max selects the new value. Then animate all stock values and
  the policy. The future value function is an argument to the update.
- Controller: `reachable` receives a successor function; mapcat expands states and into unions
  them. Show reached states and rule violations, then a readable counterexample. Compare the
  same finite model under tick-only, reacting and shielded controllers. Set equality stops it.
- Rainbow: statement + proof term → kernel → acceptance/rejection. State the Bellman bound,
  illustrate a 10-to-9 value gap using the actual browser function, then prove the general bound:
  identical rewards cancel; nonnegative normalized outcome averaging preserves γ ε; the maximum
  preserves the one-sided bound; swap V and W. Explicit model/action/discount assumptions.
- A small composition proof is shown in literal Rainbow syntax, from a checked EDN file. Its
  successful kernel check and wrong-result rejection are recorded against clean revision
  `d9080c7`; the build verifies the displayed source matches its receipt. This introduces syntax
  before the larger Bellman argument. The Bellman outline remains mathematical prose. Its
  `value/signed-optimality-step-additively-contractive` receipt is the 2026-10-09 export at
  `3f411ae`, with empty axiom closure; it was not rechecked in this revision. Neither example
  claims a proof of floating-point browser correspondence.

## 6. Testing

Node tests: `defmap`/`defmodel`/`defbellman` expansions; flock converges, penalty spreads, Adam/momentum
run; restocking policy order-up-to and residual stop; reachability finds violations for the naive and
unshielded controllers and none for the full one; falsification finds the near-tick time; LP gives 127,
prices balance, tampered receipt refused. Browser check: every slide and short reveal, sampled long traces, hash restore,
no overflow at 1600×900, 1920×1080 and 1024×768, no external requests, release build from `file://`.

## 7. Existing presentation and prototypes

The "Function Observatory" app stays untouched until the new talk passes its checks. `prototypes/` is
throwaway and is removed when the new talk is complete.

## Editorial update — 2026-10-19

Jiri's introduction: lifelong software developer, economist by education, started Barcelona Meetup
Group 13 years ago. OR's opening gives the questions without revealing the example problems.
Why Clojure has four points: simple and functional language; immutable data; the REPL; code is data.
Keep titles and visible prose short, with explanation in speaker notes. The book slide reserves
space for Jiri's forthcoming photograph. Use Aeonik for text and Aeonik Mono for code.

## Clarity update — 2026-10-19

Remove the opening subtitle. Increase agenda-to-heading space and the gap after OR's opening
prompt. Explain fixed points through the actual Newton step for √4: 3 changes, 2 returns 2.
The optimisation goal is maximum electricity under a chosen bird-risk limit. Remove revenue
and wear from the objective, label illustrative energy in kWh and bird risk as a proxy. Explain
minimize/subject-to as markers in defmodel, with its actual generated functions visible. The
penalty slide must show before/after objectives and electricity/risk numbers; replace long
expanded sums with separate, named quantities.

## Mechanics update — 2026-10-19

Before animating optimization, trace one coordinate: state → whole-plan cost → numerical slope →
gradient update → clamp → iterate. At noon, 50% becomes 50.84% under the electricity-only objective.
Keep both baseline and penalized learning rates identical. The momentum replay shows the base
movement, carried velocity and their sum. The outer clamp changes position, not stored velocity.

Restocking must name the commodity (hydrogen), units (trailer loads), timing (order before demand),
and uncertainty (weekly customer demand). Show one profit calculation and its weighted outcome
sum before maximizing over orders. Split the Bellman equation into return, average and maximum.

Validation must put its rule on screen: confident detection requires STOP in the same modeled
minute. Show the actual shield definition and run the same unseen-context detection with/without
it. Then exhaust the finite state space. The counterexample is a reproducible bug report that
explains the failed rule. Name iterate explicitly in the shared-structure slide.

Link Rainbow to https://flexiana.com/rainbow. Close with Thank you and jiri@flexiana.com; remove
the old state/step/converged slogan. Fuller learning explanations live in talk/README.md and notes.
