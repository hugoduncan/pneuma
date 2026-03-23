(ns pneuma.lean.monitor-schema-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.lean.monitor-schema :as ms]))

(def test-ops
     (es/effect-signature
      {:label "test ES"
       :operations
       {:read {:input {:key :Keyword} :output :String}}}))

(def test-caps
     (cap/capability-set
      {:label "test caps"
       :id :test-caps
       :dispatch #{:read}}))

(def test-formalisms
     {:capability-set test-caps
      :effect-signature test-ops})

(deftest monitor-schema-consistency-test
  ;; emit-monitor-schema-consistency emits Lean 4 proofs that each
  ;; formalism's gap-kind and status enumerations are exhaustive.
         (testing "emit-monitor-schema-consistency"
                  (let [src (ms/emit-monitor-schema-consistency test-formalisms)]

                       (testing "emits per-formalism sections"
                                (is (str/includes? src "capability-set"))
                                (is (str/includes? src "effect-signature")))

                       (testing "emits GapKind inductives"
                                (is (str/includes? src "GapKind where")))

                       (testing "emits Status inductives"
                                (is (str/includes? src "Status where")))

                       (testing "emits gap-kind completeness"
                                (is (str/includes? src "gap_kinds_complete")))

                       (testing "emits status completeness"
                                (is (str/includes? src "statuses_complete")))

                       (testing "emits consistency conjunction"
                                (is (str/includes? src "monitor_schema_consistent"))))))
