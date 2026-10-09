;; A little language becomes ordinary coordinates.
(def boats
  (f/fleet [⛵ · · ⛵ · · ⛵]
           [· · ⛵ · · ⛵ ·]
           [⛵ · · · ⛵ · ·]
           [· · ⛵ · · · ·]))

;; A destination becomes an objective function.
(def objective (f/rendezvous [3.0 0.0]))

;; A gradient becomes a step function.
(def step
  (h/descent-step
    (h/gradient objective) 0.025))

;; The visualizer receives actual computed states.
(f/view objective step boats 100 [3.0 0.0])
