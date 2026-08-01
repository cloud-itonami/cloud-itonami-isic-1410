(ns apparel.actor-test
  "ApparelOperationActor —— langgraph StateGraph としての振る舞い。

  `apparel.phase` が『本物の StateGraph は既知のギャップ』と自認していた部分が
  埋まったことを、**グラフを実際に走らせて**確かめる（提案と governor を直に
  呼ぶ `apparel.sim` の検証では、封じ込め・承認・台帳のどれも通らない）。"
  (:require [clojure.test :refer [deftest is testing]]
            [apparel.actor :as actor]
            [apparel.store :as store]
            [langgraph.graph :as g]))

(def ^:private coordinator
  {:actor-id "apparel-actor" :role :coordinator :phase 3})

(defn- run! [a tid request]
  (g/run* a {:request request :context coordinator} {:thread-id tid}))

(defn- approve! [a tid by]
  (g/run* a {:approval {:status :approved :by by}} {:thread-id tid :resume? true}))

(defn- reject! [a tid by]
  (g/run* a {:approval {:status :rejected :by by}} {:thread-id tid :resume? true}))

(deftest actor-builds
  (is (some? (actor/build (store/mem-store)))))

(deftest clean-batch-logging-auto-commits-at-phase-3
  (testing "物理的・金銭的リスクを負わない記録だけが自動確定する"
    (let [st (store/mem-store)
          a (actor/build st)
          r (run! a "t1" {:op :proposal/log-production-batch :subject "batch-001"})]
      (is (= :commit (get-in r [:state :disposition])))
      (is (seq (store/get-ledger st)))
      (is (= :committed (:t (last (store/get-ledger st))))))))

(deftest unverified-batch-is-held-and-never-written
  (testing "governor が拒否したものは SSoT に書かれない —— それが唯一の不変条件"
    (let [st (store/mem-store)
          a (actor/build st)
          before (store/production-batch st "batch-002")
          r (run! a "t2" {:op :proposal/log-production-batch :subject "batch-002"})]
      (is (= :hold (get-in r [:state :disposition])))
      (is (= before (store/production-batch st "batch-002"))
          "未検証バッチの記録は SSoT を変えない")
      (testing "止めた事実も台帳に残る（『提案されなかった』と区別がつく）"
        (is (= :hold (:disposition (last (store/get-ledger st)))))))))

(deftest maintenance-scheduling-waits-for-a-human
  (testing "保全計画は現場の人と設備を動かす予定を作る —— 自動では通さない"
    (let [st (store/mem-store)
          a (actor/build st)
          r (run! a "t3" {:op :proposal/schedule-maintenance :subject "maint-001"})]
      (is (= :escalate (get-in r [:state :disposition])))
      (testing "承認すると確定し、承認者が記録に残る"
        (let [r2 (approve! a "t3" "plant-supervisor-1")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (= "plant-supervisor-1"
                 (:approved-by (store/equipment st "maint-001")))
              "誰が通したかが残らない承認は承認ではない"))))))

(deftest shipment-coordination-is-held-until-the-plant-chain-verifies
  (testing "出荷は subject から工場を辿れない限り HARD で止まる（既存 governor の判断）"
    (let [st (store/mem-store)
          a (actor/build st)
          before (store/shipment st "ship-001")
          r (run! a "t3b" {:op :actuation/coordinate-shipment :subject "ship-001"})]
      (is (= :hold (get-in r [:state :disposition])))
      (is (= before (store/shipment st "ship-001"))))))

(deftest a-rejected-approval-holds-and-writes-nothing
  (let [st (store/mem-store)
        a (actor/build st)
        before (store/equipment st "maint-001")]
    (run! a "t4" {:op :proposal/schedule-maintenance :subject "maint-001"})
    (let [r (reject! a "t4" "plant-supervisor-1")]
      (is (= :hold (get-in r [:state :disposition])))
      (is (= before (store/equipment st "maint-001"))
          "却下された提案は SSoT を変えない"))))

(deftest quality-defect-always-escalates
  (testing "不良報告は出荷可否を左右する —— confidence がいくら高くても人が見る"
    (let [st (store/mem-store)
          a (actor/build st)
          r (run! a "t5" {:op :proposal/flag-quality-defect :subject "batch-001"
                          :value {:defect-type "labeling-error"}})]
      (is (not= :commit (get-in r [:state :disposition]))))))

(deftest the-effect-table-is-closed
  (testing "advisor は書き先を名乗れない —— op から引く閉じた表が決める"
    (is (= #{:proposal/log-production-batch :proposal/schedule-maintenance
             :proposal/flag-quality-defect :actuation/coordinate-shipment}
           (set (keys actor/op->effect))))
    (is (nil? (actor/op->effect :proposal/do-whatever-i-want)))))

(deftest hard-violations-cannot-be-unlocked-by-a-later-phase
  (testing "phase が緩めるのは『自動で確定してよいか』だけで『通してよいか』ではない"
    (let [st (store/mem-store)
          a (actor/build st)
          r (g/run* a {:request {:op :proposal/log-production-batch :subject "batch-002"}
                       :context (assoc coordinator :phase 3)}
                    {:thread-id "t6"})]
      (is (= :hold (get-in r [:state :disposition]))))))
