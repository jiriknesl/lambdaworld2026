(ns lambda-world.export
  "Generate a public static presentation with recorded JVM results and no evaluator."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [lambda-world.server :as server]))

(defn -main [& [destination]]
  (let [root (io/file (or destination ".preview"))
        build (str (System/currentTimeMillis))
        scenes (mapv server/scene-code server/scenes)
        results (into {} (map (fn [scene]
                               [(:id scene) (server/evaluate (:code scene))]) scenes))
        experiments (into {} (for [scene scenes, variant (:experiments scene)]
                               [(str (:id scene) "/" (:id variant))
                                (server/evaluate (:code variant))]))]
    (.mkdirs (io/file root "api"))
    (doseq [file server/public-files]
      (let [source (slurp (io/resource (str "public/" file)))]
        (spit (io/file root file)
              (if (str/ends-with? file ".html")
                (reduce (fn [html asset]
                          (str/replace html (str "/" asset "\"")
                                       (str "/" asset "?v=" build "\"")))
                        (str/replace source "content=\"local\"" (str "content=\"" build "\""))
                        ["fonts.css" "app.js" "style.css" "ocean.js" "stage.js" "experiments.js" "presenter.js" "presenter.css"])
                source))))
    (spit (io/file root "api" "scenes")
          (server/json {:mode :replay :scenes scenes :results results :experiments experiments}))
    (println (str "Static presentation exported to " (.getCanonicalPath root)))
    (println (str "Preview version: " build))
    (println "Recorded Clojure results; no evaluation endpoint or credentials included.")
    (shutdown-agents)))
