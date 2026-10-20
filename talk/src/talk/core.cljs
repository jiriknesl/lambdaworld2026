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

(def sqrt-run (fixed-point (newton-sqrt 2) #(< (abs (- %1 %2)) 1e-12) 1.0))

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

(defn scene
  "The hills in 3D, at time t of tomorrow played fast: turbines turn as hard as the plan
  says (a nil plan runs them all), and birds pass along the flyway as the forecast says."
  [plan t {:keys [w h] :or {w 660 h 470}}]
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
     [:text.clock {:x (n1 (+ (- (/ w 2)) 10)) :y (n1 (+ (- (/ h 2)) 40))} (str hour ":00")]]))

;; =============================================================================
;; 4. Decide: restocking a hydrogen station
;; =============================================================================

(def capacity 4)   ; abstract tank units; one customer uses one unit
(def price 10)     ; revenue per load sold
(def unit-cost 4)  ; cost per load ordered
(def holding 1)    ; cost per load left over for a week

(src (defn remaining-stock [stock order demand]
       (max 0 (- (+ stock order) demand))))

(src (defn weekly-profit [stock order demand]
       (let [sold (min demand (+ stock order))
             left (remaining-stock stock order demand)]
         (- (* price sold)
            (* unit-cost order)
            (* holding left)))))

(def demand-probabilities {0 0.1, 1 0.3, 2 0.4, 3 0.2})

(src (defn expected-return [V stock order]
       (reduce +
         (for [[demand p] demand-probabilities]
           (* p (+ (weekly-profit stock order demand)
                   (* 0.9 (V (remaining-stock
                              stock order demand)))))))))

(src (def restock
  {:states (vec (range (inc capacity)))
   :actions (fn [stock] (range (inc (- capacity stock))))
   :outcomes demand-probabilities :discount 0.9
   :q expected-return}))

(def zero-values (zipmap (:states restock) (repeat 0)))

(defn demand-outcomes [V stock order]
  (for [[demand probability] (:outcomes restock)
        :let [left (remaining-stock stock order demand)
              reward (weekly-profit stock order demand)
              future (* (:discount restock) (V left))]]
    {:demand demand :probability probability :left left
     :reward reward :future future
     :weighted (* probability (+ reward future))}))

(src (defn bellman
       "Problem → (Values → Values).
        Every stock level takes its best order."
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
               (zipmap (:states restock) (repeat 0))))

