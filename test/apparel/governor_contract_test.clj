(ns apparel.governor-contract-test
  (:require [clojure.test :refer [deftest is]]
            [apparel.store :as store]
            [apparel.advisor :as advisor]
            [apparel.governor :as governor]
            [apparel.registry :as registry]))

(deftest spec-basis-hard-gate
  "Spec-basis is a HARD gate: never allow proposals without official citations."
  (let [st (store/mem-store)
        proposal {:op :actuation/coordinate-shipment
                  :subject "ship-001"
                  :effect :propose
                  :value {:evidence {:export-permit true}
                          :confidence 0.9}
                  :cites []}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Proposal with empty cites should hold")
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :no-spec-basis) (:hard-violations eval))))))

(deftest process-control-block
  "HARD BLOCK: Proposals mentioning cutting speed, sewing tension, or equipment control
  are immediately rejected. Those remain engineer exclusive authority."
  (let [st (store/mem-store)
        proposal {:op :proposal/log-production-batch
                  :subject "batch-001"
                  :effect :propose
                  :cites ["some-spec"]
                  :value {:evidence {:batch-verified true}
                          :confidence 0.9
                          :detail "Please increase cutting speed to 500 units/hr"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Process-control proposal should hold")
      (is (some #(= (:rule %) :process-control-forbidden) (:hard-violations eval))
        "Should have process-control-forbidden violation"))))

(deftest quality-defect-escalation
  "Quality defects ALWAYS escalate to human. Never silently log a defect."
  (let [st (store/mem-store)
        proposal {:op :proposal/flag-quality-defect
                  :subject "batch-002"
                  :effect :propose
                  :cites ["16 CFR § 303 (Fiber Labeling)"]
                  :value {:evidence {:defect-documented true}
                          :confidence 0.92
                          :defect-type "labeling-error"
                          :detail "Fiber content label incorrect on shipment"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Quality defect should hold")
      (is (some #(= (:rule %) :quality-defect-escalates) (:hard-violations eval))
        "Should have quality-defect-escalates violation"))))

(deftest shipment-requires-escalation
  "Shipment coordination is high-stakes actuation and requires human sign-off,
  even when all other checks are clean."
  (let [st (store/mem-store)
        adv (advisor/mock-advisor)
        shipment-proposal (advisor/shipment-proposal adv "ship-001")]
    (let [eval (governor/evaluate shipment-proposal st)]
      (is (seq (:soft-violations eval)) "Should have soft violations for actuation")
      (is (some #(= (:rule %) :escalate) (:soft-violations eval))
        "Should escalate high-stakes actuation"))))

(deftest plant-not-verified-blocks
  "Production batch from unverified plant is blocked."
  (let [st (store/mem-store)
        ;; Create a batch with unverified plant
        _ (swap! (-> st :data) assoc-in [:production-batches "batch-unverified" :plant] "plant-unknown")
        proposal (registry/batch-log-draft "batch-unverified"
                   ["TCVN 6113:2020"]
                   {:batch-verified true}
                   0.85
                   "Log batch from plant")]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :plant-not-verified) (:hard-violations eval))
        "Should block unverified plant"))))

(deftest batch-not-verified-blocks
  "Production batch logging with unverified batch is blocked."
  (let [st (store/mem-store)
        proposal (registry/batch-log-draft "batch-002"
                   ["TCVN 6113:2020"]
                   {:batch-verified true}
                   0.88
                   "Log unverified batch")]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :batch-not-verified) (:hard-violations eval))
        "Should block unverified batch"))))

(deftest low-confidence-escalates
  "Low confidence proposals escalate to human, even if otherwise clean."
  (let [st (store/mem-store)
        proposal {:op :proposal/log-production-batch
                  :subject "batch-001"
                  :effect :propose
                  :cites ["TCVN 6113:2020"]
                  :value {:evidence {:batch-verified true}
                          :confidence 0.45
                          :detail "Batch logged"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:soft-violations eval)) "Should have soft violations")
      (is (some #(= (:rule %) :escalate) (:soft-violations eval))
        "Should escalate low-confidence"))))

(deftest clean-proposal
  "A proposal with all evidence, valid spec-basis, high confidence,
  and no high-stakes actuation or process-control is clean."
  (let [st (store/mem-store)
        proposal {:op :proposal/schedule-maintenance
                  :subject "maint-001"
                  :effect :propose
                  :cites ["Bangladeshi Standard BDS 1000:2020"]
                  :value {:evidence {:equipment-record true :maintenance-schedule-ok true}
                          :confidence 0.9
                          :detail "Maintenance scheduled"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:clean? eval) "Should be clean")
      (is (empty? (:hard-violations eval)) "Should have no hard violations")
      (is (empty? (:soft-violations eval)) "Should have no soft violations"))))
