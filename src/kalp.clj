(ns kalp
  (:require [calculator :as calc]))

(def input-fields
  [[:net-income "Nettoinkomst, hela hushållet (kr/mån)" ""]
   [:benefits "Bidrag och övriga nettoinkomster (kr/mån)" "0"]
   [:loan "Bolån (kr)" ""]
   [:interest "Avtalad ränta (%)" ""]
   [:stress-interest "Kalkylränta för stresstest (%)" "7"]
   [:amortization "Amortering (kr/mån)" ""]
   [:fee "Månadsavgift (kr/mån)" ""]
   [:operating-cost "Driftskostnad (kr/mån)" ""]
   [:living-cost "Levnadskostnader, hela hushållet (kr/mån)" ""]
   [:other-debts "Andra lån inkl. CSN, ränta + amortering (kr/mån)" "0"]
   [:transport "Transport och bil (kr/mån)" "0"]
   [:childcare "Barnomsorg och underhåll (kr/mån)" "0"]
   [:other-cost "Övriga kostnader (kr/mån)" "0"]
   [:buffer "Önskat sparande / buffert (kr/mån)" "0"]
   [:borrowers "Antal låntagare med lika ränteandel" "1"]])

(defn calculate [{:keys [net-income benefits loan interest stress-interest
                         amortization fee operating-cost living-cost other-debts
                         transport childcare other-cost buffer borrowers
                         include-tax-reduction?] :as inputs}]
  (doseq [[key label _] input-fields]
    (let [value (get inputs key)]
      (when-not (and (number? value) (Double/isFinite (double value))
                     (not (neg? value)))
        (throw (IllegalArgumentException.
                (str label ": ange ett ändligt tal som inte är negativt."))))))
  (when-not (and (pos? borrowers) (<= borrowers Integer/MAX_VALUE)
                 (== borrowers (Math/floor borrowers)))
    (throw (IllegalArgumentException. "Antal låntagare måste vara ett positivt heltal.")))
  (when (< stress-interest interest)
    (throw (IllegalArgumentException. "Kalkylräntan får inte vara lägre än den avtalade räntan.")))
  (let [income (+ net-income benefits)
        fixed-housing (+ amortization fee operating-cost)
        expenses (+ living-cost other-debts transport childcare other-cost buffer)
        scenario (fn [rate]
                   (let [monthly-interest (/ (* loan rate) 1200.0)
                         reduction (if include-tax-reduction?
                                     (/ (calc/tax-reduction
                                         (* 12 monthly-interest) borrowers) 12.0)
                                     0.0)
                         housing (+ fixed-housing monthly-interest (- reduction))]
                     {:interest monthly-interest
                      :tax-reduction reduction
                      :housing housing
                      :remaining (- income housing expenses)}))]
    (let [actual (scenario interest)
          stress (scenario stress-interest)]
      (when-not (every? #(Double/isFinite (double %))
                        (concat [income expenses] (vals actual) (vals stress)))
        (throw (IllegalArgumentException. "Beloppen är för stora för att beräknas.")))
      {:income income :expenses expenses
       :actual actual :stress stress
       :inputs inputs})))
