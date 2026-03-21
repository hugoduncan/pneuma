(ns pneuma.dogfood.protocol-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.protocol :as p]
              [pneuma.formalism.capability :as cap]
              [pneuma.dogfood.protocol :as dog]
              [pneuma.gap.core :as gap]))

;; Dogfood test: pneuma checks its own protocol layer.
;; This is the first time pneuma runs gap-report on itself.

(deftest protocol-formalisms-test
  ;; The dogfood formalism instances are well-formed.
         (testing "protocol formalisms"
                  (testing "effect signature has six operations"
                           (is (= 6 (count (p/extract-refs dog/protocol-operations
                                                           :operation-ids)))))

                  (testing "formalism caps require five operations"
                           (is (= 5 (count (p/extract-refs dog/formalism-record-caps
                                                           :dispatch-refs)))))

                  (testing "morphism caps require one operation"
                           (is (= 1 (count (p/extract-refs dog/morphism-record-caps
                                                           :dispatch-refs)))))

                  (testing "type schema covers all output types"
                           (let [output-types (p/extract-refs dog/protocol-operations
                                                              :operation-outputs)
                                 known-types (p/extract-refs dog/protocol-types
                                                             :type-ids)]
                                (is (every? known-types output-types)
                                    (str "unregistered types: "
                                         (pr-str (remove known-types output-types))))))))

(deftest protocol-gap-report-test
  ;; The gap report on pneuma.protocol should be clean —
  ;; all object and morphism gaps conform.
         (let [report (dog/protocol-gap-report)]

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
  ;; have a dangling ref. Simulate this by adding a bad operation
  ;; to the formalism caps.
         (testing "gap report detects simulated rename"
                  (let [bad-caps (cap/capability-set
                                  {:id :bad-formalism
                                   :dispatch #{:->schema :->monitor :->gen
                                               :->gap-type :extract-refs
                                               :renamed-method}})
                        report (gap/gap-report
                                {:formalisms
                                 (assoc dog/protocol-formalisms
                                        :capability-set/formalism bad-caps)
                                 :registry dog/protocol-registry})]

                       (testing "has failures"
                                (is (gap/has-failures? report)))

                       (testing "the existential morphism catches the dangling ref"
                                (let [morph-failures (:morphism-gaps (gap/failures report))]
                                     (is (some #(and (= :diverges (:status %))
                                                     (contains? (-> % :detail :dangling-refs)
                                                                :renamed-method))
                                               morph-failures)))))))
