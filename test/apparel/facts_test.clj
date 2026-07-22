(ns apparel.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [apparel.facts :as facts]))

(deftest catalog-has-jurisdictions
  "Catalog should define at least 6 jurisdictions with official spec-basis."
  (is (>= (count facts/catalog) 6))
  (is (contains? facts/catalog :VNM))
  (is (contains? facts/catalog :BGD))
  (is (contains? facts/catalog :USA))
  (is (contains? facts/catalog :IND))
  (is (contains? facts/catalog :GBR))
  (is (contains? facts/catalog :DEU)))

(deftest uk-restricted-substances
  "UK (GBR) has a restricted-substances citation distinct in kind from
  VNM/BGD/USA/IND's manufacturing-process/disclosure shape: a chemical-
  composition limit on the finished garment itself (UK REACH Annex XVII
  Entry 43, azo dyes)."
  (let [reqs (facts/requirement-citations :GBR)]
    (is (map? reqs))
    (is (contains? reqs :restricted-substances))
    (doseq [[_key req] reqs]
      (is (:spec-basis req) (str "Requirement should have spec-basis: " _key))
      (is (seq (:evidence req)) (str "Requirement should list evidence checklist: " _key)))
    (is (facts/required-evidence-satisfied? :GBR
          {:azo-dye-test-report true :aromatic-amine-concentration-below-threshold true}))
    (is (not (facts/required-evidence-satisfied? :GBR
               {:azo-dye-test-report true})))))

(deftest germany-requirements
  "Germany (DEU) has plant-registration (GewO Section 14 trade-notification
  obligation) and labor-standards (ArbZG Section 3 max daily working hours)
  citations, direct-primary-text HIGH confidence -- both fetched directly
  from gesetze-im-internet.de, the German Federal Ministry of Justice's
  official law repository."
  (let [reqs (facts/requirement-citations :DEU)]
    (is (map? reqs))
    (is (contains? reqs :plant-registration))
    (is (contains? reqs :labor-standards))
    (doseq [[_key req] reqs]
      (is (:spec-basis req) (str "Requirement should have spec-basis: " _key))
      (is (seq (:evidence req)) (str "Requirement should list evidence checklist: " _key)))
    (is (facts/required-evidence-satisfied? :DEU
          {:trade-notification true :business-registration-confirmation true
           :working-hours-record true :overtime-averaging-log true}))
    (is (not (facts/required-evidence-satisfied? :DEU
               {:trade-notification true})))))

(deftest india-requirements
  "India jurisdiction should have official spec-basis for all requirements."
  (let [reqs (facts/requirement-citations :IND)]
    (is (map? reqs))
    (is (contains? reqs :plant-registration))
    (is (contains? reqs :labor-standards))
    (is (contains? reqs :quality-labeling))
    (doseq [[_key req] reqs]
      (is (:spec-basis req) (str "Requirement should have spec-basis: " _key))
      (is (seq (:evidence req)) (str "Requirement should list evidence checklist: " _key)))))

(deftest jurisdiction-coverage-honest
  "Coverage reporting should be honest about scope."
  (let [cov (facts/coverage)]
    (is (map? cov))
    (is (>= (:implemented cov) 3))
    (is (= (:worldwide-jurisdictions cov) 194))
    (is (> (:coverage-pct cov) 0))
    (is (contains? cov :note))))

(deftest vietnam-requirements
  "Vietnam jurisdiction should have official spec-basis for all requirements."
  (let [reqs (facts/requirement-citations :VNM)]
    (is (map? reqs))
    (is (contains? reqs :plant-registration))
    (is (contains? reqs :labor-standards))
    (is (contains? reqs :quality-labeling))
    (is (contains? reqs :export-compliance))
    ;; Each requirement should have spec-basis
    (doseq [[_key req] reqs]
      (is (:spec-basis req) (str "Requirement should have spec-basis: " _key))
      (is (seq (:evidence req)) (str "Requirement should list evidence checklist: " _key)))))

(deftest evidence-satisfaction
  "Test jurisdiction-specific evidence checklist satisfaction."
  (testing "Vietnam complete plant registration requirement"
    (let [complete {:plant-license true :environmental-permit true :worker-contract true :wage-record true :safety-training true :quality-cert true :labeling-audit true :export-permit true :shipment-manifest true}]
      (is (facts/required-evidence-satisfied? :VNM complete))))

  (testing "Incomplete evidence should fail"
    (let [checklist {:plant-license true}]
      (is (not (facts/required-evidence-satisfied? :VNM checklist)))))

  (testing "USA complete requirements"
    (let [checklist {:tariff-cert true :origin-marking true :wage-hour-record true :safety-training true :fiber-analysis true :label-affidavit true}]
      (is (facts/required-evidence-satisfied? :USA checklist))))

  (testing "India complete requirements"
    (let [checklist {:factory-license true :site-approval true :working-hours-record true :overtime-log true :label-content-verified true :fibre-composition-disclosed true}]
      (is (facts/required-evidence-satisfied? :IND checklist))))

  (testing "Germany complete requirements"
    (let [checklist {:trade-notification true :business-registration-confirmation true :working-hours-record true :overtime-averaging-log true}]
      (is (facts/required-evidence-satisfied? :DEU checklist)))))

(deftest spec-basis-citations
  "All spec-basis citations should be strings (official references)."
  (doseq [[_jurisdiction jurisdiction-data] facts/catalog]
    (let [reqs (:requirements jurisdiction-data)]
      (doseq [[_req-key req-spec] reqs]
        (is (string? (:spec-basis req-spec))
          (str "Spec-basis should be a string in " _jurisdiction "/" _req-key))))))
