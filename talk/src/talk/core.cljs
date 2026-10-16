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

  Every number on a slide is computed by the code in this file; every code panel shows the
  exact source text of a definition wrapped in `src`."
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

(defn bellman-formula [{:keys [source discount]}]
  (let [[_ _ [s] [a] [w] & opts] source
        {:keys [reward next]} (apply hash-map opts)]
    [:div.formula
     [:div.f-line [:i "V"] "(" [:i (name s)] ") = " [:b.op "max"] [:sub [:i (name a)]] " "
      [:span.bigop "Σ"] [:sub [:i (name w)]] " " [:i "p"] "(" [:i (name w)] ") · [ "
      [:i "reward"] " + " (fmt discount) " · " [:i "V"] "(" [:i "next"] ") ]"]
     [:div.f-st
      [:div.f-line [:span.f-name "reward"] (fx reward)]
      [:div.f-line [:span.f-name "next"] (fx next)]]]))

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

;; Tomorrow's forecasts, hour by hour (illustrative numbers). Soaring birds fly on the
;; midday thermals; Spanish power is cheapest at midday, when the solar farms peak.
(def hours (vec (range 8 20)))
(def birds (zipmap hours [0.2 0.5 1.2 2.4 3.6 4 3.6 2.4 1.2 0.5 0.2 0.1]))
(def value (zipmap hours (map #(* 2 %) [92 85 70 55 42 38 40 48 66 98 120 115])))  ; € per turbine-hour
(def budget 12)

(def slots (vec (for [t turbines h hours] [t h])))

(src (defn wear
       "Changing speed wears the gearbox: 50 € for a
        full stop or start, less for less."
       [run t h]
       (if-let [later (run [t (inc h)])]
         (* 50 (sq (- later (run [t h]))))
         0)))

(defmodel day-plan [run]
  (minimize (sum [[t h] slots]
                 (+ (* (value h) (- 1 (run [t h])))
                    (wear run t h))))
  (subject-to
   [bird-risk (<= (sum [[t h] slots]
                    (* (exposure t) (birds h) (run [t h])))
                  budget)]))

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

(defn as-plan
  "The optimiser's flat numbers → the plan: a map from [turbine hour] to how hard it runs."
  [xs]
  (zipmap slots xs))

(defn on-flat [model] (comp (:objective model) as-plan))

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

(def run-specs
  (let [plain (descent-step (gradient (on-flat day-plan)) 0.0003)
        safe (descent-step (gradient (on-flat safe-plan)) 0.0001)]
    {:crowd {:label "plain step, birds cost nothing" :step (clamped plain 0 1)}
     :plain {:label "plain gradient step" :step (clamped safe 0 1)}
     :momentum {:label "with-momentum 0.9" :step (clamped (with-momentum safe 0.9) 0 1)}
     :adam {:label "with-adam 0.02" :step (clamped (with-adam safe 0.02) 0 1)}}))

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

(defn risk [plan] (reduce + (for [[t h] slots] (* (exposure t) (birds h) (plan [t h])))))
(defn lost [plan] (reduce + (for [[t h] slots] (* (value h) (- 1 (plan [t h]))))))
(def full-value (reduce + (for [[_ h] slots] (value h))))

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
        vmax (apply max (vals value))]
    (svg w h
         [:text.small {:x 0 :y 40} "birds passing"]
         [:text.small {:x 0 :y (- top 6)} "turbine · near flyway"]
         [:text.small.price {:x 0 :y 70} "power price"]
         (for [[i hr] (map-indexed vector hours)
               :let [bh (* 70 (/ (birds hr) bmax))]]
           [:rect.bird-bar {:x (+ (x-of i) 5) :y (- 92 bh) :width (- cw 10) :height bh}])
         (polyline (for [[i hr] (map-indexed vector hours)]
                     [(+ (x-of i) (/ cw 2)) (- 92 (* 70 (/ (value hr) vmax)))])
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

(def capacity 4)   ; trailer loads the station can hold
(def price 10)     ; revenue per load sold
(def unit-cost 4)  ; cost per load ordered
(def holding 1)    ; cost per load left over for a week

(defbellman restock
  [stock (range 0 (inc capacity))]
  [order (range 0 (inc (- capacity stock)))]
  [demand {0 0.1, 1 0.3, 2 0.4, 3 0.2}]
  :reward (- (* price (min demand (+ stock order)))
             (* unit-cost order)
             (* holding (max 0 (- (+ stock order) demand))))
  :next (max 0 (- (+ stock order) demand))
  :discount 0.9)

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
(def shielded-decision (with-shield decide-by-table stop-table))

(def controllers
  [{:id :tick-only :label "decides every 10 minutes"
    :code "(tick-controller decide-by-table)"
    :controller (tick-controller decide-by-table)}
   {:id :reacting :label "+ reacts to detections"
    :code "(react-to-detections\n  (tick-controller decide-by-table)\n  decide-by-table)"
    :controller (react-to-detections (tick-controller decide-by-table) decide-by-table)}
   {:id :shielded :label "+ shield for unseen situations"
    :code "(react-to-detections\n  (tick-controller shielded-decision)\n  shielded-decision)\n;; shielded-decision =\n;;   (with-shield decide-by-table stop-table)"
    :controller (react-to-detections (tick-controller shielded-decision) shielded-decision)}])

(defn unanswered? [{:keys [det action]}] (and (= 2 det) (= :run action)))

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

(src (defn reachable
       "Every state the system can reach:
        grow the set until it stops growing."
       [successors initial]
       (fixed-point
        (fn [states] (into states (mapcat successors states)))
        =
        (set initial))))

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
                 (when (and (done? k) (= i (dec (count run))))
                   (final (run-summary k)))]]))
   :notes notes})

