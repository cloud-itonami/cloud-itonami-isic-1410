(ns apparel.advisor
  "ApparelAdvisor -- the *contained intelligence node* for the apparel-
  manufacturing plant-operations coordination actor (ISIC 1410).

  It normalizes order-intake patches, drafts a pattern/size-spec
  metadata update (NOT a pattern craft engine -- that is later-wave),
  drafts a quality/labeling concern flag, and drafts an outbound
  shipment coordination proposal against a production order. CRITICAL:
  it is a smart-but-untrusted advisor. It returns a *proposal* (with a
  rationale + the fields it cited), never a committed record and NEVER
  a real sewing-machine actuation or a safety-cert finalization.
  Every output is censored downstream by `apparel.governor` before
  anything touches the SSoT -- see README `What this actor does NOT do`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM with the same
  proposal shape.

  Proposal shape (all kinds):
    {:summary    str
     :rationale  str            ; informational only, NOT trusted by governor
     :cites      [kw|str ..]
     :effect     kw             ; one of closed #{:order/upsert
                                 ; :pattern/upsert :quality/flag
                                 ; :shipment/propose}
     :stake      kw|nil         ; :coordination/quality-concern | nil
     :confidence 0..1
     :value      map}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [kotoba.lang.text :as str]
            [apparel.registry :as registry]
            [apparel.store :as store]
            [langchain.model :as model]))

(defn- order-intake
  "Order intake upsert -- the advisor only normalizes/validates the
  patch; it does not invent the order's quantity or verification
  status. High confidence, low stakes -- administrative logging."
  [_db {:keys [patch value]}]
  (let [payload (or patch value {})]
    {:summary    (str "受注記録更新: " (pr-str (keys payload)))
     :rationale  "入力patchの正規化のみ。新規事実の生成なし。"
     :cites      (vec (keys payload))
     :effect     :order/upsert
     :value      payload
     :stake      nil
     :confidence 0.95}))

(defn- pattern-spec
  "Draft a pattern/size-spec *metadata* update. This is NOT a pattern
  craft engine (geometry, grading, CAD) -- that is explicitly out of
  Wave 0 Lane C2. The advisor reports what it can see; the governor
  independently re-validates size-code against the closed set."
  [db {:keys [subject value]}]
  (let [p (store/pattern db subject)
        ready? (and p (registry/pattern-ready? p))]
    {:summary    (str subject " 向けサイズ仕様メタデータ更新"
                      (when value (str " size-code=" (:size-code value))))
     :rationale  (if p
                   (str "pattern-verified?=" (registry/pattern-verified? p)
                        " pattern-registered?=" (registry/pattern-registered? p)
                        " (craft-engine=out-of-scope-this-wave)")
                   (str subject " が見つかりません -- 新規サイズ仕様メタデータとして提案"))
     :cites      (if p [subject] [])
     :effect     :pattern/upsert
     :value      (or value {})
     :stake      nil
     :confidence (if (or ready? (nil? p)) 0.85 0.35)}))

(defn- quality-flag
  "Draft a quality/labeling concern flag. ALWAYS
  `:stake :coordination/quality-concern` -- never silently downgraded.
  Never sets `:safety-cert-finalized? true` (governor permanently
  blocks any proposal that does)."
  [db {:keys [subject value]}]
  (let [order-id (:order-id value)
        o (and order-id (store/order db order-id))]
    {:summary    (str subject " 向け品質懸念報告 (" (:concern-type value) "/" (:severity value) ")"
                      (when o (str " order=" order-id)))
     :rationale  (str "concern-type=" (:concern-type value)
                      " severity=" (:severity value)
                      " description=" (:description value))
     :cites      (if o [order-id] [])
     :effect     :quality/flag
     :value      value
     :stake      :coordination/quality-concern
     :confidence 0.9}))

(defn- shipment-coordinate
  "Draft an outbound shipment coordination proposal against a production
  order. The advisor passes through the caller's claimed quantity --
  the governor NEVER trusts it and recomputes headroom independently."
  [db {:keys [subject value]}]
  (let [order-id (:order-id value)
        o (store/order db order-id)
        ready? (and o (registry/order-ready? o))
        over? (and o (registry/shipment-quantity-exceeded? o (:quantity value)))]
    {:summary    (str subject " 向け出荷調整提案 ("
                      (:quantity value) " 点)"
                      (when o (str " order=" order-id)))
     :rationale  (if o
                   (str "order-verified?=" (registry/order-verified? o)
                        " order-registered?=" (registry/order-registered? o)
                        " over-quantity?=" over?)
                   (str order-id " が見つかりません"))
     :cites      (if o [order-id] [])
     :effect     :shipment/propose
     :value      value
     :stake      nil
     :confidence (if (and ready? (not over?)) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :effect :propose :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :order-intake          (order-intake db request)
    :pattern-spec          (pattern-spec db request)
    :quality-flag          (quality-flag db request)
    :shipment-coordinate   (shipment-coordinate db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0 :value {}}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたはアパレル製造(衣料・縫製)プラント運用コーディネーターの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:order/upsert|:pattern/upsert|:quality/flag|:shipment/propose) "
       ":stake(:coordination/quality-concern か nil) :confidence(0..1)。\n"
       "重要: 未検証または未登録の受注に対する出荷を提案してはいけません。"
       "縫製・裁断・プレス設備の直接操作を絶対に提案してはいけません"
       "(この actor は提案のみを行い、実行は一切行いません)。"
       "安全証明書(safety cert)の最終確定を絶対に提案してはいけません"
       "(懸念の報告のみ許可、確定は人間の工場責任者/認証機関の専権)。"
       "パターンクラフトエンジン(幾何・グレーディング・CAD)は本 wave の対象外です。"
       "出荷数量を偽って報告してはいけません。"))

(defn- facts-for [st {:keys [op subject value]}]
  (case op
    :order-intake          {:order (store/order st subject)}
    :pattern-spec          {:pattern (store/pattern st subject)}
    :quality-flag          {:order (and (:order-id value)
                                         (store/order st (:order-id value)))}
    :shipment-coordinate   {:order (store/order st (:order-id value))}
    {}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the governor escalates/holds."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0 :value {}})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :apparel-advisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
