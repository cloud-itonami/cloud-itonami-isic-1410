(ns apparel.store
  "SSoT for the apparel-manufacturing plant-operations coordinator.

  In-memory reference implementation; production systems would use Datomic or a
  similar persistent event store. The read accessors and guards below are the
  facts the governor censors against — **they are never inferred from a
  proposal**, which is the whole point of having a store the advisor cannot
  write to directly.

  ## 台帳は append-only

  『どのバッチが誰の承認で記録されたか / どの提案が何の違反で止まったか』は
  常に不変ログへの query。ここが監査可能性の実体で、**確定した事実と止めた事実の
  両方**を積む —— 止めた方を残さないと『提案されなかった』と『提案されたが
  止まった』の区別がつかない。")

;; ----------------------------- store initialization -----------------------------

(defn mem-store
  "Create an in-memory store with reference data for apparel manufacturing."
  []
  {:data (atom {
           :plants {
             "plant-001" {:name "Community Apparel Factory A"
                         :location "Vietnam"
                         :registered? true
                         :jurisdiction :VNM}}
           :production-batches {
             "batch-001" {:plant "plant-001"
                         :style "cotton-shirt-XL"
                         :quantity 500
                         :verified? true
                         :quality-grade "standard"}
             "batch-002" {:plant "plant-001"
                         :style "linen-dress-M"
                         :quantity 300
                         :verified? false
                         :quality-grade "standard"}}
           :shipments {
             "ship-001" {:batch "batch-001"
                        :destination "wholesale-buyer-A"
                        :qty 500
                        :scheduled-date "2026-07-20"
                        :status :pending}}
           :maintenance-log {
             "maint-001" {:equipment "cutting-machine-03"
                         :last-service "2026-06-15"
                         :status :operational}}})})

;; ----------------------------- accessors -----------------------------

(defn plant
  "Get plant record by ID."
  [st plant-id]
  (get-in @(:data st) [:plants plant-id]))

(defn production-batch
  "Get production batch record by ID."
  [st batch-id]
  (get-in @(:data st) [:production-batches batch-id]))

(defn shipment
  "Get shipment record by ID."
  [st shipment-id]
  (get-in @(:data st) [:shipments shipment-id]))

(defn equipment
  "Get equipment maintenance record by ID."
  [st equipment-id]
  (get-in @(:data st) [:maintenance-log equipment-id]))

;; ----------------------------- guards -----------------------------

(defn plant-verified?
  "Check if plant is registered and authorized."
  [st plant-id]
  (let [p (plant st plant-id)]
    (:registered? p false)))

(defn batch-verified?
  "Check if production batch is verified."
  [st batch-id]
  (let [b (production-batch st batch-id)]
    (:verified? b false)))

(defn batch-plant-verified?
  "Check if batch's plant is verified."
  [st batch-id]
  (let [b (production-batch st batch-id)
        plant-id (:plant b)]
    (plant-verified? st plant-id)))

;; ----------------------------- commit / ledger -----------------------------

(defn commit-record!
  "確定した提案を SSoT に反映する。

  `record` は `{:effect .. :path [..] :value ..}`。effect ごとに書き先を固定して
  あるのは、advisor が任意の場所に書ける経路を作らないため —— 提案が持ち込める
  のは『どのエンティティを、宣言済みの effect の形で』までで、書き先そのものは
  この関数が決める。"
  [st {:keys [effect path value]}]
  (let [id (first path)]
    (case effect
      :batch/upsert
      (swap! (:data st) update-in [:production-batches id] merge value)

      :maintenance/schedule
      (swap! (:data st) update-in [:maintenance-log id] merge value)

      :shipment/coordinate
      (swap! (:data st) update-in [:shipments id] merge value)

      :quality-defect/flag
      (swap! (:data st) update :quality-defects (fnil conj []) (assoc value :id id))

      ;; 未知の effect は書かない。governor が allowlist で止めているので通常
      ;; ここには来ないが、**来たときに黙って書かない**のが二重の床。
      nil)
    nil))

(defn append-ledger!
  "不変の決定事実を 1 件積む。"
  [st fact]
  (swap! (:data st) update :ledger (fnil conj []) fact)
  fact)

(defn get-ledger
  "append-only の決定台帳。"
  [st]
  (get @(:data st) :ledger []))

(defn quality-defects
  "flag された品質不良の append-only ログ。"
  [st]
  (get @(:data st) :quality-defects []))
