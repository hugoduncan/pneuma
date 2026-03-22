(ns pneuma.formalism.statechart-spec-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.protocol :as p]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.statechart-spec :as spec]
              [pneuma.gap.core :as gap]))

;; Specification test: pneuma checks its own statechart namespace.

(deftest sc-formalisms-test
  ;; The specification instances are well-formed.
         (testing "statechart spec formalisms"
                  (testing "api operations has one operation"
                           (is (= 1 (count (p/extract-refs spec/sc-api-operations
                                                           :operation-ids)))))

                  (testing "ref operations has three ref-kinds"
                           (is (= 3 (count (p/extract-refs spec/sc-ref-operations
                                                           :operation-ids)))))

                  (testing "api caps match api operations"
                           (is (= (p/extract-refs spec/sc-api-caps :dispatch-refs)
                                  (p/extract-refs spec/sc-api-operations :operation-ids))))

                  (testing "ref-kind caps match ref operations"
                           (is (= (p/extract-refs spec/sc-ref-kind-caps :dispatch-refs)
                                  (p/extract-refs spec/sc-ref-operations :operation-ids))))

                  (testing "type schema covers all output types"
                           (let [api-outputs (p/extract-refs spec/sc-api-operations
                                                             :operation-outputs)
                                 ref-outputs (p/extract-refs spec/sc-ref-operations
                                                             :operation-outputs)
                                 known-types (p/extract-refs spec/sc-types :type-ids)]
                                (is (every? known-types api-outputs))
                                (is (every? known-types ref-outputs))))))

(deftest sc-gap-report-test
         (let [report (spec/sc-gap-report)]
              (testing "statechart gap report"
                       (testing "has no failures"
                                (is (not (gap/has-failures? report))
                                    (str "failures: "
                                         (pr-str (gap/failures report)))))

                       (testing "all object gaps conform"
                                (is (every? #(= :conforms (:status %))
                                            (:object-gaps report))))

                       (testing "all morphism gaps conform"
                                (is (every? #(= :conforms (:status %))
                                            (:morphism-gaps report))))

                       (testing "path gaps are empty"
                                (is (empty? (:path-gaps report)))))))

(deftest sc-gap-report-detects-missing-ref-kind-test
         (testing "gap report detects simulated missing ref-kind"
                  (let [bad-caps (cap/capability-set
                                  {:label "test caps"
                                   :id :bad-ref-kinds
                                   :dispatch #{:state-ids :event-ids
                                               :raised-events :bogus-refs}})
                        report (gap/gap-report
                                {:formalisms
                                 (assoc spec/sc-formalisms
                                        :capability-set/sc-ref-kinds bad-caps)
                                 :registry spec/sc-registry})]

                       (testing "has failures"
                                (is (gap/has-failures? report)))

                       (testing "catches the dangling ref"
                                (let [morph-failures (:morphism-gaps (gap/failures report))]
                                     (is (some #(and (= :diverges (:status %))
                                                     (contains? (-> % :detail :dangling-refs)
                                                                :bogus-refs))
                                               morph-failures)))))))
