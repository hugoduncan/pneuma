(ns pneuma.doc.fragment-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.doc.fragment :as frag]))

;; Tests for fragment constructors, predicates, and gap annotation.
;; Contracts: constructors produce maps with correct :kind and fields;
;; predicates correctly identify fragment types; annotate-with-gaps
;; appends status-annotation children for matching gaps.

(deftest section-constructor-test
  ;; section produces a map with :kind :section and the given fields.
         (testing "section"
                  (testing "produces a map with :kind :section"
                           (let [f (frag/section :s1 "My Section" [])]
                                (is (= :section (:kind f)))))
                  (testing "stores id, title, and children"
                           (let [child (frag/prose :p1 "hello")
                                 f     (frag/section :s1 "Title" [child])]
                                (is (= :s1 (:id f)))
                                (is (= "Title" (:title f)))
                                (is (= [child] (:children f)))))
                  (testing "coerces children to a vector"
                           (let [f (frag/section :s1 "T" (list (frag/prose :p1 "x")))]
                                (is (vector? (:children f)))))))

(deftest table-constructor-test
  ;; table produces a map with :kind :table and the given fields.
         (testing "table"
                  (testing "produces a map with :kind :table"
                           (let [f (frag/table :t1 [:a :b] [])]
                                (is (= :table (:kind f)))))
                  (testing "stores id, columns, and rows"
                           (let [rows [{:a "1" :b "2"}]
                                 f    (frag/table :t1 [:a :b] rows)]
                                (is (= :t1 (:id f)))
                                (is (= [:a :b] (:columns f)))
                                (is (= rows (:rows f)))))
                  (testing "coerces columns and rows to vectors"
                           (let [f (frag/table :t1 (list :a) (list {:a "x"}))]
                                (is (vector? (:columns f)))
                                (is (vector? (:rows f)))))))

(deftest prose-constructor-test
  ;; prose produces a map with :kind :prose and the given fields.
         (testing "prose"
                  (testing "produces a map with :kind :prose"
                           (let [f (frag/prose :p1 "some text")]
                                (is (= :prose (:kind f)))))
                  (testing "stores id and text"
                           (let [f (frag/prose :p1 "hello")]
                                (is (= :p1 (:id f)))
                                (is (= "hello" (:text f)))))))

(deftest diagram-spec-constructor-test
  ;; diagram-spec produces a map with :kind :diagram-spec and the given fields.
         (testing "diagram-spec"
                  (testing "produces a map with :kind :diagram-spec"
                           (let [f (frag/diagram-spec :d1 :mermaid-state {})]
                                (is (= :diagram-spec (:kind f)))))
                  (testing "stores id, dialect, and data"
                           (let [data {:states [:a :b]}
                                 f    (frag/diagram-spec :d1 :mermaid-graph data)]
                                (is (= :d1 (:id f)))
                                (is (= :mermaid-graph (:dialect f)))
                                (is (= data (:data f)))))))

(deftest cross-ref-constructor-test
  ;; cross-ref produces a map with :kind :cross-ref and the given fields.
         (testing "cross-ref"
                  (testing "produces a map with :kind :cross-ref"
                           (let [f (frag/cross-ref :target "See here")]
                                (is (= :cross-ref (:kind f)))))
                  (testing "stores target-id and label"
                           (let [f (frag/cross-ref :s1 "Section 1")]
                                (is (= :s1 (:target-id f)))
                                (is (= "Section 1" (:label f)))))))

(deftest status-annotation-constructor-test
  ;; status-annotation produces a map with :kind :status-annotation.
         (testing "status-annotation"
                  (testing "produces a map with :kind :status-annotation"
                           (let [f (frag/status-annotation :s1 :conforms nil)]
                                (is (= :status-annotation (:kind f)))))
                  (testing "stores target-id, status, and detail"
                           (let [f (frag/status-annotation :s1 :diverges {:reason :mismatch})]
                                (is (= :s1 (:target-id f)))
                                (is (= :diverges (:status f)))
                                (is (= {:reason :mismatch} (:detail f)))))))

