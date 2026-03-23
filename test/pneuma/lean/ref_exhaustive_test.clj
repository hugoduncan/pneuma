(ns pneuma.lean.ref-exhaustive-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.morphism.existential :as ex]
              [pneuma.lean.ref-exhaustive :as re]))

(def test-ops
     (es/effect-signature
      {:label "test ES"
       :operations
       {:read {:input {:key :Keyword} :output :String}}}))

(def test-caps
     (cap/capability-set
      {:label "test caps"
       :id :test-caps
       :dispatch #{:read}}))

(def test-formalisms
     {:capability-set test-caps
      :effect-signature test-ops})

(def test-registry
     {:caps->ops (ex/existential-morphism
                  {:id :caps->ops
                   :from :capability-set
                   :to :effect-signature
                   :source-ref-kind :dispatch-refs
                   :target-ref-kind :operation-ids})})

(deftest ref-exhaustiveness-test
  ;; emit-ref-exhaustiveness emits Lean 4 proofs that extract-refs
  ;; covers all ref-kinds used in the registry per formalism.
         (testing "emit-ref-exhaustiveness"
                  (let [src (re/emit-ref-exhaustiveness test-formalisms test-registry)]

                       (testing "emits RefKind inductives"
                                (is (str/includes? src "RefKind where")))

                       (testing "emits completeness theorems"
                                (is (str/includes? src "ref_exhaustive")))

                       (testing "includes dispatch-refs for capability-set"
                                (is (str/includes? src "dispatch_refs")))

                       (testing "includes operation-ids for effect-signature"
                                (is (str/includes? src "operation_ids"))))))
