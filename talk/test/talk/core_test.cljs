(ns talk.core-test
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [talk.notation :as n]
            [talk.core :as c]))

(deftest notation
  (testing "defmap turns the drawing into positions"
    (is (= [14 7] (:size c/strait)))
    (is (= 3 (count (:bird c/strait))))
    (is (= 5 (count (:turbine c/strait))))
    (is (= [[13 3]] (:food c/strait))))
  (testing "src records the definition as typed"
    (is (str/starts-with? (n/source 'c/fixed-point) ";; no source"))   ; registry keys are bare names
    (is (str/starts-with? (n/source 'fixed-point) "(defn fixed-point")))
  (testing "defmodel gives an objective and constraint gaps"
    (let [straight (c/as-paths (vec (for [s c/starts x c/between
                                          :let [f (/ x (first c/food))
                                                [_ y z] (mapv + s (mapv #(* f %) (mapv - c/food s)))]
                                          v [y z]] v)))]
      (is (pos? ((:objective c/crossing) straight)))
      (is (not (n/feasible? c/crossing straight 0)) "straight routes hit a rotor")))
  (testing "random-map prints a defmap form, the same for the same seed"
    (is (= (n/random-map {:seed 7}) (n/random-map {:seed 7})))
    (is (str/starts-with? (n/random-map {:seed 7}) "(defmap strait"))))

(deftest the-loop
  (is (< (abs (- (peek c/sqrt-run) (js/Math.sqrt 2))) 1e-12))
  (is (c/settled? #(< (abs (- %1 %2)) 1e-12) c/sqrt-run))
  (testing "max-steps stops a run that never settles"
    (is (= 6 (count (c/fixed-point inc = 0 :max-steps 5))))))

(deftest optimise
  (doseq [k [:crowd :plain :momentum :adam]] (c/finish! k))
  (testing "advance is fixed-point one state at a time"
    (is (= (c/run-of :crowd)
           (c/fixed-point (:step (c/run-specs :crowd)) c/settle c/first-guess :max-steps c/max-steps))))
  (let [crowd (c/run-summary :crowd)]
    (is (:settled crowd))
    (is (neg? (:closest crowd)) "without costs the routes go through a rotor"))
  (doseq [k [:plain :momentum :adam]]
    (let [s (c/run-summary k)]
      (is (:settled s) (str k " settles"))
      (is (> (:closest s) 0.2) (str k " keeps clear of the turbines"))
      (is (= 3 (count (:routes s)))))))

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
