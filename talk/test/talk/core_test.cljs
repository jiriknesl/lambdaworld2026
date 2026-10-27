(ns talk.core-test
  (:require [cljs.test :refer [deftest is testing]]
            [cljs.reader :as reader]
            [clojure.string :as str]
            [talk.notation :as n]
            [talk.core :as c]
            [talk.speaker :as speaker]
            [talk.script-data :as script]))

(deftest proof-edit-source-is-readable
  (doseq [sym ['remove-outer 'wrap-body]]
    ;; Read a collection containing the entire displayed source, so an extra
    ;; trailing delimiter cannot be silently ignored by read-string.
    (let [forms (reader/read-string (str "[" (n/source sym) "]"))]
      (is (= 1 (count forms)))
      (is (= ['defn sym] (take 2 (first forms)))))))

(deftest notation
  (testing "defmap turns the drawing into positions"
    (is (= [12 7] (:size c/hills)))
    (is (= 6 (count (:turbine c/hills))))
    (is (= 12 (count (:flyway c/hills)))))
  (testing "src records the definition as typed"
    (is (str/starts-with? (n/source 'c/fixed-point) ";; no source"))   ; registry keys are bare names
    (is (str/starts-with? (n/source 'fixed-point) "(defn fixed-point")))
  (testing "plain model data contains an objective and constraint gaps"
    (let [all-on (c/as-plan (vec (repeat (count c/slots) 1)))]
      (is (= 0 ((:objective c/day-plan) all-on)) "running everything gives up nothing")
      (is (not (n/feasible? c/day-plan all-on 0)) "running everything breaks the bird budget")))
  (testing "random-map prints a defmap form, the same for the same seed"
    (is (= (n/random-map {:seed 7}) (n/random-map {:seed 7})))
    (is (str/starts-with? (n/random-map {:seed 7}) "(defmap hills"))))

(deftest the-loop
  (is (< (abs (- (peek c/sqrt-run) (js/Math.sqrt 2))) 1e-12))
  (is (c/settled? #(< (abs (- %1 %2)) 1e-12) c/sqrt-run))
  (testing "max-steps stops a run that never settles"
    (is (= 6 (count (c/fixed-point inc = 0 :max-steps 5)))))
  (testing "a large cap does not eagerly run all steps"
    (let [calls (atom 0)
          step (fn [x] (swap! calls inc) (inc x))
          result (c/fixed-point step (fn [_ b] (= b 12)) 0
                                :max-steps 10000)]
      (is (= 12 @calls))
      (is (= (vec (range 13)) result)))))

(deftest fixed-point-boundaries
  (doseq [{:keys [label step converged? start cap expected calls settled?]}
          [{:label "already at a fixed point"
            :step identity :converged? = :start 3 :cap 1000
            :expected [3 3] :calls 1 :settled? true}
           {:label "zero updates allowed"
            :step inc :converged? = :start 0 :cap 0
            :expected [0] :calls 0 :settled? false}
           {:label "one update allowed without convergence"
            :step inc :converged? = :start 0 :cap 1
            :expected [0 1] :calls 1 :settled? false}
           {:label "convergence on the last allowed update"
            :step #(min 4 (inc %)) :converged? = :start 0 :cap 5
            :expected [0 1 2 3 4 4] :calls 5 :settled? true}
           {:label "exhaust the cap without convergence"
            :step inc :converged? = :start 0 :cap 5
            :expected [0 1 2 3 4 5] :calls 5 :settled? false}]]
    (testing label
      (let [call-count (atom 0)
            run (c/fixed-point (fn [x] (swap! call-count inc) (step x))
                               converged? start :max-steps cap)]
        (is (= expected run))
        (is (= calls @call-count) "No work beyond the returned trajectory")
        (is (= settled? (c/settled? converged? run)))))))

(deftest fixed-point-square-root-nine
  (let [calls (atom 0)
        step (c/newton-sqrt 9)
        run (c/fixed-point (fn [x] (swap! calls inc) (step x))
                           c/close-enough? 1.0 :max-steps 10000)]
    (is (= [1 5 3.4] (subvec run 0 3)))
    (is (< (abs (- 3 (peek run))) 1e-12))
    (is (c/settled? c/close-enough? run))
    (is (= (dec (count run)) @calls))
    (is (< @calls 10))))

(deftest electricity-and-birds
  (let [off (zipmap c/slots (repeat 0))
        on (zipmap c/slots (repeat 1))
        half (zipmap c/slots (repeat 0.5))]
    (is (= c/full-energy (c/electricity-lost off)))
    (is (= 0 (c/electricity-lost on)))
    (is (= 0 (c/bird-risk off)))
    (is (< (abs (- (c/electricity-lost half) (/ c/full-energy 2))) 1e-8))
    (is (< (abs (- (c/bird-risk half) (/ (c/bird-risk on) 2))) 1e-8))
    (is (= (c/electricity-lost half) ((:objective c/day-plan) half)))))

(deftest playback-encounter-follows-the-schedule
  (c/finish! :plain)
  (let [on (zipmap c/slots (repeat 1))
        off (zipmap c/slots (repeat 0))
        noon (* c/seconds-per-hour (+ (.indexOf c/hours 12) 0.65))
        before-contact (* c/seconds-per-hour (+ (.indexOf c/hours 12) 0.2))
        hit (c/illustrated-encounter on noon)
        safe (c/illustrated-encounter off noon)
        planned (c/illustrated-encounter (:plan (c/run-summary :plain)) noon)]
    (is (= :approach (:mode (c/illustrated-encounter on before-contact))))
    (is (= :strike (:mode hit)))
    (is (= :pass (:mode safe)))
    (is (= 12 (:hour hit)))
    (is (= (:contact hit) (:contact safe) (:contact planned))
        "before/after use the same crossing, not different bird routes")
    (is (< (last (:position hit)) (last (:contact hit))) "struck bird falls")
    (is (< (abs (- (:fall hit) (/ 0.2 0.55))) 1e-9)
        "playback's fractional seconds are normalized to the 1.5-second hour")
    (is (< (:fall (c/illustrated-encounter on (* c/seconds-per-hour 4.99))) 1)
        "fall animation remains visible until the next hour")
    (is (= :pass (:mode planned)) "optimized noon schedule stops the featured turbine")
    (is (= :strike (:mode (c/illustrated-encounter (zipmap c/slots (repeat 0.1)) noon)))
        "partial curtailment is not illustrated as guaranteed safe")))

(deftest playback-birds-follow-forecast
  (let [on (zipmap c/slots (repeat 1))
        off (zipmap c/slots (repeat 0))]
    (doseq [hour [18 19] plan [on off]
            fraction [0.2 0.65 0.99]
            :let [t (* c/seconds-per-hour (+ (.indexOf c/hours hour) fraction))
                  svg (c/html (c/scene plan t {:encounters? true}))]]
      (is (zero? (c/visible-bird-count hour)))
      (is (= :absent (:mode (c/illustrated-encounter plan t))))
      (is (not (str/includes? svg "class=\"bird")))
      (is (not (str/includes? svg "strike-flash"))))
    (is (pos? (c/visible-bird-count 12)))
    (testing "the featured bird is part of the forecast flock, not an extra hourly spawn"
      (doseq [encounters? [false true]
              :let [svg (c/html (c/scene on (* c/seconds-per-hour 4.2)
                                       {:encounters? encounters?}))]]
        (is (= (c/visible-bird-count 12)
               (count (re-seq #"class=\"bird " svg))))))))

(deftest optimise
  (doseq [k [:crowd :plain :momentum :adam]] (c/finish! k))
  (testing "advance is fixed-point one state at a time"
    (is (= (c/run-of :crowd)
           (c/fixed-point (:step (c/run-specs :crowd)) c/settle c/first-guess :max-steps c/max-steps))))
  (let [crowd (c/run-summary :crowd)]
    (is (:settled crowd))
    (is (< (:lost crowd) 1) "without the rule every turbine runs flat out")
    (is (> (:risk crowd) (* 3 c/budget))))
  (let [plans (for [k [:plain :momentum :adam]]
                (let [s (c/run-summary k)]
                  (is (:settled s) (str k " settles"))
                  (is (< (:risk s) (* 1.1 c/budget)) (str k " keeps close to the bird budget"))
                  (is (seq (:stops s)) (str k " slows some turbines"))
                  (:plan s)))]
    (testing "plans are near a global lower bound, not necessarily identical"
      ;; Quadratic penalty conjugate + independent box minimization:
      ;; w max(0,R-b)^2 >= lambda (R-b) - lambda^2/(4w).
      (doseq [plan plans]
        (let [w 100
              lambda (* 2 w (max 0 (- (c/risk plan) c/budget)))
              lower (+ c/full-energy
                       (reduce + (for [[t h] c/slots]
                                   (min 0 (- (* lambda (c/exposure t) (c/birds h))
                                             (c/energy h)))))
                       (- (* lambda c/budget))
                       (- (/ (* lambda lambda) (* 4 w))))
              score ((:objective c/safe-plan) plan)]
          (is (<= lower (+ score 1e-6)))
          (is (< (- score lower) (* 0.01 c/full-energy))
              (str "objective gap to lower bound: " (- score lower))))))))

(deftest optimization-walkthrough
  (let [{:keys [x low high h slope move next]} (c/coordinate-update c/energy-cost)]
    (is (= 0.5 x))
    (is (< (abs (- slope -84)) 1e-5))
    (is (< (abs (- slope (/ (- high low) (* 2 h)))) 1e-8))
    (is (< (abs (- next 0.5084)) 1e-8))
    (is (< (abs (- next (+ x move))) 1e-8)))
  (testing "momentum carries previous movement; the wrapper remains a pure step"
    (let [base (c/descent-step (fn [[x]] [(* 2 x)]) 0.1)
          step (c/with-momentum base 0.9)
          start {:x [10]}
          a (step start)
          b (step a)]
      (is (= [8] (:x a)))
      (is (= [-2] (:velocity a)))
      (is (< (abs (- 4.6 (first (:x b)))) 1e-10))
      (is (= a (step start)))
      (is (= [0] (:x ((c/clamped step 0 1) {:x [0.2] :velocity [-1]})))))))

(deftest whole-plan-update-and-tradeoff
  (testing "one update uses the old plan for all turbine-hour coordinates"
    (let [start c/first-guess
          updated ((c/solver-step c/energy-cost) start)]
      (is (= 72 (count (:x updated))))
      (doseq [[i [_ hour]] (map-indexed vector c/slots)]
        ;; Analytic derivative of forecast * (1 - setting) is -forecast.
        (is (< (abs (- (get-in updated [:x i])
                       (+ 0.5 (* 0.0001 (c/energy hour)))))
               1e-8)))
      (is (every? #(= 0.5 %) (:x start)) "input plan is unchanged")))
  (testing "curtailing the exposed turbine reverses the ranking only with the penalty"
    (let [[a b] c/penalty-candidates
          changed (keep-indexed (fn [i x] (when (not= x ((:x b) i)) i)) (:x a))]
      (is (= [[(c/turbines 2) 12]] (mapv c/slots changed)))
      (is (= 42 (- (:lost b) (:lost a))))
      (is (< (:risk b) (:risk a)))
      (is (< (:lost a) (:lost b)))
      (is (< (:score b) (:score a)))
      (is (> (:risk b) 12) "one illustrative improvement is not the final solution"))))

(deftest restock-walkthrough
  (let [zero-V (zipmap (:states c/restock) (repeat 0))
        outcomes (c/demand-outcomes zero-V [:regular 1] 2)]
    (is (= [3 2 0] (mapv :left outcomes)))
    (is (= [[:tight 3] [:tight 2] [:tight 0]] (mapv :next outcomes)))
    (is (= [-7 4 26] (mapv :reward outcomes)))
    (is (< (abs (- 9.5 (reduce + (map :weighted outcomes)))) 1e-10)))
  (testing "the displayed outcome decomposition matches q for every feasible action"
    (doseq [V [(first c/restock-run) (second c/restock-run) (peek c/restock-run)]
            stock (:states c/restock)
            order ((:actions c/restock) stock)]
      (is (< (abs (- ((:q c/restock) V stock order)
                     (reduce + (map :weighted (c/demand-outcomes V stock order)))))
             1e-10)))))

(deftest cost-and-observer-composition
  (testing "the plain cost function preserves the prior model's numerical objective"
    (doseq [x [(vec (repeat 72 0)) (:x c/first-guess)
               (vec (repeat 72 1)) (mapv #(/ (mod % 7) 6) (range 72))]]
      (is (< (abs (- (c/risk-cost x)
                     ((:objective c/safe-plan) (c/as-plan x))))
             1e-8))))
  (testing "an observer sees each realized update without changing its result"
    (let [seen (atom [])
          wrapped (c/with-observer inc #(swap! seen conj [%1 %2]))
          states (iterate wrapped 0)]
      (is (empty? @seen))
      (is (= [0 1 2 3 4] (vec (take 5 states))))
      (is (= [[0 1] [1 2] [2 3] [3 4]] @seen))
      (is (= [0 1 2] (vec (take 3 states))))
      (is (= 4 (count @seen)) "reading cached states does not repeat logging")))
  (is (= (vec (take 5 (iterate c/risk-step c/first-guess)))
         c/observed-states))
  (is (= 4 (count @c/observed-costs))))

(deftest hydrogen-policy-comparison
  (testing "future values change the order for the same regular-week inventory"
    (is (= 2 ((c/best-order c/restock c/zero-values) [:regular 1])))
    (is (= 3 ((c/best-order c/restock (nth c/restock-run 3)) [:regular 1]))))
  (testing "two-week policy evaluation matches independent demand-pair enumeration"
    (doseq [{:keys [policy]} c/policy-comparison
            [week stock :as state] (:states c/restock)
            :let [order (policy state)
                  next-week (if (= week :regular) :tight :regular)
                  direct (reduce +
                           (for [[d1 p1] c/demand-probabilities
                                 [d2 p2] c/demand-probabilities
                                 :let [left (max 0 (- (+ stock order) d1))
                                       next-order (policy [next-week left])
                                       profit (fn [w s o d]
                                                (- (* 10 (min d (+ s o)))
                                                   (* (if (= w :regular) 2 6) o)
                                                   (max 0 (- (+ s o) d))))]]
                             (* p1 p2 (+ (profit week stock order d1)
                                         (* 0.9 (profit next-week left next-order d2))))))
                  values (nth (iterate (c/evaluate-policy policy) c/zero-values) 2)]]
      (is (< (abs (- direct (values state))) 1e-9))))
  (testing "the learned policy depends on the supply phase"
    (is (= [3 3 2 1 0] (mapv #(c/station-policy [:regular %]) (range 5))))
    (is (= [1 0 0 0 0] (mapv #(c/station-policy [:tight %]) (range 5))))
    (is (> (:twenty-weeks (peek c/policy-comparison))
           (apply max (map :twenty-weeks (butlast c/policy-comparison))))))
  (testing "future value justifies a purchase with negative immediate expected profit"
    (let [V (peek c/restock-run) state [:tight 0]]
      (is (< (abs (+ 0.4 (c/expected-return c/zero-values state 1))) 1e-9))
      (is (= 0 (c/myopic-policy state)))
      (is (= 1 (c/station-policy state)))
      (is (> (c/expected-return V state 1) (c/expected-return V state 0)))))
  (testing "all rules respect both purchase limits and tank capacity"
    (doseq [{:keys [policy]} c/policy-comparison
            [week stock :as state] (:states c/restock)]
      (is (<= 0 (policy state) (if (= week :regular) 3 1)))
      (is (<= (+ stock (policy state)) 4))))
  (testing "optimal backups dominate fixed policies at the same finite horizon"
    (let [optimal (nth (iterate (c/bellman c/restock) c/zero-values) 20)]
      (doseq [{:keys [twenty-weeks]} c/policy-comparison]
        (is (>= (+ (optimal c/station-start) 1e-9) twenty-weeks))))))

(deftest shield-walkthrough
  (is (= :run (get-in c/shield-comparison [:reacting :action])))
  (is (= :stop (get-in c/shield-comparison [:shielded :action])))
  (is (= 2 (get-in c/shield-comparison [:shielded :det])))
  (is (c/unanswered? (:reacting c/shield-comparison)))
  (is (not (c/unanswered? (:shielded c/shield-comparison))))
  (testing "the displayed event is in the exhaustive model"
    (doseq [{:keys [id controller]} c/controllers
            :let [before c/shield-input
                  after (id c/shield-comparison)]]
      (is (some #(= (select-keys after [:ctx :det :u :action])
                    (select-keys % [:ctx :det :u :action]))
                ((c/controller-successors controller) before))))))


(deftest stage-shield-and-animation
  (testing "the wrapper preserves the original policy without detections"
    (doseq [ctx [:seen :unseen] u (vals c/draws)
            :let [state {:ctx ctx :u u :det 0}]]
      (is (= (c/decide-by-table state) (c/bird-decision state)))))
  (testing "both weak and confident detections override all policy contexts"
    (doseq [ctx [:seen :unseen] u (vals c/draws) det [1 2]]
      (is (= :stop (c/bird-decision {:ctx ctx :u u :det det})))))
  (testing "the displayed shield result is closed under every modeled successor"
    (let [states (peek c/bird-reach-run)
          expand (c/reach-step (c/controller-successors c/bird-controller))]
      (is (= states (expand states)))
      (is (not-any? c/unanswered? states))
      (is (some c/unanswered? (peek (c/reach-runs :reacting))))))
  (testing "the same detection changes the real command and freezes only the shielded rotor"
    (is (= :run (:action (c/shield-frame true 1.99))))
    (is (= :stop (:action (c/shield-frame true 2))))
    (is (= :run (:action (c/shield-frame false 2))))
    (is (= (:bird-x (c/shield-frame false 3)) (:bird-x (c/shield-frame true 3))))
    (is (= (:angle (c/shield-frame true 2)) (:angle (c/shield-frame true 4))))
    (is (not= (:angle (c/shield-frame false 2)) (:angle (c/shield-frame false 4))))))

(deftest shield-illustrated-impact
  (let [contact (/ 400 90)
        hit (c/shield-frame false contact)
        pass (c/shield-frame true contact)
        fallen (c/shield-frame false (+ contact 2))]
    (is (= :approach (:mode (c/shield-frame false (- contact 0.01)))))
    (is (= :strike (:mode hit)))
    (is (= :pass (:mode pass)))
    (is (= [450 80] (mapv hit [:bird-x :bird-y])))
    (is (= [450 80] (mapv pass [:bird-x :bird-y])))
    (is (< (abs (mod (:angle hit) 120)) 1e-9) "a moving blade is at the contact point")
    (is (= 250 (:bird-y fallen)) "the struck bird falls to the ground and stays visible")
    (is (= 80 (:bird-y (c/shield-frame true (+ contact 2)))))))

(deftest decide
  (let [{:keys [residual bound]} c/restock-certificate]
    (is (< residual c/epsilon))
    (is (< bound 0.01)))
  (is (= 10 (count (:states c/restock))))
  (is (= 1 (reduce + (vals c/demand-probabilities))))
  (testing "every possible transition stays in the ten-state model"
    (doseq [state (:states c/restock)
            order (c/orders state)
            demand (keys c/demand-probabilities)]
      (is (contains? c/zero-values (c/next-state state order demand)))))
  (testing "the phase-augmented Bellman example still shrinks the uniform gap"
    (let [B (c/bellman c/restock)
          W (zipmap (:states c/restock) (repeat 10))]
      (is (< (abs (- 9 (c/largest-change (B c/zero-values) (B W)))) 1e-9)))))

(deftest verify
  (let [bad (fn [id] (count (filter c/unanswered? (peek (c/reach-runs id)))))]
    (is (pos? (bad :tick-only)))
    (is (pos? (bad :reacting)))
    (is (zero? (bad :shielded))))
  (is (= 10 (:waiting (last (c/counterexamples :tick-only)))))
  (is (nil? (c/counterexamples :shielded)))
  (let [worst (peek (:tick-only c/falsify-runs))]
    (is (< (:t worst) 1))
    (is (< (:score worst) -530)))
  (is (= 60 (:score (peek (:shielded c/falsify-runs))))))

(deftest receipt
  (is (= 127 (:plan-costs c/receipt)))
  (is (c/receipt-ok? c/receipt))
  (is (not (c/receipt-ok? c/tampered)))
  (is (= [7 2 1 8 0 0] (:plan c/answer)))
  (is (= 127 (js/Math.round (:value (peek c/solve-run))))))

(deftest slides-fit
  (testing "every code panel's source fits its column (60 characters)"
    (doseq [[sym text] @n/sources
            line (str/split-lines text)]
      (is (<= (count line) 60) (str sym ": " line)))))

(deftest formatting
  (is (= "1.2" (c/fmt 1.2 3)))
  (is (= "540" (c/fmt 540.2 0)))
  (is (= "1" (c/fmt 1.0001 3)))
  (is (= "−0.05" (c/fmt -0.05 3)))
  (is (= "127" (c/fmt 127.0 2))))

(deftest final-minute-detection-is-visible
  (let [controller (c/tick-controller (constantly :stop))
        before {:ctx :seen :det 0 :u 0.3 :action :run
                :minute 9 :waiting nil}
        next-states ((c/controller-successors controller) before)]
    (is (some c/unanswered? next-states)
        "a tick must not erase a failure immediately before it")))

(deftest speech-covers-every-rendered-phase
  (doseq [k [:crowd :plain :momentum]] (c/finish! k))
  (is (= (mapv :title c/slides) (mapv :title script/slides)))
  (doseq [[i slide] (map-indexed vector c/slides)
          :let [total (c/steps-of slide)]
          phase (range 1 (inc total))]
    (let [matches (speaker/matching-phases i phase total)]
      (is (= 1 (count matches))
          (str "Exactly one speech cue: slide " (inc i) " phase " phase))
      (is (not (str/blank? (:speech (first matches)))))
      (is (not (str/blank? (:cue (first matches))))))))

(deftest reachable-results-are-closed
  (doseq [{:keys [id controller]} c/controllers
          :let [states (peek (c/reach-runs id))
                successors (c/controller-successors controller)]]
    (is (= states ((c/reach-step successors) states)))
    (is (every? #(<= 0 (:minute %) 10) states))
    (is (every? #(<= 0 (or (:waiting %) 0) 10) states))))
