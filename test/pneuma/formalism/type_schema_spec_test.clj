(ns pneuma.formalism.type-schema-spec-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.protocol :as p]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema-spec :as spec]
              [pneuma.gap.core :as gap]))

;; Specification test: pneuma checks its own type-schema namespace.

(deftest ts-formalisms-test
  ;; The specification instances are well-formed.
         (testing "type-schema spec formalisms"
                  (testing "api operations has one operation"
                           (is (= 1 (count (p/extract-refs spec/ts-api-operations
                                                           :operation-ids)))))

                  (testing "ref operations has one ref-kind"
                           (is (= 1 (count (p/extract-refs spec/ts-ref-operations
                                                           :operation-ids)))))

                  (testing "api caps match api operations"
                           (is (= (p/extract-refs spec/ts-api-caps :dispatch-refs)
                                  (p/extract-refs spec/ts-api-operations :operation-ids))))

                  (testing "ref-kind caps match ref operations"
                           (is (= (p/extract-refs spec/ts-ref-kind-caps :dispatch-refs)
                                  (p/extract-refs spec/ts-ref-operations :operation-ids))))

                  (testing "type schema covers all output types"
                           (let [api-outputs (p/extract-refs spec/ts-api-operations
                                                             :operation-outputs)
                                 ref-outputs (p/extract-refs spec/ts-ref-operations
                                                             :operation-outputs)
                                 known-types (p/extract-refs spec/ts-types :type-ids)]
                                (is (every? known-types api-outputs))
                                (is (every? known-types ref-outputs))))))

(deftest ts-gap-report-test
         (let [report (spec/ts-gap-report)]
              (testing "type-schema gap report"
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

(deftest ts-gap-report-detects-missing-ref-kind-test
         (testing "gap report detects simulated missing ref-kind"
                  (let [bad-caps (cap/capability-set
                                  {:id :bad-ref-kinds
                                   :dispatch #{:type-ids :bogus-refs}})
                        report (gap/gap-report
                                {:formalisms
                                 (assoc spec/ts-formalisms
                                        :capability-set/ts-ref-kinds bad-caps)
                                 :registry spec/ts-registry})]

                       (testing "has failures"
                                (is (gap/has-failures? report)))

                       (testing "catches the dangling ref"
                                (let [morph-failures (:morphism-gaps (gap/failures report))]
                                     (is (some #(and (= :diverges (:status %))
                                                     (contains? (-> % :detail :dangling-refs)
                                                                :bogus-refs))
                                               morph-failures)))))))
