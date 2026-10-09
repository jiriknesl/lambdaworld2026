(ns lambda-world.lessons
  "Audience-facing explanations: problem, mechanism, and the HOF insight.")

(def foundation-lessons
  {"flock"
   {:question "Why do all eight boats end up on top of each other?"
    :answer "We asked for one thing: minimize the sum of squared distances to the last report. Putting every boat at the last report makes that cost zero. The optimizer is doing exactly what our function says."
    :steps [{:title "Represent a whole fleet as one value"
             :body "The ⛵ grid expands into [x₁ y₁ x₂ y₂ …]. Every frame is a new vector; the old one remains available."
             :code "state = {:x [x₁ y₁ x₂ y₂ …]}"}
            {:title "Turn an objective into a direction"
             :body "gradient accepts the cost function and returns a function. Here it estimates each slope by nudging one coordinate in both directions."
             :code "gradient : Objective → Gradient"}
            {:title "Turn a direction into a trajectory"
             :body "descent-step captures the gradient and learning rate. iterate repeatedly calls the returned state transformer."
             :code "x′ = x − rate × gradient(x)"}]
    :watch "Play, then pause. Open One update and choose a boat to see its position, gradient, and next position."
    :insight "The optimizer knows how to follow a function. The objective says what a good answer means."}
   "constraints"
   {:question "How do we tell the optimizer that crowding is expensive?"
    :answer "Keep the last report objective. Add a second cost whenever two boats are less than 1.1 units apart. The new objective balances arriving at the last report against staying separated."
    :steps [{:title "Each pair supplies a constraint function"
             :body "g(x) = 1.1 − distance. A positive value means that pair is too close. A nonpositive value means no penalty."
             :code "violation = max(0, g(x))"}
            {:title "Wrap the original objective"
             :body "penalize returns a new function that adds three times the sum of squared violations. Inspect the two cost components as the fleet moves."
             :code "f′(x) = f(x) + 3 × Σ violation²"}
            {:title "Reuse the same descent step"
             :body "The optimizer receives one scalar objective as before. A finite soft penalty can still allow boats to be too close."
             :code "(gradient (penalize f constraints 3))"}]
    :watch "The last report cost falls while the crowding cost rises. The closest-pair distance exposes the remaining compromise."
    :insight "A constraint handler can be a function transformer: Objective → Objective."}
   "momentum"
   {:question "What changes if a step remembers its previous movement?"
    :answer "The base step proposes a displacement from the current gradient. The wrapper adds 75% of the previous velocity. That new velocity determines the actual movement."
    :steps [{:title "Ask the original step for its proposal"
             :body "We reuse its calculation instead of duplicating the gradient-descent algorithm."
             :code "delta = base-step(state).x − state.x"}
            {:title "Combine the proposal with previous motion"
             :body "The velocity is a field in the state map. No hidden mutable accumulator is needed."
             :code "v′ = 0.75 × v + delta"}
            {:title "Return another state transformer"
             :body "The wrapper returns the same State → State interface, so iterate does not change. Momentum may overshoot; it is not a universal speedup."
             :code "x′ = x + v′"}]
    :watch "Step forward twice. On the first step the carry is zero; on the second it contributes to the movement."
    :insight "A HOF can change an algorithm by wrapping its step function."}
   "parallel"
   {:question "Which work is independent enough to run at the same time?"
    :answer "Each candidate is an entire eight-boat formation around the last reported position. We score 32 candidates independently by running 40 descent updates for each and adding its trajectory costs."
    :steps [{:title "Keep the objective and inputs identical"
             :body "Each evaluation reads its own candidate and immutable configuration. One candidate's score does not depend on another candidate finishing."
             :code "fitness : Formation → TrajectoryCost"}
            {:title "Pass in the mapping strategy"
             :body "evaluate-population accepts map or pmap. vec forces all scores before the timing ends."
             :code "(evaluate-population pmap fitness population)"}
            {:title "Measure and compare the results"
             :body "The two evaluations must produce the same ordered scores. Timings include coordination overhead and may favor either strategy."
             :code "serial-results = parallel-results"}]
    :watch "Read the measured times and equality check. The fleet is context for the workload; it is not an animation of thread scheduling."
    :insight "HOFs expose an independent evaluation boundary. Successive steps of one trajectory still depend on each other."}
   "bellman"
   {:question "How does a reward propagate backward through possible futures?"
    :answer "Each square is a state. Entering the gold destination pays +10; entering the red trap pays −6; other moves cost 0.15. Wind can push an action sideways. We estimate the discounted return from every state."
    :steps [{:title "Score one action against the old value function"
             :body "For each possible next state, add the immediate reward to 0.92 times its old value. Weight that result by the transition probability."
             :code "Q(s,a) = Σ p × (reward + 0.92 × V(s′))"}
            {:title "Choose the best action at every state"
             :body "The largest action score becomes the new value. All states read the same old V: this is a synchronous backup."
             :code "V′(s) = maxₐ Q(s,a)"}
            {:title "Iterate the function transformer"
             :body "A callable map stores the finite value function. The cell height is its value; the arrow is the greedy action under that function. Terminal values are zero because rewards were paid on entry."
             :code "(iterate (tabulate states (bellman model)) V₀)"}]
    :watch "The outlined cell [5,0] is next to the destination. One update shows its four action scores and the three outcomes of the chosen action."
    :insight "The Bellman operator is higher-order mathematics: it transforms a value function into another value function."}
   "shapley"
   {:question "How do we divide value that only appears when people cooperate?"
    :answer "The scout contributes 6 alone, the carrier 3, and the navigator 0. Scout plus navigator unlock another 12; the complete team unlocks another 9. Together they create 30."
    :steps [{:title "Let players arrive in one order"
             :body "For each arrival, evaluate the coalition before and after. Attribute the difference to the arriving player."
             :code "gain = value(team ∪ {player}) − value(team)"}
            {:title "Fold over that arrival order"
             :body "The fold carries the coalition, its value, and the credits so far. reductions exposes every intermediate state."
             :code "(reductions (contribution-step value-of) init order)"}
            {:title "Average over all six orders"
             :body "One order gives the last arrival all of the final synergy. Averaging every permutation removes that ordering advantage: scout 15, carrier 6, navigator 9."
             :code "Shapley(player) = mean of marginal gains"}]
    :watch "Follow the changing coalition and its gain. The bottom bars show the final average, not the credits from the current order."
    :insight "The algorithm receives value-of as a function. Changing the game leaves the folding and averaging machinery intact."}
   "rainbow"
   {:question "Which of these properties can a small kernel actually check?"
    :answer "Rainbow represents theorem statements and proof terms as Clojure data. A theorem is accepted only when its proof term has the claimed type. We replayed an existing checkpoint and rechecked the selected proofs."
    :steps [{:title "Momentum: check the exact recurrence"
             :body "Rainbow checks a scalar signed-rational step with nonnegative rate, β, and gradient output, plus a worked transition: with β = rate = gradient = 1, [position 0, velocity 2] becomes [1,1]."
             :code "v′ = 1 × 2 − 1 × 1 = 1;  x′ = 0 + 1"}
            {:title "Bellman: bound differences between value estimates"
             :body "For normalized nonnegative transition rows, admissible actions, and a nonnegative discount γ, value functions ε apart yield backups at most γε apart. It is a strict contraction when γ < 1."
             :code "|B(V)(s) − B(W)(s)| ≤ γ × ε"}
            {:title "Projection: check a wrapper's invariant"
             :body "For ordered bounds, clamping an exact-rational vector after a gradient step puts every component inside the bounds. Applying that projection again changes nothing up to equivalence."
             :code "lower ≤ project(step(x))ᵢ ≤ upper"}]
    :watch "One update shows the kernel results, declaration hashes, axiom closures, and a rejected attempt to use a number as a proof."
    :insight "These are proofs about exact Rainbow terms. Correspondence to our floating-point demo is a separate proof obligation."}
   "overview"
   {:question "What do these examples have in common?"
    :answer "The data changes: positions, value functions, coalitions, proof terms. The architecture is built from a small set of ways to pass, return, and combine functions."
    :steps [{:title "Iterate a transformation"
             :body "Move a formation through states, or improve a value function through successive estimates."
             :code "iterate : (A → A) → A → [A]"}
            {:title "Fold observations into knowledge"
             :body "An arrival extends a coalition and its contribution ledger. The update rule is supplied as a function."
             :code "reduce : (S × X → S) → S → [X] → S"}
            {:title "Transform a function"
             :body "A penalty changes an objective; momentum changes a step; Bellman changes a value function. The interfaces make these changes composable."
             :code "transform : Function → Function"}]
    :watch "The connecting lines show shared HOF patterns, not data flowing between these independent problems."
    :insight "Applied Higher Order Functions develops this vocabulary. Rainbow asks which properties of the resulting constructions we can prove."}})

