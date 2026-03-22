(ns pneuma.lean.existential-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.morphism.existential :as ex]
              [pneuma.lean.protocol :as lp]
              [pneuma.lean.existential]))

(deftest existential-lean-emission-test
  ;; ->lean-conn emits boundary propositions for an existential morphism,
  ;; checking that source refs embed into target refs.
         (testing "ExistentialMorphism ->lean-conn"
                  (let [caps (cap/capability-set
                              {:label "test caps"
                               :id :agent
                               :dispatch #{:send-prompt :load-ctx}})
                        sig (es/effect-signature
                             {:label "test ES"
                              :operations
                              {:send-prompt {:input {:text :String} :output :Bool}
                               :load-ctx    {:input {:id :Nat} :output :String}
                               :get-status  {:input {:id :Nat} :output :String}}})
                        morph (ex/existential-morphism
                               {:id              :caps->ops
                                :from            :capability-set
                                :to              :effect-signature
                                :source-ref-kind :dispatch-refs
                                :target-ref-kind :operation-ids})
                        lean-src (lp/->lean-conn morph caps sig)]

                       (testing "contains source inductive"
                                (is (str/includes? lean-src "inductive CapsOpsSource where"))
                                (is (str/includes? lean-src "| load_ctx"))
                                (is (str/includes? lean-src "| send_prompt")))

                       (testing "contains target inductive"
                                (is (str/includes? lean-src "inductive CapsOpsTarget where"))
                                (is (str/includes? lean-src "| get_status"))
                                (is (str/includes? lean-src "| load_ctx"))
                                (is (str/includes? lean-src "| send_prompt")))

                       (testing "contains embedding function"
                                (is (str/includes? lean-src "CapsOpsEmbed"))
                                (is (str/includes? lean-src ".some .load_ctx"))
                                (is (str/includes? lean-src ".some .send_prompt")))

                       (testing "contains completeness theorems"
                                (is (str/includes? lean-src "allCapsOpsSource_complete"))
                                (is (str/includes? lean-src "allCapsOpsTarget_complete")))

                       (testing "contains boundary proposition"
                                (is (str/includes? lean-src "CapsOps_existential_boundary"))
                                (is (str/includes? lean-src "isSome = true")))

                       (testing "contains header comment"
                                (is (str/includes? lean-src "ExistentialMorphism"))
                                (is (str/includes? lean-src "capability-set"))
                                (is (str/includes? lean-src "effect-signature"))))))
