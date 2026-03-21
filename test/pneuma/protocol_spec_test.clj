(ns pneuma.protocol-spec-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.protocol :as p]
              [pneuma.formalism.capability :as cap]
              [pneuma.protocol-spec :as spec]
              [pneuma.gap.core :as gap]))

;; Specification test: pneuma checks its own protocol layer.

(deftest protocol-formalisms-test
  ;; The specification instances are well-formed.
         (testing "protocol formalisms"
                  (testing "effect signature has six operations"
                           (is (= 6 (count (p/extract-refs spec/protocol-operations
                                                           :operation-ids)))))

                  (testing "formalism caps require five operations"
                           (is (= 5 (count (p/extract-refs spec/formalism-record-caps
                                                           :dispatch-refs)))))

                  (testing "morphism caps require one operation"
                           (is (= 1 (count (p/extract-refs spec/morphism-record-caps
                                                           :dispatch-refs)))))

                  (testing "type schema covers all output types"
                           (let [output-types (p/extract-refs spec/protocol-operations
                                                              :operation-outputs)
                                 known-types (p/extract-refs spec/protocol-types
                                                             :type-ids)]
                                (is (every? known-types output-types)
                                    (str "unregistered types: "
                                         (pr-str (remove known-types output-types))))))))

(deftest protocol-gap-report-test
  ;; The gap report on pneuma.protocol should be clean —
  ;; all object and morphism gaps conform.
         (let [report (spec/protocol-gap-report)]

              (testing "protocol gap report"
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

                       (testing "path gaps are empty (no cycles)"
                                (is (empty? (:path-gaps report)))))))

(deftest protocol-gap-report-detects-rename-test
  ;; If a protocol method were renamed, the capability set would
  ;; have a dangling ref. Simulate by adding an unknown operation.
         (testing "gap report detects simulated rename"
                  (let [bad-caps (cap/capability-set
                                  {:id :bad-formalism
                                   :dispatch #{:->schema :->monitor :->gen
                                               :->gap-type :extract-refs
                                               :renamed-method}})
                        report (gap/gap-report
                                {:formalisms
                                 (assoc spec/protocol-formalisms
                                        :capability-set/formalism bad-caps)
                                 :registry spec/protocol-registry})]

                       (testing "has failures"
                                (is (gap/has-failures? report)))

                       (testing "the existential morphism catches the dangling ref"
                                (let [morph-failures (:morphism-gaps (gap/failures report))]
                                     (is (some #(and (= :diverges (:status %))
                                                     (contains? (-> % :detail :dangling-refs)
                                                                :renamed-method))
                                               morph-failures)))))))
