;; Run from the talk repository root with a pinned Rainbow source snapshot on the classpath.
;; Args: snapshot directory (src/ and checkpoint.edn.gz), full Rainbow commit id.
(require '[rainbow.module :as module]
         '[rainbow.corpus.day54 :as checkpoint]
         '[rainbow.environment :as environment]
         '[rainbow.kernel :as kernel]
         '[rainbow.emit :as emit]
         '[clojure.pprint :as pp]
         '[clojure.java.io :as io]
         '[clojure.string :as str])
(def snapshot (io/file (first *command-line-args*)))
(def revision (second *command-line-args*))
(assert (re-matches #"[0-9a-f]{40}" revision) "Supply the pinned full commit id")
(defn sha256 [bytes]
  (format "%064x" (java.math.BigInteger. 1 (.digest (java.security.MessageDigest/getInstance "SHA-256") bytes))))
(defn file-sha [file] (sha256 (java.nio.file.Files/readAllBytes (.toPath (io/file file)))))
(def source-files (sort-by str (filter #(.isFile %) (file-seq (io/file snapshot "src")))))
(def source-digest
  (sha256 (.getBytes
           (str/join "\n" (for [f source-files]
                             (str (.relativize (.toPath snapshot) (.toPath f)) " " (file-sha f))))
           "UTF-8")))
(def targets
  [['rainbow.logic.finite-rational-argmax 'decision/finite-nnrat-argmax-dominates]
   ['rainbow.logic.nnrat-lp-weak-duality 'nnrat/lp-weak-duality]
   ['rainbow.logic.clojure-expression 'clj/clamp-program-denotes-clamp]
   ['rainbow.logic.clojure-compile-correct 'clj/compile-correct-scoped]])
(println "Replaying pinned base checkpoint") (flush)
(def base (checkpoint/load-checkpoint (str (io/file snapshot "checkpoint.edn.gz"))))
(assert (= :replayed (:status base)) (pr-str (dissoc base :environment)))
(loop [env (:environment base) remaining targets results []]
  (if-let [[m name] (first remaining)]
    (let [_ (println "Installing" m) _ (flush)
          env (module/install-module env m)
          id (environment/resolve-id env name)
          d (environment/declaration env id)
          check (kernel/check env (:rainbow/value d) (:rainbow/type d)
                              (set (:rainbow/universes d)))
          row {:name name :id id :type (:rainbow/type d)
               :status (:status check) :axioms (kernel/axioms env id)}]
      (assert (= :checked (:status check)) (pr-str check))
      (println "CHECKED" name (:axioms row)) (flush)
      (recur env (rest remaining) (conj results row)))
    (let [generated (emit/render env 'clj/clamp-program '[lower upper value])
          clamp (eval (:form generated))
          examples (mapv (fn [x] {:input (str x) :output (str (clamp 0 1 x))}) [-1 1/2 2])]
      (assert (= ["0" "1/2" "1"] (mapv :output examples)))
      (spit "talk/src/talk/or-checks.edn"
            (with-out-str
              (pp/pprint {:checked-at (str (java.time.Instant/now))
                         :rainbow-revision revision
                         :source-tree-sha256 source-digest
                         :checkpoint-sha256 (file-sha (io/file snapshot "checkpoint.edn.gz"))
                         :theorems results
                         :generated (assoc generated :display-source
                                           (binding [pp/*print-right-margin* 54]
                                             (with-out-str (pp/pprint (:form generated)))))
                         :examples examples})))
      (println (:source generated) examples))))
(shutdown-agents)
