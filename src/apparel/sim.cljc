(ns apparel.sim
  "Simulation harness for Apparel Manufacturing Plant Operations Coordinator actor.
  Run with: clojure -M:dev:run"
  (:require [apparel.advisor :as advisor]
            [apparel.governor :as governor]
            [apparel.store :as store]))

(defn -main
  "Drive a simple apparel manufacturing workflow through the governor."
  [& _args]
  (let [st (store/mem-store)
        adv (advisor/mock-advisor)

        ;; Scenario 1: Production batch logging (verified batch)
        batch-proposal (advisor/batch-log-proposal adv "batch-001")
        batch-eval (governor/evaluate batch-proposal st)

        ;; Scenario 2: Quality defect flagging (always escalates)
        defect-proposal (advisor/quality-defect-proposal adv "batch-002" "labeling-error")
        defect-eval (governor/evaluate defect-proposal st)

        ;; Scenario 3: Shipment coordination (high-stakes actuation)
        shipment-proposal (advisor/shipment-proposal adv "ship-001")
        shipment-eval (governor/evaluate shipment-proposal st)

        ;; Scenario 4: Maintenance scheduling
        maintenance-proposal (advisor/maintenance-proposal adv "maint-001")
        maintenance-eval (governor/evaluate maintenance-proposal st)]

    (println "=== APPAREL MANUFACTURING PLANT OPERATIONS COORDINATOR SIMULATION ===\n")

    (println "--- Scenario 1: Production Batch Logging (Verified Batch) ---")
    (println "Proposal:" batch-proposal)
    (println "Evaluation:" batch-eval)
    (println "Result:" (if (:clean? batch-eval) "APPROVED" "ESCALATE TO HUMAN"))
    (println)

    (println "--- Scenario 2: Quality Defect Flagging (Always Escalates) ---")
    (println "Proposal:" defect-proposal)
    (println "Evaluation:" defect-eval)
    (println "Hard Violations:" (:hard-violations defect-eval))
    (println "Result:" (if (:holds? defect-eval) "ESCALATE TO HUMAN" "ERROR"))
    (println)

    (println "--- Scenario 3: Shipment Coordination (High-Stakes Actuation) ---")
    (println "Proposal:" shipment-proposal)
    (println "Evaluation:" shipment-eval)
    (println "Soft Violations:" (:soft-violations shipment-eval))
    (println "Result:" (if (:holds? shipment-eval) "HOLD - Hard violations" "ESCALATE - High-stakes actuation"))
    (println)

    (println "--- Scenario 4: Maintenance Scheduling ---")
    (println "Proposal:" maintenance-proposal)
    (println "Evaluation:" maintenance-eval)
    (println "Result:" (if (:clean? maintenance-eval) "APPROVED" "ESCALATE TO HUMAN"))
    (println)))
