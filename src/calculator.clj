(ns calculator
  (:require [clojure.string :as str]))

(defn money [value]
  (-> (format "%.2f" (double value)) (str/replace "." ",")))

(defn percent [value]
  (-> (format "%.2f" (double value)) (str/replace "." ",")))

(def tax-reduction-low 0.30)
(def tax-reduction-high 0.21)
(def tax-reduction-threshold 100000.0)

(defn annual-interest-on-declining-balance
  [loan annual-interest monthly-amortization]
  (reduce +
          (map (fn [month]
                 (* (max 0.0 (- loan (* month monthly-amortization)))
                    (/ annual-interest 12.0)))
               (range 12))))

(defn tax-reduction
  [annual-interest-cost borrower-count]
  (let [borrowers (max 1 (int borrower-count))
        interest-per-borrower (/ annual-interest-cost borrowers)]
    (* borrowers
       (+ (* (min interest-per-borrower tax-reduction-threshold)
             tax-reduction-low)
          (* (max 0.0 (- interest-per-borrower tax-reduction-threshold))
             tax-reduction-high)))))

(defn calculate-loan
  ([purchase-price down-payment annual-interest-percent monthly-fee
    monthly-operating-cost annual-income tax-low-percent tax-high-percent extra-amortization?]
   (calculate-loan purchase-price down-payment annual-interest-percent monthly-fee
                   monthly-operating-cost annual-income tax-low-percent tax-high-percent
                   extra-amortization? 1))
  ([purchase-price down-payment annual-interest-percent monthly-fee
    monthly-operating-cost annual-income _tax-low-percent _tax-high-percent
    extra-amortization? borrower-count]
   (let [loan (- purchase-price down-payment)
         annual-interest (/ annual-interest-percent 100.0)
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
         annual-interest-cost (annual-interest-on-declining-balance
                               loan annual-interest monthly-amortization)
         monthly-interest-cost (/ annual-interest-cost 12.0)
         annual-tax-reduction (tax-reduction annual-interest-cost borrower-count)
         monthly-tax-reduction (/ annual-tax-reduction 12.0)
         monthly-interest-after-tax (- monthly-interest-cost monthly-tax-reduction)
         monthly-payment-before-tax (+ monthly-interest-cost monthly-amortization monthly-fee monthly-operating-cost)
         monthly-payment-after-tax (+ monthly-interest-after-tax monthly-amortization monthly-fee monthly-operating-cost)]
     {:loan loan :loan-to-value loan-to-value :debt-to-income debt-to-income
      :borrower-count borrower-count
      :monthly-interest-cost monthly-interest-cost :monthly-tax-reduction monthly-tax-reduction
      :monthly-interest-after-tax monthly-interest-after-tax :monthly-amortization monthly-amortization
      :basic-amortization-percent basic-amortization-percent :extra-amortization-percent extra-amortization-percent
      :total-amortization-percent total-amortization-percent :monthly-payment-before-tax monthly-payment-before-tax
      :monthly-payment-after-tax monthly-payment-after-tax})))

(defn maximum-purchase-price
  ([maximum-monthly-cost down-payment annual-interest-percent monthly-fee
    monthly-operating-cost annual-income tax-low-percent tax-high-percent
    extra-amortization?]
   (maximum-purchase-price maximum-monthly-cost down-payment annual-interest-percent
                           monthly-fee monthly-operating-cost annual-income
                           tax-low-percent tax-high-percent extra-amortization? 1))
  ([maximum-monthly-cost down-payment annual-interest-percent monthly-fee
    monthly-operating-cost annual-income tax-low-percent tax-high-percent
    extra-amortization? borrower-count]
   (let [monthly-cost (fn [purchase-price]
                        (:monthly-payment-after-tax
                         (calculate-loan
                          purchase-price down-payment annual-interest-percent
                          monthly-fee monthly-operating-cost annual-income
                          tax-low-percent tax-high-percent extra-amortization?
                          borrower-count)))
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
                 (recur lower-price middle-price (inc iteration)))))))))))
