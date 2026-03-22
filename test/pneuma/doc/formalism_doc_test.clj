(ns pneuma.doc.formalism-doc-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.doc.fragment :as frag]
              [pneuma.formalism.capability :as capability]
              [pneuma.formalism.effect-signature :as effect-sig]
              [pneuma.formalism.mealy :as mealy]
              [pneuma.formalism.optic :as optic]
              [pneuma.formalism.resolver :as resolver]
              [pneuma.formalism.statechart :as statechart]
              [pneuma.formalism.type-schema :as type-schema]
              [pneuma.protocol :as p]))

;; Tests for ->doc projection on all 7 formalism records.
;; Contracts: each ->doc returns a section fragment whose children include
;; the expected fragment kinds with correct columns and structure.

(deftest statechart-doc-test
         (testing "Statechart ->doc"
                  (let [sc  (statechart/statechart
                             {:states      #{:a :b}
                              :initial     {}
                              :transitions [{:source :a :event :go :target :b}]})
                        doc (p/->doc sc)]
                       (testing "returns a section"
                                (is (= :section (:kind doc))))
                       (testing "contains a diagram-spec child"
                                (let [diag (first (filter frag/diagram-spec? (:children doc)))]
                                     (is (some? diag))
                                     (is (= :statechart/diagram (:id diag)))
                                     (is (= :mermaid-state (:dialect diag)))))
                       (testing "contains a transition table"
                                (let [tbl (first (filter frag/table? (:children doc)))]
                                     (is (some? tbl))
                                     (is (= :statechart/transitions (:id tbl)))
                                     (is (= [:source :event :target :guard :raise] (:columns tbl)))))
                       (testing "contains a prose fragment for reachable configs"
                                (let [p (first (filter frag/prose? (:children doc)))]
                                     (is (some? p))
                                     (is (= :statechart/configs (:id p))))))))

(deftest effect-signature-doc-test
         (testing "EffectSignature ->doc"
                  (let [es  (effect-sig/effect-signature
                             {:operations {:do-thing {:input {:x :String} :output :Bool}}})
                        doc (p/->doc es)]
                       (testing "returns a section"
                                (is (= :section (:kind doc))))
                       (testing "contains an operations table"
                                (let [tbl (first (filter frag/table? (:children doc)))]
                                     (is (some? tbl))
                                     (is (= :effect-signature/operations (:id tbl)))
                                     (is (= [:operation :fields :output] (:columns tbl))))))))

(deftest mealy-handler-set-doc-test
         (testing "MealyHandlerSet ->doc"
                  (let [mhs (mealy/mealy-handler-set
                             {:declarations [{:id      :handle-it
                                              :params  [{:name :x :type :String}]
                                              :guards  [{:check :ok? :args [:x]}]
                                              :updates [{:path [:data] :op :assoc :value 1}]
                                              :effects [{:op :do-thing :fields {:x "hi"}}]}]})
                        doc (p/->doc mhs)]
                       (testing "returns a section"
                                (is (= :section (:kind doc))))
                       (testing "contains a handler table"
                                (let [tbl (first (filter frag/table? (:children doc)))]
                                     (is (some? tbl))
                                     (is (= :mealy/handlers (:id tbl)))
                                     (is (= [:handler :params :guards :updates :effects] (:columns tbl))))))))

(deftest optic-declaration-doc-test
         (testing "OpticDeclaration ->doc"
                  (let [od  (optic/optic-declaration
                             {:declarations [{:id :my-lens :optic-type :Lens :path [:data :field]}]})
                        doc (p/->doc od)]
                       (testing "returns a section"
                                (is (= :section (:kind doc))))
                       (testing "contains a catalog table"
                                (let [tbl (first (filter frag/table? (:children doc)))]
                                     (is (some? tbl))
                                     (is (= :optic/catalog (:id tbl)))
                                     (is (= [:id :optic-type :path] (:columns tbl))))))))

(deftest resolver-graph-doc-test
         (testing "ResolverGraph ->doc"
                  (let [rg  (resolver/resolver-graph
                             {:declarations [{:id :fetch :input #{:a} :output #{:b}}]})
                        doc (p/->doc rg)]
                       (testing "returns a section"
                                (is (= :section (:kind doc))))
                       (testing "contains a resolver table"
                                (let [tbl (first (filter frag/table? (:children doc)))]
                                     (is (some? tbl))
                                     (is (= :resolver/resolvers (:id tbl)))
                                     (is (= [:id :input :output :source] (:columns tbl)))))
                       (testing "contains a reachability prose fragment"
                                (let [pr (first (filter frag/prose? (:children doc)))]
                                     (is (some? pr))
                                     (is (= :resolver/reachability (:id pr)))))
                       (testing "contains a dependency graph diagram"
                                (let [diag (first (filter frag/diagram-spec? (:children doc)))]
                                     (is (some? diag))
                                     (is (= :resolver/graph (:id diag)))
                                     (is (= :mermaid-graph (:dialect diag))))))))

(deftest capability-set-doc-test
         (testing "CapabilitySet ->doc"
                  (let [cs  (capability/capability-set
                             {:id       :test-caps
                              :dispatch #{:do-thing}
                              :subscribe #{:watch-it}
                              :query   #{:get-stuff}})
                        doc (p/->doc cs)]
                       (testing "returns a section"
                                (is (= :section (:kind doc))))
                       (testing "contains a permissions table"
                                (let [tbl (first (filter frag/table? (:children doc)))]
                                     (is (some? tbl))
                                     (is (= :capability/permissions (:id tbl)))
                                     (is (= [:kind :operations] (:columns tbl))))))))

(deftest type-schema-doc-test
         (testing "TypeSchema ->doc"
                  (let [ts  (type-schema/type-schema {:String :string :Bool :boolean})
                        doc (p/->doc ts)]
                       (testing "returns a section"
                                (is (= :section (:kind doc))))
                       (testing "contains a type table"
                                (let [tbl (first (filter frag/table? (:children doc)))]
                                     (is (some? tbl))
                                     (is (= :type-schema/types (:id tbl)))
                                     (is (= [:type :schema] (:columns tbl))))))))
