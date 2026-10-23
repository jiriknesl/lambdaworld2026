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

(def section-order ["Hello" "Clojure" "OR" "HOFs" "Optimise" "Decide" "Validate" "Compose" "Rainbow" "The book" "Thank you"])

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

   {:section "HOFs" :title "Functions in. Functions out." :steps 3
    :render (fn [step]
              [:div.two
               [:div (code 'newton-sqrt {:marks #{"fn"}})
                (code "(map (newton-sqrt 2) [1 2 4])")]
               [:div
                [:p.aside "newton-sqrt returns an update function."]
                [:p.caption "(function argument …) · fn introduces a function"]
                [:p.caption "Here: n > 0 and a positive starting guess."]
                (when (>= step 1)
                  [:div.callout [:p "map applies it to each guess."]
                   [:p (pr-str (mapv (newton-sqrt 2) [1 2 4]))]])
                (when (= step 2)
                  [:p.aside "Feed each result back: iterate."])]] )
    :notes "90 seconds. First show the returned fn; then map calls it on three guesses. These numbers are evaluated by the displayed function. A higher-order function accepts a function or returns one. map makes independent calls. iterate connects the calls, carrying the result forward. That distinction prepares the rest of the talk."}

   {:section "HOFs" :title "iterate: state → state → …" :steps 5
    :render (fn [step]
              [:div
               (code "(take 5 (iterate (newton-sqrt 2) 1.0))" {:marks #{"iterate"}})
               [:div.state-chain
                (for [[i x] (map-indexed vector (take (inc step) sqrt-run))]
                  [:div.state-node [:span.caption (str "state " i)]
                   [:strong (fmt x 6)]
                   [:span.caption (if (zero? i) "initial guess" "step(previous)")]])]])
    :notes "90 seconds. Press Right one state at a time. Say: input, call, output; the output becomes the next input. This is a trace we can replay, plot or inspect. take asks for five states. It does not claim that five is enough. Next we must choose what done means."}

   {:section "HOFs" :title "A fixed point stays the same" :steps 2
    :render (fn [frame]
              (let [step (newton-sqrt 4)]
                [:div.two.even
                 [:div (code 'newton-sqrt)
                  (code "(def step (newton-sqrt 4))")
                  [:p.aside "Find a positive x with x² = 4."]
                  [:p.caption "If x is too high, 4/x is too low. Average them."]]
                 [:div
                  [:p.aside "Start at 3. Feed each result back."]
                  [:table.trace
                   (for [[i x] (map-indexed vector (take 4 (iterate step 3)))]
                     [:tr [:td.k (if (zero? i) "start" (str "update " i))]
                      [:td.v (fmt x 6)]])]
                  (when (pos? frame)
                    [:div.callout
                     [:p "At x = 2: (2 + 4/2) / 2 = 2"]
                     [:p "The update leaves the solution unchanged."]])]]))
    :notes "We seek the positive square root of four. Newton averages x and 4/x. These straddle two for positive x. In exact arithmetic the new error is (x−2)²/(2x), so the first update is at least two and subsequent updates decrease toward two. A tiny positive initial guess can become farther away on its first update: do not claim every update improves every positive guess. The displayed trace starts at three and approaches two through repeated calls, rather than jumping to an independently chosen input. A positive fixed point satisfies x=(x+4/x)/2, hence x²=4. This connection comes from this specific step; iterate alone guarantees neither improvement nor convergence. The table rounds for display. Next use a tolerance for the square root of two."}

   {:section "HOFs" :title "step + converged?" :steps (count sqrt-run)
    :render (fn [step]
              [:div.two
               [:div
                (if (= step (dec (count sqrt-run)))
                  (code 'fixed-point {:size :small :marks #{"iterate" "converged?"}})
                  (code "(fixed-point step converged? start)\n\n(iterate step start)" {:marks #{"iterate"}}))
                (code 'close-enough? {:size :small})]
               [:div
                [:div.caption "(fixed-point (newton-sqrt 2) close-enough? 1.0)"]
                [:table.trace (for [[i x] (map-indexed vector sqrt-run) :when (<= i step)]
                                [:tr [:td.k (str "x" i)] [:td.v (.toFixed x 12)]])]
                (when (= step (dec (count sqrt-run)))
                  [:p.aside "Change < 10⁻¹². Stop."])]])
    :notes "This is the pattern of the whole talk. A state, a step from state to state, a test for done. iterate makes the lazy sequence of states. take-while stops it. take and take-while are lazy. count eagerly walks only the non-converged prefix; it must inspect the first converged pair to stop. vec collects the retained states, including both states of that pair, from the already realized sequence without rerunning step. The default cap is 1000 updates, not a request to perform them all. A checked example with max-steps 10000 converges after 12 calls and returns 13 states including the start. Optimising, deciding and verifying are all this loop with different arguments."}

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

   {:section "Optimise" :title "What makes one plan better?" :steps 3
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
                     [:p.aside (if (zero? frame) "More generation also means more bird exposure." "We need a rule for choosing between these competing goals.")]])]]))
    :notes "First compare whole plans: retaining more electricity increases bird exposure too. Next reveal the full turbine-by-hour exposure grid. It is calculated from the actual factors used in bird-risk: a turbine's proximity weight times that hour's bird-traffic forecast. T3 is near the flyway; T2 is farther away, so curtailing T3 protects more exposure for the same sacrificed electricity at noon. Every row has the same daily shape scaled by location; these are not independently estimated probability distributions or bird fatality rates. The final reveal returns to the competing metrics before choosing a scalar score in slide 13. electricity-lost and bird-risk both score the whole 72-setting plan."}

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


   ;; ---------------------------------------------------------------- Verify (5 min)
   {:section "Validate" :title "Validate the turbine controller" :steps 4
    :render (fn [step]
              [:div.two.even
               [:div
                (case step
                  0 [:div
                     [:p.aside "Back at the wind farm: software decides RUN or STOP."]
                     [:div.proof-flow
                      [:div "Sensor: confident bird detection"]
                      [:span "↓ input event"]
                      [:div "Turbine controller"]
                      [:span "↓ output command"]
                      [:div.bad (str "Timer-only controller: "
                                     (.toUpperCase (name (:action (:tick-only shield-comparison)))))]]
                     [:p.caption "This detection arrives between timer ticks."]]
                  1 (code 'tick-controller {:size :small})
                  2 [:div (code 'table-decision {:size :small})
                     (code 'react-to-detections {:size :small})]
                  [:div (code 'with-shield {:size :small :marks #{"decide"}})
                   (code 'shielded-decision {:size :small})
                   (code (:code (controllers 2)) {:size :small})])]
               [:div
                [:div.callout "Rule: confident detection → STOP in the same minute."]
                (case step
                  0 [:div
                     [:p.aside "Can any sequence of events break this rule?"]
                     [:p "Explore the controller’s possible states. Look for a detection followed by RUN."]
                     [:p.caption "Bird detection is an input. We check the software’s response, not the sensor’s accuracy."]]
                  1 [:div
                     [:p.aside "The timer decides every 10 minutes."]
                     [:div.proof-flow [:div "minute 1: confident detection"]
                      [:span "↓ controller only records it"] [:div.bad "still running"]]
                     [:p.caption "The rule concerns the controller’s command; no physical braking model."]]
                  2 [:div
                     [:p.aside "React immediately. But what does the table say?"]
                     (kv-table [["known situation + confident bird" "stop probability 1"]
                                ["unknown situation" "missing row → probability 0"]])
                     [:p.caption "An immediate decision can still be the wrong decision."]]
                  [:div
                   [:p.aside "Same unseen situation. Same confident bird."]
                   (code "{:ctx :unseen :det 2 :u 0.3}" {:size :small})
                   [:div.shield-results
                    [:div [:span.caption "Reacting, no shield"]
                     [:strong.bad (name (:action (:reacting shield-comparison)))]]
                    [:div [:span.caption "Reacting + shield"]
                     [:strong.ok (name (:action (:shielded shield-comparison)))]]]
                   [:p.caption "Computed by both controllers from the same detection event."]])]])
    :notes "Return explicitly to the wind farm, with a separate controller example. The earlier optimizer chose a day schedule; this example checks a controller's response to incoming events, not the correctness of that schedule. Name the input (sensor detection), implementation (controller), output (RUN or STOP) and requirement (confident detection must yield STOP in the same modeled minute). Validation asks whether any encoded event sequence violates that requirement. We assume the sensor reports a detection; we do not validate its classifier, bird survival or turbine braking physics. The first reveal demonstrates a failure before showing code. The original controller records detection but changes action only at a ten-minute tick. react-to-detections is a HOF that returns a controller which also decides on stronger detections. Still insufficient: the learned table has no row for an unseen context, defaults to probability zero, and keeps running. with-shield wraps the decision function. For known contexts it delegates; for unseen contexts it stops on any detection. The final reveal shows the exact definition of shielded-decision and executes the same confident detection through both controllers. The following exhaustive search checks that this holds throughout the modeled state space, not just in one hand-picked example."}

   {:section "Validate" :title "Can any event sequence reach RUN + confident bird?" :steps 9
    :render (fn [frame]
              (let [ci (quot frame 3) sub (mod frame 3)
                    {:keys [id label controller]} (controllers ci)
                    run (reach-runs id) k (frame-of sub 3 (count run))
                    states (nth run k) bad (filter unanswered? states)]
                [:div.two.even
                 [:div (code 'reach-step {:size :small :marks #{"fn" "mapcat"}})
                  (code 'reachable {:size :small :marks #{"fixed-point" "="}})
                  [:p.aside "Add every possible successor. Repeat until the set stops growing."]
                  (code 'unanswered? {:size :small})
                  [:p.caption "Failure: a confident bird was detected, but the command is RUN."]]
                 [:div [:p.aside label]
                  (controller-branches controller)
                  [:p.caption "One branch point shown; counts cover all encoded initial states and events."]
                  (kv-table [["expansion round" k] ["states reached" (count states)]
                             ["states breaking the rule" (count bad)]])
                  (when (= sub 2)
                    [:p.aside {:class (if (seq bad) "bad" "ok")}
                     (if (seq bad) "Failure is reachable." "Set closed. No failure in this model.")])]]))
    :notes "Reachable means obtainable by some modeled sequence of events. We search for a state where detection is confident and the controller still commands RUN. The diagram shows just one branching point in an unseen situation; the counts cover the whole explored finite model. reach-step takes a successor function and returns a set-expansion function. mapcat gets successors of all current states; into keeps old states and adds new ones. fixed-point repeatedly applies it, stopping on set equality. Advance three reveals per controller: initial set, sampled expansion and closure. Tick-only and immediate reactions both have failures; the shielded controller has none. This checks the encoded events and commands, not braking physics or the checker implementation."}

   {:section "Validate" :title "iterate underneath all three"
    :render (fn [_]
              [:div
               [:table.levels
                [:tr [:th "example"] [:th "state"] [:th "supplied step"] [:th "stop test"]]
                [:tr [:td "optimise"] [:td "turbine settings"] [:td "gradient step"] [:td "small movement"]]
                [:tr [:td "decide"] [:td "value function"] [:td "Bellman operator"] [:td "small value change"]]
                [:tr [:td "validate"] [:td "set of reached states"] [:td "expand with successors"] [:td "set equality"]]]
               [:div.promise "(iterate step start)"]
               [:p.caption "fixed-point takes this sequence until the supplied stopping test passes."]])
    :notes "One minute. This is the payoff. Point back to each animation: a schedule, values and actions, a growing set. The reusable structure accepted different functions. Our reachability result covers this finite model under its encoded events. It does not prove the checker or a physical turbine. The optimizer has already demonstrated rate and momentum changes. Next observe a supplied update without changing its result."}

   {:section "Compose" :title "Observe each update" :steps 2
    :render (fn [frame]
              [:div.two.even
               [:div (code 'with-observer {:size :small :marks #{"step" "observe!" "fn"}})
                (when (pos? frame)
                  [:div (code 'observed-costs {:size :small})
                   (code 'record-cost! {:size :small})])
                (code "(vec\n  (take 5\n    (iterate (with-observer risk-step record-cost!)\n             first-guess)))" {:size :small})]
               [:div [:p.aside "The wrapper calls the observer, then returns the new state."]
                [:table.levels [:tr [:th "update"] [:th "recorded cost"]]
                 (for [[i value] (map-indexed vector @observed-costs)]
                   [:tr [:td (inc i)] [:td (fmt value 2)]])]
                [:p.caption "Five states, including the start. Four updates logged."]
                [:p.caption "Logging happens when the lazy sequence is realized."]]])
    :notes "with-observer wraps any step. It calls the supplied observer with old and new state, and returns the new state unchanged. record-cost! is shown in full: it appends the actual cost to an atom. The demo realizes take 5 once and displays those four log entries. The computation stays in the original pure step; the observer deliberately performs an effect. Laziness matters: creating the sequence does not run the log. Reusing realized states does not repeat the log, but constructing and realizing a fresh sequence does. This is the composition payoff: change the update or observe it without editing the traversal."}

   {:section "Rainbow" :title "Rainbow"
    :render (fn [_]
              [:div.two.even
               [:div [:p.aside "A theorem prover written in Clojure."]
                [:p.aside [:a {:href "https://flexiana.com/rainbow"} "flexiana.com/rainbow"]]]
               [:div [:p.aside "Can we prove a property of our update?"]
                [:p "For Bellman: two value estimates get closer after an update."]
                [:p.caption "Specify the claim. Construct a proof. Check it."]]])
    :notes "Clojure is dynamically typed. You might prefer Haskell for static guarantees; those catch useful classes of errors. But choosing a dynamic language does not prevent us from stating and mechanically checking a mathematical theorem. Rainbow is a theorem prover written in Clojure. Static typing and proof are complementary, and Haskell can participate in formal verification too. We will prove a specific property under explicit assumptions, not claim the entire application has no bugs. First show a tiny proof of function composition to teach Rainbow syntax, then the larger property of Bellman's update."}

   {:section "Rainbow" :title "A proof is a function" :steps 2
    :render (fn [step]
              (let [receipt (n/checked-composition)]
                [:div.two.even
                 [:div (code (:source receipt) {:size :small})
                  [:p.caption "Rainbow explicit term syntax · checked from this exact source."]]
                 [:div
                  [:div.proof-flow [:div "p : P"] [:span "↓ f : P → Q"]
                   [:div "f(p) : Q"] [:span "↓ g : Q → R"] [:div "g(f(p)) : R"]]
                  (if (zero? step)
                    [:p.aside [:span.ok (name (:status receipt))] " · no axioms"]
                    [:div
                     (code "[:app f p]" {:size :small})
                     [:p.aside [:span.bad (name (:negative-status receipt))] " · Q is not R"]])]]))
    :notes "This is actual Rainbow explicit term syntax, not Clojure pseudocode or a tactic sketch. P, Q and R are arbitrary propositions. The type says: given a proof-transformer f from P to Q, a proof-transformer g from Q to R, and a proof p of P, produce a proof of R. forall binds propositions; the arrow is implication; fn binds the proof's arguments; app applies one proof term to another. The body applies f to p, then g to that result: function composition. The kernel checked this exact file in an empty environment, with no axioms. On the second reveal propose only f(p); it has type Q, not R, and the same kernel rejects it. Both verdicts were recorded by talk/scripts/check-proof.clj, not computed in the browser. This small proof teaches the syntax; the following Bellman theorem is the larger OR application."}

   {:section "Rainbow" :title "Bellman shrinks disagreement" :steps 2
    :render (fn [step]
              (let [B (bellman restock)
                    V (zipmap (:states restock) (repeat 0))
                    W (zipmap (:states restock) (repeat 10))
                    before (largest-change V W)
                    after (largest-change (B V) (B W))]
                [:div.two.even
                 [:div [:pre.sig "B : Values → Values\n\nIf V and W differ by at most ε,\nB(V) and B(W) differ\nby at most γ × ε."]
                  [:p.caption "Finite model · p ≥ 0 · Σp = 1 · admissible actions nonempty · 0 ≤ γ < 1."]]
                 [:div
                  [:div.proof-flow [:div (str "two value tables: gap " (fmt before))]
                   [:span "↓ Bellman on both"]
                   (when (= step 1) [:div (str "new gap: " (fmt after) " = 0.9 × " (fmt before))])]
                  [:p.aside "One example. The proof must cover every V, W."]]]))
    :notes "One minute. We are back at the restocking example, not learning another OR problem. The left is a mathematical statement about an operator. The right really applies our browser Bellman function to two value tables. It is an example, not evidence that all inputs satisfy it. Now show why the general bound holds."}

   {:section "Rainbow" :title "The proof follows the code" :steps 4
    :render (fn [step]
              (let [rows [["1 · map outcomes" "Identical rewards cancel." "Future-value gap ≤ ε."]
                          ["2 · weighted reduce" "Σ p × γ × ε = γ × ε" "Nonnegative, normalized probabilities."]
                          ["3 · max over actions" "best score with V ≤ best score with W + γ × ε" "The winning actions may differ."]
                          ["4 · swap V and W" "|B(V)(s) − B(W)(s)| ≤ γ × ε" "Both directions, every state."]]]
                [:div
                 [:div.proof-steps
                  (for [[i [label statement reason]] (map-indexed vector rows)]
                    [:div.proof-row {:class (if (<= i step) "revealed" "pending")}
                     [:strong label] [:div [:b statement] [:p reason]]])]
                 [:p.caption "Proof outline · Rainbow checks the formal exact-rational term."]]))
    :notes "Two minutes. Reveal one row at a time. Fix a state and an action. Subtract the two backups: immediate reward is identical, so it cancels. Future values differ by at most epsilon; multiply by nonnegative probabilities and discount and sum. Normalization makes the sum gamma epsilon. For each action QV is at most QW plus this bound, hence at most max QW plus the bound; take max QV. Reverse V and W for the other inequality. This is a proof for all admissible inputs, organised exactly like the nested map, weighted reduce and max. Different best actions do not break the argument."}

   {:section "Rainbow" :title "What the kernel checked"
    :render (fn [_]
              [:div.two.even
               [:div
                (code "value/signed-optimality-step-\n  additively-contractive\n\nstatus: checked\naxioms: []\nchecked: 2026-10-09")
                [:p.caption "Historical receipt · replay after kernel repair pending."]]
               [:div.aside
                [:p "Exact operator: mathematical bound."]
                [:p "Floating-point browser code: correspondence unproved."]
                [:p "An exact residual can bound value error."]
                [:p.caption "With γ = 0.9: residual ≤ 0.001 ⇒ value error ≤ 0.01."]
                [:p.caption "Mathematical consequence; not a certificate for this browser run."]]])
    :notes "This historical receipt predates the documented kernel repair; replay on the repaired kernel is pending. The recorded check is the signed Bellman additive-closeness theorem with empty axiom closure. We did not check the JavaScript program against it. Point to that boundary rather than implying that a theorem name certifies a renderer. The epsilon-to-gamma-epsilon statement is what is checked; fixed-point existence and convergence use the usual finite discounted model assumptions. The residual-to-error bound is a mathematical consequence of contraction, not a recorded certificate for this floating-point run. Residual refers to a candidate table versus one more update, not the size of a chosen action."}

   {:section "Rainbow" :title "Scheduling an e-methanol plant"
    :render (fn [_]
              [:div
               [:p.aside "I built a purchasing model using Spain’s wholesale electricity prices."]
               [:div.plant-flow
                [:div.plant-node [:strong "Electrolyser"] [:span "120 MW"]]
                [:span.plant-arrow "→"]
                [:div.plant-node [:strong "Hydrogen tank"] [:span "20 t"]]
                [:span.plant-arrow "→"]
                [:div.plant-node [:strong "Methanol loop"] [:span "10 t/h"]]]
               [:p.aside "Electricity prices change every hour. The plant still has to deliver."]
               [:p.aside "When should we buy power, make hydrogen, and use the tank?"]
               [:p.caption "588 t/week · illustrative plant · known OMIE prices · no local weather inputs"]])
    :notes "This illustrative methanol model has no municipality, named physical plant or weather inputs. Spain refers to the OMIE peninsular wholesale bidding zone, not a uniform renewable PPA price. Plant model and certificate producer: ../rh2edin-challenge/proofs/rainbow_bb_export.clj. HiGHS supplies the MIP incumbent; our Clojure branch-and-bound constructs a certificate using HiGHS LP relaxations. The Rainbow checker validates that certificate. This is not the slide gradient-descent or Bellman solver. Reported plant benchmark supplied by Jiri. Electricity use is 50 MWh per tonne of hydrogen; methanol consumes 0.19 tonnes of hydrogen per tonne. The tank starts at 10 tonnes and must finish at least there. The loop has 50% turndown and a EUR 1,000 start cost. The four-stack variant has four 30 MW stacks, 20% turndown and EUR 500 per stack start. No ramp limits, degradation, CO₂ or compressor limits, grid fees or renewable-fuel sourcing rules are represented. This is a model with known weekly prices, not a live day-ahead trading strategy. No operating trace is drawn because the underlying hourly schedules were not supplied."}

   {:section "Rainbow" :title "Our code and the solver"
    :render (fn [_]
              [:div.proof-steps
               [:div.proof-row.revealed [:strong "Our model"]
                [:div [:b "Prices, equipment, storage, production"]]]
               [:div.proof-row.revealed [:strong "HiGHS"]
                [:div [:b "Finds the proposed schedule"]]]
               [:div.proof-row.revealed [:strong "Our search"]
                [:div [:b "Builds the certificate tree"]
                 [:p "HiGHS solves the smaller problems inside it."]]]
               [:div.proof-row.revealed [:strong "Rainbow"]
                [:div [:b "Checks the certificate with exact arithmetic"]]]])
    :notes "Verified against ../rh2edin-challenge/proofs/rainbow_bb_export.clj, especially the incumbent at line 84 and the branch-and-bound loop near line 162. Our contribution includes the plant formulation, branch selection, exact certificate construction and checker. HiGHS supplies both the MIP incumbent and LP relaxation solves. HiGHS is a third-party open-source solver from the Edinburgh optimization community. No claim that our gradient descent or Bellman demo generated this schedule."}

   {:section "Rainbow" :title "San Roque: what can scheduling save?"
    :render (fn [_]
              [:div
               [:p.aside "e-SAF model · Bay of Algeciras · local wind and solar data"]
               [:div.certificate-results
                [:div [:strong "€4.4m/year"] [:span "mean modeled saving"]]
                [:div [:strong "€2.5–6.3m"] [:span "reported 95% interval"]]]
               [:p.aside "Energy and backup hydrogen, compared with the best tuned operating rule."]
               [:p.caption "Excludes the assumed cost of missed deliveries. Not savings measured at a factory."]
               [:p.caption "Nine weather/price combinations · price years also used in tuning"]
               [:p.caption "Separate study from the methanol certificate benchmark."]])
    :notes "Site point: 36.18 N, 5.40 W in data/manifest.json. The study concerns the San Roque / Los Barrios industrial area, not a named operating factory or customer engineering model. Open-Meteo/ERA5 weather 2015–2025 at that point; PV calibrated to PVGIS; wind uses a generic power curve. Equipment and cost inputs are labelled assumptions in esaf/assumptions.clj. It is not an average Spanish weather profile. Source: ../rh2edin-challenge/challenge-1-esaf/README.md section 5.1 and results/results.json operations. Energy/backup component = total value_vs_rule minus shortfall component, mean 4.388895399574215 M EUR/year; the reported lower interval endpoint is 2.4782220376606423 M EUR/year. This is a modeled comparison, not realized customer savings or a guarantee. Nine cases combine three held-out weather years with three price years reused in tuning; do not call them nine independent future years. Baseline is the strongest of three tuned rule families. The full 9.3 M EUR figure includes modeled missed-delivery penalties; we deliberately quote energy/backup only. This study uses e-SAF, not the e-methanol weekly certificate model, and is not covered by that 1e-6 bound. No commercial relationship or customer outcome is asserted."}

   {:section "Rainbow" :title "How much cheaper could it be?"
    :render (fn [_]
              [:div
               [:p.aside "All 52 tested weeks of 2023: within one part per million."]
               [:div.certificate-results
                [:div [:strong "10⁻⁶"] [:span "relative cost gap"]]
                [:div [:strong "€0.26"] [:span "largest reported gap, on a €388,000 week"]]]
               [:p.aside "A solver finds the schedule. Our code builds and checks the certificate."]
               [:p.caption "Reported certificate results · exact arithmetic · optimality within the model"]
               [:p.certificate-boundary "The checker's soundness proof is still open."]
               [:p.caption "Acceptance ⇒ optimality is not yet a proved theorem."]])
    :notes "Jiri supplied these results; their provenance and the division of work are documented in ../rainbow/DESIGN-2026-10-18-proved-milp-certificates.md. No benchmark was rerun for these slides. HiGHS finds the incumbent; our own branch-and-bound uses HiGHS LP solves to construct the exact certificate. The checker is our code. The certificates describe a search tree with bounds at its leaves and accounted-for splits. Exact checking tests the submitted evidence rather than trusting a solver status. The checker runs as a kernel-certified program, but its acceptance-implies-optimality soundness theorem remains open. Do not call these end-to-end formal optimality proofs. A reported nonzero tolerance also does not mean no cheaper schedule exists. One four-stack week remains unaccepted; no monthly certificate result is claimed. HiGHS is faster at solving; this work is about checking answers. Proof coverage concerns the model, not the real plant."}

   ;; ---------------------------------------------------------------- The book (2 min)
   {:section "The book" :title "Applied Higher-Order Functions"
    :render (fn [_]
              [:div.two.even
               [:div.book-photo {:role "img" :aria-label "Space reserved for the book photo"}
                [:span "Book photo"]]
               [:div
                [:p.aside "There is more than iterate."]
                [:p "About twelve HOFs, and their composition."]
                (code "map · reduce · filter · comp\npartial · iterate …" {:size :small})
                [:p.goal "flexiana.com/book"]]])
    :notes "The talk centered on iterate. The book develops a broader toolkit of roughly twelve higher-order functions and their composition across optimization, decision-making and validation. Every listing runs with the companion library orhof. Book text: CC BY-SA 4.0; library: MIT. Replace the reserved photo area with Jiri's supplied book photograph."}

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
