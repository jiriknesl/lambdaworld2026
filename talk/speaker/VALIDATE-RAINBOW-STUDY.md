# Validate and Rainbow speaker study guide

The Validate section executes a controller against every event sequence represented by a finite model. The Rainbow section checks mathematical proof terms. These are different forms of evidence. Neither automatically proves that a physical turbine is safe or that the browser implements an exact mathematical theorem.

Read the controller example first, then the Rainbow introduction, composition proof, checked OR results and code generation. Finally read the plant example. The longer Bellman argument remains as optional background. Each section gives you a sentence you can say on stage, the mechanism behind it, and the boundary of the claim.

The slide numbers refer to the current 35-slide deck. This is a study companion; the shorter spoken script remains in `SCRIPT.md`.

## Slides 22 and 23 validate a controller

**Say:** “We have a controller and a rule. Can any allowed sequence of events make the controller break the rule?”

The earlier optimization chose tomorrow’s turbine settings. Here we use a separate, deliberately small controller example. We check its RUN or STOP commands after sensor events. We do not feed the 72-setting optimized schedule into this validator.

The rule is: a confident detection must produce a STOP command in the same modeled minute. This is a requirement on software output, not a statement about braking time or sensor accuracy.

### The state and the event

```clojure
;; Controller state before a new detection
{:ctx :unseen :det 0 :u 0.3
 :action :run :minute 1 :waiting nil}

;; Incoming event
{:type :detect :level 2}
```

| Field | Meaning |
| --- | --- |
| `:ctx` | Context, simplified to known (`:seen`) or unknown (`:unseen`) to the table. It does not mean whether a bird was seen. |
| `:det` | Strongest detection recorded during the current ten-minute slot: 0 none, 1 weak, 2 confident. |
| `:level` | Strength reported by the incoming detection event. The controller combines this with the stored `:det`. |
| `:u` | Random draw used by the probabilistic policy, held constant during a slot. It is not detection confidence. |
| `:action` | The controller’s current command, `:run` or `:stop`. |
| `:minute` | Position in the modeled ten-minute slot. This lets the search distinguish timing cases. |
| `:waiting` | Bookkeeping for how long confident detection has remained unanswered. It does not choose the action. |

The event handler uses `max` to retain the strongest detection. A weak event after a confident one does not erase the confident detection. A timer tick resets the recorded level after making its decision.

### The table is a policy input

This is the actual example table in `talk.core`:

```clojure
(def stop-table
  {:seen {0 0.05, 1 0.6, 2 1.0}})
```

| Context | No detection | Weak detection | Confident detection |
| --- | ---: | ---: | ---: |
| Known | 5% stop probability | 60% | 100% |
| Unknown | Missing | Missing | Missing |

It is a hard-coded stand-in for a learned decision table. There is no training procedure in the slide, and these probabilities are illustrative.

`table-decision` accepts this table and returns a function from state to command:

```clojure
(def decide-by-table (table-decision stop-table))

(decide-by-table {:ctx :seen :det 2 :u 0.3})
;; => :stop

(decide-by-table {:ctx :unseen :det 2 :u 0.3})
;; => :run
```

Inside it, `(get-in table [ctx det] 0)` reads the nested entry and defaults to zero if it is missing. For the known confident case it compares `0.3 < 1`; for the unknown case it compares `0.3 < 0`. That fallback silently treats an unknown situation as “never stop.”

For a probability of 0.6, a uniform draw below 0.6 chooses STOP. The finite search uses three representative draws, 0.02, 0.3 and 0.8. They cover the decision branches for this table’s thresholds. The search enumerates those choices; it does not randomly sample trajectories. Changing the table’s thresholds can require changing that abstraction.

### The two wrappers solve different problems

The original `tick-controller` records detections as they arrive but chooses an action only at a timer tick. A confident detection just after a tick can therefore leave the command at RUN.

`react-to-detections` wraps that controller. It first calls the original controller to update the state. If the event is a detection and the recorded level increased, it calls the supplied decision function immediately and puts its result in `:action`. Equal or weaker detections do not trigger another decision.

This fixes the delay. It does not fix the table’s unknown-context fallback. An immediate decision can still return RUN.

