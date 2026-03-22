(ns pneuma.doc.core-test
    (:require [clojure.string :as str]
              [clojure.test :refer [deftest testing is]]
              [pneuma.doc.core :as core]
              [pneuma.doc.fragment :as doc]
              [pneuma.formalism.capability :as capability]
              [pneuma.formalism.effect-signature :as effect-sig]
              [pneuma.morphism.existential :as ex]))

;; Tests for the document assembly and REPL integration API.
;; Contracts: cross-formalism functions return section fragments with the
;; expected children; render-doc returns a rendered string; explain-*
;; functions return rendered markdown strings.

(def test-registry
     {:caps->ops
      (ex/existential-morphism
       {:id :caps->ops :from :capability-set :to :effect-signature
        :source-ref-kind :dispatch-refs :target-ref-kind :operation-ids})})

(def test-effect-sig
     (effect-sig/effect-signature
      {:operations {:do-thing {:input {:x :String} :output :Bool}}}))

(def test-caps
     (capability/capability-set
      {:id :test-caps :dispatch #{:do-thing}}))

(deftest morphism-doc-test
  ;; morphism-doc returns a section fragment with a diagram and a table child.
         (testing "morphism-doc"
                  (testing "returns a section fragment"
                           (let [result (core/morphism-doc test-registry)]
                                (is (doc/section? result) (pr-str result))))

                  (testing "contains a diagram child"
                           (let [result   (core/morphism-doc test-registry)
                                 children (:children result)]
                                (is (some doc/diagram-spec? children) (pr-str children))))

                  (testing "contains a table child"
                           (let [result   (core/morphism-doc test-registry)
                                 children (:children result)]
                                (is (some doc/table? children) (pr-str children))))

                  (testing "table includes morphism id and kind"
                           (let [result (core/morphism-doc test-registry)
                                 table  (first (filter doc/table? (:children result)))
                                 rows   (:rows table)]
                                (is (= 1 (count rows)) (pr-str rows))
                                (is (str/includes? (str (:id (first rows))) "caps->ops")
                                    (pr-str (first rows)))))))

(deftest path-doc-test
  ;; path-doc with an empty registry returns a section with no path children.
         (testing "path-doc"
                  (testing "with empty registry returns a section"
                           (let [result (core/path-doc {})]
                                (is (doc/section? result) (pr-str result))))

                  (testing "with empty registry returns empty children"
                           (let [result (core/path-doc {})]
                                (is (empty? (:children result)) (pr-str result))))

                  (testing "with a registry returns a section"
                           (let [result (core/path-doc test-registry)]
                                (is (doc/section? result) (pr-str result))))))

(deftest gap-report-doc-test
  ;; gap-report-doc returns a section with summary prose and three gap tables.
         (testing "gap-report-doc"
                  (let [gap-report {:object-gaps   [{:formalism :effect-signature
                                                     :status    :conforms
                                                     :detail    nil}]
                                    :morphism-gaps [{:id :caps->ops :status :diverges
                                                     :detail {:dangling-refs #{:missing}}}]
                                    :path-gaps     []}]

                       (testing "returns a section fragment"
                                (let [result (core/gap-report-doc gap-report)]
                                     (is (doc/section? result) (pr-str result))))

                       (testing "contains a prose summary child"
                                (let [result   (core/gap-report-doc gap-report)
                                      children (:children result)]
                                     (is (some doc/prose? children) (pr-str children))))

                       (testing "summary mentions total and failure counts"
                                (let [result  (core/gap-report-doc gap-report)
                                      summary (first (filter doc/prose? (:children result)))]
                                     (is (str/includes? (:text summary) "Total gaps: 2") (:text summary))
                                     (is (str/includes? (:text summary) "Failures: 1") (:text summary))))

                       (testing "contains three table children"
                                (let [result  (core/gap-report-doc gap-report)
                                      tables  (filter doc/table? (:children result))]
                                     (is (= 3 (count tables)) (pr-str (mapv :id tables))))))))

(deftest render-doc-test
  ;; render-doc assembles all fragments and returns a rendered string for :markdown.
         (testing "render-doc"
                  (testing "returns a string for markdown format"
                           (let [result (core/render-doc
                                         {:formalisms [test-effect-sig test-caps]
                                          :registry   test-registry
                                          :format     :markdown})]
                                (is (string? result) (type result))))

                  (testing "rendered string includes formalism content"
                           (let [result (core/render-doc
                                         {:formalisms [test-effect-sig test-caps]
                                          :registry   test-registry
                                          :format     :markdown})]
                                (is (str/includes? result "Effect Signature") result)
                                (is (str/includes? result "Capability Set") result)))

                  (testing "rendered string includes morphism section"
                           (let [result (core/render-doc
                                         {:formalisms [test-effect-sig test-caps]
                                          :registry   test-registry
                                          :format     :markdown})]
                                (is (str/includes? result "Morphism") result)))

                  (testing "with gap report includes gap section"
                           (let [gap-report {:object-gaps   []
                                             :morphism-gaps []
                                             :path-gaps     []}
                                 result     (core/render-doc
                                             {:formalisms [test-effect-sig test-caps]
                                              :registry   test-registry
                                              :gap-report gap-report
                                              :format     :markdown})]
                                (is (str/includes? result "Gap Report") result)))))

(deftest explain-connections-test
  ;; explain-connections returns rendered markdown for morphisms involving a formalism.
         (testing "explain-connections"
                  (testing "returns a string"
                           (let [result (core/explain-connections :capability-set test-registry)]
                                (is (string? result) (type result))))

                  (testing "string contains morphism info for the formalism"
                           (let [result (core/explain-connections :capability-set test-registry)]
                                (is (str/includes? result "caps->ops") result)))

                  (testing "for unknown formalism returns string with empty table"
                           (let [result (core/explain-connections :unknown-kind test-registry)]
                                (is (string? result) (type result))))))

(deftest explain-test
  ;; explain finds the formalism containing a component id and renders its doc.
         (testing "explain"
                  (testing "returns a string when formalism is found"
                           (let [formalisms {:effect-signature test-effect-sig
                                             :capability-set   test-caps}
                                 result     (core/explain :effect-signature/root formalisms)]
                                (is (string? result) (pr-str result))))

                  (testing "returns nil when component id is not found"
                           (let [formalisms {:effect-signature test-effect-sig}
                                 result     (core/explain :no-such-id formalisms)]
                                (is (nil? result))))))

(deftest explain-cycle-test
  ;; explain-cycle finds a path by id and renders its documentation.
         (testing "explain-cycle"
                  (testing "returns nil when cycle id is not in registry"
                           (let [result (core/explain-cycle :no-such-cycle test-registry)]
                                (is (nil? result))))

                  (testing "returns nil for empty registry"
                           (let [result (core/explain-cycle :any-cycle {})]
                                (is (nil? result))))))
