(ns pneuma.code.structural-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.morphism.structural :as st]
              [pneuma.code.protocol :as cp]
              [pneuma.code.structural]))

(deftest structural-code-emission-test
  ;; ->code-conn emits schema conformance test assertions for a
  ;; structural morphism.
         (testing "StructuralMorphism ->code-conn"
                  (let [source-sig (es/effect-signature
                                    {:label "source ES"
                                     :operations {:op-a {:input {:x :String} :output :Bool}
                                                  :op-b {:input {:y :Nat} :output :String}}})
                        target-sig (es/effect-signature
                                    {:label "target ES"
                                     :operations {:op-a {:input {:x :String} :output :Bool}}})
                        morph (st/structural-morphism
                               {:id :sig->sig
                                :from :source-es
                                :to :target-es
                                :source-ref-kind :operation-outputs
                                :target-ref-kind :operation-ids})
                        code (cp/->code-conn morph source-sig target-sig {})]

                       (testing "has correct morphism metadata"
                                (is (= :sig->sig (:morphism-id code)))
                                (is (= :structural-test (:type code))))

                       (testing "assertions check schema validation"
                                (is (every? #(= :validates (:assertion %))
                                            (:assertions code))))

                       (testing "includes source count"
                                (is (pos? (get-in code [:metadata :source-count])))))))
