(ns main
  (:gen-class)
  (:require [calculator :as calc]
            [storage :as store]
            [gui :as gui]
            [clojure.string :as str])
  (:import [javax.swing SwingUtilities]))

(defn parse-number [field]
  (Double/parseDouble
    (str/replace (.getText field) "," ".")))

(defn restore-field! [field settings key]
  (when-let [value (get settings key)]
    (.setText field (str value))))

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
          " kr/mån\n")))

    (catch Exception e
      (.setText
        res
        (str "Fel: " (.getMessage e))))))

(defn init-app []
  (let [ui              (gui/create-ui)
        res             (:results ui)
        calc-btn        (:calc-btn ui)
        save-btn        (:save-btn ui)

        p-price-f       (:p-price-f ui)
        d-pay-f         (:d-pay-f ui)
        fee-f           (:fee-f ui)
        op-cost-f       (:op-cost-f ui)
        income-f        (:income-f ui)
        t-low-f         (:t-low-f ui)
        t-high-f        (:t-high-f ui)

        rate-slider     (:rate-slider ui)
        rate-label      (:rate-label ui)
        extra-amort-cb  (:extra-amort-cb ui)]

    ;; Restore saved settings
    (let [settings (store/load-settings)]
      (restore-field! p-price-f settings :purchase-price)
      (restore-field! d-pay-f settings :down-payment)
      (restore-field! fee-f settings :monthly-fee)
      (restore-field! op-cost-f settings :monthly-operating-cost)
      (restore-field! income-f settings :annual-income)
      (restore-field! t-low-f settings :tax-low-percent)
      (restore-field! t-high-f settings :tax-high-percent)

      (when-let [value (get settings :interest-slider-value)]
        (.setValue rate-slider (int value)))

      (when-let [value (get settings :extra-amortization?)]
        (.setSelected extra-amort-cb (boolean value))))

    ;; Set the initial interest label
    (update-rate-label! rate-label rate-slider)

    ;; Calculate button
    (.addActionListener
      calc-btn
      (reify java.awt.event.ActionListener
        (actionPerformed [_ _]
          (calculate-and-display!
            res
            p-price-f
            d-pay-f
            fee-f
            op-cost-f
            income-f
            t-low-f
            t-high-f
            rate-slider
            extra-amort-cb))))

    ;; Interest slider
    (.addChangeListener
      rate-slider
      (reify javax.swing.event.ChangeListener
        (stateChanged [_ _]
          ;; Update the label immediately while dragging
          (update-rate-label! rate-label rate-slider)

          ;; Recalculate only after the user releases the slider.
          ;; Remove the when-not condition for continuous recalculation.
          (when-not (.getValueIsAdjusting rate-slider)
            (calculate-and-display!
              res
              p-price-f
              d-pay-f
              fee-f
              op-cost-f
              income-f
              t-low-f
              t-high-f
              rate-slider
              extra-amort-cb)))))

    ;; Save button
    (.addActionListener
      save-btn
      (reify java.awt.event.ActionListener
        (actionPerformed [_ _]
          (store/save-settings!
            {:purchase-price
             (.getText p-price-f)

             :down-payment
             (.getText d-pay-f)

             :monthly-fee
             (.getText fee-f)

             :monthly-operating-cost
             (.getText op-cost-f)

             :annual-income
             (.getText income-f)

             :tax-low-percent
             (.getText t-low-f)

             :tax-high-percent
             (.getText t-high-f)

             :interest-slider-value
             (.getValue rate-slider)

             :extra-amortization?
             (.isSelected extra-amort-cb)}))))))

(defn -main [& args]
  (SwingUtilities/invokeLater
    (reify java.lang.Runnable
      (run [_]
        (init-app)))))
