(ns main
  (:gen-class)
  (:require [calculator :as calc]
            [storage :as store]
            [gui :as gui]
            [clojure.string :as str])
  (:import [javax.swing JMenuItem JOptionPane SwingUtilities]))

(defn parse-number [field]
  (Double/parseDouble
   (str/replace (.getText field) "," ".")))

(defn restore-field! [field settings key]
  (.setText field (str (get settings key ""))))

(defn slider-interest [rate-slider]
  (/ (.getValue rate-slider) 20.0))

(defn update-rate-label! [rate-label rate-slider]
  (let [interest (slider-interest rate-slider)]
    (.setText
     rate-label
     (str "Ränta: " (calc/percent interest) " %"))))

(defn calculate-and-display!
  [res
   p-price-f
   d-pay-f
   fee-f
   op-cost-f
   income-f
   t-low-f
   t-high-f
   rate-slider
   extra-amort-cb]

  (try
    (let [p-price  (parse-number p-price-f)
          d-pay    (parse-number d-pay-f)
          m-fee    (parse-number fee-f)
          m-op     (parse-number op-cost-f)
          inc      (parse-number income-f)
          t-low    (parse-number t-low-f)
          t-high   (parse-number t-high-f)
          interest (slider-interest rate-slider)
          data     (calc/calculate-loan
                    p-price
                    d-pay
                    interest
                    m-fee
                    m-op
                    inc
                    t-low
                    t-high
                    (.isSelected extra-amort-cb))]

      (.setText
       res
       (str
        "===== BOLÅNEKALKYL =====\n\n"
        "Lånebelopp:                 "
        (calc/money (:loan data)) " kr\n"

        "Belåningsgrad:              "
        (calc/percent
         (* 100.0 (:loan-to-value data)))
        " %\n"

        "Räntesats:                  "
        (calc/percent interest)
        " %\n\n"

        "===== AMORTERING =====\n\n"
        "Grundläggande amortering:   "
        (calc/percent
         (:basic-amortization-percent data))
        " % per år\n"

        "Extra amortering:           "
        (calc/percent
         (:extra-amortization-percent data))
        " % per år\n"

        "Total amortering:           "
        (calc/percent
         (:total-amortization-percent data))
        " % per år\n"

        "Amortering per månad:       "
        (calc/money
         (:monthly-amortization data))
        " kr\n\n"

        "===== MÅNADSKOSTNAD =====\n\n"
        "Ränta före skattereduktion: "
        (calc/money
         (:monthly-interest-cost data))
        " kr\n"

        "Skattereduktion per månad:  "
        (calc/money
         (:monthly-tax-reduction data))
        " kr\n"

        "Ränta efter skattereduktion:"
        (calc/money
         (:monthly-interest-after-tax data))
        " kr\n"

        "Månadsavgift:               "
        (calc/money m-fee)
        " kr\n"

        "Driftskostnad:              "
        (calc/money m-op)
        " kr\n\n"

        "Total före skattereduktion: "
        (calc/money
         (:monthly-payment-before-tax data))
        " kr/mån\n"

        "Total efter skattereduktion: "
        (calc/money
         (:monthly-payment-after-tax data))
        " kr/mån\n"))
      data)

    (catch Exception e
      (.setText
       res
       (str "Fel: " (.getMessage e)))
      nil)))

(def field-keys
  [[:name-f :name]
   [:address-f :address]
   [:comment-f :comment]
   [:listing-url-f :listing-url]
   [:p-price-f :purchase-price]
   [:d-pay-f :down-payment]
   [:fee-f :monthly-fee]
   [:op-cost-f :monthly-operating-cost]
   [:income-f :annual-income]
   [:t-low-f :tax-low-percent]
   [:t-high-f :tax-high-percent]])

(defn calculate-ui! [ui]
  (calculate-and-display!
   (:results ui)
   (:p-price-f ui)
   (:d-pay-f ui)
   (:fee-f ui)
   (:op-cost-f ui)
   (:income-f ui)
   (:t-low-f ui)
   (:t-high-f ui)
   (:rate-slider ui)
   (:extra-amort-cb ui)))

(defn restore-object! [ui object]
  (doseq [[field-key object-key] field-keys]
    (restore-field! (get ui field-key) object object-key))
  (gui/show-link! (:listing-link ui) (:listing-url object))

  (when-let [value (:interest-slider-value object)]
    (.setValue (:rate-slider ui) (int value)))

  (when (contains? object :extra-amortization?)
    (.setSelected (:extra-amort-cb ui)
                  (boolean (:extra-amortization? object))))

  (update-rate-label! (:rate-label ui) (:rate-slider ui))
  (if-let [result-text (:result-text object)]
    (.setText (:results ui) result-text)
    (calculate-ui! ui)))

(defn editable-state [ui]
  (merge
   (into {}
         (map (fn [[field-key object-key]]
                [object-key (.getText (get ui field-key))])
              field-keys))
   {:interest-slider-value (.getValue (:rate-slider ui))
    :extra-amortization? (.isSelected (:extra-amort-cb ui))}))

