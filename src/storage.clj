(ns storage
  (:require [clojure.edn :as edn]))

(def settings-file "bolanekalkylator.edn")

(defn load-settings []
  (try
    (let [settings (edn/read-string (slurp settings-file))]
      (when (map? settings) settings))
    (catch Exception _ nil)))

(defn save-settings! [data]
  (spit settings-file (str (pr-str data) "\n")))
