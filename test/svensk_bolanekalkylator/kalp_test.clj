(ns svensk-bolanekalkylator.kalp-test
  (:require [clojure.test :refer [deftest is testing]]
            [kalp :as kalp]))

(def inputs
  {:net-income 40000.0 :benefits 2500.0 :loan 2400000.0
   :interest 4.0 :stress-interest 7.0 :amortization 4000.0
   :fee 4000.0 :operating-cost 1000.0 :living-cost 12000.0
   :other-debts 1000.0 :transport 2000.0 :childcare 1000.0
   :other-cost 500.0 :buffer 2000.0 :borrowers 2.0
   :include-tax-reduction? false})

(deftest monthly-budget-and-stress-test
  (let [result (kalp/calculate inputs)]
    (is (= 42500.0 (:income result)))
    (is (= 18500.0 (:expenses result)))
    (is (= {:interest 8000.0 :tax-reduction 0.0
            :housing 17000.0 :remaining 7000.0}
           (:actual result)))
    (is (= {:interest 14000.0 :tax-reduction 0.0
            :housing 23000.0 :remaining 1000.0}
           (:stress result)))))

(deftest tax-reduction-is-optional-and-per-borrower
  (let [result (kalp/calculate (assoc inputs :include-tax-reduction? true))]
    (is (= 4200.0 (get-in result [:stress :tax-reduction])))
    (is (= 5200.0 (get-in result [:stress :remaining]))))
  (let [result (kalp/calculate
                (assoc inputs :borrowers 1.0 :include-tax-reduction? true))]
    (is (= 3690.0 (get-in result [:stress :tax-reduction])))))

(deftest validates-every-input
  (doseq [[key _ _] kalp/input-fields
          value [-1.0 Double/NaN Double/POSITIVE_INFINITY nil]]
    (is (thrown? IllegalArgumentException
                 (kalp/calculate (assoc inputs key value)))))
  (doseq [value [0.0 1.5 (double (inc Integer/MAX_VALUE))]]
    (is (thrown? IllegalArgumentException
                 (kalp/calculate (assoc inputs :borrowers value)))))
  (is (thrown? IllegalArgumentException
               (kalp/calculate (assoc inputs :stress-interest 3.0))))
  (is (thrown? IllegalArgumentException
               (kalp/calculate (assoc inputs :loan Double/MAX_VALUE)))))

(deftest budget-boundaries
  (testing "negative margins are displayed, not clamped"
    (is (= -9000.0 (get-in (kalp/calculate (assoc inputs :net-income 30000.0))
                           [:stress :remaining]))))
  (testing "zero loan and zero interest"
    (is (= 0.0 (get-in (kalp/calculate
                        (assoc inputs :loan 0.0 :interest 0.0 :stress-interest 0.0))
                       [:actual :interest])))))
