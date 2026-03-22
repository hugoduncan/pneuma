(ns pneuma.lean.blueprint-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.statechart :as sc]
              [pneuma.formalism.mealy :as mealy]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.lean.blueprint :as bp]))

;; Tests for Lean Blueprint LaTeX emission.

(deftest capability-blueprint-entries-test
  ;; blueprint-entries for CapabilitySet produces definition and theorem entries.
         (testing "CapabilitySet blueprint-entries"
                  (let [caps (cap/capability-set
                              {:label "test caps"
                               :id :test-caps
                               :dispatch #{:alpha :beta}})
                        entries (bp/blueprint-entries caps)]

                       (testing "returns a vector of entries"
                                (is (vector? entries))
                                (is (pos? (count (remove nil? entries)))))

                       (testing "contains definition and theorem kinds"
                                (let [kinds (into #{} (map :kind) (remove nil? entries))]
                                     (is (contains? kinds :definition))
                                     (is (contains? kinds :theorem))))

                       (testing "entries have required keys"
                                (doseq [e (remove nil? entries)]
                                       (is (contains? e :kind))
                                       (is (contains? e :name))
                                       (is (contains? e :label))
                                       (is (contains? e :prose))
                                       (is (contains? e :proved?))
                                       (is (contains? e :uses)))))))

(deftest statechart-blueprint-entries-test
  ;; blueprint-entries for Statechart produces the full set of entries.
         (testing "Statechart blueprint-entries"
                  (let [chart (sc/statechart
                               {:label "test SC"
                                :states #{:idle :running :done}
                                :initial {:root :idle}
                                :hierarchy {:root #{:idle :running :done}}
                                :transitions
                                [{:source :idle :event :start :target :running}
                                 {:source :running :event :finish :target :done}]})
                        entries (bp/blueprint-entries chart)]

                       (testing "contains State and Event definitions"
                                (is (some #(= "State" (:name %)) entries))
                                (is (some #(= "Event" (:name %)) entries)))

                       (testing "contains chart_safety theorem"
                                (is (some #(= "chart_safety" (:name %)) entries)))

                       (testing "chart_safety uses reachable and initialState"
                                (let [safety (first (filter #(= "chart_safety" (:name %)) entries))]
                                     (is (some #{"def:reachable"} (:uses safety)))
                                     (is (some #{"def:initialState"} (:uses safety))))))))

(deftest render-entry-test
  ;; render-entry produces valid LaTeX for different entry kinds.
         (testing "render-entry"
                  (testing "for a proved definition"
                           (let [e {:kind :definition :name "State" :label "def:State"
                                    :proved? true :uses [] :prose "All states."}
                                 latex (bp/render-entry e)]
                                (is (str/includes? latex "\\begin{definition}"))
                                (is (str/includes? latex "\\label{def:State}"))
                                (is (str/includes? latex "\\lean{State}"))
                                (is (str/includes? latex "\\leanok"))
                                (is (str/includes? latex "All states."))
                                (is (str/includes? latex "\\end{definition}"))))

                  (testing "for an unproved theorem"
                           (let [e {:kind :theorem :name "chase_terminates" :label "thm:chase_terminates"
                                    :proved? false :uses ["def:closed"] :prose "Chase terminates."}
                                 latex (bp/render-entry e)]
                                (is (str/includes? latex "\\begin{theorem}"))
                                (is (str/includes? latex "\\lean{chase_terminates}"))
                                (is (not (str/includes? latex "\\leanok")))
                                (is (str/includes? latex "\\uses{def:closed}"))
                                (is (str/includes? latex "\\end{theorem}"))))

                  (testing "for a theorem with multiple uses"
                           (let [e {:kind :theorem :name "safety" :label "thm:safety"
                                    :proved? true :uses ["def:State" "def:step"]
                                    :prose "Safety."}
                                 latex (bp/render-entry e)]
                                (is (str/includes? latex "\\uses{def:State, def:step}"))))))

(deftest render-section-test
  ;; render-section produces a titled LaTeX section.
         (testing "render-section"
                  (let [entries [{:kind :definition :name "Foo" :label "def:Foo"
                                  :proved? true :uses [] :prose "A foo."}]
                        latex (bp/render-section "Test Section" entries)]
                       (is (str/includes? latex "\\section{Test Section}"))
                       (is (str/includes? latex "\\begin{definition}")))))

(deftest emit-blueprint-test
  ;; emit-blueprint produces a complete LaTeX document.
         (testing "emit-blueprint"
                  (let [caps (cap/capability-set
                              {:label "test caps"
                               :id :my-caps :dispatch #{:read :write}})
                        config {:formalisms {:my-caps caps} :registry {}}
                        latex (bp/emit-blueprint "test-spec" config)]

                       (testing "contains document structure"
                                (is (str/includes? latex "\\begin{document}"))
                                (is (str/includes? latex "\\end{document}"))
                                (is (str/includes? latex "\\title{Pneuma Proof Blueprint}")))

                       (testing "contains spec name"
                                (is (str/includes? latex "test-spec")))

                       (testing "contains formalism entries"
                                (is (str/includes? latex "\\lean{"))
                                (is (str/includes? latex "\\label{"))))))

(deftest effect-signature-blueprint-entries-test
  ;; blueprint-entries for EffectSignature includes per-op structures.
         (testing "EffectSignature blueprint-entries"
                  (let [sig (es/effect-signature
                             {:label "test ES"
                              :operations {:alpha {:input {:x :String} :output :Bool}
                                           :beta {:input {:y :Nat} :output :String}}})
                        entries (bp/blueprint-entries sig)]

                       (testing "contains Op definition"
                                (is (some #(= "Op" (:name %)) entries)))

                       (testing "contains per-op Args definitions"
                                (is (some #(= "AlphaArgs" (:name %)) entries))
                                (is (some #(= "BetaArgs" (:name %)) entries)))

                       (testing "contains completeness theorem"
                                (is (some #(= "allOps_complete" (:name %)) entries))))))

(deftest mealy-blueprint-entries-test
  ;; blueprint-entries for MealyHandlerSet produces handler entries.
         (testing "MealyHandlerSet blueprint-entries"
                  (let [mhs (mealy/mealy-handler-set
                             {:label "test mealy"
                              :declarations
                              [{:id :on-click}
                               {:id :on-submit}]})
                        entries (bp/blueprint-entries mhs)]

                       (testing "contains HandlerId definition"
                                (is (some #(= "HandlerId" (:name %)) entries)))

                       (testing "all entries are proved"
                                (is (every? :proved? entries))))))

(deftest type-schema-blueprint-entries-test
  ;; blueprint-entries for TypeSchema produces type entries.
         (testing "TypeSchema blueprint-entries"
                  (let [ts-val (ts/type-schema {:label "test types" :types {:User :string :Role :int}})
                        entries (bp/blueprint-entries ts-val)]

                       (testing "contains TypeId definition"
                                (is (some #(= "TypeId" (:name %)) entries)))

                       (testing "contains count of 2"
                                (is (some #(str/includes? (:prose %) "2") entries))))))
