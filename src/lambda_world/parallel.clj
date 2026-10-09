(ns lambda-world.parallel
  "Execution strategy is another function argument. Timings are observations,
   not a speedup guarantee or a statistically controlled benchmark."
  (:require [lambda-world.hof :as h]
            [lambda-world.flock :as f]))

(defn evaluate-population [map-fn objective population]
  (vec (map-fn (fn [positions]
                {:positions positions :score (objective positions)})
              population)))

(defn measured [f]
  (let [start (System/nanoTime)
        result (f)]
    {:ms (/ (- (System/nanoTime) start) 1.0e6) :result result}))

(defn compare-execution
  ([] (compare-execution 32))
  ([population-size] (compare-execution population-size evaluate-population))
  ([population-size evaluate-fn]
   (let [objective (h/penalize (f/roost [3.0 0.0])
                              (f/separation-constraints 8 1.1) 3.0)
         step (h/descent-step (h/gradient objective) 0.025)
         ;; Score the whole trajectory, so candidate evaluation does real work.
         fitness (fn [positions]
                   (reduce + (map (comp objective :x)
                                  (take 41 (iterate step {:x positions})))))
         population (mapv (fn [i] (mapv #(+ % (* 0.015 i)) f/initial))
                          (range population-size))
         warm (vec (take 4 population))]
     (evaluate-fn map fitness warm)
     (evaluate-fn pmap fitness warm)
     (let [serial (measured #(evaluate-fn map fitness population))
           parallel (measured #(evaluate-fn pmap fitness population))]
       (assoc (f/run {:steps 100}) :execution
              {:serial-ms (:ms serial) :parallel-ms (:ms parallel)
               :equal? (= (:result serial) (:result parallel))
               :population-size population-size})))))
