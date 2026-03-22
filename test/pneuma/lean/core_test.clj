(ns pneuma.lean.core-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.lean.core :as lean]
              [pneuma.path.core]
              [pneuma.protocol-spec :as spec]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.morphism.existential :as ex]))

;; Tests for the lean.core public API. Verifies that the unified
;; emission entry points produce valid Lean 4 source and that
;; all extension protocols are loaded.

(def test-ops
     (es/effect-signature
      {:label "test ES"
       :operations
       {:read {:input {:key :Keyword} :output :String}
        :write {:input {:val :String} :output :Boolean}}}))

(def test-caps
     (cap/capability-set
      {:label "test caps"
       :id :test-caps
       :dispatch #{:read :write}}))

(def test-morphism
     (ex/existential-morphism
      {:id :caps->ops
       :from :capability-set
       :to :effect-signature
       :source-ref-kind :dispatch-refs
       :target-ref-kind :operation-ids}))

;;; Cyclic registry for path emission tests

(def cycle-ops
     (es/effect-signature
      {:label "test ES"
       :operations
       {:dispatch {:input {:event :Keyword} :output :Boolean}}}))

(def cycle-caps
     (cap/capability-set
      {:label "test caps"
       :id :cycle-caps
       :dispatch #{:dispatch}}))

(def cycle-registry
     {:caps->ops (ex/existential-morphism
                  {:id :caps->ops
                   :from :capability-set
                   :to :effect-signature
                   :source-ref-kind :dispatch-refs
                   :target-ref-kind :operation-ids})
      :ops->caps (ex/existential-morphism
                  {:id :ops->caps
                   :from :effect-signature
                   :to :capability-set
                   :source-ref-kind :operation-ids
                   :target-ref-kind :dispatch-refs})})

(deftest emit-lean-test
  ;; Contracts: emit-lean dispatches to a formalism's ->lean
  ;; and returns Lean 4 source.
         (testing "emit-lean"
                  (testing "emits Lean source for CapabilitySet"
                           (let [src (lean/emit-lean test-caps)]
                                (is (string? src))
                                (is (str/includes? src "inductive"))
                                (is (str/includes? src "DecidableEq"))))

                  (testing "emits Lean source for EffectSignature"
                           (let [src (lean/emit-lean test-ops)]
                                (is (string? src))
                                (is (str/includes? src "inductive"))))))

(deftest emit-lean-conn-test
  ;; Contracts: emit-lean-conn emits boundary propositions
  ;; for a morphism.
         (testing "emit-lean-conn"
                  (testing "emits boundary for existential morphism"
                           (let [src (lean/emit-lean-conn test-morphism
                                                          test-caps test-ops)]
                                (is (string? src))
                                (is (str/includes? src "Source"))
                                (is (str/includes? src "Target"))
                                (is (str/includes? src "PROOF TARGET"))))))

(deftest emit-lean-system-test
  ;; Contracts: emit-lean-system produces a unified Lean file
  ;; from a full specification.
         (testing "emit-lean-system"
                  (testing "emits unified file for conforming spec"
                           (let [src (lean/emit-lean-system
                                      "test-spec"
                                      {:formalisms spec/protocol-formalisms
                                       :registry spec/protocol-registry})]
                                (is (string? src))
                                (is (str/includes? src "ALL CONFORMING"))
                                (is (str/includes? src "system_conformance"))))))

(deftest emit-lean-path-test
  ;; Contracts: emit-lean-path emits per-step boundaries and
  ;; a composition theorem for a cycle.
         (testing "emit-lean-path"
                  (testing "emits composition for cyclic path"
                           (let [paths (pneuma.path.core/find-paths cycle-registry)
                                 formalisms {:capability-set cycle-caps
                                             :effect-signature cycle-ops}
                                 src (lean/emit-lean-path (first paths) formalisms)]
                                (is (string? src))
                                (is (str/includes? src "Composed path"))
                                (is (str/includes? src "composition"))
                                (is (str/includes? src "Source"))
                                (is (str/includes? src "Target"))))))

(deftest emit-lean-paths-test
  ;; Contracts: emit-lean-paths discovers and emits all paths.
         (testing "emit-lean-paths"
                  (testing "returns path emissions for cyclic registry"
                           (let [formalisms {:capability-set cycle-caps
                                             :effect-signature cycle-ops}
                                 results (lean/emit-lean-paths formalisms cycle-registry)]
                                (is (= 1 (count results)))
                                (is (keyword? (:id (first results))))
                                (is (string? (:lean-src (first results))))))

                  (testing "returns empty for acyclic registry"
                           (let [formalisms {:capability-set test-caps
                                             :effect-signature test-ops}
                                 results (lean/emit-lean-paths formalisms
                                                               {:caps->ops test-morphism})]
                                (is (empty? results))))))

(deftest emit-lean-all-test
  ;; Contracts: emit-lean-all returns a map with per-formalism,
  ;; per-morphism, path, and system-level Lean source.
         (testing "emit-lean-all"
                  (testing "returns all emission layers"
                           (let [result (lean/emit-lean-all
                                         "test-spec"
                                         {:formalisms {:effect-signature test-ops
                                                       :capability-set test-caps}
                                          :registry {:caps->ops test-morphism}})]
                                (is (map? (:formalisms result)))
                                (is (= 2 (count (:formalisms result))))
                                (is (map? (:morphisms result)))
                                (is (= 1 (count (:morphisms result))))
                                (is (vector? (:paths result)))
                                (is (string? (:system result)))))

                  (testing "includes paths for cyclic registry"
                           (let [result (lean/emit-lean-all
                                         "cycle-spec"
                                         {:formalisms {:effect-signature cycle-ops
                                                       :capability-set cycle-caps}
                                          :registry cycle-registry})]
                                (is (= 1 (count (:paths result))))))))

(deftest emit-lean-file-test
  ;; Contracts: emit-lean-file composes sections into a single
  ;; file with a header.
         (testing "emit-lean-file"
                  (testing "composes sections with header"
                           (let [src (lean/emit-lean-file "test" ["section1" "section2"])]
                                (is (str/includes? src "Generated by Pneuma"))
                                (is (str/includes? src "test"))
                                (is (str/includes? src "section1"))
                                (is (str/includes? src "section2"))))

                  (testing "filters nil sections"
                           (let [src (lean/emit-lean-file "test" ["s1" nil "s2"])]
                                (is (not (str/includes? src "nil")))))))
