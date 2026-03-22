(ns pneuma.doc.render-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.doc.fragment :as frag]
              [pneuma.doc.render :as render]))

;; Tests for render-markdown covering all fragment kinds and composed trees.
;; Contracts: each fragment kind produces correct markdown structure;
;; composed trees render recursively with correct heading levels.

(deftest render-prose-test
  ;; prose renders to a paragraph followed by a blank line.
         (testing "render-markdown"
                  (testing "prose fragment"
                           (testing "renders text followed by blank line"
                                    (let [result (render/render-markdown (frag/prose :p1 "Hello world"))]
                                         (is (str/starts-with? result "Hello world") result)
                                         (is (str/ends-with? result "\n\n") result))))))

(deftest render-table-test
  ;; table renders to a markdown table with header, separator, and data rows.
         (testing "render-markdown"
                  (testing "table fragment"
                           (let [rows   [{:name "Alice" :age "30"} {:name "Bob" :age "25"}]
                                 result (render/render-markdown (frag/table :t1 [:name :age] rows))]
                                (testing "includes column headers"
                                         (is (str/includes? result "name") result)
                                         (is (str/includes? result "age") result))
                                (testing "includes separator row"
                                         (is (str/includes? result "---") result))
                                (testing "includes row data"
                                         (is (str/includes? result "Alice") result)
                                         (is (str/includes? result "30") result))
                                (testing "is a pipe-delimited table"
                                         (is (str/includes? result "|") result))))))

(deftest render-section-test
  ;; section renders as a heading followed by rendered children.
         (testing "render-markdown"
                  (testing "section fragment"
                           (let [child  (frag/prose :p1 "body text")
                                 result (render/render-markdown (frag/section :s1 "My Title" [child]))]
                                (testing "renders heading at level ##"
                                         (is (str/starts-with? result "## My Title") result))
                                (testing "renders child content"
                                         (is (str/includes? result "body text") result)))))

         (testing "nested sections"
                  (testing "increment heading level with nesting depth"
                           (let [inner  (frag/section :inner "Inner" [(frag/prose :p1 "x")])
                                 outer  (frag/section :outer "Outer" [inner])
                                 result (render/render-markdown outer)]
                                (is (str/includes? result "## Outer") result)
                                (is (str/includes? result "### Inner") result)))))

(deftest render-diagram-spec-test
  ;; diagram-spec renders to a fenced mermaid code block.
         (testing "render-markdown"
                  (testing "diagram-spec fragment with mermaid-state dialect"
                           (let [data   {:states [:idle :running]
                                         :transitions [[:idle :running "start"]]}
                                 result (render/render-markdown (frag/diagram-spec :d1 :mermaid-state data))]
                                (testing "wraps in mermaid fenced block"
                                         (is (str/includes? result "```mermaid") result)
                                         (is (str/includes? result "```") result))
                                (testing "uses stateDiagram-v2"
                                         (is (str/includes? result "stateDiagram-v2") result))
                                (testing "includes transitions"
                                         (is (str/includes? result "idle") result)
                                         (is (str/includes? result "running") result))))))

(deftest render-cross-ref-test
  ;; cross-ref renders to a markdown link with an anchor derived from the target id.
         (testing "render-markdown"
                  (testing "cross-ref fragment"
                           (let [result (render/render-markdown (frag/cross-ref :my-section "See Section"))]
                                (testing "renders as markdown link"
                                         (is (str/starts-with? result "[See Section]") result))
                                (testing "link target is an anchor"
                                         (is (str/includes? result "(#") result))))))

(deftest render-status-annotation-test
  ;; status-annotation renders as an inline bold badge.
         (testing "render-markdown"
                  (testing "status-annotation with :conforms status"
                           (let [result (render/render-markdown
                                         (frag/status-annotation :s1 :conforms nil))]
                                (testing "renders CONFORMS badge"
                                         (is (str/includes? result "CONFORMS") result))
                                (testing "uses bold formatting"
                                         (is (str/includes? result "**") result))))

                  (testing "status-annotation with :diverges status and detail"
                           (let [result (render/render-markdown
                                         (frag/status-annotation :s1 :diverges {:reason :mismatch}))]
                                (testing "renders DIVERGES badge"
                                         (is (str/includes? result "DIVERGES") result))
                                (testing "includes detail"
                                         (is (str/includes? result "mismatch") result))))))

(deftest render-composed-tree-test
  ;; A composed section with mixed children renders all child types.
         (testing "render-markdown"
                  (testing "composed tree with mixed children"
                           (let [tree   (frag/section
                                         :root "Report"
                                         [(frag/prose :p1 "Intro text")
                                          (frag/table :t1 [:col] [{:col "val"}])
                                          (frag/cross-ref :appendix "See Appendix")
                                          (frag/status-annotation :root :conforms nil)])
                                 result (render/render-markdown tree)]
                                (testing "renders heading"
                                         (is (str/includes? result "## Report") result))
                                (testing "renders prose child"
                                         (is (str/includes? result "Intro text") result))
                                (testing "renders table child"
                                         (is (str/includes? result "col") result))
                                (testing "renders cross-ref child"
                                         (is (str/includes? result "See Appendix") result))
                                (testing "renders status-annotation child"
                                         (is (str/includes? result "CONFORMS") result))))))