`with-shield` wraps the decision function. For a context present in the table, it delegates to the original decision. For an absent context, any positive detection produces STOP; no detection produces RUN.

```clojure
(def shielded-decision
  (with-shield decide-by-table stop-table))

(def controller
  (react-to-detections
    (tick-controller shielded-decision)
    shielded-decision))
```

The shielded decision is supplied twice because there are two occasions to decide: timer ticks and stronger detections. Both must use the same fallback rule.

The Clojure syntax is ordinary destructuring. `{:keys [ctx det] :as s}` extracts `ctx` and `det` while retaining the whole input map as `s`. `assoc` returns a new map with a changed field; it does not mutate the previous state.

### Follow one complete call

For the state and confident event above, all three controllers record `:det 2`:

| Controller | What happens next | Resulting command |
| --- | --- | --- |
| Timer only | Records the detection and waits for the timer | RUN |
| Reacting | Calls the table immediately; the context is missing | RUN |
| Reacting with shield | Calls the shield; unknown context plus detection stops | STOP |

The full call flow is:

```text
validator generates a possible event
  → composed controller receives state and event
    → base controller records the detection
    → reaction wrapper notices that it became stronger
      → shielded decision reads the updated state
        → known context: table lookup and draw comparison
        → unknown context: STOP if detection is positive
    → new state contains the resulting action
  → validator checks the new state for a rule violation
```

The validator does not invent an action. It executes the controller and inspects its output.

### The shield has a specific boundary

It only checks whether the context exists. It does not inspect the quality of a known row or repair a missing detection entry within a known row.

For example, replacing the known row with `{0 0, 1 0, 2 0}` makes a confident known detection return RUN even with this shield. The study runner demonstrates that case. You can say the shield repairs this example’s unknown-context failure. You cannot say it makes an arbitrary controller safe.

### Reachability is where iterate enters

**Say:** “Each update adds every state we could reach next. We keep updating until the set stops growing.”

```clojure
(defn reach-step [successors]
  (fn [states]
    (into states (mapcat successors states))))

(defn reachable [successors initial]
  (fixed-point (reach-step successors)
               = (set initial)))
```

`successors` returns possible next states from one state. `mapcat` collects those results across the current set. `into` adds them to the old set, retaining everything already reached and removing duplicates because the destination is a set.

With a tiny cyclic graph `a → b → c → a`, the sequence is:

```clojure
#{:a}
#{:a :b}
#{:a :b :c}
#{:a :b :c}  ; equality: closed under the successor function
```

Cycles do not require exploring infinitely many distinct histories. Once every successor is already in the set, further expansion cannot add anything. This depends on the state containing the information needed to determine future behavior. Here it includes detection, context, random-draw band, action and timing.

The controller model branches on no detection, a weak detection, or a confident detection in each minute. At the slot boundary it explores timer ticks with the modeled contexts and draw bands. It records the detection state before the next timer reset, so a reset cannot erase a violation before inspection.

The bad-state predicate is small:

```clojure
(defn unanswered? [{:keys [det action]}]
  (and (= 2 det) (= :run action)))
```

Current results from the deck’s actual functions:

| Controller | Reachable states | States breaking the rule |
| --- | ---: | ---: |
| Timer only | 473 | 275 |
| Reacting | 343 | 165 |
| Reacting with shield | 241 | 0 |

These are counts of distinct states, not probabilities, incidents, or birds. All three searches reach a closed set. The shielded result covers every path represented by this finite transition model. It does not validate bird recognition, physical braking, the optimized day schedule, or unmodeled events.

The fixed-point helper has a work cap. In general, reaching that cap would not establish closure. The study runner explicitly checks that applying the expansion once more adds no state.

## Slide 24 connects the examples

**Say:** “Underneath each example is iterate. We changed the state and the function it repeatedly applies.”

| Example | State | Update | Stopping test |
| --- | --- | --- | --- |
| Optimization | A vector of turbine settings | A gradient-based move | Settings change little |
| Decision-making | A table of expected future profits | Bellman backup | Values change little |
| Validation | A set of reachable controller states | Add every successor | Exact set equality |

