(ns talk.study
  "Runnable study examples using the same functions as the presentation."
  (:require [talk.core :as c]
            ["fs" :as fs]))

(defn results []
  (let [V (zipmap (:states c/restock) (repeat 0))
        W (zipmap (:states c/restock) (repeat 10))
        B (c/bellman c/restock)
        ;; A tiny cyclic graph makes closure visible without the controller's state count.
        successors {:a #{:b} :b #{:c} :c #{:a}}
        tiny (c/reachable successors #{:a})
        bad-table {:seen {0 0, 1 0, 2 0}}
        bad-decision (c/with-shield (c/table-decision bad-table) bad-table)]
    {:input c/shield-input
     :event c/shield-event
     :stop-table c/stop-table
     :controller-results c/shield-comparison
     :reachability
     (mapv (fn [{:keys [id controller]}]
             (let [run (c/reach-runs id)
                   states (peek run)]
               {:controller id
                :initial-count (count (first run))
                :first-round-counts (mapv count (take 4 run))
                :updates (dec (count run))
                :reachable-count (count states)
                :failure-count (count (filter c/unanswered? states))
                :closed (= states ((c/reach-step (c/controller-successors controller))
                                   states))}))
           c/controllers)
     :tiny-reachability (mapv #(vec (sort %)) tiny)
     :shield-limitation
     {:input {:ctx :seen :det 2 :u 0.3}
      :action (bad-decision {:ctx :seen :det 2 :u 0.3})
      :reason "The shield delegates known contexts, even if that row is wrong."}
     :bellman {:before (c/largest-change V W)
               :after (c/largest-change (B V) (B W))
               :discount (:discount c/restock)}}))

(defn main [& [output]]
  (let [result (results)
        serialized (str (js/JSON.stringify (clj->js result) nil 2) "\n")]
    (assert (every? :closed (:reachability result)))
    (assert (= [false false true]
               (mapv #(zero? (:failure-count %)) (:reachability result))))
    (assert (= :run (get-in result [:shield-limitation :action])))
    (assert (< (abs (- 9 (get-in result [:bellman :after]))) 1e-9))
    (if output
      (do (fs/writeFileSync output serialized)
          (println (str "Study examples checked and written to " output)))
      (println serialized))))
