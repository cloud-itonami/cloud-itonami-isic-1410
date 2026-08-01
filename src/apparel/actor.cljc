(ns apparel.actor
  "ApparelOperationActor —— 1 回の運用調整依頼 = 1 回の supervised run を、
  langgraph-clj の StateGraph として表す。

  `apparel.phase` の docstring が『本物の StateGraph は :blueprint → :implemented
  への既知のギャップ』と自認していたのがこの ns。`apparel.sim` の素の関数
  パイプライン（advisor → governor を直に呼ぶ）を置き換える。

  ```
  intake ─▶ advise ─▶ govern ─▶ decide ─┬─▶ commit           (clean)
                                        ├─▶ request-approval (soft / high-stakes)
                                        └─▶ hold             (hard violation)
  ```

  1 run = 1 操作。無限の内部ループを持たない —— 工場の生産は多数の独立した op
  （バッチ記録 / 保全計画 / 不良報告 / 出荷調整）で進み、**そのそれぞれが単独で
  監査可能でチェックポイント可能**であることが、この形の理由。

  ## human-in-the-loop は本物の承認

  `interrupt-before #{:request-approval}` が actor を止め、判断を人（工場責任者・
  出荷承認者）に渡す。承認者は `{:approval {:status :approved :by \"…\"}}` で
  resume する。**止まるだけでは承認ではない** —— 誰が承認したかが state に入って
  初めて commit へ進む。

  ## advisor は差し替えられる

  `:advisor` は `apparel.advisor/Advisor` の実装。既定は決定論の mock で、
  実 LLM に差し替えてもコアは変わらない（封じ込め境界は governor 側にあり、
  advisor の賢さに依存しない）。"
  (:require [apparel.advisor :as advisor]
            [apparel.governor :as governor]
            [apparel.phase :as phase]
            [apparel.store :as store]
            [langgraph.checkpoint :as cp]
            [langgraph.graph :as g]))

;; ----------------------------- 監査事実 -----------------------------

(defn- commit-fact [request context proposal]
  {:t :committed
   :op (:op request)
   :actor (:actor-id context)
   :subject (:subject request)
   :disposition :commit
   :basis (:cites proposal)
   :summary (get-in proposal [:value :detail])})

(defn- hold-fact [request context verdict]
  {:t :governor-hold
   :op (:op request)
   :actor (:actor-id context)
   :subject (:subject request)
   :disposition :hold
   :basis (mapv :rule (:hard-violations verdict))
   :violations (:hard-violations verdict)})

(defn- approval-fact [request context approval]
  {:t :human-approved
   :op (:op request)
   :actor (:actor-id context)
   :subject (:subject request)
   :disposition :commit
   :approved-by (:by approval)})

(defn- commit-record
  "提案 → SSoT に書く記録。**書き先は `apparel.store/commit-record!` が決める**
  ので、ここが持つのは effect と対象と値だけ。"
  [request proposal]
  {:effect (:effect-target proposal)
   :path [(:subject request)]
   :value (or (:value proposal) {})})

;; ----------------------------- effect の対応表 -----------------------------

(def op->effect
  "op → SSoT に書く effect。**閉じた表**で、ここに無い op は何も書けない。

  advisor が `:effect` を自分で名乗る形にしていないのは、名乗れるなら
  『どこに書くか』を提案側が決められてしまうから。op から引く。"
  {:proposal/log-production-batch :batch/upsert
   :proposal/schedule-maintenance :maintenance/schedule
   :proposal/flag-quality-defect :quality-defect/flag
   :actuation/coordinate-shipment :shipment/coordinate})

(defn- with-effect [request proposal]
  (assoc proposal :effect-target (op->effect (:op request))))

;; ----------------------------- graph -----------------------------

(defn build
  "ApparelOperationActor のグラフを `st` に束ねて組む。

  opts:
    :advisor      —— `apparel.advisor/Advisor`（既定: 決定論 mock）
    :checkpointer —— langgraph checkpointer（既定: in-mem）"
  [st & [{:keys [advisor checkpointer]
          :or {advisor (advisor/mock-advisor)
               checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request {:default nil}
         :context {:default nil}
         :proposal {:default nil}
         :verdict {:default nil}
         :disposition {:default nil}
         :record {:default nil}
         :approval {:default nil}
         :audit {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      ;; 封じ込めた知能ノード —— 提案しか返さない。
      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [p (with-effect request (advisor/-advise advisor st request))]
            {:proposal p
             :audit [{:t :advisor-proposal :op (:op request)
                      :subject (:subject request)
                      :cites (:cites p)
                      :confidence (get-in p [:value :confidence])}]})))

      ;; 独立した検閲者。advisor の出力を信用しない。
      (g/add-node :govern
        (fn [{:keys [proposal]}]
          {:verdict (governor/evaluate proposal st)}))

      ;; governor の判定 → phase gate の順で disposition を決める。
      ;; HARD 違反は phase では覆せない。
      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (let [base (cond (:holds? verdict) :hold
                           (:clean? verdict) :commit
                           :else :escalate)
                ph (:phase context phase/default-phase)
                {:keys [disposition reason]} (phase/gate ph request base)]
            (case disposition
              :hold {:disposition :hold}
              :escalate {:disposition :escalate
                         :audit [{:t :approval-requested
                                  :op (:op request) :subject (:subject request)
                                  :reason (or reason :standard-escalation)
                                  :phase ph}]}
              :commit {:disposition :commit
                       :record (commit-record request proposal)}))))

      ;; **SSoT を書く唯一のノード。** 書き込みが 1 箇所に閉じていることが、
      ;; 「governor が拒否したものは決して書かれない」を構造で言える根拠。
      (g/add-node :commit
        (fn [{:keys [request context proposal record]}]
          (store/commit-record! st record)
          (let [f (commit-fact request context proposal)]
            (store/append-ledger! st f)
            {:audit [f]})))

      ;; 止めた事実も台帳に積む —— 残さないと『提案されなかった』と
      ;; 『提案されたが止まった』の区別がつかない。
      (g/add-node :hold
        (fn [{:keys [request context verdict audit]}]
          (let [f (or (last (filter #(#{:human-rejected} (:t %)) audit))
                      (hold-fact request context verdict))]
            (store/append-ledger! st (assoc f :disposition :hold))
            {})))

      ;; 人が resume するまで止まる（interrupt-before）。
      ;; resume 後にここが走り、**承認者が state に入っていて初めて** commit する。
      (g/add-node :request-approval
        (fn [{:keys [request context proposal approval]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             ;; 承認者を記録に焼く —— 誰が通したかが残らない承認は承認ではない。
             :record (update (commit-record request proposal) :value
                             assoc :approved-by (:by approval))
             :audit [(approval-fact request context approval)]}
            {:disposition :hold
             :audit [{:t :human-rejected :op (:op request)
                      :subject (:subject request)
                      :by (:by approval)}]})))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :commit :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}]
          (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer checkpointer
        :interrupt-before #{:request-approval}})))