(def lessons
  (-> foundation-lessons
      (assoc-in ["flock" :question] "Why do all the boats crowd the last reported position?")
      (assoc-in ["flock" :answer] "A person has fallen overboard. Start with a deliberately incomplete objective: bring the fleet to the last report. The flock formation algorithm works unchanged with boats. All boats converge on one point because that is exactly what this objective rewards.")
      (assoc-in ["flock" :watch] "The animation is a sequence of candidate formations, not a physical voyage. In One update, inspect how one boat's coordinates change.")
      (assoc-in ["bellman" :question] "If the person’s location were confirmed, which way should one boat go?")
      (assoc-in ["bellman" :answer] "First solve the fully observed subproblem: route a boat to a fixed confirmed location. Gold means rescue (+10), red means a hazardous shoal (−6), and dark blocks are reefs. Current can push a move sideways. Each move costs 0.15.")
      (assoc "pomdp"
             {:question "What does an unsuccessful search tell us?"
              :answer "The person drifts east or south. A boat detects them with probability 0.8 when searching their sector. A miss reduces the probability of that sector; it does not eliminate it. The hidden state is a location. Our computational state is a distribution over locations."
              :steps [{:title "Predict where the current carries them"
                       :body "Mix every old location's transition distribution, weighted by its probability. The current moves east with probability 0.6, south with 0.2, or stays with 0.2; blocked movement stays in place."
                       :code "predicted(s′) = Σ belief(s) × T(s,s′)"}
                      {:title "Condition on the boat's observation"
                       :body "Multiply by the likelihood of a miss or sighting and normalize. reductions preserves each posterior. The scripted search sequence is an observation example, not an optimal policy rollout."
                       :code "posterior ∝ likelihood × predicted"}
                      {:title "Apply Bellman to beliefs"
                       :body "The two-search lookahead tries each sector. A sighting ends the mission; a miss leads to a new belief and another choice. No travel cost or discount in this small POMDP."
                       :code "Q(b,a) = P(seen) + P(miss) × V(b′miss)"}]
              :watch "Step through two misses and a sighting. The heatmap is a posterior, never the hidden person's true position. One update shows likelihoods, normalization, and two-search action values."
              :insight "belief-step returns a reducer. belief-bellman returns a value function. HOFs survive the change from locations to distributions."})
      (assoc "shapley"
             {:question "How much search coverage did each boat add?"
              :answer "Reuse the belief after two unsuccessful searches and predict one more drift step. Three boats search west, central, and east sectors. Coverage overlaps. Value is the probability of at least one detection, expressed in percentage points."
              :steps [{:title "Supply a coalition-value function"
                       :body "At a location covered by two boats, independent 80% sensors detect with probability 1 − 0.2² = 0.96, not 1.6. Weight coverage by the same predicted belief for every coalition."
                       :code "value(team) = 100 × Σ b(s) × (1 − ∏ missᵢ(s))"}
                      {:title "Fold over one arrival order"
                       :body "Each arriving boat receives the extra coverage it adds to the existing coalition. A later boat may overlap an earlier one and therefore add less."
                       :code "gain = value(team ∪ {boat}) − value(team)"}
                      {:title "Average all six orders"
                       :body "reductions exposes each gain; map repeats it for every permutation; reduce combines the results. The final credits sum to the fleet's detection probability."
                       :code "Shapley(boat) = mean(marginal gains)"}]
              :watch "The center shows the current coalition's coverage. The bottom bars show final Shapley credits. One update exposes the six gains behind each average."
              :insight "This attributes expected coverage under our model. It is not causal credit for who happened to spot the person."})
      (assoc-in ["rainbow" :watch] "Step through the claims. One update lists all 11 rechecked declarations and the rejected proof. No proof of this Bayesian update or general permutation Shapley implementation is claimed. Rainbow's POMDP modules also contain narrower component results, such as worked candidate comparisons.")
      (assoc-in ["overview" :answer] "One rescue, several kinds of state: boat coordinates, route values, beliefs, coalitions, and proof terms. Search, planning, and attribution share a small functional vocabulary. The POMDP posterior feeds the Shapley example directly; formation and routing are simplified subproblems.")
      (assoc-in ["overview" :watch] "Formation → routing → uncertain search → attribution → evidence. This is a teaching model of one mission, not an integrated operational rescue system.")))
