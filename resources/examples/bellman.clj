;; An operator returns an improved VALUE FUNCTION.
(defn bellman [model]
  (fn [value]
    (fn [state]
      (let [actions ((:actions model) state)]
        (if (seq actions)
          (apply max
            (map #(b/lookahead model value state %)
                 actions))
          0.0)))))

(def model
  (b/grid-world {:discount 0.92 :wind 0.12}))
(def improve
  (b/tabulate (:states model) (bellman model)))

;; Maps materialize the finite function and are callable.
(b/view model
  (take 41 (iterate improve (constantly 0.0))))
