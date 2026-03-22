(ns pneuma.lean-spec
    "Formalism specification for the lean projection layer.
  Describes the two lean protocols (ILeanProjectable, ILeanConnection),
  their method signatures, and the required implementations using
  pneuma's own formalism types. Wires them into a gap report via a
  connection registry."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def lean-operations
     "The two lean protocol methods as an algebraic effect signature.
  Each method is an operation with typed input fields and a typed
  output."
     (es/effect-signature
      {:operations
       {;; ILeanProjectable
        :->lean
        {:input {:formalism :Formalism}
         :output :LeanSource}

        ;; ILeanConnection
        :->lean-conn
        {:input {:morphism :Morphism
                 :source :Formalism
                 :target :Formalism}
         :output :LeanSource}}}))

(def lean-formalism-caps
     "Every formalism record with a lean extension must implement these."
     (cap/capability-set
      {:id :lean-formalism
       :dispatch #{:->lean}}))

(def lean-morphism-caps
     "Every morphism record with a lean extension must implement these."
     (cap/capability-set
      {:id :lean-morphism
       :dispatch #{:->lean-conn}}))

(def lean-types
     "The type universe for lean method signatures."
     (ts/type-schema
      {:Formalism :any
       :Morphism :any
       :LeanSource :string}))

;;; Registry

(def lean-registry
     "Morphisms connecting the lean-layer formalisms."
     {:lean-formalism-caps->ops
      (ex/existential-morphism
       {:id :lean-formalism-caps->ops
        :from :capability-set/lean-formalism
        :to :effect-signature
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :lean-morphism-caps->ops
      (ex/existential-morphism
       {:id :lean-morphism-caps->ops
        :from :capability-set/lean-morphism
        :to :effect-signature
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :lean-ops->types
      (st/structural-morphism
       {:id :lean-ops->types
        :from :effect-signature
        :to :type-schema
        :source-ref-kind :operation-outputs
        :target-ref-kind :type-ids})})

;;; Formalisms map

(def lean-formalisms
     "All formalisms in the lean-layer specification graph."
     {:effect-signature lean-operations
      :capability-set/lean-formalism lean-formalism-caps
      :capability-set/lean-morphism lean-morphism-caps
      :type-schema lean-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms lean-formalisms
      :registry   lean-registry})

;;; Gap report

(defn lean-gap-report
      "Runs the full gap report on the lean projection layer specification.
  Returns the three-layer gap report map."
      []
      (gap/gap-report spec-system))
