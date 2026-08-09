(ns apparel.store
  "SSoT for the apparel-manufacturing plant-operations coordination
  actor, behind a `Store` protocol so the backend is a swap, not a
  rewrite -- the same seam every `cloud-itonami-isic-*` actor uses.

  Scope note: this build ships a single `MemStore` backend only (atom
  of EDN) -- the deterministic default for dev/tests/demo, no deps.
  A pattern-craft engine is explicitly OUT of this wave.

  Four kinds of entity live here:
    - `orders`     -- production orders. `:verified?` / `:registered?`
                       ground-truth flags; `:quantity` and
                       `:shipped-quantity` for headroom.
    - `patterns`   -- size-spec *metadata* records (not craft geometry).
                       Same verified/registered discipline.
    - `quality-flags` -- append-only quality/labeling concern flags.
    - `shipments`  -- proposed outbound shipment DRAFTs.

  Plus a generic `records` map and an append-only `ledger`."
  (:require [apparel.registry :as registry]))

(defprotocol Store
  (order [s id])
  (all-orders [s])
  (pattern [s id])
  (all-patterns [s])
  (shipment [s id])
  (quality-flags [s] "append-only quality-flag log")
  (ledger [s])
  (shipment-history [s])
  (quality-history [s])
  (next-shipment-sequence [s])
  (next-quality-sequence [s])
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (get-records [s])
  (with-orders [s orders])
  (with-patterns [s patterns]))

;; ----------------------------- sample data -----------------------------

(defn- sample-orders []
  {"order-001" {:id "order-001" :style "cotton-shirt-XL" :customer-id "buyer-acme"
                :quantity 500 :shipped-quantity 100
                :verified? true :registered? true
                :plant-id "plant-001" :last-assessed "2026-07-01"}
   "order-002" {:id "order-002" :style "linen-dress-M" :customer-id "buyer-nishijin"
                :quantity 300 :shipped-quantity 290
                :verified? true :registered? true
                :plant-id "plant-001" :last-assessed "2026-07-01"}
   "order-003" {:id "order-003" :style "denim-jacket-L" :customer-id "buyer-hakusen"
                :quantity 200 :shipped-quantity 0
                :verified? false :registered? false
                :plant-id "plant-001" :last-assessed "2026-06-15"}})

(defn- sample-patterns []
  {"pattern-001" {:id "pattern-001" :style "cotton-shirt"
                  :sizes #{:S :M :L :XL} :size-code :M
                  :verified? true :registered? true}
   "pattern-002" {:id "pattern-002" :style "linen-dress"
                  :sizes #{:S :M :L} :size-code :M
                  :verified? false :registered? false}})

;; ----------------------------- shared commit helpers -----------------------------

(defn- propose-shipment!
  [s shipment-id]
  (let [seq-n (next-shipment-sequence s)
        result (registry/register-shipment shipment-id seq-n)]
    {:result result
     :patch {:shipment-number (get result "shipment_number")}}))

(defn- flag-quality!
  [s flag-id]
  (let [seq-n (next-quality-sequence s)
        result (registry/register-quality-flag flag-id seq-n)]
    {:result result
     :patch {:flag-number (get result "flag_number")}}))

;; ----------------------------- MemStore -----------------------------

(defrecord MemStore [a]
  Store
  (order [_ id] (get-in @a [:orders id]))
  (all-orders [_] (sort-by :id (vals (:orders @a))))
  (pattern [_ id] (get-in @a [:patterns id]))
  (all-patterns [_] (sort-by :id (vals (:patterns @a))))
  (shipment [_ id] (get-in @a [:shipments id]))
  (quality-flags [_] (:quality-flags @a))
  (ledger [_] (:ledger @a))
  (shipment-history [_] (:shipment-history @a))
  (quality-history [_] (:quality-history @a))
  (next-shipment-sequence [_] (:shipment-sequence @a 0))
  (next-quality-sequence [_] (:quality-sequence @a 0))
  (get-records [_] (:records @a))
  (commit-record! [s {:keys [effect path value] :as record}]
    (cond
      (= effect :order/upsert)
      (swap! a update-in [:orders (first path)] merge (assoc value :id (first path)))

      (= effect :pattern/upsert)
      (swap! a update-in [:patterns (first path)] merge (assoc value :id (first path)))

      (= effect :quality/flag)
      (let [flag-id (first path)
            {:keys [result patch]} (flag-quality! s flag-id)
            flagged (merge value {:id flag-id} patch)]
        (swap! a (fn [state]
                   (-> state
                       (update :quality-sequence (fnil inc 0))
                       (update :quality-flags conj flagged)
                       (update :quality-history registry/append result))))
        result)

      (= effect :shipment/propose)
      (let [shipment-id (first path)
            order-id (:order-id value)
            {:keys [result patch]} (propose-shipment! s shipment-id)]
        (swap! a (fn [state]
                   (-> state
                       (update :shipment-sequence (fnil inc 0))
                       (update-in [:shipments shipment-id] merge (assoc value :id shipment-id) patch)
                       (update :shipment-history registry/append result)
                       (update-in [:orders order-id :shipped-quantity]
                                  (fn [prev]
                                    (+ (long (or prev 0))
                                       (long (or (:quantity value) 0))))))))
        result)

      (and (nil? effect) (:id record))
      (swap! a assoc-in [:records (:id record)] record)

      :else nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-orders [s orders] (when (seq orders) (swap! a assoc :orders orders)) s)
  (with-patterns [s patterns] (when (seq patterns) (swap! a assoc :patterns patterns)) s))

(defn mem-store
  "A fresh, empty MemStore."
  []
  (->MemStore (atom {:orders {} :patterns {} :shipments {}
                     :records {} :quality-flags []
                     :ledger [] :shipment-sequence 0 :shipment-history []
                     :quality-sequence 0 :quality-history []})))

(defn sample-data!
  "Seeds `s` with a small offline order + pattern set:
    - order-001 verified+registered with shipping headroom
    - order-002 verified+registered nearly fully shipped (small new
      shipment blows through quantity -- HARD hold)
    - order-003 UNVERIFIED/unregistered (blocks shipment)
    - pattern-001 verified+registered size-spec
    - pattern-002 UNVERIFIED/unregistered"
  [s]
  (with-orders s (sample-orders))
  (with-patterns s (sample-patterns))
  s)

(defn get-ledger [s] (ledger s))
