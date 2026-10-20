# Lambda World 2026: presentation design

Revision 4, 2026-10-20. This replaces the earlier macro-first and Function Observatory outlines.
Talk: **Rebuilding Operations Research in Higher-Order Functions in Clojure**, Jiri Knesl, 40 minutes.

## Audience and purpose

Clojure, Elixir and Haskell developers, plus some Lean 4 developers. Teach the algorithms through
higher-order functions. The audience should see what each supplied function does, watch successive
states, and understand why separating the update from traversal helps them change the program.

## Agreed arc

1. Who Jiri is: lifelong developer, economist by education, Barcelona Meetup Group 13 years ago.
2. Why Clojure: simple and functional, immutable data, REPL, code as data.
3. OR: optimization, decision-making, validation. Keep the examples as surprises.
4. HOFs: functions as inputs/outputs; `map` versus `iterate`; exact fixed point, numerical tolerance.
5. Optimization: turbine operation versus birds; actual numerical derivatives; compare successive plans.
6. Decision-making: hydrogen delivery and customer trucks; uncertain demand; improved value estimates and orders.
7. Validation: search reachable states for confident detection + RUN; compare controllers and shield.
8. Name `iterate` underneath all three, then demonstrate composition: pace, momentum, logging.
9. Rainbow: a theorem prover written in Clojure; actual proof syntax; Bellman property and evidence.
10. Book: more than `iterate`, about twelve HOFs and their composition; photo placeholder.
11. Thank you: jiri@flexiana.com.

The 32-slide timing worksheet is the table in `talk/README.md`. The three examples precede the
composition payoff. Avoid teaching a custom macro language or implying that functional languages
other than Clojure cannot express the same ideas.

## Concrete explanations and visuals

- Optimization uses ordinary functions. No `minimize`/`subject-to` DSL on stage, no symbolic
  differentiation or Hessians. Show one coordinate's cost samples, numerical slope, update and
  clipping. Compare energy-only and penalized schedules side by side using actual states.
- The bird penalty is soft. State what changes in the cost and what remains the same in the
  comparison. Illustrative exposure is not mortality. A small update does not prove optimality.
- The hydrogen example shows the tank, delivery tanker, served and unserved trucks. State the
  commodity, units, action, uncertainty, prices and inventory carryover. Show ordinary reward,
  expectation and Bellman functions. Value iterations improve a plan; they are not simulated weeks.
- Use two supplier windows: regular (price 2, limit 3), tight (price 6, limit 1), alternating.
  Tank capacity stays four. Demand is a discrete quiet/convoy mixture (0/.40, 1/.15, 3/.45),
  not a normal distribution. State includes supply phase and inventory. The same Bellman HOF
  updates ten values. Show why one unit in stock calls for buying three in the regular week
  and nothing in the tight week. Compare feasible fill-to-2/3/4 and myopic rules against the
  learned policy; show the actual modest gain over fill-to-three without exaggeration.
- Reachability has an explicit target: a reachable command state with confident detection and RUN.
  Show branching events and expansion counts, the shield source and its effect. The graph is one
  sample branch point, not the full state graph. Set equality ends the exhaustive finite search.
- Composition changes a function argument or wraps the update; it does not rewrite the traversal.
  Compare momentum using measured update counts and costs, without claiming general superiority.
  Logging is an intentional effect on realization, illustrated with a captured four-entry log.

## Rainbow and the book

Dynamic typing does not prevent Clojure from hosting a proof checker. Static typing can catch
useful errors; formal proofs establish particular claims under assumptions. Neither language
choice nor one theorem implies an application is bug-free.

Show the checked composition term from `talk/src/talk/composition.edn`, including a rejected
wrong-result variant. Its receipt is tied to the exact source and Rainbow revision `d9080c7`.
Then connect the hydrogen example to the Bellman contraction bound: identical rewards cancel,
nonnegative normalized averaging preserves the discounted bound, max preserves it, and swapping
inputs gives the reverse inequality. Show the existing `3f411ae` Bellman receipt from 2026-10-09.
It checks an exact rational mathematical term; correspondence with floating-point browser code
is unproved. The browser shows recorded verdicts, not an embedded Rainbow kernel.

Link to https://flexiana.com/rainbow. The book broadens the view from `iterate` to composition,
partial application, mapping, filtering, reduction and other HOFs. Reserve space for Jiri's photo.

## Delivery and verification

`talk/src/talk/core.cljs` owns the algorithms, SVG visualizations, slides and speaker notes.
`src` captures actual implementation source for definition panels. Explanatory calls are separate.
The release runs from disk, offline, without a REPL or server. Aeonik and Aeonik Mono are embedded;
embedded DejaVu covers missing glyphs. Native SVG vehicles avoid platform emoji dependencies.

Controls: up/down changes slide; left/right changes reveal; Shift+left/right jumps ten updates;
Space plays; Home/End selects first/last; N opens synchronized presenter notes; D switches theme.
Hashes restore slide/reveal after refresh. The 1600×900 canvas scales and letterboxes at 4:3.

Run algorithm tests, build the release, and check all slides and short reveals plus sampled long
traces at 1600×900, 1920×1080 and 1024×768, with networking disabled for the disk build. Inspect
new diagrams visually in addition to overflow and render-error checks. Maintain the existing
public static ngrok preview without changing its tunnel configuration; it exposes the compiled
deck, notes and evidence without authentication. Public URL and file inventory live in README.

Book photo, aloud rehearsal and room/projector checks remain external inputs. Older prototype
handoff assets are superseded and must not be described as matching this revision.

## Model-clarity corrections — 2026-10-20

The optimizer's 50% settings are an arbitrary starting guess. The supplied generation forecast
represents availability from wind; settings retain a fraction of it. Do not imply we choose wind
speed or that every physical turbine initially runs at half power. Show per-turbine/hour bird
exposure: proximity to the flyway scales a common daily traffic pattern. One gradient update
returns all 72 new settings using the same old vector.

Before teaching the cost wrapper, compare two actual plans: retain half everywhere versus stop
T3 at noon as well. The latter loses another 42 kWh but wins under the chosen bird penalty. Explain
12 and 100 as modelling choices, not physical constants or a hard constraint.

Restore the current-hour rectangle on the day replay and test its synchronization with the 3D
clock on both reveals. Label Bellman bars as expected future profit in money units, show regular
and tight supply windows separately, and read stock one with explicit profit and order values.

Introduce validation before controller code: a sensor reports a confident bird detection; the
controller must issue STOP in the same modeled minute. Show the timer-only failure, then ask
whether any modeled event sequence breaks that rule. Bird detection is input, not the classifier
being validated. This separate controller example does not verify the optimized day schedule.
