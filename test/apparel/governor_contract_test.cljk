(ns apparel.governor-contract-test
  "The governor contract as executable tests -- this vertical's own
  scope boundary ('does NOT control sewing/cutting machines directly;
  does NOT finalize safety certificates') implemented faithfully."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [apparel.store :as store]
            [apparel.operation :as op]))

(defn- fresh []
  (let [db (-> (store/mem-store) (store/sample-data!))]
    [db (op/build db)]))

(def coordinator {:actor-id "coord-1" :actor-role :plant-coordinator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "coord-1"}} {:thread-id tid :resume? true}))

(defn- reject! [actor tid]
  (g/run* actor {:approval {:status :rejected :by "coord-1"}} {:thread-id tid :resume? true}))

(deftest clean-order-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :order-intake :effect :propose :subject "order-001"
                   :patch {:last-assessed "2026-08-01"}} coordinator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "2026-08-01" (:last-assessed (store/order db "order-001"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest pattern-spec-always-needs-approval
  (testing "pattern-spec is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2"
                    {:op :pattern-spec :effect :propose :subject "pattern-001"
                     :value {:size-code :L :style "cotton-shirt"}}
                    coordinator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (= :L (:size-code (store/pattern db "pattern-001"))))))))

(deftest effect-not-propose-is-held
  (testing "a request whose own :effect is not :propose -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :order-intake :effect :direct-write :subject "order-001"
                     :patch {:style "x"}} coordinator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)))
      (is (some #{:not-propose-effect} (-> (store/ledger db) first :basis))))))

(deftest unknown-op-is-held
  (let [[db actor] (fresh)
        res (exec-op actor "t4" {:op :actuate-sewing-machine :effect :propose :subject "x"} coordinator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:unknown-op} (-> (store/ledger db) first :basis)))))

(deftest order-not-verified-is-held-and-unoverridable
  (testing "coordinating a shipment against an unverified/unregistered order -> HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "t5"
                    {:op :shipment-coordinate :effect :propose :subject "ship-2"
                     :value {:order-id "order-003" :quantity 10
                             :destination "buyer-hakusen-warehouse"}}
                    coordinator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)))
      (is (some #{:order-not-verified} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest shipment-quantity-exceeded-is-held-and-unoverridable
  (testing "a shipment proposal whose quantity would exceed the order's own logged quantity -> HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "t6"
                    {:op :shipment-coordinate :effect :propose :subject "ship-3"
                     :value {:order-id "order-002" :quantity 50
                             :destination "buyer-nishijin-warehouse"}}
                    coordinator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)))
      (is (some #{:shipment-quantity-exceeded} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest sewing-control-is-held-and-permanently-blocked
  (testing "a proposal that sets :direct-operate? true -> HOLD, PERMANENT"
    (let [[db actor] (fresh)
          res (exec-op actor "t7"
                    {:op :order-intake :effect :propose :subject "order-001"
                     :patch {:direct-operate? true :style "force-run"}}
                    coordinator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:sewing-control-blocked} (-> (store/ledger db) last :basis))))))

(deftest safety-cert-decision-is-held-and-permanently-blocked
  (testing "a proposal that sets :safety-cert-finalized? true -> HOLD, PERMANENT"
    (let [[db actor] (fresh)
          res (exec-op actor "t8"
                    {:op :quality-flag :effect :propose :subject "flag-9"
                     :value {:order-id "order-001" :concern-type :safety
                             :severity :high :safety-cert-finalized? true}}
                    coordinator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:safety-cert-decision-blocked} (-> (store/ledger db) last :basis)))
      (is (empty? (store/quality-flags db))))))

(deftest invalid-size-code-is-held
  (let [[db actor] (fresh)
        res (exec-op actor "t9" {:op :pattern-spec :effect :propose :subject "pattern-001"
                                  :value {:size-code :mega-plus-select}} coordinator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:invalid-size-code} (-> (store/ledger db) last :basis)))
    (is (not= :mega-plus-select (:size-code (store/pattern db "pattern-001"))))))

(deftest quality-flag-always-escalates-even-high-confidence
  (testing "quality-flag always escalates -- never auto-committed, regardless of confidence"
    (let [[db actor] (fresh)
          res (exec-op actor "t10" {:op :quality-flag :effect :propose :subject "flag-1"
                                    :value {:order-id "order-001" :concern-type :labeling
                                            :severity :moderate}}
                       coordinator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t10")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (= 1 (count (store/quality-flags db))))))))

(deftest quality-flag-approval-rejected-leaves-no-record-only-a-hold-fact
  (let [[db actor] (fresh)
        _ (exec-op actor "t11" {:op :quality-flag :effect :propose :subject "flag-2"
                                :value {:order-id "order-001" :concern-type :stitching
                                        :severity :low}}
                   coordinator)
        r (reject! actor "t11")]
    (is (= :hold (get-in r [:state :disposition])))
    (is (= 0 (count (store/quality-flags db))) "rejected approval never reaches the commit node")
    (is (= 1 (count (store/ledger db))))))

(deftest coordinate-shipment-always-needs-approval
  (testing "a CLEAN shipment coordination is never auto-eligible -- always escalates"
    (let [[db actor] (fresh)
          res (exec-op actor "t12" {:op :shipment-coordinate :effect :propose :subject "ship-1"
                                    :value {:order-id "order-001" :quantity 50
                                            :destination "buyer-acme-warehouse"}}
                       coordinator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t12")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (= 1 (count (store/shipment-history db))))))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N settled operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :order-intake :effect :propose :subject "order-001"
                          :patch {:last-assessed "2026-08-01"}} coordinator)
      (exec-op actor "b" {:op :order-intake :effect :propose :subject "order-001"
                          :patch {:direct-operate? true}} coordinator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
