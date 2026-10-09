(ns lambda-world.hof
  "Small, inspectable higher-order functions used by the presentation.")

(defn gradient
  "Turn a scalar objective into its central-difference gradient.
   This is numerical differentiation, not automatic differentiation."
  [f]
  (fn [x]
    (let [h 1.0e-4]
      (mapv (fn [i]
              (/ (- (f (update x i + h))
                    (f (update x i - h)))
                 (* 2 h)))
            (range (count x))))))

(defn descent-step [grad rate]
  (fn [{:keys [x] :as state}]
    (let [g (grad x)
          delta (mapv #(* (- rate) %) g)]
      (assoc state :x (mapv + x delta)
             :gradient g :base-delta delta :rate rate))))

(defn with-momentum
  "Transform a displacement-producing step into a Polyak momentum step.
   The velocity belongs to the state, so the returned function stays pure."
  [step beta]
  (fn [{:keys [x velocity] :as state}]
    (let [proposal (step state)
          delta (mapv - (:x proposal) x)
          old-v (or velocity (vec (repeat (count x) 0.0)))
          new-v (mapv #(+ (* beta %1) %2) old-v delta)]
      (assoc proposal :x (mapv + x new-v) :velocity new-v
             :carry (mapv #(* beta %) old-v) :beta beta))))

(defn penalize
  "Wrap f with soft inequality penalties. A constraint is feasible at g(x)<=0.
   A finite penalty weight does not guarantee feasibility."
  [f constraints weight]
  (fn [x]
    (+ (f x)
       (* weight
          (reduce + 0.0
                  (map (fn [g]
                         (let [violation (max 0.0 (g x))]
                           (* violation violation)))
                       constraints))))))

(defn trajectory [step initial steps]
  (vec (take (inc steps) (iterate step initial))))
