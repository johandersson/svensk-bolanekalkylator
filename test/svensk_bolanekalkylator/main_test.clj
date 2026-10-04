(ns svensk-bolanekalkylator.main-test
  (:require [clojure.test :refer [deftest is testing]]
            [gui :as gui]
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

(deftest maximum-cost-allows-blank-income-without-extra-amortization
  (doseq [maximum-cost ["17000" "24000"]]
    (let [errors (atom [])
          ui {:frame nil
              :results (JTextArea.)
              :p-price-f (JTextField. "500000")
              :d-pay-f (JTextField. "500000")
              :fee-f (JTextField. "10000")
              :op-cost-f (JTextField. "500")
              :income-f (JTextField.)
              :t-low-f (JTextField. "30")
              :t-high-f (JTextField. "21")
              :rate-slider (doto (JSlider.) (.setValue 59))
              :extra-amort-cb (JCheckBox.)
              :borrower-count-cb
              (doto (JComboBox. (into-array Integer
                                            [(Integer/valueOf 1)
                                             (Integer/valueOf 2)]))
                (.setSelectedItem (Integer/valueOf 2)))}]
      (with-redefs [gui/ask-maximum-cost! (fn [_] maximum-cost)
                    gui/show-error! (fn [_ title message]
                                      (swap! errors conj [title message]))]
        (main/apply-maximum-cost! ui))
      (is (empty? @errors))
      (is (pos? (Double/parseDouble (.getText (:p-price-f ui)))))
      (is (not-empty (.getText (:results ui)))))))

(deftest maximum-cost-keeps-a-higher-original-purchase-price
  (let [errors (atom [])
        info-messages (atom [])
        original-result "Befintlig kalkyl"
        ui {:frame nil
            :results (JTextArea. original-result)
            :p-price-f (JTextField. "3390000")
            :d-pay-f (JTextField. "500000")
            :fee-f (JTextField. "10000")
            :op-cost-f (JTextField. "500")
            :income-f (JTextField.)
            :rate-slider (doto (JSlider.) (.setValue 59))
            :extra-amort-cb (JCheckBox.)
            :borrower-count-cb
            (doto (JComboBox. (into-array Integer
                                          [(Integer/valueOf 1)
                                           (Integer/valueOf 2)]))
              (.setSelectedItem (Integer/valueOf 2)))}]
    (with-redefs [gui/ask-maximum-cost! (fn [_] "17000")
                  gui/show-error! (fn [_ title message]
                                    (swap! errors conj [title message]))
                  gui/show-info! (fn [_ title message]
                                   (swap! info-messages conj [title message]))]
      (main/apply-maximum-cost! ui))
    (is (empty? @errors))
    (is (= 1 (count @info-messages)))
    (is (= "3390000" (.getText (:p-price-f ui))))
    (is (= original-result (.getText (:results ui))))))

(deftest creating-new-object-from-startup-dialog-keeps-blank-object
  (with-redefs [gui/choose-object! (fn [_ _] :new-object)]
    (is (nil? (main/choose-startup-object
               nil
               [{:name "Sparat objekt"}])))))

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