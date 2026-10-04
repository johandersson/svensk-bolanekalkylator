(ns storage
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io PushbackReader]
           [java.nio.file AtomicMoveNotSupportedException CopyOption Files
            StandardCopyOption]))

(defn- application-data-directory []
  (io/file (or (System/getenv "APPDATA")
               (System/getProperty "user.home"))
           "Bolanekalkylator"))

(def settings-file
  (io/file (application-data-directory) "bolanekalkylator.edn"))

(def legacy-settings-file
  (io/file "bolanekalkylator.edn"))

(defn- existing-settings-file []
  (let [current (io/file settings-file)]
    (cond
      (.isFile current) current
      (.isFile legacy-settings-file) legacy-settings-file)))

(defn- read-data [file]
  (with-open [reader (PushbackReader. (io/reader file))]
    (edn/read {:eof nil} reader)))

(defn- load-settings []
  (try
    (let [data (some-> (existing-settings-file) read-data)]
      (cond
        (and (map? data) (contains? data :objects))
        (update data :objects #(if (vector? %) % []))

        (map? data) {:objects [data]}
        :else {:objects []}))
    (catch Exception _ {:objects []})))

(defn load-objects []
  (:objects (load-settings)))

(defn load-kalp-settings []
  (let [settings (:kalp-settings (load-settings))]
    (if (map? settings) settings {})))

(defn- replace-file! [temporary target]
  (try
    (Files/move (.toPath temporary)
                (.toPath target)
                (into-array CopyOption
                            [StandardCopyOption/ATOMIC_MOVE
                             StandardCopyOption/REPLACE_EXISTING]))
    (catch AtomicMoveNotSupportedException _
      (Files/move (.toPath temporary)
                  (.toPath target)
                  (into-array CopyOption
                              [StandardCopyOption/REPLACE_EXISTING])))))

(defn- write-settings! [settings]
  (let [target (io/file settings-file)
        parent (.getParentFile target)]
    (when parent
      (.mkdirs parent))
    (let [temporary (java.io.File/createTempFile
                     "bolanekalkylator-" ".tmp" parent)]
      (try
        (spit temporary (str (pr-str settings) "\n"))
        (replace-file! temporary target)
        (finally
          (.delete temporary))))))

(defn- write-objects! [objects]
  (write-settings! (assoc (load-settings) :objects objects)))

(defn save-kalp-settings! [settings]
  (write-settings! (assoc (load-settings) :kalp-settings settings))
  settings)

(defn save-object! [object]
  (let [objects (load-objects)
        name (str/trim (or (:name object) ""))
        address (:address object)
        matching-object? #(if (str/blank? name)
                            (= address (:address %))
                            (= name (str/trim (or (:name %) ""))))
        updated-objects (if (some matching-object? objects)
                          (mapv #(if (matching-object? %) object %) objects)
                          (conj objects object))]
    (write-objects! updated-objects)
    updated-objects))

(defn replace-object! [opened-object updated-object]
  (let [objects (load-objects)
        updated-objects (mapv #(if (= opened-object %)
                                 updated-object
                                 %)
                              objects)]
    (write-objects! updated-objects)
    updated-objects))

(defn delete-object! [object]
  (let [objects (load-objects)
        name (str/trim (or (:name object) ""))
        matching-object? #(if (str/blank? name)
                            (= object %)
                            (= name (str/trim (or (:name %) ""))))
        remaining-objects (filterv (complement matching-object?) objects)]
    (write-objects! remaining-objects)
    remaining-objects))
