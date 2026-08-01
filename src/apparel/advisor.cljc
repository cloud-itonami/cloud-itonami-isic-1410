(ns apparel.advisor
  "Apparel Manufacturing Plant Operations Advisor —— 封じ込めた提案層。

  **提案しか返さない。** 何が SSoT に書かれるかは `apparel.actor/op->effect` の
  閉じた表が決め、通してよいかは `apparel.governor` が決める。この ns が賢く
  なっても、賢くなくなっても、その境界は動かない —— それが封じ込めの意味。

  `Advisor` protocol 越しに注入するので、決定論の mock と実 LLM を差し替えても
  actor のコアは変わらない。")

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

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn- infer
  "request を対応する提案生成器に振る。

  `:subject` 以外の入力を取らないのは、この mock が **store の事実だけ**から
  提案を組むため（引数で結論を渡せる形にすると、governor が censor する対象が
  提案ではなく呼び出し側の意図になる）。"
  [_st {:keys [op subject value]}]
  (case op
    :proposal/log-production-batch (batch-log-proposal nil subject)
    :proposal/schedule-maintenance (maintenance-proposal nil subject)
    :proposal/flag-quality-defect  (quality-defect-proposal
                                    nil subject (get value :defect-type "unspecified"))
    :actuation/coordinate-shipment (shipment-proposal nil subject)
    {:op op :subject subject :effect :propose :cites []
     :value {:evidence {} :confidence 0.0 :detail "未対応の操作"}}))

(defn deterministic-advisor
  "決定論の advisor。既定であり、テストの基準。"
  []
  (reify Advisor (-advise [_ st req] (infer st req))))

;; `mock-advisor` は上の data-map を返す旧 API（`apparel.sim` が使っている）。
;; actor 経路では protocol 実装が要るので、こちらを protocol にも適合させる。
(extend-protocol Advisor
  #?(:clj clojure.lang.IPersistentMap :cljs PersistentArrayMap)
  (-advise [_ st req] (infer st req)))
