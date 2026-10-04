(ns main
  (:gen-class)
  (:require [calculator :as calc]
            [storage :as store]
            [gui :as gui]
            [kalp :as kalp]
            [clojure.string :as str])
  (:import [javax.swing JMenuItem JOptionPane SwingUtilities Timer]))

(defn parse-number
  ([field]
   (parse-number field "fältet"))
  ([field field-name]
   (let [value (str/trim (.getText field))]
     (when (str/blank? value)
       (throw (IllegalArgumentException.
               (str "Fyll i " field-name "."))))
     (try
       (Double/parseDouble (str/replace value "," "."))
       (catch NumberFormatException _
         (throw (IllegalArgumentException.
                 (str "Ange ett giltigt tal för " field-name "."))))))))

(defn parse-number-text [value]
  (Double/parseDouble (str/replace value "," ".")))

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
   _t-low-f
   _t-high-f
   rate-slider
   extra-amort-cb
   borrower-count-cb]

  (try
    (let [extra-amortization? (.isSelected extra-amort-cb)
          p-price  (parse-number p-price-f "köpeskilling")
          d-pay    (parse-number d-pay-f "kontantinsats")
          m-fee    (parse-number fee-f "månadsavgift")
          m-op     (parse-number op-cost-f "driftskostnad")
          inc      (if extra-amortization?
                     (parse-number income-f "hushållets bruttoinkomst")
                     0.0)
          interest (slider-interest rate-slider)
          data     (calc/calculate-loan
                    p-price
                    d-pay
                    interest
                    m-fee
                    m-op
                    inc
                    (* 100.0 calc/tax-reduction-low)
                    (* 100.0 calc/tax-reduction-high)
                    extra-amortization?
                    (int (.getSelectedItem borrower-count-cb)))]

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

        "Beräknad skattereduktion:    "
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
        " kr/mån\n\n"

        "Prognosen antar ett bolån med bostaden som säkerhet,\n"
        (int (.getSelectedItem borrower-count-cb))
        " låntagare med jämn räntefördelning och tillräcklig skatt.\n"))
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
   [:income-f :annual-income]])

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
   (:extra-amort-cb ui)
   (:borrower-count-cb ui)))

(defn calculate-and-present! [ui]
  (if-let [calculation (calculate-ui! ui)]
    (gui/show-calculation!
     (:frame ui)
     calculation
     {:object-name (.getText (:name-f ui))
      :address (.getText (:address-f ui))
      :purchase-price (parse-number (:p-price-f ui))
      :down-payment (parse-number (:d-pay-f ui))
      :interest (slider-interest (:rate-slider ui))
      :monthly-fee (parse-number (:fee-f ui))
      :monthly-operating-cost (parse-number (:op-cost-f ui))})
    (gui/show-error! (:frame ui) "Kontrollera uppgifterna"
                     (.getText (:results ui)))))

(defn apply-maximum-cost! [ui]
  (when-let [input (gui/ask-maximum-cost! (:frame ui))]
    (try
      (let [maximum-cost (parse-number-text input)]
        (if-not (pos? maximum-cost)
          (gui/show-error! (:frame ui)
                           "Ogiltig månadsgräns"
                           "Ange ett belopp som är större än noll.")
          (let [extra-amortization? (.isSelected (:extra-amort-cb ui))
                annual-income (if extra-amortization?
                                (parse-number
                                 (:income-f ui)
                                 "hushållets bruttoinkomst")
                                0.0)
                original-purchase-price
                (parse-number (:p-price-f ui) "köpeskilling")]
            (if-let [purchase-price
                     (calc/maximum-purchase-price
                      maximum-cost
                      (parse-number (:d-pay-f ui) "kontantinsats")
                      (slider-interest (:rate-slider ui))
                      (parse-number (:fee-f ui) "månadsavgift")
                      (parse-number (:op-cost-f ui) "driftskostnad")
                      annual-income
                      (* 100.0 calc/tax-reduction-low)
                      (* 100.0 calc/tax-reduction-high)
                      extra-amortization?
                      (int (.getSelectedItem (:borrower-count-cb ui))))]
              (if (< purchase-price original-purchase-price)
                (gui/show-info!
                 (:frame ui)
                 "Ingen ändring gjordes"
                 (str "Den angivna månadsgränsen skulle sänka "
                      "köpeskillingen. Den befintliga kalkylen har behållits."))
                (do
                  (.setText (:p-price-f ui) (format "%.0f" purchase-price))
                  (calculate-ui! ui)))
              (gui/show-error!
               (:frame ui)
               "Månadsgränsen är för låg"
               "Gränsen täcker inte månadsavgiften och driftskostnaden.")))))
      (catch Exception _
        (gui/show-error!
         (:frame ui)
         "Kontrollera uppgifterna"
         "Ange giltiga belopp i månadsgränsen och kalkylens obligatoriska fält.")))))

