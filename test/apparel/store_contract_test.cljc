(ns apparel.store-contract-test
  "The Store contract as executable tests. Single MemStore backend."
  (:require [clojure.test :refer [deftest is testing]]
            [apparel.store :as store]))

(defn- seeded [] (-> (store/mem-store) (store/sample-data!)))

(deftest sample-data-read-basics
  (let [s (seeded)]
    (is (true? (:verified? (store/order s "order-001"))))
    (is (true? (:registered? (store/order s "order-001"))))
    (is (true? (:verified? (store/order s "order-002"))))
    (is (false? (:verified? (store/order s "order-003"))))
    (is (false? (:registered? (store/order s "order-003"))))
    (is (= ["order-001" "order-002" "order-003"] (mapv :id (store/all-orders s))))
    (is (true? (:verified? (store/pattern s "pattern-001"))))
    (is (false? (:verified? (store/pattern s "pattern-002"))))
    (is (= ["pattern-001" "pattern-002"] (mapv :id (store/all-patterns s))))
    (is (= [] (store/ledger s)))
    (is (= [] (store/shipment-history s)))
    (is (= [] (store/quality-flags s)))
    (is (zero? (store/next-shipment-sequence s)))
    (is (zero? (store/next-quality-sequence s)))))

(deftest fresh-store-has-no-orders-or-patterns
  (let [s (store/mem-store)]
    (is (= [] (store/all-orders s)))
    (is (nil? (store/order s "order-001")))
    (is (= [] (store/all-patterns s)))
    (is (nil? (store/pattern s "pattern-001")))))

(deftest order-upsert-merges-preserving-untouched-fields
  (let [s (seeded)]
    (store/commit-record! s {:effect :order/upsert :path ["order-001"]
                             :value {:last-assessed "2026-08-01"}})
    (is (= "2026-08-01" (:last-assessed (store/order s "order-001"))))
    (is (true? (:verified? (store/order s "order-001"))) "unrelated field preserved")
    (is (true? (:registered? (store/order s "order-001"))) "unrelated field preserved")))

(deftest pattern-upsert-merges
  (let [s (seeded)]
    (store/commit-record! s {:effect :pattern/upsert :path ["pattern-001"]
                             :value {:size-code :L}})
    (is (= :L (:size-code (store/pattern s "pattern-001"))))
    (is (true? (:verified? (store/pattern s "pattern-001"))))))

(deftest quality-flag-appends
  (let [s (seeded)]
    (store/commit-record! s {:effect :quality/flag :path ["flag-1"]
                             :value {:order-id "order-001" :concern-type :labeling
                                     :severity :moderate}})
    (is (= 1 (count (store/quality-flags s))))
    (is (= :moderate (:severity (first (store/quality-flags s)))))
    (is (= "QCF-000000" (:flag-number (first (store/quality-flags s)))))
    (store/commit-record! s {:effect :quality/flag :path ["flag-2"]
                             :value {:order-id "order-002" :concern-type :stitching
                                     :severity :high}})
    (is (= 2 (count (store/quality-flags s))) "append-only")))

(deftest shipment-propose-commits-and-advances-sequence-and-order-quantity
  (let [s (seeded)]
    (store/commit-record! s {:effect :shipment/propose :path ["ship-1"]
                             :value {:order-id "order-001" :quantity 50
                                     :destination "buyer-acme-warehouse"}})
    (is (= "SHP-000000" (get (first (store/shipment-history s)) "record_id")))
    (is (= "shipment-coordination-draft" (get (first (store/shipment-history s)) "kind")))
    (is (= 1 (count (store/shipment-history s))))
    (is (= 1 (store/next-shipment-sequence s)))
    (is (= "SHP-000000" (:shipment-number (store/shipment s "ship-1"))))
    (is (= 150 (:shipped-quantity (store/order s "order-001")))
        "100 seeded + 50 committed")))

(deftest ledger-is-append-only-and-order-preserving
  (let [s (store/mem-store)]
    (store/append-ledger! s {:op :a :disposition :commit})
    (store/append-ledger! s {:op :b :disposition :hold})
    (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))

(deftest generic-commit-record-path-writes-a-raw-record-by-id
  (testing "a record with no :effect key is written verbatim into the generic records map"
    (let [s (store/mem-store)
          record {:id "test-001" :data "test"}]
      (store/commit-record! s record)
      (is (= record (get (store/get-records s) "test-001"))))))

(deftest get-ledger-alias-matches-ledger
  (let [s (store/mem-store)]
    (store/append-ledger! s {:t :x})
    (is (= (store/ledger s) (store/get-ledger s)))))
