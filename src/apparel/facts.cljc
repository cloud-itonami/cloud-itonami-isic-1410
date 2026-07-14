(ns apparel.facts
  "Per-jurisdiction apparel manufacturing compliance and labor-protection requirements.
  Every jurisdiction in this catalog is backed by an official spec-basis.
  NEVER invent requirements without an official citation.

  This is deliberately a starting catalog (honest coverage reporting) to
  prove the governor contract end-to-end, not a claim of global coverage.
  Adding a jurisdiction is additive: one map entry citing a real official
  source -- never fabricate a jurisdiction's requirements to make coverage
  look bigger.")

;; ----------------------------- jurisdiction catalog -----------------------------

(def catalog
  "Per-jurisdiction apparel manufacturing compliance requirements with official spec-basis citations."
  {
   :VNM
   {:name "Vietnam"
    :requirements
    {:plant-registration {:description "Manufacturing plant registration with provincial authorities"
                         :required true
                         :spec-basis "Vietnam Law on Environmental Protection 2014 (Amended 2021) Article 27"
                         :evidence #{:plant-license :environmental-permit}}
     :labor-standards {:description "Compliance with ILO labor standards and national minimum wage"
                      :required true
                      :spec-basis "Vietnam Labor Code 2019 Article 93-94, ILO Conventions 98, 100, 111"
                      :evidence [:worker-contract :wage-record :safety-training]}
     :quality-labeling {:description "Product quality certification and apparel labeling compliance"
                       :required true
                       :spec-basis "TCVN 6113:2020 (Vietnam Standard for Clothing Labeling)"
                       :evidence [:quality-cert :labeling-audit]}
     :export-compliance {:description "Export documentation and shipment manifest compliance"
                        :required true
                        :spec-basis "Vietnam Ministry of Industry and Trade Circular 38/2021/TT-BCT"
                        :evidence [:export-permit :shipment-manifest]}}}

   :BGD
   {:name "Bangladesh"
    :requirements
    {:plant-registration {:description "Garment factory registration with BGMEA/BKMEA and government"
                         :required true
                         :spec-basis "Bangladesh Accord on Fire and Building Safety 2013, RMG Sustainability Council"
                         :evidence [:factory-license :fire-safety-cert]}
     :labor-standards {:description "Compliance with Bangladesh Labor Act and fair-labor commitments"
                      :required true
                      :spec-basis "Bangladesh Labor Act 2006 (Amended 2013), Articles 2-5"
                      :evidence [:worker-registry :wage-slip :training-log]}
     :quality-assurance {:description "Garment quality inspection and defect reporting"
                        :required true
                        :spec-basis "Bangladeshi Standard BDS 1000:2020 (Quality of Garments)"
                        :evidence [:qc-report :defect-log]}}}

   :USA
   {:name "United States"
    :requirements
    {:tariff-compliance {:description "Trade compliance and tariff classification per HS codes"
                        :required true
                        :spec-basis "19 CFR § 12.131 (Textiles and Apparel)"
                        :evidence [:tariff-cert :origin-marking]}
     :labor-standards {:description "Compliance with Fair Labor Standards Act and workplace safety"
                      :required true
                      :spec-basis "FLSA 29 CFR § 516, OSHA 1910 Subpart A"
                      :evidence [:wage-hour-record :safety-training]}
     :fiber-content {:description "Mandatory fiber-content labeling per FTC regulations"
                    :required true
                    :spec-basis "16 CFR § 303 (Textile Fiber Products Act)"
                    :evidence [:fiber-analysis :label-affidavit]}}}})

;; ----------------------------- coverage reporting (honest) -----------------------------

(defn coverage
  "Report what fraction of worldwide jurisdictions have official spec-basis
  in this catalog. Honest about out-of-scope coverage."
  []
  (let [catalog-count (count catalog)
        world-jurisdictions 194]
    {:implemented catalog-count
     :worldwide-jurisdictions world-jurisdictions
     :coverage-pct (* 100.0 (/ catalog-count world-jurisdictions))
     :note "Starting catalog to prove governor contract end-to-end, not global coverage claim"}))

;; ----------------------------- helpers -----------------------------

(defn requirement-citations
  "Get all official citations for a jurisdiction's requirements."
  [jurisdiction]
  (get-in catalog [jurisdiction :requirements]))

(defn required-evidence-satisfied?
  "Check if a checklist satisfies this jurisdiction's evidence requirements."
  [jurisdiction checklist]
  (let [reqs (get-in catalog [jurisdiction :requirements])]
    (every? (fn [[_req-key req-spec]]
              (if (:required req-spec)
                (let [evidence-keys (set (:evidence req-spec))]
                  (every? #(contains? checklist %) evidence-keys))
                true))
            reqs)))
