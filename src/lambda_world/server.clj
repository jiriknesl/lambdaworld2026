(ns lambda-world.server
  "Offline presentation server. Evaluates trusted local Clojure on loopback only."
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.repl :as repl]
            [clojure.string :as str]
            [lambda-world.hof]
            [lambda-world.flock]
            [lambda-world.bellman]
            [lambda-world.shapley]
            [lambda-world.parallel]
            [lambda-world.lessons :as lessons]
            [lambda-world.experiments :as experiments]
            [lambda-world.rescue]
            [lambda-world.rainbow])
  (:import [com.sun.net.httpserver HttpServer HttpHandler]
           [java.net InetSocketAddress]
           [java.io PushbackReader StringReader]
           [java.util UUID]
           [java.util.concurrent Executors]))

(def scenes
  [{:id "flock" :station 0 :chapter "01 / THE SEARCH"
    :title "Person overboard. Launch the fleet."
    :subtitle "The flock idea becomes a rescue formation. The optimizer still receives a function."
    :signature "Objective → (State → State)"
    :note "Each boat is a pair of decision variables. This first objective pulls every boat toward the last reported position."
    :file "flock.clj"}
   {:id "constraints" :station 0 :chapter "02 / THE TRANSFORMATION"
    :title "Change the landscape."
    :subtitle "A constraint becomes a function that transforms a function."
    :signature "Objective → Objective"
    :note "Boat separation is a soft penalty. The optimizer still receives an ordinary scalar objective."
    :file "constraints.clj"}
   {:id "momentum" :station 0 :chapter "03 / THE COMPOSITION"
    :title "Give a function momentum."
    :subtitle "The velocity lives in the state. The history stays inspectable."
    :signature "(State → State) → (State → State)"
    :note "The wrapper reuses the base step's displacement. Momentum is tunable, not a promise of acceleration."
    :file "momentum.clj"}
   {:id "parallel" :station 0 :chapter "04 / THE EXECUTION"
    :title "Even execution is an argument."
    :subtitle "Independent objectives. Shared immutable inputs. A different mapping function."
    :signature "MappingStrategy → Objective → Population → Scores"
    :note "Actual CPU evaluation with fully realized results. Timings include coordination; speedup depends on workload and hardware."
    :file "parallel.clj"}
   {:id "bellman" :station 1 :chapter "05 / THE FUNCTION SPACE"
    :title "The next state is a function."
    :subtitle "Bellman improves our estimate of the future, one function at a time."
    :signature "Model → (ValueFunction → ValueFunction)"
    :note "First assume a confirmed location: steer one boat through currents and reefs. Rewards are paid on entry."
    :file "bellman.clj"}
   {:id "pomdp" :station 1 :chapter "06 / THE UNKNOWN"
    :title "The person is drifting. Our belief moves."
    :subtitle "A search that finds nothing still changes what we know."
    :signature "(Transition, Sensor) → (Belief × Observation → Belief)"
    :note "Exact-rational toy POMDP. Three search sectors; 80% detection; no false positives. Search actions abstract away travel."
    :file "pomdp.clj"}
   {:id "shapley" :station 2 :chapter "07 / THE ATTRIBUTION"
    :title "Who made the difference?"
    :subtitle "Three search boats overlap. Attribute the fleet’s expected detection coverage."
    :signature "(Coalition → Value) → Contributions"
    :note "Exact Shapley values: all six arrival orders, with marginal contributions averaged equally."
    :file "shapley.clj"}
   {:id "rainbow" :station 1 :chapter "08 / THE EVIDENCE"
    :title "From a function to a claim we can check."
    :subtitle "Rainbow checks proof terms. Which claims have actually been proved?"
    :signature "Proof term × Proposition → Kernel judgment"
    :note "Recorded kernel checks of exact mathematical terms. The rescue simulation itself is not formally verified."
    :file "rainbow.clj"}
   {:id "overview" :station 3 :chapter "09 / THE REVEAL"
    :title "One rescue. One vocabulary."
    :subtitle "The same higher-order patterns organize search, decisions and attribution."
    :signature "Functions are the architecture."
    :note "Applied Higher Order Functions · Jiri Knesl / Rainbow: proof terms as Clojure data, checked by a kernel."
    :file "overview.clj"}])

