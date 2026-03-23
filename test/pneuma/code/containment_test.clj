(ns pneuma.code.containment-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.morphism.containment :as ct]
              [pneuma.code.protocol :as cp]
              [pneuma.code.containment]))

(deftest containment-code-emission-test
  ;; ->code-conn emits bounds-checking test assertions for a
  ;; containment morphism.
         (testing "ContainmentMorphism ->code-conn"
                  (let [caps (cap/capability-set
                              {:label "test caps"
                               :id :agent
                               :dispatch #{:op-a :op-b}})
                        sig (es/effect-signature
                             {:label "test ES"
                              :operations {:op-a {:input {:x :String} :output :Bool}
                                           :op-b {:input {:y :Nat} :output :String}
                                           :op-c {:input {:z :Any} :output :Any}}})
                        morph (ct/containment-morphism
                               {:id :caps-in-ops
                                :from :capability-set
                                :to :effect-signature
                                :source-ref-kind :dispatch-refs
                                :target-ref-kind :operation-ids})
                        code (cp/->code-conn morph caps sig {})]

                       (testing "has correct morphism metadata"
                                (is (= :caps-in-ops (:morphism-id code)))
                                (is (= :containment-test (:type code))))

                       (testing "emits one assertion per source ref"
                                (is (= 2 (count (:assertions code)))))

                       (testing "assertions check subset membership"
                                (is (every? #(= :subset (:assertion %))
                                            (:assertions code)))))))
