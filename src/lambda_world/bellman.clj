(ns lambda-world.bellman
  "A finite discounted MDP; terminal rewards are paid on entry.")

(def actions [[0 -1] [1 0] [0 1] [-1 0]])

(defn grid-world
  ([] (grid-world {}))
  ([{:keys [discount wind] :or {discount 0.92 wind 0.12}}]
   (let [width 7, height 5, walls #{[2 1] [2 2] [2 3] [4 2]}
         goal [6 0], trap [5 2]
         states (vec (for [y (range height), x (range width)
                          :when (not (walls [x y]))] [x y]))
         valid (set states)
         move (fn [s a] (let [next (mapv + s a)] (if (valid next) next s)))]
     {:width width :height height :walls (vec walls)
      :goal goal :trap trap :states states :discount discount
      :actions (fn [s] (if (#{goal trap} s) [] actions))
      :transitions
      (fn [s [dx dy :as action]]
        (mapv (fn [[prob direction]]
                (let [next (move s direction)]
                  {:prob prob :next next
                   :reward (cond (= next goal) 10.0 (= next trap) -6.0 :else -0.15)}))
              [[(- 1.0 wind) action] [(/ wind 2) [(- dy) dx]]
               [(/ wind 2) [dy (- dx)]]]))})))

(defn lookahead [{:keys [transitions discount]} value state action]
  (reduce + 0.0
          (map (fn [{:keys [prob reward next]}]
                 (* prob (+ reward (* discount (value next)))))
               (transitions state action))))

(defn bellman
  "Model -> (ValueFunction -> ValueFunction). Terminal states have value zero."
  [model]
  (fn [value]
    (fn [state]
      (let [available ((:actions model) state)]
        (if (seq available)
          (apply max (map #(lookahead model value state %) available))
          0.0)))))

(defn tabulate
  "Wrap a function transformer with finite-state materialization.
   A Clojure map is callable, so it is also our next value function."
  [states operator]
  (fn [value]
    (let [next-value (operator value)]
      (zipmap states (map next-value states)))))

(defn greedy-policy [model value]
  (fn [state]
    (let [available ((:actions model) state)]
      (when (seq available)
        (apply max-key #(lookahead model value state %) available)))))

(defn view [model values]
  (merge (select-keys model [:width :height :walls :goal :trap :states])
         {:kind :bellman
          :frames (mapv (fn [value]
                          (let [state [5 0]
                                scores (mapv (fn [action]
                                               {:action action :score (lookahead model value state action)})
                                             ((:actions model) state))
                                chosen (when (seq scores) (apply max-key :score scores))]
                            {:values (mapv value (:states model))
                             :policy (mapv (greedy-policy model value) (:states model))
                             :inspection
                             {:state state :before (value state) :after (:score chosen)
                              :discount (:discount model) :actions scores :chosen (:action chosen)
                              :terms (mapv (fn [{:keys [prob reward next]}]
                                             {:prob prob :reward reward :next next :value (value next)
                                              :weighted (* prob (+ reward (* (:discount model) (value next))))})
                                           ((:transitions model) state (:action chosen)))}}))
                        values)}))

(defn run
  ([] (run {}))
  ([{:keys [steps] :or {steps 40} :as options}]
   (let [model (grid-world options)
         improve (tabulate (:states model) (bellman model))]
     (view model (take (inc steps) (iterate improve (constantly 0.0)))))))
