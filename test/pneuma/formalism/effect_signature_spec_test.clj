(ns pneuma.formalism.effect-signature-spec-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.protocol :as p]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature-spec :as spec]
              [pneuma.gap.core :as gap]))

;; Specification test: pneuma checks its own effect-signature namespace.

(deftest es-formalisms-test
  ;; The specification instances are well-formed and match the
  ;; actual effect-signature namespace's public API and ref-kinds.
         (testing "effect-signature spec formalisms"
                  (testing "api operations has three operations"
                           (is (= 3 (count (p/extract-refs spec/es-api-operations
                                                           :operation-ids)))))

                  (testing "ref operations has three ref-kinds"
                           (is (= 3 (count (p/extract-refs spec/es-ref-operations
                                                           :operation-ids)))))

                  (testing "api caps match api operations"
                           (is (= (p/extract-refs spec/es-api-caps :dispatch-refs)
                                  (p/extract-refs spec/es-api-operations :operation-ids))))

                  (testing "ref-kind caps match ref operations"
                           (is (= (p/extract-refs spec/es-ref-kind-caps :dispatch-refs)
                                  (p/extract-refs spec/es-ref-operations :operation-ids))))

                  (testing "type schema covers all api output types"
                           (let [output-types (p/extract-refs spec/es-api-operations
                                                              :operation-outputs)
                                 known-types (p/extract-refs spec/es-types
                                                             :type-ids)]
                                (is (every? known-types output-types)
                                    (str "unregistered types: "
                                         (pr-str (remove known-types output-types))))))

                  (testing "type schema covers all ref output types"
                           (let [output-types (p/extract-refs spec/es-ref-operations
                                                              :operation-outputs)
                                 known-types (p/extract-refs spec/es-types
                                                             :type-ids)]
                                (is (every? known-types output-types)
                                    (str "unregistered types: "
                                         (pr-str (remove known-types output-types))))))))

(deftest es-gap-report-test
  ;; The gap report on the effect-signature spec should be clean.
         (let [report (spec/es-gap-report)]

              (testing "effect-signature gap report"
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

(deftest es-gap-report-detects-missing-ref-kind-test
  ;; If a ref-kind were added to the cap set but not modeled as a
  ;; ref operation, the existential morphism catches the dangling ref.
         (testing "gap report detects simulated missing ref-kind"
                  (let [bad-caps (cap/capability-set
                                  {:label "test caps"
                                   :id :bad-ref-kinds
                                   :dispatch #{:operation-ids :callback-refs
                                               :operation-outputs :bogus-refs}})
                        report (gap/gap-report
                                {:formalisms
                                 (assoc spec/es-formalisms
                                        :capability-set/es-ref-kinds bad-caps)
                                 :registry spec/es-registry})]

                       (testing "has failures"
                                (is (gap/has-failures? report)))

                       (testing "the existential morphism catches the dangling ref"
                                (let [morph-failures (:morphism-gaps (gap/failures report))]
                                     (is (some #(and (= :diverges (:status %))
                                                     (contains? (-> % :detail :dangling-refs)
                                                                :bogus-refs))
                                               morph-failures)))))))