Small numerical movement and exact set closure are different conditions. `iterate` does not supply the argument that a stopping condition proves what we want. We supply that argument for each problem.

## Slides 25 to 27 introduce Rainbow

**Say:** “Rainbow is a theorem prover and code generator focused on Clojure, operations research and related fields. Its purpose is not to match or beat Lean.”

A kernel is the small program implementing the logical checking rules. Proof search and tactics may propose a term; the kernel is responsible for accepting or rejecting it. Choosing Clojure does not itself supply proofs. You must state the property and construct evidence for it. Static typing and theorem proving provide different guarantees and can be used together.

### How Rainbow differs from Lean (slide 26)

Lean is a general-purpose theorem prover and programming language. Its proof scripts produce terms checked by a kernel; it has metaprogramming and compiles programs to native code. These capabilities are described in the [official Lean FAQ](https://lean-lang.org/faq/) and [language reference](https://lean-lang.org/doc/reference/latest/).

Rainbow's distinction here is its Clojure workflow and operations-research focus: explicit EDN proof terms manipulated with Clojure, and code generation for a modeled subset. Do not say that Lean cannot handle OR or that only Rainbow has proof objects, code generation or visual tools. Rainbow's implementation language being dynamically typed does not make its proof logic untyped. Do not claim superiority in speed, maturity or mathematical power.

### Read the exact proof syntax

This is the source shown on slide 27:

```clojure
{:type
 [:forall [[P Prop] [Q Prop] [R Prop]]
  [:-> [:-> P Q] [:-> Q R] P R]]

 :value
 [:fn [[P Prop] [Q Prop] [R Prop]
       [f [:-> P Q]] [g [:-> Q R]] [p P]]
  [:app g [:app f p]]]}
```

The outer `:type` and `:value` fields are the input format used by our checker script: the claimed type and the proposed term. The vectors inside are Rainbow’s explicit term syntax, represented as EDN. They are not calls to Clojure’s `fn` or `->` macro.

| Syntax | Read it aloud |
| --- | --- |
| `Prop` | The sort of propositions |
| `[:forall [[P Prop] ...] body]` | For arbitrary propositions P, Q and R, establish this body |
| `[:-> P Q]` | A proof of P can be turned into a proof of Q |
| `[f [:-> P Q]]` | f is such a proof-transforming function |
| `[p P]` | p is a proof of P |
| `[:fn [bindings] body]` | Bind the arguments and construct the proof body |
| `[:app f p]` | Apply f to p; the result proves Q |
| `[:app g [:app f p]]` | Apply g to that Q proof; the result proves R |

The whole theorem says: for any P, Q and R, given `f : P → Q`, `g : Q → R` and `p : P`, produce a proof of R. The multi-argument arrow is right-associated: `(P → Q) → (Q → R) → P → R`.

The proof is composition:

```text
p proves P
f(p) proves Q
g(f(p)) proves R
```

This does not establish P, Q or R unconditionally. It establishes the implication with those inputs. The universally quantified statement works for every choice of propositions with the required proof arguments.

### Why the wrong proof is rejected

Replace the body with `[:app f p]`. It produces a proof of Q, but the expected result is a proof of R. P, Q and R are arbitrary; the kernel cannot assume Q equals R. The term is rejected.

The tiny composition proof and this negative control were checked again on 23 October 2026 using the clean Rainbow revision `d9080c7285c7cd28a81e2d184b25ecae333510fd`. The browser shows the recorded verdict; it does not execute a Rainbow kernel.

“No axioms” means no additional axiom declarations were required by this proof. It does not eliminate trust in the kernel, runtime or logical rules. The argument parameters f, g and p are explicit assumptions inside the implication, not secretly introduced global axioms.

### Edit the proof as data (slide 27, third phase)

**Say:** “The editor proposes a new term. The kernel checks it. We can change the interface without changing what counts as a proof.”

The slide now has three phases: the original composition, removal of the outer application, and insertion of that application again. Its tree is drawn from the current term. It is not a separately maintained illustration.

The body lives at `[:value 2]`: the map's `:value` is a `[:fn bindings body]` vector, whose zero-based element 2 is the body. These are ordinary Clojure functions from `src/talk/proof_data.cljc`:

```clojure
(defn remove-outer [proof]
  (update-in proof [:value 2]
             (fn [body] (nth body 2))))

(defn wrap-body [proof function-name]
  (update-in proof [:value 2]
             (fn [body] [:app function-name body])))

(def shorter (remove-outer proof))
(wrap-body shorter 'g)
```

`update-in` is the higher-order function: it receives a function to apply at the selected location and returns a new data structure. `remove-outer` selects the argument from the outer `[:app g argument]` vector. Here that argument is `[:app f p]`. `wrap-body` constructs `[:app g [:app f p]]` again. The quoted `g` is a symbol in a proof term; it is not a Clojure function being called. The original proof remains available.

The shared functions run both in the browser and in the JVM check script. The script checks the original, removed and restored terms; the browser asserts that its edits produce exactly those recorded terms before displaying their verdicts. Rechecked on 23 October against the same clean `d9080c7` revision: checked, rejected, checked. No kernel executes in the browser.

This is a small demonstration for this application-shaped term, not a general proof editor. Arbitrary edits need to respect binders, variable scope, dependencies and the claimed type. A well-formed EDN vector can still be an invalid proof. The record also does not establish that every part of the kernel is sound.

### Why this matters beyond one edit

Clojure forms have a data representation. Rainbow explicit proof terms use vectors and symbols with their own grammar. They are not the same language, but ordinary Clojure functions can manipulate both representations. That is the connection to homoiconicity; choosing Clojure alone does not make a proof correct.

A visual tool could select a subtree, insert a step, show a dependency, or propose an edit from a drawing gesture. Each creates a candidate term requiring a check. Editing means producing a new immutable value, not modifying the accepted artifact in place. The verdict on an old term does not certify a changed term.



## Slide 28: two checked OR theorems

**Say:** “We can prove a property for every input the model allows. A worked example only helps us see what that property means.”

The four results in `src/talk/or-checks.edn` were freshly checked on 24 October from the committed Rainbow snapshot `0257fd657d897843f8f2c3acbf19115631ebcfb1`. The base checkpoint was replayed through that snapshot's kernel, then the modules installed and each named theorem checked again. Every recorded transitive axiom profile is empty. The receipt includes the source-tree and checkpoint hashes. This does not establish kernel soundness or prove arbitrary software.

**Finite action selection:** `decision/finite-nnrat-argmax-dominates`. Given a finite action set, an initial member, an exact nonnegative rational score function and any candidate in the set, the selected action's score is at least the candidate's score. The starting member ensures a choice exists. The score table A=4, B=7, C=5 illustrates this universal statement. It is not the proof itself, and the theorem does not automatically certify our browser `best-order` code or arbitrary signed profit scores.

**LP weak duality:** `nnrat/lp-weak-duality`. For a finite nonnegative-rational maximization model, primal feasibility `Ax ≤ b` and dual feasibility `Aᵀy ≥ c` imply `c·x ≤ b·y`. The coefficients and variables are in the nonnegative-rational model, and the feasibility conditions are premises. The dual certificate supplies an upper bound on every feasible plan's profit.

The two 10s on the slide are a conditional illustration: **if** a feasible plan earns 10 and a valid certificate bounds all feasible plans by 10, the plan is optimal for that model. We did not add a separate checked numerical LP instance for that picture. This mathematical theorem is an ingredient of certificate verification; it does not by itself prove the plant checker's implementation or every MILP certificate.

## Slide 29: generated Clojure

**Say:** “Rainbow generated this clamp function. The checked theorems connect the modeled program to its specification and preserve meaning through the modeled compiler. The final runtime still has a trust boundary.”

The displayed function comes from `rainbow.emit/render` for `clj/clamp-program`, with arguments `lower`, `upper`, `value`. It is not a hand-written approximation. `or-checks.edn` keeps the emitted form, emitted source and a pretty-printed copy. The build checks that both source strings read back to the same form.

Two fresh checks support the explanation:

- `clj/clamp-program-denotes-clamp`: the modeled Expr program evaluates to the clamp specification for every input store.
- `clj/compile-correct-scoped`: compiling a properly scoped Expr preserves its meaning under the modeled Form semantics. The scoping premise matters; arbitrary variable capture is not allowed.

The checker script actually emits the function and evaluates it at bounds 0 and 1: input −1 gives 0, input 1/2 gives 1/2, input 2 gives 1. These recorded JVM executions illustrate the program. They do not prove the printer, Clojure primitives or JVM correct. The result concerns the modeled subset and exact arithmetic, not unrestricted Clojure, floating-point code, or the whole wind-farm optimizer.

To reproduce, export the pinned commit's `src/` and `artifacts/ahof/d63-eligibility-traces/checkpoint.edn.gz` into a scratch directory (put the checkpoint at that directory's root). Run from this presentation repository with the exported `src/` on the classpath:

