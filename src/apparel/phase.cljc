(ns apparel.phase
  "Phase table for Apparel Manufacturing Plant Operations state graph.

  NOTE: this is a plain descriptive EDN map of the intended graph
  topology (nodes/edges/predicates) plus small structural helpers
  (`starting-node`, `is-terminal?`) used by tests and `sim.cljc` --
  it is NOT compiled or run through `langgraph.graph/state-graph`.
  **そのギャップは埋まった**（2026-08-01）: 本物の StateGraph は
  `apparel.actor/build`（`g/run*` + `interrupt-before` の人間承認 +
  append-only 台帳）。この表は引き続きトポロジの記述で、`apparel.sim` は
  デモ用の素の呼び出しとして残る —— 実際の運用経路は actor 側。")

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

;; ----------------------------- rollout phase gate -----------------------------

(def write-ops
  "SSoT を書きうる op の全体。"
  #{:proposal/log-production-batch :proposal/schedule-maintenance
    :proposal/flag-quality-defect :actuation/coordinate-shipment})

(def phases
  "phase -> {:label .. :writes <書いてよい op> :auto <governor clean なら自動確定してよい op>}。

  線引きは『物理的・金銭的リスクを負わない記録だけが auto-eligible』。
  `:proposal/log-production-batch` だけがそれに当たる —— 保全計画は現場の人と
  設備を動かす予定を作り、不良報告は出荷可否に、出荷調整は輸出と請求に直結する。

  `:proposal/flag-quality-defect` は governor 側でも常に escalate する
  （`quality-defect-escalation-violations`）。**層を 2 つにしておく**のは、
  どちらか一方の設定ミスで自動承認が生えないようにするため。"
  {0 {:label "read-only"           :writes #{}                                    :auto #{}}
   1 {:label "assisted-intake"     :writes #{:proposal/log-production-batch}       :auto #{}}
   2 {:label "assisted-coordinate" :writes #{:proposal/log-production-batch
                                             :proposal/flag-quality-defect}        :auto #{}}
   3 {:label "supervised-auto"     :writes write-ops
      :auto #{:proposal/log-production-batch}}})

(def default-phase 3)

(defn gate
  "phase の書き込み許可と自動確定許可を、governor の判定 `base` に重ねる。

  **HARD 違反（`:hold`）は phase では覆せない。** phase が緩められるのは
  『自動で確定してよいか』だけで、『通してよいか』ではない。"
  [ph request base]
  (let [{:keys [writes auto]} (get phases ph (get phases default-phase))
        op (:op request)]
    (cond
      (= base :hold)                    {:disposition :hold}
      (not (contains? writes op))       {:disposition :escalate :reason :phase-write-not-allowed}
      (and (= base :commit)
           (not (contains? auto op)))   {:disposition :escalate :reason :phase-approval}
      :else                             {:disposition base})))
