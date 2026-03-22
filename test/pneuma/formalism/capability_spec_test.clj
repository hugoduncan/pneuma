(ns pneuma.formalism.capability-spec-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.protocol :as p]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.capability-spec :as spec]
              [pneuma.gap.core :as gap]))

;; Specification test: pneuma checks its own capability namespace.

(deftest cap-formalisms-test
  ;; The specification instances are well-formed and match the
  ;; actual capability namespace's public API and ref-kinds.
         (testing "capability spec formalisms"
                  (testing "api operations has one operation"
                           (is (= 1 (count (p/extract-refs spec/cap-api-operations
                                                           :operation-ids)))))

                  (testing "ref operations has four ref-kinds"
                           (is (= 4 (count (p/extract-refs spec/cap-ref-operations
                                                           :operation-ids)))))

                  (testing "api caps match api operations"
                           (is (= (p/extract-refs spec/cap-api-caps :dispatch-refs)
                                  (p/extract-refs spec/cap-api-operations :operation-ids))))

                  (testing "ref-kind caps match ref operations"
                           (is (= (p/extract-refs spec/cap-ref-kind-caps :dispatch-refs)
                                  (p/extract-refs spec/cap-ref-operations :operation-ids))))

                  (testing "type schema covers all output types"
                           (let [api-outputs (p/extract-refs spec/cap-api-operations
                                                             :operation-outputs)
                                 ref-outputs (p/extract-refs spec/cap-ref-operations
                                                             :operation-outputs)
                                 known-types (p/extract-refs spec/cap-types :type-ids)]
                                (is (every? known-types api-outputs)
                                    (str "unregistered api types: "
                                         (pr-str (remove known-types api-outputs))))
                                (is (every? known-types ref-outputs)
                                    (str "unregistered ref types: "
                                         (pr-str (remove known-types ref-outputs))))))))

(deftest cap-gap-report-test
  ;; The gap report should be clean.
         (let [report (spec/cap-gap-report)]
              (testing "capability gap report"
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

(deftest cap-gap-report-detects-missing-ref-kind-test
  ;; Adding an unknown ref-kind should trigger a dangling ref.
         (testing "gap report detects simulated missing ref-kind"
                  (let [bad-caps (cap/capability-set
                                  {:id :bad-ref-kinds
                                   :dispatch #{:dispatch-refs :subscribe-refs
                                               :query-refs :all-refs :bogus-refs}})
                        report (gap/gap-report
                                {:formalisms
                                 (assoc spec/cap-formalisms
                                        :capability-set/cap-ref-kinds bad-caps)
                                 :registry spec/cap-registry})]

                       (testing "has failures"
                                (is (gap/has-failures? report)))

                       (testing "catches the dangling ref"
                                (let [morph-failures (:morphism-gaps (gap/failures report))]
                                     (is (some #(and (= :diverges (:status %))
                                                     (contains? (-> % :detail :dangling-refs)
                                                                :bogus-refs))
                                               morph-failures)))))))
