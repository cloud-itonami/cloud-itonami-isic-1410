(ns apparel.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave2): this repo previously had NO demo page and no generator at all.
  This namespace drives the REAL actor stack
  (`apparel.operation` -> `apparel.governor` -> `apparel.store`) through
  a scenario adapted from this repo's own `apparel.sim` demo driver
  (`clojure -M:dev:run`, confirmed BEFORE writing this file to produce a
  sensible ledger against the real seeded order/pattern ids
  `order-001`..`order-003` and `pattern-001`/`pattern-002` that match
  `apparel.store/sample-data!` -- the `:commit` node genuinely calls
  `store/commit-record!` so orders, quality-flags and shipments grow by
  real entries per committed op), trimmed to a representative subset
  (one full order-intake -> pattern-spec -> quality-flag ->
  shipment-coordinate lifecycle, and four distinct HARD-hold reasons)
  and rendered deterministically -- no invented numbers, no timestamps
  in the page content, byte-identical across reruns against the same
  seed (verify by diffing two consecutive runs).

  Styling follows the 9522/`applianceshop.render-html` reference:
  `jp-go-dds.skin/dds+skin` (デジタル庁デザインシステム + skin). Domain
  shape follows the 1313 finishingops-class actor (operation/governor/
  store/sim four-op plant-ops coordination).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [apparel.store :as store]
            [apparel.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "coord-1" :actor-role :plant-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "coord-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: order-001 clears a full lifecycle -- order-intake
  (auto-commit clean at phase 3, no capital risk), a pattern-spec
  metadata update (phase-gated -- never auto at any phase -- approved),
  a quality-flag (ALWAYS escalates -- `:coordination/quality-concern`
  permanently high-stakes -- approved) and a shipment-coordinate within
  remaining quantity (ALWAYS escalates -- never auto -- approved);
  order-003 HARD-holds a shipment-coordinate because the order is
  independently UNVERIFIED/unregistered; order-002 HARD-holds a
  shipment-coordinate whose claimed quantity would exceed the order's
  own recorded headroom (shipped 290 of 300, claim 50); pattern-001
  HARD-holds a pattern-spec with a fabricated size-code; a quality-flag
  that itself tries to finalize a safety certificate HARD-holds on
  `:safety-cert-decision-blocked` (permanent). Every HARD hold never
  reaches a human. Returns the resulting store -- every field read by
  `render` below is real governor/store output, not a hand-typed copy."
  []
  (let [db (-> (store/mem-store) (store/sample-data!))
        actor (op/build db)]
    ;; --- clean lifecycle on order-001 / pattern-001 ---
    (exec! actor "t1-intake"
           {:op :order-intake :effect :propose :subject "order-001"
            :patch {:last-assessed "2026-08-01" :style "cotton-shirt-XL"}})

    (exec! actor "t1-pattern"
           {:op :pattern-spec :effect :propose :subject "pattern-001"
            :value {:size-code :L :style "cotton-shirt"}})
    (approve! actor "t1-pattern")

    (exec! actor "t1-quality"
           {:op :quality-flag :effect :propose :subject "flag-1"
            :value {:order-id "order-001" :concern-type :labeling
                    :severity :moderate
                    :description "繊維組成ラベル表記が仕様と不一致"}})
    (approve! actor "t1-quality")

    (exec! actor "t1-ship"
           {:op :shipment-coordinate :effect :propose :subject "ship-1"
            :value {:order-id "order-001" :quantity 50
                    :destination "buyer-acme-warehouse"}})
    (approve! actor "t1-ship")

    ;; --- HARD holds (never reach a human) ---
    (exec! actor "t2-unverified"
           {:op :shipment-coordinate :effect :propose :subject "ship-2"
            :value {:order-id "order-003" :quantity 10
                    :destination "buyer-hakusen-warehouse"}})

    (exec! actor "t3-over-qty"
           {:op :shipment-coordinate :effect :propose :subject "ship-3"
            :value {:order-id "order-002" :quantity 50
                    :destination "buyer-nishijin-warehouse"}})

    (exec! actor "t4-size"
           {:op :pattern-spec :effect :propose :subject "pattern-001"
            :value {:size-code :mega-plus-select}})

    (exec! actor "t5-safety-cert"
           {:op :quality-flag :effect :propose :subject "flag-2"
            :value {:order-id "order-001" :concern-type :safety
                    :severity :high :safety-cert-finalized? true
                    :description "安全証明書を最終確定"}})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject]
  (last (filter #(= (:subject %) subject) ledger)))

(defn- status-cell [ledger subject]
  (let [f (last-fact-for ledger subject)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (or (-> f :violations first :rule)
                     (first (:basis f)))]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      (= :approval-rejected (:t f)) "<span class=\"critical\">approval rejected</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- ready-cell [{:keys [verified? registered?]}]
  (cond
    (and verified? registered?) "<span class=\"ok\">verified &amp; registered</span>"
    registered? "<span class=\"warn\">registered, unverified</span>"
    verified? "<span class=\"warn\">verified, unregistered</span>"
    :else "<span class=\"critical\">unverified / unregistered</span>"))

(defn- order-row [ledger {:keys [id style customer-id quantity shipped-quantity] :as o}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s / %s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc style) (esc customer-id)
          (esc shipped-quantity) (esc quantity)
          (ready-cell o)
          (status-cell ledger id)))

(defn- pattern-row [ledger {:keys [id style size-code sizes] :as p}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc style) (esc (some-> size-code name))
          (esc (str/join ", " (map name (sort sizes))))
          (ready-cell p)
          (status-cell ledger id)))

(defn- quality-row [{:keys [id order-id concern-type severity flag-number description]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (or flag-number id)) (esc order-id)
          (esc (some-> concern-type name)) (esc (some-> severity name))
          (esc id) (esc description)))

(defn- shipment-row [{:keys [id order-id quantity destination shipment-number]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (or shipment-number id)) (esc order-id)
          (esc quantity) (esc destination) (esc id)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) (str %))) (str/join ", "))
                    (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (README Ops, apparel.governor / apparel.phase) -- documentation of
  ;; fixed behavior, not runtime telemetry.
  ["        <tr><td><code>:order-intake</code></td><td><span class=\"ok\">phase-3 auto-commit when clean, no capital risk</span> &middot; HARD hold if request effect is not <code>:propose</code> or sewing control is claimed</td></tr>"
   "        <tr><td><code>:pattern-spec</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase</span> &middot; HARD hold on fabricated size-code</td></tr>"
   "        <tr><td><code>:quality-flag</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase</span> &middot; permanently blocks safety-cert finalization</td></tr>"
   "        <tr><td><code>:shipment-coordinate</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase</span> &middot; HARD hold if order unverified/unregistered or quantity headroom exceeded (independently recomputed)</td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        orders (store/all-orders db)
        patterns (store/all-patterns db)
        quality (store/quality-flags db)
        ship-ids (distinct
                  (concat ["ship-1" "ship-2" "ship-3"]
                          (keep (fn [f]
                                  (when (= :shipment-coordinate (:op f))
                                    (:subject f)))
                                ledger)))
        shipments (vec (keep #(store/shipment db %) ship-ids))
        order-rows (str/join "\n" (map (partial order-row ledger) orders))
        pattern-rows (str/join "\n" (map (partial pattern-row ledger) patterns))
        quality-rows (str/join "\n" (map quality-row quality))
        shipment-rows (str/join "\n" (map shipment-row shipments))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-1410 &middot; apparel manufacturing</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Manufacture of wearing apparel (ISIC 1410) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · never operates sewing equipment or finalizes safety certs</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Production orders</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>apparel.store</code> via <code>apparel.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Order</th><th>Style</th><th>Customer</th><th>Shipped / Qty</th><th>Verification</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     order-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Pattern / size-spec metadata</h2>\n"
     "    <p class=\"muted\">Size-spec metadata only this wave — no pattern craft engine (geometry / grading / CAD). Fabricated size-codes are HARD-held by the governor.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Pattern</th><th>Style</th><th>Size code</th><th>Sizes</th><th>Verification</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     pattern-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Quality flags (committed)</h2>\n"
     "    <p class=\"muted\">Concern flags that actually committed after human approval. HARD holds (including permanent safety-cert finalization blocks) never appear here.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Flag #</th><th>Order</th><th>Type</th><th>Severity</th><th>Subject</th><th>Description</th></tr></thead>\n"
     "      <tbody>\n"
     quality-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Shipment drafts (committed)</h2>\n"
     "    <p class=\"muted\">Outbound shipment coordination drafts that committed after human approval. Quantity headroom is independently recomputed — never trusted from the proposal.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Shipment #</th><th>Order</th><th>Qty</th><th>Destination</th><th>Subject</th></tr></thead>\n"
     "      <tbody>\n"
     shipment-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Apparel Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Order verification and shipment quantity headroom are independently re-derived from the store; request <code>:effect</code> must be <code>:propose</code>; direct sewing/cutting/pressing control and safety-certificate finalization are permanently blocked.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)
        parent (.getParentFile (java.io.File. out))]
    (when parent (.mkdirs parent))
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/quality-flags db)) "quality flags,"
             (count (store/shipment-history db)) "shipment drafts )")))
