(ns apparel.advisor
  "Apparel Manufacturing Plant Operations Advisor -- the LLM-driven suggestion layer.
  Proposes operations to the Governor for approval.")

;; ----------------------------- mock advisor for testing -----------------------------

(defn mock-advisor
  "Create a mock advisor for testing. Real implementation would call an LLM."
  []
  {:type :mock :model "mock-v1"})

(defn batch-log-proposal
  "Propose logging a completed production batch to the audit ledger."
  [_advisor batch-id]
  {:op :proposal/log-production-batch
   :subject batch-id
   :effect :propose
   :cites ["TCVN 6113:2020"]
   :value {:evidence {:batch-verified true :quantity-confirmed true :quality-grade-assigned true}
           :confidence 0.87
           :detail "Production batch logged and quality verified"}})

(defn maintenance-proposal
  "Propose scheduling equipment maintenance."
  [_advisor equipment-id]
  {:op :proposal/schedule-maintenance
   :subject equipment-id
   :effect :propose
   :cites ["Bangladeshi Standard BDS 1000:2020"]
   :value {:evidence {:equipment-record true :maintenance-schedule-ok true}
           :confidence 0.85
           :detail "Maintenance scheduled for equipment"}})

(defn quality-defect-proposal
  "Propose flagging a quality defect or labeling issue (ALWAYS escalates to human)."
  [_advisor batch-id defect-type]
  {:op :proposal/flag-quality-defect
   :subject batch-id
   :effect :propose
   :cites ["16 CFR § 303 (Fiber Content Labeling)"]
   :value {:evidence {:defect-documented true :photos-attached true}
           :confidence 0.82
           :defect-type defect-type
           :detail (str "Quality defect flagged: " defect-type " -- escalation required")}})

(defn shipment-proposal
  "Propose outbound product shipment coordination (high-stakes actuation)."
  [_advisor shipment-id]
  {:op :actuation/coordinate-shipment
   :subject shipment-id
   :effect :propose
   :cites ["19 CFR § 12.131 (Tariff Compliance)"]
   :value {:evidence {:export-permit true :shipping-manifest true :invoice-attached true}
           :confidence 0.89
           :detail "Shipment ready for export coordination"}})
