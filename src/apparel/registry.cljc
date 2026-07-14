(ns apparel.registry
  "Proposal registry and drafting helpers for apparel plant operations.
  Every proposal carries its spec-basis and evidence checklist.")

;; ----------------------------- hard invariants -----------------------------

(defn hard-invariant-violations
  "Hard invariants that CANNOT be overridden:
  - If operation affects shipment or quality reporting, it must carry spec-basis."
  [op-type value]
  (when (contains? #{:actuation/coordinate-shipment :proposal/flag-quality-defect} op-type)
    (when (or (empty? (:cites value))
              (and (contains? value :spec-basis) (nil? (:spec-basis value))))
      [{:rule :no-spec-basis
        :detail "公式な仕様基準の引用が無い提案は処理できない"}])))

(defn protected-operation-violations
  "Operations that require human sign-off and can never be autonomous:
  - Shipment coordination (even proposals)
  - Quality defect flagging (always escalates)"
  [op-type]
  (when (contains? #{:actuation/coordinate-shipment :proposal/flag-quality-defect} op-type)
    [{:rule :requires-human-approval
      :detail "製品出荷と品質欠陥報告には人間の承認が必須"}]))

;; ----------------------------- proposal drafts -----------------------------

(defn batch-log-draft
  "Draft a production batch logging proposal.
  subject: batch ID
  cites: spec-basis citations
  evidence-checklist: map of verified evidence items (batch verification, etc.)"
  [subject cites evidence-checklist confidence detail]
  {:op :proposal/log-production-batch
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})

(defn maintenance-draft
  "Draft an equipment maintenance scheduling proposal.
  subject: equipment ID
  cites: spec-basis citations
  evidence-checklist: map of verified maintenance records"
  [subject cites evidence-checklist confidence detail]
  {:op :proposal/schedule-maintenance
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})

(defn quality-defect-draft
  "Draft a quality defect flagging proposal (ALWAYS escalates).
  subject: batch ID
  cites: spec-basis citations
  defect-type: category of defect (labeling, stitching, material, etc.)
  evidence-checklist: map of verified defect evidence
  detail: narrative description"
  [subject cites defect-type evidence-checklist confidence detail]
  {:op :proposal/flag-quality-defect
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :defect-type defect-type
           :detail detail}})

(defn shipment-draft
  "Draft a shipment coordination proposal (high-stakes actuation).
  subject: shipment ID
  cites: spec-basis citations
  evidence-checklist: map of verified shipping/tariff documentation"
  [subject cites evidence-checklist confidence detail]
  {:op :actuation/coordinate-shipment
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})
