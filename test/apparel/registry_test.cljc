(ns apparel.registry-test
  (:require [clojure.test :refer [deftest is]]
            [apparel.registry :as r]))

(deftest order-is-verified-when-flagged
  (is (true? (r/order-verified? {:id "o1" :verified? true}))))

(deftest order-is-not-verified-when-false-or-missing
  (is (false? (r/order-verified? {:id "o1" :verified? false})))
  (is (false? (r/order-verified? {:id "o1"}))))

(deftest order-is-registered-when-flagged
  (is (true? (r/order-registered? {:registered? true}))))

(deftest order-is-not-registered-when-false-or-missing
  (is (false? (r/order-registered? {:registered? false})))
  (is (false? (r/order-registered? {}))))

(deftest order-ready-requires-both
  (is (true? (r/order-ready? {:verified? true :registered? true})))
  (is (false? (r/order-ready? {:verified? true :registered? false})))
  (is (false? (r/order-ready? {:verified? false :registered? true})))
  (is (false? (r/order-ready? {}))))

(deftest pattern-ready-requires-both
  (is (true? (r/pattern-ready? {:verified? true :registered? true})))
  (is (false? (r/pattern-ready? {:verified? true :registered? false})))
  (is (false? (r/pattern-ready? {}))))

(deftest small-shipment-within-quantity-does-not-exceed
  (is (false? (r/shipment-quantity-exceeded?
               {:quantity 500 :shipped-quantity 100} 50))))

(deftest shipment-that-pushes-past-quantity-exceeds
  (is (true? (r/shipment-quantity-exceeded?
              {:quantity 300 :shipped-quantity 290} 50))))

(deftest shipment-exactly-at-quantity-does-not-exceed
  (is (false? (r/shipment-quantity-exceeded?
               {:quantity 300 :shipped-quantity 290} 10))
      "exactly at quantity is not over, only strictly beyond"))

(deftest missing-quantity-is-not-flagged-exceeded
  (is (false? (r/shipment-quantity-exceeded? {} 100)))
  (is (false? (r/shipment-quantity-exceeded? {:quantity 800} nil))))

(deftest known-size-codes-are-valid
  (doseq [g [:XS :S :M :L :XL :XXL :3XL :custom]]
    (is (r/size-code-valid? g))))

(deftest fabricated-size-code-is-invalid
  (is (not (r/size-code-valid? :mega-plus-select)))
  (is (not (r/size-code-valid? nil))))

(deftest shipment-is-a-draft-not-a-real-dispatch
  (let [result (r/register-shipment "ship-1" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest shipment-assigns-shipment-number
  (let [result (r/register-shipment "ship-1" 7)]
    (is (= (get result "shipment_number") "SHP-000007"))
    (is (= (get-in result ["record" "shipment_id"]) "ship-1"))
    (is (= (get-in result ["record" "kind"]) "shipment-coordination-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest shipment-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-shipment "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-shipment "ship-1" -1))))

(deftest quality-flag-is-a-draft
  (let [result (r/register-quality-flag "flag-1" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get result "flag_number") "QCF-000000"))
    (is (= (get-in result ["record" "kind"]) "quality-flag-draft"))))

(deftest history-is-append-only
  (let [c1 (r/register-shipment "ship-1" 0)
        hist (r/append [] c1)
        c2 (r/register-shipment "ship-2" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "SHP-000000" (get-in hist2 [0 "record_id"])))
    (is (= "SHP-000001" (get-in hist2 [1 "record_id"])))))
