(ns apparel.store-contract-test
  (:require [clojure.test :refer [deftest is]]
            [apparel.store :as store]))

(deftest ^{:doc "In-memory store should initialize with reference data."} mem-store-initialization
  (let [st (store/mem-store)]
    (is (map? st))
    (is (contains? st :data))))

(deftest ^{:doc "Store should provide plant lookups."} plant-accessors
  (let [st (store/mem-store)
        plant (store/plant st "plant-001")]
    (is (map? plant))
    (is (= (:name plant) "Community Apparel Factory A"))
    (is (= (:location plant) "Vietnam"))))

(deftest ^{:doc "Store should provide batch lookups."} production-batch-accessors
  (let [st (store/mem-store)
        batch (store/production-batch st "batch-001")]
    (is (map? batch))
    (is (= (:style batch) "cotton-shirt-XL"))
    (is (= (:quantity batch) 500))
    (is (= (:plant batch) "plant-001"))))

(deftest ^{:doc "Store should provide shipment lookups."} shipment-accessors
  (let [st (store/mem-store)
        shipment (store/shipment st "ship-001")]
    (is (map? shipment))
    (is (= (:batch shipment) "batch-001"))
    (is (= (:qty shipment) 500))))

(deftest ^{:doc "Store should provide equipment lookups."} equipment-accessors
  (let [st (store/mem-store)
        equipment (store/equipment st "maint-001")]
    (is (map? equipment))
    (is (= (:equipment equipment) "cutting-machine-03"))
    (is (= (:status equipment) :operational))))

(deftest ^{:doc "Plant verification guard should check registration status."} plant-verified-guard
  (let [st (store/mem-store)]
    (is (store/plant-verified? st "plant-001"))
    (is (not (store/plant-verified? st "plant-unknown")))))

(deftest ^{:doc "Batch verification guard should check verification status."} batch-verified-guard
  (let [st (store/mem-store)]
    (is (store/batch-verified? st "batch-001"))
    (is (not (store/batch-verified? st "batch-002")))))

(deftest ^{:doc "Batch-plant verification should check both batch and its plant."} batch-plant-verified-guard
  (let [st (store/mem-store)]
    (is (store/batch-plant-verified? st "batch-001"))
    ;; batch-002's plant is verified, but we can test with a non-existent batch
    (is (not (store/batch-plant-verified? st "batch-unknown")))))

(deftest ^{:doc "Accessors should handle missing records gracefully."} missing-records
  (let [st (store/mem-store)]
    (is (nil? (store/plant st "nonexistent")))
    (is (nil? (store/production-batch st "nonexistent")))
    (is (nil? (store/shipment st "nonexistent")))
    (is (nil? (store/equipment st "nonexistent")))))