```sh
clojure -J-Xmx2g -Sdeps '{:paths ["/path/to/snapshot/src"]}' -M \
  talk/scripts/check-rainbow-examples.clj /path/to/snapshot \
  0257fd657d897843f8f2c3acbf19115631ebcfb1
```

This writes the talk's receipt and reads the isolated Rainbow snapshot. It does not modify the active Rainbow worker's checkout. The earlier attempt to install the modules into an empty environment failed on missing rational foundations; the successful check explicitly replays the pinned base checkpoint first.

## Optional background: Bellman contraction (offstage)

**Say:** “Suppose two tables disagree about future profit. Applying the same Bellman update brings their largest disagreement to at most ninety percent of its previous size.”

Our discount factor is 0.9. This bounds the gap between two tables; it does not mean every individual table entry decreases.

Let `B` be `(bellman restock)`. V and W are two estimates for the same model. Define their distance as the largest absolute difference across states:

```text
distance(V, W) = max over states s of |V(s) − W(s)|

distance(B(V), B(W)) ≤ γ × distance(V, W)
```

Our example uses γ = 0.9. One table is zero everywhere and the other is ten everywhere. Every action’s expected future term shifts by nine because the demand probabilities sum to one. The maximum therefore shifts by nine too. Browser arithmetic produces approximately 9.000000000000004, displayed as 9. This numerical example is not a universal proof.

