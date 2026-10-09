(ns lambda-world.shapley
  "Exact permutation Shapley values for a deliberately tiny cooperative game.")

(def players [:scout :carrier :navigator])

(defn permutations [xs]
  (if (empty? xs)
    [[]]
    (mapcat (fn [x] (map #(into [x] %) (permutations (remove #{x} xs)))) xs)))

(defn expedition-value [coalition]
  (+ (if (coalition :scout) 6 0)
     (if (coalition :carrier) 3 0)
     (if (every? coalition [:scout :navigator]) 12 0)
     (if (every? coalition players) 9 0)))

(defn contribution-step
  "A game's value function configures a fold over participants joining."
  [value-of]
  (fn [{:keys [coalition value credits]} player]
    (let [joined (conj coalition player)
          next-value (value-of joined)
          gain (- next-value value)]
      {:coalition joined :value next-value :credits (assoc credits player gain)
       :player player :gain gain})))

(defn arrival-trace [value-of order]
  (vec (reductions (contribution-step value-of)
                   {:coalition #{} :value (value-of #{}) :credits {}} order)))

(defn attribute
  "ValueFunction -> Players -> Credits. Average marginal gains over all orders.
   Factorial cost: intended for small examples."
  [value-of players]
  (let [orders (vec (permutations players))
        totals (reduce (partial merge-with +)
                       (zipmap players (repeat 0))
                       (map #(-> (arrival-trace value-of %) last :credits) orders))]
    (update-vals totals #(/ % (count orders)))))

(defn view [value-of players]
  (let [orders (mapv (fn [order] {:order order :trace (arrival-trace value-of order)})
                     (permutations players))]
    {:kind :shapley :players players
     :baseline (value-of #{}) :total (value-of (set players))
     :credits (attribute value-of players)
     :samples (into {} (map (fn [player]
                              [player (mapv #(get-in (last (:trace %)) [:credits player]) orders)]) players))
     :orders orders}))

(defn run [] (view expedition-value players))
