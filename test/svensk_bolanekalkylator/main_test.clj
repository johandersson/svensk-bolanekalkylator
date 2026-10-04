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

(defn presentation-ui []
  {:frame nil
   :name-f (JTextField. "Testbostad")
   :address-f (JTextField. "Testgatan 1")
   :results (JTextArea.)
   :p-price-f (JTextField. "3000000")
   :d-pay-f (JTextField. "500000")
   :fee-f (JTextField. "4000")
   :op-cost-f (JTextField. "1000")
   :income-f (JTextField.)
   :rate-slider (doto (JSlider.) (.setValue 80))
   :extra-amort-cb (JCheckBox.)
   :borrower-count-cb (JComboBox. (into-array Integer [(Integer/valueOf 1)]))})

(deftest kalp-prefill-and-session-only-calculation
  (let [ui (assoc (presentation-ui) :kalp (gui/create-kalp-tab))
        fields (get-in ui [:kalp :fields])
        errors (atom [])
        saved-settings (atom nil)]
    (main/prefill-kalp! ui true)
    (is (= "2500000.0" (.getText (:loan fields))))
    (is (= "4000" (.getText (:fee fields))))
    (is (= "1000" (.getText (:operating-cost fields))))
    (is (= "4.0" (.getText (:interest fields))))
    (is (= "" (.getText (:net-income fields))))
    (is (= "" (.getText (:living-cost fields))))
    (.setText (:fee fields) "4500")
    (main/prefill-kalp! ui false)
    (is (= "4500" (.getText (:fee fields))))
    (.setText (:fee-f ui) "5000")
    (main/prefill-kalp! ui false)
    (is (= "5000" (.getText (:fee fields))))
    (.setText (:net-income fields) "40000")
    (.setText (:living-cost fields) "12000")
    (with-redefs [gui/show-error! (fn [& args] (swap! errors conj args))
                  storage/save-kalp-settings! #(reset! saved-settings %)]
      (is (map? (main/calculate-kalp! ui)))
      (is (= {:net-income 40000.0 :benefits 0.0 :stress-interest 7.0
              :living-cost 12000.0 :other-debts 0.0 :transport 0.0
              :childcare 0.0 :other-cost 0.0 :buffer 0.0 :borrowers 1.0}
             @saved-settings))
      (.setText (:net-income fields) "NaN")
      (is (nil? (main/calculate-kalp! ui))))
    (is (= 1 (count @errors)))
    (is (zero? (.getComponentCount (get-in ui [:kalp :result]))))
    (let [complete-ui (merge ui
                             (into {} (map (fn [[key _]]
                                             [key (or (get ui key) (JTextField.))])
                                           main/field-keys)))
          state (main/editable-state complete-ui)
          object (main/object-from-ui complete-ui {:loan 2500000.0})]
      (is (not (contains? state :kalp)))
      (is (= (set (concat (map second main/field-keys)
                          [:interest-slider-value :borrower-count
                           :extra-amortization? :calculation :result-text]))
             (set (keys object)))))
    (.setText (:net-income fields) "40000")
    (main/prefill-kalp! ui true)
    (is (= "40000" (.getText (:net-income fields))))
    (is (= "5000" (.getText (:fee fields))))))

(deftest saved-object-restores-related-kalp-inputs-only
  (let [ui (merge (presentation-ui)
                  {:kalp (gui/create-kalp-tab)
                   :borrower-count-cb (JComboBox. (into-array Integer
                                                              [(Integer/valueOf 1)
                                                               (Integer/valueOf 2)]))
                   :listing-link (javax.swing.JEditorPane.)
                   :rate-label (javax.swing.JLabel.)}
                  (into {} (map (fn [[key _]] [key (JTextField.)])
                                main/field-keys)))
        fields (get-in ui [:kalp :fields])]
    (main/restore-object!
     ui {:name "Sparad bostad"
         :purchase-price "3000000" :down-payment "600000"
         :monthly-fee "4500" :monthly-operating-cost "1500"
         :annual-income "800000" :interest-slider-value 80
         :extra-amortization? false :borrower-count 2})
    (is (= "2400000.0" (.getText (:loan fields))))
    (is (= "4000.0" (.getText (:amortization fields))))
    (is (= "4500" (.getText (:fee fields))))
    (is (= "1500" (.getText (:operating-cost fields))))
    (is (= "1" (.getText (:borrowers fields))))
    (is (= "" (.getText (:net-income fields))))
    (is (.contains (.getText (get-in ui [:kalp :source-label])) "Sparad bostad"))
    (.setText (:net-income fields) "45000")
    (main/restore-object!
     ui {:name "Ny bostad"
         :purchase-price "2000000" :down-payment "600000"
         :monthly-fee "2000" :monthly-operating-cost "1000"
         :interest-slider-value 60 :extra-amortization? false})
    (is (= "45000" (.getText (:net-income fields))))
    (is (= "1400000.0" (.getText (:loan fields))))))

(deftest restores-only-independent-kalp-settings
  (let [ui (assoc (presentation-ui) :kalp (gui/create-kalp-tab))
        fields (get-in ui [:kalp :fields])]
    (main/restore-kalp-settings!
     ui {:net-income 42000.0 :living-cost 13000.0 :buffer 2000.0
         :loan 999999.0 :fee 9999.0})
    (is (= "42000.0" (.getText (:net-income fields))))
    (is (= "13000.0" (.getText (:living-cost fields))))
    (is (= "2000.0" (.getText (:buffer fields))))
    (is (= "" (.getText (:loan fields))))
    (is (= "" (.getText (:fee fields))))))

(deftest explicit-calculation-presents-fresh-results-every-time
  (let [ui (presentation-ui)
        presentations (atom [])]
    (with-redefs [gui/show-calculation!
                  (fn [frame data details]
                    (swap! presentations conj [frame data details]))]
      (main/calculate-and-present! ui)
      (main/calculate-and-present! ui)
      (.setText (:p-price-f ui) "3500000")
      (main/calculate-and-present! ui)
      (is (= 3 (count @presentations)))
      (is (= [2500000.0 2500000.0 3000000.0]
             (mapv #(get-in % [1 :loan]) @presentations)))
      (is (= {:object-name "Testbostad"
              :address "Testgatan 1"
              :purchase-price 3500000.0
              :down-payment 500000.0
              :interest 4.0
              :monthly-fee 4000.0
              :monthly-operating-cost 1000.0}
             (get-in @presentations [2 2])))
      (is (.contains (.getText (:results ui)) "3000000,00"))
      (testing "automatic calculations still only update the original text"
        (main/calculate-ui! ui)
        (is (= 3 (count @presentations)))))))

(deftest invalid-calculation-does-not-present-stale-results
  (let [ui (presentation-ui)
        errors (atom [])]
    (main/calculate-ui! ui)
    (.setText (:p-price-f ui) "")
    (with-redefs [gui/show-calculation! (fn [& _]
                                          (is false "No results dialog on invalid input"))
                  gui/show-error! (fn [_ title message]
                                    (swap! errors conj [title message]))]
      (main/calculate-and-present! ui))
    (is (= "Fel: Fyll i köpeskilling." (.getText (:results ui))))
    (is (= [["Kontrollera uppgifterna" "Fel: Fyll i köpeskilling."]]
           @errors))))

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