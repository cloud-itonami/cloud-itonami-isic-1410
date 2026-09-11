(ns apparel.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean apparel plant
  through order intake -> pattern-spec (escalate/approve) ->
  quality-flag (escalate/approve) -> shipment coordination
  (escalate/approve), then shows HARD-hold scenarios: a mis-wired
  request whose own `:effect` is not `:propose`, an unrecognized op,
  a shipment against an UNVERIFIED order, a shipment that would exceed
  the order's own logged quantity, a proposal that tries to directly
  operate a sewing machine (permanently blocked), a quality-flag that
  tries to finalize a safety cert (permanently blocked), and a
  pattern-spec with a fabricated size-code."
  (:require [langgraph.graph :as g]
            [apparel.store :as store]
            [apparel.operation :as op]))

(def coordinator {:actor-id "coord-1" :actor-role :plant-coordinator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "coord-1"}} {:thread-id tid :resume? true}))

(defn -main [& _args]
  (let [db (-> (store/mem-store) (store/sample-data!))
        actor (op/build db)]

    (println "== order-intake order-001 (clean patch -> phase-3 auto-commit) ==")
    (println (exec-op actor "t1"
                       {:op :order-intake :effect :propose :subject "order-001"
                        :patch {:last-assessed "2026-08-01" :style "cotton-shirt-XL"}}
                       coordinator))

    (println "== pattern-spec pattern-001 (size-spec metadata -- escalates, approve) ==")
    (let [r (exec-op actor "t2"
                      {:op :pattern-spec :effect :propose :subject "pattern-001"
                       :value {:size-code :L :style "cotton-shirt"}}
                      coordinator)]
      (println r)
      (println "-- human pattern-room supervisor approves --")
      (println (approve! actor "t2")))

    (println "== quality-flag flag-1 on order-001 (always escalates -- approve) ==")
    (let [r (exec-op actor "t3"
                      {:op :quality-flag :effect :propose :subject "flag-1"
                       :value {:order-id "order-001" :concern-type :labeling
                               :severity :moderate
                               :description "繊維組成ラベル表記が仕様と不一致"}}
                      coordinator)]
      (println r)
      (println "-- human plant supervisor approves --")
      (println (approve! actor "t3")))

    (println "== shipment-coordinate ship-1 on order-001 (verified, within qty -- escalates, approve) ==")
    (let [r (exec-op actor "t4"
                      {:op :shipment-coordinate :effect :propose :subject "ship-1"
                       :value {:order-id "order-001" :quantity 50
                               :destination "buyer-acme-warehouse"}}
                      coordinator)]
      (println r)
      (println "-- human shipping approver approves --")
      (println (approve! actor "t4")))

    (println "\n-- HARD-hold scenarios --\n")

    (println "== order-intake with :effect other than :propose -> HARD hold ==")
    (println (exec-op actor "t5"
                       {:op :order-intake :effect :direct-write :subject "order-001"
                        :patch {:style "x"}}
                       coordinator))

    (println "== unrecognized op -> HARD hold ==")
    (println (exec-op actor "t6"
                       {:op :actuate-sewing-machine :effect :propose :subject "order-001"}
                       coordinator))

    (println "== shipment-coordinate on order-003 (UNVERIFIED -> HARD hold) ==")
    (println (exec-op actor "t7"
                       {:op :shipment-coordinate :effect :propose :subject "ship-2"
                        :value {:order-id "order-003" :quantity 10
                                :destination "buyer-hakusen-warehouse"}}
                       coordinator))

    (println "== shipment-coordinate on order-002 (would exceed quantity -> HARD hold) ==")
    (println (exec-op actor "t8"
                       {:op :shipment-coordinate :effect :propose :subject "ship-3"
                        :value {:order-id "order-002" :quantity 50
                                :destination "buyer-nishijin-warehouse"}}
                       coordinator))

    (println "== order-intake with :direct-operate? true -> HARD hold, PERMANENT ==")
    (println (exec-op actor "t9"
                       {:op :order-intake :effect :propose :subject "order-001"
                        :patch {:direct-operate? true :style "force-run-sewing"}}
                       coordinator))

    (println "== quality-flag with :safety-cert-finalized? true -> HARD hold, PERMANENT ==")
    (println (exec-op actor "t10"
                       {:op :quality-flag :effect :propose :subject "flag-2"
                        :value {:order-id "order-001" :concern-type :safety
                                :severity :high :safety-cert-finalized? true
                                :description "安全証明書を最終確定"}}
                       coordinator))

    (println "== pattern-spec with fabricated size-code -> HARD hold ==")
    (println (exec-op actor "t11"
                       {:op :pattern-spec :effect :propose :subject "pattern-001"
                        :value {:size-code :mega-plus-select}}
                       coordinator))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== quality flags ==")
    (doseq [r (store/quality-flags db)] (println r))

    (println "\n== draft shipment records ==")
    (doseq [r (store/shipment-history db)] (println r))))
