(ns pneuma.lean.gap-completeness-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.morphism.existential :as ex]
              [pneuma.gap.core :as gap]
              [pneuma.lean.gap-completeness :as gc]))

(def test-ops
     (es/effect-signature
      {:label "test ES"
       :operations
       {:read {:input {:key :Keyword} :output :String}
        :write {:input {:val :String} :output :Boolean}}}))

(def test-caps
     (cap/capability-set
      {:label "test caps"
       :id :test-caps
       :dispatch #{:read :write}}))

(def test-registry
     {:caps->ops (ex/existential-morphism
                  {:id :caps->ops
                   :from :capability-set
                   :to :effect-signature
                   :source-ref-kind :dispatch-refs
                   :target-ref-kind :operation-ids})})

(def test-formalisms
     {:capability-set test-caps
      :effect-signature test-ops})

(deftest gap-completeness-test
  ;; emit-gap-completeness emits Lean 4 proofs that every morphism,
  ;; formalism, and path was checked in the gap report.
         (testing "emit-gap-completeness"
                  (let [report (gap/gap-report {:formalisms test-formalisms
                                                :registry test-registry})
                        src (gc/emit-gap-completeness test-registry
                                                      test-formalisms
                                                      report)]

                       (testing "emits MorphismId inductive"
                                (is (str/includes? src "inductive MorphismId where"))
                                (is (str/includes? src "caps__ops")))

                       (testing "emits checkedMorphisms list"
                                (is (str/includes? src "checkedMorphisms")))

                       (testing "emits morphism completeness theorem"
                                (is (str/includes? src "morphism_gap_complete")))

                       (testing "emits FormalismKind inductive"
                                (is (str/includes? src "inductive FormalismKind where")))

                       (testing "emits formalism completeness theorem"
                                (is (str/includes? src "object_gap_complete")))

                       (testing "emits system-level completeness"
                                (is (str/includes? src "gap_report_complete"))))))
