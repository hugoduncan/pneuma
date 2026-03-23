(ns pneuma.code.ordering-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.capability :as cap]
              [pneuma.morphism.ordering :as ord]
              [pneuma.code.protocol :as cp]
              [pneuma.code.ordering]))

(deftest ordering-code-emission-test
  ;; ->code-conn emits ordering invariant test assertions for an
  ;; ordering morphism within an interceptor chain.
         (testing "OrderingMorphism ->code-conn"
                  (let [cap-a (cap/capability-set
                               {:label "auth caps"
                                :id :auth
                                :dispatch #{:login}})
                        cap-b (cap/capability-set
                               {:label "handler caps"
                                :id :handler
                                :dispatch #{:process}})
                        morph (ord/ordering-morphism
                               {:id :auth-before-handler
                                :from :auth
                                :to :handler
                                :source-ref-kind :dispatch-refs
                                :target-ref-kind :dispatch-refs
                                :chain [:login :validate :process]})
                        code (cp/->code-conn morph cap-a cap-b {})]

                       (testing "has correct morphism metadata"
                                (is (= :auth-before-handler (:morphism-id code)))
                                (is (= :ordering-test (:type code))))

                       (testing "emits a precedes assertion"
                                (is (= 1 (count (:assertions code))))
                                (is (= :precedes (:assertion (first (:assertions code))))))

                       (testing "assertion references the chain"
                                (let [a (first (:assertions code))]
                                     (is (= [:login :validate :process] (:chain a)))
                                     (is (= :login (:source-ref a)))
                                     (is (= :process (:target-ref a))))))))
