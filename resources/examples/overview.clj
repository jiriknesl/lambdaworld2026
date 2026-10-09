;; One rescue. Different mathematical objects.

{:search
 '(iterate step initial-state)

 :planning
 '(iterate (bellman model) initial-value)

 :belief
 '(reductions (belief-step drift detection) prior observations)

 :attribution
 '(reductions (contribution-step value-of)
              initial-state arrival-order)

 :transformation
 '(with-momentum step beta)}