(defn prefill-kalp! [ui reset?]
  (when-let [kalp-ui (:kalp ui)]
    (let [source {:name (.getText (:name-f ui))
                  :address (.getText (:address-f ui))
                  :purchase-price (.getText (:p-price-f ui))
                  :down-payment (.getText (:d-pay-f ui))
                  :fee (.getText (:fee-f ui))
                  :operating-cost (.getText (:op-cost-f ui))
                  :income (.getText (:income-f ui))
                  :interest (slider-interest (:rate-slider ui))
                  :extra? (.isSelected (:extra-amort-cb ui))
                  :borrowers (.getSelectedItem (:borrower-count-cb ui))}]
      (when (or reset? (not= source @(:source-state kalp-ui)))
        (gui/show-kalp-source! kalp-ui (:name source))
        (when reset?
          (doseq [[key _ default] kalp/input-fields]
            (.setText (get (:fields kalp-ui) key) default))
          (.setSelected (:tax-cb kalp-ui) false))
        (let [calculation (calculate-ui! ui)
              values {:loan (if calculation (str (:loan calculation)) "")
                      :amortization (if calculation
                                      (str (:monthly-amortization calculation)) "")
                      :interest (str (:interest source))
                      :fee (:fee source)
                      :operating-cost (:operating-cost source)
                      :borrowers (str (:borrowers source))}]
          (doseq [[key value] values]
            (.setText (get (:fields kalp-ui) key) value)))
        (reset! (:source-state kalp-ui) source)
        (gui/clear-kalp-result! kalp-ui)))))

(defn calculate-kalp! [ui]
  (let [kalp-ui (:kalp ui)]
    (gui/clear-kalp-result! kalp-ui)
    (try
      (let [inputs (into {} (map (fn [[key label _]]
                                   [key (parse-number
                                         (get (:fields kalp-ui) key) label)])
                                 kalp/input-fields))
            data (kalp/calculate
                  (assoc inputs :include-tax-reduction?
                         (.isSelected (:tax-cb kalp-ui))))]
        (gui/show-kalp-result! kalp-ui data)
        data)
      (catch IllegalArgumentException e
        (gui/show-error! (:frame ui) "Kontrollera KALP-uppgifterna"
                         (.getMessage e))
        nil))))

(defn restore-object! [ui object]
  (doseq [[field-key object-key] field-keys]
    (restore-field! (get ui field-key) object object-key))
  (gui/show-link! (:listing-link ui) (:listing-url object))

  (when-let [value (:interest-slider-value object)]
    (.setValue (:rate-slider ui) (int value)))

  (when (contains? object :extra-amortization?)
    (.setSelected (:extra-amort-cb ui)
                  (boolean (:extra-amortization? object))))

  (.setSelectedItem (:borrower-count-cb ui)
                    (Integer/valueOf (int (get object :borrower-count 1))))

  (update-rate-label! (:rate-label ui) (:rate-slider ui))
  (calculate-ui! ui)
  (prefill-kalp! ui true))

(defn editable-state [ui]
  (merge
   (into {}
         (map (fn [[field-key object-key]]
                [object-key (.getText (get ui field-key))])
              field-keys))
   {:interest-slider-value (.getValue (:rate-slider ui))
    :borrower-count (int (.getSelectedItem (:borrower-count-cb ui)))
    :extra-amortization? (.isSelected (:extra-amort-cb ui))}))

(defn object-from-ui [ui calculation]
  (assoc (editable-state ui)
         :calculation calculation
         :result-text (.getText (:results ui))))

(defn autosave-open-object! [opened-object current-state]
  (let [updated-object (merge opened-object current-state)]
    (store/replace-object! opened-object updated-object)
    updated-object))

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
    (when (int? selected-index)
      [(nth objects selected-index)
       (nth labels selected-index)])))

