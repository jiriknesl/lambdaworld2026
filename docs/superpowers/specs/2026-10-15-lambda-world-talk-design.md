# Lambda World 2026: presentation design

Revision 6, 2026-10-23. This replaces the earlier macro-first and Function Observatory outlines.
Talk: **Rebuilding Operations Research in Higher-Order Functions in Clojure**, Jiri Knesl, 40 minutes.

## Audience and purpose

Clojure, Elixir and Haskell developers, plus some Lean 4 developers. Teach the algorithms through
higher-order functions. The audience should see what each supplied function does, watch successive
states, and understand why separating the update from traversal helps them change the program.

## Agreed arc

1. Who Jiri is: lifelong developer, economist by education, Barcelona Meetup Group 13 years ago.
2. Why Clojure: simple and functional, immutable data, REPL-oriented programming, code as data.
3. OR: optimization, decision-making, validation. Keep the examples as surprises.
4. HOFs: functions as inputs/outputs; `map` versus `iterate`; exact fixed point, numerical tolerance.
5. Optimization: turbine operation versus birds; build the update, choose its pace, compare costs and then momentum; replay the resulting day.
6. Decision-making: hydrogen delivery and customer trucks; uncertain demand; improved value estimates and orders.
7. Validation: search reachable states for confident detection + RUN; compare controllers and shield.
8. Name `iterate` underneath all three, then show observation through the common step interface.
9. Rainbow: actual proof syntax; Bellman property and evidence; a concise e-methanol certificate example with explicit proof boundaries.
10. Book: more than `iterate`, about twelve HOFs and their composition; photo placeholder.
11. Thank you: jiri@flexiana.com.

The 36-slide timing worksheet is the table in `talk/README.md`. Step size and momentum belong within optimization; the shared structure is named after all three
examples. Avoid teaching a custom macro language or implying that functional languages
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


## Speech and audience review — 2026-10-21

Assume FP familiarity, no Clojure or OR prerequisites, and mathematically knowledgeable listeners.
The complete spoken script is `talk/speaker/script.json`, exported as SCRIPT.md and printable HTML/PDF,
and embedded in the presenter with per-phase cues. Twenty-one recorded editorial passes introduced
notation at first use, staged the hydrogen model, distinguished Bellman n-week values from n+1-week
action scores, explained the max proof without undefined Q notation, and connected the exact bound
to a useful residual consequence without claiming a floating-point certificate.

The validation review found and fixed a real model issue: the last detection in a timer slot was
reset before its unsafe command state entered reachability. `controller-successors` now retains that
state before a separate tick transition. A test demonstrated the old failure, and all final reachable
sets are tested for closure. Slide 21 uses nine phases rather than twelve, removing repetitive updates.
The full deck has 444 rendered phases covered by 80 speech cues, including continuous animation ranges.

The 4,103 spoken words plus holds estimate 37:09 at 125 wpm, leaving two minutes for questions within
40 minutes. This is not an aloud measurement; slower delivery needs the provided shortening guidance.


## Flow and Bellman outputs — 2026-10-22

The former slides 23–24 felt appended. Move rate to slide 12, directly after building gradient
descent, and momentum to slide 15, directly after the plain risk-aware trace. Rewrite their spoken
transitions; retain observation after the shared-iterate table. There are still 32 slides.

The Bellman chapter explicitly distinguishes its two calls: model → update function, then
value table → value table. The returned table stores profit estimates, not orders. Slide 19
reveal 4 shows actual old/new values; reveal 5 shows the exact best-order source and how max-key
retains an order. Slide 20 labels best-order as the producer of its order column. Hydrogen now
occupies slides 17–21 and validation 22–24. The script has 81 cues for 445 rendered phases.


## Industrial certificate example — 2026-10-23

Add two slides after the Bellman proof, before the book: a plant flow (120 MW electrolyser →
20 t hydrogen tank → 10 t/h methanol loop, 588 t weekly contract), then reported weekly
certificate results. Jiri supplied the benchmark account; no weekly schedule or chart is invented.
Keep the open acceptance-implies-optimality theorem visible. A kernel-certified checker program
is not, by itself, a proof of its soundness as an optimality checker. Label the older Bellman
receipt as historical with post-repair replay pending.

The deck now has 34 slides and 83 speech cues. Shortening the surrounding Rainbow speech keeps
the estimate at 39:50 including two minutes for questions, at 125 words/minute. New cases add
context without introducing a fourth algorithm or another live demo.


## Story and solver attribution — 2026-10-23

Replace the benchmark-first framing with the Spanish plant and Jiri's electricity-purchasing
system, then ask how much cheaper another schedule could be. Lead with the one-part-per-million
bound and EUR 0.26 example; put check speeds and stack variants in reference notes. Inspecting
rh2edin's producer confirmed that HiGHS finds the incumbent; our own branch-and-bound builds the
certificate over HiGHS LP solves. Rainbow checks the resulting evidence. Say this explicitly
in the speech; do not imply our Bellman or gradient solver generated these schedules.
Current timing: 4,186 words, 39:58 including questions at 125 wpm.


## Explain our contribution and financial value — 2026-10-23

The user selected the model → HiGHS incumbent → our certificate-producing search → Rainbow
checker explanation as stage speech. Give it its own slide (32). Follow with modeled savings
from the separate San Roque e-SAF study (33): EUR 4.4m/year mean energy/backup saving versus
the strongest tuned rule, excluding assumed shortfall penalties; reported interval EUR 2.5–6.3m.
Explicitly distinguish a model estimate from measured factory savings and from the methanol
certificate scope. Return to the methanol 1e-6 bound on slide 34, then book/thanks 35–36.
Source extraction and hashes are in speaker/plant-case-evidence.json. The checker soundness
theorem is still open. Current script: 36 slides, 85 cues, 4,174 words; estimated 39:59 with questions.


Geography correction: the methanol certificate example has no physical site or weather inputs;
Spain identifies its OMIE price zone. Slide 33 alone concerns San Roque / Los Barrios
(36.18 N, 5.40 W), with local weather, generic generation models and assumed plant equipment.
Do not say Spanish weather or imply that OMIE prices are renewable-contract prices.
