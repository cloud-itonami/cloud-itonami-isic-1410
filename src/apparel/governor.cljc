(ns apparel.governor
  "Apparel Manufacturing Plant Operations Governor -- the independent compliance layer that earns
  the Apparel Operations Advisor the right to propose and log actions.
  The LLM has no notion of labor standards, quality regulations, or when a shipment
  or batch-logging is a real-world actuation, so this MUST be a separate system able to
  *reject* a proposal and fall back to HOLD.

  HARD violations (a human approver CANNOT override):
    1. Spec-basis       -- no official jurisdiction citation
    2. Plant not verified -- batch plant registration must be confirmed
    3. Batch not verified -- batch quality must be confirmed before logging/shipment
    4. Direct equipment control -- NO cutting/sewing-line operation (those remain engineer exclusive)
    5. Quality defects  -- ALWAYS escalate (never silently log)

  SOFT violation (can be approved by human):
    6. Confidence floor / high-stakes actuation -- low confidence OR real actuation

  CRITICAL SCOPE BOUNDARY:
  This actor coordinates LOGISTICS and COMPLIANCE PAPERWORK around apparel manufacturing.
  It does NOT:
    - Operate cutting equipment or sewing machines
    - Make design decisions about patterns or materials
    - Control production-line parameters (speed, tension, etc.)
    - Approve fabric quality (that's the mill's responsibility)

  Those remain the exclusive authority of plant production engineers."
  (:require [apparel.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Operations that require human sign-off for real-world actuation:
  Shipment coordination with export/tariff implications."
  #{:actuation/coordinate-shipment})

(def process-control-keywords
  "Words that indicate process-engineering authority (FORBIDDEN for this actor).
  If a proposal mentions any of these, it's a hard block."
  #{"speed" "tension" "needle" "presser" "feed" "stitch" "pattern"
    "cutting" "sewing" "operate" "control" "blade" "angle" "parameter"
    "thread" "adjust" "trim"})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A proposal with no spec-basis citation is a HARD violation --
  never invent a jurisdiction's requirements."
  [proposal _st]
  (let [op (:op proposal)]
    (when (contains? #{:actuation/coordinate-shipment :proposal/flag-quality-defect} op)
      (when (or (empty? (:cites proposal))
                (and (contains? (:value proposal) :spec-basis)
                     (nil? (:spec-basis (:value proposal)))))
        [{:rule :no-spec-basis
          :detail "公式な仕様基準の引用が無い提案は処理できない"}]))))

(defn- plant-verification-violations
  "Batch must belong to a verified plant before any action."
  [{:keys [op subject]} st]
  (when (contains? #{:proposal/log-production-batch :actuation/coordinate-shipment} op)
    (let [batch (store/production-batch st subject)
          plant-id (:plant batch)]
      (when plant-id
        (when-not (store/plant-verified? st plant-id)
          [{:rule :plant-not-verified
            :detail "製造施設が登録・検証されていない"}])))))

(defn- batch-verification-violations
  "Batch must be verified before logging or shipment."
  [{:keys [op subject]} st]
  (when (contains? #{:proposal/log-production-batch :actuation/coordinate-shipment} op)
    (when-not (store/batch-verified? st subject)
      [{:rule :batch-not-verified
        :detail "製造ロットが検証されていない"}])))

(defn- process-control-block-violations
  "HARD BLOCK: This actor does NOT operate production equipment.
  If a proposal mentions cutting speed, sewing tension, needle control, or other
  process parameters, reject it immediately.
  Those decisions remain the exclusive authority of licensed production engineers."
  [proposal _st]
  (let [detail (str (:detail (:value proposal) "") " " (:op proposal))
        words (re-seq #"\w+" (.toLowerCase detail))
        forbidden (some #(contains? process-control-keywords %) words)]
    (when forbidden
      [{:rule :process-control-forbidden
        :detail (str "設備操作は認可エンジニアの排他的権限です。"
                    "この提案には禁止キーワード '" forbidden "' が含まれています。")}])))

(defn- quality-defect-escalation-violations
  "Quality defects ALWAYS escalate to human. Never silently log a defect."
  [{:keys [op]} _st]
  (when (= op :proposal/flag-quality-defect)
    [{:rule :quality-defect-escalates
      :detail "品質欠陥は必ず人間にエスカレートされる"}]))

(defn- confidence-gate-violations
  "Low confidence or high-stakes actuation -> escalate to human."
  [{:keys [op]} {:keys [confidence]}]
  (let [confidence (or confidence 0.5)]
    (when (or (< confidence confidence-floor)
              (contains? high-stakes op))
      [{:rule :escalate
        :detail (if (< confidence confidence-floor)
                  (str "信頼度が低い (confidence=" confidence ")")
                  "実際の操作には人間の承認が必要")}])))

;; ----------------------------- governor evaluation -----------------------------

(defn evaluate
  "Evaluate a proposal against all hard and soft gates.
  Returns a map:
    {:holds? boolean
     :hard-violations [...]
     :soft-violations [...]
     :clean? boolean}"
  [proposal st]
  (let [hard-checks-store [spec-basis-violations
                           plant-verification-violations
                           batch-verification-violations
                           process-control-block-violations]
        hard-checks-value [quality-defect-escalation-violations]
        soft-checks [confidence-gate-violations]
        hard-violations-store (mapcat #(% proposal st) hard-checks-store)
        hard-violations-value (mapcat #(% proposal (:value proposal)) hard-checks-value)
        hard-violations (concat hard-violations-store hard-violations-value)
        soft-violations (mapcat #(% proposal (:value proposal)) soft-checks)]
    {:holds? (seq hard-violations)
     :hard-violations (vec hard-violations)
     :soft-violations (vec soft-violations)
     :clean? (and (empty? hard-violations) (empty? soft-violations))}))
