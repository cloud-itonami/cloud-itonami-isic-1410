(ns apparel.governor
  "Apparel Governor -- the independent compliance layer that earns the
  ApparelAdvisor the right to commit. The advisor has no notion of
  whether an order it wants to ship against has actually been
  verified/registered, whether a proposal secretly tries to DIRECTLY
  OPERATE a sewing machine / cutter, whether a quality-flag secretly
  tries to FINALIZE a safety certificate, or when an act stops being
  a coordination proposal and becomes production-line control, so
  this MUST be a separate system able to *reject* a proposal and fall
  back to HOLD.

  `:itonami.blueprint/governor` is `:apparel-governor`.

  Checks below, ALL HARD violations except the confidence/high-stakes
  gate (SOFT -- asks a human to look, and the human may approve):

    1. Request-level propose-only  -- caller's `:effect` MUST be
                                       `:propose`. HARD, unconditional.
    2. Closed op allowlist         -- `:op` one of the four ops this
                                       actor coordinates. HARD.
    3. Closed effect allowlist     -- proposal's `:effect` one of the
                                       four propose-shaped effects.
                                       Never a sewing-machine-control
                                       or safety-cert-decision effect.
                                       HARD, PERMANENT.
    4. Sewing-control blocked      -- any proposal whose `:value`
                                       declares `:direct-operate? true`
                                       (sewing/cutting/pressing machine
                                       control). HARD, PERMANENT.
    5. Safety-cert-decision blocked -- any proposal whose `:value`
                                       declares `:safety-cert-finalized?
                                       true`. This actor may FLAG a
                                       quality/safety concern, never
                                       finalize a safety certificate.
                                       HARD, PERMANENT.
    6. Order not verified/
       registered                  -- for `:shipment-coordinate`,
                                       independently verify the
                                       referenced order. HARD.
    7. Shipment quantity exceeded  -- for `:shipment-coordinate`,
                                       recompute headroom against the
                                       order's own recorded quantity.
                                       HARD.
    8. Invalid size-code           -- for `:pattern-spec`, reject
                                       fabricated size codes. HARD.
    9. Confidence floor / high-
       stakes gate                 -- low confidence OR stake in
                                       high-stakes
                                       (`:coordination/quality-concern`
                                       always set for `:quality-flag`)
                                       -- escalate to a human. SOFT."
  (:require [apparel.registry :as registry]
            [apparel.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "Closed allowlist of coordination proposals this actor may ever route."
  #{:order-intake :pattern-spec :quality-flag :shipment-coordinate})

(def allowed-proposal-effects
  "Closed allowlist of SSoT-mutation effects a proposal may declare --
  all four are propose-shaped drafts, NEVER a sewing-machine-control
  effect and NEVER a safety-cert-finalization effect."
  #{:order/upsert :pattern/upsert :quality/flag :shipment/propose})

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Quality concerns always demand human eyes regardless of confidence."
  #{:coordination/quality-concern})

;; ----------------------------- checks -----------------------------

(defn- no-propose-effect-violations
  "HARD, unconditional, evaluated first: the caller's own request MUST
  declare `:effect :propose`."
  [{:keys [effect]}]
  (when (not= effect :propose)
    [{:rule :not-propose-effect
      :detail (str "request :effect は :propose のみ許可 (受信値: " (pr-str effect) ")")}]))

(defn- unknown-op-violations
  "HARD: `:op` must be one of the closed allowlist."
  [{:keys [op]}]
  (when-not (contains? allowed-ops op)
    [{:rule :unknown-op
      :detail (str op " はこの actor が扱う操作の許可リストに無い")}]))

(defn- equipment-control-blocked-violations
  "HARD, PERMANENT: proposal `:effect` must be within the closed
  propose-shaped allowlist. Anything else (sewing-machine control,
  safety-cert decision, hallucinated actuation) is scope boundary."
  [proposal]
  (when-not (contains? allowed-proposal-effects (:effect proposal))
    [{:rule :equipment-control-blocked
      :detail (str "proposal :effect (" (pr-str (:effect proposal))
                   ") は縫製・裁断設備の直接操作、または安全証明書の最終決定に"
                   "該当する可能性があり、恒久的に禁止")}]))

(defn- sewing-control-blocked-violations
  "HARD, PERMANENT, unconditional: any proposal whose `:value` declares
  `:direct-operate? true` is attempting to directly operate a sewing /
  cutting / pressing machine. This actor may only ever propose
  coordination drafts. No override, ever."
  [proposal]
  (when (true? (:direct-operate? (:value proposal)))
    [{:rule :sewing-control-blocked
      :detail "縫製・裁断・プレス設備の直接操作提案は恒久的に禁止 -- 調整提案(draft)のみ許可"}]))

(defn- safety-cert-decision-blocked-violations
  "HARD, PERMANENT, unconditional: any proposal whose `:value` declares
  `:safety-cert-finalized? true` is attempting to finalize a safety
  certificate -- a human plant supervisor / certifying body act this
  actor may never perform. This actor may only FLAG a quality/safety
  concern. No override, ever."
  [proposal]
  (when (true? (:safety-cert-finalized? (:value proposal)))
    [{:rule :safety-cert-decision-blocked
      :detail "安全証明書(safety cert)の最終確定提案は恒久的に禁止 -- 懸念の報告(flag)のみ許可、確定は人間の工場責任者/認証機関の専権"}]))

(defn- order-not-verified-violations
  "For `:shipment-coordinate`, INDEPENDENTLY verify the referenced
  order exists and is both verified? AND registered?."
  [{:keys [op]} proposal st]
  (when (= op :shipment-coordinate)
    (let [order-id (:order-id (:value proposal))
          o (and order-id (store/order st order-id))]
      (when-not (and o (registry/order-ready? o))
        [{:rule :order-not-verified
          :detail (str order-id " は未検証または未登録、もしくは存在しない -- 検証済み・登録済み受注記録が無い状態での出荷調整提案")}]))))

(defn- shipment-quantity-exceeded-violations
  "For `:shipment-coordinate`, INDEPENDENTLY recompute whether the
  order's own recorded shipped-to-date plus the proposal's claimed
  quantity would exceed the order's own recorded `:quantity`."
  [{:keys [op]} proposal st]
  (when (= op :shipment-coordinate)
    (let [{:keys [order-id quantity]} (:value proposal)
          o (and order-id (store/order st order-id))]
      (cond
        (not (registry/shipment-quantity-checkable? o quantity))
        [{:rule :shipment-quantity-exceeded
          :detail "受注数量/既存出荷実績/申請量のいずれかが数値として確定できない -- 空き容量を検算できないため出荷しない"}]

        (registry/shipment-quantity-exceeded? o quantity)
        [{:rule :shipment-quantity-exceeded
          :detail (str order-id " の記録済み受注数量(" (:quantity o)
                       ")を、既存出荷実績(" (:shipped-quantity o 0)
                       ")+今回申請(" quantity ")が超過")}]))))

(defn- invalid-size-code-violations
  "For `:pattern-spec`, if the patch declares a `:size-code` outside
  the closed known set, reject rather than let a fabricated size through."
  [{:keys [op]} proposal]
  (when (= op :pattern-spec)
    (let [size (:size-code (:value proposal))]
      (when (and (some? size) (not (registry/size-code-valid? size)))
        [{:rule :invalid-size-code
          :detail (str size " は既知の size-code 値ではない")}]))))

(defn check
  "Censors an ApparelAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (no-propose-effect-violations request)
                           (unknown-op-violations request)
                           (equipment-control-blocked-violations proposal)
                           (sewing-control-blocked-violations proposal)
                           (safety-cert-decision-blocked-violations proposal)
                           (order-not-verified-violations request proposal st)
                           (shipment-quantity-exceeded-violations request proposal st)
                           (invalid-size-code-violations request proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