;; ---- Slide content ---------------------------------------------------------------

(defn bullets [& items] [:ul.bullets (for [i items] [:li i])])

(def section-order ["Hello" "Why Clojure" "The loop" "Optimise" "Decide" "Verify" "Rainbow" "The book" "Questions"])

(def slides
  [;; ---------------------------------------------------------------- Hello (3 min)
   {:section "Hello" :title "Rebuilding Operations Research in Higher-Order Functions"
    :render (fn [_]
              [:div.title-slide
               [:div.kicker "Lambda World 2026"]
               [:h1.big "Rebuilding Operations Research" [:br] "in Higher-Order Functions"]
               [:div.sub "Three problems, one loop, in Clojure"]
               [:div.byline "Jiri Knesl · Flexiana"]])
    :notes "Pause. Let them read the title."}

   {:section "Hello" :title "Who I am"
    :render (fn [_]
              [:div.two.even
               [:div
                (bullets "Developer. I founded Flexiana, a software and applied-mathematics company."
                         "I live in Gibraltar, on the Bay of Algeciras."
                         "I wrote Applied Higher-Order Functions."
                         "I am building Rainbow, a theorem prover in Clojure.")]
               [:div.aside
                [:p "I am not a mathematician."]
                [:p "You could write every line of code in this talk on Monday."]]])
    :notes "Two minutes. Say you are a developer, not a mathematician. People ask more questions when they know. Add your own story here."}

   {:section "Hello" :title "Software already makes these decisions"
    :render (fn [_]
              [:div
               (bullets "Which trailer goes to which station. When to restock. When a wind turbine stops for a bird."
                        "We write the code around the solver and never open it."
                        "Today we open it. Inside are a few higher-order functions you already use.")
               [:div.promise "state · step · converged?  →  " [:code "iterate"]]])
    :notes "Every example today has three parts: a state, a step that improves it, and a test that says when to stop. iterate runs them. All three come from our work on the Bay of Algeciras: hydrogen trailers, hydrogen stations, and a wind farm on a bird migration route."}

   ;; ---------------------------------------------------------------- Why Clojure (5 min)
   {:section "Why Clojure" :title "Code is data" :steps 4
    :render (fn [step]
              (let [form '(+ 1 2)
                    rows [["(+ 1 2)" (str (apply + (rest form)))]
                          ["'(+ 1 2)" (pr-str form)]
                          ["(first '(+ 1 2))" (pr-str (first form))]
                          ["(map number? '(+ 1 2))" (pr-str (map number? form))]]]
                [:div.two
                 [:div [:table.repl (for [[i [in out]] (map-indexed vector rows) :when (<= i step)]
                                      [:tr [:td.in (highlight in)] [:td.arrow "⇒"] [:td.out out]])]]
                 [:div.aside
                  (case step
                    0 [:p "Evaluated, the list is a function call."]
                    1 [:p "Quoted, the same list is data."]
                    2 [:p "You can take it apart like any other list."]
                    [:p "Programs read and write programs as easily as they read JSON. A " [:b "macro"]
                     " is a function that does it at compile time."])]]))
    :notes "Clojure code is written in lists, vectors and maps, the same structures programs use for data. A macro runs at compile time. It receives code as data and returns code. Think of a Babel plugin written as one ordinary function."}

   {:section "Why Clojure" :title "Draw the problem" :steps 3
    :live (fn [step t] (when (= step 2) {"live-map" (scene nil t {})}))
    :render (fn [step]
              [:div.two
               [:div (code 'hills {:size :small})]
               [:div
                (case step
                  0 [:div.aside
                     [:p "A wind farm on hills in Andalusia, under a route soaring birds take between Europe and Africa."]
                     [:p "This drawing is source code. The macro " [:code "defmap"] " reads it."]]
                  1 [:div [:div.caption "what defmap returns"]
                     (code (str "{:turbine " (pr-str (:turbine hills))
                                "\n :flyway  " (pr-str (vec (take 6 (:flyway hills)))) "\n          …}") {:size :small})]
                  [:div [:div.caption "the same drawing in 3D: a turbine on every hilltop, birds along the flyway"]
                   [:div#live-map (scene nil (now-s) {})]])]])
    :notes "defmap reads the grid at compile time and returns a map of positions. The third step draws the same data in 3D, playing a day: the clock runs, birds pass along the flyway, thickest at midday when the thermals are strong. The projection is a dozen lines of pure functions. Live: move a turbine in core.cljs and save; the slide redraws and tomorrow's plan is recomputed. (n/random-map {:seed 7}) prints a new map to paste."}

   {:section "Why Clojure" :title "The model reads like the maths" :steps 3
    :render (fn [step]
              [:div.two
               [:div (code 'day-plan) (code 'wear {:size :small})]
               [:div
                (case step
                  0 [:div.aside
                     [:p "The plan: how hard each turbine runs in each hour of tomorrow, from 0 to 1. A Clojure map, so also a function: " [:code "(run [t h])"] "."]
                     [:p "The score: the power we give up, plus wear from changing speed."]
                     [:p "The rule: bird risk, closeness to the flyway × birds forecast × running, stays within a budget."]]
                  1 [:div [:div.caption "the formula, rendered from the source on the left"] (model-formula day-plan)]
                  [:div [:div.caption "what defmodel expands to"] (code (:expansion day-plan) {:size :small})])]])
    :notes "Operations research keeps two things apart: what a good answer is (the model) and how to find one (the solver). SQL works the same way: you declare the result, the engine finds it. The grammar has four words: minimize or maximize, subject-to, sum, for-all. The expansion shows that sum is reduce +. The formula is drawn from the model's source, so the two cannot disagree. The forecasts are illustrative: birds peak at midday, and Spanish power is cheapest at midday, when the solar farms flood the grid."}

   {:section "Why Clojure" :title "Effects at the edges"
    :render (fn [_]
              [:div.two
               [:div (code 'state {:size :small}) (code 'render! {:size :small :marks #{"html" "slide-view" "notes-view"}})
                [:p.caption "slide-view and notes-view are pure: state in, hiccup out"]]
               [:div.aside
                [:p "Every function you will see today is pure and works on immutable data."]
                [:p "This deck has two atoms, both at the edge: where you are, and how far each run has got."]
                [:p.muted "Haskell puts effects in the type. Clojure relies on convention and immutable data. Each choice has a cost."]]])
    :notes "Tell the Haskell people plainly: Clojure has no effect types. Purity is a habit, helped by immutable data. Because every state is a value, every run today can be replayed frame by frame."}

   ;; ---------------------------------------------------------------- The loop (3 min)
   {:section "The loop" :title "Reading maths as code"
    :render (fn [_]
              [:table.dict
               [:tr [:th "maths"] [:th "you write"]]
               [:tr [:td.m "Σ" [:sub "b ∈ birds"] " f(b)"] [:td (highlight "(reduce + (map f birds))")]]
               [:tr [:td.m "∀ b ∈ birds: ok(b)"] [:td (highlight "(every? ok birds)")]]
               [:tr [:td.m "max" [:sub "a"] " q(a)"] [:td (highlight "(apply max (map q actions))")]]
               [:tr [:td.m "argmin" [:sub "a"] " cost(a)"] [:td (highlight "(apply min-key cost actions)")]]
               [:tr [:td.m "x" [:sub "k+1"] " = step(x" [:sub "k"] ")"] [:td (highlight "(iterate step x0)")]]])
    :notes "Most of the frightening notation is folds. Σ is reduce +. ∀ is every?. 'max over a' is apply max. The last row is what this talk is about."}

   {:section "The loop" :title "One loop: state, step, converged?" :steps (count sqrt-run)
    :render (fn [step]
              [:div.two
               [:div (code 'fixed-point {:marks #{"iterate" "converged?"}}) (code 'newton-sqrt {:size :small})]
               [:div
                [:div.caption "(fixed-point (newton-sqrt 2) close-enough? 1.0)"]
                [:table.trace (for [[i x] (map-indexed vector sqrt-run) :when (<= i step)]
                                [:tr [:td.k (str "x" i)] [:td.v (.toFixed x 12)]])]
                (when (= step (dec (count sqrt-run)))
                  [:p.aside "Two states in a row agree, so the loop stops. √2 = 1.414213562373…"])]])
    :notes "This is the pattern of the whole talk. A state, a step from state to state, a test for done. iterate makes the lazy sequence of states. take-while stops it. The max-steps guard is one take, because iterate is lazy. Optimising, deciding and verifying are all this loop with different arguments."}

   ;; ---------------------------------------------------------------- Optimise (5 min)
   (plan-slide :crowd "A step is State → State"
               [:div (code 'gradient {:size :small}) (code 'descent-step {:size :small})]
               "plain gradient step, birds cost nothing"
               (fn [_] [:p.aside "Every turbine runs flat out, through the midday migration. That is what we asked for: the model charges nothing for birds."])
               "gradient nudges each number and watches the score move. That is the derivative, computed the way a developer would. descent-step takes a gradient and a rate and returns a function from state to state. Each column is an hour, each row a turbine; dark means running. Press Space to play. The fault is in the model: bird-risk is a constraint, and this solver reads only the score. The deck computes each run one state per frame, in the browser: the same states fixed-point would return.")

   (plan-slide :plain "Change the model, keep the solver"
               [:div (code 'penalize {:size :small}) [:div.caption "(penalize day-plan 100)"] (penalized-formula day-plan 100)]
               "the same descent-step, penalized model"
               (fn [{:keys [stops lost risk]}]
                 [:p.aside (str "Slows down: " (str/join ", " stops) ". Power given up: €" (fmt lost 0)
                                " of €" (fmt full-value 0) ". Bird risk " (fmt risk 1) " against a budget of " budget
                                ": a fine is a soft rule.")])
               "penalize takes a model and returns a model. Breaking a rule now costs weight × (how far it is broken)². The solver is unchanged; only its input changed. The plan slows the turbines nearest the flyway around midday, when birds peak and power is cheap anyway. Nobody wrote that rule. A soft rule can be broken a little when the fine is cheap: read the risk against the budget.")

   (plan-slide :momentum "Change the step: Step → Step"
               [:div (code 'with-momentum {:size :small :marks #{"step"}}) (code 'clamped {:size :small :marks #{"step"}})
                [:div.caption "(clamped (with-momentum step 0.9) 0 1)"]]
               "with-momentum 0.9, clamped"
               (fn [{:keys [steps]}] [:p.aside (str "Settled in " steps " steps.")])
               "with-momentum takes a step and returns a step. It asks the base step where to move, then adds 90% of the previous move. The velocity is stored in the state map, so the step stays pure. clamped is another decorator: it keeps every number between 0 and 1, because a turbine cannot run at minus 20%. Every run today is clamped; decorators compose.")

   (plan-slide :adam "Another decorator: Adam"
               [:div (code 'with-adam {:size :small :marks #{"step"}})]
               "(clamped (with-adam step 0.02) 0 1)"
               (fn [{:keys [steps]}] [:p.aside (str "Settled in " steps " steps.")])
               "Adam reuses the base step's gradient and scales each coordinate by the size of its recent gradients. The optimiser from machine learning has the same shape: Step → Step. Its variant AdamW would pull every number toward zero, which here means stopping turbines for no reason. Decorators compose. Whether the result makes sense is still your call.")

   (let [ks [:plain :momentum :adam]]
     {:section "Optimise" :title "Same loop, three steps" :runs ks
      :render (fn [_]
                (let [sums (into {} (for [k ks] [k (run-summary k)]))
                      ready (every? done? ks)
                      plans (map #(:plan (sums %)) ks)
                      gap (apply max (for [s slots] (- (apply max (map #(% s) plans)) (apply min (map #(% s) plans)))))]
                  [:div
                   [:div.race3
                    (for [k ks
                          :let [{:keys [steps settled lost done plan]} (sums k)]]
                      [:div
                       (schedule plan {:w 470})
                       [:div.r-label (:label (run-specs k))]
                       [:div.r-v (if done
                                   (str steps " steps" (when-not settled " (max-steps)") " · €" (fmt lost 0) " given up")
                                   (str "computing · state " steps))]])]
                   [:p.aside
                    (cond (not ready) "Still computing."
                          (< gap 0.15) (str "Same model, same start, same fixed-point. All three reach the same plan, to within "
                                            (fmt gap 2) " in every hour; they differ in how many steps it takes.")
                          :else "Same model, same start, same fixed-point, and they disagree: more than one valley, and the step decides which one you reach.")]]))
      :notes "These numbers are computed in this browser, now. Momentum is fastest here; Adam is slowest. Adam is not magic, and on a small smooth problem a tuned plain step does well. Between the runs one line changed, the decorator. The loop did not change. An LP solver would find this plan exactly, with a receipt; the wear term makes it a quadratic programme. We use the loop because the loop is the talk."})

   {:section "Optimise" :title "Tomorrow, played"
    :runs [:plain]
    :live (fn [_ t] {"played-3d" (scene (:plan (run-summary :plain)) t {:h 600})
                     "played-grid" (schedule (:plan (run-summary :plain)) {:w 640 :now (:hour (time-of-day t))})})
    :render (fn [_]
              (let [plan (:plan (run-summary :plain))
                    t (now-s)]
                [:div.two.even
                 [:div [:div#played-grid (schedule plan {:w 640 :now (:hour (time-of-day t))})]
                  [:p.aside "Red hubs are stopped. The plan stops the turbines nearest the flyway while the birds pass, and runs the rest."]]
                 [:div#played-3d (scene plan t {:h 600})]]))
    :notes "The plan from the last slides, played fast: a day in eighteen seconds. Watch the clock and the turbines near the flyway around midday. This is the plan made a day ahead. The bird that turns up anyway is the next section's problem: a controller that must stop in time."}

   ;; ---------------------------------------------------------------- Decide (5 min)
   {:section "Decide" :title "Ordering stock when demand is random" :steps 3
    :render (fn [step]
              [:div.two
               [:div (code 'restock {:size :small})]
               [:div
                (case step
                  0 [:div.aside
                     [:p "A hydrogen station holds up to 4 trailer loads. Each week we decide how many loads to order. Demand is random."]
                     [:p "Order too few and we lose sales. Order too many and we pay to store them."]]
                  1 [:div [:div.caption "the Bellman equation, rendered from the source"] (bellman-formula restock)]
                  [:div [:div.caption "what defbellman expands to"] (code (:expansion restock) {:size :small})])]])
    :notes "A policy is a function from state to action. Bellman's equation says what a stock level is worth: take the best order, then average over demand this week's money plus 0.9 × next week's value. defbellman writes the equation; the expansion is a reduce over outcomes. Prices and costs are made up."}

   {:section "Decide" :title "Bellman is Values → Values" :steps (count restock-run)
    :render (fn [step]
              (let [V (nth restock-run step)
                    policy (best-order restock V)
                    rs (residuals restock-run)
                    top (apply max (vals (peek restock-run)))
                    bar-h 260]
                [:div.two
                 [:div (code 'bellman {:size :small}) (code 'largest-change {:size :small})
                  [:div.caption "(fixed-point (bellman restock) #(< (largest-change %1 %2) 0.001) zeros)"]]
                 [:div
                  [:div.bars
                   (for [s (:states restock)]
                     [:div.bar-col
                      [:div.bar-v {:style {:height (str (js/Math.max 2 (* bar-h (/ (V s) top))) "px")}}]
                      [:div.bar-n (fmt (V s) 1)]
                      [:div.bar-k (str "stock " s)]
                      [:div.bar-p (str "order " (policy s))]])]
                  [:div.caption (str "update " step " of " (dec (count restock-run))
                                     (when (pos? step) (str " · largest change " (.toPrecision (rs (dec step)) 3))))]]]))
    :notes "Each update reads the old values and returns new ones. It is a function from value tables to value tables, and a Clojure map is itself a function. Under each bar is the best order at that stock level. Space plays. Shift+Right jumps 10."}

   {:section "Decide" :title "The loop stops, and finds a rule" :steps 2
    :render (fn [step]
              (let [V (peek restock-run)
                    policy (best-order restock V)
                    {:keys [updates residual bound]} restock-certificate
                    rs (residuals restock-run)
                    w 640 h 240
                    lg (fn [r] (/ (js/Math.log r) js/Math.LN10))
                    lo -4 hi (js/Math.ceil (lg (first rs)))
                    pt (fn [i r] [(* w (/ i (dec (count rs)))) (* h (/ (- hi (lg r)) (- hi lo)))])]
                [:div.two
                 [:div
                  [:table.kv
                   [:tr [:th "stock"] (for [s (:states restock)] [:td s])]
                   [:tr [:th "order"] (for [s (:states restock)] [:td (policy s)])]
                   [:tr [:th "stock + order"] (for [s (:states restock)] [:td (+ s (policy s))])]]
                  [:p.aside "Whenever stock is below 3, order up to 3. Operations research calls this a base-stock policy. Nobody wrote it; the loop found it."]]
                 [:div
                  (svg w (+ h 30)
                       [:line.axis {:x1 0 :y1 h :x2 w :y2 h}]
                       (let [[_ ey] (pt 0 epsilon)] [:line.eps {:x1 0 :y1 ey :x2 w :y2 ey}])
                       (polyline (map-indexed pt rs) {:class "curve"})
                       [:text.small {:x 4 :y (- (second (pt 0 epsilon)) 8)} "ε = 0.001"]
                       [:text.small {:x 4 :y (+ h 24)} "largest change per update (log scale)"])
                  (when (= step 1)
                    [:div.callout
                     [:p (str "Stopped after " updates " updates: largest change " (.toPrecision residual 3) " < ε = 0.001.")]
                     [:p "Theorem (Rainbow, open core): then every value is within "
                      [:b (str "change / (1 − 0.9) = " (fmt bound 4))] " of the best possible."]])]]))
    :notes "Each update shrinks the error by at least the discount, 0.9, so on a log scale the change falls in a straight line. The stopping rule is converged?. Rainbow has a theorem that turns 'the last change was below r' into 'every value is within r / (1 − γ) of the best'. The theorem is about exact rationals. This browser run uses floating point, and the kernel has not checked it."}

   ;; ---------------------------------------------------------------- Verify (5 min)
   {:section "Verify" :title "A controller that must stop for birds" :steps 2
    :render (fn [step]
              [:div.two
               [:div (code 'tick-controller {:size :small}) (code 'react-to-detections {:size :small :marks #{"controller"}})]
               [:div
                (if (zero? step)
                  [:div.aside
                   [:p "The same wind farm. The plan says when to slow down; this controller handles the bird that turns up anyway."]
                   [:p "Every 10 minutes a table decides RUN or STOP from season, wind, power price and bird detections."]
                   [:p.muted "Simplified from our RH2EDIN Reto 3 controller. Its bird passages are synthetic."]]
                  [:div (code 'with-shield {:size :small :marks #{"decide"}})
                   [:p.aside "A controller is a function, so a safety feature can be a decorator."]])]])
    :notes "Verification asks one question: can the system ever reach a bad state? The rule here: when the radar is confident a bird is there, the turbine stops within the same minute. Three versions, built by composition: decide on the tick only; also react to detections; add a shield for situations the table has never seen."}

   {:section "Verify" :title "Reachability is a fixed point too" :steps (* 3 4)
    :render (fn [step]
              (let [ci (quot step 4)
                    sub (mod step 4)
                    {:keys [id label] composed :code} (controllers ci)
                    run (reach-runs id)
                    k (frame-of sub 4 (count run))
                    states (nth run k)
                    unsafe (filter unanswered? states)
                    cells (group-by (juxt :minute :waiting) states)
                    cw 50 ch 26]
                [:div.two
                 [:div (code 'reachable {:size :small :marks #{"="}}) (code 'one-minute {:size :small})]
                 [:div
                  [:div.caption [:b label]]
                  (code composed {:size :small})
                  (svg (* 11 cw) (* 11 ch)
                       (for [m (range 10)] [:text.small {:x (+ cw (* m cw) 18) :y (- ch 8)} m])
                       (for [[r w] (map-indexed vector (cons nil (range 1 10)))]
                         [:text.small {:x 2 :y (+ (* (inc r) ch) 20)} (if w (str w "′") "ok")])
                       (for [m (range 10) [r w] (map-indexed vector (cons nil (range 1 10)))
                             :let [hit (cells [m w])]]
                         [:rect {:x (+ cw (* m cw)) :y (+ ch (* r ch)) :width (- cw 4) :height (- ch 4)
                                 :class (cond (nil? hit) "cell-empty" w "cell-bad" :else "cell-ok")}]))
                  [:div.caption (str "round " k " of " (dec (count run)) " · " (count states) " states · "
                                     (count unsafe) " break the rule")
                   (when (= sub 3) (if (seq unsafe) [:span.bad " · rule broken"] [:span.ok " · set closed, rule holds"]))]
                  [:div.legend "columns: minute in the slot · rows: minutes a confident detection has waited while running"]]]))
    :notes "The step adds every state reachable in one more minute. converged? is plain =: the set stopped growing. There are finitely many states, so it must stop. Right arrow walks the rounds for each controller. Tick-only shows a red diagonal: a bird can wait 9 minutes. Reacting fixes known situations; unseen ones never stop. With the shield the set closes with nothing red. This is an exhaustive check by our code, not a kernel proof."}

   {:section "Verify" :title "The counterexample is a path you can read" :steps 2
    :render (fn [step]
              (let [id (if (zero? step) :tick-only :reacting)
                    path (counterexamples id)]
                [:div
                 [:div.caption [:b (:label (first (filter #(= id (:id %)) controllers)))] " · the longest path found"]
                 [:div.timeline
                  (for [s path]
                    [:div.tl {:class (if (:waiting s) "tl-bad" "tl-ok")}
                     [:div.tl-m (str "min " (:minute s))]
                     [:div.tl-c (name (or (:ctx s) :seen))]
                     [:div.tl-d (case (:det s) 2 "confident bird" 1 "maybe bird" "—")]
                     [:div.tl-a (name (:action s))]])]
                 [:p.aside (if (zero? step)
                             "A confident detection just after a tick waits for the next tick: 9 minutes of RUN."
                             "Reacting fixes known situations. In a situation the table has never seen, it never stops. The shield is for that.")]]))
    :notes "The search returns a path, so the failure is a story you can debug. Tick-only: detection at minute 0, decision at minute 10. Reacting without the shield: the unseen situation has no row in the table, so its stop probability is 0."}

   {:section "Verify" :title "Too many states to list: search for the worst" :steps (count (:tick-only falsify-runs))
    :render (fn [step]
              (let [run (:tick-only falsify-runs)
                    s (nth run step)
                    w 600 h 260 pad 24
                    px (fn [t] (+ pad (* w (/ t 600))))
                    py (fn [score] (+ pad (* h (/ (- 60 score) 660))))]
                [:div.two
                 [:div (code 'bird-rule) (code 'within {:size :small})
                  [:p.aside "The rule returns seconds to spare. Negative means it was broken."]]
                 [:div
                  (svg (+ w (* 2 pad)) (+ h 70)
                       [:line.axis {:x1 pad :y1 (py 0) :x2 (+ pad w) :y2 (py 0)}]
                       [:text.small {:x (+ pad 4) :y (- (py 0) 6)} "0 s to spare"]
                       (polyline (for [t (range 1 600 6)] [(px t) (py (bird-rule (slot-trace (:controller (first controllers)) t)))])
                                 {:class "curve-muted"})
                       (for [p (take (inc step) run)] [:circle.probe {:cx (px (:t p)) :cy (py (:score p)) :r 6}])
                       [:circle.probe-now {:cx (px (:t s)) :cy (py (:score s)) :r 10}]
                       [:text.small {:x pad :y (+ h 60)} "detection time in the slot, 0–600 s → · higher: more time to spare"])
                  [:div.caption (str "try t = " (fmt (:t s) 1) " s · spare " (fmt (:score s) 1) " s · step size " (fmt (:delta s) 1) " s")]
                  (when (= step (dec (count run)))
                    [:p.aside (str "Worst case: detection " (fmt (:t s) 1) " s after a tick, answered "
                                   (fmt (- 60 (:score s)) 0) " s later. On the shielded controller the same search finds "
                                   (fmt (:score (peek (:shielded falsify-runs))) 0) " s to spare everywhere. Finding nothing proves nothing.")])]]))
    :notes "Falsification: aim an optimiser at the rule. always, implies and within are higher-order functions over a trace, and the rule returns seconds to spare. The search is fixed-point again: try earlier and later, keep the worse case, otherwise halve the step. It stops when the step is under half a second. Finding nothing is not a proof. Rainbow exists for that gap."}

   {:section "Verify" :title "Three ways the loop goes wrong"
    :render (fn [_]
              [:ol.gotchas
               [:li [:b "It may never settle."] " Set the rate too high and the plan oscillates forever. The guard is "
                [:code "max-steps"] ": one " [:code "take"] ", because " [:code "iterate"] " is lazy."]
               [:li [:b "Settled is not correct."] " The first run converged perfectly: every turbine flat out through the migration."]
               [:li [:b [:code "converged?"] " is part of the specification."] " Use exact " [:code "="]
                " for sets and plans, a tolerance for numbers. It is an argument you can read, not a constant buried in a library."]])
    :notes "Three points a developer can defend without any maths."}

   ;; ---------------------------------------------------------------- Rainbow (9 min)
   {:section "Rainbow" :title "Who checks the checker?"
    :render (fn [_]
              [:div.two
               [:div (code "{:name  'nnrat/add-zero-right\n :type  (forall [[value nnrat]]\n          (equivalent (add value zero) value))\n :value (lam [[value nnrat]] ...)}")
                [:p.caption "a theorem in Rainbow is a map: :type is the statement, :value the proof"]]
               [:div.aside
                [:p "Rainbow is a theorem prover written in Clojure. Proofs are plain EDN, immutable and addressed by content hash."]
                [:p "A kernel of about 1,200 lines, in four files, decides. Search, tactics and generators only propose."]
                [:p "What we have checked by testing so far, Rainbow can check by proof, where it has the theorem."]]])
    :notes "A theorem is data. The statement is a type, the proof is a value of that type, and a small kernel type-checks it. Lean people know this already. For everyone else: the kernel refuses a fake proof the way a compiler refuses a badly typed program."}

   {:section "Rainbow" :title "Trailers on the Bay of Algeciras" :steps 2
    :render (fn [step]
              [:div.two
               [:div (if (zero? step) (code 'trailers) (code 'linear-rows {:size :small}))]
               [:div [:div.caption "drawn from the source"] (model-formula trailers)
                (if (zero? step)
                  [:p.aside "Two hydrogen sources, three stations, loads per week. From our RH2EDIN Reto 2 entry."]
                  [:p.aside "A linear function is fixed by what it does to each one-load plan. Call it, and subtract."])]])
    :notes "Same defmodel grammar, real data. Linear means the score and every rule are sums of quantity × number. That restriction makes solvers fast and receipts possible. linear-rows reads the coefficients by calling the model on one-load plans."}

   {:section "Rainbow" :title "The solver is a fixed point" :steps (count solve-run)
    :render (fn [step]
              (let [t (nth solve-run step)
                    vars (:variables lp) rows (:rows lp)
                    nm (fn [i] (if (< i (count rows)) (row-label (rows i)) (label (vars (- i (count rows))))))]
                [:div.two
                 [:div (code 'pivot {:size :small})
                  [:div.caption "(fixed-point pivot = (tableau lp))"]]
                 [:div
                  [:table.pivots
                   [:tr [:th "pivot"] [:th "best total so far"] [:th "what changed"]]
                   (for [[i s] (map-indexed vector solve-run) :when (<= i step)]
                     [:tr [:td.k i]
                      [:td.v (fmt (:value s) 2)]
                      [:td (cond (zero? i) "start: every price 0"
                                 (identical? s (nth solve-run (dec i))) [:b "nothing: fixed point"]
                                 :else (str "now priced: " (nm (:entered s))))]])]
                  (when (= step (dec (count solve-run)))
                    [:p.aside "A pivot that changes nothing is a fixed point. converged? is =."])]]))
    :notes "Skip the simplex method. It is a step on a table of numbers, and it stops when a step returns its input unchanged. It works on prices, because all-zero prices are a valid place to start. The numbers are the best total trailer-km found so far."}

   {:section "Rainbow" :title "The plan comes with a receipt" :steps 6
    :render (fn [step]
              (let [{:keys [plan prices]} answer
                    rows (:rows lp)
                    terms (sort-by #(neg? (:rhs (first %)))
                                   (for [[r y] (map vector rows prices) :when (pos? y)] [r y]))]
                [:div.two
                 [:div
                  [:table.kv
                   [:tr [:th "route"] [:th "km per load"] [:th "loads"]]
                   (for [[r x] (map vector routes plan)]
                     [:tr {:class (when (pos? x) "hot")} [:td (label r)] [:td (km r)] [:td x]])]
                  [:p.caption (str "total: " (:plan-costs receipt) " trailer-km")]]
                 [:div
                  (when (>= step 1)
                    [:div [:div.caption "prices from the solver: what one load is worth"]
                     [:table.kv (for [[r y] terms] [:tr [:td (row-label r)] [:td (if (neg? (:rhs r)) (str y " off per load, at most " (abs (:rhs r)) " loads") y)]])]])
                  (when (>= step 2)
                    [:div.receipt
                     (for [[i [r y]] (map-indexed vector terms) :when (<= i (- step 2))]
                       [:span (when (pos? i) (if (neg? (:rhs r)) " − " " + ")) (str (abs (:rhs r)) " × " y)])
                     (when (>= step 5) [:span " = " [:b (:no-plan-costs-less-than receipt)]])])
                  (when (>= step 5)
                    [:p.aside "Each load delivered to a station is worth its price. The electrolyser takes 8 off each load, for at most 8 loads. No route costs less than that. So every valid plan costs at least 127. Ours costs 127."])]]))
    :notes "This argument must be airtight, and it does not need the word duality. Each load delivered is worth its station's price: port 8, Sotogrande 19, Tarifa 33. The electrolyser takes 8 off each load, for at most 8 loads. No route costs less than its station price minus that discount. So any plan costs at least 15×8 + 2×19 + 1×33 − 8×8 = 127. Ours costs 127. Finding the plan is hard. Checking it is arithmetic, like checking a sudoku."}

   {:section "Rainbow" :title "You check the receipt, not the solver" :steps 2
    :render (fn [step]
              (let [r (if (zero? step) receipt tampered)]
                [:div.two
                 [:div (code 'check-receipt {:size :small})]
                 [:div
                  [:div.caption (if (zero? step) "the solver's receipt" "the same receipt, Tarifa's price raised by 1")]
                  (kv-table [["plan is valid" (check-mark (:plan-is-valid r))]
                             ["prices are fair" (check-mark (:prices-are-fair r))]
                             ["plan costs" (:plan-costs r)]
                             ["no plan costs less than" (:no-plan-costs-less-than r)]])
                  (if (receipt-ok? r)
                    [:p.ok-big "accepted: 127 is the minimum"]
                    [:p.bad-big "refused: one price is too high"])]]))
    :notes "The solver returns the prices. You need not trust how it found them, because this function checks them. Linear problems always have a receipt. A receipt that claims a higher bound must overcharge some route, and the check catches it."}

   {:section "Rainbow" :title "A theorem is a function type"
    :render (fn [_]
              [:div.two
               [:div
                [:pre.sig "lp-optimality-certificate :\n  (plan x, prices y,\n   x is valid, y is fair,\n   x ≥ 0, y ≥ 0,\n   cost(x) = bound(y),\n   any plan z, z is valid, z ≥ 0)\n  → cost(z) ≥ cost(x)"]
                [:p.caption "readable rendering of optimization/signed-lp-optimality-certificate-on (Rainbow open core, math/rational/signed/lp_duality_on.clj); proved once, for every LP"]]
               [:div
                [:div.caption "the 127 plan, checked by the kernel (recorded for RH2EDIN)"]
                (kv-table [["theorem" (:theorem kernel-verdict)]
                           ["status" (name (:status kernel-verdict))]
                           ["axioms" "#{ } — none"]
                           ["kernel time" (str (fmt (:seconds kernel-verdict) 0) " s")]
                           ["id" [:span.hash (subs (:hash kernel-verdict) 0 23) "…"]]])
                [:p.aside "You read the statement. The kernel reads the proof: generated data, about 620 distinct pieces from 26 lemmas."]]])
    :notes "Curry–Howard for developers: a theorem is a function type, and a proof is a function of that type. Hand it the evidence and it returns the conclusion. The kernel certified the 127 plan using no axioms. If asked about proof size: the bundle file is large because it is stored without sharing. The proof itself has about 620 distinct pieces from 26 lemmas. Compact storage is in progress."}

   {:section "Rainbow" :title "converged? with a guarantee"
    :render (fn [_]
              (let [{:keys [updates residual bound epsilon-bound]} restock-certificate]
                [:div.two
                 [:div
                  [:pre.sig "stopping-certificate :\n  (values V, discount γ < 1, threshold ε,\n   one more Bellman update moves V\n   by at most ε)\n  → V is within ε / (1 − γ)\n    of the optimal values"]
                  [:p.caption "readable rendering of value/nnrat-optimality-fixed-point-stopping-certificate (math/finite/bellman_residual_certificate.clj; signed variant in bellman_signed_residual_certificate.clj): for all inputs, axiom-free"]]
                 [:div
                  [:div.caption "applied to the restocking run"]
                  (kv-table [["updates" updates]
                             ["last change" (.toPrecision residual 3)]
                             ["ε" epsilon]
                             ["guarantee" (str "within " (fmt bound 4) " (ε rule: " (fmt epsilon-bound 3) ")")]])
                  [:p.muted "The theorem is about exact rationals. This run uses floating point. Having the kernel check it is the next step."]]]))
    :notes "Here the loop meets the proof. converged? plus this theorem gives a distance to the best answer, where before we only knew the loop had stopped. Be exact: Rainbow proves the theorem for all inputs. It has not checked this floating-point run."}

   {:section "Rainbow" :title "A wrong proof is refused"
    :render (fn [_]
              [:div.two
               [:div (code "momentum-recurrence : v′ = β·v − α·∇f(x),  x′ = x + v′\n\n;; offered proof:\n0")]
               [:div
                (kv-table [["attempt" (:attempt kernel-rejection)]
                           ["kernel" [:span.bad (name (:status kernel-rejection))]]
                           ["recorded" (:checked-at kernel-rejection)]])
                [:p.aside "A theorem's name earns nothing. The term must have the type."]]])
    :notes "This is recorded kernel output; no kernel runs in the browser. Replace a proof with the number zero and the kernel rejects it, as a type checker rejects a value of the wrong type."}

   {:section "Rainbow" :title "What is proved, and what is not"
    :render (fn [_]
              [:div
               [:table.levels
                [:tr [:th "today's example"] [:th "status"]]
                [:tr [:td "trailer plan (127)"] [:td [:span.ok "kernel-checked instance"] " of a theorem proved for every LP"]]
                [:tr [:td "restocking stop rule"] [:td [:span.ok "theorem for all inputs"] "; this run not yet checked"]]
                [:tr [:td "gradient, momentum, Adam"] [:td [:span.warn "update formulas and worked examples only"] "; no convergence proof"]]
                [:tr [:td "bird controller"] [:td [:span.warn "exhaustive check by our code"] "; no kernel proof"]]]
               [:p.aside "Rainbow labels every theorem " [:b "for all inputs"] " or " [:b "worked instance"]
                ", and every result with a maturity level set by its weakest link. A theorem about a model is not a guarantee about the program."]])
    :notes "Say this plainly. Adam does not converge in general (Reddi et al., 2018). Tests never count as a link. Levels: 3 is a theorem about a model; 5 is a program proved to implement its specification; 6 is every link machine-checked, down to a stated trusted base."}

   {:section "Rainbow" :title "Open core and Pro"
    :render (fn [_]
              [:div.two.even
               [:div
                [:h3 "Rainbow (open source)"]
                (bullets "Apache-2.0, v1.0.0 (13 Oct 2026), github.com/flexiana/rainbow"
                         "About 5,000 kernel-checked declarations: rationals, sums, order, convexity and duality, matrices, Markov chains"
                         "The decision step of each of the 222 algorithms in the book's catalogue"
                         "Levels 1–3: the kernel and the mathematics")]
               [:div
                [:h3 "Rainbow Pro"]
                (bullets "Generates Clojure from proved specifications"
                         "A value-iteration program proved equal to the Bellman iterate, for every size"
                         "1,000 program families with certificate checkers"
                         "The only unproved part is the printer, and it refuses any form it does not know"
                         "Level 5 today. Level 6 is the goal.")]])
    :notes "Pro is a library on top of the open core. The open-source kernel checks everything Pro proves. The emit namespace's docstring lists the two things it assumes; quote it if asked."}

   {:section "Rainbow" :title "What I am building"
    :render (fn [_]
              [:div.title-slide
               [:div.goal "A free, open-source theorem prover for operations research,"]
               [:div.goal "and programs generated together with their proofs."]
               [:div.sub "Write the function. Run it. State what it must do. Prove that it does."]])
    :notes "Two lines, then stop. Ask for help: a theorem whose statement says less than its name promises is a bug. Report it."}

   ;; ---------------------------------------------------------------- The book (2 min)
   {:section "The book" :title "Applied Higher-Order Functions"
    :render (fn [_]
              [:div.two
               [:div
                [:div.book "Applied" [:br] "Higher-Order" [:br] "Functions"]
                [:p.caption "A Functional Architecture of Optimization, Decision Making, and Validation"]]
               [:div
                (bullets "Three parts: optimisation, decision making, validation. This talk follows them."
                         "A catalogue of about 480 algorithms, each annotated with the higher-order functions it uses"
                         "Every listing runs, against the companion library orhof (MIT)"
                         "Book text CC BY-SA 4.0")
                [:p.goal "flexiana.com/book"]]])
    :notes "The talk follows the book's three parts. It is at flexiana.com/book."}

   ;; ---------------------------------------------------------------- Questions
   {:section "Questions" :title "Thank you"
    :render (fn [_]
              [:div.title-slide
               [:div.promise "state · step · converged?"]
               [:div.sub "Find the state. Write the step. Decide when to stop."]
               [:div.byline "Jiri Knesl · flexiana.com/book · github.com/flexiana/rainbow"]])
    :notes "Questions. Likely ones: Why not Haskell? (Types would help. I chose Clojure for code as data and the REPL.) Where do the prices come from? (The solver. You check them.) Does Adam always converge? (No.) Why are the proofs so large? (Stored without sharing; about 620 distinct pieces.)"}])

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
     (when-not (#{"Hello" "Questions"} section) [:h2.title title])
     (when (and (#{"Hello" "Questions"} section) (pos? slide) (< slide (dec (count slides)))) [:h2.title title])
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
