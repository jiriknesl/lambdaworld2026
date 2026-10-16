(ns talk.notation
  "The talk's notation. Each macro turns something written for people into ordinary
  Clojure data and functions, and keeps the original form so a slide can show both.

    src        records the exact source text of a definition, for slides
    defmap     draw a problem as a grid of glyphs
    defmodel   write an optimisation model the way the maths is written
    defbellman write a decision problem as its Bellman equation"
  (:require [clojure.string :as str]
            #?(:clj [clojure.pprint :as pp])
            #?(:clj [clojure.java.io :as io]))
  #?(:cljs (:require-macros [talk.notation])))

;; ---------------------------------------------------------------------------
;; Runtime: the registry of source texts, and helpers used by the expansions

(defonce sources (atom {}))

(defn source
  "The exact source text of a definition wrapped in `src`."
  [sym]
  (get @sources sym (str ";; no source recorded for " sym)))

(defn pairs
  "Every unordered pair of elements, by position: [[a b] ...]."
  [xs]
  (let [v (vec xs)]
    (for [i (range (count v)) j (range (inc i) (count v))] [(v i) (v j)])))

(defn- mulberry32
  "A tiny seeded random generator, so the same seed draws the same map."
  [seed]
  (let [s (atom (bit-or seed 0))]
    (fn []
      (swap! s #(bit-or (+ % 0x6D2B79F5) 0))
      (let [t @s
            t (#?(:clj unchecked-multiply-int :cljs js/Math.imul) (bit-xor t (unsigned-bit-shift-right t 15)) (bit-or t 1))
            t (bit-xor t (+ t (#?(:clj unchecked-multiply-int :cljs js/Math.imul) (bit-xor t (unsigned-bit-shift-right t 7)) (bit-or t 61))))]
        (/ (double (unsigned-bit-shift-right (bit-xor t (unsigned-bit-shift-right t 14)) 0)) 4294967296.0)))))

(defn random-map
  "Source text of a new `defmap` form: turbines on hills, and a flyway that crosses from
  west to east, two cells per row. Paste it into core.cljs."
  [{:keys [name w h turbines seed] :or {name 'hills w 12 h 7 turbines 6 seed 1}}]
  (let [rand (mulberry32 seed)
        pick (fn [xs] (nth xs (int (* (rand) (count xs)))))
        flyway (set (loop [y (pick (range h)) x 0 out []]
                      (if (>= x w)
                        out
                        (recur (max 0 (min (dec h) (+ y (pick [-1 0 1])))) (+ x 2)
                               (into out (for [dx [0 1] :when (< (+ x dx) w)] [(+ x dx) y]))))))
        free (vec (for [x (range w) y (range h) :when (not (flyway [x y]))] [x y]))
        farm (set (take turbines (distinct (repeatedly #(pick free)))))
        glyph (fn [c] (cond (farm c) "🌀" (flyway c) "🐦" :else "・"))]
    (str "(defmap " name "\n  {・ :empty, 🌀 :turbine, 🐦 :flyway}\n"
         (str/join "\n" (for [y (range h)]
                          (str "  [" (str/join " " (for [x (range w)] (glyph [x y]))) "]")))
         ")")))

(defn violation
  "How far one constraint gap is from satisfied: 0 when it holds."
  [rel gap]
  (case rel
    :<= (max 0 gap)
    :>= (max 0 (- gap))
    := (abs gap)))

(defn violations
  "Every row of every constraint of a model: [{:constraint name :at binding :gap g :violation v} ...]."
  [model plan]
  (vec (for [{:keys [name rel gaps]} (:constraints model)
             {:keys [at gap]} (gaps plan)]
         {:constraint name :at at :rel rel :gap gap :violation (violation rel gap)})))

(defn feasible? [model plan tolerance]
  (every? #(<= (:violation %) tolerance) (violations model plan)))

;; ---------------------------------------------------------------------------
;; Macros (compiled on the JVM side)

#?(:clj
   (do
     (defn- current-file []
       (some-> (find-ns 'cljs.analyzer) (ns-resolve '*cljs-file*) deref str))

     (defn- read-text [file]
       (when file
         (let [r (or (io/resource file) (let [f (io/file file)] (when (.exists f) f)))]
           (when r (slurp r)))))

     (defn- dedent [lines]
       (let [indents (keep #(when-not (str/blank? %) (count (re-find #"^ *" %))) (rest lines))
             cut (if (seq indents) (apply min indents) 0)
             cut (min cut (count (re-find #"^ *" (or (second lines) ""))))]
         (str/join "\n" (cons (first lines) (map #(if (>= (count %) cut) (subs % cut) (str/triml %)) (rest lines))))))

     (defn- form-text
       "The form's text as typed, cut from the source file by reader metadata."
       [form]
       (let [{:keys [line column end-line end-column]} (meta form)
             text (read-text (current-file))]
         (if (and text line column end-line end-column)
           (let [lines (str/split-lines text)
                 picked (subvec (vec lines) (dec line) end-line)
                 last-i (dec (count picked))
                 picked (-> picked
                            (update last-i #(subs % 0 (min (count %) (dec end-column))))
                            (update 0 #(subs % (min (count %) (dec column)))))
                 ;; re-indent continuation lines relative to the opening column
                 picked (vec (cons (first picked)
                                   (map #(if (>= (count (re-find #"^ *" %)) (dec column)) (subs % (dec column)) (str/triml %))
                                        (rest picked))))]
             (str/join "\n" picked))
           (binding [pp/*print-right-margin* 72]
             (with-out-str (pp/with-pprint-dispatch pp/code-dispatch (pp/pprint form)))))))

     (defn- flat
       "A form on one line, as it would be typed."
       [x]
       (cond (and (seq? x) (= 'quote (first x))) (str "'" (flat (second x)))
             (seq? x) (str "(" (str/join " " (map flat x)) ")")
             (vector? x) (str "[" (str/join " " (map flat x)) "]")
             (map? x) (str "{" (str/join ", " (map (fn [[k v]] (str (flat k) " " (flat v))) x)) "}")
             :else (pr-str x)))

     (defn- layout
       "A form on one line when it fits in width; otherwise its head and first argument on
       the first line and the rest below, indented two spaces, as people write Clojure."
       [x col width]
       (let [f (flat x)
             pad (fn [n] (apply str (repeat n " ")))]
         (cond
           (or (<= (+ col (count f)) width) (not (coll? x)) (empty? x)
               (and (seq? x) (= 'quote (first x))))
           f
           (map? x)
           (str "{" (str/join (str ",\n" (pad (inc col)))
                              (for [[k v] x :let [ks (flat k)]]
                                (if (<= (+ col 2 (count ks) (count (flat v))) width)
                                  (str ks " " (flat v))
                                  (str ks "\n" (pad (+ col 2)) (layout v (+ col 2) width)))))
                "}")
           (vector? x)
           (str "[" (str/join (str "\n" (pad (inc col))) (map #(layout % (inc col) width) x)) "]")
           :else
           (let [[h & args] x
                 hs (flat h)]
             (str "(" hs " " (layout (first args) (+ col 2 (count hs)) width)
                  (apply str (for [a (rest args)] (str "\n" (pad (+ col 2)) (layout a (+ col 2) width))))
                  ")")))))

     (defn- pretty [form] (layout form 0 48))

     (defmacro src
       "Define as usual, and record the definition's exact source text for slides."
       [form]
       (let [name (second form)
             text (form-text form)]
         `(do ~form
              (swap! sources assoc '~name ~text)
              (var ~name))))

     ;; ---- defmap ---------------------------------------------------------

     (defmacro defmap
       "(defmap name {glyph role, ...} [glyph ...] ...): rows of glyphs become positions per role."
       [name legend & rows]
       (let [widths (set (map count rows))
             _ (when (not= 1 (count widths))
                 (throw (ex-info (str "defmap " name ": rows have different lengths " (mapv count rows)) {})))
             cells (for [[y row] (map-indexed vector rows) [x glyph] (map-indexed vector row)]
                     (let [role (get legend glyph ::unknown)]
                       (when (= role ::unknown)
                         (throw (ex-info (str "defmap " name ": unknown glyph " glyph " at column " x ", row " y
                                              "; the legend has " (str/join " " (keys legend))) {})))
                       [role [x y]]))
             grouped (reduce (fn [m [role pos]] (if (= role :empty) m (update m role (fnil conj []) pos))) {} cells)
             data (assoc grouped :size [(first widths) (count rows)])]
         `(do (def ~name '~data)
              (swap! sources assoc '~name ~(form-text &form))
              (var ~name))))

     ;; ---- defmodel -------------------------------------------------------

     (defn- compile-expr
       "sum → reduce + over for; everything else is ordinary Clojure."
       [e]
       (cond
         (and (seq? e) (= 'sum (first e)))
         (let [[_ bindings body] e]
           (list 'reduce '+ 0 (list 'for bindings (compile-expr body))))
         (seq? e) (apply list (map compile-expr e))
         (vector? e) (mapv compile-expr e)
         :else e))

     (def ^:private relations {'<= :<=, '>= :>=, '= :=})

     (defn- binding-label [bindings]
       (let [names (take-nth 2 (remove #{:when :let :while} bindings))]
         (if (= 1 (count names)) (first names) (vec names))))

     (defn- compile-constraint
       "A constraint becomes {:rel r :gaps (fn [plan] [{:at binding :gap (- lhs rhs)} ...])}."
       [param [cname c]]
       (let [[forall? bindings cmp] (if (and (seq? c) (= 'for-all (first c))) [true (second c) (nth c 2)] [false nil c])
             [rel lhs rhs] cmp
             r (or (relations rel) (throw (ex-info (str "defmodel: constraint " cname " must be <=, >= or =, got " rel) {})))
             gap (list '- (compile-expr lhs) (compile-expr rhs))]
         {:name (list 'quote cname)
          :rel r
          :gaps (list 'fn [param]
                      (if forall?
                        (list 'vec (list 'for bindings {:at (binding-label bindings) :gap gap}))
                        [{:at nil :gap gap}]))}))

     (defmacro defmodel
       "(defmodel name [plan]
          (minimize e) | (maximize e)
          (subject-to [constraint-name constraint] ...))
        Grammar: sum, for-all, and <= >= = in constraints. Expands to plain functions over the plan."
       [name [param] [sense objective] & [[_ & constraints]]]
       (let [sense-k (case sense minimize :minimize maximize :maximize
                           (throw (ex-info (str "defmodel " name ": expected minimize or maximize, got " sense) {})))
             expansion {:objective (list 'fn [param] (compile-expr objective))
                        :constraints (mapv #(compile-constraint param %) constraints)}]
         `(do (def ~name
                (merge ~expansion
                       {:name '~name :sense ~sense-k :param '~param
                        :source '~&form
                        :expansion ~(pretty (assoc expansion :sense sense-k))}))
              (swap! sources assoc '~name ~(form-text &form))
              (var ~name))))

     ;; ---- defbellman -----------------------------------------------------

     (defmacro defbellman
       "(defbellman name
          [state states] [action (actions-of state)] [outcome {value probability}]
          :reward e :next e :discount g)
        Expands to the Q-function Q(V, state, action) = Σ p · (reward + g · V(next))."
       [name [s states] [a actions] [w distribution] & {:keys [reward next discount]}]
       (let [q (list 'fn ['V s a]
                     (list 'reduce '+ 0
                           (list 'for [[w 'probability] distribution]
                                 (list '* 'probability (list '+ reward (list '* discount (list 'V next)))))))]
         `(do (def ~name
                {:states (vec ~states)
                 :actions (fn [~s] (vec ~actions))
                 :q ~q
                 :outcomes ~distribution
                 :discount ~discount
                 :source '~&form
                 :expansion ~(pretty q)})
              (swap! sources assoc '~name ~(form-text &form))
              (var ~name))))))