(deftest fragment-predicate-test
  ;; fragment? returns true for all known kinds and false for non-fragments.
         (testing "fragment?"
                  (testing "returns true for section"
                           (is (frag/fragment? (frag/section :s1 "T" []))))
                  (testing "returns true for table"
                           (is (frag/fragment? (frag/table :t1 [] []))))
                  (testing "returns true for prose"
                           (is (frag/fragment? (frag/prose :p1 "x"))))
                  (testing "returns true for diagram-spec"
                           (is (frag/fragment? (frag/diagram-spec :d1 :mermaid-state {}))))
                  (testing "returns true for cross-ref"
                           (is (frag/fragment? (frag/cross-ref :x1 "lbl"))))
                  (testing "returns true for status-annotation"
                           (is (frag/fragment? (frag/status-annotation :a1 :conforms nil))))
                  (testing "returns false for plain map without :kind"
                           (is (not (frag/fragment? {:foo :bar}))))
                  (testing "returns false for unknown :kind"
                           (is (not (frag/fragment? {:kind :unknown}))))
                  (testing "returns false for nil"
                           (is (not (frag/fragment? nil))))))

(deftest type-specific-predicates-test
  ;; Each type predicate returns true only for the matching kind.
         (testing "type-specific predicates"
                  (let [s  (frag/section :s1 "T" [])
                        t  (frag/table :t1 [] [])
                        p  (frag/prose :p1 "x")
                        d  (frag/diagram-spec :d1 :mermaid-state {})
                        cr (frag/cross-ref :x1 "lbl")
                        sa (frag/status-annotation :a1 :conforms nil)]
                       (testing "section? matches only section"
                                (is (frag/section? s))
                                (is (not (frag/section? p))))
                       (testing "table? matches only table"
                                (is (frag/table? t))
                                (is (not (frag/table? s))))
                       (testing "prose? matches only prose"
                                (is (frag/prose? p))
                                (is (not (frag/prose? t))))
                       (testing "diagram-spec? matches only diagram-spec"
                                (is (frag/diagram-spec? d))
                                (is (not (frag/diagram-spec? p))))
                       (testing "cross-ref? matches only cross-ref"
                                (is (frag/cross-ref? cr))
                                (is (not (frag/cross-ref? d))))
                       (testing "status-annotation? matches only status-annotation"
                                (is (frag/status-annotation? sa))
                                (is (not (frag/status-annotation? cr)))))))

(deftest annotate-with-gaps-test
  ;; annotate-with-gaps appends status-annotation children to fragments
  ;; whose :id matches gap :formalism entries in the gap report.
         (testing "annotate-with-gaps"
                  (let [prose-node (frag/prose :my-formalism "desc")
                        doc        (frag/section :root "Root" [prose-node])
                        gap-report {:object-gaps
                                    [{:formalism :my-formalism :status :diverges
                                      :detail {:reason :bad}}]
                                    :morphism-gaps []
                                    :path-gaps []}]

                       (testing "adds status-annotation to matching fragment"
                                (let [result        (frag/annotate-with-gaps doc gap-report)
                                      root-children (:children result)
                                      annotated     (first root-children)]
                                     (is (= 1 (count root-children)))
                                     (is (contains? annotated :children))
                                     (let [annotations (:children annotated)]
                                          (is (= 1 (count annotations)))
                                          (let [ann (first annotations)]
                                               (is (frag/status-annotation? ann))
                                               (is (= :diverges (:status ann)))
                                               (is (= :my-formalism (:target-id ann)))))))

                       (testing "does not alter tree when no gaps match"
                                (let [empty-report {:object-gaps [] :morphism-gaps [] :path-gaps []}
                                      result       (frag/annotate-with-gaps doc empty-report)]
                                     (is (= doc result))))

                       (testing "annotates section itself when :id matches"
                                (let [named-section (frag/section :sec1 "S" [])
                                      root2         (frag/section :root2 "R" [named-section])
                                      report2       {:object-gaps
                                                     [{:formalism :sec1 :status :absent :detail nil}]
                                                     :morphism-gaps []
                                                     :path-gaps []}
                                      result        (frag/annotate-with-gaps root2 report2)
                                      inner         (first (:children result))]
                                     (is (some frag/status-annotation? (:children inner))))))))
