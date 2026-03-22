(ns pneuma.morphism.registry-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.morphism.registry :as reg]))

;; Tests for the connection registry — the formalism graph as data.

(deftest default-registry-test
  ;; The default registry contains the dogfood morphisms.
         (testing "default-registry"
                  (testing "contains caps->protocol/operations"
                           (is (contains? reg/default-registry :caps->protocol/operations)))

                  (testing "contains effect-sig->type-schema/outputs"
                           (is (contains? reg/default-registry :effect-sig->type-schema/outputs)))

                  (testing "has two entries"
                           (is (= 2 (count reg/default-registry))))))

(deftest morphisms-involving-test
  ;; Filter registry by formalism kind.
         (testing "morphisms-involving"
                  (testing "finds morphisms involving :capability-set"
                           (is (= 1 (count (reg/morphisms-involving
                                            reg/default-registry :capability-set)))))

                  (testing "finds morphisms involving :effect-signature"
                           (is (= 2 (count (reg/morphisms-involving
                                            reg/default-registry :effect-signature)))))

                  (testing "finds morphisms involving :type-schema"
                           (is (= 1 (count (reg/morphisms-involving
                                            reg/default-registry :type-schema)))))

                  (testing "returns empty for uninvolved formalism"
                           (is (empty? (reg/morphisms-involving
                                        reg/default-registry :statechart))))))
