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

(defn load-objects []
  (try
    (let [data (some-> (existing-settings-file) read-data)]
      (cond
        (vector? (:objects data)) (:objects data)
        (map? data) [data]
        :else []))
    (catch Exception _ [])))

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

(defn- write-objects! [objects]
  (let [target (io/file settings-file)
        parent (.getParentFile target)]
    (when parent
      (.mkdirs parent))
    (let [temporary (java.io.File/createTempFile
                     "bolanekalkylator-" ".tmp" parent)]
      (try
        (spit temporary (str (pr-str {:objects objects}) "\n"))
        (replace-file! temporary target)
        (finally
          (.delete temporary))))))

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
