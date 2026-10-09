(ns lambda-world.experiments
  "Curated, runnable changes to the talk cells. The exporter evaluates these on the JVM."
  (:require [clojure.string :as str]))

(def variants
  {"constraints"
   [{:id "no-penalty" :label "Penalty = 0"
     :note "Remove the crowding cost. The boats can collapse onto the report."
     :from "    3.0))" :to "    0.0))"}]
   "momentum"
   [{:id "no-memory" :label "Momentum β = 0"
     :note "Remove carried velocity. The wrapper now gives ordinary descent."
     :from "plain-step 0.75" :to "plain-step 0.0"}
    {:id "long-memory" :label "Momentum β = 0.9"
     :note "Carry more velocity. Watch for overshoot; more memory need not be better."
     :from "plain-step 0.75" :to "plain-step 0.9"}]
   "bellman"
   [{:id "immediate-only" :label "Discount γ = 0"
     :note "Ignore future value. Reward no longer propagates through the map."
     :from ":discount 0.92" :to ":discount 0.0"}
    {:id "no-current" :label "Current = 0"
     :note "Remove sideways drift. Compare the same backup under a new model."
     :from ":wind 0.12" :to ":wind 0.0"}]})

(defn for-scene [id code]
  (mapv (fn [{:keys [from to] :as variant}]
          (when-not (str/includes? code from)
            (throw (ex-info "Experiment no longer matches its source cell" {:scene id :variant (:id variant)})))
          (assoc (dissoc variant :from :to) :code (str/replace code from to)))
        (get variants id [])))
