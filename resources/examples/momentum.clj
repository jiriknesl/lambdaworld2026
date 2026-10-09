;; A function transforms another function.
(def objective
  (h/penalize (f/rendezvous [3.0 0.0])
    (f/separation-constraints 8 1.1) 3.0))

(def plain-step
  (h/descent-step (h/gradient objective) 0.025))

(def step (h/with-momentum plain-step 0.75))

;; Same initial state. Same iteration engine.
;; Try momentum 0.0, 0.5, 0.9.
(f/view objective step f/initial 100 [3.0 0.0])