(defn object-from-ui [ui calculation]
  (assoc (editable-state ui)
         :calculation calculation
         :result-text (.getText (:results ui))))

(defn object-label [index object]
  (let [name (str/trim (or (:name object) ""))
        address (str/trim (or (:address object) ""))]
    (cond
      (not (str/blank? name)) name
      (not (str/blank? address)) address
      :else (str "Objekt " (inc index)))))

(defn refresh-objects-menu! [objects-menu objects load-object!]
  (.removeAll objects-menu)
  (if (empty? objects)
    (let [empty-item (JMenuItem. "Inga sparade objekt")]
      (.setEnabled empty-item false)
      (.add objects-menu empty-item))
    (doseq [[index object] (map-indexed vector objects)]
      (let [label (object-label index object)
            item (JMenuItem. label)]
        (.addActionListener
         item
         (reify java.awt.event.ActionListener
           (actionPerformed [_ _]
             (load-object! object label))))
        (.add objects-menu item))))
  (.revalidate objects-menu)
  (.repaint objects-menu))

(defn choose-startup-object [frame objects]
  (let [labels (mapv object-label (range) objects)
        selected-index (gui/choose-object! frame labels)]
    (when (some? selected-index)
      [(nth objects selected-index)
       (nth labels selected-index)])))

(defn init-app []
  (let [ui              (gui/create-ui)
        calc-btn        (:calc-btn ui)
        save-btn        (:save-btn ui)
        new-object-item (:new-object-item ui)
        about-item      (:about-item ui)
        rate-slider     (:rate-slider ui)
        rate-label      (:rate-label ui)
        objects-menu     (:objects-menu ui)
        new-object      (editable-state ui)
        saved-state     (atom new-object)
        objects         (store/load-objects)]
    (letfn [(activate-object! [object label]
              (restore-object! ui (merge new-object object))
              (gui/show-current-object! ui label)
              (reset! saved-state (editable-state ui)))
            (save-current! []
              (let [object-name (str/trim (.getText (:name-f ui)))]
                (if (str/blank? object-name)
                  (do
                    (gui/show-error!
                     (:frame ui)
                     "Objektnamn saknas"
                     "Ange ett namn för objektet innan du sparar det.")
                    (.requestFocusInWindow (:name-f ui))
                    false)
                  (when-let [calculation (calculate-ui! ui)]
                    (let [object (assoc (object-from-ui ui calculation)
                                        :name object-name)
                          saved-objects (store/save-object! object)]
                      (gui/show-link! (:listing-link ui) (:listing-url object))
                      (gui/show-current-object! ui object-name)
                      (reset! saved-state (editable-state ui))
                      (refresh-objects-menu!
                       objects-menu saved-objects switch-object!)
                      true)))))
            (may-leave-current? []
              (if (= @saved-state (editable-state ui))
                true
                (case (JOptionPane/showConfirmDialog
                       (:frame ui)
                       "Det finns osparade ändringar. Vill du spara dem först?"
                       "Osparade ändringar"
                       JOptionPane/YES_NO_CANCEL_OPTION
                       JOptionPane/WARNING_MESSAGE)
                  0 (boolean (save-current!))
                  1 true
                  false)))
            (switch-object! [object label]
              (when (may-leave-current?)
                (activate-object! object label)))
            (create-new-object! []
              (when (may-leave-current?)
                (activate-object! new-object "Nytt objekt")))]
      (refresh-objects-menu! objects-menu objects switch-object!)

      (cond
        (= 1 (count objects)) (activate-object! (first objects)
                                                (object-label 0 (first objects)))
        (< 1 (count objects)) (when-let [[object label]
                                         (choose-startup-object
                                          (:frame ui) objects)]
                                (activate-object! object label)))

      (update-rate-label! rate-label rate-slider)

      (.addActionListener
       calc-btn
       (reify java.awt.event.ActionListener
         (actionPerformed [_ _]
           (calculate-ui! ui))))

      (.addChangeListener
       rate-slider
       (reify javax.swing.event.ChangeListener
         (stateChanged [_ _]
           (update-rate-label! rate-label rate-slider)
           (when-not (.getValueIsAdjusting rate-slider)
             (calculate-ui! ui)))))

      (.addActionListener
       new-object-item
       (reify java.awt.event.ActionListener
         (actionPerformed [_ _]
           (create-new-object!))))

      (.addActionListener
       about-item
       (reify java.awt.event.ActionListener
         (actionPerformed [_ _]
           (gui/show-about! (:frame ui)))))

      (.addActionListener
       save-btn
       (reify java.awt.event.ActionListener
         (actionPerformed [_ _]
           (save-current!)))))))

(defn -main [& args]
  (SwingUtilities/invokeLater
   (reify java.lang.Runnable
     (run [_]
       (init-app)))))