(defn init-app []
  (let [ui               (gui/create-ui)
        calc-btn         (:calc-btn ui)
        maximum-cost-btn (:maximum-cost-btn ui)
        save-btn         (:save-btn ui)
        delete-btn       (:delete-btn ui)
        new-object-item  (:new-object-item ui)
        about-item       (:about-item ui)
        rate-slider      (:rate-slider ui)
        rate-label       (:rate-label ui)
        objects-menu     (:objects-menu ui)
        new-object       (editable-state ui)
        saved-state      (atom new-object)
        saved-object     (atom nil)
        autosave-timer   (Timer. 5000 nil)
        objects          (store/load-objects)]
    (letfn [(activate-object! [object label persisted-object]
              (restore-object! ui (merge new-object object))
              (gui/show-current-object! ui label)
              (reset! saved-object persisted-object)
              (.setEnabled delete-btn (some? persisted-object))
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
                      (reset! saved-object object)
                      (.setEnabled delete-btn true)
                      (reset! saved-state (editable-state ui))
                      (refresh-objects-menu!
                       objects-menu saved-objects switch-object!)
                      (gui/show-toast! (:frame ui) "Sparat!")
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
                (activate-object! object label object)))
            (create-new-object! []
              (when (may-leave-current?)
                (activate-object! new-object "Nytt objekt" nil)))
            (delete-current! []
              (when-let [object @saved-object]
                (let [label (object-label 0 object)]
                  (when (gui/confirm-delete! (:frame ui) label)
                    (let [remaining-objects (store/delete-object! object)]
                      (refresh-objects-menu!
                       objects-menu remaining-objects switch-object!)
                      (activate-object! new-object "Nytt objekt" nil)
                      (gui/show-toast! (:frame ui) "Raderat!"))))))]
      (refresh-objects-menu! objects-menu objects switch-object!)
      (gui/bind-save-shortcut! (:frame ui) save-current!)
      (.addActionListener
       autosave-timer
       (reify java.awt.event.ActionListener
         (actionPerformed [_ _]
           (when-let [opened-object @saved-object]
             (let [current-state (editable-state ui)]
               (when-not (= @saved-state current-state)
                 (let [updated-object
                       (autosave-open-object! opened-object current-state)]
                   (reset! saved-object updated-object)
                   (reset! saved-state current-state)
                   (refresh-objects-menu!
                    objects-menu (store/load-objects) switch-object!))))))))
      (.start autosave-timer)

      (cond
        (= 1 (count objects)) (activate-object! (first objects)
                                                (object-label 0 (first objects))
                                                (first objects))
        (< 1 (count objects)) (when-let [[object label]
                                         (choose-startup-object
                                          (:frame ui) objects)]
                                (activate-object! object label object)))

      (update-rate-label! rate-label rate-slider)

      (.addChangeListener
       (:tabs ui)
       (reify javax.swing.event.ChangeListener
         (stateChanged [_ _]
           (when (= 1 (.getSelectedIndex (:tabs ui)))
             (prefill-kalp! ui false)))))

      (.addActionListener
       (:calc-btn (:kalp ui))
       (reify java.awt.event.ActionListener
         (actionPerformed [_ _]
           (calculate-kalp! ui))))

      (.addActionListener
       calc-btn
       (reify java.awt.event.ActionListener
         (actionPerformed [_ _]
           (calculate-and-present! ui))))

      (.addActionListener
       maximum-cost-btn
       (reify java.awt.event.ActionListener
         (actionPerformed [_ _]
           (apply-maximum-cost! ui))))

      (.addChangeListener
       rate-slider
       (reify javax.swing.event.ChangeListener
         (stateChanged [_ _]
           (update-rate-label! rate-label rate-slider)
           (when-not (.getValueIsAdjusting rate-slider)
             (calculate-ui! ui)))))

      (.addActionListener
       (:borrower-count-cb ui)
       (reify java.awt.event.ActionListener
         (actionPerformed [_ _]
           (calculate-ui! ui))))

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
           (save-current!))))

      (.addActionListener
       delete-btn
       (reify java.awt.event.ActionListener
         (actionPerformed [_ _]
           (delete-current!)))))))

(defn -main [& _args]
  (SwingUtilities/invokeLater
   (reify java.lang.Runnable
     (run [_]
       (init-app)))))
