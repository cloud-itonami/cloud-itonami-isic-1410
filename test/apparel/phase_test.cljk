(ns apparel.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:pattern-spec` / `:quality-flag` / `:shipment-coordinate`
  must NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [apparel.phase :as phase]))

(deftest pattern-spec-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a pattern-spec update"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :pattern-spec))
          (str "phase " n " must not auto-commit :pattern-spec")))))

(deftest quality-flag-never-auto-at-any-phase
  (doseq [[n {:keys [auto]}] phase/phases]
    (is (not (contains? auto :quality-flag))
        (str "phase " n " must not auto-commit :quality-flag"))))

(deftest shipment-coordinate-never-auto-at-any-phase
  (doseq [[n {:keys [auto]}] phase/phases]
    (is (not (contains? auto :shipment-coordinate))
        (str "phase " n " must not auto-commit :shipment-coordinate"))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-risk-ops
  (testing ":order-intake carries no physical/financial risk -- auto-eligible; ONLY auto-eligible op"
    (is (= #{:order-intake} (:auto (get phase/phases 3))))))

(deftest pattern-spec-enabled-from-phase-3-only
  (is (contains? (:writes (get phase/phases 3)) :pattern-spec))
  (is (not (contains? (:writes (get phase/phases 2)) :pattern-spec)))
  (is (not (contains? (:writes (get phase/phases 1)) :pattern-spec))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :order-intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :pattern-spec} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :quality-flag} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :shipment-coordinate} :commit)))))

(deftest gate-auto-commits-the-one-eligible-write-when-clean
  (is (= :commit (:disposition (phase/gate 3 {:op :order-intake} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :order-intake} :commit)))))

(deftest verdict->disposition-maps-hard-to-hold
  (is (= :hold (phase/verdict->disposition {:hard? true :escalate? false}))))

(deftest verdict->disposition-maps-escalate
  (is (= :escalate (phase/verdict->disposition {:hard? false :escalate? true}))))

(deftest verdict->disposition-maps-commit
  (is (= :commit (phase/verdict->disposition {:hard? false :escalate? false}))))
