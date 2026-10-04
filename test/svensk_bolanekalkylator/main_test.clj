(ns svensk-bolanekalkylator.main-test
  (:require [clojure.test :refer [deftest is testing]]
            [main :as main]
            [storage :as storage])
  (:import [javax.swing JCheckBox JComboBox JSlider JTextArea JTextField]))

(deftest blank-calculation-fields-show-a-useful-error
  (let [results (JTextArea.)]
    (is (nil? (main/calculate-and-display!
               results
               (JTextField.)
               (JTextField.)
               (JTextField.)
               (JTextField.)
               (JTextField.)
               (JTextField. "30")
               (JTextField. "21")
               (JSlider.)
               (JCheckBox.)
               (JComboBox. (into-array Integer [(Integer/valueOf 1)])))))
    (is (= "Fel: Fyll i köpeskilling." (.getText results)))))

(deftest income-is-required-only-for-extra-amortization
  (let [calculate (fn [extra-amortization?]
                    (let [results (JTextArea.)
                          calculation
                          (main/calculate-and-display!
                           results
                           (JTextField. "3000000")
                           (JTextField. "500000")
                           (JTextField. "4000")
                           (JTextField. "1000")
                           (JTextField.)
                           (JTextField.)
                           (JTextField.)
                           (JSlider.)
                           (doto (JCheckBox.)
                             (.setSelected extra-amortization?))
                           (JComboBox. (into-array Integer
                                                   [(Integer/valueOf 1)])))]
                      [calculation (.getText results)]))]
    (testing "income is optional when debt-to-income amortization is disabled"
      (is (map? (first (calculate false)))))
    (testing "income is required when debt-to-income amortization is enabled"
      (is (= [nil "Fel: Fyll i hushållets bruttoinkomst."]
             (calculate true))))))

(deftest autosave-updates-the-opened-object
  (let [opened-object {:name "Gammalt namn"
                       :address "Gammal adress"
                       :calculation {:loan 1000000.0}}
        current-state {:name "Nytt namn"
                       :address "Ny adress"}
        replacement (atom nil)]
    (with-redefs [storage/replace-object!
                  (fn [opened updated]
                    (reset! replacement [opened updated]))]
      (let [updated-object
            (main/autosave-open-object! opened-object current-state)]
        (is (= {:name "Nytt namn"
                :address "Ny adress"
                :calculation {:loan 1000000.0}}
               updated-object))
        (is (= [opened-object updated-object] @replacement))))))