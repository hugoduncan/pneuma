(ns pneuma.code.effect-signature-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.code.protocol :as cp]
              [pneuma.code.effect-signature]))

(deftest effect-signature-code-emission-test
  ;; ->code emits executor dispatch with one defmethod per operation,
  ;; fill points for execution logic, and metadata.
         (testing "EffectSignature ->code"
                  (let [sig (es/effect-signature
                             {:label "agent effects"
                              :operations
                              {:ai-generate {:input {:session-id :Keyword
                                                     :messages :Any}
                                             :output :String}
                               :tool-execute {:input {:tool-id :Keyword}
                                              :output :Any}}})
                        code (cp/->code sig {:target-ns 'agent.effects})]

                       (testing "contains the target namespace"
                                (is (= 'agent.effects (:namespace code))))

                       (testing "contains a defmulti form"
                                (let [multi (first (:forms code))]
                                     (is (= :defmulti (:type multi)))
                                     (is (= 'execute-effect (:name multi)))))

                       (testing "contains one defmethod per operation"
                                (let [methods (filterv #(= :defmethod (:type %)) (:forms code))]
                                     (is (= 2 (count methods)))
                                     (is (= #{:ai-generate :tool-execute}
                                            (set (map :dispatch-val methods))))))

                       (testing "produces fill manifest with execute entries"
                                (let [manifest (:fill-manifest code)]
                                     (is (contains? manifest :ai-generate/execute))
                                     (is (contains? manifest :tool-execute/execute))
                                     (is (= :String (:returns (get manifest :ai-generate/execute))))))

                       (testing "includes formalism metadata"
                                (is (= :effect-signature (get-in code [:metadata :formalism])))
                                (is (= 2 (get-in code [:metadata :operation-count])))))))