### The proof follows the operations in q

Recall that q scores an order using expected immediate profit plus discounted future value. Write `Q_V(s,a)` for that score when the future table is V.

Assume every entry of V and W differs by at most ε. Fix the same state s and the same action a in both calculations.

1. **Subtract the scores.** The immediate reward is the same in both, so it cancels. Only the future-value terms differ.
2. **Bound the weighted sum.** Each future difference has absolute value at most ε. The probabilities are nonnegative and sum to one. Thus the score difference is at most `Σ p × γ × ε = γ × ε`.
3. **Take the best action.** For every a, `Q_V(s,a) ≤ Q_W(s,a) + γ ε ≤ max_b Q_W(s,b) + γ ε`. Taking the maximum on the left gives `B(V)(s) ≤ B(W)(s) + γ ε`.
4. **Reverse V and W.** The same argument gives the other inequality. Together they bound the absolute difference, for every state.

The winning action is allowed to change. The proof does not assume that V and W recommend the same order. This is the subtle reason the `max` step is valid.

The finite model must have nonempty available-action sets, normalized nonnegative probabilities, and the same rewards/transitions in both evaluations. Strict contraction needs `0 ≤ γ < 1`. With γ = 1 this argument gives a nonexpansion bound, not the contraction used for the residual result below.

### What the residual says

For an exact candidate table V, let `δ = distance(V, B(V))`. If V* is the Bellman fixed point, the triangle inequality and contraction give:

```text
distance(V, V*)
  ≤ distance(V, B(V)) + distance(B(V), B(V*))
  ≤ δ + γ × distance(V, V*)

distance(V, V*) ≤ δ / (1 − γ)
```

With γ = 0.9, an exact residual of 0.001 bounds value error by 0.01. This bounds value estimates; it does not automatically prove that a greedy order is uniquely optimal. Close action scores can change the chosen action.

The residual compares a candidate with one more update. A change between two consecutive iterates is the residual of the earlier table. A floating-point stopping test without a rounding-error analysis is not an exact residual certificate.

### What the talk can claim about the formal proof

