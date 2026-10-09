(ns lambda-world.rainbow-audit
  "Recheck existing Rainbow proofs. Never writes to the Rainbow working tree."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.pprint :as pprint]
            [rainbow.corpus.day54 :as checkpoint]
            [rainbow.environment :as env]
            [rainbow.kernel :as kernel]
            [rainbow.term :as term]
            [rainbow.logic.penalty-method :as penalty]
            [rainbow.logic.vector-projected-gradient :as projection]
            [rainbow.logic.finite-bellman-signed-optimal-contraction :as bellman]
            [rainbow.logic.ahof-shap :as shap]
            [rainbow.or.momentum :as momentum]))

(defn check-theorem [environment theorem-name]
  (let [id (env/resolve-id environment theorem-name)
        declaration (env/declaration environment id)]
    (when-not declaration
      (throw (ex-info "The theorem is absent" {:theorem theorem-name})))
    (let [result (kernel/check environment
                               (:rainbow/value declaration)
                               (:rainbow/type declaration)
                               (set (:rainbow/universes declaration)))
          axioms (kernel/axioms environment id)]
      (when-not (and (= :checked (:status result)) (empty? axioms))
        (throw (ex-info "Proof check failed" {:theorem theorem-name :result result :axioms axioms})))
      {:theorem (str theorem-name) :id id :status (:status result) :axioms (vec axioms)})))

(defn install-if-missing [environment theorem-name install]
  (if (env/resolve-id environment theorem-name) environment (install environment)))

(defn -main [& _]
  (println "Replaying Rainbow's D63 checkpoint through its kernel…") (flush)
  (let [loaded (checkpoint/load-checkpoint
                 "../rainbow/artifacts/ahof/d63-eligibility-traces/checkpoint.edn.gz")]
    (when-not (= :replayed (:status loaded))
      (throw (ex-info "Checkpoint replay failed" (dissoc loaded :environment))))
    (println "Checkpoint replayed. Checking momentum and descent…") (flush)
    (let [base (:environment loaded)
          baseline (mapv #(check-theorem base %)
                          '[optimization/signed-descent-step-unfold
                            optimization/momentum-step-unfold
                            optimization/momentum-iterate-successor
                            ahof-o19/first-position-is-one
                            ahof-o19/first-velocity-is-one])
          _ (println "Checking penalty and vector projection…")
          env (-> base
                  (install-if-missing 'penalty/augmented-unfold penalty/extend-environment)
                  (install-if-missing 'optimization/vector-projected-step-in-band projection/extend-environment))
          geometry (mapv #(check-theorem env %)
                         '[penalty/augmented-unfold penalty/feasible-example
                           optimization/vector-projected-step-in-band
                           optimization/vector-projected-step-reprojection])
          _ (do (println "Checking the signed Bellman optimality contraction theorem…") (flush))
          env (install-if-missing env 'value/signed-optimality-step-additively-contractive
                                  bellman/extend-environment)
          contraction (check-theorem env 'value/signed-optimality-step-additively-contractive)
          _ (println "Checking the narrower weighted-attribution identity…")
          env (install-if-missing env 'shap/attribution-unfold shap/extend-environment)
          attribution (check-theorem env 'shap/attribution-unfold)
          declaration (env/declaration env (env/resolve-id env 'optimization/momentum-step-unfold))
          rejected (kernel/check env (term/constant (env/resolve-id env 'data/zero) [])
                                 (:rainbow/type declaration)
                                 (set (:rainbow/universes declaration)))
          _ (when (= :checked (:status rejected))
              (throw (ex-info "A number was accepted as a momentum proof" {})))
          result {:checked-at (str (java.time.Instant/now))
                  :rainbow-commit (.trim (:out (shell/sh "git" "-C" "../rainbow" "rev-parse" "HEAD")))
                  :checkpoint "artifacts/ahof/d63-eligibility-traces/checkpoint.edn.gz"
                  :checkpoint-status (:status loaded)
                  :checks (vec (concat baseline geometry [contraction attribution]))
                  :negative-check {:attempt "Use the natural number zero as a proof of the momentum recurrence"
                                   :status (:status rejected)
                                   :category (:error/category rejected)}
                  :momentum-example {:beta 1 :rate 1 :gradient 1 :before [0 2]
                                     :after (momentum/step 1 1 (constantly 1) [0 2])}
                  :scope "Exact-rational Rainbow terms. No proof of correspondence to this presentation's floating-point Clojure or renderer."}]
      (with-open [writer (io/writer "resources/rainbow-evidence.edn")]
        (binding [*out* writer] (pprint/pprint result)))
      (println "Wrote resources/rainbow-evidence.edn:" (count (:checks result)) "kernel checks; wrong proof rejected.")
      (shutdown-agents))))
