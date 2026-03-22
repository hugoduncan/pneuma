(ns pneuma.core-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.core :as p]))

;; Tests for the public API. Verifies that constructors,
;; gap report assembly, and per-formalism checks work
;; through the pneuma.core entry point.

(def test-ops
     (p/effect-signature
      {:label "test ES"
       :operations
       {:read {:input {:key :Keyword} :output :String}
        :write {:input {:key :Keyword :val :String} :output :Boolean}}}))

(def test-caps
     (p/capability-set
      {:label "test caps"
       :id :test-caps
       :dispatch #{:read :write}}))

(def test-registry
     {:caps->ops
      (p/existential-morphism
       {:id :caps->ops
        :from :capability-set
        :to :effect-signature
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})})

(deftest constructors-test
  ;; Contracts: all constructors are accessible via pneuma.core
  ;; and produce valid formalism/morphism records.
         (testing "constructors"
                  (testing "effect-signature creates a valid formalism"
                           (is (some? (p/check-schema test-ops {:op :read :key :k}))))

                  (testing "capability-set creates a valid formalism"
                           (is (some? test-caps)))))

(deftest gap-report-via-core-test
  ;; Contracts: gap-report is callable through the public API
  ;; and produces the three-layer structure.
         (testing "gap-report"
                  (testing "produces a conforming report"
                           (let [report (p/gap-report
                                         {:formalisms {:effect-signature test-ops
                                                       :capability-set test-caps}
                                          :registry test-registry})]
                                (is (contains? report :object-gaps))
                                (is (contains? report :morphism-gaps))
                                (is (contains? report :path-gaps))
                                (is (not (p/has-failures? report)))))))

(deftest check-schema-test
  ;; Contracts: check-schema validates state against a
  ;; formalism's schema projection.
         (testing "check-schema"
                  (testing "conforms for valid state"
                           (let [result (p/check-schema test-ops {:op :read :key :k})]
                                (is (= :conforms (:status result)))))

                  (testing "diverges for invalid state"
                           (let [result (p/check-schema test-ops "not-a-map")]
                                (is (= :diverges (:status result)))
                                (is (some? (-> result :detail :errors)))))))

(deftest check-trace-test
  ;; Contracts: check-trace replays an event log through
  ;; a formalism's monitor.
         (testing "check-trace"
                  (testing "conforms for matching trace"
                           (let [result (p/check-trace test-ops
                                                       [{:operation :read
                                                         :fields {:key :k}}])]
                                (is (= :conforms (:status result)))
                                (is (= 1 (:entries-checked result)))))

                  (testing "conforms for empty trace"
                           (let [result (p/check-trace test-ops [])]
                                (is (= :conforms (:status result)))
                                (is (= 0 (:entries-checked result)))))))

(deftest check-gen-test
  ;; Contracts: check-gen runs property-based tests using
  ;; a formalism's generator and schema.
         (testing "check-gen"
                  (testing "conforms when gen matches schema"
                           (let [result (p/check-gen test-ops {:num-tests 20})]
                                (is (= :conforms (:status result)))
                                (is (= 20 (:tests-run result)))))))

(deftest check-morphism-test
  ;; Contracts: check-morphism delegates to IConnection.check.
         (testing "check-morphism"
                  (testing "conforms for valid morphism"
                           (let [m (p/existential-morphism
                                    {:id :test-m
                                     :from :capability-set
                                     :to :effect-signature
                                     :source-ref-kind :dispatch-refs
                                     :target-ref-kind :operation-ids})
                                 gaps (p/check-morphism m test-caps test-ops)]
                                (is (= 1 (count gaps)))
                                (is (= :conforms (:status (first gaps))))))))

(deftest diff-reports-via-core-test
  ;; Contracts: diff-reports is accessible via public API.
         (testing "diff-reports"
                  (testing "identical reports have no changes"
                           (let [report (p/gap-report
                                         {:formalisms {:effect-signature test-ops
                                                       :capability-set test-caps}
                                          :registry test-registry})
                                 d (p/diff-reports report report)]
                                (is (not (p/has-changes? d)))))))
