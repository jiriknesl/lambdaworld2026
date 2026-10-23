(ns talk.proof-data
  "Structural edits used by both the browser demo and the JVM check."
  (:require [talk.notation :as n]))

;; Keep the capture wrapper outside the definition's source span.
(n/src
 (defn remove-outer [proof]
   (update-in proof [:value 2]
              (fn [body] (nth body 2))))
 )

(n/src
 (defn wrap-body [proof function-name]
   (update-in proof [:value 2]
              (fn [body] [:app function-name body])))
 )
