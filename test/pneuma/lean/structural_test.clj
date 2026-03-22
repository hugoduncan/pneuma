(ns pneuma.lean.structural-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.structural :as st]
              [pneuma.lean.protocol :as lp]
              [pneuma.lean.structural]))

(deftest structural-lean-emission-test
  ;; ->lean-conn emits boundary propositions for a structural morphism,
  ;; checking that source outputs validate against the target schema.
         (testing "StructuralMorphism ->lean-conn"
                  (let [sig (es/effect-signature
                             {:operations
                              {:alpha {:input {:x :String} :output :Bool}
                               :beta  {:input {:y :Nat} :output :String}}})
                        ts (ts/type-schema
                            {:Bool   :boolean
                             :String :string
                             :Nat    nat-int?})
                        morph (st/structural-morphism
                               {:id              :sig->types
                                :from            :effect-signature
                                :to              :type-schema
                                :source-ref-kind :operation-outputs
                                :target-ref-kind :type-ids})
                        lean-src (lp/->lean-conn morph sig ts)]

                       (testing "contains output inductive"
                                (is (str/includes? lean-src "inductive SigTypesOutput where"))
                                (is (str/includes? lean-src "| Bool"))
                                (is (str/includes? lean-src "| String")))

                       (testing "contains completeness theorem"
                                (is (str/includes? lean-src "allSigTypesOutput_complete")))

                       (testing "contains opaque validation predicate"
                                (is (str/includes? lean-src "SigTypesValid"))
                                (is (str/includes? lean-src "opaque")))

                       (testing "contains boundary proposition with sorry"
                                (is (str/includes? lean-src "SigTypes_structural_boundary"))
                                (is (str/includes? lean-src "sorry")))

                       (testing "contains header comment"
                                (is (str/includes? lean-src "StructuralMorphism"))
                                (is (str/includes? lean-src "effect-signature"))
                                (is (str/includes? lean-src "type-schema"))))))
