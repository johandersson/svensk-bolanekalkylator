(ns test-clojure.calculator-test
	(:require [calculator :as calculator]
						[clojure.test :refer [deftest is testing]]))

(def calculation-parameters
	{:down-payment 600000.0
	 :interest 4.0
	 :monthly-fee 4500.0
	 :operating-cost 1200.0
	 :annual-income 720000.0
	 :tax-low 30.0
	 :tax-high 21.0
	 :extra-amortization? true})

(defn monthly-cost [purchase-price]
	(let [{:keys [down-payment interest monthly-fee operating-cost annual-income
								tax-low tax-high extra-amortization?]} calculation-parameters]
		(:monthly-payment-after-tax
		 (calculator/calculate-loan
			purchase-price down-payment interest monthly-fee operating-cost
			annual-income tax-low tax-high extra-amortization?))))

(deftest maximum-purchase-price-test
	(let [{:keys [down-payment interest monthly-fee operating-cost annual-income
								tax-low tax-high extra-amortization?]} calculation-parameters
				maximum-cost 15000.0
				purchase-price (calculator/maximum-purchase-price
												maximum-cost down-payment interest monthly-fee
												operating-cost annual-income tax-low tax-high
												extra-amortization?)]
		(testing "finds the highest whole-krona purchase price within the limit"
			(is (<= (monthly-cost purchase-price) maximum-cost))
			(is (> (monthly-cost (inc purchase-price)) maximum-cost)))
		(testing "keeps the fixed costs and down payment unchanged"
			(is (>= purchase-price down-payment)))
		(testing "returns nil when fixed monthly costs exceed the limit"
			(is (nil? (calculator/maximum-purchase-price
								 5000.0 down-payment interest monthly-fee operating-cost
								 annual-income tax-low tax-high extra-amortization?))))))