(ns lambda-world.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [lambda-world.hof :as h]
            [lambda-world.flock :as f]
            [lambda-world.bellman :as b]
            [lambda-world.shapley :as s]
            [lambda-world.parallel :as p]
            [lambda-world.rescue :as q]
            [lambda-world.rainbow :as r]
            [lambda-world.server :as server]))

(defn near? [a b] (< (abs (- a b)) 1.0e-7))

(deftest gradient-and-momentum
  (let [objective (fn [[x y]] (+ (* x x) (* 3 y y)))
        grad (h/gradient objective)
        step (h/descent-step grad 0.1)]
    (is (every? true? (map near? [4.0 18.0] (grad [2.0 3.0]))))
    (is (every? true? (map near? [1.6 1.2] (:x (step {:x [2.0 3.0]})))))
    (is (= (:x (step {:x [2.0 3.0]}))
           (:x ((h/with-momentum step 0) {:x [2.0 3.0]}))))
    (let [momentum (h/with-momentum step 0.5)
          first-step (momentum {:x [2.0 3.0]})
          second-step (momentum first-step)]
      (is (near? 1.08 (first (:x second-step))))
      (is (= first-step (momentum {:x [2.0 3.0]})) "No hidden velocity"))))

(deftest soft-constraints
  (let [objective (h/penalize (constantly 2) [#(- (first %) 1)] 3)]
    (is (= 2.0 (objective [0])))
    (is (= 14.0 (objective [3])))))

(deftest flock-language-and-trajectory
  (is (= [0.0 0.0 1.0 1.0] (f/flock [🐦 ·] [· 🐦])))
  (let [result (f/run)
        frames (:frames result)]
    (is (= 101 (count frames)))
    (is (< (:value (last frames)) (:value (first frames))))
    (is (every? #(Double/isFinite (double %)) (mapcat (comp flatten :positions) frames)))))

(deftest bellman-known-solution
  (let [model {:states [:a :b] :discount 0.5
               :actions (constantly [:stay])
               :transitions (fn [state _] [{:prob 1 :next state :reward (if (= state :a) 1 2)}])}
        improve (b/tabulate (:states model) (b/bellman model))
        value (nth (iterate improve (constantly 0)) 30)]
    (is (near? 2 (:a value)))
    (is (near? 4 (:b value)))
    (is (= {:a 1.0 :b 2.0} (improve (constantly 0))))))

(deftest grid-terminal-and-convergence
  (let [model (b/grid-world)
        improve (b/tabulate (:states model) (b/bellman model))
        values (take 151 (iterate improve (constantly 0)))
        a (nth values 149) z (last values)]
    (is (= 0.0 (z (:goal model))))
    (is (= 0.0 (z (:trap model))))
    (is (< (apply max (map #(abs (- (a %) (z %))) (:states model))) 1.0e-6))))

(deftest shapley-exact-properties
  (is (= 6 (count (s/permutations s/players))))
  (is (= {:scout 15 :carrier 6 :navigator 9}
         (s/attribute s/expedition-value s/players)))
  (testing "Three-way synergy: uniform subsets would give 1/4, not 1/3"
    (let [game #(if (= 3 (count %)) 1 0)]
      (is (= {:a 1/3 :b 1/3 :c 1/3} (s/attribute game [:a :b :c])))))
  (testing "Baseline, efficiency, symmetry, dummy player"
    (let [game #(+ 7 (if (% :a) 2 0) (if (% :b) 2 0))
          credits (s/attribute game [:a :b :dummy])]
      (is (= {:a 2 :b 2 :dummy 0} credits))
      (is (= (- (game #{:a :b :dummy}) (game #{})) (reduce + (vals credits)))))))

(deftest every-presented-cell-is-runnable
  (doseq [scene (map server/scene-code server/scenes)]
    (testing (:id scene)
      (is (:ok (server/evaluate (:code scene))))
      (when-let [code (:expanded-code scene)]
        (let [result (server/evaluate code)]
          (is (:ok result))
          (is (map? (:visual result))))))))

(deftest parallel-evaluation-preserves-results
  (is (= (p/evaluate-population map #(reduce + %) [[1 2] [3 4] [5 6]])
         (p/evaluate-population pmap #(reduce + %) [[1 2] [3 4] [5 6]]))))

(deftest displayed-updates-match-computation
  (let [data (f/run {:steps 3})]
    (doseq [[current next-frame] (partition 2 1 (:frames data))]
      (let [a (:inspection current)]
        (is (every? true? (map near? (:next-x a) (flatten (:positions next-frame)))))
        (is (every? true? (map near? (:next-x a)
                               (mapv + (:x a) (:base-delta a) (:carry a)))))
        (is (near? (:value current) (+ (:roost-cost a) (:penalty-cost a)))))))
  (doseq [frame (:frames (b/run {:steps 3}))]
    (let [a (:inspection frame)]
      (is (near? (:after a) (reduce + (map :weighted (:terms a)))))
      (is (near? (:after a) (apply max (map :score (:actions a))))))))

(deftest rescue-belief-and-planning
  (testing "Bayes: a miss is evidence, not elimination"
    (let [result (q/observation-update (fn [_ state] (if (= state :a) 4/5 0))
                                      {:a 1/2 :b 1/2} :search :missed)]
      (is (= 3/5 (:evidence result)))
      (is (= {:a 1/6 :b 5/6} (:posterior result)))))
  (testing "Impossible observations are rejected"
    (is (thrown? clojure.lang.ExceptionInfo
                 (q/observation-update (constantly 0) {:a 1} :search :seen))))
  (doseq [belief (q/belief-history)]
    (is (= 1 (reduce + (vals belief))))
    (is (every? #(<= 0 % 1) (vals belief))))
  (testing "Two searches at a known, stationary boundary location: 1 - 0.2²"
    (let [one (q/belief-bellman (constantly 0))
          two (q/belief-bellman one)]
      (is (= 4/5 (one {[6 4] 1})))
      (is (= 24/25 (two {[6 4] 1}))))))

(deftest rescue-coalition-coverage
  (let [value-of (q/rescue-value {[5 0] 1})]
    (is (= 80 (value-of #{:central})))
    (is (= 96 (value-of #{:central :east})))
    (is (= {:west 0 :central 48 :east 48} (s/attribute value-of q/boats))))
  (let [result (q/attribution)]
    (is (= (:total result) (reduce + (vals (:credits result)))))
    (doseq [boat q/boats]
      (is (= (get-in result [:credits boat])
             (/ (reduce + (get-in result [:samples boat])) 6))))))

(deftest recorded-proof-evidence-and-lessons
  (let [e (r/evidence)]
    (is (= 11 (count (:checks e))))
    (is (every? #(and (= :checked (:status %)) (empty? (:axioms %))) (:checks e)))
    (is (= :rejected (get-in e [:negative-check :status]))))
  (doseq [scene server/scenes]
    (is (seq (:steps (:lesson (server/scene-code scene)))))))

(deftest speaker-timing-and-cues
  (let [scenes (mapv server/scene-code server/scenes)
        speakers (mapv :speaker scenes)]
    (is (= 2400 (* 60 (reduce + (map :minutes speakers)))))
    (is (= (mapv :start-seconds speakers)
           (vec (butlast (reductions + 0 (map #(* 60 (:minutes %)) speakers))))))
    (doseq [[scene speaker] (map vector scenes speakers)]
      (is (= 0 (:at (first (:beats speaker)))))
      (is (apply < (map :at (:beats speaker))))
      (is (< (:at (last (:beats speaker))) (* 60 (:minutes speaker))))
      (is (every? #(every? (comp seq %)
                           [:title :text :code :say :demo]) (:beats speaker)))
      (when-let [visual (:visual (server/evaluate (:code scene)))]
        (let [maximum (if (= :shapley (:kind visual))
                        (dec (* 4 (count (:orders visual))))
                        (dec (count (:frames visual))))]
          (doseq [frame (keep :frame (:beats speaker))]
            (is (<= 0 frame maximum))))))))

(deftest coverage-chart-uses-the-coalition-model
  (let [belief (q/predict q/drift (q/search-belief))
        cells (q/coverage belief)]
    (is (= 1 (reduce + (map :belief cells))))
    (doseq [coalition [#{} #{:west} #{:central :east} (set q/boats)]]
      (let [from-chart (* 100 (reduce + (map (fn [cell]
                                             (* (:belief cell)
                                                (- 1 (reduce * 1 (for [{:keys [boat detection]} (:sensors cell)
                                                                       :when (coalition boat)]
                                                                   (- 1 detection)))))) cells)))]
        (is (= from-chart ((q/rescue-value belief) coalition)))))))

(deftest curated-experiments-change-one-model-parameter
  (let [scenes (into {} (map (juxt :id server/scene-code) server/scenes))
        results (into {} (for [[id scene] scenes, variant (:experiments scene)]
                           [[id (:id variant)] (:visual (server/evaluate (:code variant)))]))
        frames (fn [key] (:frames (results key)))
        default-frames (fn [id] (:frames (:visual (server/evaluate (:code (scenes id))))))]
    (is (= 5 (count results)))
    (is (every? some? (vals results)))
    (testing "Beta zero is the same descent trajectory, not a different objective"
      (is (= (mapv :positions (default-frames "constraints"))
             (mapv :positions (frames ["momentum" "no-memory"])))))
    (testing "Momentum first matters when a previous velocity exists"
      (let [plain (frames ["momentum" "no-memory"])
            carry (frames ["momentum" "long-memory"])]
        (is (= (:positions (plain 1)) (:positions (carry 1))))
        (is (not= (:positions (plain 2)) (:positions (carry 2))))))
    (testing "Removing the penalty collapses the formation"
      (is (< (get-in (last (frames ["constraints" "no-penalty"])) [:inspection :closest-distance]) 0.01)))
    (testing "Gamma zero stabilizes after one immediate-reward backup"
      (let [values (map :values (rest (frames ["bellman" "immediate-only"])))]
        (is (apply = values))))
    (testing "No current leaves exactly one nonzero outcome weight"
      (is (= [1.0 0.0 0.0]
             (mapv :prob (get-in (first (frames ["bellman" "no-current"])) [:inspection :terms])))))
    (doseq [[key visual] results]
      (is (every? #(Double/isFinite (double %))
                  (filter number? (tree-seq coll? seq visual))) (str key)))))

(defn enumerated-search-success
  "Sum physical paths for a fixed two-action policy, without Bayesian updates."
  [belief first-action second-action]
  (reduce +
    (for [[state mass] belief
          {p1 :prob s1 :next} (q/drift state)]
      (* mass p1
         (+ (q/detection first-action s1)
            (* (- 1 (q/detection first-action s1))
               (reduce + (for [{p2 :prob s2 :next} (q/drift s1)]
                           (* p2 (q/detection second-action s2))))))))))

(deftest two-search-explanation-matches-enumerated-policies
  (doseq [belief (butlast (q/belief-history))]
    (let [plan (q/two-search-plan belief)
          policies (for [a q/boats, b q/boats] (enumerated-search-success belief a b))]
      (is (= (apply max policies) (:score plan)))
      (is (= (:score plan) ((nth (iterate q/belief-bellman (constantly 0)) 2) belief)))
      (doseq [{:keys [action seen miss miss-posterior continuation weighted-continuation score second-actions second-best]} (:actions plan)]
        (is (= 1 (+ seen miss)))
        (is (= 1 (reduce + (vals miss-posterior))))
        (is (= weighted-continuation (* miss continuation)))
        (is (= score (+ seen weighted-continuation)))
        (is (= score (apply max (map #(enumerated-search-success belief action %) q/boats))))
        (is (= continuation (apply max (map :score second-actions))))
        (is (= second-best (mapv :action (filter #(= continuation (:score %)) second-actions))))
        (doseq [second-action second-best]
          (is (= score (enumerated-search-success belief action second-action)))))))
  (testing "A supplied continuation changes the value but not the observation model"
    (let [stop (q/action-backup (constantly 0) q/prior :central)
          guaranteed (q/action-backup (constantly 1) q/prior :central)]
      (is (= (:seen stop) (:score stop)))
      (is (= 1 (:score guaranteed)))
      (is (= (:miss-posterior stop) (:miss-posterior guaranteed)))))
  (testing "Exact ties remain visible"
    (with-redefs [q/detection (fn [_ _] 4/5)]
      (let [plan (q/two-search-plan {[6 4] 1})]
        (is (= 24/25 (:score plan)))
        (is (= q/boats (:best-actions plan)))
        (is (every? #(= q/boats (:second-best %)) (:actions plan))))))
  (testing "A sighting has no continuation plan"
    (is (nil? (get-in (q/run) [:frames 3 :inspection :plan])))))
