(ns pneuma.lean.effect-signature-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.lean.protocol :as lp]
              [pneuma.lean.effect-signature]))

;; Tests for Lean emission from EffectSignature formalisms.

(deftest effect-signature-lean-emission-test
  ;; ->lean produces structured Lean 4 source for an effect signature.
         (testing "EffectSignature ->lean"
                  (let [sig (es/effect-signature
                             {:operations
                              {:alpha {:input {:x :String :y :Nat}
                                       :output :Bool}
                               :beta {:input {:z :Keyword}
                                      :output :String}}})
                        lean-src (lp/->lean sig)]

                       (testing "returns a non-empty string"
                                (is (string? lean-src))
                                (is (pos? (count lean-src))))

                       (testing "contains the inductive type"
                                (is (str/includes? lean-src "inductive Op where")))

                       (testing "contains constructors for each operation"
                                (is (str/includes? lean-src "| alpha"))
                                (is (str/includes? lean-src "| beta")))

                       (testing "contains field structures"
                                (is (str/includes? lean-src "structure AlphaArgs"))
                                (is (str/includes? lean-src "structure BetaArgs")))

                       (testing "contains output type function"
                                (is (str/includes? lean-src "Op.outputType")))

                       (testing "contains completeness theorem"
                                (is (str/includes? lean-src "allOps_complete")))

                       (testing "contains count theorem"
                                (is (str/includes? lean-src "Op_count"))))))

(deftest protocol-operations-lean-emission-test
  ;; Real dogfood: the protocol-spec's six protocol operations.
         (testing "protocol-operations ->lean"
                  (let [sig (es/effect-signature
                             {:operations
                              {:->schema {:input {:formalism :Formalism}
                                          :output :MalliSchema}
                               :->monitor {:input {:formalism :Formalism}
                                           :output :MonitorFn}
                               :->gen {:input {:formalism :Formalism}
                                       :output :Generator}
                               :->gap-type {:input {:formalism :Formalism}
                                            :output :GapTypeDesc}
                               :check {:input {:morphism :Morphism
                                               :source :Formalism
                                               :target :Formalism
                                               :rm :RefinementMap}
                                       :output :GapSequence}
                               :extract-refs {:input {:formalism :Formalism
                                                      :ref-kind :Keyword}
                                              :output :KeywordSet}}})
                        lean-src (lp/->lean sig)]

                       (testing "has six operation constructors"
                                (is (= 6 (count (re-seq #"\| \w+" lean-src)))))

                       (testing "has six field structures"
                                (is (= 6 (count (re-seq #"structure \w+Args where" lean-src)))))

                       (testing "count theorem says 6"
                                (is (str/includes? lean-src "= 6"))))))
