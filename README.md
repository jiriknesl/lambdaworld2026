# Lambda World 2026 — The Function Observatory

> **Superseded for the stage.** The talk is now the deck in [`talk/`](talk/README.md) (shadow-cljs, keyboard-driven, offline). This application stays in the repository until the new deck has been rehearsed; the notes below describe the old app only.

A runnable 40-minute presentation for **Rebuilding Operations Research in Higher
Order Functions in Clojure**, by Jiri Knesl. One rescue connects the examples:
a person falls overboard, boats organize, currents complicate navigation, the
person drifts out of sight, and overlapping searches create an attribution problem.
The protagonists are higher-order functions.

## Run and share

Java 17+ and the Clojure CLI are required. The main application depends only on
Clojure 1.12.3. It works offline once that dependency is cached.

```sh
clojure -M:present
# Open http://127.0.0.1:7340
# Optional port: clojure -M:present 7341

clojure -M:export
ngrok http file:///absolute/path/to/lambdaworld2026/.preview
```

The public export contains all nine chapters, the complete speaking script, and recorded JVM results. It supports
navigation, explanations, per-step inspection, playback, source unfolding, and five
curated parameter comparisons computed by Clojure.
Public code is read-only. Re-export to refresh the running file tunnel.

The local application evaluates trusted Clojure with the JVM's privileges; it is
not a sandbox. It binds to loopback and checks Host, Origin, and a request token.
The static export has no evaluation endpoint or credentials. Expose that export,
not the evaluation server. A 15-second timeout cannot forcibly stop arbitrary code
that ignores interruption.

## Public preview exposure — checked 2026-10-09

