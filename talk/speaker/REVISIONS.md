# Lambda World speech revision log

21 editorial passes followed the complete first draft. Each entry records the issue found and the actual revision. Before/after fingerprints of the script and deck are retained in revisions.json. Browser checks and integration follow these editorial passes.

## 01 Clojure entry cost

The first code panel assumes the audience can read Lisp calls and defn.

Move the syntax explanation to the first code panel; add a compact call/fn legend.

## 02 Scope of OR and the book

The three topics and twelve HOFs could sound like an exhaustive taxonomy.

State the talk’s scope and distinguish the book’s chosen toolkit from a theorem about all OR.

## 03 HOF definition and Newton domain

The draft says improves a guess without stating the domain or identifying both HOF roles.

Name the constructor and map as HOFs; state the positive-input domain.

## 04 Fixed point versus stopping

Full implementation arrives before its interface; close-enough? is named but not defined on stage.

Reveal implementation last, show the actual predicate, separate tolerance from accuracy and the work cap.

## 05 State representation bridge

The talk silently switches between a vector, a state map and a map keyed by turbine-hour.

Show as-plan where the vector is introduced; explain callable maps and reduce at the first cost function.

## 06 Numerical gradient and immutable update

The gradient vector and Clojure destructuring are unexplained; the arithmetic is only spoken.

Define the vector of partial slopes, explain x/assoc, and put the worked subtraction on the slide.

## 07 Scalarization and score units

A raw sum of kWh and squared exposure can sound dimensionally meaningful without a chosen conversion.

Explain the weight’s role, the candidates still exceeding the target, and comp at its first prominent use.

## 08 Animation meaning and honest stop labels

The slide would label a capped run as settled; moving drawings can imply a measured wildlife result.

Use the actual settled flag for the stop label; distinguish replay, search and illustrative wildlife motion.

## 09 Hydrogen information load

The first reveal introduces stock, two prices, availability, probabilities, reward, holding and discount together.

Stage supplier, demand and economic details across the existing reveals; clarify independence and discount.

## 10 Bellman horizon and policy semantics

Values after n backups and a greedy order scored with those values have different horizons.

Explain the n versus n+1 distinction on stage and in speech, and distinguish the numerical run from the exact claim.

## 11 Policy comparison claim

A 20-week evaluation can be mistaken for a proof of finite-horizon optimality, and profits are discounted.

Label discounted profit, explain weighted evaluation and the stationary-policy comparison boundary.

## 12 Validation transition soundness

A failing regression showed the old minute-nine transition erased a pre-tick failure.

Separate the boundary detection state from the tick, rename successors to match its semantics, update longest-wait expectation and retain the regression.

## 13 Reachability assumptions

All event sequences sounds unbounded or physical; random-draw notation is unexplained.

State finite encoded events, representative draw bands, initial-state coverage and closure; keep physical claims out.

## 14 Reachability pacing and wrapper wiring

Twelve reveals repeat several count updates without adding a concept; final controller wiring is missing.

Reduce to nine reveals with mapped speech coverage, and show the actual shielded-controller construction.

## 15 Composition without language superiority

The pitch risks implying loops cannot be modular, and momentum boundary handling is hidden.

Credit equivalent imperative factoring; explain the first-class update benefit and this momentum variant’s boundary policy.

## 16 Effects and dynamic typing claims

Logging could imply repeat-free effects across fresh sequences; proof could sound like a dynamic-language advantage.

Explain cached versus fresh sequences and present static typing and proof as complementary tools.

## 17 Why the Bellman theorem matters

The receipt ends in caveats without explaining what the bound buys the programmer.

Connect contraction to the exact residual error bound and state the floating-point/certificate boundary next to it.

## 18 Proof accessibility for FP readers

Q notation is introduced without defining it and collides with the proposition Q in the previous example.

Explain the max argument through a winning order; remove unexplained Q notation and define the worst-entry gap.

## 19 Forty-minute delivery budget

Draft plus explicit holds and Q&A exceeded forty minutes; several explanations repeated visible tables.

Condense twelve passages while keeping notation and mathematical qualifications; give long traces continuous narration.

## 20 Complete speech continuity and voice

A final start-to-finish read found awkward spoken grammar, an unexplained zipmap, and a vague promise at the opening.

Polish those sentences, make the opening match the delivered arc, and keep the closing brief for questions.

## 21 First-use notation audit after integration

The full speech exposed remaining implicit Clojure reader notation before the audience had heard its meaning.

Introduce keyword, comprehension and shorthand lambda notation at first use; explain %1/%2 at the stopping predicate.
