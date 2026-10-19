;; Run from the repository root; reads Rainbow, writes only this talk's receipt.
(require '[clojure.edn :as edn]
         '[clojure.pprint :as pp]
         '[clojure.java.shell :as shell]
         '[clojure.string :as str]
         '[rainbow.elaborate :as elaborate]
         '[rainbow.environment :as environment]
         '[rainbow.kernel :as kernel])

(let [source (slurp "talk/src/talk/composition.edn")
      {:keys [type value]} (edn/read-string source)
      env (environment/empty-environment)
      core (fn [form]
             (let [r (elaborate/core-expression env form)]
               (assert (= :elaborated (:status r)) (pr-str r))
               (:term r)))
      accepted (kernel/check env (core value) (core type))
      ;; The same parameters, but return Q where the statement requires R.
      wrong (assoc value 2 '[:app f p])
      rejected (kernel/check env (core wrong) (core type))
      git (shell/sh "git" "-C" "../rainbow/rainbow" "rev-parse" "HEAD")
      dirty (shell/sh "git" "-C" "../rainbow/rainbow" "status" "--porcelain")]
  (assert (= :checked (:status accepted)) (pr-str accepted))
  (assert (= :rejected (:status rejected)) (pr-str rejected))
  (assert (zero? (:exit git)))
  (assert (and (zero? (:exit dirty)) (str/blank? (:out dirty)))
          "Record a reproducible Rainbow revision, not a dirty checkout")
  (let [receipt {:checked-at (str (java.time.Instant/now))
                 :rainbow-revision (str/trim (:out git))
                 :source source
                 :status (:status accepted)
                 :axioms []
                 :environment :empty
                 :negative-proof wrong
                 :negative-status (:status rejected)
                 :negative-message (:message rejected)}]
    (spit "talk/src/talk/composition-check.edn"
          (with-out-str (pp/pprint receipt)))
    (println (pr-str (dissoc receipt :source :negative-proof)))))
(shutdown-agents)
