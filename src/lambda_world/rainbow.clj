(ns lambda-world.rainbow
  "Recorded results of actual Rainbow kernel checks; rerun with -M:rainbow."
  (:require [clojure.edn :as edn] [clojure.java.io :as io]))

(defn evidence [] (edn/read-string (slurp (io/resource "rainbow-evidence.edn"))))

(defn demo []
  {:kind :rainbow :evidence (evidence)
   :frames [{:title "A step with momentum" :formula "[x = 0, v = 2] → [x = 1, v = 1]"
             :detail "β = rate = gradient = 1; exact rational arithmetic."
             :theorem "optimization/momentum-step-unfold"}
            {:title "An operator with a bound" :formula "|B(V)(s) − B(W)(s)| ≤ γ ε"
             :detail "Normalized transitions; admissible actions; |V − W| ≤ ε."
             :theorem "value/signed-optimality-step-additively-contractive"}
            {:title "A wrapper with an invariant" :formula "lower ≤ project(step(x))ᵢ ≤ upper"
             :detail "Ordered bounds; exact rational finite vectors."
             :theorem "optimization/vector-projected-step-in-band"}
            {:title "A claim without a proof" :formula "0 : MomentumRecurrence ?"
             :detail "Replacing the proof by a natural number is rejected."
             :rejected true}]})
