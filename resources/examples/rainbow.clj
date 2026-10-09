;; Actual kernel results, recorded by clojure -M:rainbow.
;; The audit loads ../rainbow and checks each declaration:
;; (kernel/check env (:rainbow/value declaration)
;;                   (:rainbow/type declaration)
;;                   (set (:rainbow/universes declaration)))
;; It also checks the axiom closure and rejects a wrong proof.

(println (select-keys (r/evidence)
           [:checked-at :rainbow-commit :negative-check]))

;; Scrub through three checked properties and one rejection.
;; This cell displays evidence; it does not rerun the kernel.
(r/demo)