The composition and new OR/code-generation examples have fresh kernel checks. The Bellman theorem has a historical receipt dated 9 October 2026, named `value/signed-optimality-step-additively-contractive`, with an empty recorded axiom profile.

That Bellman receipt predates a documented kernel repair. The kernel’s trusted-base document says an earlier bug could accept a proof of False and requires affected artifacts to be replayed after repair. This talk does not have a completed repaired-kernel replay of the Bellman artifact. The historical receipt remains pending replay; it is no longer a stage slide.

**Say:** “The contraction argument is the mathematics we want checked. This is a historical Rainbow receipt; replay after the kernel repair is pending. The browser’s floating-point implementation is not proved equivalent to that exact operator.”

Do not describe the Bellman browser demo as certified by the theorem. Nor should a theorem that merely unfolds an update definition be described as proving its convergence or optimality.

## Slides 30 and 31 explain the plant work

### The physical interpretation of the model

An illustrative methanol model uses electricity to make hydrogen, stores hydrogen in a tank, and feeds a methanol loop. Scheduling chooses when equipment runs and when the tank absorbs the difference. Electricity prices vary hourly; minimum loads, starts and a weekly production requirement constrain those choices.

The benchmark uses Spanish OMIE wholesale prices known for the whole week. It is not a named operating factory, a weather model, a live trading strategy, or a renewable-electricity supply contract. The modeled sizes are 120 MW of electrolysis, 20 tonnes of hydrogen storage and a methanol loop capable of 10 tonnes per hour, with a 588-tonne weekly requirement.

### Who performs each calculation

**Say:** “Our Clojure code builds the model. HiGHS finds a proposed schedule. Our search builds a certificate using HiGHS for its smaller optimization problems. Rainbow checks that certificate.”

| Component | Role |
| --- | --- |
| Our model | Encodes equipment, storage, prices and production constraints |
| HiGHS | Third-party solver; finds the candidate schedule and solves LP relaxations inside our search |
| Our branch-and-bound producer | Chooses branches and constructs the tree and exact bound evidence |
| Rainbow checker | Checks the submitted certificate using exact arithmetic |

An LP relaxation allows integer decisions to be fractional. For minimization, its optimum is a lower bound on the corresponding integer problem. Branching partitions integer possibilities; exact dual evidence supplies bounds or demonstrates that a branch is infeasible. The checker accounts for the branches instead of relying solely on the solver’s “optimal” status.

### What one part per million means

For a minimization problem, let U be the cost of a feasible proposed schedule and L a lower bound on all feasible schedules. Then `L ≤ optimum ≤ U`; the distance still available for improvement is at most `U − L`.

The reported weekly certificate results cover 52 weeks of 2023 at relative tolerance 10⁻⁶. The largest reported absolute gap is approximately €0.26 on a week costing approximately €388,000. This is not the amount saved against a baseline. It is the remaining improvement permitted by the reported bound. A nonzero gap permits a slightly cheaper schedule to exist.

The important unfinished piece is the checker’s **soundness theorem**: a formal proof that acceptance of any certificate implies the claimed optimality bound. The checker program and exact checks exist, but that general implication remains open. The small composition proof, the historical Bellman receipt and this checker-soundness question are three separate things.

**Say:** “The reported certificates were accepted by our exact checker. The general theorem connecting checker acceptance to optimality is still open.”

Avoid “I have a complete formal proof that the real plant is always within 10⁻⁶ of optimal.” That would combine an unfinished formal link, a finite benchmark, a modeled plant and an unmodeled real operating environment.

### Algeciras: data, estimated savings and proof scope

Slide 31 describes an assumed 50,000-tonne/year e-SAF plant near San Roque / Los Barrios,
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
`plant-case-evidence.json` as historical evidence; it is a different baseline.
The requested €74m/year figure has not been traced to a result or baseline and is not
asserted. The source question remains pending.

