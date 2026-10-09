;; Same destination. A transformed objective.
(def objective
  (h/penalize
    (f/rendezvous [3.0 0.0])
    (f/separation-constraints 8 1.1)
    3.0))

;; The optimizer only sees a function.
(def step
  (h/descent-step (h/gradient objective) 0.025))

(f/view objective step f/initial 100 [3.0 0.0])
