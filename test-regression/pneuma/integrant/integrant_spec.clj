(ns pneuma.integrant.integrant-spec
    "Formal specification of integrant's architecture using pneuma formalisms.

  Models integrant's lifecycle state machine, multimethod effect signature,
  phase-scoped capabilities, dependency ordering, and reference existence
  contracts."
    (:require [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.statechart :as chart]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def lifecycle
     "Integrant's system lifecycle as a Harel statechart.
  States: :uninitialized, :expanded, :running, :suspended, :halted.
  The expand step is implicit in init but modeled explicitly because
  expand-key is a separate multimethod with distinct contracts."
     (chart/statechart
      {:states      #{:uninitialized :expanded :running :suspended :halted}
       :initial     {}
       :transitions [{:source :uninitialized :event :expand  :target :expanded}
                     {:source :expanded      :event :init    :target :running}
                     {:source :uninitialized :event :init    :target :running}
                     {:source :running       :event :halt    :target :halted}
                     {:source :running       :event :suspend :target :suspended}
                     {:source :suspended     :event :resume  :target :running}
                     {:source :suspended     :event :halt    :target :halted}]}))

(def multimethod-sig
     "The seven integrant multimethods as an algebraic effect signature.
  Each multimethod is an operation with typed inputs and output."
     (es/effect-signature
      {:operations
       {:init-key
        {:input  {:key :ConfigKey :value :ConfigValue}
         :output :InitializedValue}

        :halt-key!
        {:input  {:key :ConfigKey :value :InitializedValue}
         :output :Void}

        :resume-key
        {:input  {:key :ConfigKey :value :ConfigValue
                  :old-value :ConfigValue :old-impl :InitializedValue}
         :output :InitializedValue}

        :suspend-key!
        {:input  {:key :ConfigKey :value :InitializedValue}
         :output :Void}

        :resolve-key
        {:input  {:key :ConfigKey :value :InitializedValue}
         :output :ResolvedValue}

        :expand-key
        {:input  {:key :ConfigKey :value :ConfigValue}
         :output :ConfigMap}

        :assert-key
        {:input  {:key :ConfigKey :value :ConfigValue}
         :output :Void}}}))

(def init-phase-caps
     "Operations permitted during the init phase."
     (cap/capability-set
      {:id       :init-phase
       :dispatch #{:assert-key :expand-key :init-key :resolve-key}}))

(def running-phase-caps
     "Operations permitted while the system is running.
  No lifecycle multimethods are called; resolve-key is available
  for query access to hide internal state from dependents."
     (cap/capability-set
      {:id       :running-phase
       :dispatch #{}
       :query    #{:resolve-key}}))

(def suspend-phase-caps
     "Operations permitted during the suspend phase."
     (cap/capability-set
      {:id       :suspend-phase
       :dispatch #{:suspend-key!}}))

(def resume-phase-caps
     "Operations permitted during the resume phase."
     (cap/capability-set
      {:id       :resume-phase
       :dispatch #{:resume-key :resolve-key :assert-key}}))

(def halt-phase-caps
     "Operations permitted during the halt phase."
     (cap/capability-set
      {:id       :halt-phase
       :dispatch #{:halt-key!}}))

(def integrant-types
     "Type universe for integrant's multimethod signatures."
     (ts/type-schema
      {:ConfigKey        :keyword
       :ConfigValue      :any
       :ConfigMap        [:map-of :keyword :any]
       :InitializedValue :any
       :ResolvedValue    :any
       :Void             :nil}))

;;; Registry

(def integrant-registry
     "Morphisms connecting integrant's specification formalisms."
     {;; Every phase's dispatch set must be a subset of the declared operations
      :init-caps->ops
      (ex/existential-morphism
       {:id              :init-caps->ops
        :from            :capability-set/init
        :to              :effect-signature
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :running-query->ops
      (ex/existential-morphism
       {:id              :running-query->ops
        :from            :capability-set/running
        :to              :effect-signature
        :source-ref-kind :query-refs
        :target-ref-kind :operation-ids})

      :suspend-caps->ops
      (ex/existential-morphism
       {:id              :suspend-caps->ops
        :from            :capability-set/suspend
        :to              :effect-signature
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :resume-caps->ops
      (ex/existential-morphism
       {:id              :resume-caps->ops
        :from            :capability-set/resume
        :to              :effect-signature
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :halt-caps->ops
      (ex/existential-morphism
       {:id              :halt-caps->ops
        :from            :capability-set/halt
        :to              :effect-signature
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

   ;; Operation output types must resolve in the type universe
      :ops->types
      (st/structural-morphism
       {:id              :ops->types
        :from            :effect-signature
        :to              :type-schema
        :source-ref-kind :operation-outputs
        :target-ref-kind :type-ids})})

;;; Formalisms map

(def integrant-formalisms
     "All formalisms in the integrant specification."
     {:statechart              lifecycle
      :effect-signature        multimethod-sig
      :capability-set/init     init-phase-caps
      :capability-set/running  running-phase-caps
      :capability-set/suspend  suspend-phase-caps
      :capability-set/resume   resume-phase-caps
      :capability-set/halt     halt-phase-caps
      :type-schema             integrant-types})

;;; Spec system

(def spec-system
     "Complete specification system for integrant."
     {:formalisms integrant-formalisms
      :registry   integrant-registry})

;;; Gap report

(defn integrant-gap-report
      "Runs the full gap report on integrant's specification.
  Returns the three-layer gap report map."
      []
      (gap/gap-report spec-system))
