(ns pneuma.lean.morphism-algebra-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.morphism.existential :as ex]
              [pneuma.lean.morphism-algebra :as ma]))

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

(deftest morphism-algebra-emission-test
  ;; emit-morphism-algebra emits Lean 4 proofs for the morphism graph
  ;; algebra: nodes, edges, source/target functions, composable predicate,
  ;; and associativity.
         (testing "emit-morphism-algebra"
                  (let [src (ma/emit-morphism-algebra test-registry test-formalisms)]

                       (testing "emits Node inductive"
                                (is (str/includes? src "inductive Node where"))
                                (is (str/includes? src "capability_set"))
                                (is (str/includes? src "effect_signature")))

                       (testing "emits Edge inductive"
                                (is (str/includes? src "inductive Edge where"))
                                (is (str/includes? src "caps__ops")))

                       (testing "emits source/target functions"
                                (is (str/includes? src "edgeSource"))
                                (is (str/includes? src "edgeTarget")))

                       (testing "emits composable predicate"
                                (is (str/includes? src "composable")))

                       (testing "emits allNodes completeness"
                                (is (str/includes? src "allNodes_complete")))

                       (testing "emits allEdges completeness"
                                (is (str/includes? src "allEdges_complete")))

                       (testing "emits associativity theorem"
                                (is (str/includes? src "composable_assoc"))
                                (is (str/includes? src "decide")))

                       (testing "emits identity theorem"
                                (is (str/includes? src "edge_endpoints_defined")))

                       (testing "emits composition uniqueness theorem"
                                (is (str/includes? src "composable_shared_node"))))))
