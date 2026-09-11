(ns apparel.operation-test
  "Smoke tests for the compiled ApparelOperationActor graph itself
  (build + one happy path per op)."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [apparel.operation :as op]
            [apparel.store :as store]))

(def coordinator {:actor-id "coord-1" :actor-role :plant-coordinator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "coord-1"}} {:thread-id tid :resume? true}))

(deftest test-actor-builds
  (testing "ApparelOperationActor can be built with a store"
    (let [s (store/mem-store)
          actor (op/build s)]
      (is (not (nil? actor))))))

(deftest test-order-intake-auto-commits
  (testing "Proposing an order intake auto-commits when clean (phase 3, no physical risk)"
    (let [s (-> (store/mem-store) (store/sample-data!))
          actor (op/build s)
          initial-ledger-size (count (store/get-ledger s))
          result (exec-op actor "t1"
                          {:op :order-intake :effect :propose :subject "order-001"
                           :patch {:last-assessed "2026-08-01"}}
                          coordinator)
          final-ledger-size (count (store/get-ledger s))]
      (is (> final-ledger-size initial-ledger-size))
      (is (= :commit (get-in result [:state :disposition]))))))

(deftest test-pattern-spec-scheduling
  (testing "Pattern-spec always escalates for human approval"
    (let [s (-> (store/mem-store) (store/sample-data!))
          actor (op/build s)
          result (exec-op actor "t2"
                          {:op :pattern-spec :effect :propose :subject "pattern-001"
                           :value {:size-code :L}}
                          coordinator)]
      (is (= :interrupted (:status result)))
      (is (= :commit (get-in (approve! actor "t2") [:state :disposition]))))))

(deftest test-quality-flag-escalation
  (testing "Quality flags always escalate"
    (let [s (-> (store/mem-store) (store/sample-data!))
          actor (op/build s)
          result (exec-op actor "t3"
                          {:op :quality-flag :effect :propose :subject "flag-1"
                           :value {:order-id "order-001" :concern-type :labeling
                                   :severity :moderate :description "label mismatch"}}
                          coordinator)]
      (is (= :interrupted (:status result))))))

(deftest test-shipment-coordination-proposal
  (testing "Shipment coordination proposal escalates for approval when within quantity"
    (let [s (-> (store/mem-store) (store/sample-data!))
          actor (op/build s)
          result (exec-op actor "t4"
                          {:op :shipment-coordinate :effect :propose :subject "ship-1"
                           :value {:order-id "order-001" :quantity 50
                                   :destination "buyer-acme-warehouse"}}
                          coordinator)]
      (is (some? result))
      (is (= :interrupted (:status result))))))

(deftest test-ledger-is-append-only
  (testing "Audit ledger is append-only"
    (let [s (store/mem-store)
          initial-count (count (store/get-ledger s))]
      (store/append-ledger! s {:t :test-entry})
      (is (= (inc initial-count) (count (store/get-ledger s)))))))
