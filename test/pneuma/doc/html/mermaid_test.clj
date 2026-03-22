(ns pneuma.doc.html.mermaid-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.doc.html.mermaid :as mermaid]))

;; Tests for mermaid text rendering across all dialects.
;; Contracts: each dialect produces correct mermaid syntax.

(deftest render-mermaid-state-test
         (testing "render-mermaid-state"
                  (testing "produces stateDiagram-v2 header"
                           (let [result (mermaid/render-mermaid-state {:states [:idle]})]
                                (is (str/starts-with? result "stateDiagram-v2") result)))
                  (testing "includes state names"
                           (let [result (mermaid/render-mermaid-state {:states [:idle :run]})]
                                (is (str/includes? result "idle") result)
                                (is (str/includes? result "run") result)))
                  (testing "includes labeled transitions"
                           (let [result (mermaid/render-mermaid-state
                                         {:transitions [[:idle :run "go"]]})]
                                (is (str/includes? result "idle --> run : go") result)))))

(deftest render-mermaid-graph-test
         (testing "render-mermaid-graph"
                  (testing "produces graph LR header"
                           (let [result (mermaid/render-mermaid-graph {:edges []})]
                                (is (str/starts-with? result "graph LR") result)))
                  (testing "includes labeled edges"
                           (let [result (mermaid/render-mermaid-graph
                                         {:edges [[:a :b "link"]]})]
                                (is (str/includes? result "a -->|link| b") result)))))

(deftest render-mermaid-sequence-test
         (testing "render-mermaid-sequence"
                  (testing "produces sequenceDiagram header"
                           (let [result (mermaid/render-mermaid-sequence {:participants [:a]})]
                                (is (str/starts-with? result "sequenceDiagram") result)))
                  (testing "includes participants"
                           (let [result (mermaid/render-mermaid-sequence
                                         {:participants [:alice :bob]})]
                                (is (str/includes? result "participant alice") result)))
                  (testing "includes interactions"
                           (let [result (mermaid/render-mermaid-sequence
                                         {:interactions [[:alice :bob "hello"]]})]
                                (is (str/includes? result "alice->>bob: hello") result)))))

(deftest render-mermaid-dispatch-test
         (testing "render-mermaid"
                  (testing "dispatches to correct dialect"
                           (is (str/starts-with?
                                (mermaid/render-mermaid :mermaid-state {:states [:a]})
                                "stateDiagram-v2"))
                           (is (str/starts-with?
                                (mermaid/render-mermaid :mermaid-graph {:edges []})
                                "graph LR")))
                  (testing "throws on unknown dialect"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (mermaid/render-mermaid :unknown {}))))))
