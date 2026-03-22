(ns pneuma.formalism.effect-signature-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.test.check :as tc]
              [clojure.test.check.generators :as gen]
              [clojure.test.check.properties :as prop]
              [malli.core :as m]
              [pneuma.protocol :as p]
              [pneuma.formalism.effect-signature :as es]))

;; Tests for the EffectSignature formalism using the dogfood instance
;; (pneuma's own protocol methods as operations) and a minimal
;; two-operation signature.

(def dogfood-ops
     "The dogfood instance: pneuma protocol methods as operations."
     {:label "test ES"
      :operations
      {:->schema
       {:input {:formalism :Keyword}
        :output :Any}

       :->monitor
       {:input {:formalism :Keyword}
        :output :Any}

       :->gen
       {:input {:formalism :Keyword}
        :output :Any}

       :->gap-type
       {:input {:formalism :Keyword}
        :output :Any}

       :check
       {:input {:morphism :Keyword
                :source :Keyword
                :target :Keyword
                :rm :Keyword}
        :output :Any}

       :extract-refs
       {:input {:formalism :Keyword
                :ref-kind :Keyword}
        :output :KeywordSet}}})

(def minimal-ops
     "Minimal two-operation signature for focused tests."
     {:label "test ES"
      :operations
      {:ai/generate
       {:input {:session-id :String
                :model :Keyword}
        :output :EventRef}

       :tool/execute
       {:input {:session-id :String
                :tool :Keyword}
        :output :EventRef}}})

(deftest constructor-test
  ;; The effect-signature constructor validates input and rejects
  ;; malformed operation maps.
         (testing "effect-signature"
                  (testing "accepts valid dogfood operations"
                           (is (some? (es/effect-signature dogfood-ops))))

                  (testing "accepts valid minimal operations"
                           (is (some? (es/effect-signature minimal-ops))))

                  (testing "rejects missing :operations key"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (es/effect-signature {:label "test ES"}))))

                  (testing "rejects non-map operations"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (es/effect-signature {:label "test ES" :operations "bad"}))))

                  (testing "rejects operation missing :input"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (es/effect-signature
                                         {:label "test ES" :operations {:foo {:output :Any}}}))))

                  (testing "rejects operation missing :output"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (es/effect-signature
                                         {:label "test ES" :operations {:foo {:input {:x :String}}}}))))))

(deftest schema-projection-test
  ;; ->schema produces a Malli :multi schema that validates effect
  ;; description maps dispatched on :op.
         (let [sig (es/effect-signature minimal-ops)
               schema (p/->schema sig)]

              (testing "->schema"
                       (testing "produces a valid Malli schema"
                                (is (m/schema? (m/schema schema))))

                       (testing "validates conforming effect for :ai/generate"
                                (is (m/validate schema
                                                {:op :ai/generate
                                                 :session-id "s1"
                                                 :model :gpt})))

                       (testing "validates conforming effect for :tool/execute"
                                (is (m/validate schema
                                                {:op :tool/execute
                                                 :session-id "s1"
                                                 :tool :grep})))

                       (testing "rejects effect with unknown :op"
                                (is (not (m/validate schema
                                                     {:op :unknown}))))

                       (testing "rejects effect missing required field"
                                (is (not (m/validate schema
                                                     {:op :ai/generate
                                                      :session-id "s1"}))))

                       (testing "rejects effect with wrong field type"
                                (is (not (m/validate schema
                                                     {:op :ai/generate
                                                      :session-id 123
                                                      :model :gpt})))))))

(deftest monitor-projection-test
  ;; ->monitor produces a function that checks event log entries'
  ;; :effects against the declared operation signatures.
         (let [sig (es/effect-signature minimal-ops)
               monitor (p/->monitor sig)]

              (testing "->monitor"
                       (testing "returns :ok for conforming effects"
                                (is (= :ok
                                       (:verdict
                                        (monitor {:effects [{:op :ai/generate
                                                             :session-id "s1"
                                                             :model :gpt}]})))))

                       (testing "returns :ok for empty effects list"
                                (is (= :ok (:verdict (monitor {:effects []})))))

                       (testing "returns :violation for missing :op key"
                                (let [result (monitor {:effects [{:session-id "s1"}]})]
                                     (is (= :violation (:verdict result)))
                                     (is (= :missing-op-key
                                            (-> result :violations first :detail :kind)))))

                       (testing "returns :violation for unknown operation"
                                (let [result (monitor {:effects [{:op :bogus}]})]
                                     (is (= :violation (:verdict result)))
                                     (is (= :missing-operation
                                            (-> result :violations first :detail :kind)))))

                       (testing "returns :violation for malformed fields"
                                (let [result (monitor {:effects [{:op :ai/generate
                                                                  :session-id 999
                                                                  :model :gpt}]})]
                                     (is (= :violation (:verdict result)))
                                     (is (= :malformed-fields
                                            (-> result :violations first :detail :kind)))))

                       (testing "reports multiple violations"
                                (let [result (monitor {:effects [{:op :bogus}
                                                                 {:session-id "s1"}]})]
                                     (is (= :violation (:verdict result)))
                                     (is (= 2 (count (:violations result)))))))))

