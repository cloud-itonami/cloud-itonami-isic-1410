(ns apparel.store
  "In-memory store for apparel manufacturing plant operations state.
  This is a reference implementation; production systems would use Datomic
  or similar persistent event store for audit and replay.")

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
