(ns pneuma.gap.diff-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.gap.diff :as diff]))

;; Tests for gap report diffing and filtering.
;; Uses fabricated gap reports to exercise diff logic
;; independently of real formalisms.

(def report-a
     {:object-gaps [{:layer :object :formalism :statechart :status :conforms}
                    {:layer :object :formalism :capability-set :status :conforms}]
      :morphism-gaps [{:layer :morphism :id :m1 :status :conforms}
                      {:layer :morphism :id :m2 :status :diverges
                       :detail {:reason :dangling}}]
      :path-gaps []})

(def report-b
     {:object-gaps [{:layer :object :formalism :statechart :status :conforms}
                    {:layer :object :formalism :capability-set :status :diverges
                     :detail {:kind :malformed}}]
      :morphism-gaps [{:layer :morphism :id :m1 :status :conforms}]
      :path-gaps [{:layer :path :id :p1 :status :conforms}]})

(deftest diff-reports-test
  ;; Contracts: diff-reports compares two gap reports per layer,
  ;; identifying introduced, resolved, and changed gaps.
         (testing "diff-reports"
                  (let [d (diff/diff-reports report-a report-b)]

                       (testing "detects resolved morphism gaps"
                                (is (= 1 (count (-> d :morphism-gaps :resolved))))
                                (is (= :m2 (:id (first (-> d :morphism-gaps :resolved))))))

                       (testing "detects introduced path gaps"
                                (is (= 1 (count (-> d :path-gaps :introduced))))
                                (is (= :p1 (:id (first (-> d :path-gaps :introduced))))))

                       (testing "detects changed object gaps"
                                (is (= 1 (count (-> d :object-gaps :changed))))
                                (is (= :conforms
                                       (:previous-status (first (-> d :object-gaps :changed))))))

                       (testing "unchanged gaps are not reported"
                                (is (empty? (-> d :object-gaps :introduced)))
                                (is (empty? (-> d :object-gaps :resolved)))
                                (is (empty? (-> d :morphism-gaps :introduced)))
                                (is (empty? (-> d :morphism-gaps :changed)))))))

(deftest diff-identical-reports-test
  ;; Contracts: diffing a report with itself produces no changes.
         (testing "diff-reports"
                  (testing "identical reports have no changes"
                           (let [d (diff/diff-reports report-a report-a)]
                                (is (not (diff/has-changes? d)))))))

(deftest has-changes-test
  ;; Contracts: has-changes? detects any non-empty delta.
         (testing "has-changes?"
                  (testing "returns true when diff has changes"
                           (is (diff/has-changes? (diff/diff-reports report-a report-b))))

                  (testing "returns false for identical reports"
                           (is (not (diff/has-changes? (diff/diff-reports report-a report-a)))))))

(deftest gaps-involving-test
  ;; Contracts: gaps-involving filters a report to gaps that
  ;; reference a specific formalism kind.
         (testing "gaps-involving"
                  (testing "filters object gaps by formalism"
                           (let [filtered (diff/gaps-involving report-a :statechart)]
                                (is (= 1 (count (:object-gaps filtered))))
                                (is (= :statechart (:formalism (first (:object-gaps filtered)))))))

                  (testing "returns empty for uninvolved formalism"
                           (let [filtered (diff/gaps-involving report-a :resolver-graph)]
                                (is (empty? (:object-gaps filtered)))))))
