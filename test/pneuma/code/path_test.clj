(ns pneuma.code.path-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.morphism.existential :as ex]
              [pneuma.code.path :as code-path]
              [pneuma.code.existential]))

(deftest path-tests-with-cycle-test
  ;; Verifies path-tests discovers cycles and generates closure,
  ;; adjacency, and per-step boundary test assertions.
         (testing "path-tests"
                  (let [caps (cap/capability-set
                              {:label "caps" :id :agent
                               :dispatch #{:op-a :op-b}})
                        sig (es/effect-signature
                             {:label "ES"
                              :operations {:op-a {:input {:x :String} :output :Bool}
                                           :op-b {:input {:y :Nat} :output :String}}})
          ;; Two morphisms forming a cycle: caps → sig → caps
                        m1 (ex/existential-morphism
                            {:id :caps->sig
                             :from :capability-set
                             :to :effect-signature
                             :source-ref-kind :dispatch-refs
                             :target-ref-kind :operation-ids})
                        m2 (ex/existential-morphism
                            {:id :sig->caps
                             :from :effect-signature
                             :to :capability-set
                             :source-ref-kind :operation-ids
                             :target-ref-kind :dispatch-refs})
                        registry {:caps->sig m1 :sig->caps m2}
                        formalisms {:capability-set caps
                                    :effect-signature sig}
                        results (code-path/path-tests registry formalisms {})]

                       (testing "discovers the cycle"
                                (is (= 1 (count results))))

                       (testing "generates closure assertion"
                                (let [r (first results)
                                      closure (get-in r [:assertions :closure])]
                                     (is (= :cycle-closure (:assertion closure)))
                                     (is (= (:first-from closure) (:last-to closure)))))

                       (testing "generates adjacency assertions"
                                (let [adj (get-in (first results) [:assertions :adjacency])]
                                     (is (pos? (count adj)))
                                     (is (every? #(= :adjacency (:assertion %)) adj))))

                       (testing "generates per-step boundary tests"
                                (let [boundary (get-in (first results) [:assertions :boundary-tests])]
                                     (is (= 2 (count boundary)))
                                     (is (every? #(= :existential-test (:type %)) boundary)))))))

(deftest path-tests-no-cycles-test
  ;; Verifies path-tests returns empty when there are no cycles.
         (testing "path-tests with no cycles"
                  (let [caps (cap/capability-set
                              {:label "caps" :id :agent :dispatch #{:op-a}})
                        sig (es/effect-signature
                             {:label "ES"
                              :operations {:op-a {:input {:x :String} :output :Bool}}})
                        m1 (ex/existential-morphism
                            {:id :caps->sig
                             :from :capability-set
                             :to :effect-signature
                             :source-ref-kind :dispatch-refs
                             :target-ref-kind :operation-ids})
                        registry {:caps->sig m1}
                        formalisms {:capability-set caps :effect-signature sig}
                        results (code-path/path-tests registry formalisms {})]

                       (testing "returns empty"
                                (is (empty? results))))))
