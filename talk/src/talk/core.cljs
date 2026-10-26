(ns talk.core
  "Rebuilding Operations Research in Higher-Order Functions in Clojure — Lambda World 2026.

  One namespace, in sections:
    1. Rendering kit        hiccup → HTML, code highlighting, formulas, small SVG helpers
    2. The shared loop      fixed-point = iterate + step + converged?
    3. Optimise             a wind farm's stop plan for tomorrow: gradient, penalize, momentum, Adam
    4. Decide               restocking a hydrogen station: Bellman
    5. Verify               the bird stop controller: reachability and falsification
    6. Receipt              the trailer plan: solver as a fixed point, and a checkable receipt
    7. Slides               the talk, slide by slide, with speaker notes
    8. App                  keyboard, URL, notes window, scaling

  Demo values are computed here. Definition panels use source captured by `src`;
  illustrative calls, proof explanations and recorded kernel evidence are labelled separately."
  (:require [clojure.string :as str]
            [talk.speaker :as speaker]
            [talk.proof-data :as proof-data]
            [talk.notation :as n :refer [src defmap defmodel defbellman pairs violations]]))

;; =============================================================================
;; 1. Rendering kit
;; =============================================================================

(defn esc [s]
  (-> (str s)
      (str/replace "&" "&amp;") (str/replace "<" "&lt;")
      (str/replace ">" "&gt;") (str/replace "\"" "&quot;")))