Data references: [OMIE market results](https://www.omie.es/en/market-results/daily/daily-market/day-ahead-price?scope=daily),
[PVGIS hourly data](https://joint-research-centre.ec.europa.eu/photovoltaic-geographical-information-system-pvgis/using-pvgis-5/pvgis-5-tools/hourly-radiation_en),
and `../rh2edin-challenge/data/manifest.json`. OMIE wholesale prices are not a
renewable-specific supply-contract price. The separate methanol benchmark does not
certify this e-SAF study.

## Questions to practise aloud

Try each answer without looking at the earlier sections. A short accurate answer is enough.

1. What is the difference between `:det`, `:level` and `:u`?
2. Where does the STOP/RUN action come from? Does the validator choose it?
3. Why are both wrappers needed? What case can this shield still miss?
4. Why does set equality let the reachability search stop, even with cycles?
5. Does zero bad states mean no real bird can be harmed?
6. Read `[:app g [:app f p]]` and explain the rejected alternative.
7. Does an empty axiom list remove the need to trust anything?
8. What cancels in the Bellman proof? Why can the maximizing action change?
9. Does a larger value table necessarily represent a better policy?
10. What does residual 0.001 imply when γ is 0.9, and under what arithmetic assumptions?
11. Which results are freshly checked, which receipt is historical, and which theorem is open?
12. What does HiGHS do, and what did we implement?
13. How do the €7.9m design saving and the €0.26 gap differ?

### Answer checks

1. `:det` is recorded detection, `:level` is an incoming report, and `:u` is a held random draw used by the policy.
2. The supplied controller calls its decision function. The validator generates allowed inputs and checks returned states.
3. Reacting fixes timing; shielding fixes unknown-context fallback. A bad known row is still delegated to.
4. The accumulated finite set already contains every successor. The state must retain the information needed for future transitions.
5. No. It establishes the command rule within the encoded model, not sensor correctness or physical safety.
6. f takes p to a Q proof; g takes that to an R proof. Returning only f(p) supplies Q where R is required.
7. No. Logical checking still trusts the kernel and its execution environment.
8. The same immediate reward cancels. Each action’s score satisfies the bound, so taking maxima preserves the bound without requiring the same winner.
9. No. Value iteration also extends the planning horizon; a value is a money estimate and a policy is an action choice.
10. An exact candidate’s value error is at most 0.01 under the discounted-model assumptions. Floating-point rounding needs separate treatment.
11. Composition, finite action selection, LP weak duality, clamp refinement and scoped compilation have fresh checks. Bellman remains a historical receipt pending replay. The plant checker’s acceptance-implies-optimality theorem is open.
12. HiGHS supplies the candidate and LP solves. Our code supplies the model, certificate-producing search and checking machinery.
13. €7.9m is an estimated e-SAF design saving against an average-year design. €0.26 is the largest reported methanol certificate gap, not baseline savings.

## Run the examples and inspect the evidence

From the `talk` directory:

```sh
npm run study
```

This imports the presentation’s actual ClojureScript functions. It prints the common detection event and three controller outputs, reachable-state counts, a tiny cyclic fixed point, the shield’s bad-known-row counterexample and the numerical Bellman gap. To save the output:

```sh
npm run study -- speaker/study-results.json
```

From the repository root, check the composition proof and its deliberately wrong alternative:

```sh
clojure -Sdeps '{:paths ["../rainbow/rainbow/src" "talk/src"]}' \
  -M talk/scripts/check-proof.clj
```

That command requires the local clean Rainbow checkout and updates the talk’s composition receipt. It does not replay the Bellman corpus or prove the plant checker’s soundness theorem.

The main references are:

- [Deck code and controller implementation](../src/talk/core.cljs).
- [Runnable study examples](../src/talk/study.cljs) and [recorded output](study-results.json).
- [Exact composition term](../src/talk/composition.edn) and [fresh check receipt](../src/talk/composition-check.edn).
- [Fresh OR and code-generation receipt](../src/talk/or-checks.edn).
- [Historical Bellman receipt](../../resources/rainbow-evidence.edn).
- [Plant and savings provenance](plant-case-evidence.json).
- Rainbow checkout: `../rainbow/rainbow/doc/trusted-base.md` and `doc/claims.md`.
- Plant checker: `../rainbow/internal-milp/src/rainbow/logic/clojure_bb_tree_certificate.clj`.
- Certificate report: `../rainbow/DESIGN-2026-10-18-proved-milp-certificates.md`.

The sibling-repository paths in the last three items are relative to the presentation repository root.
