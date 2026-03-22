(ns pneuma.lean-spec-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.protocol :as p]
              [pneuma.formalism.capability :as cap]
              [pneuma.lean-spec :as spec]
              [pneuma.gap.core :as gap]))

;; Specification test: pneuma checks its own lean projection layer.

(deftest lean-formalisms-test
  ;; The lean specification instances are well-formed.
         (testing "lean formalisms"
                  (testing "effect signature has two operations"
                           (is (= 2 (count (p/extract-refs spec/lean-operations
                                                           :operation-ids)))))

                  (testing "formalism caps require one operation"
                           (is (= 1 (count (p/extract-refs spec/lean-formalism-caps
                                                           :dispatch-refs)))))

                  (testing "morphism caps require one operation"
                           (is (= 1 (count (p/extract-refs spec/lean-morphism-caps
                                                           :dispatch-refs)))))

                  (testing "type schema covers all output types"
                           (let [output-types (p/extract-refs spec/lean-operations
                                                              :operation-outputs)
                                 known-types (p/extract-refs spec/lean-types
                                                             :type-ids)]
                                (is (every? known-types output-types)
                                    (str "unregistered types: "
                                         (pr-str (remove known-types output-types))))))))

(deftest lean-gap-report-test
  ;; The gap report on the lean layer should be clean —
  ;; all object and morphism gaps conform.
         (let [report (spec/lean-gap-report)]

              (testing "lean gap report"
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

(deftest lean-gap-report-detects-rename-test
  ;; If a lean method were renamed, the capability set would have a
  ;; dangling ref. Simulate by adding an unknown operation.
         (testing "gap report detects simulated rename"
                  (let [bad-caps (cap/capability-set
                                  {:label "test caps"
                                   :id :bad-lean-formalism
                                   :dispatch #{:->lean :->renamed-lean}})
                        report (gap/gap-report
                                {:formalisms
                                 (assoc spec/lean-formalisms
                                        :capability-set/lean-formalism bad-caps)
                                 :registry spec/lean-registry})]

                       (testing "has failures"
                                (is (gap/has-failures? report)))

                       (testing "the existential morphism catches the dangling ref"
                                (let [morph-failures (:morphism-gaps (gap/failures report))]
                                     (is (some #(and (= :diverges (:status %))
                                                     (contains? (-> % :detail :dangling-refs)
                                                                :->renamed-lean))
                                               morph-failures)))))))
