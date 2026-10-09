;; Reuse the belief after two unsuccessful searches.
;; Advance drift once for the next simultaneous sweep.
(def search-area (q/predict q/drift (q/search-belief)))

;; A coalition's value is expected detection coverage.
;; Overlapping boats do not each claim the same 80%.
(def value-of (q/rescue-value search-area))

;; One fold per arrival order, then average all six.
;; Units: percentage points of detection probability.
(assoc (s/view value-of q/boats)
       :coverage (q/coverage search-area)
       :units "DETECTION PROBABILITY / PERCENTAGE POINTS")
