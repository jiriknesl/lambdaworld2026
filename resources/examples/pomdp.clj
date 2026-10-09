;; We cannot see the person's location. We carry a belief.
;; Transition and sensor functions configure the fold.
(def update-belief
  (q/belief-step q/drift q/detection))

(def beliefs
  (vec (reductions update-belief
         q/prior q/observations)))

;; Bellman still transforms a value function.
;; Its argument is now a probability distribution.
(defn belief-bellman [value]
  (fn [belief]
    (apply max
      (map #(q/action-value value belief %) q/boats))))

(def two-search-value
  (nth (iterate belief-bellman (constantly 0)) 2))
(println "Success probability within two searches:"
         (double (two-search-value q/prior)))

;; The same backup exposes its branches for explanation.
;; V₁ is itself the result of applying the Bellman operator.
(def one-search-value (belief-bellman (constantly 0)))
(println "One conditional backup after two misses:"
  (select-keys
    (q/action-backup one-search-value (nth beliefs 2) :central)
    [:seen :miss :continuation :score]))

(q/view beliefs)
