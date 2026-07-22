(ns apparel.facts
  "Per-jurisdiction apparel manufacturing compliance and labor-protection requirements.
  Every jurisdiction in this catalog is backed by an official spec-basis.
  NEVER invent requirements without an official citation.

  :IND's labor-standards citation is corroborated via multiple independent
  secondary sources rather than the primary Act text directly (see the
  catalog entry's own note) -- moderate-high, not the direct-primary-text
  confidence of its other two requirements.

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
                    :evidence [:fiber-analysis :label-affidavit]}}}

   ;; India -- WebFetch/curl-verified 2026-07-21. The Factories Act 1948's
   ;; own official India Code text (indiacode.nic.in, the Government of
   ;; India's official law repository) was read directly for Section 6;
   ;; the working-hours sections (51/54) were corroborated across multiple
   ;; independent secondary sources (indiankanoon.org and others) rather
   ;; than fetched from the primary text directly, so that citation is
   ;; MODERATE-HIGH rather than the direct-primary-text HIGH confidence of
   ;; the other two entries. The IS 15798:2007 textile-labeling standard
   ;; PDF (hosted at law.resource.org's official Bureau of Indian
   ;; Standards Right-to-Information mirror) was downloaded and read
   ;; directly, confirming its exact title. Note: this iteration also
   ;; found that the Ministry of Textiles/BIS are actively drafting NEW,
   ;; stricter mandatory labeling rules (as of this same week, per news
   ;; coverage) -- deliberately NOT cited here since they are still in
   ;; draft, not yet in force; only the existing, current IS 15798:2007
   ;; standard is cited.
   :IND
   {:name "India"
    :requirements
    {:plant-registration {:description "Factory approval, licensing and registration with the State Government / Chief Inspector of Factories (applies to factories employing 10+ workers with power, or 20+ without power)"
                         :required true
                         :spec-basis "Factories Act, 1948, Section 6 (Approval, licensing and registration of factories) -- confirmed directly on indiacode.nic.in, the Government of India's official law repository"
                         :evidence [:factory-license :site-approval]}
     :labor-standards {:description "Maximum working hours for adult workers (48 hours/week, 9 hours/day)"
                      :required true
                      :spec-basis "Factories Act, 1948, Sections 51 and 54 (Weekly hours / Daily hours) -- corroborated across multiple independent secondary legal sources, not fetched from the primary Act text directly (MODERATE-HIGH confidence)"
                      :evidence [:working-hours-record :overtime-log]}
     :quality-labeling {:description "Textile labeling and marking requirements for consumer textiles/apparel"
                       :required true
                       :spec-basis "IS 15798:2007 (Textiles -- Requirements for labelling and marking of consumer textiles), Bureau of Indian Standards"
                       :evidence [:label-content-verified :fibre-composition-disclosed]}}}

   ;; United Kingdom -- direct curl-verified 2026-07-22 against
   ;; legislation.gov.uk (The National Archives' official UK legislation
   ;; site, HTTP 200, no anti-bot blocking encountered). UK REACH is
   ;; retained EU Regulation (EC) No 1907/2006 as it continues to apply in
   ;; UK domestic law post-Brexit. This is the catalog's first
   ;; CHEMICAL-SAFETY/restricted-substances requirement -- genuinely
   ;; distinct in kind from VNM/BGD/USA/IND's plant-registration/
   ;; labor-standards/quality-labeling/tariff-compliance/fiber-content
   ;; shape, which regulate the manufacturing process or disclosure, not
   ;; the chemical composition of the finished garment itself.
   :GBR
   {:name "United Kingdom"
    :requirements
    {:restricted-substances {:description "Azo dyes that may release listed aromatic amines above 30 mg/kg (0.003% by weight) must not be used in textile/leather articles which may come into direct and prolonged contact with human skin or the oral cavity; separately, azodyes listed in Appendix 9 must not be placed on the market or used, as substances or in mixtures, at concentrations above 0.1% by weight where intended for colouring textile/leather articles"
                            :required true
                            :spec-basis "UK REACH (retained Regulation (EC) No 1907/2006) Annex XVII, Entry 43 (Azocolourants and Azodyes)"
                            :evidence [:azo-dye-test-report :aromatic-amine-concentration-below-threshold]}}}

   ;; Germany -- direct curl-verified 2026-07-22 against gesetze-im-internet.de
   ;; (the German Federal Ministry of Justice / Bundesamt fuer Justiz's
   ;; official law repository, HTTP 200 on both cites below, no anti-bot
   ;; blocking encountered). Both requirements are direct-primary-text HIGH
   ;; confidence -- the exact operative sentences were read from the cited
   ;; sections themselves, not paraphrased from secondary sources:
   ;;   - GewO Section 14(1): "Wer den selbstaendigen Betrieb eines stehenden
   ;;     Gewerbes ... anfaengt, muss dies der zustaendigen Behoerde
   ;;     gleichzeitig anzeigen." (Anyone who commences the independent
   ;;     operation of a stationary trade business must simultaneously notify
   ;;     the competent authority) -- confirmed at
   ;;     https://www.gesetze-im-internet.de/gewo/__14.html
   ;;   - ArbZG Section 3: "Die werktaegliche Arbeitszeit der Arbeitnehmer darf
   ;;     acht Stunden nicht ueberschreiten. Sie kann auf bis zu zehn Stunden
   ;;     nur verlaengert werden, wenn innerhalb von sechs Kalendermonaten oder
   ;;     innerhalb von 24 Wochen im Durchschnitt acht Stunden werktaeglich
   ;;     nicht ueberschritten werden." (The working day of employees may not
   ;;     exceed eight hours. It may be extended to up to ten hours only if
   ;;     the eight-hour daily average is not exceeded within six calendar
   ;;     months or 24 weeks) -- confirmed at
   ;;     https://www.gesetze-im-internet.de/arbzg/__3.html
   ;; A German national textile-labeling law (TextilKennzG) and the EU
   ;; textile-fibre-labelling Regulation (EU) No 1007/2011 that superseded it
   ;; were investigated as a possible third (quality-labeling) requirement,
   ;; but neither could be located/confirmed with a working direct fetch in
   ;; this session (TextilKennzG is no longer listed on gesetze-im-internet.de;
   ;; EUR-Lex returned an anti-bot HTTP 202 challenge page with no text) --
   ;; deliberately NOT cited to avoid a fabricated/paraphrased-only claim.
   :DEU
   {:name "Germany"
    :requirements
    {:plant-registration {:description "Notification (Gewerbeanzeige) to the competent local trade authority upon commencing operation of a manufacturing business (stehendes Gewerbe), including garment/apparel manufacturing plants"
                         :required true
                         :spec-basis "Gewerbeordnung (GewO) Section 14 (Anzeigepflicht -- notification obligation) -- confirmed directly on gesetze-im-internet.de, the German Federal Ministry of Justice's official law repository"
                         :evidence [:trade-notification :business-registration-confirmation]}
     :labor-standards {:description "Maximum daily working hours for employees (8 hours/day, extendable to 10 hours only where the 8-hour daily average is maintained over 6 calendar months or 24 weeks)"
                      :required true
                      :spec-basis "Arbeitszeitgesetz (ArbZG) Section 3 (Arbeitszeit der Arbeitnehmer / working hours of employees) -- confirmed directly on gesetze-im-internet.de"
                      :evidence [:working-hours-record :overtime-averaging-log]}}}})

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
