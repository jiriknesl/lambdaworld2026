# Rebuilding Operations Research in Higher-Order Functions — the deck

Lambda World 2026, Jiri Knesl. One ClojureScript namespace (`src/talk/core.cljs`) holds the slides,
the algorithms they show and the drawing code; `src/talk/notation.cljc` holds the macros. Every number
on a slide is computed by that code, and every code panel shows the exact source text of the running
definition (captured at compile time by the `src` macro), so slides cannot drift from what runs.

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
                       # every slide and step at 1600×900, 1920×1080 and 1024×768: render errors,
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
   - move 🍞 or a 🐦 in the `defmap feeding` drawing;
   - change `(penalize flock 5)` or the momentum `0.8`;
   - change `:discount 0.9` or a demand probability in `defbellman restock`;
   - remove `with-shield` from the third controller and watch reachability turn red.
3. A new random map, from the REPL: `(talk.notation/random-map {:seed 7})`, then paste it.

**Fallback.** Every slide's default example is compiled in. A broken edit shows shadow-cljs's error
overlay and the last good build keeps running: undo, save. If the watch process or the editor fails,
open the release build (`public/index.html`) — it needs nothing else.

## Where things are in `core.cljs`

| Section | What |
|---|---|
| 1. Rendering kit | hiccup → HTML, Clojure highlighting, formulas rendered from model source, SVG helpers |
| 2. The shared loop | `fixed-point` (iterate + step + converged?), Newton's √2 |
| 3. Optimise | `defmap feeding`, `defmodel flock`, `penalize`, `gradient`, `descent-step`, `with-momentum`, `with-adam` |
| 4. Decide | `defbellman restock`, `bellman`, the stopping bound |
| 5. Verify | `table-decision`, `with-shield`, `tick-controller`, `react-to-detections`, `reachable`, rules (`always`, `implies`, `within`), the worst-case search |
| 6. Receipt | `defmodel trailers` (RH2EDIN Reto 2), `linear-rows`, `pivot`, `check-receipt`, recorded Rainbow verdicts |
| 7. Slides | 35 slides with speaker notes |
| 8. App | keyboard, URL, notes window, playback, scaling |

## Timing (40 minutes)

Hello 3 · Why Clojure 5 · The loop 3 · Optimise 5 · Decide 5 · Verify 5 · Rainbow 9 · The book 2 ·
Questions. These are targets; a spoken rehearsal will tell.

## What the slides claim, and what they don't

- The trailer plan's optimality (127) was certified by Rainbow's kernel for RH2EDIN, axioms `#{}`;
  the slide shows that recorded result. The browser checks the same receipt with arithmetic.
- The restocking stop rule quotes Rainbow's stopping-certificate theorem (for all inputs). The browser
  run is floating point and has not been kernel-checked; the slide says so.
- Gradient descent, momentum and Adam have no convergence proof here; the bird controller is checked
  exhaustively by this code, not by a kernel. The "What is proved" slide lists this.
- Reto 3 bird passages are synthetic; the controller is simplified from `wildlife.edge`.

## Before the talk

- The "Who I am" slide is a sketch; personalise it.
- Emoji in the `defmap` source are drawn by the system emoji font; check alignment in Cursor's font.
- A timed spoken rehearsal and a projector check remain.
