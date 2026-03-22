(ns pneuma.formalism.type-schema-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.test.check.generators :as gen]
              [malli.core :as m]
              [pneuma.protocol :as p]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.morphism.structural :as st]))

;; Tests for the TypeSchema formalism and its use as a structural
;; morphism target for EffectSignature output type checking.

(def test-types
     (ts/type-schema
      {:label "test types"
       :types {:String :string
               :Keyword :keyword
               :Any :any
               :EventRef :keyword
               :KeywordSet [:set :keyword]}}))

(deftest constructor-test
  ;; type-schema validates that values are valid Malli schemas.
         (testing "type-schema"
                  (testing "accepts valid type map"
                           (is (some? (ts/type-schema {:label "test types" :types {:Foo :string :Bar :int}}))))

                  (testing "rejects invalid Malli schema value"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (ts/type-schema {:label "test types" :types {:Bad "not-a-schema"}}))))))

(deftest schema-projection-test
  ;; ->schema produces an enum of registered type keywords.
         (let [schema (p/->schema test-types)]
              (testing "->schema"
                       (testing "produces a valid Malli schema"
                                (is (m/schema? (m/schema schema))))

                       (testing "validates a known type keyword"
                                (is (m/validate schema :String)))

                       (testing "rejects an unknown type keyword"
                                (is (not (m/validate schema :Unknown)))))))

(deftest generator-projection-test
  ;; ->gen produces type keywords from the registry.
         (let [g (p/->gen test-types)
               schema (p/->schema test-types)
               samples (gen/sample g 20)]
              (testing "->gen"
                       (testing "generated values are known types (A24)"
                                (doseq [s samples]
                                       (is (m/validate schema s)
                                           (str "unknown type: " s)))))))

(deftest referenceable-test
  ;; extract-refs returns the set of registered type keywords.
         (testing "extract-refs"
                  (testing "returns type ids"
                           (is (= #{:String :Keyword :Any :EventRef :KeywordSet}
                                  (p/extract-refs test-types :type-ids))))

                  (testing "returns empty for unknown ref-kind"
                           (is (= #{} (p/extract-refs test-types :unknown))))))

(deftest structural-morphism-integration-test
  ;; The structural morphism from EffectSignature outputs to
  ;; TypeSchema type-ids now works correctly.
         (let [morphism (st/structural-morphism
                         {:id :sig->types
                          :from :effect-signature
                          :to :type-schema
                          :source-ref-kind :operation-outputs
                          :target-ref-kind :type-ids})]

              (testing "structural morphism to TypeSchema"
                       (testing "conforms when all output types are registered"
                                (let [sig (es/effect-signature
                                           {:label "test ES"
                                            :operations
                                            {:op-a {:input {:x :Keyword} :output :String}
                                             :op-b {:input {:y :Keyword} :output :EventRef}}})
                                      gaps (p/check morphism sig test-types {})]
                                     (is (= :conforms (:status (first gaps))))))

                       (testing "diverges when an output type is not registered"
                                (let [sig (es/effect-signature
                                           {:label "test ES"
                                            :operations
                                            {:op-a {:input {:x :Keyword} :output :String}
                                             :op-b {:input {:y :Keyword} :output :UnknownType}}})
                                      gaps (p/check morphism sig test-types {})]
                                     (is (= :diverges (:status (first gaps))))
                                     (is (seq (-> gaps first :detail :shape-mismatches))))))))