**This preview is public and unauthenticated, including before the conference.**
URL: [https://haphazard-sanction-pavestone.ngrok-free.dev](https://haphazard-sanction-pavestone.ngrok-free.dev).
Anyone with the URL can read, download, and redistribute the material. The observed
`X-Robots-Tag: noindex, nofollow` header is not access control. Neither the
presenter view nor the speaking draft is private.

The running ngrok agent's `/api/tunnels` reports one tunnel, serving
`file:///Users/jiriknesl/src/lambdaworld2026/.preview`. The inventory below covers
all 14 files currently in that directory, including its subdirectory; there are
no symlinks. Unauthenticated public GETs returned HTTP 200 with byte-for-byte
matches to the local files, except `/index.html`, which redirects to `/` (301).

| Public path | Exposed content |
| --- | --- |
| `/` (also `/index.html` via redirect) | Complete nine-chapter presentation, including the final reveal and book/Rainbow promotion. |
| `/api/scenes` | 1,883,918-byte replay manifest: all chapter explanations, compact and available expanded Clojure source, all 30 speaking cues and timing targets, demo instructions, transitions, cuts, recovery notes, recorded JVM results and numerical frames, and five parameter comparisons. Rainbow evidence includes check timestamp, commit, checkpoint-relative path, theorem names/hashes, axiom closures and rejection result. No evaluation token is present. |
| `/presenter.html` | Public script reader and printable complete speaking draft; the word “presenter” does not imply authentication. |
| `/contingency.html` | 1,251,368-byte standalone fallback page: every demo's code/numerical checkpoint, words to say, failure recovery and claim limits, plus embedded fonts and license. Most of its size is font data. |
| `/app.js` | Browser application source, replay/evaluation client logic, visualization and navigation controls. The shared local-mode client code references `/api/eval`; that does not provide a public evaluator. |
| `/stage.js` | Stage rendering and explanation/calculation presentation source. |
| `/experiments.js` | Parameter-comparison controls and result-display source. |
| `/ocean.js` | Ocean visualization source. |
| `/presenter.js` | Speaking-script rendering, printing, timing and same-browser presenter controls. |
| `/style.css` | Audience presentation styles. |
| `/presenter.css` | Presenter and printed-script styles. |
| `/fonts.css` | 1,234,774 bytes of bundled font CSS and embedded WOFF data. |
| `/font-license.txt` | Font licensing notices. |
| `/sw.js` | Recovery worker source for retiring the previous app's worker on this reused domain. |

`/api/` also returns an unauthenticated directory listing linking to `scenes`.
The file tunnel serves its directory, not the Clojure server's route whitelist:
any additional file placed in `.preview` can become public. Re-exporting updates
the public material immediately. Presenter controls use same-origin browser
`BroadcastChannel`; a visitor on another computer cannot control Jiri's audience
window through those controls.

**Boundary checks:** public GET and an empty POST to `/api/eval` returned 404.
GETs to `/README.md`, `/.git/config`, `/.env`, `/src/lambda_world/server.clj`,
`/resources/rainbow-evidence.edn`, `/artifacts/lambda-world-rehearsal.zip`,
`/lambda-world-speaker-script.pdf` and `/lambda-world-contingency.pdf` returned
404. The ZIP, PDF files, repository source tree, full book and Rainbow checkout
are outside the served directory; selected source and proof metadata are exposed
in the manifest as described above. No credentials were found in the exported
content reviewed. These are checks of this directory and running configuration,
not a guarantee about future exports or an exhaustive security audit.

**Pre-conference disclosure concern:** the complete speaking draft, presenter
cuts/recovery notes, and final reveal are already downloadable through
`/api/scenes` and the presentation views; fallback wording is also in
`/contingency.html`. If those are to be held until the talk, they should not be
public before it. Removing a presenter link alone would not hide them. No
embargo requirement was previously recorded, and this audit does not decide one
on Jiri's behalf. The tunnel, its configuration and exported files were left
unchanged, as requested. Work is parked; the aloud rehearsal belongs to Jiri and
the venue checks require the room.

## Stage and presenter views

Click **Present on stage** on the opening screen, or press **S** inside the talk.
The stage view shows a large explanation and code fragment at a time, over the
rescue chart. **Up / down** moves between explanation cues; **left / right** changes
chapters. A cue can also select the relevant computed frame and belief layer.
**Clojure ↗** returns to the editable notebook; **S** returns to stage.
Stage fragments illustrate the argument; the complete runnable cells remain in
the notebook. Press **T** or **One update** to replace the explanation with large
numerical values from the current computed step. This works for gradients,
Bellman backups, Bayesian conditioning, coalition arrivals, parallel timings,
and recorded proof evidence. The presenter window has **Show calculation** too.

**Change one thing** offers five comparisons in the penalty, momentum, and Bellman
chapters: zero penalty, beta zero, beta 0.9, gamma zero, and no sideways current.
Choosing one keeps the same iteration visible and loads its complete Clojure
source. Local mode evaluates it; public mode replays that cell's exported JVM
result. Choose **Talk default** before returning to the scripted explanation.
The fixed speaking cues describe the defaults. Comparisons are independent:
selecting another replaces the previous change. They are not a general browser REPL.

Press **P** or click **Presenter ↗** to open a separate window. Move the audience
window to the projector, keep the presenter window on the laptop, and start its
clock when beginning the talk. It shows the speaking draft, absolute cue times,
demo actions, transitions, cuts if running late, and recovery instructions. Chapter,
cue, calculation, playback, and code controls operate the audience window. The windows must be
in the same browser and on the same origin; a phone or another computer does not
act as a remote control. The clock starts manually and can be paused or reset.

The audience tab remembers its chapter, cue, frame, stage mode, calculation view, belief layer, and
clock in session storage. Refreshing that tab reconnects the existing presenter
window and pauses the visual playback at the saved frame. The talk clock continues
if it was running. This recovery does **not** save edited Clojure cells; save useful
code edits in the repository. Closing the tab can discard the session state.

The script lives in `resources/presentation.edn`: nine chapters, 30 cues, and an
explicit 2,400-second budget. Its timings include demonstrations and pauses;
they are rehearsal targets, not a measured speaking duration. Read it independently
at `/presenter.html`, or use **Print script / save PDF**. The browser validation
also generates `artifacts/lambda-world-speaker-script.pdf`.

The portable handoff is `artifacts/lambda-world-rehearsal.zip`. It contains the
entire recorded presentation, presenter window, PDF, rehearsal timing audit,
blank timing worksheet, and `START-HERE.txt`.
Extract it and run `python3 -m http.server 7342 --bind 127.0.0.1` in that directory,
then open `http://127.0.0.1:7342`. This replay needs neither ngrok nor Java and makes
no external requests. Regenerate it with `python3 scripts/package-preview.py`
after exporting, running `python3 scripts/rehearsal-audit.py`, and generating the
current speaker PDF. Generate the contingency HTML with
`python3 scripts/build-contingency.py` and its PDF with
`node scripts/contingency-smoke.cjs /path/to/playwright` before packaging.
The audit refuses to overwrite a worksheet containing actual
rehearsal observations; archive those measurements before making a fresh template.

For the POMDP chapter, **Predicted / Posterior** separates probability transported
by current from probability conditioned on the search result. The shaded sector
shows where the selected search looks. Shapley's small map reuses the same frozen
belief and highlights the sectors covered by the current coalition.

In the final POMDP cue, **Plan two searches** shows all first-action values and
`P(seen) + P(miss) × V₁(miss-posterior)` for one optimal plan. `V₁` is supplied as a
function and chooses the second search after another drift prediction. Open the
notebook for the full branch tables and tied optima. A sighting has no next plan.
The map shows the observed history; the plan describes hypothetical future searches.
The large view rounds numbers to four decimals; Clojure computes exact ratios.

## Explore an example

Every chapter has **Explain**, **One update**, and **Clojure** tabs. Explanations
state the problem, show three computational steps, and identify the HOF insight.
One update shows actual values returned by Clojure, rather than recomputing the
algorithm in JavaScript. Use + / − or the timeline to inspect adjacent states.

| Control | Action |
| --- | --- |
| Left / right | Travel between chapters |
| Space | Play / pause |
| + / − buttons | Advance / reverse one computed state |
| Timeline | Inspect a particular state |
| C / Notebook | Hide / reveal the notebook |
| S | Toggle stage view |
| P | Open the presenter window |
| Up / down | Previous / next explanation cue |
| Cmd+Enter / Ctrl+Enter | Execute the local Clojure cell |
| Unfold HOF | Load runnable function definitions into the cell |
| Reset | Restore source; run it to restore results |
| F | Fullscreen |
| ? | Keyboard help |

Definitions share a local namespace. Reset changes the cell text, not every
previous definition. Browser drafts are lost on reload; save important edits to
`resources/examples/`. Deep links include `/#pomdp` and `/#rainbow`. Reduced motion
is supported. Small screens show the notebook as an optional overlay.

## Suggested 40-minute arc

| Chapter | Minutes | HOF insight |
| --- | ---: | --- |
| Rescue + formation | 7 | A Unicode ⛵ macro makes data; objective → gradient → step; `iterate` exposes the trajectory. The original 🐦 macro remains available. |
| Separation | 3 | `penalize` changes an objective, preserving the solver interface. |
| Momentum | 4 | A step wrapper adds explicit velocity state. Inspect proposal + carried velocity. |
| Parallel evaluation | 2 | Supply `map` or `pmap` to the same independent workload. |
| Bellman | 6 | With a confirmed fixed location, improve a value function over boat locations. Inspect four actions and the chosen action's outcomes. |
| Partial observability | 7 | `belief-step` builds a reducer; `reductions` reveals posteriors. Bellman now transforms functions of beliefs. |
| Shapley | 4 | Supply a coalition-value function, fold each arrival order, and average six permutations. |
| Rainbow | 4 | Inspect three proved properties and one rejected proof. Separate theorem statements from runtime correspondence. |
| Reveal + book | 3 | Pull back to the shared vocabulary; connect to *Applied Higher Order Functions*. |

The formation and routing examples are simplified subproblems, not one integrated
rescue controller. The POMDP and attribution examples **do share data**: Shapley
uses the posterior after two missed searches, advanced by one drift prediction.
The connecting lines in the observatory primarily denote shared functional patterns.

## Rescue model and mathematical scope

- Formation states are candidate coordinate vectors, not vessel trajectories.
  The objective attracts eight boats to the last report at `[3,0]`; soft pairwise
  separation penalties expose the initial objective's shortcomings. Numerical
  gradients and coordinates use floating point. Neither feasibility, global
  optimality, nor acceleration is guaranteed.
- Parallelism scores 32 independent candidate formations using trajectory costs.
  Results are forced before timing. A single measured `pmap` run may be slower.
- The fully observed MDP routes one boat to a fixed confirmed location `[6,0]`.
  Current can push sideways; reefs block motion; entering a hazardous shoal costs
  6 and reaching the person rewards 10. Terminal rewards are paid on entry, so
  terminal values are zero. Forty backups are not a convergence certificate.
- The POMDP tracks the person's hidden location with exact rational probabilities.
  Drift is east 3/5, south 1/5, or stay 1/5; blocked moves stay. Three actions search
  west (`x ≤ 4`), central (`4 ≤ x ≤ 5`), or east (`x ≥ 5`). Detection is 4/5 inside
  the sector and zero outside; there are no false positives. Sector actions
  abstract away boat travel. A sighting ends this toy mission.
- The belief reducer predicts, multiplies observation likelihoods, and normalizes.
  The animation replays a scripted sequence of two misses and a sighting. It is
  not an optimal-policy rollout. The separately evaluated **two-search Bellman
  lookahead is real**: success terminates; a miss yields a posterior from which
  the next action is chosen. There is no discount or travel cost in this example.
  This is a small finite-horizon POMDP, not an infinite-horizon solver. Background:
  [Cassandra's POMDP tutorial](https://www.pomdp.org/tutorial/pomdp-solving.html).
- Attribution freezes the same predicted belief for all coalitions. Conditional
  on location, boat sensors are independent. Coalition value is the probability
  of at least one detection, in percentage points: `100 Σ b(s) [1 − ∏ missᵢ(s)]`.
  Two overlapping 80% sensors give 96%, not 160%. Exact permutation Shapley credits
  sum to the fleet's expected coverage. They do not establish causal credit for
  an observed rescue. Enumeration has factorial cost.

## Rainbow evidence

```sh
clojure -M:rainbow
```

This optional command adds the adjacent Rainbow source directories, replays its
D63 checkpoint through the kernel, checks 11 declarations, checks their axiom
closures, and substitutes a natural number for a momentum proof to verify rejection.
It writes `resources/rainbow-evidence.edn` with timestamp, Rainbow commit, declaration
hashes, and results. It does not modify Rainbow. Re-export to publish fresh evidence.
The presentation displays this recorded evidence; its Run/Replay button does not
rerun the kernel. The audit command is the actual reproducible kernel demonstration.

| Area | What was rechecked | What this does not establish |
| --- | --- | --- |
| Descent / momentum | Exact scalar recurrence, iteration successor, and worked `[0,2] → [1,1]` state. Signed position/velocity; nonnegative rational rate, beta, and gradient output. | General convergence or acceleration; equivalence to our floating-point vector optimizer. |
| Penalty | Augmented-objective identity and a feasible worked example. | That a finite penalty always produces a feasible optimizer. |
| Projection | An exact-rational vector projected step stays inside ordered bounds; projection is idempotent up to equivalence. | Convergence or correctness of our soft separation penalty. Projection is a related proved wrapper, not the one used by the boats. |
| Bellman | Signed optimality backup maps pointwise epsilon bounds to gamma-epsilon bounds, for normalized nonnegative transition rows and admissible actions. | Existence of a fixed point or correctness of the displayed JVM implementation. Strict contraction additionally needs gamma < 1. |
| Attribution | `shap/attribution-unfold`, a weighted two-term attribution identity. | The general permutation Shapley algorithm or this rescue coverage model. |

Rainbow also has fixed-point uniqueness and conditional convergence developments.
Those were inspected but are not among these 11 audit results. Its airport-game
manifest explicitly leaves the equivalence to Shapley allocation unproved.
POMDP modules contain narrower results such as worked PBVI candidate comparisons
and a monotone max backup; we did not recheck them in this audit and make no proof
claim for the rescue Bayesian update or its belief-space planner.

## Validation

```sh
clojure -M:test
node --check resources/public/app.js
node scripts/browser-smoke.cjs /path/to/playwright
node scripts/stage-smoke.cjs /path/to/playwright http://127.0.0.1:7340
python3 scripts/rehearsal-audit.py
python3 scripts/package-preview.py
node scripts/offline-smoke.cjs /path/to/playwright
# Optional public replay check:
node scripts/browser-smoke.cjs /path/to/playwright https://your-ngrok-url
```

Tests cover analytic gradients, momentum recurrence and purity, macro expansion,
penalties, known Bellman solutions, exact Shapley properties, displayed arithmetic,
Bayesian conditioning including impossible observations, belief normalization,
a known two-search result (96%), overlapping sensor coverage, parallel equivalence,
recorded proof metadata, lessons, and every compact/expanded presentation cell. Curated experiment tests check beta-zero
equivalence, the onset of carried velocity, penalty removal, immediate-reward
backups, deterministic transitions, and finite results. The POMDP decision trace is also
checked against direct physical-path enumeration for all nine two-action policies,
including normalization, conditional continuation, and tied optima.

Browser checks cover nine chapters, explanations and traces, belief stepping,
unfolding, edited JVM evaluation, error recovery, keyboard controls, and mobile.
Public mode checks recorded replay and read-only code. Screenshots are ignored in
`artifacts/preview/`. No Node dependency is needed to run the presentation itself.

Stage checks visit every cue at 1920×1080, 1366×768, and 1024×768, check for overflow,
exercise all five comparisons and default restoration, check large numerical
traces, exercise presenter commands, verify audience-refresh recovery, compare belief
layers, and export the script PDF. Screenshots and the layout report go to
`artifacts/stage/`. These automated checks do not establish legibility from the
back of the actual room.

## Hostile-room findings

The hardened deck passes at **1024×768 (4:3)** and **1920×1080 (16:9)**, plus
1366×768. All 30 explanation cues, numerical traces, and five comparisons were
exercised. One real 4:3 overflow appeared after replacing the fonts: the penalty
card needed scrolling. Its heading and explanation were shortened; the full rerun
reports zero overflow. PDF inspection also found and removed a Courier fallback
in the contingency page’s inline code. Checks now inspect every rendered text
element and allow only the bundled font families in PDFs. Screenshots and machine-readable findings are committed
under `artifacts/hostile-room/`.

**Fonts:** Avenir, system monospace, system emoji, and the text-based favicon have
been removed from the presentation. Three licensed DejaVu faces and original boat,
bird, fullscreen, and bracket glyphs are embedded as WOFF data in `fonts.css`.
Canvas labels use the same bundled family. Chromium's rendered-font inspection
reports custom fonts only, including the Clojure boat grid. `pdffonts` confirms
that every font in the speaker and contingency PDFs is embedded. Source fonts and
license are in `resources/fonts/`; regenerate with `python3 scripts/build-fonts.py`
when necessary (fontTools is a build tool, not a presentation dependency).

**Network:** On this Mac, `scripts/no-network.sb` denies all network operations in
its child process. A socket probe was rejected with `EPERM`. With that policy,
all 256 Clojure assertions pass, every compact/expanded cell and comparison runs,
the export succeeds, and Rainbow rechecks 11 declarations and rejects the invalid
proof. This uses the installed JDK, cached Clojure dependency, and adjacent Rainbow
checkout; a fresh uncached JVM installation is not claimed to work offline.
No host firewall or network settings were changed.

The browser test cold-loads the static deck with only loopback permitted, then
turns on Chromium's offline mode while visiting all chapters, cues, and comparisons.
It observes zero external requests and no system-font fallback. A local static
server is still needed for a cold start of the interactive replay. The standalone
contingency HTML is also tested as a `file://` page with the browser offline; it
requires neither Java nor a server. These checks establish the specified rendering
and offline behavior, not physical projector contrast or room readability.

**If a demo fails:** open `/contingency.html` from the presenter's **Demo fallback**
link, open `artifacts/lambda-world-contingency.html` directly from disk, or use
`artifacts/lambda-world-contingency.pdf`. Every chapter has a failure trigger,
replay route, static code/numerical example, words to say, and the precise limit of
its claim. The ZIP includes both fallbacks and all embedded fonts.

To reproduce the checks (the sandbox profile is macOS-specific):

```sh
sandbox-exec -f scripts/no-network.sb clojure -M:test
sandbox-exec -f scripts/no-network.sb clojure -M:rainbow
sandbox-exec -f scripts/no-network.sb clojure -M:export
python3 scripts/build-contingency.py
python3 scripts/rehearsal-audit.py
# In a separate terminal, serve .preview on loopback:
python3 -m http.server 7342 --bind 127.0.0.1 --directory .preview
# With Playwright installed:
node scripts/stage-smoke.cjs /path/to/playwright http://127.0.0.1:7342 --hostile
node scripts/contingency-smoke.cjs /path/to/playwright
python3 scripts/package-preview.py
node scripts/offline-smoke.cjs /path/to/playwright
```

## Delivery status — single acceptance checklist

**Technically ready for rehearsal; not yet signed off for live delivery.**
The spoken and physical-room checks are deferred at Jiri’s request. Only the
following two acceptance items remain. Do not treat another browser test
or a generated timing estimate as evidence that either has passed.

| Priority | Missing acceptance evidence | Who / where | Pass condition |
| --- | --- | --- | --- |
| 1 | Timed spoken rehearsal, including the live demonstrations | Jiri, with the intended laptop and browser | Speak the full talk, record actual chapter/finish times, try the cuts and one failed-cell recovery, then confirm wording and fit within the 40-minute slot. |
| 2 | Venue and projector rehearsal | Jiri / venue | Confirm readability from the back of the room, audience/notes separation, keyboard and fullscreen behavior, and the offline fallback on the actual two-display setup. |

**Work on priority 1:** a desk timing/content audit is recorded in
`artifacts/rehearsal-audit.json`; `artifacts/rehearsal-timings.csv` contains the
30 cue targets and blank fields for actual starts, finishes, and observations.
Run `python3 scripts/rehearsal-audit.py` after exporting to regenerate them.
Speaking rates in this audit are explicit planning assumptions, not measurements
of Jiri. The generous demo/discussion allowance is the main pacing uncertainty:
do not fill unused time with silence or claim the script already lasts 40 minutes.
The current count is 2,362 scripted words: 18:10 of estimated speech at 130 wpm,
leaving 21:50 for demos, discussion, live narration, and pauses. The opening alone
leaves about 4:05, while the final Rainbow rejection leaves about 11 seconds.
Use the actual rehearsal to adjust these allocations. Instructions that require proof metadata
or all six Shapley gains now explicitly open the notebook rather than the large
calculation card.

**Already accepted:** Clojure arithmetic and independent POMDP policy enumeration
(256 assertions); stage layouts and controls; presenter synchronization and refresh;
recorded Rainbow evidence with stated proof limits; printed speaker script;
public ngrok edition; and clean ZIP extraction with comparison, POMDP, presenter,
refresh, and current-PDF checks while external requests are blocked. These are
completed checks, not remaining work. The offline report records the last fully exercised ZIP
and PDF hashes in `artifacts/stage/offline-report.json`.

## Rehearsal procedure

1. Run `clojure -M:present`, open the local page, and open Presenter from it.
   Put only the audience window on the projector. Confirm a cue change works from
   the presenter window before making the audience window fullscreen.
2. Start the timer and rehearse the complete 40-minute sequence aloud. Milestones:
   separation at 07:00, momentum at 10:00, parallelism at 14:00, Bellman at 16:00,
   belief at 22:00, Shapley at 29:00, Rainbow at 33:00, closing at 37:00.
3. Practice the beta-zero comparison once, then choose Talk default. Locally,
   also practice changing the source and using Reset and Run. Practice one deliberate
   Clojure error and recovery. Keep the public replay available as the fallback;
   it contains computed results but cannot execute edits.
4. Read one Bellman outcome row and one Bayesian update aloud. Point to the quantity
   being displayed before interpreting it: values, probability mass, and percentage
   points are different units.
5. Practice the Rainbow scope statement: exact checked terms, not a proof of the
   complete rescue simulation. The kernel audit is reproducible separately with
   `clojure -M:rainbow`; the browser shows recorded results.
6. Check type size and dark-background contrast on the actual projector from the
   back of the room. Complete a two-screen rehearsal with the intended browser and
   the final speaker phrasing. Those human and venue checks remain open.

The ngrok preview runs in detached tmux session `lambdaworld-preview`; the local
stage-edition server runs in `lambdaworld-server` on port 7341. Inspect with
`tmux capture-pane -p -t lambdaworld-preview` or the corresponding server session.
Re-export to refresh the preview; do not point the public tunnel at the evaluator.
A small `/sw.js` recovery worker retires the previous app's worker on this reused
preview domain without deleting its cache or local storage. Its reproduction test
is `node scripts/service-worker-smoke.cjs /path/to/playwright` after export.

Source material: `../ahof/latex`, `../orhof`, and `../rainbow`.
