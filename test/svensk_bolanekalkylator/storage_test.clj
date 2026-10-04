(ns svensk-bolanekalkylator.storage-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [storage :as store]))

(defn- with-temp-storage [test-fn]
  (let [directory (java.nio.file.Files/createTempDirectory
                   "bolanekalkylator-test"
                   (make-array java.nio.file.attribute.FileAttribute 0))
        file (io/file (.toFile directory) "data" "bolanekalkylator.edn")]
    (try
      (with-redefs [store/settings-file file
                    store/legacy-settings-file
                    (io/file (.toFile directory) "legacy.edn")]
        (test-fn))
      (finally
        (doseq [child (reverse (file-seq (.toFile directory)))]
          (.delete child))))))

(deftest loads-legacy-settings-as-an-object
  (with-temp-storage
    (fn []
      (let [legacy-object {:address "Gamla vägen 1"}
            new-object {:name "Nytt objekt" :address "Nya vägen 2"}]
        (spit store/legacy-settings-file (pr-str legacy-object))
        (is (= [legacy-object] (store/load-objects)))

        (store/save-object! new-object)

        (testing "saving migrates legacy data to stable storage without loss"
          (is (.isFile (io/file store/settings-file)))
          (is (= [legacy-object new-object]
                 (store/load-objects))))))))

(deftest round-trips-complete-objects
  (with-temp-storage
    (fn []
      (let [first-object {:name "Första objektet"
                          :address "Första vägen 1"
                          :comment "Nära stationen"
                          :listing-url "https://example.se/objekt/1"
                          :purchase-price "3231000"
                          :down-payment "500000"
                          :interest-slider-value 63
                          :extra-amortization? false
                          :calculation {:loan 2731000.0
                                        :monthly-payment-after-tax 14567.89}
                          :result-text "Rad 1\nRad 2\n"}
            second-object {:name "Andra objektet"
                           :address "Andra vägen 2"
                           :comment "Balkong"
                           :listing-url "https://example.se/objekt/2"
                           :purchase-price "4100000"
                           :calculation {:loan 3500000.0}
                           :result-text "Sparad kalkyl"}]
        (store/save-object! first-object)
        (store/save-object! second-object)

        (testing "all fields and nested calculations survive disk round trips"
          (is (= [first-object second-object]
                 (store/load-objects))))

        (testing "the stable data directory is created automatically"
          (is (.isFile (io/file store/settings-file))))

        (let [updated (assoc first-object :comment "Uppdaterad kommentar")]
          (store/save-object! updated)

          (testing "saving an existing name updates only that object"
            (is (= [updated second-object]
                   (store/load-objects))))

          (testing "deleting an object keeps the other saved objects"
            (is (= [second-object]
                   (store/delete-object! updated)))
            (is (= [second-object]
                   (store/load-objects)))))))))

(deftest replaces-only-the-opened-object
  (with-temp-storage
    (fn []
      (let [opened-object {:name "Första objektet" :address "Gammal adress"}
            other-object {:name "Andra objektet" :address "Oförändrad adress"}
            updated-object (assoc opened-object
                                  :name "Nytt namn"
                                  :address "Ny adress")]
        (store/save-object! opened-object)
        (store/save-object! other-object)

        (is (= [updated-object other-object]
               (store/replace-object! opened-object updated-object)))
        (is (= [updated-object other-object]
               (store/load-objects)))))))

(deftest stores-kalp-settings-independently-from-objects
  (with-temp-storage
    (fn []
      (let [object {:name "Bostad" :address "Testgatan 1"}
            kalp-settings {:net-income 40000.0
                           :living-cost 12000.0
                           :transport 2000.0
                           :buffer 1500.0}]
        (store/save-object! object)
        (is (= kalp-settings (store/save-kalp-settings! kalp-settings)))
        (is (= [object] (store/load-objects)))
        (is (= kalp-settings (store/load-kalp-settings)))

        (testing "later object writes preserve the household settings"
          (store/save-object! (assoc object :address "Ny adress"))
          (is (= kalp-settings (store/load-kalp-settings))))

        (testing "later household writes preserve apartment objects"
          (store/save-kalp-settings! (assoc kalp-settings :buffer 2000.0))
          (is (= [(assoc object :address "Ny adress")]
                 (store/load-objects))))))))

(deftest invalid-edn-does-not-crash-loading
  (with-temp-storage
    (fn []
      (.mkdirs (.getParentFile (io/file store/settings-file)))
      (spit store/settings-file "{:objects [")
      (is (= [] (store/load-objects)))
      (is (= {} (store/load-kalp-settings))))))