(deftest generator-projection-test
  ;; ->gen produces a test.check generator whose output conforms
  ;; to the schema (axiom A24).
         (let [sig (es/effect-signature minimal-ops)
               g (p/->gen sig)
               schema (p/->schema sig)
               samples (gen/sample g 20)]

              (testing "->gen"
                       (testing "produces a generator"
                                (is (some? g)))

                       (testing "generated values conform to schema (A24)"
                                (doseq [sample samples]
                                       (is (m/validate schema sample)
                                           (str "sample failed: " (pr-str sample))))))))

(deftest a24-property-test
  ;; Axiom A24: for all generated values, the value conforms to the
  ;; schema projected by the same formalism. Tested as a proper
  ;; generative property with shrinking.
         (testing "A24: ->gen output conforms to ->schema"
                  (doseq [[label ops] [["minimal" minimal-ops]
                                       ["dogfood" dogfood-ops]]]
                         (testing (str "for " label " signature")
                                  (let [sig    (es/effect-signature ops)
                                        g      (p/->gen sig)
                                        schema (p/->schema sig)
                                        result (tc/quick-check
                                                100
                                                (prop/for-all [v g]
                                                              (m/validate schema v)))]
                                       (is (:pass? result)
                                           (str label " A24 failure: "
                                                (pr-str (:shrunk result)))))))))

(deftest gap-type-projection-test
  ;; ->gap-type returns the failure taxonomy for effect signatures.
         (let [sig (es/effect-signature minimal-ops)
               gt (p/->gap-type sig)]

              (testing "->gap-type"
                       (testing "has :formalism key"
                                (is (= :effect-signature (:formalism gt))))

                       (testing "has gap-kinds set"
                                (is (contains? (:gap-kinds gt) :missing-operation))
                                (is (contains? (:gap-kinds gt) :malformed-fields)))

                       (testing "has statuses set"
                                (is (= #{:conforms :absent :diverges} (:statuses gt)))))))

(deftest referenceable-test
  ;; extract-refs returns cross-formalism reference sets keyed by
  ;; ref-kind.
         (let [sig (es/effect-signature minimal-ops)]

              (testing "extract-refs"
                       (testing "returns operation ids for :operation-ids"
                                (is (= #{:ai/generate :tool/execute}
                                       (p/extract-refs sig :operation-ids))))

                       (testing "returns output types for :callback-refs"
                                (is (= #{:EventRef}
                                       (p/extract-refs sig :callback-refs))))

                       (testing "returns output types for :operation-outputs"
                                (is (= #{:EventRef}
                                       (p/extract-refs sig :operation-outputs))))

                       (testing "returns empty set for unknown ref-kind"
                                (is (= #{} (p/extract-refs sig :unknown)))))))

(deftest dogfood-instance-test
  ;; The dogfood instance (pneuma protocol methods) constructs and
  ;; projects successfully.
         (let [sig (es/effect-signature dogfood-ops)
               schema (p/->schema sig)]

              (testing "dogfood instance"
                       (testing "constructs successfully"
                                (is (some? sig)))

                       (testing "has six operations"
                                (is (= 6 (count (p/extract-refs sig :operation-ids)))))

                       (testing "schema validates a conforming ->schema call"
                                (is (m/validate schema
                                                {:op :->schema
                                                 :formalism :statechart})))

                       (testing "schema validates a conforming :check call"
                                (is (m/validate schema
                                                {:op :check
                                                 :morphism :m1
                                                 :source :chart
                                                 :target :mealy
                                                 :rm :rm1}))))))
