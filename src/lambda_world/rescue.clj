(ns lambda-world.rescue
  "A tiny rescue POMDP: drifting location, three search sectors, imperfect detection.
   Search actions abstract away travel. A sighting ends this toy mission."
  (:require [lambda-world.bellman :as b]
            [lambda-world.shapley :as s]))

(def states (:states (b/grid-world)))
(def boats [:west :central :east])
(def prior {[3 0] 1/2 [4 0] 1/3 [4 1] 1/6})
(def observations [{:action :central :observation :missed}
                   {:action :east :observation :missed}
                   {:action :east :observation :seen}])

(defn drift [state]
  (let [water (set states)]
    (mapv (fn [[p direction]]
            (let [next (mapv + state direction)]
              {:prob p :next (if (water next) next state)}))
          [[3/5 [1 0]] [1/5 [0 1]] [1/5 [0 0]]])))

(defn detection [boat [x _]]
  (if (case boat :west (<= x 4) :central (<= 4 x 5) :east (>= x 5)) 4/5 0))

(defn predict [transition belief]
  (reduce-kv (fn [out state mass]
               (reduce (fn [out {:keys [prob next]}]
                         (update out next (fnil + 0) (* mass prob)))
                       out (transition state))) {} belief))

(defn observation-update [sensor predicted action observation]
  (let [weighted (into {} (map (fn [[state p]]
                                [state (* p (let [d (sensor action state)]
                                              (if (= observation :seen) d (- 1 d))))]) predicted))
        evidence (reduce + (vals weighted))]
    (when (zero? evidence)
      (throw (ex-info "Observation has zero probability under this model" {:observation observation})))
    {:predicted predicted :evidence evidence
     :posterior (update-vals weighted #(/ % evidence))}))

(defn belief-step
  "Transition and sensor functions configure a reducer over action/observation pairs."
  [transition sensor]
  (fn [belief {:keys [action observation]}]
    (:posterior (observation-update sensor (predict transition belief) action observation))))

(defn action-backup
  "Expose the terms of the same backup used by the planner.
   value is the supplied continuation function, evaluated only after a miss."
  [value belief action]
  (let [predicted (predict drift belief)
        success (reduce-kv (fn [n state p] (+ n (* p (detection action state)))) 0 predicted)
        miss (- 1 success)
        posterior (when (pos? miss)
                    (:posterior (observation-update detection predicted action :missed)))
        continuation (if posterior (value posterior) 0)]
    {:action action :seen success :miss miss :miss-posterior posterior
     :continuation continuation :weighted-continuation (* miss continuation)
     :score (+ success (* miss continuation))}))

(defn action-value [value belief action]
  (:score (action-backup value belief action)))

(defn belief-bellman
  "A finite-horizon Bellman operator on BELIEF value functions.
   Success is terminal; no discount or travel cost in this small model."
  [value]
  (fn [belief] (apply max (map #(action-value value belief %) boats))))

(defn two-search-plan
  "Materialize the two-search backup and its contingent second decisions.
   All tied optima are retained; first in boats order is used for display."
  [belief]
  (let [one-search (belief-bellman (constantly 0))
        actions (mapv (fn [action]
                        (let [backup (action-backup one-search belief action)
                              second-actions (when-let [posterior (:miss-posterior backup)]
                                               (mapv (fn [next-action]
                                                       {:action next-action
                                                        :score (action-value (constantly 0) posterior next-action)}) boats))
                              best (filterv #(= (:score %) (:continuation backup)) second-actions)]
                          (assoc backup :second-actions second-actions
                                 :second-best (mapv :action best)))) boats)
        best-score (apply max (map :score actions))]
    {:horizon 2 :actions actions :score best-score
     :best-actions (mapv :action (filter #(= (:score %) best-score) actions))}))

(defn belief-history []
  (vec (reductions (belief-step drift detection) prior observations)))

(defn search-belief [] (nth (belief-history) 2))

(defn rescue-value
  "Freeze one predicted belief. Independent detections conditional on location.
   Returns coalition -> expected detection probability in percentage points."
  [belief]
  (fn [coalition]
    (* 100 (reduce-kv
            (fn [total state p]
              (+ total (* p (- 1 (reduce * 1 (map #(- 1 (detection % state)) coalition))))))
            0 belief))))

(defn attribution []
  (assoc (s/view (rescue-value (predict drift (search-belief))) boats)
         :units "DETECTION PROBABILITY / PERCENTAGE POINTS"))

(defn coverage [belief]
  (mapv (fn [state]
          {:state state :belief (get belief state 0)
           :sensors (mapv (fn [boat] {:boat boat :detection (detection boat state)}) boats)})
        states))

(defn view [history]
  {:kind :belief :states states :width 7 :height 5
   :frames
   (mapv (fn [i belief]
           (let [event (get observations (dec i))
                 trace (when event (observation-update detection (predict drift (nth history (dec i)))
                                                       (:action event) (:observation event)))
                 plan (when (not= :seen (:observation event)) (two-search-plan belief))]
             {:values (mapv #(get belief % 0) states)
              :inspection {:event event :evidence (:evidence trace)
                           :terminal (= :seen (:observation event))
                           :mass (reduce + (vals belief)) :actions (:actions plan) :plan plan
                           :cells (mapv (fn [state]
                                          {:state state :predicted (get (:predicted trace) state 0)
                                           :likelihood (when event (let [d (detection (:action event) state)]
                                                                    (if (= :seen (:observation event)) d (- 1 d))))
                                           :posterior (get belief state 0)}) states)}}))
         (range) history)})

(defn run [] (view (belief-history)))