(defn- parse-tag [k]
  (let [[_ tag rest] (re-find #"^([^.#]*)(.*)$" (name k))
        id (second (re-find #"#([^.]+)" rest))
        classes (map second (re-seq #"\.([^.#]+)" rest))]
    [(if (str/blank? tag) "div" tag) id classes]))

(defn- style-str [m]
  (str/join ";" (for [[k v] m :when (some? v)] (str (name k) ":" v))))

(def ^:private void-tags #{"br" "hr" "img" "input" "meta"})

(defn html
  "Hiccup → HTML string. [:raw s] inserts s unescaped."
  [x]
  (cond
    (nil? x) ""
    (string? x) (esc x)
    (number? x) (esc x)
    (vector? x)
    (let [[t & more] x]
      (if (= t :raw)
        (apply str more)
        (let [[attrs kids] (if (map? (first more)) [(first more) (rest more)] [{} more])
              [tag id classes] (parse-tag t)
              cls (str/join " " (concat classes (let [c (:class attrs)] (if (sequential? c) c (when c [c])))))
              attrs (cond-> (dissoc attrs :class)
                      (seq cls) (assoc :class cls)
                      id (assoc :id id)
                      (map? (:style attrs)) (update :style style-str))
              attr-str (apply str (for [[k v] attrs :when (and (some? v) (not (false? v)))]
                                    (if (true? v) (str " " (name k)) (str " " (name k) "=\"" (esc v) "\""))))]
          (if (void-tags tag)
            (str "<" tag attr-str ">")
            (str "<" tag attr-str ">" (apply str (map html kids)) "</" tag ">")))))
    (seq? x) (apply str (map html x))
    :else (esc x)))

(defn fmt
  "A number for people: integers as integers, others with d decimals."
  ([x] (fmt x 2))
  ([x d]
   (cond
     (not (number? x)) (str x)
     (= x ##Inf) "∞"
     (= x ##-Inf) "−∞"
     (== x (js/Math.round x)) (str/replace (str (js/Math.round x)) "-" "−")
     :else (let [t (.toFixed x d)
                 t (if (pos? d) (str/replace t #"\.?0+$" "") t)]
             (str/replace t "-" "−")))))

;; ---- Clojure code highlighting ----------------------------------------------

(def ^:private special
  #{"defn" "defn-" "def" "fn" "let" "if" "when" "cond" "case" "for" "loop" "recur" "do" "->" "->>"
    "src" "defmap" "defmodel" "defbellman" "if-let" "when-let" "minimize" "maximize" "subject-to"
    "sum" "for-all" "and" "or" "not" "quote"})

(defn highlight
  "Clojure source → hiccup spans. `marks` is a set of symbol names to emphasise."
  ([text] (highlight text #{}))
  ([text marks]
   (for [[tok comment string kw number paren ws] (re-seq #"(;[^\n]*)|(\"(?:\\.|[^\"\\])*\")|(:[^\s()\[\]{}\",]+)|(-?\d+(?:\.\d+)?(?:e-?\d+)?(?![\w\-]))|([()\[\]{}])|(\s+)|[^\s()\[\]{}\"]+" text)]
     (cond
       comment [:span.c-comment tok]
       string [:span.c-string tok]
       kw [:span.c-kw tok]
       number [:span.c-num tok]
       paren [:span.c-paren tok]
       ws tok
       (marks tok) [:span.c-mark tok]
       (special tok) [:span.c-special tok]
       :else tok))))

(defn code
  "A code panel. `x` is a symbol (source of a `src` definition) or a string."
  ([x] (code x {}))
  ([x {:keys [marks size] :or {marks #{}}}]
   [:pre.code {:class (when size (name size))}
    [:code (highlight (if (symbol? x) (n/source x) x) marks)]]))

;; ---- Formulas, rendered from code -------------------------------------------

(declare fx)

(def ^:private lookups #{'km 'loads 'supply 'need 'p 'value 'exposure 'birds 'run})

(defn- sub-text [e]
  (cond (symbol? e) [:i (name e)]
        (vector? e) (interpose "," (map sub-text e))
        :else (fx e)))

(defn- binding-fx [b xs]
  [:span (if (vector? b) (into [:span "("] (conj (vec (interpose ", " (map sub-text b))) ")")) (sub-text b))
   " ∈ " (fx xs)])

(defn- bindings-fx [bs]
  (interpose ", " (for [[b xs] (partition 2 bs)] (binding-fx b xs))))

(defn- paren-if [parent e]
  (if (and (seq? e) (#{'+ '-} (first e)) (= parent '*))
    [:span "(" (fx e) ")"]
    (fx e)))

(defn fx
  "Clojure expression → mathematical notation (hiccup)."
  [e]
  (cond
    (number? e) [:span.num (fmt e 3)]
    (symbol? e) [:i (name e)]
    (keyword? e) [:span (name e)]
    (vector? e) (into [:span "("] (conj (vec (interpose ", " (map fx e))) ")"))
    (seq? e)
    (let [[op & args] e
          [a b] args]
      (case op
        minimize [:span [:b.op "min "] (fx a)]
        maximize [:span [:b.op "max "] (fx a)]
        sum [:span.sum [:span.bigop "Σ"] [:sub (bindings-fx a)] " " (fx b)]
        for-all [:span "∀ " (bindings-fx a) ": " (fx b)]
        + (into [:span] (interpose " + " (map fx args)))
        - (if (= 1 (count args))
            [:span "−" (fx a)]
            (into [:span] (interpose " − " (map #(paren-if '* %) args))))
        * (into [:span] (interpose " · " (map #(paren-if '* %) args)))
        <= [:span (fx a) " ≤ " (fx b)]
        >= [:span (fx a) " ≥ " (fx b)]
        = [:span (fx a) " = " (fx b)]
        dist [:span "‖" (fx a) " − " (fx b) "‖"]
        dist2 [:span "‖" (fx a) " − " (fx b) "‖" [:sup "2"]]
        sq [:span "(" (fx a) ")" [:sup "2"]]
        max [:span "max(" (interpose ", " (map fx args)) ")"]
        min [:span "min(" (interpose ", " (map fx args)) ")"]
        (if (lookups op)
          [:span [:i (name op)] [:sub (sub-text a)]]
          [:span [:i (name op)] "(" (interpose ", " (map fx args)) ")"])))
    :else (str e)))

(defn model-formula
  "The model, drawn from its own source form."
  [{:keys [source]}]
  (let [[_ _ _ objective [_ & constraints]] source]
    [:div.formula
     [:div.f-line (fx objective)]
     (when (seq constraints)
       [:div.f-st [:span.f-label "subject to"]
        (for [[cname c] constraints]
          [:div.f-line [:span.f-name (name cname)] (fx c)])])]))

(defn- penalty-term
  "The cost of one constraint, as a form: what penalize adds to the objective."
  [c]
  (let [[forall? bindings cmp] (if (= 'for-all (first c)) [true (second c) (nth c 2)] [false nil c])
        [rel l r] cmp
        broken (if (= rel '>=) (list 'max 0 (list '- r l)) (list 'max 0 (list '- l r)))
        term (list 'sq broken)]
    (if forall? (list 'sum bindings term) term)))

(defn penalized-formula [{:keys [source]} weight]
  (let [[_ _ _ [sense objective] [_ & constraints]] source]
    [:div.formula
     [:div.f-line (fx (list sense (apply list '+ objective
                                         (for [[_ c] constraints] (list '* weight (penalty-term c))))))]
     [:div.f-st [:span.f-label "no constraints left: they became costs"]]]))

(defn bellman-formula [{:keys [discount]}]
  [:div.equation-stack
   [:div.equation-part [:span.f-label "For each possible demand"]
    [:div "return = reward + " (fmt discount) " × next value"]]
   [:div.equation-part [:span.f-label "For each order"]
    [:div "score = probability-weighted average of returns"]]
   [:div.equation-part [:span.f-label "For this stock level"]
    [:div "new value = best order’s score"]]])

;; ---- SVG helpers --------------------------------------------------------------

(defn svg [w h & kids]
  (into [:svg {:viewBox (str "0 0 " w " " h) :width w :height h :xmlns "http://www.w3.org/2000/svg"}] kids))

(defn polyline [pts attrs]
  [:polyline (merge {:fill "none" :points (str/join " " (map (fn [[x y]] (str (.toFixed x 1) "," (.toFixed y 1))) pts))} attrs)])

(defn bird-glyph [x y {:keys [cls scale] :or {cls "bird" scale 1}}]
  [:g {:transform (str "translate(" (.toFixed x 1) "," (.toFixed y 1) ") scale(" scale ")")}
   [:path {:class cls :d "M -14 -2 Q -7 -11 0 -1 Q 7 -11 14 -2"}]])

(defn food-glyph [x y]
  [:g {:transform (str "translate(" x "," y ")")}
   [:rect.food {:x -16 :y -11 :width 32 :height 22 :rx 9}]
   [:path.food-line {:d "M -8 -11 L -8 11 M 0 -11 L 0 11 M 8 -11 L 8 11"}]])

(defn proof-tree
  "Draw the explicit application term itself; no independent diagram or type inference."
  [term]
  (if (vector? term)
    [:div.term-node
     [:div.term-label (str (first term))]
     [:div.term-children (map proof-tree (rest term))]]
    [:div.term-node [:div.term-label (str term)]]))

;; =============================================================================
;; 2. The shared loop
;; =============================================================================

(src (defn fixed-point
       "Apply step from start until (converged? a b).
        Returns every state, so a slide can replay it."
       [step converged? start
        & {:keys [max-steps] :or {max-steps 1000}}]
       (let [states (take (inc max-steps) (iterate step start))
             pairs  (map vector states (rest states))
             moving (count (take-while
                            (fn [[a b]] (not (converged? a b)))
                            pairs))]
         (vec (take (+ moving 2) states)))))

(defn settled?
  "Did a fixed-point run stop because it converged (rather than hitting max-steps)?"
  [converged? run]
  (let [[a b] (take-last 2 run)]
    (boolean (and (some? b) (converged? a b)))))

(src (defn newton-sqrt
       "Step for √n: average the guess and n / guess."
       [n]
       (fn [guess] (/ (+ guess (/ n guess)) 2))))

(src (defn close-enough? [a b]
       (< (abs (- a b)) 1e-12)))

(def sqrt-run (fixed-point (newton-sqrt 2) close-enough? 1.0))

;; =============================================================================
;; 3. Optimise: tomorrow's stop plan for a wind farm
;; =============================================================================

(defmap hills
  {・ :empty, 🌀 :turbine, 🐦 :flyway}
  [・ ・ ・ ・ ・ ・ ・ ・ ・ ・ ・ ・]
  [🐦 🐦 ・ ・ 🌀 ・ ・ ・ 🌀 ・ ・ ・]
  [・ ・ 🐦 🐦 ・ ・ ・ ・ ・ ・ ・ ・]
  [・ ・ ・ ・ 🐦 🐦 ・ 🌀 ・ ・ ・ 🌀]
  [・ ・ 🌀 ・ ・ ・ 🐦 🐦 ・ ・ ・ ・]
  [・ ・ ・ 🌀 ・ ・ ・ ・ 🐦 🐦 ・ ・]
  [・ ・ ・ ・ ・ ・ ・ ・ ・ ・ 🐦 🐦])

(def turbines (:turbine hills))
(def flyway (vec (sort (:flyway hills))))   ; west to east

(defn dist2 [[x1 y1] [x2 y2]]
  (let [dx (- x1 x2) dy (- y1 y2)] (+ (* dx dx) (* dy dy))))

(defn dist [a b] (js/Math.sqrt (reduce + (map #(let [d (- %1 %2)] (* d d)) a b))))

(defn sq [x] (* x x))

(def exposure
  "How much of the flyway passes close to each turbine: a map, so also a function."
  (into {} (for [t turbines]
             [t (reduce + (for [f flyway] (js/Math.exp (- (/ (dist2 t f) 2)))))])))

;; Illustrative hourly forecasts: energy is kWh per fully running turbine.
;; Bird risk is an exposure index, not calibrated expected fatalities.
(def hours (vec (range 8 20)))
(def birds (zipmap hours [0.2 0.5 1.2 2.4 3.6 4 3.6 2.4 1.2 0.5 0.2 0.1]))
(def energy (zipmap hours (map #(* 2 %) [92 85 70 55 42 38 40 48 66 98 120 115])))
(def budget 12)
(def slots (vec (for [t turbines h hours] [t h])))

(src (defn electricity-lost [run]
       (reduce +
               (for [[t h] slots]
                 (* (energy h) (- 1 (run [t h])))))))

(src (defn bird-risk [run]
       (reduce +
               (for [[t h] slots]
                 (* (exposure t) (birds h) (run [t h]))))))

(def day-plan
  {:objective electricity-lost
   :constraints [{:name 'birds :rel :<=
                  :gaps (fn [run]
                          [{:at nil :gap (- (bird-risk run) budget)}])}]})

(src (defn penalize
       "Model → Model. Breaking a constraint now costs
        weight × (how far it is broken)²."
       [model weight]
       (let [f (:objective model)
             fine (fn [plan]
                    (->> (violations model plan)
                         (map :violation)
                         (map #(* % %))
                         (reduce +)))]
         (assoc model
                :objective #(+ (f %) (* weight (fine %)))
                :constraints []))))

(def safe-plan (penalize day-plan 100))

(src (defn gradient
       "Objective → gradient: nudge each number a little,
        see how the score moves."
       [f]
       (fn [xs]
         (let [h 1e-5
               nudged (fn [i d] (f (update xs i + d)))]
           (mapv (fn [i] (/ (- (nudged i h) (nudged i (- h)))
                            (* 2 h)))
                 (range (count xs)))))))

(src (defn as-plan
  "72 numbers → settings keyed by [turbine hour]."
  [xs]
  (zipmap slots xs)))

(src (defn on-flat [model]
       (comp (:objective model) as-plan)))

(src (defn with-risk-penalty [cost risk limit weight]
       (fn [settings]
         (let [excess (max 0 (- (risk settings) limit))]
           (+ (cost settings) (* weight excess excess))))))

(src (def energy-cost (comp electricity-lost as-plan)))
(src (def risk-cost
       (with-risk-penalty energy-cost
                          (comp bird-risk as-plan)
                          12 100)))

(def first-guess
  "Half power everywhere: somewhere to start."
  {:x (vec (repeat (count slots) 0.5))})

(src (defn descent-step
       "Gradient and rate → Step. A step is State → State."
       [grad rate]
       (fn [{:keys [x] :as state}]
         (let [g (grad x)]
           (assoc state
                  :x (mapv - x (mapv #(* rate %) g))
                  :gradient g)))))

(src (defn clamped
       "Step → Step. Keep every number between lo and hi:
        a turbine runs between 0% and 100%."
       [step lo hi]
       (fn [state]
         (update (step state) :x
                 (fn [xs] (mapv #(max lo (min hi %)) xs))))))

(src (defn with-momentum
       "Step → Step. Keep some of the previous move."
       [step beta]
       (fn [{:keys [x velocity] :as state}]
         (let [proposal (step state)
               move (mapv - (:x proposal) x)
               old (or velocity (mapv (constantly 0) x))
               v (mapv + (mapv #(* beta %) old) move)]
           (assoc proposal :x (mapv + x v) :velocity v)))))

(src (defn with-adam
       "Step → Step. Reuse the base step's gradient; scale
        each coordinate by the size of its recent gradients."
       [step rate]
       (fn [{:keys [x m v t] :or {t 0} :as state}]
         (let [g (:gradient (step state))
               avg (fn [keep old new]
                     (+ (* keep old) (* (- 1 keep) new)))
               zero (mapv (constantly 0) x)
               t (inc t)
               m (mapv #(avg 0.9 %1 %2) (or m zero) g)
               v (mapv #(avg 0.999 %1 (* %2 %2)) (or v zero) g)
               m-hat (mapv #(/ % (- 1 (js/Math.pow 0.9 t))) m)
               v-hat (mapv #(/ % (- 1 (js/Math.pow 0.999 t))) v)
               move (fn [xi mi vi]
                      (- xi (/ (* rate mi)
                               (+ (js/Math.sqrt vi) 1e-8))))]
           (assoc state :x (mapv move x m-hat v-hat)
                  :gradient g :m m :v v :t t)))))

(src (defn moved-less-than
       "converged? for numbers: nothing moved more than eps."
       [eps]
       (fn [a b]
         (< (apply max (map #(abs (- %1 %2)) (:x a) (:x b)))
            eps))))

(def settle (moved-less-than 1e-3))
(def max-steps 600)

(src (defn solver-step [cost]
       (clamped
        (descent-step (gradient cost) 0.0001)
        0 1)))

(defn coordinate-update [cost]
  (let [i 4 x (:x first-guess) f cost
        h 1e-5 g ((gradient f) x)
        next ((solver-step cost) first-guess)]
    {:index i :slot (slots i) :x (x i) :h h
     :low (f (update x i - h)) :high (f (update x i + h))
     :slope (g i) :move (* -0.0001 (g i))
     :next (get-in next [:x i])}))

(src (def energy-step (solver-step energy-cost)))
(src (def risk-descent
       (descent-step (gradient risk-cost) 0.0001)))
(src (def risk-step (clamped risk-descent 0 1)))
(src (def momentum-step
       (clamped (with-momentum risk-descent 0.9) 0 1)))

(src (defn with-observer [step observe!]
       (fn [state]
         (let [next (step state)]
           (observe! state next)
           next))))

(src (def observed-costs (atom [])))
(src (defn record-cost! [_ next]
       (swap! observed-costs conj (risk-cost (:x next)))))

(def observed-states
  (vec (take 5 (iterate (with-observer risk-step record-cost!)
                        first-guess))))

(def penalty-candidates
  (let [a (:x first-guess)
        i (.indexOf slots [(turbines 2) 12])
        b (assoc a i 0)]
    (mapv (fn [x]
            (let [lost (energy-cost x) score (risk-cost x)]
              {:x x :lost lost :risk (bird-risk (as-plan x))
               :penalty (- score lost) :score score}))
          [a b])))

(def run-specs
  {:crowd {:label "electricity only" :step energy-step}
   :plain {:label "electricity + bird penalty" :step risk-step}
   :momentum {:label "same cost, with momentum" :step momentum-step}
   :adam {:label "with-adam 0.02"
          :step (clamped (with-adam risk-descent 0.02) 0 1)}})

;; A run takes seconds, so the deck computes it one state per animation frame and draws
;; while it works. advance is the loop of fixed-point, unrolled: the same step, the same
;; converged?, the same max-steps, and therefore the same states.

(defonce runs (atom {}))

(defn advance [{:keys [states done] :as r} step]
  (if done
    r
    (let [a (peek states)
          b (step a)
          states (conj states b)]
      (assoc r :states states :done (or (settle a b) (> (count states) max-steps))))))

(defn advance! [k]
  (swap! runs update k #(advance (or % {:states [first-guess] :done false}) (:step (run-specs k)))))

(defn run-of [k] (get-in @runs [k :states] [first-guess]))
(defn done? [k] (get-in @runs [k :done] false))

(defn finish!
  "Compute run k to the end, now."
  [k]
  (while (not (done? k)) (advance! k))
  (run-of k))

(defn steps-for
  "How many steps a slide replaying run k has: its states once done, room for all until then."
  [k]
  (if (done? k) (count (run-of k)) (inc max-steps)))

(defn plan-at [k step]
  (let [run (run-of k)]
    (as-plan (:x (nth run (min step (dec (count run))))))))

(def risk bird-risk)
(def lost electricity-lost)
(def full-energy (reduce + (for [[_ h] slots] (energy h))))

(defn label-of [t] (str "T" (inc (.indexOf turbines t))))

(defn stops
  "For each turbine that drops below half power: when."
  [plan]
  (for [t turbines
        :let [slow (filter #(< (plan [t %]) 0.5) hours)]
        :when (seq slow)]
    (str (label-of t) " " (first slow) ":00–" (inc (last slow)) ":00")))

(defn run-summary [k]
  (let [run (run-of k)
        plan (as-plan (:x (peek run)))]
    {:steps (dec (count run)) :done (done? k) :settled (settled? settle run)
     :lost (lost plan) :risk (risk plan) :stops (stops plan) :plan plan}))

;; ---- Tomorrow as a grid: a row per turbine, a column per hour ---------------------

(defn schedule
  "The plan as a grid, darker where a turbine runs harder, under the two forecasts
  that drive it. now marks one hour."
  [plan {:keys [w now] :or {w 660}}]
  (let [left 120 top 112 cw (/ (- w left 4) (count hours)) ch 36
        h (+ top (* ch (count turbines)) 34)
        x-of (fn [i] (+ left (* i cw)))
        bmax (apply max (vals birds))
        vmax (apply max (vals energy))]
    (svg w h
         [:text.small {:x 0 :y 40} "birds passing"]
         [:text.small {:x 0 :y (- top 6)} "turbine · near flyway"]
         [:text.small.price {:x 0 :y 70} "available kWh"]
         (for [[i hr] (map-indexed vector hours)
               :let [bh (* 70 (/ (birds hr) bmax))]]
           [:rect.bird-bar {:x (+ (x-of i) 5) :y (- 92 bh) :width (- cw 10) :height bh}])
         (polyline (for [[i hr] (map-indexed vector hours)]
                     [(+ (x-of i) (/ cw 2)) (- 92 (* 70 (/ (energy hr) vmax)))])
                   {:class "price-line"})
         (for [[r t] (map-indexed vector turbines)
               :let [y (+ top (* r ch))]]
           (list [:text.row {:x 0 :y (+ y 25)} (label-of t)]
                 [:text.small {:x 38 :y (+ y 24)} (fmt (exposure t) 2)]
                 (for [[i hr] (map-indexed vector hours)]
                   [:rect.cell {:x (+ (x-of i) 1) :y (+ y 1) :width (- cw 2) :height (- ch 2)
                                :style {:fill-opacity (.toFixed (+ 0.06 (* 0.94 (plan [t hr]))) 2)}}])))
         (for [[i hr] (map-indexed vector hours) :when (even? i)]
           [:text.small {:x (+ (x-of i) 4) :y (- h 8)} (str hr ":00")])
         (when now
           (let [i (.indexOf hours now)]
             [:rect.now {:x (x-of i) :y 8 :width cw :height (- h 30)}])))))

;; ---- The hills in 3D: a camera is a function from points to screen points ---------

(src (defn project
       "Camera → (point → screen point): turn round,
        tilt down, divide by distance."
       [{:keys [turn tilt distance focal centre]}]
       (let [ct (js/Math.cos turn) st (js/Math.sin turn)
             cp (js/Math.cos tilt) sp (js/Math.sin tilt)]
         (fn [p]
           (let [[x y z] (mapv - p centre)
                 x1 (- (* x ct) (* y st))
                 y1 (+ (* x st) (* y ct))
                 depth (- distance (* y1 cp) (* z sp))
                 up (- (* z cp) (* y1 sp))]
             [(/ (* focal x1) depth)
              (/ (* focal (- up)) depth)
              depth])))))

(defn- n1 "A number for SVG: plain minus sign, one decimal." [x] (.toFixed x 1))

(defn now-s [] (/ (js/performance.now) 1000))

(def hub-height 2.2)
(def rotor 1.1)
(def seconds-per-hour 1.5)

(defn ground
  "Height of the hills: a hill under every turbine."
  [[x y]]
  (+ 0.2 (reduce + (for [[tx ty] turbines]
                     (* 1.5 (js/Math.exp (- (/ (+ (sq (- x tx)) (sq (- y ty))) 3))))))))

(defn time-of-day
  "Where in tomorrow t seconds of playback are: the hour, and how far into it."
  [t]
  (let [day (* seconds-per-hour (count hours))
        within (mod t day)
        i (js/Math.floor (/ within seconds-per-hour))]
    {:i i :hour (hours i) :frac (- within (* i seconds-per-hour)) :days (js/Math.floor (/ t day))}))

(defn- spin
  "Blade angle at time t for a turbine whose run level, hour by hour, is levels."
  [levels t]
  (let [{:keys [i frac days]} (time-of-day t)
        per-day (* seconds-per-hour (reduce + levels))]
    (* 3 (+ (* days per-day) (* seconds-per-hour (reduce + (take i levels))) (* (levels i) frac)))))

(defn camera [t w]
  (let [[mx my] (:size hills)]
    {:turn (+ 0.35 (* 0.2 (js/Math.sin (* 0.11 t)))) :tilt 0.62 :distance (* 1.45 mx)
     :focal (* 1.15 w) :centre [(/ (dec mx) 2) (/ (dec my) 2) 2.4]}))

(defn- pts [ps] (str/join " " (map (fn [[x y]] (str (n1 x) "," (n1 y))) ps)))

(defn- along
  "The point a fraction u of the way along a path, by length."
  [path u]
  (let [ls (map dist path (rest path))
        total (reduce + ls)]
    (loop [[[a b] & more] (map vector path (rest path)) [l & ls] ls left (* u total)]
      (if (or (empty? more) (<= left l))
        (mapv + a (mapv #(* (/ left (max l 1e-9)) %) (mapv - b a)))
        (recur more ls (- left l))))))

(defn illustrated-encounter
  "A scripted rotor crossing for playback, not a collision or fatality model.
  The same bird crosses the same turbine each hour in both schedules."
  [plan t]
  (let [tb (apply max-key exposure turbines)
        [tx ty] tb
        {:keys [hour frac]} (time-of-day t)
        setting (if plan (plan [tb hour]) 1)
        contact [(+ tx (* 0.5 rotor)) ty (+ (ground tb) hub-height (* 0.4 rotor))]
        since (- (/ frac seconds-per-hour) 0.45)
        hit? (and (>= since 0) (> setting 1e-6))
        fall (max 0 (/ since 0.55))]
    {:turbine tb :hour hour :contact contact
     :mode (cond hit? :strike (neg? since) :approach :else :pass)
     :fall fall
     :position (if hit?
                 (mapv + contact [(* 0.5 fall) 0 (- (* 2.6 fall fall))])
                 (update contact 1 + (* 4 since)))}))

(defn- encounter-glyph [plan t P]
  (let [{:keys [position contact mode fall turbine]} (illustrated-encounter plan t)
        [x y] (P position)
        [hx hy] (P contact)
        hit? (= mode :strike)]
    [:g.encounter {:data-mode (name mode) :data-turbine (label-of turbine)}
     (when hit?
       [:g.strike-flash {:opacity (- 1 (* 0.35 fall))}
        [:circle {:cx (n1 hx) :cy (n1 hy) :r (+ 12 (* 30 fall))}]
        [:path {:d (str "M " (n1 (- hx 9)) " " (n1 (- hy 9))
                       " l 18 18 m 0 -18 l -18 18")}]
        [:text {:x (n1 (+ hx 20)) :y (n1 (- hy 20))} "Bird strike"]])
     [:g {:transform (str "translate(" (n1 x) "," (n1 y) ") rotate(" (if hit? (* 150 fall) 0) ")")
          :opacity (if hit? (- 1 (* 0.35 fall)) 1)}
      (bird-glyph 0 0 {:cls (if hit? "bird struck" "bird encounter-bird") :scale 0.85})]]))

(defn scene
  "The hills in 3D, at time t of tomorrow played fast: turbines turn as hard as the plan
  says (a nil plan runs them all), and birds pass along the flyway as the forecast says."
  [plan t {:keys [w h encounters?] :or {w 660 h 470}}]
  (let [[mx my] (:size hills)
        P (project (camera t w))
        xy (fn [p] (let [[x y] (P p)] [x y]))
        on-ground (fn [x y] [x y (ground [x y])])
        poly (fn [cls ps] [:polygon {:class cls :points (pts (map xy ps))}])
        seg (fn [cls a b] (let [[x1 y1] (xy a) [x2 y2] (xy b)]
                            [:line {:class cls :x1 (n1 x1) :y1 (n1 y1) :x2 (n1 x2) :y2 (n1 y2)}]))
        level (fn [t hr] (if plan (plan [t hr]) 1))
        {:keys [hour]} (time-of-day t)
        fly-path (mapv (fn [[x y]] [x y (+ (ground [x y]) hub-height)]) flyway)
        far-first (sort-by #(- (nth (P (on-ground (first %) (second %))) 2)) turbines)
        turbine (fn [[tx ty :as tb]]
                  (let [base (on-ground tx ty)
                        hub (update base 2 + hub-height)
                        levels (mapv #(level tb %) hours)
                        a (spin levels t)
                        stopped (< (level tb hour) 0.15)
                        tip (fn [a] [(+ tx (* rotor (js/Math.sin a))) ty (+ (hub 2) (* rotor (js/Math.cos a)))])
                        [hx hy] (xy hub)]
                    (list (poly "disc" (map #(tip (* % (/ js/Math.PI 14))) (range 28)))
                          (seg "tower" base hub)
                          (for [k (range 3)] (seg "blade" hub (tip (+ a (* k 2.0944)))))
                          [:circle {:class (if stopped "hub stopped" "hub") :cx (n1 hx) :cy (n1 hy) :r (if stopped 7 4)}]
                          (let [[lx ly] (xy (update base 2 - 0.1))]
                            [:text.tlabel {:x (n1 lx) :y (n1 (+ ly 20))} (label-of tb)]))))]
    [:svg.scene {:viewBox (str (- (/ w 2)) " " (- (/ h 2)) " " w " " h) :width w :height h
                 :xmlns "http://www.w3.org/2000/svg"}
     (for [y (range my)] [:polyline.terrain {:points (pts (map #(xy (on-ground % y)) (range -0.5 (- mx 0.4) 0.25)))}])
     (for [x (range mx)] [:polyline.terrain {:points (pts (map #(xy (on-ground x %)) (range -0.5 (- my 0.4) 0.25)))}])
     [:polyline.flyway {:points (pts (map xy fly-path))}]
     (map turbine far-first)
     (let [n (js/Math.round (* 2.5 (birds hour)))]
       (for [k (range n)
             :let [b (mapv + (along fly-path (mod (+ (/ t 5) (/ k n)) 1))
                           [0 (* 0.35 (js/Math.sin (* 3.1 k))) (* 0.3 (js/Math.cos (* 1.7 k)))])
                   [x y depth] (P b)]]
         (bird-glyph x y {:cls "bird flying" :scale (/ (* 0.03 w) depth)})))
     (when encounters? (encounter-glyph plan t P))
     [:text.clock {:x (n1 (+ (- (/ w 2)) 10)) :y (n1 (+ (- (/ h 2)) 40))} (str hour ":00")]]))

;; =============================================================================
;; 4. Decide: restocking a hydrogen station
;; =============================================================================

(def capacity 4)   ; fixed tank capacity; one customer uses one unit
(def price 10)     ; sales revenue per unit
(def holding 1)    ; cost per leftover unit per week

(src (def supply-calendar
       {:regular {:price 2 :limit 3 :next :tight}
        :tight   {:price 6 :limit 1 :next :regular}}))

(src (def demand-probabilities {0 0.4, 1 0.15, 3 0.45}))

(src (defn orders [[week stock]]
       (let [space (- capacity stock)
             available (get-in supply-calendar [week :limit])]
         (range (inc (min space available))))))

(src (defn remaining-stock [stock order demand]
       (max 0 (- (+ stock order) demand))))

(src (defn next-state [[week stock] order demand]
       [(get-in supply-calendar [week :next])
        (remaining-stock stock order demand)]))

(src (defn weekly-profit [[week stock] order demand]
       (let [sold (min demand (+ stock order))
             left (remaining-stock stock order demand)
             buy-price (get-in supply-calendar [week :price])]
         (- (* price sold)
            (* buy-price order)
            (* holding left)))))

(src (defn expected-return [V state order]
       (reduce +
         (for [[demand p] demand-probabilities]
           (* p (+ (weekly-profit state order demand)
                   (* 0.9 (V (next-state
                              state order demand)))))))))

(src (def restock
       {:states (vec (for [week [:regular :tight]
                          stock (range (inc capacity))]
                      [week stock]))
        :actions orders :q expected-return
        :outcomes demand-probabilities :discount 0.9}))

(def zero-values (zipmap (:states restock) (repeat 0)))
(def station-start [:regular 1])

(defn demand-outcomes [V state order]
  (for [[demand probability] (:outcomes restock)
        :let [next (next-state state order demand)
              reward (weekly-profit state order demand)
              future (* (:discount restock) (V next))]]
    {:demand demand :probability probability :next next
     :left (second next) :reward reward :future future
     :weighted (* probability (+ reward future))}))

(src (defn bellman
       "Model → (Values → Values).
        Each state gets its best expected return."
       [{:keys [states actions q]}]
       (fn [V]
         (zipmap states
                 (for [s states]
                   (apply max (map #(q V s %) (actions s))))))))

(src (defn largest-change [V W]
       (apply max (map #(abs (- (V %) (W %))) (keys V)))))

(def epsilon 0.001)

(def restock-run
  (fixed-point (bellman restock)
               #(< (largest-change %1 %2) epsilon)
               zero-values))

(src (defn best-order
       "Model + values → policy: best order per state."
       [{:keys [states actions q]} V]
       (into (sorted-map)
             (for [s states]
               [s (apply max-key #(q V s %)
                         (reverse (actions s)))]))))

(src (defn order-up-to [target]
       (fn [[_ stock :as state]]
         (min (max 0 (- target stock))
              (last (orders state))))))

(src (defn evaluate-policy [policy]
       (fn [V]
         (into {} (for [s (:states restock)]
                    [s (expected-return V s (policy s))])))))

(def station-policy (best-order restock (peek restock-run)))
(def myopic-policy (best-order restock zero-values))

(def policy-comparison
  (mapv (fn [[label policy]]
          (let [values (iterate (evaluate-policy policy)
                                zero-values)]
            {:label label :policy policy
             :one-week ((nth values 1) station-start)
             :twenty-weeks ((nth values 20) station-start)}))
        [["Fill to 2" (order-up-to 2)]
         ["Fill to 3" (order-up-to 3)]
         ["Fill to 4" (order-up-to 4)]
         ["This week only" myopic-policy]
         ["Bellman" station-policy]]))

(defn residuals [run] (mapv (fn [[a b]] (largest-change a b)) (partition 2 1 run)))

(def restock-certificate
  (let [run restock-run
        candidate (nth run (- (count run) 2))
        r (largest-change candidate (peek run))
        gamma (:discount restock)]
    {:updates (- (count run) 2) :residual r :bound (/ r (- 1 gamma)) :epsilon-bound (/ epsilon (- 1 gamma))
     :candidate candidate}))

;; =============================================================================
;; 5. Verify: the bird stop controller
;; =============================================================================

;; Simplified from wildlife.edge (RH2EDIN Reto 3). Each 10-minute slot draws one random number u;
;; the turbine stops when u is below the table's stop probability for the detection level.
(def stop-table {:seen {0 0.05, 1 0.6, 2 1.0}})   ; situations the table was trained on
(def draws {:low 0.02 :mid 0.3 :high 0.8})          ; one draw per slot, in three bands

(src (defn table-decision
       "Stop when this slot's draw falls under the
        table's stop probability."
       [table]
       (fn [{:keys [ctx det u]}]
         (if (< u (get-in table [ctx det] 0)) :stop :run))))

(src (defn with-shield
       "Decision → Decision. Where the table has never
        been, stop on any detection."
       [decide table]
       (fn [{:keys [ctx det] :as s}]
         (if (contains? table ctx)
           (decide s)
           (if (pos? det) :stop :run)))))

(src (defn tick-controller
       "Decide once per 10-minute slot, from the
        detections of the slot that just ended."
       [decide]
       (fn [s {:keys [type] :as event}]
         (case type
           :tick (let [s (merge s (select-keys event [:u :ctx]))]
                   (assoc s :action (decide s) :det 0))
           :detect (update s :det max (:level event))))))

(src (defn react-to-detections
       "Controller → Controller. Also decide the moment
        a stronger detection arrives."
       [controller decide]
       (fn [s event]
         (let [after (controller s event)]
           (if (and (= :detect (:type event))
                    (> (:det after) (:det s)))
             (assoc after :action (decide after))
             after)))))

(def decide-by-table (table-decision stop-table))
(src (def shielded-decision
       (with-shield decide-by-table stop-table)))

(def controllers
  [{:id :tick-only :label "decides every 10 minutes"
    :code "(tick-controller decide-by-table)"
    :controller (tick-controller decide-by-table)}
   {:id :reacting :label "+ reacts to detections"
    :code "(react-to-detections\n  (tick-controller decide-by-table)\n  decide-by-table)"
    :controller (react-to-detections (tick-controller decide-by-table) decide-by-table)}
   {:id :shielded :label "+ shield for unseen situations"
    :code "(react-to-detections\n  (tick-controller shielded-decision)\n  shielded-decision)"
    :controller (react-to-detections (tick-controller shielded-decision) shielded-decision)}])

(def shield-input
  {:ctx :unseen :det 0 :u 0.3
   :action :run :minute 1 :waiting nil})
(def shield-event {:type :detect :level 2})
(def shield-comparison
  (into {} (for [{:keys [id controller]} controllers]
             [id (controller shield-input shield-event)])))

(src (defn unanswered? [{:keys [det action]}]
       (and (= 2 det) (= :run action))))

(defn ticks [controller s]
  (for [u (vals draws) ctx [:seen :unseen]]
    (-> (controller s {:type :tick :u u :ctx ctx}) (assoc :minute 0 :waiting nil))))

(src (defn controller-successors
       "Controller → (State → next states).
        Record detection before the next timer tick."
       [controller]
       (fn [{:keys [minute waiting] :as s}]
         (if (= minute 10)
           (set (ticks controller s))
           (set
             (for [event [nil
                          {:type :detect :level 1}
                          {:type :detect :level 2}]
                   :let [s (if event (controller s event) s)]]
               (assoc s :minute (inc minute)
                        :waiting (when (unanswered? s)
                                   (inc (or waiting 0))))))))))

(src (defn reach-step [successors]
       (fn [states]
         (into states (mapcat successors states)))))

(src (defn reachable [successors initial]
       (fixed-point (reach-step successors)
                    = (set initial))))

(defn initial-states [controller]
  (ticks controller {:det 0 :action :run :minute 0 :waiting nil}))

(def reach-runs
  (into {} (for [{:keys [id controller]} controllers]
             [id (reachable (controller-successors controller) (initial-states controller))])))

;; The stage version uses a deliberately simpler shield: every detection stops.
;; The earlier table-specific shield remains available in the study examples.
(src (defn stop-for-birds [decide]
       (fn [state]
         (if (pos? (:det state))
           :stop
           (decide state)))))

(def bird-decision (stop-for-birds decide-by-table))
(def bird-controller
  (react-to-detections (tick-controller bird-decision) bird-decision))
(def bird-reach-run
  (reachable (controller-successors bird-controller)
             (initial-states bird-controller)))

(defn shield-frame [shield? elapsed]
  (let [detected? (>= elapsed 2)
        controller (if shield? bird-controller (:controller (controllers 1)))
        after (if detected? (controller shield-input shield-event) shield-input)
        stopped? (= :stop (:action after))
        contact-time (/ 400 90)
        crossed? (>= elapsed contact-time)
        hit? (and crossed? (not stopped?))
        fall (min 1 (max 0 (/ (- elapsed contact-time) 1.1)))]
    {:detected? detected? :action (:action after)
     :mode (cond hit? :strike crossed? :pass :else :approach)
     :bird-x (if hit? (+ 450 (* 35 fall))
                 (+ 50 (* 90 (min 6 (max 0 elapsed)))))
     :bird-y (if hit? (+ 80 (* 170 fall fall)) 80)
     ;; Align a moving blade with the illustrated contact at [450, 80].
     :angle (- (* 90 (if stopped? 2 elapsed)) 40)}))

(defn shield-animation [shield? elapsed]
  (let [{:keys [detected? action bird-x bird-y angle mode]} (shield-frame shield? elapsed)
        stopped? (= action :stop)
        reached (peek (if shield? bird-reach-run (reach-runs :reacting)))
        failures (count (filter unanswered? reached))]
    [:div {:data-shield (str shield?) :data-action (name action)
           :data-detected (str detected?) :data-encounter (name mode)}
     [:p.aside (if shield? "With the shield" "Without the shield")]
     [:svg.scene.shield-scene {:viewBox "0 0 680 300" :width 680 :height 300
                               :role "img" :aria-label (str "Bird detection; turbine command " (name action))}
      [:line {:x1 20 :y1 270 :x2 660 :y2 270 :stroke "var(--line)"}]
      [:line.tower {:x1 450 :y1 125 :x2 450 :y2 270}]
      [:g {:transform (str "translate(450,125) rotate(" angle ")") :data-rotor-angle angle}
       (for [a [0 120 240]]
         [:line.blade {:x1 0 :y1 0 :x2 0 :y2 -88 :transform (str "rotate(" a ")")}])]
      [:circle.hub {:cx 450 :cy 125 :r 7}]
      [:line {:x1 230 :y1 30 :x2 230 :y2 240 :stroke "var(--accent)" :stroke-dasharray "5 5"}]
      [:text {:x 230 :y 292 :text-anchor "middle" :fill "var(--muted)" :font-size 19} "Detection"]
      (when (= mode :strike)
        [:g.strike-flash
         [:circle {:cx 450 :cy 80 :r 23}]
         [:path {:d "M 418 80 H 430 M 470 80 H 482 M 450 48 V 60 M 450 100 V 112"}]])
      (bird-glyph bird-x bird-y {:cls (if (= mode :strike) "bird struck" "bird encounter-bird")
                                :scale 1.4})
      [:text {:x 450 :y 32 :text-anchor "middle" :font-size 26
              :fill (if stopped? "var(--ok)" "var(--ink)")}
       (.toUpperCase (name action))]]
     [:p.aside {:class (when detected? (if stopped? "ok" "bad"))}
      (case mode
        :strike "Still RUN → bird hits the rotor"
        :pass "STOP → bird passes the turbine"
        (if detected?
          (if stopped? "Bird detected → STOP command" "Bird detected → still RUN")
          "No detection → RUN"))]
     [:p.caption (str "Exhaustive model check: " failures " states break the rule.")]]))

(defonce shield-start (atom 0))

(defn longest-wait-path
  "Breadth-first search with parents: the reachable path to the longest unanswered wait."
  [controller]
  (let [succ (controller-successors controller)
        init (initial-states controller)]
    (loop [frontier init
           parent (zipmap init (repeat nil))]
      (if (empty? frontier)
        (let [worst (apply max-key #(or (:waiting %) -1) (keys parent))]
          (when (:waiting worst)
            (reverse (take-while some? (iterate parent worst)))))
        (let [[parent fresh] (reduce (fn [[p fresh] [s n]]
                                       (if (contains? p n) [p fresh] [(assoc p n s) (conj fresh n)]))
                                     [parent []]
                                     (for [s frontier n (succ s)] [s n]))]
          (recur fresh parent))))))

(def counterexamples
  (into {} (for [{:keys [id controller]} controllers] [id (longest-wait-path controller)])))

;; ---- Rules over traces, and a search for the worst case -------------------------

(src (defn always [rule]
       (fn [trace]
         (reduce min ##Inf
                 (map #(rule trace %) (range (count trace)))))))

(src (defn implies [condition rule]
       (fn [trace i]
         (if (condition (trace i)) (rule trace i) ##Inf))))

(src (defn within [seconds goal]
       (fn [trace i]
         (let [start (:t (trace i))
               done (->> (range i (count trace))
                         (filter #(goal (trace %)))
                         first)
               end (if done (:t (trace done)) ##Inf)]
           (- seconds (- end start))))))

(def confident? #(= 2 (:det %)))
(def stopped? #(= :stop (:action %)))

(src (def bird-rule
       (always (implies confident? (within 60 stopped?)))))

(defn slot-trace
  "One slot, in seconds: a seen situation, the high draw band, one confident detection at t."
  [controller t]
  (let [s0 (controller {:det 0 :action :run} {:type :tick :u (:high draws) :ctx :seen})
        s1 (controller s0 {:type :detect :level 2})
        s2 (controller s1 {:type :tick :u (:high draws) :ctx :seen})]
    [(assoc s0 :t 0) (assoc s1 :t t) (assoc s2 :t 600)]))

(src (defn search-step
       "Try a little earlier and a little later; keep the
        worse case, else look closer."
       [score]
       (fn [{:keys [t delta] :as s}]
         (let [tries (for [c [(- t delta) (+ t delta)]
                           :when (< 0 c 600)]
                       {:t c :score (score c)})
               worst (first (sort-by :score tries))]
           (if (and worst (< (:score worst) (:score s)))
             (merge s worst)
             (assoc s :delta (/ delta 2)))))))

(defn falsify [controller]
  (let [score #(bird-rule (slot-trace controller %))]
    (fixed-point (search-step score) (fn [_ b] (< (:delta b) 0.5))
                 {:t 300 :delta 128 :score (score 300)})))

(def falsify-runs
  {:tick-only (falsify (:controller (first controllers)))
   :shielded (falsify (:controller (last controllers)))})

;; =============================================================================
;; 6. Receipt: the trailer plan (RH2EDIN Reto 2 instance)
;; =============================================================================

(def sources [:hub :electrolyser])
(def stations [:port :sotogrande :tarifa])
(def routes (vec (for [src sources s stations] [src s])))
(def km {[:hub :port] 8, [:hub :sotogrande] 19, [:hub :tarifa] 33
         [:electrolyser :port] 0, [:electrolyser :sotogrande] 28, [:electrolyser :tarifa] 26})
(def supply {:hub 18 :electrolyser 8})
(def need {:port 15 :sotogrande 2 :tarifa 1})

(defmodel trailers [loads]
  (minimize (sum [r routes] (* (km r) (loads r))))
  (subject-to
   [supply (for-all [src sources]
             (<= (sum [s stations] (loads [src s]))
                 (supply src)))]
   [demand (for-all [s stations]
             (>= (sum [src sources] (loads [src s]))
                 (need s)))]))

(src (defn linear-rows
       "Read a linear model by calling it: a variable's
        coefficient is the change it makes on its own.
        Rows come out as coefs · x ≥ rhs."
       [model variables]
       (let [f (:objective model)
             zero (zipmap variables (repeat 0))
             unit #(assoc zero % 1)
             indexed #(map-indexed vector %)
             effect (fn [g k v] (- (:gap (nth (g (unit v)) k))
                                   (:gap (nth (g zero) k))))]
         {:variables variables
          :costs (mapv #(- (f (unit %)) (f zero)) variables)
          :rows
          (vec (for [{:keys [name rel gaps]} (:constraints model)
                     :let [sign (if (= rel :<=) -1 1)]
                     [k {:keys [at gap]}] (indexed (gaps zero))]
                 {:name name :at at
                  :coefs (mapv #(* sign (effect gaps k %))
                               variables)
                  :rhs (* sign (- gap))}))})))

(def lp (linear-rows trailers routes))

;; The solver works on the other side of the receipt: prices y ≥ 0 with Aᵀy ≤ costs, maximise
;; rhs · y. Its starting point (all prices zero) is already valid, so no warm-up phase is needed.
(defn tableau [{:keys [costs rows]}]
  (let [n-rows (count rows) n-vars (count costs)]
    {:basis (vec (range n-rows (+ n-rows n-vars)))
     :rows (vec (for [j (range n-vars)]
                  {:a (into (mapv #(nth (:coefs %) j) rows) (map #(if (= % j) 1 0) (range n-vars)))
                   :rhs (costs j)}))
     :z (into (mapv #(- (:rhs %)) rows) (repeat n-vars 0))
     :value 0}))

(def tiny 1e-9)

(src (defn pivot
       "Tableau → Tableau: one improvement. At the optimum
        it returns the tableau unchanged."
       [{:keys [z rows basis value] :as t}]
       (if-let [j (->> z
                       (keep-indexed #(when (< %2 (- tiny)) %1))
                       first)]
         (let [room (fn [i {:keys [a rhs]}]
                      (when (> (a j) tiny) [(/ rhs (a j)) i]))
               i (->> rows (keep-indexed room) sort first second)
               {:keys [a rhs]} (rows i)
               p {:a (mapv #(/ % (a j)) a) :rhs (/ rhs (a j))}
               clear (fn [{ra :a rr :rhs}]
                       (let [f (ra j)]
                         {:a (mapv #(- %1 (* f %2)) ra (:a p))
                          :rhs (- rr (* f (:rhs p)))}))]
           (assoc t
                  :rows (vec (map-indexed
                              #(if (= %1 i) p (clear %2)) rows))
                  :z (mapv - z (map #(* (z j) %) (:a p)))
                  :value (- value (* (z j) (:rhs p)))
                  :basis (assoc basis i j)
                  :entered j))
         t)))

(def solve-run (fixed-point pivot = (tableau lp)))

(defn read-answer [{t-rows :rows :keys [basis z]} lp-data]
  (let [n-rows (count (:rows lp-data))
        prices (reduce (fn [y [i b]] (if (< b n-rows) (assoc y b (:rhs (t-rows i))) y))
                       (vec (repeat n-rows 0)) (map-indexed vector basis))
        plan (subvec z n-rows)]
    {:plan (mapv #(js/Math.round %) plan) :prices (mapv #(js/Math.round %) prices)}))

(def answer (read-answer (peek solve-run) lp))

(src (defn check-receipt
       "Is the plan the cheapest possible? Arithmetic only:
        no trust in the solver."
       [{:keys [costs rows]} plan prices]
       (let [dot #(reduce + (map * %1 %2))
             column (fn [j] (map #(nth (:coefs %) j) rows))]
         {:plan-is-valid
          (every? #(>= (dot (:coefs %) plan) (:rhs %)) rows)
          :prices-are-fair
          (every? #(<= (dot (column %) prices) (costs %))
                  (range (count plan)))
          :plan-costs (dot costs plan)
          :no-plan-costs-less-than
          (dot (map :rhs rows) prices)})))

(defn receipt-ok? [{:keys [plan-is-valid prices-are-fair plan-costs no-plan-costs-less-than]}]
  (and plan-is-valid prices-are-fair (= plan-costs no-plan-costs-less-than)))

(def receipt (check-receipt lp (:plan answer) (:prices answer)))

(def tampered-prices
  (let [i (first (keep-indexed (fn [i r] (when (= :tarifa (:at r)) i)) (:rows lp)))]
    (update (:prices answer) i inc)))

(def tampered (check-receipt lp (:plan answer) tampered-prices))

(def kernel-verdict
  ;; Recorded: ../rh2edin-challenge/challenge-2-hrs/proofs/rainbow-results.edn (Rainbow Pro on the open core)
  {:id "c2/trailer-allocation" :status :certified :objective 127 :axioms #{}
   :theorem "optimization/lp-instance-optimal"
   :hash "sha256:f95c317766803f4f5a309e7748d8435fdb7d9e5186ff7019d6d88dff2f48a32a"
   :seconds 173.1})

(def kernel-rejection
  ;; Recorded: resources/rainbow-evidence.edn, checked 2026-10-09 against Rainbow 3f411ae
  {:attempt "the natural number 0, offered as the proof of the momentum recurrence"
   :status :rejected :checked-at "2026-10-09"})

;; =============================================================================
;; 7. Slides
;; =============================================================================

(defn label [route] (str (name (first route)) " → " (name (second route))))

(defn row-label [{cname :name at :at}]
  (if (= cname 'demand) (str "a load to " (name at)) (str (name at) " capacity")))

(defn frame-of
  "Map a step (0..steps-1) onto a run of n states, spreading evenly."
  [step steps n]
  (if (<= steps 1) (dec n) (js/Math.round (* (/ step (dec steps)) (dec n)))))

(defn kv-table [rows]
  [:table.kv (for [[k v] rows] [:tr [:th k] [:td v]])])

(defn check-mark [ok] (if ok [:span.ok "yes"] [:span.bad "no"]))

(defn progress-label [step steps]
  [:div.step-label (str "step " (inc step) " / " steps)])

(defn plan-slide
  "A slide that replays run k as tomorrow's schedule, next to the code that makes its step."
  [k title left caption final notes]
  {:section "Optimise" :title title :runs [k] :steps #(steps-for k)
   :render (fn [step]
             (let [run (run-of k)
                   i (min step (dec (count run)))]
               [:div.two
                left
                [:div (schedule (plan-at k step) {})
                 [:div.caption (str caption " · " (if (done? k)
                                                     (str "state " i " of " (dec (count run)))
                                                     (str "computing · state " i)))]
                 (when (= k :plain)
                   (let [plan (plan-at k step)
                         all-on (zipmap slots (repeat 1))]
                     [:table.comparison
                      [:tr [:th "plan"] [:th "generated kWh"] [:th "bird risk"]]
                      [:tr [:td "all running"] [:td (fmt full-energy 0)] [:td (fmt (risk all-on) 1)]]
                      [:tr [:td "this state"] [:td (fmt (- full-energy (lost plan)) 0)] [:td (fmt (risk plan) 1)]]]))
                 (when (and (= k :momentum) (pos? i))
                   (let [previous (nth run (dec i))
                         base (descent-step (gradient (on-flat safe-plan)) 0.0001)
                         move (- (get-in (base previous) [:x 4]) (get-in previous [:x 4]))
                         carry (* 0.9 (get-in previous [:velocity 4] 0))]
                     [:div
                      [:div.caption "Turbine 1, noon · one coordinate of this update"]
                      [:table.comparison
                       [:tr [:th "base move"] [:th "+ 90% of old velocity"] [:th "= new velocity"]]
                       [:tr [:td (fmt move 4)] [:td (fmt carry 4)] [:td (fmt (+ move carry) 4)]]]
                      [:p.caption "Add velocity to the old setting; clamp the result to 0…1."]]))
                 (when (and (done? k) (= i (dec (count run))))
                   (final (run-summary k)))]]))
   :notes notes})

;; Visuals use the same state and functions as the demonstrations.
(defn plan-metrics [plan]
  (kv-table [["generated electricity" (str (fmt (- full-energy (lost plan)) 0) " kWh")]
             ["bird-risk index" (fmt (risk plan) 2)]]))

(defn exposure-grid []
  (let [w 680 left 55 top 40 cw 49 ch 44
        high (apply max (for [[t h] slots]
                          (* (exposure t) (birds h))))]
    (svg w 370
         [:text.small {:x left :y 20} "potential bird exposure at 100% operation"]
         (for [[r t] (map-indexed vector turbines)]
           [:g
            [:text {:x 0 :y (+ top (* r ch) 29) :font-size 20} (label-of t)]
            (for [[i h] (map-indexed vector hours)
                  :let [amount (* (exposure t) (birds h))]]
              [:rect {:x (+ left (* i cw)) :y (+ top (* r ch))
                      :width (- cw 3) :height (- ch 3)
                      :fill "var(--accent)"
                      :fill-opacity (+ 0.05 (* 0.95 (/ amount high)))}])])
         (for [[i h] (map-indexed vector hours) :when (even? i)]
           [:text.small {:x (+ left (* i cw)) :y 327} (str h ":00")])
         [:text.small {:x left :y 360} "pale: less exposure · dark: more exposure"])))

(defn coordinate-plot [cost]
  (let [xs (:x first-guess) i 4
        points (for [x (range 0.4 0.601 0.01)] [x (cost (assoc xs i x))])
        low (apply min (map second points)) high (apply max (map second points))
        xy (fn [[x y]] [(+ 50 (* 2500 (- x 0.4)))
                       (+ 30 (* 220 (/ (- high y) (max 1 (- high low)))))])]
    (svg 620 320
         [:text.small {:x 50 :y 20} "electricity lost (kWh)"]
         [:line.axis {:x1 50 :y1 260 :x2 560 :y2 260}]
         (polyline (map xy points) {:class "curve"})
         (for [x [0.4 0.5 0.6]]
           [:text.small {:x (- (first (xy [x low])) 15) :y 289} (str (fmt (* 100 x)) "%")])
         (let [[x y] (xy [0.5 (cost xs)])]
           [:g [:circle {:cx x :cy y :r 7 :fill "var(--accent)"}]
            [:text.small {:x (+ x 14) :y (- y 12)} (str (fmt (cost xs)) " kWh")]])
         [:text.small {:x 120 :y 315} "turbine 1 at noon · other settings held at 50%"])))

(defn convergence-chart []
  (let [traces (mapv (fn [k] (mapv #(risk-cost (:x %)) (run-of k))) [:plain :momentum])
        n (max 1 (dec (apply max (map count traces))))
        top (max 1 (apply max (mapcat identity traces)))
        point (fn [i y] [(+ 48 (* 540 (/ i n))) (+ 30 (* 200 (- 1 (/ y top))))])]
    (svg 640 285
         [:text.small {:x 48 :y 18} "same cost function"]
         [:line.axis {:x1 48 :y1 232 :x2 600 :y2 232}]
         (for [[trace colour] (map vector traces ["var(--muted)" "var(--accent)"])]
           (polyline (map-indexed point trace) {:stroke colour :stroke-width 3}))
         [:text.small {:x 48 :y 259} "0"]
         [:text.small {:x 540 :y 259} (str n " updates")]
         [:text.small {:x 48 :y 282} "gray: plain descent · blue: with momentum"])))

(defn truck-icon [x y colour tanker?]
  [:g {:transform (str "translate(" x "," y ")")}
   [:rect {:x 0 :y 0 :width 60 :height 32 :rx (if tanker? 14 3) :fill colour}]
   [:path {:d "M 64 8 L 83 8 L 94 21 L 94 36 L 64 36 Z" :fill colour}]
   [:rect {:x 70 :y 12 :width 12 :height 10 :fill "var(--bg)"}]
   [:circle {:cx 17 :cy 38 :r 8 :fill "var(--ink)"}]
   [:circle {:cx 76 :cy 38 :r 8 :fill "var(--ink)"}]
   (when tanker? [:text {:x 19 :y 23 :fill "var(--bg)" :font-size 20} "H₂"])])

(defn hydrogen-scene [stock order demand phase]
  (let [available (+ stock order)
        sold (min demand available)
        tank (case phase 0 stock 1 available (- available sold))]
    (svg 680 310
         [:text.small {:x 20 :y 24} (str "delivery: " order " units")]
         (truck-icon (if (= phase 1) 155 20) 60 "var(--accent)" true)
         [:path {:d "M 125 112 L 280 112" :stroke "var(--accent)" :stroke-width 3 :stroke-dasharray "8 6"}]
         [:text.small {:x 320 :y 24} (str "tank: " tank " / 4")]
         [:rect {:x 320 :y 38 :width 104 :height 142 :rx 12 :fill "var(--wash)" :stroke "var(--ink)" :stroke-width 2}]
         (for [i (range 4)]
           [:rect {:x 328 :y (- 145 (* 32 i)) :width 88 :height 26 :rx 4
                   :fill (if (< i tank) "var(--accent)" "var(--line)")}])
         [:path {:d "M 424 132 L 470 132 L 470 180" :fill "none" :stroke "var(--ink)" :stroke-width 4}]
         [:text.small {:x 490 :y 100} "customer trucks"]
         [:text.small {:x 490 :y 126} "1 unit each"]
         (if (< phase 2)
           [:g
            [:rect {:x 20 :y 200 :width 610 :height 100 :rx 8 :fill "none"
                    :stroke "var(--muted)" :stroke-dasharray "6 5"}]
            [:text {:x 40 :y 238 :font-size 24} "Customers have not arrived."]
            [:text {:x 40 :y 276 :font-size 24} "0, 1 or 3 trucks?"]]
           (for [i (range demand)
                 :let [served? (< i sold)
                       colour (if served? "var(--ok)" "var(--bad)")
                       x (+ 40 (* 205 i))]]
             [:g.customer (truck-icon x 215 colour false)
              [:text.small {:x x :y 292} (if served? "filled" "turned away")]])))))

(defn station-value-chart [V]
  (let [top (max 1 (apply max (vals (peek restock-run))))
        height (fn [v] (* 220 (/ v top)))]
    (svg 680 360
         [:rect {:x 45 :y 10 :width 16 :height 16 :fill "var(--accent)"}]
         [:text {:x 68 :y 24 :font-size 18} "regular week"]
         [:rect {:x 260 :y 10 :width 16 :height 16 :fill "var(--warn)"}]
         [:text {:x 283 :y 24 :font-size 18} "tight week"]
         (for [fraction [0 0.5 1]
               :let [y (- 290 (* 220 fraction))]]
           [:g [:line.grid {:x1 42 :y1 y :x2 660 :y2 y}]
            [:text.small {:x 2 :y (+ y 5)} (fmt (* fraction top) 0)]])
         (for [stock (range (inc capacity)) :let [x (+ 56 (* stock 120))]]
           [:g
            (for [[week dx colour] [[:regular 0 "var(--accent)"] [:tight 44 "var(--warn)"]]
                  :let [value (V [week stock]) h (height value)]]
              [:g
               [:rect {:x (+ x dx) :y (- 290 h) :width 34 :height (max 1 h) :fill colour}]
               [:text.small {:x (+ x dx 17) :y (- 280 h) :text-anchor "middle"} (fmt value 1)]])
            [:text {:x (+ x 38) :y 319 :text-anchor "middle" :font-size 19} (str "stock " stock)]])
         [:text.small {:x 140 :y 351} "hydrogen in the tank at the start of the week (units)"])))

(defn controller-branches [controller]
  (let [s shield-input
        branches [["no detection" nil] ["possible bird" {:type :detect :level 1}]
                  ["confident bird" shield-event]]]
    (svg 650 330
         [:rect {:x 10 :y 123 :width 190 :height 85 :rx 8 :fill "var(--wash)" :stroke "var(--muted)"}]
         [:text.small {:x 25 :y 150} "minute 1 · RUN"]
         [:text.small {:x 25 :y 178} "no detection yet"]
         (for [[i [label event]] (map-indexed vector branches)
               :let [after (if event (controller s event) s) y (+ 16 (* i 105)) bad? (unanswered? after)]]
           [:g [:line {:x1 200 :y1 165 :x2 330 :y2 (+ y 40) :stroke "var(--muted)" :stroke-width 2}]
            [:rect {:x 330 :y y :width 300 :height 80 :rx 8 :fill "var(--wash)"
                    :stroke (if bad? "var(--bad)" "var(--ok)") :stroke-width 3}]
            [:text.small {:x 346 :y (+ y 28)} label]
            [:text {:x 346 :y (+ y 58) :font-size 24 :fill (if bad? "var(--bad)" "var(--ink)")}
             (str (str/upper-case (name (:action after))) (when bad? " · rule broken"))]]))))


;; ---- Slide content ---------------------------------------------------------------

(defn bullets [& items] [:ul.bullets (for [i items] [:li i])])

(def section-order ["Hello" "Clojure" "OR" "HOFs" "Optimise" "Decide" "Validate" "Rainbow" "The book" "Thank you"])

(def slides
  [
   {:section "Hello" :title "Rebuilding Operations Research in Higher-Order Functions"
    :render (fn [_]
              [:div.title-slide
               [:div.kicker "Lambda World 2026"]
               [:h1.big "Rebuilding Operations Research" [:br] "in Higher-Order Functions"]
               [:div.byline "Jiri Knesl · Flexiana"]])
    :notes "15 seconds. Today I want to show you what higher-order functions let us do inside operations research."}

   {:section "Hello" :title "About me"
    :render (fn [_]
              [:div
               (bullets "Started programming 34 years ago"
                        "Using Clojure for 14 years"
                        "Economist by education"
                        "Started Barcelona Meetup Group 13 years ago"
                        [:span "Interviews with Clojure open-source authors"
                         [:br] "(youtube.com/@flexianadev)"])])
    :notes "Started programming 34 years ago; using Clojure for 14 years. Economist by education. Started Barcelona Meetup Group 13 years ago. Interviews many Clojure open-source authors on the Flexiana YouTube channel. Next: why I work in Clojure."}

   {:section "Clojure" :title "Why Clojure?"
    :render (fn [_]
              [:div
               (bullets "Clojure is a simple & functional language"
                        "Immutable data"
                        "REPL-oriented programming"
                        "Code is data")])
    :notes "Two minutes. Explain each point aloud: functions as values; old states remain available; experiment in the REPL; code can be inspected and transformed as data. Those are reasons for my choice, not claims that other languages cannot do this. Next introduce the three areas of operations research before the higher-order functions."}

   {:section "OR" :title "Operations research"
    :render (fn [_]
              [:div
               [:p.aside "Choices, objectives, constraints."]
               (bullets "Optimization: choose a plan."
                        "Decision-making: act under uncertainty."
                        "Validation: check the rules.")])
    :notes "Two minutes. A plan is a choice; an objective scores it; constraints limit it. Uncertainty changes what a good decision means. Validation asks whether a system meets its specification. Keep the concrete examples as surprises for their sections."}

   {:section "HOFs" :title "iterate takes an update function" :steps 2
    :render (fn [phase]
              [:div
               [:div.two.even
                [:div (code 'newton-sqrt {:marks #{"fn"}})
                 [:p.caption "Find √2, starting from a positive guess."]]
                [:div
                 (code "(def step (newton-sqrt 2))\n\n(take 5 (iterate step 1.0))" {:marks #{"iterate"}})
                 [:p "newton-sqrt returns a function. iterate takes it as an argument."]]]
               [:div.state-chain
                (for [[i x] (map-indexed vector (take (if (zero? phase) 1 5) sqrt-run))]
                  [:div.state-node [:span.caption (str "state " i)]
                   [:strong (fmt x 6)]
                   [:span.caption (if (zero? i) "initial guess" "step(previous)")]])]])
    :notes "One example throughout: positive square root of two. defn names a function; fn returns an anonymous update capturing n. The first iterate element is the initial guess, not an update. Reveal the next four states together: each output is the next input. take requests five states from a lazy sequence; it does not test convergence. No separate map lesson or switch to square root of four. The update is Newton's method for positive n and a positive starting guess; iterate itself supplies no convergence guarantee."}

   {:section "HOFs" :title "Choose when to stop" :steps 2
    :render (fn [phase]
              (let [[a b] (take-last 2 sqrt-run)]
                [:div.two.even
                 [:div
                  (if (zero? phase)
                    [:div
                     (code "(fixed-point (newton-sqrt 2)\n             close-enough?\n             1.0)")
                     (code 'close-enough?)]
                    (code 'fixed-point {:size :small :marks #{"iterate" "converged?"}}))]
                 [:div
                  [:p.aside "A fixed point is unchanged by the update."]
                  [:p "For this step: x = (x + 2/x) / 2, so x² = 2."]
                  [:table.trace
                   [:tr [:td.k "previous"] [:td.v (.toFixed a 15)]]
                   [:tr [:td.k "next"] [:td.v (.toFixed b 15)]]]
                  [:p.aside (str "Change: " (.toExponential (abs (- b a)) 2) " < 10⁻¹²")]
                  [:p "The stopping test is another function we supply."]
                  [:p.caption "Small change is a stopping rule, not a general error bound."]]]))
    :notes "Use the same square-root-of-two trajectory. In exact arithmetic a positive fixed point satisfies x=(x+2/x)/2, hence x²=2. Floating-point iteration uses a tolerance between successive states. This alone is not a general error certificate. The second reveal shows the actual helper for reference: iterate builds the lazy sequence, take caps work, take-while checks pairs, count realizes only the prefix up to the first passing pair, vec returns the retained trace. The cap is not a convergence result. A regression with cap 10000 stops after 12 updates when the predicate passes. No need to read every implementation line aloud; the important interface is step, stopping predicate and initial state."}

   {:section "Optimise" :title "Plan tomorrow’s turbine settings"
    :live (fn [_ t] {"farm-context" (scene nil t {:w 700 :h 510})})
    :render (fn [_]
              [:div.two.even
               [:div
                [:p.aside "Wind sets the available electricity. We choose how much to use."]
                (kv-table [["noon forecast, per turbine" (str (energy 12) " kWh")]
                           ["setting 1: use all of it" (str (energy 12) " kWh")]
                           ["setting 0.5: use half" (str (* 0.5 (energy 12)) " kWh")]])
                [:p.aside "6 turbines × 12 hourly decisions"]
                [:p.aside "72 settings to improve."]]
               [:div#farm-context (scene nil (now-s) {:w 700 :h 510})]])
    :notes "Distinguish the forecast from the decision. energy is an illustrative forecast of electricity available if a turbine operates fully during an hour; in practice it would come from wind and a power curve. This demo supplies that forecast directly, with the same values for all six turbines. We do not optimize wind speed or forecast generation. We choose a retained fraction of available generation for every turbine-hour. At noon the forecast is 84 kWh; retaining half means 42 kWh, not an assertion that the wind is at half strength. The 0.5 vector is only an arbitrary interior starting guess for the numerical search, not a physical initial condition or a recommended policy. Other starting plans are possible. The optimization revises all 72 settings for the same future day."}

   {:section "Optimise" :title "What makes one plan better?" :steps 2
    :render (fn [frame]
              (let [off (zipmap slots (repeat 0)) on (zipmap slots (repeat 1))
                    half (as-plan (:x first-guess))]
                [:div.two.even
                 [:div
                  (if (= frame 1)
                    [:div (code 'bird-risk {:size :small})
                     [:p.aside "Exposure depends on the turbine’s position and the hour."]
                     [:p.caption "exposure(t): proximity to the flight path."]
                     [:p.caption "birds(h): the shared hourly traffic forecast."]]
                    [:div (code 'electricity-lost {:size :small})
                     [:p.aside "Lost electricity = forecast × fraction we curtail."]])
                  [:p.caption "Bird exposure is an illustrative index, not a death count or a probability."]]
                 [:div
                  (if (= frame 1)
                    [:div (exposure-grid)
                     (kv-table [["T2 at noon, running fully" (fmt (* (exposure (turbines 1)) (birds 12)) 3)]
                                ["T3 at noon, running fully" (fmt (* (exposure (turbines 2)) (birds 12)) 3)]])
                     [:p.caption "Different exposure by turbine; the same daily traffic pattern, scaled by location."]]
                    [:div
                     [:table.levels
                      [:tr [:th "plan"] [:th "generated kWh"] [:th "bird risk"]]
                      (for [[label plan] [["all stopped" off] ["half retained" half] ["all retained" on]]]
                        [:tr [:td label] [:td (fmt (- full-energy (lost plan)) 0)] [:td (fmt (risk plan) 1)]])]
                     [:p.aside "More generation also means more bird exposure."]])]]))
    :notes "First compare whole plans: retaining more electricity increases bird exposure too. Next reveal the full turbine-by-hour exposure grid. It is calculated from the actual factors used in bird-risk: a turbine's proximity weight times that hour's bird-traffic forecast. T3 is near the flyway; T2 is farther away, so curtailing T3 protects more exposure for the same sacrificed electricity at noon. Every row has the same daily shape scaled by location; these are not independently estimated probability distributions or bird fatality rates. Next we build an electricity-only update before combining both concerns in the cost function. electricity-lost and bird-risk both score the whole 72-setting plan."}

   {:section "Optimise" :title "Optimization is iterate" :steps 3
    :render (fn [frame]
              (let [updated ((solver-step energy-cost) first-guess)]
                [:div.two.even
                 [:div
                  (case frame
                    0 (code 'gradient {:size :small})
                    1 (code 'descent-step {:size :small})
                    [:div (code 'clamped {:size :small})
                     (code 'solver-step {:size :small})])
                  (code "(iterate (solver-step energy-cost) first-guess)" {:size :small :marks #{"iterate"}})]
                 [:div
                  (case frame
                    0 [:div (coordinate-plot energy-cost)]
                    1 [:div
                       [:p.aside "One call updates all 72 settings from the same old plan."]
                       [:table.levels
                        [:tr [:th "at noon"] [:th "old setting"] [:th "new setting"]]
                        (for [t turbines :let [i (.indexOf slots [t 12])]]
                          [:tr [:td (label-of t)] [:td "0.5"]
                           [:td (fmt (get-in updated [:x i]) 4)]])]
                       [:p.caption "0.5 − 0.0001 × (−84) = 0.5084"]
                       [:p.caption "The other 11 hours are updated too."]
                       [:p.caption "This first run scores electricity only. Equal forecasts give equal slopes."]]
                    [:div
                     [:p.aside "clamped wraps a step and limits its output."]
                     (kv-table [["proposed setting" "after clamped"] ["−0.1" "0"] ["0.5084" "0.5084"] ["1.2" "1"]])
                     [:p.caption "A turbine cannot operate below 0% or above 100%."]
                     [:p.aside "iterate feeds the new state back into this step."]])]]))
    :notes "This is the optimization algorithm: repeated updates to a vector of settings. gradient receives a numerical cost function and returns a numerical gradient function. It samples the cost just below and above each coordinate, holding the others fixed. No symbolic algebra or Hessian is used. descent-step receives that gradient and a learning rate, and returns a state-update function. One component moves from 0.5 to 0.5084 in the electricity-only example. All coordinates are updated from the same old vector. clamped is a function wrapper that limits each resulting setting to the physical range zero to one. It clips values; it is not a bird-safety constraint. The displayed iterate call repeatedly applies the resulting step. Next inspect the rate argument, then change the cost we supply."}

   {:section "Optimise" :title "How far should one update move?" :steps 2
    :render (fn [frame]
              (let [rate (if (zero? frame) 0.0001 0.0002)
                    step (clamped (descent-step (gradient energy-cost) rate) 0 1)
                    next (get-in (step first-guess) [:x 4])]
                [:div.two.even
                 [:div (code 'descent-step {:size :small})
                  (code (str "(def step\n  (clamped\n    (descent-step (gradient energy-cost) " rate ")\n    0 1))\n\n(iterate step first-guess)") {:size :small})]
                 [:div [:p.aside "Turbine 1 at noon: the same slope, −84."]
                  (kv-table [["rate" rate] ["old setting" "0.5"] ["new setting" (fmt next 4)]])]]))
    :notes "Immediately after constructing gradient descent, explain the rate parameter using the same coordinate and slope. To change the pace, pass a different rate to the function that constructs the update. The same coordinate moves twice as far; the iteration and stopping test do not change. This is the electricity-only cost, chosen to keep the arithmetic familiar. Larger rates can overshoot; this is an interface demonstration, not a recommendation to double every learning rate."}

   {:section "Optimise" :title "Which plan should win?" :steps 3
    :render (fn [frame]
              (let [[a b] penalty-candidates]
                [:div.two.even
                 [:div
                  (case frame
                    0 [:div
                       [:p.aside "A: retain 50% everywhere."]
                       [:p.aside "B: the same plan, but stop T3 at noon."]
                       (code 'energy-cost {:size :small})
                       [:p.aside "If we only score electricity lost, A wins."]]
                    1 [:div
                       [:p.aside "Add a cost when total bird risk exceeds 12."]
                       [:div.equation-stack
                        [:div.equation-part "Example: risk 14 is 2 above the target."]
                        [:div.equation-part "Extra cost = 100 × 2² = 400."]]
                       [:p.aside "At or below 12, the extra cost is zero."]
                       [:p.caption "12 is our chosen risk target. 100 controls the tradeoff with electricity."]
                       [:p.caption "This encourages lower risk; it does not enforce a strict limit."]]
                    [:div
                     (code 'with-risk-penalty {:size :small :marks #{"cost" "risk" "fn"}})
                     (code 'risk-cost {:size :small})
                     (code "(solver-step energy-cost)
(solver-step risk-cost)" {:size :small})
                     [:p.caption "Pass the new scoring function to the same numerical update."]])]
                 [:div
                  [:p.caption "A: retain half everywhere. B: also stop T3 at noon."]
                  [:table.levels
                   [:tr [:th "whole-day plan"] [:th "A"] [:th "B"]]
                   [:tr [:td "electricity lost (kWh)"] [:td (fmt (:lost a))] [:td (fmt (:lost b))]]
                   [:tr [:td "bird-risk index"] [:td (fmt (:risk a) 2)] [:td (fmt (:risk b) 2)]]
                   (when (pos? frame)
                     [:tr [:td "extra cost"] [:td (fmt (:penalty a) 0)] [:td (fmt (:penalty b) 0)]])
                   (when (pos? frame)
                     [:tr [:td "total score"] [:td (fmt (:score a) 0)] [:td.ok (fmt (:score b) 0)]])]
                  [:p.aside (if (zero? frame)
                              "A gives up less electricity."
                              "B wins with the penalty: less risk outweighs 42 kWh lost.")]
                  (when (= frame 2)
                    [:p.caption "Both functions take a plan and return a number. The optimizer seeks a lower number."])]]))
    :notes "The algorithm cannot choose between competing metrics until we specify a preference. These are two actual complete plans, computed by the displayed functions. A retains half of available generation everywhere. B changes just T3 at noon from half to stopped, sacrificing 42 more kWh but reducing exposure near the flyway. With electricity-lost alone, A is better. We choose a soft risk target of 12 and penalty weight 100. Neither is a physical constant or a scientific mortality threshold. Weight 100 converts squared excess exposure into score units comparable with the electricity-loss term; a larger weight sacrifices more generation to avoid excess risk. Below the target the penalty is zero; risk 14 incurs 400. Under this chosen tradeoff B has a lower total score. A and B both still exceed the target; this comparison is one useful direction for improvement, not the final solution. On the third reveal show the implementation: with-risk-penalty closes over two functions and the chosen parameters and returns a new scoring function. solver-step receives whichever scoring function we supply. The numerical gradient samples it, so its preferred direction changes without rewriting gradient descent or iterate. This is scalarization of the two goals, not a hard safety guarantee."}

   {:section "Optimise" :title "Same updates, different objectives" :runs [:crowd :plain]
    :steps #(max (steps-for :crowd) (steps-for :plain))
    :render (fn [step]
              [:div.two.even
               (for [[k label call] [[:crowd "Electricity only" "(iterate energy-step first-guess)"]
                                     [:plain "Electricity + bird penalty" "(iterate risk-step first-guess)"]]
                     :let [plan (plan-at k step) i (min step (dec (count (run-of k))))]]
                 [:div [:p.aside label]
                  (code call {:size :small :marks #{"iterate"}})
                  (schedule plan {:w 700})
                  (plan-metrics plan)
                  [:p.caption (str "Update " i (when (and (done? k) (= i (dec (count (run-of k)))))
                                                    (if (:settled (run-summary k))
                                                      " · settings barely move"
                                                      " · work cap reached")))]])])
    :notes "Both panels begin with exactly the same half-power plan and use rate 0.0001, the same clamp and the same stopping test. Press Space. Left: electricity alone pushes everything to full power. Right: the extra bird penalty reduces operation near the flight path and during heavy bird traffic. Read both electricity and risk at the end. A row is a turbine, a column is an hour; darker means more operation. An optimization iteration is a revised plan for the same day, not an hour passing. Stop when maximum setting change is below 0.001, with a work cap. Numerical settling is not a proof of global optimality. The right-hand final risk may slightly exceed twelve because this is a soft penalty."}

   {:section "Optimise" :title "Can momentum reduce the number of updates?" :steps 2 :runs [:plain :momentum]
    :render (fn [frame]
              [:div.two.even
               [:div
                (if (zero? frame)
                  (code 'with-momentum {:size :small :marks #{"step" "fn"}})
                  [:div (code 'risk-descent {:size :small})
                   (code 'risk-step {:size :small})
                   (code 'momentum-step {:size :small})])
                (code "(iterate momentum-step first-guess)" {:size :small})
                [:p.caption "Base move + 90% of the previous velocity. Then limit settings to 0…1."]]
               [:div (convergence-chart)
                [:table.levels [:tr [:th "update"] [:th "steps"] [:th "final cost"]]
                 (for [[k label] [[:plain "plain"] [:momentum "momentum"]]
                       :let [run (run-of k)]]
                   [:tr [:td label] [:td (dec (count run))]
                    [:td (fmt (risk-cost (:x (peek run))) 1)]])]]])
    :notes "Continue the risk-aware run just shown: can another step reach a settled plan in fewer updates? with-momentum accepts a state-update function and returns another. It calls the base update once, measures that proposal's displacement, adds 90 percent of stored velocity, and returns new position and velocity. The outer clamped wrapper clips position to the allowed range; it does not reset velocity at a bound. The chart and table use actual runs. Fewer updates reach the same movement tolerance here; that does not prove a better optimum, general convergence, or a wall-clock speedup. The second reveal shows exactly what wraps what. The iteration remains unchanged."}

   {:section "Optimise" :title "Play the resulting day" :hide-title? true :steps 2 :runs [:plain]
    :live (fn [frame t]
            (let [plan (if (zero? frame) (zipmap slots (repeat 1)) (:plan (run-summary :plain)))]
              {"played-3d" (scene plan t {:w 700 :h 500 :encounters? true})
               "played-schedule" (schedule plan {:w 700 :now (:hour (time-of-day t))})}))
    :render (fn [frame]
              (let [plan (if (zero? frame) (zipmap slots (repeat 1)) (:plan (run-summary :plain)))
                    t (now-s)]
                [:div.two.even
                 [:div [:p.aside (if (zero? frame) "Before: every turbine runs." "After: the optimized schedule.")]
                  [:div#played-schedule (schedule plan {:w 700 :now (:hour (time-of-day t))})]
                  (plan-metrics plan)]
                 [:div#played-3d (scene plan t {:w 700 :h 500 :encounters? true})]]))
    :notes "Now time really is passing through a day. First reveal is full power; second is the risk-aware plan. The prominent bird follows the same scripted crossing of the most exposed turbine each hour. At the rotor, a moving turbine produces a red impact flash and a falling bird; a fully stopped turbine lets this illustrated bird continue. The setting is read from the displayed schedule at the current hour. This is an explanatory animation, not geometric blade collision physics, a fatality estimate, or proof that stopped turbines cannot harm birds. Partial curtailment is not treated as guaranteed safe. The optimizer still scores the existing exposure index; the animation does not change its objective or results. The red schedule rectangle tracks the same clock as the encounter."}

   {:section "Decide" :title "Buy hydrogen before the customers arrive" :steps 4
    :render (fn [frame]
              [:div.two.even
               [:div (hydrogen-scene 1 1 3 (min frame 2))
                [:p.aside (nth ["Start with 1 unit. Choose an order."
                               "The tanker delivers before demand is known."
                               "A convoy arrives: two served, one turned away."
                               "This outcome earns 18: sell 2 × 10, buy 1 × 2."] frame)]
                [:p.caption "The tank holds 4 units. Each customer truck needs 1."]]
               [:div
                (if (= frame 3)
                  [:div (code 'weekly-profit {:size :small})
                   (code 'next-state {:size :small})
                   (code 'remaining-stock {:size :small})]
                  [:div
                   [:table.levels
                    [:tr [:th "supply week"] [:th "buy price"] [:th "max purchase"]]
                    [:tr [:td "regular"] [:td "2 / unit"] [:td "3 units"]]
                    [:tr [:td "tight"] [:td "6 / unit"] [:td "1 unit"]]]
                   [:p.caption "Regular → tight → regular … Known delivery cycle."]
                   (when (pos? frame)
                     [:div
                   [:p.aside "Customers come in bursts."]
                   [:table.levels
                    [:tr [:th "trucks"] [:th "probability"]]
                    (for [[d p] demand-probabilities]
                      [:tr [:td d] [:td (str (fmt (* 100 p)) "%")]])]
                   [:p.caption "No arrivals, a single truck, or a convoy."]])])
                (when (>= frame 2)
                  [:p.caption "Sell for 10; holding cost 1 per leftover unit/week. Future discount 0.9."])]])
    :notes "The station buys hydrogen from a supplier and sells it to customer trucks. The tank always holds four units, while supplier capacity changes: regular weeks offer at most three units at price two, tight weeks at most one at price six. Weeks alternate predictably; this is a two-period model, not a general market price forecast. Customer demand is a discrete mixture: no arrivals with probability .4, one truck with .15, a three-truck convoy with .45. There is no demand of two in this teaching distribution. The two peaks represent quiet weeks and convoy arrivals, and the same distribution applies in both supply weeks. Mean demand is 1.5 but does not capture the convoy risk. Each week, observe phase and stock, choose a feasible order, receive it, serve uncertain customers and carry leftovers. No evaporation, backlog or fixed delivery fee is included. Walk through one regular-week outcome: stock one, order one, demand three, profit 20 minus 2 = 18. Last reveal shows the actual reward and state transition: leftover inventory plus the next supply phase. The optimization goal is expected discounted profit over repeated weeks, not simply serving every truck."}

   {:section "Decide" :title "Buy for this week—or the next?"
    :render (fn [_]
              [:div.two.even
               [:div
                (code 'supply-calendar {:size :small})
                (code 'orders {:size :small})
                [:p.aside "Regular week. One unit in stock."]
                (code "[:regular 1]" {:size :small})
                [:p.caption "State includes the delivery window as well as inventory."]]
               [:div
                [:p.aside "Order 2 to cover the largest possible convoy? Or 3 to carry more forward?"]
                [:table.levels
                 [:tr [:th "demand"] [:th "order 2: leftover"] [:th "order 3: leftover"]]
                 (for [[d _] demand-probabilities]
                   [:tr [:td d]
                    [:td (second (next-state station-start 2 d))]
                    [:td (second (next-state station-start 3 d))]])]
                [:p.caption "Every row leads to a tight week: price 6, at most 1 unit available."]]])
    :notes "This is why the new problem cannot be reduced to ordering the maximum customer demand. We could buy two and reach three units, enough for any current-week arrival. Or buy three and reach four, even though only three trucks can arrive this week. Carrying an extra unit is costly if demand is quiet but useful before the expensive, capacity-limited supply week. Show all demand branches and their resulting stocks. The next supply phase is part of the state, so identical stock can justify different orders. In Bellman, future values are indexed by [week stock]. Adding the phase makes the repeating time-varying problem a fixed state-transition model; the same update can be iterated."}

   {:section "Decide" :title "Future values change the order" :steps 5
    :render (fn [frame]
              (let [n (nth [0 1 3 3 3] frame)
                    V (nth restock-run n)
                    scores (mapv (fn [order] [order (expected-return V station-start order)]) (orders station-start))
                    best (apply max (map second scores))]
                [:div.two.even
                 [:div
                  (case frame
                    3 [:div (code 'bellman {:size :small :marks #{"fn" "map" "max"}})
                       (code 'restock {:size :small})]
                    4 [:div (code 'best-order {:size :small :marks #{"max-key"}})
                       (code "((best-order restock V) [:regular 1])" {:size :small})]
                    [:div (code 'expected-return {:size :small :marks #{"V" "reduce"}})
                     (code 'demand-probabilities {:size :small})])
                  (code "(iterate (bellman restock) zero-values)" {:size :small :marks #{"iterate"}})
                  [:p.caption "V after n updates covers n weeks; scoring an order looks one week further."]]
                 [:div
                  [:p.aside (str "Regular week · stock 1 · values after " n " updates")]
                  [:table.levels [:tr [:th "order"] [:th "expected return"]]
                   (for [[order value] scores]
                     [:tr [:td order] [:td {:class (when (= value best) "ok")} (fmt value 2)]])]
                  [:p.caption "Average over demand: this week’s profit + 0.9 × V(next week, leftover stock)."]
                  [:p.aside (cond
                              (= frame 4) "max-key keeps the order: buy 3."
                              (= frame 3) "max keeps the score. Repeat for all ten states."
                              (zero? frame) "Ignoring the future: buy 2."
                              (= frame 2) "With better future estimates: buy 3."
                              :else "One update: buying 2 still looks better.")]
                  (when (= frame 3)
                    (kv-table [["input V: profit estimate at this state" (fmt (V station-start) 2)]
                               ["output B(V): new profit estimate" (fmt best 2)]]))
                  (when (= frame 4)
                    [:p.caption "best-order returns state → order. The value table is unchanged."])]]))
    :notes "Read the actual scores for the same state and same four feasible actions. With V zero, only this week counts and order two wins. After one backup the recommendation stays two. After three backups the value of inventory in the tight week changes the preferred action to three. We skip backup two here; the next slide replays every update. This is a planning update, not a sequence of real purchases. The fourth reveal shows the generic Bellman HOF and the ten-state model: two supply phases times five inventory levels. expected-return takes future values as an argument, and Bellman constructs the next values by maximizing those scores. The fifth reveal shows best-order: max-key retains the action achieving the best score, while Bellman retains only the score. Reversing the ascending actions breaks equal-score ties toward smaller orders. No Gaussian approximation or average-demand substitution is used; the reduce weights each discrete outcome explicitly."}

   {:section "Decide" :title "Hydrogen: iterate the value table" :steps (count restock-run)
    :render (fn [step]
              (let [V (nth restock-run step) policy (best-order restock V)]
                [:div.two.even
                 [:div (code 'bellman {:size :small})
                  (code "(fixed-point (bellman restock)\n  #(< (largest-change %1 %2) 0.001)\n  zero-values)" {:size :small})
                  [:p.aside "Read the bars for stock 1:"]
                  [:table.levels
                   [:tr [:th "week"] [:th "future profit"] [:th "order"]]
                   (for [week [:regular :tight]]
                     [:tr [:td (name week)] [:td (fmt (V [week 1]) 1)] [:td (policy [week 1])]])]
                  (code "(best-order restock V) ; state → order" {:size :small})
                  [:p.caption "The order column comes from best-order, not from V."]]
                 [:div
                  [:p.aside "Height = expected future profit"]
                  [:p.caption "Money units, with future weeks discounted by 0.9."]
                  (station-value-chart V)
                  [:p.caption (str "Planning update " step " · each pair is one possible starting inventory")]
                  (when (pos? step)
                    [:p.aside (str "Largest value change: " (fmt (largest-change (nth restock-run (dec step)) V) 6))])]]))
    :notes "Bellman takes the model and returns a value-table update. Its input and output are both maps from [week stock] to money values. best-order separately extracts actions from V and the model. Each bar is V([week stock]): an estimate of total future discounted profit in money units. It is not hydrogen volume or weekly sales. Five inventory levels each have two bars: regular delivery week in blue and tight supply week in amber. Read stock one using the table: the same inventory has different value and recommended action depending on the supply window. Every update revises all ten values together. After n backups V is the optimal n-week value; the greedy recommended order looks one step beyond that table. Space advances the planning calculation, not physical time. Stop when the largest change across the ten entries is below .001. The Rainbow section returns to why this operator reduces discrepancies between value tables."}

   {:section "Decide" :title "The best order depends on the supply window" :steps 2
    :render (fn [frame]
              [:div.two.even
               [:div
                [:p.aside "Bellman’s recommended order"]
                [:table.levels
                 [:tr [:th "stock"] [:th "regular week"] [:th "tight week"]]
                 (for [stock (range (inc capacity))]
                   [:tr [:td stock] [:td (station-policy [:regular stock])]
                    [:td (station-policy [:tight stock])]])]
                [:p.caption "Regular: price 2, buy ≤ 3. Tight: price 6, buy ≤ 1."]
                [:p.aside "At stock 1: buy 3 in a regular week, buy 0 in a tight week."]]
               [:div
                [:table.levels [:tr [:th "rule"] [:th "20-week discounted profit"]]
                 (for [{:keys [label twenty-weeks]} policy-comparison]
                   [:tr [:td label] [:td {:class (when (= label "Bellman") "ok")} (fmt twenty-weeks 2)]])]
                [:p.caption "Start in a regular week with 1 unit. Every rule respects tank and supplier limits."]
                (when (pos? frame)
                  [:div
                   [:p.aside (str "Empty tank, tight week: buy 1 despite "
                                  (fmt (expected-return zero-values [:tight 0] 1) 2)
                                  " expected profit this week.")]
                   [:p.caption "Possible leftover stock helps when next week’s cheap delivery is capped."]])]])
    :notes "Read the state-dependent policy before the comparison. In regular weeks fill toward four subject to the supplier's limit of three; with empty storage only three can be bought. In tight weeks buy one only when empty, otherwise wait for cheap supply. This is not simply fill to the maximum demand of three, or always fill to capacity. The five rules are evaluated for twenty weeks with the same initial state and exact weighted demand branching. This-week-only maximizes immediate expected profit; the three target rules attempt the named stock level but obey both capacity constraints. Bellman is the stationary policy from converged values, evaluated over that same twenty-week horizon. It is not claimed to be the optimal finite-horizon policy for the final week of that comparison. The table shows the actual gain, including that fill-to-three is already fairly good. We chose a compact illustrative model, not a realistic hydrogen market forecast. The HOF structure is unchanged despite time-varying prices and availability."}


   ;; ---------------------------------------------------------------- Validate
   {:section "Validate" :title "Stop when a bird is detected" :steps 2
    :render (fn [step]
              (reset! shield-start (now-s))
              [:div.two.even
               [:div
                [:p.aside "A shield wraps the decision function."]
                (code 'stop-for-birds {:size :small :marks #{"decide" "fn"}})
                (code "(def safe-decide\n  (stop-for-birds decide))" {:size :small})
                [:p.caption ":det is the recorded detection level. Zero means none."]
                [:p "On each detection, call the decision function again."]
                [:p.caption "Check every reachable state by iterating until the set stops growing."]]
               [:div#shield-animation (shield-animation (pos? step) 0)]])
    :live (fn [step t] {"shield-animation" (shield-animation (pos? step) (- t @shield-start))})
    :notes "Two views of the same detection event. The illustrated bird hits a moving blade and falls in the first view; after STOP it passes in the second. This scripted encounter is not a physical collision or braking model. The first uses the reacting table controller in an unseen context: its missing row defaults to RUN. The second calls stop-for-birds around that same decision. This simpler stage shield stops on any positive detection in every context; unlike the older study example it does not consult a context table. react-to-detections invokes it on a stronger detection; tick-controller also uses it. The animation calls these actual controllers, sends the level-2 event at two seconds and freezes the illustrated rotor on the STOP command. Real turbines take time to brake. Counts come from finite reachability: the existing events, initial states and three draw bands, iterated to set equality. The on-stage rule is confident detection implies STOP in the same modeled minute; the shield is stronger and stops on weak detection too. One animated trace demonstrates the reaction; the exhaustive finite search supplies the validation result. Neither checks sensor accuracy, turbine physics, or the checker implementation. Do not claim the optimized day schedule was verified by this separate controller model."}

   {:section "HOFs" :title "iterate underneath all three"
    :render (fn [_]
              [:div
               [:table.levels
                [:tr [:th "example"] [:th "state"] [:th "supplied step"] [:th "stop test"]]
                [:tr [:td "optimise"] [:td "turbine settings"] [:td "gradient step"] [:td "small movement"]]
                [:tr [:td "decide"] [:td "value function"] [:td "Bellman operator"] [:td "small value change"]]
                [:tr [:td "validate"] [:td "set of reached states"] [:td "expand with successors"] [:td "set equality"]]]
               [:div.promise "(iterate step start)"]
               [:p.caption "fixed-point takes this sequence until the supplied stopping test passes."]])
    :notes "Summarize the three uses of iterate: a schedule, a value table and the reachable-state set behind the shield check. The reusable structure accepted different functions. Our reachability result covers this finite model under its encoded events. It does not prove the checker or a physical turbine. The optimizer has already demonstrated rate and momentum changes. Next introduce Rainbow and proofs as data."}

   {:section "Rainbow" :title "Rainbow"
    :render (fn [_]
              [:div.two.even
               [:div [:p.aside "A theorem prover & code generator."]
                [:p "Focused on Clojure and operations research."]
                [:p.aside "flexiana.com/rainbow"]]
               [:div [:p.aside "Designed for AI-assisted proving."]
                [:p "Aim: more verified progress per token."]
                [:p "Reuse checked lemmas instead of repeating arguments."]
                [:p.caption "Its purpose is not to match or beat Lean."]]])
    :notes "Introduce the tool without teaching its proof language. A statement alone is not enough: Rainbow checks a supplied proof against it. Code generation currently covers a modeled subset of Clojure. It does not prove arbitrary Clojure programs correct, replace testing, or validate a physical model's assumptions. Lean is a broader prover; no exclusive capability or superiority is claimed. The motivation is an OR workflow accessible from Clojure. The AI emphasis is a design goal, grounded in Rainbow_AI_Worker_Implementation_Specification.md section 3.5: useful verified progress per AI round trip and per million tokens. Reuse and local checking are the mechanism; no tokenizer-specific compression, lower cost or benchmark superiority is asserted. The reported 5:1 proof-to-generated-code ratio awaits a unit and example, so it is not displayed."}

   {:section "Rainbow" :title "From proofs to Clojure"
    :render (fn [_]
              (let [receipt (n/checked-or-examples)]
                [:div.two.even
                 [:div
                  [:p.aside "Keep a turbine setting between 0 and 1."]
                  [:table.levels [:tr [:th "input"] [:th "clamped value"]]
                   (for [{:keys [input output]} (:examples receipt)]
                     [:tr [:td input] [:td output]])]
                  [:p "The modeled clamp returns the nearest bound when outside; it keeps the value when inside."]
                  [:p.caption "Proved for the modeled program, beyond these three examples."]]
                 [:div
                  [:p.aside "Why generate Clojure?"]
                  [:p "Code and proofs are data."]
                  [:p "Generate forms. Compose them with higher-order functions."]
                  [:p "Inspect and run the result in the REPL."]
                  [:p.caption "Currently: a modeled subset. The runtime remains trusted."]]]))
    :notes "One familiar example instead of proof syntax. Explain why Clojure is a useful generation target: structured forms can be constructed and traversed as data; higher-order functions provide reusable pieces; the REPL lets us inspect and run generated output. A compact representation alone does not prove token efficiency or correctness. The values are the recorded executions of the generated clamp program, read from or-checks.edn. Bounds are ordered, here 0 and 1. clj/clamp-program-denotes-clamp and clj/compile-correct-scoped were checked on 2026-10-24 with empty transitive axiom profiles. The first relates the modeled program to its specification; the second preserves meaning for scoped programs between modeled representations. This is not a theorem about all real numbers in a floating-point JVM implementation: the printer, exact-arithmetic primitives and runtime remain trusted. Three executions illustrate the result; they are not the proof. Rainbow proof terms are EDN; Clojure functions can construct and transform them, but each changed term must be checked again. This property enables visual tools; do not claim a released general editor or that other provers lack structured proof data. Full terms, generated source, selection/LP results and the earlier syntax walkthrough remain in the study material."}

   {:section "Rainbow" :title "Where we use Rainbow now?"
    :render (fn [_]
              [:div.two.even
               [:div
                [:p.aside "An e-SAF plant model in the Bay of Algeciras."]
                [:div.certificate-results {:style {:grid-template-columns "1fr"}}
                 [:div [:strong "€7.9m/year"]
                  [:span "estimated saving from better plant design"]]]
                [:p.caption "Compared with a design based on an average year."]
                [:p "50,000 tonnes of synthetic aviation fuel per year."]]
               [:div
                [:p.aside "Hourly OMIE electricity prices. Local wind and solar history."]
                [:p "We model electrolysis, storage, CO₂ supply and fuel production, including operating limits and costs."]
                [:p "We compare how to size the plant, buy electricity and run production."]
                [:p "Rainbow checks the proof of the modeled fuel-blending optimum."]
                [:p.caption "Annual savings are model estimates; the proof covers a specific decision."]]])
    :notes "Business case from challenge-1-esaf, separate from the optional e-methanol benchmark in the study guide. The site is San Roque / Los Barrios, Bay of Algeciras, at 36.18 N, -5.40 W. This is a modeled 50,000 t/year plant, not a named operating customer's installation. OMIE Spanish-zone hourly prices cover 2023–2025; local ERA5/Open-Meteo weather covers 2015–2025 with PVGIS calibration. Equipment costs and efficiencies are explicit assumptions, not vendor quotes. The figure is the paired out-of-sample mean cost difference between average-year design B1 and the stochastic design: 157.2451162402811 EUR/t times the assumed 50,000 t/year target = 7.862255812014055 M EUR/year. It includes the study's modeled costs and shortfall assumptions; it is distinct from the earlier 4.4 M energy/backup-only operating-policy comparison. Nine weather/price combinations reuse the price years used in tuning; do not call them nine independent unseen years. The annual saving is an evaluated model result, not a formal theorem or realized customer saving. The user-suggested 74 M EUR/year figure has not been traced to a result/baseline and is not asserted. The recorded Rainbow result certifies the specified 60-unit fuel-blending model's optimum: at most 28 units of e-SPK under the supplied properties/constraints. The 24 operating LP certificates use a separate exact checker; do not attribute their implementation verification or the whole annual saving to Rainbow. HiGHS solves the LPs; our modeling/search and verification work use it. Sources and pending-figure status are in speaker/plant-case-evidence.json and the study guide."}

   ;; ---------------------------------------------------------------- The book (2 min)
   {:section "The book" :title "Applied Higher-Order Functions"
    :render (fn [_]
              [:div.two.even
               [:div.book-photo
                [:img {:src (n/embedded-book-photo)
                       :alt "Jiri Knesl’s Applied Higher Order Functions hardback on a wooden table"
                       :width 390 :height 520}]]
               [:div
                [:p.aside "I wrote this book about using higher-order functions in operations research."]
                [:p "The ideas apply more broadly to designing algorithms that optimize or help make decisions."]
                (code "map · reduce · filter · comp\npartial · iterate …" {:size :small})
                [:p.goal "flexiana.com/book"]]])
    :notes "The book explains how to use higher-order functions in operations research. The ideas also apply more broadly to designing algorithms that optimize or support decision-making. Every listing runs with the companion library orhof. Book text: CC BY-SA 4.0; library: MIT. Photograph from flexiana-web/site/src/assets/book/book-wood-angle.web.webp, embedded in the release for offline use."}

   ;; ---------------------------------------------------------------- Closing
   {:section "Thank you" :title "Thank you"
    :render (fn [_]
              [:div.title-slide
               [:h1.big "Thank you"]
               [:div.byline [:a {:href "mailto:jiri@flexiana.com"} "jiri@flexiana.com"]]
               [:p.aside [:a {:href "https://flexiana.com/rainbow"} "flexiana.com/rainbow"]]])
    :notes "Questions. Likely ones: Why not Haskell? (Types would help. I chose Clojure for code as data and the REPL.) Does momentum always help? (No.) Is the browser program proved? (No; the Rainbow result is about exact mathematical terms.)"}])

;; =============================================================================
;; 8. App
;; =============================================================================

(src (defonce state
       (atom {:slide 0 :step 0 :playing false :help false})))

(defn steps-of [slide]
  (let [s (:steps slide 1)]
    (max 1 (if (fn? s) (s) s))))

(defn clamp [x lo hi] (max lo (min hi x)))

(defn slide-view [{:keys [slide step help]}]
  (let [{:keys [section title render hide-title?]} (nth slides slide)
        steps (steps-of (nth slides slide))]
    [:div.slide {:class (when hide-title? "no-title")}
     [:div.top
      [:div.sections (for [s section-order] [:span {:class (when (= s section) "on")} s])]
      [:div.count (str (inc slide) " / " (count slides))]]
     (when-not (or hide-title? (#{"Hello" "Thank you"} section)) [:h2.title title])
     (when (and (#{"Hello" "Thank you"} section) (pos? slide) (< slide (dec (count slides)))) [:h2.title title])
     [:div.body (try (render step)
                     (catch :default e [:pre.error (str "This slide failed to render:\n" (.-message e))]))]
     (when (> steps 1)
       [:div.steps
        [:div.steps-bar {:style {:width (str (* 100 (/ (inc step) steps)) "%")}}]
        [:div.steps-text (str (inc step) " / " steps)]])
     (when help
       [:div.help
        [:table (for [[k v] [["↑ ↓" "slide"] ["← →" "step"] ["Shift ← →" "10 steps"] ["Space" "play / pause"]
                             ["Home End" "first / last step"] ["N" "notes window"] ["F" "fullscreen"]
                             ["D" "dark / light"] ["?" "this help"]]]
                  [:tr [:th k] [:td v]])]])]))

(defn notes-view [{:keys [slide step]}]
  (let [{:keys [title notes]} (nth slides slide)
        nxt (get slides (inc slide))
        speech (speaker/current slide (inc step) (steps-of (nth slides slide)))]
    [:div.notes-view
     [:div.n-head (str (inc slide) " / " (count slides) " · step " (inc step) " / " (steps-of (nth slides slide)))]
     [:h1 title]
     [:p.n-cue (:cue speech)]
     [:div.n-text
      (for [paragraph (str/split (or (:speech speech) notes) #"\n\n")]
        [:p paragraph])]
     [:details.n-reference
      [:summary "Technical reference and optional detail"]
      [:p notes]]
     [:div.n-next (if nxt (str "next: " (:title nxt)) "last slide")]
     [:div.n-clock {:id "clock"} "00:00"]
     [:div.n-keys "keys here drive the stage window · T start/pause clock"]]))

(def browser? (exists? js/document))
(def notes-mode? (and browser? (str/includes? (.-search js/location) "notes")))
(defonce channel (when browser? (js/BroadcastChannel. "lambda-world-2026")))

(defn- hash-str [{:keys [slide step]}] (str "#s" (inc slide) (when (pos? step) (str "a" (inc step)))))

(defn- parse-hash []
  (when-let [[_ s a] (re-find #"^#s(\d+)(?:a(\d+))?" (.-hash js/location))]
    (let [slide (clamp (dec (js/parseInt s)) 0 (dec (count slides)))]
      {:slide slide :step (clamp (dec (js/parseInt (or a "1"))) 0 (dec (steps-of (nth slides slide))))})))

(defn- sync-hash! [st]
  (let [h (hash-str st)]
    (when (not= h (.-hash js/location)) (.replaceState js/history nil "" h))))

(defn- broadcast! [{:keys [slide step]}]
  (.postMessage channel (clj->js {:type "state" :slide slide :step step})))

(src (defn render! []
       (let [st @state
             view (if notes-mode? notes-view slide-view)]
         (set! (.-innerHTML (js/document.getElementById "stage"))
               (html (view st)))
         (when-not notes-mode?
           (sync-hash! st)
           (broadcast! st)))))

(defn go! [slide step]
  (let [slide (clamp slide 0 (dec (count slides)))
        step (clamp step 0 (dec (steps-of (nth slides slide))))]
    (swap! state assoc :slide slide :step step)
    (render!)))

(defonce timer (atom nil))

(defn stop-play! []
  (when-let [t @timer] (js/clearInterval t) (reset! timer nil))
  (swap! state assoc :playing false))

(defn toggle-play! []
  (if @timer
    (stop-play!)
    (do (swap! state assoc :playing true)
        (reset! timer (js/setInterval
                       (fn [] (let [{:keys [slide step]} @state
                                    last-step (dec (steps-of (nth slides slide)))]
                                (if (< step last-step) (go! slide (inc step)) (stop-play!))))
                       90)))))

(defn command! [cmd]
  (let [{:keys [slide step]} @state
        last-step (dec (steps-of (nth slides slide)))]
    (when-not (= cmd "play") (stop-play!))
    (case cmd
      "next-slide" (go! (inc slide) 0)
      "prev-slide" (go! (dec slide) (dec (steps-of (nth slides (max 0 (dec slide))))))
      "next-step" (go! slide (inc step))
      "prev-step" (go! slide (dec step))
      "next-10" (go! slide (+ step 10))
      "prev-10" (go! slide (- step 10))
      "first-step" (go! slide 0)
      "last-step" (go! slide last-step)
      "play" (toggle-play!)
      "help" (do (swap! state update :help not) (render!))
      nil)))

(defn key->command [e]
  (let [k (.-key e) shift (.-shiftKey e)]
    (cond
      (#{"ArrowDown" "PageDown"} k) "next-slide"
      (#{"ArrowUp" "PageUp"} k) "prev-slide"
      (= k "ArrowRight") (if shift "next-10" "next-step")
      (= k "ArrowLeft") (if shift "prev-10" "prev-step")
      (= k "Home") "first-step"
      (= k "End") "last-step"
      (= k " ") "play"
      (= k "?") "help")))

(defn fullscreen! []
  (if (.-fullscreenElement js/document)
    (.exitFullscreen js/document)
    (.requestFullscreen (.-documentElement js/document))))

(defn toggle-theme! []
  (let [root (.-documentElement js/document)
        dark (= "dark" (.getAttribute root "data-theme"))]
    (.setAttribute root "data-theme" (if dark "light" "dark"))))

(defn scale! []
  (when-not notes-mode?
    (let [el (.getElementById js/document "stage")
          s (min (/ (.-innerWidth js/window) 1600) (/ (.-innerHeight js/window) 900))]
      (set! (.. el -style -transform) (str "translate(-50%, -50%) scale(" s ")")))))

(defonce clock (atom {:running false :elapsed 0 :since nil}))

(defn clock-text []
  (let [{:keys [running elapsed since]} @clock
        ms (+ elapsed (if running (- (js/Date.now) since) 0))
        s (js/Math.floor (/ ms 1000))]
    (str (.padStart (str (quot s 60)) 2 "0") ":" (.padStart (str (mod s 60)) 2 "0"))))

(defn on-key [e]
  (when-not (or (.-metaKey e) (.-ctrlKey e) (.-altKey e))
    (let [k (.-key e)]
      (cond
        (#{"n" "N"} k) (when-not notes-mode? (.open js/window (str (.-pathname js/location) "?notes") "notes" "width=1000,height=760"))
        (#{"f" "F"} k) (fullscreen!)
        (#{"d" "D"} k) (toggle-theme!)
        (and notes-mode? (#{"t" "T"} k))
        (swap! clock (fn [{:keys [running elapsed since] :as c}]
                       (if running
                         (assoc c :running false :elapsed (+ elapsed (- (js/Date.now) since)))
                         (assoc c :running true :since (js/Date.now)))))
        :else (when-let [cmd (key->command e)]
                (.preventDefault e)
                (if notes-mode?
                  (.postMessage channel (clj->js {:type "command" :cmd cmd}))
                  (command! cmd)))))))

(defn on-message [e]
  (let [{:keys [type cmd slide step]} (js->clj (.-data e) :keywordize-keys true)]
    (cond
      (and notes-mode? (= type "state")) (do (swap! state assoc :slide slide :step step) (render!))
      (and (not notes-mode?) (= type "command")) (command! cmd)
      (and (not notes-mode?) (= type "hello")) (render!))))

(defn- current [] (nth slides (:slide @state)))

(defn- compute!
  "Advance one run by one state: the current slide's first, then the rest in order.
  Redraw the slide when it shows that run."
  []
  (if-let [k (first (remove done? (concat (:runs (current)) [:crowd :plain :momentum :adam])))]
    (do (advance! k)
        (when (some #{k} (:runs (current))) (render!)))
    (set! (.-talkReady js/window) true)))

(defn- frame!
  "Every animation frame: compute a little, then redraw the moving parts of the slide."
  []
  (when-not notes-mode?
    (compute!)
    (when-let [live (:live (current))]
      (doseq [[id content] (live (:step @state) (now-s))
              :let [el (.getElementById js/document id)]
              :when el]
        (set! (.-innerHTML el) (html content)))))
  (js/requestAnimationFrame frame!))

(defn reload!
  "Called by shadow-cljs after every hot reload: slides were redefined, redraw them.
  The runs restart, because the model or the steps may have changed."
  []
  (reset! runs {})
  (set! (.-talkReady js/window) false)
  (swap! state (fn [{:keys [slide step] :as st}]
                 (let [slide (clamp slide 0 (dec (count slides)))]
                   (assoc st :slide slide :step (clamp step 0 (dec (steps-of (nth slides slide))))))))
  (render!))

(defonce started (atom false))

(defn init []
  (when (compare-and-set! started false true)
    (when notes-mode? (.add (.-classList (.-body js/document)) "notes"))
    (when-let [h (and (not notes-mode?) (parse-hash))] (swap! state merge h))
    (.addEventListener js/window "keydown" on-key)
    (.addEventListener js/window "resize" scale!)
    (.addEventListener js/window "hashchange" #(when-let [h (parse-hash)] (go! (:slide h) (:step h))))
    (set! (.-onmessage channel) on-message)
    (js/requestAnimationFrame frame!)
    (when notes-mode?
      (.postMessage channel (clj->js {:type "hello"}))
      (js/setInterval #(when-let [c (.getElementById js/document "clock")] (set! (.-textContent c) (clock-text))) 500)))
  (scale!)
  (render!))
