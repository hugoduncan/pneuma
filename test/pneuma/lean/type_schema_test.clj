(ns pneuma.lean.type-schema-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.lean.protocol :as lp]
              [pneuma.lean.type-schema]))

(deftest type-schema-lean-emission-test
  ;; ->lean produces a TypeId inductive and completeness proof.
         (testing "TypeSchema ->lean"
                  (let [schema (ts/type-schema {:Foo :string :Bar :int})
                        lean-src (lp/->lean schema)]

                       (testing "contains inductive TypeId"
                                (is (str/includes? lean-src "inductive TypeId where")))

                       (testing "contains constructors"
                                (is (str/includes? lean-src "| Bar"))
                                (is (str/includes? lean-src "| Foo")))

                       (testing "contains completeness theorem"
                                (is (str/includes? lean-src "allTypeIds_complete")))

                       (testing "contains count theorem"
                                (is (str/includes? lean-src "= 2"))))))
