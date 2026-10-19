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
  (testing "defmodel gives an objective and constraint gaps"
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
    (is (= 6 (count (c/fixed-point inc = 0 :max-steps 5))))))

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
