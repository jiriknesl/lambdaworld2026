(ns lambda-world.flock
  "Optimize a formation: move toward a roost while penalizing crowding.
   These birds are optimization variables, not a boids or aerodynamic model."
  (:require [lambda-world.hof :as h]))

(defmacro flock
  "A literal grid becomes a flat coordinate vector. Only · and 🐦 are accepted."
  [& rows]
  (when-not (and (seq rows) (every? vector? rows)
                 (apply = (map count rows))
                 (every? '#{· 🐦} (mapcat identity rows)))
    (throw (IllegalArgumentException. "Use equal-width vectors containing · and 🐦.")))
  (vec (mapcat (fn [[y row]]
                (mapcat (fn [[x cell]]
                          (when (= cell '🐦) [(double x) (double y)]))
                        (map-indexed vector row)))
              (map-indexed vector rows))))

(defmacro fleet
  "The same literal formation language, with boats in place of birds."
  [& rows]
  (let [birds (mapv (fn [row] (mapv #(if (= % '⛵) '🐦 %) row)) rows)]
    `(flock ~@birds)))

(def initial
  (flock [🐦 · · 🐦 · · 🐦]
         [· · 🐦 · · 🐦 ·]
         [🐦 · · · 🐦 · ·]
         [· · 🐦 · · · ·]))

(defn roost
  "A destination configures an objective function over an entire flock."
  [[tx ty]]
  (fn [positions]
    (reduce + 0.0
            (map (fn [[x y]]
                   (+ (* (- x tx) (- x tx)) (* (- y ty) (- y ty))))
                 (partition 2 positions)))))

(def rendezvous roost)

(defn separation-constraints [bird-count distance]
  (for [i (range bird-count), j (range (inc i) bird-count)]
    (fn [positions]
      (let [dx (- (nth positions (* 2 i)) (nth positions (* 2 j)))
            dy (- (nth positions (inc (* 2 i))) (nth positions (inc (* 2 j))))]
        (- distance (Math/sqrt (+ (* dx dx) (* dy dy))))))))

(defn view
  "Materialize the actual Clojure trajectory for browser playback."
  [objective step positions steps target]
  (let [states (h/trajectory step {:x positions} steps)
        base-objective (roost target)]
    {:kind :flock
     :target target
     :frames
     (mapv (fn [i {:keys [x velocity]}]
             (let [next-state (get states (inc i))
                   points (mapv vec (partition 2 x))
                   distances (for [a (range (count points)), b (range (inc a) (count points))]
                               (Math/sqrt (reduce + (map (fn [u v] (* (- u v) (- u v)))
                                                        (points a) (points b)))))]
               {:positions points :value (objective x)
                :inspection {:x x :next-x (:x next-state)
                             :gradient (:gradient next-state)
                             :base-delta (:base-delta next-state)
                             :velocity (or velocity (vec (repeat (count x) 0)))
                             :carry (:carry next-state) :beta (:beta next-state)
                             :rate (:rate next-state)
                             :roost-cost (base-objective x)
                             :penalty-cost (- (objective x) (base-objective x))
                             :closest-distance (when (seq distances) (apply min distances))}}))
           (range) states)}))

(defn run
  ([] (run {}))
  ([{:keys [momentum penalty steps rate target]
     :or {momentum 0.75 penalty 3.0 steps 100 rate 0.025 target [3.0 0.0]}}]
   (let [objective (h/penalize (roost target)
                              (separation-constraints (/ (count initial) 2) 1.1)
                              penalty)
         step (h/with-momentum (h/descent-step (h/gradient objective) rate) momentum)]
     (view objective step initial steps target))))