(def presentation (edn/read-string (slurp (io/resource "presentation.edn"))))
(def public-files #{"index.html" "app.js" "style.css" "sw.js" "ocean.js"
                    "fonts.css" "font-license.txt" "contingency.html" "stage.js" "experiments.js" "presenter.html" "presenter.js" "presenter.css"})

(defn json-string [s]
  (str "\""
       (apply str (map (fn [c]
                         (case c
                           \" "\\\"" \\ "\\\\" \newline "\\n" \return "\\r" \tab "\\t"
                           (if (< (int c) 32) (format "\\u%04x" (int c)) (str c)))) s))
       "\""))

(declare json)
(defn json [x]
  (cond
    (nil? x) "null"
    (string? x) (json-string x)
    (keyword? x) (json-string (name x))
    (symbol? x) (json-string (str x))
    (boolean? x) (str x)
    (number? x) (if (Double/isFinite (double x)) (str (if (ratio? x) (double x) x)) "null")
    (map? x) (str "{" (str/join "," (map (fn [[k v]] (str (json-string (if (keyword? k) (name k) (str k))) ":" (json v))) x)) "}")
    (or (sequential? x) (set? x)) (str "[" (str/join "," (map json x)) "]")
    :else (json-string (str x))))

(def playground (create-ns 'lambda-world.playground))
(binding [*ns* playground]
  (refer 'clojure.core)
  (alias 'h 'lambda-world.hof)
  (alias 'f 'lambda-world.flock)
  (alias 'b 'lambda-world.bellman)
  (alias 's 'lambda-world.shapley)
  (alias 'p 'lambda-world.parallel)
  (alias 'q 'lambda-world.rescue)
  (alias 'r 'lambda-world.rainbow))

(defn scene-code [scene]
  (let [code (slurp (io/resource (str "examples/" (:file scene))))
        [symbols from to]
        (case (:id scene)
          "flock" [['lambda-world.hof/descent-step] "h/descent-step" "descent-step"]
          "constraints" [['lambda-world.hof/penalize] "h/penalize" "penalize"]
          "momentum" [['lambda-world.hof/with-momentum] "h/with-momentum" "with-momentum"]
          "pomdp" [['lambda-world.rescue/predict
                    'lambda-world.rescue/observation-update
                    'lambda-world.rescue/belief-step] "q/belief-step" "belief-step"]
          "shapley" [['lambda-world.shapley/permutations
                      'lambda-world.shapley/contribution-step
                      'lambda-world.shapley/arrival-trace
                      'lambda-world.shapley/attribute
                      'lambda-world.shapley/view] "s/view" "view"]
          [nil nil nil])]
    (assoc scene :lesson (get lessons/lessons (:id scene)) :code code
           :experiments (experiments/for-scene (:id scene) code)
           :speaker (assoc (get-in presentation [:chapters (:id scene)])
                           :start-seconds
                           (* 60 (reduce + (map #(get-in presentation [:chapters (:id %) :minutes])
                                               (take-while #(not= (:id %) (:id scene)) scenes)))))
           :expanded-code (when symbols
                            (str (str/join "\n\n" (map repl/source-fn symbols))
                                 "\n\n;; --- Run the function you just defined ---\n\n"
                                 (str/replace code from to))))))

(defn evaluate [code]
  (let [out (java.io.StringWriter.)
        start (System/nanoTime)
        result (binding [*ns* playground *out* out *err* out *read-eval* false]
                 (with-open [reader (PushbackReader. (StringReader. code))]
                   (loop [last-result nil]
                     (let [form (read {:eof ::eof} reader)]
                       (if (= ::eof form) last-result (recur (eval form)))))))]
    {:ok true :elapsed-ms (/ (- (System/nanoTime) start) 1.0e6)
     :stdout (str out)
     :printed (binding [*print-length* 12 *print-level* 5] (pr-str result))
     :visual (when (and (map? result) (#{:flock :bellman :shapley :belief :rainbow} (:kind result))) result)}))

(defn respond [exchange status content-type body]
  (let [bytes (.getBytes (str body) "UTF-8")]
    (.set (.getResponseHeaders exchange) "Content-Type" content-type)
    (.set (.getResponseHeaders exchange) "Cache-Control" "no-store")
    (.set (.getResponseHeaders exchange) "X-Content-Type-Options" "nosniff")
    (.sendResponseHeaders exchange status (alength bytes))
    (with-open [out (.getResponseBody exchange)] (.write out bytes))))

(defn start! [port]
  (let [server (HttpServer/create (InetSocketAddress. "127.0.0.1" port) 0)
        token (str (UUID/randomUUID))
        allowed-hosts #{(str "127.0.0.1:" port) (str "localhost:" port)}
        eval-lock (Object.)]
    (.createContext server "/"
      (reify HttpHandler
        (handle [_ exchange]
          (try
            (let [path (.getPath (.getRequestURI exchange))
                  method (.getRequestMethod exchange)
                  headers (.getRequestHeaders exchange)]
              (cond
                (not (allowed-hosts (.getFirst headers "Host")))
                (respond exchange 403 "text/plain" "Loopback requests only")

                (and (= method "GET") (= path "/api/scenes"))
                (respond exchange 200 "application/json; charset=utf-8"
                  (json {:token token :scenes (mapv scene-code scenes)}))

                (and (= method "POST") (= path "/api/eval"))
                (if (and (= token (.getFirst headers "X-Presentation-Token"))
                         (or (nil? (.getFirst headers "Origin"))
                             (#{(str "http://127.0.0.1:" port) (str "http://localhost:" port)}
                              (.getFirst headers "Origin"))))
                  (let [bytes (.readNBytes (.getRequestBody exchange) 24577)]
                    (if (> (alength bytes) 24576)
                      (respond exchange 413 "application/json" (json {:ok false :error "Cell exceeds 24 KB"}))
                      (let [code (String. bytes "UTF-8")
                            task (future
                                   (locking eval-lock
                                     (try (evaluate code)
                                          (catch Throwable error
                                            {:ok false :error (or (ex-message (ex-cause error)) (ex-message error))}))))
                            result (deref task 15000 ::timeout)]
                        (when (= result ::timeout) (future-cancel task))
                        (respond exchange 200 "application/json; charset=utf-8"
                          (json (if (= result ::timeout)
                                  {:ok false :error "Evaluation exceeded 15 seconds; restart the server if the cell ignores interruption."}
                                  result))))))
                  (respond exchange 403 "application/json" (json {:ok false :error "Open the local presentation to execute a cell."})))

                (and (= method "GET") (or (= path "/") (public-files (subs path 1))))
                (let [file (if (= path "/") "index.html" (subs path 1))]
                  (respond exchange 200
                    (cond (str/ends-with? file ".js") "text/javascript; charset=utf-8"
                          (str/ends-with? file ".css") "text/css; charset=utf-8"
                          (str/ends-with? file ".txt") "text/plain; charset=utf-8"
                          :else "text/html; charset=utf-8")
                    (slurp (io/resource (str "public/" file)))))

                :else (respond exchange 404 "text/plain" "Not found")))
            (catch Throwable error
              (respond exchange 500 "application/json" (json {:ok false :error (ex-message error)})))))))
    (.setExecutor server (Executors/newFixedThreadPool 4))
    (.start server)
    server))

(defn -main [& [port]]
  (let [port (if port (Integer/parseInt port) 7340)]
    (start! port)
    (println (str "The Function Observatory → http://127.0.0.1:" port))
    (println "Local trusted-code REPL. Ctrl+C to stop.")))
