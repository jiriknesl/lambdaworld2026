(ns lambda-world.test-runner
  (:require [clojure.test :as test]
            [lambda-world.core-test]))

(defn -main [& _]
  (let [result (test/run-tests 'lambda-world.core-test)]
    (shutdown-agents)
    (System/exit (if (zero? (+ (:fail result) (:error result))) 0 1))))
