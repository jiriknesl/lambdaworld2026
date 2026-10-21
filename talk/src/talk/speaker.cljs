(ns talk.speaker
  "Speech cues aligned to one-based slide phases, including numerical animation ranges."
  (:require [talk.script-data :as data]))

(defn phase-number [x total]
  (case x
    "last" total
    "penultimate" (dec total)
    x))

(defn matching-phases [slide-index phase total]
  (filter (fn [{:keys [from to]}]
            (<= (phase-number from total) phase
                (phase-number to total)))
          (:phases (get data/slides slide-index))))

(defn current [slide-index phase total]
  (first (matching-phases slide-index phase total)))
