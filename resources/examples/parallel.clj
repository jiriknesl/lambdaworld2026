;; Pass the execution strategy as a function.
(defn evaluate-population [map-fn objective population]
  (vec
    (map-fn
      (fn [positions]
        {:positions positions
         :score (objective positions)})
      population)))

;; Same inputs, same pure objective, realized results:
;; (evaluate-population map  fitness population)
;; (evaluate-population pmap fitness population)

;; Measure actual CPU work, including realization.
;; A single run illustrates overhead, not a guarantee.
(p/compare-execution 32 evaluate-population)
