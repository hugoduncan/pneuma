(ns pneuma.gap.core-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.morphism.existential :as ex]
              [pneuma.gap.core :as gap]))

;; Tests for gap report assembly using the dogfood formalisms
;; and a minimal existential-only registry.

(def protocol-ops
     (es/effect-signature
      {:label "test ES"
       :operations
       {:->schema {:input {:formalism :Keyword} :output :Any}
        :->monitor {:input {:formalism :Keyword} :output :Any}
        :->gen {:input {:formalism :Keyword} :output :Any}
        :->gap-type {:input {:formalism :Keyword} :output :Any}
        :check {:input {:morphism :Keyword} :output :Any}
        :extract-refs {:input {:formalism :Keyword} :output :Any}}}))

(def formalism-caps
     (cap/capability-set
      {:label "test caps"
       :id :formalism-record
       :dispatch #{:->schema :->monitor :->gen :->gap-type
                   :extract-refs}}))

(def bad-caps
     (cap/capability-set
      {:label "test caps"
       :id :bad-record
       :dispatch #{:->schema :bogus-method}}))

(def existential-registry
     "Registry with only the existential morphism for focused testing."
     {:caps->protocol/operations
      (ex/existential-morphism
       {:id :caps->protocol/operations
        :from :capability-set
        :to :effect-signature
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})})

(deftest gap-report-conforming-test
  ;; A conforming system produces a report with no failures.
         (testing "gap-report"
                  (testing "with conforming formalisms"
                           (let [report (gap/gap-report
                                         {:formalisms {:effect-signature protocol-ops
                                                       :capability-set formalism-caps}
                                          :registry existential-registry})]

                                (testing "has three layers"
                                         (is (contains? report :object-gaps))
                                         (is (contains? report :morphism-gaps))
                                         (is (contains? report :path-gaps)))

                                (testing "object gaps are all conforming"
                                         (is (every? #(= :conforms (:status %))
                                                     (:object-gaps report))))

                                (testing "existential morphism conforms"
                                         (is (every? #(= :conforms (:status %))
                                                     (:morphism-gaps report))))

                                (testing "has no failures"
                                         (is (not (gap/has-failures? report))))))))

(deftest gap-report-with-dangling-ref-test
  ;; A capability set referencing a non-existent operation produces
  ;; a morphism-level divergence.
         (testing "gap-report"
                  (testing "with dangling ref in capability set"
                           (let [report (gap/gap-report
                                         {:formalisms {:effect-signature protocol-ops
                                                       :capability-set bad-caps}
                                          :registry existential-registry})]

                                (testing "has morphism failures"
                                         (is (gap/has-failures? report)))

                                (testing "the existential morphism diverges"
                                         (let [morph-gaps (:morphism-gaps (gap/failures report))]
                                              (is (some #(= :diverges (:status %)) morph-gaps))
                                              (is (some #(= :caps->protocol/operations (:id %))
                                                        morph-gaps))))))))

(deftest gap-report-missing-formalism-test
  ;; A registry entry referencing a missing formalism produces
  ;; an absent gap.
         (testing "gap-report"
                  (testing "with missing formalism"
                           (let [report (gap/gap-report
                                         {:formalisms {:effect-signature protocol-ops}
                                          :registry existential-registry})]

                                (testing "has morphism failures"
                                         (is (gap/has-failures? report)))

                                (testing "the missing formalism is reported as absent"
                                         (let [morph-gaps (:morphism-gaps (gap/failures report))]
                                              (is (some #(= :absent (:status %)) morph-gaps))))))))

(deftest failures-filter-test
  ;; failures strips conforming gaps from the report.
         (testing "failures"
                  (testing "returns empty layers for conforming report"
                           (let [report (gap/gap-report
                                         {:formalisms {:effect-signature protocol-ops
                                                       :capability-set formalism-caps}
                                          :registry existential-registry})
                                 f (gap/failures report)]
                                (is (empty? (:object-gaps f)))
                                (is (empty? (:morphism-gaps f)))
                                (is (empty? (:path-gaps f)))))))
