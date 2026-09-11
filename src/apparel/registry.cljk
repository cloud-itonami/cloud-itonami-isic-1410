(ns apparel.registry
  "Pure-function domain logic for the apparel-manufacturing plant-
  operations coordination actor -- order/pattern verification,
  shipment-quantity recompute, size-spec validation, and draft
  shipment/quality-flag record construction.

  This vertical has NO pattern-craft engine in this wave (Wave 0 Lane
  C2): pattern work here is size-spec *metadata* only
  (`:pattern-spec`), never geometry, grading algorithms, or CAD
  actuation. The domain logic lives here as pure functions,
  re-verified INDEPENDENTLY by `apparel.governor` -- never trust a
  proposal's own self-reported quantity/status when the inputs needed
  to recompute it independently are already on record.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real plant-management system. It builds the DRAFT record
  a plant coordinator would keep, not the act of actuating a sewing
  machine, cutter, or finalizing a safety certificate (this actor
  NEVER does either -- see README `What this actor does NOT do`).")

;; ----------------------------- constants -----------------------------

(def valid-size-codes
  "Closed set of size-spec codes a pattern record may declare. Anything
  else is a fabricated/unrecognized size -- the governor HARD-holds
  rather than let an invented size pass through."
  #{:XS :S :M :L :XL :XXL :3XL :custom})

;; ----------------------------- order checks -----------------------------

(defn order-verified?
  "Ground-truth check: has `order`'s own record been marked verified?"
  [order]
  (true? (:verified? order)))

(defn order-registered?
  "Ground-truth check: is `order` on file in the plant's order ledger?"
  [order]
  (true? (:registered? order)))

(defn order-ready?
  "Combined ground-truth gate: order must be both verified? AND
  registered? before ANY shipment may be coordinated against it."
  [order]
  (and (order-verified? order) (order-registered? order)))

;; ----------------------------- pattern checks -----------------------------

(defn pattern-verified?
  "Ground-truth check: has `pattern`'s own size-spec record been QC'd?"
  [pattern]
  (true? (:verified? pattern)))

(defn pattern-registered?
  "Ground-truth check: is `pattern` on file in the plant's pattern ledger?"
  [pattern]
  (true? (:registered? pattern)))

(defn pattern-ready?
  "Combined ground-truth gate for pattern-spec updates that reference
  an existing pattern subject."
  [pattern]
  (and (pattern-verified? pattern) (pattern-registered? pattern)))

(defn size-code-valid?
  "Is `size` one of the closed, known size-spec codes?"
  [size]
  (contains? valid-size-codes size))

;; ----------------------------- shipment volume -----------------------------

(defn shipment-quantity-exceeded?
  "Would `shipped-to-date` + `new-qty` exceed `order`'s own recorded
  `:quantity`? Ground truth from the order's permanent fields."
  [order new-qty]
  (let [capacity (:quantity order)
        so-far (:shipped-quantity order 0)]
    (and (number? capacity)
         (number? new-qty)
         (number? so-far)
         (> (+ (long so-far) (long new-qty)) (long capacity)))))

(defn shipment-quantity-checkable?
  "Can headroom actually be computed for `new-qty`? Un-checkable is not headroom."
  [order new-qty]
  (boolean (and (map? order)
                (number? (:quantity order))
                (number? (:shipped-quantity order 0))
                (number? new-qty))))

;; ----------------------------- draft record construction -----------------------------

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the human plant supervisor's / shipping approver's act, not this
  actor's. Finalizing a safety cert is permanently blocked at the
  governor (see `safety-cert-decision-blocked`)."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn register-shipment
  "Validate + construct a SHIPMENT-COORDINATION DRAFT against a verified
  order. Pure -- does not dispatch freight."
  [shipment-id sequence]
  (when-not (and shipment-id (not= shipment-id ""))
    (throw (ex-info "shipment: shipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "shipment: sequence must be >= 0" {})))
  (let [shipment-number (str "SHP-" (zero-pad sequence 6))
        record {"record_id" shipment-number
                "kind" "shipment-coordination-draft"
                "shipment_id" shipment-id
                "immutable" true}]
    {"record" record "shipment_number" shipment-number
     "certificate" (unsigned-certificate "ShipmentCoordination" shipment-number shipment-number)}))

(defn register-quality-flag
  "Validate + construct a QUALITY-FLAG DRAFT. Pure -- does not finalize
  any safety certificate or decide lot disposition."
  [flag-id sequence]
  (when-not (and flag-id (not= flag-id ""))
    (throw (ex-info "quality-flag: flag_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "quality-flag: sequence must be >= 0" {})))
  (let [flag-number (str "QCF-" (zero-pad sequence 6))
        record {"record_id" flag-number
                "kind" "quality-flag-draft"
                "flag_id" flag-id
                "immutable" true}]
    {"record" record "flag_number" flag-number
     "certificate" (unsigned-certificate "QualityFlag" flag-number flag-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
