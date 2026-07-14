(ns apparel.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [apparel.facts :as facts]))

(deftest catalog-has-jurisdictions
  "Catalog should define at least 3 jurisdictions with official spec-basis."
  (is (>= (count facts/catalog) 3))
  (is (contains? facts/catalog :VNM))
  (is (contains? facts/catalog :BGD))
  (is (contains? facts/catalog :USA)))

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
      (is (facts/required-evidence-satisfied? :USA checklist)))))

(deftest spec-basis-citations
  "All spec-basis citations should be strings (official references)."
  (doseq [[_jurisdiction jurisdiction-data] facts/catalog]
    (let [reqs (:requirements jurisdiction-data)]
      (doseq [[_req-key req-spec] reqs]
        (is (string? (:spec-basis req-spec))
          (str "Spec-basis should be a string in " _jurisdiction "/" _req-key))))))
