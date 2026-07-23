(ns apparel.phase
  "Phase table for Apparel Manufacturing Plant Operations state graph.

  NOTE: this is a plain descriptive EDN map of the intended graph
  topology (nodes/edges/predicates) plus small structural helpers
  (`starting-node`, `is-terminal?`) used by tests and `sim.cljc` --
  it is NOT compiled or run through `langgraph.graph/state-graph`.
  `apparel.sim` drives the actual workflow as a plain function
  pipeline (advisor proposal -> `governor/evaluate`), not this table.
  A real langgraph-clj StateGraph (with `g/run*`/interrupt-before
  human sign-off) is a known gap for this actor's upgrade from
  :blueprint to :implemented; see e.g. cloud-itonami-isco-1221's
  salesmgmt.actor for the target shape.")

;; ----------------------------- phase constants -----------------------------

(def ADVISOR-NODE :advisor)
(def GOVERNOR-NODE :governor)
(def HOLD-NODE :hold)
(def COMPLETE-NODE :complete)

;; ----------------------------- phase table (graph structure) -----------------------------

(def phase-table
  "State graph topology for apparel-manufacturing plant operations coordination.
  Entry: ADVISOR-NODE
  Output nodes: COMPLETE-NODE (approved), HOLD-NODE (blocked)

  Flow:
    advisor -> proposes operation (batch logging, maintenance, defect flag, shipment)
    governor -> evaluates against hard/soft gates
    if holds (hard violation): HOLD (escalate to human)
    if clean or only soft violations: COMPLETE (record approved by human)
  "
  {:start ADVISOR-NODE
   :nodes {ADVISOR-NODE {:type :function
                         :description "LLM advisor proposes apparel plant operations"}
           GOVERNOR-NODE {:type :function
                          :description "Compliance governor evaluates proposal"}
           HOLD-NODE {:type :terminal
                      :description "Proposal blocked, requires human review"}
           COMPLETE-NODE {:type :terminal
                          :description "Proposal approved, logged to audit ledger"}}
   :edges [[ADVISOR-NODE GOVERNOR-NODE]
           [GOVERNOR-NODE :decision]
           [:decision HOLD-NODE {:predicate :holds?}]
           [:decision COMPLETE-NODE {:predicate (fn [x] (not (:holds? x)))}]]
   :output-node COMPLETE-NODE})

;; ----------------------------- helpers for test harnesses -----------------------------

(defn starting-node
  "Get the entry point node."
  []
  ADVISOR-NODE)

(defn is-terminal?
  "Check if a node is terminal (output/end state)."
  [node-id]
  (contains? #{HOLD-NODE COMPLETE-NODE} node-id))
