(ns pneuma.code.existential-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.morphism.existential :as ex]
              [pneuma.code.protocol :as cp]
              [pneuma.code.existential]))

(deftest existential-code-emission-test
  ;; ->code-conn emits referential integrity test assertions for an
  ;; existential morphism between capability dispatch refs and effect
  ;; signature operation ids.
         (testing "ExistentialMorphism ->code-conn"
                  (let [caps (cap/capability-set
                              {:label "test caps"
                               :id :agent
                               :dispatch #{:send :load}})
                        sig (es/effect-signature
                             {:label "test ES"
                              :operations {:send {:input {:text :String} :output :Bool}
                                           :load {:input {:id :Nat} :output :String}
                                           :extra {:input {:x :Any} :output :Any}}})
                        morph (ex/existential-morphism
                               {:id :caps->ops
                                :from :capability-set
                                :to :effect-signature
                                :source-ref-kind :dispatch-refs
                                :target-ref-kind :operation-ids})
                        code (cp/->code-conn morph caps sig {})]

                       (testing "has correct morphism metadata"
                                (is (= :caps->ops (:morphism-id code)))
                                (is (= :existential-test (:type code))))

                       (testing "emits one assertion per source ref"
                                (is (= 2 (count (:assertions code)))))

                       (testing "assertions check containment"
                                (is (every? #(= :contains (:assertion %))
                                            (:assertions code))))

                       (testing "includes ref counts in metadata"
                                (is (= 2 (get-in code [:metadata :source-count])))
                                (is (= 3 (get-in code [:metadata :target-count])))))))
