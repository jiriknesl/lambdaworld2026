# Lambda World 2026 talk: design

Talk: **Rebuilding Operations Research in Higher-Order Functions in Clojure**, Jiri Knesl, 40 minutes.
Status: agreed in conversation on 2026-10-15 (revision 2). Replaces the "Function Observatory"
presentation as the stage version.

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
16:9 and 4:3 projectors. Fonts (DejaVu Sans, Sans Mono) are embedded; no network requests.

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

| Block | Min | Slides |
|---|---|---|
| About me | 3 | title; who I am and why OR + FP |
| Why Clojure | 5 | code is data; `defmap` (source → data → picture); `defmodel` (source → formula → expansion); effects at the edges |
| The shared loop | 3 | maths-to-code dictionary; `fixed-point` = `iterate` + step + `converged?`, warm-up √2 |
| Optimise: birds and food | 5 | model; plain gradient step, birds pile up; `penalize`; `with-momentum`; `with-adam`; race of the three steps |
| Decide: restocking | 5 | `defbellman`; value iteration frames (values, policy, residual); order-up-to policy |
| Verify: bird stop controller | 5 | controller as decorators; reachability as a fixed point (naive → reacting → shielded); the 9-minute counterexample; falsification search with a robustness rule |
| Gotchas | 1 | may never settle; settled ≠ correct; `converged?` is part of the spec |
| Rainbow | 9 | who checks the checker; trailer model + solver as a fixed point; the receipt and checker; a theorem is a function type + kernel verdict; `converged?` with a guarantee; refused fake proof; what is not proved (maturity levels); open core vs Pro; the goal |
| The book | 2 | *Applied Higher-Order Functions*: parts mirror the talk |
| Questions | 2+ | closing card |

## 5. Demos

**Birds and food (optimise).** Drawn with `defmap`. Model: minimise Σ ‖b − food‖² subject to
∀ pairs ‖a − b‖ ≥ 1.2. Plain gradient steps pile the birds on the food (the model never said "keep
apart" as a cost); `(penalize model w)` spreads them. Steps are decorators `Step → Step`:
`with-momentum`, `with-adam`. A race compares steps-to-converge. Not claimed: convergence proofs.

**Restocking (decide).** A station holds up to 4 trailer loads; random weekly demand; revenue, order and
holding costs; discount 0.9. Value iteration with `converged?` = largest change < ε. Frames show values,
the policy (order up to a level) and the residual against ε. The final frame quotes the stopping bound
ε/(1 − γ) from Rainbow's theorem, labelled as a theorem about exact rationals, not a check of this run.

**Bird stop controller (verify).** Simplified from `wildlife.edge` (Reto 3): 10-minute slots, detection
levels none/low/high, a stop-probability table with one draw per slot, and a fallback for contexts the
table has never seen. Controllers are composed: `table-controller`, `react-to-detections`, `with-shield`.
Rule: a high-confidence detection is answered by STOP within the same minute. Reachability (`fixed-point`
over sets of states, `converged?` = `=`) finds the 9-minute gap in the tick-only controller, a
never-stopping gap without the shield, and nothing for the full composition. Falsification: a search over
detection times, scored by the rule's robustness (seconds to spare), converges to "just after a tick".
Bird data in Reto 3 is synthetic; the slide says so.

**Trailer plan (Rainbow section).** The RH2EDIN Reto 2 instance in `defmodel`; LP coefficients read from
the model; simplex as `fixed-point` of a pivot (objective 0, 0, 56, 94, 127); the receipt
15·8 + 2·19 + 1·33 − 8·8 = 127 built term by term; the checker accepts it and refuses a tampered price.
Kernel verdict from `rainbow-results.edn`: certified, axioms `#{}`. Proof size is described as measured:
about 620 distinct subterms from 26 lemmas.

## 6. Testing

Node tests: `defmap`/`defmodel`/`defbellman` expansions; flock converges, penalty spreads, Adam/momentum
run; restocking policy order-up-to and residual stop; reachability finds violations for the naive and
unshielded controllers and none for the full one; falsification finds the near-tick time; LP gives 127,
prices balance, tampered receipt refused. Browser check: every slide and step reachable, hash restore,
no overflow at 1600×900, 1920×1080 and 1024×768, no external requests, release build from `file://`.

## 7. Existing presentation and prototypes

The "Function Observatory" app stays untouched until the new talk passes its checks. `prototypes/` is
throwaway and is removed when the new talk is complete.
