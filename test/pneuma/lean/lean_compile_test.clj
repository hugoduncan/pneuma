(ns pneuma.lean.lean-compile-test
    "Tests that emitted Lean 4 source actually compiles.
  These tests require the Lean 4 toolchain (via elan) and are slow
  (~1s per file). Run via `clojure -M:test --profile :ci` or
  `clojure -M:test :lean`."
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.java.io :as io]
              [clojure.java.shell :refer [sh]]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.mealy :as mealy]
              [pneuma.formalism.statechart :as sc]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.lean.protocol :as lp]
              [pneuma.lean.capability]
              [pneuma.lean.effect-signature]
              [pneuma.lean.mealy]
              [pneuma.lean.statechart]
              [pneuma.lean.type-schema]
              [pneuma.lean.system :as sys]
              [pneuma.protocol-spec :as spec]))

(def ^:private lean-cmd
     "Path to the lean executable."
     (let [elan-path (str (System/getProperty "user.home") "/.elan/bin/lean")]
          (if (.exists (io/file elan-path))
              elan-path
              "lean")))

(defn- lean-available?
       "Returns true if the Lean compiler is available."
       []
       (try
        (let [{:keys [exit]} (sh lean-cmd "--version")]
             (zero? exit))
        (catch Exception _ false)))

(defn- compile-lean
       "Compiles a Lean source string. Returns {:exit int :err string}."
       [lean-src]
       (let [tmp (java.io.File/createTempFile "pneuma_" ".lean")]
            (try
             (spit tmp lean-src)
             (let [result (sh lean-cmd (.getAbsolutePath tmp))]
                  {:exit (:exit result)
                   :err (str (:out result) (:err result))})
             (finally
              (.delete tmp)))))

(defn- assert-compiles
       "Asserts that the Lean source compiles without errors."
       [label lean-src]
       (let [{:keys [exit err]} (compile-lean lean-src)]
            (is (zero? exit)
                (str label " failed to compile:\n" err))))

;;; Per-formalism compilation tests

(deftest ^:lean capability-set-lean-compiles-test
  ;; CapabilitySet ->lean produces valid Lean 4.
         (when (lean-available?)
               (testing "CapabilitySet ->lean compiles"
                        (assert-compiles
                         "CapabilitySet"
                         (lp/->lean (cap/capability-set
                                     {:id :test-caps
                                      :dispatch #{:alpha :beta :gamma}}))))))

(deftest ^:lean effect-signature-lean-compiles-test
  ;; EffectSignature ->lean produces valid Lean 4.
         (when (lean-available?)
               (testing "EffectSignature ->lean compiles"
                        (assert-compiles
                         "EffectSignature"
                         (lp/->lean (es/effect-signature
                                     {:operations
                                      {:op-a {:input {:x :String} :output :Bool}
                                       :op-b {:input {:y :Nat :z :Keyword}
                                              :output :String}}}))))))

(deftest ^:lean type-schema-lean-compiles-test
  ;; TypeSchema ->lean produces valid Lean 4.
         (when (lean-available?)
               (testing "TypeSchema ->lean compiles"
                        (assert-compiles
                         "TypeSchema"
                         (lp/->lean (ts/type-schema
                                     {:Foo :string :Bar :int :Baz :any}))))))

(deftest ^:lean statechart-lean-compiles-test
  ;; Statechart ->lean produces valid Lean 4.
         (when (lean-available?)
               (testing "Statechart ->lean compiles"
                        (assert-compiles
                         "Statechart"
                         (lp/->lean (sc/statechart
                                     {:states #{:idle :running :done}
                                      :initial {:root :idle}
                                      :hierarchy {:root #{:idle :running :done}}
                                      :transitions
                                      [{:source :idle :event :start :target :running}
                                       {:source :running :event :finish :target :done}
                                       {:source :done :event :reset :target :idle}]}))))))

(deftest ^:lean mealy-lean-compiles-test
  ;; MealyHandlerSet ->lean produces valid Lean 4.
         (when (lean-available?)
               (testing "MealyHandlerSet ->lean compiles"
                        (assert-compiles
                         "MealyHandlerSet"
                         (lp/->lean (mealy/mealy-handler-set
                                     {:declarations
                                      [{:id :handle-a
                                        :guards [{:check :in-state? :args [:sid :idle]}]
                                        :effects [{:op :do-thing
                                                   :fields {:cb [:event-ref :done]}}]}
                                       {:id :handle-b}]}))))))

;;; System-level compilation test

(deftest ^:lean system-lean-compiles-test
  ;; The system-level emission from protocol-spec compiles.
         (when (lean-available?)
               (testing "system-level ->lean compiles"
                        (assert-compiles
                         "System (protocol-spec)"
                         (sys/emit-system-lean
                          "pneuma.protocol-spec"
                          {:formalisms spec/protocol-formalisms
                           :registry spec/protocol-registry})))))
