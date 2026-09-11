(ns apparel.phase
  "Phase 0->3 staged rollout for the apparel-manufacturing plant-
  operations coordination actor.

    Phase 0  read-only          -- no writes, still governor-gated.
    Phase 1  assisted-intake    -- order intake allowed, every write
                                    needs human approval.
    Phase 2  assisted-coordinate -- adds quality flags and shipment
                                    coordination, still approval.
    Phase 3  supervised-auto    -- adds pattern-spec (still always
                                    approval); governor-clean, high-
                                    confidence `:order-intake` (no
                                    physical/financial risk) may auto-
                                    commit.

  `:pattern-spec`, `:quality-flag`, and `:shipment-coordinate` are
  deliberately ABSENT from every phase's `:auto` set, including phase
  3 -- permanent structural facts. Pattern-spec is size-spec metadata
  only (no craft engine this wave) but still needs a human pattern-
  room check before commit; quality flags and shipments always need
  human eyes. `apparel.governor` independently hard-blocks sewing-
  machine control and safety-cert finalization -- multiple layers
  agree on where this actor's authority ends.")

(def write-ops
  #{:order-intake :pattern-spec :quality-flag :shipment-coordinate})

;; NOTE the invariant: only `:order-intake` is ever a member of any
;; phase's `:auto` set below. Do not add the others there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed
  to auto-commit when governor-clean>}."
  {0 {:label "read-only"           :writes #{}                                            :auto #{}}
   1 {:label "assisted-intake"     :writes #{:order-intake}                               :auto #{}}
   2 {:label "assisted-coordinate" :writes #{:order-intake :quality-flag
                                             :shipment-coordinate}                         :auto #{}}
   3 {:label "supervised-auto"     :writes write-ops
      :auto #{:order-intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map an Apparel Governor verdict to a base disposition before the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
