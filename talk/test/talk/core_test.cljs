(ns talk.core-test
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [talk.notation :as n]
            [talk.core :as c]))

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

(deftest restock-walkthrough
  (let [zero-V (zipmap (:states c/restock) (repeat 0))
        outcomes (c/demand-outcomes zero-V 1 2)]
    (is (= [3 2 1 0] (mapv :left outcomes)))
    (is (= [-11 0 11 22] (mapv :reward outcomes)))
    (is (< (abs (- 7.7 (reduce + (map :weighted outcomes)))) 1e-10)))
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
  (testing "the visible recommendation changes as future values improve"
    (is (= 1 ((c/best-order c/restock (first c/restock-run)) 1)))
    (is (= 2 ((c/best-order c/restock (nth c/restock-run 2)) 1))))
  (testing "two-week policy evaluation matches direct enumeration of demand pairs"
    (doseq [target (range 5) stock (range 5)
            :let [policy (c/order-up-to target)
                  order (policy stock)
                  direct (reduce +
                           (for [[d1 p1] c/demand-probabilities
                                 [d2 p2] c/demand-probabilities
                                 :let [left (max 0 (- (+ stock order) d1))
                                       next-order (policy left)
                                       profit (fn [s o d]
                                                (- (* 10 (min d (+ s o)))
                                                   (* 4 o)
                                                   (max 0 (- (+ s o) d))))]]
                             (* p1 p2 (+ (profit stock order d1)
                                         (* 0.9 (profit left next-order d2))))))
                  values (nth (iterate (c/evaluate-policy policy) c/zero-values) 2)]]
      (is (< (abs (- direct (values stock))) 1e-9))))
  (let [two (c/policy-comparison 2) three (c/policy-comparison 3)]
    (is (> (:one-week two) (:one-week three)))
    (is (< (:twenty-weeks two) (:twenty-weeks three)))
    (is (= {0 3, 1 2, 2 1, 3 0, 4 0}
           (c/best-order c/restock (peek c/restock-run)))))
  (testing "optimal backups dominate every displayed fixed policy at the same horizon"
    (let [optimal (nth (iterate (c/bellman c/restock) c/zero-values) 20)]
      (doseq [{:keys [twenty-weeks]} c/policy-comparison]
        (is (>= (+ (optimal 1) 1e-9) twenty-weeks))))))

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
                ((c/one-minute controller) before))))))


(deftest decide
  (let [policy (c/best-order c/restock (peek c/restock-run))
        {:keys [residual bound]} c/restock-certificate]
    (is (< residual c/epsilon))
    (is (< bound 0.01))
    (testing "order-up-to: stock + order is the same level wherever stock is below it"
      (let [levels (set (for [[s o] policy :when (pos? o)] (+ s o)))]
        (is (= 1 (count levels)))))))

(deftest verify
  (let [bad (fn [id] (count (filter c/unanswered? (peek (c/reach-runs id)))))]
    (is (pos? (bad :tick-only)))
    (is (pos? (bad :reacting)))
    (is (zero? (bad :shielded))))
  (is (= 9 (:waiting (last (c/counterexamples :tick-only)))))
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
