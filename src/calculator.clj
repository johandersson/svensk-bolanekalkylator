(ns calculator
  (:require [clojure.string :as str]))

(defn money [value]
  (-> (format "%.2f" (double value)) (str/replace "." ",")))

(defn percent [value]
  (-> (format "%.2f" (double value)) (str/replace "." ",")))

(defn calculate-loan
  [purchase-price down-payment annual-interest-percent monthly-fee
   monthly-operating-cost annual-income tax-low-percent tax-high-percent extra-amortization?]
  (let [loan (- purchase-price down-payment)
        annual-interest (/ annual-interest-percent 100.0)
        annual-interest-cost (* loan annual-interest)
        monthly-interest-cost (/ annual-interest-cost 12.0)
        loan-to-value (if (zero? purchase-price) 0.0 (/ loan purchase-price))
        basic-amortization-percent (cond
                                     (> loan-to-value 0.70) 2.0
                                     (> loan-to-value 0.50) 1.0
                                     :else 0.0)

        debt-to-income (if (zero? annual-income) Double/POSITIVE_INFINITY (/ loan annual-income))
        extra-amortization-percent (if (and extra-amortization? (> debt-to-income 4.5)) 1.0 0.0)
        total-amortization-percent (+ basic-amortization-percent extra-amortization-percent)
        annual-amortization (* loan (/ total-amortization-percent 100.0))
        monthly-amortization (/ annual-amortization 12.0)
        tax-low (/ tax-low-percent 100.0)
        tax-high (/ tax-high-percent 100.0)
        tax-reduction (+ (* (min annual-interest-cost 100000.0) tax-low)
                         (* (max 0.0 (- annual-interest-cost 100000.0)) tax-high))
        monthly-tax-reduction (/ tax-reduction 12.0)
        monthly-interest-after-tax (- monthly-interest-cost monthly-tax-reduction)
        monthly-payment-before-tax (+ monthly-interest-cost monthly-amortization monthly-fee monthly-operating-cost)
        monthly-payment-after-tax (+ monthly-interest-after-tax monthly-amortization monthly-fee monthly-operating-cost)]
    {:loan loan :loan-to-value loan-to-value :debt-to-income debt-to-income
     :monthly-interest-cost monthly-interest-cost :monthly-tax-reduction monthly-tax-reduction
     :monthly-interest-after-tax monthly-interest-after-tax :monthly-amortization monthly-amortization
     :basic-amortization-percent basic-amortization-percent :extra-amortization-percent extra-amortization-percent
     :total-amortization-percent total-amortization-percent :monthly-payment-before-tax monthly-payment-before-tax
     :monthly-payment-after-tax monthly-payment-after-tax}))

(defn maximum-purchase-price
  [maximum-monthly-cost down-payment annual-interest-percent monthly-fee
   monthly-operating-cost annual-income tax-low-percent tax-high-percent
   extra-amortization?]
  (let [monthly-cost (fn [purchase-price]
                       (:monthly-payment-after-tax
                        (calculate-loan
                         purchase-price down-payment annual-interest-percent
                         monthly-fee monthly-operating-cost annual-income
                         tax-low-percent tax-high-percent extra-amortization?)))
        minimum-price (max 0.0 down-payment)]
    (when (<= (monthly-cost minimum-price) maximum-monthly-cost)
      (let [upper-price (loop [loan-size 1.0]
                          (let [purchase-price (+ minimum-price loan-size)]
                            (if (> (monthly-cost purchase-price)
                                   maximum-monthly-cost)
                              purchase-price
                              (recur (* loan-size 2.0)))))]
        (loop [lower-price minimum-price
               upper-price upper-price
               iteration 0]
          (if (= iteration 80)
            (Math/floor lower-price)
            (let [middle-price (/ (+ lower-price upper-price) 2.0)]
              (if (<= (monthly-cost middle-price) maximum-monthly-cost)
                (recur middle-price upper-price (inc iteration))
                (recur lower-price middle-price (inc iteration))))))))))