(defn best-order [{:keys [states actions q]} V]
  (into (sorted-map) (for [s states] [s (apply max-key #(q V s %) (reverse (actions s)))])))

(src (defn order-up-to [target]
       (fn [stock] (max 0 (- target stock)))))

(src (defn evaluate-policy [policy]
       (fn [V]
         (into {} (for [stock (:states restock)]
                    [stock (expected-return
                            V stock (policy stock))])))))

(def policy-comparison
  (mapv (fn [target]
          (let [policy (order-up-to target)
                values (iterate (evaluate-policy policy)
                                zero-values)]
            {:target target :policy policy
             :one-week ((nth values 1) 1)
             :twenty-weeks ((nth values 20) 1)}))
        (range 5)))

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

(src (defn one-minute
       "Controller → (State → next states): any detection
        may arrive, then a minute passes."
       [controller]
       (fn [{:keys [minute waiting] :as s}]
         (set (for [event [nil
                           {:type :detect :level 1}
                           {:type :detect :level 2}]
                    :let [s (if event (controller s event) s)
                          s (assoc s :waiting
                                   (when (unanswered? s)
                                     (inc (or waiting 0))))]
                    next (if (< minute 9)
                           [(assoc s :minute (inc minute))]
                           (ticks controller s))]
                next)))))

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
             [id (reachable (one-minute controller) (initial-states controller))])))

(defn longest-wait-path
  "Breadth-first search with parents: the reachable path to the longest unanswered wait."
  [controller]
  (let [succ (one-minute controller)
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
         (for [i (range demand)
               :let [served? (< i sold)
                     colour (if (< phase 2) "var(--muted)" (if served? "var(--ok)" "var(--bad)"))
                     x (+ 40 (* 205 i))]]
           [:g (truck-icon x 215 colour false)
            [:text.small {:x x :y 292}
             (if (< phase 2) "waiting" (if served? "filled" "turned away"))]]))))

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
               (bullets "Lifelong software developer"
                        "Economist by education"
                        "Started Barcelona Meetup Group 13 years ago")])
    :notes "45 seconds. Lifelong software developer, economist by education. Started Barcelona Meetup Group 13 years ago. Next: why I work in Clojure."}

   {:section "Clojure" :title "Why Clojure?"
    :render (fn [_]
              [:div
               (bullets "Clojure is a simple & functional language"
                        "Immutable data"
                        "The REPL"
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
                  [:p.aside "This step improves a guess for √4."]]
                 [:div
                  [:div.fixed-example [:span "(step 3)"] [:strong (str "→ " (fmt (step 3) 6))]
                   [:span.caption "Changed. Keep going."]]
                  (when (pos? frame)
                    [:div.fixed-example.fixed-example-same
                     [:span "(step 2)"] [:strong (str "→ " (fmt (step 2)))]
                     [:span "2 is a fixed point: input = output."]])]]))
    :notes "Use the same Newton step, this time for the square root of four. Three changes to about 2.166667. Two maps exactly to two: applying the step again changes nothing. That is all fixed point means. It does not claim that every starting value reaches one. In the next slide the square root of two cannot be represented exactly, so we supply a tolerance instead of exact equality."}

   {:section "HOFs" :title "step + converged?" :steps (count sqrt-run)
    :render (fn [step]
              [:div.two
               [:div (code 'fixed-point {:marks #{"iterate" "converged?"}}) (code 'newton-sqrt {:size :small})]
               [:div
                [:div.caption "(fixed-point (newton-sqrt 2) close-enough? 1.0)"]
                [:table.trace (for [[i x] (map-indexed vector sqrt-run) :when (<= i step)]
                                [:tr [:td.k (str "x" i)] [:td.v (.toFixed x 12)]])]
                (when (= step (dec (count sqrt-run)))
                  [:p.aside "Change < 10⁻¹². Stop."])]])
    :notes "This is the pattern of the whole talk. A state, a step from state to state, a test for done. iterate makes the lazy sequence of states. take-while stops it. take and take-while are lazy. count eagerly walks only the non-converged prefix; it must inspect the first converged pair to stop. vec collects the retained states, including both states of that pair, from the already realized sequence without rerunning step. The default cap is 1000 updates, not a request to perform them all. A checked example with max-steps 10000 converges after 12 calls and returns 13 states including the start. Optimising, deciding and verifying are all this loop with different arguments."}

   {:section "Optimise" :title "Plan tomorrow’s turbine settings" :steps 2
    :live (fn [frame t] {"farm-context" (scene (when (pos? frame) (as-plan (:x first-guess))) t {:w 700 :h 510})})
    :render (fn [frame]
              [:div.two.even
               [:div
                (bullets "Six turbines. Twelve hours."
                         "Choose an operating level for each turbine-hour."
                         "More operation generates electricity and exposes more birds.")
                (code "{:x [0.5 0.5 0.5 …]}" {:size :small})
                [:p.caption "72 numbers between 0 and 1. Initially, every setting is 50%."]]
               [:div#farm-context (scene (when (pos? frame) (as-plan (:x first-guess))) (now-s) {:w 700 :h 510})]])
    :notes "We are optimizing one day-ahead operating plan. There are six turbines and twelve forecast hours, so a state has 72 operating fractions. Start with half power everywhere. The bird forecast and electricity forecast are fixed inputs for this optimization. We change the settings, not those forecasts. The picture locates the turbines relative to the flight path. The map macro remains available in the source, but we do not teach its implementation here."}

   {:section "Optimise" :title "What makes one plan better?" :steps 2
    :render (fn [frame]
              (let [off (zipmap slots (repeat 0)) on (zipmap slots (repeat 1))
                    half (as-plan (:x first-guess))]
                [:div.two.even
                 [:div
                  (if (zero? frame)
                    [:div (code 'electricity-lost {:size :small})
                     [:p.aside "Less electricity lost is better."]]
                    [:div (code 'bird-risk {:size :small})
                     [:p.aside "We also want less bird exposure."]])
                  [:p.caption "Bird risk is an illustrative exposure index, not a death count."]]
                 [:div
                  [:table.levels
                   [:tr [:th "plan"] [:th "generated kWh"] [:th "bird risk"]]
                   (for [[label plan] [["all stopped" off] ["half power" half] ["full power" on]]]
                     [:tr [:td label] [:td (fmt (- full-energy (lost plan)) 0)] [:td (fmt (risk plan) 1)]])]
                  [:p.aside "Choose where to reduce operation, rather than stopping everything."]
                  [:p.caption "A lower electricity cost alone would run every turbine at full power."]]]))
    :notes "Read the three complete candidate plans before any algorithm. Stopping everything protects birds but generates zero energy. Full power produces the most energy and the most exposure. Half power is our initial guess. electricity-lost returns one number for a whole plan; bird-risk returns another. They are ordinary Clojure functions over a map. We will combine them into a single cost and pass that function to the same update algorithm. The exposure index is a teaching model, not a mortality forecast."}

   {:section "Optimise" :title "Optimization is iterate" :steps 3
    :render (fn [frame]
              (let [{:keys [low high slope next]} (coordinate-update energy-cost)]
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
                    0 [:div (coordinate-plot energy-cost)
                       [:p.aside "Hold 71 settings fixed. Nudge turbine 1 at noon."]
                       [:p.caption (str "At 0.49999: " (fmt low 6) " kWh lost. At 0.50001: " (fmt high 6) ".")]
                       [:p.caption "Divide the cost difference by 0.00002: slope −84."]]
                    1 [:div
                       [:p.aside "Subtract rate × gradient from every setting."]
                       [:div.state-chain
                        [:div.state-node [:span.caption "old setting"] [:strong "0.5"]]
                        [:div.state-node [:span.caption "new setting"] [:strong (fmt next 4)]]]
                       (code "0.5 - 0.0001 * (-84) = 0.5084" {:size :small})
                       [:p.caption "All 72 slopes use the old settings. One call returns a new state."]]
                    [:div
                     [:p.aside "clamped wraps a step and limits its output."]
                     (kv-table [["proposed setting" "after clamped"] ["−0.1" "0"] ["0.5084" "0.5084"] ["1.2" "1"]])
                     [:p.caption "A turbine cannot operate below 0% or above 100%."]
                     [:p.aside "iterate feeds the new state back into this step."]])]]))
    :notes "This is the optimization algorithm: repeated updates to a vector of settings. gradient receives a numerical cost function and returns a numerical gradient function. It samples the cost just below and above each coordinate, holding the others fixed. No symbolic algebra or Hessian is used. descent-step receives that gradient and a learning rate, and returns a state-update function. One component moves from 0.5 to 0.5084 in the electricity-only example. All coordinates are updated from the same old vector. clamped is a function wrapper that limits each resulting setting to the physical range zero to one. It clips values; it is not a bird-safety constraint. The displayed iterate call repeatedly applies the resulting step. The next slide changes only the cost we supply."}

   {:section "Optimise" :title "Pass in a cost that includes birds" :steps 2
    :render (fn [frame]
              [:div.two.even
               [:div (code 'with-risk-penalty {:size :small :marks #{"cost" "risk" "fn"}})
                (code 'energy-cost {:size :small})
                (code 'risk-cost {:size :small})]
               [:div
                [:p.aside "with-risk-penalty takes two functions and returns a cost function."]
                [:div.equation-stack
                 [:div.equation-part "electricity lost"]
                 [:div.equation-part "+ 100 × excess bird risk²"]]
                [:p.caption "Excess risk is the amount above 12. The penalty is zero below 12."]
                (when (pos? frame)
                  [:div (code "(solver-step energy-cost)\n(solver-step risk-cost)" {:size :small})
                   [:p.caption "Same update algorithm, rate and initial state. Different cost function."]])]])
    :notes "This replaces the obscure minimize/subject-to language. These are real ordinary functions, and all the relevant source is on screen. energy-cost composes the plan representation with electricity-lost. with-risk-penalty closes over the cost, risk function, limit and weight and returns another function. When exposure exceeds twelve, the returned cost includes a quadratic penalty. This is a chosen tradeoff, not a hard guarantee of risk below twelve. The numerical gradient samples whichever cost we pass; it needs no understanding of birds. The next slide shows the two runs side by side instead of making the audience infer what changed between two slides."}

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
                  [:p.caption (str "Update " i (when (and (done? k) (= i (dec (count (run-of k))))) " · stopped: settings barely move"))]])])
    :notes "Both panels begin with exactly the same half-power plan and use rate 0.0001, the same clamp and the same stopping test. Press Space. Left: electricity alone pushes everything to full power. Right: the extra bird penalty reduces operation near the flight path and during heavy bird traffic. Read both electricity and risk at the end. A row is a turbine, a column is an hour; darker means more operation. An optimization iteration is a revised plan for the same day, not an hour passing. Stop when maximum setting change is below 0.001, with a work cap. Numerical settling is not a proof of global optimality. The right-hand final risk may slightly exceed twelve because this is a soft penalty."}

   {:section "Optimise" :title "Play the resulting day" :steps 2 :runs [:plain]
    :live (fn [frame t]
            (let [plan (if (zero? frame) (zipmap slots (repeat 1)) (:plan (run-summary :plain)))]
              {"played-3d" (scene plan t {:w 700 :h 500})}))
    :render (fn [frame]
              (let [plan (if (zero? frame) (zipmap slots (repeat 1)) (:plan (run-summary :plain)))]
                [:div.two.even
                 [:div [:p.aside (if (zero? frame) "Before: every turbine runs." "After: the optimized schedule.")]
                  (schedule plan {:w 700}) (plan-metrics plan)]
                 [:div#played-3d (scene plan (now-s) {:w 700 :h 500})]]))
    :notes "Now time really is passing through a day. First reveal is full power; second is the risk-aware plan. Contrast that with the previous slide, where an update changed the plan rather than advancing the clock. The red hubs mark stopped turbines. The example shows what the supplied cost function changes in the real-world interpretation of the state."}

   {:section "Decide" :title "How much hydrogen should we order?" :steps 4
    :render (fn [phase]
              [:div.two.even
               [:div (hydrogen-scene 1 1 3 (min phase 2))
                [:p.aside (nth ["Start with 1 unit. Order 1 more." "Delivery arrives before customers." "Three trucks arrive. One cannot refuel."] (min phase 2))]]
               [:div
                (if (= phase 3)
                  [:div (code 'remaining-stock {:size :small})
                   (code 'weekly-profit {:size :small})]
                  (kv-table [["state" "hydrogen left in the tank"]
                           ["decision" "units to order each week"]
                           ["uncertainty" "0, 1, 2 or 3 customer trucks"]
                           ["capacity" "4 units; each customer uses 1"]]))
                [:p.aside "Maximize expected profit over future weeks."]
                [:p.caption "Sell a unit for 10. Buy it for 4. Store leftovers for 1 per unit per week."]
                [:p.caption "Probabilities: 0 trucks 10% · 1 truck 30% · 2 trucks 40% · 3 trucks 20%."]
                (when (= phase 2)
                  [:p.caption "This outcome: 2 × 10 − 1 × 4 = 16 profit; no stock left."])]] )
    :notes "New problem, still a step function iterated to a fixed point. We operate a hydrogen filling station. Units are abstract tank units; each modeled customer truck consumes one, and the tank holds four. State is stock left at the start of a week. Choose an order before learning this week's demand. The tanker delivers, then customer trucks arrive. Walk through stock one, order one, demand three: two served, one turned away. Unsatisfied demand is lost sales, not a backlog. Unsold hydrogen carries forward; we charge a holding cost, not physical hydrogen evaporation. Prices, demand and units are illustrative. We maximize expected discounted profit, with future weeks discounted by 0.9."}

   {:section "Decide" :title "Why not stock the average demand?" :steps 2
    :render (fn [frame]
              [:div.two.even
               [:div
                [:p.aside "Average demand is 1.7 units. Try filling to 2."]
                (hydrogen-scene 1 (if (zero? frame) 1 2) 3 2)
                [:p.caption (if (zero? frame) "Fill to 2: one lost sale when 3 trucks arrive." "Fill to 3: all three served, but more stock can be left over.")]]
               [:div
                [:table.levels
                 [:tr [:th "fill to"] [:th "1 week"] [:th "20 weeks"]]
                 (for [{:keys [target one-week twenty-weeks]} policy-comparison :when (#{2 3} target)]
                   [:tr [:td target] [:td (fmt one-week 2)] [:td (fmt twenty-weeks 2)]])]
                [:p.caption "Expected discounted profit, starting with 1 unit. Same demand distribution."]
                [:p.aside "Today’s leftovers change next week’s decision."]
                [:p.caption "This small case can be checked by enumerating stocking rules. Bellman gives us a reusable update."]]])
    :notes "Take the user's simple heuristic seriously. Mean demand is 1.7, so filling to two sounds reasonable. But min(demand, stock) is nonlinear: the mean does not say how often we lose a sale or pay for leftover inventory. A rule filling to two beats filling to three for one week's expected profit from stock one. Over twenty weeks the ordering can reverse because inventory is carried forward and has future value. Both table entries are exact weighted enumeration for a fixed policy, not one cherry-picked random simulation. There is no need to pretend this five-state toy needs advanced mathematics: we could enumerate sensible policies. The point is the Bellman HOF that generalizes when there are more states and actions."}

   {:section "Decide" :title "Improve the estimate, then the order" :steps 4
    :render (fn [frame]
              (let [V (nth restock-run (min frame 2)) stock 1
                    scores (mapv (fn [order] [order (expected-return V stock order)]) ((:actions restock) stock))
                    best (apply max (map second scores))]
                [:div.two.even
                 [:div
                  (if (= frame 3)
                    [:div (code 'bellman {:size :small :marks #{"fn" "map" "max"}})
                     (code 'restock {:size :small})]
                    (code 'expected-return {:size :small :marks #{"V" "reduce"}}))
                  (code "(iterate (bellman restock) zero-values)" {:size :small :marks #{"iterate"}})
                  [:p.caption "V(stock) estimates future profit from that inventory."]]
                 [:div
                  [:p.aside (str "Hydrogen in stock: 1. Use V from update " (min frame 2) ".")]
                  [:table.levels [:tr [:th "order"] [:th "expected return"]]
                   (for [[order value] scores]
                     [:tr [:td order] [:td {:class (when (= value best) "ok")} (fmt value 2)]])]
                  [:p.caption "For each demand: this week’s profit + 0.9 × value of leftover stock."]
                  [:p.aside (if (= frame 3) "Repeat for every stock level to get the next V." "Choose the order with the highest expected return.")]]]))
    :notes "The input state to this iteration is a value table, not a truck or the physical tank. Start with all values zero, ignoring the future. Score each order by summing over possible demands. The first two estimates favor ordering one. After two value updates the recommendation changes to ordering two: future inventory now justifies the extra purchase. The fourth reveal shows bellman itself: it takes a model of states, actions and an ordinary scoring function, and returns the value-table update. The new maximum score becomes V(stock). Iterate feeds that whole table into the next backup. No custom macro and no symbolic differentiation are involved. weekly-profit and remaining-stock are ordinary arithmetic functions; their source and worked values appear in the station example."}

   {:section "Decide" :title "Hydrogen: iterate the value table" :steps (count restock-run)
    :render (fn [step]
              (let [V (nth restock-run step) policy (best-order restock V)
                    top (apply max (vals (peek restock-run)))]
                [:div.two.even
                 [:div (code 'bellman {:size :small})
                  (code "(fixed-point (bellman restock)\n  #(< (largest-change %1 %2) 0.001)\n  zero-values)" {:size :small})
                  [:p.aside "Same hydrogen station. Better estimates of future profit."]
                  [:p.caption "The tank is not changing here. We are improving a plan for every possible stock level."]]
                 [:div
                  [:div.bars
                   (for [stock (:states restock)]
                     [:div.bar-col
                      [:div.bar-v {:style {:height (str (max 2 (* 240 (/ (V stock) top))) "px")}}]
                      [:div.bar-n (fmt (V stock) 1)]
                      [:div.bar-k (str "stock " stock)]
                      [:div.bar-p (str "order " (policy stock))]])]
                  [:p.caption (str "Update " step " · bars: expected future profit · below: recommended order")]
                  (when (pos? step)
                    [:p.aside (str "Largest value change: " (fmt (largest-change (nth restock-run (dec step)) V) 4))])]]))
    :notes "This is still the hydrogen problem. Each column corresponds to a possible starting stock. Its height is the estimated discounted profit from that state; the label below is the best order under those estimates. Space advances value iteration, not calendar time. Early recommendations may change, then stabilize. Value estimates keep refining until the maximum change drops below 0.001. The stopping test is numerical; the proof section will explain a property of this operator."}

   {:section "Decide" :title "Hydrogen: compare the stocking rules" :steps 2
    :render (fn [frame]
              [:div.two.even
               [:div
                [:table.levels [:tr [:th "fill to"] [:th "20-week profit"]]
                 (for [{:keys [target twenty-weeks]} policy-comparison]
                   [:tr [:td target] [:td {:class (when (= target 3) "ok")} (fmt twenty-weeks 2)]])]
                [:p.caption "Expected discounted profit, initial stock 1. All five fixed target rules evaluated."]
                (code 'order-up-to {:size :small})]
               [:div
                [:p.aside "Bellman recommends ordering up to 3."]
                (hydrogen-scene 1 (if (zero? frame) 1 2) 3 2)
                [:p.caption (if (zero? frame) "Before: fill to 2, based on average demand." "After: fill to 3, accounting for future inventory.")]
                [:p.caption "This policy serves all three in this outcome. Other outcomes can leave stock in the tank."]]])
    :notes "Close the decision example with the policy and a before/after, not just a residual chart. The table computes all five fixed order-up-to rules for twenty weeks, starting with one unit; it does not claim these are all possible policies. Bellman independently produces the best stationary action for each state, which in this model is order up to three (and order zero when already at four). The finite-horizon table is an interpretable comparison, not the infinite-horizon convergence proof. The customer picture illustrates demand three; the profit table averages all demands, not that one favorable outcome. This same simple policy can be discovered by enumeration in a tiny model."}


   ;; ---------------------------------------------------------------- Verify (5 min)
   {:section "Validate" :title "Can a detected bird be left unprotected?" :steps 3
    :render (fn [step]
              [:div.two.even
               [:div
                (case step
                  0 (code 'tick-controller {:size :small})
                  1 [:div (code 'table-decision {:size :small})
                     (code 'react-to-detections {:size :small})]
                  [:div (code 'with-shield {:size :small :marks #{"decide"}})
                   (code 'shielded-decision {:size :small})])]
               [:div
                [:div.callout "Rule: confident detection → STOP in the same minute."]
                (case step
                  0 [:div
                     [:p.aside "The timer decides every 10 minutes."]
                     [:div.proof-flow [:div "minute 1: confident detection"]
                      [:span "↓ controller only records it"] [:div.bad "still running"]]
                     [:p.caption "The rule concerns the controller’s command; no physical braking model."]]
                  1 [:div
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
    :notes "Name the safety rule first, and what we validate: controller commands in a finite model. It is not a proof of bird survival or turbine braking physics. The original controller records detection but changes action only at a ten-minute tick. react-to-detections is a HOF that returns a controller which also decides on stronger detections. Still insufficient: the learned table has no row for an unseen context, defaults to probability zero, and keeps running. with-shield wraps the decision function. For known contexts it delegates; for unseen contexts it stops on any detection. The final reveal shows the exact definition of shielded-decision and executes the same confident detection through both controllers. The following exhaustive search checks that this holds throughout the modeled state space, not just in one hand-picked example."}

   {:section "Validate" :title "Can any event sequence reach RUN + confident bird?" :steps 12
    :render (fn [frame]
              (let [ci (quot frame 4) sub (mod frame 4)
                    {:keys [id label controller]} (controllers ci)
                    run (reach-runs id) k (frame-of sub 4 (count run))
                    states (nth run k) bad (filter unanswered? states)]
                [:div.two.even
                 [:div (code 'reach-step {:size :small :marks #{"fn" "mapcat"}})
                  (code 'reachable {:size :small :marks #{"fixed-point" "="}})
                  [:p.aside "Add every possible successor. Repeat until the set stops growing."]
                  (code 'unanswered? {:size :small})
                  [:p.caption "Failure: a confident bird was detected, but the command is RUN."]]
                 [:div [:p.aside label]
                  (controller-branches controller)
                  [:p.caption "One branch point: minute 1, an unseen situation."]
                  (kv-table [["expansion round" k] ["states reached" (count states)]
                             ["states breaking the rule" (count bad)]])
                  (when (= sub 3)
                    [:p.aside {:class (if (seq bad) "bad" "ok")}
                     (if (seq bad) "Failure is reachable." "Set closed. No failure in this model.")])]]))
    :notes "Reachable means obtainable by some modeled sequence of events. We search for a state where detection is confident and the controller still commands RUN. The diagram shows just one branching point in an unseen situation; the counts cover the whole explored finite model. reach-step takes a successor function and returns a set-expansion function. mapcat gets successors of all current states; into keeps old states and adds new ones. fixed-point repeatedly applies it, stopping on set equality. Advance four reveals per controller. Tick-only and immediate reactions both have failures; the shielded controller has none. This checks the encoded events and commands, not braking physics or the checker implementation."}

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
    :notes "One minute. This is the payoff. Point back to each animation: a schedule, values and actions, a growing set. The reusable structure accepted different functions. Our reachability result covers this finite model under its encoded events. It does not prove the checker or a physical turbine. Now use that separation to change the update without changing the iteration."}

   {:section "Compose" :title "Change the pace" :steps 2
    :render (fn [frame]
              (let [rate (if (zero? frame) 0.0001 0.0002)
                    step (clamped (descent-step (gradient energy-cost) rate) 0 1)
                    next (get-in (step first-guess) [:x 4])]
                [:div.two.even
                 [:div (code 'descent-step {:size :small})
                  (code (str "(def step\n  (clamped\n    (descent-step (gradient energy-cost) " rate ")\n    0 1))\n\n(iterate step first-guess)") {:size :small})]
                 [:div [:p.aside "Turbine 1 at noon: the same slope, −84."]
                  (kv-table [["rate" rate] ["old setting" "0.5"] ["new setting" (fmt next 4)]])
                  [:p.aside "Change the argument. Keep the iteration."]
                  [:p.caption "A larger step can overshoot. Faster movement does not guarantee faster convergence."]]]))
    :notes "We have seen all three algorithms run. Now compare this design with an update buried inside nested loops. To change the pace, pass a different rate to the function that constructs the update. The same coordinate moves twice as far; the iteration and stopping test do not change. This is the electricity-only cost, chosen to keep the arithmetic familiar. Larger rates can overshoot; this is an interface demonstration, not a recommendation to double every learning rate."}

   {:section "Compose" :title "Give the update momentum" :steps 2 :runs [:plain :momentum]
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
                    [:td (fmt (risk-cost (:x (peek run))) 1)]])]
                [:p.caption "Same cost, rate, start and stopping test. Momentum helps this run; it can hurt others."]]])
    :notes "Return to the cost including bird risk. with-momentum accepts a state-update function and returns another. It calls the base update once, measures that proposal's displacement, adds 90 percent of stored velocity, and returns new position and velocity. The outer clamped wrapper clips position to the allowed range; it does not reset velocity at a bound. The chart and table use actual runs. Fewer updates reach the same movement tolerance here; that does not prove a better optimum, general convergence, or a wall-clock speedup. The second reveal shows exactly what wraps what. The iteration remains unchanged."}

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
                          ["3 · max over actions" "max Q(V) ≤ max Q(W) + γ × ε" "Each action is bounded; so is max."]
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
                [:p.caption "Recorded: rainbow-evidence.edn · 3f411ae. Identifier wrapped."]]
               [:div.aside
                [:p "Exact operator: bound proved."]
                [:p "Floating-point browser code: correspondence unproved."]
                [:p "Stopping ≠ correctness."]]])
    :notes "One minute. The recorded check is the signed Bellman additive-closeness theorem with empty axiom closure. We did not check the JavaScript program against it. Point to that boundary rather than implying that a theorem name certifies a renderer. The epsilon-to-gamma-epsilon statement is what is checked; fixed-point existence, convergence and a run-level stopping certificate need their own premises and proofs."}

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
  (let [{:keys [section title render]} (nth slides slide)
        steps (steps-of (nth slides slide))]
    [:div.slide
     [:div.top
      [:div.sections (for [s section-order] [:span {:class (when (= s section) "on")} s])]
      [:div.count (str (inc slide) " / " (count slides))]]
     (when-not (#{"Hello" "Thank you"} section) [:h2.title title])
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
        nxt (get slides (inc slide))]
    [:div.notes-view
     [:div.n-head (str (inc slide) " / " (count slides) " · step " (inc step) " / " (steps-of (nth slides slide)))]
     [:h1 title]
     [:div.n-text notes]
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
