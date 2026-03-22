(ns pneuma.lean.doc-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.lean.doc :as doc]))

;; Tests for Lean 4 docstring generation helpers.

(deftest lean-doc-test
  ;; lean-doc produces well-formed /-- ... -/ docstring blocks.
         (testing "lean-doc"
                  (testing "wraps a single line in docstring delimiters"
                           (let [result (doc/lean-doc "Hello world.")]
                                (is (str/starts-with? result "/-- "))
                                (is (str/ends-with? result " -/\n"))
                                (is (str/includes? result "Hello world."))))

                  (testing "joins multiple lines with indentation"
                           (let [result (doc/lean-doc "Line one." "Line two.")]
                                (is (str/includes? result "Line one."))
                                (is (str/includes? result "Line two."))
                                (is (str/includes? result "\n    "))))))

(deftest type-doc-test
  ;; type-doc produces docstrings for type definitions.
         (testing "type-doc"
                  (testing "includes the description and source reference"
                           (let [result (doc/type-doc "Statechart" :my-chart "States of the chart.")]
                                (is (str/includes? result "States of the chart."))
                                (is (str/includes? result "Derived from Pneuma Statechart :my-chart."))))

                  (testing "handles nil id gracefully"
                           (let [result (doc/type-doc "EffectSignature" nil "Op alphabet.")]
                                (is (str/includes? result "Op alphabet."))
                                (is (str/includes? result "Derived from Pneuma EffectSignature."))
                                (is (not (str/includes? result ":nil")))))))

(deftest theorem-doc-test
  ;; theorem-doc produces docstrings for theorems.
         (testing "theorem-doc"
                  (testing "wraps the property in docstring delimiters"
                           (let [result (doc/theorem-doc "All states are reachable.")]
                                (is (str/starts-with? result "/-- "))
                                (is (str/includes? result "All states are reachable."))))))

(deftest morphism-theorem-doc-test
  ;; morphism-theorem-doc includes morphism identity.
         (testing "morphism-theorem-doc"
                  (testing "includes the property and morphism source"
                           (let [result (doc/morphism-theorem-doc "ContainmentMorphism" :caps->ops
                                                                  "Every source ref is contained.")]
                                (is (str/includes? result "Every source ref is contained."))
                                (is (str/includes? result "Boundary of ContainmentMorphism :caps->ops."))))))

(deftest id-str-test
  ;; id-str converts keywords to ":name" strings.
         (testing "id-str"
                  (testing "returns :name for a keyword"
                           (is (= ":my-id" (doc/id-str :my-id))))

                  (testing "returns empty string for nil"
                           (is (= "" (doc/id-str nil))))))
