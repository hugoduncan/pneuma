(ns pneuma.integrant.lean-compile-test
    "Lean 4 proof emission and compilation tests for the integrant spec.
  Emits Lean source from the formal model and verifies it compiles."
    (:require [clojure.java.io :as io]
              [clojure.java.shell :refer [sh]]
              [clojure.test :refer [deftest is testing]]
            ;; Require formalism/morphism namespaces first so record
            ;; classes are compiled before lean.blueprint imports them
              pneuma.formalism.capability
              pneuma.formalism.effect-signature
              pneuma.formalism.mealy
              pneuma.formalism.optic
              pneuma.formalism.resolver
              pneuma.formalism.statechart
              pneuma.formalism.type-schema
              pneuma.morphism.containment
              pneuma.morphism.existential
              pneuma.morphism.ordering
              pneuma.morphism.structural
              [pneuma.integrant.integrant-spec :as spec]
              [pneuma.lean.core :as lean]
              [pneuma.lean.protocol :as lp]))

(def ^:private lean-cmd
     "Path to the lean executable."
     (let [elan-path (str (System/getProperty "user.home") "/.elan/bin/lean")]
          (if (.exists (io/file elan-path))
              elan-path
              "lean")))

(defn- lean-available? []
       (try
        (let [{:keys [exit]} (sh lean-cmd "--version")]
             (zero? exit))
        (catch Exception _ false)))

(defn- compile-lean [lean-src]
       (let [tmp (java.io.File/createTempFile "pneuma_ig_" ".lean")]
            (try
             (spit tmp lean-src)
             (let [result (sh lean-cmd (.getAbsolutePath tmp))]
                  {:exit (:exit result)
                   :err (str (:out result) (:err result))})
             (finally
              (.delete tmp)))))

(defn- assert-compiles [label lean-src]
       (let [{:keys [exit err]} (compile-lean lean-src)]
            (is (zero? exit)
                (str label " failed to compile:\n" err))))

;;; Per-formalism lean emission tests

(deftest ^{:lean true :regression true} integrant-lifecycle-lean-test
  ;; Verify lifecycle statechart emits valid Lean 4.
         (when (lean-available?)
               (testing "integrant lifecycle ->lean compiles"
                        (assert-compiles "Lifecycle" (lp/->lean spec/lifecycle)))))

(deftest ^{:lean true :regression true} integrant-multimethod-sig-lean-test
  ;; Verify multimethod effect signature emits valid Lean 4.
         (when (lean-available?)
               (testing "integrant multimethod-sig ->lean compiles"
                        (assert-compiles "MultimethodSig" (lp/->lean spec/multimethod-sig)))))

(deftest ^{:lean true :regression true} integrant-capabilities-lean-test
  ;; Verify all capability sets emit valid Lean 4.
         (when (lean-available?)
               (testing "integrant capabilities ->lean compile"
                        (doseq [[id caps] [[:init    spec/init-phase-caps]
                                           [:running spec/running-phase-caps]
                                           [:suspend spec/suspend-phase-caps]
                                           [:resume  spec/resume-phase-caps]
                                           [:halt    spec/halt-phase-caps]]]
                               (testing (str "phase " (name id))
                                        (assert-compiles (str "Capability/" (name id))
                                                         (lp/->lean caps)))))))

(deftest ^{:lean true :regression true} integrant-types-lean-test
  ;; Verify type schema emits valid Lean 4.
         (when (lean-available?)
               (testing "integrant types ->lean compiles"
                        (assert-compiles "TypeSchema" (lp/->lean spec/integrant-types)))))

;;; System-level lean emission

(deftest ^{:lean true :regression true} integrant-system-lean-test
  ;; Verify the full system-level emission compiles.
         (when (lean-available?)
               (testing "integrant system ->lean compiles"
                        (assert-compiles
                         "IntegrantSystem"
                         (lean/emit-lean-system "integrant" spec/spec-system)))))

;;; Verification: no sorry, all conforming

(deftest ^{:lean true :regression true} integrant-system-verified-test
  ;; Verify the integrant system proof has no sorry obligations
  ;; and is fully machine-checked.
         (when (lean-available?)
               (testing "integrant system verification"
                        (let [lean-src (lean/emit-lean-system "integrant" spec/spec-system)]
                             (testing "reports all conforming"
                                      (is (re-find #"ALL CONFORMING" lean-src)
                                          "System should report all conforming"))

                             (testing "contains no sorry"
                                      (is (not (re-find #"(?m)^\s*sorry" lean-src))
                                          "System proofs should not contain sorry"))

                             (testing "all proofs use decide"
                                      (is (re-find #"decide" lean-src)
                                          "Proofs should use decide tactic"))

                             (testing "system_conformance theorem present"
                                      (is (re-find #"theorem system_conformance" lean-src)
                                          "System conformance theorem should be present"))

                             (testing "compiles and verifies in Lean"
                                      (assert-compiles "IntegrantVerified" lean-src))))))

;;; Full emission pipeline

(deftest ^{:lean true :regression true} integrant-emit-all-test
  ;; Verify emit-lean-all produces non-empty output for all sections.
         (when (lean-available?)
               (testing "integrant emit-lean-all"
                        (let [result (lean/emit-lean-all "integrant" spec/spec-system)]
                             (testing "produces formalism emissions"
                                      (is (pos? (count (:formalisms result))))
                                      (doseq [[kind src] (:formalisms result)]
                                             (is (string? src)
                                                 (str "formalism " kind " should emit a string"))
                                             (is (pos? (count src))
                                                 (str "formalism " kind " should emit non-empty"))))

                             (testing "produces morphism emissions"
                                      (is (pos? (count (:morphisms result))))
                                      (doseq [[id src] (:morphisms result)]
                                             (is (string? src)
                                                 (str "morphism " id " should emit a string"))))

                             (testing "produces system emission"
                                      (is (string? (:system result)))
                                      (is (pos? (count (:system result)))))

                             (testing "produces blueprint emission"
                                      (is (string? (:blueprint result)))
                                      (is (pos? (count (:blueprint result)))))

                             (testing "system emission compiles"
                                      (assert-compiles "IntegrantFull" (:system result)))))))
